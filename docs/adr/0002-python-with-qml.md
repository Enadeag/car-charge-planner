---
status: superseded by ADR-0005
---

# Python for the engine and the desktop app, with QML for the interface

The engine and the desktop app stay in Python, and the new interface is written in Qt Quick (QML) instead of Qt Widgets, so the engine and the desktop app share one language while the interface can look modern. A web or phone client, if one comes, talks to the engine through its interface and may use another technology.

## Considered options

- **Dart and Flutter**, or **TypeScript**: one code base for desktop, web and phone, but a full rewrite of a working and well-tested code base just before the launch, for a phone app that is not the main product.
- **Rust or Go for the engine only**: a smaller and faster program on a Raspberry Pi, at the price of two languages, for gains in memory and start-up time that a planner waking a few times a minute does not need.
- **Qt Widgets with a modern style**: keeps the current screens, but a modern look and smooth animation are much harder to reach than in QML.
