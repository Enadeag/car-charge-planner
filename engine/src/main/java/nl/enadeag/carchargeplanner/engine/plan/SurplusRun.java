package nl.enadeag.carchargeplanner.engine.plan;

import java.time.Instant;
import java.util.List;

/**
 * Back-to-back segments in which the surplus can power the charger.
 *
 * @param segments the segments in time order, each starting where the one before ends; at least one
 */
record SurplusRun(List<Segment> segments) {

    /**
     * @throws NullPointerException when the segments are null or hold null
     */
    SurplusRun {
        segments = List.copyOf(segments);
    }

    Instant start() {
        return segments.get(0).start();
    }

    Instant end() {
        return segments.get(segments.size() - 1).end();
    }

    Segment get(int index) {
        return segments.get(index);
    }

    int size() {
        return segments.size();
    }
}
