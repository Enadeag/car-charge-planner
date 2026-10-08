# Open-Meteo as a provider

Status: done (not committed yet)
Type: task
Blocked by: P1, P2

Spec section 4.

## Acceptance

- The existing client becomes the Open-Meteo provider: it requests tilted irradiance for the system, converts to power (`kwp x irradiance / 1000 x (1 - losses)`) inside the provider, keeps the preceding-hour handling (the value stamped T covers [T-1h, T)), and drops the unused cloud-cover request.
- Metadata: horizon 4 days, minimum refresh 60 minutes, a useful age declared, attribution "Weather data: Open-Meteo.com (CC BY 4.0)", terms link, what is sent (coordinates and panel angles).
- Settings: an optional server address and an optional API key (secret); the key is sent the way Open-Meteo documents for commercial use (checked against its live documentation).
- Passes the conformance kit with the existing recorded fixture; the fixture folder has a notice crediting Open-Meteo.
- The User-Agent names the project and its URL.
- The old module's cache and public functions are removed once P6 uses the new provider; until then both coexist.

## Comments

- `forecast/providers/open_meteo.py` (`OpenMeteo`), registered as a built-in in `forecast/registry.py`. It takes a transport (default: the real one). The old `forecast/open_meteo.py` stays for the controller until P6 replaces it; both use the same formula for now.
- Checked against Open-Meteo's live documentation on 2026-10-07: the key is the `apikey` query parameter on a customer server (`customer-api.open-meteo.com`); the free API is non-commercial, under 10,000 calls a day; the data is CC BY 4.0. A paid plan is a server address plus a key.
- A server address must be `https://` (or `http://localhost` / `127.0.0.1` for a server of your own); anything else is `NotConfigured` before a request is made. 429 is `RateLimited` (with `Retry-After` when given), 5xx and a failed connection `Unavailable`, a refusal (400, 401, 403) or an answer that is not as documented `BadResponse` (401 and 403 say to check the key and address). Error texts never include the URL, which may carry the key; tested by mutation.
- Declared: horizon 4 days, refresh 60 minutes, useful age 12 hours. The User-Agent carries the project's address (`github.com/Enadeag/car-charge-planner`, the name after the rename).
- Provider texts (description, what is sent, setting labels and hints) are `N_`-marked and translated when shown.
- Found on the way, and fixed in this ticket: the template extraction only read `app/`, so the 29 validation messages in `planner/model.py` (I2) were marked but never reached the `.pot`. It now reads `app`, `planner`, `forecast` and `storage` (236 messages), with a test.
- The recorded fixture still held the real home coordinates; they are now zeros (the history rewrite already replaces them in every older version), and `tests/fixtures/NOTICE.md` credits Open-Meteo.
