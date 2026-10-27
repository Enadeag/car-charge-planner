package nl.enadeag.carchargeplanner.desktop.settings.form;

/** A step of the Calendar page, in the order they unlock: each one opens once the one before it is done. */
public enum CalendarStep {
    /** The two consents; always open. */
    CONSENTS,
    /** Which calendar: its iCal link or an {@code .ics} file; open once both consents are given. */
    SOURCE,
    /** The route service's API key; open once a calendar is chosen. */
    API_KEY,
    /** How events become trips: the travel margin and the all-day times; open once the API key is there. */
    SETTINGS
}
