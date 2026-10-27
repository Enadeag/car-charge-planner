package nl.enadeag.carchargeplanner.engine.route;

import nl.enadeag.carchargeplanner.engine.config.Coordinates;

/**
 * A place a route service found for a location text. It can be an address, so {@link #toString()} shows none of it.
 *
 * @param name the place's name or address as the service writes it, not blank
 * @param location the place's coordinates
 */
public record Candidate(String name, Coordinates location) {

    /**
     * Checks the name.
     *
     * @throws IllegalArgumentException when the name is blank
     */
    public Candidate {
        if (name.isBlank()) {
            throw new IllegalArgumentException("a place found needs a name");
        }
    }

    /**
     * A description that may be logged.
     *
     * @return the type's name only
     */
    @Override
    public String toString() {
        return "Candidate[hidden]";
    }
}
