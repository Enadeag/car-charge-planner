package nl.enadeag.carchargeplanner.engine.dashboard;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import java.util.List;
import java.util.Optional;

/**
 * One day of the plan as the dashboard shows it, in the home's time zone.
 *
 * @param name how the day is named, never null; it also holds the day's date, in the home's time zone
 * @param kind where the day's charge comes from: {@link DayKind#SOLAR} exactly when it has a main window,
 *     {@link DayKind#GRID} exactly when the grid advice is why it has none
 * @param main the day's first charge window with its figures; empty when the day has none
 * @param later the day's other charge windows, in time order; empty when it has one or none
 * @param noWindow why the day has no charge window; empty exactly when it has one
 * @param battery the day's battery levels
 * @param assumedLevel the battery level the plan assumed, which the day's note tells; present on today only, and only
 *     while the owner entered no level
 * @param chart the day's expected solar power, the baseload and the charge windows, never null
 */
public record DashboardDay(
        DayName name,
        DayKind kind,
        Optional<MainCharge> main,
        List<Window> later,
        Optional<NoWindowReason> noWindow,
        BatteryLevels battery,
        Optional<Percent> assumedLevel,
        DayChart chart) {

    /**
     * Keeps a copy of the later windows and checks that the parts agree.
     *
     * @throws IllegalArgumentException when the day is {@link DayKind#SOLAR} without a main window or has one while
     *     it is another kind; when it has later windows without a main one; when it has a reason without a window
     *     together with a main window, or neither; when it is {@link DayKind#GRID} without the reason
     *     {@link NoWindowReason#GRID} or has that reason while it is another kind; or when another day than today
     *     has an assumed level
     */
    public DashboardDay {
        later = List.copyOf(later);
        if ((kind == DayKind.SOLAR) != main.isPresent()) {
            throw new IllegalArgumentException("a day has a main window exactly when it is SOLAR, not " + kind
                    + " with " + main);
        }
        if (main.isEmpty() && !later.isEmpty()) {
            throw new IllegalArgumentException("a day without a main window has no later ones, not " + later);
        }
        if (main.isPresent() == noWindow.isPresent()) {
            throw new IllegalArgumentException("a day has a reason without a window exactly when it has no window, not "
                    + noWindow + " with " + main);
        }
        if ((kind == DayKind.GRID) != noWindow.equals(Optional.of(NoWindowReason.GRID))) {
            throw new IllegalArgumentException("a day is GRID exactly when the grid advice is why it has no window, not "
                    + kind + " with " + noWindow);
        }
        if (assumedLevel.isPresent() && !(name instanceof DayName.Today)) {
            throw new IllegalArgumentException("only today has an assumed level, not " + name);
        }
    }
}
