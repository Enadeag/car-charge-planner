package nl.enadeag.carchargeplanner.engine.dashboard;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import java.util.List;
import java.util.Optional;

/**
 * One day of the plan as the dashboard shows it, in the home's time zone.
 *
 * @param name how the day is named, never null; it also holds the day's date, in the home's time zone
 * @param main the day's first charge window with its figures; never null, empty when the day has none
 * @param later the day's other charge windows, in time order; never null, empty when it has one or none
 * @param noWindow why the day has no charge window; never null, empty exactly when it has one
 * @param battery the day's battery levels, never null
 * @param assumedLevel the battery level the plan assumed, which the day's note tells; never null, empty unless it is
 *     today and the owner entered no level
 * @param chart the day's expected solar power, the baseload and the charge windows, never null
 */
public record DashboardDay(
        DayName name,
        Optional<MainCharge> main,
        List<Window> later,
        Optional<NoWindowReason> noWindow,
        BatteryLevels battery,
        Optional<Percent> assumedLevel,
        DayChart chart) {

    /**
     * Keeps a copy of the later windows and checks that the parts agree.
     *
     * @throws IllegalArgumentException when the day has later windows without a main one; when it has a reason
     *     without a window together with a main window, or neither; or when another day than today has an assumed
     *     level
     */
    public DashboardDay {
        later = List.copyOf(later);
        if (main.isEmpty() && !later.isEmpty()) {
            throw new IllegalArgumentException("a day without a main window has no later ones, not " + later);
        }
        if (main.isPresent() == noWindow.isPresent()) {
            throw new IllegalArgumentException("a day has a reason without a window exactly when it has no window, not "
                    + noWindow + " with " + main);
        }
        if (assumedLevel.isPresent() && !(name instanceof DayName.Today)) {
            throw new IllegalArgumentException("only today has an assumed level, not " + name);
        }
    }

    /**
     * Where the day's charge comes from.
     *
     * @return {@link DayKind#SOLAR} when the day has a main window, {@link DayKind#GRID} when the grid advice is why it
     *     has none, otherwise {@link DayKind#NONE}
     */
    public DayKind kind() {
        if (main.isPresent()) {
            return DayKind.SOLAR;
        }
        return noWindow.equals(Optional.of(NoWindowReason.GRID)) ? DayKind.GRID : DayKind.NONE;
    }
}
