---
status: accepted
---

# An Android app beside the desktop app, sharing the engine

The app runs on Linux and Windows, as a JavaFX desktop app with the engine inside it (ADR 0001, 0005). A phone is with the owner all day and is where a "charge tonight" notification is most useful, so an Android app is added beside the desktop app. This records what it is, what it shares with the desktop app, and what changes in the code base to make that possible. It was settled in one design session on 2026-10-30; the work is in the milestones `v0.6.0 Shared engine` (#258) and `android-v0.1.0` (#259).

## The decision

**What the Android app is.**

- **Standalone.** The engine runs on the phone: it fetches the forecasts itself and makes the plan itself. It needs no desktop app and no server. Syncing with the desktop app may come later, as a feature of its own.
- **The first version does what the desktop app does**, except calendar trips and the route service, which leave the code base (below). Trips entered by hand are part of it.

**Calendar trips and the route service are withdrawn.** Layer 2 of ADR 0009 (the calendar as a source of trips, with Geoapify as the route service) is not what it should be, and needs a new design before it comes back. None of it was released, so it is removed from the code base, with ical4j, rather than carried along into the Android app. Layer 1, trips entered by hand (v0.5.0), stays and may be refined.

**What is shared.**

- **The engine only.** The interfaces are built for each platform: JavaFX on the desktop, **Kotlin with Jetpack Compose (Material 3)** on Android. The engine stays Java; Kotlin can call it directly, and Java and Kotlin can live in one module should the engine move to Kotlin bit by bit later.
- **One repository** with `engine/`, `desktop/` and `android/`, so a change to the engine and its effect on both apps go in one pull request. **Maven stays** for the engine and the desktop app. The Android **Gradle** build compiles the engine's sources from `engine/src/main/java` directly: no published jar, no install step; the engine's few dependencies are declared in both builds, and a check keeps the lists equal.
- **The engine is held to Java 17** (`--release 17` in its module), so the compiler itself rejects newer language features and APIs. Android's build tools are confirmed for Java 17, and Android's own Java library is roughly that of Java 17 on Android 14 and only partly that of Java 21 on Android 15: a newer API in the engine would build and then crash on older phones. The desktop module stays on Java 25. The minimum Android version is **Android 8 (API 26)**, with core library desugaring (the nio variant).
- **Platform seams.** Where the engine uses something Android lacks or does differently, the engine says what is needed and each platform says how, as the desktop app already does for Linux and Windows: HTTP (`java.net.http` does not exist on Android; the engine keeps its `HttpGet` interface, the desktop keeps `JdkHttpGet`, Android uses `HttpURLConnection`), logging (slf4j instead of `System.Logger`), making a file private (POSIX permissions on the desktop; Android's app storage is private already) and secrets (the OS keyring on the desktop; on Android encrypted with a key from the Android Keystore).

**How the Android app runs.**

- **Background work:** WorkManager refreshes the forecast and the plan, about hourly. For each announcement an inexact `AlarmManager.setWindow` timer wakes the app at the window's start minus the lead time, within about ten minutes, also in Doze. It is an internal timer: the owner sees only the notification. It needs no exact-alarm permission; only the notification permission Android 13 asks for.
- **Texts:** Android's own `strings.xml`, in English and Dutch, maintained apart from the desktop's resource bundles, as the screens and sentences differ. What must stay the same, the words for the terms and the way the texts speak to the owner, is in `docs/translating.md`, a guide for developers and translators of both apps, not in `CONTEXT.md`.

**How it is released.**

- **Versions per app:** the desktop app keeps `vX.Y.Z`; the Android app gets `android-vX.Y.Z` tags and `release/android-X.Y.Z` branches. The engine has no version of its own, as it is not released on its own. One changelog, with a heading per app.
- **F-Droid and GitHub Releases** first, Google Play maybe later. No Google libraries (Play Services, Firebase), so F-Droid can build the app and Play stays possible.
- **Reproducible builds and an own signing key**, so F-Droid can ship the app under that key once its build matches the release. Google's developer verification, enforced in four countries since 30 September 2026 and expected wider in 2027, ties a package name to the developer's key; this keeps both F-Droid and the APK open.
- **The signing key** is a secret in the GitHub environment `android-release`, used only after the maintainer approves, on an `android-v*` tag; an offline backup is kept, as a lost key means the app can never be updated again.
- **Tests:** the engine's tests stay in Maven. Android's code and screens are tested with Robolectric on every pull request; an emulator job on API 26 runs the instrumented tests (timers, notifications, the Keystore) on `release/android-*` branches and weekly. Before a release the app is checked by hand on a real phone.
- **The app ID** is `io.github.enadeag.car_charge_planner`, after the Flatpak ID.

## Considered options

- **A companion app** that shows and changes what the desktop app plans: the desktop app would have to become a server that is always on, and the phone app would be useless without it.
- **A first version with only the plan and its notification:** smaller, but the owner wanted the whole app; only the calendar part, which is withdrawn anyway, is left out.
- **JavaFX on Android through Gluon and GraalVM:** some of the desktop interface could be reused, but the tooling is a niche, the build is hard, and the interface was not made for touch. **Java with Android Views:** keeps one language, but Views is the older way and Compose cannot be used from Java.
- **A repository of its own for the Android app,** using the engine as a published library: separate builds, but every engine change would need an engine release and a version bump, while the engine is still changing.
- **All of it on Gradle:** the cleanest build, but the whole desktop pipeline (jlink, jpackage, PIT, CycloneDX, the Linux packages and the offline Flatpak build) would have to move for it. **The engine jar through the local Maven repository:** works, but an Android build can silently use a stale engine when the install step is forgotten.
- **A Java 25 engine jar used as it is:** Android's build tools are not confirmed for Java 25 class files, and the APIs a jar calls must exist on the phone. **Java 21 with Android 15 as the minimum:** fewer rewrites, but it leaves out many phones in use. **Rewriting the engine in Kotlin, or in Kotlin Multiplatform:** a large rewrite right after the Java port, before Android does anything; Kotlin Multiplatform only pays off with an iOS app, which is not planned.
- **Exact alarms:** to the minute, but the owner has to grant "Alarms and reminders" since Android 14, for ten minutes that do not matter. **A foreground service that always runs,** as on the desktop: a permanent notification and more battery use. **WorkManager alone:** a notification could come far too late.
- **Texts generated from the desktop's resource bundles:** one source, but different sentences and placeholder styles would need tooling full of exceptions.
- **One version for both apps:** each app would wait for the other, and a fix in one would release the other unchanged.
- **Google Play first:** the most users, but a paid account with identity checks whose name is shown, and a closed test before the app may be public.
- **Signing on the maintainer's computer only:** the key never leaves it, but releases are built outside the recorded build that F-Droid has to match.

## Consequences

- ADR 0009's layer 2 is withdrawn: its issues (#36, #236–#239) and the `v0.6.0 Calendar trips` milestone are closed, and the code goes in #261. A calendar feature comes back only with a new design and an ADR of its own.
- The build becomes a Maven multi-module project; contributors and CI build `engine` and `desktop`, and Android with Gradle in `android/`.
- The engine may not use Java 18 or newer, which the compiler enforces; contributors need to know why.
- `CONTEXT.md` loses its `_Dutch_` lines to `docs/translating.md`, and the translation part of `CONTRIBUTING.md` becomes a pointer to it.
- CI grows Android jobs, and a release environment holds a key whose loss cannot be undone.
- The README, install guide and privacy page get an Android part when the app ships.
