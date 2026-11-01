package nl.enadeag.carchargeplanner.android

import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Duration
import kotlin.concurrent.thread
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet
import nl.enadeag.carchargeplanner.engine.forecast.HttpGetContractTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test

/**
 * The app's [HttpGet] against the contract every one meets, on the JVM's `HttpURLConnection`, and what it does beyond
 * that. `HttpOnTheDeviceTest` checks what Android's own `HttpURLConnection` could do differently.
 */
class AndroidHttpGetTest : HttpGetContractTest() {

    private val http = AndroidHttpGet()

    override fun http(): HttpGet = http

    @Test
    fun theUserAgentIsTheDesktopsAndTheBodyIsAskedForUncompressed() {
        get("/refusal", Duration.ofSeconds(5))

        assertThat(receivedHeaders().getFirst("User-Agent"))
            .isEqualTo("car-charge-planner (+https://github.com/Enadeag/car-charge-planner)")
        assertThat(receivedHeaders().getFirst("Accept-Encoding")).isEqualTo("identity")
    }

    @Test
    fun aBodyCutShortOfItsLengthIsAnIOException() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            thread(isDaemon = true) {
                server.accept().use { client ->
                    val request = client.getInputStream().bufferedReader(StandardCharsets.ISO_8859_1)
                    while (!request.readLine().isNullOrEmpty()) {
                        // the request's lines, up to the empty one
                    }
                    client.getOutputStream().write(
                        "HTTP/1.1 200 OK\r\nContent-Length: 100\r\nConnection: close\r\n\r\nonly ten b"
                            .toByteArray(StandardCharsets.ISO_8859_1))
                }
            }
            val request = HttpGet.Request(URI.create("http://127.0.0.1:${server.localPort}/"), mapOf(),
                Duration.ofSeconds(5), SMALL_LIMIT)

            val error = catchThrowable { http.get(request) }

            assertThat(error).isInstanceOf(IOException::class.java).isNotInstanceOf(HttpGet.TooLarge::class.java)
        }
    }

    @Test
    fun anAddressThatIsNotOnTheWebIsRefused() {
        val file = HttpGet.Request.of(URI.create("file:///etc/hostname"))

        assertThat(catchThrowable { http.get(file) }).isInstanceOf(IOException::class.java)
            .hasMessageContaining("not a web address")
    }

    @Test
    fun anErrorIsRethrownAsItIs() {
        val error = StackOverflowError("in the worker")

        assertThatThrownBy { AndroidHttpGet.failure(error) }.isSameAs(error)
    }

    @Test
    fun anIOExceptionIsPassedOnAsItIs() {
        val refused = ConnectException("refused")

        assertThat(AndroidHttpGet.failure(refused)).isSameAs(refused)
    }

    @Test
    fun anyOtherFailureBecomesAnIOException() {
        val bug = IllegalStateException("a bug in the connection")

        assertThat(AndroidHttpGet.failure(bug)).isExactlyInstanceOf(IOException::class.java).hasCause(bug)
    }
}
