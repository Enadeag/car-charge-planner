package io.github.enadeag.carchargeplanner;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Catches what a logger and the loggers below it log, at every level, while it is open, and keeps it out of the test
 * run's output. Closing it puts the logger back as it was.
 */
public final class LogCapture implements AutoCloseable {

    private final List<LogRecord> records = new CopyOnWriteArrayList<>();
    private final Logger logger;
    private final Level levelBefore;
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    private LogCapture(String name) {
        logger = Logger.getLogger(name);
        levelBefore = logger.getLevel();
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
    }

    /**
     * Starts catching what a class's logger logs.
     *
     * @param type the class whose name its logger has
     * @return the open capture
     */
    public static LogCapture of(Class<?> type) {
        return of(type.getName());
    }

    /**
     * Starts catching what a logger and the loggers below it log.
     *
     * @param name the logger's name, such as a package's
     * @return the open capture
     */
    public static LogCapture of(String name) {
        return new LogCapture(name);
    }

    /**
     * What was logged so far.
     *
     * @return the records, oldest first
     */
    public List<LogRecord> records() {
        return List.copyOf(records);
    }

    /**
     * The messages logged so far, each with its level, such as {@code "WARNING the config file ..."}.
     *
     * @return the lines, oldest first
     */
    public List<String> lines() {
        return records.stream().map(record -> record.getLevel().getName() + " " + record.getMessage()).toList();
    }

    @Override
    public void close() {
        logger.removeHandler(handler);
        logger.setUseParentHandlers(true);
        logger.setLevel(levelBefore);
    }
}
