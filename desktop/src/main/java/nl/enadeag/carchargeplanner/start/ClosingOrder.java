package nl.enadeag.carchargeplanner.start;

import java.lang.System.Logger.Level;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Closes the app's parts once, the last one made first, so nothing is closed while a part made after it still uses
 * it. Quitting and the JVM's shutdown hook share it: whichever comes first closes, and the other finds it done.
 */
final class ClosingOrder implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(ClosingOrder.class.getName());

    private record Part(String name, AutoCloseable part) {
    }

    private final Deque<Part> parts = new ArrayDeque<>();
    private boolean closed;

    /** Starts with nothing to close. */
    ClosingOrder() {
    }

    /**
     * Adds a part to close; after {@link #close()} it is closed at once, as the app is going away.
     *
     * @param name what the part is, for the log, such as "the tray"
     * @param part closes the part; it is called once
     */
    void add(String name, AutoCloseable part) {
        synchronized (this) {
            if (!closed) {
                parts.push(new Part(name, part));
                return;
            }
        }
        closeQuietly(new Part(name, part));
    }

    /**
     * Closes the parts, the last one added first. A part that fails to close is logged, and the others still close.
     * Closing again does nothing; a second caller waits until the first is done.
     */
    @Override
    public synchronized void close() {
        closed = true;
        while (!parts.isEmpty()) {
            closeQuietly(parts.pop());
        }
    }

    private static void closeQuietly(Part part) {
        try {
            part.part().close();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "could not close " + part.name(), e);
        }
    }
}
