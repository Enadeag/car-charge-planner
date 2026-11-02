package nl.enadeag.carchargeplanner.android

import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet
import nl.enadeag.carchargeplanner.engine.forecast.HttpGetContractTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

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

    /**
     * Answers one request on the loopback address with [answer], byte for byte, and closes the connection: an answer
     * the contract's server cannot send.
     *
     * @return the request's header lines, the request line first
     */
    private fun answeredOnce(answer: String, headers: Map<String, String> = mapOf(), check: (HttpGet.Request) -> Unit):
        List<String> {
        val lines = CopyOnWriteArrayList<String>()
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val serving = thread(isDaemon = true) {
                server.accept().use { client ->
                    val request = client.getInputStream().bufferedReader(StandardCharsets.ISO_8859_1)
                    lines += generateSequence { request.readLine() }.takeWhile { it.isNotEmpty() }
                    client.getOutputStream().write(answer.toByteArray(StandardCharsets.ISO_8859_1))
                }
            }
            check(HttpGet.Request(URI.create("http://127.0.0.1:${server.localPort}/"), headers, Duration.ofSeconds(5),
                SMALL_LIMIT))
            serving.join(5000)
        }
        return lines
    }

    @Test
    fun aRequestsOwnHeadersCannotReplaceTheUserAgentOrAskForCompression() {
        val lines = answeredOnce("HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n",
            mapOf("Accept-Encoding" to "gzip", "User-Agent" to "something else", "Accept" to "application/json")) {
            http.get(it)
        }

        assertThat(lines).contains("User-Agent: ${AndroidHttpGet.USER_AGENT}", "Accept-Encoding: identity",
            "Accept: application/json")
        assertThat(lines).noneMatch { it.contains("gzip") || it.contains("something else") }
    }

    @Test
    fun aBodyCutShortOfItsLengthIsAnIOException() {
        answeredOnce("HTTP/1.1 200 OK\r\nContent-Length: 100\r\nConnection: close\r\n\r\nonly ten b") {
            assertThat(catchThrowable { http.get(it) }).isInstanceOf(IOException::class.java)
                .isNotInstanceOf(HttpGet.TooLarge::class.java)
        }
    }

    /** A 304 may give the length of the answer it stands for, and Content-Length over the limit counts neither. */
    @ParameterizedTest
    @ValueSource(strings = ["100", "999999999"])
    fun aNotModifiedWithALengthButNoBodyIsAnEmptyReply(length: String) {
        answeredOnce("HTTP/1.1 304 Not Modified\r\nContent-Length: $length\r\nETag: \"7\"\r\n"
            + "Connection: close\r\n\r\n") {
            val reply = http.get(it)

            assertThat(reply.status()).isEqualTo(304)
            assertThat(reply.body()).isEmpty()
            assertThat(reply.header("ETag")).contains("\"7\"")
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
