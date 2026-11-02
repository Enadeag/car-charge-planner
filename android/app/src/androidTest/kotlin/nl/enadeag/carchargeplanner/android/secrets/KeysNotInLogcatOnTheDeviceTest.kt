package nl.enadeag.carchargeplanner.android.secrets

import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.KeyStore
import java.time.Clock
import java.util.UUID
import java.util.concurrent.TimeUnit
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.secrets.ApiKeys
import nl.enadeag.carchargeplanner.engine.secrets.KeyFallback
import nl.enadeag.carchargeplanner.engine.secrets.Secrets
import nl.enadeag.carchargeplanner.engine.secrets.SecretsFile
import nl.enadeag.carchargeplanner.engine.secrets.SecretsProblem
import nl.enadeag.carchargeplanner.engine.store.FilePrivacy
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory

/**
 * No API key reaches Logcat, which the emulator job publishes: a made-up key goes through the API keys and the
 * keychain on the device, also through the warnings of a lost key, and the app's own Logcat lines never hold it.
 */
@RunWith(AndroidJUnit4::class)
class KeysNotInLogcatOnTheDeviceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alias = "keychain-test-${UUID.randomUUID()}"
    private val folder = File(context.cacheDir, alias)
    private val madeUpKey = "MADE-UP-KEY-${UUID.randomUUID()}"
    private val log = LoggerFactory.getLogger(KeysNotInLogcatOnTheDeviceTest::class.java)

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun apiKeys(): ApiKeys {
        val files = PrivateFiles(FilePrivacy.NONE)
        val keychain = EncryptedKeychain(File(folder, EncryptedKeychain.FILE_NAME).toPath(), files,
            AndroidKeystoreKeys(), alias)
        return ApiKeys(keychain, SecretsFile(File(folder, "secrets.json").toPath(), files), KeyFallback.NONE,
            Clock.systemUTC())
    }

    @After
    fun removeTheKeysAndTheFile() {
        val keyStore = keyStore()
        keyStore.aliases().toList().filter { it.startsWith(alias) }.forEach(keyStore::deleteEntry)
        folder.deleteRecursively()
    }

    @Test
    fun aKeyStoredReadAndLostNeverReachesLogcat() {
        val keys = Secrets(mapOf(ProviderId("open-meteo") to mapOf("api_key" to madeUpKey)))
        apiKeys().save(keys)
        assertEquals(keys, apiKeys().load())
        keyStore().deleteEntry(alias)
        val lost = try {
            apiKeys().load()
            null
        } catch (e: SecretsProblem) {
            e
        }
        apiKeys().save(keys)

        // the lines before the marker are in Logcat once the marker is
        val marker = "the keychain test is done ${UUID.randomUUID()}"
        log.info(marker)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        var lines = logcat()
        while (lines.none { marker in it } && System.nanoTime() < deadline) {
            Thread.sleep(150) // not a busy loop of logcat processes
            lines = logcat()
        }
        assertTrue("the lost key could not be read", lost != null)
        assertTrue("the marker in $lines", lines.any { marker in it })
        assertTrue("the keychain logged its lost key", lines.any { "EncryptedKeychain" in it })
        // also a part of it, and across the lines a long record is split into
        val part = madeUpKey.removePrefix("MADE-UP-KEY-").take(16)
        assertEquals(emptyList<String>(), lines.filter { part in it })
        assertFalse(madeUpKey in lines.joinToString(""))
    }

    /** Every Logcat line of this process so far: the app's, the engine's and this test's. */
    private fun logcat(): List<String> {
        val process = ProcessBuilder("logcat", "-d", "-v", "tag", "--pid=${Process.myPid()}")
            .redirectErrorStream(true).start()
        val lines = process.inputStream.bufferedReader().readLines()
        process.waitFor(10, TimeUnit.SECONDS)
        return lines
    }
}
