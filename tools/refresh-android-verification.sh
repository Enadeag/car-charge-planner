#!/bin/sh
# Refreshes android/gradle/verification-metadata.xml, the SHA-256 of every file the Android build downloads, which
# Gradle checks on every build (#484). It runs every task that CI, the release and the weekly mutation run use, with
# --write-verification-metadata sha256, so each resolves its files, and adds the checksums the file lacks; it never
# changes or removes an entry. Read the diff before you commit it: a new entry is trusted as it was downloaded.
# With --clean the file starts again from its header and configuration, so entries that no build uses any more drop
# out; the old file is kept as android/build/verification-metadata.old.xml to compare with.
# Usage: sh tools/refresh-android-verification.sh [--clean]. Needs what the Android build needs (docs/dev/android.md)
# and access to Google's Maven repository and Maven Central. It records the files of the system it runs on: run it on
# Linux, as CI does.
set -eu
cd "$(dirname "$0")/../android"
metadata=gradle/verification-metadata.xml

case "${1-}" in
  "") ;;
  --clean)
    mkdir -p build
    cp "$metadata" build/verification-metadata.old.xml
    # the header and the configuration stay; Gradle fills the components in again
    sed '/<components>/,$d' build/verification-metadata.old.xml > "$metadata"
    printf '   <components>\n   </components>\n</verification-metadata>\n' >> "$metadata"
    ;;
  *) echo "usage: sh tools/refresh-android-verification.sh [--clean]" >&2; exit 2 ;;
esac

# Without the configuration cache, so the settings and the plugins are resolved again. PIT on one class only: its
# classpath is the full run's, which takes far longer. --continue, so a failing test does not keep later tasks from
# resolving their files.
./gradlew --write-verification-metadata sha256 --no-configuration-cache --continue \
  assembleDebug assembleDebugAndroidTest assembleRelease lint check :app:pitestDebug \
  -PpitClasses='nl.enadeag.carchargeplanner.android.OwnCode*'
