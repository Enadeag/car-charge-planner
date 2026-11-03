package nl.enadeag.carchargeplanner.android

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStoreException
import java.time.Instant
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
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod
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
    fun everyReadAndWriteRunsOnTheDispatcherItIsGiven() = runBlocking<Unit> {
        access.readSettings()
        access.saveSettings(draft)
        access.storedKeys()
        access.applyKeyChanges(KeyChanges.NONE)
        access.searchPlaces(ProviderId("unknown"), "Utrecht", "nl")
        access.setSettingsAside()
        access.outlook()
        access.awayPeriods()
        access.checkConnection(ProviderId("unknown"), draft.system(), emptyMap())

        assertThat(dispatcher.handed.get()).isEqualTo(9)
        assertThat(dispatcher.threads).containsExactly("engine-io")
    }

    @Test
    fun withoutASettingsFileTheAppIsNotSetUp() = runBlocking<Unit> {
        assertThat(access.readSettings()).isEqualTo(StartState.NotSetUp)
    }

    @Test
    fun aSettingsFileThatSaysNothingUsableIsUnusableWithItsNameAndTheEnglishDetails() = runBlocking<Unit> {
        settingsFile("{\"version\": 1, \"system\": ")

        assertThat(access.readSettings()).isInstanceOfSatisfying(StartState.Unusable::class.java) { unusable ->
            assertThat(unusable.fileName).isEqualTo("engine.json")
            assertThat(unusable.details).startsWith("not valid JSON (line 1")
        }
    }

    @Test
    fun aSettingsFileOfANewerVersionAsksForAnUpdateAndIsLeftAsItIs() = runBlocking<Unit> {
        val text = StoredJson.pretty(StoredJson.`object`().put("version", 99))
        val file = settingsFile(text)

        assertThat(access.readSettings()).isInstanceOfSatisfying(StartState.NewerVersion::class.java) { newer ->
            assertThat(newer.details).contains("written by a newer version of the app")
        }
        assertThat(file.readText()).isEqualTo(text)
    }

    @Test
    fun savedSettingsAreReadBackReady() = runBlocking<Unit> {
        access.saveSettings(draft)

        assertThat(access.readSettings()).isEqualTo(StartState.Ready(draft))
    }

    @Test
    fun aSettingsFileSetAsideIsKeptBesideItAndTheAppIsNotSetUpThen() = runBlocking<Unit> {
        settingsFile("damaged")

        val aside = access.setSettingsAside()

        assertThat(aside.parent).isEqualTo(filesFolder)
        assertThat(aside.fileName.toString()).startsWith("engine.json.damaged-")
        assertThat(aside.readText()).isEqualTo("damaged")
        assertThat(access.readSettings()).isEqualTo(StartState.NotSetUp)
    }

    @Test
    fun withoutASettingsFileThereIsNoPlanAndNothingIsAsked() = runBlocking<Unit> {
        assertThat(access.outlook()).isInstanceOf(Outlook.NotSetUp::class.java)
    }

    @Test
    fun theOneOffAwayPeriodsAreReadFromTheFilesFolderInOrder() = runBlocking<Unit> {
        Files.createDirectories(filesFolder)
        filesFolder.resolve("away.json").writeText("""{"version": 1, "periods": [
            {"start": "2026-06-03T08:00:00Z", "end": "2026-06-03T16:00:00Z"},
            {"start": "2026-06-01T08:00:00Z", "end": "2026-06-01T10:00:00Z"}]}""")

        assertThat(access.awayPeriods()).containsExactly(
            OneOffAwayPeriod(Instant.parse("2026-06-01T08:00:00Z"), Instant.parse("2026-06-01T10:00:00Z")),
            OneOffAwayPeriod(Instant.parse("2026-06-03T08:00:00Z"), Instant.parse("2026-06-03T16:00:00Z")))
    }

    @Test
    fun keysThatTheKeychainCannotGiveAreUnreadable() = runBlocking<Unit> {
        val key = Secrets(mapOf(ProviderId("open-meteo") to mapOf("api_key" to "MADEUPKEY")))
        access.applyKeyChanges(KeyChanges.entering(key))
        keys.failNext(KeyStoreException("the Keystore is busy"))
        val platform = AndroidPlatform(filesFolder.toFile(), root.resolve("no_backup").toFile(), keys)
        val later = EngineOnDispatcher(AppEngine.build(platform, QuietNotifier), dispatcher)

        assertThat(later.storedKeys()).isEqualTo(StoredKeys.Unreadable)
    }

    @Test
    fun keysAppliedAreReadBack() = runBlocking<Unit> {
        val key = Secrets(mapOf(ProviderId("open-meteo") to mapOf("api_key" to "MADEUPKEY")))

        access.applyKeyChanges(KeyChanges.entering(key))

        assertThat(access.storedKeys()).isEqualTo(StoredKeys.Read(key))
    }

    @Test
    fun openMeteoHasAPlaceSearchAndForecastSolarNone() {
        assertThat(access.hasPlaceSearch(ProviderId("open-meteo"))).isTrue()
        assertThat(access.hasPlaceSearch(ProviderId("forecast-solar"))).isFalse()
    }
}
