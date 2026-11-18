# Android: build, tests and CI

Decisions about building, testing and checking the Android app: SDK levels, dependencies, the debug build, StrictMode, the tests, mutation testing, the licence files, the emulator job, R8, the release and dependency verification.

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
- After the debug build's tests, a second Gradle call in the same boot runs the minified suite (see [R8](#r8)), with a key a step before made for the run, so `android-release.yml`'s gate on the emulator job covers it without a change (#485).

### R8

- The release build is shrunk and optimised by R8 (`isMinifyEnabled = true`) with `proguard-android-optimize.txt` and the app's `proguard-rules.pro`, in AGP's default full mode without a compat switch: the APK went from about 30 MB to 5 MB (#485).
- R8 renames nothing (`-dontobfuscate`), so the log's tags, its stack traces' classes and methods and the error texts that name a class stay readable without a mapping file; no `mapping.txt` goes with a release (#485).
- `-keepattributes SourceFile,LineNumberTable` stays as decided, although R8 of AGP 9.5 keeps both without it (the APK is the same byte for byte): it names every file `r8-map-id-<id>` and numbers the lines anew, so a trace's file and line need the build's mapping file, its class and method names do not (#485).
- Resources are not shrunk for now (`isShrinkResources = false`): the change stays with the code, and resource shrinking is looked at again after the reproducible build (#424, #485).
- The keep rules are as narrow as possible: the libraries' own consumer rules first, then only what the minified suite proves. The app's file adds two named `-dontwarn` for the `java.beans` annotations Jackson looks for and does without, and an explicit `-keep` of `LogcatServiceProvider` with its no-argument constructor, which R8 would keep by itself through `META-INF/services` but slf4j reaches by name. No `-keep` of all of Jackson, no `-dontwarn **`, no absolute path and no `-printconfiguration`, for #424 (#485).
- The debug build stays unshrunk, as Robolectric, coverage and PIT run on it (#485).
- `checkReleaseShrunk` (in `android/build-logic`, under `check` and before every build of a type other than debug) fails when the release build is not minified, a rule file of its own says `-dontshrink`, or none says `-dontobfuscate`: a tripwire against a mistake, not intent (#485).
- The build type `minified` is the release build (`initWith`) with an id of its own (`.minified`), signed by Gradle with a key made for the run (`tools/android-sign.sh throwaway-key`, named by `-PminifiedKeystore`, its password in `ANDROID_KEYSTORE_PASSWORD`), never the debug key and never a committed one, so a device can run tests on it; `release` stays unsigned (#485).
- `-PtestBuildType=minified` makes it the tested build type for one Gradle call, debug staying the default; AGP and the configuration cache take the property (#485).
- AGP does not keep what the test APK refers to: the minified build's R8 configuration and list of removed code are those of the release build, also with tests that name app classes. The test APK leaves out every library the app has, so the tests run against the app's shrunk copies; R8 changes signatures and merges classes, so a test that called the app's or a library's code would fail where the app does not (#485).
- So the minified suite (`src/androidTestMinified/`, Java) reaches the app only from outside, as Android and slf4j do: the logger by slf4j, the activity by intent on a prepared settings file and forecast, the plan by the accessibility tree, a round on WorkManager by WorkManager's own Logcat line, an API key saved through Settings by the accessibility tree. It runs on a runner of its own on JUnit 4 alone, as AndroidX Test's runner needs `androidx.tracing` and Kotlin's library, which R8 removed from the app (#485).
- The Keystore is tested through the app itself, as R8 drops the alias parameter of `EncryptedKeychain`'s constructor (the app always passes its own), so no test can write under an alias of its own: the suite types a made-up API key into Settings, Forecast and saves it, checks the keychain's file names the entry without the key, that the Android Keystore holds a key, that the key shows again after the activity is made anew and that it is not in the app's Logcat lines. It runs in the `.minified` installation, so the app's own alias there is no one's real key. The key shown again comes from the engine's memory of the save, as only a new process makes the app decrypt it anew, and one test run is one process (#485).
- The debug build's instrumented tests moved to `src/androidTestDebug/`, so the minified test APK holds none of their references (#485).
- `checkInstrumentedResults` follows the connected test task and fails on a failure or on no test at all in its results: AGP 9.5 lets the task pass when the app's process crashes before the first test, as one without the App Startup rules did (#485).
- Each rule was taken out in turn and the suite run; the pull request of #485 records what failed (#485).

### Release

How a release is built, signed and published (`android-release.yml`, `tools/android-sign.sh`; the steps are in [Releasing the Android app](../../dev/ci-and-releasing.md#releasing-the-android-app)). The release key and the environment are the maintainer's (#271); R8 is under [R8](#r8) (#485) and a reproducible build waits for #424; Gradle's dependency verification is under [Dependency verification](#dependency-verification) (#484).

- The release commit sets `versionName` in `android/app/build.gradle.kts`, and a tag that is not `android-v` plus that version, or a version that still ends in `-SNAPSHOT`, stops the run, so the tag, the APK and the notes can never disagree (#272).
- `versionCode` is major × 10000 + minor × 100 + patch (0.1.0 is 100), worked out only in `AppVersion` with its tests; a minor or patch above 99, any other form of the name and a code of 0 or above Play's 2,100,000,000 fail the build, as two versions could otherwise share a code (#272).
- Tags are `android-vX.Y.Z` only, without pre-releases; any other tag stops before the key is asked for (#272).
- Each job touches only what it needs, so the key never meets Gradle's plugins and the write token never meets the key (#272):
  - prepare (the tag's form, the version, the notes) and build (lint, the tests, the unsigned APK) run Gradle with a read-only token and no secrets;
  - tag-checks and no-release-yet check out nothing and only ask GitHub's API; no-release-yet has `contents: write`, as GitHub lists draft releases only to a token that can write, the one exception to "only the release job writes";
  - sign has the environment `android-release` and runs `apksigner` through `tools/android-sign.sh`, no Gradle and no third-party action, with a read-only token; sign-dry-run does the same with a throw-away key and without the environment;
  - release has the token that writes the draft and the attestation, and no key.
- Before signing, the sign job checks the APK's SHA-256 against the build job's, that it is unsigned and zip-aligned, and its app ID, `versionName` and `versionCode`, so it signs only what the build made (#272).
- The sign jobs check out the repository again and stop unless it is at the commit the prepare job named, so the script and the fingerprint file are those of the commit that was built; the release job stops, before it makes the draft, unless the notes name the certificate of the APK that was signed (#272).
- Only the APK Signature Schemes v2 and v3, with exactly one signer, checked after signing: the app needs Android 10, which reads v3, so v1 would add nothing but files in the APK (#272).
- The certificate's SHA-256 fingerprint is recorded in `android/release-certificate.sha256`; a tag run refuses any other certificate, the debug one too, and stops early while the file records none (#272).
- The keystore reaches the runner only in the signing step's `env:`, is written to the runner's temp folder owner-only (`umask 077`), its password is passed to `apksigner` through `--ks-pass env:`, and it is removed in a step that always runs; the sign job uses no third-party action (#272).
- After signing the APK is read back: app ID without `.debug`, the tag's version, not debuggable, not test-only (#272).
- The signed APK's run artifact is kept for one day (`retention-days: 1`), as any signed-in user can download it until the draft is published or deleted; a rejected draft is deleted together with its workflow run, which removes the artifact (#272).
- A dry run skips the checks that need a tag (the tag's form, the release branch, the emulator job and an existing release) and only warns when `CHANGELOG.md` has no section for the version (#272).
- The sign jobs use the runner's own Java and install build-tools 36.0.0 with `sdkmanager`; the exact Java version is the build's, which `AndroidReleaseWorkflowTest` keeps (#272).
- "The emulator job was green" means a run of `android.yml` for the tag's commit, by push or by hand, whose `android-emulator` job succeeded (#272).
- The keystore is PKCS12 with one password for the store and the key, so `apksigner` takes the one secret for both (#272).
- The workflow only makes a draft, created with `--latest=false`; the maintainer reads and publishes it, and unticks GitHub's **Set as the latest release** when publishing, as `--latest=false` holds for the draft only (#272).
- The notes come from a changelog section `## [Android X.Y.Z] - date`, through `ReleaseNotes.java --android`, with an installing text of the APK's own; the desktop's notes stay as they were (#272).
- The dry run, started by hand, signs with a throw-away key made in the run in a job without the environment, so it never asks for the release key; it skips only the recorded certificate of the signing checks, and its draft, tag and APK name say "dry run" (#272).
- `release/android-X.Y.Z` must exist at the tag's commit before the key is asked for; the workflow never creates or moves it (#272).
- The signed APK gets a build provenance attestation, except in a dry run (#272).
- The files are `car-charge-planner-android-X.Y.Z.apk` and `SHA256SUMS`, as the desktop's, and the release's title is "Car charge planner for Android X.Y.Z" (#272).
- The build job runs lint, the unit tests and the build's checks again; it runs no emulator, but the run stops unless `android.yml`'s emulator job was green for the tag's commit in a push or a run by hand, as a pull request's run tests a merge commit (#272).
- One exact Temurin 21 (`AndroidReleaseWorkflowTest`) and build-tools 36.0.0 build the release; a Java update is a pull request (#272).
- A release, draft or published, that exists for the tag stops the run; nothing is overwritten. Runs for one tag wait for each other in order and are never cancelled (`queue: max`), up to GitHub's limit of 100 waiting runs in a group, beyond which a run is cancelled (#272).
- No cache anywhere in the workflow (`setup-gradle` with `cache-disabled: true`, no `actions/cache`, no `cache:` of `setup-java`), so no other run can change what is built or signed (#272).
- `AndroidReleaseWorkflowTest` holds the workflow's text to these rules, in the Maven build, so every pull request that changes the workflow runs it (#272).
- No check that the tag is reachable from `main`, as on the desktop: a release branch may carry a fix (#272).

### Dependency verification

Every file Gradle downloads is checked against its SHA-256 in `android/gradle/verification-metadata.xml`, so the signed release holds only recorded files ([Dependency verification](../../dev/android.md#dependency-verification); #484).

- Checksums only, SHA-256, with poms and `.module` files checked too (`verify-metadata`) and no PGP signatures or keyring (`verify-signatures` off) (#484).
- One file, for the root build, its plugins, `build-logic` and `maven-poms`, as Gradle reads only the file of the build it runs; `MavenVersionsTest`'s TestKit builds have none, as they download nothing (#484).
- Only `*-sources.jar` and `*-javadoc.jar` are trusted without a checksum, for an IDE, as the build never uses them; nothing else is trusted by a pattern (#484).
- Strict, Gradle's default, in every job (build, emulator, release, weekly mutation run); `AndroidDependencyVerificationTest` is a tripwire beside it, which Gradle's strict default does not depend on. It checks the settings of this file below; that no build script, source, properties file or wrapper script of the Android build names the mode's property or the API that sets the mode or switches it off; that the wrapper is the recorded one; that the repository's root and `release/` hold no `gradlew` or `settings.gradle(.kts)`, as a build there would run without this file; and, as plain text line by line, that no workflow or composite action under `.github/` gives Gradle `--dependency-verification` or `-F`, the property `org.gradle.dependency.verification`, `--write-verification-metadata` or `-M`, `--refresh-keys` or `--export-keys` (#484).
- `tools/refresh-android-verification.sh` is the one place with the list of tasks (those of CI, the release and the weekly mutation run, `assembleRelease` included, and the input files of the emulator job's `connectedDebugAndroidTest`, resolved without a device), and writes the file incrementally; its diff is read. It runs Gradle with an empty Gradle user home of its own, as Gradle leaves out parent poms and BOMs it has cached (gradle/gradle#19228), so the file holds what a fresh machine downloads. PIT runs narrowed to one class there, as its classpath is the full run's and the full run takes far longer. `--clean` rebuilds it from its header and configuration now and then, with the old file kept to compare; it fails and puts the old file back when a recorded file has another checksum, which a folded diff would hardly show (#484).
- Every task a `./gradlew` call of a workflow names belongs in the script's list, as the release and the weekly mutation run never run on a pull request; `:app:connectedTestInputs` stands for `connectedDebugAndroidTest`, and in a second Gradle call with `-PtestBuildType=minified` for `connectedMinifiedAndroidTest` (#485). The review of a workflow change checks it (#484).
- Linux entries only, as CI builds on Linux; a Windows or macOS build fails on its own files, and such a developer runs the script on their own system (#484).
- Dependabot's pull requests for `/android`, and a version that reaches the build through `pom.xml`, are refreshed by hand: a step of `android.yml` says how after a verification failure, known by the report folder Gradle writes only then, and no workflow gets write rights (#484).
- Robolectric's `android-all-instrumented` jars come through a Gradle configuration per SDK the tests use, and the tests and PIT run Robolectric offline on them; `RobolectricSdkJarsTest` holds the jars to the Robolectric version, and Dependabot leaves them to a Robolectric update, as a lone bump would only fail that test (#484).
- The test is a tripwire against a mistake, not a guard against intent (decided 2026-11-18; #484). It had grown into a YAML parser with allowlists of Gradle calls, actions, inputs, variables and shell rules, and each round of review found a new way around it. A shell step can do anything, and whoever may edit a workflow may edit the test too, so that work could not be finished.
- Its limit: it catches a mistake, not intent. A workflow can switch the check off in ways the test does not see, such as a script it runs or a variable it builds. The review of a pull request that touches `.github/`, the Android build's files or the test is what protects against that (decided 2026-11-18; #484).
- The Android build's sources are read for the mode's property and API: Gradle and Kotlin scripts (`.gradle`, any `.kts`), Java, Kotlin and Groovy sources, every properties file and the wrapper scripts; a source folder named `build` is read, and only a `build` folder beside a build or settings script and outside `src` is skipped as output (#484).
- The wrapper is pinned: the SHA-256 of `gradlew` and `gradlew.bat`, with every line end read as LF, as the Windows job and a local checkout may have other line ends, and of `gradle-wrapper.jar` as it is; a Gradle update records the new values. The wrapper downloads Gradle from `services.gradle.org` with a recorded checksum into Gradle's user home (`distributionBase` and `zipStoreBase` `GRADLE_USER_HOME`, the paths `wrapper/dists`), as its scripts and jar run before Gradle reads any setting (decided 2026-11-17; #484).
- The short flags `-F` and `-M` count only on a line that names Gradle, a backslash's continuation joined, so `grep -F` or `tar -xf` elsewhere is no alarm. The hint after a failed check may name the refresh script and the metadata file, as the patterns are the flags, not those names (decided 2026-11-18; #484).
- The comparison of `--clean` was tried only with bash as `sh`, a known limit (decided 2026-11-17; #484).
- The cache of `android.yml` stays, as cached files are checked too; the release keeps building without a cache (#484).
- Outside the check, and not closed here: the Gradle distribution (the wrapper's `distributionSha256Sum`), the Android SDK and build-tools (`sdkmanager`, over HTTPS) and the JDK (`setup-java`, over HTTPS) (#484).
- The file is marked as generated in `.gitattributes` (#484).
