package nl.enadeag.carchargeplanner.engine.config;

import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;

/**
 * The owner's electric car.
 *
 * @param usableBatteryKwh the usable capacity of the battery in kWh, more than 0
 * @param consumptionKwhPer100km the energy the car uses per 100 km in kWh, more than 0
 * @param kmPerDay the distance of the other driving, the short drives that are not trips, on a typical day in km, 0 or
 *     more
 * @param tripSurcharge the share added to the consumption for the energy of a trip
 */
public record Car(double usableBatteryKwh, double consumptionKwhPer100km, double kmPerDay, Percent tripSurcharge) {

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
        if (usableBatteryKwh <= 0 || Double.isNaN(usableBatteryKwh)) {
            throw new InvalidSetting(ConfigSetting.CAR_BATTERY, InvalidSetting.Rule.OUT_OF_RANGE,
                    "usableBatteryKwh must be more than 0");
        }
        if (consumptionKwhPer100km <= 0 || Double.isNaN(consumptionKwhPer100km)) {
            throw new InvalidSetting(ConfigSetting.CAR_CONSUMPTION, InvalidSetting.Rule.OUT_OF_RANGE,
                    "consumptionKwhPer100km must be more than 0");
        }
        if (kmPerDay < 0 || Double.isNaN(kmPerDay)) {
            throw new InvalidSetting(ConfigSetting.CAR_DAILY_DISTANCE, InvalidSetting.Rule.OUT_OF_RANGE,
                    "kmPerDay must not be negative");
        }
    }

    /**
     * A car with the {@link #STANDARD_TRIP_SURCHARGE}.
     *
     * @param usableBatteryKwh the usable capacity of the battery in kWh, more than 0
     * @param consumptionKwhPer100km the energy the car uses per 100 km in kWh, more than 0
     * @param kmPerDay the distance of the other driving on a typical day in km, 0 or more
     * @throws InvalidSetting when a value is outside the range given for it
     */
    public Car(double usableBatteryKwh, double consumptionKwhPer100km, double kmPerDay) {
        this(usableBatteryKwh, consumptionKwhPer100km, kmPerDay, STANDARD_TRIP_SURCHARGE);
    }
}
