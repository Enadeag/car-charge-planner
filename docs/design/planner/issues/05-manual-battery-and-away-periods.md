# Manual battery level and away periods

Status: resolved (clicking through the popup on the real desktop still to do)
Type: task
Blocked by: 04

Let the user enter state in the popup, persisted (spec 3, 4.5, 4.7, 5, 6).

## Acceptance

- Field to enter the current battery %; projected % at end of window shown.
- Day picker to add and remove manual **away periods** (all day or time range).
- Recurring an example weekday 06:00-19:00 **away period** from config.
- State stored as JSON under the XDG state dir; plan recomputes on every edit.
- Stale and missing battery states per spec 4.7.

## Comments

Implemented: `storage/state.py` (battery + away periods, atomic JSON at `~/.local/state/car-charge-planner/state.json`), `app/away_input.py`, popup inputs in `app/tray.py`, projection and stale-battery flag in `app/view_model.py`. 73 tests.

Behaviour:
- Battery: spinbox + "Set battery"; shows "Battery 45% -> 61% by 15:00" for the next window (counts earlier windows that day, capped at the target).
- Never entered: shown as assumed, flagged red, prompts for entry. One day or older: "entered N days ago. Please update." The plan still uses the last known value (spec 4.7).
- Away periods: pick one of the next 8 days, all day or a time range, Add; each entry has Remove. Entries that ended more than a day ago are pruned when adding. The recurring example rule stays in the config.
- Every edit saves and replans immediately.

Fixed along the way:
- Durations used wall-clock subtraction, which is wrong across DST for same-timezone datetimes; now real elapsed time (`elapsed_hours`).
- The active-window state lost its battery projection (caught by rendering, now tested).

Not verified (no screen access): clicking through the popup on Plasma/Wayland, in particular that the day and kind dropdowns do not close the popup. The popup is now a `Qt.Tool` window (not `Qt.Popup`) so text fields can take keyboard focus; it closes on Escape or when focus moves away.
