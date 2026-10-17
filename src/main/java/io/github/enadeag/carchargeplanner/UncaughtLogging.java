package io.github.enadeag.carchargeplanner;

import java.lang.System.Logger.Level;

/**
 * Logs what a thread threw and nothing caught, with its stack trace, as an error: in the log file it is found again,
 * where the JVM would only print it to a terminal the owner does not have. As the default handler it covers every
 * thread without a handler of its own: platform and virtual threads, and the JavaFX thread, which hands an exception
 * that an event handler threw to it.
 */
final class UncaughtLogging implements Thread.UncaughtExceptionHandler {

    private final System.Logger log;

    /**
     * Makes the handler.
     *
     * @param log where the exceptions are logged; never null
     */
    UncaughtLogging(System.Logger log) {
        if (log == null) {
            throw new IllegalArgumentException("the logger must not be null");
        }
        this.log = log;
    }

    /**
     * Makes this the handler of every thread that has none of its own, for the rest of the JVM's life. Only the
     * composition root calls it, once, as the app starts.
     */
    void install() {
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    /**
     * Logs the exception and the thread it ended.
     *
     * @param thread the thread that ends; a virtual thread has no name, so it is named as Java shows it
     * @param thrown what it threw
     */
    @Override
    public void uncaughtException(Thread thread, Throwable thrown) {
        var name = thread.getName().isEmpty() ? thread.toString() : "\"" + thread.getName() + "\"";
        log.log(Level.ERROR, "nothing caught what thread " + name + " threw", thrown);
    }
}
