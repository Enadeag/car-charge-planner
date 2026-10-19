package nl.enadeag.carchargeplanner;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Where the app's log goes, set up once as the app starts: records of INFO and up, and of the app's own loggers from
 * the level the owner asks for, go to the {@linkplain LogFile log file} and, when the app runs in a terminal, there as
 * well. A level below INFO for the app's own loggers only, so a library's debug records, which may name addresses, stay
 * out.
 */
final class AppLog implements AutoCloseable {

    /** The environment variable that sets the level of the app's own loggers, such as {@code DEBUG}. */
    static final String LEVEL_VARIABLE = "CAR_CHARGE_PLANNER_LOG_LEVEL";
    /** The size in bytes after which the log file is rotated. */
    static final long FILE_LIMIT = 1_000_000;
    /** How many log files are kept, the newest included. */
    static final int FILE_COUNT = 3;

    // held here, as java.util.logging forgets a logger's level and handlers once nothing refers to the logger
    private final Logger root;
    private final Logger app;
    private final List<Handler> handlers = new ArrayList<>();

    /**
     * Gets the loggers to set up; nothing is changed yet.
     *
     * @param root the logger every record reaches, normally java.util.logging's root logger; its handlers are
     *     replaced
     * @param app the parent logger of the app's own loggers, whose level the owner may lower
     */
    AppLog(Logger root, Logger app) {
        this.root = root;
        this.app = app;
    }

    /**
     * The newest log file in a folder, to tell the owner where to look.
     *
     * @param folder the app's data folder
     * @return the file records are written to now; it need not exist yet
     */
    static Path file(Path folder) {
        return LogFile.newest(folder);
    }

    /**
     * Sends the records to the log file and, when asked, the terminal, in place of where they went before. When the
     * file cannot be opened the records go to the terminal instead, whether or not there is one, and that is logged.
     *
     * @param folder the app's data folder, where the log files are kept; made, private, when it is missing
     * @param environment the environment variables, for {@link #LEVEL_VARIABLE}; a value that is not one of
     *     {@link System.Logger.Level}'s names is logged and INFO is used
     * @param terminal whether the app runs in a terminal, where the records are shown too
     * @param zone the time zone the times are written in
     */
    void start(Path folder, Map<String, String> environment, boolean terminal, ZoneId zone) {
        for (var handler : root.getHandlers()) {
            root.removeHandler(handler); // the JDK's own console handler, without times
        }
        var format = new LogLine(zone);
        IOException fileProblem = null;
        try {
            add(new LogFile(folder, FILE_LIMIT, FILE_COUNT, format));
        } catch (IOException e) {
            fileProblem = e;
        }
        if (terminal || fileProblem != null) {
            var console = new ConsoleHandler();
            console.setFormatter(format);
            console.setLevel(Level.ALL);
            add(console);
        }
        root.setLevel(Level.INFO);
        var requested = environment.getOrDefault(LEVEL_VARIABLE, "");
        var level = level(requested);
        app.setLevel(level.orElse(Level.INFO));
        var own = Logger.getLogger(app.getName() + "." + AppLog.class.getSimpleName());
        if (fileProblem != null) {
            own.warning("cannot write the log file in " + folder + ": " + fileProblem);
        }
        if (level.isEmpty() && !requested.isBlank()) {
            own.warning(LEVEL_VARIABLE + " is \"" + requested + "\", which is not one of "
                    + List.of(System.Logger.Level.values()) + "; logging from INFO");
        }
    }

    /** Stops writing to the log file and the terminal, and closes the file. Closing again does nothing. */
    @Override
    public void close() {
        for (var handler : handlers) {
            root.removeHandler(handler);
            handler.close();
        }
        handlers.clear();
    }

    private void add(Handler handler) {
        handlers.add(handler);
        root.addHandler(handler);
    }

    /** The java.util.logging level of a System.Logger level's name, which has the same severity. */
    private static Optional<Level> level(String name) {
        try {
            var level = System.Logger.Level.valueOf(name.strip().toUpperCase(Locale.ROOT));
            return Optional.of(Level.parse(Integer.toString(level.getSeverity())));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
