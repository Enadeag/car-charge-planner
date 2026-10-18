package io.github.enadeag.carchargeplanner.desktop.dashboard;

/** Where a day's charge comes from; its tab shows that source's glyph. */
enum DayKind {
    /** The day has a charge window in the sun. */
    SUN,
    /** The day needs a charge from the grid. */
    GRID,
    /** The day has no charge window. */
    NONE
}
