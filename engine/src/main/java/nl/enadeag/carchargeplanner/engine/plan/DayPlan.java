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
 * @param departureLevelPcts the battery level at each of the day's departures, in time order, after the solar
 *     charging and a trip's grid charge before it; empty on a day without a departure
 * @param windows the charge windows of the day in time order, possibly none
 * @param levelAfterChargingPct the highest of the day's battery levels: its start level, the level at each departure,
 *     the level after its last solar charging, and its end level
 * @param chargeToPct the battery level the day's solar charging aims at, the target charge level or more: more on a
 *     day the plan charges above the target for a trip; on a day with a departure the highest of the parts before and
 *     after it. A trip's grid charge does not raise it.
 * @param endLevelPct the battery level at the end of the day, after its driving and a trip's grid charge that runs
 *     after it; at most 100, and 0 when driving takes more than is left and no grid charge follows
 * @param drivingPct the part of the battery driving takes off the day in percent, 0 or more: the daily distance, and
 *     the trips that depart on the day; more than 100 when the driving needs more than the battery holds
 * @param drivingShortPct the part of the day's driving the battery cannot hold, in percent, from 0 to
 *     {@code drivingPct}: 0 when the battery holds it all
 * @param forecast the forecast steps that start on the day in the home's time zone, in time order; empty when the
 *     forecast does not reach the day
 */
public record DayPlan(
        LocalDate date,
        Percent startLevelPct,
        List<Percent> departureLevelPcts,
        List<Window> windows,
        Percent levelAfterChargingPct,
        Percent chargeToPct,
        Percent endLevelPct,
        double drivingPct,
        double drivingShortPct,
        List<ForecastStep> forecast) {

    /**
     * Checks the departure levels and the driving the battery cannot hold, and copies the lists of departure levels,
     * windows and forecast steps.
     *
     * @throws IllegalArgumentException when a departure level is above the highest level, or the driving the battery
     *     cannot hold is below 0, more than the day's driving or not a number
     * @throws NullPointerException when the departure levels, the windows or the steps are null or hold null
     */
    public DayPlan {
        departureLevelPcts = List.copyOf(departureLevelPcts);
        for (var departurePct : departureLevelPcts) {
            if (departurePct.value() > levelAfterChargingPct.value()) {
                throw new IllegalArgumentException("a departure level, " + departurePct.value()
                        + " %, must be at most the day's highest level, " + levelAfterChargingPct.value() + " %");
            }
        }
        if (drivingShortPct < 0 || Double.isNaN(drivingShortPct)) {
            throw new IllegalArgumentException(
                    "the driving the battery cannot hold must be 0 % or more, not " + drivingShortPct + " %");
        }
        if (drivingShortPct > drivingPct) {
            throw new IllegalArgumentException("the driving the battery cannot hold, " + drivingShortPct
                    + " %, must be at most the day's driving, " + drivingPct + " %");
        }
        windows = List.copyOf(windows);
        forecast = List.copyOf(forecast);
    }

    /**
     * The level the day's solar charging aims at as a level to charge to: rounded up to a whole percent, the way
     * {@link TripAdvice#chargeLimitPct()} gives a trip's level, so a battery shown at this number holds what the plan
     * wanted.
     *
     * @return {@link #chargeToPct()} rounded up to a whole percent, at most 100
     */
    public Percent wholeChargeToPct() {
        return Charging.chargeLimit(chargeToPct.value());
    }
}
