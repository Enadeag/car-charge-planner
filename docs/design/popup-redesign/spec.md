# Popup redesign

Status: built 2026-10-06, requested ("the popup feels cramped and dense, hard to read"); scope chosen: everything.

## Problem

The popup answered "when do I charge?" and also held two input forms and a full outlook. The same facts appeared three times (headline, detail line, first outlook row), the grid advice was a ~50-word paragraph, controls were always visible, and four rules plus mixed font sizes gave it little hierarchy.

## Design

Two pages in one popup, opened on **Plan** every time.

**Plan** answers one question and nothing else:
- Headline, one facts line (`Until 15:00 · 1.6 kW (7 A) · 5.7 kWh · 99% solar`), battery projection (`Battery 45% → 52% by 15:00`).
- Grid advice, only when needed, as facts first: a bold title with the when (`Grid charge needed · Tomorrow 15:00-16:25`), a line with power and energy, and one muted line with the reason.
- **Next days**: aligned columns (day, time, power, energy). The window already in the headline is not repeated; days without a window are merged into one muted line (`Thu, Fri: no good window`), left out when every day is quiet because the headline then says so.
- Two compact status lines: battery (`Battery 45%` + thin gauge + **Edit**) and away (`Away: Thu weekly, Fri 9 Oct` + **Manage**). A stale or never-entered battery turns red and the Update tab shows a dot.

**Update** holds the inputs:
- Battery level (box + Set battery) with its status line.
- Car away: weekly rules read-only, one-off days with Remove, **Edit weekly rules in Settings...**, and the add form.

Where things are edited is unchanged: configuration only in Settings, daily inputs (battery, one-off away days) in the popup.

Styling: no horizontal rules, three text sizes (headline, body, small muted), section headings small and muted, 440 px wide, the popup is as tall as the page being shown, one close control (the × in the corner; the bottom Close button is gone). Footer is one short line, `Forecast 5 min old · Weather data: Open-Meteo.com`, with the CC BY 4.0 wording in its tooltip.

## Code

- `app/popup.py`: the popup (moved out of `tray.py`).
- `app/view_model.py`: structured output (`OutlookRow`, `AdviceView`, `quiet_days`, `battery_pct`) instead of ready-made sentences.
- `app/away_input.py`: `away_summary`.
- Tests: `tests/test_popup_layout.py` (new), `tests/test_view_model.py`, `tests/test_popup_offscreen.py`.

## Review notes

- Checked by rendering both pages in a light and a simulated dark theme. Not clicked through on the real desktop.
- The old single-string `advice_text` and the outlook sentences are gone; anything that read them had to change (tests only).
- Found while building: a tab widget is as tall as its tallest page whatever the size policies say, so the popup now fixes the tab area's height to the visible page.
- Fixed after a report: with Qt's `QTabWidget` the tab strip only stretched over the tabs plus its corner widget, so the close button stood about 50 px short of the right edge and the line under the tabs stopped early. The popup now has its own header row (a `QTabBar`, a stretch, the ×) above a `QStackedWidget`. `tests/test_popup_layout.py` checks that the × lines up with the content edge and that the header spans the full width; both failed before the fix.
- Open: the Plan page has no table header for the outlook columns; the layout reads clearly without one.

## Closing (decided with the maintainer)

Both ways, as other tray popups do: a click outside closes it, and the x in the corner, Escape and a click on the tray icon still work.
- Click-away is detected as the popup losing focus. It only counts once the popup has actually had the focus since it was shown (a new window on Wayland may not be activated at once), it waits 150 ms and ignores the loss while one of the popup's own dropdowns is open, and a quick return of the focus cancels it.
- Clicking the tray icon while the popup is open first takes its focus (closing it) and then toggles it. The tray therefore ignores a click within 0.5 s of an automatic close, so that click does not reopen it.
- `tests/test_popup_layout.py`: 8 tests; with the "has had focus" guard and the dropdown guard removed, 3 of them fail.
- Not verified on the real desktop: that KWin reports the activation change the way the tests assume. If the popup ever fails to close on an outside click, or closes while a dropdown is open, that is the place to look.
