package nl.enadeag.carchargeplanner.desktop.dashboard.trip;

import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.NumberText;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import nl.enadeag.carchargeplanner.engine.trip.TripEntryRules;
import nl.enadeag.carchargeplanner.engine.trip.TripProblem;
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
 * Whether what the owner entered for a trip can be saved, and as which trip: the times must be times, the distance is
 * read in the owner's units as a whole number, as Settings reads one, and the rest are the engine's
 * {@link TripEntryRules}, each problem shown in the owner's words at the field where the owner fixes it.
 */
final class TripCheck {

    /** Both of the app's languages write a time on a 24-hour clock; a leading zero may be left out. */
    private static final DateTimeFormatter TYPED = DateTimeFormatter.ofPattern("H:mm", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    private final ZoneId zone;
    private final InstantSource clock;
    private final Units units;
    private final Messages messages;
    private final NumberText numbers;

    /**
     * Makes the check.
     *
     * @param zone the time zone of the owner's home, in which the days and times are read
     * @param clock tells the time at which a trip must not have returned yet
     * @param units the units the distance is typed in
     * @param messages writes the numbers the way the owner's language does and says the problems
     */
    TripCheck(ZoneId zone, InstantSource clock, Units units, Messages messages) {
        this.zone = zone;
        this.clock = clock;
        this.units = units;
        this.messages = messages;
        this.numbers = new NumberText(messages);
    }

    /**
     * The trip as entered.
     *
     * @param entered what the owner entered
     * @return the trip, or why it cannot be saved and where to fix it; the first problem in the order of the fields.
     *     The distance is a whole number of the owner's units, without a thousands separator; whole miles are stored
     *     as their exact km, and a number too long to read is too far.
     */
    Checked check(Entered entered) {
        return switch (numbers.read(entered.distance(), 0, 0, true)) {
            case NumberText.Read.Number(var value) -> check(entered, units.km(value));
            case NumberText.Read.OutOfRange() -> check(entered, Double.MAX_VALUE);
            case NumberText.Read.Wrong(var problem) -> check(entered, Double.NaN,
                    problem == TextKey.PROBLEM_NOT_A_NUMBER ? TextKey.TRIP_DISTANCE_NOT_A_NUMBER : problem);
        };
    }

    /**
     * The trip as entered, with its distance read already: also the stored distance of a trip being changed, whose
     * field shows it rounded, or in miles, and which the owner left as it showed.
     *
     * @param entered what the owner entered; its distance as typed is not read
     * @param distanceKm the distance there and back in km
     * @return the trip, or why it cannot be saved and where to fix it, as {@link #check(Entered)}
     */
    Checked check(Entered entered, double distanceKm) {
        return check(entered, distanceKm, TextKey.TRIP_DISTANCE_NOT_A_NUMBER);
    }

    /** A distance typed that cannot be read is NaN; it is refused with {@code unreadable} in its place. */
    private Checked check(Entered entered, double distanceKm, TextKey unreadable) {
        var leaves = time(entered.departureTime());
        if (leaves.isEmpty()) {
            return new Checked.Refused(TextKey.PROBLEM_NOT_A_TIME, Field.DEPARTURE_TIME);
        }
        var back = time(entered.returnTime());
        if (back.isEmpty()) {
            return new Checked.Refused(TextKey.PROBLEM_NOT_A_TIME, Field.RETURN_TIME);
        }
        var read = new TripEntryRules.Entered(entered.departureDay().atTime(leaves.get()),
                entered.returnDay().atTime(back.get()), distanceKm, entered.canChargeAtDestination(),
                entered.notWithThisCar());
        return switch (TripEntryRules.check(read, clock.instant(), zone)) {
            case TripEntryRules.Checked.Valid(var trip) -> new Checked.Valid(trip);
            case TripEntryRules.Checked.Refused(var problem) -> refused(problem, unreadable);
        };
    }

    private Checked.Refused refused(TripProblem problem, TextKey unreadable) {
        return switch (problem) {
            // only a computer whose clock was put back near 1970 offers such a day, and such a trip is over
            case DEPARTURE_BEFORE_EARLIEST -> new Checked.Refused(TextKey.TRIP_PASSED, Field.DEPARTURE_TIME);
            case RETURN_NOT_AFTER_DEPARTURE ->
                    new Checked.Refused(TextKey.TRIP_RETURN_BEFORE_DEPARTURE, Field.RETURN_DAY);
            case PASSED -> new Checked.Refused(TextKey.TRIP_PASSED, Field.RETURN_DAY);
            // the most days go as a number, for the text's plural
            case TOO_LONG -> new Checked.Refused(TextKey.TRIP_TOO_LONG, Field.RETURN_DAY,
                    List.of(Trip.MAX_LENGTH.toDays()));
            case DISTANCE_NOT_POSITIVE -> new Checked.Refused(TextKey.TRIP_DISTANCE_NOT_POSITIVE, Field.DISTANCE);
            case DISTANCE_NOT_A_NUMBER -> new Checked.Refused(unreadable, Field.DISTANCE);
            // rounded down, so a trip of the distance named is never refused
            case DISTANCE_TOO_FAR -> new Checked.Refused(TextKey.TRIP_DISTANCE_TOO_FAR, Field.DISTANCE,
                    List.of(messages.number(Math.floor(units.distance(Trip.MAX_DISTANCE_KM)), 0),
                            units.distanceUnit()));
        };
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
     * @param distance the distance there and back as typed, a whole number in the owner's units
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
