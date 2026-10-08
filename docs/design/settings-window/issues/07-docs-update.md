# Update README and spec references

Status: resolved
Type: task
Blocked by: 04, 05, 06

## Acceptance

- README: replace "Open the config file from the tray menu / restart" with the Settings window; keep the file location and note that the app only reads it at startup, so hand edits belong while the app is closed.
- README project layout lists `app/settings/` and `planner/config_io.py`.
- `docs/design/planner/spec.md` section 7 notes that the defaults are now edited in the Settings window.
- Review the finished window against `docs/design/settings-window/spec.md` and write findings next to it, as was done for the planner.

## Comments

Done:
- README: new Configuration section for the Settings window (tabs table, Save/Cancel/Reset, Enter saves, file location, read only at startup, hand edits while the app is closed); layout table lists `app/settings/` and `planner/config_io.py`; current state mentions both reviews.
- `docs/design/planner/spec.md` section 7 points to the Settings window.
- Review written: `docs/design/settings-window/review.md`.
