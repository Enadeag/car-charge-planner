# Place search as a provider ability

Status: done except the wizard parts (moved to S6)
Type: task
Blocked by: P1, S4 (set-up work)

## Acceptance

- The `PlaceSearch` interface from the set-up work is the provider's optional `search_places`; the Open-Meteo provider implements it with its geocoding service; Forecast.Solar says "not available".
- The wizard's Location page asks the primary provider; when it has no place search the page offers coordinates only, with a line saying why.
- The privacy line on the Location page names the provider that receives the search text.
- Tests with the fake provider with and without the ability.

## Comments

- Done with S4: `search_places` on the contract is a real type (`PlaceSearch`, `PlaceResults`); Open-Meteo implements it with its geocoder; Forecast.Solar says not available (`None`); `place_search(cfg)` picks the primary's; tests with the fake provider with and without the ability.
- **Moved to S6 (the Location page):** asking the primary provider on the page, the coordinates-only fallback with a line saying why, and the privacy line naming the provider that receives the search text.
