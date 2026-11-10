package nl.enadeag.carchargeplanner.engine.plan;

import java.time.Instant;

/**
 * A stretch of time in which no new charging is planned: an away period,
 * or a charge window that is already planned.
 */
record BlockedTime(Instant start, Instant end) {

    /**
     * @return true when the two overlap; stretches that only touch do not
     */
    boolean overlaps(Instant from, Instant to) {
        return start.isBefore(to) && from.isBefore(end);
    }
}
