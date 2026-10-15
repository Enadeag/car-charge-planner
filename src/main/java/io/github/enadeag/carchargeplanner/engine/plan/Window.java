package io.github.enadeag.carchargeplanner.engine.plan;

import java.time.Instant;

/** A recommended charge window at one constant charge power. */
public record Window(Instant start, Instant end, int amps, double powerKw, double solarKwh, double importKwh) {

    public double solarShare() {
        double total = solarKwh + importKwh;
        return total > 0 ? solarKwh / total : 0;
    }

    boolean isRunningAt(Instant moment) {
        return !start.isAfter(moment) && end.isAfter(moment);
    }

    /** The same window with only the energy still to come after the moment: start, end and power stay. */
    Window remainingAfter(Instant moment) {
        double share = Hours.between(moment, end) / Hours.between(start, end);
        return new Window(start, end, amps, powerKw, solarKwh * share, importKwh * share);
    }
}
