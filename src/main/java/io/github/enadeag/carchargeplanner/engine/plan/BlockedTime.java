package io.github.enadeag.carchargeplanner.engine.plan;

import java.time.Instant;

/**
 * A stretch of time in which no new charging is planned: an away period, or a charge window that is already planned.
 *
 * @param start the first moment of the time
 * @param end the end of the time
 */
record BlockedTime(Instant start, Instant end) {

    /**
     * Whether another stretch of time shares some of this one.
     *
     * @param from the first moment of the other stretch
     * @param to the end of the other stretch
     * @return true when the two overlap; stretches that only touch do not
     */
    boolean overlaps(Instant from, Instant to) {
        return start.isBefore(to) && from.isBefore(end);
    }
}
