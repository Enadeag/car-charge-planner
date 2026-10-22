package nl.enadeag.carchargeplanner.engine.notify;

/** Tells the owner about an announcement or a trip's notice; the desktop app shows it as a notification. */
@FunctionalInterface
public interface Notifier {

    /**
     * Tells the owner. Called on the thread that plans; must not throw when the notice cannot be shown, for the
     * engine counts it as told either way.
     *
     * @param notice what to tell; never null
     */
    void announce(Notice notice);
}
