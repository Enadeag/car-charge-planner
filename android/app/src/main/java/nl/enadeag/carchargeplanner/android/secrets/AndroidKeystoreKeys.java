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
 * The keychain's keys in the Android Keystore: AES-256 for GCM, each under an alias of the app. A key never leaves the
 * device and cannot be read out, even by the app. It needs no authentication by the owner and, as the Keystore's
 * defaults leave it, can be used while the device is locked and is not in StrongBox, so background work reads the API
 * keys without a prompt; randomized encryption is required, so every value gets a new IV.
 *
 * <p>A key counts as lost for good only when the Keystore has invalidated it
 * ({@link KeyPermanentlyInvalidatedException}) or, from Android 13 on, cannot load it for a reason the Keystore does
 * not call transient ({@link UnrecoverableKeyException} caused by a {@code android.security.KeyStoreException} whose
 * {@code isTransientFailure()} is false). Every other failure, such as an {@link UnrecoverableKeyException} before
 * Android 13, which does not say why, may pass. A loss counted too readily costs nothing: the next write takes a new
 * alias and the old entries stay.
 */
public final class AndroidKeystoreKeys implements KeychainKeys {

    /** The alias of the app's first keychain key; renaming it would leave every install's entries unreadable. */
    public static final String ALIAS = "keychain";

    private static final String KEYSTORE = "AndroidKeyStore";
    private static final int KEY_BITS = 256;

    /** Makes the handle; the Keystore is not touched yet. */
    public AndroidKeystoreKeys() {
    }

    @Override
    public Optional<SecretKey> find(String alias) throws GeneralSecurityException, IOException {
        // a Keystore loaded afresh for every call, so a lookup after a restart of the Keystore sees it as it is now
        var keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        var found = keyStore.getKey(alias, null);
        if (found == null) {
            return Optional.empty();
        }
        if (!(found instanceof SecretKey secretKey)) {
            throw new GeneralSecurityException("the alias " + alias + " holds no secret key");
        }
        return Optional.of(secretKey);
    }

    @Override
    public SecretKey make(String alias) throws GeneralSecurityException {
        var spec = new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(KEY_BITS)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build();
        var generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(spec);
        return generator.generateKey();
    }

    @Override
    public boolean lostForGood(GeneralSecurityException failure) {
        if (failure instanceof KeyPermanentlyInvalidatedException) {
            return true;
        }
        return failure instanceof UnrecoverableKeyException && notTransient(failure);
    }

    /** Only Android 13 and later say whether a key that could not be loaded may load again. */
    private static boolean notTransient(Throwable failure) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return false;
        }
        for (var cause = failure.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof android.security.KeyStoreException keystore) {
                return !keystore.isTransientFailure();
            }
        }
        return false;
    }
}
