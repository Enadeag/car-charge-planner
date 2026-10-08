# Forecast.Solar provider

Status: done (not committed yet)
Type: feature
Blocked by: P1, P2

Spec section 4. Check every API detail against the live documentation first.

## Acceptance

- Client for the public estimate endpoint (no key), using the system's location, tilt, direction and kWp; optional API key (secret) for paid plans.
- Converts the returned watts by local timestamp into aware `ForecastStep`s, with the response's time zone, DST gaps and repeats handled; the horizon (this and the next day) and 1-hour resolution of the public plan are declared.
- Rate-limit answers (12 calls an hour per address) raise `RateLimited` with the retry time when the response gives one; other HTTP and network failures map to `Unavailable`; unexpected shapes to `BadResponse`.
- The system losses setting is documented as not applied; the azimuth convention is mapped and tested.
- Recorded fixtures (including a rate-limit response and a DST day); passes the conformance kit; fixture notice and attribution.
- No place search.

## Comments

- `forecast/providers/forecast_solar.py` (`ForecastSolar`), registered as a built-in beside Open-Meteo.
- **Checked against the live documentation on 2026-10-07**, and by one real request to the public API (a neutral location, the centre of Amsterdam), which is the recorded fixture:
  - Route `https://api.forecast.solar/[<key>/]estimate/watthours/period/<lat>/<lon>/<dec>/<az>/<kwp>`; tilt and azimuth are integers, lat and lon four decimals; azimuth 0 = south, -90 = east, 90 = west, +-180 = north: the same as ours, so it passes through (tested).
  - The answer is `{"result": {"<local time>": <Wh>, ...}, "message": {"info": {"timezone": ...}, "ratelimit": ...}}`. Keys are **local time at the place**; the value is the energy "from the last timestamp to the timestamp in the key". The arithmetic of the recording agrees (a ramp from sunrise gives the average of 0 and the hourly value). I use that route rather than `watts`, whose "average" wording is loose.
  - Each day starts with a sunrise key worth 0 (nothing before it): it gives no step. So steps are the sunrise-to-first-hour piece, the hourly ones, and the last piece to sunset; nights are gaps. Time arithmetic is done on real instants, so the missing and the repeated hour of a DST day count as what they really are (tested with energy in those periods, mutation-checked).
  - Public plan (account models page): today and tomorrow ("+1 day"), 1 hour, 1 plane, 12 calls per rolling 60 minutes per IP address; with a key 60 or more calls, +3 days, 30 or 15 minute steps. Declared: horizon 2 days (the public plan), refresh 60 minutes (the 12 are shared by everything behind the address), useful age 6 hours.
  - 429: the retry time is in the header `X-Ratelimit-Retry-At` and in the body (`message.ratelimit["retry-at"]`, an ISO time); `RateLimited.retry_after` is that minus now. The documented warning ("if you ignore the retry time you get an infinite 429 loop") is why the service (P6) must honour it.
- The system losses setting is not used (its description says so). The key is the only setting.
- **Fixtures:** `forecast_solar_2days.json` is the real recording (place name, request id and requester address scrubbed). `forecast_solar_429.json`, `forecast_solar_dst_spring.json` and `forecast_solar_dst_autumn.json` are **constructed by hand** in the documented shape (a real 429 would have needed burning the rate limit); `tests/fixtures/NOTICE.md` says so.
- Not found: a standalone terms page. The docs say the public plan is "free for life"; the terms link points at the account models page. Whether commercial use is allowed is not stated; the README (P10) should say to ask the provider.
- The User-Agent (with the project address) now lives in `forecast/transport.py` and is shared.
