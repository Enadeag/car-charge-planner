# Code

## Java versions and the editor

CI uses the latest JDK 25; the release build uses one exact Java 25 version (see [Java updates](ci-and-releasing.md#java-updates)), and the packaged app brings that runtime. A newer JDK on your machine compiles the desktop app for Java 25 (`--release 25`) and the engine for Java 17 (`--release 17`), so it refuses an API that those versions lack, but the tests and `exec:exec` then run on the newer runtime, which can behave differently: Java 25's `HttpClient` timeout, for one, ends when the headers are in, which is why `JdkHttpGet` sets a deadline of its own. When a test passes for you and fails in CI, or the reverse, try it on JDK 25. `exec:exec` starts the app with the JDK that runs Maven (`JAVA_HOME`), not the first `java` on the `PATH`.

The engine is held to Java 17: `engine/pom.xml` compiles its main code and its tests with `--release 17`, so the compiler refuses a language feature or API that Java 17 lacks. The Android app compiles the engine's sources, and Android's build tools and Java library reach only about as far as Java 17 ([ADR 0010](../adr/0010-android-app.md)); the desktop module stays on Java 25. The reason is Android's, not taste: the desktop module may use what the engine may not. CI also runs the engine's tests on a real Temurin 17 (job `engine-java17`, [What is tested where](testing.md#what-is-tested-where)), so an API that behaves differently there shows as a failed check on the pull request. Java 17 has no unnamed variable `_`, record patterns, pattern `switch` or sequenced-collection methods, so in the engine:

- a caught exception or lambda parameter that is not used is named `ignored`;
- read a record's parts after a plain `instanceof` with a type pattern;
- write a choice between the cases of a sealed type as an `if`/`else` chain that ends in a `throw` for an unknown case, with a test for every branch, as the compiler no longer checks that every case is covered;
- write `get(0)`, `get(size() - 1)` and `remove(size() - 1)` for `getFirst()`, `getLast()` and `removeLast()`, and a reversed copy for `reversed()`.

`.editorconfig` holds the house style for editors: UTF-8, LF line ends, a final newline, 4 spaces (2 in XML, YAML, JSON and scripts), lines of at most 120 characters, except in Markdown, where a paragraph is one line. There is no formatter. In IntelliJ IDEA, set Maven home to *Bundled* (Settings, Build Tools, Maven): its Maven connector fails on the wrapper's Maven 3.10.0, while `./mvnw` builds as usual.

## Code layout

Everything is in the package `nl.enadeag.carchargeplanner`, in two Maven modules under one parent `pom.xml`, and the Android app's Gradle build in `android/` ([ADR 0010](../adr/0010-android-app.md)):

| Module | Folder | What it holds |
| --- | --- | --- |
| `car-charge-planner-engine` | `engine/` | The package `engine` and its tests: no user interface, desktop or platform, so the Android app can share it. Its test classes, with the test helpers the desktop's tests use, also go in a test-jar. |
| `car-charge-planner-desktop` | `desktop/` | Every other package below and their tests, the texts, icons and version file; it builds `car-charge-planner.jar`, its libraries in `desktop/target/lib` and the SBOM. |
| `:app`, `:engine`, `:engine-lint` (Gradle) | `android/` | The Android app in `nl.enadeag.carchargeplanner.android` (`app/`, Kotlin with Compose), the engine's sources and contract tests compiled for it (`engine/`, `engine-lint/`, which hold only build files), and the build's own checks (`build-logic/`); see [The Android app](android.md). |

In each Maven module the code is in `src/main/java/` and the tests in `src/test/java/`, in the same packages; in the Android app, in `src/main/kotlin/` and `src/test/kotlin/` (with what the instrumented tests share in `src/sharedTest/kotlin/`), and its classes in plain Java (`AndroidHttpGet`, the slf4j provider, the keychain in `android.secrets` and `AndroidPlatform`, which makes the engine's platform parts) in `src/main/java/`. The packaging (`src/packaging/`), `tools/` and `docs/` stay at the root.

| Package | What it holds |
| --- | --- |
| `engine` | Plans, fetches forecasts and owns the stored data, without user interface; clients get plain records. `engine.config` the settings file (`engine.json`), `engine.setting` the names of its settings and what is wrong with one, `engine.store` how every stored file is read, written, versioned and kept private (`PrivateFiles`, and `FilePrivacy`, the way each platform makes a file private), `engine.away` the away periods (weekly, and the ones entered for a specific day and kept in `away.json`), `engine.trip` the trips (kept in `trips.json`), `engine.battery` the battery level the owner entered last (kept in `battery.json`), `engine.forecast` the provider contract, `HttpGet` (the providers' way to the network, which each platform fills) and the forecast service (fallback, cache, rate limits), `engine.forecast.providers` Open-Meteo and Forecast.Solar, `engine.secrets` the API keys, `engine.plan` the planner, `engine.dashboard` what the dashboard shows of a plan as numbers, hours and flags, which `Outlook.Planned.dashboard` makes and each app puts in words and draws, `engine.notify` when the owner is told about a window or a trip (the notifier port, the announced windows kept in `announced.json`, and what was told about each trip, kept in `trip-notices.json`). |
| `desktop` | The client on the desktop, in parts that depend one way: `desktop.system` the ports each platform implements (tray, notifications, start at login, keeping the popup out of the taskbar, the system's text size), the icons they show and `PosixFilePrivacy`, the desktop's `FilePrivacy`, without any other desktop part; `desktop.text` every text in each language (`Messages`, with the bundles beside it in `src/main/resources`); `desktop.notify` the notifications' texts; `desktop.app` the app settings (`app.json`: language, appearance, units and display mode) and `Background`, which every window uses for slow work; `desktop.theme` light or dark, the system's text size and the stylesheet the windows share (`WindowTheme`), the labels they build their texts with (`Labels`), and what a screen reader needs beyond JavaFX (`ScreenReader`); `desktop.dashboard` the JavaFX dashboard and the tray icon's status, mapped from the engine's outlook: the window and the popup in the package itself, and a sub-package for each part of it (`outlook`, `day`, `away`, `battery`, `solar`, `advice`, `trip`, `tray`), built on the shared pieces in `dashboard.parts`. `desktop.settings` is the Settings window (a page per category, cards of fields, saved through the engine and `app.json`) and the first-run set-up, whose steps show the same cards and save through the same storage: the window in the package itself, set-up in `setup`, the storage in `store`, a sub-package per category (`location`, `solar`, `car`, `charger`, `forecast`, `away`, `planning`, `app`, `about`) that knows no other, built on the page and card pieces in `page` and the fields in `field`, and `form` the logic without JavaFX that they all use. |
| `instance` | One running copy per user: the running copy holds a lock file in a private folder, and a second start asks it, over a private Unix domain socket beside it, to show its dashboard. |
| `platform.linux`, `platform.windows` | The tray icon, notifications, the keychain and start at login on each system: our own StatusNotifierItem, notification and Secret Service clients over D-Bus, and a desktop entry in the autostart folder (in a Flatpak, the Background portal); AWT's `SystemTray` and its icon's messages, the Credential Manager, the Run key and the text size in the registry. |
| `Main` | The composition root: the one place where the parts are made and handed to each other. `Main`, the root package's only class, reads the system once (`host`) and runs a command (`cli`) or the app (`start`); the five packages below are the composition root's own, and depend one way: `start` on `cli`, both on `platforms`, `logging` and `host`, and `platforms` and `logging` on `host` alone. |
| `start` | The app as it runs (`DesktopApp`): startup order, tray or window (`StartupDecision`, `TrayStarter`), one `ClosingOrder` for Quit and the shutdown hook, and `JdkHttpGet`, the `HttpGet` on Java's own HTTP client that it hands the engine. |
| `platforms` | `PlatformParts`, with `FreedesktopParts` and `WindowsParts`, which make each system's own parts from `platform.linux` or `platform.windows`, for the app and the commands. |
| `cli` | The command line: what the app was started to do (`CommandLine`), the commands that do one thing and end (`Commands`), and the exit codes. |
| `logging` | The app's log, set up once (`AppLog`, which names the parent of the app's own loggers): the rotating private log file, one line per record, and the logging of what no thread caught. |
| `host` | The app's names on the system (`AppName`: the title the owner sees and the ID that names its log files, desktop entries and keychain entries), the system and its user, read once (`Host`, `Os`), and where the app's folders and files are on each system: config, data, the instance lock, the private runtime folders and the API keys' file. |

Nothing slow runs on the JavaFX thread: a window hands work that can wait on the keychain, a file, the system or the network to `desktop.app.Background`, which runs it on a virtual thread, logs a failure, and hands the outcome back to the JavaFX thread. A test gives it executors of its own, such as one that runs the work at once or one that holds it.

The engine's changes to the away periods and to the trips may be called from any thread, also at the same time: `AwayFile` and `TripFile` read, change and write their file as one step, so no change is lost to another and no client needs a lock of its own. Every change of `Engine`'s stored data goes through its `thenReplan`, which has the engine plan again soon after, also when the change failed; a new one does too.

How to add a forecast provider is in [docs/providers.md](../providers.md). `CONTEXT.md` explains the words the project uses (window, charge power, away and so on), and `docs/adr/` records the larger decisions.

## Javadoc

Everything that is `public` carries full Javadoc, in every module. Protected, package-private and private members get a comment only where the reason does not show from the code (see Comments below). The build enforces the public part: `./mvnw verify` fails on any doclint finding at the `public` level in the main sources (the tests are exempt).

Of the public API:

- Classes, interfaces, methods and constructors: a main description, then every `@param`, `@return` and `@throws`. Each `@param` states the intent of the value: what it is used for, whether it may be null or empty, its units and range. Never a tautology such as "@param volts the volts".
- Records: every component gets an `@param` too, but plainly as data, such as "@param volts the voltage per phase in V, more than 0". A record's docs never say how or where the data is used.
- Enums: each constant gets a line.
- Public fields and nested types get a line as well.

The rule "comment only where needed" (below) is about inline comments and about everything that is not public. It does not apply to the public API: that is documented in full, even where the name seems to say it all.

## Comments

Write one only when it says something the name, signature and types cannot.

- Do comment the why: a decision, a constraint, a trade-off, an edge case, a unit or format the types don't show.
- Always comment a workaround for JavaFX, AWT, D-Bus, the keychain, time-zone changes or another library, saying what breaks and why.
- Public contracts (the provider contract, the conformance kit) are documented fully but briefly, in one place.
- No spec section or ticket numbers, no history ("used to", "as before"): git keeps that. No section banners: split the file instead.
- Delete a comment that restates the next line.

## Types and invariants

- **Strict types.** A value with a meaning of its own gets a type of its own (`Percent`, `ProviderId`, `Coordinates`) rather than a bare `double` or `String`, and a choice is an enum (`Phases`, `Freshness`, `ProviderRole`), not an `int` that must be 1 or 3 or a pair of booleans of which only some combinations make sense.
- **`Optional` where absent is a real case.** `Optional` is for return values and record components only, such as `AppSettings.language` when the owner chose none and the app follows the system; never a field or a parameter, and not for a collection, which is empty instead. A method that may get nothing comes in two (`choose(Language)` and `followSystem()`), or takes a type that names the none case (`Owner.Unchecked`), and a caller that holds an `Optional` unwraps it at the call. In a field, an injection that may be missing becomes a null object or a type that says why it is missing, state that becomes known later is a private field that is null until then and goes out through `Optional.ofNullable`, and a callback set later starts as a no-op. `ArchitectureTest` checks the fields and the parameters.
- **Plain invariants.** A record checks its own rules in its constructor, each as a plain `if` and `throw new IllegalArgumentException(...)` naming the value and what is allowed, without a helper. A range check on a `double` refuses NaN explicitly (`if (kwp <= 0 || Double.isNaN(kwp))`). The Javadoc states the same rule, in the `@param` and the `@throws`, so an object that exists is valid.
- **No catching `NullPointerException` or `Throwable`.** Check a value that may be null where it comes in, and let an `Error` end what it hit. The one exception is `platform.windows.Advapi32`, because `MethodHandle.invokeExact` declares `Throwable`: it passes a `RuntimeException` or `Error` on first and turns only the rest into the caller's exception. `ArchitectureTest` and `CatchClausesTest` check this.

## Logging

The app writes its log to `car-charge-planner.0.log` in its data folder (`~/.local/share/car-charge-planner` on Linux, `%LOCALAPPDATA%\car-charge-planner` on Windows; Settings, About shows the file), one line per record with the time, level, logger and message, and an exception's stack trace on the lines after it. At 1 MB the file becomes `.1.log` and the one before `.2.log`; older ones are dropped. The files and their folder are readable only by you. When the app runs in a terminal it shows the log there too; otherwise follow the file, for example with `tail -F ~/.local/share/car-charge-planner/car-charge-planner.0.log`.

It logs from INFO. For more of the app's own records, set `CAR_CHARGE_PLANNER_LOG_LEVEL` to `DEBUG` or `TRACE` (one of `System.Logger.Level`'s names) before you start it, such as `CAR_CHARGE_PLANNER_LOG_LEVEL=DEBUG ./mvnw -pl :car-charge-planner-desktop -am compile exec:exec`; the libraries stay at INFO. Logging is set up once, in `DesktopApp` (`AppLog`); what a thread throws and nothing catches is logged as an error (`UncaughtLogging`).

The Android app writes its log to Logcat only, no file: from INFO in the release build and from DEBUG in the debug build (`BuildConfig.DEBUG`), each line tagged with the logging class's simple name, through the app's own slf4j provider (`LogcatServiceProvider`); follow it with `adb logcat` or in Android Studio.

In the code, log through `System.Logger` in the desktop app, through slf4j in the Android app's own code, and through slf4j (`LoggerFactory.getLogger(X.class)`, with the text concatenated, no `{}`) in the engine, which `EngineArchitectureTest` holds to it, so each platform can bind it to its own log (ADR 0010). The desktop sends slf4j's records on to `java.util.logging`, so they reach the same log. The engine logs at debug or above: slf4j's trace becomes `FINEST`, below the `FINER` that `CAR_CHARGE_PLANNER_LOG_LEVEL=TRACE` lets through, so an engine trace line would never reach the log. Log a change of state, such as a provider that starts failing, not what happens every minute. Never log an API key, a request's address (it can hold a key) or the home's coordinates; a path is fine, although it shows the user name. `ArchitectureTest` keeps every class that makes or reads a request's address from logging at all, and the provider conformance kit checks that no address, key or coordinate reaches a log line or the reason the owner is shown.

The engine logs these changes of state, each once: a provider that gives no forecast and one that gives forecasts again (`ProviderHealth`), a settings file that cannot be used and one that can be used again (`ConfigHealth`), where the API keys are kept (`ApiKeys`), a stored file that cannot be used or is set aside (`StoredFile`), and each announcement and notice about a trip (`Announcer`, `TripNotices`).
