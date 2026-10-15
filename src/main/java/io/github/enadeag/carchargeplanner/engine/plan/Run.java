package io.github.enadeag.carchargeplanner.engine.plan;

import java.time.Instant;
import java.util.List;

/**
 * Back-to-back segments in which the surplus can power the charger.
 *
 * @param segments the segments in time order, each starting where the one before ends; at least one
 */
record Run(List<Segment> segments) {

    /**
     * Copies the list of segments.
     *
     * @throws NullPointerException when the segments are null or hold null
     */
    Run {
        segments = List.copyOf(segments);
    }

    /**
     * When the run starts.
     *
     * @return the start of the first segment
     */
    Instant start() {
        return segments.getFirst().start();
    }

    /**
     * When the run ends.
     *
     * @return the end of the last segment
     */
    Instant end() {
        return segments.getLast().end();
    }

    /**
     * One segment of the run.
     *
     * @param index the position of the segment, from 0
     * @return the segment
     * @throws IndexOutOfBoundsException when the index is not below {@link #size()}
     */
    Segment get(int index) {
        return segments.get(index);
    }

    /**
     * How many segments the run has.
     *
     * @return the number of segments
     */
    int size() {
        return segments.size();
    }
}
