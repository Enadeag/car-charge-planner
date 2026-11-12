# Translating

This is for everyone who writes or changes the app's texts: developers, and translators who add a language. It covers both apps: the desktop app and the Android app keep their texts apart, as their screens and sentences differ, but use the same words and write them the same way. What each term means is in [CONTEXT.md](../CONTEXT.md); this page says which word each language uses for it and how the texts are written.

The Android app's texts, and how to add a language to that app, are in [translating-android.md](translating-android.md).

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
| Baseload | verbruik van het huis | shown as "Household use" ("Verbruik van het huis"), asked in kWh per day ("kWh per dag"), in both apps; "basisverbruik" is not shown | House use, standby use |
| Weekly away period | wekelijkse afwezigheid |  | Recurring away time, absence, trip |
| One-off away period | afwezig |  | Away day, absence, trip |
| Trip | rit |  | Journey, ride, away period (which has no distance) |
| Departure | vertrek |  | Deadline, start |
| Trip surcharge | ritopslag |  | Margin, buffer |
| Daily distance | dagelijkse afstand | the car's field, without a unit in its name, as the desktop can show miles; its hint "How far you drive on a normal day, for example for shopping, school or sport. Trips you enter separately do not count." ("Hoeveel je op een gewone dag rijdt, bijvoorbeeld voor boodschappen, school of sport. Ritten die je apart invoert, tellen niet mee.") | Other driving, distance per day, average distance |
| Charging hours | laaduren |  | Awake hours, charge times |
| Notification lead time | melding vooraf | the Planning page's drop-down: At the start, 5 minutes ahead, 10, 15, 30 or 60 minutes ahead (Bij het begin, 5 minuten ervoor, …); a stored lead that is none of them is a choice of its own as long as it is chosen, such as "20 minutes ahead" ("20 minuten ervoor"); its hint "How long before a charge window starts you are told." ("Hoe lang voordat een laadvenster begint je een melding krijgt.") |  |
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
| Number of panels | aantal panelen | a panel array's field |  |
| Power per panel | vermogen per paneel | in Wp, a panel array's field; its hint "Wp, as on the invoice or the panel." ("Wp, staat op de factuur of het paneel.") |  |
| Year placed | jaar van plaatsing | a panel array's field, a number |  |
| Direction | richting | a panel array's drop-down: North, North-east, East, South-east, South, South-west, West, North-west (Noord, Noordoost, Oost, Zuidoost, Zuid, Zuidwest, West, Noordwest) |  |
| Tilt | helling | a panel array's drop-down: Flat, Pitched, Steep, Upright (Plat, Schuin, Steil, Rechtop) |  |
| Together about 3.6 kWp | Samen ongeveer 3,6 kWp | below a panel array's fields: the size its panels give |  |
| Now set: 4.2 kWp. Enter the number of panels to replace this. | Nu ingesteld: 4,2 kWp. Vul het aantal panelen in om dit te vervangen. | above the fields of an array entered before as a size in kWp |  |
| More settings | Meer instellingen | the row that folds the seldom changed settings away, such as a panel array's losses and name, the trip surcharge and the household use on the Car and house page, the charger's phases, currents, voltage and efficiency, and the planning margin, the import tolerance and the minimum charge window length on the Planning page |  |
| Charge power at home | laadvermogen thuis | the Charger page's drop-down: 3.7 kW, 7.4 kW, 11 kW, 22 kW, Other (3,7 kW, 7,4 kW, 11 kW, 22 kW, Anders), each one text of its own; its hint "The power the car charges with at home: the lower of what the charger and the car can take." ("Het vermogen waarmee de auto thuis laadt: het laagste van wat de laadpaal en de auto aankunnen.") | Charger power, charger size, wallbox rating |
| Choose… | Kies… | what a drop-down shows before a choice |  |
| Typical value | gangbare waarde | what I don't know: use typical values (Weet ik niet: gangbare waarden gebruiken) fills in; no field says that its value is typical | Default, assumed value, guess |
| Planning preferences | planning |  | Planning settings, options |
| App settings | app-instellingen |  | Preferences, interface config |
| Place search | zoeken (de zoekdienst) |  | Geocoding, location lookup, address search |
| Place name | plaatsnaam | the name of the place chosen, kept with the coordinates | Address, location name, city |
| Chosen place: Utrecht, Netherlands | Gekozen plaats: Utrecht, Nederland | the Location page's top line, in both apps, in set-up and Settings; the name stays as it was chosen, in the language of that moment | Chosen: (the card it replaces) |
| No place chosen yet | Nog geen plaats gekozen | the same line while the settings hold no place name |  |
| Search to choose another place | Zoek om een andere plaats te kiezen | under the place search while a place is chosen |  |
| Coordinates and time zone | Coördinaten en tijdzone | the fold under the place search with the latitude, the longitude and the time zone; the same words title them for a provider without a place search | Advanced, Enter coordinates instead (what it replaces on Android) |
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
- **A text with a count has one form per plural**; each app's part says how it is written: the desktop's below, the Android app's in [translating-android.md](translating-android.md). Both cover languages with a singular and a plural, such as English and Dutch.
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

5. **Send it.** Commit the `.properties` file and the line in `Language.java`, and open a pull request. A language needs the Android app's texts too, see [Adding a language to the Android app](translating-android.md#adding-a-language-to-the-android-app), or that app shows English.
