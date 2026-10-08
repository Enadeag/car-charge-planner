# Planner core with fixture forecast and CLI

Status: resolved
Type: task
Blocked by: none

Build the pure planner (spec sections 4.2-4.5, 6) and a CLI that prints today's plan from a fixture forecast. No UI, no network.

## Acceptance

- `planner/` modules: model, surplus, windows, projection, per spec section 6.
- Config loaded from TOML with the section 7 defaults.
- CLI prints today's **charge window(s)**, **charge power** (kW and amps), solar share, expected energy.
- Table-driven tests from spec section 8: clear day, overcast day, surplus below 1.4 kW, **away period** removal, planning margin keeps expected import within tolerance, DST days, midnight-spanning windows.

## Comments

Implemented in `planner/` (model, surplus, windows, projection, demo, cli) with 21 tests in `tests/test_planner.py`.
Run: `.venv/bin/pytest -q` and `.venv/bin/python -m planner.cli --demo summer --battery 45`.

Notes for follow-up tickets:
- One constant charge power per window means the shoulders of a sunny day are not captured (e.g. summer day: 2.1 kW over 09:00-15:00 vs ~2.6 kW peak). Two windows at different powers would capture more but means changing the current twice. Revisit once there is real production data.
- Projection applies driving at end of day and does not yet warn about dropping below the minimum charge level (ticket 07).
- Midnight-spanning runs are tested with synthetic data only; real solar never produces one.
