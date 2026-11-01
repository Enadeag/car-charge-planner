package nl.enadeag.carchargeplanner.android.secrets;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Optional;
import javax.crypto.SecretKey;

/**
 * The one AES key that {@link EncryptedKeychain} encrypts its entries with: on the phone a key in the Android Keystore
 * ({@link AndroidKeystoreKey}), in the app's local tests a key in memory. The store does the rest itself (the file, its
 * format, the encryption with this key and what each failure means), so the same logic runs on the phone and on the
 * JVM. Safe to use from several threads.
 */
public interface KeychainKey {

    /**
     * Finds the key.
     *
     * @return the key, usable with {@code AES/GCM/NoPadding} to encrypt and decrypt; empty when there is none
     * @throws GeneralSecurityException when the key could not be got; {@link #lostForGood} says whether it is gone
     * @throws IOException when the store of keys could not be read
     */
    Optional<SecretKey> find() throws GeneralSecurityException, IOException;

    /**
     * Makes a new key, replacing the one there may be.
     *
     * @return the new key
     * @throws GeneralSecurityException when it could not be made
     * @throws IOException when the store of keys could not be read
     */
    SecretKey make() throws GeneralSecurityException, IOException;

    /**
     * Removes the key; nothing happens when there is none.
     *
     * @throws GeneralSecurityException when it could not be removed
     * @throws IOException when the store of keys could not be read
     */
    void forget() throws GeneralSecurityException, IOException;

    /**
     * Whether a failure to get the key or to start a cipher with it means the key can never be used again, such as a
     * key the system has invalidated, as opposed to a failure that may pass.
     *
     * @param failure what {@link #find()} or a cipher's {@code init} with the key threw; never null
     * @return true when the key is lost for good
     */
    boolean lostForGood(GeneralSecurityException failure);
}
