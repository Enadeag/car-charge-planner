package nl.enadeag.carchargeplanner.desktop.dashboard.trip;

import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Whether what the owner entered for a trip can be saved, and as which trip: the times must be times, the departure
 * must not be before a trip's earliest moment, the return must be after the departure, not have passed and not be
 * later than a trip may last, and the distance must be a number more than 0 and at most a trip's longest, in the
 * owner's units.
 */
final class TripCheck {

    /** Both of the app's languages write a time on a 24-hour clock; a leading zero may be left out. */
    private static final DateTimeFormatter TYPED = DateTimeFormatter.ofPattern("H:mm", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    private final ZoneId zone;
    private final InstantSource clock;
    private final Units units;
    private final Messages messages;

    /**
     * Makes the check.
     *
     * @param zone the time zone of the owner's home, in which the days and times are read
     * @param clock tells the time at which a trip must not have returned yet
     * @param units the units the distance is typed in
     * @param messages reads the distance the way the owner's language writes numbers
     */
    TripCheck(ZoneId zone, InstantSource clock, Units units, Messages messages) {
        this.zone = zone;
        this.clock = clock;
        this.units = units;
        this.messages = messages;
    }

    /**
     * The trip as entered.
     *
     * @param entered what the owner entered
     * @return the trip, or why it cannot be saved and where to fix it; the first problem in the order of the fields
     */
    Checked check(Entered entered) {
        var leaves = time(entered.departureTime());
        if (leaves.isEmpty()) {
            return new Checked.Refused(TextKey.PROBLEM_NOT_A_TIME, Field.DEPARTURE_TIME);
        }
        var back = time(entered.returnTime());
        if (back.isEmpty()) {
            return new Checked.Refused(TextKey.PROBLEM_NOT_A_TIME, Field.RETURN_TIME);
        }
        var departure = moment(entered.departureDay(), leaves.get());
        var returnTime = moment(entered.returnDay(), back.get());
        if (departure.isBefore(Trip.EARLIEST_INSTANT)) {
            // only a computer whose clock was put back near 1970 offers such a day, and such a trip is over
            return new Checked.Refused(TextKey.TRIP_PASSED, Field.DEPARTURE_TIME);
        }
        if (!returnTime.isAfter(departure)) {
            return new Checked.Refused(TextKey.TRIP_RETURN_BEFORE_DEPARTURE, Field.RETURN_DAY);
        }
        if (!returnTime.isAfter(clock.instant())) {
            return new Checked.Refused(TextKey.TRIP_PASSED, Field.RETURN_DAY);
        }
        if (Duration.between(departure, returnTime).compareTo(Trip.MAX_LENGTH) > 0) {
            return tooLong();
        }
        var typed = messages.parseNumber(entered.distance());
        if (typed.isEmpty()) {
            return new Checked.Refused(TextKey.TRIP_DISTANCE_NOT_A_NUMBER, Field.DISTANCE);
        }
        if (typed.getAsDouble() <= 0) {
            return new Checked.Refused(TextKey.TRIP_DISTANCE_NOT_POSITIVE, Field.DISTANCE);
        }
        var km = units.km(typed.getAsDouble());
        if (!Double.isFinite(km)) {
            return new Checked.Refused(TextKey.TRIP_DISTANCE_NOT_A_NUMBER, Field.DISTANCE);
        }
        if (km > Trip.MAX_DISTANCE_KM) {
            // rounded down, so a trip of the distance named is never refused
            var longest = Math.floor(units.distance(Trip.MAX_DISTANCE_KM));
            return new Checked.Refused(TextKey.TRIP_DISTANCE_TOO_FAR, Field.DISTANCE,
                    List.of(messages.number(longest, 0), units.distanceUnit()));
        }
        if (!returnTime.isBefore(Trip.INSTANTS_END)) {
            // only a trip edited by hand to return at the end of the year 9999 offers such a day
            return tooLong();
        }
        return new Checked.Valid(new Trip(departure, returnTime, km, entered.canChargeAtDestination(),
                entered.notWithThisCar()));
    }

    /** A return later than a trip may last, at the return; the most days go as a number, for the text's plural. */
    private static Checked.Refused tooLong() {
        return new Checked.Refused(TextKey.TRIP_TOO_LONG, Field.RETURN_DAY, List.of(Trip.MAX_LENGTH.toDays()));
    }

    /** A time that the clock skips when it is put forward counts as the time after the gap. */
    private Instant moment(LocalDate day, LocalTime time) {
        return day.atTime(time).atZone(zone).toInstant();
    }

    private static Optional<LocalTime> time(String text) {
        try {
            return Optional.of(LocalTime.parse(text.strip(), TYPED));
        } catch (DateTimeParseException _) {
            return Optional.empty();
        }
    }

    /**
     * What the owner entered for a trip.
     *
     * @param departureDay the day the car leaves, in the owner's home
     * @param departureTime the time it leaves as typed, such as "7:30" or "07:30"; spaces around it are ignored
     * @param returnDay the day the car is back
     * @param returnTime the time it is back, typed in the same way
     * @param distance the distance there and back as typed, in the owner's units and language
     * @param canChargeAtDestination whether the owner can charge the car where the trip goes
     * @param notWithThisCar whether the owner makes the trip without this car
     */
    record Entered(LocalDate departureDay, String departureTime, LocalDate returnDay, String returnTime,
            String distance, boolean canChargeAtDestination, boolean notWithThisCar) {
    }

    /** The field where the owner fixes a problem. */
    enum Field {
        /** The time of departure. */
        DEPARTURE_TIME,
        /** The day of return, the first of the return's fields. */
        RETURN_DAY,
        /** The time of return. */
        RETURN_TIME,
        /** The distance there and back. */
        DISTANCE
    }

    /** What the entry holds: a trip that can be saved, or why it cannot. */
    sealed interface Checked {

        /**
         * A trip that can be saved.
         *
         * @param trip the trip entered
         */
        record Valid(Trip trip) implements Checked {
        }

        /**
         * Why the entered trip cannot be saved.
         *
         * @param why the text of the problem
         * @param where the field where the owner fixes it
         * @param values what the text puts in its places: texts written already, or whole numbers that a
         *     {@code choice} place picks a plural by; empty for a text without any
         */
        record Refused(TextKey why, Field where, List<Object> values) implements Checked {

            /** Copies the values, so the refusal cannot change. */
            public Refused {
                values = List.copyOf(values);
            }

            /**
             * A refusal whose text has no places to fill.
             *
             * @param why the text of the problem
             * @param where the field where the owner fixes it
             */
            public Refused(TextKey why, Field where) {
                this(why, where, List.of());
            }
        }
    }
}
