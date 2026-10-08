# Review against the spec

Status: written 2026-10-08, for tickets I1-I4 and S1-S10 (S11 is this review). Spec: `docs/design/setup-screen/spec.md`.

## 1. Compliance

| Spec | Verdict | Evidence |
| --- | --- | --- |
| 2 Shape: a step-by-step wizard in its own window, opened by the controller, the same on both shells | Met | `app/setup/`, `Controller.open_setup()`; both shells have the Set up button; one wizard at a time. |
| 2 Flow: Welcome, Location, Solar panels, Car and charger, What next?, Finish | Met | Six steps; House and Away open from the hub and return to it. |
| 2 Location: place search with a manual fallback, fills the time zone, no address detection | Met | Search through the **primary provider** (so a provider without one asks for coordinates and says why); coordinates behind an arrow; the zone is the result's, the computer's for typed coordinates, editable under Advanced. |
| 2 Solar: panels x watts, compass buttons, tilt presets, "I don't know", where to find each, east-west note | Met | `solar.py`; pure helpers tested; a pressed button is highlighted in every theme (found by looking at the dark render). |
| 2 Car and charger: guidance, typical ranges, weekly distance, "I don't know", phases | Met | `car.py`; validation in Settings' own words. |
| 2 What next?: a hub of cards, ticks, Finish always available, the commuter line | Met | `hub.py`; a card is ticked when an **answer** was given, not when it was opened. |
| 2 Finish: a recap with Change links, one test fetch that never blocks Finish | Met | `finish.py`; the check runs on entering the page (the Welcome text says a forecast is requested). |
| 2 When: opens by itself on the first start, never from a hidden autostart (a notification at most); Settings has Run set-up again | Met | `Controller.offer_setup`; the notification is sent once ever; two whole-app subprocess runs. |
| 2 Saving only at Finish; closing early asks and discards | Met, one nuance | Asks unless nothing was done and the user is still on the first page. |
| 2 Units: distance and consumption only, stored metric, pre-selected from the region | Met | `app/units.py`; consumption by the reciprocal; Settings and the wizard follow at once; no drift (tested). |
| 2 Charger phases: a real setting | Met | One function for the power, used in three places; texts say "per phase". |
| 2 Typical values: remembered, tagged only where they are, cleared when the user gives their own | Met | `[setup] typical`; one "Typical value" tag; the plan page line. |
| 2 i18n: the whole app, gettext, English only | Met | I1-I4; 464 messages; a pseudo-language test renders every page and window, and the template test keeps the committed template current. |
| 3 Pages as described | Met | See above; every page rendered in light and dark and looked at. |
| 4 Typical values | **Settled** | The table is in `app/setup/typical.py`, and since S11 it **is the built-in defaults** (below). |
| 5 Model and behaviour changes | Met | |
| 6 Internationalisation design | Met | Tabs have keys, not titles; day and month names have contexts; sentences are templates; `number()` has a translatable separator. |
| 8 Testing: Qt-free page logic, offscreen pages in both themes and the pseudo-language, the wizard end to end, a whole-app run on a tray-less desktop, the three-phase matrix | Met | See section 4. |
| Not in scope: a language picker until a second language exists | **Done anyway** | I4 added it (hidden while only English is installed); it was cheap and the ticket asked for it. |

## 2. Decisions made on the way (to check)

1. **The built-in defaults are now the typical values** (5.0 kWp facing south, 0.5 kW house, a 60 kWh battery, 40 km a day, a 16 A charger). Until now they were different (4.8 kWp facing south-east, 0.6 kW, 70 kWh, 32 A), so Settings' Reset tab and the wizard disagreed. One test pins the two together. The tests use an explicit profile (`example_config()`) and were not affected; three expectations that read the old numbers were changed to read `Config()`.
2. **An older config file has no `[setup]` section and is taken to hold the user's own values**, so nothing in it is tagged. Only a new install starts with every value typical.
3. The wizard's **connection check runs by itself** on the last page. The first page says what is requested.
4. **Leaving the wizard** with nothing done asks nothing.
5. **Finish needs a real place** (not 0, 0); everything else has a value from the start.
6. The **Open-Meteo "what is sent" text now includes the time zone**, which the live audit showed is part of the request.

## 3. Found while building, and fixed

- The template extraction read only `app/`, so the validation messages in `planner/` were never translatable (found in P3).
- A spin box clamps its value when its range is set and announces it: opening the Car page recorded a consumption of 5 as the user's own answer (S8, found by a test written for the purpose).
- Tests that opened Settings or the wizard left windows, controllers and worker threads alive, which crashed an unrelated test about one run in three. The test fixture now closes windows and waits for workers; the suite is stable (many consecutive runs) and about 20% faster (S6).
- The "Terms and limits" link was unreadable on a dark window (P11), and the pressed compass and tilt buttons were invisible on a dark window (S11).
- The recap marked "Current 6 to 20 A" as typical when only one of the two was; each current has its own line now.

## 4. Verification

- **Tests**: 1,222, run offscreen; no network (a guard fails any test that tries); the wizard and its pages are covered at three levels (Qt-free logic, page behaviour, two whole-app subprocess runs).
- **Looked at**: every wizard page, light and dark, as images (the set committed in `docs/images`, regenerated by `docs/screenshots.py`).
- **A real walk on 2026-10-08**, the real wizard against the real services, for a neutral place (central Amsterdam): typing "Amsterdam" gave eight places; picking the first set the place and time zone; the Finish page's check said "Forecast received for Amsterdam, North Holland, The Netherlands: 4 days."; Finish wrote the config. **Exactly two requests left the machine**, and nothing else: the place search (`name=Amsterdam`, the count, the language) and the forecast (`latitude`, `longitude`, `tilt`, `azimuth`, `timezone`, the number of days, the unit of time). The panel size, the car, the charger, the house, the away times and the battery were not sent. This is what the README's Data and privacy section and the Welcome page say (now word for word, with a test).

## 5. Could not be verified

- **A real first run on a clean desktop account** (no existing config, state or cache, a real tray or panel): the whole-app runs used a private folder set on a desktop without a tray.
- **The tray shell's first run as a program**: only in-process.
- **Other locales**: only English exists; the pseudo-language proves strings are translated and fit, not that another language reads well; a right-to-left language was never tried. The region-to-units choice was tested with names, not with a real `LC_MEASUREMENT` setup.
- **Keyboard use and screen readers**: Tab order, mnemonics and accessible names were not examined.
- **Real desktop themes and high-DPI screens**: renders use Qt's default style with a light and a dark palette.
- **Wayland window placement** of the wizard and the pages' size on a small screen.
- **A paid forecast plan** (no key) and a real rate limit.

## 6. Still open

- Windows and macOS (`docs/design/multi-platform/`): "Start at login" is in the glossary and the plan, not in the code yet.
