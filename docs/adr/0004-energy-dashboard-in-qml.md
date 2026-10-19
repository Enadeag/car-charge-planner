---
status: superseded in part by ADR 0005 (the dashboard is built in JavaFX; the design stands)
---

# The interface is an energy dashboard in QML

The app is mostly used at a glance from the tray, and the old Qt Widgets screens looked dated. The new interface, written in QML (ADR 0002) against the engine's plain-data interface (ADR 0001), is one **dashboard**: shown as the popup next to the tray icon, as a window in window mode, and detachable from the popup. It holds the next charge window, grid advice when it is needed, today's solar curve, the battery, and the coming days. Colour carries meaning, the same everywhere: sun amber, grid blue, car and battery green, each with an icon or label too. The app has one look on every system and follows the system's light or dark mode and font. Accessibility (WCAG AA contrast, keyboard, screen-reader labels, 150 and 200% scaling) is a requirement from the start, not a later pass.

## Consequences

- Settings becomes a sidebar with categories and cards, with explicit Save and Cancel, and holds "About" as its last category; the first-run set-up uses the same cards as steps and ends with a preview of the dashboard.
- The app icon becomes a sun arc with a plug or car, and the tray icon a one-colour silhouette with a status dot in the source colours (red when the owner must act).
- Day cards keep a place for trips and away periods with their origin, so a calendar integration and per-day distances fit later without redesigning the screens.
- Prototypes of the dashboard and the icons come before any QML is built.

## Considered options

- **A calm, minimal look** with one accent colour: fits a glance best, but the maintainer wants the information density of an energy dashboard.
- **A compact popup with the full dashboard in a separate window**: two screens to design and keep in step; the popup itself became the dashboard.
- **A native look per system** (Fluent, libadwaita, Breeze, Aqua): four designs to build and test for a dashboard that is a custom design anyway.

## Note, 2026-10-15: the accessibility check of the Java version

Checked for #98 and #41 on the dashboard, Settings and set-up. Keyboard use, WCAG AA contrast (also the focus ring) and 150 and 200% scaling work on Linux and Windows, and tests keep them: every screen is walked with Tab for reading order, a visible focus ring and a name per control as Windows' UI Automation reads it. Screen readers work through UI Automation on Windows (Narrator, NVDA). On Linux, Orca cannot read the app: JavaFX has no AT-SPI support. The maintainer accepts that limit for now; #132 follows OpenJFX.
