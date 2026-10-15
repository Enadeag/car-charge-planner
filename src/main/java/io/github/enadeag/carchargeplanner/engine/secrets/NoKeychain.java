package io.github.enadeag.carchargeplanner.engine.secrets;

import java.util.Optional;

/** For a system without a keychain the app can use: the keys then go to the private file. */
public final class NoKeychain implements Keychain {

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

    private static KeychainException unavailable() {
        return new KeychainException("this system has no keychain the app can use");
    }
}
