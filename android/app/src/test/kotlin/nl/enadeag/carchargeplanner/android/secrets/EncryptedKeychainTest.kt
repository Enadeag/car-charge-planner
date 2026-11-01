package nl.enadeag.carchargeplanner.android.secrets

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStoreException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import java.util.Base64
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
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
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import tools.jackson.databind.node.ObjectNode

/**
 * What the Android keychain does beyond the engine's contract: how it encrypts, which failures it refuses as lost for
 * good and which it reports as unavailable, as without the fallback a refused `index` starts a new one, and that it
 * never removes a key or an entry the owner did not remove.
 */
class EncryptedKeychainTest {

    private companion object {
        const val KEY = "forecast/open-meteo/api_key"
        const val OTHER = "forecast/forecast-solar/api_key"
        const val FIRST = "keychain"

        @JvmStatic
        fun passingKeyFailures(): List<Named<Exception>> = listOf(
            Named.of("a busy Keystore", KeyStoreException("the Keystore is busy")),
            Named.of("a Keystore that does not answer", ProviderException("the Keystore did not answer")),
            Named.of("a Keystore that cannot be loaded", IOException("could not load the Keystore")),
            Named.of("a key that cannot be loaded, without saying why", UnrecoverableKeyException("no key")))
    }

    @TempDir
    lateinit var folder: Path

    private val keys = SoftwareKeys()

    private val pauses = mutableListOf<Long>()

    private val file by lazy { folder.resolve("no_backup").resolve(EncryptedKeychain.FILE_NAME) }

    private val keychain by lazy { store() }

    private fun store(pause: EncryptedKeychain.Pause = EncryptedKeychain.Pause { pauses += it }) =
        EncryptedKeychain(file, PrivateFiles(FilePrivacy.NONE), keys, FIRST, pause)

    private fun failure(call: () -> Unit): Kind? =
        catchThrowableOfType(KeychainException::class.java) { call() }?.kind()

    private fun root(): ObjectNode = StoredJson.parse(file.readText()) as ObjectNode

    private fun entries(): ObjectNode = root().get("entries") as ObjectNode

    private fun aliasOf(name: String): String = entries().get(name).get("key").stringValue()

    private fun sealed(name: String): ByteArray =
        Base64.getDecoder().decode(entries().get(name).get("value").stringValue())

    private fun replaceEntries(change: (ObjectNode) -> Unit) {
        val root = root()
        change(root.get("entries") as ObjectNode)
        file.writeText(StoredJson.compact(root))
    }

    private fun badKey() = SecretKeySpec(ByteArray(5), "AES") // an InvalidKeyException at a cipher's init

    @Test
    fun theFileNamesTheKeyOfEachEntryAndHoldsTheValueOnlyAsAnIvAndCiphertextWithItsTag() {
        keychain.write(KEY, "OM-SECRET-1")

        val text = file.readText()
        assertThat(text).doesNotContain("OM-SECRET-1")
        assertThat(root().get("version").intValue()).isEqualTo(1)
        assertThat(root().get("key").stringValue()).isEqualTo(FIRST)
        assertThat(aliasOf(KEY)).isEqualTo(FIRST)
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
    fun anEmptyStoreMakesNoKeyAndNoFileUntilTheFirstWriteAndThenKeepsThatKey() {
        assertThat(keychain.read(KEY)).isEmpty
        keychain.delete(KEY)
        assertThat(keys.made).hasValue(0)
        assertThat(file).doesNotExist()

        keychain.write(KEY, "OM-SECRET-1")
        keychain.write(OTHER, "FS-SECRET-1")

        assertThat(keys.made).hasValue(1)
        assertThat(keys.keys.keys).containsExactly(FIRST)
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
        replaceEntries { (it.get(KEY) as ObjectNode).put("value", Base64.getEncoder().encodeToString(changed)) }

        assertThat(failure { keychain.read(KEY) }).isEqualTo(Kind.REFUSED)
    }

    @Test
    fun anEntryEncryptedWithAnotherKeyIsRefused() {
        keychain.write(KEY, "OM-SECRET-1")
        keys.keys[FIRST] = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

        assertThat(failure { keychain.read(KEY) }).isEqualTo(Kind.REFUSED)
    }

    @Test
    fun aKeyMissingForAMomentIsLookedForAgainAndNothingChanges() {
        keychain.write(KEY, "OM-SECRET-1")

        keys.missNext(EncryptedKeychain.ABSENCE_CHECKS - 1)
        assertThat(keychain.read(KEY)).contains("OM-SECRET-1")
        keys.missNext(EncryptedKeychain.ABSENCE_CHECKS - 1)
        keychain.write(OTHER, "FS-SECRET-1")

        assertThat(pauses).containsExactly(500L, 500L, 500L, 500L)
        assertThat(keys.made).hasValue(1)
        assertThat(aliasOf(OTHER)).isEqualTo(FIRST)
        assertThat(keychain.read(KEY)).contains("OM-SECRET-1")
    }

    @Test
    fun aKeyMissingAtEveryLookIsRefusedAndTheNextWriteMakesANewKeyAndKeepsTheOldEntries() {
        keychain.write(KEY, "OM-SECRET-1")
        keys.keys.remove(FIRST)

        assertThat(failure { keychain.read(KEY) }).isEqualTo(Kind.REFUSED)
        assertThat(pauses).`as`("three looks, half a second apart").containsExactly(500L, 500L)
        keychain.write(OTHER, "FS-SECRET-1")

        assertThat(root().get("key").stringValue()).isEqualTo("$FIRST-2")
        assertThat(aliasOf(OTHER)).isEqualTo("$FIRST-2")
        assertThat(aliasOf(KEY)).isEqualTo(FIRST)
        assertThat(keychain.read(OTHER)).contains("FS-SECRET-1")
        assertThat(failure { keychain.read(KEY) }).isEqualTo(Kind.REFUSED)
    }

    @Test
    fun aKeyThatComesBackAfterANewOneWasMadeStillOpensItsOldEntries() {
        keychain.write(KEY, "OM-SECRET-1")
        val first = keys.keys.remove(FIRST)!!
        keychain.write(OTHER, "FS-SECRET-1")

        keys.keys[FIRST] = first

        assertThat(keychain.read(KEY)).contains("OM-SECRET-1")
        assertThat(keychain.read(OTHER)).contains("FS-SECRET-1")
    }

    @Test
    fun aNewKeyTakesTheNextAliasThatHoldsNoKey() {
        keychain.write(KEY, "OM-SECRET-1")
        keys.keys.remove(FIRST)
        val someoneElses = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        keys.keys["$FIRST-2"] = someoneElses

        keychain.write(OTHER, "FS-SECRET-1")
        keys.keys.remove("$FIRST-3")
        keychain.write("index", "[]")

        assertThat(aliasOf(OTHER)).isEqualTo("$FIRST-3")
        assertThat(aliasOf("index")).isEqualTo("$FIRST-4")
        assertThat(keys.keys["$FIRST-2"]).isSameAs(someoneElses)
    }

    @Test
    fun aDeleteKeepsTheNewKeyInUse() {
        keychain.write(KEY, "OM-SECRET-1")
        keys.keys.remove(FIRST)
        keychain.write(OTHER, "FS-SECRET-1")

        keychain.delete(OTHER)
        keychain.write("index", "[]")

        assertThat(root().get("key").stringValue()).isEqualTo("$FIRST-2")
        assertThat(aliasOf("index")).isEqualTo("$FIRST-2")
    }

    @Test
    fun aNewKeyPassesOverAnAliasAnEntryNamesThoughItsKeyIsGone() {
        keychain.write(KEY, "OM-SECRET-1")
        keys.keys.remove(FIRST)
        keychain.write(OTHER, "FS-SECRET-1")
        keys.keys.remove("$FIRST-2")
        val root = root().put("key", FIRST)
        file.writeText(StoredJson.compact(root))

        keychain.write("index", "[]")

        assertThat(aliasOf("index")).isEqualTo("$FIRST-3")
    }

    @Test
    fun aNewKeyPassesOverAnAliasWhoseKeyIsLostForGood() {
        keychain.write(KEY, "OM-SECRET-1")
        keys.keys.remove(FIRST)
        keys.lostForGood = { it is UnrecoverableKeyException }
        keys.fail("$FIRST-2", UnrecoverableKeyException("the key's data is corrupted"))

        keychain.write(OTHER, "FS-SECRET-1")

        assertThat(aliasOf(OTHER)).isEqualTo("$FIRST-3")
    }

    @Test
    fun whenNoAliasIsFreeANewKeyIsUnavailableAndNothingChanges() {
        keychain.write(KEY, "OM-SECRET-1")
        val before = file.readText()
        keys.lostForGood = { it is UnrecoverableKeyException }
        for (number in 1..EncryptedKeychain.MAX_ALIASES + 1) {
            keys.fail(if (number == 1) FIRST else "$FIRST-$number", UnrecoverableKeyException("corrupted"))
        }

        assertThat(failure { keychain.write(OTHER, "FS-SECRET-1") }).isEqualTo(Kind.UNAVAILABLE)

        assertThat(file.readText()).isEqualTo(before)
    }

    @Test
    fun aKeyLostForGoodIsRefusedAndTheNextWriteMakesANewKeyAndKeepsTheOldEntries() {
        keychain.write(KEY, "OM-SECRET-1")
        keys.lostForGood = { it is UnrecoverableKeyException }
        keys.fail(FIRST, UnrecoverableKeyException("the key's data is corrupted"))

        assertThat(failure { keychain.read(KEY) }).isEqualTo(Kind.REFUSED)
        keychain.write(OTHER, "FS-SECRET-1")

        assertThat(pauses).isEmpty()
        assertThat(aliasOf(OTHER)).isEqualTo("$FIRST-2")
        assertThat(aliasOf(KEY)).isEqualTo(FIRST)
        assertThat(keychain.read(OTHER)).contains("FS-SECRET-1")
        assertThat(failure { keychain.read(KEY) }).isEqualTo(Kind.REFUSED)
    }

    @Test
    fun aKeyTheCipherRejectsForGoodIsRefusedAndTheNextWriteMakesANewKeyAndKeepsTheOldEntries() {
        keychain.write(KEY, "OM-SECRET-1")
        keys.keys[FIRST] = badKey()
        keys.lostForGood = { true }

        assertThat(failure { keychain.read(KEY) }).isEqualTo(Kind.REFUSED)
        keychain.write(OTHER, "FS-SECRET-1")

        assertThat(aliasOf(OTHER)).isEqualTo("$FIRST-2")
        assertThat(entries().has(KEY)).isTrue()
        assertThat(keychain.read(OTHER)).contains("FS-SECRET-1")
    }

    @Test
    fun aKeyTheCipherRejectsForNowIsUnavailableAndChangesNothing() {
        keychain.write(KEY, "OM-SECRET-1")
        val good = keys.key(FIRST)
        val before = file.readText()
        keys.keys[FIRST] = badKey()

        assertThat(failure { keychain.read(KEY) }).isEqualTo(Kind.UNAVAILABLE)
        assertThat(failure { keychain.write(OTHER, "FS-SECRET-1") }).isEqualTo(Kind.UNAVAILABLE)

        assertThat(file.readText()).isEqualTo(before)
        keys.keys[FIRST] = good
        assertThat(keychain.read(KEY)).contains("OM-SECRET-1")
    }

    @ParameterizedTest
    @MethodSource("passingKeyFailures")
    fun aKeyThatCannotBeGotForNowIsUnavailableAndChangesNothing(passing: Exception) {
        keychain.write(KEY, "OM-SECRET-1")
        val before = file.readText()
        keys.failNext(passing)

        assertThat(failure { keychain.read(KEY) }).isEqualTo(Kind.UNAVAILABLE)
        assertThat(failure { keychain.write(OTHER, "FS-SECRET-1") }).isEqualTo(Kind.UNAVAILABLE)

        assertThat(file.readText()).isEqualTo(before)
        keys.stopFailing()
        assertThat(keychain.read(KEY)).contains("OM-SECRET-1")
        assertThat(keys.made).hasValue(1)
    }

    @Test
    fun anInterruptWhileLookingAgainForAKeyIsUnavailableAndKeepsTheThreadInterrupted() {
        val interrupted = store { throw InterruptedException() }
        interrupted.write(KEY, "OM-SECRET-1")
        keys.missNext(1)

        val kind = failure { interrupted.read(KEY) }

        assertThat(Thread.interrupted()).isTrue()
        assertThat(kind).isEqualTo(Kind.UNAVAILABLE)
    }

    @Test
    fun aKeyThatCannotBeMadeIsUnavailableAndNothingIsWritten() {
        keys.makeFailure = KeyStoreException("the Keystore is busy")

        assertThat(failure { keychain.write(KEY, "OM-SECRET-1") }).isEqualTo(Kind.UNAVAILABLE)

        assertThat(file).doesNotExist()
    }

    @Test
    fun aNewKeyThatCannotEncryptIsUnavailableAndChangesNothing() {
        keychain.write(KEY, "OM-SECRET-1")
        val before = file.readText()
        keys.keys[FIRST] = badKey()
        keys.lostForGood = { true }
        keys.nextMade = badKey()

        assertThat(failure { keychain.write(OTHER, "FS-SECRET-1") }).isEqualTo(Kind.UNAVAILABLE)

        assertThat(file.readText()).isEqualTo(before)
    }

    @Test
    fun aFileThatCannotBeReadIsUnavailable() {
        Files.createDirectories(file)

        assertThat(failure { keychain.read(KEY) }).isEqualTo(Kind.UNAVAILABLE)
        assertThat(failure { keychain.write(KEY, "OM-SECRET-1") }).isEqualTo(Kind.UNAVAILABLE)
        assertThat(failure { keychain.delete(KEY) }).isEqualTo(Kind.UNAVAILABLE)
    }

    @Test
    fun aFileThatCanBeReadButNotWrittenIsUnavailableAndStaysAsItWas() {
        keychain.write(KEY, "OM-SECRET-1")
        val before = file.readText()
        Files.createDirectories(file.resolveSibling("${EncryptedKeychain.FILE_NAME}.tmp"))

        assertThat(failure { keychain.write(OTHER, "FS-SECRET-1") }).isEqualTo(Kind.UNAVAILABLE)
        assertThat(failure { keychain.delete(KEY) }).isEqualTo(Kind.UNAVAILABLE)

        assertThat(file.readText()).isEqualTo(before)
    }

    @Test
    fun aFolderThatCannotBeMadeIsUnavailable() {
        file.parent.writeText("a file where the folder should be")

        assertThat(failure { keychain.write(KEY, "OM-SECRET-1") }).isEqualTo(Kind.UNAVAILABLE)
    }

    @ParameterizedTest
    @ValueSource(strings = ["not JSON", "[]", "{\"key\":\"keychain\",\"entries\":{}}",
        "{\"version\":0,\"key\":\"keychain\",\"entries\":{}}", "{\"version\":1.5,\"key\":\"keychain\",\"entries\":{}}",
        "{\"version\":\"1\",\"key\":\"keychain\",\"entries\":{}}", "{\"version\":1,\"entries\":{}}",
        "{\"version\":1,\"key\":2,\"entries\":{}}", "{\"version\":1,\"key\":\"\",\"entries\":{}}",
        "{\"version\":1,\"key\":\"keychain\"}", "{\"version\":1,\"key\":\"keychain\",\"entries\":[]}"])
    fun aFileNotInThisFormatIsRefusedAndReplacedByTheNextWrite(content: String) {
        Files.createDirectories(file.parent)
        file.writeText(content)

        assertThat(failure { keychain.read(KEY) }).isEqualTo(Kind.REFUSED)
        assertThat(failure { keychain.delete(KEY) }).isEqualTo(Kind.REFUSED)
        keychain.write(KEY, "OM-SECRET-1")

        assertThat(keychain.read(KEY)).contains("OM-SECRET-1")
    }

    /** A version is written as a whole number, as the engine's files have it; `1.0` is not one. */
    @ParameterizedTest
    @ValueSource(doubles = [1.5, 1.0])
    fun aFileWhoseVersionIsNotAWholeNumberIsNotReadAsVersion1(version: Double) {
        keychain.write(KEY, "OM-SECRET-1")
        file.writeText(StoredJson.compact(root().put("version", version)))

        assertThat(failure { keychain.read(KEY) }).isEqualTo(Kind.REFUSED)
    }

    @ParameterizedTest
    @ValueSource(strings = ["other", "keychain-1", "keychain-0", "keychain-02", "keychain-x", "keychain-", "keychain2"])
    fun aFileWhoseKeyIsNotOfThisStoresFormIsRefused(alias: String) {
        keychain.write(KEY, "OM-SECRET-1")
        file.writeText(StoredJson.compact(root().put("key", alias)))

        assertThat(failure { keychain.read(KEY) }).isEqualTo(Kind.REFUSED)
    }

    @ParameterizedTest
    @ValueSource(strings = ["other", "keychain-1", "keychain-x"])
    fun anEntryWhoseKeyIsNotOfThisStoresFormIsRefusedAndTheOthersStayReadable(alias: String) {
        keychain.write(KEY, "OM-SECRET-1")
        keychain.write(OTHER, "FS-SECRET-1")
        replaceEntries { (it.get(KEY) as ObjectNode).put("key", alias) }

        assertThat(failure { keychain.read(KEY) }).isEqualTo(Kind.REFUSED)
        assertThat(keychain.read(OTHER)).contains("FS-SECRET-1")
    }

    @Test
    fun deletingANameThatIsNotThereLeavesTheFileAlone() {
        keychain.write(KEY, "OM-SECRET-1")
        val before = Files.getAttribute(file, "fileKey")
        val modified = Files.getLastModifiedTime(file)

        keychain.delete(OTHER)

        assertThat(Files.getAttribute(file, "fileKey")).isEqualTo(before)
        assertThat(Files.getLastModifiedTime(file)).isEqualTo(modified)
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
        val newer = "{\"version\":2,\"key\":\"keychain\",\"entries\":{}}"
        file.writeText(newer)

        assertThat(failure { keychain.read(KEY) }).isEqualTo(Kind.REFUSED)
        assertThat(failure { keychain.write(KEY, "OM-SECRET-1") }).isEqualTo(Kind.REFUSED)
        assertThat(failure { keychain.delete(KEY) }).isEqualTo(Kind.REFUSED)

        assertThat(file.readText()).isEqualTo(newer)
    }

    @ParameterizedTest
    @ValueSource(strings = ["42", "null", "\"a value without its key\"",
        "{\"value\":\"AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGw==\"}",
        "{\"key\":\"keychain\"}", "{\"key\":\"keychain\",\"value\":\"not Base64!\"}",
        "{\"key\":\"keychain\",\"value\":\"AAECAwQFBgcICQ==\"}"])
    fun anEntryNotInThisFormatIsRefusedAndTheOthersStayReadable(stored: String) {
        keychain.write(OTHER, "FS-SECRET-1")
        replaceEntries { it.set(KEY, StoredJson.parse(stored)) }

        assertThat(failure { keychain.read(KEY) }).isEqualTo(Kind.REFUSED)
        assertThat(keychain.read(OTHER)).contains("FS-SECRET-1")
    }

    @Test
    fun anEntryThatDoesNotDecryptToUtf8IsRefused() {
        keychain.write(OTHER, "FS-SECRET-1")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, keys.key(FIRST))
            updateAAD(KEY.toByteArray())
        }
        val sealed = cipher.doFinal(byteArrayOf(0xff.toByte(), 0xfe.toByte()))
        val value = Base64.getEncoder().encodeToString(cipher.iv + sealed)
        replaceEntries { it.set(KEY, StoredJson.`object`().put("key", FIRST).put("value", value)) }

        assertThat(failure { keychain.read(KEY) }).isEqualTo(Kind.REFUSED)
    }

    @Test
    fun aValueUtf8CannotHoldIsRefusedAndNothingIsWritten() {
        assertThat(failure { keychain.write(KEY, "lone \uD800 surrogate") }).isEqualTo(Kind.REFUSED)

        assertThat(file).doesNotExist()
    }

    @Test
    fun twoStoresOnOneFileWritingAtOnceKeepEveryEntry() {
        writeAtOnceThrough(store(), store())
    }

    @Test
    fun twoStoresOnOneFileNamedInTwoWaysShareTheirLock() {
        val roundabout = folder.resolve("no_backup").resolve("..").resolve("no_backup")
            .resolve(EncryptedKeychain.FILE_NAME)
        writeAtOnceThrough(store(), EncryptedKeychain(roundabout, PrivateFiles(FilePrivacy.NONE), keys, FIRST))
    }

    private fun writeAtOnceThrough(vararg stores: EncryptedKeychain) {
        val names = (0 until 16).map { "forecast/thread-$it/api_key" }
        val threads = Executors.newFixedThreadPool(8)
        try {
            val done = names.mapIndexed { i, name ->
                threads.submit(Callable { stores[i % 2].write(name, "value-$i") })
            }
            done.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            threads.shutdownNow()
        }

        assertThat(entries().propertyNames()).containsExactlyInAnyOrderElementsOf(names)
        assertThat(keys.made).hasValue(1)
    }
}
