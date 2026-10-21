# Car Charge Planner

A tool that tells the owner each day when to charge their electric car so that as much of their own solar energy as possible is used.

Each term gives the word the Dutch texts use for it, after _Dutch_.

## Language

**Owner**:
The person who runs the app for their own home, car and solar panels, and the only one whose files and keys it keeps.
_Avoid_: User, customer, driver
_Dutch_: je (the texts speak to the owner)

**Feed-in**:
Solar energy that is produced but not used in the home or car, and flows back to the grid. The thing the owner wants to minimise.
_Avoid_: Return, export
_Dutch_: teruglevering

**Self-consumption**:
The share of produced solar energy that is used on-site (home or car) rather than fed in. The primary quantity the tool maximises.
_Avoid_: Own use, direct use
_Dutch_: eigen verbruik

**Charge window**:
A contiguous period of the day that the tool recommends for charging the car, chosen to capture the most solar surplus.
_Avoid_: Slot, charge time, window (on its own)
_Dutch_: laadvenster (never a bare venster)

**Charge current**:
The current in amperes, per phase, that the owner sets in the charger's app for a charge window. Always shown with the charge power it gives.
_Avoid_: Amps, charge speed
_Dutch_: laadstroom

**Charge power**:
The charging rate (kW) that a charge current gives on the charger's phases, chosen to match the expected solar surplus.
_Avoid_: Charge speed, charge rate
_Dutch_: laadvermogen

**Surplus**:
Solar production minus the house's own use at a given moment; what is left over for the car (or would be fed in).
_Avoid_: Excess, spare solar
_Dutch_: overschot

**Solar share**:
The part of a charge window's energy that comes from the owner's own solar panels, in percent.
_Avoid_: Sun share, sun (for solar power)
_Dutch_: zonnestroom (as in "92% zonnestroom")

**Baseload**:
The power the house uses without the car, as an average over the whole day: the kWh of a month divided by 730.
_Avoid_: House use, standby use
_Dutch_: basisverbruik

**Weekly away period**:
A span of time on one weekday, every week, when the car is not at home and so cannot be charged, such as Thursday 06:00–19:00.
_Avoid_: Recurring away time, absence, trip
_Dutch_: wekelijkse afwezigheid

**One-off away period**:
A span of time on specific days, entered on the dashboard, when the car is not at home: all day or between two times.
_Avoid_: Away day, absence, trip
_Dutch_: afwezig

**Trip**:
A drive from home and back, with a departure, a return and a distance, for which the battery must hold enough energy before the departure. The car is away from the departure to the return. Entered by hand on the dashboard or read from the calendar.
_Avoid_: Journey, ride, away period (which has no distance)
_Dutch_: rit

**Departure**:
The moment the car leaves on a trip; the energy for the trip must be in the battery by then.
_Avoid_: Deadline, start
_Dutch_: vertrek

**Trip surcharge**:
The share added to the average consumption for the energy of a trip, as long drives use more (15% unless the owner changes it).
_Avoid_: Margin, buffer
_Dutch_: ritopslag

**Other driving**:
The short drives that are not trips, such as shopping, as a distance per day; trips come on top of it.
_Avoid_: Daily distance, average distance
_Dutch_: overige rijafstand

**Charging hours**:
The hours in which the owner can start a charge (07:00–22:00 unless the owner changes it), both ends included; they may span midnight, such as 20:00–02:00. A grid charge starts within them and may run on after them.
_Avoid_: Awake hours, charge times
_Dutch_: laaduren

**Charge limit**:
The battery level the owner sets in the car or the charger for a grid charge, at which charging stops: the level the grid charge brings the battery to, in whole percent. Not a setting of the app.
_Avoid_: Target, maximum (the target charge level is the app's setting)
_Dutch_: laadlimiet

**Battery level**:
How full the car's battery is, in percent, as the car shows it.
_Avoid_: State of charge, SoC, charge
_Dutch_: accuniveau

**Assumed battery level**:
The battery level a plan starts from while the owner has not entered the level the car shows. The dashboard says it is assumed until the owner enters one. Not a typical value: it is not a setting, and the next entered level replaces it.
_Avoid_: Default level, estimated level, state of charge
_Dutch_: aangenomen accuniveau

**Minimum charge level**:
The battery level the car must never fall below (30% unless the owner changes it). The plan must keep the car above it, even if that means charging outside the best solar window.
_Avoid_: Floor, reserve
_Dutch_: minimaal laadniveau

**Target charge level**:
The highest battery level the plan charges the car to in everyday use (80% unless the owner changes it).
_Avoid_: Maximum, limit, goal
_Dutch_: doelniveau

**Planning margin**:
The share of the forecast surplus the plan counts on; lower is more cautious.
_Avoid_: Safety factor, buffer
_Dutch_: planningsmarge

**Import tolerance**:
The grid energy a charge window may draw before its charge power is lowered. That energy is allowed but not counted toward what the car needs.
_Avoid_: Grid allowance, import limit
_Dutch_: toegestane netstroom

**Announcement**:
A notification that tells the owner, the notification lead time before a charge window starts, what to set in the charger's app; also for the window of a grid charge. Each window is announced once, and not while the forecast could not be refreshed or is more than 6 hours old. A click on it opens the dashboard.
_Avoid_: Reminder, alert
_Dutch_: melding

**Grid advice**:
The advice to charge from the grid, because on solar power alone the battery would end a day below the minimum charge level: how much energy, and the charge window to take it in, on that day or the latest day before it with room. The charging it asks for is a **grid charge**, announced like any charge window.
_Avoid_: Grid warning, top-up, emergency charge
_Dutch_: advies: laden uit het net; a grid charge is laden uit het net

## Setting up

**Set-up**:
The guided first-run process in which the owner describes their system (location, panels, car, charger, and optionally house and away periods) so a plan can be made. Short for what the owner does once; the ongoing editing of values is Settings.
_Avoid_: Onboarding, configuration, wizard (the wizard is only the form set-up takes)
_Dutch_: instelhulp

**Not set up**:
The state before set-up was finished: there is no settings file yet, so no forecast is fetched and no plan is shown, only a way into set-up.
_Avoid_: Unconfigured, first run (first run is the moment, not the state)
_Dutch_: niet ingesteld

**Unusable config**:
A settings file that is there but cannot be used, because it cannot be read or holds a value that is not allowed. Unlike not set up, the owner fixes the file rather than going through set-up.
_Avoid_: Broken config, not set up (that is the state without a file)
_Dutch_: niet ingesteld, with what to fix

**Settings file**:
The file that holds the description of the owner's system, the planning preferences and the forecast providers; the app settings are in a file of their own.
_Avoid_: Config file, configuration, engine config
_Dutch_: instellingenbestand

**Panel array**:
Solar panels that share one tilt and one direction, described by their size (kWp), tilt, direction, losses and an optional name. A home has one or more (an east and a west roof, a garage); the forecast is the sum of all of them.
_Avoid_: Plane (the forecast services' word), string (an electrical term), roof
_Dutch_: panelengroep

**Typical value**:
A setting the owner did not give ("I don't know") and the app filled in with a common value. It is flagged as an estimate until the owner saves a value of their own.
_Avoid_: Default, assumed value, guess
_Dutch_: gangbare waarde

**Planning preferences**:
The owner's choices about how the plan is made: the minimum and target charge level, the planning margin, the import tolerance, the minimum window length, the notification lead time and the charging hours. Kept by the engine beside the description of the system; never a typical value.
_Avoid_: Planning settings, options
_Dutch_: planning

**App settings**:
The desktop app's own settings: whether it shows itself as a tray icon or a window, the units it shows, its language and whether it looks light, dark or as the system does. Kept by the desktop app, not the engine, so they stay on the computer even when the engine runs elsewhere.
_Avoid_: Preferences, interface config
_Dutch_: app-instellingen

**Place search**:
Finding a place's coordinates and time zone from a name or postcode the owner types. A forecast provider may offer one; the typed text goes to the primary provider, and without one the owner types coordinates.
_Avoid_: Geocoding, location lookup, address search
_Dutch_: zoeken (de zoekdienst)

**Charger phases**:
Whether the charger charges on one phase or three. Each amp delivers three times the power on three phases, and so does the minimum charge current.
_Avoid_: Single/three-phase connection (the home connection can have three phases while the charger uses one)
_Dutch_: fasen

## Forecasts

**Forecast provider**:
A service the app asks for the expected solar power of the owner's system over the next days. The app hands it a description of the system and gets back power per forecast step.
_Avoid_: Weather source, weather service, API
_Dutch_: voorspellingsdienst

**Primary provider**:
The forecast provider the owner chose to ask first; its place search is the one set-up and Settings use.
_Avoid_: Main provider, default provider
_Dutch_: voorspellingsdienst

**Fallback provider**:
A second forecast provider, chosen in Settings, asked only when the primary provider has no forecast that is still usable.
_Avoid_: Backup provider, secondary source
_Dutch_: reservedienst

**Forecast step**:
One span of time in a forecast, such as a quarter of an hour or an hour, with the expected solar power over it.
_Avoid_: Slot, interval, sample
_Dutch_: stap

**Forecast freshness**:
How recent the forecast behind a plan is: **fresh** when it was just fetched from the provider, **cached** when it was fetched earlier and it is too soon to ask again, **stale** when a refresh was due but failed and the older forecast is still young enough to use. A forecast too old to use is not stale: there is then no forecast.
_Avoid_: Age, outdated, expired
_Dutch_: bijgewerkt, niet vernieuwd

**Cool-down**:
The time after a forecast provider said it was asked too often, until which the app does not ask it again. The dashboard shows the provider as busy until then.
_Avoid_: Rate limit (the provider's rule, not the wait), back-off, cooldown period
_Dutch_: dienst weer beschikbaar om

**API key**:
The code a forecast provider gives for a paid plan, which the app sends with each request to that provider. Kept in the system keychain, or in a file only the owner can read when there is no keychain; never in the settings files.
_Avoid_: Secret, token, password, key (on its own)
_Dutch_: API-sleutel

**Secret setting**:
A provider's setting whose value is kept like an API key, in the keychain and never in the settings files, and is hidden while it is typed.
_Avoid_: Secret (on its own), password field
_Dutch_: API-sleutel

**Keychain**:
The system's safe store for passwords: Secret Service on Linux, Credential Manager on Windows. It can be locked, and then the app asks the owner to unlock it.
_Avoid_: Keyring (only the Secret Service's own word), wallet, vault
_Dutch_: sleutelbos

## Running on a computer

**Start at login**:
The app launching itself, hidden, when the owner signs in to their computer. A choice in Settings, the same on every system.
_Avoid_: Autostart, run at boot, startup item
_Dutch_: starten bij aanmelden

**Supported / Beta / Untested**:
How far a system or an integration is known to work. **Supported**: a person checks every release on it. **Beta**: someone has confirmed it on a real system or device, but releases are only tested automatically or by someone not always available. **Untested**: built and tested against documented or recorded behaviour, but nobody has confirmed it on a real one yet.
_Avoid_: Experimental, unofficial, verified
_Dutch_: ondersteund, bèta, niet getest

**Dashboard**:
The one screen that shows the plan: the next charge window, grid advice, today's solar curve, the battery and the coming days. It opens from the tray as a popup, or as a window in window mode.
_Avoid_: Popup (only one of the places it appears), plan window, main screen
_Dutch_: dashboard

**Engine**:
The part of the app that plans, fetches forecasts, talks to devices and owns all stored data, without any user interface. It runs inside the desktop app, and later can also run on its own on an always-on machine.
_Avoid_: Backend, server, daemon, core
_Dutch_: (not shown in the app)

**Client**:
Anything that shows the engine's plan and passes on the owner's changes: today the desktop app's tray, popup and windows; later perhaps a web or phone app. A client never stores data itself.
_Avoid_: Frontend, UI (a client is more than its screens)
_Dutch_: (not shown in the app)

**Outlook**:
What the engine tells its clients after each planning round: either the plan for the coming days, with where its forecast came from, or why there is no plan (not set up, an unusable config, or no forecast).
_Avoid_: Status, result, plan (a plan is only one kind of outlook)
_Dutch_: (not shown in the app)

**Desktop app**:
What the owner installs on their computer: the engine and the client together in one program.
_Avoid_: Tray app, the application (too vague)
_Dutch_: de app

## Integrations

**Integration**:
Built-in code that connects the app to one outside service or device, such as a forecast provider, a smart meter, an inverter or a car maker's service. Added to the app by pull request and released with it.
_Avoid_: Plugin, connector, driver, recipe (a recipe is a deferred idea, not an integration)
_Dutch_: koppeling

**Ability**:
One kind of thing a device can give or do, such as a battery level, solar production, house use and grid flow, or the charger's state. The planner asks for an ability without knowing which integration provides it; one integration can offer several.
_Avoid_: Capability, feature, sensor
_Dutch_: (not shown in the app)

**Official route**:
A way for an owner to read their own car that the car maker offers and allows, with an API key, a sign-in or a developer app of the owner's own. A car without one is read by entering its battery level by hand.
_Avoid_: Official API (a maker can have an API that owners may not use), unofficial route
_Dutch_: (not shown in the app)

**Local network (not encrypted)**:
An integration that reaches a device on the owner's own network over plain http, which only a private address allows. Settings labels it so.
_Avoid_: LAN, insecure, http mode
_Dutch_: lokaal netwerk (niet versleuteld)

**Home Assistant**:
A home automation system some owners run. To this app it is one optional integration among others, never a requirement: the app works fully without it.
_Avoid_: HA, the hub
_Dutch_: Home Assistant
