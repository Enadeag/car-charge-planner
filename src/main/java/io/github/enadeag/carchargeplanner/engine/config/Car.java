package io.github.enadeag.carchargeplanner.engine.config;

/**
 * The owner's electric car.
 *
 * @param usableBatteryKwh the usable capacity of the battery in kWh, more than 0
 * @param consumptionKwhPer100km the energy the car uses per 100 km in kWh, more than 0
 * @param kmPerDay the distance driven on a typical day in km, 0 or more
 */
public record Car(double usableBatteryKwh, double consumptionKwhPer100km, double kmPerDay) {

    /**
     * Checks the values.
     *
     * @throws IllegalArgumentException when a value is outside the range given for it
     */
    public Car {
        Checks.require(usableBatteryKwh > 0, "usableBatteryKwh must be more than 0");
        Checks.require(consumptionKwhPer100km > 0, "consumptionKwhPer100km must be more than 0");
        Checks.require(kmPerDay >= 0, "kmPerDay must not be negative");
    }
}
