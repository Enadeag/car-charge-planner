# Design documents

Each folder here is one feature and holds a **`decisions.md`**: a short list of the decisions about that feature's behaviour (its defaults, edge cases and errors) that the code alone does not explain, grouped by subject, each saying only what holds now, with the one issue or pull request that settled it. `android/` holds a file per part of the app instead, in the same form. It is where the maintainer's review process records such a decision. Where a document and the code disagree, the code is right.

| Folder | What it covers |
| --- | --- |
| `secrets/` | Decisions about keeping and removing API keys and links |
| `android/` | Decisions about the Android app: [set-up](android/setup.md), [the home and dashboard](android/dashboard.md), [Settings](android/settings.md), [notifications and the work in the background](android/notifications.md), [every screen](android/screens.md), and [build, tests and CI](android/build.md) |
| `engine/` | Decisions about the engine that both apps share: files of a newer version, the round, the stored forecast, a refused key, the next notice, the engine factory, the dashboard model |
| `trips/` | Decisions about trips entered by hand that ADR 0009 does not carry: the charging hours' edge cases and the trip notices |

The design documents of the Python version (specs, reviews and tickets) were removed on 2026-11-10 and are in the git history before that date.

Words such as *charge window*, *away period* and *set-up* are defined in `CONTEXT.md`.

## Findings worth knowing about

Found in the Python version; the Java version meets the same problems with other libraries.

- **D-Bus integer types:** a desktop notification over D-Bus needs its id as an unsigned integer, and a library that sends it as a signed one makes the call fail.
- **Single instance race:** listening on a socket path that another live server holds can succeed and take over its socket, so two simultaneous starts can both believe they are the first.
