# Review against the spec

Status: written 2026-10-07, for tickets P1-P8 and P10. **P9 (place search) is not built** (it waits for set-up ticket S4), and the two wizard sentences of spec section 7 belong to the set-up tickets; both are listed at the end.

## 1. Compliance, section by section

| Spec | Verdict | Evidence |
| --- | --- | --- |
| 2 Name: "forecast provider" in the UI and docs | Met | Tab "Forecast", headings "Forecast provider" and "Fallback provider"; no "weather source" left in user texts. |
| 2 Contract: power per step, `gti_wm2` becomes `power_kw` | Met | `ForecastStep(start, end, power_kw)`; the planner has no irradiance, kWp or losses (tested: the same steps plan the same for any panel settings). |
| 2 At launch: Open-Meteo and Forecast.Solar | Met | Both built in, both registered, both live-checked (below). |
| 2 Discovery: built in, registry, documented interface, fake provider, conformance kit | Met | `forecast/registry.py`, `docs/providers.md`, `tests/fake_provider.py`, `tests/provider_kit.py`; the guide's own skeleton runs through the kit. Entry points: not offered, documented as such. |
| 2 Secrets: `secrets.toml`, 0600, never in `config.toml`, masked | Met | `forecast/secret_store.py`; created private even with umask 0; a secret in the config is refused on write and never used; masked box with Show. Mutation-checked. |
| 2 Selection: Forecast tab with fields, description, what is sent, limits, terms, Test | Met, one nuance | All present. "Limits" are derived from the declared refresh interval and horizon; the contract has no free-text field, so a provider's rate limits worth knowing go in its description (Forecast.Solar's do). |
| 2 Selection: wizard names the provider on Welcome and Finish | **Open** | The wizard does not exist yet: set-up tickets S5-S9. Noted there. |
| 2 Failure: primary first at every refresh; keep the primary's last good forecast within its useful age; only then the fallback; the primary takes over when it works | Met | `ForecastService`; 36 service tests, the main rules mutation-checked; live-checked with a dead primary. |
| 2 Caching: per provider and system; cool-down; shorter horizon is not an error | Met | `forecast-<id>.json`; cool-down stored in the cache file so a restart does not ask again; one- and two-day forecasts tested at view-model, popup and controller level. |
| 2 Visibility: footer names the source, fallback and cool-down wording | Met | `Forecast 5 min old · from Forecast.Solar, primary unavailable · <credit>`, `provider busy, retrying later`; the tooltip explains. |
| 2 Place search as an optional ability | **Open** | P9, blocked by S4. The contract has the `search_places` slot (None for both). |
| 3 Interface, errors, steps, losses, time zones | Met | As specified; Forecast.Solar's local-time answers handled across the missing and the repeated hour (energy in those periods, mutation-checked). |
| 4 Open-Meteo: horizon 4 days, hourly, attribution, server address, key, no cloud cover | Met | Key sent as `apikey` (confirmed against the live docs); address must be https (localhost may be http). |
| 4 Forecast.Solar: public plan, optional key, 429 with retry time | Met | Retry time from the header or the body; checked against the live "Facing 429 responses" page. |
| 5 Orchestration steps 1-6, result fields, late-result guard, drop the shown forecast on change | Met | `forecast_key` includes panel size and losses (the provider applies them) and the selection; tests in `test_apply_config` and `test_controller_forecasts`. |
| 6 Config and files | Met | `[forecast]` and `[forecast.<id>]`; an old config means Open-Meteo, no fallback; the old `forecast.json` is never read. |
| 7 Settings tab, footer, plan page | Met | Rendered and looked at in light and dark (two things fixed, below). |
| 8 Testing | Met | Conformance kit, fake provider, the orchestration matrix, parsing against recordings with DST cases, secrets tests, planner unchanged in meaning. |

## 2. Found while reviewing, and fixed

1. **The Test button said "5 days" for Open-Meteo.** Its first step is the hour before midnight (it belongs to yesterday), so four days of data touch five dates. Days are now counted from today by each step's middle. Found by the live check; test added.
2. **The "Terms and limits" link was unreadable on a dark window** (default link blue on dark grey). Found by rendering the tab and looking at it. A style rule inside the label now sets a themed link colour (`colours.LINK`, same contrast rule as the other accents).
3. **The two provider sections were run together.** A gap before the fallback.
4. Earlier in the series, from the same habit of checking: validation messages missing from the translation template (fixed with P3), a real home coordinate left in a test fixture (replaced with zeros, P3), and the "secret in the config is still used" gap (P5).

## 3. Behaviour worth knowing

- **A forced refresh forces the fallback too.** When the user presses refresh, whoever answers is asked again; a provider's rate limit is never overridden, forced or not.
- **A call that never got an answer is retried after 5 minutes** (a laptop at login with no network); a bad answer or an unexpected error waits the full minimum refresh; a rate limit waits the time the provider gave, else the minimum refresh.
- **Saving settings clears those waits** (not a rate limit): a corrected key is tried at once.
- **The cache is bounded by the provider's useful age** (12 hours for Open-Meteo, 6 for Forecast.Solar). The old client kept a stale cache indefinitely.
- **Forecast.Solar's public plan allows 12 requests an hour for everything behind one internet address.** The app asks once an hour, but other devices at home share the allowance.
- **The footer credit is shown as the provider gives it and is not translated.**
- **Open-Meteo's forecast includes the hours already past today**; the planner ignores steps that have ended.
- With the default (Open-Meteo), nothing changes for an existing user except the footer wording and a new Forecast tab.

## 4. Live check (2026-10-07, neutral location in central Amsterdam)

- The **Test button** (the real Settings window, offscreen), both providers: Open-Meteo `OK: 4 days of forecast` (after the fix), Forecast.Solar `OK: 2 days of forecast`; nothing written to disk.
- A **normal refresh** through the service, three arrangements (Open-Meteo with Forecast.Solar as fallback, the reverse, Open-Meteo alone): each answered by the primary, a second call came from the cache.
- **Switching primary and fallback**: both orders work; Forecast.Solar gave 26 steps over 2 days (peak 2.0 kW), Open-Meteo 96 steps over 4 days (peak 2.9 kW) for the same 4.8 kWp system. They differ, as the spec's risk section said; the footer names the source.
- **A dead primary** (an unreachable server address) was answered by the fallback with `from_fallback` and the primary named.
- **A rate-limit cool-down**, simulated with the real provider classes and a replayed 429 with `Retry-After`: the primary's cached forecast kept being used (stale, "provider busy"), a forced refresh did not ask again, the primary answered again once the limit had passed.
- About 8 requests went to each provider's public service during the series. Not tried: a paid plan or an API key (none available), a real Forecast.Solar 429 (would need burning the limit; the fixture is made by hand from the documentation).

## 5. Terms and limits, re-read 2026-10-07

Confirmed on the live pages: Open-Meteo free API non-commercial, under 10,000 calls a day, 5,000 an hour, 600 a minute; paid plans use `customer-api.open-meteo.com` with the `apikey` parameter; data CC BY 4.0 (stated on the licence page). Forecast.Solar public plan: free for life, one plane, this and the next day, 1 hour, 12 calls per 60 minutes per IP address; the 429 answer carries `retry-at` in the body and `X-Ratelimit-Retry-At` as a header. Forecast.Solar still states nothing about commercial use of the public plan. Nothing in the README needed to change.

## 6. Could not be verified

- Anything on a real KDE or GNOME desktop: the tab, the footer and the Test button were run and looked at offscreen only.
- A paid Open-Meteo or Forecast.Solar plan (no key).
- A real rate-limit answer from Forecast.Solar (hand-made fixture).
- Behaviour behind a proxy or a captive portal (an HTML page instead of JSON is treated as a bad answer, tested with a stub).
- Solcast: not built, only documented.

## 7. Still to do

- **P9** (place search): waits for S4. `search_places` is None on both providers.
- **Wizard**: Welcome names the provider and what it receives; Finish does its connection check with the primary and names it (spec section 7). Added to the wizard tickets.
- **Windows and macOS**: provider code is portable (no OS calls); the secrets file's mode check is skipped where file modes do not exist.
