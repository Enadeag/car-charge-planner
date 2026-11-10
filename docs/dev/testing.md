# Testing

## Tests

`./mvnw verify` compiles every Maven module, runs every test and checks the Javadoc; it must pass on Linux and on Windows. The Android app is built and tested apart, with `cd android && ./gradlew check assembleDebug` (see [The Android app](android.md)); a change to the engine needs both, as the Android build compiles the engine's sources too. A change comes with a test that fails without it.

The engine's tests are in `engine/src/test/java/` and the desktop app's in `desktop/src/test/java/`.

- The test helpers the desktop's tests share with the engine's (`LogCapture`, `ManualScheduler`, `FakeProvider`, `SunnyDays`, `MemoryKeychain`, `LockableKeychain`) are among the engine's tests, which the engine module also packs as a test-jar that the desktop uses in test scope.
- The contract tests are there too, each checking what every implementation of an engine seam must do:
  - `HttpGetContractTest` checks every `HttpGet` against a server on the loopback address: the desktop's `JdkHttpGetTest` extends it, as the Android app's `AndroidHttpGetTest` does.
  - `FilePrivacyContractTest` checks with plain files what every `FilePrivacy` must do on any platform: the desktop's `PosixFilePrivacyTest` extends it and also checks the file modes, which the contract cannot see without POSIX types, and the Android app's `NoPrivacyContractTest` extends it to show that the inherited contract tests run in the app's tests.
  - `KeychainContractTest` checks what every `Keychain` must keep (exact values, the engine's names, deleting, threads, closing): the engine's `MemoryKeychainTest`, the desktop's `SecretServiceKeychainTest` and `CredentialManagerKeychainTest` and the Android app's `EncryptedKeychainContractTest` extend it, and add what only their system has, such as locking and the failure paths.
- `./mvnw -pl :car-charge-planner-engine verify` builds and tests the engine alone, but a change to the engine still needs the full `./mvnw verify`: the rules that hold for every class (the clock, threads, static state, logging, the one HTTP class) live in the desktop's `ArchitectureTest`, which checks the engine's classes too.

The tests never touch the network, your keychain or your desktop:

- the providers replay recorded answers (`engine/src/test/resources/.../engine/forecast/providers/`);
- the Linux keychain test runs a throwaway GNOME Keyring on a private D-Bus; it has no prompter there, so the unlock prompt's outcomes are tested against a fake Secret Service on a private D-Bus, and the notification tests run against a fake notification server on a private D-Bus that cannot start a real one;
- the Windows keychain test uses an entry of its own that it removes again (over ssh, where Windows has no logon session for the Credential Manager, it checks only that this counts as no keychain);
- `DesktopAppSmokeTest` (Linux) starts the app in a JVM of its own with the example config, on a private D-Bus with a fake tray host, headless JavaFX, private XDG folders and a proxy that refuses every connection, waits for the tray icon, sends `quit` and checks that both end cleanly; and, with a fake notification server, sends `test-notification` and checks the notification it shows;
- no test may put a window or a tray icon on the developer's desktop: the JavaFX tests run on JavaFX's headless platform, and AWT runs with `java.awt.headless`.

The Linux tests that need a program of the system (`dbus-daemon`, `gnome-keyring-daemon`, `desktop-file-validate`) start it through `SystemPrograms`, which skips the test when the program is not installed. With `CCP_REQUIRE_PLATFORM=1`, as in CI, such a test fails instead, so a missing package cannot hide as a skip. On Debian and Ubuntu the packages are `dbus-daemon`, `gnome-keyring` and `desktop-file-utils`.

All JavaFX tests share one headless platform in one JVM, so a test class that uses it is marked `@FxTest` (`Fx` refuses to run without it): each test then starts without a window, and fails when the JavaFX thread threw an exception that nothing caught. Its allow-list takes only OpenJFX bugs, each with a link. The test classes run in alphabetical order; see Random order below for a random one.

A test that only makes sense on one system says so with JUnit's `@EnabledOnOs`. Tests without one must pass on every system, so they use the location classes and not hard-coded folders.

### Architecture tests

- `ArchitectureTest` (in `desktop`, which sees the engine's classes too) keeps the packages apart: the engine knows nothing of the desktop, only the platform packages know their system, and they know only `desktop.system` of the desktop.
- `EngineArchitectureTest` (in `engine`) holds the rules about the engine's own parts, so they run when the engine is built alone. Among them: the engine uses no `java.net.http`, which Android lacks, no POSIX file modes, which it leaves to the platform's `FilePrivacy`, no keychain API of a system (D-Bus, `java.lang.foreign`, JNA), which it leaves to the platform's `Keychain`, and no programs started (`ProcessBuilder`, `Runtime.exec`) or `native` methods, which are each platform's; and in `engine.forecast` only `Providers` knows the providers' classes. It also holds ADR 0005's rules for the engine's own classes (no clock, sleep, thread or static mutable state of its own, no request address in a log, no caught `NullPointerException`, no `Optional` field or parameter), which `ArchitectureTest` holds for the rest (its frozen sleep rule, which keeps its description, sees the engine too), with one exception named in the rule: `StoredFile`'s constructor without a clock.
- `TestCodeArchitectureTest` and `EngineTestCodeArchitectureTest` hold the rules for each module's tests, the latter with the same rule against `java.net.http`.
- What older code still breaks is frozen in `desktop/src/test/archunit_store`; the engine has nothing frozen, so its rules hold without exception. No new violation is frozen unless an issue asks for it: new code keeps every rule, and a frozen rule keeps its description word for word, so its frozen violations stay matched to it.
- ArchUnit never changes the store by itself, so a frozen violation that is fixed fails the test too. To update a store on purpose, run `./mvnw test -Dtest='*ArchitectureTest' -Dsurefire.failIfNoSpecifiedTests=false -Darchunit.freeze.store.default.allowStoreUpdate=true` once, then review the changed store files and commit them. When a frozen violation only changes form (the same call through another overload, a moved line), that run fails, as ArchUnit takes the new form for a new violation: add `-Darchunit.freeze.refreeze=true`, then check in the diff that the store holds the same violations and no new ones.

## What is tested where

| When | Where | What |
| --- | --- | --- |
| Every pull request and every push to `main` | Linux CI (`ci.yml`) | `./mvnw -B clean verify -Pcoverage` with `CCP_REQUIRE_PLATFORM=1`: every test, the private-bus tests included, then the coverage floors below; the test and coverage reports are kept 7 days. |
| The same | Windows CI (`ci.yml`) | `mvnw.cmd -B clean verify`: every test, the Windows-only ones included. |
| A change to a `pom.xml`, the test set-up or `platform.windows`, before the pull request | Windows over ssh, by the maintainer | `mvnw.cmd -B clean verify`. Without a logon session the Credential Manager test only checks that this counts as no keychain. |
| A pull request into `main` that touches the engine or its tests | `mutation.yml` | PIT on the engine; it fails when fewer than 94% of the mutants are killed. |
| Weekly (early Monday, 03:23 UTC) and by hand | `mutation.yml` | PIT on the engine, on the desktop's logic without JavaFX (threshold 89%) and on the Android app's logic (job `android`, `./gradlew :app:pitestDebug`, threshold 84%, without the Kotlin compiler's own mutants), each with its HTML report for 14 days, and the whole build with the test classes in a fresh random order. |
| A pull request that touches `android/`, `engine/src/`, a `pom.xml` or `android.yml`; every push to `main` or a `release/android-*` branch; weekly and by hand | Linux CI (`android.yml`, job `android-build`) | `./gradlew assembleDebug assembleDebugAndroidTest`, then `lint`, then `check`: the Android build, lint, the unit tests with their coverage floor and the build's own checks; the lint and test reports are kept 7 days, and the debug APK 14 days (not for a pull request from a fork or from Dependabot). Not a required check yet. |
| A push to a `release/android-*` branch or a pull request into one; a pull request that changes `android.yml`; weekly (Tuesday, 04:41 UTC) and by hand | An API 29 emulator in Linux CI (`android.yml`, job `android-emulator`) | `./gradlew connectedDebugAndroidTest`: the instrumented tests, among them the Android Keystore and no API key in Logcat; their report and the emulator's logcat are kept 7 days; a hang on shutdown fails the step after 15 minutes. Not a required check yet. |
| Before each release, by hand | The maintainer's machine and real desktops | `./mvnw test -Plive` and the checklist below. |

### Coverage

`./mvnw verify -Pcoverage` (Linux only) writes JaCoCo's report of each module to its `target/site/jacoco/` (`engine/target/site/jacoco/`, `desktop/target/site/jacoco/`) and fails when a part's share of covered lines or branches drops below its floor. Each module counts only what its own tests cover: the engine's floor is met by the engine's tests alone.

| Module | Part | Lines | Branches |
| --- | --- | --- | --- |
| `engine` | `engine` | 96% | 90% |
| `desktop` | `desktop` | 95% | 82% |
| `desktop` | `instance` | 88% | 74% |
| `desktop` | `platform.linux` | 86% | 75% |
| `desktop` | `start`, `cli`, `platforms`, `logging` and `host`, without the wiring | 93% | 94% |
| `android/app` | all of `:app`, the screens included | 97% | 83% |

The classes that wire the app (`Main`, `start.DesktopApp`, `host.Host` and the `*Parts` classes in `platforms`) are left out: `DesktopAppSmokeTest` runs them in a JVM of its own, which JaCoCo does not see. `platform.windows` is not checked either, because its tests run on Windows only. The floors are a point or two below what was measured when they were set; raise them when the tests grow, rather than writing tests for the number. A floor is never lowered without the maintainer: when a change misses one, the work stops and goes to the maintainer with the numbers.

The Android app's floor is `checkCoverage` in `android/app/build.gradle.kts`, which `./gradlew check` runs (so `android.yml` does): AGP's JaCoCo report of the unit tests (`enableUnitTestCoverage` on the debug build type; the report is `app/build/reports/coverage/test/debug/`, also `./gradlew :app:createDebugUnitTestCoverageReport`), which counts what the JVM tests and the Robolectric screen tests run, the instrumented tests not. Robolectric loads the app's classes in a class loader of its own, so the tests' JaCoCo setting includes classes without a location, or the screens would count as uncovered.

### Mutation testing

PIT changes the code in small ways (a `<` for a `<=`, a dropped call) and checks that a test fails for each change. It runs from the `pit` profile, two test JVMs at a time (`-Dpit.threads=4` for more); the `engine` run is in the engine module and the `desktop` run in the desktop module, and the other module skips it:

```bash
./mvnw -Ppit -pl :car-charge-planner-engine -am test-compile pitest:mutationCoverage@engine     # report: engine/target/pit-reports/engine/index.html
./mvnw -Ppit -pl :car-charge-planner-desktop -am test-compile pitest:mutationCoverage@desktop   # report: desktop/target/pit-reports/desktop/index.html
```

The desktop run covers what the tests without JavaFX test: the mappers to the dashboard's models, the chart and popup geometry, the app settings, the logic of the Settings and set-up forms, the tray icon's drawing, the text size, the texts, `JdkHttpGet` and `PosixFilePrivacy`. The `@FxTest` classes carry the tag `fx` and are left out, because PIT does not run the JavaFX platform. That is why a form's logic (what a field shows, how a value converts, which problem applies, how the entered values become the config) lives in `desktop.settings.form`, which `ArchitectureTest` keeps free of JavaFX, and the pages only bind their controls to it.

A mutant that survives is a missing test or code that does nothing; look at it before you raise or lower a threshold. In every module, a surviving mutant in new code must be fixed (a test that kills it, or the dead code removed) in `engine/plan` and in the code that guards secrets, files and outside input: `engine/secrets`, `engine/store`, the reading of the settings and the reading of the forecast providers' answers. Everywhere else, list a survivor in the pull request and fix it when that is trivial.

The engine run leaves out what only a power cut would show (`engine/pom.xml`): the method `flushFolder`, by its name in any class (there is one), which flushes a folder after a file was moved into it, and every line that calls `FileChannel` (`avoidCallsTo`), for the flush of the file itself (`force`). That also leaves out the other lines that call `FileChannel`, in `PrivateFiles` those that open a file and write to it; `PrivateFilesTest` checks what they do. The call of `flushFolder` in `PrivateFiles.write` stays a survivor.

The Android app's logic runs from Gradle, in `android/`, with the same PIT and JUnit 5 plugin as Maven (their versions come from `pom.xml`, see [Dependencies](ci-and-releasing.md#dependencies)), PIT's default mutators, two test JVMs of 512 MB each, on the debug variant only:

```bash
./gradlew :app:pitestDebug                                                                    # report: android/app/build/reports/pitest/debug/index.html
./gradlew :app:pitestDebug -PpitClasses='nl.enadeag.carchargeplanner.android.form.CarForm*'   # only the changed classes, comma-separated
```

- It mutates the classes of `pitLogic` in `app/build.gradle.kts`: the packages `form`, `setup`, `settings`, `store`, `home`, `start`, `licences`, `logging`, `secrets`, `notify` and `prefs`, the background work's `BackgroundRules`, `BackgroundRounds`, `BackgroundSchedule` and `ReschedulingAccess`, the charts' geometry and draw list in `ui/home/ChartGeometry.kt`, the view model in `ui/AppScreen.kt`, `ui/SourceColours.kt`, and `OwnCode`, `MainThreadViolations`, `AndroidHttpGet` and `AndroidPlatform`.
- The full run fails when fewer than 84% of the mutants are killed, counted without the Kotlin compiler's own: the removed calls of its null and result checks (`Intrinsics`, `ResultKt.throwOnFailure`), a result replaced by null where the caller throws it away, and the logger's `getLOG`, which no test can kill. A result replaced by null where the caller throws it away means a `Unit` lambda's, a suspend function's that its source declares with no result or `Unit`, and a coroutine's `invokeSuspend` whose class is named after such a function, as `EngineOnDispatcher$outlook$2` is after `outlook`. Compiled, every suspend function and `invokeSuspend` returns `Object`, so `checkPitScore` reads the declaration: one with an inferred result, `= ...`, is counted, and so is a `launch { }` lambda's `Unit` in a function with a result, while an `invokeSuspend` whose class names no function in the file, and a lambda's value in a function without a result, such as `val x = withContext(..) { .. }` there, are left out though a test could see that null.
- PIT's own threshold is 0 there: `checkPitScore`, which runs after `pitestDebug`, reads the report and prints this share (PIT's report still lists those mutants), and a filter in PIT itself would drop the real mutants on the same lines. A run narrowed with `-PpitClasses` has no floor, and an empty `-PpitClasses` fails.
- The screens' Robolectric tests, with those of the notifications' words and their posting in `ui.notify` and those of the background work's alarm, WorkManager work, receivers and worker, the tests of what the build makes (the resources, the manifest, the APK and `AppEngineTest` on the example settings file) and `AppArchitectureTest` are left out by name (`excludedTestClasses` there), so a new test of that kind goes on that list; the screens and those Android parts keep hand mutants in the review.
- The settings the logic tests need in their JVM (`logicTestProperties`, such as slf4j's no-op provider) are given to the unit-test task and to PIT from one place.
- The build uses `pl.droidsonroids.pitest`, the Android fork of the Gradle PIT plugin, from Maven Central: the standard `info.solidsoft.pitest` knows no Android variants or their classpaths. The fork's classpath leaves out what the engine's test fixtures bring at run time, so `:app` gives PIT the JUnit 5 engine and the app's Java resources (`pitestRuntimeOnly`). `android/build-logic`, a plain Java build, gets the standard plugin.

### Random order

The test classes run in alphabetical order. `./mvnw verify -Dsurefire.runOrder=random` runs them in a fresh order and prints its seed; `-Dsurefire.runOrder.random.seed=<seed>` repeats that order.

### Live providers

The tests tagged `live` ask the real forecast services, on their free public plans, whether the answers still read as the recorded ones do: a forecast into tomorrow of a sensible size, and a place search. They are in the desktop module (`LiveProvidersTest`), as they ask through `JdkHttpGet`, and are left out of every build; `./mvnw test -Plive` runs only them. Forecast.Solar's public plan allows 12 requests an hour.

### On Windows, the secrets file

Where the keychain cannot be used, the API keys are kept in `secrets.json`, which the engine has the platform's `FilePrivacy` make private. On Linux the desktop's tests (`PosixFilePrivacyTest`, `PrivateFilesOnTheDesktopTest`) check that it is readable by its owner only; on Windows `PosixFilePrivacy` adds nothing, and the file relies on the access rights of the user's own `%LOCALAPPDATA%`, which no test checks.

## Before a release

On a real desktop, with the packaged app (see [Packaging](packaging.md)):

- [ ] `./mvnw test -Plive` passes.
- [ ] The tray icon shows and reads well on KDE Plasma and Windows 11, each in light and dark. GNOME joins this list once it has been tested (#52).
- [ ] Clicking a notification opens the dashboard: `car-charge-planner test-notification` shows one on demand.
- [ ] Keychain: in a desktop session the API key is kept in the keychain; without one (on Linux, no Secret Service) it falls back to `secrets.json` (the log says where the API keys are kept).
- [ ] Start at login: switch it on, log out and in again, and the app starts; switch it off, and it does not.
- [ ] Install, start, quit and uninstall: the Linux tar.gz with `install-launcher` and `uninstall-launcher`, and the Windows installer.
- [ ] Once the app is on Flathub: the Flatpak's tray icon, notifications, keychain and start at login, on KDE Plasma and GNOME.
- [ ] At 150% and 200% scaling nothing is cut off.
- [ ] Switching the system between light and dark while the app runs switches the app.
- [ ] A second start shows the running app's dashboard and ends.

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

To check the notifications without waiting for a charge window, run this while the app runs:

```bash
car-charge-planner test-notification
```

The app shows a test notification, the same as an announcement but for its text. Check that it shows the app's name and icon, then click it: the dashboard should open and come to the front. Nothing is planned or recorded for it. When the app does not run, the command says so and ends with exit code 4. In the report, say whether the name and icon showed and whether the click opened the dashboard, and if not, which notification daemon your desktop uses.
