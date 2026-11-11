package nl.enadeag.carchargeplanner.desktop.settings.form;

import nl.enadeag.carchargeplanner.engine.config.Coordinates;
import nl.enadeag.carchargeplanner.engine.config.PlaceName;
import java.util.Optional;

/**
 * A place with its name, chosen from the place search or read from the settings, and the coordinates it is the name
 * of. The name holds while the location page's fields show these coordinates as they write them, so typing a digit
 * and putting it back keeps it, and a real difference drops it.
 *
 * @param name the place's name
 * @param location the coordinates the name belongs to
 */
public record ChosenPlace(PlaceName name, Coordinates location) {

    /** The decimals the location page's coordinate fields show, which is what the comparison goes by. */
    public static final int DECIMALS = 4;

    /**
     * The name, while the fields hold this place's coordinates.
     *
     * @param latitude the latitude the field holds now, in degrees, typed or not
     * @param longitude the longitude the field holds now, in degrees, typed or not
     * @param text writes the numbers as the fields do, in the owner's language
     * @return the name when both coordinates are written the same as this place's at {@link #DECIMALS} decimals;
     *     empty once one of them differs
     */
    public Optional<PlaceName> nameAt(double latitude, double longitude, NumberText text) {
        var same = text.write(latitude, DECIMALS).equals(text.write(location.latitude(), DECIMALS))
                && text.write(longitude, DECIMALS).equals(text.write(location.longitude(), DECIMALS));
        return same ? Optional.of(name) : Optional.empty();
    }
}
