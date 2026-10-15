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
    /** The solar chart's heading. */
    CHART_SOLAR,
    /** The legend's name for the expected solar power. */
    CHART_SOLAR_LEGEND,
    /** The legend's name for the house's own use. */
    CHART_HOUSE_LEGEND,
    /** The legend's name for the charge windows. */
    CHART_WINDOW_LEGEND,
    /** The label on the line that marks the current time. */
    CHART_NOW,
    /** The top of the power axis: {0} the power in kW. */
    CHART_SCALE,
    /** One step's values on hover: {0} its start time, {1} the solar power in kW, {2} the house's use in kW. */
    CHART_READOUT,
    /** The solar chart in words: {0} the day, {1} the peak power in kW, {2} the peak's time, {3} the house's use in kW. */
    CHART_SPOKEN,
    /** The solar chart in words on a day without sun: {0} the day. */
    CHART_SPOKEN_NO_SUN,
    /** The charge windows in words, after the chart's: {0} the windows, joined. */
    CHART_SPOKEN_WINDOWS,
    /** One window in words: {0} its start, {1} its end. */
    CHART_SPOKEN_SPAN,
    /** The current time in words, after the chart's: {0} the time. */
    CHART_SPOKEN_NOW,
    /** The battery's heading. */
    BATTERY_HEADING,
    /** The battery's levels: {0} at the start, {1} after charging, in %. */
    BATTERY_LEVELS,
    /** The label of the minimum charge level's mark: {0} the minimum in %. */
    BATTERY_MINIMUM,
    /** The label of the target charge level's mark: {0} the target in %. */
    BATTERY_TARGET,
    /** Today's battery in words: {0} now, {1} after charging, {2} the minimum, {3} the target, all in %. */
    BATTERY_SPOKEN_TODAY,
    /** Another day's battery in words: {0} at its start, {1} the day, {2} after charging, {3} the minimum, {4} the target. */
    BATTERY_SPOKEN_DAY,
    /** Added to today's battery in words while its level is assumed. */
    BATTERY_ASSUMED,
    /** The button that opens the battery level's entry. */
    BATTERY_UPDATE,
    /** What the update button does. */
    BATTERY_UPDATE_HINT,
    /** The name of the field the battery level is entered in. */
    BATTERY_LEVEL_FIELD,
    /** The button that saves the entered battery level. */
    BATTERY_SAVE,
    /** The button that closes the battery level's entry without saving. */
    BATTERY_CANCEL,
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
    CLI_START_AT_LOGIN_FAILED,
    /** The tray menu's entry that opens Settings. */
    MENU_SETTINGS,
    /** The tray menu's entry that opens Settings on About. */
    MENU_ABOUT,
    /** The title of the Settings window. */
    SETTINGS_TITLE,
    /** The heading above Settings' categories. */
    SETTINGS_HEADING,
    /** What a screen reader calls the list of Settings' categories. */
    SETTINGS_CATEGORIES,
    /** The category of the planning preferences. */
    CATEGORY_PLANNING,
    /** The category of the desktop app's own settings. */
    CATEGORY_APP,
    /** The category with the version, the licence and the files. */
    CATEGORY_ABOUT,
    /** What a screen reader says for a category with a refused value: {0} the category. */
    CATEGORY_HAS_PROBLEMS,
    /** The button that saves and keeps Settings open. */
    SETTINGS_SAVE,
    /** The button that saves and closes Settings. */
    SETTINGS_SAVE_CLOSE,
    /** The button that closes Settings while there are unsaved changes. */
    SETTINGS_CANCEL,
    /** The button that closes Settings when nothing is unsaved. */
    SETTINGS_CLOSE,
    /** Said after a save. */
    SETTINGS_SAVED,
    /** Said when a value was refused and nothing was saved. */
    SETTINGS_REFUSED,
    /** Said when a file could not be written: {0} the system's error. */
    SETTINGS_SAVE_FAILED,
    /** The question before closing with unsaved changes. */
    SETTINGS_DISCARD_QUESTION,
    /** The button that closes and throws the changes away. */
    SETTINGS_DISCARD,
    /** The button that goes back to the changes. */
    SETTINGS_KEEP_EDITING,
    /** The title of a card in place of settings that cannot be edited now. */
    SETTINGS_NO_CONFIG,
    /** Why: the config file could not be read; the engine's words follow. */
    SETTINGS_NO_CONFIG_HINT,
    /** A value outside the range its setting allows. */
    PROBLEM_OUT_OF_RANGE,
    /** A minimum charge level that is not below the target. */
    PROBLEM_NOT_BELOW_TARGET,
    /** The unit after a number of minutes. */
    UNIT_MINUTES,
    /** The title of the card with the minimum and target charge levels. */
    CARD_CHARGE_LEVELS,
    /** What the charge levels do. */
    CARD_CHARGE_LEVELS_HINT,
    /** The minimum charge level's name. */
    FIELD_MINIMUM_CHARGE,
    /** What the minimum charge level does. */
    FIELD_MINIMUM_CHARGE_HINT,
    /** The target charge level's name. */
    FIELD_TARGET_CHARGE,
    /** What the target charge level does. */
    FIELD_TARGET_CHARGE_HINT,
    /** The title of the card with the margin, import tolerance and window length. */
    CARD_PLAN,
    /** What the card's settings do. */
    CARD_PLAN_HINT,
    /** The planning margin's name. */
    FIELD_MARGIN,
    /** What the planning margin does. */
    FIELD_MARGIN_HINT,
    /** The import tolerance's name. */
    FIELD_IMPORT_TOLERANCE,
    /** What the import tolerance does. */
    FIELD_IMPORT_TOLERANCE_HINT,
    /** The minimum window length's name. */
    FIELD_MIN_WINDOW,
    /** What the minimum window length does. */
    FIELD_MIN_WINDOW_HINT,
    /** The title of the card with the notification lead time. */
    CARD_NOTIFICATIONS,
    /** What notifications do. */
    CARD_NOTIFICATIONS_HINT,
    /** The notification lead time's name. */
    FIELD_NOTIFY_LEAD,
    /** What the notification lead time does. */
    FIELD_NOTIFY_LEAD_HINT,
    /** The title of the card with the display mode and the language. */
    CARD_DISPLAY,
    /** When the card's settings apply. */
    CARD_DISPLAY_HINT,
    /** The display mode's name. */
    FIELD_MODE,
    /** What the display mode needs. */
    FIELD_MODE_HINT,
    /** The automatic display mode. */
    MODE_AUTOMATIC,
    /** The tray display mode. */
    MODE_TRAY,
    /** The window display mode. */
    MODE_WINDOW,
    /** The language's name. */
    FIELD_LANGUAGE,
    /** What the language does. */
    FIELD_LANGUAGE_HINT,
    /** The language choice that follows the system. */
    LANGUAGE_SYSTEM,
    /** The title of the card with the units. */
    CARD_UNITS,
    /** What the units do. */
    CARD_UNITS_HINT,
    /** The units' name. */
    FIELD_UNITS,
    /** The metric units. */
    UNITS_METRIC,
    /** The imperial units. */
    UNITS_IMPERIAL,
    /** The title of the card with start at login. */
    CARD_START_AT_LOGIN,
    /** What start at login does. */
    CARD_START_AT_LOGIN_HINT,
    /** The start at login box. */
    FIELD_START_AT_LOGIN,
    /** Why the box is switched off outside the installed app. */
    START_AT_LOGIN_UNAVAILABLE,
    /** The system refused to change start at login: {0} the system's error. */
    START_AT_LOGIN_FAILED,
    /** The app's version: {0} the version. */
    ABOUT_VERSION,
    /** The title of the licence's card. */
    ABOUT_LICENCE_TITLE,
    /** The licence in a sentence. */
    ABOUT_LICENCE,
    /** The link to the licence. */
    ABOUT_LICENCE_LINK,
    /** The title of the source code's card. */
    ABOUT_SOURCE_TITLE,
    /** Where the source code is. */
    ABOUT_SOURCE,
    /** The link to the project. */
    ABOUT_SOURCE_LINK,
    /** The title of the files' card. */
    ABOUT_FILES_TITLE,
    /** What the files' card shows. */
    ABOUT_FILES,
    /** The engine's config file. */
    ABOUT_CONFIG_FILE,
    /** The app settings file. */
    ABOUT_APP_FILE,
    /** The data folder. */
    ABOUT_DATA_FOLDER,
    /** Where the API keys are. */
    ABOUT_KEYS,
    /** The keys are in the keychain. */
    ABOUT_KEYS_KEYCHAIN,
    /** The keys are in a file: {0} the file. */
    ABOUT_KEYS_FILE
}
