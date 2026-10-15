# Java with JavaFX, replacing Python and QML

The maintainer is a Java developer with no Python knowledge and has to be able to review every change, so the app is rewritten in Java 25 with JavaFX (and the AtlantaFX theme) instead of continuing in Python with QML. A spike (branch `prototype/java-spike`, `spike/java/FINDINGS.md`) showed the risky parts work: a crisp tray icon with menu and status dot on KDE Wayland through our own StatusNotifierItem over D-Bus, notifications with a click action, a modern dashboard card in light and dark, single instance, the system keychain, and jpackage installers on Linux and Windows. The price is memory: about 150–200 MB at rest on Linux against 90 MB for the Python app, accepted for now and tuned later.

The engine stays as ADR 0001 describes it. The dashboard design of ADR 0004 stays; only its technology changes from QML to JavaFX.

## Consequences

- Plain Java, no application framework: dependencies come in through constructors only, all wiring sits in one composition root in `main`, and there is no static mutable state. ArchUnit tests guard this and keep the engine free of JavaFX, AWT and D-Bus. A compile-time injection library (Avaje Inject) is reconsidered only when the wiring grows unwieldy.
- One Maven module, built with the Maven wrapper, with packages for the engine, the desktop app and each platform.
- On Linux the tray icon, notifications and the keychain go through D-Bus (dbus-java) with our own small clients; on Windows through AWT `SystemTray` and the Credential Manager. AWT's tray is not used on Linux: on Wayland it shows a grey square that ignores clicks.
- Installers are built with jpackage and a jlink'd runtime (WiX 5 on Windows) instead of PyInstaller and Inno Setup; the rest of ADR 0003 (unsigned until SignPath at the launch) stands.
- The Java code grows in the repository root next to the Python code, on `main`, in small pull requests, starting with a thin working app from forecast to tray and dashboard and widening from there. The Python version is frozen (critical fixes only) and removed in one pull request once the Java version does everything v0.1.0 does, in the new design; that release is v0.3.0.
- The Java version defines its own files and keychain entries; it does not read the Python version's settings.
- The GitHub Actions workflows are switched off during the port; every change is tested locally and on the Windows test machine, and the workflows return with the switch.

## Considered options

- **Stay with Python and QML**, with pull requests explained in Java terms: keeps the working code, but leaves the maintainer dependent on someone else's judgement for every change.
- **Kotlin with Compose Multiplatform**: a very modern look and a later path to phone and web, but a second language to learn and Gradle instead of Maven.
- **Swing with FlatLaf**: mature, but a dated model for a dashboard.
- **A server framework (Spring Boot, Quarkus, Micronaut)**: built for servers; adds start-up time and memory and fights JavaFX's own life cycle, for dependency injection that constructors already give.
- **GraalVM native-image**: less memory, but fragile with JavaFX; kept as a later option.
