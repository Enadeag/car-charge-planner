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
        if (usableBatteryKwh <= 0 || Double.isNaN(usableBatteryKwh)) {
            throw new IllegalArgumentException("usableBatteryKwh must be more than 0");
        }
        if (consumptionKwhPer100km <= 0 || Double.isNaN(consumptionKwhPer100km)) {
            throw new IllegalArgumentException("consumptionKwhPer100km must be more than 0");
        }
        if (kmPerDay < 0 || Double.isNaN(kmPerDay)) {
            throw new IllegalArgumentException("kmPerDay must not be negative");
        }
    }
}
