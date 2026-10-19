package nl.enadeag.carchargeplanner.desktop.dashboard.day;

import nl.enadeag.carchargeplanner.desktop.dashboard.battery.BatteryModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.solar.SolarCurveModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.solar.SolarCurveModel.Span;
import nl.enadeag.carchargeplanner.desktop.dashboard.solar.SolarCurveModel.Step;
import nl.enadeag.carchargeplanner.desktop.dashboard.solar.SolarCurveModel.Tick;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.Outlook;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The days of a plan as the solar chart and the battery bar draw them, in the time zone of the owner's home. */
final class ChartMapper {

    /** Less power than this counts as no sun: a forecast's dawn and dusk. */
    private static final double SUN_KW = 0.01;
    /** The chart shows this many hours before the first sun and after the last. */
    private static final int MARGIN_HOURS = 1;
    /** The hours a day without sun shows. */
    private static final int DARK_FROM = 6;
    private static final int DARK_TO = 20;
    private static final int TICK_HOURS = 3;

    private final Outlook.Planned planned;
    private final Messages messages;
    private final ZoneId zone;
    private final LocalDate today;
    private final double scaleKw;

    /**
     * Makes the mapper for one plan.
     *
     * @param planned the plan whose time zone, moment of making, house use, charge levels and days the charts show;
     *     all days share one power scale, so they can be compared
     * @param messages the texts, numbers and times, in the owner's language
     */
    ChartMapper(Outlook.Planned planned, Messages messages) {
        this.planned = planned;
        this.messages = messages;
        this.zone = planned.zone();
        this.today = planned.madeAt().atZone(zone).toLocalDate();
        double peak = planned.days().stream().flatMap(day -> day.forecast().stream())
                .mapToDouble(ForecastStep::powerKw).max().orElse(0);
        this.scaleKw = Math.max(1, Math.ceil(Math.max(peak, planned.baseloadKw())));
    }

    /**
     * A day's expected sun, the house's use and the charge windows.
     *
     * @param day one of the plan's days
     * @return the chart's data, showing the hours with sun and an hour on either side
     */
    SolarCurveModel curve(DayPlan day) {
        var start = day.date().atStartOfDay(zone);
        double dayHours = hoursFrom(start, day.date().plusDays(1).atStartOfDay(zone).toInstant());
        var sunny = day.forecast().stream().filter(step -> step.powerKw() > SUN_KW).toList();
        double from;
        double to;
        if (sunny.isEmpty()) {
            from = DARK_FROM;
            to = DARK_TO;
        } else {
            from = Math.max(0, Math.floor(hoursFrom(start, sunny.getFirst().start())) - MARGIN_HOURS);
            to = Math.min(dayHours, Math.ceil(hoursFrom(start, sunny.getLast().end())) + MARGIN_HOURS);
        }
        var steps = day.forecast().stream()
                .filter(step -> hoursFrom(start, step.end()) > from && hoursFrom(start, step.start()) < to)
                .map(step -> new Step(hoursFrom(start, step.start()), hoursFrom(start, step.end()), step.powerKw(),
                        messages.text(TextKey.CHART_READOUT, messages.time(step.start(), zone),
                                messages.decimal(step.powerKw()), messages.decimal(planned.baseloadKw()))))
                .toList();
        var windows = day.windows().stream()
                .map(window -> new Span(hoursFrom(start, window.start()), hoursFrom(start, window.end())))
                .toList();
        double now = hoursFrom(start, planned.madeAt());
        var nowHour = day.date().equals(today) && now >= from && now <= to ? OptionalDouble.of(now) : OptionalDouble.empty();
        return new SolarCurveModel(from, to, scaleKw, messages.text(TextKey.CHART_SCALE, messages.whole(scaleKw)), steps, planned.baseloadKw(),
                windows, nowHour, ticks(day.date(), start, from, to), spoken(day, nowHour.isPresent()));
    }

    /**
     * A day's battery: its level at the start and after charging, with the minimum and target.
     *
     * @param day one of the plan's days
     * @return the bar's data; only today's level can be entered
     */
    BatteryModel battery(DayPlan day) {
        double level = day.startLevelPct().value();
        double after = day.levelAfterChargingPct().value();
        double minimum = planned.minimumPct().value();
        double target = planned.targetPct().value();
        boolean isToday = day.date().equals(today);
        var spoken = isToday
                ? sentences(messages.text(TextKey.BATTERY_SPOKEN_TODAY, pct(level), pct(after), pct(minimum), pct(target)),
                        planned.batteryAssumed() ? messages.text(TextKey.BATTERY_ASSUMED) : "")
                : messages.text(TextKey.BATTERY_SPOKEN_DAY, pct(level), messages.inSentence(day.date(), today),
                        pct(after), pct(minimum), pct(target));
        return new BatteryModel(level, after, minimum, target,
                messages.text(TextKey.BATTERY_LEVELS, pct(level), pct(after)),
                messages.text(TextKey.BATTERY_MINIMUM, pct(minimum)), messages.text(TextKey.BATTERY_TARGET, pct(target)),
                isToday, spoken);
    }

    /** Every third hour of the clock that lies within the hours shown, where it is on this day. */
    private List<Tick> ticks(LocalDate date, ZonedDateTime start, double from, double to) {
        var ticks = new ArrayList<Tick>();
        for (int hour = 0; hour <= 24; hour += TICK_HOURS) {
            var at = hour == 24 ? date.plusDays(1).atStartOfDay(zone) : date.atTime(hour, 0).atZone(zone);
            double hours = hoursFrom(start, at.toInstant());
            if (hours >= from && hours <= to) {
                ticks.add(new Tick(hours, String.format(Locale.ROOT, "%02d", hour)));
            }
        }
        return ticks;
    }

    private String spoken(DayPlan day, boolean showsNow) {
        var title = messages.title(day.date(), today);
        var peak = day.forecast().stream().max(Comparator.comparingDouble(ForecastStep::powerKw));
        if (peak.isEmpty() || peak.get().powerKw() <= SUN_KW) {
            return messages.text(TextKey.CHART_SPOKEN_NO_SUN, title);
        }
        var chart = messages.text(TextKey.CHART_SPOKEN, title, messages.decimal(peak.get().powerKw()),
                messages.time(peak.get().start(), zone), messages.decimal(planned.baseloadKw()));
        var windows = day.windows().isEmpty() ? "" : messages.text(TextKey.CHART_SPOKEN_WINDOWS, day.windows().stream()
                .map(window -> messages.text(TextKey.CHART_SPOKEN_SPAN, messages.time(window.start(), zone),
                        messages.time(window.end(), zone)))
                .collect(Collectors.joining(", ")));
        var now = showsNow ? messages.text(TextKey.CHART_SPOKEN_NOW, messages.time(planned.madeAt(), zone)) : "";
        return sentences(chart, windows, now);
    }

    private String pct(double value) {
        return messages.whole(value);
    }

    private static String sentences(String... sentences) {
        return Stream.of(sentences).filter(sentence -> !sentence.isEmpty()).collect(Collectors.joining(" "));
    }

    private static double hoursFrom(ZonedDateTime start, Instant moment) {
        return Duration.between(start.toInstant(), moment).toNanos() / 3_600e9;
    }
}
