package io.github.enadeag.carchargeplanner.engine.secrets;

import java.util.Optional;

/**
 * The system's keychain (the Secret Service on Linux, the Credential Manager on Windows), holding one text per name.
 * The engine reaches it only through this interface; each system's client lives in its platform package.
 */
public interface Keychain {

    Optional<String> read(String name) throws KeychainException;

    /** Replaces the value when the name is there already. */
    void write(String name, String value) throws KeychainException;

    /** Nothing happens when the name is not there. */
    void delete(String name) throws KeychainException;
}
