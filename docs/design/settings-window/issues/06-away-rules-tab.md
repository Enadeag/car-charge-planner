# Away rules tab

Status: resolved
Type: task
Blocked by: 03

Add the tab (spec 3.2, decision: list of rows).

## Acceptance

- One row per rule: weekday dropdown (Monday to Sunday), from time, to time, Remove button; **Add rule** appends a row.
- The list may be empty; saving writes no rules.
- A rule that ends at or before its start is reported in the error area on Save (no overnight rules).
- Reset tab restores the default an example weekday 06:00-19:00 rule.
- Tests: existing rule shown; add, edit, remove and save; empty list round trips; bad rule reported.

## Comments

Implemented: `AwayRulesTab` in `app/settings/tabs.py` (rows of weekday dropdown, from, to, Remove; Add rule). Every tab now has `load(cfg)` and `apply_to(cfg)`, and `SettingsWindow` loops over `all_tabs`, so dirty check, Save and Reset tab treat the away tab like the others.
- An empty list saves `recurring = []` and reloads empty (it does not bring back the default example rule).
- A rule that ends at or before its start is reported by `validate()` ("no overnight rules"); the message does not say which row.
- Rules keep their order; editing a time back to its original value is not a change.
- New rules default to Monday 08:00-17:00.
