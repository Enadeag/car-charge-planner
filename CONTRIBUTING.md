# Contributing

Thank you for helping. Small fixes can go straight to a pull request; for anything bigger, open an issue first so we can agree on the approach. Be kind: this project follows the [Code of Conduct](CODE_OF_CONDUCT.md).

## Licence of contributions

A contribution is accepted under the project's licence: the GNU General Public License, version 3 or later, with the additional term in [ADDITIONAL-TERMS.md](ADDITIONAL-TERMS.md). You keep your copyright. Do not add a licence header to the files you write: [NOTICE](NOTICE) covers them all.

## Set up

You need a JDK 25 or newer, such as Temurin. Maven comes with the repository through the wrapper: `./mvnw` on Linux, `mvnw.cmd` on Windows. Nothing else needs to be installed. The wrapper checks the Maven it downloads against `distributionSha256Sum` in `.mvn/wrapper/maven-wrapper.properties`, and `.mvn/maven.config` has Maven refuse a library whose checksum does not match (`--strict-checksums`).

CI uses the latest JDK 25; the release build uses one exact Java 25 version (see [Java updates](#java-updates)), and the packaged app brings that runtime. A newer JDK on your machine compiles the desktop app for Java 25 (`--release 25`) and the engine for Java 17 (`--release 17`), so it refuses an API that those versions lack, but the tests and `exec:exec` then run on the newer runtime, which can behave differently: Java 25's `HttpClient` timeout, for one, ends when the headers are in, which is why `JdkHttpGet` sets a deadline of its own. When a test passes for you and fails in CI, or the reverse, try it on JDK 25. `exec:exec` starts the app with the JDK that runs Maven (`JAVA_HOME`), not the first `java` on the `PATH`.

The engine is held to Java 17: `engine/pom.xml` compiles its main code and its tests with `--release 17`, so the compiler refuses a language feature or API that Java 17 lacks. The Android app compiles the engine's sources, and Android's build tools and Java library reach only about as far as Java 17 ([ADR 0010](docs/adr/0010-android-app.md)); the desktop module stays on Java 25. Java 17 has no unnamed variable `_`, so in the engine a caught exception or lambda parameter that is not used is named `ignored`.

`.editorconfig` holds the house style for editors: UTF-8, LF line ends, a final newline, 4 spaces (2 in XML, YAML, JSON and scripts), lines of at most 120 characters, except in Markdown, where a paragraph is one line. There is no formatter. In IntelliJ IDEA, set Maven home to *Bundled* (Settings, Build Tools, Maven): its Maven connector fails on the wrapper's Maven 3.10.0, while `./mvnw` builds as usual.

```bash
./mvnw verify                                                                    # build and test; nothing touches the network or your desktop
./mvnw -pl :car-charge-planner-desktop -am compile exec:exec \
    -Dapp.args="--config=docs/example-engine.json"                               # run it with the example system
```

Run Maven from the repository's root: it builds the modules in `engine/` and `desktop/` (see [Code layout](#code-layout)), and `-pl :car-charge-planner-desktop -am` picks the desktop app and what it needs. The tests of every module run in the repository's root, where they find `docs/`, `tools/` and `src/packaging/`; an IDE that runs a test itself needs the same working directory (in IntelliJ IDEA: Run, Edit Configurations, Edit configuration templates, JUnit, Working directory `$PROJECT_DIR$`, with the project opened from the root `pom.xml`). `./mvnw` sets it for you.

Without `--config=` the app uses your own `engine.json` (see [What is kept, and where](docs/privacy.md#what-is-kept-and-where)), and without one it opens set-up. `docs/example-engine.json` shows every setting, for a made-up place; its `forecast` section names the forecast provider and the fallback provider, and an optional `typical` section lists the values that are typical values, such as `"system": ["CAR_BATTERY"]`.

The app also takes the commands `quit` (asks the running app to quit), `test-notification` (asks the running app to show a test notification), `start-at-login on` and `start-at-login off`, and, on Linux, `install-launcher` and `uninstall-launcher` (the menu entry and icons), for example `-Dapp.args="quit"`. Start at login and the menu entry need the packaged app, whose launcher is what they start. Try `install-launcher` with `XDG_DATA_HOME` pointing at a scratch folder, not your real one.

### The Android app

The Android app ([ADR 0010](docs/adr/0010-android-app.md)) is built with Gradle in `android/`, apart from Maven. Gradle runs on a JDK 21 (`JAVA_HOME`); the engine is still compiled with `--release 17`. It also needs the Android SDK with platform 36 and its build-tools, which Android Studio installs: name its folder in `sdk.dir` in `android/local.properties` (git ignores the file) or in `ANDROID_HOME`. The Gradle wrapper checks the Gradle it downloads against `distributionSha256Sum` in `android/gradle/wrapper/gradle-wrapper.properties`, and every version the build uses is in `android/gradle/libs.versions.toml`. Plugins and libraries come only from Google's repository and Maven Central (`android/settings.gradle.kts`); the Gradle Plugin Portal is left out, so a plugin found only there brings it back in its own pull request, with the reason.

```bash
cd android && ./gradlew check assembleDebug    # build, lint and test; the APK is app/build/outputs/apk/debug/app-debug.apk
```

`check` runs the app's tests on the JVM, lint, and the build's own checks:

- `:engine` compiles `engine/src/main/java` as it is, and the contract tests `HttpGetContractTest`, `KeychainContractTest` and `FilePrivacyContractTest` from `engine/src/test/java`, by name, as test fixtures that the app's tests extend. They are compiled there, against the JDK, because `HttpGetContractTest`'s server is not in Android's API; the tests run on the JVM. A contract test that comes to need a helper gets it added to the list in `android/engine/build.gradle.kts`, and the build fails until it is. Its `checkMavenDrift` fails when the engine's libraries (Jackson and slf4j, and JUnit and AssertJ for the contract tests) differ from the poms, in artifact or in version (see [Dependencies](#dependencies)).
- `:engine-lint` compiles the engine's sources once more, as an Android library that nothing uses, for two checks at the app's minimum, Android 8 (API 26), with core library desugaring. Lint's `NewApi` covers the classes and methods that some Android version has: one that Android 8 lacks and desugaring does not add, such as `java.time.InstantSource`, fails the build (lint skips that check in a plain Java module, hence this library). Its `checkAndroidApiClasses` covers the classes that no Android version has, which `NewApi` does not see: it reads the compiled classes and fails on a `java.*` or `javax.*` class missing from the compileSdk's `android.jar`, such as `ProcessHandle`, naming each and the engine class that uses it (desugaring adds no class that a program compiled against the JDK can name). Lint needs the module's classes compiled; they are compiled with `--release 17`, as against Android's own JDK classes the compiler would refuse the calls that desugaring adds, such as `Files.readString`. A method that no Android version has and desugaring does not add is still not seen by either check: `EngineArchitectureTest` keeps out the larger gaps, such as `java.net.http`, and the emulator job's instrumented tests (see below) and the check on a phone before a release are the backstop.
- `checkNoGoogleLibraries`, in each module, fails when a configuration resolves to Play Services, Firebase or the Play libraries (the groups `com.google.android.gms`, `com.google.firebase` and `com.google.android.play`), which F-Droid does not allow.
- `android/build-logic/` holds these checks, with the tests that show they fail when they should.
- The app's own tests, in `app/src/test/`, run on the JVM with JUnit 5, against the stubs of Android's classes that the Android Gradle plugin provides, without Robolectric. `AndroidHttpGetTest` holds `AndroidHttpGet`, the app's `HttpGet` on `HttpURLConnection`, to `HttpGetContractTest` on the JVM's `HttpURLConnection`; `LogcatLoggingTest` checks how slf4j's records become Logcat lines, with a sink that keeps them. `AppArchitectureTest` holds the app's main code, Java and Kotlin, to ArchUnit rules as `EngineArchitectureTest` does the engine: only `AndroidHttpGet` opens network connections (no `URLConnection`, socket, OkHttp, WebView, `DownloadManager` or `java.net.http` elsewhere), no class sets up TLS of its own (a trust manager, a host name verifier, `SSLContext.init`) or changes every connection of the process (a default proxy selector, authenticator or cookie handler, `HttpURLConnection.setFollowRedirects`), and only `LogcatSink` uses `android.util.Log`, while nothing writes to `System.out` or `System.err`. `NetworkSecurityConfigTest` checks that the release build's network security config refuses plain http everywhere. `EncryptedKeychainContractTest` holds `EncryptedKeychain`, the app's keychain, to `KeychainContractTest`, with keys the JVM makes (`SoftwareKeys`) in place of the Android Keystore's, behind the store's seam for its keys (`KeychainKeys`): the file, its format, the encryption and what each failure means are the same code as on the phone. `EncryptedKeychainTest` checks how it encrypts and which failures it refuses as lost for good (a key still missing after a few looks, an entry that fails its check) and which it reports as unavailable (a file it cannot read, a Keystore that fails for now), as without the fallback to `secrets.json` a refused `index` starts a new one, and that it never removes a key or an entry: a lost key is followed by a new one under a new alias, and an old key that comes back still opens its entries. `AndroidKeystoreKeysTest` checks which Keystore failures count as lost for good on the JVM. `AndroidPlatformTest` checks the parts the app hands the engine: the API keys only in `keychain.json` in the folder that is never backed up, never in a file of their own, and the engine's files in the app's files folder. `DataExtractionRulesTest` reads the backup rules, which keep only the keychain's file on the phone. As that stub fails when it is called, slf4j in these tests uses its no-op provider (`slf4j.provider` in `app/build.gradle.kts`), so the engine's records go nowhere there.

The instrumented tests, in `app/src/androidTest/`, run on a device or an emulator with AndroidX Test, which is in their test APK only, never in the app: `OnTheDeviceTest`, which starts the app and has the engine plan the made-up sunny day on the device, through a `java.time` method that desugaring adds; `HttpOnTheDeviceTest`, which checks what Android's own `HttpURLConnection`, built on OkHttp, could do differently from the JVM's (compression, a connection that ends too soon, redirects, a repeated header, the network security config), against a small server on the device's loopback address; `LoggingOnTheDeviceTest`, which checks that slf4j finds the app's provider and a record reaches Logcat; `KeystoreOnTheDeviceTest`, which runs the keychain on keys of its own in the real Android Keystore (made at the first write, AES-256 for GCM, not readable out, no owner needed to use it, randomized encryption) and checks that a changed entry and a lost key give `REFUSED` and that a lost key is followed by one under a new alias; `AndroidPlatformOnTheDeviceTest`, which checks that the app has one set of platform parts, in its files folder and its folder that is never backed up; and `KeysNotInLogcatOnTheDeviceTest`, which stores, reads and loses a made-up API key and checks that the app's own Logcat lines never hold it, as the emulator job publishes them. The device tests use a Keystore alias of their own, never the app's. The app refuses plain http, except in the debug build, to `localhost` and `127.0.0.1` only, for that server (`src/debug/res/xml/network_security_config.xml`). `./gradlew assembleDebugAndroidTest` builds their APK, and `./gradlew connectedDebugAndroidTest` runs them on the emulator or phone that `adb` sees.

CI builds the app with `android.yml`, on Linux with JDK 21, in two jobs. The build job runs `assembleDebug assembleDebugAndroidTest`, `lint` and `check` as separate steps, for a pull request that touches `android/`, the engine's sources, a `pom.xml` or `android.yml`, and for every push to `main` or a `release/android-*` branch, weekly and by hand. The emulator job runs `connectedDebugAndroidTest` on an Android 8 (API 26) emulator, the AOSP image without Google's apps: for a push to a `release/android-*` branch and a pull request into one, weekly, by hand, and for a pull request that changes `android.yml`, so a change to the job is tried before it is merged; not for other pull requests. Neither is a required check yet: look at them before merging, until the maintainer makes them required once they run stably.

To try the app, open `android/` in Android Studio and run it on an emulator from its Device Manager (any system image from API 26) or on a phone; or start an emulator and run `adb install app/build/outputs/apk/debug/app-debug.apk` from `android/`. For now it shows today's charge windows for a made-up sunny day and a car written to JSON and read back, without network, keychain or files.

## Logging

The app writes its log to `car-charge-planner.0.log` in its data folder (`~/.local/share/car-charge-planner` on Linux, `%LOCALAPPDATA%\car-charge-planner` on Windows; Settings, About shows the file), one line per record with the time, level, logger and message, and an exception's stack trace on the lines after it. At 1 MB the file becomes `.1.log` and the one before `.2.log`; older ones are dropped. The files and their folder are readable only by you. When the app runs in a terminal it shows the log there too; otherwise follow the file, for example with `tail -F ~/.local/share/car-charge-planner/car-charge-planner.0.log`.

It logs from INFO. For more of the app's own records, set `CAR_CHARGE_PLANNER_LOG_LEVEL` to `DEBUG` or `TRACE` (one of `System.Logger.Level`'s names) before you start it, such as `CAR_CHARGE_PLANNER_LOG_LEVEL=DEBUG ./mvnw -pl :car-charge-planner-desktop -am compile exec:exec`; the libraries stay at INFO. Logging is set up once, in `DesktopApp` (`AppLog`); what a thread throws and nothing catches is logged as an error (`UncaughtLogging`).

The Android app writes its log to Logcat only, no file: from INFO in the release build and from DEBUG in the debug build (`BuildConfig.DEBUG`), each line tagged with the logging class's simple name, through the app's own slf4j provider (`LogcatServiceProvider`); follow it with `adb logcat` or in Android Studio.

In the code, log through `System.Logger` in the desktop app, through slf4j in the Android app's own code, and through slf4j (`LoggerFactory.getLogger(X.class)`, with the text concatenated, no `{}`) in the engine, which `EngineArchitectureTest` holds to it, so each platform can bind it to its own log (ADR 0010). The desktop sends slf4j's records on to `java.util.logging`, so they reach the same log. The engine logs at debug or above: slf4j's trace becomes `FINEST`, below the `FINER` that `CAR_CHARGE_PLANNER_LOG_LEVEL=TRACE` lets through, so an engine trace line would never reach the log. Log a change of state, such as a provider that starts failing, not what happens every minute. Never log an API key, a request's address (it can hold a key) or the home's coordinates; a path is fine, although it shows the user name. `ArchitectureTest` keeps every class that makes or reads a request's address from logging at all, and the provider conformance kit checks that no address, key or coordinate reaches a log line or the reason the owner is shown.

The engine logs these changes of state, each once: a provider that gives no forecast and one that gives forecasts again (`ProviderHealth`), a settings file that cannot be used and one that can be used again (`ConfigHealth`), where the API keys are kept (`ApiKeys`), a stored file that cannot be used or is set aside (`StoredFile`), and each announcement and notice about a trip (`Announcer`, `TripNotices`).

## Tests

`./mvnw verify` compiles every Maven module, runs every test and checks the Javadoc; it must pass on Linux and on Windows. The Android app is built and tested apart, with `cd android && ./gradlew check assembleDebug` (see [The Android app](#the-android-app)); a change to the engine needs both, as the Android build compiles the engine's sources too. The engine's tests are in `engine/src/test/java/` and the desktop app's in `desktop/src/test/java/`. The test helpers the desktop's tests share with the engine's (`LogCapture`, `ManualScheduler`, `FakeProvider`, `SunnyDays`, `MemoryKeychain`, `LockableKeychain`) are among the engine's tests, which the engine module also packs as a test-jar that the desktop uses in test scope. The contract tests are there too, each checking what every implementation of an engine seam must do. `HttpGetContractTest` checks every `HttpGet` against a server on the loopback address: the desktop's `JdkHttpGetTest` extends it, as the Android app's `AndroidHttpGetTest` does. `FilePrivacyContractTest` checks with plain files what every `FilePrivacy` must do on any platform: the desktop's `PosixFilePrivacyTest` extends it and also checks the file modes, which the contract cannot see without POSIX types, and the Android app's `NoPrivacyContractTest` extends it to show that the inherited contract tests run in the app's tests. `KeychainContractTest` checks what every `Keychain` must keep (exact values, the engine's names, deleting, threads, closing): the engine's `MemoryKeychainTest`, the desktop's `SecretServiceKeychainTest` and `CredentialManagerKeychainTest` and the Android app's `EncryptedKeychainContractTest` extend it, and add what only their system has, such as locking and the failure paths. `./mvnw -pl :car-charge-planner-engine verify` builds and tests the engine alone, but a change to the engine still needs the full `./mvnw verify`: the rules that hold for every class (the clock, threads, static state, logging, the one HTTP class) live in the desktop's `ArchitectureTest`, which checks the engine's classes too. The tests never touch the network, your keychain or your desktop:

- the providers replay recorded answers (`engine/src/test/resources/.../engine/forecast/providers/`);
- the Linux keychain test runs a throwaway GNOME Keyring on a private D-Bus; it has no prompter there, so the unlock prompt's outcomes are tested against a fake Secret Service on a private D-Bus, and the notification tests run against a fake notification server on a private D-Bus that cannot start a real one;
- the Windows keychain test uses an entry of its own that it removes again (over ssh, where Windows has no logon session for the Credential Manager, it checks only that this counts as no keychain);
- `DesktopAppSmokeTest` (Linux) starts the app in a JVM of its own with the example config, on a private D-Bus with a fake tray host, headless JavaFX, private XDG folders and a proxy that refuses every connection, waits for the tray icon, sends `quit` and checks that both end cleanly; and, with a fake notification server, sends `test-notification` and checks the notification it shows;
- no test may put a window or a tray icon on the developer's desktop: the JavaFX tests run on JavaFX's headless platform, and AWT runs with `java.awt.headless`.

The Linux tests that need a program of the system (`dbus-daemon`, `gnome-keyring-daemon`, `desktop-file-validate`) start it through `SystemPrograms`, which skips the test when the program is not installed. With `CCP_REQUIRE_PLATFORM=1`, as in CI, such a test fails instead, so a missing package cannot hide as a skip. On Debian and Ubuntu the packages are `dbus-daemon`, `gnome-keyring` and `desktop-file-utils`.

All JavaFX tests share one headless platform in one JVM, so a test class that uses it is marked `@FxTest` (`Fx` refuses to run without it): each test then starts without a window, and fails when the JavaFX thread threw an exception that nothing caught. Its allow-list takes only OpenJFX bugs, each with a link. The test classes run in alphabetical order; [What is tested where](#what-is-tested-where) says how to run them in a random one.

A test that only makes sense on one system says so with JUnit's `@EnabledOnOs`. Tests without one must pass on every system, so they use the location classes and not hard-coded folders. `ArchitectureTest` (in `desktop`, which sees the engine's classes too) keeps the packages apart: the engine knows nothing of the desktop, only the platform packages know their system, and they know only `desktop.system` of the desktop. `EngineArchitectureTest` (in `engine`) holds the rules about the engine's own parts, so they run when the engine is built alone, among them that the engine uses no `java.net.http`, which Android lacks, no POSIX file modes, which it leaves to the platform's `FilePrivacy`, no keychain API of a system (D-Bus, `java.lang.foreign`, JNA), which it leaves to the platform's `Keychain`, and no programs started (`ProcessBuilder`, `Runtime.exec`) or `native` methods, which are each platform's. `TestCodeArchitectureTest` and `EngineTestCodeArchitectureTest` hold the rules for each module's tests, the latter with the same rule against `java.net.http`. What older code still breaks is frozen in `desktop/src/test/archunit_store`; the engine has nothing frozen, so its rules hold without exception. ArchUnit never changes the store by itself, so a frozen violation that is fixed fails the test too. To update a store on purpose, run `./mvnw test -Dtest='*ArchitectureTest' -Dsurefire.failIfNoSpecifiedTests=false -Darchunit.freeze.store.default.allowStoreUpdate=true` once, then review the changed store files and commit them. When a frozen violation only changes form (the same call through another overload, a moved line), that run fails, as ArchUnit takes the new form for a new violation: add `-Darchunit.freeze.refreeze=true`, then check in the diff that the store holds the same violations and no new ones.

A change comes with a test that fails without it.

## What is tested where

| When | Where | What |
| --- | --- | --- |
| Every pull request and every push to `main` | Linux CI (`ci.yml`) | `./mvnw -B clean verify -Pcoverage` with `CCP_REQUIRE_PLATFORM=1`: every test, the private-bus tests included, then the coverage floors below; the test and coverage reports are kept 7 days. |
| The same | Windows CI (`ci.yml`) | `mvnw.cmd -B clean verify`: every test, the Windows-only ones included. |
| A change to a `pom.xml`, the test set-up or `platform.windows`, before the pull request | Windows over ssh, by the maintainer | `mvnw.cmd -B clean verify`. Without a logon session the Credential Manager test only checks that this counts as no keychain. |
| A pull request into `main` that touches the engine or its tests | `mutation.yml` | PIT on the engine; it fails when fewer than 94% of the mutants are killed. |
| Weekly (early Monday, 03:23 UTC) and by hand | `mutation.yml` | PIT on the engine and on the desktop's logic without JavaFX (threshold 89%), each with its HTML report for 14 days, and the whole build with the test classes in a fresh random order. |
| A pull request that touches `android/`, `engine/src/`, a `pom.xml` or `android.yml`; every push to `main` or a `release/android-*` branch; weekly and by hand | Linux CI (`android.yml`, job `android-build`) | `./gradlew assembleDebug assembleDebugAndroidTest`, then `lint`, then `check`: the Android build, lint, the unit tests and the build's own checks; the lint and test reports are kept 7 days. Not a required check yet. |
| A push to a `release/android-*` branch or a pull request into one; a pull request that changes `android.yml`; weekly (Tuesday, 04:41 UTC) and by hand | An API 26 emulator in Linux CI (`android.yml`, job `android-emulator`) | `./gradlew connectedDebugAndroidTest`: the instrumented tests, among them the Android Keystore and no API key in Logcat; their report and the emulator's logcat are kept 7 days. Not a required check yet. |
| Before each release, by hand | The maintainer's machine and real desktops | `./mvnw test -Plive` and the checklist below. |
| Not yet | macOS | The CI job is off until the JavaFX tests run on the macOS runner (#134); a person checks a release on a Mac (#29). |

**Coverage.** `./mvnw verify -Pcoverage` (Linux only) writes JaCoCo's report of each module to its `target/site/jacoco/` (`engine/target/site/jacoco/`, `desktop/target/site/jacoco/`) and fails when a part's share of covered lines or branches drops below its floor. Each module counts only what its own tests cover: the engine's floor is met by the engine's tests alone.

| Module | Part | Lines | Branches |
| --- | --- | --- | --- |
| `engine` | `engine` | 96% | 90% |
| `desktop` | `desktop` | 95% | 82% |
| `desktop` | `instance` | 88% | 74% |
| `desktop` | `platform.linux` | 86% | 75% |
| `desktop` | `start`, `cli`, `platforms`, `logging` and `host`, without the wiring | 93% | 94% |

The classes that wire the app (`Main`, `start.DesktopApp`, `host.Host` and the `*Parts` classes in `platforms`) are left out: `DesktopAppSmokeTest` runs them in a JVM of its own, which JaCoCo does not see. `platform.windows` is not checked either, because its tests run on Windows only. The floors are a point or two below what was measured when they were set; raise them when the tests grow, rather than writing tests for the number.

**Mutation testing.** PIT changes the code in small ways (a `<` for a `<=`, a dropped call) and checks that a test fails for each change. It runs from the `pit` profile, two test JVMs at a time (`-Dpit.threads=4` for more); the `engine` run is in the engine module and the `desktop` run in the desktop module, and the other module skips it:

```bash
./mvnw -Ppit -pl :car-charge-planner-engine -am test-compile pitest:mutationCoverage@engine     # report: engine/target/pit-reports/engine/index.html
./mvnw -Ppit -pl :car-charge-planner-desktop -am test-compile pitest:mutationCoverage@desktop   # report: desktop/target/pit-reports/desktop/index.html
```

The desktop run covers what the tests without JavaFX test: the mappers to the dashboard's models, the chart and popup geometry, the app settings, the logic of the Settings and set-up forms, the tray icon's drawing, the text size, the texts, `JdkHttpGet` and `PosixFilePrivacy`. The `@FxTest` classes carry the tag `fx` and are left out, because PIT does not run the JavaFX platform. That is why a form's logic (what a field shows, how a value converts, which problem applies, how the entered values become the config) lives in `desktop.settings.form`, which `ArchitectureTest` keeps free of JavaFX, and the pages only bind their controls to it. A mutant that survives is a missing test or code that does nothing; look at it before you raise or lower a threshold.

**Random order.** The test classes run in alphabetical order. `./mvnw verify -Dsurefire.runOrder=random` runs them in a fresh order and prints its seed; `-Dsurefire.runOrder.random.seed=<seed>` repeats that order.

**Live providers.** The tests tagged `live` ask the real forecast services, on their free public plans, whether the answers still read as the recorded ones do: a forecast into tomorrow of a sensible size, and a place search. They are in the desktop module (`LiveProvidersTest`), as they ask through `JdkHttpGet`, and are left out of every build; `./mvnw test -Plive` runs only them. Forecast.Solar's public plan allows 12 requests an hour.

**On Windows, the secrets file.** Where the keychain cannot be used, the API keys are kept in `secrets.json`, which the engine has the platform's `FilePrivacy` make private. On Linux the desktop's tests (`PosixFilePrivacyTest`, `PrivateFilesOnTheDesktopTest`) check that it is readable by its owner only; on Windows `PosixFilePrivacy` adds nothing, and the file relies on the access rights of the user's own `%LOCALAPPDATA%`, which no test checks.

### Before a release

On a real desktop, with the packaged app (see [Packaging](#packaging)):

- [ ] `./mvnw test -Plive` passes.
- [ ] The tray icon shows and reads well on KDE Plasma and Windows 11, each in light and dark. GNOME joins this list once it has been tested (#52).
- [ ] Clicking a notification opens the dashboard: `car-charge-planner test-notification` shows one on demand.
- [ ] Keychain: in a desktop session the API key is kept in the keychain; without one (on Linux, no Secret Service) it falls back to `secrets.json` (the log says where the API keys are kept).
- [ ] Start at login: switch it on, log out and in again, and the app starts; switch it off, and it does not.
- [ ] Install, start, quit and uninstall: the Linux tar.gz with `install-launcher` and `uninstall-launcher`, and the Windows installer.
- [ ] Once the app is on Flathub: the Flatpak's tray icon, notifications, keychain and start at login, on KDE Plasma and GNOME.
- [ ] The dashboard, set-up and Settings read well with Narrator on Windows (Orca on Linux cannot read it yet, #132).
- [ ] At 150% and 200% scaling nothing is cut off.
- [ ] Switching the system between light and dark while the app runs switches the app.
- [ ] A second start shows the running app's dashboard and ends.

## Pictures in the documentation

The pictures that the README and the pages in `docs/` show (`docs/screenshots/java/`) are made by code, each in light and in dark, as `<name>-light.png` and `<name>-dark.png`. After a change to what a pictured screen shows, make them again and commit the ones that changed:

```bash
./mvnw test -Pscreenshots    # prints which pictures changed, or "No picture changed."
```

It runs like the other JavaFX tests: offscreen on JavaFX's headless platform, in a JVM that cannot reach your display. The screens show the example home of the tests, in English: `docs/example-engine.json`, the made-up place Zonnedorp, a forecast made up in the code and a fixed date and time, so the pictures are the same on every run. A file is written only when its pixels change, and holds nothing but the pixels, so a run without a change leaves git clean. `ReadmePictures` lists the pictures, and `DashboardPictures`, `SetUpPictures` and `SettingsPictures` make them; `ReadmePicturesTest` makes them all into a temporary folder in every build, and checks that each picture the README and the `docs/*.md` pages name is made and that a second run changes nothing. A new picture goes in `ReadmePictures`, and in the page that shows it as a `<picture>` with its dark file as the `prefers-color-scheme: dark` source; a page in `docs/` names it relative to itself, as `screenshots/java/<name>-light.png`.

The text is drawn in JavaFX's system font, which differs per system (Noto Sans on most Linux desktops, Segoe UI on Windows): make the pictures on Linux with Noto Sans, or a run changes every picture.

## Dependencies

Each module declares every library its code uses directly in its own `pom.xml`, also one that already comes in through another library or module, without a version: the versions are in the parent `pom.xml` (`dependencyManagement`, and the plugins' in `pluginManagement`). `./mvnw verify` runs `dependency:analyze` in every module and fails on a library that is used but not declared, or declared but not used. A library that is only found at run time and never named in the code (such as the SLF4J binding) goes in the list of ignored ones in the parent, with the reason. Keep the third-party table in [docs/privacy.md](docs/privacy.md#third-party-software) up to date.

The Android build declares the engine's libraries again, with their versions in `android/gradle/libs.versions.toml`. A pull request that changes the version of Jackson, slf4j, JUnit or AssertJ in the poms, or the engine's libraries, changes the catalog (its versions, and its `engine` bundle, the libraries `:engine` and `:engine-lint` both declare) in the same pull request: `checkMavenDrift` fails until the two builds agree. Dependabot leaves those four out of its Android updates for that reason.

Each library the app brings at run time, also one that comes in through another library, needs its licence text in `src/packaging/legal/`, which the packages carry in their `legal` folder: one file per component, starting with its name, an `Artifact: groupId:artifactId` line per jar it covers, its source and its licence, then the licence and notice texts as the jar's `META-INF` has them (or the project's own `LICENSE` at that release when the jar has none). `PackagingTest` fails when a runtime library has no file there, or a file names a library the app no longer brings. The app's own modules (`nl.enadeag:*`, such as the engine's jar) are under the app's own licence and need no file.

## Packaging

Each package brings a runtime made by jlink, so users need no Java of their own, and comes with an SBOM (CycloneDX JSON) of what it holds: `cyclonedx-maven-plugin` lists the libraries, the engine among them, in `desktop/target/car-charge-planner.cdx.json` at `package`, and `tools/SbomRuntime.java` adds the runtime, from the JDK that ran jlink. The jar, the SBOM and the Linux tar.gz take the time of the last commit (or `SOURCE_DATE_EPOCH`), and the tar.gz holds its files in name order, owned by root and not writable by group or others, so the same commit and JDK give the same tar.gz. The packages are not code-signed (ADR 0003); users check them with `SHA256SUMS` and the attestation. Both scripts take jlink's options, such as the modules and locales, from `src/packaging/jlink.args`, and link JavaFX from the official jmods, downloaded from a pinned address and checked against a pinned SHA-256; they need `curl` and `git` (and `unzip` and GNU tar on Linux). The scripts take the app's jar and its libraries from `desktop/target/` (`car-charge-planner.jar` and `lib/`), do their own work in `target/` at the root and put the products in `target/dist/`. A new JavaFX version in the parent `pom.xml` needs the new address and checksum in both scripts (`PackagingTest` checks the version). Each package has a `legal` folder (in the `.deb` and `.rpm` `lib/legal`, as jpackage packages only `bin` and `lib`) with `LICENSE`, `NOTICE`, `ADDITIONAL-TERMS.md` and the files of `src/packaging/legal/`, and the Windows installer shows the GPL.

- **Linux:** `sh tools/package-linux.sh` makes `target/dist/car-charge-planner/` (start `bin/car-charge-planner`), `target/dist/car-charge-planner-<version>-linux.tar.gz` and its SBOM `car-charge-planner-<version>-linux.cdx.json`, and from the same app image `car-charge-planner_<version>_amd64.deb` and `car-charge-planner-<version>-1.x86_64.rpm` (jpackage's own names; ADR 0007). The packages install the app in `/opt/car-charge-planner` and a menu entry for every user, `car-charge-planner-car-charge-planner.desktop` (jpackage names it after the package and the launcher), made from `src/packaging/linux/car-charge-planner.desktop`; its icon is the 256-pixel PNG in the app's `lib` folder, not an icon theme. The system libraries they depend on are listed by hand, as jpackage would take the build machine's package names: in `src/packaging/linux/control` for the `.deb` (Debian and Ubuntu names) and in the script for the `.rpm` (by soname); a JavaFX or Java update that links another library needs it in both (`readelf -d` on the app image shows them). The tar.gz's SBOM describes the packages too, as they hold the same libraries and runtime. They need `dpkg-deb` and `fakeroot` for the `.deb` and `rpmbuild` for the `.rpm` (Ubuntu: `apt install fakeroot rpm`); the script stops before the build when one is missing, and `--tar-only` skips the packages. `sh tools/test-linux-packages.sh target/dist` installs them in fresh `debian:stable` and `fedora:latest` Docker containers, runs the app's commands there and removes them again.
- **Windows:** `powershell -ExecutionPolicy Bypass -File tools\package-windows.ps1` makes a per-user installer, `target\dist\car-charge-planner-<version>.exe`, and its SBOM `car-charge-planner-<version>-windows.cdx.json`; `-Type app-image` makes the program folder only. The installer needs WiX 5: install the .NET SDK 8, then run `powershell -ExecutionPolicy Bypass -File tools\install-wix.ps1`, which installs WiX 5.0.2 and its Util and UI extensions for you from packages checked against pinned SHA-256 sums. WiX 6 and later ask to accept a maintenance fee agreement first.

`sh tools/scan-sbom.sh target/dist/*.cdx.json` checks SBOMs against the [OSV](https://osv.dev) vulnerability database with a pinned, checksum-checked OSV-Scanner, and fails on any known vulnerability; the release workflow runs it on both SBOMs, the Linux one and the Windows one. A finding that was looked at and does not reach the app goes in `osv-scanner.toml` at the repository root, with the reason and a date it expires. OSV knows the libraries, not the Java runtime: that is what [Java updates](#java-updates) is for.

The app icon's drawings are in `src/packaging/icons/`: `car-charge-planner.svg` is the icon the PNGs in `desktop/src/main/resources/.../desktop/system/icons/` and `src/packaging/windows/car-charge-planner.ico` show, and `car-charge-planner-symbolic.svg` has the shapes the tray icon draws (`StatusIcon`).

### Flatpak

The Flatpak ([ADR 0008](docs/adr/0008-flatpak.md)) is built from source by flatpak-builder, offline, from the files in `src/packaging/flatpak/`: the manifest `<app ID>.yml`, the AppStream metadata `<app ID>.metainfo.xml`, the menu entry `<app ID>.desktop`, `maven-sources.json` and `flathub.json`. The manifest runs `tools/package-flatpak.sh`, which builds the same app image as `tools/package-linux.sh` with the JDK 25 of the `org.freedesktop.Sdk.Extension.openjdk25` extension and installs it in `/app`, with the menu entry, the metadata, the icons and the licences. It is not on Flathub yet (#217), so the install guide does not mention it.

To build and try it, with `flatpak-builder` installed and Flathub added as a remote:

```bash
flatpak install --user flathub org.freedesktop.Platform//26.08 org.freedesktop.Sdk//26.08 org.freedesktop.Sdk.Extension.openjdk25//26.08
flatpak-builder --user --install --force-clean build-dir src/packaging/flatpak/io.github.enadeag.car_charge_planner.CarChargePlanner.yml
flatpak run io.github.enadeag.car_charge_planner.CarChargePlanner
```

`flatpak run --command=car-charge-planner io.github.enadeag.car_charge_planner.CarChargePlanner quit` runs a command in the sandbox. In the sandbox the settings and data are in `~/.var/app/io.github.enadeag.car_charge_planner.CarChargePlanner/`, not in `~/.config` and `~/.local/share`.

**The Maven sources.** flatpak-builder cannot download during the build, so `maven-sources.json` lists every file the Maven build needs (the libraries, the plugins and what they need) with its address on Maven Central and its SHA-256. After a change to a `pom.xml`, the Maven version in `.mvn/wrapper/maven-wrapper.properties` or the Maven build in `tools/package-flatpak.sh`, make it again and commit it: `sh tools/flatpak-maven-sources.sh` (it builds the app once, online, into an empty Maven repository, and fails on a file whose SHA-1 is not the one Maven Central publishes). A new Maven or JavaFX version needs its address and checksum in the manifest too; `FlatpakPackagingTest` checks them against the wrapper and `tools/package-linux.sh`, and checks that the manifest's source folder leaves out the root's `target` and each module's, and `android/`, with the Android build's output and its `local.properties`.

**The permissions** (`finish-args`) are only what the app uses, each with its reason in the manifest; `FlatpakPackagingTest` fails on any other, so a new one is a deliberate change to the test, the manifest and ADR 0008.

**The app ID** `io.github.enadeag.car_charge_planner.CarChargePlanner` is Flathub's form for an app on GitHub: `io.github.<user>.<repository>.<name>`, with the repository's dashes as underscores; Flathub checks that the repository exists. Changing it changes:

- `AppName.FLATPAK_ID` in `desktop/src/main/java/nl/enadeag/carchargeplanner/host/AppName.java`: the notifications' menu entry and the shared runtime folder in the sandbox;
- the names of the manifest, the metadata and the menu entry in `src/packaging/flatpak/`, and inside them the manifest's `id:` and its comment, the metadata's `<id>` and `<launchable>`, and the menu entry's `Icon=`;
- the commands above, and the ID in ADR 0008.

`tools/package-flatpak.sh` takes the ID from flatpak-builder (`FLATPAK_ID`), and `FlatpakPackagingTest` checks that the files agree with `AppName.FLATPAK_ID`. The keychain entries, the launcher and the data folders inside `~/.var/app/<app ID>/` keep the name `car-charge-planner`.

## Continuous integration

The workflows are in `.github/workflows/`:

- `ci.yml` runs `./mvnw -B clean verify` for every pull request and every push to `main`, on Linux (with `dbus-daemon`, GNOME Keyring and `desktop-file-validate`, so the private-bus tests run, with `CCP_REQUIRE_PLATFORM=1` and the coverage floors) and on Windows. It keeps each module's test reports, and on Linux each module's coverage report, for 7 days. A change to documentation only (`*.md`, `docs/` except `docs/example-engine.json`, which the tests read) skips both, and GitHub counts a skipped job as passed. The macOS job is switched off until the JavaFX tests run on the macOS runner (#134); the maintainer decides when it comes back.
- `android.yml` builds the Android app and runs its lint, unit tests and checks with JDK 21, and its instrumented tests on an API 26 emulator; see [The Android app](#the-android-app) for when each job runs. A job a pull request does not need is skipped, which GitHub counts as passed. `gradle/actions/setup-gradle` checks `gradle-wrapper.jar` against Gradle's checksums and caches Gradle's downloads with its open-source provider, written from `main` only. It keeps the lint and test reports, and the emulator's logcat, for 7 days; no APK.
- `linux-packages.yml` builds the `.deb` and `.rpm` and runs `tools/test-linux-packages.sh` on them, for a pull request or a push to `main` that changes the packaging or any `pom.xml`, and by hand.
- `windows-installer.yml` builds the installer with `tools\package-windows.ps1`, installs it silently, runs the installed `car-charge-planner.exe quit`, uninstalls it and keeps the installer as an artifact for 14 days. It runs when the packaging changes on `main`, by hand, and for a release.
- `mutation.yml` runs PIT and the tests in a random order, weekly and by hand, and PIT on the engine for a pull request that touches it; see [What is tested where](#what-is-tested-where).
- `release.yml` makes a release; see below.

The actions are pinned to a full commit SHA, with the version in a comment beside it: update both together. Dependabot (`.github/dependabot.yml`) proposes new versions of the actions, of what the poms name (from the root, which reaches the modules) and of what the Android build's catalog names, weekly and a week after their release, as pull requests that CI builds like any other.

## Releasing

The app has one version, `<revision>` in the parent `pom.xml` at the root; every module inherits it (its `<parent>` names it as `${revision}`), and the scripts read it from the parent alone (`./mvnw -N help:evaluate -Dexpression=project.version`). Between releases it is the next one with `-SNAPSHOT`, such as `0.3.0-SNAPSHOT`. The tagged commit carries the exact version, because the app shows it in About and the download names carry it. To release X.Y.Z:

1. Go through [Before a release](#before-a-release).
2. In a pull request, set `<revision>X.Y.Z</revision>` in the parent `pom.xml`, turn `## [Unreleased]` in `CHANGELOG.md` into `## [X.Y.Z] - YYYY-MM-DD`, and add `<release version="X.Y.Z" date="YYYY-MM-DD">` with a short description at the top of `<releases>` in `src/packaging/flatpak/<app ID>.metainfo.xml` (`FlatpakPackagingTest` checks that it matches the changelog).
3. After the merge, tag that commit `vX.Y.Z` and push the tag. The release workflow stops when the tag is not `v` plus the version in the parent `pom.xml` (`ReleaseWorkflowTest` checks that it reads it with `-N`), when that version still ends in `-SNAPSHOT`, when `CHANGELOG.md` has no section for it, or when `release/X.Y.Z` exists at another commit. Otherwise it builds and tests, packages the Linux tar.gz, `.deb` and `.rpm` and the Windows installer with an SBOM for Linux and one for Windows, smoke-tests the tar.gz, the `.deb` (installed on the runner and removed again) and the installer, checks the files in the `.rpm`, stops when OSV-Scanner finds a known vulnerability in an SBOM, writes `SHA256SUMS` over the six files, attests where they were built (`actions/attest-build-provenance`), creates the branch `release/X.Y.Z` at the tag, and makes a draft release with the changelog section, install notes and the Java runtime (`java tools/ReleaseNotes.java X.Y.Z CHANGELOG.md`, made before the job that can write to the repository).
4. Read the draft and publish it by hand.
5. In a pull request, set the version to the next `-SNAPSHOT` the same way and add an empty `## [Unreleased]`.

To try the workflow without releasing, start it by hand on `main`: it builds everything and makes a draft called "dry run" (delete it afterwards), without the tag check, the attestations or the release branch.

### Java updates

The release builds use one exact Java version, the `java-version` of each `setup-java` in `release.yml` and `windows-installer.yml` (`ReleaseWorkflowTest` keeps them the same), and the packages bring that runtime: a user's app gets Java's security fixes only through a new release. Java's security updates come quarterly, on the third Tuesday of January, April, July and October. After each one:

1. Read the advisory of the OpenJDK Vulnerability Group (<https://openjdk.org/groups/vulnerability/advisories/>) and check which fixed vulnerabilities reach the app: the modules it links are in `src/packaging/jlink.args` and in the SBOM (`jlink.modules`); what matters most is what a forecast provider's answer reaches: TLS, the HTTP client and the JSON parsing.
2. Once Temurin has the update, set its exact version (such as `25.0.5+11`) in both workflows in a pull request, and run `sh tools/scan-sbom.sh` on fresh SBOMs for the libraries.
3. When a fixed vulnerability reaches the app, release a patch version on the new runtime; otherwise the next release takes it along. The release notes and the SBOMs name the runtime each release brings.

## Code layout

Everything is in the package `nl.enadeag.carchargeplanner`, in two Maven modules under one parent `pom.xml`, and the Android app's Gradle build in `android/` ([ADR 0010](docs/adr/0010-android-app.md)):

| Module | Folder | What it holds |
| --- | --- | --- |
| `car-charge-planner-engine` | `engine/` | The package `engine` and its tests: no user interface, desktop or platform, so the Android app can share it. Its test classes, with the test helpers the desktop's tests use, also go in a test-jar. |
| `car-charge-planner-desktop` | `desktop/` | Every other package below and their tests, the texts, icons and version file; it builds `car-charge-planner.jar`, its libraries in `desktop/target/lib` and the SBOM. |
| `:app`, `:engine`, `:engine-lint` (Gradle) | `android/` | The Android app in `nl.enadeag.carchargeplanner.android` (`app/`, Kotlin with Compose), the engine's sources and contract tests compiled for it (`engine/`, `engine-lint/`, which hold only build files), and the build's own checks (`build-logic/`); see [The Android app](#the-android-app). |

In each Maven module the code is in `src/main/java/` and the tests in `src/test/java/`, in the same packages; in the Android app, in `src/main/kotlin/` and `src/test/kotlin/`, and its classes in plain Java (`AndroidHttpGet`, the slf4j provider, the keychain in `android.secrets` and `AndroidPlatform`, which makes the engine's platform parts) in `src/main/java/`. The packaging (`src/packaging/`), `tools/` and `docs/` stay at the root.

| Package | What it holds |
| --- | --- |
| `engine` | Plans, fetches forecasts and owns the stored data, without user interface; clients get plain records. `engine.config` the settings file (`engine.json`), `engine.setting` the names of its settings and what is wrong with one, `engine.store` how every stored file is read, written, versioned and kept private (`PrivateFiles`, and `FilePrivacy`, the way each platform makes a file private), `engine.away` the away periods (weekly, and the ones entered for a specific day and kept in `away.json`), `engine.trip` the trips (kept in `trips.json`), `engine.battery` the battery level the owner entered last (kept in `battery.json`), `engine.forecast` the provider contract, `HttpGet` (the providers' way to the network, which each platform fills) and the forecast service (fallback, cache, rate limits), `engine.forecast.providers` Open-Meteo and Forecast.Solar, `engine.secrets` the API keys, `engine.plan` the planner, `engine.notify` when the owner is told about a window or a trip (the notifier port, the announced windows kept in `announced.json`, and what was told about each trip, kept in `trip-notices.json`). |
| `desktop` | The client on the desktop, in parts that depend one way: `desktop.system` the ports each platform implements (tray, notifications, start at login, keeping the popup out of the taskbar, the system's text size), the icons they show and `PosixFilePrivacy`, the desktop's `FilePrivacy`, without any other desktop part; `desktop.text` every text in each language (`Messages`, with the bundles beside it in `src/main/resources`); `desktop.notify` the notifications' texts; `desktop.app` the app settings (`app.json`: language, appearance, units and display mode) and `Background`, which every window uses for slow work; `desktop.theme` light or dark, the system's text size and the stylesheet the windows share (`WindowTheme`), the labels they build their texts with (`Labels`), and what a screen reader needs beyond JavaFX (`ScreenReader`); `desktop.dashboard` the JavaFX dashboard and the tray icon's status, mapped from the engine's outlook: the window and the popup in the package itself, and a sub-package for each part of it (`outlook`, `day`, `away`, `battery`, `solar`, `advice`, `tray`), built on the shared pieces in `dashboard.parts`. `desktop.settings` is the Settings window (a page per category, cards of fields, saved through the engine and `app.json`) and the first-run set-up, whose steps show the same cards and save through the same storage: the window in the package itself, set-up in `setup`, the storage in `store`, a sub-package per category (`location`, `solar`, `car`, `charger`, `forecast`, `away`, `planning`, `app`, `about`) that knows no other, built on the page and card pieces in `page` and the fields in `field`, and `form` the logic without JavaFX that they all use. |
| `instance` | One running copy per user: the running copy holds a lock file in a private folder, and a second start asks it, over a private Unix domain socket beside it, to show its dashboard. |
| `platform.linux`, `platform.windows` | The tray icon, notifications, the keychain and start at login on each system: our own StatusNotifierItem, notification and Secret Service clients over D-Bus, and a desktop entry in the autostart folder (in a Flatpak, the Background portal); AWT's `SystemTray` and its icon's messages, the Credential Manager, the Run key and the text size in the registry. |
| `Main` | The composition root: the one place where the parts are made and handed to each other. `Main`, the root package's only class, reads the system once (`host`) and runs a command (`cli`) or the app (`start`); the five packages below are the composition root's own, and depend one way: `start` on `cli`, both on `platforms`, `logging` and `host`, and `platforms` and `logging` on `host` alone. |
| `start` | The app as it runs (`DesktopApp`): startup order, tray or window (`StartupDecision`, `TrayStarter`), one `ClosingOrder` for Quit and the shutdown hook, and `JdkHttpGet`, the `HttpGet` on Java's own HTTP client that it hands the engine. |
| `platforms` | `PlatformParts`, with `FreedesktopParts` and `WindowsParts`, which make each system's own parts from `platform.linux` or `platform.windows`, for the app and the commands. |
| `cli` | The command line: what the app was started to do (`CommandLine`), the commands that do one thing and end (`Commands`), and the exit codes. |
| `logging` | The app's log, set up once (`AppLog`, which names the parent of the app's own loggers): the rotating private log file, one line per record, and the logging of what no thread caught. |
| `host` | The app's names on the system (`AppName`: the title the owner sees and the ID that names its log files, desktop entries and keychain entries), the system and its user, read once (`Host`, `Os`), and where the app's folders and files are on each system: config, data, the instance lock, the private runtime folders and the API keys' file. |

Nothing slow runs on the JavaFX thread: a window hands work that can wait on the keychain, a file, the system or the network to `desktop.app.Background`, which runs it on a virtual thread, logs a failure, and hands the outcome back to the JavaFX thread. A test gives it executors of its own, such as one that runs the work at once or one that holds it.

How to add a forecast provider is in [docs/providers.md](docs/providers.md). `CONTEXT.md` explains the words the project uses (window, charge power, away and so on), and `docs/adr/` records the larger decisions.

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

- **Strict types.** A value with a meaning of its own gets a type of its own (`Percent`, `ProviderId`, `Coordinates`) rather than a bare `double` or `String`, and a choice is an enum (`Phases`, `Freshness`, `ProviderRole`), not an `int` that must be 1 or 3 or a pair of booleans of which only some combinations make sense.
- **`Optional` where absent is a real case.** `Optional` is for return values and record components only, such as `AppSettings.language` when the owner chose none and the app follows the system; never a field or a parameter. A method that may get nothing comes in two (`choose(Language)` and `followSystem()`), or takes a type that names the none case (`Owner.Unchecked`), and a caller that holds an `Optional` unwraps it at the call. In a field, an injection that may be missing becomes a null object or a type that says why it is missing, state that becomes known later is a private field that is null until then and goes out through `Optional.ofNullable`, and a callback set later starts as a no-op. Not for a collection either, which is empty instead. `ArchitectureTest` checks the fields and the parameters.
- **Plain invariants.** A record checks its own rules in its constructor, each as a plain `if` and `throw new IllegalArgumentException(...)` naming the value and what is allowed, without a helper. A range check on a `double` refuses NaN explicitly (`if (kwp <= 0 || Double.isNaN(kwp))`). The Javadoc states the same rule, in the `@param` and the `@throws`, so an object that exists is valid.
- **No catching `NullPointerException` or `Throwable`.** Check a value that may be null where it comes in, and let an `Error` end what it hit. The one exception is `platform.windows.Advapi32`, because `MethodHandle.invokeExact` declares `Throwable`: it passes a `RuntimeException` or `Error` on first and turns only the rest into the caller's exception. `ArchitectureTest` and `CatchClausesTest` check this.

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

## Adding a language

Every text the app shows is translatable, and a language is one file. How the texts are written, the words for the terms and the steps to add a language are in [docs/translating.md](docs/translating.md).
