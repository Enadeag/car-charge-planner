package io.github.enadeag.carchargeplanner.desktop.settings.form;

import io.github.enadeag.carchargeplanner.engine.config.Coordinates;
import io.github.enadeag.carchargeplanner.engine.config.EngineConfig;
import java.time.ZoneId;

/**
 * Where the home is, as the location page shows it.
 *
 * @param latitude the latitude, in degrees
 * @param longitude the longitude, in degrees
 * @param timezone the home's time zone
 */
public record LocationEntry(double latitude, double longitude, ZoneId timezone) {

    /**
     * Whether the owner chose a place, as set-up needs before it goes on.
     *
     * @return false for coordinates that are not valid, and for {@link Coordinates#NOT_CHOSEN}
     */
    public boolean isChosen() {
        return Coordinates.areValid(latitude, longitude) && new Coordinates(latitude, longitude).isChosen();
    }

    /**
     * A config with this location in it.
     *
     * @param config the config to change
     * @return the same config with this place and time zone
     * @throws io.github.enadeag.carchargeplanner.engine.setting.InvalidSetting when a coordinate is out of range
     */
    public EngineConfig applyTo(EngineConfig config) {
        return config.withSystem(config.system().withLocation(new Coordinates(latitude, longitude), timezone),
                config.typical());
    }
}
