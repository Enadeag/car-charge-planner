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
| Secret setting | API-sleutel |  | Secret (on its own), password field |
| Keychain | sleutelbos |  | Keyring (only the Secret Service's own word), wallet, vault |
| Start at login | starten bij aanmelden |  | Autostart, run at boot, startup item |
| Supported / Beta / Untested | ondersteund, bèta, niet getest |  | Experimental, unofficial, verified |
| Dashboard | dashboard |  | Popup (only one of the places it appears), plan window, main screen |
| Engine | – | not shown in the app | Backend, server, daemon, core |
| Client | – | not shown in the app | Frontend, UI (a client is more than its screens) |
| Outlook | – | not shown in the app | Status, result, plan (a plan is only one kind of outlook) |
| Desktop app | de app |  | Tray app, the application (too vague) |
| Integration | koppeling |  | Plugin, connector, driver, recipe (a recipe is a deferred idea, not an integration) |
| Ability | – | not shown in the app | Capability, feature, sensor |
| Official route | – | not shown in the app | Official API (a maker can have an API that owners may not use), unofficial route |
| Local network (not encrypted) | lokaal netwerk (niet versleuteld) |  | LAN, insecure, http mode |
| Home Assistant | Home Assistant |  | HA, the hub |

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

The Android app's texts are Android string resources, in `android/app/src/main/res/`: `values/strings.xml` is English, which any language without its own file falls back to, and `values-nl/strings.xml` is Dutch. The names are the same in every file. `app_name` is the one text that is not translated (`translatable="false"`): it is the product's name.

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

### Trying the pseudo-locales

The debug build has two made-up languages, `en-XA` (English with accents and extra length, to see where a text is cut off or a screen has a fixed size) and `ar-XB` (mirrored, right to left, to see the layout flip). An English text that shows without accents in `en-XA` is not a resource but written into the code. They are never in the release build. They show in the system's language list, and in the app's language list on Android 13 and later, of debug builds only; the release build offers English and the `values-*` languages. Always try a language on a debug build, where you can also see the pseudo-locales.

### Adding a language to the Android app

1. **Start the file.** Create `android/app/src/main/res/values-<code>/strings.xml` with the language's two or three letter code (`values-de`), copying `values/strings.xml` and translating every text (the comments above the texts stay in English, for the next translator), following the rules above and the words in the table. Leave `app_name` out.

2. **Nothing else lists the language.** The app's language list (what Android offers as the app's own language from Android 13) and the languages kept from libraries come from the `values-*` folders, in `android/app/build.gradle.kts`.

3. **Check it.** `cd android && ./gradlew check` runs Lint and `StringsResourcesTest`.

4. **Try it.** On a debug build, choose the language in Android's settings for the app (Android 13 and later: Settings, Apps, the app, Language) or as the phone's language, and look at every screen: long words are the usual trouble. The app has no language menu of its own.

5. **Send it.** Commit the `values-<code>` folder, and open a pull request. A language needs the desktop's texts too, see [Adding a language to the desktop app](#adding-a-language-to-the-desktop-app), or the other app shows English.
