package nl.enadeag.carchargeplanner.android

import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneId
import kotlin.io.path.copyTo
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import nl.enadeag.carchargeplanner.android.secrets.SoftwareKeys
import nl.enadeag.carchargeplanner.android.store.EngineOnDispatcher
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.engine.config.ConfigFile
import nl.enadeag.carchargeplanner.engine.config.Coordinates
import nl.enadeag.carchargeplanner.engine.config.ProviderId
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
        AndroidPlatform(filesFolder.toFile(), root.resolve("no_backup").toFile(), SoftwareKeys())
    }

    @Test
    fun theAppOffersTheDesktopsProvidersInItsOrder() {
        val engine = AppEngine.build(platform, QuietNotifier)

        assertThat(engine.providers().map { it.id() })
            .containsExactly(ProviderId("open-meteo"), ProviderId("forecast-solar"))
    }

    @Test
    fun theDocsExampleSettingsFileIsReadThroughTheEngineFromTheFilesFolder() = runTest {
        Files.createDirectories(filesFolder)
        EXAMPLE.copyTo(filesFolder.resolve("engine.json"))
        val access = EngineOnDispatcher(AppEngine.build(platform, QuietNotifier),
            StandardTestDispatcher(testScheduler))

        val state = access.readSettings()

        assertThat(state).isInstanceOfSatisfying(StartState.Ready::class.java) { ready ->
            assertThat(ready.config.system().location()).isEqualTo(Coordinates(51.0, 5.0))
            assertThat(ready.config.system().timezone()).isEqualTo(ZoneId.of("Europe/Brussels"))
            assertThat(ready.config).isEqualTo(ConfigFile(EXAMPLE, PrivateFiles(FilePrivacy.NONE)).read())
        }
    }

    @Test
    fun theSettingsAreWrittenToEngineJsonInTheFilesFolderInTheDesktopsFormat() = runTest {
        val example = ConfigFile(EXAMPLE, PrivateFiles(FilePrivacy.NONE)).read()
        val access = EngineOnDispatcher(AppEngine.build(platform, QuietNotifier),
            StandardTestDispatcher(testScheduler))

        access.saveSettings(example)

        val file = filesFolder.resolve("engine.json")
        assertThat(ConfigFile(file, PrivateFiles(FilePrivacy.NONE)).read()).isEqualTo(example)
        assertThat(Files.list(filesFolder).use { it.toList() }).containsExactly(file)
    }
}
