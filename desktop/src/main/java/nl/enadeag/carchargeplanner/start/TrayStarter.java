package nl.enadeag.carchargeplanner.start;

import nl.enadeag.carchargeplanner.desktop.system.Tray;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.time.InstantSource;

/** Puts the icon in the tray, trying again for a while when the tray is not there yet, as at login. */
final class TrayStarter {

    static final Duration RETRY = Duration.ofMillis(500);

    private static final System.Logger LOG = System.getLogger(TrayStarter.class.getName());

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

    TrayStarter(InstantSource clock, Sleeper sleeper) {
        this.clock = clock;
        this.sleeper = sleeper;
    }

    /**
     * The wait is how long to keep trying, zero for one try. Gives false when no tray came, which is logged: the
     * dashboard is then a window.
     */
    boolean tryAdd(Tray tray, Tray.Actions actions, Duration wait) throws InterruptedException {
        var deadline = clock.instant().plus(wait);
        while (true) {
            try {
                tray.add(actions);
                return true;
            } catch (Tray.TrayUnavailable e) {
                if (!clock.instant().isBefore(deadline)) {
                    LOG.log(Level.WARNING, e.getMessage() + "; the dashboard is a window instead",
                            e.getCause());
                    return false;
                }
                sleeper.sleep(RETRY);
            }
        }
    }
}
