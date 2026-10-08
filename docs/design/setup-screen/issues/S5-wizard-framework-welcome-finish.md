# Wizard framework, Welcome and Finish pages

Status: done (not committed yet)
Type: feature
Blocked by: I1, S2, and forecast-providers P6 for the Finish page's connection check (it uses the primary provider through the forecast service)

Spec section 3.

## Acceptance

- `app/setup/`: `SetupWizard` in its own window opened by the controller (one at a time, like Settings), Back and Next, page registry, a state object holding every answer, **written only at Finish** (one config write, then `apply_config`); closing early asks "Leave set-up?" and writes nothing; opening it pre-fills from the current config.
- Page logic Qt-free where possible and unit-tested.
- Welcome: the two-sentence description, the privacy text (naming the forecast provider and what it receives), the units choice pre-selected from the region.
- Finish: the recap grouped with "Change" links that return to that page, typical values listed as such, the connection check (one test fetch with the chosen location; the result shown; a failure does not block Finish).
- Pages are translatable from the start and pass the pseudo-language check (I3, when available).
- Offscreen tests with a fake fetch.

## Comments

- 2026-10-07 (forecast-providers review): the Welcome page names the forecast provider (Open-Meteo to begin with) and says what it receives (`provider.sends`); the Finish page does its connection check with the primary and names it. The wizard starts with Open-Meteo and no fallback and does not ask about providers.

- `app/setup/`: `state.py` (`SetupState`: the draft config, how it started, the place's name, hub ticks; `give_value` / `use_typical` keep the typical list in order; `result()` checks it as Settings does) and `recap.py` (`build_recap`, `compass_label`) are Qt-free and tested directly (23 tests); `pages.py` (`Page`: `enter`, `leave`, `can_continue`, `next_label`, `in_sequence`), `welcome.py`, `finish.py`, `wizard.py` (`SetupWizard`, `PAGES`).
- **How later tickets add pages** (S6-S9): write a `Page` subclass with an `id`, put it in `PAGES` in the order of the flow (`in_sequence = False` for a page the hub opens: reached with `wizard.navigate(id)`, left with Back, which pops the history); the Finish page's "Change" link appears by itself for a group whose page exists. Tested with stand-in pages.
- Welcome: the two sentences, the privacy text (names the primary provider and `provider.sends`, and says the typed place goes to it when it has a place search; a generic text for an unknown provider), the units choice pre-selected from the region on a new install and **kept as the user has it** on a re-run.
- Finish: the six-group recap with typical values marked "(typical value)", the "Based on typical values for: ..." line, and the connection check, which **runs by itself on entering the page** (the privacy text says it will) on a worker thread through the new `forecast/check.py`, and again with "Check again". A failure says so and ends "You can still finish; the app tries again by itself"; it never blocks Finish. **Finish itself is disabled until a place is chosen** (what the plan cannot exist without), with a line saying so; no request is made before then.
- `forecast/check.py` is the Settings tab's Test button logic moved out (`try_provider`, `describe_failure`, `forecast_days`, `missing_settings`), so both use the same words.
- **Writing**: only at Finish: `state.result()` (validated like Settings), one `write_config`, then `on_finish(cfg)` (the controller's `apply_config`), then close. A problem is listed in the wizard and nothing is written; a failed write is said and the wizard stays.
- **Leaving early**: close or Escape asks "Leave set-up? Nothing has been saved yet." (Discard / Cancel) and writes nothing. **A decision to check**: it does not ask when nothing was done and the user is still on the first page (an accidental open), and it asks as soon as an answer changed or the user has moved on.
- `Controller.open_setup()`: one wizard at a time, starting from the current config (so "Run set-up again" is pre-filled). The menu entry and the automatic first-run opening are S10.
- Mutation-checked: asking when leaving, a failed check not blocking Finish. The pseudo-language test renders both pages (and the failure text); time-zone names count as data there.
