package nl.enadeag.carchargeplanner.android

import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.ZoneId
import kotlin.io.path.copyTo
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import nl.enadeag.carchargeplanner.android.secrets.AndroidKeystoreKeys
import nl.enadeag.carchargeplanner.android.secrets.SoftwareKeys
import nl.enadeag.carchargeplanner.android.store.EngineOnDispatcher
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.android.store.StoredKeys
import nl.enadeag.carchargeplanner.engine.ForecastUse
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.config.ConfigFile
import nl.enadeag.carchargeplanner.engine.config.Coordinates
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet
import nl.enadeag.carchargeplanner.engine.notify.Notifier
import nl.enadeag.carchargeplanner.engine.secrets.KeyChanges
import nl.enadeag.carchargeplanner.engine.secrets.Secrets
import nl.enadeag.carchargeplanner.engine.store.FilePrivacy
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** The engine as the app builds it, on the app's platform parts with a key in memory for the Keystore's. */
class AppEngineTest {

    private companion object {
        /** The docs' example settings file, a declared input of these tests (app/build.gradle.kts). */
        val EXAMPLE: Path = Path.of(requireNotNull(System.getProperty("example.config")))
    }

    @TempDir
    lateinit var root: Path

    private val filesFolder by lazy { root.resolve("files") }
    private val platform by lazy {
        AndroidPlatform(filesFolder.toFile(), root.resolve("no_backup").toFile(), SoftwareKeys(),
            Clock.systemUTC())
    }

    @Test
    fun theAppOffersTheDesktopsProvidersInItsOrder() {
        val engine = AppEngine.build(platform, Notifier { }, Clock.systemUTC())

        assertThat(engine.providers().map { it.id() })
            .containsExactly(ProviderId("open-meteo"), ProviderId("forecast-solar"))
    }

    @Test
    fun theDocsExampleSettingsFileIsReadThroughTheEngineFromTheFilesFolder() = runTest {
        Files.createDirectories(filesFolder)
        EXAMPLE.copyTo(filesFolder.resolve("engine.json"))
        val access = EngineOnDispatcher(AppEngine.build(platform, Notifier { }, Clock.systemUTC()),
            StandardTestDispatcher(testScheduler))

        val state = access.readSettings()

        assertThat(state).isInstanceOfSatisfying(StartState.Ready::class.java) { ready ->
            assertThat(ready.config.system().location()).isEqualTo(Coordinates(51.0, 5.0))
            assertThat(ready.config.system().timezone()).isEqualTo(ZoneId.of("Europe/Brussels"))
            assertThat(ready.config)
                .isEqualTo(ConfigFile(EXAMPLE, PrivateFiles(FilePrivacy.NONE), Clock.systemUTC()).read())
        }
    }

    @Test
    fun theSettingsAreWrittenToEngineJsonInTheFilesFolderInTheDesktopsFormat() = runTest {
        val example = ConfigFile(EXAMPLE, PrivateFiles(FilePrivacy.NONE), Clock.systemUTC()).read()
        val access = EngineOnDispatcher(AppEngine.build(platform, Notifier { }, Clock.systemUTC()),
            StandardTestDispatcher(testScheduler))

        access.saveSettings(example)

        val file = filesFolder.resolve("engine.json")
        assertThat(ConfigFile(file, PrivateFiles(FilePrivacy.NONE), Clock.systemUTC()).read()).isEqualTo(example)
        assertThat(Files.list(filesFolder).use { it.toList() }).containsExactly(file)
    }

    /**
     * The next phone after a transfer (#397): the engine's files came along, the keychain's file and its Keystore key
     * did not. The app opens on the settings, not on set-up; the API keys count as none, not as unreadable, so
     * Settings shows empty fields to type them in; a provider that needs its key refuses the request, which the
     * dashboard names; and a key typed again is kept. Nothing here depends on the Android version, so it holds on
     * Android 10 as on 16.
     */
    @Test
    fun afterATransferTheSettingsAreReadyTheKeysCountAsNoneARefusalNamesTheProviderAndAKeyTypedAgainIsKept() = runTest {
        Files.createDirectories(filesFolder)
        EXAMPLE.copyTo(filesFolder.resolve("engine.json"))
        val keystore = SoftwareKeys()
        val newPhone = AndroidPlatform(filesFolder.toFile(), root.resolve("no_backup").toFile(), keystore,
            Clock.systemUTC())
        // as a paid plan's server answers a request without its API key
        val refusing = HttpGet { HttpGet.Reply(401, "") }
        val access = EngineOnDispatcher(AppEngine.build(filesFolder, refusing, newPhone.files(), newPhone.apiKeys(),
            Notifier { }, Clock.systemUTC()), StandardTestDispatcher(testScheduler))

        assertThat(access.readSettings()).isInstanceOf(StartState.Ready::class.java)
        assertThat(access.storedKeys()).isEqualTo(StoredKeys.Read(Secrets.NONE))
        assertThat(access.round(ForecastUse.ASK_PROVIDERS)).isInstanceOfSatisfying(Outlook.NoForecast::class.java) {
            assertThat(it.keyRefusedBy()).contains(ProviderId("open-meteo"))
        }

        val typedAgain = Secrets(mapOf(ProviderId("open-meteo") to mapOf("api_key" to "MADE-UP-OM-KEY")))
        access.applyKeyChanges(KeyChanges.entering(typedAgain))

        assertThat(access.storedKeys()).isEqualTo(StoredKeys.Read(typedAgain))
        assertThat(keystore.keys).containsOnlyKeys(AndroidKeystoreKeys.ALIAS)
    }
}
