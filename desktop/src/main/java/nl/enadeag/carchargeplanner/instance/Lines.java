package nl.enadeag.carchargeplanner.instance;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** One short line of text over a socket, read with a deadline: a channel's blocking read has no timeout. */
final class Lines {

    /** The longest line read, its end included; the commands and the answer are far shorter. */
    static final int MAX_LENGTH = 64;

    private Lines() {
    }

    /**
     * Sends one line.
     *
     * @param channel the connected channel, in blocking mode
     * @param text the line without its end; ASCII
     * @throws IOException when the line could not be sent
     */
    static void write(SocketChannel channel, String text) throws IOException {
        var buffer = ByteBuffer.wrap((text + "\n").getBytes(StandardCharsets.US_ASCII));
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    /**
     * Reads one line, waiting no longer than the timeout. The channel is in blocking mode again afterwards.
     *
     * @param channel the connected channel, in blocking mode
     * @param timeout how long to wait for the whole line; more than zero
     * @return the line without its end, or empty when none came in time, the other side closed first, or it was longer
     *     than {@link #MAX_LENGTH}
     * @throws IOException when reading fails
     */
    static Optional<String> read(SocketChannel channel, Duration timeout) throws IOException {
        var buffer = ByteBuffer.allocate(MAX_LENGTH);
        var deadline = System.nanoTime() + timeout.toNanos();
        channel.configureBlocking(false);
        try (var selector = Selector.open()) {
            channel.register(selector, SelectionKey.OP_READ);
            while (buffer.hasRemaining()) {
                var left = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (left <= 0) { // select(0) would wait forever
                    return Optional.empty();
                }
                selector.select(left);
                selector.selectedKeys().clear();
                if (channel.read(buffer) < 0) {
                    return Optional.empty();
                }
                var line = line(buffer);
                if (line.isPresent()) {
                    return line;
                }
            }
            return Optional.empty();
        } finally {
            channel.configureBlocking(true); // the selector is closed, so the channel may block again
        }
    }

    private static Optional<String> line(ByteBuffer buffer) {
        for (var i = 0; i < buffer.position(); i++) {
            if (buffer.get(i) == '\n') {
                return Optional.of(new String(buffer.array(), 0, i, StandardCharsets.US_ASCII));
            }
        }
        return Optional.empty();
    }
}
