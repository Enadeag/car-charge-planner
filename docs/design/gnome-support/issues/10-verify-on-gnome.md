# Check it on a real GNOME session

Status: ready-for-human
Type: task
Blocked by: 07

The development machine runs KDE; none of this can be seen here. Needs a GNOME session: a VM, a live image, or a second machine.

Target versions: GNOME 48, 49, 50 and 51. At minimum test the oldest (48) and the newest (51); say which others were tried. GNOME 50 and 51 have no X11 session, so the app must run under Wayland.

## Checklist

- Install the app (venv, `pip install PySide6-Essentials`), run `python -m app install-launcher` and `install-autostart`.
- Check which Qt platform it runs on (should be Wayland) and whether the Qt Wayland plugin came with the PySide6 wheel or needed a distribution package; write the answer into the README requirements.
- Without the tray extension: starts in window mode, no error, the window opens from the launcher; closing hides it and notifications still arrive; Quit works; a second launch raises the window.
- With the AppIndicator extension (note its version): starts in tray mode; popup behaviour and placement; compare with `interface.mode = window`.
- A notification arrives, shows the app name and icon, is grouped under the app, and clicking it shows the plan. Run `python -m app test-notification` and click the notification: it says whether the click reached the app; then wait for (or provoke) a real window notification and click that too.
- Light and dark appearance follow the system; note anything that looks off (Qt style) and whether an Adwaita Qt theme fixes it.
- Settings window: opens, saves, the Interface tab, the split Save button.
- Log the GNOME version, the distribution, and what differed from the spec.

## Comments
