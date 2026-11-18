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
# The emulator job's connectedDebugAndroidTest and connectedMinifiedAndroidTest need a device, so the script does not
# run them: an init script adds a task to :app that resolves the input files of the one of the tested build type (AGP's
# test platform among them), so their checksums are recorded. The minified build type is a Gradle run of its own, with
# -PtestBuildType=minified as in the emulator job, signed with a throw-away key made in the temporary folder (#485).
#
# With --clean the file starts again from its header and configuration, so entries that no build uses any more drop
# out; the old file is kept as android/build/verification-metadata.old.xml to compare with, and is put back should
# Gradle fail. It is put back too, and the script fails naming each file, when a file of the same component (group,
# name and version) and name has another SHA-256 than before: a changed checksum is no update, and would otherwise
# show only in the folded diff.
#
# Usage, from the repository's root: sh tools/refresh-android-verification.sh [--clean]. Needs what the Android build
# needs (docs/dev/android.md: JAVA_HOME on a JDK 21, the Android SDK) and access to Google's Maven repository, Maven
# Central and services.gradle.org. It records the files of the system it runs on: CI builds on Linux, so the refresh
# that is committed runs on Linux; on Windows or macOS it adds the files only that system gets, for your own builds.
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
    if cp "$old" "$metadata"; then
      echo "The refresh failed: android/$metadata is back as it was before --clean." >&2
    else
      echo "The refresh failed, and android/$metadata could not be put back: copy android/$old over it," \
        "or restore it from git." >&2
    fi
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

# :app:connectedTestInputs resolves the files connectedDebugAndroidTest, or with -PtestBuildType=minified
# connectedMinifiedAndroidTest, takes, without a device and without running it.
cat > "$home/connected-test-inputs.gradle" << 'EOF'
allprojects {
    if (path == ':app') {
        tasks.register('connectedTestInputs') {
            def type = findProperty('testBuildType') ?: 'debug'
            def connected = tasks.named("connected${type.capitalize()}AndroidTest")
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

# The minified build and its test APK, as the emulator job's second Gradle call builds them, with a key of its own.
ANDROID_KEYSTORE_PASSWORD=$(od -An -N24 -tx1 /dev/urandom | tr -d ' \n')
export ANDROID_KEYSTORE_PASSWORD
sh ../tools/android-sign.sh throwaway-key "$home/minified.p12" > /dev/null
GRADLE_USER_HOME="$home" ./gradlew --no-daemon --write-verification-metadata sha256 --no-configuration-cache \
  --continue --init-script "$home/connected-test-inputs.gradle" -PtestBuildType=minified \
  -PminifiedKeystore="$home/minified.p12" :app:assembleMinified :app:assembleMinifiedAndroidTest :app:connectedTestInputs

[ "$clean" = true ] || exit 0

# Each file's checksum as one line, "group:name:version file sha256". This reads the layout Gradle writes, one element
# per line: <component group=".." name=".." version="..">, then for each file <artifact name="..">, then its
# <sha256 value=".." .../>. A file in another layout fails here instead of passing unread.
checksums() {
  # the hold space keeps "group:name:version", and from an <artifact> on "group:name:version file"
  sed -n '
/^ *<component /{
  s/^ *<component group="\([^" ]*\)" name="\([^" ]*\)" version="\([^" ]*\)">$/\1:\2:\3/
  h
}
/^ *<artifact /{
  s/^ *<artifact name="\([^" ]*\)">$/\1/
  x
  s/ .*//
  G
  s/\n/ /
  h
}
/^ *<sha256 /{
  s/^ *<sha256 value="\([0-9a-f]\{64\}\)" .*\/>$/\1/
  G
  s/^\(.*\)\n\(.*\)$/\2 \1/p
}' "$1" > "$2"
  # one line per checksum, each with a component, a file and a checksum in the expected form
  if [ "$(grep -c '<sha256 ' "$1" || true)" != "$(grep -c . "$2" || true)" ] \
    || grep -v '^[^ :<>"]*:[^ :<>"]*:[^ <>"]* [^ <>"]* [0-9a-f]\{64\}$' "$2" > /dev/null; then
    echo "android/$3 is not in the layout this script reads (one element per line, as Gradle writes it)," \
      "so its checksums cannot be compared: see what changed in Gradle's format first" >&2
    exit 1
  fi
}
checksums "$old" "$home/old" "$old"
checksums "$metadata" "$home/new" "$metadata"
# the files of the new file not in the old one as they are; those whose component and name the old one has, changed
grep -Fxv -f "$home/old" "$home/new" > "$home/added" || true
sed 's/ [^ ]*$//' "$home/old" > "$home/old-files"
changed=false
while read -r component file checksum; do
  if grep -Fqx "$component $file" "$home/old-files"; then
    echo "$component $file: its SHA-256 is $checksum now, another than android/$old records" >&2
    changed=true
  fi
done < "$home/added"
if [ "$changed" = true ]; then
  echo "A file that was recorded has another checksum at the repository: that is no update, find out why first." >&2
  exit 1
fi
