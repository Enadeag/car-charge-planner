package nl.enadeag.carchargeplanner.engine.plan;

import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import nl.enadeag.carchargeplanner.engine.away.WeeklyAwayPeriod;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * All the times the car is not at home: the weekly away periods, the ones entered by hand, and the trips. They add up:
 * the car is away when any of them says so.
 *
 * @param weekly the away periods that come back every week, possibly none
 * @param oneOff the away periods entered by hand for specific days, possibly none; they may overlap the weekly ones
 * @param trips the trips, possibly none; the car is away from each departure to its return, and they may overlap the
 *     away periods and each other
 */
public record AwayTimes(List<WeeklyAwayPeriod> weekly, List<OneOffAwayPeriod> oneOff, List<Trip> trips) {

    /** The car is always at home. */
    public static final AwayTimes NONE = new AwayTimes(List.of(), List.of(), List.of());

    /**
     * Copies the lists.
     *
     * @throws NullPointerException when a list is null or holds null
     */
    public AwayTimes {
        weekly = List.copyOf(weekly);
        oneOff = List.copyOf(oneOff);
        trips = List.copyOf(trips);
    }

    /**
     * The away time during a forecast, as time in which the charger cannot be used.
     *
     * @param forecast the forecast, in any order; empty gives no weekly away time
     * @param zone the home's time zone, which decides the dates the weekly periods fall on
     * @return the periods entered by hand, the trips, and the weekly periods on every local date the forecast touches,
     *     in order of their start, where times that overlap or touch are merged into one
     */
    List<BlockedTime> during(List<ForecastStep> forecast, ZoneId zone) {
        var times = new ArrayList<BlockedTime>();
        oneOff.forEach(period -> times.add(new BlockedTime(period.start(), period.end())));
        trips.forEach(trip -> times.add(new BlockedTime(trip.departure(), trip.returnTime())));
        if (!forecast.isEmpty()) {
            var first = forecast.stream().map(ForecastStep::start).min(Comparator.naturalOrder()).orElseThrow();
            var last = forecast.stream().map(ForecastStep::end).max(Comparator.naturalOrder()).orElseThrow();
            var lastDate = last.atZone(zone).toLocalDate();
            for (var date = first.atZone(zone).toLocalDate(); !date.isAfter(lastDate); date = date.plusDays(1)) {
                for (var period : weekly) {
                    period.on(date, zone).ifPresent(away -> times.add(new BlockedTime(away.start(), away.end())));
                }
            }
        }
        return merged(times);
    }

    private static List<BlockedTime> merged(List<BlockedTime> times) {
        var sorted = times.stream().sorted(Comparator.comparing(BlockedTime::start)).toList();
        var merged = new ArrayList<BlockedTime>();
        for (var time : sorted) {
            if (!merged.isEmpty() && !merged.get(merged.size() - 1).end().isBefore(time.start())) {
                var last = merged.remove(merged.size() - 1);
                merged.add(new BlockedTime(last.start(), last.end().isAfter(time.end()) ? last.end() : time.end()));
            } else {
                merged.add(time);
            }
        }
        return merged;
    }
}
