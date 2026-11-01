package nl.enadeag.carchargeplanner.android.secrets;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Optional;
import javax.crypto.SecretKey;

/**
 * The AES keys that {@link EncryptedKeychain} encrypts its entries with, each under an alias: on the phone keys in the
 * Android Keystore ({@link AndroidKeystoreKeys}), in the app's local tests keys in memory. The store does the rest
 * itself (the file, its format, the encryption with these keys, which alias to use and what each failure means), so
 * the same logic runs on the phone and on the JVM. A key is never removed: the store has no way to. Safe to use from
 * several threads.
 */
public interface KeychainKeys {

    /**
     * Finds a key.
     *
     * @param alias the key's alias; never null
     * @return the key, usable with {@code AES/GCM/NoPadding} to encrypt and decrypt; empty when there is none
     * @throws GeneralSecurityException when the key could not be got; {@link #lostForGood} says whether it is gone
     * @throws IOException when the store of keys could not be read
     */
    Optional<SecretKey> find(String alias) throws GeneralSecurityException, IOException;

    /**
     * Makes a new key under an alias that holds none.
     *
     * @param alias the new key's alias; never null
     * @return the new key
     * @throws GeneralSecurityException when it could not be made
     * @throws IOException when the store of keys could not be read
     */
    SecretKey make(String alias) throws GeneralSecurityException, IOException;

    /**
     * Whether a failure to get a key or to start a cipher with it means the key can never be used again, such as a
     * key the system has invalidated, as opposed to a failure that may pass.
     *
     * @param failure what {@link #find} or a cipher's {@code init} with the key threw; never null
     * @return true only when the key is lost for good
     */
    boolean lostForGood(GeneralSecurityException failure);
}
