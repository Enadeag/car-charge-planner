# Contributing

Thank you for helping. Small fixes can go straight to a pull request; for anything bigger, open an issue first so we can agree on the approach. Be kind: this project follows the [Code of Conduct](CODE_OF_CONDUCT.md).

## Licence of contributions

A contribution is accepted under the project's licence: the GNU General Public License, version 3 or later, with the additional term in [ADDITIONAL-TERMS.md](ADDITIONAL-TERMS.md). You keep your copyright. Do not add a licence header to the files you write: [NOTICE](NOTICE) covers them all.

## Set up

You need Python 3.12 or newer. From a clone:

```bash
python -m venv .venv
.venv/bin/pip install -e ".[dev]"
```

Run the app with `.venv/bin/car-charge-planner` (Windows: `.venv\Scripts\car-charge-planner.exe`).

## Tests and lint

On Linux:

```bash
QT_QPA_PLATFORM=offscreen .venv/bin/pytest -q    # about two minutes; no window appears and nothing touches the network
.venv/bin/ruff check .
```

On Windows, in PowerShell, with the environment made as under Set up (the program is in `.venv\Scripts`):

```powershell
$env:QT_QPA_PLATFORM = "offscreen"
$env:PYTHONUTF8 = "1"    # the tests read and write UTF-8 files; Windows defaults to a code page
.venv\Scripts\python.exe -m pytest -q
.venv\Scripts\python.exe -m ruff check .
```

A test that only makes sense on one system carries the marker `linux`, `windows` or `macos` (`@pytest.mark.windows`, or `pytestmark = pytest.mark.windows` for a file). `tests/conftest.py` skips it on the other systems; several markers mean any of them. Tests without a marker must pass on every system, so they use `paths` and not hard-coded folders.

`ruff check .` also checks import order and a few common mistakes (bugbear, pyupgrade). Types are checked with [pyright](https://github.com/microsoft/pyright), which CI runs too and which must report no errors:

```bash
PATH="$PWD/.venv/bin:$PATH" pyright    # or activate the venv first; pyright then finds PySide6
```

The code is checked in basic mode, and `planner/` and `forecast/` (no Qt) in strict mode, so give new code there full type hints. The tests are not type-checked. Where the Qt stubs are wrong, a `# pyright: ignore[rule]` with a short reason is fine.

A change comes with a test that fails without it. New or changed texts need a refreshed template (see below).

## Comments and docstrings

Write one only when it says something the name, signature and types cannot.

- Do comment the why: a decision, a constraint, a trade-off, an edge case, a unit or format the types don't show.
- Always comment a workaround for Qt, D-Bus, TOML, time-zone changes or another library, saying what breaks and why.
- Public contracts (the provider protocol, the conformance kit) are documented fully but briefly, in one place.
- A module docstring is one sentence about the module's role. Longer explanations go in `docs/`.
- Leave out docstrings on trivial properties, private helpers, signals and overrides whose names say it all.
- No spec section or ticket numbers, no history ("used to", "as before"): git keeps that. No section banners: split the file instead.
- Delete a comment that restates the next line.

## What a test should do

- Test behaviour someone depends on: a plan, a saved file, a request sent, a message shown in the right language. Assert the effect, not that a mock was called or that a widget exists.
- Every test must be able to fail when the behaviour breaks. Prefer exact expected values and recorded responses over "does not crash" or "is not None".
- Don't pin exact UI wording, widget geometry, widget parentage or private attributes. Go through the public method, the signal or the saved result. The pseudo-language test already covers untranslated and clipped texts on every page.
- One behaviour, one test. The same path with other inputs is a `parametrize`. A rule shared by all providers belongs in the conformance kit, not in each provider's tests.
- Checks on the repository itself (docs, README, packaging, template) only when they are cheap and catch a real mistake.
- A test that starts the app or uses real timers waits for a condition, never a fixed `sleep`.
- Don't test test helpers, fixtures or constants. In tests too, the test name is the documentation: comment only what is surprising.

## Building the Windows installer

On Windows, in the environment of the app with [PyInstaller](https://pyinstaller.org) and Babel added, and with [Inno Setup](https://jrsoftware.org/isinfo.php) 6 installed:

```powershell
.venv\Scripts\python.exe tools\build_windows.py
```

This writes the program folder to `dist\car-charge-planner\` and `dist\car-charge-planner-<version>-setup.exe` with its checksum beside it. The script finds `ISCC.exe` on the PATH, in Inno Setup's usual folders, or where the `ISCC` variable says. The release workflow does the same for a version tag. The installer is not signed yet.

## Code layout

Everything is in `src/car_charge_planner/`:

| Folder or file | What it holds |
| --- | --- |
| `planner/` | The planning itself: surplus, windows, projection, advice. No Qt. |
| `forecast/` | Forecast providers (Open-Meteo, Forecast.Solar), the service that picks and caches, place search, key storage. How to add a provider: `docs/providers.md`. |
| `storage/`, `config_file.py` | The state file and the TOML configuration. |
| `controller.py` | Ties it together: fetches, plans, announces, opens the windows. |
| `shells/` | The tray shell and the window shell (the two ways the app shows itself). |
| `planview.py`, `popup.py`, `view_model.py` | The plan page and its Qt-free view model. |
| `settings/`, `setup/` | The Settings window and the first-run set-up wizard. |
| `locale/` | Translations. |

`CONTEXT.md` explains the words the project uses (window, charge power, away and so on).

## The Java version (in progress)

The app is being rewritten in Java with JavaFX (ADR 0005). The Java code lives beside the Python code: `pom.xml`
and the Maven wrapper in the root, the code in `src/main/java/io/github/enadeag/carchargeplanner/`, the tests in
`src/test/java/`. You need a JDK 25 or newer; Maven comes through the wrapper (`mvnw.cmd` on Windows).

| Package | What it holds |
| --- | --- |
| `engine` | Plans, fetches forecasts and owns the stored data, without user interface; clients get plain records. `engine.config` the config file, `engine.away` the away periods (weekly, and the ones entered by hand and kept in `away.json`), `engine.battery` the battery level the owner entered last (kept in `battery.json`), `engine.forecast` the provider contract and the forecast service (fallback, cache, rate limits), `engine.forecast.providers` Open-Meteo and Forecast.Solar, `engine.secrets` the API keys, `engine.plan` the planner, `engine.notify` when the owner is told about a window (the notifier port, and the announced windows kept in `announced.json`). |
| `desktop` | The JavaFX dashboard, the notifications' texts, every text in each language (`Messages`, with the bundles in `src/main/resources`), the app settings (`app.json`: language, units and display mode), and what a tray icon, notifications and start at login must do. `desktop.settings` is the Settings window: a page per category, cards of fields, saved through the engine and `app.json`. |
| `instance` | One running copy per user: a second start asks the first, over a private Unix domain socket, to show its dashboard. |
| `platform.linux`, `platform.windows` | The tray icon, notifications, the keychain and start at login on each system: our own StatusNotifierItem, notification and Secret Service clients over D-Bus, and a desktop entry in the autostart folder; AWT's `SystemTray` and its icon's messages, the Credential Manager and the Run key. |
| `Main` | The composition root: the one place where the parts are made and handed to each other. |

```bash
./mvnw verify                                                           # build and test; nothing touches the network or your desktop
./mvnw compile exec:exec -Dapp.args="--config=docs/example-engine.json"  # run it with the example system
```

The app also takes the commands `quit` (asks the running app to quit), `start-at-login on` and `start-at-login off`;
start at login needs the packaged app, whose launcher is what starts at login.

There are no set-up screens yet; Settings (from the dashboard's footer or the tray menu) edits a config that exists,
and the engine writes it back whole. The engine reads the system from `engine.json` in `~/.config/car-charge-planner/`
(Windows: `%APPDATA%\car-charge-planner\`), or from the file `--config=` names. `docs/example-engine.json` shows
every setting, for a made-up place; its `forecast` section names the forecast provider and the fallback provider. An
optional `typical` section lists the values that are typical values, such as `"system": ["CAR_BATTERY"]`.
API keys are kept in the system keychain, or without one in `secrets.json` beside `engine.json`, readable only by
you. Weekly away periods are in the optional `away` section of `engine.json`; the ones entered for a specific day
are kept by the engine in `away.json` beside the forecast cache, and the battery level entered on the dashboard in
`battery.json` (until one is entered, the plan assumes 50 %). The forecast cache is in
`~/.local/share/car-charge-planner/forecasts/` (Windows: `%LOCALAPPDATA%\car-charge-planner\forecasts\`).

The tests never touch the network, your keychain or your desktop: the providers replay recorded answers, the Linux
keychain test runs a throwaway GNOME Keyring on a private D-Bus (it is skipped without `dbus-daemon` and
`gnome-keyring-daemon`), the notification tests a fake notification server on a private D-Bus that cannot start a
real one, and the Windows keychain test uses an entry of its own that it removes again (it is skipped over ssh, where Windows has no
logon session for the Credential Manager).

Packages, with a runtime made by jlink (unsigned, as the Python version's):

- Linux: `tools/package-linux.sh` makes `target/dist/car-charge-planner/` (start `bin/car-charge-planner`) and a
  `.tar.gz` of it.
- Windows: `powershell -ExecutionPolicy Bypass -File tools\package-windows.ps1` makes a per-user installer in
  `target\dist\` (`-Type app-image` makes the folder only). The installer needs WiX 5: install the .NET SDK 8, then
  `dotnet tool install --global wix --version 5.0.2`, `wix extension add -g WixToolset.Util.wixext/5.0.2` and
  `wix extension add -g WixToolset.UI.wixext/5.0.2`. WiX 7 asks to accept a maintenance fee agreement first.

### Javadoc in the Java code

All public, protected and package-private code carries full Javadoc; only `private` members are exempt. The build enforces it: `./mvnw verify` fails on any doclint finding in the main sources (the tests are exempt).

- Classes, interfaces, methods and constructors: a main description, then every `@param`, `@return` and `@throws`. Each `@param` states the intent of the value: what it is used for, whether it may be null or empty, its units and range. Never a tautology such as "@param volts the volts".
- Records: every component gets an `@param` too, but plainly as data, such as "@param volts the voltage per phase in V, more than 0". A record's docs never say how or where the data is used.
- Enums: each constant gets a line.
- Fields and nested types that are not private get a line as well.

The rule "comment only where needed" (see Comments and docstrings above) is about inline comments. It does not apply to Javadoc: the Java code documents its whole non-private API, even where the name seems to say it all.

## Reporting how it runs on your desktop

The app is developed on KDE Plasma under Wayland and checked on Windows. If you try it on another Linux desktop, please open a **Does it work on my desktop?** issue: it takes five minutes and tells us what works where.

## Adding a language

Every text the app shows is translatable, and a language is one file. The Java version and the Python version keep
their texts apart: the Java way comes first, the Python way below it stays until the Python version is removed.

### In the Java version

The texts are in `src/main/resources/io/github/enadeag/carchargeplanner/desktop/`, one file per language:
`messages_en.properties` for English, `messages_nl.properties` for Dutch. They are Java resource bundles in UTF-8
(type `é` and `ü` as they are, no `\u00e9` escapes). Each line is a key, the same in every language, and its text:

```properties
DAY_SET = Set {0} A ({1} kW)
```

1. **Add the language to the app.** In `desktop/Language.java`, add a constant with the language's ISO 639 code and
   the locale whose numbers, times and day names it uses, such as `GERMAN("de", Locale.GERMANY, "Deutsch")`. Language codes are
   two letters: `de`, `fr`, `pt`. The last part is its name in itself, which the language list shows. Add its code to
   `--include-locales` in `tools/package-linux.sh` and `tools/package-windows.ps1`, so the packaged app has its day
   names and numbers.

2. **Start the file.** Copy `messages_en.properties` to `messages_<code>.properties` and translate every text after
   the `=`. Things to know:
   - `{0}`, `{1}` are filled in by the app. Keep each one, but put it wherever your language wants it: word order is
     yours. `TextKey.java` says, per key, what each number holds.
   - An apostrophe is written twice: `charger''s app` shows as "charger's app". A single one would hide the text after
     it (the texts are `java.text.MessageFormat` patterns).
   - Numbers, times and day names come in already written your language's way (the locale of step 1): a decimal comma
     or point, a 24- or 12-hour clock, "zondag" or "Sunday". Only the words around them are yours, including the unit
     and the space before it (`{0} %` in English, `{0}%` in Dutch).
   - A text with a count has one form per plural with a `choice` place: `{0,choice,1#{0} day|1<{0} days}`. That
     covers languages with a singular and a plural, such as English and Dutch; a language with more plural forms
     (Polish, Russian) would need ICU4J, which the app does not use yet.
   - Product and provider names (Open-Meteo, Forecast.Solar, "Car charge planner") and the provider's credit stay as
     they are.
   - What the engine says about a config file or a provider (the "details") stays English, like its log messages; the
     sentence around it is translated.

3. **Check it.** `./mvnw verify` runs `MessagesBundleTest`, which fails when a language misses a key or has one too
   many, when a text has other `{n}` places than the English one, or when it has a single apostrophe.

4. **Try it.** The app follows the system's language: on Linux `LANGUAGE`, then `LC_ALL`, `LC_MESSAGES` and `LANG`
   (the plain `C` locale means English); on Windows the language of the user's display. To choose one, write
   `{"language": "de"}` to `app.json` beside `engine.json` (`~/.config/car-charge-planner/`, Windows
   `%APPDATA%\car-charge-planner\`); `"system"` follows the system again. The file is read when the app starts;
   choosing a language in Settings (App) and saving switches the running app at once.

   ```bash
   LANGUAGE=nl ./mvnw compile exec:exec -Dapp.args="--config=docs/example-engine.json"
   ```

   Look at the dashboard, the tray icon's tooltip and menu, and a notification: long words are the usual trouble.

5. **Send it.** Commit the `.properties` file and the line in `Language.java`, and open a pull request.

When the English texts change, a new text gets a constant in `TextKey.java` and a key in every language's file in
the same pull request; a text you cannot translate yourself may stay English there for a translator to pick up, but
the key must be in every file.

### In the Python version

You need the project set up as in the README, plus Babel (`pip install babel`). Run the commands from the project
root.

1. **Start the file.** Use the language code, `nl` for Dutch, `pt_BR` for Brazilian Portuguese:

   ```bash
   .venv/bin/python -m car_charge_planner.i18n_tools init nl
   ```

   This creates `src/car_charge_planner/locale/nl/LC_MESSAGES/car-charge-planner.po`, a copy of the template with every text
   waiting for a translation.

2. **Translate.** Open the `.po` file in a translation editor (Poedit, Lokalize, Gtranslator) or a text editor.
   Things to know:
   - `{name}` placeholders (`{pct}`, `{end}`, `{n}`) are filled in by the app. Keep them exactly as written, but
     put them wherever your language wants them: word order is yours.
   - Texts with a number have one form per plural your language has (`{n} day` / `{n} days`).
   - A text with a context line (`msgctxt`) is the same English word in a different role. `weekday, short` is
     "Mon", `month, short` is "Jan"; write them the way your language abbreviates them.
   - `decimal separator` is the one place to write `,` if your language writes 1,6 instead of 1.6.
   - `list separator` is what goes between items of a list (`, `).
   - A text you leave empty stays English.

3. **Compile and try it.**

   ```bash
   .venv/bin/python -m car_charge_planner.i18n_tools compile
   LANGUAGE=nl .venv/bin/car-charge-planner
   ```

   The app follows `LANGUAGE`, then `LC_ALL`, `LC_MESSAGES` and `LANG`. Once at least one translation is installed,
   Settings, on the Interface tab, shows a Language choice; it applies the next time the app starts.

4. **Check it.** The tests find the usual mistakes (a text that is still English, a label that no longer fits):

   ```bash
   .venv/bin/pytest -q tests/test_i18n.py tests/test_i18n_texts.py tests/test_pseudo_language.py tests/test_language_setting.py
   ```

   Look at the Settings tabs and the popup in your language: long words are the usual trouble.

5. **Send it.** Commit the `.po` file of your language and open a pull request. The compiled `.mo` files are not committed: they are built from the `.po` files when the app is packaged, and you make them yourself (step 3) to try a language from a clone.

#### When the English texts change

`python -m car_charge_planner.i18n_tools extract` rewrites the template from the code, and `python -m car_charge_planner.i18n_tools update` merges
it into every language's `.po`; new and changed texts show up untranslated (or marked fuzzy: check those). A test
fails when the committed template is behind the code, so a text added to the code comes with a refreshed template.
