# Android: the home and the dashboard

Decisions about the home: the day pages and their cards, the chart and the battery bar, the battery level, the trips and their advice, the one-off away periods, and the entries that change them.

What holds on every screen (top app bar, buttons, snackbars, dialogs, spoken names, saved state) is in [screens.md](screens.md); read it too.

### The page

- A day's page holds, in this order: the notice after a save, the stale warning, the refused-key warning and the notifications card (these four on the page in view only), the zone line, the day card, a trip advice card per trip, the grid advice, the solar chart, the Car battery card, the Trips card, the away list and the footer. Everything scrolls, nothing is pinned, so the page keeps its room at 200% on a small phone on its side (#335).
- Refresh is an icon in the top app bar beside a pull down the page; both keep the plan in view while a new one is made, and do nothing while a question is pending (#335).
- Coming back to the app asks again when the last answer, a plan or No forecast, is more than 15 minutes old; a Settings change asks at once and shows "Making the plan…" (#335).
- The stale warning is a polite live region, read when the forecast turns stale and when the home opens with one; a refresh that stays stale says nothing new (#335).
- A refused key gets a warning in the stale warning's place and style ("Open-Meteo refused the API key. Check it under Forecast in Settings."), one line per provider, also while a fallback or an older forecast gives a plan, and without a plan above "No forecast" (#361).

### The dashboard model

- The rules of the day card, the battery bar, the trip advice, the footer, the coming away periods and the trips list are the engine's `engine.dashboard` ([engine/decisions.md](../engine/decisions.md)); the app keeps only the words and the drawing (#401).
- `Dashboard` makes the engine's view once from the plan with all stored away periods and trips, so the view cannot disagree with the plan. After a trip or away change the view is made again at once from the plan shown, then the plan is made again with the shown plan kept in view (#404).
- `AppArchitectureTest` keeps `home/Dashboard.kt` free of Android, AndroidX and Compose (#404).

### Chart and battery bar

- Both are drawn on cards of a fixed ground, white in light and dark grey in dark, not the wallpaper's colours, so the tested contrast of the solar, grid and car colours holds (lines and marks 3:1, words 4.5:1). Both run left to right in every language (#336).
- For TalkBack each is one node with the role of an image and the desktop's sentence (`CHART_SPOKEN`, `BATTERY_SPOKEN_*`), which is also shown under the chart as its text equivalent; the drawing's own words are not read apart (#336).
- The time axis marks whole hours only ("15", or "3 PM"), every 3 hours, else 6, else 12, so no two labels meet, each within the plot; a narrow phone shows fewer hours (#336).
- On the day a trip with advice leaves, the level after the trip adds "after the trip" to the levels, the day card's battery chip and the bar's sentence, as text only; the wording with a charge is chosen by `BatteryLevels.chargesBeforeTrip` (#401).
- Without a charge before the trip the chip is read "Battery 50%, 30% after the trip" ("Accu 50%, 30% na de rit") (#377).
- The level after a trip of several days shows on its departure day; whether it should show on the day of return is open in #388 (#377).

### Day card

- A day with a window is unchanged by trips. Without one: the grid advice's day says so; the day a trip with advice leaves says "No charge window" without a why-line; a day the car is away on a stored trip says "The car is away on a trip, or there is too little solar power while it is home."; there is no partly-away wording (#377).
- Any other day without a window says one of two things, by the engine's reason: "Charging not needed" ("Laden niet nodig") alone, without a sentence; or "No charge window" with "There is too little solar power while the car is home." ("Er is te weinig zonnestroom terwijl de auto thuis is."). Neither has a battery chip in the day card; the Car battery card stays on every day, and today's note on an assumed level stays, also under "Charging not needed". The heading comes from the reason (`DashboardWords.heading`), as on the desktop (`DayMapper`), and the card with no figure has no empty row of chips (#460).

### Battery level

- The level is entered in whole percent, 0 to 100, on today's Car battery card through **Update battery level**, in a dialog that opens empty, so the owner reads the car rather than accepting the assumed level (#337).
- A decimal, text or a number outside 0 to 100 is refused with a line under the field, never clamped, read through a polite live region and not read again when repeated. Cancel closes without asking (#337).
- A saved level has the plan made again with the shown plan kept in view, even while a plan is being asked (#337).

### Trip advice

- A card per trip comes below the day card, in the engine's order, with no cap or merge. No plan, no advice (#377).
- The words are the desktop's, word for word in English and Dutch. On today's tab the day is "today", "tonight" (a start today at 18:00 or later, in the home's zone), "tomorrow" or the weekday; on another tab always the weekday; lower case in Dutch. Times are in the home's zone on the phone's clock, with no zone line, and a no-break space only around a window's dash (#377).
- The chip "Trip advice" ("Advies voor de rit") is in the source's colour pair, as the grid advice's; there is no glyph. Chip, title, plan and level are one heading node read as the desktop's sentence, with windows read "12:00 to 15:00" ("12:00 tot 15:00"); the shown words are hidden one by one, not cleared, so tests can see them (#377).
- **Adjust**, its own button named "Adjust the battery level" ("Aanpassen: accuniveau"), moves the pager to today without animation and opens the battery entry. It shows whenever a plan does and is not held while a trip change runs (#377).
- A stale plan shows its advice as planned; "now" and "tonight" are told from the plan's moment, not refreshed by a timer. With no level entered the card says "Assuming a battery level of N%." (#377).

### Trips card

- Each day's page lists the trips of that day (`DashboardView.tripsOn`), in the stored order, a trip under way or back in the last day included, without a mark; otherwise "No trip entered for this day." A trip beyond the forecast's days is not seen until its day has a tab, as on the desktop. Without a plan there is no Trips card, as on the desktop: by design (#375).
- A trip reads as the desktop's ("Away 07:30 – 18:00 · 300 km there and back", with the days when it returns on another day), on the phone's clock in whole km, spoken "Away from 07:30 to 18:00, 300 km there and back" (#375).
- Its two choices are checkboxes in whole toggleable rows; a tap saves at once and shows the new state while saving. A failed choice springs back with "Not saved: the trip could not be written." and no Undo; one that worked says nothing (#375).
- Rows are keyed by the trip, and a trip a choice changed keeps the key of the trip it was, so the focus stays on it (#375).
- Remove acts at once with Undo, as the away list's. After each saved change the work in the background is told, so a trip's charge start gets its alarm at once (#375).
- Two trips with the same departure share one told-state in `trip-notices.json`, as on the desktop: a known limit (#375).

### Trip entry

- **Add** ("Add trip", "Toevoegen: rit") is in the Trips card's heading row, **Change** ("Change the trip …", "Wijzigen: rit …") beside each Remove. The entry is a full-width dialog, at most 600 dp of content, with the title as heading, Close (×) in Up's place and Save in its top app bar; Back is Close, and a tap beside does not close it (#376).
- The four buttons "Departure day", "Departure time", "Return day", "Return time" ("Vertrekdag", "Vertrektijd", "Terugkomstdag", "Terugkomsttijd") go two to a row, under "Times in <zone>" when the zone is not the phone's; then the distance there and back and "I can charge at the destination" (#376).
- The distance field shows "km" (from `DISTANCE_UNIT`) only once it has the focus or a value, keeps its first 16 characters, and its keyboard's Done saves (#376).
- The date picker offers today up to today + 20 days, widened to the opened trip's days or Add's page day, with "Keyboard"/"Calendar" ("Toetsenbord"/"Kalender") to switch to typing; OK without a date keeps the day. It sits in a dialog no wider than the screen, the calendar scrolling sideways, because Material's `DatePickerDialog` is cut off on a 320 dp phone and scaling would draw days under 48 dp; its UTC-midnight millis never pass through a zone (#376).
- Change opens on the trip in the home's zone, to the minute, and keeps its "Not with this car" unseen; seconds are dropped at Save. A distance left as shown keeps the trip's exact distance, so a trip listed as 0 km still saves (#376).
- Save checks, with the clock at Save, the return after the departure (a departure in the spring-forward gap counts as the hour after), the return not passed, then the distance, read as set-up reads a number (else "Type the distance as a number, such as 120."); only the first problem shows, in a polite live region, with the focus on its control (#376).
- The reading order is title, Close, Save, then the fields; the focus starts on "Departure day". Save says "Saved: <trip>." and puts the focus on that trip's Change, else on Add, else on the title (#376).
- After a picker or "Discard changes?" the focus returns to the control that last had it; after a failed Save it goes to the title (#376).
- "Discard changes?" asks only when the trimmed draft differs from how the entry opened: "The trip you entered is not saved yet." (#376).
- The saved state keeps the draft, how the entry opened and the day it opened on, so the window and the day names stay the same after the system ended the app; an entry kept there that cannot be read is closed and forgotten (#376).

### Away list

- The "Car away" row ends with **Add**, spoken "Add away period" ("Toevoegen: afwezigheid"); each coming period has **Remove**, spoken with its period ("Remove Tomorrow, all day", "Verwijderen: …") (#338).
- Add opens a dialog for the page's day, named in its title ("Car away on Thursday, November 5"), on **All day**, with **Part of the day** ready at 09:00 to 17:00 (#338).
- A time opens Material's `TimePicker` on the phone's 12- or 24-hour clock, with a text button "Keyboard"/"Clock" ("Toetsenbord"/"Klok") in its content to switch to typing and back, the mode kept when the phone turns, because the `modeToggleButton` slot cannot hold it beside Cancel and OK at 200% or in Dutch (#338).
- Times are in the home's zone. Save refuses an end not after the start and a period already ended, under the end time, or under the choice for all day, in a polite live region. A day the clocks change is 23 or 25 hours all day; a time in the repeated hour is its first (#338).
- A failed save says "Not saved: the away time could not be written." ("Niet opgeslagen: de afwezige tijd kon niet worden geschreven.") with the English details (#338).
- Remove acts at once; its snackbar "Removed: …" has **Undo**, which adds the period back. A failed Remove says "Not removed: the away time could not be written." ("Niet verwijderd: de afwezige tijd kon niet worden geschreven."); failures carry the English details and stay until the next notice or Close. After a Remove the focus goes to the period before, or after, or the line that says there is none (#338).

### Changes and entries

- The trip, away and battery entries each run their changes one at a time on a `ChangeQueue` of their own; while one runs, a choice, Remove, Add or Save does nothing (#393).
- Away and trip changes return `Saved` or `Failed(detail)` and never throw; a failed change makes no new plan (#338).
- An Undo tapped while another change runs waits for it and for its outcome to show, then runs, with "Saved: …" and the focus on the restored row (#375).
- `EngineOnDispatcher` holds no lock for away and trip changes, as the engine's file changes are atomic (#382).
- The entries share one `SaveState` (`Idle`, `Saving`, `Failed(detail)`) and `EntryFlow` ("Discard changes?", the save, the refusals count); Settings keeps its own `SaveState` with `Saved` and `KeysNotSaved` (#393).
- An entry is `Idle` when it opens. While its save runs, edits are ignored, Discard and Cancel do nothing, and Save is held, not off, so it keeps the focus. A question kept in the saved state shows only with an entry that came back open (#393).
- A snackbar names a period or trip with days from the shown plan's today, not the clock; before any plan was shown, from the entry's zone and the clock at Save (#404).
