# Java desktop spike (throwaway)

Small experiments that answer whether a Java (JavaFX) version of the tray app is technically sound.
Results and evidence: [FINDINGS.md](FINDINGS.md). Screenshots: `screenshots/`.
This is not a port: no tests, no structure, made-up example data.

Requirements: a JDK 25 or newer on the PATH (built here with OpenJDK 27 on Linux and Temurin 25 on
Windows). Maven comes through the wrapper. All commands run from this directory (`spike/java`).
On Windows use `.\mvnw.cmd` instead of `./mvnw`.

| # | Experiment | Command |
|---|------------|---------|
| 1a / 2 | AWT `SystemTray`: left click opens a window, right click a menu, a green dot toggles every 5 s, "Show notification" calls `displayMessage` | `./mvnw -q compile exec:exec -Dmain=spike.AwtTray` |
| 1b | dorkbox SystemTray (extra profile, pulls Kotlin and JNA) | `./mvnw -q -Pdorkbox compile exec:exec -Dmain=spike.DorkboxTray` |
| 1c | Own StatusNotifierItem + dbusmenu over dbus-java (Linux) | `./mvnw -q compile exec:exec -Dmain=spike.SniTray` |
| 3 | Linux notification with actions; clicking it raises a window | `./mvnw -q compile exec:exec -Dmain=spike.LinuxNotify` |
| 3 | Windows balloon: AWT tray with a notification after 3 s; clicks are logged | `.\mvnw.cmd -q compile exec:exec -Dmain=spike.AwtTray "-Dargs=--notify-after-3s"` |
| 4 | Dashboard card, follows the system theme (toggle in the header) | `./mvnw -q compile exec:exec -Dmain=spike.Card` |
| 4 | Card as PNG without a desktop | `./mvnw -q compile exec:exec -Dmain=spike.Card "-Dargs=--dark --snapshot=card.png --scale=2"` |
| 5 | The tray app: single instance; start it twice and the second launch shows the first window | `./mvnw -q compile exec:exec -Dmain=spike.App` (and again in a second terminal) |
| 6 | Secret via java-keyring (Windows Credential Manager) | `.\mvnw.cmd -q compile exec:exec -Dmain=spike.Secret` |
| 6 | Secret via own Secret Service client, in a throwaway keyring (Linux) | `./keyring-sandbox.sh` |
| 7 | Linux app-image with a jlink'd runtime | `./package.sh` → `target/dist/car-charge-planner-spike/bin/car-charge-planner-spike` |
| 7 | Windows app-image / installer | `powershell -ExecutionPolicy Bypass -File package.ps1 [-Type exe]` |

Notes

- `spike.App` options: `--hidden` starts in the tray only, `--exit-when-shown` prints the start-up time and quits.
- `spike.Secret` with `--keep` leaves the example entry in the keychain so you can look at it.
- Do not run `spike.SecretDbus` or `spike.Secret` straight against your own login keyring unless you want an
  example entry in it; `keyring-sandbox.sh` uses a private one.
- `package.ps1 -Type exe` needs WiX on the PATH. What worked: .NET SDK 8 plus
  `dotnet tool install --global wix --version 5.0.2`,
  `wix extension add -g WixToolset.Util.wixext/5.0.2`, `wix extension add -g WixToolset.UI.wixext/5.0.2`.
