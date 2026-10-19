package nl.enadeag.carchargeplanner.desktop.dashboard;

import nl.enadeag.carchargeplanner.engine.Outlook;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import nl.enadeag.carchargeplanner.engine.forecast.Freshness;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderRole;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntToDoubleFunction;

/** The engine's plans as the chart and battery mappers get them, in Brussels, with the house at 0.4 kW. */
public final class PlannedDays {

    public static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    /** A Friday. */
    public static final LocalDate TODAY = LocalDate.of(2026, 7, 3);
    /** 25 hours: 03:00 summer time becomes 02:00 winter time. */
    public static final LocalDate FALL_BACK = LocalDate.of(2026, 10, 25);

    private static final Window WINDOW = new Window(at(TODAY, 11, 15), at(TODAY, 14, 30), 10, 2.3, 7.3, 0.2);

    /** Made at 08:12 with an assumed battery level: today sunny with a window, tomorrow sunnier without one. */
    public static final Outlook.Planned TWO_DAYS = planned(at(TODAY, 8, 12), true,
            day(TODAY, 50, 74, sunny(TODAY, 4.2), WINDOW),
            day(TODAY.plusDays(1), 62, 62, sunny(TODAY.plusDays(1), 5.3)));

    private PlannedDays() {
    }

    public static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(BRUSSELS).toInstant();
    }

    /** Hourly steps over the whole day, with the power the function gives for each hour of the clock. */
    public static List<ForecastStep> hourly(LocalDate day, IntToDoubleFunction kwAtHour) {
        var steps = new ArrayList<ForecastStep>();
        for (var start = day.atStartOfDay(BRUSSELS).toInstant(); start.isBefore(day.plusDays(1).atStartOfDay(BRUSSELS).toInstant());
                start = start.plus(Duration.ofHours(1))) {
            steps.add(new ForecastStep(start, start.plus(Duration.ofHours(1)), kwAtHour.applyAsDouble(start.atZone(BRUSSELS).getHour())));
        }
        return steps;
    }

    /** Sun from 07:00 to 19:00, peaking at 13:00 at the given power. */
    public static List<ForecastStep> sunny(LocalDate day, double peakKw) {
        return hourly(day, hour -> hour >= 7 && hour < 19 ? peakKw - Math.abs(hour - 13) * peakKw / 7 : 0);
    }

    public static DayPlan day(LocalDate date, double start, double after, List<ForecastStep> forecast, Window... windows) {
        return new DayPlan(date, new Percent(start), List.of(windows), new Percent(after), new Percent(start), 12, forecast);
    }

    /** A plan with a minimum of 30 % and a target of 80 %. */
    public static Outlook.Planned planned(Instant madeAt, boolean assumed, DayPlan... days) {
        var source = new ForecastSource(new ProviderId("open-meteo"), "Open-Meteo", "Weather data", madeAt, Freshness.FRESH,
                ProviderRole.PRIMARY, "", Optional.empty());
        return new Outlook.Planned(madeAt, BRUSSELS, List.of(days), new Percent(50), assumed, Optional.empty(), source, 0.4,
                new Percent(30), new Percent(80));
    }
}
