# Code signing policy

Free code signing provided by [SignPath.io](https://about.signpath.io), certificate by [SignPath Foundation](https://signpath.org).

The Windows installer, `car-charge-planner-<version>.exe`, is to be signed through the SignPath Foundation programme. Until a release says it is signed, the installer is not, and [Installing](install.md#check-a-download) explains how to check a download with `SHA256SUMS` and GitHub's build attestation instead. The Linux archive is not code-signed; its checksum and attestation are the way to check it.

## What is signed

Only what this project builds from its own source code in this repository: the Windows installer that the [release workflow](../.github/workflows/release.yml) makes from a version tag, on GitHub's own runners. Nothing built on a developer's computer is signed. The libraries and the Java runtime the installer brings are other projects' work; they are included as they are, and their licences are in the download's `legal` folder.

The installer is signed before `SHA256SUMS` and the build attestation are made, so both cover the signed file.

## Team roles

| Role | Who | What the role does |
| --- | --- | --- |
| Committer and reviewer | [Kelvin Vroegop (@Enadeag)](https://github.com/Enadeag) | Changes the source code; reviews and merges every pull request, from anyone, before it reaches `main`. |
| Approver | [Kelvin Vroegop (@Enadeag)](https://github.com/Enadeag) | Approves each signing request by hand, for a release he made from a reviewed commit. |

Every team member uses multi-factor authentication for GitHub and for SignPath. `main`, the `release/*` branches and the `v*` tags are protected: a change reaches `main` only through a reviewed pull request with green CI, and only the maintainer can create a release branch or tag.

## Privacy

The app does not send information to other networked systems unless the owner asks for it: it contacts only the forecast provider the owner chose in set-up or Settings, for a forecast for the owner's location and panels. [Data and privacy](privacy.md) lists exactly what is sent, to whom, and what is kept on the computer. There is no account, no telemetry and no update check. The installer installs for the current user only, without administrator rights, and the app can be uninstalled from Windows' Settings like any other app.
