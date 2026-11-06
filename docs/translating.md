# Translating

This is for everyone who writes or changes the app's texts: developers, and translators who add a language. It covers both apps: the desktop app and the Android app keep their texts apart, as their screens and sentences differ, but use the same words and write them the same way. What each term means is in [CONTEXT.md](../CONTEXT.md); this page says which word each language uses for it and how the texts are written.

## The words

Each term in `CONTEXT.md` has one word per language, used everywhere the app shows it. The terms are not translated two ways. The Note column says how the word is used; the last column is the English wording to avoid, as in `CONTEXT.md`.

| English term | Dutch | Note | English words to avoid |
| --- | --- | --- | --- |
| Owner | je | the texts speak to the owner | User, customer, driver |
| Feed-in | teruglevering |  | Return, export |
| Self-consumption | eigen verbruik |  | Own use, direct use |
| Charge window | laadvenster | never a bare venster | Slot, charge time, window (on its own) |
| Charge current | laadstroom |  | Amps, charge speed |
| Charge power | laadvermogen |  | Charge speed, charge rate |
| Surplus | overschot |  | Excess, spare solar |
| Solar share | zonnestroom | as in "92% zonnestroom" | Sun share, sun (for solar power) |
| Baseload | basisverbruik |  | House use, standby use |
| Weekly away period | wekelijkse afwezigheid |  | Recurring away time, absence, trip |
| One-off away period | afwezig |  | Away day, absence, trip |
| Trip | rit |  | Journey, ride, away period (which has no distance) |
| Departure | vertrek |  | Deadline, start |
| Trip surcharge | ritopslag |  | Margin, buffer |
| Other driving | overige rijafstand |  | Distance per day, daily distance, average distance |
| Charging hours | laaduren |  | Awake hours, charge times |
| Charge limit | laadlimiet |  | Target, maximum (the target charge level is the app's setting) |
| Battery level | accuniveau |  | State of charge, SoC, charge |
| Assumed battery level | aangenomen accuniveau |  | Default level, estimated level, state of charge |
| Minimum charge level | minimaal laadniveau |  | Floor, reserve |
| Target charge level | doelniveau |  | Maximum, limit, goal |
| Planning margin | planningsmarge |  | Safety factor, buffer |
| Import tolerance | toegestane netstroom |  | Grid allowance, import limit |
| Announcement | melding |  | Reminder, alert |
| Battery check | controle van het accuniveau | as in "Controleer het accuniveau" | Battery question, level reminder |
| Enter battery level | Accuniveau invullen | the battery check's notification action only; the dashboard's button is Update battery level (Accuniveau bijwerken) | Update battery level (in a notification), set level |
| Notice | melding | the same word as for an announcement, as in "Get a notice before each charge window" ("Krijg een melding vóór elk laadvenster") | Alert, message, notification (the way a system shows a notice) |
| Trip start notice | – | not shown as a word: its title is the grid charge's, or Start charging now (Start nu met laden) | Trip reminder, departure alert |
| Trip short notice | – | not shown as a word: its title is Plan a charging stop (Plan een laadstop) | Range warning, low-range alert |
| Grid advice | advies: laden uit het net | a grid charge is laden uit het net | Grid warning, top-up, emergency charge |
| Set-up | instelhulp |  | Onboarding, configuration, wizard (the wizard is only the form set-up takes) |
| Not set up | niet ingesteld |  | Unconfigured, first run (first run is the moment, not the state) |
| Unusable config | niet ingesteld | with what to fix | Broken config, not set up (that is the state without a file) |
| Settings file | instellingenbestand |  | Config file, configuration, engine config |
| Panel array | panelengroep |  | Plane (the forecast services' word), string (an electrical term), roof |
| Typical value | gangbare waarde |  | Default, assumed value, guess |
| Planning preferences | planning |  | Planning settings, options |
| App settings | app-instellingen |  | Preferences, interface config |
| Place search | zoeken (de zoekdienst) |  | Geocoding, location lookup, address search |
| Charger phases | fasen |  | Single/three-phase connection (the home connection can have three phases while the charger uses one) |
| Forecast provider | voorspellingsdienst |  | Weather source, weather service, API |
| Primary provider | voorspellingsdienst |  | Main provider, default provider |
| Fallback provider | reservedienst |  | Backup provider, secondary source |
| Forecast step | stap |  | Slot, interval, sample |
| Forecast freshness | bijgewerkt, niet vernieuwd |  | Age, outdated, expired |
| Cool-down | dienst weer beschikbaar om |  | Rate limit (the provider's rule, not the wait), back-off, cooldown period |
| API key | API-sleutel |  | Secret, token, password, key (on its own) |
| Refused key | weigert de API-sleutel | as in "Open-Meteo refused the API key." ("Open-Meteo weigert de API-sleutel.") | Invalid key, wrong key, unauthorised |
| Secret setting | API-sleutel |  | Secret (on its own), password field |
| Keychain | sleutelbos |  | Keyring (only the Secret Service's own word), wallet, vault |
| Start at login | starten bij aanmelden |  | Autostart, run at boot, startup item |
| Supported / Beta / Untested | ondersteund, bèta, niet getest |  | Experimental, unofficial, verified |
| Dashboard | dashboard |  | Popup (only one of the places it appears), plan window, main screen |
| Engine | – | not shown in the app | Backend, server, daemon, core |
| Client | – | not shown in the app | Frontend, UI (a client is more than its screens) |
| Outlook | – | not shown in the app | Status, result, plan (a plan is only one kind of outlook) |
| Round | – | not shown in the app | Tick, cycle, refresh (a round need not ask a provider), poll |
| Hourly update | de update elk uur | the Android app's round while it is closed, as in the "Wi-Fi only" hint | Background refresh, sync, polling, hourly round (in texts) |
| Desktop app | de app |  | Tray app, the application (too vague) |
| Integration | koppeling |  | Plugin, connector, driver, recipe (a recipe is a deferred idea, not an integration) |
| Ability | – | not shown in the app | Capability, feature, sensor |
| Official route | – | not shown in the app | Official API (a maker can have an API that owners may not use), unofficial route |
| Local network (not encrypted) | lokaal netwerk (niet versleuteld) |  | LAN, insecure, http mode |
| Home Assistant | Home Assistant |  | HA, the hub |
| Wi-Fi only | Alleen wifi | the Android app's switch for the hourly update while the app is closed; Dutch writes "wifi" in lower case | WLAN only, unmetered only, Wifi (in English) |

## How the texts are written

- **The texts speak to the owner**, in Dutch as "je".
- **Numbers, times and day names come in already written** in the language's own way: a decimal comma or point, a 24- or 12-hour clock, "zondag" or "Sunday". Only the words around them are the translator's, including the unit and the space before it: `{0} kW`, but `{0}%` without one, as English and Dutch write it.
- **A text with a count has one form per plural**; each app's part below says how it is written. Both cover languages with a singular and a plural, such as English and Dutch.
- **Product and provider names** (Open-Meteo, Forecast.Solar, "Car charge planner") and the provider's credit stay as they are.
- **What the engine says about a settings file or a provider** (the "details") stays English, like its log messages. The sentence around it is translated and says that the details are in English, such as "Details (in het Engels): {0}".
- **A translation of a term uses the word in the table above**, so the dashboard, Settings, set-up and notifications agree.
- **Each app's texts are checked by its own tests**, and a language needs both apps: adding one to only one app leaves the other showing English.

## Desktop texts

Every text the app shows is translatable, and a language is one file. The texts are in `desktop/src/main/resources/nl/enadeag/carchargeplanner/desktop/text/`, one file per language: `messages_en.properties` for English, `messages_nl.properties` for Dutch. They are Java resource bundles in UTF-8 (type `é` and `ü` as they are, no `\u00e9` escapes). Each line is a key, the same in every language, and its text:

```properties
DAY_SET = Set {0} A ({1} kW)
```

The keys are the constants of `TextKey.java`, which says, per key, what each `{0}`, `{1}` holds. `{0}`, `{1}` are filled in by the app: keep each one, but put it wherever your language wants it, as word order is yours.

A text with a count has one form per plural, with a `choice` place: `{0,choice,1#{0} day|1<{0} days}`. A language with more plural forms (Polish, Russian) would need ICU4J, which the app does not use yet.

An apostrophe is written twice, also in a text without `{0}`: `charger''s app` shows as "charger's app". A single one would hide the text after it (the texts are `java.text.MessageFormat` patterns).

**A new text gets a constant in `TextKey.java` and a key in every language's file in the same pull request.** A text you cannot translate yourself may stay English there for a translator to pick up, but the key must be in every file.

### Adding a language to the desktop app

1. **Add the language to the app.** In `desktop/text/Language.java`, add a constant with the language's ISO 639 code, the locale whose numbers, times and day names it uses, and its name in itself, which the language list shows, such as `GERMAN("de", Locale.GERMANY, "Deutsch")`. Language codes are two letters: `de`, `fr`, `pt`. Add the code to `--include-locales` in `src/packaging/jlink.args` too, so the packaged app has the language's day names and numbers.

2. **Start the file.** Copy `messages_en.properties` to `messages_<code>.properties` and translate every text after the `=`, following the rules above and the words in the table.

3. **Check it.** `./mvnw verify` runs `MessagesBundleTest`, which fails when a language misses a key or has one too many, when a text has other `{n}` places than the English one, or when it has a single apostrophe.

4. **Try it.** The app follows the system's language: on Linux `LANGUAGE`, then `LC_ALL`, `LC_MESSAGES` and `LANG` (the plain `C` locale means English); on Windows the language of the user's display. To choose one, write `{"language": "de"}` to `app.json` beside `engine.json` (`~/.config/car-charge-planner/`, Windows `%APPDATA%\car-charge-planner\`); `"system"` follows the system again. The file is read when the app starts; choosing a language in Settings (App) and saving switches the running app at once.

   ```bash
   LANGUAGE=nl ./mvnw -pl :car-charge-planner-desktop -am compile exec:exec -Dapp.args="--config=docs/example-engine.json"
   ```

   Look at the dashboard, set-up, Settings, the tray icon's tooltip and menu, and a notification: long words are the usual trouble.

5. **Send it.** Commit the `.properties` file and the line in `Language.java`, and open a pull request. A language needs the Android app's texts too, see [Adding a language to the Android app](#adding-a-language-to-the-android-app), or that app shows English.

## Android texts

The Android app's texts are Android string resources, in `android/app/src/main/res/`: `values/strings.xml` is English, which any language without its own file falls back to, and `values-nl/strings.xml` is Dutch. The names are the same in every file. `app_name` is the one text that is not translated (`translatable="false"`): it is the product's name. The debug build alone overrides it in `android/app/src/debug/res/values/strings.xml` as "Car charge planner (debug)", also untranslated, so the two builds differ in the launcher; a new language leaves it out as before.

```xml
<!-- %1$s the start time; %2$d the number of days -->
<string name="window_from">From %1$s, for %2$d days</string>
```

- **A comment above a text says what each placeholder holds**, in the English file; the translations keep to it.
- **Placeholders are always positional**: `%1$s`, `%2$d`, `%3$.1f`, never a bare `%s`. Keep every one, with the same number and type as in English, but put it where your language wants it: `%2$s` may come before `%1$s`. Numbers and times that are already written for the phone come in as `%s`; a number the app has not written yet is `%d` or `%.1f`.
- **Escaping** differs from the desktop's: an apostrophe is `\'` (the desktop writes `''`), a literal percent sign is `%%`, a new line is `\n`, and a text that starts with `@` or `?` starts with `\@` or `\?`. An unescaped `'` or `"` is an error, and so is a text that is not valid XML: write `&amp;` and `&lt;`. Type `é` and `ü` as they are.
- **A text with a count is a `<plurals>`**, with the forms the language has: English and Dutch both have `one` and `other`. Give every form its own sentence, also `one` when it has no number to show ("One day"), and give both languages the same forms:

  ```xml
  <plurals name="days">
    <item quantity="one">%1$d day</item>
    <item quantity="other">%1$d days</item>
  </plurals>
  ```

  The desktop writes the same as one text, `{0,choice,1#{0} day|1<{0} days}`.
- **What the engine says about a settings file or a provider** (the "details") stays English: one text around it, such as `Details (in het Engels): %1$s`.
- **Texts must work with a screen reader (TalkBack)**: write them so they make sense when read aloud, with no meaning only in colour, symbols or position, and do not rely on a text's length for the layout: a translation can be twice as long.

**A new text goes into every language's file in the same pull request.** Lint fails the build on a text that is missing in a language or has no English one (`MissingTranslation`, `ExtraTranslation`), on a placeholder that does not match (`StringFormatMatches`, `StringFormatInvalid`, `StringFormatCount`) and on a plural without a form its language needs or with one it does not (`MissingQuantity`, `UnusedQuantity`, `ImpliedQuantity`). A text you cannot translate yourself must still be in the file: put the English text there for a translator to pick up. `StringsResourcesTest` checks the same on the files themselves, and also that every placeholder is positional, that nothing is wrongly escaped, and that the languages the app offers are English and the `values-*` folders.

**The app's language is chosen in Android's settings, not in the app** (#274; the generated list agreed in PR #316, 2026-11-01). From Android 13 the owner can give the app a language of its own there, from the list in the app's `android:localeConfig`; the build generates that list (`generateLocaleConfig`) from English and the `values-*` folders, and `localeFilters`, from the same folders, keeps the languages that libraries bring out of the app. The app has no language menu of its own; on an older phone it follows the phone's language.

### The words of the Android screens

The set-up screens, the screen of a settings file the app cannot use, the dashboard, Settings and the notifications use these words beside the terms above, in every language the same way. The buttons and headings are short, as on a phone; the desktop's wording of the same step may differ.

| English | Dutch | Note |
| --- | --- | --- |
| Next | Volgende | set-up's button to the next step |
| Back | Vorige | set-up's button to the step before |
| Finish | Voltooien | set-up's last button, which saves |
| Step 3 of 7 | Stap 3 van 7 | read with each step's heading |
| I don't know: use typical values | Weet ik niet: gangbare waarden gebruiken | fills in typical values |
| Enter coordinates instead | Liever de coördinaten invullen | opens the latitude and longitude |
| Chosen: Utrecht, Netherlands | Gekozen: Utrecht, Nederland | the first line of the card shown after a place is chosen from the search's results |
| Coordinates: 52.0907, 5.1214 | Coördinaten: 52,0907; 5,1214 | the card's line with what the choice filled in, the numbers as the fields write them; Dutch separates them with a semicolon, as it writes a decimal comma; the card's last line is "Time zone: Europe/Amsterdam" ("Tijdzone: Europe/Amsterdam"), as under Advanced |
| Advanced | Geavanceerd | opens the time zone |
| Check again | Opnieuw controleren | asks the forecast provider once more |
| Try again | Opnieuw proberen | repeats what failed |
| Set up again | Opnieuw instellen | for an unusable config |
| Update the app | Werk de app bij | for a settings file of a newer version |
| Settings file cannot be used | Niet ingesteld | the heading of the screen for an unusable config |
| Away until, period 2 | Afwezig tot, afwezigheid 2 | a weekly away period's field, named with the period's number |
| Add period | Afwezigheid toevoegen | adds a weekly away period |
| Today, Tomorrow | Vandaag, Morgen | a day's tab and title; the other days by their weekday |
| Refresh | Vernieuwen | makes the plan again; the top app bar's action |
| Charge window, No charge window | Laadvenster, Geen laadvenster | what a day holds, under its name |
| Grid charge needed | Laden uit het net nodig | a day without a window that the grid advice is about |
| Set 10 A (2.3 kW) | Stel 10 A in (2,3 kW) | what to set for a day's charge window |
| about 7.9 kWh | ongeveer 7,9 kWh | what the window charges |
| 92% solar, 0.4 kWh from the grid | 92% zonnestroom, 0,4 kWh uit het net | a day's figures by source, each in its colour |
| Battery 50 → 74% | Accu 50 → 74% | the battery level at the start of the day and after charging; TalkBack reads "Battery from 50% to 74%" |
| 11:15 to 14:30 | 11:15 tot 14:30 | what TalkBack reads for a charge window's 11:15 – 14:30 |
| Solar power is not enough to stay above the minimum: see the grid advice on Today. | Zonnestroom is niet genoeg om boven het minimum te blijven: zie het advies over laden uit het net bij Vandaag. | why a day the grid advice is about has no charge window; "Today" is today's tab |
| The car needs no charging, or there is too little solar power for it. | De auto hoeft niet te laden, of er is te weinig zonnestroom voor. | why a day has no charge window |
| Also 16:00 – 17:00 at 6 A, 18:00 – 19:00 at 8 A. | Ook 16:00 – 17:00 met 6 A, 18:00 – 19:00 met 8 A. | a day's later charge windows, in its note |
| Grid advice | Advies: laden uit het net | the heading of today's grid advice |
| Charge 8.4 kWh from the grid: Sunday 09:00 – 15:10. | Laad 8,4 kWh uit het net: zondag 09:00 – 15:10. | the grid advice's title; "before Sunday" when no day has room |
| Without it the battery ends Sunday at 17%, below the minimum of 30%. | Anders eindigt de accu zondag op 17%, onder het minimum van 30%. | why the grid advice; "runs empty" when it would end at 0% or below |
| Set 16 A (3.7 kW), and a charge limit of 80% in the car or the charger. | Stel 16 A in (3,7 kW), en een laadlimiet van 80% in de auto of de laadpaal. | what to set for the grid charge; otherwise "No day before it has room for a grid charge." |
| Solar, House, Charge window | Zonnestroom, Huis, Laadvenster | the solar chart's legend; "House" is the legend's short word for the baseload, as on the desktop, not "House use" |
| now | nu | on the chart's line at the moment the plan was made, today only |
| Today, expected solar power: peak 4.2 kW around 13:00; the house uses about 0.4 kW. Charging: 13:00 to 15:00. Now 09:00. | Vandaag, verwachte zonnestroom: piek 4,2 kW rond 13:00; het huis gebruikt ongeveer 0,4 kW. Laden: 13:00 tot 15:00. Nu 09:00. | the solar chart in words: what TalkBack reads for it, and the line under it; "expected solar power: none." on a day without sun |
| Car battery | Accu van de auto | the heading of the battery bar |
| min 30%, target 80% | min. 30%, doel 80% | the marks on the battery bar |
| Car battery 50% now, 74% after charging; minimum 30%, target 80%. | Accu van de auto nu 50%, na het laden 74%; minimum 30%, doel 80%. | what TalkBack reads for the battery bar; on another day "50% at the start of Sunday" ("50% aan het begin van zondag") |
| The battery level is assumed. Use Update battery level under it to enter the level the car shows. | Het accuniveau is aangenomen. Gebruik Accuniveau bijwerken eronder om in te vullen wat de auto aangeeft. | added to what TalkBack reads for today's battery bar while the plan starts from the assumed battery level; "Update battery level" is the button under the bar |
| The plan assumes the battery is at 40%. Use Update battery level under Car battery. | De planning gaat ervan uit dat de accu op 40% staat. Gebruik Accuniveau bijwerken onder Accu van de auto. | today's note while the level is assumed; "Update battery level" is the button on the card headed "Car battery" |
| Update battery level | Accuniveau bijwerken | today's battery card's button, which opens the entry of the level |
| Battery level, Battery level in percent | Accuniveau, Accuniveau in procent | the entry's title and its field's label |
| A whole number from 0 to 100, such as 64. | Een heel getal van 0 tot 100, zoals 64. | the hint under the entry's field; a decimal gets "Type a whole number, without decimals.", a number outside the range "Type a level from 0 to 100." |
| Type a level from 0 to 100. | Typ een niveau van 0 tot 100. | under the entry's field after Save, for a whole number below 0 or above 100 |
| Battery level saved. | Accuniveau opgeslagen. | after the entry's Save, in a snackbar |
| Not saved: the battery level could not be written. | Niet opgeslagen: het accuniveau kon niet worden geschreven. | in the entry after its Save failed, above the English details |
| The battery level you typed is not saved yet. | Het accuniveau dat je typte is nog niet opgeslagen. | under "Discard changes?", asked by Back with a level typed |
| Car away | Afwezig | the heading of the one-off away periods |
| None entered for the coming days. | Niets ingevuld voor de komende dagen. | no one-off away period to come |
| Add | Toevoegen | the button in the "Car away" heading row, which opens the entry of a one-off away period on the page's day |
| Add away period | Afwezig toevoegen | what TalkBack and Voice Access call that button; it starts with the word on the button in English, and holds it in Dutch |
| Remove, Remove Tomorrow, all day | Verwijderen, Morgen, hele dag verwijderen | a one-off away period's button, and what TalkBack reads for it: the period as the list shows it |
| Car away on Thursday, November 5 | Afwezig op donderdag 5 november | the entry's title, with the day's weekday and date as the phone writes them in a sentence |
| All day, Part of the day | Hele dag, Deel van de dag | the entry's choice |
| Away from, Away until | Afwezig vanaf, Afwezig tot | part of the day's two times, each a button that opens the time picker, and the picker's title |
| OK | OK | confirms the time picker's time |
| Keyboard, Clock | Toetsenbord, Klok | the time picker's button under the dial or the typed time, named for what it switches to: typing the time, or the dial |
| The end must be after the start. | Het einde moet na het begin liggen. | under the end time after Save |
| That time has passed already. | Die tijd is al voorbij. | under the end time, or the choice for all day, after Save |
| Saved: Tomorrow, all day. | Opgeslagen: Morgen, hele dag. | in a snackbar after the entry's Save or Undo |
| Removed: Tomorrow, all day. | Verwijderd: Morgen, hele dag. | in a snackbar after Remove, with Undo |
| Undo | Ongedaan maken | the snackbar's action after Remove, which adds the period back |
| Not saved: the away time could not be written. | Niet opgeslagen: de afwezige tijd kon niet worden geschreven. | in the entry after its Save failed, and in a snackbar after Undo failed, with the English details; a one-off period is "afwezig", never "afwezigheid" |
| Not removed: the away time could not be written. | Niet verwijderd: de afwezige tijd kon niet worden geschreven. | in a snackbar after Remove failed, with the English details |
| The away time you entered is not saved yet. | De afwezige tijd die je invulde is nog niet opgeslagen. | under "Discard changes?", asked by Back in the entry with the choice or a time changed |
| updated 10:51, not refreshed since 10:51 | bijgewerkt om 10:51, niet vernieuwd sinds 10:51 | the footer, after the provider's credit, which stays as it is |
| provider busy until 11:30 | dienst weer beschikbaar om 11:30 | the footer, during a cool-down |
| The forecast was not refreshed since 10:51. The plan uses that forecast. | De voorspelling is niet vernieuwd sinds 10:51. De planning gebruikt die voorspelling. | the warning above a plan on a stale forecast |
| Open-Meteo refused the API key. Check it under Forecast in Settings. | Open-Meteo weigert de API-sleutel. Controleer de sleutel bij Voorspelling in Instellingen. | the warning in the same place when a provider refused its API key, also while another forecast is used and above "No forecast" when there is no plan; "Forecast" is the Settings page |
| Get a notice before each charge window | Krijg een melding vóór elk laadvenster | the title of the dashboard's card while notifications are not allowed; under it "The app tells you shortly before a charge window starts, so you can set your charger in time. You can change this later under Notifications in Settings." ("De app meldt het kort voordat een laadvenster begint, zodat je je laadpaal op tijd kunt instellen. Dat kun je later wijzigen bij Meldingen in Instellingen.") |
| Turn on notifications | Meldingen aanzetten | the card's button, which asks Android |
| Open notification settings | Instellingen voor meldingen openen | the card's button after a refusal, with "Android does not ask again. Turn notifications on there." ("Android vraagt het niet nog een keer. Zet meldingen daar aan."); not "Meldingsinstellingen openen", a word too long for a narrow phone at 200%, where it breaks in two |
| No forecast | Geen voorspelling | the heading when there is no plan |
| Settings | Instellingen | the home's top app bar action, and the list's title |
| Solar panels | Zonnepanelen | a Settings page: every panel array |
| Location | Locatie | a Settings page |
| Car and house | Auto en huis | a Settings page |
| Charger | Laadpaal | a Settings page |
| Forecast | Voorspelling | a Settings page: the providers and API keys; also a day's solar chart |
| Away | Afwezigheid | a Settings page: the weekly away periods only |
| Planning | Planning | a Settings page: the planning preferences |
| Save | Opslaan | a page's Save, in its top app bar |
| Saved. | Opgeslagen. | after a page's Save, in a snackbar |
| Not saved. | Niet opgeslagen. | under the top app bar after a page's Save failed; why is at the top of the page |
| Back | Terug | what TalkBack says for the top app bar's Up arrow (set-up's own button stays Vorige) |
| Saving… | Opslaan… | Save while it saves, on a Settings page and in the battery level's and the one-off away period's entries |
| Discard changes? | Wijzigingen weggooien? | asked by Back or Up on a Settings page with edits, and by Back, or a tap beside it, in the battery level's entry with a level typed and in the one-off away period's entry with something changed |
| Keep editing | Verder bewerken | stays on the page, or in the entry |
| Discard | Weggooien | throws the page's edits, or the typed level, away |
| Close | Sluiten | closes a save's outcome shown on the home, the notification card's ×, and the close button of a failure or Undo snackbar |
| Array 2, east roof | Panelengroep 2, oostdak | a panel array's heading, by place and name |
| Tilt, array 2 | Helling, panelengroep 2 | a panel array's field or button, named with its place |
| Add an array | Panelengroep toevoegen | |
| A system needs at least one panel array | Een systeem heeft minstens één panelengroep nodig | why the last array's Remove is off |
| Check connection to Open-Meteo | Verbinding met Open-Meteo testen | asks the provider once, with what was typed |
| Show, Hide | Tonen, Verbergen | an API key's mask |
| None | Geen | no fallback provider; no weekly away period |
| Notifications | Meldingen | Settings' row before the licences', "Opens Android's settings for this app" ("Opent de instellingen van Android voor deze app"); a notification is a "melding" everywhere, as the announcement is |
| Wi-Fi only | Alleen wifi | the Forecast page's switch for the hourly update while the app is closed; Dutch writes "wifi" in lower case, as the Groene Boekje does, and the hint says "or another network without a data limit" ("of een ander netwerk zonder datalimiet") |
| Open source licences | Opensourcelicenties | Settings' last row, and the title of the read-only screen it opens; the licence texts and their titles stay English |
| Source code: … | Broncode: … | the repository's address on that screen, as plain text |
| Expanded, Collapsed | Uitgevouwen, Ingevouwen | what TalkBack says of a licence's row: whether its text shows under it |
| Reading the licences… | De licenties worden gelezen… | while a licence text, or the list of them, is read |
| This text could not be read. | Deze tekst kon niet worden gelezen. | a licence text that is missing |
| The licences could not be read. | De licenties konden niet worden gelezen. | the list of the licences that is missing |

### The words of the notifications

The notifications say the desktop's facts in the dashboard's words, with "Tap" ("Tik") where the desktop says "Click", a window's times as TalkBack reads them ("11:15 to 14:30", "11:15 tot 14:30"), and a trip by the weekday and time of its departure.

| English | Dutch | Note |
| --- | --- | --- |
| Charge window 11:15 to 14:30 | Laadvenster 11:15 tot 14:30 | a solar window's title; its text starts with what to set ("Set the charge current to 10 A (2.3 kW) in your charger's app.", "Stel in de app van je laadpaal de laadstroom in op 10 A (2,3 kW).") and ends "About 7.9 kWh, 92% solar." ("Ongeveer 7,9 kWh, 92% zonnestroom.") |
| Grid charge window 09:00 to 15:10 | Laden uit het net 09:00 tot 15:10 | the grid charge's title, also a trip's planned grid charge's |
| Start charging now | Start nu met laden | a trip's grid charge that cannot wait |
| Check the battery level | Controleer het accuniveau | the battery check's title; its text ends "Tap to enter the level the car shows." ("Tik om het accuniveau in te vullen dat de auto aangeeft.") |
| Enter battery level | Accuniveau invullen | the battery check's one action, which opens the entry of the level; a notification's word only, as the dashboard's button is Update battery level (Accuniveau bijwerken) |
| Plan a charging stop | Plan een laadstop | a short trip's title; the distance in km, "12 km short" ("12 km tekort") |
| Forecast not updated | Voorspelling niet vernieuwd | the Problems notification's title, over "The forecast could not be updated. Open the app for details." ("De voorspelling kon niet worden vernieuwd. Open de app voor details.") |
| Charge window soon | Laadvenster binnenkort | what a locked screen shows of a charge window's, a grid charge's and a trip's planned grid charge's notification; the others show their title, also Start charging now |
| Charge windows, Battery and trip checks, Problems | Laadvensters, Controles van accuniveau en ritten, Problemen | the channels' names in Android's settings, each with a description of one sentence |

`place_search_language` is no sentence: it is the two-letter code of the file's own language (`en`, `nl`), which the place search is asked to answer in, so the names of places come in the language the app shows.

### Trying the pseudo-locales

The debug build has two made-up languages, `en-XA` (English with accents and extra length, to see where a text is cut off or a screen has a fixed size) and `ar-XB` (mirrored, right to left, to see the layout flip). An English text that shows without accents in `en-XA` is not a resource but written into the code. They are never in the release build, which offers English and the `values-*` languages only. To pick one on Android 13 and later, open the app's own language (Settings, Apps, Car charge planner, Language): a debug build lists the pseudo-locales there. On an older phone, turn on Developer options and pick one as the phone's language; Android lists them there only with Developer options on. Always try a language on a debug build. (These steps are not yet tried on a device.)

### Adding a language to the Android app

1. **Start the file.** Create `android/app/src/main/res/values-<code>/strings.xml` with the language's two-letter ISO 639 code (`values-de`), copying `values/strings.xml` and translating every text (the comments above the texts stay in English, for the next translator), following the rules above and the words in the tables. Leave `app_name` out, and set `place_search_language` to the language's code.

2. **Nothing else lists the language.** The app's language list (what Android offers as the app's own language from Android 13) and the languages kept from libraries come from the `values-*` folders, in `android/app/build.gradle.kts`. Only a folder named after a two-letter code that Java knows counts: use the current codes (`values-id`, `values-he`, `values-yi`, not Android's old `values-in`, `values-iw`, `values-ji`). A regional variant (`values-pt-rBR`) or a language with only a three-letter code is not supported yet: its texts would not be offered.

3. **Check it.** `cd android && ./gradlew check` runs Lint and `StringsResourcesTest`.

4. **Try it.** On a debug build, choose the language in Android's settings for the app (Android 13 and later: Settings, Apps, the app, Language) or as the phone's language, and look at every screen: long words are the usual trouble. The app has no language menu of its own.

5. **Send it.** Commit the `values-<code>` folder, and open a pull request. A language needs the desktop's texts too, see [Adding a language to the desktop app](#adding-a-language-to-the-desktop-app), or the other app shows English.
