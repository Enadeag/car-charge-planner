package nl.enadeag.carchargeplanner.android.logging;

import org.slf4j.event.Level;

/** Where the app's log records go: Logcat in the app, a list in the tests. */
interface LogSink {

    /**
     * Writes one record.
     *
     * @param level how severe the record is
     * @param tag the logging class's simple name
     * @param message the whole text, with the stack trace of an exception on the lines after it
     */
    void write(Level level, String tag, String message);
}
