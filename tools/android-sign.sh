#!/bin/sh
# Signs the Android release APK and checks it before and after, for android-release.yml (#272), which runs each step
# below as a step of its own; it can be run by hand the same way, with a throw-away key.
#
#   check-unsigned APK SHA256 APP_ID VERSION_NAME VERSION_CODE
#       the APK has that SHA-256 (the build job's), is unsigned and zip-aligned, carries that app ID, version name
#       and version code, and is neither debuggable nor test-only
#   sign UNSIGNED KEYSTORE SIGNED
#       signs with the key "release" of KEYSTORE, whose password is in ANDROID_KEYSTORE_PASSWORD, with the APK
#       Signature Schemes v2 and v3 only, for Android 10 (API 29) and newer
#   check-signed APK APP_ID VERSION_NAME VERSION_CODE DEBUG_KEYSTORE [FINGERPRINT_FILE]
#       the APK verifies with v2 and v3 and without v1, has exactly one signer, whose certificate is not that of
#       DEBUG_KEYSTORE and, with FINGERPRINT_FILE, is the one recorded there; and it is zip-aligned, carries that app
#       ID, version name and version code, and is neither debuggable nor test-only
#   certificate APK
#       prints the SHA-256 digest of the one signer's certificate of APK, as apksigner shows it
#   fingerprint FINGERPRINT_FILE
#       prints the SHA-256 fingerprint FINGERPRINT_FILE records, as 64 lowercase hex digits, or fails when it holds
#       none: its one line that is not blank and does not start with #, with or without colons, in either case
#   throwaway-key KEYSTORE
#       makes a new PKCS12 keystore with a key "release", whose password is ANDROID_KEYSTORE_PASSWORD, for a dry run
#
# BUILD_TOOLS names the folder of build-tools 36.0.0 (apksigner, aapt2, zipalign); by default the one in ANDROID_HOME.
# Also needs keytool, unzip and sha256sum. Never prints a password. A failing tool fails the script: sh has no
# pipefail, so a tool's output is captured, and its exit status tested, before it is filtered.
set -eu

# On stderr, so a caller that captures stdout still shows it; GitHub reads workflow commands from stdout only, so in
# Actions the annotation goes there as well.
fail() {
  echo "error: $*" >&2
  if [ "${GITHUB_ACTIONS:-}" = true ]; then echo "::error::$*"; fi
  exit 1
}

usage() {
  echo "usage: sh tools/android-sign.sh check-unsigned|sign|check-signed|certificate|fingerprint|throwaway-key ..." >&2
  echo "(the comment at the top of tools/android-sign.sh describes each)" >&2
  exit 2
}

[ $# -gt 0 ] || usage
tools=${BUILD_TOOLS:-${ANDROID_HOME:-}/build-tools/36.0.0}
apksigner="$tools/apksigner"
aapt2="$tools/aapt2"
zipalign="$tools/zipalign"
alias=release
min_sdk=29

need_build_tools() {
  for tool in "$apksigner" "$aapt2" "$zipalign"; do
    [ -x "$tool" ] || fail "$tool is not an executable: set BUILD_TOOLS to the folder of build-tools 36.0.0, or \
ANDROID_HOME to the Android SDK that holds it"
  done
}

need_apk() {
  [ -f "$1" ] && [ -r "$1" ] || fail "$1 is not a readable file"
}

# Sets sha256 to the SHA-256 of file $1.
sha256_of() {
  sum=$(sha256sum < "$1") || fail "sha256sum cannot read $1"
  sha256=${sum%% *}
}

# The app ID, version name and version code that the manifest of APK $1 gives, and that it is neither debuggable nor
# test-only.
check_manifest() {
  apk=$1 app_id=$2 version_name=$3 version_code=$4
  badging=$("$aapt2" dump badging "$apk") || fail "aapt2 cannot read $apk"
  package=$(printf '%s\n' "$badging" | sed -n '1{/^package: /p;}')
  [ -n "$package" ] || fail "aapt2 reads no package line from $apk"
  found_id=$(printf '%s\n' "$package" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")
  found_code=$(printf '%s\n' "$package" | sed -n "s/.* versionCode='\([^']*\)'.*/\1/p")
  found_name=$(printf '%s\n' "$package" | sed -n "s/.* versionName='\([^']*\)'.*/\1/p")
  [ "$found_id" = "$app_id" ] || fail "$apk has app ID '$found_id', expected '$app_id'"
  [ "$found_name" = "$version_name" ] || fail "$apk has version name '$found_name', expected '$version_name'"
  [ "$found_code" = "$version_code" ] || fail "$apk has version code '$found_code', expected '$version_code'"
  # android:debuggable is 0x0101000f, android:testOnly 0x01010272; either may be there only as false
  manifest=$("$aapt2" dump xmltree --file AndroidManifest.xml "$apk") || fail "aapt2 cannot read the manifest of $apk"
  flags=$(printf '%s\n' "$manifest" | grep -E ':(debuggable|testOnly)\(0x0101(000f|0272)\)=' | grep -v '=false$' \
    || true)
  [ -z "$flags" ] || fail "$apk is debuggable or test-only: $flags"
  echo "$apk: app ID $found_id, version $found_name ($found_code), not debuggable, not test-only"
}

# That APK $1 has no v1 (JAR) signature files, which apksigner does not look at for API 24 and later.
no_jar_signature() {
  entries=$(unzip -Z1 "$1") || fail "unzip cannot list the files of $1"
  if printf '%s\n' "$entries" | grep -E '^META-INF/([^/]+\.(SF|RSA|DSA|EC)|MANIFEST\.MF)$'; then
    fail "$1 carries the v1 (JAR) signature files above; the release APK is signed with v2 and v3 only"
  fi
}

# Sets sha256 to the SHA-256 digest of the certificate of key $2 in keystore $1, opened with the public password $3,
# in apksigner's form.
certificate_digest() {
  certificate=$(mktemp)
  if ! out=$(keytool -exportcert -keystore "$1" -alias "$2" -storepass "$3" -file "$certificate" 2>&1); then
    rm -f "$certificate"
    fail "keytool reads no certificate of $2 from $1: $out"
  fi
  sum=$(sha256sum < "$certificate") || { rm -f "$certificate"; fail "sha256sum cannot read the certificate"; }
  rm -f "$certificate"
  sha256=${sum%% *}
}

# Sets fingerprint to the one file $1 records, in apksigner's form. It sets a variable rather than printing, so that a
# fail in it ends the script, not a subshell.
read_fingerprint() {
  file=$1
  [ -f "$file" ] && [ -r "$file" ] || fail "$file is not a readable file"
  lines=$(grep -v -e '^[[:space:]]*#' -e '^[[:space:]]*$' "$file" || true)
  [ -n "$lines" ] || fail "$file records no certificate fingerprint yet: add the SHA-256 fingerprint of the release \
key's certificate (docs/dev/ci-and-releasing.md, Releasing the Android app)"
  [ "$(printf '%s\n' "$lines" | wc -l)" -eq 1 ] || fail "$file records more than one line; it holds one fingerprint"
  value=$(printf '%s' "$lines" | tr -d ': \t\r' | tr 'A-F' 'a-f')
  printf '%s\n' "$value" | grep -Eqx '[0-9a-f]{64}' \
    || fail "$file does not hold a SHA-256 fingerprint (64 hex digits, with or without colons)"
  fingerprint=$value
}

# Sets report to apksigner's verbose output for APK $1 and digest to the SHA-256 digest of the certificate of its one
# signer. A failure shows the last lines of apksigner's output.
signer_digest() {
  if ! report=$("$apksigner" verify --min-sdk-version 24 --verbose --print-certs "$1" 2>&1); then
    fail "$1 does not verify: $(printf '%s\n' "$report" | tail -n 5)"
  fi
  digest=$(printf '%s\n' "$report" | sed -n 's/^Signer #[0-9]* certificate SHA-256 digest: //p')
  [ "$(printf '%s\n' "$digest" | grep -c .)" -eq 1 ] || fail "$1 has not exactly one signer's certificate"
  printf '%s\n' "$digest" | grep -Eqx '[0-9a-f]{64}' \
    || fail "$1: apksigner shows no SHA-256 digest of 64 lowercase hex digits for the signer's certificate"
}

command=$1
shift
case "$command" in
  check-unsigned)
    [ $# -eq 5 ] || usage
    need_build_tools
    need_apk "$1"
    apk=$1 expected=$2
    sha256_of "$apk"
    actual=$sha256
    [ "$actual" = "$expected" ] || fail "$apk has SHA-256 $actual, not $expected as the build job made it"
    # Any failure of verify counts as unsigned; a broken apksigner is caught by sign and check-signed, which need it.
    if "$apksigner" verify --min-sdk-version "$min_sdk" "$apk" > /dev/null 2>&1; then
      fail "$apk is signed already; the build must leave the release APK unsigned"
    fi
    no_jar_signature "$apk"
    "$zipalign" -c -P 16 4 "$apk" || fail "$apk is not zip-aligned"
    check_manifest "$apk" "$3" "$4" "$5"
    echo "$apk: SHA-256 $actual, unsigned"
    ;;
  sign)
    [ $# -eq 3 ] || usage
    need_build_tools
    need_apk "$1"
    [ -n "${ANDROID_KEYSTORE_PASSWORD:-}" ] || fail "ANDROID_KEYSTORE_PASSWORD is not set"
    [ -s "$2" ] || fail "the keystore $2 is missing or empty"
    "$apksigner" sign --ks "$2" --ks-key-alias "$alias" \
      --ks-pass env:ANDROID_KEYSTORE_PASSWORD --key-pass env:ANDROID_KEYSTORE_PASSWORD \
      --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false \
      --min-sdk-version "$min_sdk" --out "$3" "$1"
    echo "signed $1 as $3"
    ;;
  check-signed)
    [ $# -eq 5 ] || [ $# -eq 6 ] || usage
    need_build_tools
    need_apk "$1"
    apk=$1
    # From API 28 on apksigner checks the v3 signature alone and reports v2 as false; from API 24, the first with
    # v2, it checks both.
    signer_digest "$apk"
    for line in "Verified using v1 scheme (JAR signing): false" \
        "Verified using v2 scheme (APK Signature Scheme v2): true" \
        "Verified using v3 scheme (APK Signature Scheme v3): true" \
        "Number of signers: 1"; do
      printf '%s\n' "$report" | grep -Fqx "$line" || fail "$apk: apksigner does not say \"$line\""
    done
    no_jar_signature "$apk"
    "$zipalign" -c -P 16 4 "$apk" || fail "$apk is not zip-aligned"
    # the debug keystore's password is public (docs/dev/android.md)
    certificate_digest "$5" androiddebugkey android
    debug=$sha256
    printf '%s\n' "$debug" | grep -Eqx '[0-9a-f]{64}' || fail "no certificate read from $5"
    [ "$digest" != "$debug" ] || fail "$apk is signed with the public debug key"
    if [ $# -eq 6 ]; then
      read_fingerprint "$6"
      [ "$digest" = "$fingerprint" ] || fail "$apk is signed with certificate $digest, not with the release \
certificate $fingerprint that $6 records"
      echo "$apk: the certificate is the one $6 records"
    fi
    check_manifest "$apk" "$2" "$3" "$4"
    echo "$apk: signed with v2 and v3 only, by one signer, certificate SHA-256 $digest"
    ;;
  certificate)
    [ $# -eq 1 ] || usage
    need_build_tools
    need_apk "$1"
    signer_digest "$1"
    printf '%s\n' "$digest"
    ;;
  fingerprint)
    [ $# -eq 1 ] || usage
    read_fingerprint "$1"
    printf '%s\n' "$fingerprint"
    ;;
  throwaway-key)
    [ $# -eq 1 ] || usage
    [ -n "${ANDROID_KEYSTORE_PASSWORD:-}" ] || fail "ANDROID_KEYSTORE_PASSWORD is not set"
    [ ! -e "$1" ] || fail "$1 exists already"
    umask 077
    out=$(keytool -genkeypair -noprompt -keystore "$1" -storetype PKCS12 -storepass:env ANDROID_KEYSTORE_PASSWORD \
      -alias "$alias" -keyalg RSA -keysize 3072 -validity 2 -dname "CN=Car charge planner dry run" 2>&1) \
      || fail "keytool makes no key in $1: $out"
    echo "made a throw-away key in $1"
    ;;
  *)
    usage
    ;;
esac
