# First-run set-up screen, and internationalisation: Spec

Status: agreed in a grilling session, 2026-10-06. Part of the open-source plan (the open-source plan, prerequisite 1). Vocabulary: `CONTEXT.md` (**set-up**, **not set up**, **typical value**, **charger phases**).

## 1. Goal

A new user opens the app and is walked through describing their system, in plain language, in a few minutes, and ends with a plan that fits their home. Everything is translatable from the first line, with English as the only language provided at first.

## 2. Decisions

| Topic | Decision |
| --- | --- |
| Shape | A **step-by-step wizard** in its own window (like Settings), opened by the controller; the same for the tray and window shells. |
| Flow | Welcome -> Location -> Solar panels -> Car and charger -> **What next?** -> Finish. |
| Location | **Place search** (the typed text goes to the weather provider; the page says so) with **manual coordinates** as a fallback. Fills in the time zone. No network-address detection. |
| Solar panels | Helpers: panels x watts -> kWp, eight compass buttons, tilt presets, "I don't know", a sentence on where to find each value. One roof direction only; the page says what to enter for east-west roofs. |
| Car and charger | Plain-language guidance, typical ranges, "weekly distance" -> daily distance, "I don't know". No built-in list of car models. **Charger phases** (one or three) is asked. |
| What next? | A **hub** of cards: **House electricity** and **When is the car away?**. Each card says what it does and what happens if skipped; a tick marks it done; **Finish** is always available. The away card carries an extra line for commuters. |
| Finish | A recap with "Change" links per group, and one **test fetch** ("Forecast received for <place>" or a failure notice that does not block Finish). |
| When | Opens by itself on the first start, **never from a hidden autostart** (a notification at most). Closing it unfinished leaves **not set up**, with a "Set up" button in the popup and the window. Settings gets **"Run set-up again"**, which opens it pre-filled. |
| Saving | **Only at Finish**, all at once. Closing early asks "Leave set-up?" and discards. |
| Units | A units choice on Welcome and in Settings: **distance and consumption only** (km and kWh/100 km, or miles and mi/kWh). Stored metric. The clock stays 24-hour. |
| Charger phases | A real `charger.phases` (1 or 3) in the config, the planner and Settings. Existing configs mean one phase. |
| Typical values | The config remembers which settings are typical values. Settings shows the "Assumed"/"Estimate" tag only on those and clears it when the user saves their own value. The plan page shows "Based on typical values for: ...". |
| i18n scope | **The whole app**: wizard, Settings, popup and window, notifications, errors, command-line output. English is the only catalogue provided. |
| i18n system | **gettext** (`_`, `ngettext`, `pgettext`), extracted with Babel into a committed `.pot`; Qt's own button texts via the translation files in the PySide6 wheel. |
| Not in scope | Translating the README and design documents; a language picker until a second language exists; a clock-format setting; a built-in car model list; automatic location detection. |

## 3. The wizard, page by page

**Welcome.** What the app does, in two sentences; what leaves the machine (the place search text, then the coordinates and panel angles for forecasts, to the weather provider; nothing else); the **units** choice (pre-selected from the system region: imperial for US, UK, Liberia and Myanmar, else metric). Buttons: Next.

**Location.** A search box, results as "Name, region, country", pick one. Below: "Enter coordinates instead" (latitude, longitude, with a hint on reading them from a map app). The time zone is filled from the result or the system and is editable behind an "Advanced" fold. Next is disabled until a location is chosen.

**Solar panels.** Size: either kWp or number of panels x watts each (result shown as kWp). Direction: eight compass buttons plus a degrees field. Tilt: presets (flat roof about 10 degrees, typical pitched roof about 35, steep about 45) and an exact angle. "I don't know": picks typical values and records them as such. A short note for east-west roofs. Where-to-find lines: installer datasheet, inverter app, satellite view.

**Car and charger.**
- Car: usable battery (kWh, with the gross-versus-usable note and a typical range of about 40 to 100), consumption (typical 14 to 22 kWh/100 km, or mi/kWh when imperial), distance driven per week (divided by seven for the planner), minimum and target charge level (pre-filled 30 and 80, each with a one-line explanation).
- Charger: **phases** (one or three, with how to tell: charger label, fuse box, manual; and a note that some chargers can be switched to single-phase charging, which suits solar better), maximum current (typical 16 or 32 A), minimum current (6 A), voltage under "Advanced" (230 V).
- "I don't know" on either half records typical values.

**What next?** Cards:
- *House electricity*: "Tells the plan how much power the house itself uses, so it knows what is left for the car. If skipped: a typical value is used." Opens a page: yearly use from the bill (divided by the hours in a year, minus the car's if it is already included) or "I don't know".
- *When is the car away?*: "Stops the plan recommending charging while the car is out, for example at work. If skipped: the car is treated as home all day. **If you drive to work, set this.**" Opens a page: weekday boxes with from and to times, or "the car is usually home".
- Finish is always available; each completed card shows a tick.

**Finish.** A recap grouped as place, panels, car, charger, house, away, with a "Change" link per group, the typical values listed as such, and the connection check. Buttons: Back, Finish.

## 4. Typical values (proposals, to be confirmed in the tickets)

Panels 5.0 kWp, tilt 35 degrees, direction south (0), losses 14%. House baseload 0.5 kW. Battery 60 kWh usable, consumption 18 kWh/100 km, driving 40 km a day. Charger one phase, 16 A maximum, 6 A minimum, 230 V, efficiency 90%. Charge levels 30 and 80 (preferences, not typical values).

## 5. Model and behaviour changes

- `charger.phases` in 1, 3; power is `phases x amps x volts / 1000` in every place that computes it (`planner/windows.py`, `planner/advice.py`, `planner/surplus.py`); the minimum-current threshold follows; texts say "per phase" for three phases (notification, popup, Settings hint).
- `[interface] units = "metric" | "imperial"`; a Qt-free conversion module; consumption converts by reciprocal (mi/kWh = 62.1371 / (kWh/100 km)), so the Settings field machinery needs a transform, not just a scale. Stored values stay metric and exact.
- `[setup] typical = ["panels.kwp", ...]`: the keys still typical. The wizard adds on "I don't know"; Settings removes a key when its field is edited and saved.
- "Not set up" is defined by the open-source ticket 01 (location unset, no fetch); this work builds the way out of it.
- An optional **place search** capability (Open-Meteo geocoding to start with), kept behind a small interface so the pluggable weather providers (open-source ticket 03) can supply their own.

## 6. Internationalisation design

- `app/i18n.py`: `_`, `ngettext`, `pgettext`, `N_` (a lazy marker that returns the text untranslated for extraction), loaded for the system language (`LANGUAGE`, `LC_ALL`, `LC_MESSAGES`, `LANG`, then Qt's locale), catalogues under the package's `locale/` directory, domain `car-charge-planner`.
- **Lazy strings.** Texts defined at import time (the field definitions in `app/settings/fields.py`, the card texts, tab titles) are marked with `N_()` and translated when displayed, so the language is applied after startup.
- **Sentences are templates.** The ~31 fragment-built sentences become one translatable template each (`_("Until {end}")`, with named placeholders), never concatenated pieces. Plurals use `ngettext`.
- **Day and month names** are translatable strings with context (`pgettext("weekday, short", "Mon")`), which keeps the view model free of Qt and of extra runtime dependencies; translators supply them.
- **Numbers in text** (for example "1.6 kW") use a small formatter whose decimal separator is itself a translatable string; Qt's boxes already follow the system locale.
- **Qt's own texts** (Discard, Cancel in standard dialogs): load `qtbase_<language>.qm` from the PySide6 translations directory.
- **Tooling:** Babel as a development dependency (`pybabel extract`, `update`, `compile`); a committed `.pot`; `.mo` files compiled in the build and by a dev command.
- **Checks:** a pseudo-language catalogue generated from the `.pot` (every string wrapped like `[!! text !!]`, with extra length) used to render every page and assert that visible texts are translated and nothing is clipped; a test that the `.pot` is up to date with the code.
- The language follows the system; a picker appears in Settings only once a second catalogue exists.
- `CONTRIBUTING.md` explains how to add a language.

## 7. Order of work

```
i18n:  I1 foundation -> I2 convert every string -> I3 pseudo-language checks -> I4 translator docs
model: S1 phases, S2 units, S3 typical values, S4 place search      (after I1)
pages: S5 wizard framework + Welcome + Finish
       S6 Location, S7 Solar, S8 Car and charger, S9 hub + House + Away
S10 first-run integration (needs open-source ticket 01)
S11 docs, screenshots, review against this spec
```

## 8. Testing

- Page logic is Qt-free where possible (helpers: panels x watts, compass to degrees, yearly use to baseload, weekly to daily distance, unit conversions, recap building) and tested directly.
- Each page rendered offscreen in light and dark, and with the pseudo-language, checked for clipping.
- The wizard end to end offscreen with a fake place-search client and a fake forecast: first start opens it, Finish writes the config once, closing early writes nothing, "Run set-up again" is pre-filled.
- A whole-app subprocess test on a tray-less desktop for the first-run flow (no network).
- Three-phase power maths across a matrix of currents and surpluses.

## 9. Risks and open items

- The typical values in section 4 are proposals.
- Place search ties to the pluggable weather work: the interface must allow a provider without a geocoder (manual coordinates always work).
- East-west roofs are described only approximately; a two-array model is a possible later feature.
- Number and name formatting by hand covers the common cases; a language with different digit systems would need more.
- i18n converts about 150 existing strings and adds about 150 more; the fragment sentences are the careful part.
