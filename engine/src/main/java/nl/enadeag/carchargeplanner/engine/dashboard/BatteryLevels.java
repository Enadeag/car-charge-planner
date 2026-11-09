package nl.enadeag.carchargeplanner.engine.dashboard;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import java.util.Optional;

/**
 * A day's battery levels: at the start of the day and after its charging, and on the day a trip the plan advises on
 * leaves, at the end of the day, after the trip.
 *
 * @param start at the start of the day; today's at the moment the plan was made
 * @param after after the day's charging; the start's when the day has none
 * @param afterTrip at the end of the day a trip the plan advises on leaves, after the trip; empty on any other day
 * @param chargesBeforeTrip true when the start and the level after charging differ in whole percent, rounded half up
 */
public record BatteryLevels(Percent start, Percent after, Optional<Percent> afterTrip, boolean chargesBeforeTrip) {

    /**
     * The levels of a day no trip the plan advises on leaves.
     *
     * @param start the level at the start of the day, never null
     * @param after the level after the day's charging, never null
     * @return the levels, without a level after a trip
     */
    public static BatteryLevels of(Percent start, Percent after) {
        return new BatteryLevels(start, after, Optional.empty(), differ(start, after));
    }

    /**
     * The levels of the day a trip the plan advises on leaves. Without a charge before the trip, which is when the
     * start and the level after charging read the same in whole percent, a client shows only the start and the level
     * after the trip.
     *
     * @param start the level at the start of the day, never null
     * @param after the level after the day's charging, never null
     * @param afterTrip the level at the end of the day, after the trip, never null
     * @return the levels, with the one after the trip
     */
    public static BatteryLevels leaving(Percent start, Percent after, Percent afterTrip) {
        return new BatteryLevels(start, after, Optional.of(afterTrip), differ(start, after));
    }

    /**
     * Both apps write a level as a whole percentage rounded half up. Within 0 to 100 a tie is an exact half, which
     * {@link Math#round(double)} rounds up too, so this agrees with what they write.
     */
    private static boolean differ(Percent start, Percent after) {
        return Math.round(start.value()) != Math.round(after.value());
    }
}
