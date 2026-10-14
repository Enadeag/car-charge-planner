#!/bin/sh
# Question 6, Linux: run the secret-storage experiments against a throwaway gnome-keyring on a private
# session bus, so the real login keyring is never touched.
# Usage: ./keyring-sandbox.sh            (needs gnome-keyring and dbus-run-session)
set -e
cd "$(dirname "$0")"
./mvnw -q -B compile
CP="$PWD/target/classes:$(./mvnw -q -B dependency:build-classpath -Dmdep.outputFile=/dev/stdout)"
# AF_UNIX paths are limited to ~108 bytes, so keep the sandbox short and under the user's runtime dir.
SANDBOX="${XDG_RUNTIME_DIR:-/tmp}/ccp-spike-kr"
rm -rf "$SANDBOX"
mkdir -p "$SANDBOX/run" "$SANDBOX/data"
chmod 700 "$SANDBOX" "$SANDBOX/run"

env -u GNOME_KEYRING_CONTROL -u SSH_AUTH_SOCK \
  XDG_RUNTIME_DIR="$SANDBOX/run" XDG_DATA_HOME="$SANDBOX/data" HOME="$SANDBOX" \
  dbus-run-session -- sh -c '
    printf throwaway | gnome-keyring-daemon --unlock --components=secrets --daemonize >/dev/null
    sleep 1
    echo "== own Secret Service client over dbus-java 5 (spike.SecretDbus)"
    java -cp "$0" spike.SecretDbus 2>&1 | grep -v "INFO\|^WARNING"
    echo "== java-keyring 1.0.4 on this class path (dbus-java 5): expected to fail"
    java --enable-native-access=ALL-UNNAMED -cp "$0" spike.Secret 2>&1 | grep -v "INFO\|^WARNING\|^\s*at " | head -3
  ' "$CP"
rm -rf "$SANDBOX"
