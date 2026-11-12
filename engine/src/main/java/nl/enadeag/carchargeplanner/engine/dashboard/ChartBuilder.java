package nl.enadeag.carchargeplanner.engine.dashboard;

import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import nl.enadeag.carchargeplanner.engine.plan.Hours;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The days of one plan as the solar chart shows them, in the home's time zone. All days share one power scale, so they
 * can be compared.
 */
final class ChartBuilder {

    /** Less power than this counts as no sun: a forecast's dawn and dusk. */
    private static final double SUN_KW = 0.01;
    /** The chart shows this many hours before the first sun and after the last. */
    private static final int MARGIN_HOURS = 1;
    /** The first hour a day without sun shows. */
    private static final double DARK_FROM = 6;
    /** The last hour a day without sun shows. */
    private static final double DARK_TO = 20;
    private static final int LAST_CLOCK_HOUR = 24;

    private final ZoneId zone;
    private final Instant madeAt;
    private final LocalDate today;
    private final double baseloadKw;
    private final double scaleKw;

    /**
     * @param parts the plan's parts, never null: when it was made, which on today's chart is "now"; the home's time
     *     zone, in which the days start and the clock hours are told; all its days, whose highest solar power sets the
     *     shared scale; and the baseload
     */
    ChartBuilder(PlanParts parts) {
        this.zone = parts.zone();
        this.madeAt = parts.madeAt();
        this.today = parts.today();
        this.baseloadKw = parts.baseloadKw();
        double peak = parts.days().stream().flatMap(day -> day.forecast().stream())
                .mapToDouble(ForecastStep::powerKw).max().orElse(0);
        this.scaleKw = Math.max(1, Math.ceil(Math.max(peak, baseloadKw)));
    }

    /**
     * @return the chart, showing the hours with sun and an hour on either side, within the day; 06:00 to 20:00 for a
     *     day without sun
     */
    DayChart chart(DayPlan day) {
        var start = day.date().atStartOfDay(zone);
        var sunny = day.forecast().stream().filter(step -> step.powerKw() > SUN_KW).toList();
        var shown = shownHours(sunny, start);
        double from = shown.from();
        double to = shown.to();
        var steps = day.forecast().stream()
                .filter(step -> hoursFrom(start, step.end()) > from && hoursFrom(start, step.start()) < to)
                .map(step -> new DayChart.Step(hoursFrom(start, step.start()), hoursFrom(start, step.end()),
                        step.powerKw(), step.start()))
                .toList();
        var windows = day.windows().stream()
                .map(window -> new DayChart.WindowBand(hoursFrom(start, window.start()),
                        hoursFrom(start, window.end()), window.start(), window.end()))
                .toList();
        double madeAtHour = hoursFrom(start, madeAt);
        var now = day.date().equals(today) && madeAtHour >= from && madeAtHour <= to
                ? Optional.of(new DayChart.Moment(madeAtHour, madeAt)) : Optional.<DayChart.Moment>empty();
        // of two equal peaks the first is taken
        var peak = sunny.stream().max(Comparator.comparingDouble(ForecastStep::powerKw))
                .map(step -> new DayChart.Peak(step.powerKw(), step.start()));
        return new DayChart(from, to, scaleKw, steps, baseloadKw, windows, now, ticks(start, from, to), peak);
    }

    /**
     * From the hour before the first sun to the hour after the last, within the day, or 06:00 to 20:00 for a day
     * without sun.
     */
    private ShownHours shownHours(List<ForecastStep> sunny, ZonedDateTime start) {
        if (sunny.isEmpty()) {
            return new ShownHours(DARK_FROM, DARK_TO);
        }
        double dayHours = hoursFrom(start, nextDay(start));
        return new ShownHours(Math.max(0, Math.floor(hoursFrom(start, sunny.get(0).start())) - MARGIN_HOURS),
                Math.min(dayHours, Math.ceil(hoursFrom(start, sunny.get(sunny.size() - 1).end())) + MARGIN_HOURS));
    }

    /**
     * Every whole clock hour that lies within the hours shown; one the clocks skip on the short day is left out, and
     * on the 25-hour day the repeated clock hour gets one tick, at its first time.
     */
    private List<DayChart.Tick> ticks(ZonedDateTime start, double from, double to) {
        var ticks = new ArrayList<DayChart.Tick>();
        for (int clockHour = 0; clockHour <= LAST_CLOCK_HOUR; clockHour++) {
            var at = clockHour == LAST_CLOCK_HOUR ? nextDay(start).atZone(zone)
                    : start.toLocalDate().atTime(clockHour, 0).atZone(zone);
            double hours = hoursFrom(start, at.toInstant());
            boolean exists = clockHour == LAST_CLOCK_HOUR || at.getHour() == clockHour;
            if (exists && hours >= from && hours <= to) {
                ticks.add(new DayChart.Tick(hours, clockHour, at.toInstant()));
            }
        }
        return ticks;
    }

    private Instant nextDay(ZonedDateTime start) {
        return start.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant();
    }

    private static double hoursFrom(ZonedDateTime start, Instant moment) {
        return Hours.between(start.toInstant(), moment);
    }

    /** The first and the last hour the chart shows, from the start of the day. */
    private record ShownHours(double from, double to) {
    }
}
