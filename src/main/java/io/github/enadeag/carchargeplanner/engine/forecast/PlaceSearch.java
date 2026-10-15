package io.github.enadeag.carchargeplanner.engine.forecast;

/** An ability a forecast provider may have: turning what a person typed into places. */
public interface PlaceSearch {

    /** Blocks while the service is asked; never throws, a failure is a {@link PlaceResults.Failed}. */
    PlaceResults search(String text, String language);
}
