# Extract PlanView from Popup

Status: resolved
Type: task
Blocked by: none

Pure refactor (spec 3, 5). `PlanView` is the Plan and Update pages with their header tabs and footer, without window flags, the x button or click-away.

## Acceptance

- `app/planview.py: PlanView(QWidget)` with the existing signals (`battery_submitted`, `away_added`, `away_removed`, `settings_requested`) and `update_view(...)`.
- `Popup` wraps a `PlanView`: frameless tool window, x button, Escape, click-away rules, `show_near`, `auto_closed_at`. Its look and behaviour are unchanged.
- All existing popup tests pass; tests that reach into the widgets use the same attribute names or are updated minimally.
- Rendered before and after on light and dark themes; no visible difference.

## Comments

Implemented: `app/planview.py` (`PlanView`: the Plan and Update pages, header tabs and footer, no window behaviour) and `app/popup.py` (`Popup`: frameless tool window, close button, Escape, click-away, `show_near`, `auto_closed_at`, wrapping one `PlanView`). 9 tests in `tests/test_planview.py`; the existing popup, tray and settings tests pass without changes.

Notes:
- **Verified pixel-identical** to the version before the split: 8 renders (light and dark theme x Plan and Update page x two different states: stale battery with advice and a one-off away day, and a fresh battery with nothing special). The first comparison caught a 32 px width difference (the view had its own 440 px minimum on top of the popup's margins); fixed by letting only the popup carry the minimum.
- `Popup` forwards unknown attributes to its view (`__getattr__`), so `popup.headline`, `popup.battery_input`, `popup.stack` and so on keep working, and has a `tz` property that sets the view's. Signals are re-emitted unchanged.
- `PlanView.add_to_header(widget)` puts a control at the right end of the header row (the popup's close button; another shell can add its menu button).
- `PlanView.layout_changed` is emitted after the view resizes itself to the visible page, so a wrapping window can follow its height.
- `fit_to_page` is now public.
