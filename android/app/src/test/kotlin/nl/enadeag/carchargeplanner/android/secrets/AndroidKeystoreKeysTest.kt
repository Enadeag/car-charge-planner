package nl.enadeag.carchargeplanner.android.secrets

import java.security.KeyStoreException
import java.security.UnrecoverableKeyException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * What the Keystore's keys count as lost for good, on the JVM, where Android's stubs report SDK 0: nothing is lost
 * there but a key the Keystore invalidated. The versions that decide more are in
 * `AndroidKeystoreKeysOnRobolectricTest`, and `KeystoreOnTheDeviceTest` checks the real Keystore on the device.
 */
class AndroidKeystoreKeysTest {

    private val keys = AndroidKeystoreKeys()

    @Test
    fun aKeyThatCannotBeLoadedBeforeAndroid13MayComeBack() {
        assertThat(keys.lostForGood(UnrecoverableKeyException("Failed to obtain information about key"))).isFalse()
    }

    @Test
    fun aKeystoreThatFailsMayWorkAgain() {
        assertThat(keys.lostForGood(KeyStoreException("the Keystore is busy"))).isFalse()
    }

    /** Every install keeps its first key under this alias: another name would leave its entries unreadable. */
    @Test
    fun theFirstKeysAliasStaysKeychain() {
        assertThat(AndroidKeystoreKeys.ALIAS).isEqualTo("keychain")
    }
}
