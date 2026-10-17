package io.github.enadeag.carchargeplanner;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * One line per log record: the time with its offset, the level, the logger and the message, such as
 * {@code 2026-10-17 14:30:00.123 +02:00 WARNING engine.PlanSchedule: planning failed}. The app's own loggers are named
 * without the app's package. An exception follows on the lines after it, with its stack trace.
 */
final class LogLine extends Formatter {

    private static final String APP_PACKAGE = Main.class.getPackageName() + ".";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS xxx");

    private final ZoneId zone;

    /**
     * Makes the formatter.
     *
     * @param zone the time zone the times are written in, normally the system's own, so they match the owner's
     *     clock; never null
     */
    LogLine(ZoneId zone) {
        if (zone == null) {
            throw new IllegalArgumentException("the zone must not be null");
        }
        this.zone = zone;
    }

    /**
     * The record as one line, and its exception's stack trace on the lines after it.
     *
     * @param record the record to write
     * @return the text, ending with a line break
     */
    @Override
    public String format(LogRecord record) {
        var text = new StringBuilder()
                .append(TIME.format(record.getInstant().atZone(zone))).append(' ')
                .append(record.getLevel().getName()).append(' ')
                .append(shortName(record.getLoggerName())).append(": ")
                // a message with line breaks would look like several records
                .append(String.valueOf(formatMessage(record)).replaceAll("\\R", " "))
                .append(System.lineSeparator());
        if (record.getThrown() != null) {
            var trace = new StringWriter();
            record.getThrown().printStackTrace(new PrintWriter(trace));
            text.append(trace);
        }
        return text.toString();
    }

    private static String shortName(String logger) {
        if (logger == null || logger.isEmpty()) {
            return "root";
        }
        return logger.startsWith(APP_PACKAGE) ? logger.substring(APP_PACKAGE.length()) : logger;
    }
}
