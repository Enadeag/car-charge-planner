package io.github.enadeag.carchargeplanner.engine.secrets;

import java.lang.System.Logger.Level;
import java.util.Optional;

/**
 * Where the API keys are kept: in the system keychain, or in a private file when there is no usable keychain. A file
 * found while a keychain is usable is moved into it, and removed only after every value was written and read back.
 * Values never appear in a log line or an error message; where the keys are kept is logged each time it changes.
 */
public final class ApiKeys implements SecretsSource {

    private static final System.Logger LOG = System.getLogger(ApiKeys.class.getName());

    private final KeychainEntries keychain;
    private final SecretsFile file;
    /** Where the keys were kept when that was logged last; empty until it is first known. */
    private Optional<KeyStorage> logged = Optional.empty();

    /**
     * Makes the store; nothing is read or written yet.
     *
     * @param keychain the system's keychain, or {@link NoKeychain} where there is none
     * @param file where the keys go when the keychain cannot be used
     */
    public ApiKeys(Keychain keychain, SecretsFile file) {
        this.keychain = new KeychainEntries(keychain);
        this.file = file;
    }

    @Override
    public Secrets load() throws SecretsProblem {
        if (!keychain.usable()) {
            keptIn(KeyStorage.FILE, "the system keychain cannot be used");
            return file.exists() ? file.read() : Secrets.NONE;
        }
        if (file.exists()) {
            var kept = moveToKeychain();
            if (kept.isPresent()) {
                keptIn(KeyStorage.FILE, "they could not be moved into the system keychain");
                return kept.get();
            }
        }
        try {
            var secrets = keychain.read();
            keptIn(KeyStorage.KEYCHAIN, "");
            return secrets;
        } catch (KeychainException e) {
            throw new SecretsProblem("The system keychain could not be read. Unlock it, or enter the keys again.");
        }
    }

    /**
     * Stores them all, in the keychain when it works and else in the file.
     *
     * @param secrets every provider's secret settings; what is left out is forgotten
     * @throws SecretsProblem when a name has characters that are not allowed, or the file could not be written
     */
    public void save(Secrets secrets) throws SecretsProblem {
        secrets.checkNames();
        if (keychain.usable()) {
            try {
                keychain.write(secrets);
                file.delete();
                keptIn(KeyStorage.KEYCHAIN, "");
                return;
            } catch (KeychainException e) {
                // such as Windows error 1312 over ssh: no keychain after all, so the file it is
            }
        }
        file.write(secrets);
        keptIn(KeyStorage.FILE, "the system keychain cannot be used");
    }

    /**
     * Where the keys are kept now; asks the keychain whether it can be used.
     *
     * @return the keychain, or the file when the keychain cannot be used or the file could not be moved into it
     */
    public KeyStorage storage() {
        return keychain.usable() && !file.exists() ? KeyStorage.KEYCHAIN : KeyStorage.FILE;
    }

    /**
     * The file the keys go to without a keychain.
     *
     * @return the file this was made with
     */
    public SecretsFile file() {
        return file;
    }

    /** Logs where the keys are kept when that changed: the file is a warning, the keychain is as it should be. */
    private synchronized void keptIn(KeyStorage storage, String why) {
        if (logged.equals(Optional.of(storage))) {
            return;
        }
        switch (storage) {
            case FILE -> LOG.log(Level.WARNING, "API keys are kept in " + file.path() + ", readable only by you: "
                    + why);
            case KEYCHAIN -> LOG.log(Level.INFO, "API keys are kept in the system keychain"
                    + (logged.isPresent() ? " again" : ""));
        }
        logged = Optional.of(storage);
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
