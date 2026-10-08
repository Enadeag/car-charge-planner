# Settings window with the Solar and location tab

Status: resolved (clicking through it on the real desktop still to do)
Type: task
Blocked by: 01, 02

The first end-to-end slice: window, field specs, Save and Cancel, one tab (spec 2, 3.1, 3.2, 3.4, 4).

## Acceptance

- Tray menu: **Settings...** opens the window; **Open config file** is removed.
- `app/settings/fields.py` field specs (key, label, unit, hint, limits, assumed flag); `window.py` builds tabs from them. Percent fields display as percent and store fractions.
- Tab **Solar and location** with all fields from spec 3.2, assumed values labelled.
- Save: build `Config`, `validate()`, on success `write_config` + `apply_config` + close; on failure keep open and list every problem, write nothing.
- Cancel, close button and Escape ask "Discard changes?" only when something changed.
- **Reset tab** button on the window, working on whichever tab is shown.
- One instance: a second open raises the first.
- Untouched fields keep their exact stored value (spec 3.4).
- Offscreen tests per spec section 5 for what this ticket covers.

## Comments

Implemented in `app/settings/` (`fields.py` data, `tabs.py` FieldTab, `window.py` SettingsWindow) and wired into `app/tray.py` (`open_settings`, menu entry "Settings...", "Open config file" removed). 18 new tests (`tests/test_settings_window.py`, three in `tests/test_apply_config.py`); 173 in total.

Notes for tickets 04-06:
- A new tab is a `TabSpec` of `FieldSpec`s appended to `TABS` in `fields.py`; `kind="int"` gives a whole-number box. The Away rules tab (06) needs its own widget but must offer the same `load(cfg)` / `stored_value` style hooks, since `SettingsWindow.current_config`, `is_dirty` and `reset_tab` loop over `field_tabs`.
- Exactness: each box remembers a baseline; a box still showing the baseline value returns the exact stored value, so lat 50.123456789 or tilt 35 (int) survive an unrelated edit.
- Reset tab reloads the tab from `Config()` and makes the defaults the new baseline, so Save writes exact defaults.
- Input boxes show the system locale's decimal comma (4,80 kWp). Typing either separator works.
- Cancel, the close button and Escape all go through `reject()`, which asks only when the form differs from the stored config. `SettingsWindow.confirm_discard` is replaceable for tests.
- Hints are kept to one line at the 660 px minimum width; longer hints get clipped in a word-wrapped grid row.

Dark mode fix (found on a real desktop):
- Plain Qt widgets follow the system theme on their own. Only text coloured on purpose broke: hints used `palette(mid)`, which has a contrast of 1.1 against a dark window (1.7 even on light).
- `app/colours.py`: secondary text uses `palette(placeholder-text)`; the red (errors, stale battery) and orange (assumed tag, grid advice) switch between a light and a dark shade by the window's lightness, all at least 4.5:1 on typical backgrounds. Used in the popup and the Settings window. 8 tests in `tests/test_colours.py`.
- Limit: colours are chosen when text is set, so switching the system theme while a window is open needs the window reopened (the popup recolours on its next update, within a minute).
