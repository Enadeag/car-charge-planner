#!/bin/sh
# Runs F-Droid's build recipe of the Android app, tools/fdroid/io.github.enadeag.car_charge_planner.yml, the way
# F-Droid does, and fails unless F-Droid's build gives the released APK: run it from the repository's root as
#
#   sh tools/fdroid-check.sh SCRATCH
#
# SCRATCH is a new or empty folder; what the check fetches and builds lands there, apart from the image, which Docker
# keeps, and the SDK parts the build installs in the container, which go with it.
#
# The image is F-Droid's buildserver image, the one fdroiddata's CI builds with, pulled by its digest: FDROID_IMAGE,
# by default the digest below. In it the script fetches fdroidserver at FDROIDSERVER_COMMIT and the config of
# fdroiddata at FDROIDDATA_COMMIT, by default the commits below, and records what ran in SCRATCH/check/versions.txt.
# A newer image or commit is taken on purpose: run the check with the variables set, then change the defaults.
#
# The container runs with 6 GB of memory and 3 CPUs and only SCRATCH mounted, and Gradle with at most two workers. It
# puts the recipe in metadata/ and runs fdroid lint, fdroid rewritemeta (which must change nothing), fdroid scanner
# (--exit-code, so a finding fails) and fdroid build on the server's own JDK, with the flags of fdroiddata's CI
# (--verbose --test --refresh-scanner --on-server --no-tarball) and -l, which builds only the latest version. fdroid
# build downloads the APK the recipe's Binaries names, copies its signature onto the APK it built and verifies it. The
# check passes only when the build log says that the built APK matched the reference and that the reference's signer is
# the certificate in android/release-certificate.sha256. The logs are in SCRATCH/check/, the APKs in
# SCRATCH/fdroiddata/tmp/. The verdict is read from fdroid's own log, into which the build's output also goes, so the
# check shows that an honest build gives the released APK and is no defence against a build that fakes that line;
# F-Droid's own build is the gate.
#
# The container keeps Docker's default capabilities and may gain privileges: fdroid build --on-server runs sudo as the
# image's user, which no-new-privileges would stop. Root in the container fetches fdroidserver and keeps it, the
# recipe's copy and the logs its own; the image's user vagrant, who runs fdroid and so the build, gets only fdroiddata/
# and gradle-home/, and root's shell writes the logs. Root also makes the tree readable for others, whatever the host's
# umask, so that vagrant can read it; it stays readable after the run. The split between root and vagrant holds because
# vagrant may use sudo until fdroid build --on-server locks root and removes sudo, which it does before it prepares the
# source (build.py), and fdroid lint, rewritemeta and scanner run no code of the source; check that again whenever
# FDROIDSERVER_COMMIT moves. The image must have the user vagrant; the build never runs as root. At the end root gives
# SCRATCH back to the user who ran the script; a run stopped before that leaves files owned by other users in SCRATCH,
# which this gives back:
#
#   docker run --rm --mount type=bind,src=SCRATCH,dst=/work IMAGE chown -R "$(id -u):$(id -g)" /work
#
# It is written for rootful Docker: under rootless Docker or Podman that chown maps to a subordinate uid, and the
# files stay another user's. --inside is the part that runs in the container, not for use by hand. Needs docker and
# permission to use it.
set -eu

app=io.github.enadeag.car_charge_planner
fdroidserver_commit=${FDROIDSERVER_COMMIT:-fc2c86473e66a44fc641f403c338a8b2cccfad88}
fdroiddata_commit=${FDROIDDATA_COMMIT:-a7530ea2a9ef3628d33143ddb3485430bf83ee9b}
digest=sha256:1ea8bef1820bff06c2974e31b4fc4c79c364cd24231b86e71fa4b926797e0dac
image=${FDROID_IMAGE:-registry.gitlab.com/fdroid/fdroidserver@$digest}

fail() {
  echo "error: $*" >&2
  exit 1
}

# one line, all of it matching the extended regular expression
whole() {
  case "$1" in *'
'*) return 1 ;; esac
  printf '%s\n' "$1" | grep -Eqx "$2"
}
for commit in "$fdroidserver_commit" "$fdroiddata_commit"; do
  whole "$commit" '[0-9a-f]{40}' \
    || fail "FDROIDSERVER_COMMIT and FDROIDDATA_COMMIT must be a commit's 40 lowercase hex digits, not '$commit'"
done
whole "$image" '[a-z0-9][^@[:space:]]*@sha256:[0-9a-f]{64}' \
  || fail "FDROID_IMAGE must name the image by its digest, as NAME@sha256:<64 lowercase hex digits>, NAME starting" \
    "with a lowercase letter or digit, not '$image'"

# Inside the container, as root: everything F-Droid's side does. The image's user vagrant runs fdroid, as on F-Droid's
# buildserver.
inside() {
  work=/work
  . /etc/profile.d/bsenv.sh
  cd "$work"
  git init --quiet fdroidserver
  git -C fdroidserver fetch --quiet --depth 1 -- https://gitlab.com/fdroid/fdroidserver.git "$fdroidserver_commit"
  git -C fdroidserver checkout --quiet FETCH_HEAD
  git init --quiet fdroiddata
  git -C fdroiddata sparse-checkout set config
  git -C fdroiddata fetch --quiet --depth 1 --filter=blob:none -- https://gitlab.com/fdroid/fdroiddata.git \
    "$fdroiddata_commit"
  git -C fdroiddata checkout --quiet FETCH_HEAD
  chmod 0600 fdroiddata/config.yml
  mkdir fdroiddata/metadata gradle-home
  cp input/recipe.yml "fdroiddata/metadata/$app.yml"
  cp /home/vagrant/.gradle/gradle.properties gradle-home/gradle.properties
  printf '\norg.gradle.workers.max=2\n' >> gradle-home/gradle.properties

  {
    echo "image's buildserver: $(cat /home/vagrant/buildserverid 2> /dev/null || echo unknown)"
    echo "fdroidserver: $(git -C fdroidserver rev-parse HEAD)"
    echo "fdroiddata: $(git -C fdroiddata rev-parse HEAD)"
    java -version 2>&1
  } | tee check/versions.txt

  if [ "$(id -u)" != 0 ] || ! runuser -u vagrant -- true 2> /dev/null; then
    echo "error: the image has no user vagrant that root can become; the build never runs as root" >&2
    exit 1
  fi
  # the mount belongs to the user who ran the script, who may have vagrant's uid, and keeps that user's umask
  chown -R root:root "$work"
  chmod -R go+rX,go-w "$work"
  chown -R vagrant:vagrant fdroiddata gradle-home
  cd fdroiddata
  step() {
    name=$1
    shift
    echo "== fdroid $*"
    set +e
    runuser -u vagrant -- env HOME=/home/vagrant PATH="$work/fdroidserver:$PATH" \
      PYTHONPATH="$work/fdroidserver:$work/fdroidserver/examples" PYTHONUNBUFFERED=true \
      GRADLE_USER_HOME="$work/gradle-home" serverwebroot=/tmp fdroid "$@" > "$work/check/$name.log" 2>&1
    rc=$?
    set -e
    tail -n 15 "$work/check/$name.log"
    echo "$rc" > "$work/check/$name.rc"
  }
  step lint lint "$app"
  step rewritemeta rewritemeta "$app"
  # rewritemeta succeeds when it rewrites the file; a recipe it changes fails the check all the same
  if ! cmp -s "$work/input/recipe.yml" "metadata/$app.yml"; then
    diff -u "$work/input/recipe.yml" "metadata/$app.yml" > "$work/check/rewritemeta.diff" || true
    echo 1 > "$work/check/rewritemeta.rc"
  fi
  step scanner scanner --verbose --exit-code "$app"
  step build build --verbose --test --refresh-scanner --on-server --no-tarball -l "$app"
}

if [ "${1:-}" = --inside ]; then
  inside
  exit 0
fi

[ $# -eq 1 ] || { echo "usage: sh tools/fdroid-check.sh SCRATCH" >&2; exit 2; }
recipe="tools/fdroid/$app.yml"
certificate=android/release-certificate.sha256
[ -f "$recipe" ] && [ -f "$certificate" ] || fail "run this from the repository's root"
command -v docker > /dev/null || fail "docker is not on PATH"
case "$1" in /*) target=$1 ;; *) target=$PWD/$1 ;; esac
case "$target" in *,*) fail "$target has a comma, which docker run --mount cannot take" ;; esac
if [ -e "$target" ]; then
  [ -d "$target" ] && [ -z "$(ls -A "$target")" ] || fail "$target is not an empty folder; give a new or empty one"
fi
fingerprint=$(sh tools/android-sign.sh fingerprint "$certificate") || fail "no fingerprint in $certificate"
docker pull --quiet -- "$image" > /dev/null || fail "docker could not pull $image"
pulled=$(docker image inspect --format '{{index .RepoDigests 0}}' "$image") && [ -n "$pulled" ] \
  || fail "docker image inspect gave no digest for $image"
echo "$pulled"

mkdir -p "$target"
scratch=$(cd "$target" && pwd)
mkdir "$scratch/input" "$scratch/check"
printf '%s\n' "$pulled" > "$scratch/check/image.txt"
cp "$recipe" "$scratch/input/recipe.yml"
cp "$0" "$scratch/input/fdroid-check.sh"

set +e
docker run --rm --memory 6g --memory-swap 6g --cpus 3 --mount "type=bind,src=$scratch,dst=/work" \
  --env "FDROIDSERVER_COMMIT=$fdroidserver_commit" --env "FDROIDDATA_COMMIT=$fdroiddata_commit" "$image" \
  sh -c "sh /work/input/fdroid-check.sh --inside; rc=\$?; chown -R $(id -u):$(id -g) /work; exit \$rc"
container=$?
set -e
[ "$container" -eq 0 ] || fail "the container failed (exit $container); the logs are in $scratch/check/"

for name in lint rewritemeta scanner build; do
  rc=$(cat "$scratch/check/$name.rc" 2> /dev/null || echo missing)
  [ "$rc" = 0 ] || fail "fdroid $name failed ($rc); see $scratch/check/$name.log"
done
# fdroid's log lines: date, time, level, message
log=$scratch/check/build.log
line='^[0-9-]* [0-9:,]* INFO: '
grep -q "${line}compared built binary to supplied reference binary successfully\$" "$log" \
  || fail "the build log does not say that the built APK matched the reference APK; see $log"
grep -q "${line}supplied reference binary has allowed signer $fingerprint\$" "$log" \
  || fail "the build log does not say that the reference APK has the signer $fingerprint; see $log"
echo "F-Droid's build gave the released APK, signed by $fingerprint"
