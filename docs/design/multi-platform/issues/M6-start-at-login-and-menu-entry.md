# Start at login and the application menu entry, per system

Status: ready-for-agent
Type: task
Blocked by: M5

Spec section 2 (Start at login), section 4 (the platform layer).

## Acceptance

- `app/platform/` with an interface `start_at_login` (`enable`, `disable`, `is_enabled`) and `menu_entry` (`install`, `uninstall`, `is_installed`), and one implementation per system: Linux (the existing `.desktop` files, moved, behaviour unchanged), Windows (a per-user `Run` registry key; a Start menu shortcut), macOS (a LaunchAgent plist; the app bundle is the menu entry).
- The entry runs the installed program (the installed command, the installer's executable, or the interpreter with `-m`) and starts the app hidden (`--background`); no path depends on the current directory.
- Settings, Interface tab: a **"Start when I log in"** checkbox, shown on all systems, saved with the rest and applied at save time; its state is read from the system, not from the config file. A failure shows an error line and leaves the box as it was.
- The terminal commands (`install-autostart`, `uninstall-autostart`, `install-launcher`, `uninstall-launcher`) keep working through the same implementations.
- Tests: Windows through a fake registry object, macOS through a temporary `LaunchAgents` directory, Linux as today; the Settings checkbox with a fake platform layer; translations of the new texts (pseudo-language test still passes).

## Comments
