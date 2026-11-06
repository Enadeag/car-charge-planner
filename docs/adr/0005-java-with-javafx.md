---
status: accepted
---

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

## Note, 2026-10-30

The build is no longer one Maven module: a parent with the modules `engine` and `desktop` (ADR 0010, #263). The engine module holds only the engine and builds without JavaFX; the desktop module holds the rest and still builds `car-charge-planner.jar`.

## Note, 2026-10-19 (#161)

Memory and start-up time were measured on the packaged app, five runs per option on Linux and Windows, headless with software rendering (the Linux runs on JDK 27, not the release's JDK 25). Without options: on Linux about 253 MiB (PSS) at rest, 367 MiB with the dashboard open and 381 MiB after closing it, and 0.79 s to the tray; on Windows about 103 MiB private working set at rest, 129 MiB with the dashboard open, and 0.83 s until the app is ready. What helped: a heap cap (`-Xmx96m`) took about 70 MiB off the Linux figures with the dashboard open and after closing it, and nothing on Windows; G1 with lower free ratios and a periodic GC brought Linux after closing to 307 MiB and Windows at rest to 87 MiB; an AOT cache shortened the start (Linux 0.66 s, Windows 0.38 s) but added memory on Windows. `-XX:+UseSerialGC` alone made both worse. About 110–120 MiB at rest on Linux is native memory outside the JVM, which no JVM option reaches. Decided 2026-10-18: no change to the launcher options; the issue was closed on 2026-10-19 with the numbers kept on it. Reopen it when memory use or start-up time becomes a real problem for users.
