# What next? hub, House page, Away page

Status: done (not committed yet)
Type: feature
Blocked by: S3, S5

## Acceptance

- The hub: a card per option with title, what it does, what happens if skipped, a button, and a tick when done; Finish always available; returning to the hub after each option.
- House: yearly electricity use from the bill, converted to an average power (hours in a year; note about the car's use if already included), or "I don't know" (typical value).
- Away: weekday boxes with from and to times (the same rules as Settings: each ends after it starts the same day), or "the car is usually home"; the commuter line on the card.
- Skipped options leave typical values for the house and no away rules, and the recap says so.
- Tests: hub state, helpers, validation, the recap.

## Comments

- `app/setup/hub.py` (`HubPage`, id `next`, in the sequence before Finish; its Next reads "Review and finish", always available), `house.py` and `away.py` (opened from the hub, `in_sequence = False`), Qt-free helpers in `app/setup/home.py`. **Framework change**: Next on a page that is not in the sequence means "Done" and goes back to where it came from (the hub); the sequence is now Welcome, Location, Solar, Car, What next?, Finish (six steps).
- Hub: two cards (what it does, "If skipped: ...", the commuter line "If you drive to work, set this." on the away card, a button that becomes "Change" when done, a tick and a one-line status such as "0.50 kW, a typical value" or "Thu 08:00-17:00"). **A card is done when an answer was given on its page**, not merely opened: looking at a page and going back records nothing (tested).
- House: the year's use from the bill, divided by 8,760 hours (note says so); a box "My bill already includes charging the car" subtracts the car's yearly energy from what was said about the car (km a day x consumption x 365); never below nothing; a result over Settings' 20 kW is warned about and stops Done; "I don't know" keeps the typical 0.5 kW and counts as an answer. A value is stored only once something was typed (the box starts as "not entered").
- Away: "usually home" (clears the rules) or "away at set times each week" with the same rows as Settings (the `RuleRow` widget is reused), "Add a time", Remove; validation is Settings' (`validate`, "[away] each rule must end after it starts on the same day (no overnight rules)"), shown on the page, and Done waits. The choice "usually home" counts as an answer only once it was chosen.
- Skipping both leaves the typical house value and no rules; the recap says "(typical value)" and "the car is treated as home all day"; the Finish page's Change links now cover house and away too. Mutation-checked: Next from a page outside the sequence, the car's share, the away validation.
- Looked at in dark: the hub, house and away pages. The pseudo-language test walks the hub and its pages in six states.
