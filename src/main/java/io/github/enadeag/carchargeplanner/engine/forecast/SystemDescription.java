package io.github.enadeag.carchargeplanner.engine.forecast;

import io.github.enadeag.carchargeplanner.engine.config.HomeSystem;
import io.github.enadeag.carchargeplanner.engine.config.PanelArray;
import java.time.ZoneId;
import java.util.List;

/**
 * What a forecast provider needs to know: where the panels are and their panel arrays.
 *
 * @param latitude the latitude in degrees, north positive
 * @param longitude the longitude in degrees, east positive
 * @param timezone the time zone of the place
 * @param arrays the panel arrays, at least one
 */
public record SystemDescription(double latitude, double longitude, ZoneId timezone, List<PanelArray> arrays) {

    /** Copies the arrays. */
    public SystemDescription {
        arrays = List.copyOf(arrays);
    }

    /**
     * The part of the owner's system a forecast depends on.
     *
     * @param system the owner's system as the config file describes it
     * @return its place, time zone and panel arrays
     */
    public static SystemDescription of(HomeSystem system) {
        return new SystemDescription(system.latitude(), system.longitude(), system.timezone(), system.panels());
    }

    /**
     * The same place with other panel arrays, such as one array of several.
     *
     * @param otherArrays the arrays to describe instead, at least one
     * @return a description of the same place with those arrays
     */
    public SystemDescription withArrays(List<PanelArray> otherArrays) {
        return new SystemDescription(latitude, longitude, timezone, otherArrays);
    }
}
