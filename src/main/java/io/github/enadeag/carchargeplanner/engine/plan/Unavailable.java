package io.github.enadeag.carchargeplanner.engine.plan;

import java.time.Instant;

/** A time in which no new charge window may be planned, such as an announced window that is running. */
record Unavailable(Instant start, Instant end) {

    boolean overlaps(Instant from, Instant to) {
        return start.isBefore(to) && from.isBefore(end);
    }
}
