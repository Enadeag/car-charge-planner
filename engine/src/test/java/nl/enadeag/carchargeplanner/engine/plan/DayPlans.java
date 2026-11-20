package nl.enadeag.carchargeplanner.engine.plan;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import java.time.LocalDate;
import java.util.List;

/** Day plans for the tests that need one without a planner. */
public final class DayPlans {

    private DayPlans() {
    }

    /** A day whose battery holds all its driving: none of it is short. A test that needs a shortfall builds the day. */
    public static DayPlan of(LocalDate date, Percent startLevelPct, List<Window> windows,
            Percent levelAfterChargingPct, Percent chargeToPct, Percent endLevelPct, double drivingPct,
            List<ForecastStep> forecast) {
        return new DayPlan(date, startLevelPct, windows, levelAfterChargingPct, chargeToPct, endLevelPct, drivingPct, 0,
                forecast);
    }
}
