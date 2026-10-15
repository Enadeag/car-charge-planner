package io.github.enadeag.carchargeplanner.engine.forecast;

/** An ability a forecast provider may have: turning what a person typed into places. */
public interface PlaceSearch {

    /**
     * Asks the service for places; blocks while it is asked.
     *
     * @param text what the owner typed: a name or a postcode; fewer than two characters is not searched
     * @param language the language the names should come in, such as "nl" or "nl_NL"; empty means English
     * @return the places found, possibly none, or why there are none; never throws
     */
    PlaceResults search(String text, String language);
}
