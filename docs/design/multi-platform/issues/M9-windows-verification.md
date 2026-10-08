# Windows verification (the maintainer)

Status: ready-for-human
Type: HITL
Blocked by: M8

The maintainer tests on Windows 10 or 11. Gate for the public launch: open-source 11 does not proceed until this is done or the maintainer decides to launch without it.

## Checklist (the agent writes it into `docs/windows-checklist.md` as part of M8)

- Install from the `.exe`; the Start menu entry opens the app; first run shows the set-up or the not-set-up state.
- Tray icon visible (or found in the overflow area); left click opens the popup near it; click-away and Escape close it; Settings and the plan window open.
- A notification appears with the app's name and icon; clicking it opens the popup.
- "Start when I log in": tick it, sign out and in, the app starts hidden; untick, it does not.
- Dark and light theme; 100%, 150%, 200% scaling.
- Second start shows the running instance instead of a second one.
- Uninstall removes the entries; config and state remain.
- Result recorded in the comments below, with a screenshot of anything wrong.

## Comments
