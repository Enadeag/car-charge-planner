package nl.enadeag.carchargeplanner.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.URI
import java.net.UnknownServiceException
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.zip.GZIPOutputStream
import nl.enadeag.carchargeplanner.engine.forecast.ByteSize
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [AndroidHttpGet] on Android's own `HttpURLConnection`, which is built on OkHttp and can differ from the JVM's that
 * `AndroidHttpGetTest` checks against the engine's contract: compression, a connection that ends too soon, and the
 * network security config, and what the contract asks that a device's client might do on its own (redirects,
 * repeated headers). Against [RawServer] on the device's loopback address, which only the debug build may
 * reach over plain http.
 */
@RunWith(AndroidJUnit4::class)
class HttpOnTheDeviceTest {

    private val http = AndroidHttpGet()

    private fun request(address: String) =
        HttpGet.Request(URI.create(address), mapOf(), Duration.ofSeconds(10), ByteSize.ofKilobytes(64))

    private fun gzip(text: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use { it.write(text.toByteArray(StandardCharsets.UTF_8)) }
        return bytes.toByteArray()
    }

    @Test
    fun theBodyIsAskedForUncompressedAndAGzipBodyComesAsItWasSentAsOnTheJvm() {
        val packed = gzip("{\"watts\": 4500}")
        RawServer { out ->
            out.write(("HTTP/1.1 200 OK\r\nContent-Encoding: gzip\r\nContent-Length: ${packed.size}\r\n"
                + "Connection: close\r\n\r\n").toByteArray(StandardCharsets.ISO_8859_1))
            out.write(packed)
        }.use { server ->
            val reply = http.get(request(server.root))

            assertEquals("identity", server.firstRequestHeader("Accept-Encoding"))
            assertEquals(AndroidHttpGet.USER_AGENT, server.firstRequestHeader("User-Agent"))
            assertEquals(String(packed, StandardCharsets.UTF_8), reply.body())
            assertEquals("gzip", reply.header("Content-Encoding").orElse(null))
        }
    }

    @Test
    fun ofAHeaderSentTwiceTheFirstValueCounts() {
        RawServer { out ->
            out.write("HTTP/1.1 200 OK\r\nX-Several: first\r\nX-Several: second\r\nContent-Length: 0\r\n\r\n"
                .toByteArray(StandardCharsets.ISO_8859_1))
        }.use { server ->
            assertEquals("first", http.get(request(server.root)).header("X-Several").orElse(null))
        }
    }

    @Test
    fun aRedirectIsReturnedNotFollowed() {
        RawServer { out ->
            out.write("HTTP/1.1 302 Found\r\nLocation: /moved-here\r\nContent-Length: 0\r\n\r\n"
                .toByteArray(StandardCharsets.ISO_8859_1))
        }.use { server ->
            val reply = http.get(request(server.root))

            assertEquals(302, reply.status())
            assertEquals("/moved-here", reply.header("Location").orElse(null))
            assertEquals(1, server.requests.size)
        }
    }

    @Test
    fun aConnectionClosedBeforeTheAnswerIsAnIOException() {
        RawServer { }.use { server ->
            assertThrows(IOException::class.java) { http.get(request(server.root)) }
            assertTrue("asked again at most once", server.requests.size <= 2)
        }
    }

    @Test
    fun aBodyCutShortOfItsLengthIsAnIOException() {
        RawServer { out ->
            out.write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\nConnection: close\r\n\r\nonly ten b"
                .toByteArray(StandardCharsets.ISO_8859_1))
        }.use { server ->
            val error = assertThrows(IOException::class.java) { http.get(request(server.root)) }
            assertFalse(error is HttpGet.TooLarge)
        }
    }

    /**
     * 127.0.0.2 is this device too, but the debug build's network security config names only 127.0.0.1: the request
     * is refused before a connection is tried. Were plain http let through, nothing listens there, and it would be a
     * [ConnectException] instead.
     */
    @Test
    fun plainHttpToAnyOtherAddressIsRefused() {
        val error = assertThrows(IOException::class.java) { http.get(request("http://127.0.0.2:9/")) }

        assertTrue("refused by the network security config: $error", error is UnknownServiceException)
    }
}
