package nl.enadeag.carchargeplanner.engine.dashboard;

import nl.enadeag.carchargeplanner.engine.plan.GridAdvice;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/** The plan's grid advice as the dashboard shows it, with its days named from the day the plan was made. */
final class GridAdviceBuilder {

    private GridAdviceBuilder() {
    }

    /**
     * The grid advice of one plan.
     *
     * @param parts the plan's parts, never null
     * @return the advice; empty when the plan gives none
     */
    static Optional<GridAdviceView> advice(PlanParts parts) {
        var zone = parts.zone();
        var today = parts.madeAt().atZone(zone).toLocalDate();
        return parts.gridAdvice().map(advice -> new GridAdviceView(DayName.of(advice.day(), today),
                advice.energyKwh(), advice.endLevelPct(), parts.minimumPct(), charge(advice, zone, today)));
    }

    /** The window's day is that of its start in the home's zone; the charge limit comes with the window. */
    private static Optional<GridAdviceView.Charge> charge(GridAdvice advice, ZoneId zone, LocalDate today) {
        return advice.window().map(window -> new GridAdviceView.Charge(window,
                DayName.of(window.start().atZone(zone).toLocalDate(), today), advice.chargeLimitPct().orElseThrow()));
    }
}
