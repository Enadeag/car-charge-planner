# Time zone through tzlocal, dependencies per system

Status: ready-for-agent
Type: task
Blocked by: M1

## Acceptance

- `Config.timezone` defaults to the system zone from `tzlocal`; `find_timezone`, `system_timezone` and their reading of `/etc/timezone` and `/etc/localtime` are removed, with their tests replaced by tests that fake `tzlocal` (a known zone, an unknown or failing answer falls back to `UTC`).
- A test pins that the common Linux set-ups still resolve (a `TZ` variable, a `/etc/localtime` link) by running `tzlocal` in a controlled environment where possible.
- `pyproject.toml`: `tzlocal`, `platformdirs` for all systems; `jeepney; sys_platform == "linux"`; `tzdata; sys_platform == "win32"`.
- Code that imports `jeepney` imports it lazily and only on Linux; the app starts without it on other systems.

## Comments
