package nl.enadeag.carchargeplanner.engine.plan;

import nl.enadeag.carchargeplanner.engine.config.HomeSystem;
import nl.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Where a grid charge can go: the free quarters of an hour, and every stretch of them that delivers an energy at one
 * current. The grid advice and a trip's grid charge each pick their own from these.
 */
final class GridPlacements {

    private static final Duration QUARTER_HOUR = Duration.ofMinutes(15);
    private static final double QUARTER_HOUR_IN_HOURS = 0.25;
    private static final double EPS = 1e-9;

    private final HomeSystem system;
    private final PlanningPreferences preferences;
    private final ZoneId zone;

    /**
     * Makes the placements for one home and one set of preferences.
     *
     * @param system the home and equipment, for the charger and the house's own use
     * @param preferences the planning preferences, for the margin on the surplus
     */
    GridPlacements(HomeSystem system, PlanningPreferences preferences) {
        this.system = system;
        this.preferences = preferences;
        this.zone = system.timezone();
    }

    /**
     * A quarter of an hour, counted from a local midnight in real time.
     *
     * @param start the first moment of the quarter
     * @param end the end of the quarter, 15 minutes after the start
     * @param usableKw the usable surplus in kW, 0 or more; 0 where the forecast does not reach
     * @param free whether the forecast covers it, the car is at home and nothing else is planned in it
     */
    record QuarterHour(Instant start, Instant end, double usableKw, boolean free) {
    }

    /**
     * The quarters of an hour of whole days in real time, so a daylight saving day has 92 or 100 of them.
     *
     * @param first the first local date
     * @param last the last local date, not before {@code first}
     * @param forecast the forecast, in any order
     * @param unavailable the times in which nothing may be charged, such as away time and planned windows
     * @return the quarters in time order
     */
    List<QuarterHour> quarterHours(LocalDate first, LocalDate last, List<ForecastStep> forecast,
            List<BlockedTime> unavailable) {
        var end = last.plusDays(1).atStartOfDay(zone).toInstant();
        var quarters = new ArrayList<QuarterHour>();
        for (var start = first.atStartOfDay(zone).toInstant(); start.isBefore(end); start = start.plus(QUARTER_HOUR)) {
            quarters.add(quarterHour(start, forecast, unavailable));
        }
        return quarters;
    }

    private QuarterHour quarterHour(Instant start, List<ForecastStep> forecast, List<BlockedTime> unavailable) {
        var end = start.plus(QUARTER_HOUR);
        var step = forecast.stream()
                .filter(candidate -> !candidate.start().isAfter(start) && candidate.end().isAfter(start))
                .findFirst();
        boolean free = step.isPresent() && unavailable.stream().noneMatch(time -> time.overlaps(start, end));
        double usableKw = step.map(found -> Charging.usableSurplusKw(found, system, preferences)).orElse(0.0);
        return new QuarterHour(start, end, usableKw, free);
    }

    /**
     * Every stretch of free quarters of an hour that delivers the energy at this current, as a window.
     *
     * @param quarters the quarters to look in, in time order, without gaps
     * @param energyKwh the wall-side energy to deliver in kWh, more than 0
     * @param amps the current per phase in A
     * @param notBefore the earliest start, such as now
     * @return the windows, in order of their start; none when no stretch is long enough
     */
    List<Window> placements(List<QuarterHour> quarters, double energyKwh, int amps, Instant notBefore) {
        double powerKw = Charging.powerKw(amps, system.charger());
        double hours = energyKwh / powerKw;
        int count = (int) Math.ceil(hours / QUARTER_HOUR_IN_HOURS - EPS);
        var placements = new ArrayList<Window>();
        for (int first = 0; first + count <= quarters.size(); first++) {
            var stretch = quarters.subList(first, first + count);
            if (!stretch.getFirst().start().isBefore(notBefore) && stretch.stream().allMatch(QuarterHour::free)) {
                placements.add(window(stretch, amps, powerKw, hours));
            }
        }
        return placements;
    }

    /**
     * Charging at once: from a moment on, at a current, until the energy is in or the car cannot charge any more.
     *
     * @param quarters the quarters to charge in, in time order, without gaps
     * @param energyKwh the wall-side energy to deliver in kWh, more than 0
     * @param amps the current per phase in A
     * @param from the moment to start, or the first free moment after it
     * @param until the moment the charging must have stopped, such as a departure
     * @return the window, which ends early when a time that is not free comes first; empty when the car has no free
     *     time from {@code from} to {@code until}
     */
    Optional<Window> fromNow(List<QuarterHour> quarters, double energyKwh, int amps, Instant from, Instant until) {
        double powerKw = Charging.powerKw(amps, system.charger());
        Instant start = null;
        Instant end = null;
        double solar = 0;
        double imported = 0;
        for (var quarter : quarters) {
            var begin = Hours.latest(quarter.start(), from);
            var stop = Hours.earliest(quarter.end(), until);
            if (!stop.isAfter(begin)) {
                continue;
            }
            if (!quarter.free()) {
                if (start != null) {
                    break;
                }
                continue;
            }
            if (start == null) {
                start = begin;
            }
            double hours = Math.min(Hours.between(begin, stop), (energyKwh - solar - imported) / powerKw);
            solar += Math.min(powerKw, quarter.usableKw()) * hours;
            imported += Math.max(0, powerKw - quarter.usableKw()) * hours;
            end = Hours.plus(begin, hours);
            if (solar + imported >= energyKwh - EPS) {
                break;
            }
        }
        if (start == null) {
            return Optional.empty();
        }
        var rounded = Hours.earliest(Hours.ceilToFiveMinutes(end, zone), until);
        return Optional.of(new Window(start, rounded, amps, powerKw, solar, imported));
    }

    /**
     * Charging for the hours over the quarters of an hour: the whole of each, and of the last only the part still
     * needed.
     */
    private Window window(List<QuarterHour> stretch, int amps, double powerKw, double hours) {
        double solar = 0;
        double imported = 0;
        int last = stretch.size() - 1;
        for (int i = 0; i <= last; i++) {
            double quarterHours = i < last ? QUARTER_HOUR_IN_HOURS : hours - last * QUARTER_HOUR_IN_HOURS;
            double usableKw = stretch.get(i).usableKw();
            solar += Math.min(powerKw, usableKw) * quarterHours;
            imported += Math.max(0, powerKw - usableKw) * quarterHours;
        }
        var start = stretch.getFirst().start();
        var end = Hours.earliest(Hours.ceilToFiveMinutes(Hours.plus(start, hours), zone), stretch.getLast().end());
        return new Window(start, end, amps, powerKw, solar, imported);
    }
}
