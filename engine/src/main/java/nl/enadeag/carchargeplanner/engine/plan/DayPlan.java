package nl.enadeag.carchargeplanner.engine.plan;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import java.time.LocalDate;
import java.util.List;

/**
 * One day of the plan, assuming the owner follows it.
 *
 * @param date the local date
 * @param startLevelPct the battery level at the start of the day
 * @param windows the charge windows of the day in time order, possibly none
 * @param levelAfterChargingPct the battery level once the windows and the trips' grid charges of the day are done, as if
 *     no trip had left; at most 100
 * @param chargeToPct the battery level the day's charging aims at, the target charge level or more: more on a day the
 *     plan charges above the target for a trip, and at least what a trip needs on the day its grid charge starts; on a
 *     day with a departure the highest of the parts before and after it
 * @param endLevelPct the battery level at the end of the day; 0 when driving takes more than is left
 * @param drivingPct the part of the battery driving takes off the day in percent, 0 or more: the daily distance, and
 *     the trips that depart on the day; more than 100 when the driving needs more than the battery holds
 * @param forecast the forecast steps that start on the day in the home's time zone, in time order; empty when the
 *     forecast does not reach the day
 */
public record DayPlan(
        LocalDate date,
        Percent startLevelPct,
        List<Window> windows,
        Percent levelAfterChargingPct,
        Percent chargeToPct,
        Percent endLevelPct,
        double drivingPct,
        List<ForecastStep> forecast) {

    /**
     * Copies the lists of windows and forecast steps.
     *
     * @throws NullPointerException when the windows or the steps are null or hold null
     */
    public DayPlan {
        windows = List.copyOf(windows);
        forecast = List.copyOf(forecast);
    }

    /**
     * The level the day's charging aims at as a whole percentage, as a level to charge to is shown, so a battery shown
     * at it holds what the plan wanted.
     *
     * @return {@link #chargeToPct()} rounded up to a whole percentage, a level less than a millionth of a point above a
     *     whole percentage rounded down to it, as that is the plan's rounding
     */
    public Percent wholeChargeToPct() {
        return Charging.chargeLimit(chargeToPct.value());
    }
}
