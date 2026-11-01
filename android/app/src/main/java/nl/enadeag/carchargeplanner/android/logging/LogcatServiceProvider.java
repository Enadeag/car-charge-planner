package nl.enadeag.carchargeplanner.android.logging;

import java.util.concurrent.ConcurrentHashMap;
import nl.enadeag.carchargeplanner.android.BuildConfig;
import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.helpers.NOPMDCAdapter;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

/**
 * Binds slf4j to Logcat, so the engine's log and the app's own reach the same place: from INFO in the release build,
 * from DEBUG in the debug build ({@code BuildConfig.DEBUG}). Nothing is written to a file. slf4j finds it through
 * {@code META-INF/services}; once R8 shrinks the app, that file needs a rule keeping this class.
 */
public final class LogcatServiceProvider implements SLF4JServiceProvider {

    private final IMarkerFactory markers = new BasicMarkerFactory();
    private final MDCAdapter mdc = new NOPMDCAdapter();
    private ILoggerFactory loggers;

    /** Makes the provider; slf4j calls it, then {@link #initialize()}. */
    public LogcatServiceProvider() {
    }

    @Override
    public void initialize() {
        loggers = loggers(new LogcatSink(), threshold(BuildConfig.DEBUG));
    }

    /**
     * The lowest level the build writes.
     *
     * @param debugBuild whether this is the debug build
     * @return DEBUG in the debug build, INFO in the release build
     */
    static Level threshold(boolean debugBuild) {
        return debugBuild ? Level.DEBUG : Level.INFO;
    }

    /**
     * The loggers, one per name, made when first asked for.
     *
     * @param sink where the records go
     * @param threshold the lowest level that is written
     * @return the factory
     */
    static ILoggerFactory loggers(LogSink sink, Level threshold) {
        var made = new ConcurrentHashMap<String, Logger>();
        return name -> made.computeIfAbsent(name, ignored -> new LogcatLogger(name, threshold, sink));
    }

    @Override
    public ILoggerFactory getLoggerFactory() {
        return loggers;
    }

    @Override
    public IMarkerFactory getMarkerFactory() {
        return markers;
    }

    @Override
    public MDCAdapter getMDCAdapter() {
        return mdc;
    }

    @Override
    public String getRequestedApiVersion() {
        return "2.0.99";
    }
}
