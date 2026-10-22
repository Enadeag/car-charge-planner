package nl.enadeag.carchargeplanner.engine.calendar;

import nl.enadeag.carchargeplanner.engine.config.ProviderId;

/**
 * Where the calendar's secret settings are kept among the API keys: the calendar's iCal link, which gives whoever has it
 * the whole calendar, and the route service's API key.
 */
public final class CalendarSecrets {

    /** What the iCal link is kept under. */
    public static final ProviderId CALENDAR = new ProviderId("calendar");
    /** The setting name of the iCal link. */
    public static final String ICAL_LINK = "ical-link";
    /** What the route service's API key is kept under: OpenRouteService. */
    public static final ProviderId ROUTE_SERVICE = new ProviderId("openrouteservice");
    /** The setting name of the route service's API key. */
    public static final String API_KEY = "api-key";

    private CalendarSecrets() {
    }
}
