# The Forecast tab in Settings

Status: done (not committed yet)
Type: feature
Blocked by: P5, P6, I1 (set-up and i18n work)

Spec section 7.

## Acceptance

- A **Forecast** tab: primary dropdown and its fields; fallback dropdown ("None" by default) and its fields; for each provider the description, what it sends, its limits and a terms link.
- **Test** button: one fetch on a worker thread, reports "OK: 4 days of forecast" or the error in plain words (rate limited, no network, missing key), without saving.
- Validation: primary and fallback differ; required settings present; secrets masked and written to `secrets.toml`.
- Dirty tracking, reset-tab and the Close/Cancel label work for it like the other tabs; strings translatable; rendered in light and dark; no clipped text.
- Tests for each of the above, including the Test button with the fake provider.

## Comments

- `app/settings/forecast_tab.py`: the **Forecast** tab (between Planning and Interface), key `forecast`. A picker for the primary provider and one for the fallback ("None" first); every registered provider has a **card** (description, what is sent, "Asks at most once every N minutes. Looks N days ahead." from its metadata, a terms link, and its settings as fields) that sits under the role it is chosen for. One provider cannot be both: the fallback then says so, its Test button is off and Save refuses.
- Limits are derived from the provider's declared refresh interval and horizon; the contract has no free-text "limits" field, so a provider that has rate limits worth knowing (Forecast.Solar's 12 an hour) says so in its description.
- **Test** runs one fetch on a worker thread with what is typed now (settings, keys, and the location from the other tabs, saved or not), straight against the provider (not the service cache), and says `OK: N days of forecast` or the problem in plain words (rate limited with the time to wait, no network, answer not understood, missing setting, no location). A missing required setting is said without asking. An unexpected provider error names its type, never its text (it may hold a key). Nothing is written.
- **Window**: the tab's plain settings go through `apply_to`, its secrets through `collect_secrets`; Save checks the whole selection (`check_selection`: unknown provider, unknown setting, required setting missing) before writing anything, writes `secrets.toml` first and then `config.toml`, and a secrets error stops the save. A changed secret alone marks the window as changed (Close becomes Cancel), mutation-checked. **Reset tab** gives Open-Meteo, no fallback, and empty settings and keys. A secrets file that cannot be read is said on the tab; saving without typing a key leaves it untouched. A provider id from a newer version shows as "<id> (not available)" and must be replaced to save.
- The tab **scrolls**: two providers with their settings are taller than the other tabs and were squeezed (the existing clipped-hint test caught it). Required settings carry a "Required" tag; secret ones are masked with a Show button.
- New colour `SUCCESS` (same contrast rule as the others). `ForecastService.forget_failures()` is called when settings are saved, so a corrected key or address is tried at once instead of after the retry wait (a rate limit still stands).
- The pseudo-language test treats the providers' names as proper nouns. The rest of the tab is translated, rendered in both themes in the tests, and unclipped.
