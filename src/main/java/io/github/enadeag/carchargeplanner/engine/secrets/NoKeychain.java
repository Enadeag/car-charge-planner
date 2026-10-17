package io.github.enadeag.carchargeplanner.engine.secrets;

import java.time.Duration;
import java.util.Optional;

/** For a system without a keychain the app can use: the keys then go to the private file. */
public final class NoKeychain implements Keychain {

    /** Makes the keychain that is not there. */
    public NoKeychain() {
        // every call reports that there is no keychain
    }

    @Override
    public Optional<String> read(String name) throws KeychainException {
        throw unavailable();
    }

    @Override
    public void write(String name, String value) throws KeychainException {
        throw unavailable();
    }

    @Override
    public void delete(String name) throws KeychainException {
        throw unavailable();
    }

    @Override
    public UnlockOutcome unlock(Duration wait) throws KeychainException {
        throw unavailable();
    }

    private static KeychainException unavailable() {
        return new KeychainException(KeychainException.Kind.UNAVAILABLE, "this system has no keychain the app can use");
    }
}
