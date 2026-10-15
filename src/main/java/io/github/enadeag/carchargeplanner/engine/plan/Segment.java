package io.github.enadeag.carchargeplanner.engine.plan;

import java.time.Instant;

/**
 * A forecast step cut off at "now", with its usable surplus.
 *
 * @param start the first moment of the segment
 * @param end the end of the segment
 * @param usableKw the usable surplus in kW, 0 or more
 */
record Segment(Instant start, Instant end, double usableKw) {

    /**
     * The length of the segment.
     *
     * @return the hours from start to end
     */
    double hours() {
        return Hours.between(start, end);
    }
}
