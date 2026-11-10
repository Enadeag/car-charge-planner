# Trips: decisions

Decisions about trips entered by hand ([ADR 0009](../../adr/0009-trips-and-calendar.md), layer 1) that the ADR and the code alone do not explain. Each says what holds now and ends with the issue that settled it. What the terms mean (trip, departure, charging hours, battery check) is in `CONTEXT.md`.

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

- The start of a trip's grid charge is told once per trip, like an announcement of a grid charge: within the notification lead time before a planned start, and, for a charge that has to start now, from the lead time before its start until its end, also outside the charging hours when the plan says so (#231).
- A trip's grid charge stays out of the windows the planner keeps as announced (`announced.json`), so telling it never makes the planner plan a second grid charge (#231).
- A trip that even a full battery cannot make is told once per trip, within the charging hours, from 19:00 on the day before the departure; it names how many km are missing and says to plan a charging stop (#231).
- The three trip notices are kept per trip in `trip-notices.json`, so a restart tells nothing again, and a trip's entry goes a day after its departure (#231).
- Like the announcements, nothing about a trip is told while the forecast could not be refreshed or is more than 6 hours old (#231).
