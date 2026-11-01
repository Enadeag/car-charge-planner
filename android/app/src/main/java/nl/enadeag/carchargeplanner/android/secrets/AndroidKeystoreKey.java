package nl.enadeag.carchargeplanner.android.secrets;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.UnrecoverableKeyException;
import java.util.Optional;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * The keychain's key in the Android Keystore: AES-256 for GCM, under one alias of the app. It never leaves the device
 * and cannot be read out, even by the app. It needs no authentication by the owner and can be used while the device is
 * locked, so background work reads the API keys without a prompt; randomized encryption is required, so every value
 * gets a new IV; StrongBox is not asked for, as most phones lack it and it is slow.
 *
 * <p>A key counts as lost for good when the Keystore says so ({@link KeyPermanentlyInvalidatedException}) or cannot
 * load it ({@link UnrecoverableKeyException}), except, from Android 13 on, when the Keystore marks the failure
 * underneath as one that may pass.
 */
public final class AndroidKeystoreKey implements KeychainKey {

    /** The alias of the app's own keychain key. */
    public static final String ALIAS = "keychain";

    private static final String KEYSTORE = "AndroidKeyStore";
    private static final int KEY_BITS = 256;

    private final String alias;

    /**
     * Makes the key's handle; the Keystore is not touched yet.
     *
     * @param alias the key's alias in the app's Keystore: {@link #ALIAS} for the app, another for a test; never null
     */
    public AndroidKeystoreKey(String alias) {
        this.alias = alias;
    }

    @Override
    public Optional<SecretKey> find() throws GeneralSecurityException, IOException {
        var found = keyStore().getKey(alias, null);
        if (found == null) {
            return Optional.empty();
        }
        if (!(found instanceof SecretKey secretKey)) {
            throw new UnrecoverableKeyException("the alias " + alias + " holds no secret key");
        }
        return Optional.of(secretKey);
    }

    @Override
    public SecretKey make() throws GeneralSecurityException {
        var spec = new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(KEY_BITS)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // both are false by default; said here, as they are what lets background work use the key
            spec.setUnlockedDeviceRequired(false).setIsStrongBoxBacked(false);
        }
        var generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(spec.build());
        return generator.generateKey();
    }

    @Override
    public void forget() throws GeneralSecurityException, IOException {
        var keyStore = keyStore();
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias);
        }
    }

    @Override
    public boolean lostForGood(GeneralSecurityException failure) {
        if (failure instanceof KeyPermanentlyInvalidatedException) {
            return true;
        }
        return failure instanceof UnrecoverableKeyException && !mayPass(failure);
    }

    /** Only Android 13 and later tell a Keystore failure that may pass, such as a busy Keystore, from one that won't. */
    private static boolean mayPass(Throwable failure) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return false;
        }
        for (var cause = failure.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof android.security.KeyStoreException keystore && keystore.isTransientFailure()) {
                return true;
            }
        }
        return false;
    }

    private static KeyStore keyStore() throws GeneralSecurityException, IOException {
        var keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        return keyStore;
    }
}
