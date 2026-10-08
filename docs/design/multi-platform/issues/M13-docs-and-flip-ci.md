# Documentation for three systems, CI required

Status: ready-for-agent
Type: task
Blocked by: M9 (Windows part), M12 (macOS part, later)

## Acceptance

- README: install and run on each system (installer first, `pipx` second), what "supported" and "beta" mean and which system is which, the SmartScreen and Gatekeeper notes, where files live, how to start at login, a "Supported systems" table with the minimum versions (Windows 10+, macOS 13+, Linux with glibc 2.34+).
- The wording "KDE and GNOME" becomes "Linux, Windows and macOS" in the README, the Settings hint for the interface mode, `CONTRIBUTING.md` and the launch backlog drafts (open-source 08/09), without promising what is not verified.
- `CONTRIBUTING.md`: how to run the tests on each system and how the markers work.
- CI: Windows becomes a required check when M9 passes; macOS a required check when M11 lands (demoted to advisory only if it proves flaky, with a comment saying why).
- When M12 passes: macOS moves from beta to supported in the README and a release note.

## Comments
