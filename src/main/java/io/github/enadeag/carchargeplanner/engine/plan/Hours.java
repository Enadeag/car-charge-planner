package io.github.enadeag.carchargeplanner.engine.plan;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Time arithmetic on instants, so a daylight saving switch never changes how long something lasts. */
final class Hours {

    private static final double NANOS_PER_HOUR = 3_600e9;
    private static final long FIVE_MINUTES_NANOS = Duration.ofMinutes(5).toNanos();

    private Hours() {
    }

    static double between(Instant start, Instant end) {
        return Duration.between(start, end).toNanos() / NANOS_PER_HOUR;
    }

    static Instant plus(Instant moment, double hours) {
        return moment.plusNanos(Math.round(hours * NANOS_PER_HOUR));
    }

    /** The moment rounded up to whole five minutes of real time since its local midnight. */
    static Instant ceilToFiveMinutes(Instant moment, ZoneId zone) {
        var midnight = moment.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant();
        double steps = (double) Duration.between(midnight, moment).toNanos() / FIVE_MINUTES_NANOS;
        return midnight.plusNanos((long) Math.ceil(steps - 1e-9) * FIVE_MINUTES_NANOS);
    }
}
