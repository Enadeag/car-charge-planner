# Trips: decisions

Decisions about trips entered by hand ([ADR 0009](../../adr/0009-trips-and-calendar.md), layer 1) that the ADR and the code alone do not explain. Each says what holds now and ends with the issue that settled it. What the terms mean (trip, departure, charging hours, battery check) is in `CONTEXT.md`.

### Bounds and damaged rows

- A trip's distance is more than 0 and at most 20,000 km there and back, well above a drive across a continent and back; it lasts at most 366 days, as the planner goes through a trip day by day; it departs from 1970-01-01T00:00Z and returns before 10000-01-01T00:00Z (#410).
- Both apps' entries refuse a distance beyond 20,000 km before they make the trip, at the distance, with a text that names the engine's limit: "The distance must be at most 20,000 km." on the desktop, in miles rounded down to a whole mile (12,427 mi), so a trip of the distance named is never refused; Android has km only (#410).
- A return more than 366 days after the departure, counted in real hours so that across a clock change it may differ by an hour from the days and times shown, is refused at the return with "A trip can last at most 366 days.", the number from the engine; so is a return on 1 January 10000 (UTC) or later, which only a trip edited by hand to return at the end of 9999, or a clock put forward to then, can reach (#410).
- A departure before 1970, which only a device whose clock was put back can offer, is refused with "That trip is over already.", as such a trip is over: on the desktop at the departure's time, on Android at the return, where that text shows already, so the trip entry's layout does not change; the range of moments has no text of its own (#410).
- A row of `trips.json` or `away.json` outside these bounds is a damaged row, as any row the engine cannot read: left out when planning, and while it is in the file no entry of that file can be added, changed or removed. Neither app shows such rows (#410).

### Entering a trip or a one-off away period

- Both apps hold what the owner enters to the engine's entry rules, `TripEntryRules` and `OneOffAwayEntryRules` beside the records: a pure check of the values the app read, the moment of Save and the home's zone, which names the first problem as a `TripProblem` or a `OneOffAwayProblem`. A trip's are, in this order: a departure before 1970, a return not after the departure, a return at or before now, more than 366 days, a distance of 0 or less, a distance that is no number (the app hands NaN for a text it cannot read), more than 20,000 km, and a return in the year 10000. An away period's: an end not after the start, as typed or as moments after a time the clock skips, then an end at or before now. The records themselves keep accepting a trip or period that has ended, so a stored one still loads (#390).
- Reading the typed times and distance, miles, and the field and words of each problem stay with each app; so does the limit named in miles, rounded down (#390).
- The entries open on the engine's defaults: an away period part of the day from 09:00 to 17:00, a new trip from 08:00 to 18:00, with the 21 days from today offered for its departure and return, widened to a changed trip's own days (#390).
- A changed trip whose distance the owner left as it first showed keeps its stored distance in km on both apps, before it is checked, so a trip too short to show more than 0 is saved as it was (#390).

### Grid charges and the charging hours

- Every grid charge starts within the charging hours, a trip's and the everyday grid advice's alike, and may run on after them (#227).
- A trip's grid charge is placed as late as possible, finished at least an hour before the departure; one whose latest start falls at night moves to the latest start the evening before that still finishes in time (#227).
- Both ends of the charging hours count as within them, an end before the start spans midnight, and an end equal to the start is refused (#227).
- When a trip's grid charge has to start now and now is outside the charging hours, the plan waits for their next start when that still delivers as much before the departure; otherwise the charge starts at once, marked as outside the charging hours (`TripGridCharge.Start.NOW_OUTSIDE_CHARGING_HOURS`), and the advice and the notice say so (#227).

### The battery check

- The owner is asked when a trip is tight: it needs a grid charge, or is short, or leaves less than 5 percentage points above what it needs while solar power did not fill the battery to the target; and only when no battery level was entered in the last 12 hours (#231).
- It is asked within the charging hours, from their start on the day before the departure, or on the day of the trip's grid charge when that is earlier (#231).
- It is asked once per trip, and again only when the trip has come to need at least 10 percentage points more from the grid than when it was asked (#231).

### Trip notices

- The start of a trip's grid charge is told once per trip, like an announcement of a grid charge: within the notification lead time before a planned start (with a lead of 0, from its start for 10 minutes at most, never past its end), and, for a charge that has to start now, from the lead time before its start until its end, also outside the charging hours when the plan says so (#231, #360).
- A trip's grid charge stays out of the windows the planner keeps as announced (`announced.json`), so telling it never makes the planner plan a second grid charge (#231).
- A trip that even a full battery cannot make is told once per trip, within the charging hours, from 19:00 on the day before the departure; it names how many km are missing and says to plan a charging stop (#231).
- The three trip notices are kept per trip in `trip-notices.json`, so a restart tells nothing again, and a trip's entry goes a day after its departure (#231).
- Like the announcements, nothing about a trip is told while the forecast could not be refreshed or is more than 6 hours old (#231).
