package io.github.enadeag.carchargeplanner.engine.config;

import java.time.ZoneId;
import java.util.List;

/**
 * Facts about the owner's home and equipment.
 *
 * @param location the coordinates of the home
 * @param timezone the time zone of the home
 * @param panels the panel arrays, at least one
 * @param house the home's own use of power
 * @param car the car
 * @param charger the charger
 */
public record HomeSystem(
        Coordinates location,
        ZoneId timezone,
        List<PanelArray> panels,
        House house,
        Car car,
        Charger charger) {

    /**
     * Checks the values and copies the list of panel arrays.
     *
     * @throws IllegalArgumentException when there is no panel array
     */
    public HomeSystem {
        if (panels.isEmpty()) {
            throw new IllegalArgumentException("panels needs at least one panel array");
        }
        panels = List.copyOf(panels);
    }

    /**
     * The system with other panel arrays.
     *
     * @param newPanels the panel arrays, at least one
     * @return a system with everything else the same
     * @throws IllegalArgumentException when there is no panel array
     */
    public HomeSystem withPanels(List<PanelArray> newPanels) {
        return new HomeSystem(location, timezone, newPanels, house, car, charger);
    }

    /**
     * The system at another place.
     *
     * @param newLocation the coordinates of the home
     * @param newTimezone the time zone of the home
     * @return a system with everything else the same
     */
    public HomeSystem withLocation(Coordinates newLocation, ZoneId newTimezone) {
        return new HomeSystem(newLocation, newTimezone, panels, house, car, charger);
    }

    /**
     * The system with another house and car.
     *
     * @param newHouse the home's own use of power
     * @param newCar the car
     * @return a system with everything else the same
     */
    public HomeSystem withHouseAndCar(House newHouse, Car newCar) {
        return new HomeSystem(location, timezone, panels, newHouse, newCar, charger);
    }

    /**
     * The system with another charger.
     *
     * @param newCharger the charger
     * @return a system with everything else the same
     */
    public HomeSystem withCharger(Charger newCharger) {
        return new HomeSystem(location, timezone, panels, house, car, newCharger);
    }
}
