package io.github.enadeag.carchargeplanner.engine.plan;

import io.github.enadeag.carchargeplanner.engine.config.AwayPeriod;
import io.github.enadeag.carchargeplanner.engine.config.RecurringAwayPeriod;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * All the times the car is not at home: the weekly away periods and the ones entered by hand.
 *
 * @param recurring the away periods that come back every week, possibly none
 * @param oneOff the away periods entered by hand for specific days, possibly none; they may overlap the weekly ones
 */
public record AwayTimes(List<RecurringAwayPeriod> recurring, List<AwayPeriod> oneOff) {

    /** The car is always at home. */
    public static final AwayTimes NONE = new AwayTimes(List.of(), List.of());

    /**
     * Copies the lists.
     *
     * @throws NullPointerException when a list is null or holds null
     */
    public AwayTimes {
        recurring = List.copyOf(recurring);
        oneOff = List.copyOf(oneOff);
    }

    /**
     * The away time during a forecast, as time in which the charger cannot be used.
     *
     * @param forecast the forecast, in any order; empty gives no away time
     * @param zone the home's time zone, which decides the dates the weekly periods fall on
     * @return the periods entered by hand, and the weekly periods on every local date the forecast touches
     */
    List<Unavailable> during(List<ForecastStep> forecast, ZoneId zone) {
        var times = new ArrayList<Unavailable>();
        oneOff.forEach(period -> times.add(new Unavailable(period.start(), period.end())));
        if (forecast.isEmpty()) {
            return times;
        }
        var first = forecast.stream().map(ForecastStep::start).min(Comparator.naturalOrder()).orElseThrow();
        var last = forecast.stream().map(ForecastStep::end).max(Comparator.naturalOrder()).orElseThrow();
        var lastDate = last.atZone(zone).toLocalDate();
        for (var date = first.atZone(zone).toLocalDate(); !date.isAfter(lastDate); date = date.plusDays(1)) {
            for (var weekly : recurring) {
                weekly.on(date, zone).ifPresent(period -> times.add(new Unavailable(period.start(), period.end())));
            }
        }
        return times;
    }
}
