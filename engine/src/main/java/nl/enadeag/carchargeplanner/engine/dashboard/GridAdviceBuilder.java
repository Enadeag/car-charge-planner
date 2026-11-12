package nl.enadeag.carchargeplanner.engine.dashboard;

import nl.enadeag.carchargeplanner.engine.plan.GridAdvice;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/** The plan's grid advice as the dashboard shows it, with its days named from the day the plan was made. */
final class GridAdviceBuilder {

    private GridAdviceBuilder() {
    }

    static Optional<GridAdviceView> advice(PlanParts parts) {
        var zone = parts.zone();
        var today = parts.today();
        return parts.gridAdvice().map(advice -> new GridAdviceView(DayName.of(advice.day(), today),
                advice.energyKwh(), advice.endLevelPct(), window(advice, zone, today)));
    }

    /** The window's day is that of its start in the home's zone; the charge limit comes with the window. */
    private static Optional<GridWindow> window(GridAdvice advice, ZoneId zone, LocalDate today) {
        return advice.window().map(window -> new GridWindow(window,
                DayName.of(window.start().atZone(zone).toLocalDate(), today), advice.chargeLimitPct().orElseThrow()));
    }
}
