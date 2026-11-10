# Packaging

Each package brings a runtime made by jlink, so users need no Java of their own, and comes with an SBOM (CycloneDX JSON) of what it holds: `cyclonedx-maven-plugin` lists the libraries, the engine among them, in `desktop/target/car-charge-planner.cdx.json` at `package`, and `tools/SbomRuntime.java` adds the runtime, from the JDK that ran jlink. The jar, the SBOM and the Linux tar.gz take the time of the last commit (or `SOURCE_DATE_EPOCH`), and the tar.gz holds its files in name order, owned by root and not writable by group or others, so the same commit and JDK give the same tar.gz. The packages are not code-signed (ADR 0003); users check them with `SHA256SUMS` and the attestation.

Both scripts take jlink's options, such as the modules and locales, from `src/packaging/jlink.args`, and link JavaFX from the official jmods, downloaded from a pinned address and checked against a pinned SHA-256; they need `curl` and `git` (and `unzip` and GNU tar on Linux). The scripts take the app's jar and its libraries from `desktop/target/` (`car-charge-planner.jar` and `lib/`), do their own work in `target/` at the root and put the products in `target/dist/`. A new JavaFX version in the parent `pom.xml` needs the new address and checksum in both scripts (`PackagingTest` checks the version). Each package has a `legal` folder (in the `.deb` and `.rpm` `lib/legal`, as jpackage packages only `bin` and `lib`) with `LICENSE`, `NOTICE`, `ADDITIONAL-TERMS.md` and the files of `src/packaging/legal/` (see [Licence files](ci-and-releasing.md#licence-files)), and the Windows installer shows the GPL.

- **Linux:** `sh tools/package-linux.sh` makes `target/dist/car-charge-planner/` (start `bin/car-charge-planner`), `target/dist/car-charge-planner-<version>-linux.tar.gz` and its SBOM `car-charge-planner-<version>-linux.cdx.json`, and from the same app image `car-charge-planner_<version>_amd64.deb` and `car-charge-planner-<version>-1.x86_64.rpm` (jpackage's own names; ADR 0007).
  - The packages install the app in `/opt/car-charge-planner` and a menu entry for every user, `car-charge-planner-car-charge-planner.desktop` (jpackage names it after the package and the launcher), made from `src/packaging/linux/car-charge-planner.desktop`; its icon is the 256-pixel PNG in the app's `lib` folder, not an icon theme.
  - The system libraries they depend on are listed by hand, as jpackage would take the build machine's package names: in `src/packaging/linux/control` for the `.deb` (Debian and Ubuntu names) and in the script for the `.rpm` (by soname); a JavaFX or Java update that links another library needs it in both (`readelf -d` on the app image shows them).
  - The tar.gz's SBOM describes the packages too, as they hold the same libraries and runtime.
  - The script needs `dpkg-deb` and `fakeroot` for the `.deb` and `rpmbuild` for the `.rpm` (Ubuntu: `apt install fakeroot rpm`); it stops before the build when one is missing, and `--tar-only` skips the packages.
  - `sh tools/test-linux-packages.sh target/dist` installs the packages in fresh `debian:stable` and `fedora:latest` Docker containers, runs the app's commands there and removes them again.
- **Windows:** `powershell -ExecutionPolicy Bypass -File tools\package-windows.ps1` makes a per-user installer, `target\dist\car-charge-planner-<version>.exe`, and its SBOM `car-charge-planner-<version>-windows.cdx.json`; `-Type app-image` makes the program folder only. The installer needs WiX 5: install the .NET SDK 8, then run `powershell -ExecutionPolicy Bypass -File tools\install-wix.ps1`, which installs WiX 5.0.2 and its Util and UI extensions for you from packages checked against pinned SHA-256 sums. WiX 6 and later ask to accept a maintenance fee agreement first.

`sh tools/scan-sbom.sh target/dist/*.cdx.json` checks SBOMs against the [OSV](https://osv.dev) vulnerability database with a pinned, checksum-checked OSV-Scanner, and fails on any known vulnerability; the release workflow runs it on both SBOMs, the Linux one and the Windows one. A finding that was looked at and does not reach the app goes in `osv-scanner.toml` at the repository root, with the reason and a date it expires. OSV knows the libraries, not the Java runtime: that is what [Java updates](ci-and-releasing.md#java-updates) is for.

The app icon's drawings are in `src/packaging/icons/`: `car-charge-planner.svg` is the icon the PNGs in `desktop/src/main/resources/.../desktop/system/icons/` and `src/packaging/windows/car-charge-planner.ico` show, and `car-charge-planner-symbolic.svg` has the shapes the tray icon draws (`StatusIcon`).

## Flatpak

The Flatpak ([ADR 0008](../adr/0008-flatpak.md)) is built from source by flatpak-builder, offline, from the files in `src/packaging/flatpak/`: the manifest `<app ID>.yml`, the AppStream metadata `<app ID>.metainfo.xml`, the menu entry `<app ID>.desktop`, `maven-sources.json` and `flathub.json`. The manifest runs `tools/package-flatpak.sh`, which builds the same app image as `tools/package-linux.sh` with the JDK 25 of the `org.freedesktop.Sdk.Extension.openjdk25` extension and installs it in `/app`, with the menu entry, the metadata, the icons and the licences. It is not on Flathub yet (#217), so the install guide does not mention it.

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

## Pictures in the documentation

The pictures that the README and the pages in `docs/` show (`docs/screenshots/java/`) are made by code, each in light and in dark, as `<name>-light.png` and `<name>-dark.png`. After a change to what a pictured screen shows, make them again and commit the ones that changed:

```bash
./mvnw test -Pscreenshots    # prints which pictures changed, or "No picture changed."
```

It runs like the other JavaFX tests: offscreen on JavaFX's headless platform, in a JVM that cannot reach your display. The screens show the example home of the tests, in English: `docs/example-engine.json`, the made-up place Zonnedorp, a forecast made up in the code and a fixed date and time, so the pictures are the same on every run. A file is written only when its pixels change, and holds nothing but the pixels, so a run without a change leaves git clean. `ReadmePictures` lists the pictures, and `DashboardPictures`, `SetUpPictures` and `SettingsPictures` make them; `ReadmePicturesTest` makes them all into a temporary folder in every build, and checks that each picture the README and the `docs/*.md` pages name is made and that a second run changes nothing. A new picture goes in `ReadmePictures`, and in the page that shows it as a `<picture>` with its dark file as the `prefers-color-scheme: dark` source; a page in `docs/` names it relative to itself, as `screenshots/java/<name>-light.png`.

The text is drawn in JavaFX's system font, which differs per system (Noto Sans on most Linux desktops, Segoe UI on Windows): make the pictures on Linux with Noto Sans, or a run changes every picture.
