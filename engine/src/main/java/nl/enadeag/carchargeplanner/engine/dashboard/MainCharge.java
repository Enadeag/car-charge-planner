package nl.enadeag.carchargeplanner.engine.dashboard;

import nl.enadeag.carchargeplanner.engine.plan.Window;
import java.util.OptionalDouble;

/**
 * A day's first charge window, the one its card is about, with what it takes from the grid when that is enough to
 * show.
 *
 * @param window the charge window, never null, with what to set, its energy and its solar share
 * @param gridKwh what the window takes from the grid in kWh, finite and more than 0; never null, empty when it is too
 *     little to show
 */
public record MainCharge(Window window, OptionalDouble gridKwh) {

    /**
     * Checks the grid energy.
     *
     * @throws IllegalArgumentException when the grid energy is present and not finite and more than 0
     */
    public MainCharge {
        if (gridKwh.isPresent() && (!(gridKwh.getAsDouble() > 0) || Double.isInfinite(gridKwh.getAsDouble()))) {
            throw new IllegalArgumentException("the grid energy must be finite and more than 0, not "
                    + gridKwh.getAsDouble() + " kWh");
        }
    }
}
