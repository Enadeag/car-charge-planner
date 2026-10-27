package nl.enadeag.carchargeplanner.engine.route;

import nl.enadeag.carchargeplanner.engine.config.Coordinates;
import java.util.Optional;

/**
 * Where a calendar event takes place, as the event gives it. It is personal data, so {@link #toString()} shows none
 * of it.
 *
 * @param text the event's location text, possibly blank
 * @param coordinates the coordinates the event holds itself, such as its GEO field; empty when it holds none
 */
public record Destination(String text, Optional<Coordinates> coordinates) {

    /**
     * A location given only as text.
     *
     * @param text the event's location text, possibly blank
     * @return the destination without coordinates
     */
    public static Destination of(String text) {
        return new Destination(text, Optional.empty());
    }

    /**
     * A location that comes with coordinates of its own.
     *
     * @param text the event's location text, possibly blank
     * @param coordinates where the event takes place, as the event holds it
     * @return the destination with those coordinates
     */
    public static Destination at(String text, Coordinates coordinates) {
        return new Destination(text, Optional.of(coordinates));
    }

    /**
     * A description that may be logged.
     *
     * @return whether it has coordinates, without the text or the coordinates
     */
    @Override
    public String toString() {
        return coordinates.isPresent() ? "Destination[with coordinates]" : "Destination[text only]";
    }
}
