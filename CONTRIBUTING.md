# Contributing

Thank you for helping. Small fixes can go straight to a pull request; for anything bigger, open an issue first so we can agree on the approach. Be kind: this project follows the [Code of Conduct](CODE_OF_CONDUCT.md).

## Licence of contributions

A contribution is accepted under the project's licence: the GNU General Public License, version 3 or later, with the additional term in [ADDITIONAL-TERMS.md](ADDITIONAL-TERMS.md). You keep your copyright. Do not add a licence header to the files you write: [NOTICE](NOTICE) covers them all.

## Set up

You need a JDK 25 or newer, such as Temurin. Maven comes with the repository through the wrapper: `./mvnw` on
Linux, `mvnw.cmd` on Windows. Nothing else needs to be installed.

```bash
./mvnw verify                                                           # build and test; nothing touches the network or your desktop
./mvnw compile exec:exec -Dapp.args="--config=docs/example-engine.json"  # run it with the example system
```

Without `--config=` the app uses your own `engine.json` (see the README, Where things are kept), and without one it
opens set-up. `docs/example-engine.json` shows every setting, for a made-up place; its `forecast` section names the
forecast provider and the fallback provider, and an optional `typical` section lists the values that are typical
values, such as `"system": ["CAR_BATTERY"]`.

The app also takes the commands `quit` (asks the running app to quit), `start-at-login on` and `start-at-login off`,
and, on Linux, `install-launcher` and `uninstall-launcher` (the menu entry and icons), for example
`-Dapp.args="quit"`. Start at login and the menu entry need the packaged app, whose launcher is what they start. Try
`install-launcher` with `XDG_DATA_HOME` pointing at a scratch folder, not your real one.

## Logging

The app writes its log to `car-charge-planner.0.log` in its data folder (`~/.local/share/car-charge-planner` on Linux,
`%LOCALAPPDATA%\car-charge-planner` on Windows; Settings, About shows the file), one line per record with the time,
level, logger and message, and an exception's stack trace on the lines after it. At 1 MB the file becomes `.1.log`
and the one before `.2.log`; older ones are dropped. The files and their folder are readable only by you. When the
app runs in a terminal it shows the log there too; otherwise follow the file, for example with
`tail -F ~/.local/share/car-charge-planner/car-charge-planner.0.log`.

It logs from INFO. For more of the app's own records, set `CAR_CHARGE_PLANNER_LOG_LEVEL` to `DEBUG` or `TRACE` (one of
`System.Logger.Level`'s names) before you start it, such as
`CAR_CHARGE_PLANNER_LOG_LEVEL=DEBUG ./mvnw compile exec:exec`; the libraries stay at INFO. Logging is set up once,
in `DesktopApp` (`AppLog`); what a thread throws and nothing catches is logged as an error (`UncaughtLogging`).

In the code, log through `System.Logger`. Log a change of state, such as a provider that starts failing, not what
happens every minute. Never log an API key, a request's address (it can hold a key) or the home's coordinates; a
path is fine, although it shows the user name.
`ArchitectureTest` keeps every class that makes or reads a request's address from logging at all, and the provider
conformance kit checks that no address, key or coordinate reaches a log line or the reason the owner is shown.

The engine logs these changes of state, each once: a provider that gives no forecast and one that gives forecasts
again (`ProviderHealth`), a config file that cannot be used and one that can be used again (`ConfigHealth`), where the
API keys are kept (`ApiKeys`), a stored file that cannot be used or is set aside (`StoredFile`), and each announcement
(`Announcer`).

## Tests

`./mvnw verify` compiles, runs every test and checks the Javadoc; it must pass on Linux and on Windows. The tests never
touch the network, your keychain or your desktop:

- the providers replay recorded answers (`src/test/resources/.../engine/forecast/providers/`);
- the Linux keychain test runs a throwaway GNOME Keyring on a private D-Bus (it is skipped without `dbus-daemon` and
  `gnome-keyring-daemon`), and the notification tests a fake notification server on a private D-Bus that cannot start
  a real one;
- the Windows keychain test uses an entry of its own that it removes again (it is skipped over ssh, where Windows has
  no logon session for the Credential Manager);
- `DesktopAppSmokeTest` (Linux, skipped without `dbus-daemon`) starts the app in a JVM of its own with the example
  config, on a private D-Bus with a fake tray host, headless JavaFX, private XDG folders and a proxy that refuses
  every connection, waits for the tray icon, sends `quit` and checks that both end cleanly;
- no test may put a window or a tray icon on the developer's desktop: the JavaFX tests run on JavaFX's headless
  platform, and AWT runs with `java.awt.headless`.

All JavaFX tests share one headless platform in one JVM, so a test class that uses it is marked `@FxTest` (`Fx`
refuses to run without it): each test then starts without a window, and fails when the JavaFX thread threw an
exception that nothing caught. Its allow-list takes only OpenJFX bugs, each with a link. The test classes run in
alphabetical order; `-Dsurefire.runOrder=random` shows whether a test depends on the ones before it.

A test that only makes sense on one system says so with JUnit's `@EnabledOnOs`. Tests without one must pass on every
system, so they use the location classes and not hard-coded folders. `ArchitectureTest` keeps the packages apart:
the engine knows nothing of the desktop, and only the platform packages know their system.

A change comes with a test that fails without it.

## Dependencies

Declare every library the code uses directly in `pom.xml`, also one that already comes in through another
library: `./mvnw verify` runs `dependency:analyze` and fails on a library that is used but not declared, or declared
but not used. A library that is only found at run time and never named in the code (such as the SLF4J binding) goes
in the list of ignored ones there, with the reason. Keep the third-party table in the README up to date.

## Packaging

Each package brings a runtime made by jlink, so users need no Java of their own. Neither is signed yet: signing waits
for SignPath (ADR 0003).

- **Linux:** `sh tools/package-linux.sh` makes `target/dist/car-charge-planner/` (start `bin/car-charge-planner`) and
  `target/dist/car-charge-planner-<version>-linux.tar.gz`.
- **Windows:** `powershell -ExecutionPolicy Bypass -File tools\package-windows.ps1` makes a per-user installer,
  `target\dist\car-charge-planner-<version>.exe`; `-Type app-image` makes the program folder only. The installer
  needs WiX 5: install the .NET SDK 8, then `dotnet tool install --global wix --version 5.0.2`,
  `wix extension add -g WixToolset.Util.wixext/5.0.2` and `wix extension add -g WixToolset.UI.wixext/5.0.2`. WiX 7
  asks to accept a maintenance fee agreement first.

The app icon's drawings are in `src/packaging/icons/`: `car-charge-planner.svg` is the icon the PNGs in
`src/main/resources/.../desktop/icons/` and `src/packaging/windows/car-charge-planner.ico` show, and
`car-charge-planner-symbolic.svg` has the shapes the tray icon draws (`StatusIcon`).

## Continuous integration

The workflows are in `.github/workflows/`:

- `ci.yml` runs `./mvnw -B clean verify` for every pull request into `main` and every push to `main`, on Linux (with
  `dbus-daemon` and GNOME Keyring, so the private-bus tests run) and on Windows. A change to documentation only
  (`*.md`, `docs/` except `docs/example-engine.json`, which the tests read) skips both, and GitHub counts a skipped job
  as passed. While the repository is private, Windows minutes count double, so a pull request builds on Windows only
  when it touches `platform/windows`, the Windows packaging, `pom.xml`, the Maven wrapper or the workflows; every
  push to `main` still builds on Windows. A stacked pull request runs once it is retargeted to `main`. The public
  launch (#31) turns Windows back on for every pull request. The macOS job is switched off until the JavaFX tests run
  on the macOS runner (#134); the maintainer decides when it comes back.
- `windows-installer.yml` builds the installer with `tools\package-windows.ps1`, installs it silently, runs the
  installed `car-charge-planner.exe quit`, uninstalls it and keeps the installer as an artifact for 14 days. While the
  repository is private it runs only by hand and for a release.
- `release.yml` makes a release; see below.

The actions are pinned to a full commit SHA, with the version in a comment beside it: update both together.

## Releasing

Between releases the version in `pom.xml` is the next one with `-SNAPSHOT`, such as `0.3.0-SNAPSHOT`. The tagged
commit carries the exact version, because the app shows it in About and the download names carry it. To release
X.Y.Z:

1. In a pull request, set `<version>X.Y.Z</version>` in `pom.xml` and turn `## [Unreleased]` in `CHANGELOG.md` into
   `## [X.Y.Z] - YYYY-MM-DD`.
2. After the merge, tag that commit `vX.Y.Z` and push the tag. The release workflow stops when the tag is not `v`
   plus the version in `pom.xml`, when that version still ends in `-SNAPSHOT`, when `CHANGELOG.md` has no section for
   it, or when `release/X.Y.Z` exists at another commit. Otherwise it builds and tests, packages the Linux tar.gz and
   the Windows installer and smoke-tests both, writes `SHA256SUMS` over them, creates the branch `release/X.Y.Z` at
   the tag, and makes a draft release with the changelog section and install notes
   (`java tools/ReleaseNotes.java X.Y.Z CHANGELOG.md`).
3. Read the draft and publish it by hand.
4. In a pull request, set the version to the next `-SNAPSHOT` and add an empty `## [Unreleased]`.

To try the workflow without releasing, start it by hand on `main`: it builds everything and makes a draft called
"dry run" (delete it afterwards), without the tag check or the release branch.

## Code layout

Everything is in the package `io.github.enadeag.carchargeplanner`, under `src/main/java/`; the tests are in
`src/test/java/`, in the same packages.

| Package | What it holds |
| --- | --- |
| `engine` | Plans, fetches forecasts and owns the stored data, without user interface; clients get plain records. `engine.config` the config file, `engine.away` the away periods (weekly, and the ones entered for a specific day and kept in `away.json`), `engine.battery` the battery level the owner entered last (kept in `battery.json`), `engine.forecast` the provider contract and the forecast service (fallback, cache, rate limits), `engine.forecast.providers` Open-Meteo and Forecast.Solar, `engine.secrets` the API keys, `engine.plan` the planner, `engine.notify` when the owner is told about a window (the notifier port, and the announced windows kept in `announced.json`). |
| `desktop` | The JavaFX dashboard, the notifications' texts, every text in each language (`Messages`, with the bundles in `src/main/resources`), the app settings (`app.json`: language, appearance, units and display mode), and what a tray icon, notifications and start at login must do. `desktop.settings` is the Settings window (a page per category, cards of fields, saved through the engine and `app.json`) and the first-run set-up, whose steps show the same cards and save through the same storage. |
| `instance` | One running copy per user: a second start asks the first, over a private Unix domain socket, to show its dashboard. |
| `platform.linux`, `platform.windows` | The tray icon, notifications, the keychain and start at login on each system: our own StatusNotifierItem, notification and Secret Service clients over D-Bus, and a desktop entry in the autostart folder; AWT's `SystemTray` and its icon's messages, the Credential Manager and the Run key. |
| `Main` and the root package | The composition root: the one place where the parts are made and handed to each other. `Main` reads the system once (`Host`, `Os`) and runs a command (`Commands`) or the app (`DesktopApp`: startup order, tray or window (`StartupDecision`, `TrayStarter`), and one `ClosingOrder` for Quit and the shutdown hook); `PlatformParts` with `LinuxParts` and `WindowsParts` makes each system's own parts. |

How to add a forecast provider is in [docs/providers.md](docs/providers.md). `CONTEXT.md` explains the words the
project uses (window, charge power, away and so on), and `docs/adr/` records the larger decisions.

## Javadoc

All public, protected and package-private code carries full Javadoc; only `private` members are exempt. The build enforces it: `./mvnw verify` fails on any doclint finding in the main sources (the tests are exempt).

- Classes, interfaces, methods and constructors: a main description, then every `@param`, `@return` and `@throws`. Each `@param` states the intent of the value: what it is used for, whether it may be null or empty, its units and range. Never a tautology such as "@param volts the volts".
- Records: every component gets an `@param` too, but plainly as data, such as "@param volts the voltage per phase in V, more than 0". A record's docs never say how or where the data is used.
- Enums: each constant gets a line.
- Fields and nested types that are not private get a line as well.

The rule "comment only where needed" (below) is about inline comments. It does not apply to Javadoc: the code documents its whole non-private API, even where the name seems to say it all.

## Comments

Write one only when it says something the name, signature and types cannot.

- Do comment the why: a decision, a constraint, a trade-off, an edge case, a unit or format the types don't show.
- Always comment a workaround for JavaFX, AWT, D-Bus, the keychain, time-zone changes or another library, saying what breaks and why.
- Public contracts (the provider contract, the conformance kit) are documented fully but briefly, in one place.
- No spec section or ticket numbers, no history ("used to", "as before"): git keeps that. No section banners: split the file instead.
- Delete a comment that restates the next line.

## Types and invariants

- **Strict types.** A value with a meaning of its own gets a type of its own (`Percent`, `ProviderId`, `Coordinates`)
  rather than a bare `double` or `String`, and a choice is an enum (`Phases`, `Freshness`, `ProviderRole`), not an
  `int` that must be 1 or 3 or a pair of booleans of which only some combinations make sense.
- **Plain invariants.** A record checks its own rules in its constructor, each as a plain `if` and
  `throw new IllegalArgumentException(...)` naming the value and what is allowed, without a helper. A range check on a
  `double` refuses NaN explicitly (`if (kwp <= 0 || Double.isNaN(kwp))`). The Javadoc states the same rule, in the
  `@param` and the `@throws`, so an object that exists is valid.

## What a test should do

- Test behaviour someone depends on: a plan, a saved file, a request sent, a message shown in the right language. Assert the effect, not that a mock was called or that a widget exists.
- Every test must be able to fail when the behaviour breaks. Prefer exact expected values and recorded responses over "does not crash" or "is not null".
- Don't pin exact UI wording, node geometry, node parentage or private fields. Go through the public method, the model or the saved result.
- One behaviour, one test. The same path with other inputs is a `@ParameterizedTest`. A rule shared by all providers belongs in the conformance kit, not in each provider's tests.
- Checks on the repository itself (docs, packaging, bundles) only when they are cheap and catch a real mistake.
- A test that starts the app or uses real timers waits for a condition, never a fixed sleep.
- Don't test test helpers, fixtures or constants. In tests too, the test name is the documentation: comment only what is surprising.

## Reporting how it runs on your desktop

The app is developed on KDE Plasma under Wayland and checked on Windows. If you try it on another Linux desktop, please open a **Does it work on my desktop?** issue: it takes five minutes and tells us what works where.

## Adding a language

Every text the app shows is translatable, and a language is one file. The texts are in
`src/main/resources/io/github/enadeag/carchargeplanner/desktop/`, one file per language: `messages_en.properties` for
English, `messages_nl.properties` for Dutch. They are Java resource bundles in UTF-8 (type `é` and `ü` as they are, no
`é` escapes). Each line is a key, the same in every language, and its text:

```properties
DAY_SET = Set {0} A ({1} kW)
```

1. **Add the language to the app.** In `desktop/Language.java`, add a constant with the language's ISO 639 code,
   the locale whose numbers, times and day names it uses, and its name in itself, which the language list shows,
   such as `GERMAN("de", Locale.GERMANY, "Deutsch")`. Language codes are two letters: `de`, `fr`, `pt`. Add the code
   to `--include-locales` in `tools/package-linux.sh` and `tools/package-windows.ps1` too, so the packaged app has
   the language's day names and numbers.

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

   Look at the dashboard, set-up, Settings, the tray icon's tooltip and menu, and a notification: long words are the
   usual trouble.

5. **Send it.** Commit the `.properties` file and the line in `Language.java`, and open a pull request.

When the English texts change, a new text gets a constant in `TextKey.java` and a key in every language's file in
the same pull request; a text you cannot translate yourself may stay English there for a translator to pick up, but
the key must be in every file.
