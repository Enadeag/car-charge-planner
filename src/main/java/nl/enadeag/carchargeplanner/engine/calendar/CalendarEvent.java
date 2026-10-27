package nl.enadeag.carchargeplanner.engine.calendar;

import nl.enadeag.carchargeplanner.engine.config.Coordinates;
import java.time.LocalDate;
import java.util.Optional;

/**
 * One occurrence of an event in the calendar: an event that does not repeat, or one repeat of an event that does.
 *
 * @param uid the event's UID, which all its occurrences share; not blank
 * @param occurrence the date the occurrence was planned on by the event's repeat, also when it was moved to another
 *     time; the date of its start in the home's time zone, or its first day when it lasts all day
 * @param time when the occurrence takes place
 * @param location the event's location as written in the calendar; empty when it has none
 * @param coordinates the event's own coordinates, from its {@code GEO} field or Apple Calendar's structured location;
 *     empty when it has neither
 * @param title the event's title; empty when it has none
 * @param description the event's description; empty when it has none
 */
public record CalendarEvent(String uid, LocalDate occurrence, EventTime time, Optional<String> location,
        Optional<Coordinates> coordinates, String title, String description) {

    /**
     * Checks the UID.
     *
     * @throws IllegalArgumentException when the UID is blank
     */
    public CalendarEvent {
        if (uid.isBlank()) {
            throw new IllegalArgumentException("an event needs a UID");
        }
    }
}
