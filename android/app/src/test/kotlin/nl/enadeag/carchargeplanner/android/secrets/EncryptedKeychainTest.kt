package nl.enadeag.carchargeplanner.android.secrets

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStoreException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import java.util.Base64
import javax.crypto.spec.SecretKeySpec
import kotlin.io.path.readText
import kotlin.io.path.writeText
import nl.enadeag.carchargeplanner.engine.secrets.KeychainException
import nl.enadeag.carchargeplanner.engine.secrets.KeychainException.Kind
import nl.enadeag.carchargeplanner.engine.store.FilePrivacy
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles
import nl.enadeag.carchargeplanner.engine.store.StoredJson
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowableOfType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import tools.jackson.databind.node.ObjectNode

/**
 * What the Android keychain does beyond the engine's contract: how it encrypts, and which failures it refuses as lost
 * for good and which it reports as unavailable, as without the fallback a refused `index` starts a new one.
 */
class EncryptedKeychainTest {

    private companion object {
        const val KEY = "forecast/open-meteo/api_key"
        const val OTHER = "forecast/forecast-solar/api_key"

        @JvmStatic
        fun passingKeyFailures(): List<Exception> = listOf(KeyStoreException("the Keystore is busy"),
            ProviderException("the Keystore did not answer"), IOException("could not load the Keystore"))
    }

    @TempDir
    lateinit var folder: Path

    private val key = SoftwareKey()

    private val file by lazy { folder.resolve("no_backup").resolve(EncryptedKeychain.FILE_NAME) }

    private val keychain by lazy { EncryptedKeychain(file, PrivateFiles(FilePrivacy.NONE), key) }

    private fun failure(call: () -> Unit): Kind? =
        catchThrowableOfType(KeychainException::class.java) { call() }?.kind()

    private fun entries(): ObjectNode = StoredJson.parse(file.readText()).get("entries") as ObjectNode

    private fun sealed(name: String): ByteArray = Base64.getDecoder().decode(entries().get(name).stringValue())

    private fun replaceEntries(change: (ObjectNode) -> Unit) {
        val root = StoredJson.parse(file.readText()) as ObjectNode
        change(root.get("entries") as ObjectNode)
        file.writeText(StoredJson.compact(root))
    }

    @Test
    fun theFileHoldsAVersionAndEachValueOnlyAsAnIvAndCiphertextWithItsTag() {
        keychain.write(KEY, "OM-SECRET-1")

        val text = file.readText()
        assertThat(text).doesNotContain("OM-SECRET-1")
        assertThat(StoredJson.parse(text).get("version").intValue()).isEqualTo(1)
        assertThat(sealed(KEY)).hasSize(12 + "OM-SECRET-1".length + 16)
    }

    @Test
    fun everyWriteTakesANewIv() {
        keychain.write(KEY, "OM-SECRET-1")
        val first = sealed(KEY)
        keychain.write(KEY, "OM-SECRET-1")
        val second = sealed(KEY)

        assertThat(second.copyOf(12)).isNotEqualTo(first.copyOf(12))
    }

    @Test
    fun theKeyIsMadeAtTheFirstWriteAndKeptAfterIt() {
        keychain.read(KEY)
        keychain.delete(KEY)
        assertThat(key.made).hasValue(0)

        keychain.write(KEY, "OM-SECRET-1")
        keychain.write(OTHER, "FS-SECRET-1")

        assertThat(key.made).hasValue(1)
    }

    @Test
    fun anEntryMovedToAnotherNameFailsItsCheck() {
        keychain.write(KEY, "OM-SECRET-1")
        keychain.write(OTHER, "FS-SECRET-1")
        replaceEntries { entries ->
            val moved = entries.get(KEY)
            entries.set(KEY, entries.get(OTHER))
            entries.set(OTHER, moved)
        }

        assertThat(failure { keychain.read(KEY) }).isEqualTo(Kind.REFUSED)
        assertThat(failure { keychain.read(OTHER) }).isEqualTo(Kind.REFUSED)
    }

    @Test
    fun aChangedCiphertextFailsItsCheck() {
        keychain.write(KEY, "OM-SECRET-1")
        val changed = sealed(KEY).also { it[14] = (it[14].toInt() xor 1).toByte() }
        replaceEntries { it.put(KEY, Base64.getEncoder().encodeToString(changed)) }

        assertThat(failure { keychain.read(KEY) }).isEqualTo(Kind.REFUSED)
    }

    @Test
    fun anEntryEncryptedWithAnotherKeyIsRefused() {
        keychain.write(KEY, "OM-SECRET-1")
        key.make()

        assertThat(failure { keychain.read(KEY) }).isEqualTo(Kind.REFUSED)
    }

    @Test
    fun withTheKeyGoneAnEntryIsRefusedAndTheNextWriteStartsAgainWithANewKey() {
        keychain.write(KEY, "OM-SECRET-1")
        keychain.write("index", "[\"$KEY\"]")
        key.forget()

        assertThat(failure { keychain.read(KEY) }).isEqualTo(Kind.REFUSED)
        assertThat(failure { keychain.read("index") }).isEqualTo(Kind.REFUSED)
        keychain.write(OTHER, "FS-SECRET-1")

        assertThat(keychain.read(OTHER)).contains("FS-SECRET-1")
        assertThat(keychain.read(KEY)).isEmpty
        assertThat(entries().propertyNames()).containsExactly(OTHER)
        assertThat(key.made).hasValue(2)
    }

    @Test
    fun aKeyLostForGoodIsRefusedAndReplacedAtTheNextWrite() {
        keychain.write(KEY, "OM-SECRET-1")
        key.lostForGood = { it is UnrecoverableKeyException }
        key.failNext(UnrecoverableKeyException("the key cannot be loaded"))

        assertThat(failure { keychain.read(KEY) }).isEqualTo(Kind.REFUSED)
        keychain.write(OTHER, "FS-SECRET-1")

        assertThat(keychain.read(OTHER)).contains("FS-SECRET-1")
        assertThat(keychain.read(KEY)).isEmpty
    }

    @Test
    fun aKeyTheCipherRejectsForGoodIsRefusedAndReplacedAtTheNextWrite() {
        keychain.write(KEY, "OM-SECRET-1")
        key.key = SecretKeySpec(ByteArray(5), "AES") // an InvalidKeyException at init, as an invalidated key gives
        key.lostForGood = { true }

        assertThat(failure { keychain.read(KEY) }).isEqualTo(Kind.REFUSED)
        keychain.write(OTHER, "FS-SECRET-1")

        assertThat(keychain.read(OTHER)).contains("FS-SECRET-1")
        assertThat(keychain.read(KEY)).isEmpty
    }

    @Test
    fun aKeyTheCipherRejectsForNowIsUnavailableAndLosesNothing() {
        keychain.write(KEY, "OM-SECRET-1")
        val good = key.key
        val before = file.readText()
        key.key = SecretKeySpec(ByteArray(5), "AES")

        assertThat(failure { keychain.read(KEY) }).isEqualTo(Kind.UNAVAILABLE)
        assertThat(failure { keychain.write(OTHER, "FS-SECRET-1") }).isEqualTo(Kind.UNAVAILABLE)

        assertThat(file.readText()).isEqualTo(before)
        key.key = good
        assertThat(keychain.read(KEY)).contains("OM-SECRET-1")
    }

    @ParameterizedTest
    @MethodSource("passingKeyFailures")
    fun aKeyThatCannotBeGotForNowIsUnavailableAndLosesNothing(passing: Exception) {
        keychain.write(KEY, "OM-SECRET-1")
        val before = file.readText()
        key.failNext(passing)

        assertThat(failure { keychain.read(KEY) }).isEqualTo(Kind.UNAVAILABLE)
        assertThat(failure { keychain.write(OTHER, "FS-SECRET-1") }).isEqualTo(Kind.UNAVAILABLE)

        assertThat(file.readText()).isEqualTo(before)
        key.failNext(passing, 0)
        assertThat(keychain.read(KEY)).contains("OM-SECRET-1")
        assertThat(key.made).hasValue(1)
    }

    @Test
    fun aFileThatCannotBeReadIsUnavailable() {
        Files.createDirectories(file)

        assertThat(failure { keychain.read(KEY) }).isEqualTo(Kind.UNAVAILABLE)
        assertThat(failure { keychain.write(KEY, "OM-SECRET-1") }).isEqualTo(Kind.UNAVAILABLE)
        assertThat(failure { keychain.delete(KEY) }).isEqualTo(Kind.UNAVAILABLE)
    }

    @Test
    fun aFolderThatCannotBeMadeIsUnavailable() {
        file.parent.writeText("a file where the folder should be")

        assertThat(failure { keychain.write(KEY, "OM-SECRET-1") }).isEqualTo(Kind.UNAVAILABLE)
    }

    @ParameterizedTest
    @ValueSource(strings = ["not JSON", "[]", "{\"entries\":{}}", "{\"version\":0,\"entries\":{}}",
        "{\"version\":\"1\",\"entries\":{}}", "{\"version\":1}", "{\"version\":1,\"entries\":[]}"])
    fun aFileNotInThisFormatIsRefusedAndReplacedByTheNextWrite(content: String) {
        Files.createDirectories(file.parent)
        file.writeText(content)

        assertThat(failure { keychain.read(KEY) }).isEqualTo(Kind.REFUSED)
        assertThat(failure { keychain.delete(KEY) }).isEqualTo(Kind.REFUSED)
        keychain.write(KEY, "OM-SECRET-1")

        assertThat(keychain.read(KEY)).contains("OM-SECRET-1")
    }

    @Test
    fun aFileThatIsNotUtf8IsRefusedAndReplacedByTheNextWrite() {
        Files.createDirectories(file.parent)
        Files.write(file, byteArrayOf(0x7b, 0xff.toByte(), 0xfe.toByte(), 0x7d))

        assertThat(failure { keychain.read(KEY) }).isEqualTo(Kind.REFUSED)
        keychain.write(KEY, "OM-SECRET-1")

        assertThat(keychain.read(KEY)).contains("OM-SECRET-1")
    }

    @Test
    fun aFileOfANewerVersionIsRefusedAndNeverOverwritten() {
        Files.createDirectories(file.parent)
        val newer = "{\"version\":2,\"entries\":{}}"
        file.writeText(newer)

        assertThat(failure { keychain.read(KEY) }).isEqualTo(Kind.REFUSED)
        assertThat(failure { keychain.write(KEY, "OM-SECRET-1") }).isEqualTo(Kind.REFUSED)
        assertThat(failure { keychain.delete(KEY) }).isEqualTo(Kind.REFUSED)

        assertThat(file.readText()).isEqualTo(newer)
    }

    @ParameterizedTest
    @ValueSource(strings = ["42", "\"not Base64!\"", "\"AAECAwQFBgcICQ==\"", "null"])
    fun anEntryNotInThisFormatIsRefusedAndTheOthersStayReadable(stored: String) {
        keychain.write(OTHER, "FS-SECRET-1")
        replaceEntries { it.set(KEY, StoredJson.parse(stored)) }

        assertThat(failure { keychain.read(KEY) }).isEqualTo(Kind.REFUSED)
        assertThat(keychain.read(OTHER)).contains("FS-SECRET-1")
    }

    @Test
    fun aValueUtf8CannotHoldIsRefusedAndNothingIsWritten() {
        assertThat(failure { keychain.write(KEY, "lone \uD800 surrogate") }).isEqualTo(Kind.REFUSED)

        assertThat(file).doesNotExist()
    }
}
