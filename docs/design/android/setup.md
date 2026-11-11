# Android: set-up

Decisions about the first-run set-up: its steps, the check, Finish, the time zone and the place search, which Settings' Location page shares.

What holds on every screen (top app bar, buttons, snackbars, dialogs, spoken names, saved state) is in [screens.md](screens.md); read it too.

### Steps and Back

- Set-up is seven steps, without a navigation library: Welcome, Location, Panels, Car, Charger, Away periods (the weekly away periods) and Check (#314, #450).
- Set-up does not ask for the house's use: it saves the draft's typical 0.5 kW, marked as typical. A set-up that comes back from saved state with a house's use typed in it, from before #450, forgets it (#450).
- Back on Welcome closes the app; on a later step it goes one step back and keeps what was entered. Back while the check runs cancels it; Back while Finish saves does nothing until the save ends (#314).
- Welcome says that the app asks the provider about once an hour while it is closed (#361).
- Set-up forgets its step and texts when the home opens and before a new set-up, because its forms' saved-state keys are the same as Settings' (#315).

### Check and Finish

- The check runs on entering the Check step, on coming back to it, and after the system ended the app; a failure shows **Check again** and the English details, and Finish stays on (#314).
- While saving, Finish is off and shows progress. Saved opens the dashboard without a message; Failed keeps the English details and **Try again**, in a live region. A newer settings file opens the "update the app" screen; a value the engine refuses goes back to the first step with a refused value, with a line that says why (#314).

### Panels

- The step asks one array, empty but for the typical losses: the number of panels, the direction and the tilt (shared exposed drop-downs, "Choose…" until chosen), the year placed and the power per panel, the kWp they give together, and the losses and the name under the shared fold row "More settings", which opens by itself while one of them shows a problem. There is no "I don't know" on this step (#448).
- A number with decimals is refused as for the amps; an empty count is refused with the count's own sentence. The power's placeholder, the typical power of the year, shows only while the field has the focus, as Material draws a placeholder beside a label; its hint says the same at all times (#448).

### Car

- The step asks the battery, the consumption and the daily distance, with "km" after it, then "I don't know", which fills in those three; the trip surcharge is under the shared fold row "More settings", closed until a problem in it opens it and keeps it open, as on the Car and house page in Settings, which shows the same step with the household use added under the same fold. The daily distance has the keyboard's Done while the fold is closed, the trip surcharge while it is open (#452).

### Away periods

- Each weekly away period's from and until are `TimeButton`s with the time on the phone's 12- or 24-hour clock, each opening Material's time picker with its Keyboard and Clock button, as the one-off away period's (#338); a new period still starts at 08:00–17:00. Next checks them by the engine's rule, an end after the start, and shows the problem under the until button, which carries it and takes the focus. The step and Settings' Away page share this; set-up has no Planning page, so the charging hours are chosen in Settings only (#452).

### Charger

- The step starts with the charge power at home, a shared exposed drop-down with "Choose…" until chosen and its hint under it; the phases, the currents, the voltage, the efficiency and "I don't know" sit under the shared fold row "More settings", closed until "Other" is chosen or a folded field shows a problem, which opens it and keeps it open as on the Car and house page. Next refuses a step without a choice with "Choose one." at the field. The rule itself is in [engine/decisions.md](../engine/decisions.md#the-charge-power-at-home) (#451).
- The choice is kept in the saved state as a text (`charger.text.POWER`: empty, or the name of the engine's `ChargePowerChoice`, such as `KW_11` or `OTHER`), so a set-up that comes back after the system ended the app keeps it; kept state that no longer fits, such as one left by an older build of the app, counts as no choice (#451).

### Time zone

- The zone is chosen under **Coordinates and time zone**, the shared fold row, from the list of zone ids only. A chosen place sets it; typed coordinates put it back to the phone's zone unless the owner chose one by hand. The phone's zone is read when used, not at start (#314, #449).

### Place search

- The search runs on the Search button or the keyboard's Search key, never per keystroke, in the app's language (`place_search_language`, `en` or `nl`), and shows at most 8 places; labels are told apart only among those shown (#314).
- The typed text is at most 100 characters and survives the system ending the app; the results do not (#314).
- Results are list rows with dividers, at least 48 dp high, whose labels wrap in full (#343).
- The step, and Settings' Location page, the same, start with "Chosen place: <place>" or "No place chosen yet", then the search, then the latitude, the longitude and the time zone under the fold **Coordinates and time zone** (#449).
- After a choice the field is empty, for a new search only, and its hint starts with "Search to choose another place". The place's name is its label as chosen, cut at 100 characters (`PlaceName.fromLabel`); it stays at the top while searching again, and a failed search leaves it and the fold alone (#449).
- The name holds while the fields show the place's coordinates at their four decimals: typing a digit and putting it back keeps it, a real difference shows "No place chosen yet" at once and is saved without a name. The name and the place's coordinates are kept in the saved state as texts (#449).
- The fold is closed with a place and open without one; a new choice closes it; a problem in a coordinate opens it and keeps it open, also after the problem is put right, and a press on its row does nothing meanwhile. Its state survives turning the phone (`rememberSaveable`) (#449).
- Without a place search the page shows the line that the provider has none and only the coordinates and the time zone, unfolded, without the top line (#449).
- TalkBack's focus moves to the top line once per choice, so turning the phone or coming back to the step does not move it again (#343, #449).

### Language and words

- A language change keeps what was typed: the view model is told the new language in each `onCreate`, and numbers keep their decimal mark, read either way (#314).
- The heading for an unusable settings file is "Settings file cannot be used", in Dutch "Niet ingesteld". In Dutch a weekly away period is "afwezigheid" throughout, its controls named with its number ("Afwezig tot, afwezigheid 2") (#314).
