# Contributing

Thank you for helping. Small fixes can go straight to a pull request; for anything bigger, open an issue first so we can agree on the approach. Be kind: this project follows the [Code of Conduct](CODE_OF_CONDUCT.md).

## Licence of contributions

A contribution is accepted under the project's licence: the GNU General Public License, version 3 or later, with the additional term in [ADDITIONAL-TERMS.md](ADDITIONAL-TERMS.md). You keep your copyright. Do not add a licence header to the files you write: [NOTICE](NOTICE) covers them all.

## Set up

You need a JDK 25 or newer, such as Temurin. Maven comes with the repository through the wrapper: `./mvnw` on Linux, `mvnw.cmd` on Windows. Nothing else needs to be installed. The wrapper checks the Maven it downloads against `distributionSha256Sum` in `.mvn/wrapper/maven-wrapper.properties`, and `.mvn/maven.config` has Maven refuse a library whose checksum does not match (`--strict-checksums`). The Android app needs more: see [The Android app](docs/dev/android.md).

```bash
./mvnw verify                                                                    # build and test; nothing touches the network or your desktop
./mvnw -pl :car-charge-planner-desktop -am compile exec:exec \
    -Dapp.args="--config=docs/example-engine.json"                               # run it with the example system
```

Run Maven from the repository's root: it builds the modules in `engine/` and `desktop/` (see [Code layout](docs/dev/code.md#code-layout)), and `-pl :car-charge-planner-desktop -am` picks the desktop app and what it needs. The tests of every module run in the repository's root, where they find `docs/`, `tools/` and `src/packaging/`; an IDE that runs a test itself needs the same working directory (in IntelliJ IDEA: Run, Edit Configurations, Edit configuration templates, JUnit, Working directory `$PROJECT_DIR$`, with the project opened from the root `pom.xml`). `./mvnw` sets it for you. Java versions, `.editorconfig` and IntelliJ settings are in [Java versions and the editor](docs/dev/code.md#java-versions-and-the-editor); a test that passes for you and fails in CI, or the reverse, is worth trying on JDK 25.

Without `--config=` the app uses your own `engine.json` (see [What is kept, and where](docs/privacy.md#what-is-kept-and-where)), and without one it opens set-up. `docs/example-engine.json` shows every setting, for a made-up place; its `forecast` section names the forecast provider and the fallback provider, and an optional `typical` section lists the values that are typical values, such as `"system": ["CAR_BATTERY"]`.

The app also takes the commands `quit` (asks the running app to quit), `test-notification` (asks the running app to show a test notification), `start-at-login on` and `start-at-login off`, and, on Linux, `install-launcher` and `uninstall-launcher` (the menu entry and icons), for example `-Dapp.args="quit"`. Start at login and the menu entry need the packaged app, whose launcher is what they start. Try `install-launcher` with `XDG_DATA_HOME` pointing at a scratch folder, not your real one.

## Rules for every change

- **A change comes with a test that fails without it**, and `./mvnw verify` passes on Linux and on Windows. A change to the engine also needs `cd android && ./gradlew check assembleDebug`, as the Android build compiles the engine's sources. See [Tests](docs/dev/testing.md#tests) and [What a test should do](docs/dev/testing.md#what-a-test-should-do).
- **The engine stays on Java 17** (`--release 17`) because the Android app compiles it; the desktop module uses Java 25. See [Java versions and the editor](docs/dev/code.md#java-versions-and-the-editor).
- **Everything `public` has full Javadoc**; `./mvnw verify` fails on a doclint finding. Other code gets a comment only where the reason does not show from the code, and no history in comments. See [Javadoc](docs/dev/code.md#javadoc) and [Comments](docs/dev/code.md#comments).
- **Strict types and plain invariants**: a value with a meaning gets a type, a choice is an enum, `Optional` is for return values and record components only, a record checks its own rules with a plain `if`/`throw`, and nobody catches `NullPointerException` or `Throwable`. See [Types and invariants](docs/dev/code.md#types-and-invariants).
- **The architecture tests keep the packages apart** (the engine knows no desktop, platform or clock). New code keeps every rule; nothing new is frozen unless an issue asks for it, and an ArchUnit rule's description is never reworded. See [Architecture tests](docs/dev/testing.md#architecture-tests).
- **The coverage floors are never lowered** without the maintainer, and a surviving mutant is looked at before a mutation threshold is raised or lowered. See [Coverage](docs/dev/testing.md#coverage) and [Mutation testing](docs/dev/testing.md#mutation-testing).
- **Never log an API key, a request's address or the home's coordinates.** See [Logging](docs/dev/code.md#logging).
- **A library a module uses is declared in its own `pom.xml`**, without a version, and every runtime library has its licence text in `src/packaging/legal/` (and `android/legal/` for the Android app). See [Dependencies](docs/dev/ci-and-releasing.md#dependencies).
- **Pictures in the documentation** are made by code: after a change to a pictured screen, run `./mvnw test -Pscreenshots` and commit what changed. See [Pictures in the documentation](docs/dev/packaging.md#pictures-in-the-documentation).
- **Every text either app shows is translatable**, one file per language and app: see [docs/translating.md](docs/translating.md), which also says how to add a language.
- **Every change reaches `main` through a pull request** with one approving review, and CI (`changes`, `linux`, `windows`) must pass. Documentation-only changes skip the builds. See [CI](docs/dev/ci-and-releasing.md#continuous-integration) and [Repository rules](docs/dev/ci-and-releasing.md#repository-rules).

## Reporting how it runs on your desktop

The app is developed on KDE Plasma under Wayland and checked on Windows. If you try it on another Linux desktop, please open a **Does it work on my desktop?** issue: it takes five minutes and tells us what works where. How to check notifications, and what to say in the report, is in [Reporting how it runs on your desktop](docs/dev/testing.md#reporting-how-it-runs-on-your-desktop).

## Where to read more

| File | Read it when |
| --- | --- |
| [docs/dev/code.md](docs/dev/code.md) | You write or change code: Java versions and the editor, code layout (modules and packages), Javadoc, comments, types and invariants, logging. |
| [docs/dev/testing.md](docs/dev/testing.md) | You write a test or a PR needs checking: how the tests run, architecture tests, what is tested where, coverage floors, mutation testing (PIT), live providers, the checklist Before a release, what a test should do, reporting how it runs on your desktop. |
| [docs/dev/android.md](docs/dev/android.md) | You touch `android/` or the engine's sources: building, the app's unit and instrumented tests, its CI jobs, trying the app and installing the debug build. |
| [docs/dev/ci-and-releasing.md](docs/dev/ci-and-releasing.md) | You touch a workflow, a `pom.xml` or a library, or you make a release: the workflows, repository rules, Releasing, Java updates, Dependencies and licence files. |
| [docs/dev/packaging.md](docs/dev/packaging.md) | You build or change a package: the jlink runtime, Linux and Windows packages, SBOMs, the Flatpak and its app ID, pictures in the documentation. |
| [docs/translating.md](docs/translating.md) | You translate the app or add a language. |
| [docs/providers.md](docs/providers.md) | You add a forecast provider. |
| [CONTEXT.md](CONTEXT.md) and [docs/adr/](docs/adr/) | You need the words the project uses, or the reason for a larger decision. |
