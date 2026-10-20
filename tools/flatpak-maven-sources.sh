#!/bin/sh
# Writes src/packaging/flatpak/maven-sources.json: every file the Flatpak's Maven build downloads (the libraries, the
# plugins and what they need), with its address on Maven Central and its SHA-256, for flatpak-builder's offline build.
# Run it after a change to pom.xml, the Maven version in .mvn/wrapper or tools/package-flatpak.sh's Maven build, and
# commit the result. Needs a JDK 25 or newer and the network; it builds the app once, into an empty Maven repository.
set -eu
cd "$(dirname "$0")/.."

repo=$(mktemp -d /tmp/ccp-maven-XXXXXX)
# only the folder made here, whatever the variable holds by then
trap 'case "$repo" in /tmp/ccp-maven-?*) rm -rf -- "$repo" ;; esac' EXIT
sh tools/package-flatpak.sh --resolve "$repo"
java tools/FlatpakMavenSources.java "$repo" src/packaging/flatpak/maven-sources.json
