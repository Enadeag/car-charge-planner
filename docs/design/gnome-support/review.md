# Review against spec.md (GNOME support)

Date: 2026-10-06. Method: each spec section checked against the code and the tests, the whole app started as a subprocess on a desktop without a tray, and the pieces that touch the desktop (notifications, the session bus, the tray) checked live on the development machine (KDE Plasma, Wayland). 388 tests.

**What this review cannot cover:** a real GNOME session. The development machine runs KDE; no GNOME is installed. Everything below that is about GNOME is therefore "built to the spec and tested with simulated conditions" (no tray, offscreen platform), not "seen working". Ticket 10 is the real check and is the user's.

## Compliance

| Spec | Result |
| --- | --- |
| 3 One project, controller and shells, `interface.mode`, notifications over D-Bus, shared `PlanView` | Met |
| 3 Assumptions: closing hides the window; login starts hidden; own Interface tab | Met (the Interface tab sits before Away rules, not last) |
| 4.1 Controller owns everything but drawing; one `view_changed` signal | Met. The Settings window is opened by the controller, so both shells share it |
| 4.2 Tray shell unchanged for existing users | Met |
| 4.3 Window shell: ordinary window, state in title and icon, menu button, Ctrl+Q, close hides, `--background`, centred on first show | Met. **Centring was missing until this review found it** (added with a test; Wayland compositors may ignore it) |
| 4.4 Single instance by local socket, second start shows the running one | Met (see the Qt finding below) |
| 4.5 Notifier over D-Bus with default action and desktop-entry hint; click shows the plan; failures never crash; `showMessage` gone | Met. Click delivery on a real desktop is unverified |
| 4.6 Icon, `install-launcher` / `uninstall-launcher`, desktop file name, autostart reuses the id and adds `--background` | Met. The autostart `--background` was added in ticket 09 |
| 4.7 Setting `auto` / `tray` / `window`, fallback with a notice, applies at next start | Met |
| 5 Architecture | Met; one addition: `app/main.py`, `app/selftest.py`, `app/notification_actions.py`, `app/identity.py`, `app/shells/icons.py` |
| 6 Testing | Met, with extras: whole-app subprocess tests, a real session-bus round trip, mutation checks |
| 7 Compatibility on KDE | Met, with the listed differences below |
| 8 Out of scope | Respected |
| 10 Requirements | Met. The open question "does the PySide6 wheel bring the Qt Wayland plugin" is answered: yes (`libqwayland.so` and the shell-integration plugins are in the wheel; the app runs on the `wayland` platform here) |

### Differences on KDE (the compatibility rule allowed only the notification path)

- A second start no longer refuses with exit code 1; it shows the running app's popup and exits 0.
- Clicking a notification now opens the popup.
- The popup, when not opened from the tray icon, reappears where it was last shown or near the top right.
- A new setting (`interface.mode`, default `auto`, which gives the tray on KDE), a new Settings tab, and a new `jeepney` dependency.
- A tray that is not yet registered at login is waited for (1 s, or 5 s with `--background`), then the window is used.

## Findings, all fixed

1. **QtDBus cannot make the `Notify` call from Python** (it sends the id as a signed integer, the protocol needs unsigned). Sending and receiving use `jeepney`, a new pure-Python dependency (ticket 01, 08).
2. **Qt's `UserAccessOption` breaks the single-instance race.** With it, `listen()` on a path a *live* server holds succeeds and replaces its socket file, leaving two copies running. Not used; a plain `listen()` plus a private directory and `chmod 0600` instead. Found by the race test; each guard has a test that fails when it is removed (ticket 05).
3. **A top-level window did not shrink back** to a shorter page; layouts are recalculated lazily. Fixed in the window shell with a test (ticket 06). The tray popup does not have the problem.
4. **`isSystemTrayAvailable()` can be false at login** because the panel or extension is still starting; asking once would pick the window on a desktop that has a tray. Fixed by waiting (ticket 07).
5. **The window was not centred on first show** although the spec says so. Fixed in this review.
6. **Desktop id registration:** setting the Wayland app id with no launcher installed made the portal log a failed lookup. The id is now set only when the launcher entry exists (ticket 02).

## Behaviour to know about

- On stock GNOME the window opens about a second late (the app first looks for a tray); 5 seconds at most when started with `--background`.
- Closing the window keeps the app running with no visible sign on GNOME except the notifications; the first close says so, once, ever.
- An autostart entry written before this version has no `--background`: run `install-autostart` again. With the tray shell the flag is harmless.
- Choosing `tray` on a desktop without a tray gives the window plus a one-time notification.
- The old lock file in `~/.cache` is no longer used and can be deleted.

## Not verified here

- Everything on a real GNOME desktop: how the window looks and is placed, whether the Qt look is acceptable without an Adwaita Qt theme, light and dark following the system, the tray extension path, and which of GNOME 48 to 51 behave differently. Ticket 10 lists the steps.
- That clicking a real notification reaches the app on KDE and GNOME (`python -m app test-notification` checks it).
- That the distribution package names in the README (`gnome-shell-extension-appindicator` on Fedora and Debian) are current; Arch's was checked (`extra`, version 66).
