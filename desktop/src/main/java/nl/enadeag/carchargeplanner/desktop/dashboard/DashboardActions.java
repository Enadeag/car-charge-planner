package nl.enadeag.carchargeplanner.desktop.dashboard;

import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * What the dashboard's controls do. Each is called on the JavaFX thread, so one that saves or waits hands the work on.
 *
 * @param batteryEntered gets the battery level the owner saved on today's battery card
 * @param oneOffAdded gets an away period the owner entered for a specific day
 * @param oneOffRemoved gets an away period the owner removed, as the dashboard showed it
 * @param tripAdded gets a trip the owner entered
 * @param tripChanged gets a trip as the dashboard showed it and the owner's changed version of it
 * @param tripRemoved gets a trip the owner removed, as the dashboard showed it
 * @param openWeeklyAway opens the weekly away periods, from the footer's Away button
 * @param settings opens Settings, from the footer's Settings button
 * @param setUp opens set-up, from the message shown before the first set-up
 * @param quit quits the app, when closing the window quits it ({@link Dashboard#quitWhenClosed()})
 */
public record DashboardActions(Consumer<Percent> batteryEntered, Consumer<OneOffAwayPeriod> oneOffAdded,
        Consumer<OneOffAwayPeriod> oneOffRemoved, Consumer<Trip> tripAdded, BiConsumer<Trip, Trip> tripChanged,
        Consumer<Trip> tripRemoved, Runnable openWeeklyAway, Runnable settings, Runnable setUp, Runnable quit) {

    /**
     * Checks that every action is there.
     *
     * @throws IllegalArgumentException when an action is null
     */
    public DashboardActions {
        if (batteryEntered == null || oneOffAdded == null || oneOffRemoved == null || tripAdded == null
                || tripChanged == null || tripRemoved == null || openWeeklyAway == null || settings == null
                || setUp == null || quit == null) {
            throw new IllegalArgumentException("every dashboard action must be given");
        }
    }
}
