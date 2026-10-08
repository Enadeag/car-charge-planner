# Location page

Status: done (not committed yet)
Type: feature
Blocked by: S4, S5

## Acceptance

- Search box with a short pause before searching, results as "Name, region, country", pick one; the time zone is taken from the result.
- "Enter coordinates instead": latitude and longitude with a hint on reading them from a map app; validated; the time zone defaults to the system's, editable under "Advanced".
- Next is disabled until a real location (not 0, 0) is chosen.
- A visible line that the search text goes to the weather provider; a clear message and the coordinates fallback when search is unavailable or offline.
- Tests: search with a fake client, selection, errors, manual entry, validation, the disabled Next.

## Comments
- 2026-10-07: the place search exists (S4, P9): call `forecast.selection.place_search(cfg)`; None means offer coordinates only and say why ("<Provider> has no place search"). Name the provider that receives the typed text, show `Place.label` and, where two results read alike, `detailed_label`. Add to the README's Data and privacy: the text typed in the search box goes to the primary provider's place search.

- `app/setup/location.py` (`LocationPage`, in `PAGES` between Welcome and Finish). Asks `place_search(cfg)` for the **primary** provider's search; the privacy line names it ("What you type here is sent to Open-Meteo to find the place."); without a place search the search box and the "Enter coordinates instead" toggle are hidden, the coordinates are shown, and the line says "<Provider> has no place search, so enter the coordinates of your panels instead." (the forecast-providers P9 wizard parts are done here).
- Search: after a 400 ms pause (Enter searches at once), on a worker thread, in the app's language; fewer than two characters sends nothing and says "Type at least two characters."; the newest search wins (a late answer to an older one is ignored). Results read "Name, region, country", with the finer area when two would read alike; "N places found. Pick yours."; no match, busy, unreachable and odd-answer each have a plain message, and a problem opens the coordinates box. The credit "Place data: GeoNames, via Open-Meteo.com" shows where the search is used (a provider attribute, `place_attribution`).
- Picking sets latitude, longitude and the time zone from the result and the place's name for the recap. Typing coordinates sets them, forgets the name, and takes the computer's time zone unless one was chosen by hand. The time zone is under **Advanced** (the same searchable list as Settings, now `timezone_box()`); an unknown one is refused and opened. Next waits until the place is a real one (not 0, 0) and the zone is known. A re-run starts with the current place (coordinates).
- README: Data and privacy says the typed place goes to the primary provider's place search, and that place names come from GeoNames (CC BY 4.0); a test keeps this in step with the providers that have a place search; the Attribution section credits GeoNames.
- Looked at in light and dark: the two fold buttons were full-width bars and are now small arrow buttons; the picked row is selected. Pseudo-language test covers six states of the page (start, results, none found, unreachable, busy, odd answer) with and without a place search. Mutation-checked: the late-answer guard, the not-0,0 rule, the pause, telling alike places apart.
- A known limit: a place search goes to the first provider only; if the user later picks another primary in Settings, the page uses that one the next time it is opened.

- **A rare crash, found and fixed on the way** (a segmentation fault in the full suite, about one run in three after these tests were added, always in an unrelated test's event loop). Cause, found with a small plugin that listed every `Controller` still alive after its test: tests that opened Settings or the set-up wizard through a controller left the window (and so the controller, its timer and any queued fetch result) alive for the rest of the session, and two tests ended with a fetch still running. Fixes: the autouse fixture in `tests/conftest.py` now hides and deletes every top-level window a test left, delivers what is queued, and waits for worker threads (failing if one will not finish); the two tests that left a fetch running now wait for it (`settle_controller`). Afterwards no controller outlives its test, the suite runs about 20% faster, and it passed 5 full runs in a row. Nothing in the app itself changed for this: the app keeps one window per kind and never discards one with a worker running.
