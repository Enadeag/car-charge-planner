# Android: build, tests and CI

Decisions about building, testing and checking the Android app: SDK levels, dependencies, the debug build, StrictMode, the tests, mutation testing, the licence files, the emulator job, the release and dependency verification.

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

### Release

How a release is built, signed and published (`android-release.yml`, `tools/android-sign.sh`; the steps are in [Releasing the Android app](../../dev/ci-and-releasing.md#releasing-the-android-app)). The release key and the environment are the maintainer's (#271); R8 waits for #485 and a reproducible build for #424; Gradle's dependency verification is under [Dependency verification](#dependency-verification) (#484).

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
- `assembleRelease` builds as it is, without a release block (#272).
- No check that the tag is reachable from `main`, as on the desktop: a release branch may carry a fix (#272).

### Dependency verification

Every file Gradle downloads is checked against its SHA-256 in `android/gradle/verification-metadata.xml`, so the signed release holds only recorded files ([Dependency verification](../../dev/android.md#dependency-verification); #484).

- Checksums only, SHA-256, with poms and `.module` files checked too (`verify-metadata`) and no PGP signatures or keyring (`verify-signatures` off) (#484).
- One file, for the root build, its plugins, `build-logic` and `maven-poms`, as Gradle reads only the file of the build it runs; `MavenVersionsTest`'s TestKit builds have none, as they download nothing (#484).
- Only `*-sources.jar` and `*-javadoc.jar` are trusted without a checksum, for an IDE, as the build never uses them; nothing else is trusted by a pattern (#484).
- Strict, Gradle's default, in every job (build, emulator, release, weekly mutation run); `AndroidDependencyVerificationTest` is a tripwire beside it, which Gradle's strict default does not depend on. It holds each Gradle call of a workflow or composite action to an allowlist: the wrapper written plainly as `./gradlew`, then only the options the test allows (`--continue`, `--stacktrace`, `-q`, `--console=plain`, none with a value of its own) and tasks the refresh script runs, up to the end of the line or the `)` of a `$(...)`; any other word fails, and so does Gradle named any other way as a command. Beside that it refuses the mode's property in any spelling and the verification flags in the workflows, composite actions and `gradle.properties` files, another Gradle user home or an init script there, a workflow that runs the refresh script, which writes instead of checking, the build API that sets the mode or switches it off in the Android build's scripts and sources, and any change of the file's settings below. It does not see a Gradle call through a shell variable or from a script file a workflow runs, and text written to hide one is left to the review (#484).
- `tools/refresh-android-verification.sh` is the one place with the list of tasks (those of CI, the release and the weekly mutation run, `assembleRelease` included, and the input files of the emulator job's `connectedDebugAndroidTest`, resolved without a device), and writes the file incrementally; its diff is read. It runs Gradle with an empty Gradle user home of its own, as Gradle leaves out parent poms and BOMs it has cached (gradle/gradle#19228), so the file holds what a fresh machine downloads. PIT runs narrowed to one class there, as its classpath is the full run's and the full run takes far longer. `--clean` rebuilds it from its header and configuration now and then, with the old file kept to compare; it fails and puts the old file back when a recorded file has another checksum, which a folded diff would hardly show (#484).
- Every task a `./gradlew` call of a workflow names is in the script's list, held by `AndroidDependencyVerificationTest`, as the release and the weekly mutation run never run on a pull request; `:app:connectedTestInputs` stands for `connectedDebugAndroidTest`. Task names are compared as written, so `lint` and `:app:lint` differ (decided 2026-11-17; #484).
- Linux entries only, as CI builds on Linux; a Windows or macOS build fails on its own files, and such a developer runs the script on their own system (#484).
- Dependabot's pull requests for `/android`, and a version that reaches the build through `pom.xml`, are refreshed by hand: a step of `android.yml` says how after a verification failure, known by the report folder Gradle writes only then, and no workflow gets write rights (#484).
- Robolectric's `android-all-instrumented` jars come through a Gradle configuration per SDK the tests use, and the tests and PIT run Robolectric offline on them; `RobolectricSdkJarsTest` holds the jars to the Robolectric version, and Dependabot leaves them to a Robolectric update, as a lone bump would only fail that test (#484).
- The guard for Gradle calls is an allowlist, not a list of what a call may not carry, as two rounds of deny patterns each left new ways around them (decided 2026-11-17; #484).
- A Gradle call stands on its `run:` line with nothing deeper below it, or in a literal block (`|`): YAML joins a folded block or a plain value over several lines into one command, which hid an option on the next line. The wrapper's jar and `gradle-build-action` are refused, as they run Gradle without its name as a command (decided 2026-11-17; #484).
- In a workflow or composite action that calls Gradle, every `working-directory` and every `cd` goes to `android`, `.` or `release` (the signed APK's folder of `android-release.yml`), for any step, not only one that calls Gradle, as `build-logic` and `maven-poms` are no root build with a file of their own (decided 2026-11-17; #484).
- A workflow may name the refresh script only in an `echo` of plain double-quoted text; a `paths:` entry naming it fails too (decided 2026-11-17; #484).
- `init.gradle` of a Gradle home is refused as `init.d` is; the system's `/etc/init.d` is not (decided 2026-11-17; #484).
- The comparison of `--clean` was tried only with bash as `sh`, a known limit (decided 2026-11-17; #484).
- The cache of `android.yml` stays, as cached files are checked too; the release keeps building without a cache (#484).
- Outside the check, and not closed here: the Gradle distribution (the wrapper's `distributionSha256Sum`), the Android SDK and build-tools (`sdkmanager`, over HTTPS) and the JDK (`setup-java`, over HTTPS) (#484).
- The file is marked as generated in `.gitattributes` (#484).
