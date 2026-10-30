package nl.enadeag.carchargeplanner.engine.plan;

import java.time.Instant;

/**
 * A recommended charge window at one constant charge power.
 *
 * @param start the first moment of the window
 * @param end the end of the window
 * @param amps the current per phase in A
 * @param powerKw the charge power in kW, measured at the wall
 * @param solarKwh the energy the window takes from the solar surplus in kWh
 * @param importKwh the energy the window takes from the grid in kWh
 */
public record Window(Instant start, Instant end, int amps, double powerKw, double solarKwh, double importKwh) {

    /**
     * How much of the window's energy is solar.
     *
     * @return the share from 0 to 1; 0 when the window takes no energy
     */
    public double solarShare() {
        double total = solarKwh + importKwh;
        return total > 0 ? solarKwh / total : 0;
    }

    /**
     * Whether the window is going on at a moment.
     *
     * @param moment the moment to test
     * @return true from the start up to, but not including, the end
     */
    boolean isRunningAt(Instant moment) {
        return !start.isAfter(moment) && end.isAfter(moment);
    }

    /**
     * The same window with only the energy still to come after a moment: start, end and power stay.
     *
     * @param moment a moment inside the window
     * @return the window with its solar and import energy cut to what is left
     */
    Window remainingAfter(Instant moment) {
        double share = Hours.between(moment, end) / Hours.between(start, end);
        return new Window(start, end, amps, powerKw, solarKwh * share, importKwh * share);
    }
}
