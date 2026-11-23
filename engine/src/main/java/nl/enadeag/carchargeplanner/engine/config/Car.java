package nl.enadeag.carchargeplanner.engine.config;

import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;

/**
 * The owner's electric car.
 *
 * @param usableBatteryKwh the usable capacity of the battery in kWh, from {@link #MIN_USABLE_BATTERY_KWH} to
 *     {@link #MAX_USABLE_BATTERY_KWH}
 * @param consumptionKwhPer100km the energy the car uses per 100 km in kWh, from {@link #MIN_CONSUMPTION_KWH_PER_100KM}
 *     to {@link #MAX_CONSUMPTION_KWH_PER_100KM}
 * @param kmPerDay the daily distance, how far the car is driven on a normal day besides the trips, in km, from
 *     {@link #MIN_KM_PER_DAY} to {@link #MAX_KM_PER_DAY}
 * @param tripSurcharge the share added to the consumption for the energy of a trip
 */
public record Car(double usableBatteryKwh, double consumptionKwhPer100km, double kmPerDay, Percent tripSurcharge) {

    /** The smallest usable battery, in kWh: a battery near nothing makes the planner's levels grow without end. */
    public static final int MIN_USABLE_BATTERY_KWH = 1;

    /** The largest usable battery, in kWh: more than any road vehicle has. */
    public static final int MAX_USABLE_BATTERY_KWH = 1000;

    /** The lowest consumption, in kWh per 100 km. */
    public static final int MIN_CONSUMPTION_KWH_PER_100KM = 1;

    /** The highest consumption, in kWh per 100 km: more than any road vehicle uses. */
    public static final int MAX_CONSUMPTION_KWH_PER_100KM = 200;

    /** The shortest daily distance, in km: a car that stays home. */
    public static final int MIN_KM_PER_DAY = 0;

    /** The longest daily distance, in km. */
    public static final int MAX_KM_PER_DAY = 1000;

    /** The trip surcharge unless the owner changes it: long drives on motorways and in winter use more. */
    public static final Percent STANDARD_TRIP_SURCHARGE = new Percent(15);

    /** A common car, for an owner who does not know the values: 60 kWh, 18 kWh per 100 km, 40 km a day. */
    public static final Car TYPICAL = new Car(60, 18, 40);

    /**
     * Checks the values.
     *
     * @throws InvalidSetting when a value is outside the range given for it
     */
    public Car {
        if (usableBatteryKwh < MIN_USABLE_BATTERY_KWH || usableBatteryKwh > MAX_USABLE_BATTERY_KWH
                || Double.isNaN(usableBatteryKwh)) {
            throw new InvalidSetting(ConfigSetting.CAR_BATTERY, InvalidSetting.Rule.OUT_OF_RANGE,
                    "usableBatteryKwh must be from " + MIN_USABLE_BATTERY_KWH + " to " + MAX_USABLE_BATTERY_KWH
                            + ", not " + usableBatteryKwh);
        }
        if (consumptionKwhPer100km < MIN_CONSUMPTION_KWH_PER_100KM
                || consumptionKwhPer100km > MAX_CONSUMPTION_KWH_PER_100KM || Double.isNaN(consumptionKwhPer100km)) {
            throw new InvalidSetting(ConfigSetting.CAR_CONSUMPTION, InvalidSetting.Rule.OUT_OF_RANGE,
                    "consumptionKwhPer100km must be from " + MIN_CONSUMPTION_KWH_PER_100KM + " to "
                            + MAX_CONSUMPTION_KWH_PER_100KM + ", not " + consumptionKwhPer100km);
        }
        if (kmPerDay < MIN_KM_PER_DAY || kmPerDay > MAX_KM_PER_DAY || Double.isNaN(kmPerDay)) {
            throw new InvalidSetting(ConfigSetting.CAR_DAILY_DISTANCE, InvalidSetting.Rule.OUT_OF_RANGE,
                    "kmPerDay must be from " + MIN_KM_PER_DAY + " to " + MAX_KM_PER_DAY + ", not " + kmPerDay);
        }
    }

    /**
     * A car with the {@link #STANDARD_TRIP_SURCHARGE}.
     *
     * @param usableBatteryKwh the usable capacity of the battery in kWh, from {@link #MIN_USABLE_BATTERY_KWH} to
     *     {@link #MAX_USABLE_BATTERY_KWH}
     * @param consumptionKwhPer100km the energy the car uses per 100 km in kWh, from
     *     {@link #MIN_CONSUMPTION_KWH_PER_100KM} to {@link #MAX_CONSUMPTION_KWH_PER_100KM}
     * @param kmPerDay the daily distance, besides the trips, in km, from {@link #MIN_KM_PER_DAY} to
     *     {@link #MAX_KM_PER_DAY}
     * @throws InvalidSetting when a value is outside the range given for it
     */
    public Car(double usableBatteryKwh, double consumptionKwhPer100km, double kmPerDay) {
        this(usableBatteryKwh, consumptionKwhPer100km, kmPerDay, STANDARD_TRIP_SURCHARGE);
    }
}
