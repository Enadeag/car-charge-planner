package io.github.enadeag.carchargeplanner.engine.forecast;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Forecasts made by hand: hourly steps of a day with sunshine shaped like a half sine from 06:00 to 18:00. */
public final class SunnyDays {

    public static final ZoneId TZ = ZoneId.of("Europe/Amsterdam");

    private SunnyDays() {
    }

    /** @param peakKw the power at noon */
    public static List<ForecastStep> day(LocalDate day, double peakKw) {
        var steps = new ArrayList<ForecastStep>();
        var start = day.atStartOfDay(TZ).toInstant();
        for (int hour = 0; hour < 24; hour++) {
            double middle = hour + 0.5;
            double powerKw = middle >= 6 && middle <= 18 ? peakKw * Math.sin(Math.PI * (middle - 6) / 12) : 0;
            var from = start.plus(Duration.ofHours(hour));
            steps.add(new ForecastStep(from, from.plus(Duration.ofHours(1)), powerKw));
        }
        return steps;
    }

    public static List<ForecastStep> days(LocalDate first, int count, double peakKw) {
        var steps = new ArrayList<ForecastStep>();
        for (int i = 0; i < count; i++) {
            steps.addAll(day(first.plusDays(i), peakKw));
        }
        return steps;
    }
}
