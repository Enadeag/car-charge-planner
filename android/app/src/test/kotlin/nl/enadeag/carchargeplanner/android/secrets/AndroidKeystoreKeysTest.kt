package nl.enadeag.carchargeplanner.android.secrets

import java.security.KeyStoreException
import java.security.UnrecoverableKeyException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * What the Keystore's keys count as lost for good, on the JVM, where Android's stubs report SDK 0: only a key the
 * Keystore invalidated is lost there, which `KeystoreOnTheDeviceTest` checks on the device, as the stub's exceptions
 * cannot be made here.
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
