# Car Charge Planner

A tool that tells the user each day when to charge their electric car so that as much of their own solar energy as possible is used.

## Language

**Feed-in**:
Solar energy that is produced but not used in the home or car, and flows back to the grid. The thing the user wants to minimise.
_Avoid_: Return, teruglevering, export

**Charge window**:
A contiguous period of the day that the tool recommends for charging the car, chosen to capture the most solar surplus.
_Avoid_: Slot, charge time

**Charge power**:
The charging rate (kW) the tool recommends for a charge window, chosen to match the expected solar surplus. The user sets it in the charger's app.
_Avoid_: Charge speed, charge rate, current

**Surplus**:
Solar production minus the house's own use at a given moment; what is left over for the car (or would be fed in).
_Avoid_: Excess, spare solar

**Away period**:
A span of time when the car is not at home and so cannot be charged. Recurring (for example weekdays 06:00–19:00) or entered by hand for a specific day.
_Avoid_: Absence, trip

**Announcement**:
A notification that tells the owner, the notification lead time before a charge window starts, what to set in the charger's app; also for the window of a grid charge. Each window is announced once, and not while the forecast could not be refreshed. A click on it opens the dashboard.
_Avoid_: Reminder, alert

**Minimum charge level**:
The battery percentage the car must never fall below (30% unless the user changes it). The plan must keep the car above it, even if that means charging outside the best solar window.
_Avoid_: Floor, reserve

**Grid advice**:
The advice to charge from the grid, because on sun alone the battery would end a day below the minimum charge level: how much energy, and the charge window to take it in, on that day or the latest day before it with room. The charging it asks for is a **grid charge**, announced like any charge window.
_Avoid_: Grid warning, top-up, emergency charge

**Assumed battery level**:
The battery level a plan starts from while the owner has not entered the level the car shows. The dashboard says it is assumed until the owner enters one. Not a typical value: it is not a setting, and the next entered level replaces it.
_Avoid_: Default level, estimated level, state of charge

**Self-consumption**:
The share of produced solar energy that is used on-site (home or car) rather than fed in. The primary quantity the tool maximises.
_Avoid_: Own use, direct use

## Setting up

**Set-up**:
The guided first-run process in which the user describes their system (location, panels, car, charger, and optionally house and away times) so a plan can be made. Short for what the user does once; the ongoing editing of values is Settings.
_Avoid_: Onboarding, configuration, wizard (the wizard is only the form set-up takes)

**Not set up**:
The state before a real location has been saved: no forecast is fetched and no plan is shown, only a way into set-up.
_Avoid_: Unconfigured, first run (first run is the moment, not the state)

**Panel array**:
Solar panels that share one tilt and one direction, described by their size (kWp), tilt, direction, losses and an optional name. A home has one or more (an east and a west roof, a garage); the forecast is the sum of all of them.
_Avoid_: Plane (the forecast services' word), string (an electrical term), roof

**Typical value**:
A setting the user did not give ("I don't know") and the app filled in with a common value. It is flagged as an estimate until the user saves a value of their own.
_Avoid_: Default, assumed value, guess

**Planning preferences**:
The owner's choices about how the plan is made: the minimum and target charge level, the planning margin, the import tolerance, the minimum window length and the notification lead time. Kept by the engine beside the description of the system; never a typical value.
_Avoid_: Planning settings, options

**App settings**:
The desktop app's own settings: whether it shows itself as a tray icon or a window, the units it shows, its language and whether it looks light, dark or as the system does. Kept by the desktop app, not the engine, so they stay on the computer even when the engine runs elsewhere.
_Avoid_: Preferences, interface config

**Place search**:
Finding a place's coordinates and time zone from a name or postcode the user types. A forecast provider may offer one; the typed text goes to the primary provider, and without one the user types coordinates.
_Avoid_: Geocoding, location lookup, address search

**Charger phases**:
Whether the charger charges on one phase or three. Each amp delivers three times the power on three phases, and so does the minimum current.
_Avoid_: Single/three-phase connection (the home connection can have three phases while the charger uses one)

## Forecasts

**Forecast provider**:
A service the app asks for the expected solar power of the user's system over the next days. The app hands it a description of the system and gets back power per time step.
_Avoid_: Weather source, weather service, API

**Fallback provider**:
A second forecast provider, chosen in Settings, asked only when the first has no forecast that is still usable.
_Avoid_: Backup provider, secondary source

**Forecast freshness**:
How recent the forecast behind a plan is: **fresh** when it was just fetched from the provider, **cached** when it was fetched earlier and it is too soon to ask again, **stale** when a refresh was due but failed and the older forecast is still young enough to use. A forecast too old to use is not stale: there is then no forecast.
_Avoid_: Age, outdated, expired

**Cool-down**:
The time after a forecast provider said it was asked too often, until which the app does not ask it again. The dashboard shows the provider as busy until then.
_Avoid_: Rate limit (the provider's rule, not the wait), back-off, cooldown period

**API key**:
The code a forecast provider gives for a paid plan, which the app sends with each request to that provider. Kept in the system keychain, or in a file only the owner can read when there is no keychain; never in the settings files.
_Avoid_: Secret, token, password, key (on its own)

## Running on a computer

**Start at login**:
The app launching itself, hidden, when the user signs in to their computer. A choice in Settings, the same on every system.
_Avoid_: Autostart, run at boot, startup item

**Supported / Beta / Untested**:
How far a system or an integration is known to work. **Supported**: a person checks every release on it. **Beta**: someone has confirmed it on a real system or device, but releases are only tested automatically or by someone not always available. **Untested**: built and tested against documented or recorded behaviour, but nobody has confirmed it on a real one yet.
_Avoid_: Experimental, unofficial, verified

**Dashboard**:
The one screen that shows the plan: the next charge window, grid advice, today's solar curve, the battery and the coming days. It opens from the tray as a popup, or as a window in window mode.
_Avoid_: Popup (only one of the places it appears), plan window, main screen

**Engine**:
The part of the app that plans, fetches forecasts, talks to devices and owns all stored data, without any user interface. It runs inside the desktop app, and later can also run on its own on an always-on machine.
_Avoid_: Backend, server, daemon, core

**Client**:
Anything that shows the engine's plan and passes on the user's changes: today the desktop app's tray, popup and windows; later perhaps a web or phone app. A client never stores data itself.
_Avoid_: Frontend, UI (a client is more than its screens)

**Outlook**:
What the engine tells its clients after each planning round: either the plan for the coming days, with where its forecast came from, or why there is no plan (not set up, or no forecast).
_Avoid_: Status, result, plan (a plan is only one kind of outlook)

**Desktop app**:
What the user installs on their computer: the engine and the client together in one program.
_Avoid_: Tray app, the application (too vague)

## Integrations

**Integration**:
Built-in code that connects the app to one outside service or device, such as a forecast provider, a smart meter, an inverter or a car maker's service. Added to the app by pull request and released with it.
_Avoid_: Plugin, connector, driver, recipe (a recipe is a deferred idea, not an integration)

**Ability**:
One kind of thing a device can give or do, such as a battery level, solar production, house use and grid flow, or the charger's state. The planner asks for an ability without knowing which integration provides it; one integration can offer several.
_Avoid_: Capability, feature, sensor

**Official route**:
A way for an owner to read their own car that the car maker offers and allows, with an API key, a sign-in or a developer app of the owner's own. A car without one is read by entering its battery level by hand.
_Avoid_: Official API (a maker can have an API that owners may not use), unofficial route

**Local network (not encrypted)**:
An integration that reaches a device on the owner's own network over plain http, which only a private address allows. Settings labels it so.
_Avoid_: LAN, insecure, http mode

**Home Assistant**:
A home automation system some owners run. To this app it is one optional integration among others, never a requirement: the app works fully without it.
_Avoid_: HA, the hub
