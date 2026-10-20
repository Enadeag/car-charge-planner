#!/bin/sh
# Installs the .deb in a fresh debian:stable container and the .rpm in a fresh fedora:latest one, runs the installed
# app's commands there (no desktop, no D-Bus), checks the menu entry, its icon and start at login, removes the package
# again and checks that nothing is left in /opt.
# Usage: sh tools/test-linux-packages.sh target/dist. Needs Docker; the containers get a read-only copy of the two
# packages and are removed when they end.
set -eu
[ $# -eq 1 ] || { echo "usage: sh tools/test-linux-packages.sh <folder with the .deb and the .rpm>" >&2; exit 2; }

copy=$(mktemp -d)
trap 'case "$copy" in /tmp/*) rm -rf -- "$copy";; esac' EXIT
cp "$1"/car-charge-planner_*_amd64.deb "$1"/car-charge-planner-*.x86_64.rpm "$copy/"
chmod -R a+rX "$copy"

# Run inside each container after the package is installed; $1 is the package manager's remove command.
checks='
set -eu
app=/opt/car-charge-planner/bin/car-charge-planner
# a home and a runtime folder of its own, as a user has
HOME=$(mktemp -d); XDG_RUNTIME_DIR=$(mktemp -d); export HOME XDG_RUNTIME_DIR
unset XDG_DATA_HOME XDG_CONFIG_HOME

status=0; "$app" bogus > /dev/null 2>&1 || status=$?
echo "bogus: exit code $status"; [ "$status" -eq 2 ]
out=$("$app" quit); echo "quit: $out"
[ "$out" = "Car charge planner is not running." ]

out=$("$app" install-launcher); echo "install-launcher: $out"
case "$out" in *"puts it in the application menu already"*) ;; *) exit 1 ;; esac
[ ! -e "$HOME/.local/share/applications" ]

entry=$(find /usr -name car-charge-planner-car-charge-planner.desktop)
echo "menu entry: $entry"; [ -n "$entry" ]
cat "$entry"
desktop-file-validate "$entry"
icon=$(sed -n "s/^Icon=//p" "$entry")
echo "icon: $icon, $(od -An -tu1 -j16 -N8 "$icon" | awk "{print \$3*256+\$4 \"x\" \$7*256+\$8}") pixels"
[ -f "$icon" ]
[ -f /opt/car-charge-planner/legal/LICENSE ]

out=$("$app" start-at-login on); echo "start-at-login on: $out"
autostart="$HOME/.config/autostart/car-charge-planner.desktop"
grep -qx "Exec=\"$app\" --background" "$autostart"
desktop-file-validate "$autostart"
"$app" start-at-login off > /dev/null; [ ! -e "$autostart" ]

$1 car-charge-planner
if [ -e /opt/car-charge-planner ] || [ -e "$entry" ]; then
  echo "left behind:"; ls -laR /opt/car-charge-planner "$entry" 2>&1 | head -20; exit 1
fi
echo "removed: nothing left in /opt/car-charge-planner, and no menu entry"
'

echo "== debian:stable, .deb"
docker run --rm -v "$copy:/packages:ro" debian:stable sh -euc '
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -qq
  apt-get install -y -qq desktop-file-utils > /dev/null
  apt-get install -y -qq /packages/car-charge-planner_*_amd64.deb > /dev/null
  dpkg -s car-charge-planner | grep -E "^(Package|Version|Maintainer|Depends|Homepage):"
  sh -c "$1" checks "apt-get remove -y -qq"
' sh "$checks"

echo "== fedora:latest, .rpm"
docker run --rm -v "$copy:/packages:ro" fedora:latest sh -euc '
  dnf install -y -q desktop-file-utils > /dev/null
  dnf install -y -q /packages/car-charge-planner-*.x86_64.rpm > /dev/null
  rpm -qi car-charge-planner | grep -E "^(Name|Version|Release|License|URL|Vendor)"
  sh -c "$1" checks "dnf remove -y -q"
' sh "$checks"
