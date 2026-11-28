package nl.enadeag.carchargeplanner.engine.dashboard;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import java.util.List;
import java.util.Optional;

/**
 * A day's battery levels: at the start of the day and the highest after its charging, and on the day a trip the plan
 * advises on leaves, at each departure and at the end of the day, after the trip.
 *
 * <p>A level is shown as a whole percentage, rounded half up; within 0 to 100 a tie is an exact half, which
 * {@link Math#round(double)} rounds up too. Every comparison below is of such whole percentages, so two levels differ
 * exactly when the written numbers do.
 *
 * @param startPct at the start of the day, never null; today's at the moment the plan was made
 * @param afterPct after the day's charging, never null: the highest level the day reaches; the start's when the day
 *     has no charging
 * @param departurePcts at each departure of the day a trip the plan advises on leaves, in time order, after the
 *     charging before it; empty on any other day, never null
 * @param afterTripPct at the end of the day a trip the plan advises on leaves, after the trip; empty on any other
 *     day, never null
 */
public record BatteryLevels(Percent startPct, Percent afterPct, List<Percent> departurePcts,
        Optional<Percent> afterTripPct) {

    /**
     * Checks that the departures and the level after the trip come together, and copies the departures.
     *
     * @throws IllegalArgumentException when there are departures without a level after the trip, or the reverse
     * @throws NullPointerException when the departures are null or hold null
     */
    public BatteryLevels {
        departurePcts = List.copyOf(departurePcts);
        if (departurePcts.isEmpty() != afterTripPct.isEmpty()) {
            throw new IllegalArgumentException("the levels at the departures, " + departurePcts
                    + ", and the level after the trip, " + afterTripPct + ", must both be there or both be empty");
        }
    }

    /**
     * The level at the day's first departure, after the solar charging and any grid charge before it.
     *
     * @return the level; empty on a day no trip the plan advises on leaves
     */
    public Optional<Percent> departurePct() {
        return departurePcts.stream().findFirst();
    }

    /**
     * Whether the day charges before the trip: the start and the level at the first departure differ in whole
     * percent.
     *
     * @return true when they differ; false when they read the same, and on a day no trip the plan advises on leaves
     */
    public boolean chargesBeforeTrip() {
        return departurePct().filter(departure -> whole(departure) != whole(startPct)).isPresent();
    }

    /**
     * Whether the day charges after the return: its highest level is above every departure level in whole percent.
     * Charging between two trips counts as after the return; a charge after the return that stays at or under a
     * departure level does not show in the levels, so it does not count.
     *
     * @return true when the highest level is above every departure level; false otherwise, and on a day no trip the
     *     plan advises on leaves
     */
    public boolean chargesAfterReturn() {
        return !departurePcts.isEmpty()
                && departurePcts.stream().allMatch(departure -> whole(afterPct) > whole(departure));
    }

    /**
     * When the day charges, which decides the levels a client shows and how it words them.
     *
     * @return the day's charging, from {@link #chargesBeforeTrip()} and {@link #chargesAfterReturn()}
     */
    public Charging charging() {
        if (departurePcts.isEmpty()) {
            return Charging.NO_DEPARTURE;
        }
        if (chargesBeforeTrip()) {
            return chargesAfterReturn() ? Charging.BEFORE_TRIP_AND_AFTER_RETURN : Charging.BEFORE_TRIP;
        }
        return chargesAfterReturn() ? Charging.AFTER_RETURN : Charging.NONE;
    }

    private static long whole(Percent level) {
        return Math.round(level.value());
    }

    /** When a day charges, as its levels show it. */
    public enum Charging {
        /** No trip the plan advises on leaves: the start and the level after charging. */
        NO_DEPARTURE,
        /** A trip leaves and the day charges neither before it nor after the return: the start and the end. */
        NONE,
        /** The day charges before the trip only: the start, the first departure and the end. */
        BEFORE_TRIP,
        /** The day charges after the return only: the start and the highest level. */
        AFTER_RETURN,
        /**
         * The day charges before the trip and after the return: the start, the first departure and the highest level.
         */
        BEFORE_TRIP_AND_AFTER_RETURN
    }
}
