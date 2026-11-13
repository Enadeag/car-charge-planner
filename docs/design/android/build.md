# Android: build, tests and CI

Decisions about building, testing and checking the Android app: SDK levels, dependencies, the debug build, StrictMode, the tests, mutation testing, the licence files and the emulator job.

What holds on every screen (top app bar, buttons, snackbars, dialogs, spoken names, saved state) is in [screens.md](screens.md); read it too.

### SDK levels and dependencies

- The app compiles against SDK 37, which resolves to `platforms;android-37.0`, with AGP's default build-tools (36.0.0) and no `buildToolsVersion`; targetSdk stays 36 until #383 raises it with a device check (#321).
- The screen tests use Espresso 3.7.0, because the 3.5.0 that Compose's test rule brings calls `InputManager.getInstance()`, which Android 17 removed; the instrumented tests keep 3.5.0 while the emulator runs API 29 (#321).
- One test of the notifications card's refusal in `HomeScreenTest` runs on SDK 36, because Robolectric 4.17 counts the manifest's permissions as granted on 37; it is tried again with each Robolectric update (#321).
- Dependabot has no ignore entries for the AndroidX libraries: every update is a pull request behind CI and review (#321).
- `material-icons-core` is the only icon library; `NOTICE` names it as an exception, with its licence in `android/legal/apache-2.0.txt` (#315).
- DataStore brings a repackaged Protocol Buffers under BSD-3-Clause, whose text is `android/legal/protobuf.txt` (#361).

### Debug build and signing

- The debug build has `applicationIdSuffix = ".debug"`, so it can never update the released app, the launcher label "Car charge planner (debug)", and the version name suffix `-debug+<first 7 characters of the commit>` on CI (`BUILD_COMMIT`), `-debug+dev` elsewhere (#355).
- It is signed with `android/app/debug.keystore`, a public key (alias `androiddebugkey`, password `android`), marked binary in `.gitattributes` (#355).
- `checkReleaseNotDebugSigned`, under `check`, fails when any build type other than debug, by its effective signing config (its own, a product flavour's, or the default config's), is signed with the debug key or a copy of it (#355).
- CI uploads only `app-debug.apk`, as `android-debug-apk-<run number>-<run attempt>`, for 14 days, also when lint or the tests failed; not for a pull request from a fork or by Dependabot. The job has `contents: read` and posts no comment on the pull request (#355).

### StrictMode

- The debug build detects disk reads, disk writes and network on the main thread, set in `onCreate` after the engine is built; the release build sets no policy, and there is no VM policy (#347).
- A violation crashes the app only when a frame of its stack is in `nl.enadeag.carchargeplanner.`, because a phone's own input code read a file on the main thread; the crash is an `IllegalStateException` posted to the main looper right after the current message, never thrown inside StrictMode or a vendor's code (#347).
- Other violations are logged at warn, once per kind and stack, up to 32, then one line says further ones are not logged. Vendor code running inside one of the app's lambdas still crashes (#347).
- The parts that ask for the app's folders are made in `onCreate` with the disk policy lifted around them (`beforeThePolicy`), because Robolectric runs each test's application on a main thread where an earlier test set the policy already (#382).

### Tests

- Screen tests run on Robolectric only, with explicit checks (name, role, a drawn size of 48 dp, scrolling at font scale 2) in place of Compose's accessibility checks, which find nothing there. What TalkBack says is checked on a phone before a release, by the list in `docs/accessibility.md` (#314).
- `AppArchitectureTest` holds the app to ADR 0005's wiring rules (one clock, no static mutable state but `EncryptedKeychain.LOCKS`, threads only from the root, `AndroidPlatform` no singleton), with rules on cycles, screens, engine access and sleeping in tests (#382). The engine's classes are not among the app's: `EngineArchitectureTest` holds them to theirs, and the engine's files take the clock the app hands in, so no exception for a clock is left (#285).
- The home screen stays split into a file per card (#382).
- `check` packages the debug APK, for `LicencesInTheApkTest` (#331).

### Mutation testing and coverage

- PIT runs on `:app` through `pl.droidsonroids.pitest`, with PIT's versions taken from `pom.xml`, so Maven and Gradle run the same PIT; Dependabot updates only the plugin (#327).
- It mutates the app's logic (`pitLogic`), the debug variant only; the screens' Robolectric tests and the tests of resources, manifest, APK and architecture are left out by name, and the screens keep hand mutants in review (#327).
- `./gradlew :app:pitestDebug` runs it, `-PpitClasses=…` on the changed classes only; an empty `-PpitClasses` fails, and a narrowed run has no floor (#327).
- `checkPitScore` leaves out the Kotlin compiler's own null and result checks, which no test can kill, and holds the full run to 84% (#382).
- PIT's test classpath gets test-only artifacts for Android's mockable jar, never the APK; their known CVEs do not apply, and `excludeMockableAndroidJar = true` breaks PIT's coverage (#327).
- `mutation.yml`'s `android` job runs weekly and by hand only, never on a pull request (#327).
- `checkCoverage`, under `check`, holds JaCoCo's line and branch floors, the screens included (#382).

### Licence files in the APK

- The hand-kept files in `android/legal` are the source; `licenceAssets` copies them with `NOTICE`, `LICENSE` and `ADDITIONAL-TERMS.md` into the assets, the same bytes on every machine, without a licence plugin (#331).
- `checkLegalFiles` holds those files to the release build's libraries by `group:artifact`, to the jars' `META-INF/NOTICE*` texts (line endings, trailing spaces and blank end lines ignored), and to third-party files in the app's `src` and the engine's `src/main`. A symbolic link, or anything but plain UTF-8 `.txt` in `android/legal`, fails the build (#331).
- Only modules that bring a file get an `Artifact:` line; KMP root modules and BOMs get none (#331).
- The GPLv2 §3 source of `desugar_jdk_libs_nio` is offered by pointing at its upstream commit, as the desktop does for OpenJDK (#331).

### Emulator job

- The instrumented tests boot a new AVD cold each run (`force-avd-creation: true`, `-no-snapshot`), without an AVD cache, because boots from a cached snapshot sometimes lacked the `input` service and failed the action's own keyevent (#371).
- The script first waits about 60 seconds for the services `input`, `settings`, `package` and `activity` (`adb shell service check`, each limited to 5 seconds, 1 second between rounds), logs "services ready after N s", and on a timeout fails, printing the missing services, `adb devices` and the last 100 lines of logcat, each limited to 10 seconds. A step before logs the runner's CPU model (#371).
- The test step has `timeout-minutes: 15` and no `continue-on-error`, so a hang on shutdown fails the job fast (#371).
