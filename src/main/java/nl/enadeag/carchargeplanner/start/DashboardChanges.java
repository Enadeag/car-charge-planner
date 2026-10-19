package nl.enadeag.carchargeplanner.start;

import nl.enadeag.carchargeplanner.desktop.app.Background;
import nl.enadeag.carchargeplanner.engine.Engine;
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * The changes the owner makes on the dashboard, handed to the engine away from the JavaFX thread, one at a time and in
 * the order they were made. The engine plans again after each, and the dashboard shows that plan; a change that could
 * not be saved is logged, and the next plan shows the stored data as it was.
 */
final class DashboardChanges {

    /** One change saved through the engine. */
    @FunctionalInterface
    private interface Change {
        void save() throws IOException;
    }

    private final Engine engine;
    private final Background saving;

    /**
     * Makes the changes' way to the engine.
     *
     * @param engine saves each change, and plans again after it
     * @param saving runs the saves one at a time, in order, away from the JavaFX thread, and logs one that fails
     */
    DashboardChanges(Engine engine, Background saving) {
        this.engine = engine;
        this.saving = saving;
    }

    /**
     * Saves the battery level the owner entered.
     *
     * @param level the level the car shows
     */
    void setBatteryLevel(Percent level) {
        save("the battery level", () -> engine.setBatteryLevel(level));
    }

    /**
     * Saves an away period the owner entered.
     *
     * @param period the time the car is away
     */
    void addAwayPeriod(OneOffAwayPeriod period) {
        save("the away period", () -> engine.addAwayPeriod(period));
    }

    /**
     * Forgets an away period the owner removed.
     *
     * @param period the period as the dashboard showed it
     */
    void removeAwayPeriod(OneOffAwayPeriod period) {
        save("the removal of the away period", () -> engine.removeAwayPeriod(period));
    }

    private void save(String what, Change change) {
        saving.run("saving " + what, () -> {
            try {
                change.save();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return null;
        }, done -> { }, failed -> { });
    }
}
