package nl.enadeag.carchargeplanner.engine.dashboard;

import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import nl.enadeag.carchargeplanner.engine.plan.GridAdvice;
import nl.enadeag.carchargeplanner.engine.plan.TripAdvice;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * The parts of a plan that the dashboard is made from, with the stored one-off away periods and trips.
 * {@code Outlook.Planned} hands them over, since {@code engine.dashboard} does not reach up into the engine's root
 * package.
 *
 * @param madeAt when the plan was made, never null; its day in {@code zone} is today, and on today's chart it is "now"
 * @param zone the home's time zone, never null, in which the days and times are meant
 * @param days the plan of each day, today first, never null; may be empty
 * @param baseloadKw the baseload in kW, 0 or more
 * @param batteryLevelPct the battery level the plan starts from, never null
 * @param batteryAssumed true while the battery level is a placeholder, not a reading
 * @param gridAdvice the plan's grid advice; empty when it gives none
 * @param tripAdvice the plan's advice per trip, in order of departure, never null; may be empty
 * @param minimumPct the minimum charge level, never null
 * @param targetPct the target charge level, never null
 * @param away the stored one-off away periods, also those that ended, never null; may be empty
 * @param trips the stored trips, also those under way and those made without this car, never null; may be empty
 * @param forecastSource where the plan's forecast came from, never null
 */
public record PlanParts(
        Instant madeAt,
        ZoneId zone,
        List<DayPlan> days,
        double baseloadKw,
        Percent batteryLevelPct,
        boolean batteryAssumed,
        Optional<GridAdvice> gridAdvice,
        List<TripAdvice> tripAdvice,
        Percent minimumPct,
        Percent targetPct,
        List<OneOffAwayPeriod> away,
        List<Trip> trips,
        ForecastSource forecastSource) {

    /** Keeps copies of the lists. */
    public PlanParts {
        days = List.copyOf(days);
        tripAdvice = List.copyOf(tripAdvice);
        away = List.copyOf(away);
        trips = List.copyOf(trips);
    }

    /**
     * Today in the home's time zone: the day of {@code madeAt} in {@code zone}. The dashboard names its days, cards and
     * windows against this day, so every part of it agrees on which day is today, also around midnight in UTC.
     *
     * @return the day the plan was made on in the home's time zone, never null
     */
    public LocalDate today() {
        return madeAt.atZone(zone).toLocalDate();
    }
}
