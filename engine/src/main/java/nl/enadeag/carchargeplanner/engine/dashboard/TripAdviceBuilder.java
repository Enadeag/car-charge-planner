package nl.enadeag.carchargeplanner.engine.dashboard;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.plan.TripAdvice;
import nl.enadeag.carchargeplanner.engine.plan.TripGridCharge;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.OptionalDouble;

/** The plan's advice per trip as the dashboard shows it, with its days named from the day the plan was made. */
final class TripAdviceBuilder {

    /**
     * From this hour on, a grid start later on the day the plan was made is in the evening, so it can be told as
     * tonight; before it, it is just later that day.
     */
    private static final LocalTime EVENING = LocalTime.of(18, 0);

    private final ZoneId zone;
    private final Instant madeAt;
    private final LocalDate today;
    private final double targetPct;
    private final Percent levelPct;
    private final boolean levelAssumed;

    /**
     * Makes the advice of one plan.
     *
     * @param parts the plan's parts, never null
     */
    TripAdviceBuilder(PlanParts parts) {
        this.zone = parts.zone();
        this.madeAt = parts.madeAt();
        this.today = madeAt.atZone(zone).toLocalDate();
        this.targetPct = parts.targetPct().value();
        this.levelPct = parts.batteryLevelPct();
        this.levelAssumed = parts.batteryAssumed();
    }

    /**
     * The advice for one trip.
     *
     * @param advice the plan's advice for the trip, never null
     * @return the advice, its days named from today
     */
    TripAdviceView advice(TripAdvice advice) {
        double kmShort = advice.kmShort();
        boolean enough = advice.grid().isEmpty() && kmShort <= 0
                && (advice.solarWindows().isEmpty() || advice.neededPct().value() <= targetPct);
        return new TripAdviceView(advice.trip(), name(advice.trip().firstDay(zone)),
                enough ? new TripPlan.Enough(advice.departureLevelPct()) : plan(advice),
                kmShort > 0 ? OptionalDouble.of(kmShort) : OptionalDouble.empty(), levelPct, levelAssumed);
    }

    /** Grid before solar alone; without either, the trip only leaves. */
    private TripPlan plan(TripAdvice advice) {
        var limit = advice.chargeLimitPct();
        var solar = byDay(advice.solarWindows());
        if (advice.grid().isPresent()) {
            var grid = start(advice.grid().get());
            return solar.isEmpty() ? new TripPlan.Grid(limit, grid) : new TripPlan.SolarThenGrid(limit, solar, grid);
        }
        return solar.isEmpty() ? new TripPlan.Leaves(advice.departureLevelPct()) : new TripPlan.Solar(limit, solar);
    }

    /** Due now up to the moment the plan was made; tonight only after it, on its day, from the evening on. */
    private TripGridStart start(TripGridCharge grid) {
        var at = grid.window().start();
        var local = at.atZone(zone);
        boolean startsNow = !at.isAfter(madeAt);
        boolean tonight = !startsNow && local.toLocalDate().equals(today) && !local.toLocalTime().isBefore(EVENING);
        return new TripGridStart(grid.window(), grid.start(), name(local.toLocalDate()), startsNow, tonight);
    }

    /** The windows grouped by the day of their start in the home's time zone, in time order. */
    private List<SolarDay> byDay(List<Window> windows) {
        var byDay = new LinkedHashMap<LocalDate, List<Window>>();
        for (var window : windows) {
            byDay.computeIfAbsent(window.start().atZone(zone).toLocalDate(), day -> new ArrayList<>()).add(window);
        }
        return byDay.entrySet().stream().map(day -> new SolarDay(name(day.getKey()), day.getValue())).toList();
    }

    private DayName name(LocalDate date) {
        return DayName.of(date, today);
    }
}
