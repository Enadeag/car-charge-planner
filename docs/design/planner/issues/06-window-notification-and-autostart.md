# Window-start notification and autostart

Status: resolved (autostart written but deliberately not installed; waiting for the user to run it)
Type: task
Blocked by: 04

Notify 15 minutes before a recommended **charge window** and start at login (spec 5, 6).

## Acceptance

- Desktop notification `notify.lead_minutes` before each window: window times, kW and amps, reminder to set current in your charger's app.
- No notification for windows already started or when the forecast is stale.
- Autostart at login via systemd user service or KDE autostart file, with install instructions.

## Comments

Implemented: `app/notifications.py` (pure: when and what), `app/autostart.py`, wiring in `app/tray.py`, `storage/state.py` (`notified`). 112 tests including tray round trips with a frozen clock and a stubbed `showMessage`.

Notification rules:
- Announces a window once, when `now < start <= now + lead` (config `notify.lead_minutes`, default 15).
- Never announces a window that has started. The live plan clips a running window's start to now, so those drop out by construction.
- A window overlapping one already announced counts as announced, so a forecast refresh that nudges a start by minutes does not notify twice. Announced windows are saved in `state.json`, so a restart does not repeat them (pruned after a day).
- Silent when the last forecast refresh failed or the forecast is older than 6 h (spec 4.7).
- Covers solar windows and the grid-charge window from ticket 07 (worded differently: "Grid charge window", stating the minimum). The grid window can fall at night.
- Text: window times, amps and kW to set in your charger's app, expected kWh and solar share.

Verified on the real desktop: one test notification via the same Qt call reached `org.freedesktop.Notifications` with the correct title and body.

Autostart (XDG autostart entry, picked over a systemd unit because it needs no environment plumbing for the graphical session):

    cd ~/projects/car-charge-notification
    .venv/bin/python -m app install-autostart      # writes ~/.config/autostart/car-charge-planner.desktop
    .venv/bin/python -m app uninstall-autostart    # removes it

The entry uses absolute paths to this venv and sets the working directory, so the project directory must stay where it is. A second launch is refused by a lock file, so a manual start plus autostart cannot create two tray icons.
