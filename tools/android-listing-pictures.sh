#!/bin/sh
# Makes the phone screenshots of the Android app's listing for F-Droid (#486), five per language, from an emulator:
#
#   sh tools/android-listing-pictures.sh
#
# writes fastlane/metadata/android/{en-US,nl-NL}/images/phoneScreenshots/1.png … 5.png: today's dashboard with a
# charge window and the chart, Sunday with a trip and its advice, a grey Monday without a charge window, set-up's
# Welcome page, which says what leaves the phone, and Settings' Location page. Run it only when one of those screens
# visibly changes; it does not run in CI.
#
# The data is the made-up example home of docs/example-engine.json, on Friday 3 July 2026 at 08:20 in Brussels, as the
# desktop's pictures, with a battery level of 45 % entered that morning. The forecast is made up too and never a live
# one: tools/android-listing/MadeUpOpenMeteo.java answers as Open-Meteo on 127.0.0.1, which the emulator reaches
# through adb reverse, and the settings file the app gets names it as Open-Meteo's server address, without a fallback
# provider, so nothing is asked of a real service. The trip is put in trips.json before the second picture. The Welcome
# page is taken on the app without any of its files, as at its first start. In Dutch the place is "Zonnedorp, België",
# as the place search names it in that language. Set-up's Location page is not pictured: its place search asks
# Open-Meteo's real geocoding service, so a place can only be chosen there live; Settings' Location page, the same
# page, shows the example's place instead.
#
# The app is the debug build, as only a debuggable app takes files through run-as and plain http to 127.0.0.1, built
# with -PdebugAppName so it shows the name a release shows instead of "(debug)". The pictures are taken in the light
# theme at 1080x2400 and 420 dpi with gesture navigation, the system's font and display size, English (en-US) and then
# Dutch (nl-NL) as the phone's language, and SystemUI's demo mode for a clean status bar.
#
# Without SERIAL it makes an AVD of its own in a temporary folder (IMAGE, under the SDK, the Android 17 Google Play
# image by default), boots it without a window on EMULATOR_PORT (5584 by default) and removes both afterwards. With
# SERIAL=emulator-5554 it uses that booted emulator, which must be an emulator (the script sets its clock and stops
# apps), 1080x2400 at 420 dpi with gesture navigation, and must not have the debug app installed (its data would be
# lost); it puts the emulator's language, time zone,
# automatic time, night mode and demo mode back afterwards and uninstalls the app, but the clock stays where the
# pictures left it until automatic time sets it again. APK=… takes a debug APK built with -PdebugAppName instead of
# building one. Needs a JDK, the Android SDK (ANDROID_HOME, else ~/Android/Sdk) with platform-tools and the emulator,
# and runs Gradle once.
set -eu

fail() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

root=$(cd "$(dirname "$0")/.." && pwd)
sdk=${ANDROID_HOME:-$HOME/Android/Sdk}
image=${IMAGE:-system-images/android-37.1/google_apis_playstore_ps16k/x86_64}
emulator_port=${EMULATOR_PORT:-5584}
forecast_port=${FORECAST_PORT:-8765}
package=io.github.enadeag.car_charge_planner.debug
activity=nl.enadeag.carchargeplanner.android.MainActivity
res=$root/android/app/src/main/res
listing=$root/fastlane/metadata/android
day=2026-07-03
clock=08:20
languages="en-US nl-NL"

[ -x "$sdk/platform-tools/adb" ] || fail "no adb in $sdk/platform-tools: set ANDROID_HOME to the Android SDK"
command -v java > /dev/null || fail "no java on PATH"

work=$(mktemp -d)
serial=${SERIAL:-}
own_emulator=
emulator_pid=
forecast_pid=
installed=
saved=
saved_demo=
saved_locale=
saved_zone=
saved_auto_zone=
saved_auto_time=
saved_night=

# adb shell joins its arguments into one command for the device's shell; older systems end its lines with CR
on_device() { "$sdk/platform-tools/adb" -s "$serial" shell "$@" | tr -d '\r'; }
adb() { "$sdk/platform-tools/adb" -s "$serial" "$@"; }

demo() { on_device am broadcast -a com.android.systemui.demo -e command "$@" > /dev/null; }

clean_up() {
  [ -z "$forecast_pid" ] || kill "$forecast_pid" 2> /dev/null || true
  [ -z "$serial" ] || adb shell rm -f /sdcard/listing-window.xml > /dev/null 2>&1 || true
  if [ -n "$own_emulator" ]; then
    adb emu kill > /dev/null 2>&1 || kill "$emulator_pid" 2> /dev/null || true
    waited=0
    while kill -0 "$emulator_pid" 2> /dev/null && [ "$waited" -lt 30 ]; do
      sleep 1
      waited=$((waited + 1))
    done
    kill -9 "$emulator_pid" 2> /dev/null || true
  elif [ -n "$serial" ]; then
    adb reverse --remove "tcp:$forecast_port" > /dev/null 2>&1 || true
    [ -z "$installed" ] || adb uninstall "$package" > /dev/null 2>&1 || true
    if [ -n "$saved" ]; then
      demo exit || true
      on_device settings put global sysui_demo_allowed "$saved_demo" || true
      on_device cmd locale set-device-locale "$saved_locale" || true
      on_device cmd alarm set-timezone "$saved_zone" || true
      on_device cmd time_zone_detector set_auto_detection_enabled "$saved_auto_zone" || true
      on_device cmd time_detector set_auto_detection_enabled "$saved_auto_time" || true
      on_device cmd uimode night "$saved_night" > /dev/null || true
    fi
  fi
  rm -rf "${work:?}"
}
trap clean_up EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

text_of() { # the text named $1 in the values folder $2, as strings.xml has it
  found=$(sed -n "s|.*<string name=\"$1\"[^>]*>\(.*\)</string>.*|\1|p" "$res/$2/strings.xml")
  [ -n "$found" ] || fail "no text $1 in $2/strings.xml"
  printf '%s\n' "$found"
}
values_of() { # the values folder of a language, such as values-nl for nl-NL; English is the default folder
  case $1 in en-*) printf 'values\n' ;; *) printf 'values-%s\n' "${1%%-*}" ;; esac
}

# The debug build, with the name a release shows
app_name=$(text_of app_name values)
apk=${APK:-}
if [ -z "$apk" ]; then
  printf 'building the debug app as "%s"\n' "$app_name"
  (cd "$root/android" && ANDROID_HOME=$sdk ./gradlew --console=plain -q --max-workers=2 -PdebugAppName="$app_name" \
    assembleDebug)
  apk=$root/android/app/build/outputs/apk/debug/app-debug.apk
fi
[ -f "$apk" ] || fail "no APK at $apk"

# The emulator
if [ -z "$serial" ]; then
  [ -f "$sdk/$image/system.img" ] || fail "no system image in $sdk/$image: name one in IMAGE"
  serial=emulator-$emulator_port
  "$sdk/platform-tools/adb" devices | grep -q "^$serial[[:space:]]" && fail "$serial is in use: set EMULATOR_PORT"
  mkdir -p "$work/avd/listing.avd"
  printf 'avd.ini.encoding=UTF-8\npath=%s\n' "$work/avd/listing.avd" > "$work/avd/listing.ini"
  cat > "$work/avd/listing.avd/config.ini" << EOF
avd.ini.encoding=UTF-8
abi.type=x86_64
hw.cpu.arch=x86_64
hw.cpu.ncore=4
hw.ramSize=2048
hw.lcd.width=1080
hw.lcd.height=2400
hw.lcd.density=420
hw.mainKeys=no
hw.keyboard=no
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader_indirect
hw.initialOrientation=portrait
disk.dataPartition.size=4G
image.sysdir.1=$image/
PlayStore.enabled=true
showDeviceFrame=no
EOF
  printf 'booting an emulator of its own on %s\n' "$serial"
  ANDROID_AVD_HOME=$work/avd ANDROID_SDK_ROOT=$sdk "$sdk/emulator/emulator" -avd listing -port "$emulator_port" \
    -no-window -no-audio -no-boot-anim -no-snapshot -wipe-data -memory 2048 > "$work/emulator.log" 2>&1 &
  emulator_pid=$!
  own_emulator=yes
  waited=0
  until [ "$(on_device getprop sys.boot_completed 2> /dev/null)" = 1 ]; do
    kill -0 "$emulator_pid" 2> /dev/null || { cat "$work/emulator.log" >&2; fail "the emulator stopped"; }
    [ "$waited" -lt 300 ] || fail "the emulator did not boot in 5 minutes"
    sleep 5
    waited=$((waited + 5))
  done
  # the package manager answers a little after the boot is complete
  waited=0
  until on_device pm path android 2> /dev/null | grep -q '^package:'; do
    [ "$waited" -lt 60 ] || fail "the emulator's package manager did not answer in a minute"
    sleep 2
    waited=$((waited + 2))
  done
else
  case $serial in
    emulator-*) ;;
    *) fail "SERIAL=$serial is no emulator: the script sets its clock and stops apps" ;;
  esac
  adb get-state > /dev/null 2>&1 || fail "no device $serial"
  [ "$(on_device getprop ro.kernel.qemu)" = 1 ] || [ "$(on_device getprop ro.boot.qemu)" = 1 ] ||
    fail "$serial is no emulator: the script sets its clock and stops apps"
  if adb shell pm path "$package" > /dev/null 2>&1; then
    fail "$serial has the debug app installed: uninstall it first, as this would replace its data"
  fi
fi

[ "$(on_device wm size)" = "Physical size: 1080x2400" ] && [ "$(on_device wm density)" = "Physical density: 420" ] ||
  fail "$serial is not 1080x2400 at 420 dpi, or has a size or density set: $(on_device wm size; on_device wm density)"
[ "$(on_device settings get secure navigation_mode)" = 2 ] || fail "$serial does not use gesture navigation"
case $(on_device settings get system font_scale) in null | 1.0) ;; *) fail "$serial has a font size set" ;; esac

saved_demo=$(on_device settings get global sysui_demo_allowed | sed 's/null/0/')
saved_locale=$(on_device cmd locale get-device-locale)
saved_zone=$(on_device getprop persist.sys.timezone)
saved_auto_zone=$(on_device cmd time_zone_detector is_auto_detection_enabled)
saved_auto_time=$(on_device cmd time_detector is_auto_detection_enabled)
saved_night=$(on_device cmd uimode night | sed 's/^Night mode: //')
saved=yes

# The made-up forecast
java "$root/tools/android-listing/MadeUpOpenMeteo.java" "$forecast_port" "$day" > "$work/forecast.log" 2>&1 &
forecast_pid=$!
waited=0
until grep -q '^made-up Open-Meteo' "$work/forecast.log"; do
  kill -0 "$forecast_pid" 2> /dev/null || { cat "$work/forecast.log" >&2; fail "the made-up forecast did not start"; }
  [ "$waited" -lt 60 ] || fail "the made-up forecast did not start in a minute"
  sleep 1
  waited=$((waited + 1))
done
adb reverse "tcp:$forecast_port" "tcp:$forecast_port" > /dev/null
served() { grep -c '^served' "$work/forecast.log" || true; }

# The app and its files
adb install "$apk" > /dev/null
installed=yes
on_device pm grant "$package" android.permission.POST_NOTIFICATIONS
sed -e 's|"fallback": "forecast-solar",|"fallback": "",|' \
  -e "s|\"base_url\": \"\"|\"base_url\": \"http://127.0.0.1:$forecast_port\"|" \
  "$root/docs/example-engine.json" > "$work/engine.json"
grep -q '"fallback": "",' "$work/engine.json" && grep -q "127.0.0.1:$forecast_port" "$work/engine.json" &&
  grep -q '"placeName": "Zonnedorp, Belgium"' "$work/engine.json" ||
  fail "docs/example-engine.json changed: the place and the forecast's settings could not be set"
printf '{"version": 1, "levelPct": 45, "enteredAt": "%sT05:30:00Z"}\n' "$day" > "$work/battery.json"
# Sunday 08:30 to 19:00 in Brussels, two days on
printf '{"version": 1, "trips": [{"departure": "2026-07-05T06:30:00Z", "return": "2026-07-05T17:00:00Z", %s}]}\n' \
  '"distanceKm": 150, "canChargeAtDestination": false' > "$work/trips.json"
put_file() { # puts $work/$1 in the app's files folder as $2 (else as $1), private as the app writes it
  adb shell "run-as $package sh -c 'umask 077 && mkdir -p files && cat > files/${2:-$1}'" < "$work/$1"
}

on_device cmd uimode night no > /dev/null
on_device cmd time_detector set_auto_detection_enabled false
on_device cmd time_zone_detector set_auto_detection_enabled false
on_device cmd alarm set-timezone Europe/Brussels
on_device settings put global sysui_demo_allowed 1
# again after each change of language, as SystemUI then shows its icons anew. A new language can start a download with
# an ongoing notification, as Android System Intelligence's of its language packs, whose icon demo mode does not hide:
# the script stops such an app, which Android starts again when it needs it.
clean_status_bar() {
  sleep 5
  for other in $(on_device dumpsys notification |
    sed -n 's/^ *NotificationRecord([^:]*: pkg=\([^ ]*\) .*FOREGROUND_SERVICE.*/\1/p' | sort -u); do
    case $other in android | com.android.systemui | "$package") ;; *)
      printf 'stopping %s, whose ongoing notification would show in the status bar\n' "$other"
      on_device am force-stop "$other" ;;
    esac
  done
  demo enter
  demo clock -e hhmm "$(printf '%s' "$clock" | tr -d :)"
  demo battery -e level 100 -e plugged false -e powersave false
  demo network -e wifi show -e level 4 -e fully true
  demo network -e mobile hide
  demo notifications -e visible false
  demo status -e volume hide -e bluetooth hide -e location hide -e alarm hide -e sync hide -e mute hide
}

# The window's nodes, a line each: left top right bottom, then the text or the description
nodes() {
  on_device uiautomator dump /sdcard/listing-window.xml > /dev/null
  bounds='bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]"'
  on_device cat /sdcard/listing-window.xml | tr '<' '\n' | sed -n \
    -e "s/.* text=\"\\([^\"][^\"]*\\)\".* $bounds.*/\\2 \\3 \\4 \\5 \\1/p" \
    -e "s/.* content-desc=\"\\([^\"][^\"]*\\)\".* $bounds.*/\\2 \\3 \\4 \\5 \\1/p"
}
# waits until a node shows the text, and prints its line
find_node() {
  tries=0
  while :; do
    line=$(nodes | awk -v text="$1" '{ t = $0; sub(/^[0-9]+ [0-9]+ [0-9]+ [0-9]+ /, "", t) } t == text' | head -n 1)
    [ -z "$line" ] || { printf '%s\n' "$line"; return; }
    [ "$tries" -lt 20 ] || fail "nothing on the screen shows \"$1\""
    sleep 1
    tries=$((tries + 1))
  done
}
tap_line() { # taps the middle of a node's line
  [ -n "$1" ] || fail "no node to tap"
  printf '%s\n' "$1" | {
    read -r left top right bottom _
    on_device input tap $(((left + right) / 2)) $(((top + bottom) / 2))
  }
  sleep 2
}
tap() {
  node=$(find_node "$1")
  tap_line "$node"
}
# taps the day tab $2 of the row that starts with today, $1, and waits until the day's own heading shows below the
# tabs: the tab's description, such as "Sunday", is the heading's word
tap_day() {
  node=$(find_node "$1")
  top=$(printf '%s\n' "$node" | cut -d ' ' -f 2)
  tab=$(nodes | awk -v top="$top" '$2 == top' | sort -n -k 1 | sed -n "$2p")
  [ -n "$tab" ] || fail "no day tab $2 beside \"$1\""
  tap_line "$tab"
  name=$(printf '%s\n' "$tab" | cut -d ' ' -f 5-)
  bottom=$(printf '%s\n' "$tab" | cut -d ' ' -f 4)
  nodes | awk -v name="$name" -v below="$bottom" '{ t = $0; sub(/^[0-9]+ [0-9]+ [0-9]+ [0-9]+ /, "", t) }
    $2 > below && tolower(t) == tolower(name) { found = 1 } END { exit !found }' ||
    fail "the day tab \"$name\" did not show its day"
}

start_app() { # starts the app anew at the pictures' time, so it asks for the forecast, and waits for today's plan
  adb shell am force-stop "$package"
  adb shell "run-as $package rm -rf files/forecasts"
  before=$(served)
  on_device "cmd alarm set-time \$(date -d ${day}T$clock:00 +%s)000" > /dev/null
  on_device input keyevent KEYCODE_WAKEUP
  on_device wm dismiss-keyguard
  adb shell am start -W -n "$package/$activity" > /dev/null
  find_node "$1" > /dev/null
  [ "$(served)" -gt "$before" ] || fail "the app did not ask the made-up forecast"
  sleep 4
}

picture() { # $1 the folder, $2 the number
  nodes | grep -F '(debug)' && fail "picture $2 would show \"(debug)\""
  adb exec-out screencap -p > "$1/$2.png"
}

for language in $languages; do
  values=$(values_of "$language")
  today=$(text_of day_today "$values")
  welcome=$(text_of setup_welcome_title "$values")
  home_settings=$(text_of home_settings "$values")
  location=$(text_of settings_location "$values")
  fold=$(text_of coordinates_fold "$values")
  printf 'pictures in %s\n' "$language"
  on_device cmd locale set-device-locale "$language"
  clean_status_bar
  mkdir -p "$work/$language"

  # set-up's Welcome page, on the app without any of its files, as at its first start
  adb shell am force-stop "$package"
  adb shell "run-as $package rm -rf files no_backup"
  on_device "cmd alarm set-time \$(date -d ${day}T$clock:00 +%s)000" > /dev/null
  adb shell am start -W -n "$package/$activity" > /dev/null
  find_node "$welcome" > /dev/null
  sleep 3
  picture "$work/$language" 4

  # the place's name as the place search gives it in the phone's language
  case $language in
    nl-NL) sed 's|"placeName": "Zonnedorp, Belgium"|"placeName": "Zonnedorp, België"|' "$work/engine.json" ;;
    *) cat "$work/engine.json" ;;
  esac > "$work/engine-$language.json"
  adb shell "run-as $package rm -f files/trips.json"
  put_file "engine-$language.json" engine.json
  put_file battery.json
  start_app "$today"
  picture "$work/$language" 1
  tap_day "$today" 4
  picture "$work/$language" 3

  put_file trips.json
  start_app "$today"
  tap_day "$today" 3
  picture "$work/$language" 2

  tap "$home_settings"
  tap "$location"
  tap "$fold"
  picture "$work/$language" 5
done

# Each a PNG of 1080x2400; screencap writes no text or time chunks, so only an optimiser has anything to strip
for picture in "$work"/*/*.png; do
  size=$(od -An -tu1 -j 16 -N 8 "$picture" | awk '{ print $3 * 256 + $4 "x" $7 * 256 + $8 }')
  [ "$size" = 1080x2400 ] || fail "$picture is $size, not 1080x2400"
done
if command -v oxipng > /dev/null; then
  printf 'optimising with oxipng\n'
  oxipng -q -o 4 --strip all "$work"/*/*.png
elif command -v optipng > /dev/null; then
  printf 'optimising with optipng\n'
  optipng -quiet -o2 -strip all "$work"/*/*.png
else
  printf 'no oxipng or optipng: the pictures are as screencap wrote them\n'
fi
for language in $languages; do
  folder=$listing/$language/images/phoneScreenshots
  mkdir -p "$folder"
  rm -f "$folder"/*.png
  cp "$work/$language"/*.png "$folder/"
  printf 'wrote %s pictures to %s\n' "$(ls "$folder" | wc -l)" "${folder#"$root"/}"
done
