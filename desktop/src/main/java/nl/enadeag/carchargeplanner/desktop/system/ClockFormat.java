package nl.enadeag.carchargeplanner.desktop.system;

/**
 * Whether the system shows clock times on a 12-hour clock, such as 7:30 pm, or on a 24-hour clock, such as 19:30, as
 * the owner set it for the whole desktop.
 */
public enum ClockFormat {
    /** The system says nothing the app can read: times are written the way the app's language writes them. */
    LANGUAGE,
    /** Hours from 1 to 12 with a morning or afternoon marker, such as 7:30 pm. */
    TWELVE_HOUR,
    /** Hours from 00 to 23, such as 19:30. */
    TWENTY_FOUR_HOUR
}
