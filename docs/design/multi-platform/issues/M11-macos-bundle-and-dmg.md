# macOS app bundle and dmg

Status: ready-for-agent
Type: task
Blocked by: M6, M7, M10

## Acceptance

- A PyInstaller spec builds `Car charge planner.app` (universal2 where the wheels allow, otherwise arm64 and x86_64 builds), with `LSUIElement`, the bundle id, the `.icns` generated from the SVG at build time, translations included; minimum macOS 13.
- The `.dmg` (drag to Applications) is built in CI on `macos-latest` and attached by the release workflow; a smoke test starts the built app with the flag that exits once it is up.
- Unsigned and not notarised; the README text for the Gatekeeper warning ("right-click, Open") is drafted here.
- The LaunchAgent from M6 points at the bundle's executable.

## Comments
