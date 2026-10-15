package io.github.enadeag.carchargeplanner.engine.config;

import java.time.ZoneId;
import java.util.List;

/** Facts about the owner's home and equipment: what the plan is made for. */
public record HomeSystem(
        double latitude,
        double longitude,
        ZoneId timezone,
        List<PanelArray> panels,
        House house,
        Car car,
        Charger charger) {

    public HomeSystem {
        Checks.require(latitude >= -90 && latitude <= 90, "latitude must be between -90 and 90");
        Checks.require(longitude >= -180 && longitude <= 180, "longitude must be between -180 and 180");
        Checks.require(!panels.isEmpty(), "panels needs at least one panel array");
        panels = List.copyOf(panels);
    }
}
