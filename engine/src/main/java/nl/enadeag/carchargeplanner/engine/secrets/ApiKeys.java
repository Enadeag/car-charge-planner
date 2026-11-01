package nl.enadeag.carchargeplanner.engine.secrets;

import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Where the API keys are kept: in the system keychain, or in a private file when there is no usable keychain. While
 * the file exists, the keys come from it; {@link #moveFileIntoKeychain()} moves it into a keychain that can be used,
 * and removes it only after every value was written and read back. Loading never moves it. Values never appear in a
 * log line or an error message; where the keys are kept is logged each time it changes.
 *
 * <p>The keychain is slow to ask and may not answer at all, while the engine wants the keys for a plan every minute,
 * so what it gave is kept in memory: the keys until the next save, and a keychain that could not be used or read is
 * left alone for {@link #RETRY_AFTER}; the keys then come from the file, which is read again only when it changed. A
 * save always tries the keychain. Loads, moves and saves take turns, so a save never mixes with another of them.
 *
 * <p>A locked keychain, such as a desktop's keychain that was not unlocked at login, is not a missing one: the keys in
 * it are not available yet. A load says so, with {@link SecretsProblem.Kind#LOCKED}, and the keychain is asked again at
 * the next load and the next move, so the unlocking is noticed soon. A save into a locked keychain has the system ask
 * the owner to unlock it, and waits up to {@link #UNLOCK_WAIT} for their answer without holding up the loads. The keys
 * go to the file only when the owner dismisses that prompt or there turns out to be no keychain; when the app cannot
 * have it unlocked, or nobody answers, nothing is saved and the owner is told to unlock it and save again.
 */
public final class ApiKeys implements SecretsSource {

    /** How long a keychain that could not be used, or not be read, is left alone before it is asked again. */
    public static final Duration RETRY_AFTER = Duration.ofMinutes(5);
    /** How long a save waits for the owner to answer the system's prompt to unlock the keychain. */
    public static final Duration UNLOCK_WAIT = Duration.ofMinutes(2);

    private static final Logger LOG = LoggerFactory.getLogger(ApiKeys.class);

    /** What an attempt to store the keys in the keychain came to. */
    private enum SaveResult {
        /** They are in the keychain. */
        IN_KEYCHAIN,
        /** The keychain is locked: nothing was written. */
        LOCKED,
        /** There is no keychain that can be used: they are in the file. */
        IN_FILE
    }

    /** How a save turns what is stored into what is to be stored. */
    private interface Edit {

        /** The keys to store, from the ones stored now. */
        Secrets apply(Secrets stored);

        /** Whether what is stored now does not matter, so it need not be read. */
        boolean replaces();

        /** Per provider, the setting names it removes, also ones whose entry could not be read. */
        default Map<ProviderId, Set<String>> removes() {
            return Map.of();
        }
    }

    private final Keychain keychain;
    private final KeychainEntries entries;
    private final SecretsFile file;
    private final Clock clock;
    private final Object turn = new Object();
    /** Held for a whole save, which may wait for the owner without holding the turn, so saves never mix. */
    private final Object saving = new Object();
    // the fields below belong to whoever holds the turn
    /** The keys as the keychain gave or took them last; null when they must be read. */
    private Secrets remembered;
    /** Until when the keychain is left alone after it could not be used or read; null when it may be asked. */
    private Instant askAgainAt;
    /** Why the keychain could not be read, while it is left alone; null when it could not be used at all instead. */
    private SecretsProblem unreadable;
    /** Where the keys were kept when that was found last; null until it is first known. */
    private KeyStorage where;

    /**
     * Makes the store on the system's clock; nothing is read or written yet.
     *
     * @param keychain the system's keychain, or {@link NoKeychain} where there is none
     * @param file where the keys go when the keychain cannot be used
     */
    public ApiKeys(Keychain keychain, SecretsFile file) {
        this(keychain, file, Clock.systemUTC());
    }

    /**
     * Makes the store; nothing is read or written yet. It takes a {@link Clock}, not an {@code InstantSource}, which
     * Android has only from Android 14 on and desugaring does not add (ADR 0010).
     *
     * @param keychain the system's keychain, or {@link NoKeychain} where there is none
     * @param file where the keys go when the keychain cannot be used
     * @param clock tells when a keychain that failed may be asked again; only its instant is used; never null
     * @throws IllegalArgumentException when the clock is null
     */
    public ApiKeys(Keychain keychain, SecretsFile file, Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("the clock must not be null");
        }
        this.keychain = keychain;
        this.entries = new KeychainEntries(keychain);
        this.file = file;
        this.clock = clock;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Only reads: while the file exists the keys come from it, and it is left where it is. Otherwise asks the
     * keychain the first time, after a save, at every load while it is locked, and once {@link #RETRY_AFTER} has
     * passed since it failed; in between answers from memory.
     *
     * @throws SecretsProblem of the kind {@link SecretsProblem.Kind#LOCKED} while the keychain that holds the keys is
     *     locked; of the kind {@link SecretsProblem.Kind#FAILED} when the keychain or the file could not be read, or
     *     the file holds a name that is not allowed
     */
    @Override
    public Secrets load() throws SecretsProblem {
        synchronized (turn) {
            if (remembered != null) {
                return remembered;
            }
            if (file.exists()) {
                keptIn(KeyStorage.FILE, "they have not been moved into the system keychain");
                var inFile = file.read();
                inFile.checkNames();
                return inFile;
            }
            if (waitingToAskKeychain()) {
                if (unreadable != null) {
                    throw unreadable;
                }
                return Secrets.NONE;
            }
            unreadable = null;
            switch (entries.state()) {
                case UNAVAILABLE -> {
                    waitBeforeAskingKeychain();
                    keptIn(KeyStorage.FILE, "the system keychain cannot be used");
                    return Secrets.NONE;
                }
                case LOCKED -> {
                    throw locked();
                }
                case USABLE -> {
                    // read below
                }
            }
            try {
                var secrets = entries.read();
                remembered = secrets;
                keptIn(KeyStorage.KEYCHAIN, "");
                return secrets;
            } catch (KeychainException e) {
                if (e.kind() == KeychainException.Kind.LOCKED) {
                    throw locked(); // locked between the two questions
                }
                var problem = new SecretsProblem(
                        "The system keychain could not be read. Unlock it, or enter the keys again.");
                waitBeforeAskingKeychain();
                unreadable = problem;
                keptIn(KeyStorage.KEYCHAIN, "");
                throw problem;
            }
        }
    }

    /**
     * Moves the keys from the file into the keychain, when there is a file and the keychain can be used. They are
     * laid over what the keychain holds, so a key kept only in the keychain, such as one the owner did not enter again
     * while it was locked, stays, as does an entry the keychain refuses to read, unless the file holds that key; the
     * file is removed only after every value was written and read back. Does nothing without a file, while the
     * keychain is left alone after a failure, or while it is locked, so the next call notices the unlocking. A
     * keychain that cannot be used, or a move that fails, leaves the keychain alone for {@link #RETRY_AFTER}. A file
     * that cannot be read, or holds a name that is not allowed, stays, and a load says why. May ask the keychain,
     * which can take a few seconds: call it away from the user interface's thread.
     */
    public void moveFileIntoKeychain() {
        synchronized (turn) {
            if (!file.exists() || waitingToAskKeychain()) {
                return;
            }
            switch (entries.state()) {
                case UNAVAILABLE -> {
                    waitBeforeAskingKeychain();
                    keptIn(KeyStorage.FILE, "the system keychain cannot be used");
                    return;
                }
                case LOCKED -> {
                    keptIn(KeyStorage.FILE, "the system keychain is locked");
                    return;
                }
                case USABLE -> {
                    // moved below
                }
            }
            Secrets inFile;
            try {
                inFile = file.read();
                inFile.checkNames();
            } catch (SecretsProblem ignored) {
                waitBeforeAskingKeychain();
                keptIn(KeyStorage.FILE, "the file could not be read, or holds a name that is not allowed");
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
                    keptIn(KeyStorage.FILE, "the system keychain is locked");
                    return; // locked between the questions: tried again next time
                }
            }
            waitBeforeAskingKeychain();
            keptIn(KeyStorage.FILE, "they could not be moved into the system keychain");
        }
    }

    /**
     * Stores them all, replacing what is stored, in the keychain when it works and else in the file; as
     * {@link #applyChanges(KeyChanges, Runnable)}, which a client that may not have read every stored key uses instead.
     *
     * @param secrets every provider's secret settings; what is left out is forgotten
     * @throws SecretsProblem as {@link #applyChanges(KeyChanges, Runnable)}
     */
    public void save(Secrets secrets) throws SecretsProblem {
        secrets.checkNames();
        store(new Edit() {
            @Override
            public Secrets apply(Secrets stored) {
                return secrets;
            }

            @Override
            public boolean replaces() {
                return true;
            }
        }, () -> { });
    }

    /**
     * Applies the owner's changes to the stored keys, in the keychain when it works and else in the file: every key
     * they did not enter or empty stays as it is, also one that could not be read, such as from a locked keychain or
     * an entry the keychain refuses to read. When the keychain holds such an entry, the keys never go to the file.
     * Tries the keychain also while it is left alone after a failure: the owner asked for this. When the keychain is
     * locked, the system asks the owner to unlock it and this waits up to {@link #UNLOCK_WAIT} for their answer, while
     * loads go on. Blocks: call it away from the user interface's thread.
     *
     * @param changes what the owner entered and emptied
     * @param prompting runs, on this thread, once the system's prompt to unlock the keychain shows, so the client can
     *     tell the owner; not when the keychain unlocks without one
     * @throws SecretsProblem of the kind {@link SecretsProblem.Kind#LOCKED} when the keychain stayed locked, as the
     *     app cannot have it unlocked, nobody answered in time, or {@link #stopUnlocking()} was called: nothing was
     *     saved. Of the kind {@link SecretsProblem.Kind#FAILED} when a name has characters that are not allowed, the
     *     file could not be written, or the keychain failed while it holds an entry it refuses to read
     */
    public void applyChanges(KeyChanges changes, Runnable prompting) throws SecretsProblem {
        changes.entered().checkNames();
        store(changing(changes), prompting);
    }

    /**
     * Removes these settings from where the keys are kept, without asking the owner anything: unlike
     * {@link #applyChanges(KeyChanges, Runnable)} it never has the system ask to unlock the keychain, so it can run
     * unasked, such as at start-up. Writes only when one of them is stored; it then saves as
     * {@link #applyChanges(KeyChanges, Runnable)} does, so keys kept in the file move into a keychain that can be used.
     * While the keychain is locked they are still removed from the file, which needs no keychain, so a secret kept
     * there does not stay behind for as long as the owner leaves the keychain locked. Blocks while the keychain
     * answers: call it away from the user interface's thread.
     *
     * @param names per provider, the setting names to remove, also one whose entry the keychain refuses to read; one
     *     that is not stored is passed over
     * @return true when one of them was stored and is removed now; false when none was stored
     * @throws SecretsProblem of the kind {@link SecretsProblem.Kind#LOCKED} while the keychain is locked: the keychain
     *     may still hold an older copy, so the caller should try again once it can be read, while the file, when there
     *     is one, no longer holds them; of the kind {@link SecretsProblem.Kind#FAILED} when the file or the
     *     keychain's index could not be read, the file not written, or the keychain failed while it holds an entry it
     *     refuses to read, such as when it refuses to delete one
     */
    public boolean remove(Map<ProviderId, Set<String>> names) throws SecretsProblem {
        var changes = new KeyChanges(Secrets.NONE, names);
        synchronized (saving) {
            if (!holdsAnyOf(names)) {
                return false;
            }
            if (intoKeychainElseFile(changing(changes)) == SaveResult.LOCKED) {
                if (file.exists()) {
                    intoFile(changing(changes), "they have not been moved into the system keychain");
                }
                throw new SecretsProblem(SecretsProblem.Kind.LOCKED, "The system keychain is locked.");
            }
            return true;
        }
    }

    /**
     * Whether one of these settings is stored where a load finds the keys. When the keychain holds an entry it refuses
     * to read, so a load fails, its entries are asked instead, so a setting whose entry cannot be read counts too.
     */
    private boolean holdsAnyOf(Map<ProviderId, Set<String>> names) throws SecretsProblem {
        synchronized (turn) {
            Secrets stored;
            try {
                stored = load();
            } catch (SecretsProblem problem) {
                if (problem.kind() == SecretsProblem.Kind.LOCKED || file.exists()) {
                    throw problem;
                }
                try {
                    return entries.contents().holdsAnyOf(names);
                } catch (KeychainException e) {
                    if (e.kind() == KeychainException.Kind.LOCKED) {
                        throw locked();
                    }
                    throw problem;
                }
            }
            var changes = new KeyChanges(Secrets.NONE, names);
            return !changes.applyTo(stored).equals(stored);
        }
    }

    private static Edit changing(KeyChanges changes) {
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

    private void store(Edit edit, Runnable prompting) throws SecretsProblem {
        synchronized (saving) {
            if (intoKeychainElseFile(edit) != SaveResult.LOCKED) {
                return;
            }
            var stored = switch (unlock(prompting)) {
                case UNLOCKED -> intoKeychainElseFile(edit);
                case DISMISSED -> {
                    intoFile(edit, "the system keychain is locked, and was not unlocked when asked");
                    yield SaveResult.IN_FILE;
                }
                case NOT_ANSWERED -> SaveResult.LOCKED;
            };
            if (stored == SaveResult.LOCKED) {
                throw new SecretsProblem(SecretsProblem.Kind.LOCKED,
                        "The system keychain is locked. Unlock it and save again.");
            }
        }
    }

    /**
     * Stops a save that waits for the owner to unlock the keychain, such as when its window closes: it then saves
     * nothing and says that the keychain is locked. Nothing happens when no save waits. Never blocks for long.
     */
    public void stopUnlocking() {
        keychain.stopUnlocking();
    }

    /**
     * Where the keys are kept now. Finds out as {@link #load()} does, so it asks the keychain only when a load would.
     *
     * @return the file while it exists or when the keychain cannot be used; else the locked keychain while it holds
     *     the keys and is locked, or the keychain
     */
    public KeyStorage storage() {
        synchronized (turn) {
            try {
                load();
            } catch (SecretsProblem ignored) {
                // where they are kept is known all the same
            }
            return where == null ? KeyStorage.FILE : where;
        }
    }

    /**
     * The file the keys go to without a keychain.
     *
     * @return the file this was made with
     */
    public SecretsFile file() {
        return file;
    }

    /**
     * Stores the keys in the keychain, or in the file when there is no usable keychain; never while it is locked. The
     * keys stored now, in the keychain and the file, are read first unless the edit replaces them, so the file is
     * moved in with the edit, such as after the owner unlocked the keychain; a damaged index counts as none, as its
     * entries cannot be found anyway. An entry the keychain refuses to read stays as it is unless the edit enters or
     * removes it; a keychain that holds one fails the save rather than sending it to the file, which would hide it. A
     * file whose keys could not be read is kept, as {@link #moveFileIntoKeychain()} keeps it, so a save never drops a
     * key it could not read: the keys then still come from the file, and a load says why it cannot be used.
     */
    private SaveResult intoKeychainElseFile(Edit edit) throws SecretsProblem {
        synchronized (turn) {
            remembered = null;
            askAgainAt = null;
            unreadable = null;
            switch (entries.state()) {
                case UNAVAILABLE -> {
                    intoFile(edit, "the system keychain cannot be used");
                    return SaveResult.IN_FILE;
                }
                case LOCKED -> {
                    return SaveResult.LOCKED;
                }
                case USABLE -> {
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
                // a keychain with entries it cannot read was readable otherwise, and the file would hide those
                // entries: the save fails rather than fall back, also when deleting a removed one is refused
                if (e.kind() != KeychainException.Kind.LOCKED && !stored.inKeychain().unreadable().isEmpty()) {
                    throw new SecretsProblem("The API keys could not be stored in the system keychain ("
                            + e.getMessage() + "). As it holds keys that could not be read, they were not stored "
                            + "in the file either.");
                }
                return notInKeychain(e, edit);
            }
        }
    }

    /** Writes the keys into the keychain, keeping the entries it could not read that the edit leaves alone. */
    private SaveResult intoKeychain(Edit edit, StoredKeys stored) throws KeychainException {
        var secrets = edit.apply(stored.secrets());
        var keptUnread = stored.inKeychain().keptBy(secrets, edit.removes());
        entries.write(secrets, keptUnread, stored.inKeychain());
        if (!stored.fileFoldedIn()) {
            logKept(keptUnread);
            keptIn(KeyStorage.FILE, "the file could not be read, or holds a name that is not allowed");
            LOG.warn("the API keys were saved in the system keychain, but " + file.path()
                    + " is kept: its keys could not be read, so they were not moved");
            return SaveResult.IN_KEYCHAIN;
        }
        file.delete();
        savedInKeychain(secrets, keptUnread);
        return SaveResult.IN_KEYCHAIN;
    }

    /** What a keychain that failed comes to: locked, or no keychain after all, so the keys go to the file. */
    private SaveResult notInKeychain(KeychainException failure, Edit edit) throws SecretsProblem {
        if (failure.kind() == KeychainException.Kind.LOCKED) {
            return SaveResult.LOCKED; // locked since it was asked, or the keychain holds none of our items yet
        }
        // such as Windows error 1312 over ssh: no keychain after all, so the file it is
        intoFile(edit, "the system keychain cannot be used");
        return SaveResult.IN_FILE;
    }

    /**
     * What is stored, for a save that changes only some keys.
     *
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
        if (!file.exists()) {
            return new StoredKeys(inKeychain, true);
        }
        try {
            var inFile = file.read();
            inFile.checkNames();
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
        remembered = keptUnread.isEmpty() ? secrets : null;
        keptIn(KeyStorage.KEYCHAIN, "");
    }

    /** Logs the entries a save kept unread, by name, when there are any. */
    private static void logKept(Map<ProviderId, Set<String>> keptUnread) {
        var names = KeychainEntries.names(keptUnread);
        if (names.isEmpty()) {
            return;
        }
        LOG.warn("the API keys were saved in the system keychain; entries it could not read, kept as they were: "
                + names.size() + " (" + String.join(", ", names) + ")");
    }

    /**
     * Writes the file, applying the edit to what it holds; a file that cannot be read counts as none, and is set aside
     * as it is replaced. Then leaves the keychain alone for a while.
     */
    private void intoFile(Edit edit, String why) throws SecretsProblem {
        synchronized (turn) {
            var stored = Secrets.NONE;
            if (!edit.replaces() && file.exists()) {
                try {
                    stored = file.read();
                } catch (SecretsProblem ignored) {
                    // set aside by the write
                }
            }
            file.write(edit.apply(stored));
            waitBeforeAskingKeychain();
            keptIn(KeyStorage.FILE, why);
        }
    }

    /**
     * Has the system ask the owner to unlock the keychain; outside the turn, so a plan meanwhile still finds out that
     * it is locked. No keychain after all counts as dismissed, so the keys go to the file; a keychain the app cannot
     * have unlocked counts as not answered, so nothing is saved.
     */
    private UnlockOutcome unlock(Runnable prompting) {
        LOG.info("the system keychain is locked: asking to unlock it to save the API keys");
        try {
            var outcome = keychain.unlock(UNLOCK_WAIT, prompting);
            LOG.info(switch (outcome) {
                case UNLOCKED -> "the system keychain was unlocked";
                case DISMISSED -> "the system keychain was not unlocked: the prompt was dismissed";
                case NOT_ANSWERED -> "the system keychain was not unlocked: no answer, or no longer waited for";
            });
            return outcome;
        } catch (KeychainException e) {
            LOG.warn("the system keychain could not be unlocked: " + e.getMessage());
            return e.kind() == KeychainException.Kind.UNAVAILABLE ? UnlockOutcome.DISMISSED : UnlockOutcome.NOT_ANSWERED;
        }
    }

    /** The keychain that holds the keys is locked: they are not available yet, and the next load asks again. */
    private SecretsProblem locked() {
        keptIn(KeyStorage.LOCKED_KEYCHAIN, "");
        return new SecretsProblem(SecretsProblem.Kind.LOCKED,
                "The system keychain is locked. Unlock it so the app can read the API keys.");
    }

    private boolean waitingToAskKeychain() {
        return askAgainAt != null && clock.instant().isBefore(askAgainAt);
    }

    private void waitBeforeAskingKeychain() {
        askAgainAt = clock.instant().plus(RETRY_AFTER);
    }

    /**
     * Notes where the keys are kept, and logs it when that changed: the file and a locked keychain are warnings, the
     * keychain is normal.
     */
    private void keptIn(KeyStorage storage, String why) {
        if (where == storage) {
            return;
        }
        switch (storage) {
            case FILE -> LOG.warn("API keys are kept in " + file.path() + ", readable only by you: "
                    + why);
            case LOCKED_KEYCHAIN -> LOG.warn("API keys are kept in the system keychain, which is locked: "
                    + "they can be read once it is unlocked");
            case KEYCHAIN -> LOG.info("API keys are kept in the system keychain"
                    + (where != null ? " again" : ""));
        }
        where = storage;
    }

}
