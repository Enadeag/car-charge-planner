# CI: tests on Linux, Windows and macOS

Status: ready-for-agent
Type: task
Blocked by: open-source 04 (package rename), open-source 06 (CI skeleton, if it exists by then)

Spec sections 2 and 4.

## Acceptance

- A GitHub Actions workflow runs the test suite on Ubuntu, Windows and macOS (offscreen Qt) for every push and pull request, with the dependencies installed from the package.
- Markers `linux`, `windows`, `macos` in the test configuration; tests that only make sense on one system carry them and are skipped elsewhere.
- Windows and macOS jobs are **informational** (`continue-on-error`) until M13 makes them required; the failures they show are the to-do list for M2-M7.
- The workflow also checks that the template is up to date and compiles the translations.

## Comments
