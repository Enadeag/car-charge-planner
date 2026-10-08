# Window shell

Status: resolved (built and tested; main() does not choose it until ticket 07)
Type: task
Blocked by: 03, 04, 05

Spec 4.3.

## Acceptance

- `app/shells/window.py: WindowShell`: a normal window holding `PlanView`; title and window icon follow the state; menu button with Settings..., Refresh forecast, Quit; Ctrl+Q quits.
- The close button hides the window; the app keeps running (notifications continue). The first time, a short note says so.
- `--background` starts hidden; a second launch shows and raises the window (ticket 05).
- No click-away closing, no x button of its own.
- Offscreen tests: shows the plan; title follows the state; close hides and the controller keeps running; Quit exits; `--background` is hidden; the menu opens Settings on the right tab.

## Comments

Implemented: `app/shells/window.py` (`WindowShell`, `PlanWindow`), `app/shells/icons.py` (the status icon, now shared with the tray shell, which re-exports `make_icon`), `storage/state.py` (`seen` + `mark_seen`, for one-time hints), `--background` handled in `main()` and accepted by `python -m app`. 23 tests in `tests/test_window_shell.py`; 335 tests in all.

What it is:
- An ordinary top-level window (`QMainWindow`) holding the shared `PlanView`; no x of its own, no click-away. The window title is the state headline ("Charge now", "Next: Today 09:00-15:00", "No forecast available") and the window icon is the coloured status icon, so the taskbar and Alt+Tab show the state.
- A menu button (a hamburger at the right end of the header) with Settings..., Refresh forecast and Quit; Ctrl+Q quits from anywhere in the window.
- Closing hides the window and the app keeps running, so notifications continue. The first close, ever, sends a notification "Still running in the background ... quit it from its menu (Ctrl+Q)"; it is remembered in `state.json` (`seen`) and not repeated, also not after a restart.
- `start(background=True)` leaves the window hidden but starts the controller. `show_plan()` shows, un-minimises and raises the window; it is what a second launch calls (ticket 05).
- The window resizes to the visible page.

Findings:
- A top-level window did not shrink back when going from a taller page to a shorter one: Qt recalculates layouts lazily, so the larger minimum lingered. The window now activates its layouts before `adjustSize()`; a test fails without that. The tray popup does not have the problem (tested), so the shared `PlanView` was left alone.
- `python -m app --background` was rejected as an unknown command; any flag now means "run".
- Rendered in light and dark. Colours are chosen when text is set, so a theme must be in place before the window is created, which is true at start-up. (Rendering after swapping the palette on a live window gave a dark-brown title and a black menu button; with the palette set first, they are bright orange and white.)

Not done here: `main()` still builds the tray shell only; choosing between the shells is ticket 07. The autostart entry does not add `--background` yet (ticket 09).
