# Footer, attribution and horizon in the UI

Status: done (not committed yet)
Type: task
Blocked by: P6, I1

Spec section 7.

## Acceptance

- The two hard-wired Open-Meteo texts in the view model come from the provider that made the forecast: footer "Forecast 12 min old · <attribution>", the tooltip with the full attribution.
- A fallback forecast reads "from <Fallback>, primary unavailable"; during a cool-down "provider busy, retrying later"; both translatable and plural-aware where counts appear.
- A forecast with fewer days shows fewer days, and "Next days" and the quiet-days line cope with a one- or two-day horizon.
- Tests of each footer state and a short-horizon plan.

## Comments

- The two hard-wired Open-Meteo texts are gone (`ATTRIBUTION`, `SOURCE`). The view model gets a `ForecastSource` (the provider's credit, its name, whether it is the fallback, the primary's name, whether it is cooling down) that the controller builds from the `ForecastResult`.
- Footer: `Forecast 12 min old · [from Forecast.Solar, primary unavailable ·] [provider busy, retrying later ·] <the provider's credit>`; stale reads `Forecast 5 h old, refresh failed`. The items are joined with a translatable separator (`pgettext("footer separator", " · ")`); each note is a whole translatable text. Tooltip: the credit, then a sentence naming who is used and why (with the primary's name when known) and, in a cool-down, that the provider asked not to be asked yet.
- The credit is shown **as the provider gives it**, not translated (it is what its terms ask for); the pseudo-language helper treats provider names and credits as proper nouns. Providers are told this in `docs/providers.md`.
- Before there is a forecast (not set up, none yet) the footer is empty: no provider has made anything to credit.
- The wording in the spec said "from <Fallback>, primary unavailable"; that is what the footer says. The primary's name goes in the tooltip, to keep the footer inside the popup's width.
- Short horizons: a one-day forecast gives no "Next days" box and no quiet line; "Not enough solar surplus in the next 1 day." is singular; a two-day forecast shows tomorrow only; late in the day a short forecast still plans. Tests at the view-model, popup and controller level.
