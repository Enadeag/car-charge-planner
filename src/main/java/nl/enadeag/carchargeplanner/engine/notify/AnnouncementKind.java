package nl.enadeag.carchargeplanner.engine.notify;

/** What an announcement is about, which decides what the owner is told. */
public enum AnnouncementKind {

    /** A charge window on the solar surplus starts soon. */
    CHARGE_WINDOW,

    /** The window of the grid advice starts soon: without it the battery would end a day below the minimum. */
    GRID_CHARGE
}
