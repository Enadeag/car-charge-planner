package nl.enadeag.carchargeplanner;

import nl.enadeag.carchargeplanner.desktop.system.Tray;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.time.InstantSource;

/** Puts the icon in the tray, trying again for a while when the tray is not there yet, as at login. */
final class TrayStarter {

    /** How long to wait between two tries. */
    static final Duration RETRY = Duration.ofMillis(500);

    private static final System.Logger LOG = System.getLogger(TrayStarter.class.getName());

    /** Waits between two tries. */
    @FunctionalInterface
    interface Sleeper {

        /**
         * Waits.
         *
         * @param time how long
         * @throws InterruptedException when the thread was interrupted while it waited
         */
        void sleep(Duration time) throws InterruptedException;
    }

    private final InstantSource clock;
    private final Sleeper sleeper;

    /**
     * Makes the starter.
     *
     * @param clock tells when the wait is over
     * @param sleeper waits between two tries, such as {@link Thread#sleep(Duration)}
     */
    TrayStarter(InstantSource clock, Sleeper sleeper) {
        this.clock = clock;
        this.sleeper = sleeper;
    }

    /**
     * Shows the icon, trying until it works or the wait is over.
     *
     * @param tray the system's tray
     * @param actions what the icon and its menu do
     * @param wait how long to keep trying; zero for one try
     * @return true when the icon is in the tray; false when no tray came, which is logged, and the app shows a window
     *     instead
     * @throws InterruptedException when the thread was interrupted while it waited
     */
    boolean show(Tray tray, Tray.TrayActions actions, Duration wait) throws InterruptedException {
        var deadline = clock.instant().plus(wait);
        while (true) {
            try {
                tray.show(actions);
                return true;
            } catch (Tray.TrayUnavailable e) {
                if (!clock.instant().isBefore(deadline)) {
                    LOG.log(Level.WARNING, e.getMessage() + "; showing the dashboard as a window instead",
                            e.getCause());
                    return false;
                }
                sleeper.sleep(RETRY);
            }
        }
    }
}
