package nl.enadeag.carchargeplanner.engine.dashboard;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * What the dashboard shows of a plan, as numbers, hours and flags; each client writes the words and draws it.
 *
 * @param madeAt when the plan was made, never null; its day in {@code zone} is today
 * @param zone the home's time zone, never null, in which the days and times are meant
 * @param days the plan's days, today first, in the plan's order; may be empty
 * @param levels the minimum and target charge levels, which every day's battery bar marks
 * @param gridAdvice the plan's grid advice; never null, empty when the plan gives none
 */
public record DashboardView(Instant madeAt, ZoneId zone, List<DashboardDay> days, ChargeLevels levels,
        Optional<GridAdviceView> gridAdvice) {

    /** Keeps a copy of the days. */
    public DashboardView {
        days = List.copyOf(days);
    }

    /**
     * The dashboard of a plan, from its parts. Clients use {@code Outlook.Planned.dashboard}; this is the engine's own
     * seam. Nothing reads the clock: "now" is the moment the plan was made.
     *
     * @param parts the plan's parts, never null
     * @return one dashboard day per plan day, in the same order, and the plan's grid advice
     */
    public static DashboardView of(PlanParts parts) {
        var charts = new ChartBuilder(parts);
        var cards = new DayBuilder(parts);
        return new DashboardView(parts.madeAt(), parts.zone(),
                parts.days().stream().map(day -> cards.day(day, charts.chart(day))).toList(),
                new ChargeLevels(parts.minimumPct(), parts.targetPct()), GridAdviceBuilder.advice(parts));
    }
}
