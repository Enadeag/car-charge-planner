#!/bin/sh
# Checks SBOMs (the .cdx.json files of the downloads) against the OSV vulnerability database: exits with 1 when any
# library in them has a known vulnerability, which stops a release. A finding that was looked at and does not reach
# the app can be listed, with the reason and a date it expires, in osv-scanner.toml at the repository root
# (https://google.github.io/osv-scanner/configuration/).
# Usage: sh tools/scan-sbom.sh target/dist/*.cdx.json. Needs Linux on x86-64, curl and access to GitHub and osv.dev.
set -eu
[ $# -gt 0 ] || { echo "usage: sh tools/scan-sbom.sh <file.cdx.json>..." >&2; exit 2; }
root=$(cd "$(dirname "$0")/.." && pwd)

# A new version needs its checksum from the osv-scanner_SHA256SUMS of the same release.
version=v2.6.0
sha256=ca69b3d3cd08f889a49dc0a383122f71cc528b83803671df5fd874d97485b108
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
scanner="$work/osv-scanner"
curl -fsSL --proto '=https' -o "$scanner" \
  "https://github.com/google/osv-scanner/releases/download/$version/osv-scanner_linux_amd64"
echo "$sha256  $scanner" | sha256sum -c --quiet -
chmod +x "$scanner"

for sbom in "$@"; do shift; set -- "$@" -L "$sbom"; done
if [ -f "$root/osv-scanner.toml" ]; then set -- --config="$root/osv-scanner.toml" "$@"; fi
"$scanner" scan source "$@"
