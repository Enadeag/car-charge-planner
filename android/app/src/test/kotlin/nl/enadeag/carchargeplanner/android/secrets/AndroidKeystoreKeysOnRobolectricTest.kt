package nl.enadeag.carchargeplanner.android.secrets

import android.security.KeyStoreException
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.UnrecoverableKeyException
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What the Keystore's keys count as lost for good on the Android versions that decide it: the Keystore's own
 * exceptions are made here, on Robolectric's real Android classes, where the plain tests of `AndroidKeystoreKeysTest`
 * see the stubs' SDK 0. The version gate sits at Android 13 (SDK 33).
 */
@RunWith(AndroidJUnit4::class)
class AndroidKeystoreKeysOnRobolectricTest {

    private val keys = AndroidKeystoreKeys()

    // Android's KeyStoreException has no public constructor: the real class is made through its (code, message) one,
    // with the Keystore's own code, that Android turns into the public ones
    private fun keystoreFailure(code: Int) = KeyStoreException::class.java
        .getDeclaredConstructor(Int::class.java, String::class.java)
        .apply { isAccessible = true }.newInstance(code, "Keystore code $code")

    /** The key is not there at all, which no waiting changes. */
    private fun permanent() = keystoreFailure(KEY_NOT_FOUND)

    /** A failure the Keystore asks to retry after a back-off. */
    private fun transient() = keystoreFailure(RETRY_LATER)

    private fun unrecoverable(cause: Throwable?) = UnrecoverableKeyException("the key cannot be loaded").also {
        if (cause != null) it.initCause(cause)
    }

    @Test
    @Config(sdk = [32, 33])
    fun aKeyTheKeystoreInvalidatedIsLostForGoodOnEveryAndroid() {
        assertThat(keys.lostForGood(KeyPermanentlyInvalidatedException("a new fingerprint"))).isTrue()
    }

    @Test
    @Config(sdk = [32])
    fun beforeAndroid13NoReasonIsGivenSoAKeyThatCannotBeLoadedMayComeBack() {
        assertThat(keys.lostForGood(unrecoverable(null))).isFalse()
    }

    @Test
    @Config(sdk = [33])
    fun onAndroid13AKeyThatCannotBeLoadedWithoutAReasonMayComeBack() {
        assertThat(keys.lostForGood(unrecoverable(null))).isFalse()
        assertThat(keys.lostForGood(unrecoverable(IllegalStateException("not a Keystore failure")))).isFalse()
    }

    @Test
    @Config(sdk = [33])
    fun onAndroid13AKeyThatCannotBeLoadedForAReasonThatIsNoTransientFailureIsLostForGood() {
        assertThat(permanent().numericErrorCode).isEqualTo(KeyStoreException.ERROR_KEY_DOES_NOT_EXIST)
        assertThat(permanent().isTransientFailure).isFalse()
        assertThat(keys.lostForGood(unrecoverable(permanent()))).isTrue()
    }

    @Test
    @Config(sdk = [33])
    fun onAndroid13AKeyThatFailedTransientlyMayComeBack() {
        assertThat(transient().retryPolicy).isEqualTo(KeyStoreException.RETRY_WITH_EXPONENTIAL_BACKOFF)
        assertThat(transient().isTransientFailure).isTrue()
        assertThat(keys.lostForGood(unrecoverable(transient()))).isFalse()
    }

    @Test
    @Config(sdk = [33])
    fun theReasonIsFoundFurtherDownTheChainOfCauses() {
        val wrapped = IllegalStateException("wrapped", permanent())
        assertThat(keys.lostForGood(unrecoverable(wrapped))).isTrue()
        assertThat(keys.lostForGood(unrecoverable(IllegalStateException("wrapped", transient())))).isFalse()
    }

    @Test
    @Config(sdk = [33])
    fun aFailureOtherThanAKeyThatCannotBeLoadedIsNeverLostForGood() {
        assertThat(keys.lostForGood(java.security.KeyStoreException("busy", permanent()))).isFalse()
    }

    private companion object {
        /** The Keystore's code for a key that does not exist. */
        const val KEY_NOT_FOUND = 7

        /** The Keystore's code for a failure to retry later. */
        const val RETRY_LATER = -28
    }
}
