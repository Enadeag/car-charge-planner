package nl.enadeag.carchargeplanner.android

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStoreException
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import nl.enadeag.carchargeplanner.android.secrets.SoftwareKeys
import nl.enadeag.carchargeplanner.android.store.EngineOnDispatcher
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.android.store.StoredKeys
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.secrets.KeyChanges
import nl.enadeag.carchargeplanner.engine.secrets.Secrets
import nl.enadeag.carchargeplanner.engine.store.StoredJson
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** The app's engine behind its suspend interface, on its platform parts with a key in memory for the Keystore's. */
class EngineOnDispatcherTest {

    /** The engine's own thread for these tests, which counts the calls handed to it. */
    private class CountingDispatcher : CoroutineDispatcher() {
        val handed = AtomicInteger()
        val threads: MutableSet<String> = ConcurrentHashMap.newKeySet()
        private val thread = Executors.newSingleThreadExecutor { Thread(it, "engine-io") }
        private val inner = thread.asCoroutineDispatcher()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            handed.incrementAndGet()
            inner.dispatch(context) {
                threads += Thread.currentThread().name
                block.run()
            }
        }

        fun close() = thread.shutdownNow()
    }

    @TempDir
    lateinit var root: Path

    private val keys = SoftwareKeys()
    private val dispatcher = CountingDispatcher()
    private val filesFolder by lazy { root.resolve("files") }
    private val access by lazy {
        val platform = AndroidPlatform(filesFolder.toFile(), root.resolve("no_backup").toFile(), keys)
        EngineOnDispatcher(AppEngine.build(platform, QuietNotifier), dispatcher)
    }
    private val draft = EngineConfig.setUpDraft(ZoneId.of("Europe/Amsterdam"), ProviderId("open-meteo"))

    @AfterEach
    fun stopTheThread() {
        dispatcher.close()
    }

    private fun settingsFile(text: String): Path {
        Files.createDirectories(filesFolder)
        return filesFolder.resolve("engine.json").also { it.writeText(text) }
    }

    @Test
    fun everyReadAndWriteRunsOnTheDispatcherItIsGiven() = runBlocking {
        access.readSettings()
        access.saveSettings(draft)
        access.storedKeys()
        access.applyKeyChanges(KeyChanges.NONE)
        access.searchPlaces(ProviderId("unknown"), "Utrecht", "nl")
        access.setSettingsAside()

        assertThat(dispatcher.handed.get()).isGreaterThanOrEqualTo(6)
        assertThat(dispatcher.threads).containsExactly("engine-io")
    }

    @Test
    fun withoutASettingsFileTheAppIsNotSetUp() = runBlocking {
        assertThat(access.readSettings()).isEqualTo(StartState.NotSetUp)
    }

    @Test
    fun aSettingsFileThatSaysNothingUsableIsUnusableWithItsNameAndTheEnglishDetails() = runBlocking {
        settingsFile("{\"version\": 1, \"system\": ")

        assertThat(access.readSettings()).isInstanceOfSatisfying(StartState.Unusable::class.java) { unusable ->
            assertThat(unusable.fileName).isEqualTo("engine.json")
            assertThat(unusable.details).startsWith("not valid JSON (line 1")
        }
    }

    @Test
    fun aSettingsFileOfANewerVersionAsksForAnUpdateAndIsLeftAsItIs() = runBlocking {
        val text = StoredJson.pretty(StoredJson.`object`().put("version", 99))
        val file = settingsFile(text)

        assertThat(access.readSettings()).isInstanceOfSatisfying(StartState.NewerVersion::class.java) { newer ->
            assertThat(newer.details).contains("written by a newer version of the app")
        }
        assertThat(file.readText()).isEqualTo(text)
    }

    @Test
    fun savedSettingsAreReadBackReady() = runBlocking {
        access.saveSettings(draft)

        assertThat(access.readSettings()).isEqualTo(StartState.Ready(draft))
    }

    @Test
    fun aSettingsFileSetAsideIsKeptBesideItAndTheAppIsNotSetUpThen() = runBlocking {
        settingsFile("damaged")

        val aside = access.setSettingsAside()

        assertThat(aside.parent).isEqualTo(filesFolder)
        assertThat(aside.fileName.toString()).startsWith("engine.json.damaged-")
        assertThat(aside.readText()).isEqualTo("damaged")
        assertThat(access.readSettings()).isEqualTo(StartState.NotSetUp)
    }

    @Test
    fun keysThatTheKeychainCannotGiveAreUnreadable() = runBlocking {
        keys.failNext(KeyStoreException("the Keystore is busy"))

        assertThat(access.storedKeys()).isEqualTo(StoredKeys.Unreadable)
    }

    @Test
    fun keysAppliedAreReadBack() = runBlocking {
        val key = Secrets(mapOf(ProviderId("open-meteo") to mapOf("api_key" to "MADEUPKEY")))

        access.applyKeyChanges(KeyChanges.entering(key))

        assertThat(access.storedKeys()).isEqualTo(StoredKeys.Read(key))
    }
}
