package nl.enadeag.carchargeplanner.engine.dashboard;

/** Where a day's charge comes from; each client shows that source on the day's tab and card. */
public enum DayKind {

    /** The day has a charge window. */
    SOLAR,

    /** The day without a charge window that would end below the minimum charge level, as the grid advice says. */
    GRID,

    /** The day has no charge window, and the grid advice is not about it. */
    NONE
}
