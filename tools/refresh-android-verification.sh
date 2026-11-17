#!/bin/sh
# Refreshes android/gradle/verification-metadata.xml, the SHA-256 of every file the Android build downloads, which
# Gradle checks on every build (#484). It runs every task that CI, the release and the weekly mutation run use, with
# --write-verification-metadata sha256, so each resolves its files, and adds the checksums the file lacks; it never
# changes or removes an entry. Read the diff before you commit it: a new entry is trusted as it was downloaded.
#
# Gradle runs with an empty Gradle user home of its own, a temporary folder removed at the end, so it downloads every
# file as a fresh machine (CI, the release) does: with your own cache Gradle reads poms it already parsed from its
# metadata cache and leaves out the parent poms and BOMs a fresh machine fetches
# (https://github.com/gradle/gradle/issues/19228). So the entries are what Google's Maven repository and Maven Central
# serve at the time, and your ~/.gradle/gradle.properties and init scripts are not used.
#
# The emulator job's connectedDebugAndroidTest needs a device, so the script does not run it: an init script adds a
# task to :app that resolves that task's input files (AGP's test platform among them), so their checksums are recorded.
#
# With --clean the file starts again from its header and configuration, so entries that no build uses any more drop
# out; the old file is kept as android/build/verification-metadata.old.xml to compare with, and is put back should
# Gradle fail.
#
# Usage: sh tools/refresh-android-verification.sh [--clean], from anywhere in the repository. Needs what the Android
# build needs (docs/dev/android.md: JAVA_HOME on a JDK 21, the Android SDK) and access to Google's Maven repository,
# Maven Central and services.gradle.org. It records the files of the system it runs on: run it on Linux, as CI does.
set -eu
cd "$(dirname "$0")/../android"
metadata=gradle/verification-metadata.xml
old=build/verification-metadata.old.xml

usage() {
  echo "usage: sh tools/refresh-android-verification.sh [--clean]" >&2
  exit 2
}
case $# in
  0) clean=false ;;
  1) [ "$1" = --clean ] || usage; clean=true ;;
  *) usage ;;
esac

if [ ! -f "$metadata" ]; then
  echo "android/$metadata is missing: restore it with git checkout -- android/$metadata" >&2
  exit 1
fi

# on disk, as /tmp is often in memory and the downloads take a few gigabytes
home=$(mktemp -d "${TMPDIR:-/var/tmp}/refresh-android-verification.XXXXXX")
restore=false
finish() {
  status=$?
  # Gradle's daemon of this home, and Kotlin's, whose classpath lies in it
  if [ -d "${home:?}/daemon" ]; then
    GRADLE_USER_HOME="$home" ./gradlew --stop > /dev/null 2>&1 || true
  fi
  pkill -f -- "${home:?}/" 2> /dev/null || true
  rm -rf "${home:?}"
  if [ "$status" -ne 0 ] && [ "$restore" = true ]; then
    cp "$old" "$metadata"
    echo "The refresh failed: android/$metadata is back as it was before --clean." >&2
  fi
  exit "$status"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

if [ "$clean" = true ]; then
  # the line to cut at: <components>, or <components/> in a file without entries
  cut_at='^ *<components/\{0,1\}> *$'
  if [ "$(grep -c "$cut_at" "$metadata" || true)" != 1 ]; then
    echo "android/$metadata has no single <components> line to start again from: restore it from git" >&2
    exit 1
  fi
  mkdir -p build
  cp "$metadata" "$old"
  restore=true
  # the header and the configuration stay; Gradle fills the components in again
  sed "\\%$cut_at%,\$d" "$old" > "$metadata"
  printf '   <components>\n   </components>\n</verification-metadata>\n' >> "$metadata"
fi

# :app:connectedTestInputs resolves the files connectedDebugAndroidTest takes, without a device and without running it.
cat > "$home/connected-test-inputs.gradle" << 'EOF'
allprojects {
    if (path == ':app') {
        tasks.register('connectedTestInputs') {
            def connected = tasks.named('connectedDebugAndroidTest')
            doLast { logger.lifecycle("${connected.get().inputs.files.files.size()} input files of ${connected.name}") }
        }
    }
}
EOF

# Without the configuration cache, so the settings and the plugins are resolved again. PIT on one class only: its
# classpath is the full run's, which takes far longer. --continue, so a failing test does not keep later tasks from
# resolving their files; the script still fails then.
GRADLE_USER_HOME="$home" ./gradlew --no-daemon --write-verification-metadata sha256 --no-configuration-cache \
  --continue --init-script "$home/connected-test-inputs.gradle" \
  assembleDebug assembleDebugAndroidTest assembleRelease lint check :app:printReleaseVersion :app:pitestDebug \
  :app:connectedTestInputs -PpitClasses='nl.enadeag.carchargeplanner.android.OwnCode*'
