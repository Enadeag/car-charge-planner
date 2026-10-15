package io.github.enadeag.carchargeplanner.engine.forecast;

import io.github.enadeag.carchargeplanner.engine.config.HomeSystem;
import io.github.enadeag.carchargeplanner.engine.config.PanelArray;
import java.time.ZoneId;
import java.util.List;

/** What a forecast provider needs to know: where the panels are and their panel arrays (at least one). */
public record SystemDescription(double latitude, double longitude, ZoneId timezone, List<PanelArray> arrays) {

    public SystemDescription {
        arrays = List.copyOf(arrays);
    }

    public static SystemDescription of(HomeSystem system) {
        return new SystemDescription(system.latitude(), system.longitude(), system.timezone(), system.panels());
    }

    public SystemDescription withArrays(List<PanelArray> otherArrays) {
        return new SystemDescription(latitude, longitude, timezone, otherArrays);
    }
}
