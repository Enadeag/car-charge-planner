# Charger phases

Status: done (not committed yet)
Type: task
Blocked by: I1

Spec section 5. Without this a three-phase charger gets plans wrong by a factor of three.

## Acceptance

- `charger.phases` (1 or 3; default 1) read, validated and written; a config without it means one phase.
- Power is `phases x amps x volts / 1000` in `planner/windows.py`, `planner/advice.py` and `planner/surplus.py`; the minimum-current threshold and the amps-for-surplus calculation follow (a 6 A minimum is about 4.1 kW on three phases).
- Notification, popup and window texts say the current is per phase when there are three; the Settings field and its hint are added.
- Tests: a matrix of currents and surpluses for one and three phases; a three-phase plan never recommends a power below the three-phase minimum; existing one-phase tests unchanged.

## Comments

- `charger.phases` (1 or 3, default 1): an old config means one phase; validated (a bool, 2 or 1.5 is refused).
- One function, `planner.surplus.charge_power_kw(amps, cfg)` = amps x volts x phases / 1000, used by `windows.py` and `advice.py` (three places); `amps_for` divides by volts x phases, so the minimum-current threshold follows (6 A is 1.38 kW on one phase, 4.14 kW on three). Mutation-checked in both functions.
- Texts: `{power} kW ({amps} A per phase)` in the outlook, advice and detail lines, "at {amps} A per phase" in the tooltips, "Set the current to {amps} A per phase (...)" in the notification: each a whole translatable text, used only with three phases; one-phase texts are unchanged.
- Settings: a **Phases** choice first on the Charger tab (the stored value is the number 1 or 3, so the choice field now returns the stored value as it is instead of its text), with the "how to tell" hint and the single-phase-charging note; the current fields say "per phase".
- Found on the way: the test helper's forecast is power for the example 4.8 kWp system, so a test cannot get a bigger array by changing the config's panels any more; the three-phase tests use a stronger (artificial) irradiance instead.
- A three-phase charger on a surplus of about 3 kW gets no window (6 A on three phases already takes 4.1 kW), while one phase uses 13 A: tested, and worth knowing for users: the Phases hint mentions single-phase charging for that reason.
