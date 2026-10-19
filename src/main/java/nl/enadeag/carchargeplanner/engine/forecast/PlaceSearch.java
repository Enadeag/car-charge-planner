package nl.enadeag.carchargeplanner.engine.forecast;

/** A place search a forecast provider may offer: it turns what the owner typed into places. */
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
