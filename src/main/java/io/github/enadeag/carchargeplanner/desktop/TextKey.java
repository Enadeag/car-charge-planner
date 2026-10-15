package io.github.enadeag.carchargeplanner.desktop;

/**
 * The texts the app shows. Each constant's name is its key in the resource bundles {@code messages_*.properties}, where
 * every language has every key; the bundles hold the words, with {@code {0}}-style places for the values.
 */
public enum TextKey {
    /** The tooltip of the dashboard's button that opens it in a window. */
    DASHBOARD_DETACH,
    /** What a screen reader calls the strip of day tabs. */
    DASHBOARD_DAYS,
    /** The footer's button for the away periods. */
    FOOTER_AWAY,
    /** The footer's button for the settings. */
    FOOTER_SETTINGS,
    /** A footer button that does nothing yet: {0} is the button's name. */
    FOOTER_SOON,
    /** What a screen reader says for a footer button that does nothing yet: {0} is the button's name. */
    FOOTER_NOT_YET,
    /** The footer's forecast part when the fallback provider stands in: {0} is the primary provider's name. */
    FOOTER_FALLBACK,
    /** The same when the primary provider is not known to the app. */
    FOOTER_FALLBACK_UNKNOWN,
    /** The footer's freshness: {0} is the time the forecast was fetched. */
    FOOTER_UPDATED,
    /** The footer's freshness when a refresh failed: {0} is the time the forecast was fetched. */
    FOOTER_STALE,
    /** The footer's rate limit: {0} is the time the provider may be asked again. */
    FOOTER_BUSY,
    /** The place of the charts, until they are built. */
    CHART_NOT_YET,
    /** The chip above the grid advice. */
    ADVICE_LABEL,
    /** The grid advice's title with a window: {0} kWh, {1} the day in a sentence, {2} the window's times. */
    ADVICE_TITLE_WINDOW,
    /** The grid advice's title without a window: {0} kWh, {1} the day in a sentence. */
    ADVICE_TITLE_NO_WINDOW,
    /** The grid advice's setting: {0} the current in A, {1} the power in kW. */
    ADVICE_SET,
    /** The grid advice when no day has room for a grid charge. */
    ADVICE_NO_ROOM,
    /** Why the grid charge, when the battery would run empty: {0} the day in a sentence, {1} the minimum in %. */
    ADVICE_RUNS_EMPTY,
    /** Why the grid charge: {0} the day in a sentence, {1} the end level in %, {2} the minimum in %. */
    ADVICE_ENDS_LOW,
    /** The dashboard and tray before the first plan. */
    LOADING,
    /** The dashboard's headline without a forecast. */
    NO_FORECAST,
    /** The dashboard's detail when the forecast has no days. */
    NO_FORECAST_NO_DAYS,
    /** The dashboard's detail without a forecast: {0} is the engine's reason, in English. */
    NO_FORECAST_DETAIL,
    /** The dashboard's headline without a usable config file. */
    NOT_SET_UP,
    /** There is no config file: {0} is its path. */
    NOT_SET_UP_MISSING,
    /** The config file could not be read: {0} is its path, {1} the system's error. */
    NOT_SET_UP_UNREADABLE,
    /** The config file is wrong: {0} is its name, {1} the setting and what is wrong, in English. */
    NOT_SET_UP_INVALID,
    /** The tray's tooltip without a usable config file: {0} is what is wrong. */
    TRAY_NOT_SET_UP,
    /** The tray's tooltip without a forecast: {0} is the engine's reason, in English. */
    TRAY_NO_FORECAST,
    /** The tray's tooltip when the forecast has no days. */
    TRAY_NO_DAYS,
    /** The tray's tooltip for a window later today: {0} start, {1} end, {2} the current in A. */
    TRAY_SUN_LATER,
    /** The tray's tooltip during a window: {0} its end, {1} the current in A. */
    TRAY_SUN_NOW,
    /** The tray's tooltip with no window today: {0} the day in a sentence, {1} start, {2} end. */
    TRAY_NEXT,
    /** The tray's tooltip with no window in the coming days. */
    TRAY_NOTHING,
    /** The tray menu's entry and the notifications' button that open the dashboard. */
    OPEN_DASHBOARD,
    /** The tray menu's entry that quits the app. */
    QUIT,
    /** Today, as a tab and a title. */
    DAY_TODAY,
    /** Tomorrow, as a title. */
    DAY_TOMORROW,
    /** Today, in the middle of a sentence. */
    DAY_TODAY_IN_SENTENCE,
    /** Tomorrow, in the middle of a sentence. */
    DAY_TOMORROW_IN_SENTENCE,
    /** Today's note while the battery level is assumed: {0} the level in %. */
    DAY_BATTERY_ASSUMED,
    /** A window's setting: {0} the current in A, {1} the power in kW. */
    DAY_SET,
    /** A window's energy: {0} kWh. */
    DAY_ENERGY,
    /** A window's share of sun: {0} in %. */
    DAY_SUN_SHARE,
    /** A window's energy from the grid: {0} kWh. */
    DAY_FROM_GRID,
    /** A day with a window for a screen reader: {0} the day, {1} start, {2} end, {3} the current in A, {4} sun in %. */
    DAY_SPOKEN,
    /** The heading of a day with a charge window. */
    DAY_WINDOW,
    /** The heading of the day that needs a grid charge. */
    DAY_GRID,
    /** Why that day has no window. */
    DAY_GRID_WHY,
    /** The heading of a day without a charge window. */
    DAY_NONE,
    /** Why a day has no window. */
    DAY_NONE_WHY,
    /** The battery chip: {0} the level at the start of the day, {1} after charging, both in %. */
    DAY_BATTERY,
    /** A later window of the same day: {0} its times, {1} the current in A. */
    DAY_LATER_WINDOW,
    /** The later windows of the same day: {0} the windows, separated by commas. */
    DAY_LATER_WINDOWS,
    /** A notification's setting on one phase: {0} the current in A, {1} the power in kW. */
    NOTIFY_SET_ONE_PHASE,
    /** A notification's setting on three phases: {0} the current per phase in A, {1} the power in kW. */
    NOTIFY_SET_THREE_PHASES,
    /** The title of a charge window's notification: {0} the window's times. */
    NOTIFY_WINDOW_TITLE,
    /** The rest of a charge window's notification: {0} kWh, {1} sun in %. */
    NOTIFY_WINDOW_BODY,
    /** The title of a grid charge's notification: {0} the window's times. */
    NOTIFY_GRID_TITLE,
    /** The rest of a grid charge's notification: {0} kWh, {1} the minimum in %. */
    NOTIFY_GRID_BODY,
    /** A second start handed over to the running app: {0} the app's name. */
    CLI_HANDED_OVER,
    /** A second start found the running app silent: {0} the app's name. */
    CLI_NO_ANSWER,
    /** The running app confirmed it quits: {0} the app's name. */
    CLI_QUIT_CONFIRMED,
    /** The running app did not answer the request to quit: {0} the app's name. */
    CLI_QUIT_SILENT,
    /** No app was running to quit: {0} the app's name. */
    CLI_NOT_RUNNING,
    /** Start at login was asked for outside the installed app. */
    CLI_START_AT_LOGIN_NEEDS_INSTALL,
    /** Start at login was switched on: {0} the app's name. */
    CLI_START_AT_LOGIN_ON,
    /** Start at login was switched off: {0} the app's name. */
    CLI_START_AT_LOGIN_OFF,
    /** Start at login could not be changed: {0} the system's error. */
    CLI_START_AT_LOGIN_FAILED
}
