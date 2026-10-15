package io.github.enadeag.carchargeplanner.engine.plan;

import java.time.Instant;
import java.util.List;

/** Back-to-back segments in which the surplus can power the charger. */
record Run(List<Segment> segments) {

    Run {
        segments = List.copyOf(segments);
    }

    Instant start() {
        return segments.getFirst().start();
    }

    Segment get(int index) {
        return segments.get(index);
    }

    int size() {
        return segments.size();
    }
}
