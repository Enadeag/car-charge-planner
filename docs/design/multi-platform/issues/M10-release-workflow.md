# Release by tag: a draft GitHub Release

Status: ready-for-agent
Type: task
Blocked by: M8

## Acceptance

- A workflow triggered by a tag `v*` builds the wheel and sdist (with compiled translations), the Windows installer and, once M11 exists, the macOS dmg, and attaches them to a **draft** GitHub Release; it never publishes.
- The version is read from the package; the workflow fails if the tag and the version differ.
- Checksums (`SHA256SUMS`) are attached.
- Signing steps exist but are skipped without their secrets (documented in the workflow's comments).
- A dry run on a test tag in the private repository is part of the verification, and its draft is deleted afterwards.

## Comments
