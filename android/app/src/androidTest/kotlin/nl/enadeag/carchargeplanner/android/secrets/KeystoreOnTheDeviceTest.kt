package nl.enadeag.carchargeplanner.android.secrets

import android.content.Context
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import nl.enadeag.carchargeplanner.engine.secrets.KeychainException
import nl.enadeag.carchargeplanner.engine.store.FilePrivacy
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles
import nl.enadeag.carchargeplanner.engine.store.StoredJson
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tools.jackson.databind.node.ObjectNode

/**
 * The keychain with its keys in the real Android Keystore, which the JVM tests (`EncryptedKeychainContractTest`) stand
 * in for with keys in memory. Each test has aliases and a file of its own, so the app's own key is never touched.
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
    private val keychain =
        EncryptedKeychain(file.toPath(), PrivateFiles(FilePrivacy.NONE), AndroidKeystoreKeys(), alias)

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun refusal(call: () -> Unit): KeychainException.Kind? = try {
        call()
        null
    } catch (e: KeychainException) {
        e.kind()
    }

    private fun entries(): ObjectNode = StoredJson.parse(file.readText()).get("entries") as ObjectNode

    @After
    fun removeTheKeysAndTheFile() {
        val keyStore = keyStore()
        keyStore.aliases().toList().filter { it.startsWith(alias) }.forEach(keyStore::deleteEntry)
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
    fun theKeyIsAes256ForGcmCannotBeReadOutNeedsNoOwnerAndTakesNoIvOfTheApps() {
        keychain.write(KEY, "MADE-UP-OM-KEY")

        val key = keyStore().getKey(alias, null) as SecretKey
        val info = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
            .getKeySpec(key, KeyInfo::class.java) as KeyInfo
        assertNull("the key's bytes", key.encoded)
        assertEquals(256, info.keySize)
        assertEquals(listOf(KeyProperties.BLOCK_MODE_GCM), info.blockModes.toList())
        assertEquals(listOf(KeyProperties.ENCRYPTION_PADDING_NONE), info.encryptionPaddings.toList())
        assertFalse(info.isUserAuthenticationRequired)
        val ownIv = try {
            Cipher.getInstance("AES/GCM/NoPadding").init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, ByteArray(12)))
            "accepted"
        } catch (e: InvalidAlgorithmParameterException) {
            "refused"
        }
        assertEquals("randomized encryption", "refused", ownIv)
    }

    @Test
    fun aChangedCiphertextGivesRefused() {
        keychain.write(KEY, "MADE-UP-OM-KEY")
        val root = StoredJson.parse(file.readText()) as ObjectNode
        val entry = (root.get("entries") as ObjectNode).get(KEY) as ObjectNode
        val bytes = Base64.getDecoder().decode(entry.get("value").stringValue())
        bytes[14] = (bytes[14].toInt() xor 1).toByte()
        entry.put("value", Base64.getEncoder().encodeToString(bytes))
        file.writeText(StoredJson.compact(root))

        assertEquals(KeychainException.Kind.REFUSED, refusal { keychain.read(KEY) })
    }

    @Test
    fun aLostKeyGivesRefusedAndTheNextWriteMakesANewKeyAndKeepsTheOldEntry() {
        keychain.write(KEY, "MADE-UP-OM-KEY")
        keyStore().deleteEntry(alias)

        val refused = refusal { keychain.read(KEY) }
        keychain.write(OTHER, "MADE-UP-FS-KEY")

        assertEquals(KeychainException.Kind.REFUSED, refused)
        assertEquals("MADE-UP-FS-KEY", keychain.read(OTHER).get())
        assertTrue(keyStore().containsAlias("$alias-2"))
        assertTrue(entries().has(KEY))
        assertEquals(KeychainException.Kind.REFUSED, refusal { keychain.read(KEY) })
    }

    @Test
    fun anInvalidatedKeyIsLostForGoodAndOneThatCannotBeLoadedWithoutAReasonIsNot() {
        val keys = AndroidKeystoreKeys()

        assertTrue(keys.lostForGood(KeyPermanentlyInvalidatedException()))
        assertFalse(keys.lostForGood(UnrecoverableKeyException("no reason given")))
    }
}
