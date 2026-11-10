package nl.enadeag.carchargeplanner.start;

import nl.enadeag.carchargeplanner.desktop.app.Background;
import nl.enadeag.carchargeplanner.engine.Engine;
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * The changes the owner makes on the dashboard, handed to the engine away from the JavaFX thread, one at a time and in
 * the order they were made. The engine plans again after each, and the dashboard shows that plan; a change that could
 * not be saved is logged, and the next plan shows the stored data as it was.
 */
final class DashboardChanges {

    @FunctionalInterface
    private interface Change {
        void save() throws IOException;
    }

    private final Engine engine;
    private final Background saving;

    DashboardChanges(Engine engine, Background saving) {
        this.engine = engine;
        this.saving = saving;
    }

    void setBatteryLevel(Percent level) {
        save("the battery level", () -> engine.setBatteryLevel(level));
    }

    void addAwayPeriod(OneOffAwayPeriod period) {
        save("the away period", () -> engine.addAwayPeriod(period));
    }

    void removeAwayPeriod(OneOffAwayPeriod period) {
        save("the removal of the away period", () -> engine.removeAwayPeriod(period));
    }

    void addTrip(Trip trip) {
        save("the trip", () -> engine.addTrip(trip));
    }

    /** The trip is as the dashboard showed it, changed is as the owner changed it. */
    void changeTrip(Trip trip, Trip changed) {
        save("the change to the trip", () -> engine.changeTrip(trip, changed));
    }

    void removeTrip(Trip trip) {
        save("the removal of the trip", () -> engine.removeTrip(trip));
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
