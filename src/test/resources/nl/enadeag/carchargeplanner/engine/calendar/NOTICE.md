# Calendars

The tests read these calendars so they never touch the network. They are **not recordings**: they are made by hand in the shape each service documents for its export, with made-up events, places and coordinates, and are to be replaced by exports of test calendars made with each service.

- `google_export.ics`: Google Calendar's secret iCal address, with `X-WR-TIMEZONE` and a `VTIMEZONE` for Europe/Amsterdam, single events in UTC, a weekly repeat across the clock change of 25 October 2026 with an exception date, a cancelled and a moved repeat, an all-day and a multi-day event, an event without a location and one after the days the tests cover.
- `outlook_export.ics`: a calendar published from Outlook.com, with Windows time zone names (`W. Europe Standard Time`, and Outlook's own `UTC`), Outlook's folded UIDs and `X-MICROSOFT-CDO-*` fields, a weekly repeat across the clock change with an exception date and a moved repeat, a two-day all-day event and an online meeting.
- `nextcloud_export.ics`: a Nextcloud calendar exported through its public link (SabreDAV), with a daily repeat with an exception date and a moved repeat, an event with `GEO`, one with Apple Calendar's `X-APPLE-STRUCTURED-LOCATION` on the night of the clock change, and an all-day event without a location.
