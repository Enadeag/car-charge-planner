package nl.enadeag.carchargeplanner.engine.dashboard;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import java.util.Optional;

/**
 * A day's battery levels: at the start of the day and after its charging, and on the day a trip the plan advises on
 * leaves, at the end of the day, after the trip.
 *
 * @param startPct at the start of the day, never null; today's at the moment the plan was made
 * @param afterPct after the day's charging, never null; the start's when the day has none
 * @param afterTripPct at the end of the day a trip the plan advises on leaves, after the trip; empty on any other
 *     day, never null
 */
public record BatteryLevels(Percent startPct, Percent afterPct, Optional<Percent> afterTripPct) {

    /**
     * Whether the day charges before the trip, which is when the start and the level after charging differ in whole
     * percent. Without it a client shows only the start and the level after the trip.
     *
     * <p>A level is shown as a whole percentage, rounded half up; within 0 to 100 a tie is an exact half, which
     * {@link Math#round(double)} rounds up too.
     *
     * @return true when the start and the level after charging differ in whole percent; false when they read the same
     */
    public boolean chargesBeforeTrip() {
        return Math.round(startPct.value()) != Math.round(afterPct.value());
    }
}
