# Review against spec.md

Date: 2026-10-06. Method: read each spec section, then probed behaviour (replanning through a running window, bad configs, DST days, timing, startup lock), not only the code. 137 tests.

## Defects found and fixed

1. **Recommended power drifted while a window ran** (spec 4.3: one power per window). A 09:00-15:00 window at 9 A was replanned at 12:00 as 12:00-16:00 at 7 A. Fix: windows announced by a notification are pinned (stored in full in `state.json`); while one runs, the plan keeps its power and end, counts only its remaining energy, and plans no extra window right before or after it.
2. **Config values were not validated.** `volts = 0` crashed with a traceback (invisible under autostart); `margin = 0`, `min_amps > max_amps`, negative `kwp`, bad weekday were accepted silently. Fix: `validate()` with readable messages, all problems reported together; the app shows a dialog and exits instead of silently never appearing.
3. **DST arithmetic.** Adding hours to a zoned datetime moves the wall clock, so window ends were an hour off on the two clock-change days. Fix: `add_hours` / `elapsed_hours` work on the UTC timeline. Found by the 25-hour-day test.
4. **Startup lock** reported "already running" when `~/.cache` was missing. Fix: create the directory, tell the two failures apart.

## Deviations from the spec that remain

- **4.3 power choice.** The spec says "highest power with expected import within tolerance". The code picks the window and power that deliver the most solar energy within that tolerance, which gives lower power over a longer window. Judged better; spec text not updated.
- **4.1 cloud cover** is requested from Open-Meteo but not shown anywhere.
- **Away periods in the popup** are one day at a time (all day or a time range). A trip over several days needs one entry per day. The data type supports longer spans.
- **Extra over spec:** grid-charge windows (ticket 07) also trigger the 15-minute notification.

## Known limits (accepted for v1)

- Driving is an even daily average (85.7 km/day); a known long trip is not modelled.
- A grid-charge window cannot cross midnight.
- Config changes need an app restart.
- A forecast cache stamped in the future (clock changed) would be treated as fresh.
- No README; usage is in the tickets.

## Not verifiable here

- How the popup is placed on Wayland and clicking through dropdowns (no screen access; it has been used on a real desktop).
- The real minimum charge current of a car, consumption, losses; real panel size and tilt (tickets 08, 09).
