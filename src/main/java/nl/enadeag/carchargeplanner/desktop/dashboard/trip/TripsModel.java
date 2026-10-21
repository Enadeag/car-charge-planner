package nl.enadeag.carchargeplanner.desktop.dashboard.trip;

import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * The trips the owner entered, in words, with the time zone, the moment of the plan they belong to and the units the
 * distances are in.
 *
 * @param zone the time zone of the owner's home
 * @param madeAt the moment the plan was made
 * @param units the units the distances are shown and typed in
 * @param trips the stored trips, in order of their departure; may be empty
 */
public record TripsModel(ZoneId zone, Instant madeAt, Units units, List<Listed> trips) {

    /**
     * Keeps a copy of the trips, so the model cannot change afterwards.
     */
    public TripsModel {
        trips = List.copyOf(trips);
    }

    /**
     * The trips the car is away for on a day.
     *
     * @param day a day in the owner's home
     * @return the trips from whose day of departure to whose day of return the day is, in order of their departure
     */
    public List<Listed> on(LocalDate day) {
        return trips.stream().filter(trip -> !day.isBefore(trip.firstDay()) && !day.isAfter(trip.lastDay())).toList();
    }

    /**
     * One trip in words.
     *
     * @param trip the trip as the engine stores it
     * @param firstDay the day of its departure, in the owner's home
     * @param lastDay the last day it takes, in the owner's home: the day of its return, or the day before when it
     *     returns at midnight
     * @param origin where the trip comes from
     * @param text the trip's times and distance, such as "Away 07:30 – 18:00 · 300 km there and back"
     * @param originText where the trip comes from, such as "Entered by hand"
     * @param changeSpoken what a screen reader calls its change button, such as "Change the trip Away 07:30 – 18:00 ·
     *     300 km there and back"
     * @param removeSpoken what a screen reader calls its remove button, in the same way
     */
    public record Listed(Trip trip, LocalDate firstDay, LocalDate lastDay, TripOrigin origin, String text,
            String originText, String changeSpoken, String removeSpoken) {
    }
}
