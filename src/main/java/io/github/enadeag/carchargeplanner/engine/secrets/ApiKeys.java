package io.github.enadeag.carchargeplanner.engine.secrets;

import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Optional;

/**
 * Where the API keys are kept: in the system keychain, or in a private file when there is no usable keychain. A file
 * found while a keychain is usable is moved into it, and removed only after every value was written and read back.
 * Values never appear in a log line or an error message; where the keys are kept is logged each time it changes.
 *
 * <p>The keychain is slow to ask and may not answer at all, while the engine wants the keys for a plan every minute,
 * so what it gave is kept in memory: the keys until the next save, and a keychain that could not be used or read is
 * left alone for {@link #RETRY_AFTER}; the keys then come from the file, which is read again only when it changed. A
 * save always tries the keychain. Loads and saves take turns, so a save never mixes with a load or another save.
 *
 * <p>A locked keychain, such as a desktop keyring that was not unlocked at login, is not a missing one: the keys in it
 * are not available yet. A load says so, with {@link SecretsProblem.Kind#LOCKED}, and the keychain is asked again at
 * the next load, so the unlocking is noticed soon. A save into a locked keychain has the system ask the owner to unlock
 * it, and waits up to {@link #UNLOCK_WAIT} for their answer without holding up the loads. The keys go to the file only
 * when the owner dismisses that prompt or there turns out to be no keychain; when the app cannot have it unlocked, or
 * nobody answers, nothing is saved and the owner is told to unlock it and save again.
 */
public final class ApiKeys implements SecretsSource {

    /** How long a keychain that could not be used, or not be read, is left alone before it is asked again. */
    public static final Duration RETRY_AFTER = Duration.ofMinutes(5);
    /** How long a save waits for the owner to answer the system's prompt to unlock the keychain. */
    public static final Duration UNLOCK_WAIT = Duration.ofMinutes(2);

    private static final System.Logger LOG = System.getLogger(ApiKeys.class.getName());

    /** What an attempt to store the keys in the keychain came to. */
    private enum Stored {
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

    private final Keychain system;
    private final KeychainEntries keychain;
    private final SecretsFile file;
    private final InstantSource clock;
    private final Object turn = new Object();
    /** Held for a whole save, which may wait for the owner without holding the turn, so saves never mix. */
    private final Object saving = new Object();
    // the fields below belong to whoever holds the turn
    /** The keys as the keychain gave or took them last; empty when they must be read. */
    private Optional<Secrets> remembered = Optional.empty();
    /** Until when the keychain is left alone after it could not be used or read; empty when it may be asked. */
    private Optional<Instant> askAgainAt = Optional.empty();
    /** Why the keychain could not be read, while it is left alone; empty when it could not be used at all instead. */
    private Optional<SecretsProblem> unreadable = Optional.empty();
    /** Where the keys were kept when that was found last; empty until it is first known. */
    private Optional<KeyStorage> where = Optional.empty();

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
        this.system = keychain;
        this.keychain = new KeychainEntries(keychain);
        this.file = file;
        this.clock = clock;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Asks the keychain the first time, after a save, at every load while it is locked, and once
     * {@link #RETRY_AFTER} has passed since it failed; otherwise answers from memory or from the file. While the
     * keychain is locked, the keys come from the file only when they were kept there already.
     *
     * @throws SecretsProblem of the kind {@link SecretsProblem.Kind#LOCKED} while the keychain that holds the keys is
     *     locked; of the kind {@link SecretsProblem.Kind#FAILED} when the keychain or the file could not be read
     */
    @Override
    public Secrets load() throws SecretsProblem {
        synchronized (turn) {
            if (remembered.isPresent()) {
                return remembered.get();
            }
            if (leftAlone()) {
                if (unreadable.isPresent()) {
                    throw unreadable.get();
                }
                return fromFile();
            }
            unreadable = Optional.empty();
            switch (keychain.state()) {
                case UNUSABLE -> {
                    leaveAlone();
                    keptIn(KeyStorage.FILE, "the system keychain cannot be used");
                    return fromFile();
                }
                case LOCKED -> {
                    return whileLocked();
                }
                case USABLE -> {
                    // read below
                }
            }
            if (file.exists()) {
                var kept = moveToKeychain();
                if (kept.isPresent()) {
                    leaveAlone();
                    keptIn(KeyStorage.FILE, "they could not be moved into the system keychain");
                    return kept.get();
                }
            }
            try {
                var secrets = keychain.read();
                remembered = Optional.of(secrets);
                keptIn(KeyStorage.KEYCHAIN, "");
                return secrets;
            } catch (KeychainException e) {
                if (e.kind() == KeychainException.Kind.LOCKED) {
                    return whileLocked(); // locked between the two questions
                }
                var problem = new SecretsProblem(
                        "The system keychain could not be read. Unlock it, or enter the keys again.");
                leaveAlone();
                unreadable = Optional.of(problem);
                keptIn(KeyStorage.KEYCHAIN, "");
                throw problem;
            }
        }
    }

    /**
     * Stores them all, replacing what is stored, in the keychain when it works and else in the file; as
     * {@link #update(KeyChanges, Runnable)}, which a client that may not have read every stored key uses instead.
     *
     * @param secrets every provider's secret settings; what is left out is forgotten
     * @throws SecretsProblem as {@link #update(KeyChanges, Runnable)}
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
     * they did not enter or empty stays as it is, also one that could not be read, such as from a locked keyring.
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
    public void update(KeyChanges changes, Runnable prompting) throws SecretsProblem {
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
            if (intoKeychain(edit) != Stored.LOCKED) {
                return;
            }
            var stored = switch (unlock(prompting)) {
                case UNLOCKED -> intoKeychain(edit);
                case DISMISSED -> {
                    intoFile(edit, "the system keychain is locked, and was not unlocked when asked");
                    yield Stored.IN_FILE;
                }
                case NOT_ANSWERED -> Stored.LOCKED;
            };
            if (stored == Stored.LOCKED) {
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
        system.stopUnlocking();
    }

    /**
     * Where the keys are kept now. Finds out as {@link #load()} does, so it asks the keychain only when a load would.
     *
     * @return the keychain; the locked keychain while it holds the keys and is locked; or the file when the keychain
     *     cannot be used, the keys were kept in the file before, or the file could not be moved into the keychain
     */
    public KeyStorage storage() {
        synchronized (turn) {
            try {
                load();
            } catch (SecretsProblem e) {
                // where they are kept is known all the same
            }
            return where.orElse(KeyStorage.FILE);
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
     * keys stored now are read first unless the edit replaces them; a damaged index counts as none, as its entries
     * cannot be found anyway.
     */
    private Stored intoKeychain(Edit edit) throws SecretsProblem {
        synchronized (turn) {
            remembered = Optional.empty();
            askAgainAt = Optional.empty();
            unreadable = Optional.empty();
            switch (keychain.state()) {
                case UNUSABLE -> {
                    intoFile(edit, "the system keychain cannot be used");
                    return Stored.IN_FILE;
                }
                case LOCKED -> {
                    return Stored.LOCKED;
                }
                case USABLE -> {
                    // written below
                }
            }
            try {
                var secrets = edit.apply(edit.replaces() ? Secrets.NONE : storedInKeychain());
                keychain.write(secrets);
                file.delete();
                remembered = Optional.of(secrets);
                keptIn(KeyStorage.KEYCHAIN, "");
                return Stored.IN_KEYCHAIN;
            } catch (KeychainException e) {
                if (e.kind() == KeychainException.Kind.LOCKED) {
                    return Stored.LOCKED; // locked since it was asked, or the keyring holds none of our items yet
                }
                // such as Windows error 1312 over ssh: no keychain after all, so the file it is
                intoFile(edit, "the system keychain cannot be used");
                return Stored.IN_FILE;
            }
        }
    }

    /**
     * What the keychain holds, for a save that changes only some keys; none when its index is damaged.
     *
     * @throws KeychainException when it is locked or cannot be used
     */
    private Secrets storedInKeychain() throws KeychainException {
        try {
            return keychain.read();
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
                } catch (SecretsProblem e) {
                    // set aside by the write
                }
            }
            file.write(edit.apply(stored));
            leaveAlone();
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
            var outcome = system.unlock(UNLOCK_WAIT, prompting);
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

    /**
     * The keys while the keychain is locked: from the file when they were kept there, else not available yet. The
     * keychain is not left alone, so the next load notices the unlocking.
     */
    private Secrets whileLocked() throws SecretsProblem {
        if (file.exists()) {
            keptIn(KeyStorage.FILE, "the system keychain is locked");
            return file.read();
        }
        keptIn(KeyStorage.LOCKED_KEYCHAIN, "");
        throw new SecretsProblem(SecretsProblem.Kind.LOCKED,
                "The system keychain is locked. Unlock it so the app can read the API keys.");
    }

    private boolean leftAlone() {
        return askAgainAt.filter(clock.instant()::isBefore).isPresent();
    }

    private void leaveAlone() {
        askAgainAt = Optional.of(clock.instant().plus(RETRY_AFTER));
    }

    private Secrets fromFile() throws SecretsProblem {
        return file.exists() ? file.read() : Secrets.NONE;
    }

    /**
     * Notes where the keys are kept, and logs it when that changed: the file and a locked keychain are warnings, the
     * keychain is normal.
     */
    private void keptIn(KeyStorage storage, String why) {
        if (where.equals(Optional.of(storage))) {
            return;
        }
        switch (storage) {
            case FILE -> LOG.log(Level.WARNING, "API keys are kept in " + file.path() + ", readable only by you: "
                    + why);
            case LOCKED_KEYCHAIN -> LOG.log(Level.WARNING, "API keys are kept in the system keychain, which is locked: "
                    + "they can be read once it is unlocked");
            case KEYCHAIN -> LOG.log(Level.INFO, "API keys are kept in the system keychain"
                    + (where.isPresent() ? " again" : ""));
        }
        where = Optional.of(storage);
    }

    /**
     * The file's secrets when the move failed and the file stays. They are laid over what the keychain holds, so a key
     * kept only in the keychain, such as one the owner did not enter again while it was locked, stays.
     */
    private Optional<Secrets> moveToKeychain() throws SecretsProblem {
        var inFile = file.read();
        inFile.checkNames();
        try {
            var secrets = KeyChanges.entering(inFile).applyTo(storedInKeychain());
            keychain.write(secrets);
            if (!keychain.read().equals(secrets)) {
                return Optional.of(inFile);
            }
        } catch (KeychainException e) {
            return Optional.of(inFile);
        }
        file.delete();
        LOG.log(Level.INFO, "moved the API keys from " + file.path() + " into the system keychain");
        return Optional.empty();
    }
}
