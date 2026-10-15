package io.github.enadeag.carchargeplanner.engine.plan;

import java.time.Instant;

/** A forecast step cut off at "now", with its usable surplus. */
record Segment(Instant start, Instant end, double usableKw) {

    double hours() {
        return Hours.between(start, end);
    }
}
