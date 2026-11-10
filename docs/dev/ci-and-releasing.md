# CI, releasing and dependencies

## Continuous integration

The workflows are in `.github/workflows/`:

- `ci.yml` runs `./mvnw -B clean verify` for every pull request and every push to `main`, on Linux (with `dbus-daemon`, GNOME Keyring and `desktop-file-validate`, so the private-bus tests run, with `CCP_REQUIRE_PLATFORM=1` and the coverage floors) and on Windows. It keeps each module's test reports, and on Linux each module's coverage report, for 7 days. A change to documentation only (`*.md`, `docs/` except `docs/example-engine.json`, which the tests read; `LICENSE`, `NOTICE` and `.github/ISSUE_TEMPLATE/`) skips both, and GitHub counts a skipped job as passed. macOS is not built or tested: nobody has a Mac to check a release on.
- `android.yml` builds the Android app and runs its lint, unit tests and checks with JDK 21, and its instrumented tests on an API 29 emulator; see [CI for the app](android.md#ci-for-the-app) for when each job runs. A job a pull request does not need is skipped, which GitHub counts as passed. `gradle/actions/setup-gradle` checks `gradle-wrapper.jar` against Gradle's checksums and caches Gradle's downloads with its open-source provider, written from `main` only. It keeps the lint and test reports, and the emulator's logcat, for 7 days, and the debug APK for 14 days (not for a pull request from a fork or from Dependabot).
- `linux-packages.yml` builds the `.deb` and `.rpm` and runs `tools/test-linux-packages.sh` on them, for a pull request or a push to `main` that changes the packaging or any `pom.xml`, and by hand.
- `windows-installer.yml` builds the installer with `tools\package-windows.ps1`, installs it silently, runs the installed `car-charge-planner.exe quit`, uninstalls it and keeps the installer as an artifact for 14 days. It runs when the packaging changes on `main`, by hand, and for a release.
- `mutation.yml` runs PIT and the tests in a random order, weekly and by hand, and PIT on the engine for a pull request that touches it; see [What is tested where](testing.md#what-is-tested-where).
- `release.yml` makes a release; see [Releasing](#releasing).

The actions are pinned to a full commit SHA, with the version in a comment beside it: update both together. Dependabot (`.github/dependabot.yml`) proposes new versions of the actions, of what the poms name (from the root, which reaches the modules) and of what the Android build's catalog names, weekly and a week after their release, as pull requests that CI builds like any other.

### Repository rules

GitHub's rulesets hold three refs:

- `main` cannot be deleted or force-pushed, and every change reaches it through a pull request with one approving review: a new push dismisses an earlier approval and needs one of its own, and every review thread must be resolved.
- The required checks of `main` are `changes`, `linux` and `windows` of `ci.yml`, without the need to be up to date with `main` first. The Android jobs (`android-changes`, `android-build`, `android-emulator`) are not among them, so a red Android job does not block a merge.
- A repository admin may bypass the `main` rules only through a pull request, never with a direct push, which is how the maintainer merges their own pull requests, as nobody can approve their own.
- Branches `release/**` and tags `v*` can be created, moved or deleted only by a repository admin.

Private vulnerability reporting, secret scanning and push protection are on.

## Releasing

The app has one version, `<revision>` in the parent `pom.xml` at the root; every module inherits it (its `<parent>` names it as `${revision}`), and the scripts read it from the parent alone (`./mvnw -N help:evaluate -Dexpression=project.version`). Between releases it is the next one with `-SNAPSHOT`, such as `0.3.0-SNAPSHOT`. The tagged commit carries the exact version, because the app shows it in About and the download names carry it. To release X.Y.Z:

1. Go through [Before a release](testing.md#before-a-release).
2. In a pull request, set `<revision>X.Y.Z</revision>` in the parent `pom.xml`, turn `## [Unreleased]` in `CHANGELOG.md` into `## [X.Y.Z] - YYYY-MM-DD`, and add `<release version="X.Y.Z" date="YYYY-MM-DD">` with a short description at the top of `<releases>` in `src/packaging/flatpak/<app ID>.metainfo.xml` (`FlatpakPackagingTest` checks that it matches the changelog).
3. After the merge, create the branch `release/X.Y.Z` at that commit, then tag the same commit `vX.Y.Z` and push the tag. The ruleset lets only an admin create `release/**`, so the maintainer makes the branch; the workflow's own token cannot. The release workflow stops when the tag is not `v` plus the version in the parent `pom.xml` (`ReleaseWorkflowTest` checks that it reads it with `-N`), when that version still ends in `-SNAPSHOT`, when `CHANGELOG.md` has no section for it, or when `release/X.Y.Z` exists at another commit; when the branch is missing, its attempt to create it is refused by the ruleset. When none of that stops it, it builds and tests, packages the Linux tar.gz, `.deb` and `.rpm` and the Windows installer with an SBOM for Linux and one for Windows, smoke-tests the tar.gz, the `.deb` (installed on the runner and removed again) and the installer, checks the files in the `.rpm`, stops when OSV-Scanner finds a known vulnerability in an SBOM, writes `SHA256SUMS` over the six files, attests where they were built (`actions/attest-build-provenance`), checks the branch `release/X.Y.Z`, and makes a draft release with the changelog section, install notes and the Java runtime (`java tools/ReleaseNotes.java X.Y.Z CHANGELOG.md`, made before the job that can write to the repository).
4. Read the draft and publish it by hand.
5. In a pull request, set the version to the next `-SNAPSHOT` the same way and add an empty `## [Unreleased]`, or keep the one that holds the Android app's part.

To try the workflow without releasing, start it by hand on `main`: it builds everything and makes a draft called "dry run" (delete it afterwards), without the tag check, the attestations or the release branch.

### Java updates

The release builds use one exact Java version, the `java-version` of each `setup-java` in `release.yml`, `windows-installer.yml` and `linux-packages.yml` (`ReleaseWorkflowTest` keeps them the same; the last one only builds the test packages, which are not released), and the packages bring that runtime: a user's app gets Java's security fixes only through a new release. Java's security updates come quarterly, on the third Tuesday of January, April, July and October. After each one:

1. Read the advisory of the OpenJDK Vulnerability Group (<https://openjdk.org/groups/vulnerability/advisories/>) and check which fixed vulnerabilities reach the app: the modules it links are in `src/packaging/jlink.args` and in the SBOM (`jlink.modules`); what matters most is what a forecast provider's answer reaches: TLS, the HTTP client and the JSON parsing.
2. Once Temurin has the update, set its exact version (such as `25.0.5+11`) in all three workflows in a pull request, and run `sh tools/scan-sbom.sh` on fresh SBOMs for the libraries.
3. When a fixed vulnerability reaches the app, release a patch version on the new runtime; otherwise the next release takes it along. The release notes and the SBOMs name the runtime each release brings.

## Dependencies

Each module declares every library its code uses directly in its own `pom.xml`, also one that already comes in through another library or module, without a version: the versions are in the parent `pom.xml` (`dependencyManagement`, and the plugins' in `pluginManagement`). `./mvnw verify` runs `dependency:analyze` in every module and fails on a library that is used but not declared, or declared but not used. A library that is only found at run time and never named in the code (such as the SLF4J binding) goes in the list of ignored ones in the parent, with the reason. Keep the third-party table in [docs/privacy.md](../privacy.md#third-party-software) up to date.

### Versions in the Android build

The Android build declares the engine's libraries again, but not their versions: the settings of `android/` and of `android/build-logic/` read the versions of Jackson, slf4j, JUnit, AssertJ and ArchUnit from the parent `pom.xml` (`dependencyManagement`, with its properties filled in) into the `libs` catalog while the build configures, and `android/gradle/libs.versions.toml` names them (`version.ref`) without giving them. A Maven update of one of them therefore reaches the Android build in the same pull request, and Dependabot leaves those five out of its Android updates. The settings also read the parent's top-level properties `pitest.version` and `pitest-junit5.version` into the catalog's versions `pitest` and `pitest-junit5`, so the app's mutation testing runs the PIT the `pit` profile runs; a profile or `.mvn/maven.config` that sets one of them fails the build as for the five. The pom is an input of Gradle's configuration cache, so a changed version is read again.

A pom that lacks one of these libraries in its `dependencyManagement`, or a property its version names, fails the Android build while it configures, naming the library or the property and the pom; there is no fallback version. A version the Maven build pins where Android does not read it fails the same way, with a message that says so:

- another entry of the parent's `dependencyManagement` in those five groups (and in `com.fasterxml.jackson`, which Jackson's BOM manages too) that does not take its version from the same property as the entry of its group (such as `slf4j-jdk14` with `${slf4j.version}`);
- a BOM import other than `tools.jackson:jackson-bom` and `org.junit:junit-bom`, or any BOM import in a profile (Maven lets the first import that manages a library win, so another BOM could change the engine's versions);
- any `dependencies` of the parent, at the top level or in a profile, which the engine inherits (plugin dependencies are fine);
- a profile of the parent that sets one of the properties those versions name or has a managed entry in those groups;
- a `-D` of one of those properties in `.mvn/maven.config` (quoted or not; `.mvn/jvm.config` ranks below the pom's properties and is not read);
- a `<parent>` of the parent `pom.xml`, whose `dependencyManagement` Maven would apply;
- in `engine/pom.xml`: a dependency with a `<version>` of its own, a `dependencyManagement`, a property other than `maven.compiler.release` (Maven would let it override the parent's, such as `jackson.version`), or a profile with properties, dependencies or a `dependencyManagement` (plugin configuration is fine).

No versions are compared. A version the poms give must be a plain one (letters, digits, `.`, `_` and `-`) and a property may not name another property, or the build fails. `android/gradle/libs.versions.toml` must not give the versions of Jackson, slf4j, JUnit, AssertJ and ArchUnit, nor `pitest` and `pitest-junit5`, itself, as the poms win (`CatalogVersionsTest`). A pull request that changes the engine's libraries themselves changes `android/engine/build.gradle.kts` and the catalog's `engine` bundle (the libraries `:engine` and `:engine-lint` both declare) in the same pull request: `checkMavenDrift` fails until the libraries `:engine` declares are those of `engine/pom.xml`, by group and artifact.

### Licence files

Each library the app brings at run time, also one that comes in through another library, needs its licence text in `src/packaging/legal/`, which the packages carry in their `legal` folder: one file per component, starting with its name, an `Artifact: groupId:artifactId` line per jar it covers, its source and its licence, then the licence and notice texts as the jar's `META-INF` has them (or the project's own `LICENSE` at that release when the jar has none). `PackagingTest` fails when a runtime library has no file there, or a file names a library the app no longer brings. The app's own modules (`nl.enadeag:*`, such as the engine's jar) are under the app's own licence and need no file.

The Android app keeps its own files in `android/legal/`, in the same form, and the APK carries them (see [The Android app](android.md)). `checkLegalFiles` (`android/build-logic`, run by `check`) compares them with what the app's release build brings, by `group:artifact` without the version, over `releaseRuntimeClasspath` (the engine's libraries included; a module that only points to its Android variant, or a BOM, brings no file and needs no line) and `coreLibraryDesugaring`. Every library under the Apache License 2.0 gets an `Artifact:` line in `android/legal/apache-2.0.txt`; a library under another licence, or one whose jar has a `NOTICE`, gets a file of its own, with the texts its `META-INF` holds under a line `==== META-INF/NOTICE of <artifact> ====`. The check fails, with the fix, when:

- the release build has a library no file names, a file names a library the app no longer brings, or a library is named twice;
- a jar has a `META-INF/NOTICE*` the file that names it does not carry, or carries with another text (line endings, trailing spaces and blank lines at the ends of the text do not count: take over the jar's new `NOTICE` text);
- a file that a `Copied:` block names (a path from the repository's root on a line of its own, before the block's `From:` line) does not exist, or a path of a `Copied:` block is no regular file of the repository (a folder, a symbolic link, a path through a linked folder and a path out of the repository count as missing);
- `android/app/src` or the engine's `src/main` holds a symbolic link, or a file in either carries a third party's header (a comment line starting with a copyright or an SPDX identifier) without being named in a `Copied:` block;
- a file of `android/legal` is no UTF-8 text.

The check does not look into aars: for a new aar dependency, unzip it and look in its `META-INF` (and its `classes.jar`'s) for a licence or notice file yourself, and carry what you find. A version update needs nothing, unless the new version brings another library or a notice. `android/legal` holds plain `.txt` files only, as the APK carries every file in it: the check and the asset task refuse a folder, a link or another file there. The asset task also refuses a tab in a title line, in `NOTICE`'s copyright line or in a file's name, a line break in a file's name, and a `NOTICE`, `LICENSE` or `ADDITIONAL-TERMS.md` that is no UTF-8 text, as the index separates its fields by tabs and has one line per file.
