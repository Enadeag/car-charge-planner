# Car and charger page

Status: done (not committed yet)
Type: feature
Blocked by: S1, S2, S3, S5

## Acceptance

- Car: usable battery, consumption, distance per week (converted to per day), minimum and target charge level with one-line explanations; units follow the choice; gross-versus-usable note and typical ranges.
- Charger: phases (with how to tell and the single-phase-switch note), maximum current, minimum current (6 A), voltage under "Advanced".
- "I don't know" for each half records typical values.
- Validation shows the same messages as Settings (minimum below target, minimum current not above maximum).
- Helpers for weekly to daily distance and unit conversion unit-tested; both unit systems rendered.

## Comments

- `app/setup/car.py` (`CarPage`, fourth in `PAGES`; the recap's Car and Charger groups both change here) and the Qt-free `app/setup/vehicle.py` (`weekly_to_daily`, `daily_to_weekly`, the two halves' key lists, `problems(config)`). The shared fold button moved to `pages.fold()`.
- **Car**: usable battery (with the gross-versus-usable note and "about 40 to 100 kWh"), consumption in the chosen units (with the typical range converted: 14-22 kWh/100 km, or 2.82-4.44 mi/kWh, ordered so the lower comes first), **distance in a week** shown in km or miles and stored as a seventh of it per day, the charge levels ("Never below", "Charge up to") with one-line explanations. The levels are preferences: not typical values, nothing remembered.
- **Charger**: phases (radio buttons) with how to tell (label, manual, fuse box) and the single-phase-switch note including that three phases need about three times the surplus; highest and lowest current (per phase); the voltage under "Advanced". Each half has its own "I don't know the ... numbers", which puts that half's typical values back and remembers them; the efficiency is never asked and stays typical.
- **Validation is Settings' own**: the page runs `validate` and shows the "[car]" and "[charger]" lines it returns (minimum below target; lowest current not above the highest), outside the scrolling part so it is seen; Next waits.
- **A bug found by a test I wrote for it**: a spin box clamps its value when its range is set and announces the change, so opening the page recorded a consumption of 5 as the person's own answer (and removed it from the typical list). Reshaping the boxes now records nothing; a test opens the page in both unit systems for a new and a set-up config and checks no answer changed. Mutation-checked, with the remembering of the charger values.
- Units follow the first page, re-read each time the page is shown (going back to Welcome and changing them works); an untouched distance is never rewritten from its rounded display.
