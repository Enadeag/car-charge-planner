package nl.enadeag.carchargeplanner.android

import androidx.datastore.preferences.core.PreferencesFileSerializer
import java.io.File
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat

/**
 * What the phone's preferences files hold on the disk. DataStore shows a write to its readers just before it moves the
 * file into place, so a test that closes or reopens a store waits for the file, not for the value its readers see.
 */
object StoreFiles {

    /** What [file] holds, by key name; empty while there is no file. */
    suspend fun held(file: File): Map<String, Any> {
        if (!file.exists()) {
            return emptyMap()
        }
        val preferences = file.inputStream().use { PreferencesFileSerializer.readFrom(it) }
        return preferences.asMap().mapKeys { it.key.name }
    }

    /** Waits until [file] holds something and returns it; fails when it holds nothing in time. */
    fun awaitHeld(file: File): Map<String, Any> {
        var now = emptyMap<String, Any>()
        val arrived = Waiting.waitUntil {
            now = runBlocking { held(file) }
            now.isNotEmpty()
        }
        assertThat(arrived).`as`("${file.name} never held anything").isTrue()
        return now
    }
}
