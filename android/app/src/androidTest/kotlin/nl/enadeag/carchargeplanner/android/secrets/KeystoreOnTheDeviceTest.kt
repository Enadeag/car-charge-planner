package nl.enadeag.carchargeplanner.android.secrets

import android.content.Context
import android.security.keystore.KeyInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import nl.enadeag.carchargeplanner.engine.secrets.KeychainException
import nl.enadeag.carchargeplanner.engine.store.FilePrivacy
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The keychain with its key in the real Android Keystore, which the JVM tests (`EncryptedKeychainContractTest`) stand
 * in for with a key in memory. Each test has a key and a file of its own, so the app's own key is never touched.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreOnTheDeviceTest {

    private companion object {
        const val KEY = "forecast/open-meteo/api_key"
        const val OTHER = "forecast/forecast-solar/api_key"
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alias = "keychain-test-${UUID.randomUUID()}"
    private val folder = File(context.cacheDir, alias)
    private val file = File(folder, EncryptedKeychain.FILE_NAME)
    private val keychain = EncryptedKeychain(file.toPath(), PrivateFiles(FilePrivacy.NONE), AndroidKeystoreKey(alias))

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @After
    fun removeTheKeyAndTheFile() {
        keyStore().deleteEntry(alias)
        folder.deleteRecursively()
    }

    @Test
    fun aValueIsEncryptedWithAKeyMadeInTheKeystoreAndReadBack() {
        keychain.write(KEY, "MADE-UP-OM-KEY")

        assertEquals("MADE-UP-OM-KEY", keychain.read(KEY).get())
        assertFalse(file.readText().contains("MADE-UP-OM-KEY"))
        assertTrue(keyStore().containsAlias(alias))
    }

    @Test
    fun theKeyCannotBeReadOutAndNeedsNoOwnerToUseIt() {
        keychain.write(KEY, "MADE-UP-OM-KEY")

        val key = keyStore().getKey(alias, null) as SecretKey
        val info = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
            .getKeySpec(key, KeyInfo::class.java) as KeyInfo
        assertNull("the key's bytes", key.encoded)
        assertEquals(256, info.keySize)
        assertFalse(info.isUserAuthenticationRequired)
    }

    @Test
    fun aLostKeyGivesRefusedAndTheNextWriteStartsAgain() {
        keychain.write(KEY, "MADE-UP-OM-KEY")
        keyStore().deleteEntry(alias)

        val refused = try {
            keychain.read(KEY)
            null
        } catch (e: KeychainException) {
            e.kind()
        }
        keychain.write(OTHER, "MADE-UP-FS-KEY")

        assertEquals(KeychainException.Kind.REFUSED, refused)
        assertEquals("MADE-UP-FS-KEY", keychain.read(OTHER).get())
        assertFalse(keychain.read(KEY).isPresent)
    }

    /** The data extraction rules name the keychain's file as `no_backup/keychain.json` in the app's data folder. */
    @Test
    fun theFolderThatIsNeverBackedUpIsNoBackupInTheAppsDataFolder() {
        assertEquals(File(context.dataDir, "no_backup"), context.noBackupFilesDir)
    }
}
