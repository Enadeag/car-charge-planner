package nl.enadeag.carchargeplanner.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.file.Files
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import nl.enadeag.carchargeplanner.android.store.EngineOnDispatcher
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.engine.config.Coordinates
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The docs' example settings file read through the engine on the device, in a folder of its own rather than the app's
 * files folder: Jackson, java.time and java.nio.file on Android 10 as the app uses them. The test APK carries the file
 * as an asset (app/build.gradle.kts).
 */
@RunWith(AndroidJUnit4::class)
class ExampleSettingsOnTheDeviceTest {

    private lateinit var folder: File

    @Before
    fun makeTheFolder() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        folder = Files.createTempDirectory(app.cacheDir.toPath(), "example-settings").toFile()
    }

    @After
    fun removeTheFolder() {
        folder.deleteRecursively()
    }

    @Test
    fun theDocsExampleIsReadThroughTheEngine() = runBlocking {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        assets.open("example-engine.json").use { input ->
            File(folder, AppEngine.SETTINGS_FILE).outputStream().use { input.copyTo(it) }
        }
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val platform = AndroidPlatform.of(app)
        val engine = AppEngine.build(folder.toPath(), platform.http(), platform.files(), platform.apiKeys(),
            QuietNotifier)

        val state = EngineOnDispatcher(engine, Dispatchers.IO).readSettings()

        assertTrue("the example is ready, not $state", state is StartState.Ready)
        val system = (state as StartState.Ready).config.system()
        assertEquals(Coordinates(51.0, 5.0), system.location())
        assertEquals(ZoneId.of("Europe/Brussels"), system.timezone())
    }
}
