# Place search

Status: done (not committed yet), together with the non-wizard part of forecast-providers P9
Type: task
Blocked by: I1

Spec sections 3 and 5. An optional capability with a small interface; Open-Meteo's geocoding service first.

Note: the geocoding client built here becomes the Open-Meteo provider's place-search ability (forecast-providers ticket P9); keep it independent of the wizard.

## Acceptance

- `app/geocoding.py`: `search(text, language) -> list[Place]` (name, region, country, latitude, longitude, time zone), injectable transport, errors as a typed result (no network, no results, rate limited), short timeout, no search for fewer than two characters.
- A `PlaceSearch` interface so a weather provider without a geocoder can say "not available" and the wizard falls back to coordinates.
- Recorded fixture for tests; the suite's network guard stays on.
- The User-Agent names the project.

## Comments

- Built with P9 in mind, so it lives with the provider contract and not in `app/`: `forecast/provider.py` has `Place` (name, coordinates, time zone, region, country, finer area; `label` = "Name, region, country" without repeating the name; `detailed_label` adds the area), `PlaceResults` (places, or a typed `Problem`: too short, unavailable, rate limited, bad response; matching nothing is `ok` with no places) and the `PlaceSearch` protocol (never raises). `forecast/providers/open_meteo_geocoding.py` is the client.
- Checked against the live documentation and by two real requests (2026-10-07): `https://geocoding-api.open-meteo.com/v1/search?name=&count=&language=&format=json`; two characters match a name exactly, three or more by prefix; an answer with no matches has no `results` key; errors are a 400 with `{"error": true, "reason": ...}`; data from GeoNames. Recorded: "Amsterdam" (it returns two "Amsterdam, Ohio" that read alike, so the finer area is kept for the UI to tell them apart, and "eMvelo" found by another name) and a no-match.
- No request for fewer than two characters; a 6-second timeout (`Request.timeout_s`, new); the language is the code before the underscore, lower case; results without coordinates, a time zone we know, or a name are left out; the User-Agent is the shared one naming the project.
- `place_search(cfg)` in `forecast/selection.py` gives the **primary** provider's search or None (then the Location page offers coordinates and says why). The fallback is never asked: the text would go to a provider the person did not choose first.
- Tests: the conformance suite for place search (`PlaceSearchConformance`, usable by future providers), 34 tests; mutation-checked (the too-short guard, the time-zone check).
- For the Location page (S6): the page names the provider that receives the search text; and the README's Data and privacy section must then say that the text typed in the search box goes to that provider (today no screen sends one).
