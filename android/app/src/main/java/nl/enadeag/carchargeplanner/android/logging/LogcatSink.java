package nl.enadeag.carchargeplanner.android.logging;

import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.event.Level;

/**
 * Writes the app's log records to Logcat: the one class in the app that uses {@link Log}. Logcat cuts an entry at
 * about 4 KB, so a longer record, such as a deep stack trace, goes in several entries.
 */
final class LogcatSink implements LogSink {

    /** The most characters in one Logcat entry: below its 4 KB, which also holds the tag, in UTF-8 bytes. */
    static final int MOST_PER_ENTRY = 1000;

    @Override
    public void write(Level level, String tag, String message) {
        for (String part : parts(message)) {
            Log.println(priority(level), tag, part);
        }
    }

    /**
     * A message in parts of at most {@link #MOST_PER_ENTRY} characters, cut at a line end where it can be.
     *
     * @param message the whole text, not null
     * @return the parts in order, one when the message is short enough; a line end where the message was cut is left
     *     out
     */
    static List<String> parts(String message) {
        var parts = new ArrayList<String>();
        int start = 0;
        while (message.length() - start > MOST_PER_ENTRY) {
            int newline = message.lastIndexOf('\n', start + MOST_PER_ENTRY);
            if (newline >= start) {
                parts.add(message.substring(start, newline));
                start = newline + 1;
            } else {
                int end = start + MOST_PER_ENTRY;
                if (Character.isHighSurrogate(message.charAt(end - 1))) {
                    end--; // not between the two halves of one character
                }
                parts.add(message.substring(start, end));
                start = end;
            }
        }
        parts.add(message.substring(start));
        return parts;
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
