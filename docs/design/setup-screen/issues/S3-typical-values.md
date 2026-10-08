# Typical values are remembered

Status: done (not committed yet)
Type: task
Blocked by: I1

Spec section 5.

## Acceptance

- `[setup] typical = [...]` (setting keys) read, validated against the known keys, and written.
- Settings shows the "Assumed"/"Estimate" tag only on keys in that list; saving an edited value removes its key; saving without touching a field leaves it. A user who has given every value sees no tags.
- The plan view carries one line "Based on typical values for: ..." (translatable, plural-aware, shortened when long), linking to Settings; nothing is shown when the list is empty.
- The fixed per-field tags in `app/settings/fields.py` are removed.
- Tests: tags follow the list, edit clears, untouched stays, the plan line appears and disappears, an old config without the section works.

## Comments

- `[setup] typical = ["panels.kwp", ...]`: `Config.setup.typical`, validated against `TYPICAL_KEYS` (the 13 settings a person can give no value for: the four panel values, the house baseload, battery size, consumption, driving per day, and the charger's phases, currents, voltage and efficiency; charge levels and the planning margin are preferences and not on it) and against duplicates; an unknown or malformed list is refused. Always written, `typical = []` when empty.
- **A decision to check**: a config file with **no** `[setup]` section (every file written before this) means "these are your values": nothing is tagged. Only `first_run_config()` (what `ensure_config` writes on a new install) marks every key typical. The alternative, treating old files as all-typical, would tag values that people entered themselves.
- Settings: one tag, **Typical value** (it replaces the fixed "Assumed" and "Estimate" and their per-field flags, which are gone; `FieldSpec.flag` stays for a provider's "Required"). The tag shows while the key is listed and the field has not been edited, and clears **at once** when it is edited (not only after saving); typing the original value back brings it back. Saving removes edited keys and leaves untouched ones; other tabs' keys are never touched; **Reset tab** makes that tab's built-in values typical again (it is a change, so Close becomes Cancel unless they already were).
- Plan page: a muted line under the status rows, "Based on a typical value for: Panel size." / "Based on typical values for: Panel size, Tilt, Direction and 5 more." (plural-aware, three names at most), with a themed "Check them in Settings" link that opens the tab of the first listed value. Nothing when the list is empty, and nothing before there is a plan (not set up, no forecast).
- Mutation-checked: an edited key staying in the list, the tag ignoring edits, and the line shown when empty each fail tests. The pseudo-language test renders the line.
- Not here: the typical *values* table for "I don't know" (spec section 4) belongs to the wizard tickets (S7, S8, S9); this ticket only remembers which values are typical.
