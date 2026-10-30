---
status: accepted; layer 2 withdrawn by ADR 0010
---

# Trips: the plan makes sure the car can make them, and the calendar can supply them

Until now the plan has assumed the same distance every day, the "Distance per day" setting, and has looked for the sunniest charge windows; it advises a grid charge only when the battery would end a day below the minimum charge level. That works for an even week, not for a known long drive: with 100 km of range left and a 300 km round trip tomorrow, the useful advice is "charge to 85% before you leave", not "charge tomorrow afternoon, when the sun shines". This records how the plan takes trips into account (#42, #81) and how a calendar supplies them (#36). It was settled in one design session in October 2026.

## The decision

**Layer 1, the plan (v0.5.0).**

- **A trip** is a drive from home and back with a departure, a return and a distance. While on a trip the car is away. An away period stays time only, without a distance. Trips are entered by hand on the dashboard ("Add trip") or, in layer 2, read from the calendar.
- **The trip comes first.** At the departure the battery must hold the trip's energy plus enough to be back at the minimum charge level on return. Solar power before the departure is used first; only the shortfall comes from the grid. After that, the usual preference for solar power applies again.
- **Above the target level for a trip.** A trip may have the battery charged above the target charge level, up to 100%, and the part above the target is planned as late as possible before the departure, as a battery that stands full wears faster. When even 100% is not enough, the app says how many km are missing and to plan a charging stop.
- **The grid part as late as possible:** finished at least one hour before the departure, so earlier solar power counts first; when the time left is shorter, the advice becomes "charge now".
- **Charging hours.** The app does not control the charger yet (#16), so the owner starts each charge. A new planning setting, "Start charging between" (07:00–22:00 unless the owner changes it), keeps every start within hours a person is awake; a charge may run on through the night, with the limit set in the car or the charger: "Start tonight at 21:00 and set the charge limit in the car to 85%". It falls away for a charger the app controls, once that exists.
- **Energy of a trip:** the distance at the average consumption, plus a trip surcharge (15% unless the owner changes it), as long drives on motorways and in winter use more. No model of speed or temperature: there are no data to check one against.
- **Other driving.** The "Distance per day" setting stays, renamed to say it is the short drives that are not trips, such as shopping; trips come on top of it.
- **The battery level before a trip.** When a trip needs a grid charge or is tight, a notification within the charging hours asks whether the assumed battery level is right, and the plan is made again with the level entered. The advice always shows the level it assumes.
- **Away periods and trips add up:** the car is away when any of them says so, and overlapping times merge. Only trips have a distance.
- **On the dashboard** the main card names the trip, the charge plan for it and the assumed level; the day's tab shows the trip with its origin and two choices, "Not with this car" (the trip does not count) and "I can charge at the destination" (only the way there counts), kept per occurrence or for all occurrences of a repeating event. Notifications: the battery level question, "Start charging now for your trip", and the evening before, a trip that even a full battery cannot make.

**Layer 2, the calendar as a source of trips (v0.6.0).**

- **Two consents, both needed.** One to read the calendar, which stays on the computer; one to send the events' locations and the home location to a route service to find the places, distances and travel times. Each is a checkbox that starts unticked, with what happens in plain words, stored with its date and the version of its text; the app asks again when what is sent changes. Withdrawing either one, or disconnecting the calendar, wipes everything the calendar brought in.
- **Sources:** a calendar's secret iCal address (an `.ics` link over https, kept in the keychain) or an `.ics` file; one reader for both. No CalDAV, no desktop calendar services and no Google or Microsoft sign-in for now.
- **Which events count:** those whose location is a physical place the route service finds. Links, Teams, Zoom, Meet and "online" are filtered out on the computer first and never sent. A location with several matches takes the one closest to home and shows "(chosen)" with a way to correct it; one that is not found is not a trip, and the day shows it with a search to give the address. A correction counts for that occurrence, or for all occurrences of a repeating event. When the event itself holds coordinates (its `GEO` field, or Apple Calendar's own location field), those are used and nothing about that place is sent.
- **The route service is Geoapify**, with servers in the EU and the owner's own free API key in the keychain, behind a contract like the forecast providers', so another service can be added. The consent says that Geoapify keeps each request for at most 24 hours. Its free plan asks for a credit, "Powered by Geoapify · © OpenStreetMap contributors": it is shown on the Calendar page, in About and with each trip from the calendar. When a route cannot be had, the straight-line distance times 1.3 stands in.
- **From events to trips:** the car leaves at the start minus the travel time and a margin (15 minutes unless the owner changes it) and is back at the end plus both. Between two events on one day it goes home when the gap holds the drive home and back, the margins and at least the minimum charge window length; otherwise it drives on, and the day is one round trip. An all-day or multi-day event is one trip, from 09:00 on the first day to 18:00 on the last unless the owner changes these times, there and back unless the owner can charge at the destination; driving there does not count.
- **How far ahead:** only the days the plan covers, so as few locations as possible are sent. The calendar is read again with each new plan, at most every 15 minutes, at once when the file changes, and with "Read calendar now".
- **What is kept:** per occurrence of an event (its UID and the occurrence's date), the distance, the travel time, the name of the place found and the location text they were found for; no coordinates and no routes. A changed location text means a new lookup; a changed time does not. The entry goes once the event has passed or is gone from the calendar, and a new home location drops them all. The owner's choices and corrections per event are kept until it has passed. Titles and descriptions stay in memory only. The file is private, like the app's other files.
- **Setting it up** is a "Calendar" page in Settings, not part of set-up: the consents, the source, the API key, a test that lists the trips the calendar would give, and the margin, the surcharge and the all-day times.

## Why the calendar may send data

The app's rule has been that nothing about the owner leaves the computer except the home location for the forecast (docs/privacy.md). Telling a physical place from "Meeting room 3", and knowing how far it is, needs a lookup outside the computer. The owner wanted that, but only after explicit consent, so the exception exists only while both consents are given, says exactly what is sent and to whom, and can be withdrawn with everything wiped. An EU service with the owner's own key was chosen over OpenStreetMap's volunteer servers, whose terms do not allow an app to rely on them.

## Considered options

- **The calendar gives away periods only, without distances:** private and simple, but it leaves the plan blind to the drives that empty the battery, which is the point.
- **Reading a distance written in the event:** nothing sent, but owners would have to type it into every event.
- **A dedicated calendar or a keyword in the title:** predictable, but the owner wanted the calendar they already use, with real places as the rule.
- **Every event with any location, filtered on the computer only:** "Meeting room 3" and "Canteen" would count as trips.
- **Solar first and only a warning before a trip:** keeps the old behaviour, but the advice the owner needs is whether the car makes the drive.
- **Charging the grid part as early as possible, or always at night:** safer against an early departure, or cheaper with a night tariff, but it leaves later solar power unused and the battery full for longer; tariffs are a subject of their own.
- **The calendar first, the planner later:** the planner is the larger risk, and with trips entered by hand it can be used and tested without any outside service.

## Consequences

- ADR 0006 placed calendar sync beside the integrations "as it gives away periods rather than readings". It now gives trips, and it is the app's first integration that sends more than the home location; it still does not wait for the shared integration core (#11).
- The planner gets deadlines: energy needed by a departure, above the target level when a trip asks for it, and the charging hours for every grid charge.
- New settings: charging hours (planning), trip surcharge, travel margin and all-day times (calendar); "Distance per day" is renamed for other driving.
- docs/privacy.md gets a section on the calendar when layer 2 ships, and the README's privacy summary names the exception.
- Until the app controls a charger, the advice asks the owner to set a charge limit in the car or the charger.

## Note, 2026-10-27

Two parts of layer 2 changed before it was built (#234, #235). **Geoapify replaces OpenRouteService.** HeiGIT's terms of use list sending personal data as prohibited conduct, without an exception for an owner who sends their own data with their own key, and the event locations and the home location are personal data. TomTom forbids storing its results beyond the caching its answers allow, and Mapbox, Google, LocationIQ, Stadia Maps, GraphHopper and Jawg fail on a credit card, a short storage limit or a free plan that excludes desktop apps. Geoapify's terms have neither ban, its servers are in the EU, and it keeps a request for at most 24 hours against HeiGIT's 7 days. **The results are kept per event, not per location text for 90 days:** a distance and a travel time that go once the event has passed keep far less about the owner, for a handful of extra lookups that stay well within the free plan.

## Note, 2026-10-30

**Layer 2 is withdrawn** by [ADR 0010](0010-android-app.md). The calendar trips as built are not what they should be, and the feature needs a new design before it comes back; none of it was released, so the calendar reader, the route service and ical4j are removed (#261). Layer 1, trips entered by hand, still holds.
