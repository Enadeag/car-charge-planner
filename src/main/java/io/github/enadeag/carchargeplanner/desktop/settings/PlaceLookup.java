package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import io.github.enadeag.carchargeplanner.engine.forecast.PlaceResults;
import java.util.Optional;

/** Finds places for what the owner typed, through a forecast provider's place search. */
@FunctionalInterface
interface PlaceLookup {

    /**
     * Asks the provider's place search; blocks while it is asked, so call it away from the JavaFX thread.
     *
     * @param provider the provider whose search to use, normally the primary
     * @param text what the owner typed: a name or a postcode
     * @return the places, or why there are none; empty when the provider has no place search
     */
    Optional<PlaceResults> search(ProviderId provider, String text);
}
