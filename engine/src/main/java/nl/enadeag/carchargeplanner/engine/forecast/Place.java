package nl.enadeag.carchargeplanner.engine.forecast;

import nl.enadeag.carchargeplanner.engine.config.Coordinates;
import java.time.ZoneId;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A place with its coordinates and time zone.
 *
 * @param name the place's name
 * @param location the place's coordinates
 * @param timezone the place's time zone
 * @param region the first administrative area: a state, a province; may be empty
 * @param country the country's name, or its code; may be empty
 * @param area a finer one (county, municipality) that tells apart two places with the same name; may be empty
 */
public record Place(String name, Coordinates location, ZoneId timezone, String region, String country, String area) {

    /**
     * What a list of results shows.
     *
     * @return "name, region, country", leaving out empty parts and a region that repeats the name
     */
    public String label() {
        return join(name, sameAsName(region), country);
    }

    /**
     * The label with the finer area, for when two results would read alike.
     *
     * @return "name, area, region, country", leaving out empty and repeated parts
     */
    public String detailedLabel() {
        var finer = area.equals(name) || area.equals(region) ? "" : area;
        return join(name, finer, sameAsName(region), country);
    }

    private String sameAsName(String part) {
        return Objects.equals(part, name) ? "" : part;
    }

    private static String join(String... parts) {
        return Stream.of(parts).filter(part -> !part.isEmpty()).collect(Collectors.joining(", "));
    }
}
