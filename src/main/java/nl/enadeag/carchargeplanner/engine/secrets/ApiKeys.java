package nl.enadeag.carchargeplanner.engine.secrets;

import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;

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

    private static final System.Logger LOG = System.getLogger(ApiKeys.class.getName());

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
    }

    private final Keychain keychain;
    private final KeychainEntries entries;
    private final SecretsFile file;
    private final InstantSource clock;
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
     * Makes the store; nothing is read or written yet.
     *
     * @param keychain the system's keychain, or {@link NoKeychain} where there is none
     * @param file where the keys go when the keychain cannot be used
     * @param clock tells when a keychain that failed may be asked again; never null
     * @throws IllegalArgumentException when the clock is null
     */
    public ApiKeys(Keychain keychain, SecretsFile file, InstantSource clock) {
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
     * while it was locked, stays; the file is removed only after every value was written and read back. Does nothing
     * without a file, while the keychain is left alone after a failure, or while it is locked, so the next call
     * notices the unlocking. A keychain that cannot be used, or a move that fails, leaves the keychain alone for
     * {@link #RETRY_AFTER}. A file that cannot be read, or holds a name that is not allowed, stays, and a load says
     * why. May ask the keychain, which can take a few seconds: call it away from the user interface's thread.
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
            } catch (SecretsProblem _) {
                waitBeforeAskingKeychain();
                keptIn(KeyStorage.FILE, "the file could not be read, or holds a name that is not allowed");
                return;
            }
            try {
                var secrets = KeyChanges.entering(inFile).applyTo(storedInKeychain());
                entries.write(secrets);
                if (entries.read().equals(secrets)) {
                    file.delete();
                    remembered = secrets;
                    LOG.log(Level.INFO, "moved the API keys from " + file.path() + " into the system keychain");
                    keptIn(KeyStorage.KEYCHAIN, "");
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
     * they did not enter or empty stays as it is, also one that could not be read, such as from a locked keychain.
     * Tries the keychain also while it is left alone after a failure: the owner asked for this. When the keychain is
     * locked, the system asks the owner to unlock it and this waits up to {@link #UNLOCK_WAIT} for their answer, while
     * loads go on. Blocks: call it away from the user interface's thread.
     *
     * @param changes what the owner entered and emptied
     * @param prompting runs, on this thread, once the system's prompt to unlock the keychain shows, so the client can
     *     tell the owner; not when the keychain unlocks without one
     * @throws SecretsProblem of the kind {@link SecretsProblem.Kind#LOCKED} when the keychain stayed locked, as the
     *     app cannot have it unlocked, nobody answered in time, or {@link #stopUnlocking()} was called: nothing was
     *     saved. Of the kind {@link SecretsProblem.Kind#FAILED} when a name has characters that are not allowed or
     *     the file could not be written
     */
    public void applyChanges(KeyChanges changes, Runnable prompting) throws SecretsProblem {
        changes.entered().checkNames();
        store(new Edit() {
            @Override
            public Secrets apply(Secrets stored) {
                return changes.applyTo(stored);
            }

            @Override
            public boolean replaces() {
                return false;
            }
        }, prompting);
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
            } catch (SecretsProblem _) {
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
     * entries cannot be found anyway. A file whose keys could not be read is kept, as {@link #moveFileIntoKeychain()}
     * keeps it, so a save never drops a key it could not read: the keys then still come from the file, and a load says
     * why it cannot be used.
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
            try {
                var stored = edit.replaces() ? new StoredKeys(Secrets.NONE, true) : storedWithFile();
                var secrets = edit.apply(stored.secrets());
                entries.write(secrets);
                if (!stored.fileFoldedIn()) {
                    keptIn(KeyStorage.FILE, "the file could not be read, or holds a name that is not allowed");
                    LOG.log(Level.WARNING, "the API keys were saved in the system keychain, but " + file.path()
                            + " is kept: its keys could not be read, so they were not moved");
                    return SaveResult.IN_KEYCHAIN;
                }
                file.delete();
                remembered = secrets;
                keptIn(KeyStorage.KEYCHAIN, "");
                return SaveResult.IN_KEYCHAIN;
            } catch (KeychainException e) {
                if (e.kind() == KeychainException.Kind.LOCKED) {
                    return SaveResult.LOCKED; // locked since it was asked, or the keychain holds none of our items yet
                }
                // such as Windows error 1312 over ssh: no keychain after all, so the file it is
                intoFile(edit, "the system keychain cannot be used");
                return SaveResult.IN_FILE;
            }
        }
    }

    /**
     * What is stored, for a save that changes only some keys.
     *
     * @param secrets what the keychain holds, with the file's keys laid over it when they could be read
     * @param fileFoldedIn false when there is a file whose keys could not be read, or hold a name that is not allowed
     */
    private record StoredKeys(Secrets secrets, boolean fileFoldedIn) {
    }

    /**
     * What is stored, for a save that changes only some keys: what the keychain holds, with the file's keys laid over
     * it, so that no key kept only in the file is lost when the save removes the file. A file that cannot be read, or
     * holds a name that is not allowed, adds nothing, and says so, so the save keeps it.
     *
     * @throws KeychainException when the keychain is locked or cannot be used
     */
    private StoredKeys storedWithFile() throws KeychainException {
        var inKeychain = storedInKeychain();
        if (!file.exists()) {
            return new StoredKeys(inKeychain, true);
        }
        try {
            var inFile = file.read();
            inFile.checkNames();
            return new StoredKeys(KeyChanges.entering(inFile).applyTo(inKeychain), true);
        } catch (SecretsProblem _) {
            return new StoredKeys(inKeychain, false);
        }
    }

    /**
     * What the keychain holds; none when its index is damaged.
     *
     * @throws KeychainException when it is locked or cannot be used
     */
    private Secrets storedInKeychain() throws KeychainException {
        try {
            return entries.read();
        } catch (KeychainException e) {
            if (e.kind() != KeychainException.Kind.REFUSED) {
                throw e;
            }
            return Secrets.NONE;
        }
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
                } catch (SecretsProblem _) {
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
        LOG.log(Level.INFO, "the system keychain is locked: asking to unlock it to save the API keys");
        try {
            var outcome = keychain.unlock(UNLOCK_WAIT, prompting);
            LOG.log(Level.INFO, switch (outcome) {
                case UNLOCKED -> "the system keychain was unlocked";
                case DISMISSED -> "the system keychain was not unlocked: the prompt was dismissed";
                case NOT_ANSWERED -> "the system keychain was not unlocked: no answer, or no longer waited for";
            });
            return outcome;
        } catch (KeychainException e) {
            LOG.log(Level.WARNING, "the system keychain could not be unlocked: " + e.getMessage());
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
            case FILE -> LOG.log(Level.WARNING, "API keys are kept in " + file.path() + ", readable only by you: "
                    + why);
            case LOCKED_KEYCHAIN -> LOG.log(Level.WARNING, "API keys are kept in the system keychain, which is locked: "
                    + "they can be read once it is unlocked");
            case KEYCHAIN -> LOG.log(Level.INFO, "API keys are kept in the system keychain"
                    + (where != null ? " again" : ""));
        }
        where = storage;
    }

}
