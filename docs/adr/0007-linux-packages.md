---
status: accepted
---

# Linux packages: .deb and .rpm now, Flathub next

Until now the app came for Linux only as a tar.gz of the jlink app image, which the user unpacks and adds to the menu with `install-launcher`. Installing and updating should be easier than that. The maintainer decided on two steps:

1. **Now: a `.deb` and an `.rpm`**, made by jpackage from the same app image as the tar.gz and attached to each release beside it. They install the app in `/opt/car-charge-planner` with a menu entry for every user. They are a quick step: the release workflow already has everything they need, and they install with the distribution's own tools (`apt`, `dnf`, `zypper`). They bring **no automatic updates**: there is no repository behind them, so a user installs each new release by hand, over the old one.
2. **Later: Flathub**, as separate work (#217; #131 asked for this decision). Flathub is meant to become the main way to install on Linux, because it gives automatic updates on every distribution, from one build. It costs more: the app must run in the Flatpak sandbox, with permissions to talk over D-Bus to the tray (`org.kde.StatusNotifierWatcher`), notifications and the Secret Service; start at login must go through the Background portal instead of the app's own entry in `~/.config/autostart`; and Flathub builds from source, so the Maven build and its dependencies have to be described for its offline builder.

The tar.gz stays for other distributions and for people who want no package.

## Considered options

- **Flathub (chosen, later):** automatic updates on every distribution, a store that people search, and a sandbox. The work above (sandbox permissions, the Background portal, a build from source) is why it is not the first step.
- **.deb and .rpm through jpackage (chosen, now):** little work and no new tools, the same content as the tar.gz, a menu entry for everyone. No automatic updates without a package repository (an apt repository, a COPR or the openSUSE Build Service) that someone keeps, so none is set up. jpackage names the menu entry `car-charge-planner-car-charge-planner.desktop` and refers to the icon by its path in `/opt`, not through the icon theme; the app ties its notifications to that entry when it runs from a package.
- **AUR:** not wanted by the maintainer; the tar.gz serves Arch users, and someone else may publish an AUR package from it (#47).
- **AppImage:** one file that runs on most distributions, but it brings no updates without an extra updater, no menu entry without a helper such as AppImageLauncher, and needs FUSE on the user's system.
- **Snap:** automatic updates, but in practice for Ubuntu only, with a store and confinement of its own that the tray, notifications and keychain would have to be fitted to as well; Flathub reaches more distributions for the same kind of work.
- **Only the tar.gz:** no work, but every user unpacks, adds the menu entry and updates by hand.

## Consequences

- Each release has a `.deb` and an `.rpm` beside the tar.gz, in `SHA256SUMS` and in the build attestation. The tar.gz's SBOM covers them, as they hold the same libraries and runtime.
- The release workflow installs the `.deb` on its runner, runs the app's commands and removes it, and checks the files in the `.rpm`; `linux-packages.yml` installs both in Debian and Fedora containers when the packaging changes.
- `install-launcher` in a package install says the package provides the menu entry and writes nothing; `uninstall-launcher` still removes an entry of the user's own, such as one left from the tar.gz. Start at login works unchanged: it starts `/opt/car-charge-planner/bin/car-charge-planner`, which stays the same across updates.
- The packages are not signed, like the other downloads (ADR 0003); users check them with `SHA256SUMS` and the attestation.
- Users update by hand until Flathub is there; the install guide and the release notes say so.
