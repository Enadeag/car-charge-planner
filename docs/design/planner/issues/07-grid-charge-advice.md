# Grid-charge advice

Status: resolved
Type: task
Blocked by: 02, 05

Warn when the battery would drop below the **minimum charge level** (spec 4.6).

## Acceptance

- Projection detects a drop below 30% before the next day with a usable solar window.
- Popup shows distinct advice: least-bad window (smallest expected grid import) at the lowest power that gets the car to safety in time.
- Otherwise a day with no window shows only "no good window today".
- Tests: dip before next sunny day, comfortable battery on overcast day.

## Comments

Implemented in `planner/advice.py` (`grid_advice`), wired into `app/view_model.py`, the tray (orange icon, advice block) and the CLI. 95 tests in the suite, mainly `tests/test_advice.py` and `tests/test_view_model.py`.

Rules as built:
- Dip = the first day whose projected end-of-day level (after planned solar charging and driving) is more than 1 point under the minimum charge level. A shortfall under 1 point is ignored as forecast noise (`MIN_DEFICIT_PCT`).
- Energy needed = the shortfall back up to the minimum, grid side (efficiency applied). Charging only up to the minimum, not the 80% target, is deliberate: "as little as possible".
- Placement: 15-minute slots on the dip day, at home, after now, not overlapping planned solar windows. Picks the placement with least expected grid draw; ties go to the lowest power, then the earliest start. If the dip day has no room it tries the previous days, back to today. If nothing fits: "no free time at home".
- View: if no solar window is coming, the tray turns orange ("Grid charge needed"). If a solar window exists, the advice still shows as its own block.

Also changed: the projection now takes only the remaining share of today's driving (otherwise every evening looked like a dip), and `DayPlan.driving_pct` keeps the true shortfall because end-of-day is clamped at 0.

Limitations:
- A window cannot cross midnight, so an overnight charge into a morning departure is split to the dip day only (e.g. Thu 00:00-05:45 at 13 A rather than Wed evening at 6 A).
- Driving is modelled as an even daily average; a known long trip is not known to the planner.
