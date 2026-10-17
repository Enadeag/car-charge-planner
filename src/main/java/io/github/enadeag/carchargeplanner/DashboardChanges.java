package io.github.enadeag.carchargeplanner;

import io.github.enadeag.carchargeplanner.engine.Engine;
import io.github.enadeag.carchargeplanner.engine.away.AwayPeriod;
import io.github.enadeag.carchargeplanner.engine.config.Percent;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.util.concurrent.Executor;

/**
 * The changes the owner makes on the dashboard, handed to the engine away from the JavaFX thread, one at a time and in
 * the order they were made. The engine plans again after each, and the dashboard shows that plan.
 */
final class DashboardChanges {

    private static final System.Logger LOG = System.getLogger(DashboardChanges.class.getName());

    /** One change saved through the engine. */
    @FunctionalInterface
    private interface Change {
        void save() throws IOException;
    }

    private final Engine engine;
    private final Executor saving;

    /**
     * Makes the changes' way to the engine.
     *
     * @param engine saves each change, and plans again after it
     * @param saving runs the saves one at a time, in order, on a thread that is not the JavaFX thread
     */
    DashboardChanges(Engine engine, Executor saving) {
        this.engine = engine;
        this.saving = saving;
    }

    /**
     * Saves the battery level the owner entered.
     *
     * @param level the level the car shows
     */
    void enterBatteryLevel(Percent level) {
        save("the battery level", () -> engine.setBatteryLevel(level));
    }

    /**
     * Saves an away period the owner entered.
     *
     * @param period the time the car is away
     */
    void addAwayPeriod(AwayPeriod period) {
        save("the away period", () -> engine.addAwayPeriod(period));
    }

    /**
     * Forgets an away period the owner removed.
     *
     * @param period the period as the dashboard showed it
     */
    void removeAwayPeriod(AwayPeriod period) {
        save("the removal of the away period", () -> engine.removeAwayPeriod(period));
    }

    /** A save that fails leaves the stored data as it was, which the next plan shows; the log says why. */
    private void save(String what, Change change) {
        saving.execute(() -> {
            try {
                change.save();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "could not save " + what, e);
            }
        });
    }
}
