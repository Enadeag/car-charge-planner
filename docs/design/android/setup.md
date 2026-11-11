# Android: set-up

Decisions about the first-run set-up: its steps, the check, Finish, the time zone and the place search, which Settings' Location page shares.

What holds on every screen (top app bar, buttons, snackbars, dialogs, spoken names, saved state) is in [screens.md](screens.md); read it too.

### Steps and Back

- Set-up is seven steps, without a navigation library: Welcome, Location, Panels, Car, Charger, More (the house and the weekly away periods) and Check (#314).
- Back on Welcome closes the app; on a later step it goes one step back and keeps what was entered. Back while the check runs cancels it; Back while Finish saves does nothing until the save ends (#314).
- Welcome says that the app asks the provider about once an hour while it is closed (#361).
- Set-up forgets its step and texts when the home opens and before a new set-up, because its forms' saved-state keys are the same as Settings' (#315).

### Check and Finish

- The check runs on entering the Check step, on coming back to it, and after the system ended the app; a failure shows **Check again** and the English details, and Finish stays on (#314).
- While saving, Finish is off and shows progress. Saved opens the dashboard without a message; Failed keeps the English details and **Try again**, in a live region. A newer settings file opens the "update the app" screen; a value the engine refuses goes back to the first step with a refused value, with a line that says why (#314).

### Panels

- The step asks one array, empty but for the typical losses: the number of panels, the direction and the tilt (shared exposed drop-downs, "Choose…" until chosen), the year placed and the power per panel, the kWp they give together, and the losses and the name under the shared fold row "More settings", which opens by itself while one of them shows a problem. There is no "I don't know" on this step (#448).
- A number with decimals is refused as for the amps; an empty count is refused with the count's own sentence. The power's placeholder, the typical power of the year, shows only while the field has the focus, as Material draws a placeholder beside a label; its hint says the same at all times (#448).

### Time zone

- The zone is chosen under **Advanced**, the shared fold row with an arrow, like "More settings" (#448), from the list of zone ids only; "Enter coordinates instead" on the same step stays a check box until #449. A chosen place sets it; typed coordinates put it back to the phone's zone unless the owner chose one by hand. The phone's zone is read when used, not at start (#314).

### Place search

- The search runs on the Search button or the keyboard's Search key, never per keystroke, in the app's language (`place_search_language`, `en` or `nl`), and shows at most 8 places; labels are told apart only among those shown (#314).
- The typed text is at most 100 characters and survives the system ending the app; the results do not (#314).
- Results are list rows with dividers, at least 48 dp high, whose labels wrap; a label is cut at 100 characters, so the field, the card and the saved state hold the same text (#343).
- After a choice the field shows the place's label and stays editable, the coordinates toggle does not open, and a card under the Search button says "Chosen: <place>", "Coordinates: <lat>, <lon>" and "Time zone: <zone>", the form's zone. Dutch separates the coordinates with a semicolon ("Coördinaten: 52,0907; 5,1214"), because a comma is ambiguous next to a decimal comma (#343).
- The card shows only after a choice in this session, stays while the field is edited and while searching again, and goes, with the field emptied, when typed coordinates forget the place. In Settings it leaves out the coordinates, stays after Save and goes when the page is left (#343).
- TalkBack's focus moves to the card once per choice, so turning the phone or coming back to the step does not move it again (#343).

### Language and words

- A language change keeps what was typed: the view model is told the new language in each `onCreate`, and numbers keep their decimal mark, read either way (#314).
- The heading for an unusable settings file is "Settings file cannot be used", in Dutch "Niet ingesteld". In Dutch a weekly away period is "afwezigheid" throughout, its controls named with its number ("Afwezig tot, afwezigheid 2") (#314).
