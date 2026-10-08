# Docs and autostart for window mode

Status: resolved
Type: task
Blocked by: 07, 08

## Acceptance

- `install-autostart` adds `--background` when the effective shell is the window shell (or always, harmlessly), so login starts hidden.
- README: new section "Running on GNOME": supported versions (GNOME 48-51), the requirements table from spec section 10 (Wayland session, notifications service, Qt Wayland plugin, launcher entry), the tray extension as **optional** with how to get it per distribution, window mode, what closing does, `install-launcher`, `install-autostart`, the interface setting.
- README and the planner spec note that notifications go over D-Bus.
- A review of the finished work against `docs/design/gnome-support/spec.md`, written next to it as for the earlier features.

## Comments

Done:
- `install-autostart` now writes `Exec=... -m app --background` (always, harmlessly: the tray shell ignores it) and `X-GNOME-Autostart-enabled=true`. An entry written earlier has to be rewritten by running the command again; the README says so.
- README: new section "Running on GNOME" (status, target versions 48-51, the requirements table, the tray extension as optional with install commands for Fedora, Debian, Arch and others, how the window mode behaves, autostart, the interface setting, `test-notification`); the requirements, current-state and "what it does" lines updated; notifications over D-Bus mentioned there and in the planner spec.
- Spec section 10: the Qt Wayland plugin question answered (the PySide6 wheel bundles it).
- Review written: `docs/design/gnome-support/review.md`. Reviewing found one gap, now fixed: the spec's "opens centred on first show" had not been implemented for the window shell.
- Extra tests: the autostart entry's contents, the window's centring and not being moved again, and the whole app started with `--background` on a desktop without a tray.
