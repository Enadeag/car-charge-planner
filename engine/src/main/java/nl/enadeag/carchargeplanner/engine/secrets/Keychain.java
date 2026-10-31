package nl.enadeag.carchargeplanner.engine.secrets;

import java.time.Duration;
import java.util.Optional;

/**
 * The system's keychain (the Secret Service on Linux, the Credential Manager on Windows, on Android a store encrypted
 * with a key from the Android Keystore), holding one text per name. The engine reaches it only through this interface;
 * each system's client lives on the platform's side, so the engine needs no system's API (ADR 0010). A client may keep
 * a connection open between calls; whoever made it closes it when the app ends.
 *
 * <p>What every store guarantees, which {@code KeychainContractTest} in the engine's tests checks:
 * <ul>
 *   <li><b>Exactly what was written.</b> A read gives every text exactly as it was written: any Unicode, quotes,
 *       backslashes, line ends of any kind, an empty text (read back as empty text, not as no entry) and a long one;
 *       there is no hard size limit, and a store takes at least 2 KB of UTF-8 per value. A write replaces the value
 *       under that name; a name never written, or deleted, reads as no entry.</li>
 *   <li><b>The engine's names.</b> A name is made of {@code a-z}, {@code 0-9}, {@code /}, {@code _} and {@code -},
 *       such as {@code forecast/open-meteo/api_key}. The entry {@code index}, which lists the others, is kept like any
 *       other entry; a store cannot be asked for its names.</li>
 *   <li><b>Failures are a {@link KeychainException}, never a runtime exception.</b> Its kind says what went wrong.
 *       {@link KeychainException.Kind#UNAVAILABLE UNAVAILABLE}: the store cannot be used at all, such as no keychain
 *       on the system or one that does not answer. {@link KeychainException.Kind#LOCKED LOCKED}: only a store that can
 *       be locked, while it is; the owner can unlock it. {@link KeychainException.Kind#REFUSED REFUSED}: the store
 *       answered and failed, such as a value it cannot decrypt or one it did not keep. Only {@code LOCKED}, or an
 *       {@link #unlock(Duration, Runnable) unlock} the store refuses (which the engine counts as not answered, so the
 *       save ends locked), makes the engine wait for the owner. Any other failure of a save sends the API keys to the
 *       private file ({@code secrets.json}) instead. A load whose first read of the {@code index} fails takes the file
 *       as their place; a failure later in a load, of an entry or from a damaged index, is reported as keys that could
 *       not be read, and the file is not used. A store without a locked state never throws {@code LOCKED} and keeps
 *       the refusing {@link #unlock(Duration, Runnable)}. A null name or value is the caller's mistake, not a failure
 *       of the store.</li>
 *   <li><b>Threads.</b> A store is safe to use from several threads at once; of writes to one name at the same time,
 *       one value is kept whole, never a mix of them. A call may block for a while (on Linux up to about 5 seconds
 *       for a call, and an unlock as long as its prompt shows), so it is never made on the user interface's thread.
 *       On a thread that is interrupted, a call either works or ends with a {@code KeychainException}, and the thread
 *       stays interrupted.</li>
 *   <li><b>Closing.</b> {@link #close()} may be called more than once. A call after it works as before: the store
 *       opens again what it needs, which a later close lets go of again.</li>
 * </ul>
 */
public interface Keychain extends AutoCloseable {

    /**
     * Reads one entry.
     *
     * @param name the entry's name, such as "forecast/open-meteo/api_key"; never null
     * @return its value, or empty when there is no such entry
     * @throws KeychainException when there is no keychain, it is locked, or it failed
     */
    Optional<String> read(String name) throws KeychainException;

    /**
     * Stores one entry, replacing the value when the name is there already.
     *
     * @param name the entry's name, which the app keeps unique, such as "forecast/open-meteo/api_key"; never null
     * @param value the text to keep, which may be empty; never null, and it never appears in a message
     * @throws KeychainException when there is no keychain, it is locked, or it refused
     */
    void write(String name, String value) throws KeychainException;

    /**
     * Removes one entry; nothing happens when the name is not there.
     *
     * @param name the name the entry was written under; never null
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
     *     keychain unlocks without one; never null
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
     * nothing. Never throws, and may be called again; a later call opens again what it needs.
     */
    @Override
    default void close() {
        // nothing is kept between calls
    }
}
