# Accessibility

The dashboard, Settings and set-up are made to be used with a keyboard only (on the desktop), with large fonts and scaling, and in light and dark. This page says what the app offers.

**Screen readers are not supported.** Earlier versions were built and checked for Narrator, NVDA and TalkBack. That is no longer kept up: new screens are not made or checked for a screen reader, and nothing is read out as a requirement. The code that was built only for screen readers is being removed ([#435](https://github.com/Enadeag/car-charge-planner/issues/435)). On Linux, Orca never could read the app, as JavaFX, the toolkit the app is built with, has no AT-SPI support.

## What the app offers

- **Large fonts and scaling.** The app follows the system's display scaling on Linux and Windows. At 150 and 200% nothing is cut off; Settings and set-up are never larger than the screen, and their pages scroll. On Windows the app also follows **Text size** (Settings, Accessibility): the text in every window grows by as much, also when you change it while the app runs; Settings and set-up open as much larger. On Linux, GNOME's **Large Text** (text scaling factor) and KDE's forced font DPI reach the app through the screen's DPI, which makes the whole window larger, as display scaling does. On Android the app follows the system's font size up to its largest: nothing is cut off at 200%, also with the keyboard open.
- **Contrast.** Every text reaches WCAG AA (4.5:1), and every glyph, chart line and focus ring 3:1, in light and in dark.
- **Colour is never the only sign.** The source colours always come with a glyph or a word, and a warning has a sign and a sentence as well as its colour.
- **A label on every control.** Every button, field and choice has a visible label or words of its own.
- **Keyboard on the desktop.** Tab and Shift+Tab reach every control in reading order, and the control with the focus has a ring around it. Enter saves or goes on (Save, Next, an entry's Save), Escape cancels or closes (Close, Cancel, an entry's Cancel, the popup); while Settings or set-up asks whether to throw away your changes, Escape keeps them. The arrow keys move between the day tabs, the categories of Settings, and along the solar chart. On Windows, Win+B and the arrow keys reach the tray icon: Enter opens the dashboard, and the menu key opens its menu.

<p>
<picture><source media="(prefers-color-scheme: dark)" srcset="screenshots/java/dashboard-200-dark.png"><img alt="The dashboard at 200%, with the focus ring on the battery's Update button." src="screenshots/java/dashboard-200-light.png" width="300"></picture>
<picture><source media="(prefers-color-scheme: dark)" srcset="screenshots/java/settings-200-dark.png"><img alt="Settings at 200%, on the Charger page, with the focus ring on the Charge power at home field." src="screenshots/java/settings-200-light.png" width="500"></picture>
</p>

## Android

The Android app is not released yet. Every control is drawn at least 48 by 48 dp, buttons too, which Material draws at 40 dp, and each row of Settings' list at least 56 dp high. Each screen is one column of at most about 600 dp that scrolls, and a top app bar's title wraps onto more lines with the bar growing, so nothing is cut off at 200% font size. On the dashboard each day's page scrolls under the tabs, and the solar chart takes the column's width, with fewer hours on its time axis when the text is large or the phone is narrow, so its labels never overlap. The chart and the battery bar are drawn in fixed colours on a ground of their own, whatever the wallpaper's: their lines and marks reach 3:1 and their words 4.5:1 on it, in light and in dark, and each sign has its word in the legend or beside it. The tests of the screens check on every pull request, at twice the font size, that every control is drawn at least 48 dp in both directions and can be scrolled to, and that every page scrolls far enough to show its last control whole.

### Check on a phone before an Android release

The tests show what the app draws, not what a person sees. Before each Android release the maintainer checks these on a real phone, in light and dark, at the largest font size, and with a keyboard:

- **Large fonts:** the set-up, the dashboard, the trip entry, the dialogs and Settings at the largest font size and on a small phone (320 dp), also on its side and with the keyboard open: nothing is cut off, the date and time pickers reach all their parts, and the keyboard does not cover the field being typed in.
- **Contrast and colour:** the cards, the chart, the battery bar, the chip's colours and the icons of Settings' list on the wallpaper's colours, in light and in dark; every colour comes with a word or sign.
- **Keyboard:** with a keyboard, Tab reaches every control, a held or busy control keeps the focus through a save, and Enter and Escape do what the buttons do.
- **A notification's tap** (a tap goes through the app's own alias, `.NoticeTap`, so a forged intent opens nothing): tap a battery check and its "Enter battery level" action with the app open on the dashboard, in the background, and from the recent apps; each reaches the one open app, not a second copy of it (one Back leaves the app), opens the entry of the battery level with the focus on its field, and takes the notification away.
- **Not about access, on the same phone** (#347, #362): typing on the Galaxy S25 gives one warning and no crash; an alarm comes, late at most an hour, while the phone saves battery; Samsung's path of the note on #279; a notice from the background is in the phone's language.

If something in the floor above does not work for you, please open an issue with your system.
