# Convert every user-visible string

Status: done (not committed yet)
Type: task
Blocked by: I1

Spec section 6. About 150 existing strings.

## Acceptance

- Every user-visible text in `app/` goes through `_`, `ngettext`, `pgettext` or `N_`: the view model, notifications, the popup and window, Settings (field labels, hints, tab titles, errors), the shells' menus, the selftest and the command-line messages, the single-instance message.
- The ~31 sentences built from fragments become one template each with named placeholders; counts use `ngettext`.
- Day and month names become translatable strings with context; the fixed English tuples are gone.
- Numbers in text go through one formatter with a translatable decimal separator.
- Module-level texts (the field definitions, tab titles) use `N_` and are translated on display.
- Validation errors from `planner/model.py` that reach the user are translatable too.
- No behaviour change in English: all existing tests pass unchanged in meaning.
- The `.pot` is generated and committed.

## Comments

- Done. Every `app/` module and the validation messages in `planner/model.py` go through the lookups; `planner` imports `app.i18n` for that (the module has no Qt dependency).
- Settings tabs now have a stable `key` ("solar", "house_car", "charger", "planning", "interface", "away"); `show_tab` and the popup's `settings_requested` signal use keys, so a translated title cannot break opening a tab.
- Shown dates are one template, `"{weekday} {day} {month}"`, in `app/names.py` (`short_date`); the weekday and month names carry contexts.
- The advice reason is three whole phrases (today, tomorrow, a named day) instead of a lower-cased label.
- `planner/cli.py` is the developer demo tool and stays English on purpose.
- The plain-English fallback time zone of a bare `PlanView` is now UTC (it was a personal default).
- New tests: `tests/test_i18n_sources.py` (reads the code: lookups take literal text, no raw English handed to a widget, no sentence joined from translated pieces; mutation-checked) and `tests/test_i18n_texts.py` (word order, plurals, decimal separator, names, tab keys, validation errors).
