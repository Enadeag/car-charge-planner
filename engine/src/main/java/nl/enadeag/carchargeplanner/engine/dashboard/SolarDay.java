package nl.enadeag.carchargeplanner.engine.dashboard;

import nl.enadeag.carchargeplanner.engine.plan.Window;
import java.util.List;

/**
 * The solar windows of a trip's advice on one day.
 *
 * @param day the day, by the windows' start in the home's time zone, never null
 * @param windows the day's windows, in time order, never null; never empty
 */
public record SolarDay(DayName day, List<Window> windows) {

    /**
     * Keeps a copy of the windows.
     *
     * @throws IllegalArgumentException when there are no windows
     */
    public SolarDay {
        windows = List.copyOf(windows);
        if (windows.isEmpty()) {
            throw new IllegalArgumentException("a day of solar windows has at least one, not none on " + day);
        }
    }
}
