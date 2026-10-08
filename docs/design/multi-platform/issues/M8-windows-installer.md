# Windows installer

Status: ready-for-agent
Type: task
Blocked by: open-source 04, M6, M7

## Acceptance

- A PyInstaller spec builds a one-folder Windows bundle (PySide6-Essentials only) that starts the app; translations compiled and included; the icon included.
- An Inno Setup script wraps it in `car-charge-planner-<version>-setup.exe`: per-user install (no admin), Start menu entry with an AppUserModelID so notifications show the app's name and icon, an optional "Start when I log in" task, an uninstaller that removes the entries and the `Run` key and keeps the user's data.
- A GitHub Actions job builds it on `windows-latest` and uploads it as an artifact; a smoke test starts the built program with a flag that exits after the application is up.
- Unsigned; the README text for the SmartScreen warning is drafted here (placed by M13).
- Windows on arm64 is noted: the x64 build runs through emulation; a native arm64 build is a follow-up.

## Comments
