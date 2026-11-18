#!/bin/sh
# Shows on an emulator that a backup whose transport sets no device-to-device flag saves nothing of the app on that
# emulator's Android version (#397), for the android-emulator job of android.yml, after the instrumented suites; it can
# be run by hand the same way:
#
#   sh tools/android-backup-check.sh DEBUG_APK
#
# On Android 10 and 11 every include of backup_rules.xml requires deviceToDeviceTransfer. The local transport sets no
# such flag there (it can from Android 12 on), as neither the cloud's transport nor adb's does, so its backup is one
# whose transport sets no flag. The script installs the debug build (run-as needs a debuggable app, to put files in
# its folders), puts made-up files in every place the rules name and in some they leave out, starts the app (a stopped
# package gives "Backup is not allowed"), backs it up with the local transport and fails unless the transport declined
# it as empty. It does not show that a transfer with the flag moves the files: no transport on Android 10 or 11 sets
# the flag on an emulator. Nor does it back up a second app with data as a positive control: the AOSP image has no
# app that reliably holds files and allows a full backup. What it has instead: "Transport rejected package" comes only
# from the local transport's check of the size the app's backup agent measured (LocalTransport.checkFullBackupSize
# declines zero bytes; an agent that did not run gives "Agent error"), so the agent ran and found nothing to save.
# That an include without the flag is noticed rests on DataExtractionRulesTest. The emulator's backup switch,
# transport and transport settings are put back as they were, also when the script is stopped, and the app is
# uninstalled. Needs adb on PATH, or ADB, such as "adb -s emulator-5554".
set -eu

saved="a backup without the device-to-device flag saved files of the app; before Android 12 every include of"
saved="$saved backup_rules.xml needs requireFlags=\"deviceToDeviceTransfer\", from Android 12 on the cloud-backup"
saved="$saved section of data_extraction_rules.xml excludes every domain"

fail() {
  echo "error: $*" >&2
  if [ "${GITHUB_ACTIONS:-}" = true ]; then echo "::error::$*"; fi
  exit 1
}

[ $# -eq 1 ] || { echo "usage: sh tools/android-backup-check.sh DEBUG_APK" >&2; exit 2; }
apk=$1
[ -f "$apk" ] || fail "no APK at $apk"
adb=${ADB:-adb}
$adb get-state > /dev/null 2>&1 || fail "no device for $adb: start the emulator, or name one in ADB=\"adb -s …\""
package=io.github.enadeag.car_charge_planner.debug
activity=nl.enadeag.carchargeplanner.android.MainActivity
rules="$(dirname "$0")/../android/app/src/main/res/xml/backup_rules.xml"

# adb shell joins its arguments into one command for the device's shell, so a command with quotes is one argument;
# older systems end its lines with CR
on_device() { $adb shell "$@" | tr -d '\r'; }

# the files the rules name, all in the domain "file"; and some they leave out
listed=$(sed -n 's/.*<include domain="file" path="\([^"]*\)".*/\1/p' "$rules")
[ -n "$listed" ] || fail "no include of the domain file in $rules"
seeded="$listed datastore/state.preferences_pb engine.json.tmp engine.json.damaged-20261118-120000"

app_files() {
  on_device "run-as $package sh -c 'find files databases shared_prefs no_backup -type f 2> /dev/null'" || true
}

was_enabled=$(on_device bmgr enabled)
was_transport=$(on_device bmgr list transports | sed -n 's/^ *\* *//p')
was_parameters=$(on_device settings get secure backup_local_transport_parameters)
put_back() {
  $adb uninstall "$package" > /dev/null 2>&1 || true
  if [ "$was_parameters" = null ]; then
    $adb shell settings delete secure backup_local_transport_parameters > /dev/null || true
  else
    $adb shell settings put secure backup_local_transport_parameters "\"$was_parameters\"" || true
  fi
  [ -z "$was_transport" ] || $adb shell bmgr transport "$was_transport" > /dev/null || true
  case "$was_enabled" in *disabled*) $adb shell bmgr enable false > /dev/null || true ;; esac
}
trap put_back EXIT
trap 'exit 130' INT TERM

$adb install -r "$apk" > /dev/null || fail "the debug APK could not be installed"
for file in $seeded no_backup/keychain.json; do
  case "$file" in no_backup/*) path=$file ;; *) path=files/$file ;; esac
  on_device "run-as $package sh -c 'mkdir -p $(dirname "$path") && echo made-up > $path'" > /dev/null
done
seeded_now=$(app_files)
echo "$seeded_now" | sed 's/^/  seeded: /'
[ "$(echo "$seeded_now" | wc -l)" -eq "$(echo $seeded no_backup/keychain.json | wc -w)" ] ||
  fail "not every made-up file could be written into the app's folders"

$adb shell bmgr enable true > /dev/null
# com.android.localtransport/.LocalTransport on Android 10, android/com.android.internal.backup.LocalTransport before;
# bmgr says "Selected" even for a name that is not there, so the name comes from the list
local_transport=$(on_device bmgr list transports | sed -n 's/^ *\*\{0,1\} *\([^ ]*LocalTransport\)$/\1/p' | head -n 1)
[ -n "$local_transport" ] || fail "the emulator has no local backup transport"
$adb shell bmgr transport "$local_transport" > /dev/null
$adb shell settings delete secure backup_local_transport_parameters > /dev/null
$adb shell bmgr wipe "$local_transport" "$package" > /dev/null 2>&1 || true

$adb shell am start -W -n "$package/$activity" > /dev/null || fail "the app could not be started"
sleep 3
$adb shell input keyevent KEYCODE_HOME
sleep 1

backup=$(on_device bmgr backupnow "$package")
echo "$backup" | sed 's/^/  /'
result=$(echo "$backup" | sed -n "s/^Package $package with result: //p")
# The local transport declines a full backup of zero bytes (LocalTransport.checkFullBackupSize, on Android 10 as
# later), which bmgr reports as a rejected package; Success means something of the app was saved.
case "$result" in
  "Transport rejected package"*) ;;
  Success) fail "$saved" ;;
  "Backup is not allowed"*)
    fail "Android did not back the app up at all (Backup is not allowed): the app may be stopped, its manifest may" \
      "turn backup off, or backup may be off on the emulator; so it shows nothing" ;;
  *) fail "the backup did not reach the app (result: ${result:-none}), so it shows nothing" ;;
esac

echo "a backup without the device-to-device flag saved nothing of the app"
