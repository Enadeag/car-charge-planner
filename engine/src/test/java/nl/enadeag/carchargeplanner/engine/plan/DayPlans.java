package nl.enadeag.carchargeplanner.engine.plan;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import java.time.LocalDate;
import java.util.List;

/** Day plans for the tests that need one without a planner. */
public final class DayPlans {

    private DayPlans() {
    }

    /**
     * A day without a departure whose battery holds all its driving: none of it is short. A test that needs a
     * shortfall builds the day.
     */
    public static DayPlan of(LocalDate date, Percent startLevelPct, List<Window> windows,
            Percent levelAfterChargingPct, Percent chargeToPct, Percent endLevelPct, double drivingPct,
            List<ForecastStep> forecast) {
        return departing(date, startLevelPct, List.of(), windows, levelAfterChargingPct, chargeToPct, endLevelPct,
                drivingPct, forecast);
    }

    /** A day with departures, at the given levels, whose battery holds all its driving. */
    public static DayPlan departing(LocalDate date, Percent startLevelPct, List<Percent> departureLevelPcts,
            List<Window> windows, Percent levelAfterChargingPct, Percent chargeToPct, Percent endLevelPct,
            double drivingPct, List<ForecastStep> forecast) {
        return new DayPlan(date, startLevelPct, departureLevelPcts, windows, levelAfterChargingPct, chargeToPct,
                endLevelPct, drivingPct, 0, forecast);
    }
}
