package nl.enadeag.carchargeplanner.engine.dashboard;

import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
     * The dashboard of a plan, from its parts. Nothing reads the clock: "now" is the moment the plan was made.
     *
     * @param madeAt when the plan was made; its day in {@code zone} is today
     * @param zone the home's time zone, in which the days and times are meant
     * @param days the plan of each day, today first; may be empty
     * @param houseKw the power the house uses on its own in kW, 0 or more
     * @return one dashboard day per plan day, in the same order
     */
    public static DashboardView of(Instant madeAt, ZoneId zone, List<DayPlan> days, double houseKw) {
        LocalDate today = madeAt.atZone(zone).toLocalDate();
        var charts = new DayCharts(madeAt, zone, days, houseKw);
        return new DashboardView(days.stream()
                .map(day -> new DashboardDay(day.date(), DayName.of(day.date(), today), charts.chart(day)))
                .toList());
    }
}
