package nl.enadeag.carchargeplanner.engine.secrets;

import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The keychain's states and what a load, a move of the file and a save do in each, with the file as fallback where it
 * is one: {@link #way(Use, KeychainEntries.State)} decides that for every state in one place. Not safe for threads:
 * whoever holds {@link ApiKeys}' turn calls it, but for {@link #unlock(Duration, Runnable)}, which must not hold it.
 */
final class KeychainWithFallback {

    /** What a save does to the stored keys. */
    interface Edit {

        Secrets apply(Secrets stored);

        /** Whether what is stored now does not matter, so it need not be read. */
        boolean replaces();

        /** Per provider, the setting names it removes, also ones whose entry could not be read. */
        default Map<ProviderId, Set<String>> removes() {
            return Map.of();
        }

        static Edit replacing(Secrets secrets) {
            return new Edit() {
                @Override
                public Secrets apply(Secrets stored) {
                    return secrets;
                }

                @Override
                public boolean replaces() {
                    return true;
                }
            };
        }

        static Edit changing(KeyChanges changes) {
            return new Edit() {
                @Override
                public Secrets apply(Secrets stored) {
                    return changes.applyTo(stored);
                }

                @Override
                public boolean replaces() {
                    return false;
                }

                @Override
                public Map<ProviderId, Set<String>> removes() {
                    return changes.removed();
                }
            };
        }
    }

    enum SaveResult {
        /** In the keychain, or in the file. */
        SAVED,
        /** The keychain is locked: nothing was written. */
        LOCKED
    }

    private enum Use {
        LOAD,
        MOVE,
        SAVE
    }

    /** Where a use goes on after the keychain's state was asked. */
    private enum Way {
        KEYCHAIN,
        /** The keys stay in the file, or go there; a load gives none, as there is no file. */
        FILE,
        /** Nothing is read or written until the owner unlocks the keychain. */
        LOCKED,
        /** The keychain cannot be used, and the file is not the fallback: a load or a save fails. */
        NOWHERE
    }

    private static final Logger LOG = LoggerFactory.getLogger(ApiKeys.class);
    /** Why a save failed without the file as fallback when the keychain cannot be used. */
    private static final String CANNOT_BE_USED = "The system keychain cannot be used now, so the API keys were not "
            + "stored.";

    private final Keychain keychain;
    private final KeychainEntries entries;
    private final FallbackFile file;
    private final KeychainMemory memory;

    KeychainWithFallback(Keychain keychain, FallbackFile file, KeychainMemory memory, KeyFallback fallback) {
        this.keychain = keychain;
        this.entries = new KeychainEntries(keychain, fallback);
        this.file = file;
        this.memory = memory;
    }

    /**
     * What a load, a move or a save does with the keychain in this state, also when it turns out locked or not usable
     * part of the way: where it goes on, and what is remembered and logged for that.
     */
    private Way way(Use use, KeychainEntries.State state) {
        return switch (state) {
            case USABLE -> Way.KEYCHAIN;
            case LOCKED -> switch (use) {
                case LOAD -> {
                    memory.keptIn(KeyStorage.LOCKED_KEYCHAIN, "");
                    yield Way.LOCKED;
                }
                case MOVE -> {
                    memory.keptIn(KeyStorage.FILE, "the system keychain is locked");
                    yield Way.FILE;
                }
                case SAVE -> Way.LOCKED;
            };
            case UNAVAILABLE -> switch (use) {
                case LOAD -> {
                    memory.waitBeforeAsking();
                    if (!file.isTheFallback()) {
                        memory.keptIn(KeyStorage.UNAVAILABLE, "the system keychain cannot be used");
                        yield Way.NOWHERE;
                    }
                    memory.keptIn(KeyStorage.FILE, "the system keychain cannot be used");
                    yield Way.FILE;
                }
                case MOVE -> {
                    memory.waitBeforeAsking();
                    memory.keptIn(KeyStorage.FILE, "the system keychain cannot be used");
                    yield Way.FILE;
                }
                case SAVE -> file.isTheFallback() ? Way.FILE : Way.NOWHERE;
            };
        };
    }

    /** As {@link ApiKeys#load()}. */
    Secrets load() throws SecretsProblem {
        if (memory.keys() != null) {
            return memory.keys();
        }
        if (file.inUse()) {
            memory.keptIn(KeyStorage.FILE, "they have not been moved into the system keychain");
            return file.read();
        }
        if (memory.waitingToAsk()) {
            if (memory.unreadable() != null) {
                throw memory.unreadable();
            }
            return Secrets.NONE;
        }
        memory.unreadable(null);
        switch (way(Use.LOAD, entries.state())) {
            case FILE -> {
                return Secrets.NONE;
            }
            case LOCKED -> throw locked();
            case NOWHERE -> {
                memory.unreadable(new SecretsProblem(
                        "The system keychain cannot be used now, so the API keys could not be read."));
                throw memory.unreadable();
            }
            case KEYCHAIN -> {
                // read below
            }
        }
        try {
            var secrets = entries.read();
            memory.remember(secrets);
            memory.keptIn(KeyStorage.KEYCHAIN, "");
            return secrets;
        } catch (KeychainException e) {
            if (e.kind() == KeychainException.Kind.LOCKED) {
                way(Use.LOAD, KeychainEntries.State.LOCKED);
                throw locked(); // locked between the two questions
            }
            var problem = new SecretsProblem(
                    "The system keychain could not be read. Unlock it, or enter the keys again.");
            memory.waitBeforeAsking();
            memory.unreadable(problem);
            memory.keptIn(KeyStorage.KEYCHAIN, "");
            throw problem;
        }
    }

    /** As {@link ApiKeys#moveFileIntoKeychain()}. */
    void moveFileIn() {
        if (!file.inUse() || memory.waitingToAsk()) {
            return;
        }
        if (way(Use.MOVE, entries.state()) != Way.KEYCHAIN) {
            return;
        }
        Secrets inFile;
        try {
            inFile = file.read();
        } catch (SecretsProblem ignored) {
            memory.waitBeforeAsking();
            memory.keptIn(KeyStorage.FILE, "the file could not be read, or holds a name that is not allowed");
            return;
        }
        try {
            var stored = entries.contents();
            var secrets = KeyChanges.entering(inFile).applyTo(stored.readable());
            var keptUnread = stored.keptBy(secrets, Map.of());
            entries.write(secrets, keptUnread, stored);
            var after = entries.contents();
            if (after.readable().equals(secrets) && after.unreadable().equals(keptUnread)) {
                file.delete();
                LOG.info("moved the API keys from " + file.path() + " into the system keychain");
                savedInKeychain(secrets, keptUnread);
                return;
            }
        } catch (KeychainException e) {
            if (e.kind() == KeychainException.Kind.LOCKED) {
                way(Use.MOVE, KeychainEntries.State.LOCKED);
                return; // locked between the questions: tried again next time
            }
        }
        memory.waitBeforeAsking();
        memory.keptIn(KeyStorage.FILE, "they could not be moved into the system keychain");
    }

    /**
     * Stores the keys in the keychain, or in the file when there is no usable keychain and the file is the fallback;
     * without it, such a save fails. Never while it is locked. The keys stored now, in the keychain and the file, are
     * read first unless the edit replaces them, so the file is moved in with the edit, such as after the owner
     * unlocked the keychain; a damaged index counts as none, as its entries cannot be found anyway. An entry the
     * keychain refuses to read stays as it is unless the edit enters or removes it. A keychain that was read and holds
     * keys, readable or not, fails the save rather than sending it to the file, which would hide those keys. A file
     * whose keys could not be read is kept, as {@link #moveFileIn()} keeps it, so a save never drops a key it could
     * not read: the keys then still come from the file, and a load says why it cannot be used.
     */
    SaveResult save(Edit edit) throws SecretsProblem {
        memory.forget();
        switch (way(Use.SAVE, entries.state())) {
            case FILE -> {
                intoFile(edit, "the system keychain cannot be used");
                return SaveResult.SAVED;
            }
            case LOCKED -> {
                return SaveResult.LOCKED;
            }
            case NOWHERE -> throw new SecretsProblem(CANNOT_BE_USED);
            case KEYCHAIN -> {
                // written below
            }
        }
        StoredKeys stored;
        try {
            stored = edit.replaces() ? new StoredKeys(entries.listed(), true) : storedWithFile();
        } catch (KeychainException e) {
            return notInKeychain(e, edit);
        }
        try {
            return intoKeychain(edit, stored);
        } catch (KeychainException e) {
            // a keychain that was read and holds keys, readable or not, would be hidden by the file, which a load
            // reads first: the save fails rather than fall back, also when deleting a removed one is refused
            if (e.kind() != KeychainException.Kind.LOCKED && stored.inKeychain().holdsKeys()) {
                throw new SecretsProblem("The API keys could not be stored in the system keychain ("
                        + e.getMessage() + ")." + (file.isTheFallback()
                                ? " They were not stored in the file either: it would hide the keys the keychain"
                                        + " holds, or bring back a removed one when moved in."
                                : ""));
            }
            return notInKeychain(e, edit);
        }
    }

    /**
     * A save after the owner dismissed the prompt to unlock the keychain: into the file, when it is the fallback.
     *
     * @return saved, or locked without the file as fallback: nothing was written then
     */
    SaveResult dismissed(Edit edit) throws SecretsProblem {
        if (!file.isTheFallback()) {
            return SaveResult.LOCKED;
        }
        intoFile(edit, "the system keychain is locked, and was not unlocked when asked");
        return SaveResult.SAVED;
    }

    /** Writes the keys into the keychain, keeping the entries it could not read that the edit leaves alone. */
    private SaveResult intoKeychain(Edit edit, StoredKeys stored) throws KeychainException {
        var secrets = edit.apply(stored.secrets());
        var keptUnread = stored.inKeychain().keptBy(secrets, edit.removes());
        entries.write(secrets, keptUnread, stored.inKeychain());
        if (!stored.fileFoldedIn()) {
            logKept(keptUnread);
            memory.keptIn(KeyStorage.FILE, "the file could not be read, or holds a name that is not allowed");
            LOG.warn("the API keys were saved in the system keychain, but " + file.path()
                    + " is kept: its keys could not be read, so they were not moved");
            return SaveResult.SAVED;
        }
        if (file.inUse()) {
            file.delete();
        }
        savedInKeychain(secrets, keptUnread);
        return SaveResult.SAVED;
    }

    /**
     * What a keychain that failed comes to: locked, or no keychain after all, so the keys go to the file, or the save
     * fails when it is not the fallback.
     */
    private SaveResult notInKeychain(KeychainException failure, Edit edit) throws SecretsProblem {
        if (failure.kind() == KeychainException.Kind.LOCKED) {
            return SaveResult.LOCKED; // locked since it was asked, or the keychain holds none of our items yet
        }
        if (!file.isTheFallback()) {
            throw new SecretsProblem("The API keys could not be stored in the system keychain (" + failure.getMessage()
                    + ").");
        }
        // such as Windows error 1312 over ssh: no keychain after all, so the file it is
        intoFile(edit, "the system keychain cannot be used");
        return SaveResult.SAVED;
    }

    /**
     * @param inKeychain what the keychain holds, as far as it could be read
     * @param secrets what the keychain holds that could be read, with the file's keys laid over it when they could be
     *     read
     * @param fileFoldedIn false when there is a file whose keys could not be read, or hold a name that is not allowed
     */
    private record StoredKeys(KeychainEntries.Contents inKeychain, Secrets secrets, boolean fileFoldedIn) {

        StoredKeys(KeychainEntries.Contents inKeychain, boolean fileFoldedIn) {
            this(inKeychain, inKeychain.readable(), fileFoldedIn);
        }
    }

    /**
     * What is stored, for a save that changes only some keys: what the keychain holds, with the file's keys laid over
     * it, so that no key kept only in the file is lost when the save removes the file. A file that cannot be read, or
     * holds a name that is not allowed, adds nothing, and says so, so the save keeps it. Entries the keychain refuses
     * to read are named, so the save keeps them.
     *
     * @throws KeychainException when the keychain is locked or cannot be used
     */
    private StoredKeys storedWithFile() throws KeychainException {
        var inKeychain = entries.contents();
        if (!file.inUse()) {
            return new StoredKeys(inKeychain, true);
        }
        try {
            var inFile = file.read();
            return new StoredKeys(inKeychain, KeyChanges.entering(inFile).applyTo(inKeychain.readable()), true);
        } catch (SecretsProblem ignored) {
            return new StoredKeys(inKeychain, false);
        }
    }

    /**
     * Notes that the keys are in the keychain now. They are remembered only when the keychain holds no entry it could
     * not read: else the next load reads it again, and says that keys could not be read.
     */
    private void savedInKeychain(Secrets secrets, Map<ProviderId, Set<String>> keptUnread) {
        logKept(keptUnread);
        memory.remember(keptUnread.isEmpty() ? secrets : null);
        memory.keptIn(KeyStorage.KEYCHAIN, "");
    }

    private static void logKept(Map<ProviderId, Set<String>> keptUnread) {
        var names = KeychainEntries.names(keptUnread);
        if (names.isEmpty()) {
            return;
        }
        LOG.warn("the API keys were saved in the system keychain; entries it could not read, kept as they were: "
                + names.size() + " (" + String.join(", ", names) + ")");
    }

    /** Writes the file, applying the edit to what it holds; then leaves the keychain alone for a while. */
    private void intoFile(Edit edit, String why) throws SecretsProblem {
        file.write(edit);
        memory.waitBeforeAsking();
        memory.keptIn(KeyStorage.FILE, why);
    }

    /**
     * Has the system ask the owner to unlock the keychain; never while holding the turn, so a plan meanwhile still
     * finds out that it is locked. No keychain after all counts as dismissed, so the keys go to the file, or, without
     * the file as fallback, fails the save; a keychain the app cannot have unlocked counts as not answered, so nothing
     * is saved.
     *
     * @throws SecretsProblem of the kind {@link SecretsProblem.Kind#FAILED} when there turns out to be no keychain and
     *     the file is not the fallback
     */
    UnlockOutcome unlock(Duration wait, Runnable prompting) throws SecretsProblem {
        LOG.info("the system keychain is locked: asking to unlock it to save the API keys");
        try {
            var outcome = keychain.unlock(wait, prompting);
            LOG.info(switch (outcome) {
                case UNLOCKED -> "the system keychain was unlocked";
                case DISMISSED -> "the system keychain was not unlocked: the prompt was dismissed";
                case NOT_ANSWERED -> "the system keychain was not unlocked: no answer, or no longer waited for";
            });
            return outcome;
        } catch (KeychainException e) {
            LOG.warn("the system keychain could not be unlocked: " + e.getMessage());
            if (!file.isTheFallback() && e.kind() == KeychainException.Kind.UNAVAILABLE) {
                throw new SecretsProblem(CANNOT_BE_USED);
            }
            return e.kind() == KeychainException.Kind.UNAVAILABLE ? UnlockOutcome.DISMISSED
                    : UnlockOutcome.NOT_ANSWERED;
        }
    }

    /** The keychain that holds the keys is locked: they are not available yet, and the next load asks again. */
    private static SecretsProblem locked() {
        return new SecretsProblem(SecretsProblem.Kind.LOCKED,
                "The system keychain is locked. Unlock it so the app can read the API keys.");
    }
}
