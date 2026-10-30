package nl.enadeag.carchargeplanner.desktop.dashboard.parts;

/** Where a day's charge comes from; its tab shows that source's glyph. */
public enum DayKind {
    /** The day has a charge window on solar energy. */
    SOLAR,
    /** The day that would end below the minimum charge level without a grid charge. */
    GRID,
    /** The day has no charge window. */
    NONE
}
