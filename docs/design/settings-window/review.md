# Review against spec.md (Settings window)

Date: 2026-10-06. Method: each spec section checked against behaviour, with the window driven in offscreen tests and rendered in light and simulated dark themes; a real `config.toml` saved through the window. Test suite: 202 tests.

## Spec compliance

| Spec | Result |
| --- | --- |
| 2 Decisions: separate window from the tray menu, "Open config file" removed, Save/Cancel, clean TOML, five tabs, rows for away rules, Reset tab per tab | Met |
| 3.1 One instance, not modal, centred, Save/Cancel/error area visible on every tab, validation lists every problem, dirty check on Cancel, close button and Escape, Reset tab | Met |
| 3.2 Every field, unit, limit and hint; percentages shown as percent and stored as fractions | Met. Hints reworded generically on request; the spec table follows the code |
| 3.3 Apply live: time zone swap, replan, refetch only when the forecast key changes, a late fetch from the old config is discarded, announced windows unaffected | Met |
| 3.4 Untouched fields keep their exact value (checked for numbers, ints, the time zone and away rules) | Met |
| 3.5 File: header, values only, atomic write, round trip, `ensure_config` through the writer | Met. An empty away list is written as `recurring = []` |
| 4 Architecture | Met, with one change: the spec said one tab class per group. Four groups share one data-driven `FieldTab`; only Away rules has its own class |
| 5 Testing | Every listed case has a test, plus clipping, colour contrast and Enter-key behaviour |
| 6 Out of scope | Respected: no file watching, battery and away days stay in the popup, no live preview |

A real config saved unchanged gives an identical config (only the old comments go), and opening the window on it shows no changes.

## Defects found while building, all fixed

1. **Hints unreadable in a dark theme** (reported on a real desktop). `palette(mid)` had a contrast of 1.1. Muted text now uses `palette(placeholder-text)`; red and orange pick a light or dark shade from the window's lightness. `app/colours.py`, tests in `tests/test_colours.py`.
2. **Hints clipped** when long. Fixed by wording and width; `test_no_hint_text_is_clipped_on_any_tab` guards it and was shown to fail on a squashed window.
3. **Error text lost its colour** after an edit; caught by a new test. An earlier "simplify show_errors" edit had silently not applied.
4. **Profile-specific help text** (a car brand, a city, personal consumption figures) replaced with generic wording.

## Behaviour to know about (not in the spec)

- **Enter in a field does what the save button currently does** (save, or save and close). Reset tab and Cancel are not triggered by Enter.
- Number boxes use the system locale: a decimal comma on a Dutch system. Both separators can be typed.
- Reset tab restores the built-in defaults, which are the project's neutral defaults.
- The "Assumed" and "Estimate" tags describe the shipped defaults, not what a user has verified; they never clear.
- A rule that ends before it starts is reported, but the message does not say which row.
- Colours are chosen when text is set, so a system theme switch needs the Settings window reopened.
- Hand edits to `config.toml` while the app runs are not picked up and are overwritten by the next Save.
- The notification text still names a specific charger app ("in your charger's app"); it is not a settings hint, so it was left as is.

## Not verifiable here

- Window placement on Wayland and clicking through every control on the real desktop. The window was opened on a real desktop and the dark-mode problem was reported, and is fixed; the other tabs have been rendered but not clicked through by the reviewer.

## Change after the review: split Save button

Requested: Save without closing, and Save and close, as one button with a dropdown, defaulting to Save.
- One `QToolButton` with a menu (`MenuButtonPopup`): main part runs the current mode, the arrow lists "Save" and "Save and close" (current one marked). Picking an entry only switches the mode and the label; the window starts in Save mode every time and the choice is not remembered.
- After a save with the window open, the saved values become the new baseline: nothing counts as changed until the next edit, and Cancel then closes without asking.
- Reset tab and Cancel are no longer the Enter key's target (`autoDefault` off); Enter runs the save button's action.
- Away rule rows are rebuilt after a save (same values).
- 14 new tests in `tests/test_settings_window.py`. 

## Change after the review: Close / Cancel label

Requested: after a save with the window open there is nothing to cancel, so the button should say so.
- The button reads **Close** while nothing is unsaved and **Cancel** while any field differs from the config as last saved. Each tab emits `changed` on every edit (number boxes, the time zone box, away rule rows added, removed or edited); Reset tab and a save refresh it directly.
- Its width is fixed to the wider label, so it does not shift when the text flips.
- 9 new tests; with the signal disconnected, 5 of them fail.

## Change after the review: one place for each thing

It was noticed that the popup's "Car away" and the Settings' "Away rules" overlapped. Decision (maintainer): keep the daily inputs in the popup, but never edit configuration there.
- Settings holds configuration, including the weekly away rules. The popup holds daily inputs: battery level and one-off away days (both live in the state file, not in `config.toml`).
- The popup now also lists the weekly rules, read-only and marked "(weekly rule)", with an **Edit weekly rules in Settings...** button that hides the popup and opens Settings on the Away rules tab. A rule saved in Settings shows up in the popup at once.
- Found on the way: removed popup rows were only scheduled for deletion and stayed in the widget tree until the event loop ran; they are now hidden and detached immediately.
- 6 new tests in `tests/test_popup_offscreen.py`.
