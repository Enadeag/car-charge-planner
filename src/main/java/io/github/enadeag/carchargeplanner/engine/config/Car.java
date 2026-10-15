package io.github.enadeag.carchargeplanner.engine.config;

public record Car(double usableBatteryKwh, double consumptionKwhPer100km, double kmPerDay) {

    public Car {
        Checks.require(usableBatteryKwh > 0, "usableBatteryKwh must be more than 0");
        Checks.require(consumptionKwhPer100km > 0, "consumptionKwhPer100km must be more than 0");
        Checks.require(kmPerDay >= 0, "kmPerDay must not be negative");
    }
}
