---
status: accepted
---

# The engine runs inside the desktop app first

The app is used first by someone with a laptop or desktop computer and no home server; people with a server, a Raspberry Pi or Home Assistant come later. Reading devices (the car's battery, the inverter, the meter) comes after the public launch, and controlling a charger after that, and control needs something that runs all the time. So the app is split into an **engine** (planning, forecasts, devices, scheduling, all stored data) and **clients**, and the engine runs inside the desktop app for now: one program to install, no service, no network port. Later the same engine can also run on its own on an always-on machine, with the desktop app as one of its clients.

## Consequences

- The engine contains no Qt. A thin Qt adapter in the desktop app gives it its timer, threads and the hand-over of events to the user interface.
- Engine and clients talk through plain Python calls in which only plain data crosses the boundary, never Qt objects or windows, so a network transport can be added later without rewriting either side.
- The engine owns all data: settings, state, keys and the forecast cache. A client changes them only by asking the engine; no client code writes a file. The settings split into the user's system and planning preferences (the engine's) and the app's own settings such as language and interface mode (the desktop app's).
- The Windows and macOS installers install the desktop app only.
- Order of work: the engine split and the settings split come before the new interface and the public launch, so the new interface is built against the engine once. How a stand-alone engine is reached over the network (authentication, local network only) is decided when it is built.
- The app complements Home Assistant (which may become one of the engine's data sources) rather than becoming a Home Assistant integration, because the first users have no Home Assistant.

## Considered options

- **Desktop app only, for good**: simplest, but charger control would only work while the computer is on.
- **A separate engine for everyone from the start** (a service on Windows, a daemon on Linux): harder to install and to troubleshoot for every user, now, for a feature that comes later.
- **A Home Assistant integration**: leaves out the users who have no Home Assistant, who come first.

## Notes

- **2026-10-17**: No client writes the engine's files; the desktop app keeps its own app settings. The engine has no interface of its own until it runs on its own: today the desktop app is its only client and calls the `Engine` class, and the interface is cut when a second client, reached over a transport, needs one.
