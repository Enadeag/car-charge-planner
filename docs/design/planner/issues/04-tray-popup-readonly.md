# Tray icon and read-only popup

Status: resolved (popup placement on Wayland needs a look on a real desktop)
Type: task
Blocked by: 03

PySide6 tray app showing the plan (spec 5, 6).

## Acceptance

- Tray icon on KDE Plasma Wayland; neutral vs highlighted when a **charge window** is active.
- Tooltip: next window and power.
- Popup: next window, power in kW and amps, solar share, 2-3 day outlook, forecast age, Open-Meteo attribution.
- Hourly refresh and recompute.
- Menu entry that opens the config file.
- Manual smoke test on the real desktop.

## Comments

Implemented in `app/` (`tray.py` Qt layer, `view_model.py` pure text, `config_file.py`) and `storage/state.py` (read-only battery level).
Run: `.venv/bin/python -m app`. Config is created at `~/.config/car-charge-planner/config.toml` on first start.

Verified: 47 tests (view model, config file, popup renders offscreen); the tray item registers with Plasma's StatusNotifierWatcher with the right tooltip; the popup renders correctly to an image.
NOT verified (no screen access): where the popup appears on Wayland, whether left click toggles it, how the icon looks in the panel.

Notes:
- Battery % is a placeholder (50%, flagged "assumed") until ticket 05 adds entry.
- Day and month names are fixed English strings: Qt changes the process locale, so `strftime("%a")` turned Sun into "zo".
- The active window is replanned every minute with a clipped start, so its power/end can shift slightly while running. Ticket 06 must schedule notifications from windows planned ahead of time, not from the live clipped plan.
- Wayland does not let apps position windows; if the popup lands in a bad spot, switch it to a QMenu-based popup.
