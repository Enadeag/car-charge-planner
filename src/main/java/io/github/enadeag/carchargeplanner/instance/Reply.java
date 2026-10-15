package io.github.enadeag.carchargeplanner.instance;

/** What came of sending a command to the running app. */
public enum Reply {
    /** There is no socket: no copy of the app runs. */
    NOBODY,
    /** A socket is left, but nothing listens on it: the app that made it has crashed. */
    STALE,
    /** The running app confirmed the command. */
    ANSWERED,
    /** Something listens, but it did not confirm the command in time. */
    SILENT
}
