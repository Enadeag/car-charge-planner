#!/bin/sh
# Builds the app inside flatpak-builder and installs it in the Flatpak (docs/adr/0008-flatpak.md). The manifest,
# src/packaging/flatpak/<app ID>.yml, runs it in the source folder, offline, after flatpak-builder has put there:
# Maven in maven/, every Maven file the build needs in maven-local/ (maven-sources.json) and JavaFX's jmods in
# javafx-jmods/, each checked against its SHA-256. flatpak-builder sets FLATPAK_ID and FLATPAK_DEST (/app).
#
# The app image is the one tools/package-linux.sh makes, installed in /app so that its launcher is
# /app/bin/car-charge-planner, with the menu entry, the AppStream metadata, the icons and the licences.
#
# "sh tools/package-flatpak.sh --resolve <empty folder>" only runs the same Maven build with ./mvnw, online, into
# that folder as its local repository: tools/flatpak-maven-sources.sh lists what it downloads.
set -eu
cd "$(dirname "$0")/.."

# One Maven build for both: the jar and its runtime libraries in target/lib, without the tests.
build() {
  "$@" -B clean package -Dmaven.test.skip=true
}

case "${1:-}" in
  --resolve)
    build ./mvnw -Dmaven.repo.local="$2"
    exit 0 ;;
  "") ;;
  *) echo "usage: sh tools/package-flatpak.sh [--resolve <empty folder>]" >&2; exit 2 ;;
esac

id=${FLATPAK_ID:?run by flatpak-builder, which sets FLATPAK_ID}
dest=${FLATPAK_DEST:?run by flatpak-builder, which sets FLATPAK_DEST}

build maven/bin/mvn -o -Dmaven.repo.local="$PWD/maven-local"
version=$(sed -n 's/^version=//p' target/classes/nl/enadeag/carchargeplanner/start/version.properties)

# As tools/package-linux.sh: JavaFX from its jmods, linked into the runtime; the app's jar and its other libraries.
mkdir -p target/input
rm target/lib/javafx-*.jar
cp target/lib/*.jar target/car-charge-planner.jar target/input/
jlink --module-path javafx-jmods @src/packaging/jlink.args --output target/runtime
jpackage --type app-image --dest target/flatpak \
  --name car-charge-planner --app-version "${version%-SNAPSHOT}" \
  --input target/input --main-jar car-charge-planner.jar \
  --main-class nl.enadeag.carchargeplanner.Main \
  --runtime-image target/runtime \
  --java-options "--enable-native-access=javafx.graphics"

mkdir -p "$dest/bin" "$dest/lib"
cp -a target/flatpak/car-charge-planner/bin/. "$dest/bin/"
cp -a target/flatpak/car-charge-planner/lib/. "$dest/lib/"

# Named after the app ID, as Flathub asks; the menu entry's Icon= is the ID too.
install -Dm644 "src/packaging/flatpak/$id.desktop" "$dest/share/applications/$id.desktop"
install -Dm644 "src/packaging/flatpak/$id.metainfo.xml" "$dest/share/metainfo/$id.metainfo.xml"
install -Dm644 src/packaging/icons/car-charge-planner.svg "$dest/share/icons/hicolor/scalable/apps/$id.svg"
for png in src/main/resources/nl/enadeag/carchargeplanner/desktop/system/icons/icon-*.png; do
  size=${png##*/icon-}
  size=${size%.png}
  install -Dm644 "$png" "$dest/share/icons/hicolor/${size}x${size}/apps/$id.png"
done

# The licences: the app's own and one file per library; the runtime's are in lib/runtime/legal.
install -d "$dest/share/licenses/$id"
cp LICENSE NOTICE ADDITIONAL-TERMS.md src/packaging/legal/*.txt "$dest/share/licenses/$id/"
