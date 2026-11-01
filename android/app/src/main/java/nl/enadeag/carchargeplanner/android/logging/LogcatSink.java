package nl.enadeag.carchargeplanner.android.logging;

import android.util.Log;
import org.slf4j.event.Level;

/** Writes the app's log records to Logcat: the one class in the app that uses {@link Log}. */
final class LogcatSink implements LogSink {

    @Override
    public void write(Level level, String tag, String message) {
        Log.println(priority(level), tag, message);
    }

    /**
     * Logcat's priority for an slf4j level.
     *
     * @param level the record's level
     * @return the matching {@link Log} priority
     */
    static int priority(Level level) {
        switch (level) {
            case ERROR:
                return Log.ERROR;
            case WARN:
                return Log.WARN;
            case INFO:
                return Log.INFO;
            case DEBUG:
                return Log.DEBUG;
            default:
                return Log.VERBOSE;
        }
    }
}
