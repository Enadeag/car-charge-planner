package nl.enadeag.carchargeplanner.android

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStoreException
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import nl.enadeag.carchargeplanner.android.secrets.AndroidKeystoreKeys
import nl.enadeag.carchargeplanner.android.secrets.SoftwareKeys
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.secrets.KeyChanges
import nl.enadeag.carchargeplanner.engine.secrets.KeychainException
import nl.enadeag.carchargeplanner.engine.secrets.Secrets
import nl.enadeag.carchargeplanner.engine.secrets.SecretsProblem
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowableOfType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** The parts the app hands the engine, made from the app's folders, with a key in memory for the Keystore's. */
class AndroidPlatformTest {

    private companion object {
        val OPEN_METEO = ProviderId("open-meteo")
        val FORECAST_SOLAR = ProviderId("forecast-solar")
    }

    @TempDir
    lateinit var root: Path

    private val filesFolder by lazy { root.resolve("files") }
    private val noBackupFolder by lazy { root.resolve("no_backup") }
    private val key = SoftwareKeys()
    private val platform by lazy { AndroidPlatform(filesFolder.toFile(), noBackupFolder.toFile(), key) }

    private fun keys(provider: ProviderId, value: String) = Secrets(mapOf(provider to mapOf("api_key" to value)))

    private fun everyFile(): List<Path> = Files.walk(root).use { paths -> paths.filter(Files::isRegularFile).toList() }

    @Test
    fun theApiKeysGoEncryptedIntoTheKeychainFileInTheFolderThatIsNeverBackedUp() {
        platform.apiKeys().save(keys(OPEN_METEO, "MADE-UP-OM-KEY"))

        assertThat(everyFile()).containsExactly(noBackupFolder.resolve("keychain.json"))
        assertThat(platform.keychainFile()).isEqualTo(noBackupFolder.resolve("keychain.json"))
        assertThat(noBackupFolder.resolve("keychain.json").readText()).doesNotContain("MADE-UP-OM-KEY")
        assertThat(platform.apiKeys().load()).isEqualTo(keys(OPEN_METEO, "MADE-UP-OM-KEY"))
    }

    @Test
    fun aKeychainThatCannotBeUsedLeavesTheKeysUnsavedRatherThanInAFile() {
        key.failNext(KeyStoreException("the Keystore is busy"))

        val problem = catchThrowableOfType(SecretsProblem::class.java) {
            platform.apiKeys().save(keys(OPEN_METEO, "MADE-UP-OM-KEY"))
        }

        assertThat(problem.kind()).isEqualTo(SecretsProblem.Kind.FAILED)
        assertThat(everyFile()).isEmpty()
    }

    /** Were a passing failure REFUSED, the save would start a new index and lose the stored key (decision of #273). */
    @Test
    fun aKeystoreFailureThatPassesLosesNoStoredKey() {
        platform.apiKeys().save(keys(OPEN_METEO, "MADE-UP-OM-KEY"))
        key.failNext(KeyStoreException("the Keystore is busy"), times = 1)

        val problem = catchThrowableOfType(SecretsProblem::class.java) {
            platform.apiKeys().applyChanges(KeyChanges.entering(keys(FORECAST_SOLAR, "MADE-UP-FS-KEY"))) { }
        }
        platform.apiKeys().applyChanges(KeyChanges.entering(keys(FORECAST_SOLAR, "MADE-UP-FS-KEY"))) { }

        assertThat(problem.kind()).isEqualTo(SecretsProblem.Kind.FAILED)
        assertThat(platform.apiKeys().load().byProvider()).isEqualTo(mapOf(
            OPEN_METEO to mapOf("api_key" to "MADE-UP-OM-KEY"), FORECAST_SOLAR to mapOf("api_key" to "MADE-UP-FS-KEY")))
    }

    @Test
    fun theEngineFilesGoInTheFilesFolderWithoutPrivacyOfTheirOwnAndTheNetworkThroughTheAppsHttpGet() {
        platform.files().write(platform.dataFolder().resolve("trips.json"), "{}")

        assertThat(filesFolder.listDirectoryEntries()).containsExactly(filesFolder.resolve("trips.json"))
        assertThat(platform.http()).isInstanceOf(AndroidHttpGet::class.java)
    }

    /** The real Keystore key on a JVM, which has no Android Keystore: a failure that may pass, never a refusal. */
    @Test
    fun withoutAnAndroidKeystoreTheKeychainIsUnavailable() {
        val onTheJvm =
            AndroidPlatform(File(root.toFile(), "files"), File(root.toFile(), "no_backup"), AndroidKeystoreKeys())

        val failure = catchThrowableOfType(KeychainException::class.java) {
            onTheJvm.keychain().write("forecast/open-meteo/api_key", "MADE-UP-OM-KEY")
        }

        assertThat(failure.kind()).isEqualTo(KeychainException.Kind.UNAVAILABLE)
        assertThat(everyFile()).isEmpty()
    }
}
