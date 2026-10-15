package io.github.enadeag.carchargeplanner.engine.forecast;

/** Which of the selected providers made a forecast. */
public enum ProviderRole {

    /** The provider the owner chose, which is asked first. */
    PRIMARY,

    /** The provider that is asked only when the primary has nothing usable. */
    FALLBACK
}
