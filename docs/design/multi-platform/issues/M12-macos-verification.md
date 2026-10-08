# macOS verification (a friend, after launch)

Status: ready-for-human
Type: HITL
Blocked by: M11

The maintainer asks someone with a Mac to run this on a release candidate. Until a release has passed it, macOS stays labelled **beta**.

## Checklist (written into `docs/macos-checklist.md` by M11)

- Open the dmg, drag to Applications, first launch (right-click, Open); no Dock icon; the icon is in the menu bar.
- Click it: the popup opens below it; click-away and Escape close it; Settings opens.
- A notification appears and **clicking it opens the popup** (the known risk; if it fails, a native backend ticket is written).
- "Start when I log in": tick, log out and in, the app starts hidden; untick, it does not.
- Dark and light appearance; a Retina and a non-Retina display if available.
- Quit from the menu; starting it twice shows the running instance.
- Result with screenshots in the comments.

## Comments
