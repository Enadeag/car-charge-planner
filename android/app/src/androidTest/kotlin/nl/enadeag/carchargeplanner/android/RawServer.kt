package nl.enadeag.carchargeplanner.android

import java.io.Closeable
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * A web server of a few lines on the device's loopback address, which answers each request with whatever [answer]
 * writes, byte for byte, and then closes the connection: so a test can send what a real server should not, such as a
 * body cut short. It keeps the header lines of each request.
 *
 * @param answer writes the answer to one request
 */
class RawServer(private val answer: (OutputStream) -> Unit) : Closeable {

    private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))

    /** The header lines of each request so far, the request line first. */
    val requests: MutableList<List<String>> = CopyOnWriteArrayList()

    /** The address of the server's root, over plain http. */
    val root: String = "http://127.0.0.1:${socket.localPort}/"

    init {
        thread(isDaemon = true, name = "raw-server") {
            try {
                while (true) {
                    socket.accept().use { client ->
                        val reader = client.getInputStream().bufferedReader(StandardCharsets.ISO_8859_1)
                        requests += generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.toList()
                        answer(client.getOutputStream())
                        client.getOutputStream().flush()
                    }
                }
            } catch (closed: SocketException) {
                // close() ends the server
            }
        }
    }

    /**
     * The value of a header of the first request.
     *
     * @param name the header's name, in any case
     * @return its value, or null when the request did not have it
     */
    fun firstRequestHeader(name: String): String? = requests.first()
        .drop(1)
        .map { it.split(":", limit = 2) }
        .firstOrNull { it[0].trim().equals(name, ignoreCase = true) }
        ?.get(1)?.trim()

    override fun close() {
        socket.close()
    }
}
