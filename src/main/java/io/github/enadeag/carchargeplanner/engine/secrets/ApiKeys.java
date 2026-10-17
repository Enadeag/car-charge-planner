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
 */
public final class ApiKeys implements SecretsSource {

    /** How long a keychain that could not be used, or not be read, is left alone before it is asked again. */
    public static final Duration RETRY_AFTER = Duration.ofMinutes(5);

    private static final System.Logger LOG = System.getLogger(ApiKeys.class.getName());

    private final KeychainEntries keychain;
    private final SecretsFile file;
    private final InstantSource clock;
    private final Object turn = new Object();
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
        this.keychain = new KeychainEntries(keychain);
        this.file = file;
        this.clock = clock;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Asks the keychain the first time, after a save, and once {@link #RETRY_AFTER} has passed since it failed;
     * otherwise answers from memory or from the file.
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
            if (!keychain.usable()) {
                leaveAlone();
                keptIn(KeyStorage.FILE, "the system keychain cannot be used");
                return fromFile();
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
     * Stores them all, in the keychain when it works and else in the file. Tries the keychain also while it is left
     * alone after a failure: the owner asked for this.
     *
     * @param secrets every provider's secret settings; what is left out is forgotten
     * @throws SecretsProblem when a name has characters that are not allowed, or the file could not be written
     */
    public void save(Secrets secrets) throws SecretsProblem {
        secrets.checkNames();
        synchronized (turn) {
            remembered = Optional.empty();
            askAgainAt = Optional.empty();
            unreadable = Optional.empty();
            if (keychain.usable()) {
                try {
                    keychain.write(secrets);
                    file.delete();
                    remembered = Optional.of(secrets);
                    keptIn(KeyStorage.KEYCHAIN, "");
                    return;
                } catch (KeychainException e) {
                    // such as Windows error 1312 over ssh: no keychain after all, so the file it is
                }
            }
            file.write(secrets);
            leaveAlone();
            keptIn(KeyStorage.FILE, "the system keychain cannot be used");
        }
    }

    /**
     * Where the keys are kept now. Finds out as {@link #load()} does, so it asks the keychain only when a load would.
     *
     * @return the keychain, or the file when the keychain cannot be used or the file could not be moved into it
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

    private boolean leftAlone() {
        return askAgainAt.filter(clock.instant()::isBefore).isPresent();
    }

    private void leaveAlone() {
        askAgainAt = Optional.of(clock.instant().plus(RETRY_AFTER));
    }

    private Secrets fromFile() throws SecretsProblem {
        return file.exists() ? file.read() : Secrets.NONE;
    }

    /** Notes where the keys are kept, and logs it when that changed: the file is a warning, the keychain is normal. */
    private void keptIn(KeyStorage storage, String why) {
        if (where.equals(Optional.of(storage))) {
            return;
        }
        switch (storage) {
            case FILE -> LOG.log(Level.WARNING, "API keys are kept in " + file.path() + ", readable only by you: "
                    + why);
            case KEYCHAIN -> LOG.log(Level.INFO, "API keys are kept in the system keychain"
                    + (where.isPresent() ? " again" : ""));
        }
        where = Optional.of(storage);
    }

    /** The file's secrets when the move failed and the file stays. */
    private Optional<Secrets> moveToKeychain() throws SecretsProblem {
        var secrets = file.read();
        secrets.checkNames();
        try {
            keychain.write(secrets);
            if (!keychain.read().equals(secrets)) {
                return Optional.of(secrets);
            }
        } catch (KeychainException e) {
            return Optional.of(secrets);
        }
        file.delete();
        LOG.log(Level.INFO, "moved the API keys from " + file.path() + " into the system keychain");
        return Optional.empty();
    }
}
