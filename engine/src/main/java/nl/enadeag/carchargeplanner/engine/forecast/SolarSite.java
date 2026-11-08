package nl.enadeag.carchargeplanner.engine.forecast;

import nl.enadeag.carchargeplanner.engine.config.Coordinates;
import nl.enadeag.carchargeplanner.engine.config.HomeSystem;
import nl.enadeag.carchargeplanner.engine.config.PanelArray;
import java.time.ZoneId;
import java.util.List;

/**
 * The solar site a forecast is made for: where the panels are, and their panel arrays.
 *
 * @param location the coordinates of the place
 * @param timezone the time zone of the place
 * @param arrays the panel arrays, at least one
 */
public record SolarSite(Coordinates location, ZoneId timezone, List<PanelArray> arrays) {

    /** Copies the arrays. */
    public SolarSite {
        arrays = List.copyOf(arrays);
    }

    /**
     * The part of the owner's system a forecast depends on.
     *
     * @param system the owner's system as the config file describes it
     * @return its place, time zone and panel arrays
     */
    public static SolarSite of(HomeSystem system) {
        return new SolarSite(system.location(), system.timezone(), system.panels());
    }
}
