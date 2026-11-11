# Android: Settings

Decisions about the Settings screens: the list, the pages, saving, and the open source licences screen.

What holds on every screen (top app bar, buttons, snackbars, dialogs, spoken names, saved state) is in [screens.md](screens.md); read it too.

### The list

- Settings has seven pages, in the desktop's order: Solar panels, Location, Car and house, Charger, Forecast, Away (weekly away periods) and Planning. There is no About page, no App page and no "Set up again", which is offered for an unusable settings file only (#315).
- After the pages come **Notifications** (bell icon, "Opens Android's settings for this app"), which opens `ACTION_APP_NOTIFICATION_SETTINGS`, or the app's details where a phone lacks it (#361).
- Last comes **Open source licences** (`Info` icon), a read-only screen, not an eighth page, so Save and Discard do not apply to it (#331).
- Each page's row starts with Material's Filled icon, 24 dp, in `ListItem`'s leading colour, without a circle: Solar SolarPower, Location LocationOn, Car and house ElectricCar, Charger EvStation, Forecast WbCloudy, Away EventBusy, Planning Schedule. The icons are decoration; each row is one button that reads its name and values, which wrap without a line limit, with thin `HorizontalDivider`s between rows. There is no chevron and no group header, and the place results keep no icon (#344).
- That each row has its icon is tested by what is drawn, with no test tags in production code (#344).
- The six icons not in `material-icons-core` are copied unchanged from `material-icons-extended-android` into `SettingsIcons.kt` (Apache-2.0 as a whole), each built `by lazy`, because the extended library is too large for six icons; a further icon is copied the same way (#344).

### Saving a page

- Each page has its own Save and stays open after saving. Back or Up with unsaved edits asks "Discard changes?", with Keep editing and Discard. Back goes from a page to the list, then to the home, without a navigation library (#315).
- The open page and the typed texts survive the system ending the app; the API keys and the problems do not (#315).
- A save that worked says so in a snackbar. One that failed puts "Not saved." under the top app bar, read at once, and why, with the English details, at the top of the page that scrolls, so the bar never crowds the page out at 200% (#315).
- The home asks for the plan again only when a save changed something stored; a save outcome no screen showed yet shows on the home; a save that finds the settings file unusable or newer leads to that file's screen (#315).

### Location

- Settings treats a stored time zone as chosen by hand, so typed coordinates leave it alone; set-up keeps its own rule (#315).
- The place search uses the stored primary provider, and says so when that provider has none (#315).

### Panel arrays

- Each array has set-up's fields, in its order, with "More settings" folded; "Add an array" adds an empty one. An array stored by its kWp says "Now set: 4.2 kWp. Enter the number of panels to replace this." and keeps that kWp while the count, the year and the power are empty; a stored tilt or direction shows the nearest choice and is stored as it was until chosen, so showing it is no edit. The list's summary stays "1 array, 3.6 kWp", from the computed kWp (#448).
- Remove acts at once. The last array's Remove is off, with the reason "A system needs at least one panel array", read with the button. Arrays are named by position and name ("Array 2, east roof"); there is no reordering (#315).

### Forecast and API keys

- The page has a primary choice, a fallback choice that includes "None", and a section per provider; the primary and the fallback each have Check connection, on the typed, unsaved values and keys. Plain http is allowed as the engine allows it, and the check then shows the engine's English details (#315).
- The connection check shows a refused key as the provider's own message ("Open-Meteo refused the request (status 401): check the API key"). A check never marks the saved system's forecast as failed nor makes its next request wait; it ignores the provider's wait after an earlier failure, so a newly typed key is tried at once, but a provider's cool-down still holds (#359).
- An API key is a masked field with Show and Hide, filled with the stored key; a key left untouched stays, an emptied one is removed. Keys that cannot be read get an inline note: "Your saved API keys could not be read. Enter them again and save; an API key left empty stays as it is. The app tries to read them again in a few minutes." There is no retry button (#315).
- Keys that could not be stored give "Not saved: the API keys could not be stored. Try again." (#315).
- While the Forecast page shows, its window is `FLAG_SECURE` and its fields ask for no autofill (#315).
- "Wi-Fi only" is the page's last row, a switch that applies at once, outside the page's Save (#361).

### Open source licences

- The screen shows the app's name, its copyright line and "Source code: github.com/Enadeag/car-charge-planner" as plain text, then a row per text: the app's own first (`NOTICE`, the GPL, the additional terms), then the libraries' files (#331).
- A row is one button that expands or collapses its text and says which ("Expanded", "Collapsed"); only expanded texts are read, off the main thread, with their own line breaks (#331).
- The texts and titles stay English and the screen does not say so; its own words are translated, the Dutch row "Opensourcelicenties" (#331).
- The screen and its expanded rows survive turning the phone and the system ending the app, until Settings closes (#331).
