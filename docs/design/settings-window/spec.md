# Settings window: Spec

Status: draft, agreed in grilling session 2026-10-06
Builds on: `docs/design/planner/spec.md` (section 7 config defaults, section 6 architecture). Vocabulary: `CONTEXT.md`.

## 1. Purpose

Replace editing `config.toml` by hand with a **Settings** window in the tray app: fields and buttons, grouped over tabs. The TOML file stays the storage, so the planner, CLI and tests keep reading it as before.

Today there is no settings UI: the tray menu has "Open config file", and every change needs an app restart.

## 2. Decisions

| Topic | Decision |
| --- | --- |
| Where | A separate window, opened from the tray menu entry **Settings...** |
| Old entry | "Open config file" is removed. The file stays plain TOML, readable by hand, but the app reads it only at startup and the next Save overwrites hand edits. |
| Saving | **Cancel** and one split **Save** button. Save validates all values together, writes the file and applies the change immediately (no restart). Its main part runs the current mode: **Save** (window stays open, the default) or **Save and close**. The arrow opens a menu to pick the mode, which only switches the mode. The window opens in Save mode every time. |
| File format | A clean TOML file: values only, a short header saying the Settings window manages it. Explanations move into per-field hints. |
| Tabs | Solar and location, House and car, Charger, Planning and notifications, Away rules |
| Away rules | A list of rows (weekday, from, to, Remove) and an "Add rule" button. One row per weekday. |
| Reset | A **Reset tab** button per tab. It only fills the tab's fields with built-in defaults; nothing is saved until Save. |

## 3. Behaviour

### 3.1 Window

- One instance: opening Settings while it is open raises the existing window.
- Not modal; the tray and popup keep working. Opens centred on the screen.
- Tabs in a tab bar; Save, Cancel and an error area below the tabs, visible on every tab.
- **Save** (main part of the split button; the mode decides whether it then closes): build a `Config` from the fields, run `validate()`. On success write the file, apply it (3.3) and make what was saved the baseline for "changed". In **Save and close** mode the window then closes. On failure write nothing, keep the window open and list **every** problem in the error area (the messages come from `validate()`, the single source of truth).
- The Enter key does what the save button currently does. Reset tab and Cancel are not triggered by Enter.
- The button beside Save reads **Close** while every field matches the config as last saved, and **Cancel** while any field differs. It updates on every edit, on Reset tab and after a save.
- **Cancel/Close**, the window's close button or Escape: if any field differs from the config as last saved, ask "Discard changes?" first; otherwise close.
- **Reset tab**: sets the current tab's fields to the defaults of `Config()`. Marks the window as changed.

### 3.2 Fields

Each field has a label, a unit, a hint line, and limits that match `validate()`. Hints describe the setting in general terms: no brands, places or personal figures. The "Assumed" and "Estimate" tags mark values that ship as guesses; the code in `app/settings/fields.py` is the source of truth for the wording. Percentages are shown as percent (14 %) and stored as fractions (0.14).

**Solar and location**

| Field | Key | Unit | Limits | Hint |
| --- | --- | --- | --- | --- |
| Panel size | `panels.kwp` | kWp | above 0 | **Assumed.** Total peak power of all panels together, as on the installer's datasheet or in the inverter app. |
| Tilt | `panels.tilt_deg` | degrees | 0-90 | **Assumed.** Angle of the panels from horizontal: 0 is flat, 90 is vertical. |
| Direction | `panels.azimuth_deg` | degrees | -180 to 180 | Compass direction the panels face: 0 = south, -90 = east, 90 = west, 180 = north. South-east is -45. |
| System losses | `panels.system_losses` | % | 0 to under 100 | Combined losses from the inverter, cables, heat and dirt. Typically 10-15%. |
| Latitude | `latitude` | degrees | -90 to 90 | Decimal degrees of the panels' location. Positive is north. |
| Longitude | `longitude` | degrees | -180 to 180 | Decimal degrees of the panels' location. Positive is east. |
| Time zone | `timezone` | | a known zone | The time zone of the location. Dropdown of known zones, searchable by typing. |

**House and car**

| Field | Key | Unit | Limits | Hint |
| --- | --- | --- | --- | --- |
| House baseload | `house.baseload_kw` | kW | 0 or more | Average power the household uses without the car. Monthly kWh divided by 730 gives a rough figure. |
| Usable battery | `car.usable_battery_kwh` | kWh | above 0 | **Estimate.** The net (usable) battery capacity. Manufacturers often list the gross capacity, which is higher. |
| Consumption | `car.consumption_kwh_per_100km` | kWh/100 km | above 0 | **Estimate.** |
| Driving per day | `car.km_per_day` | km | 0 or more | Average distance per day. Weekly distance divided by 7 gives a rough figure. |
| Minimum charge level | `car.minimum_pct` | % | 0 up to below target | The battery should never drop below this. |
| Target charge level | `car.target_pct` | % | above minimum, up to 100 | Everyday maximum. |

**Charger**

| Field | Key | Unit | Limits | Hint |
| --- | --- | --- | --- | --- |
| Minimum current | `charger.min_amps` | A | 1 up to max | The lowest current the charger and car both accept, often 6 A. |
| Maximum current | `charger.max_amps` | A | min or more | The highest current the charger and the home connection allow. |
| Voltage | `charger.volts` | V | above 0 | Mains voltage at the charger, typically 230 V single-phase. |
| Charging efficiency | `charger.efficiency` | % | above 0, up to 100 | **Estimate.** Share of grid energy that ends up in the battery. |

**Planning and notifications**

| Field | Key | Unit | Limits | Hint |
| --- | --- | --- | --- | --- |
| Planning margin | `planning.margin` | % | above 0, up to 100 | Plan on this share of the forecast surplus. Lower is more cautious. |
| Import tolerance | `planning.import_tolerance_kwh` | kWh | 0 or more | Grid energy a window may draw before its power is lowered. |
| Minimum window length | `planning.min_window_minutes` | minutes | above 0 | Shorter windows are dropped. |
| Notification lead time | `notify.lead_minutes` | minutes | 0 or more | |

**Away rules**: rows of weekday (Monday to Sunday), from and to time. Each rule must end after it starts on the same day (no overnight rules). The list may be empty.

### 3.3 Applying a saved config

- The app swaps in the new `Config`, updates its time zone and the popup's, replans immediately.
- Changing location, tilt, direction or time zone changes the forecast cache key, so the forecast is fetched again. Other changes reuse the cached forecast.
- A refresh that started before the save and finishes after it must not overwrite the new settings' result: if the config changed meanwhile, fetch again.
- Windows already announced (pinned) are unaffected.

### 3.4 Exactness

A field the user did not touch keeps its stored value exactly. Example: `km_per_day` is 85.714285714... and the field shows 85.7; saving without editing must not turn it into 85.7. Save writes a field's displayed value only when it differs from the stored value rounded to the field's decimals.

### 3.5 The file

`planner/config_io.py` writes `config.toml`:

- A header comment: managed by the Settings window; edit by hand only while the app is closed; comments are not kept.
- Same keys and tables `load_config` already reads, including `[away] recurring = [...]`.
- Written atomically (temp file, then rename). A failed write leaves the old file in place and shows the error.
- `load_config(write(cfg)) == cfg` for every valid `Config`.
- `ensure_config` creates a first file from `Config()` through the same writer; the long commented default template goes away.

## 4. Architecture

```
planner/config_io.py        dump_config / write_config (new)
app/settings/
  fields.py                 field specs: key, label, unit, hint, limits, assumed flag
  window.py                 SettingsWindow: tabs, Save, Cancel, error area, dirty check
  tabs.py                   one tab class per group, plus the away-rules tab
app/tray.py                 menu entry, apply_config(), stale-refresh guard
```

- Field specs are data, not repeated widget code, so limits and hints sit in one table and the window builds each tab from them.
- The window never writes the file itself: it produces a `Config`; `validate()` and `write_config()` do the rest. That keeps the logic testable without Qt.
- Qt tests run offscreen, as for the popup.

## 5. Testing

- Writer: round trip for defaults, for a changed config and for several away rules; atomic write leaves the old file on failure; header present.
- Window (offscreen): each tab shows the stored values; editing and Save writes and applies; invalid values list every problem and write nothing; dirty close asks, clean close does not; Reset tab restores defaults without saving; untouched fields keep exact values; one instance only.
- Apply: location change triggers a fetch, a margin change does not; a refresh started before the save does not overwrite the new result.

## 6. Out of scope

- Watching the file for hand edits while the app runs.
- Settings stored anywhere other than `config.toml`.
- Battery level and one-off away days: they stay in the popup (daily inputs, not settings). The popup lists the weekly away rules read-only and has a button that opens Settings on the Away rules tab; the rules themselves are edited only in Settings.
- A live preview of how a setting would change the plan.

## 7. Open items

- Popup-style window placement on Wayland cannot be verified without screen access; the Settings window is centred by the compositor.
