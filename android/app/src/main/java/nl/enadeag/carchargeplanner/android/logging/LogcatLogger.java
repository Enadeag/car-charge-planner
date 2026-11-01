package nl.enadeag.carchargeplanner.android.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.LegacyAbstractLogger;
import org.slf4j.helpers.MessageFormatter;

/**
 * One class's slf4j logger: it writes each record at or above its threshold to a {@link LogSink}, tagged with the
 * class's simple name. slf4j's base class turns every form of call into one {@link #handleNormalizedLoggingCall}.
 */
final class LogcatLogger extends LegacyAbstractLogger {

    private final String tag;
    private final Level threshold;
    private final LogSink sink;

    /**
     * Makes the logger.
     *
     * @param name the logger's name, normally the logging class's full name
     * @param threshold the lowest level that is written
     * @param sink where the records go
     */
    LogcatLogger(String name, Level threshold, LogSink sink) {
        this.name = name;
        this.tag = name.substring(name.lastIndexOf('.') + 1);
        this.threshold = threshold;
        this.sink = sink;
    }

    private boolean writes(Level level) {
        return level.toInt() >= threshold.toInt();
    }

    @Override
    public boolean isTraceEnabled() {
        return writes(Level.TRACE);
    }

    @Override
    public boolean isDebugEnabled() {
        return writes(Level.DEBUG);
    }

    @Override
    public boolean isInfoEnabled() {
        return writes(Level.INFO);
    }

    @Override
    public boolean isWarnEnabled() {
        return writes(Level.WARN);
    }

    @Override
    public boolean isErrorEnabled() {
        return writes(Level.ERROR);
    }

    @Override
    protected String getFullyQualifiedCallerName() {
        return null;
    }

    @Override
    protected void handleNormalizedLoggingCall(Level level, Marker marker, String messagePattern, Object[] arguments,
            Throwable throwable) {
        String message = String.valueOf(MessageFormatter.basicArrayFormat(messagePattern, arguments));
        if (throwable != null) {
            // not Log.getStackTraceString, which leaves out the whole trace of an UnknownHostException
            var trace = new StringWriter();
            throwable.printStackTrace(new PrintWriter(trace));
            message = message + "\n" + trace.toString().stripTrailing();
        }
        sink.write(level, tag, message);
    }
}
