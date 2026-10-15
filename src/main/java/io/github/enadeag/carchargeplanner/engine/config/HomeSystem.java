package io.github.enadeag.carchargeplanner.engine.config;

import java.time.ZoneId;
import java.util.List;

/**
 * Facts about the owner's home and equipment.
 *
 * @param latitude the latitude of the home in degrees, from -90 to 90, north positive
 * @param longitude the longitude of the home in degrees, from -180 to 180, east positive
 * @param timezone the time zone of the home
 * @param panels the panel arrays, at least one
 * @param house the home's own use of power
 * @param car the car
 * @param charger the charger
 */
public record HomeSystem(
        double latitude,
        double longitude,
        ZoneId timezone,
        List<PanelArray> panels,
        House house,
        Car car,
        Charger charger) {

    /**
     * Checks the values and copies the list of panel arrays.
     *
     * @throws IllegalArgumentException when a coordinate is out of range or there is no panel array
     */
    public HomeSystem {
        Checks.require(latitude >= -90 && latitude <= 90, "latitude must be between -90 and 90");
        Checks.require(longitude >= -180 && longitude <= 180, "longitude must be between -180 and 180");
        Checks.require(!panels.isEmpty(), "panels needs at least one panel array");
        panels = List.copyOf(panels);
    }
}
