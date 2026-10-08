# Tray, popup and look on Windows and macOS

Status: ready-for-agent
Type: task
Blocked by: M5

## Acceptance

- The tray shell shows the icon and opens the popup on Windows (left click, popup near the notification area at the bottom or wherever the taskbar is) and macOS (menu bar, popup below it); click-away and Escape close it; tested offscreen where possible and written into the M9 and M12 checklists where not.
- macOS: the app bundle setting `LSUIElement` is in the build files (M11); a running-from-source start does not try to hide the Dock icon.
- Windows: the icon is a proper multi-size `.ico` generated from the existing SVG at build time; the window mode and Settings use the system dark/light palette like on Linux.
- Fonts, sizes and the "Close" button of the popup look right at 100%, 150% and 200% scaling (checked on Windows by the maintainer, recorded in M9).
- The "no tray" fallback notice is Linux-only; Windows and macOS always have a tray or menu bar.
- The window shell on Windows and macOS: quitting from its menu quits; closing hides (as today).

## Comments
