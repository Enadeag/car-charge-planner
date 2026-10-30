# Design documents

These are the working documents behind the code: why it is built the way it is, written while it was built. They are kept because the reasoning is hard to recover from the code alone, and because the findings in them (bugs that cost a day, platform quirks) are worth knowing before you change the same part.

They were written for the first version of the app, in Python with Qt ([ADR 0002](../adr/0002-python-with-qml.md)). The app has since been rewritten in Java with JavaFX ([ADR 0005](../adr/0005-java-with-javafx.md)) and the Python code removed, so the files, modules, commands and file formats they name (`src/car_charge_planner/`, `config.toml`, `pytest` and so on) are in the git history only. The decisions about what the app does still hold unless an ADR says otherwise.

Each folder is one feature and holds a **spec** (what and why, with the decisions and the alternatives that were rejected), a **review** (the finished work checked against the spec, with what could not be verified) and the **tickets** it was built from, each with notes on what was found while doing it.

| Folder | What it covers |
| --- | --- |
| `planner/` | The planner itself: surplus, charge windows, projection, grid-charge advice, the first tray popup |
| `settings-window/` | The Settings window and the TOML configuration behind it |
| `popup-redesign/` | The popup's layout and behaviour |
| `gnome-support/` | Notifications over D-Bus, the window shell, single instance, the launcher entry |
| `setup-screen/` | The first-run set-up wizard, typical values, units, and the translation machinery |
| `forecast-providers/` | The forecast provider contract, the service, Open-Meteo and Forecast.Solar |
| `multi-platform/` | The plan for Windows and macOS (Windows was built; macOS was not) |
| `secrets/` | Decisions about keeping and removing API keys and links (`decisions.md`, from the Java version on) |

## Reading them

- They are history, not a manual. Where a document and the code disagree, the code is right. For using or extending the app read the README, `CONTRIBUTING.md` and `docs/providers.md`.
- Names are those of the time: the Python package was called `app` before it became `car_charge_planner`, so tickets mention `app/` and `python -m app`.
- Every figure that looks like a real home (panel size, charger limits, car consumption) is an **example** used to make the plan concrete.
- Words such as *charge window*, *away period* and *set-up* are defined in `CONTEXT.md`.

## Findings worth knowing about

From the Python version; the Java version meets some of the same problems (D-Bus types, a single-instance race, dark themes) with other libraries.

- **QtDBus and unsigned integers:** QtDBus cannot make the notification call from Python (it sends the id as a signed integer, the protocol needs unsigned), so notifications use `jeepney`; see `gnome-support/review.md`.
- **Single instance race:** `QLocalServer.listen()` on a path another live server holds *succeeds* and takes over its socket, so two simultaneous starts can both believe they are the first; see `gnome-support/issues/05-single-instance.md`.
- **Spin boxes clamp when their range changes** and announce it as if the user had typed it, which recorded a default as an answer; see `setup-screen/review.md`.
- **Dark themes:** several colours (hints, links, pressed buttons) were unreadable until they were derived from the palette; see `settings-window/review.md`.
