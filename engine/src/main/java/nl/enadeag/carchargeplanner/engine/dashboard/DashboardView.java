package nl.enadeag.carchargeplanner.engine.dashboard;

import java.time.LocalDate;
import java.util.List;

/**
 * What the dashboard shows of a plan, as numbers, hours and flags; each client writes the words and draws it.
 *
 * @param days the plan's days, today first, in the plan's order; may be empty
 */
public record DashboardView(List<DashboardDay> days) {

    /** Keeps a copy of the days. */
    public DashboardView {
        days = List.copyOf(days);
    }

    /**
     * The dashboard of a plan, from its parts. Clients use {@code Outlook.Planned.dashboard}; this is the engine's own
     * seam. Nothing reads the clock: "now" is the moment the plan was made.
     *
     * @param parts the plan's parts, never null
     * @return one dashboard day per plan day, in the same order
     */
    public static DashboardView of(PlanParts parts) {
        LocalDate today = parts.madeAt().atZone(parts.zone()).toLocalDate();
        var charts = new ChartBuilder(parts);
        return new DashboardView(parts.days().stream()
                .map(day -> new DashboardDay(DayName.of(day.date(), today), charts.chart(day)))
                .toList());
    }
}
