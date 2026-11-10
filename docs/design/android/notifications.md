# Android: notifications and the work in the background

Decisions about the notifications, their channels and permission, the rounds in the background, the alarm and WorkManager, and the engine's part in them.

### The engine's part

- A round with `STORED_ONLY`, used only by the alarm's and the boot receiver's rounds, never asks a provider and reads no API key: it plans from the primary's stored forecast while that is still useful, else the fallback's, and does not wait for a provider being asked (#359).
- Each provider's cache file keeps `refreshFailed`, cleared by its next answer, not by a settings save; every failed ask counts, a cool-down and a missing required setting included. A stored-only round tells such a forecast as stale, so nothing is announced while the forecast could not be refreshed (#359).
- `Engine.nextNoticeDue` gives the earliest moment a window not yet announced (its start minus the lead time) or a trip's charge start is due; moments at which the forecast would be stale or more than 6 hours old are left out, and so are the battery check and a short trip (#359).
- `keyRefusedBy` (a 401 or 403) names the providers that refused their key, also while a fallback answered or an older forecast is used, until the provider answers or the keys or settings are saved; a stored-only round never says so (#359).
- Only classes in the package `nl.enadeag.carchargeplanner.android.background` may make a round or ask for the next notice, through `EngineAccess`; `AppArchitectureTest` holds this (#359).

### Notifications

- Each of the five notices is posted with the tag `<kind>:<epoch second>`: the window kinds by the window's start, a trip's grid charge, battery check and short trip by the departure (#361).
- The window kinds and a trip's grid charge go to the channel `charge_windows`, time out at the window's end and alert once. The battery check and the short trip go to `battery_trip_checks` and time out at the departure; a battery check asked again replaces the first and alerts again. A notice whose timeout has come is not posted (#361).
- Both channels have default importance and category reminder; `problems` is low, without sound, its notification of category error with the fixed tag `problems`. The channels' names are put in the app's language at every start (#361).
- Every notification is `VISIBILITY_PRIVATE`: the lock screen shows "Charge window soon" for the window kinds and a trip's planned grid charge, and the title for the rest (#361).
- Taps go through an explicit, immutable `PendingIntent` to the non-exported alias `.NoticeTap`, so only the app's own intents can take a notification away or open the battery entry (#382).
- A tap, also on an action, takes the notification away (its tag is the intent's data, `notice:<tag>`), but not for an intent from the recent apps (#361).
- The battery check's tap and its action "Enter battery level" open the battery entry, only on the home or the home that comes next at a start; in set-up or Settings the tap only brings the app forward (#361).
- The words are the desktop's facts with "Tap" and the spoken span ("11:15 to 14:30"), made when posted, in the app's language and the phone's 12- or 24-hour clock, the weekday in the home's zone, a distance in whole km rounded up until #311 (#361).
- Nothing is posted while notifications are not allowed; a notification that cannot be posted is logged at warn with its kind only (#361).
- Trip and window times are logged at DEBUG only (#382).

### Problems notification

- An unusable or newer settings file, or a refused key, is a problem; a plan without one, or no settings file, ends an episode; no forecast without a refused key changes nothing (#361).
- An episode starts, with one notification, only on a round that asked the providers; a stored-only round neither starts nor ends one. The dashboard ends one after it shows the plan, so a slow disk does not hold the plan back (#361).

### Permission card

- The card shows on the page in view, after the save outcome and the warnings, while notifications are not allowed and it was not closed, and is looked at again on every `ON_START` (#361).
- "Turn on notifications" asks Android on Android 13 and later; after a refusal, or where they are blocked on an older phone, "Open notification settings" with "Android does not ask again. Turn notifications on there." The Dutch button is "Instellingen voor meldingen openen", as "Meldingsinstellingen" does not fit at 200% on a narrow phone (#361).
- × ("Close") closes the card for good. The card is read title, text, button, Close; after Close or a refusal the focus goes to its title (#361).

### Preferences on the phone

- Two DataStore files in `files/datastore/`: `settings` ("Wi-Fi only", which goes along in a device transfer) and `state` (the card, the refused question, the Problems episode, `missed_rounds`; left out of backup and transfer) (#361).
- A failed read gives the default; a file that cannot be parsed is replaced by an empty one, so a damaged `settings` file turns "Wi-Fi only" off (#361).

### Rounds in the background

- One `Mutex` per process keeps rounds apart: the hourly work's and the round now's ask the providers; the alarm's and the boot receiver's use the stored forecast only (#362).
- With notifications not allowed a round cancels the work and the alarm and makes no round; without a settings file it cancels them after the Problems episodes. Otherwise, after the episodes, it sets the alarm for the next notice, never sooner than 9 minutes from now, or cancels it when nothing is due (#362).
- A failure is logged by its kind only and ends the round as a success. A round without an outlook sets the alarm 9 minutes from now, so the alarm never stops coming; each further one in a row doubles the gap, 18, 36, then at most 60 minutes, and a round with an outlook resets it. The count is `missed_rounds` in the `state` store (#362).
- The alarm is `setAndAllowWhileIdle(RTC_WAKEUP)` with the fixed request code 362, so each alarm replaces the last. Its receiver, not exported, runs the round under `goAsync()` for at most 8 seconds, below the 10 Android gives a broadcast (#362).
- `BootReceiver`, exported for `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` only, runs the stored-only round within 8 seconds, then asks for a round now while the app is set up and notifications are allowed (#362).
- A lead under about 10 minutes is best effort: Android 12 and later may delay the alarm up to an hour while saving battery (#362).

### WorkManager

- The hourly work (`hourly-round`, 60 minutes with 15 of flex, `UPDATE`) and the round now (`round-now`) wait for a network, unmetered with "Wi-Fi only", never for the battery, and end as a success. Their names never change after a release, or old work is orphaned on phones (#362).
- While a round now only waits, asking again adds nothing; while one runs, exactly one more waits after it (`APPEND_OR_REPLACE`) (#362).
- The schedule is refreshed at every `onStart`, when notifications are allowed through the card, and after the settings file is written or set aside, changed API keys, a saved battery level, and a changed away period or trip. With ready settings and notifications allowed it schedules both; without either it cancels both with the alarm; an unusable file leaves them (#362).
- A "Wi-Fi only" change moves the hourly work and a waiting round now to the new network; a running one is left alone (#362).
- WorkManager's database in `no_backup/` is excluded by both backup rule files all the same, as the keychain's file is (#362).

### Permissions and manifest

- `POST_NOTIFICATIONS` is the one permission the notifications add (#361).
- `RECEIVE_BOOT_COMPLETED` is the one permission of the app's own for the background work; WorkManager's `WAKE_LOCK` and `ACCESS_NETWORK_STATE` stay, and its `FOREGROUND_SERVICE` and `SystemForegroundService` are removed, as the app never runs work in the foreground (#362).
- `MergedManifestTest` holds the release build's merged manifest to exactly those permissions and, as exported parts, only the activity, the boot receiver, and WorkManager's and profileinstaller's parts behind `BIND_JOB_SERVICE` and `DUMP` (#362).
