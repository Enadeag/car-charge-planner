package nl.enadeag.carchargeplanner.desktop.dashboard.trip;

import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.Instant;
import java.time.ZoneId;

/**
 * What the trips the owner entered are told and entered in: the time zone, the moment of the plan they belong to and
 * the units the distances are in. Each day carries its own trips, as {@link Listed}.
 *
 * @param zone the time zone of the owner's home
 * @param madeAt the moment the plan was made
 * @param units the units the distances are shown and typed in
 */
public record TripsModel(ZoneId zone, Instant madeAt, Units units) {

    /**
     * One trip in words.
     *
     * @param trip the trip as the engine stores it
     * @param origin where the trip comes from
     * @param text the trip's times and distance, such as "Away 07:30 – 18:00 · 300 km there and back"
     * @param originText where the trip comes from, such as "Entered by hand"
     * @param changeSpoken what a screen reader calls its change button, such as "Change the trip Away 07:30 – 18:00 ·
     *     300 km there and back"
     * @param removeSpoken what a screen reader calls its remove button, in the same way
     */
    public record Listed(Trip trip, TripOrigin origin, String text, String originText, String changeSpoken,
            String removeSpoken) {
    }
}
