package nl.enadeag.carchargeplanner.engine.secrets;

import java.time.Duration;
import java.util.Optional;

/**
 * The system's keychain (the Secret Service on Linux, the Credential Manager on Windows), holding one text per name.
 * The engine reaches it only through this interface; each system's client lives in its platform package. A client may
 * keep a connection open between calls; whoever made it closes it when the app ends.
 */
public interface Keychain extends AutoCloseable {

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

    /**
     * Asks the owner to unlock the keychain with the system's own prompt, and waits for their answer. Only a keychain
     * that can be locked, such as a desktop's keychain, can do this; the others refuse. Blocks for as long as the
     * prompt shows, up to {@code wait}: call it away from the user interface's thread, and never while holding a lock
     * that reading the keychain needs.
     *
     * @param wait how long to wait for the owner's answer, more than zero; then the prompt is dismissed
     * @param prompting runs once the prompt shows, before the wait, so the app can tell the owner; not when the
     *     keychain unlocks without one
     * @return unlocked, dismissed by the owner, or not answered in time or no longer waited for
     * @throws KeychainException {@link KeychainException.Kind#UNAVAILABLE} when there is no keychain to unlock;
     *     {@link KeychainException.Kind#REFUSED} when this keychain cannot be unlocked by the app, or the unlocking
     *     failed at once
     */
    default UnlockOutcome unlock(Duration wait, Runnable prompting) throws KeychainException {
        throw new KeychainException(KeychainException.Kind.REFUSED, "this keychain cannot be unlocked by the app");
    }

    /**
     * Stops waiting for the owner's answer to {@link #unlock(Duration, Runnable)}, which then ends as not answered and
     * dismisses its prompt; nothing happens when it is not waiting. Never blocks for long and never throws.
     */
    default void stopUnlocking() {
        // nothing waits for an answer
    }

    /**
     * Lets go of what the client keeps between calls, such as a connection, and stops waiting for an unlock; most keep
     * nothing. Never throws.
     */
    @Override
    default void close() {
        // nothing is kept between calls
    }
}
