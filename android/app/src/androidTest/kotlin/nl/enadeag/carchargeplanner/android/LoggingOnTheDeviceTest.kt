package nl.enadeag.carchargeplanner.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory

/** slf4j on the device: it finds the app's provider in the APK, and a record reaches Logcat. */
@RunWith(AndroidJUnit4::class)
class LoggingOnTheDeviceTest {

    private val log = LoggerFactory.getLogger(LoggingOnTheDeviceTest::class.java)

    @Test
    fun slf4jUsesTheAppsProvider() {
        assertEquals("nl.enadeag.carchargeplanner.android.logging.LogcatLogger", log.javaClass.name)
    }

    /** The test runs against the debug build, which logs from DEBUG; an app may read its own Logcat lines. */
    @Test
    fun aDebugRecordReachesLogcatUnderTheClassesSimpleName() {
        val marker = "logged from the device test ${UUID.randomUUID()}"

        log.debug(marker)

        // Logcat may show the line a moment after it was written
        val expected = "D/LoggingOnTheDeviceTest: $marker"
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        var lines = logcat()
        while (expected !in lines && System.nanoTime() < deadline) {
            lines = logcat()
        }
        assertTrue("found in $lines", expected in lines)
    }

    /** This test's own Logcat lines so far, as `D/LoggingOnTheDeviceTest: message`. */
    private fun logcat(): List<String> {
        val process = ProcessBuilder("logcat", "-d", "-v", "tag", "-s", "LoggingOnTheDeviceTest:D")
            .redirectErrorStream(true).start()
        val lines = process.inputStream.bufferedReader().readLines()
        process.waitFor(10, TimeUnit.SECONDS)
        return lines
    }
}
