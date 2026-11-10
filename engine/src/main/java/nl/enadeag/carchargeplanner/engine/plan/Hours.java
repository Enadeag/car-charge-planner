package nl.enadeag.carchargeplanner.engine.plan;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Time arithmetic on instants, so a daylight saving switch never changes how long something lasts. The planner and the
 * dashboard's model share it.
 */
public final class Hours {

    private static final double NANOS_PER_HOUR = 3_600e9;
    private static final long FIVE_MINUTES_NANOS = Duration.ofMinutes(5).toNanos();

    private Hours() {
    }

    /**
     * The real time between two moments.
     *
     * @param start the moment to count from
     * @param end the moment to count to, usually the later one
     * @return the hours from start to end; negative when end is before start
     */
    public static double between(Instant start, Instant end) {
        return Duration.between(start, end).toNanos() / NANOS_PER_HOUR;
    }

    /**
     * A moment some hours later, in real time.
     *
     * @param hours the hours to add, rounded to whole nanoseconds
     */
    static Instant plus(Instant moment, double hours) {
        return moment.plusNanos(Math.round(hours * NANOS_PER_HOUR));
    }

    /**
     * Rounds a moment up to whole five minutes of real time since its local midnight.
     *
     * @return the moment itself when it is already on a five-minute mark, otherwise the next one
     */
    static Instant ceilToFiveMinutes(Instant moment, ZoneId zone) {
        var midnight = moment.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant();
        double steps = (double) Duration.between(midnight, moment).toNanos() / FIVE_MINUTES_NANOS;
        return midnight.plusNanos((long) Math.ceil(steps - 1e-9) * FIVE_MINUTES_NANOS);
    }

    static Instant earliest(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    static Instant latest(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }
}
