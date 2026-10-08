# Units: metric and imperial

Status: done (not committed yet)
Type: task
Blocked by: I1

Spec section 5.

## Acceptance

- `[interface] units = "metric" | "imperial"` (default metric), validated.
- A Qt-free `app/units.py`: kilometres and miles; kWh/100 km and mi/kWh by **reciprocal**; exact round trips for the stored metric values.
- The Settings field machinery supports a transform (not only a scale) so the `car.km_per_day` and `car.consumption_kwh_per_100km` fields show and accept the chosen units, with unit suffixes and limits converted; an untouched field still keeps its exact stored value.
- A units choice in Settings (Interface tab); a pure function that suggests imperial for the regions US, GB, LR and MM from the system locale and metric otherwise (used by the wizard).
- Tests: conversions both ways, the reciprocal at the edges, exactness, Settings display and save in both systems.

## Comments

- `app/units.py` (Qt-free): km and miles; consumption by the reciprocal (18 kWh/100 km = 3.45 mi/kWh, `62.1371 / x`, an error for none at all); labels, decimals (2 for mi/kWh), steps and **converted limits** (the reciprocal turns them round: 5-60 kWh/100 km becomes 1.0-12.5 mi/kWh); `suggest_system(environ, qt_locale_name)` for US, GB, LR, MM from LC_ALL, then LC_MEASUREMENT (the category about units), then LANG, then Qt's locale, metric otherwise.
- `[interface] units = "metric" | "imperial"` (default metric, validated; an old config means metric). Stored values stay metric.
- **Settings**: `FieldSpec.quantity` marks the two fields (`car.km_per_day`, `car.consumption_kwh_per_100km`); the field machinery shows and accepts the chosen system, with suffix, limits, decimals and step converted. Choosing the system on the Interface tab changes the boxes **at once**, without saving, and keeps what they stand for.
- **No drift**: an untouched field keeps its exact stored value in both systems; and an edited value is remembered exactly across a unit switch (found by a test: 100 km shows as 62.1 mi and came back as 99.9 km until the exact value was kept). Mutation-checked.
- Unit symbols are not translated (`units.suffix` rather than an f-string, which the source rules would rightly flag).
- Reset tab shows the defaults in the chosen system. The wizard (S8) will use `suggest_system` and the same field machinery.
