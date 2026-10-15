package io.github.enadeag.carchargeplanner.engine.forecast;

/** How fresh a forecast is: fetched just now, or from the cache, and then whether a refresh was due. */
public enum Freshness {

    /** Fetched from the provider just now. */
    FRESH,

    /** From the cache, fetched too recently to ask the provider again. */
    CACHED,

    /** From the cache, because a refresh was due but could not be done; still young enough to use. */
    STALE
}
