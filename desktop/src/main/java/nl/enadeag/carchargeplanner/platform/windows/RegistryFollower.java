package nl.enadeag.carchargeplanner.platform.windows;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.lang.foreign.MemorySegment;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Follows a setting that a key below HKEY_CURRENT_USER keeps, by the registry's change notification, on a thread of
 * its own that waits for an event: gives the value now, then again whenever it changes.
 *
 * @param <T> what the setting's values stand for
 */
final class RegistryFollower<T> {

    private static final System.Logger LOG = System.getLogger(RegistryFollower.class.getName());

    private final String key;
    private final Supplier<T> read;
    private final T missing;
    private final String subject;
    private final String keptNote;
    private final Level level;
    private final String threadName;

    /**
     * Makes the follower; nothing is read yet.
     *
     * @param key the key's path below HKEY_CURRENT_USER
     * @param read reads the setting now, never failing: it gives what stands for a value that cannot be read
     * @param missing what stands for a key that does not exist
     * @param subject the setting's name in the log, such as "Windows' text size"
     * @param keptNote what the app keeps when the setting cannot be followed, for the log
     * @param level the level at which a failure is logged
     * @param threadName the name of the thread that waits for changes
     */
    RegistryFollower(String key, Supplier<T> read, T missing, String subject, String keptNote, Level level,
            String threadName) {
        this.key = key;
        this.read = read;
        this.missing = missing;
        this.subject = subject;
        this.keptNote = keptNote;
        this.level = level;
        this.threadName = threadName;
    }

    /**
     * Gives the value now, then follows it while the key exists. Without the key there is nothing to follow: a change
     * in Settings makes it, and the app follows from its next start.
     */
    void follow(Consumer<T> changed) {
        var handle = MemorySegment.NULL;
        var event = MemorySegment.NULL;
        try {
            var opened = Advapi32.openKey(key);
            if (opened.isEmpty()) {
                changed.accept(missing);
                return;
            }
            handle = opened.get();
            event = Kernel32.newEvent();
            // asked before the first read, so a change right after it is not missed
            Advapi32.signalOnChange(handle, event, key);
        } catch (IOException e) {
            LOG.log(level, "cannot follow " + subject + "; " + keptNote, e);
            release(handle, event);
            changed.accept(read.get());
            return;
        }
        var value = read.get();
        changed.accept(value);
        var open = handle;
        var signal = event;
        Thread.ofPlatform().daemon().name(threadName).start(() -> watch(open, signal, value, changed));
    }

    /** Waits for each change and gives the new value, until the key is deleted or Windows refuses. */
    private void watch(MemorySegment handle, MemorySegment event, T first, Consumer<T> changed) {
        var last = first;
        try {
            while (true) {
                Kernel32.await(event);
                if (!Advapi32.signalOnChange(handle, event, key)) {
                    break;
                }
                var now = read.get();
                if (!now.equals(last)) {
                    last = now;
                    changed.accept(now);
                }
            }
        } catch (IOException e) {
            LOG.log(level, "stopped following " + subject, e);
        } finally {
            release(handle, event);
        }
    }

    /**
     * Closes the key and the event once they are no longer followed; one that was not opened, or that cannot be
     * closed, is left as it is.
     *
     * @param handle the open key, or {@link MemorySegment#NULL} when it was not opened
     * @param event the event, or {@link MemorySegment#NULL} when it was not made
     */
    private void release(MemorySegment handle, MemorySegment event) {
        try {
            if (!handle.equals(MemorySegment.NULL)) {
                Advapi32.closeKey(handle, key);
            }
        } catch (IOException e) {
            LOG.log(level, "cannot close the key of " + subject, e);
        }
        try {
            if (!event.equals(MemorySegment.NULL)) {
                Kernel32.close(event);
            }
        } catch (IOException e) {
            LOG.log(level, "cannot close the event that follows " + subject, e);
        }
    }
}
