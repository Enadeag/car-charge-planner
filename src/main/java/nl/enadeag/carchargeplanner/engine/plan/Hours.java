package nl.enadeag.carchargeplanner.engine.plan;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Time arithmetic on instants, so a daylight saving switch never changes how long something lasts. */
final class Hours {

    private static final double NANOS_PER_HOUR = 3_600e9;
    private static final long FIVE_MINUTES_NANOS = Duration.ofMinutes(5).toNanos();

    private Hours() {
    }

    /**
     * The real time between two moments.
     *
     * @param start the earlier moment
     * @param end the later moment
     * @return the hours from start to end
     */
    static double between(Instant start, Instant end) {
        return Duration.between(start, end).toNanos() / NANOS_PER_HOUR;
    }

    /**
     * A moment some hours later, in real time.
     *
     * @param moment the moment to start from
     * @param hours the hours to add, rounded to whole nanoseconds
     * @return the later moment
     */
    static Instant plus(Instant moment, double hours) {
        return moment.plusNanos(Math.round(hours * NANOS_PER_HOUR));
    }

    /**
     * Rounds a moment up to whole five minutes of real time since its local midnight.
     *
     * @param moment the moment to round
     * @param zone the time zone that decides which midnight counts
     * @return the moment itself when it is already on a five-minute mark, otherwise the next one
     */
    static Instant ceilToFiveMinutes(Instant moment, ZoneId zone) {
        var midnight = moment.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant();
        double steps = (double) Duration.between(midnight, moment).toNanos() / FIVE_MINUTES_NANOS;
        return midnight.plusNanos((long) Math.ceil(steps - 1e-9) * FIVE_MINUTES_NANOS);
    }

    /**
     * The earlier of two moments.
     *
     * @param a one moment
     * @param b the other moment
     * @return the earlier of the two
     */
    static Instant earliest(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    /**
     * The later of two moments.
     *
     * @param a one moment
     * @param b the other moment
     * @return the later of the two
     */
    static Instant latest(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }
}
