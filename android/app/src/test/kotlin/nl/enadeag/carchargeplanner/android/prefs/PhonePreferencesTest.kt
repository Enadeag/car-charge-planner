package nl.enadeag.carchargeplanner.android.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import java.io.File
import java.io.IOException
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** The phone's preferences on DataStore's own files, in a folder of the test's. */
class PhonePreferencesTest {

    @TempDir
    lateinit var folder: Path

    private val scopes = mutableListOf<CoroutineScope>()

    @AfterEach
    fun stop() {
        scopes.forEach { it.cancel() }
    }

    /** A store on its file, as the app makes one; one per file at a time, as DataStore needs. */
    private fun store(file: File): Pair<DataStore<Preferences>, CoroutineScope> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
        return phoneStore(scope) { file } to scope
    }

    private fun file(name: String) = folder.resolve("datastore/$name.preferences_pb").toFile()

    /** The store on its file again, as after the app was closed: the one before is ended first. */
    private fun again(scope: CoroutineScope, file: File): Pair<DataStore<Preferences>, CoroutineScope> {
        runBlocking {
            val job = scope.coroutineContext[Job]!!
            job.cancel()
            job.join()
        }
        return store(file)
    }

    private fun <T> waitFor(read: suspend () -> T, until: (T) -> Boolean): T = runBlocking {
        withTimeout(5_000) {
            var now = read()
            while (!until(now)) {
                delay(10)
                now = read()
            }
            now
        }
    }

    @Test
    fun wifiOnlyIsOffUntilSwitched() {
        val (settings, scope) = store(file(PhoneStoreFiles.SETTINGS))

        assertThat(runBlocking { PhoneSettings(settings, scope).on.first() }).isFalse()
    }

    @Test
    fun wifiOnlyIsKeptOnceSwitchedAlsoAfterTheAppWasClosed() {
        val file = file(PhoneStoreFiles.SETTINGS)
        val (settings, scope) = store(file)
        val wifiOnly = PhoneSettings(settings, scope)

        wifiOnly.switch(true)
        waitFor({ wifiOnly.on.first() }) { it }

        val (later, laterScope) = again(scope, file)
        assertThat(runBlocking { PhoneSettings(later, laterScope).on.first() }).isTrue()
    }

    @Test
    fun wifiOnlySwitchedOffAgainIsOff() {
        val (settings, scope) = store(file(PhoneStoreFiles.SETTINGS))
        val wifiOnly = PhoneSettings(settings, scope)
        wifiOnly.switch(true)
        waitFor({ wifiOnly.on.first() }) { it }

        wifiOnly.switch(false)

        assertThat(waitFor({ wifiOnly.on.first() }) { !it }).isFalse()
    }

    @Test
    fun theCardAndTheEpisodeStartUnsetAndAreKeptApartAfterTheAppWasClosed() {
        val file = file(PhoneStoreFiles.STATE)
        val (state, scope) = store(file)
        val phone = PhoneState(state)
        runBlocking {
            assertThat(listOf(phone.closed(), phone.refused(), phone.episodeStarted())).containsOnly(false)
            phone.refuse()
            phone.episodeStarted(true)
        }

        val (later, _) = again(scope, file)
        val after = PhoneState(later)
        runBlocking {
            assertThat(after.closed()).isFalse()
            assertThat(after.refused()).isTrue()
            assertThat(after.episodeStarted()).isTrue()
            after.close()
            after.episodeStarted(false)
            assertThat(after.closed()).isTrue()
            assertThat(after.episodeStarted()).isFalse()
        }
    }

    @Test
    fun aFileThatCannotBeReadGivesTheDefaults() {
        val file = file(PhoneStoreFiles.STATE)
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(0x7f, 0x13, 0x00, 0x42, 0x55))
        val (state, scope) = store(file)

        runBlocking {
            assertThat(PhoneState(state).episodeStarted()).isFalse()
            assertThat(PhoneSettings(state, scope).on.first()).isFalse()
        }
    }

    @Test
    fun aFileThatCannotBeParsedIsReplacedSoAWriteIsKeptAgain() {
        val file = file(PhoneStoreFiles.STATE)
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(0x7f, 0x13, 0x00, 0x42, 0x55))
        val (state, scope) = store(file)
        runBlocking { PhoneState(state).close() }

        val (later, _) = again(scope, file)

        assertThat(runBlocking { PhoneState(later).closed() }).isTrue()
    }

    @Test
    fun aWriteThatFailsIsLoggedAndLeavesTheValueAsItWas() {
        // a folder where the store's file should be: nothing can be written there
        val file = file(PhoneStoreFiles.STATE).also { it.mkdirs() }
        val (state, _) = store(file)
        val phone = PhoneState(state)

        runBlocking {
            phone.close()
            assertThat(phone.closed()).isFalse()
        }
    }

    /** A store whose reads and writes fail with this, as a file that went bad or a full disk would. */
    private class Failing(private val failure: Exception) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw failure }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw failure
    }

    @Test
    fun aStoreThatCannotBeReadOrWrittenGivesTheDefaultsAndThrowsNothing() {
        val failing = Failing(IOException("disk gone"))
        val phone = PhoneState(failing)

        runBlocking {
            assertThat(phone.episodeStarted()).isFalse()
            phone.episodeStarted(true)
            phone.refuse()
            assertThat(PhoneSettings(failing, this).on.first()).isFalse()
        }
    }

    @Test
    fun aFaultOtherThanTheFilesIsNotHidden() {
        val fault = IllegalStateException("a fault in the code")

        assertThatThrownBy { runBlocking { PhoneState(Failing(fault)).closed() } }.isSameAs(fault)
        assertThatThrownBy { runBlocking { PhoneState(Failing(fault)).close() } }.isSameAs(fault)
    }
}
