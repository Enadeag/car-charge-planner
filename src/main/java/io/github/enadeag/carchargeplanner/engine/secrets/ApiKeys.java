package io.github.enadeag.carchargeplanner.engine.secrets;

import java.util.Optional;

/**
 * Where the API keys are kept: in the system keychain, or in a private file when there is no usable keychain. A file
 * found while a keychain is usable is moved into it, and removed only after every value was written and read back.
 * Values never appear in a log line or an error message.
 */
public final class ApiKeys implements SecretsSource {

    private final KeychainEntries keychain;
    private final SecretsFile file;

    public ApiKeys(Keychain keychain, SecretsFile file) {
        this.keychain = new KeychainEntries(keychain);
        this.file = file;
    }

    @Override
    public Secrets load() throws SecretsProblem {
        if (!keychain.usable()) {
            return file.exists() ? file.read() : Secrets.NONE;
        }
        if (file.exists()) {
            var kept = moveToKeychain();
            if (kept.isPresent()) {
                return kept.get();
            }
        }
        try {
            return keychain.read();
        } catch (KeychainException e) {
            throw new SecretsProblem("The system keychain could not be read. Unlock it, or enter the keys again.");
        }
    }

    /** Stores them all; what is not in {@code secrets} is forgotten. */
    public void save(Secrets secrets) throws SecretsProblem {
        secrets.checkNames();
        if (keychain.usable()) {
            try {
                keychain.write(secrets);
                file.delete();
                return;
            } catch (KeychainException e) {
                // such as Windows error 1312 over ssh: no keychain after all, so the file it is
            }
        }
        file.write(secrets);
    }

    public KeyStorage storage() {
        return keychain.usable() && !file.exists() ? KeyStorage.KEYCHAIN : KeyStorage.FILE;
    }

    public SecretsFile file() {
        return file;
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
        return Optional.empty();
    }
}
