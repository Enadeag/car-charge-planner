package nl.enadeag.carchargeplanner.android.settings

/** The pages of Settings, in the desktop's order, as the list shows them (#315); each has its own Save. */
enum class SettingsPage {
    /** Every panel array. */
    SOLAR,
    /** The coordinates, the place search and the time zone. */
    LOCATION,
    /** The car, and the house's own use. */
    CAR_AND_HOUSE,
    /** The wall charger. */
    CHARGER,
    /** The forecast providers and their API keys. */
    FORECAST,
    /** The weekly away periods. */
    AWAY,
    /** The planning preferences: levels, margin, tolerance, window, lead and charging hours. */
    PLANNING,
}
