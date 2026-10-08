# Documentation, screenshots and review

Status: done (not committed yet)
Type: task
Blocked by: S10, I4

## Acceptance

- README "First run" section with screenshots of each page (neutral example answers, light and dark); the privacy text mirrors the Welcome page.
- Review of the finished work against `docs/design/setup-screen/spec.md`, written next to it, with what could not be verified (a real first run on a clean desktop account, other locales).
- The glossary terms match the code; the design documents for this feature are written for the sanitised set (open-source ticket 07).

## Comments

- `docs/screenshots.py` and `docs/images/` (16 pictures: eight pages, light and dark); the README's "First run" has them (a `<picture>` per page, dark for dark GitHub themes) and the Data and privacy section quotes the Welcome page's text word for word, both kept true by tests.
- Review: `docs/design/setup-screen/review.md` (compliance, decisions to check, bugs found and fixed, a real walk of the wizard against the live services with the requests listed, what could not be verified).
- The glossary gained "Place search"; "Start at login" stays in it for the multi-platform work.
- **Built-in defaults aligned with the typical values** (the open item from S7): `Config()` now holds the typical values; a test pins `TYPICAL_VALUES` to the defaults so they cannot drift.
- Sanitised-set check: nothing personal in the new documents, code or tests.
