package io.github.enadeag.carchargeplanner.engine.secrets;

import java.util.Optional;

/**
 * The system's keychain (the Secret Service on Linux, the Credential Manager on Windows), holding one text per name.
 * The engine reaches it only through this interface; each system's client lives in its platform package.
 */
public interface Keychain {

    /**
     * Reads one entry.
     *
     * @param name the entry's name, such as "forecast/open-meteo/api_key"
     * @return its value, or empty when there is no such entry
     * @throws KeychainException when there is no keychain, it is locked, or it failed
     */
    Optional<String> read(String name) throws KeychainException;

    /**
     * Stores one entry, replacing the value when the name is there already.
     *
     * @param name the entry's name, which the app keeps unique, such as "forecast/open-meteo/api_key"
     * @param value the text to keep; it never appears in a message
     * @throws KeychainException when there is no keychain, it is locked, or it refused
     */
    void write(String name, String value) throws KeychainException;

    /**
     * Removes one entry; nothing happens when the name is not there.
     *
     * @param name the name the entry was written under
     * @throws KeychainException when there is no keychain, it is locked, or it failed
     */
    void delete(String name) throws KeychainException;
}
