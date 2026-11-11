package nl.enadeag.carchargeplanner.desktop.settings.form;

import nl.enadeag.carchargeplanner.engine.config.Coordinates;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.config.PlaceName;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Where the home is, as the location page shows it.
 *
 * @param latitude the latitude, in degrees
 * @param longitude the longitude, in degrees
 * @param timezone the home's time zone
 * @param place the name of the place chosen for these coordinates; empty when none is, or they were typed
 */
public record LocationEntry(double latitude, double longitude, ZoneId timezone, Optional<PlaceName> place) {

    /**
     * Whether the owner chose a place, as set-up needs before it goes on.
     *
     * @return false for coordinates that are not valid, and for {@link Coordinates#NOT_CHOSEN}
     */
    public boolean isChosen() {
        return Coordinates.areValid(latitude, longitude) && new Coordinates(latitude, longitude).isChosen();
    }

    /**
     * The coordinates, after their record checked them.
     *
     * @return the place
     * @throws nl.enadeag.carchargeplanner.engine.setting.InvalidSetting when a coordinate is out of range
     */
    public Coordinates coordinates() {
        return new Coordinates(latitude, longitude);
    }

    /**
     * A config with this location in it: with the place's name, or without a name, so none kept before stays with
     * these coordinates.
     *
     * @param config the config to change
     * @return the same config with this place, its name or none, and time zone
     * @throws nl.enadeag.carchargeplanner.engine.setting.InvalidSetting when a coordinate is out of range
     */
    public EngineConfig applyTo(EngineConfig config) {
        var system = config.system();
        var located = place.map(name -> system.withPlace(coordinates(), timezone, name))
                .orElseGet(() -> system.withUnnamedLocation(coordinates(), timezone));
        return config.withSystem(located, config.typical());
    }
}
