---
status: accepted
---

# The engine says what a setting may be, for both apps

The desktop app's Settings once had ranges of its own for each number (a battery of 1 to 200 kWh, a household use of up to 480 kWh a day, a shortest charge window of up to 1440 minutes) and brought a value outside them into the range, also a value it loaded from the settings file. The Android app checks against the engine's rules only (#313). So a file that both apps load, such as one with a 250 kWh battery edited by hand, was cut to 200 on the desktop by a save of anything, while Android kept it. #410 gave the engine bounds for the numbers that can hang the planner and promised this record; #317 made both apps follow them.

## The decision

- **The engine is the one place that says what a setting may be.** Each bound is a public constant beside the plain check in its record (`Car.MAX_USABLE_BATTERY_KWH`, `PlanningPreferences.MARGIN_ABOVE`, `Percent.MAX`), and the record refuses a value outside it with an `InvalidSetting` that names the setting and `OUT_OF_RANGE`. A percentage the owner sets is made with `Percent.of(setting, value)`, which refuses the same way. No app has a range of its own.
- **A setting without a bound in the engine has none in an app.** The household use, the import tolerance, the shortest charge window and the notification lead time have no highest value: none of them can hang the planner (#410).
- **The apps refuse, they never cut.** A value the engine refuses is an error under its field on Save or Next, naming the limit in the owner's unit and number format; a value the engine allows shows and saves as it is. A limit converted to other units is named rounded inward, so a value the text names is always accepted (621 mi for 1000 km).
- **What stays with each app** is how a field shows and steps a number: its decimals, its step, its keypad, and "usually …" in its hint. The desktop's arrows stop at the engine's bound.

## Consequences

- A new bound is one change in the engine; both apps' fields and texts follow it. Each app maps a setting to its field's unit in one place (`Limit.of`), from the engine's constants.
- A settings file within the engine's rules is never changed by opening and saving Settings, in either app.
- The desktop's fields accept any text: one that is not a number, or a decimal in a whole-number field, is an error under the field as on Android, not the value shown before.
