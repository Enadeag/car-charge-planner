package io.github.enadeag.carchargeplanner.engine.plan;

import java.time.Instant;

/** A recommended charge window at one constant charge power. */
public record Window(Instant start, Instant end, int amps, double powerKw, double solarKwh, double importKwh) {

    public double solarShare() {
        double total = solarKwh + importKwh;
        return total > 0 ? solarKwh / total : 0;
    }
}
