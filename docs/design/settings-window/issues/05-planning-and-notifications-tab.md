# Planning and notifications tab

Status: resolved
Type: task
Blocked by: 03

Add the tab (spec 3.2): planning margin, import tolerance, minimum window length, notification lead time.

## Acceptance

- Fields with units, limits and hints per spec; margin shown as percent.
- Reset tab works.
- Tests: edit and save; saving a new lead time changes when the next notification is due.

## Comments

Implemented: `PLANNING` spec (margin as percent, import tolerance, minimum window length, notification lead time; the last two are whole-number boxes). A test saves a new lead time through the tray's config and shows the next notification is due earlier (`app.cfg` is read live, so no restart).
