# House and car tab, Charger tab

Status: resolved
Type: task
Blocked by: 03

Add two tabs using the field specs from ticket 03 (spec 3.2).

## Acceptance

- **House and car**: baseload, usable battery, consumption, driving per day, minimum and target charge level. Estimates labelled.
- **Charger**: minimum and maximum current, voltage, charging efficiency. Efficiency labelled as an estimate.
- Cross-field rules show in the error area on Save: minimum below target, minimum current not above maximum.
- Reset tab works on both.
- Tests: values shown, edit and save, a cross-field error lists both problems when both are wrong.

## Comments

Implemented: `HOUSE_CAR` and `CHARGER` specs in `app/settings/fields.py`. Estimates are labelled (usable battery, consumption, charging efficiency). Cross-field rules come from `validate()`: setting minimum charge above target and minimum current above maximum at once lists both problems, nothing is written. Tests in `tests/test_settings_tabs.py`.
