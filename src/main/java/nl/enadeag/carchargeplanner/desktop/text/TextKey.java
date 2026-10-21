package nl.enadeag.carchargeplanner.desktop.text;

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
    /** What the footer's Away button opens: the weekly away periods, in Settings. */
    FOOTER_AWAY_HINT,
    /** The footer's button for the settings. */
    FOOTER_SETTINGS,
    /** The footer's forecast part when the fallback provider stands in: {0} is the primary provider's name. */
    FOOTER_FALLBACK,
    /** The same when the primary provider is not known to the app. */
    FOOTER_FALLBACK_UNKNOWN,
    /** The footer's freshness: {0} is the time the forecast was fetched. */
    FOOTER_UPDATED,
    /** The footer's freshness when a refresh failed: {0} is the time the forecast was fetched. */
    FOOTER_STALE,
    /** The footer's cool-down: {0} is the time the provider may be asked again. */
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
    /** The solar chart in words on a day without solar power: {0} the day. */
    CHART_SPOKEN_NO_SOLAR,
    /** The charge windows in words, after the chart's: {0} one window, or several joined with commas. */
    CHART_SPOKEN_WINDOWS,
    /** One window in words: {0} its start, {1} its end. */
    CHART_SPOKEN_SPAN,
    /** The current time in words, after the chart's: {0} the time. */
    CHART_SPOKEN_NOW,
    /** What the arrow keys do on the solar chart, read by a screen reader after the chart's text. */
    CHART_KEYS,
    /** The battery's heading. */
    BATTERY_HEADING,
    /** The battery's levels: {0} at the start, {1} after charging, in %. */
    BATTERY_LEVELS,
    /** The label of the minimum charge level's mark: {0} the minimum, a number of percent without the sign. */
    BATTERY_MINIMUM,
    /** The label of the target charge level's mark: {0} the target, a number of percent without the sign. */
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
    /** Why a battery level that is not a whole number is not saved. */
    BATTERY_NOT_A_NUMBER,
    /** The heading of the card with the away periods entered for specific days. */
    ONE_OFF_HEADING,
    /** The button that opens the entry of an away period on the selected day. */
    ONE_OFF_ADD,
    /** What the add button does: {0} the selected day as a title, such as "Tomorrow" or "Sunday". */
    ONE_OFF_ADD_HINT,
    /** Said when no away period is entered for a day to come. */
    ONE_OFF_NONE,
    /** The choice for an away period of the whole day. */
    ONE_OFF_WHOLE_DAY,
    /** The choice for an away period of part of the day, between two times. */
    ONE_OFF_PART_OF_DAY,
    /** The button that saves the entered away period. */
    ONE_OFF_SAVE,
    /** The button that closes the away period's entry without saving. */
    ONE_OFF_CANCEL,
    /** Said when the entered end time is not after the start time. */
    ONE_OFF_END_BEFORE_START,
    /** Said when the entered away period has ended already. */
    ONE_OFF_PASSED,
    /** An away period of a whole day: {0} the day, such as "Tomorrow" or "Sat 17 Oct". */
    ONE_OFF_ALL_DAY,
    /** An away period on one day: {0} the day, {1} its times, such as "10:00 – 16:30". */
    ONE_OFF_PART,
    /** An away period over more days: {0} the first day, {1} its start time, {2} the last day, {3} its end time. */
    ONE_OFF_DAYS,
    /** The button that removes an away period. */
    ONE_OFF_REMOVE,
    /** What a screen reader calls the remove button: {0} the period, such as "Tomorrow, all day". */
    ONE_OFF_REMOVE_SPOKEN,
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
    /** Without a forecast because the API keys are in a locked keychain: what the owner should do. */
    NO_FORECAST_KEYCHAIN_LOCKED,
    /** The dashboard's headline without a usable config file. */
    NOT_SET_UP,
    /** The config file could not be read: {0} is its path, {1} the system's error. */
    NOT_SET_UP_UNREADABLE,
    /** The config file is wrong: {0} is its name, {1} the setting and what is wrong, in English. */
    NOT_SET_UP_INVALID,
    /** The tray's tooltip without a usable config file: {0} is what is wrong. */
    TRAY_NOT_SET_UP,
    /** The tray's tooltip without a forecast: {0} is the engine's reason, in English. */
    TRAY_NO_FORECAST,
    /** The tray's tooltip without a forecast because the API keys are in a locked keychain: what the owner should do. */
    TRAY_NO_FORECAST_KEYCHAIN_LOCKED,
    /** The tray's tooltip when the forecast has no days. */
    TRAY_NO_DAYS,
    /** The tray's tooltip for a charge window later today: {0} start, {1} end, {2} the charge current in A. */
    TRAY_SOLAR_LATER,
    /** The tray's tooltip during a charge window: {0} its end, {1} the charge current in A. */
    TRAY_SOLAR_NOW,
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
    /** A charge window's solar share: {0} in %. */
    DAY_SOLAR_SHARE,
    /** A window's energy from the grid: {0} kWh. */
    DAY_FROM_GRID,
    /**
     * A day with a charge window for a screen reader: {0} the day, {1} start, {2} end, {3} the charge current in A,
     * {4} the solar share in %.
     */
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
    /** A notification's setting on one phase: {0} the charge current in A, {1} the power in kW. */
    NOTIFY_SET_ONE_PHASE,
    /** A notification's setting on three phases: {0} the charge current per phase in A, {1} the power in kW. */
    NOTIFY_SET_THREE_PHASES,
    /** The title of a charge window's notification: {0} the window's times. */
    NOTIFY_WINDOW_TITLE,
    /** The rest of a charge window's notification: {0} kWh, {1} the solar share in %. */
    NOTIFY_WINDOW_BODY,
    /** The title of a grid charge's notification: {0} the window's times. */
    NOTIFY_GRID_TITLE,
    /** The rest of a grid charge's notification: {0} kWh, {1} the minimum in %. */
    NOTIFY_GRID_BODY,
    /** The title of the test notification, which the command line asks for. */
    NOTIFY_TEST_TITLE,
    /** The rest of the test notification: {0} the app's name. */
    NOTIFY_TEST_BODY,
    /** A second start handed over to the running app: {0} the app's name. */
    CLI_HANDED_OVER,
    /** A second start found the running app silent: {0} the app's name. */
    CLI_NO_ANSWER,
    /** A second start found the running app's lock, but could not reach it: {0} the app's name. */
    CLI_UNREACHABLE,
    /** The running app could not make its socket, so later starts cannot reach it: {0} the app's name. */
    CLI_RUNS_UNREACHABLE,
    /**
     * The app did not start, as the single-instance guard's folder is not the user's only: {0} the app's name, {1}
     * what is wrong with which folder, in English.
     */
    CLI_INSTANCE_NOT_PRIVATE,
    /** The running app confirmed it quits: {0} the app's name. */
    CLI_QUIT_CONFIRMED,
    /** The running app did not answer a command, such as the request to quit: {0} the app's name. */
    CLI_COMMAND_SILENT,
    /** The running app's lock is held, but a command could not reach it: {0} the app's name. */
    CLI_COMMAND_UNREACHABLE,
    /**
     * A command was not sent, as the single-instance guard's folder is not the user's only: {0} the app's name, {1}
     * what is wrong with which folder, in English.
     */
    CLI_COMMAND_NOT_PRIVATE,
    /**
     * A command was not sent, as the single-instance guard's folder or lock could not be read: {0} the app's name,
     * {1} the system's error, in English.
     */
    CLI_COMMAND_FAILED,
    /** No app was running to quit: {0} the app's name. */
    CLI_NOT_RUNNING,
    /** The running app confirmed it shows a test notification: {0} the app's name. */
    CLI_TEST_NOTIFICATION_SHOWN,
    /** No app was running to show a test notification: {0} the app's name. */
    CLI_TEST_NOTIFICATION_NOT_RUNNING,
    /** Start at login was asked for outside the installed app. */
    CLI_START_AT_LOGIN_NEEDS_INSTALL,
    /** Start at login was switched on: {0} the app's name. */
    CLI_START_AT_LOGIN_ON,
    /** Start at login was switched off: {0} the app's name. */
    CLI_START_AT_LOGIN_OFF,
    /** Start at login could not be changed: {0} the system's error. */
    CLI_START_AT_LOGIN_FAILED,
    /** The menu entry was asked for outside the installed app. */
    CLI_LAUNCHER_NEEDS_INSTALL,
    /** The menu entry is for Linux only: {0} the app's name. */
    CLI_LAUNCHER_LINUX_ONLY,
    /** The .deb or .rpm that installed the app put it in the menu already: {0} the app's name. */
    CLI_LAUNCHER_FROM_PACKAGE,
    /** The Flatpak puts the app in the menu itself, so the menu entry commands do nothing: {0} the app's name. */
    CLI_LAUNCHER_FROM_FLATPAK,
    /** The menu entry was written: {0} the app's name, {1} the files written, one per line. */
    CLI_LAUNCHER_INSTALLED,
    /** The menu entry could not be written: {0} the system's error. */
    CLI_LAUNCHER_INSTALL_FAILED,
    /** The menu entry was removed: {0} the app's name, {1} the files removed, one per line. */
    CLI_LAUNCHER_REMOVED,
    /** There was no menu entry to remove: {0} the app's name. */
    CLI_LAUNCHER_NOT_FOUND,
    /** The menu entry could not be removed: {0} the system's error. */
    CLI_LAUNCHER_REMOVE_FAILED,
    /** The tray menu's entry that opens Settings. */
    MENU_SETTINGS,
    /** The tray menu's entry that opens Settings on About. */
    MENU_ABOUT,
    /** The title of the Settings window: {0} the app's name. */
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
    /** Said when a value was refused and nothing was saved: the refused values are marked. */
    SETTINGS_REFUSED,
    /** Said when a file could not be written: {0} the system's error. */
    SETTINGS_SAVE_FAILED,
    /** Said when nothing was saved because the keychain stayed locked. */
    SETTINGS_KEYCHAIN_LOCKED,
    /** Said when the API keys could not be stored for another reason, which the log has in English. */
    SETTINGS_KEYS_NOT_SAVED,
    /** Said while Settings or set-up reads what is stored, before anything can be edited. */
    SETTINGS_READING,
    /** Said while Save or Finish stores what was entered. */
    SETTINGS_SAVING,
    /** Said while Save or Finish waits for the owner to unlock the keychain in the system's prompt. */
    SETTINGS_SAVING_UNLOCK,
    /** Said when what is stored could not be read: {0} what went wrong. */
    SETTINGS_READ_FAILED,
    /** What went wrong when the app itself failed, in place of the system's error: {0} the kind of failure. */
    UNEXPECTED_ERROR,
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
    /** The charging hours' name, which their first time completes. */
    FIELD_CHARGING_HOURS,
    /** What the charging hours do. */
    FIELD_CHARGING_HOURS_HINT,
    /** The word between the two times of the charging hours. */
    CHARGING_HOURS_AND,
    /** What a screen reader calls the end of the charging hours. */
    CHARGING_HOURS_END_FIELD,
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
    /** The appearance's name: light, dark or as the system is. */
    FIELD_APPEARANCE,
    /** The appearance that follows the system's light or dark. */
    APPEARANCE_SYSTEM,
    /** The appearance that is always light. */
    APPEARANCE_LIGHT,
    /** The appearance that is always dark. */
    APPEARANCE_DARK,
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
    /** The log file. */
    ABOUT_LOG_FILE,
    /** Where the API keys are. */
    ABOUT_KEYS,
    /** About's short answer to where the API keys are: in the keychain. */
    ABOUT_KEYS_KEYCHAIN,
    /** The keys are in a file: {0} the file. */
    ABOUT_KEYS_FILE,
    /** The keys are in the keychain, which is locked. */
    ABOUT_KEYS_LOCKED,
    /** The category of the panel arrays. */
    CATEGORY_SOLAR,
    /** The category of the home's place and time zone. */
    CATEGORY_LOCATION,
    /** The category of the car and the house's use. */
    CATEGORY_CAR_HOUSE,
    /** The category of the charger. */
    CATEGORY_CHARGER,
    /** A highest current below the lowest. */
    PROBLEM_BELOW_MINIMUM_CURRENT,
    /** The tag on a value that is a typical value. */
    TYPICAL_TAG,
    /** The button that fills a card with typical values. */
    USE_TYPICAL,
    /**
     * A control's name for a screen reader where the same control shows more than once: {0} the control's own name,
     * {1} what tells it apart, such as "Panel array 2" or a provider's name.
     */
    IN_GROUP,
    /** The title of a panel array's card: {0} its number. */
    CARD_PANEL_ARRAY,
    /** What a panel array is. */
    CARD_PANEL_ARRAY_HINT,
    /** The button that adds a panel array. */
    PANELS_ADD,
    /** The button that removes a panel array. */
    PANELS_REMOVE,
    /** The panel array's name. */
    FIELD_PANEL_NAME,
    /** What the name is for. */
    FIELD_PANEL_NAME_HINT,
    /** The panel array's size. */
    FIELD_PANEL_SIZE,
    /** What the size is. */
    FIELD_PANEL_SIZE_HINT,
    /** The panel array's tilt. */
    FIELD_PANEL_TILT,
    /** What the tilt is. */
    FIELD_PANEL_TILT_HINT,
    /** The panel array's direction. */
    FIELD_PANEL_DIRECTION,
    /** What the direction is. */
    FIELD_PANEL_DIRECTION_HINT,
    /** The panel array's losses. */
    FIELD_PANEL_LOSSES,
    /** What the losses are. */
    FIELD_PANEL_LOSSES_HINT,
    /** The title of the place search's card. */
    CARD_PLACE,
    /** What the place search does. */
    CARD_PLACE_HINT,
    /** What a screen reader calls the place search's text field. */
    PLACE_FIELD,
    /** The text in the empty place search field. */
    PLACE_PROMPT,
    /** The button that searches. */
    PLACE_SEARCH,
    /** Said while the search runs. */
    PLACE_SEARCHING,
    /** Said above the results. */
    PLACE_CHOOSE,
    /** Said after a place was chosen: {0} the place. */
    PLACE_CHOSEN,
    /** No places matched: {0} what was typed. */
    PLACE_NONE,
    /** The provider has no place search. */
    PLACE_NO_SEARCH,
    /** Fewer than two characters were typed. */
    PLACE_TOO_SHORT,
    /** The search could not be reached. */
    PLACE_UNAVAILABLE,
    /** The search refuses more requests for now. */
    PLACE_BUSY,
    /** The search gave an answer the app cannot read. */
    PLACE_BAD_RESPONSE,
    /** The title of the coordinates' card. */
    CARD_COORDINATES,
    /** What the coordinates are for. */
    CARD_COORDINATES_HINT,
    /** The latitude. */
    FIELD_LATITUDE,
    /** What the latitude is. */
    FIELD_LATITUDE_HINT,
    /** The longitude. */
    FIELD_LONGITUDE,
    /** What the longitude is. */
    FIELD_LONGITUDE_HINT,
    /** The time zone. */
    FIELD_TIMEZONE,
    /** What the time zone is. */
    FIELD_TIMEZONE_HINT,
    /** The title of the car's card. */
    CARD_CAR,
    /** What the car's values are for. */
    CARD_CAR_HINT,
    /** The usable battery. */
    FIELD_CAR_BATTERY,
    /** What the usable battery is. */
    FIELD_CAR_BATTERY_HINT,
    /** The consumption. */
    FIELD_CAR_CONSUMPTION,
    /** What the consumption is. */
    FIELD_CAR_CONSUMPTION_HINT,
    /** The distance a day. */
    FIELD_CAR_DISTANCE,
    /** What the distance is. */
    FIELD_CAR_DISTANCE_HINT,
    /** The title of the house's card. */
    CARD_HOUSE,
    /** What the house's value is for. */
    CARD_HOUSE_HINT,
    /** The house's baseload. */
    FIELD_BASELOAD,
    /** What the baseload is. */
    FIELD_BASELOAD_HINT,
    /** The title of the charger's card. */
    CARD_CHARGER,
    /** What the charger's values are for. */
    CARD_CHARGER_HINT,
    /** The charger's phases. */
    FIELD_PHASES,
    /** What the phases are. */
    FIELD_PHASES_HINT,
    /** One phase. */
    PHASES_ONE,
    /** Three phases. */
    PHASES_THREE,
    /** The lowest charge current. */
    FIELD_MIN_CURRENT,
    /** What the lowest charge current is. */
    FIELD_MIN_CURRENT_HINT,
    /** The highest charge current. */
    FIELD_MAX_CURRENT,
    /** What the highest charge current is. */
    FIELD_MAX_CURRENT_HINT,
    /** The voltage. */
    FIELD_VOLTAGE,
    /** What the voltage is. */
    FIELD_VOLTAGE_HINT,
    /** The charging efficiency. */
    FIELD_EFFICIENCY,
    /** What the efficiency is. */
    FIELD_EFFICIENCY_HINT,
    /** The category of the forecast providers. */
    CATEGORY_FORECAST,
    /** The category of the weekly away periods. */
    CATEGORY_AWAY,
    /** A fallback provider that is the primary one. */
    PROBLEM_SAME_AS_PRIMARY,
    /** An away period that does not end after it starts. */
    PROBLEM_NOT_AFTER_START,
    /** Charging hours that end at the time they start. */
    PROBLEM_SAME_AS_START,
    /** A time that is not a time. */
    PROBLEM_NOT_A_TIME,
    /** A required provider setting that is empty. */
    PROBLEM_REQUIRED,
    /** A provider's server address that a key may not be sent to. */
    PROBLEM_SERVER_ADDRESS,
    /** A provider setting, such as an API key, that must be letters and digits only. */
    PROBLEM_LETTERS_AND_DIGITS,
    /** A provider setting of any text that holds a line break or another control character. */
    PROBLEM_ONE_LINE,
    /** The title of the card that chooses the providers. */
    CARD_PROVIDERS,
    /** What the providers are. */
    CARD_PROVIDERS_HINT,
    /** The primary provider. */
    FIELD_PRIMARY,
    /** What the primary provider is. */
    FIELD_PRIMARY_HINT,
    /** The fallback provider. */
    FIELD_FALLBACK,
    /** What the fallback provider is. */
    FIELD_FALLBACK_HINT,
    /** No fallback provider. */
    FALLBACK_NONE,
    /** A provider this version does not know: {0} its id. */
    PROVIDER_UNKNOWN,
    /** What is sent to a provider: {0} in words. */
    PROVIDER_SENDS,
    /** A provider's limits: {0} minutes between requests, {1} days ahead. */
    PROVIDER_LIMITS,
    /** The link to a provider's terms. */
    PROVIDER_TERMS,
    /** The tag on a required provider setting. */
    PROVIDER_REQUIRED,
    /** The button that tries a provider. */
    PROVIDER_TEST,
    /** Said while a provider is tried: {0} its name. */
    PROVIDER_TESTING,
    /** A provider answered: {0} days of forecast. */
    PROVIDER_WORKS,
    /** A provider did not answer: {0} the engine's words, in English. */
    PROVIDER_FAILED,
    /** Open-Meteo's description. */
    PROVIDER_OPEN_METEO,
    /** What is sent to Open-Meteo. */
    PROVIDER_OPEN_METEO_SENDS,
    /** Forecast.Solar's description. */
    PROVIDER_FORECAST_SOLAR,
    /** What is sent to Forecast.Solar. */
    PROVIDER_FORECAST_SOLAR_SENDS,
    /** The name of a provider's API key. */
    PROVIDER_API_KEY,
    /** The name of a provider's server address. */
    PROVIDER_BASE_URL,
    /** What Open-Meteo's key is for. */
    PROVIDER_OPEN_METEO_API_KEY_HINT,
    /** What Open-Meteo's server address is for. */
    PROVIDER_OPEN_METEO_BASE_URL_HINT,
    /** What Forecast.Solar's key is for. */
    PROVIDER_FORECAST_SOLAR_API_KEY_HINT,
    /** The button that shows a hidden key. */
    SECRET_SHOW,
    /** The button that hides a key again. */
    SECRET_HIDE,
    /** In a key's field that could not be read because the keychain is locked. */
    SECRET_LOCKED,
    /** The button that lets the owner enter a new key in place of one that could not be read. */
    SECRET_REPLACE,
    /** What a screen reader says for that button: {0} the key's field. */
    SECRET_LOCKED_SPOKEN,
    /** The title of the card about where the keys are kept. */
    CARD_KEYS,
    /** The note on the keys' card when the API keys are in the keychain, not in the settings files. */
    KEYS_IN_KEYCHAIN,
    /** The keys are in a file: {0} the file. */
    KEYS_IN_FILE,
    /** The keys are in the keychain, which is locked, so they cannot be read yet. */
    KEYS_IN_LOCKED_KEYCHAIN,
    /** The stored keys could not be read, and what to do; the engine's own words about why go to the log. */
    KEYS_PROBLEM,
    /** The title of the weekly away periods' card. */
    CARD_AWAY,
    /** What weekly away periods are. */
    CARD_AWAY_HINT,
    /** Said when there are no weekly away periods. */
    AWAY_NONE,
    /** The button that adds a weekly away period. */
    AWAY_ADD,
    /** The button that removes one. */
    AWAY_REMOVE,
    /** The word between the weekday and the start. */
    AWAY_FROM,
    /** The word between the start and the end. */
    AWAY_TO,
    /** What a screen reader calls the weekday. */
    AWAY_WEEKDAY,
    /** What a screen reader calls the start. */
    AWAY_FROM_FIELD,
    /** What a screen reader calls the end. */
    AWAY_TO_FIELD,
    /** One weekly away period, to tell its fields apart for a screen reader: {0} its number, from 1. */
    AWAY_PERIOD_NUMBER,
    /** The title of the set-up card. */
    CARD_SET_UP,
    /** What running set-up again does. */
    CARD_SET_UP_HINT,
    /** The button that runs set-up again. */
    RUN_SET_UP,
    /** Said at the latitude while set-up has no place for the home yet. */
    PROBLEM_NO_PLACE,
    /** The dashboard's message before the first set-up. */
    NOT_SET_UP_FIRST,
    /** The button on that message that opens set-up. */
    START_SET_UP,
    /** The tray menu's entry that opens set-up, in place of Settings before the first set-up. */
    MENU_SET_UP,
    /** The tray's tooltip before the first set-up. */
    TRAY_NOT_SET_UP_FIRST,
    /** The set-up window's title: {0} the app's name. */
    SET_UP_TITLE,
    /** Above set-up's page: {0} the step's number, {1} how many steps there are. */
    SET_UP_STEP,
    /** What a screen reader says of set-up's progress bar: {0} the step's number, {1} how many steps there are, {2} the step's name. */
    SET_UP_PROGRESS,
    /** Set-up's button to the step before. */
    SET_UP_BACK,
    /** Set-up's button to the step after. */
    SET_UP_NEXT,
    /** Set-up's button on its last step, which saves everything. */
    SET_UP_FINISH,
    /** Set-up's button that leaves it without saving. */
    SET_UP_CANCEL,
    /** Asked before set-up is left after the owner started. */
    SET_UP_LEAVE_QUESTION,
    /** The answer that stays in set-up. */
    SET_UP_KEEP_GOING,
    /** The answer that leaves set-up without saving. */
    SET_UP_LEAVE,
    /** Said when a step's values are refused: they show their problem at their field. */
    SET_UP_REFUSED,
    /** The first step's name. */
    SET_UP_WELCOME,
    /** What the app does, on the first step. */
    SET_UP_WELCOME_INTRO,
    /** The title of the card about what leaves the computer. */
    SET_UP_PRIVACY_TITLE,
    /** What leaves the computer: {0} the forecast provider's name, {1} what it is sent. */
    SET_UP_PRIVACY,
    /** Added when the provider has a place search: {0} its name. */
    SET_UP_PRIVACY_PLACE,
    /** The title of the card about values the owner does not know. */
    SET_UP_TYPICAL_TITLE,
    /** What to do with a value the owner does not know. */
    SET_UP_TYPICAL,
    /** The car step's name. */
    SET_UP_CAR,
    /** The name of the step with the house and the away periods. */
    SET_UP_MORE,
    /** What that step is for. */
    SET_UP_MORE_HINT,
    /** The name of the step that asks for one forecast. */
    SET_UP_CHECK,
    /** The title of the card with the check. */
    SET_UP_CHECK_CARD,
    /** What the check does. */
    SET_UP_CHECK_HINT,
    /** Said while the provider is asked: {0} its name. */
    SET_UP_CHECK_ASKING,
    /** Said when it answered: {0} its name, {1} the days of forecast. */
    SET_UP_CHECK_WORKS,
    /** Said when it did not: {0} why, in the engine's words, without a full stop at the end. */
    SET_UP_CHECK_FAILED,
    /** The button that asks again. */
    SET_UP_CHECK_AGAIN,
    /** The last step's name. */
    SET_UP_PREVIEW,
    /** What the last step shows. */
    SET_UP_PREVIEW_HINT,
    /** What the display card says in set-up, where the language and the appearance show at once. */
    SET_UP_DISPLAY_HINT
}
