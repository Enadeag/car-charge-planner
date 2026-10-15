package io.github.enadeag.carchargeplanner.desktop;

/** The coloured dot on the tray icon: what the plan asks of the owner, at a glance. */
public enum StatusDot {
    /** Amber: charge on sun now or later today. */
    SUN,
    /** Blue: charge from the grid, as the grid advice says. */
    GRID,
    /** Red: there is no plan until the owner acts, because the app is not set up or has no forecast. */
    ACTION,
    /** No dot: nothing to do today. */
    NONE
}
