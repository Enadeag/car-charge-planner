# Contributing

Thank you for helping. Small fixes can go straight to a pull request; for anything bigger, open an issue first so we can agree on the approach. Be kind: this project follows the [Code of Conduct](CODE_OF_CONDUCT.md).

## Licence of contributions

A contribution is accepted under the project's licence: the GNU General Public License, version 3 or later, with the additional term in [ADDITIONAL-TERMS.md](ADDITIONAL-TERMS.md). You keep your copyright. Do not add a licence header to the files you write: [NOTICE](NOTICE) covers them all.

## Set up

You need a JDK 25 or newer, such as Temurin. Maven comes with the repository through the wrapper: `./mvnw` on
Linux, `mvnw.cmd` on Windows. Nothing else needs to be installed. The wrapper checks the Maven it downloads against
`distributionSha256Sum` in `.mvn/wrapper/maven-wrapper.properties`, and `.mvn/maven.config` has Maven refuse a
library whose checksum does not match (`--strict-checksums`).

CI uses the latest JDK 25; the release build uses one exact Java 25 version (see [Java updates](#java-updates)), and
the packaged app brings that runtime. A newer JDK on your machine
compiles for Java 25 (`--release 25`), so it refuses an API that Java 25 lacks, but the tests and `exec:exec` then
run on the newer runtime, which can behave differently: Java 25's `HttpClient` timeout, for one, ends when the
headers are in, which is why `JdkHttpGet` sets a deadline of its own. When a test passes for you and fails in CI,
or the reverse, try it on JDK 25. `exec:exec` starts the app with the JDK that runs Maven (`JAVA_HOME`), not the
first `java` on the `PATH`.

`.editorconfig` holds the house style for editors: UTF-8, LF line ends, a final newline, 4 spaces (2 in XML, YAML,
JSON and scripts), lines of at most 120 characters. There is no formatter.

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
  `gnome-keyring-daemon`); it has no prompter there, so the unlock prompt's outcomes are tested against a fake Secret
  Service on a private D-Bus, and the notification tests a fake notification server on a private D-Bus that cannot
  start a real one;
- the Windows keychain test uses an entry of its own that it removes again (over ssh, where Windows has no logon
  session for the Credential Manager, it checks only that this counts as no keychain);
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
the engine knows nothing of the desktop, only the platform packages know their system, and they know only
`desktop.system` of the desktop.

A change comes with a test that fails without it.

## Dependencies

Declare every library the code uses directly in `pom.xml`, also one that already comes in through another
library: `./mvnw verify` runs `dependency:analyze` and fails on a library that is used but not declared, or declared
but not used. A library that is only found at run time and never named in the code (such as the SLF4J binding) goes
in the list of ignored ones there, with the reason. Keep the third-party table in the README up to date.

Each library the app brings at run time, also one that comes in through another library, needs its licence text in
`src/packaging/legal/`, which the packages carry in their `legal` folder: one file per component, starting with its
name, an `Artifact: groupId:artifactId` line per jar it covers, its source and its licence, then the licence and
notice texts as the jar's `META-INF` has them (or the project's own `LICENSE` at that release when the jar has none).
`PackagingTest` fails when a runtime library has no file there, or a file names a library the app no longer brings.

## Packaging

Each package brings a runtime made by jlink, so users need no Java of their own, and comes with an SBOM (CycloneDX
JSON) of what it holds: `cyclonedx-maven-plugin` lists the libraries in `target/car-charge-planner.cdx.json` at
`package`, and `tools/SbomRuntime.java` adds the runtime, from the JDK that ran jlink. The jar, the SBOM and the
Linux tar.gz take the time of the last commit (or `SOURCE_DATE_EPOCH`), and the tar.gz holds its files in name order,
owned by root and not writable by group or others, so the same commit and JDK give the same tar.gz. Neither is signed yet: signing waits
for SignPath (ADR 0003). Both scripts take jlink's options, such as the modules and locales, from
`src/packaging/jlink.args`, and link JavaFX from the official jmods, downloaded from a pinned address and checked
against a pinned SHA-256; they need `curl` (and `unzip` on Linux). A new JavaFX version in `pom.xml` needs the new
address and checksum in both scripts (`PackagingTest` checks the version). Each package has a `legal` folder with
`LICENSE`, `NOTICE`, `ADDITIONAL-TERMS.md` and the files of `src/packaging/legal/`, and the Windows installer shows
the GPL.

- **Linux:** `sh tools/package-linux.sh` makes `target/dist/car-charge-planner/` (start `bin/car-charge-planner`),
  `target/dist/car-charge-planner-<version>-linux.tar.gz` and its SBOM `car-charge-planner-<version>-linux.cdx.json`;
  it needs GNU tar and git besides the JDK.
- **Windows:** `powershell -ExecutionPolicy Bypass -File tools\package-windows.ps1` makes a per-user installer,
  `target\dist\car-charge-planner-<version>.exe`, and its SBOM `car-charge-planner-<version>-windows.cdx.json`;
  `-Type app-image` makes the program folder only. The installer needs WiX 5: install the .NET SDK 8, then run
  `powershell -ExecutionPolicy Bypass -File tools\install-wix.ps1`, which installs WiX 5.0.2 and its Util and UI
  extensions for you from packages checked against pinned SHA-256 sums. WiX 6 and later ask to accept a maintenance
  fee agreement first.

`sh tools/scan-sbom.sh target/dist/*.cdx.json` checks SBOMs against the [OSV](https://osv.dev) vulnerability
database with a pinned, checksum-checked OSV-Scanner, and fails on any known vulnerability; the release workflow runs
it on both packages. A finding that was looked at and does not reach the app goes in `osv-scanner.toml` at the
repository root, with the reason and a date it expires. OSV knows the libraries, not the Java runtime: that is what
[Java updates](#java-updates) is for.

The app icon's drawings are in `src/packaging/icons/`: `car-charge-planner.svg` is the icon the PNGs in
`src/main/resources/.../desktop/system/icons/` and `src/packaging/windows/car-charge-planner.ico` show, and
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
Dependabot (`.github/dependabot.yml`) proposes new versions of the actions and of what `pom.xml` names, weekly and a
week after their release, as pull requests that CI builds like any other.

## Releasing

Between releases the version in `pom.xml` is the next one with `-SNAPSHOT`, such as `0.3.0-SNAPSHOT`. The tagged
commit carries the exact version, because the app shows it in About and the download names carry it. To release
X.Y.Z:

1. In a pull request, set `<version>X.Y.Z</version>` in `pom.xml` and turn `## [Unreleased]` in `CHANGELOG.md` into
   `## [X.Y.Z] - YYYY-MM-DD`.
2. After the merge, tag that commit `vX.Y.Z` and push the tag. The release workflow stops when the tag is not `v`
   plus the version in `pom.xml`, when that version still ends in `-SNAPSHOT`, when `CHANGELOG.md` has no section for
   it, or when `release/X.Y.Z` exists at another commit. Otherwise it builds and tests, packages the Linux tar.gz and
   the Windows installer with an SBOM each and smoke-tests both, stops when OSV-Scanner finds a known vulnerability in
   an SBOM, writes `SHA256SUMS` over the four files, attests where they were built (`actions/attest-build-provenance`),
   creates the branch `release/X.Y.Z` at the tag, and makes a draft release with the changelog section, install notes
   and the Java runtime (`java tools/ReleaseNotes.java X.Y.Z CHANGELOG.md`, made before the job that can write to the
   repository).
3. Read the draft and publish it by hand.
4. In a pull request, set the version to the next `-SNAPSHOT` and add an empty `## [Unreleased]`.

To try the workflow without releasing, start it by hand on `main`: it builds everything and makes a draft called
"dry run" (delete it afterwards), without the tag check, the attestations or the release branch.

### Java updates

The release builds use one exact Java version, the `java-version` of each `setup-java` in `release.yml` and
`windows-installer.yml` (`ReleaseWorkflowTest` keeps them the same), and the packages bring that runtime: a user's
app gets Java's security fixes only through a new release. Java's security updates come quarterly, on the third
Tuesday of January, April, July and October. After each one:

1. Read the advisory of the OpenJDK Vulnerability Group (<https://openjdk.org/groups/vulnerability/advisories/>) and
   check which fixed vulnerabilities reach the app: the modules it links are in `src/packaging/jlink.args` and in the
   SBOM (`jlink.modules`); what matters most is what a forecast provider's answer reaches: TLS, the HTTP client and
   the JSON parsing.
2. Once Temurin has the update, set its exact version (such as `25.0.5+11`) in both workflows in a pull request, and
   run `sh tools/scan-sbom.sh` on fresh SBOMs for the libraries.
3. When a fixed vulnerability reaches the app, release a patch version on the new runtime; otherwise the next release
   takes it along. The release notes and the SBOMs name the runtime each release brings.

## Code layout

Everything is in the package `io.github.enadeag.carchargeplanner`, under `src/main/java/`; the tests are in
`src/test/java/`, in the same packages.

| Package | What it holds |
| --- | --- |
| `engine` | Plans, fetches forecasts and owns the stored data, without user interface; clients get plain records. `engine.config` the config file, `engine.away` the away periods (weekly, and the ones entered for a specific day and kept in `away.json`), `engine.battery` the battery level the owner entered last (kept in `battery.json`), `engine.forecast` the provider contract and the forecast service (fallback, cache, rate limits), `engine.forecast.providers` Open-Meteo and Forecast.Solar, `engine.secrets` the API keys, `engine.plan` the planner, `engine.notify` when the owner is told about a window (the notifier port, and the announced windows kept in `announced.json`). |
| `desktop` | The client on the desktop, in parts that depend one way: `desktop.system` the ports each platform implements (tray, notifications, start at login, keeping the popup out of the taskbar) and the icons they show, without any other desktop part; `desktop.text` every text in each language (`Messages`, with the bundles beside it in `src/main/resources`) and the notifications' texts; `desktop.app` the app settings (`app.json`: language, appearance, units and display mode) and `Background`, which every window uses for slow work; `desktop.theme` light or dark and the stylesheet the windows share, and what a screen reader needs beyond JavaFX; `desktop.dashboard` the JavaFX dashboard and the tray icon's status, mapped from the engine's outlook. `desktop.settings` is the Settings window (a page per category, cards of fields, saved through the engine and `app.json`) and the first-run set-up, whose steps show the same cards and save through the same storage. |
| `instance` | One running copy per user: the running copy holds a lock file in a private folder, and a second start asks it, over a private Unix domain socket beside it, to show its dashboard. |
| `platform.linux`, `platform.windows` | The tray icon, notifications, the keychain and start at login on each system: our own StatusNotifierItem, notification and Secret Service clients over D-Bus, and a desktop entry in the autostart folder; AWT's `SystemTray` and its icon's messages, the Credential Manager and the Run key. |
| `Main` and the root package | The composition root: the one place where the parts are made and handed to each other. `Main` reads the system once (`Host`, `Os`) and runs a command (`Commands`) or the app (`DesktopApp`: startup order, tray or window (`StartupDecision`, `TrayStarter`), and one `ClosingOrder` for Quit and the shutdown hook); `PlatformParts` with `LinuxParts` and `WindowsParts` makes each system's own parts. |

Nothing slow runs on the JavaFX thread: a window hands work that can wait on the keychain, a file, the system or the
network to `desktop.app.Background`, which runs it on a virtual thread, logs a failure, and hands the outcome back to the
JavaFX thread. A test gives it executors of its own, such as one that runs the work at once or one that holds it.

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
- **`Optional` where absent is a real case.** A record component or a parameter may be an `Optional` when having
  no value is a case of its own that the code handles, such as `AppSettings.language` when the owner chose none
  and the app follows the system; not to stand for null in general, and not for a collection, which is empty
  instead.
- **Plain invariants.** A record checks its own rules in its constructor, each as a plain `if` and
  `throw new IllegalArgumentException(...)` naming the value and what is allowed, without a helper. A range check on a
  `double` refuses NaN explicitly (`if (kwp <= 0 || Double.isNaN(kwp))`). The Javadoc states the same rule, in the
  `@param` and the `@throws`, so an object that exists is valid.
- **No catching `NullPointerException` or `Throwable`.** Check a value that may be null where it comes in, and let an
  `Error` end what it hit. The one exception is `platform.windows.Advapi32`, because `MethodHandle.invokeExact`
  declares `Throwable`: it passes a `RuntimeException` or `Error` on first and turns only the rest into the caller's
  exception. `ArchitectureTest` and `CatchClausesTest` check this.

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
`src/main/resources/io/github/enadeag/carchargeplanner/desktop/text/`, one file per language: `messages_en.properties` for
English, `messages_nl.properties` for Dutch. They are Java resource bundles in UTF-8 (type `é` and `ü` as they are, no
`é` escapes). Each line is a key, the same in every language, and its text:

```properties
DAY_SET = Set {0} A ({1} kW)
```

1. **Add the language to the app.** In `desktop/text/Language.java`, add a constant with the language's ISO 639 code,
   the locale whose numbers, times and day names it uses, and its name in itself, which the language list shows,
   such as `GERMAN("de", Locale.GERMANY, "Deutsch")`. Language codes are two letters: `de`, `fr`, `pt`. Add the code
   to `--include-locales` in `src/packaging/jlink.args` too, so the packaged app has the language's day names and
   numbers.

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
