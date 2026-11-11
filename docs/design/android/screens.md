# Android: every screen

Decisions that hold on every screen of the app: the top app bar, buttons, snackbars, dialogs and spoken names.

### Top app bar and buttons

- Up is Material's `ArrowBack`, mirrored right to left, and the home's Settings is Material's gear, named "Settings" (#315).
- The top app bar's title wraps without a line limit and the bar grows with it, so nothing is cut off at 200% (#335).
- Every button is drawn at least 48 dp high, over Material's 40 dp, through the app's own button wrappers (#315).
- A control that is off while a change runs stays focusable: it is marked unavailable for TalkBack, drawn as off, and ignores a press, because Compose takes the focus from a disabled control (#375).
- A button's spoken name starts with its visible word, in Dutch too, for Voice Access: "Verwijderen: rit …", "Verwijderen: …", "Toevoegen: afwezigheid" (#375).

### Drop-downs and folding

- A choice from a fixed list is the shared `ChoiceMenu` (`ui/components/ChoiceMenu.kt`), Material's exposed drop-down menu, which shows "Choose…" until a choice is made; a few choices that all fit on one line may stay radio buttons (`ChoiceRow`) (#448).
- Settings that are seldom changed fold under the shared `Fold` row (`ui/components/Fold.kt`), "▸ More settings" or "Coordinates and time zone": one button with an arrow, read as "Collapsed" or "Expanded" (#448, #449).

### Snackbars

- `AppSnackbar` draws the app's own **Close** (`action_close`) on every snackbar that asks for a close button, the Undo one too, never Material's "Dismiss"; `BarScaffold` pads the page's bottom by the shown snackbar's height, so the last row and the footer scroll above it (#338).

### Dialogs

- An entry dialog's title is a heading; Save shows its progress, and nothing closes while it saves. A failed save is read at once with the English details. In the battery and away dialogs Back or a tap beside, with something changed, asks "Discard changes?" and Cancel closes without asking; when a dialog closes the focus goes back to the button that opened it, or to the title when that button is not on the page in view (#338).
- The trip entry never closes on a tap beside it; Back is its Close (#376).

### Saved state

- Each screen's saved state ends with it: Settings forgets its page, its question and its texts when it closes, and set-up its step and texts when the home opens (#315).
- An entry restored from the saved state that can no longer be read (a time-zone update removed its zone) is closed and forgotten (#338).
