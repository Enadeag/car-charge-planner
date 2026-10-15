package io.github.enadeag.carchargeplanner.engine.forecast;

import java.time.ZoneId;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A place a person can pick as the location of their panels.
 *
 * @param region the first administrative area: a state, a province; may be empty
 * @param area a finer one (county, municipality) that tells apart two places with the same name; may be empty
 */
public record Place(String name, double latitude, double longitude, ZoneId timezone, String region, String country,
        String area) {

    /** "Name, region, country": what a list of results shows. */
    public String label() {
        return join(name, sameAsName(region), country);
    }

    /** The label with the finer area, for when two results would read alike. */
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
