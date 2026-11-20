#!/bin/sh
# Runs F-Droid's build recipe of the Android app, tools/fdroid/io.github.enadeag.car_charge_planner.yml, the way
# F-Droid does, and fails unless F-Droid's build gives the released APK: run it from the repository's root as
#
#   sh tools/fdroid-check.sh SCRATCH
#
# SCRATCH is a new or empty folder; what the check fetches and builds lands there, apart from the image, which Docker
# keeps, and the SDK parts the build installs in the container, which go with it. The image is IMAGE, by default
# registry.gitlab.com/fdroid/fdroidserver:buildserver-trixie, the one fdroiddata's CI builds with; it runs limited to
# 6 GB of memory and 3 CPUs, with only SCRATCH mounted, and in it the script fetches fdroidserver and the config of fdroiddata, puts the recipe in metadata/ and
# runs fdroid lint, fdroid rewritemeta (which must change nothing), fdroid scanner and fdroid build on the server's
# own JDK, as fdroiddata's CI runs it (--on-server --test --no-tarball). fdroid build downloads the APK the recipe's
# Binaries names, copies its signature onto the APK it built and verifies it, so the check passes only when the build
# log says that the built APK matched the reference and that the reference's signer is the certificate in
# android/release-certificate.sha256. The logs are in SCRATCH/check/, F-Droid's build in SCRATCH/fdroiddata/tmp/.
# Gradle runs with at most two workers. Needs docker and permission to use it.
set -eu

app=io.github.enadeag.car_charge_planner

fail() {
  echo "error: $*" >&2
  exit 1
}

# Inside the container, as root: everything F-Droid's side does. The image's user vagrant runs fdroid, as on F-Droid's
# buildserver; where root cannot become vagrant, fdroid runs as the user the script runs as.
inside() {
  work=/work
  . /etc/profile.d/bsenv.sh
  cd "$work"
  git clone --quiet --depth 1 https://gitlab.com/fdroid/fdroidserver.git fdroidserver
  git clone --quiet --depth 1 --filter=blob:none --sparse https://gitlab.com/fdroid/fdroiddata.git fdroiddata
  git -C fdroiddata sparse-checkout set config
  chmod 0600 fdroiddata/config.yml
  mkdir fdroiddata/metadata gradle-home
  cp input/recipe.yml "fdroiddata/metadata/$app.yml"
  cp /home/vagrant/.gradle/gradle.properties gradle-home/gradle.properties
  echo "org.gradle.workers.max=2" >> gradle-home/gradle.properties

  {
    echo "image's buildserver: $(cat /home/vagrant/buildserverid 2> /dev/null || echo unknown)"
    echo "fdroidserver: $(git -C fdroidserver rev-parse HEAD)"
    echo "fdroiddata: $(git -C fdroiddata rev-parse HEAD)"
    java -version 2>&1
  } | tee check/versions.txt

  user=
  if [ "$(id -u)" = 0 ] && runuser -u vagrant -- true 2> /dev/null; then
    chown -R vagrant:vagrant "$work"
    user=vagrant
  fi
  cd fdroiddata
  step() {
    name=$1
    shift
    echo "== fdroid $*"
    set +e
    if [ -n "$user" ]; then
      runuser -u "$user" -- env HOME=/home/vagrant PATH="$work/fdroidserver:$PATH" \
        PYTHONPATH="$work/fdroidserver:$work/fdroidserver/examples" PYTHONUNBUFFERED=true \
        GRADLE_USER_HOME="$work/gradle-home" serverwebroot=/tmp fdroid "$@" > "$work/check/$name.log" 2>&1
    else
      env HOME=/home/vagrant PATH="$work/fdroidserver:$PATH" \
        PYTHONPATH="$work/fdroidserver:$work/fdroidserver/examples" PYTHONUNBUFFERED=true \
        GRADLE_USER_HOME="$work/gradle-home" serverwebroot=/tmp fdroid "$@" > "$work/check/$name.log" 2>&1
    fi
    rc=$?
    set -e
    tail -n 15 "$work/check/$name.log"
    echo "$rc" > "$work/check/$name.rc"
  }
  step lint lint "$app"
  step rewritemeta rewritemeta "$app"
  if ! cmp -s "$work/input/recipe.yml" "metadata/$app.yml"; then
    diff -u "$work/input/recipe.yml" "metadata/$app.yml" > "$work/check/rewritemeta.diff" || true
    echo 1 > "$work/check/rewritemeta.rc"
  fi
  step scanner scanner -v "$app"
  step build build -v -l --on-server --test --no-tarball "$app"
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
image=${IMAGE:-registry.gitlab.com/fdroid/fdroidserver:buildserver-trixie}

mkdir -p "$1"
scratch=$(cd "$1" && pwd)
[ -z "$(ls -A "$scratch")" ] || fail "$scratch is not empty; give a new or empty folder"
fingerprint=$(sh tools/android-sign.sh fingerprint "$certificate")

mkdir "$scratch/input" "$scratch/check"
cp "$recipe" "$scratch/input/recipe.yml"
cp "$0" "$scratch/input/fdroid-check.sh"

docker pull --quiet "$image"
docker image inspect --format '{{index .RepoDigests 0}}' "$image" | tee "$scratch/check/image.txt"
set +e
docker run --rm --memory 6g --memory-swap 6g --cpus 3 --volume "$scratch:/work" "$image" \
  sh -c "sh /work/input/fdroid-check.sh --inside; rc=\$?; chown -R $(id -u):$(id -g) /work; exit \$rc"
container=$?
set -e
[ "$container" -eq 0 ] || fail "the container failed (exit $container); the logs are in $scratch/check/"

for name in lint rewritemeta scanner build; do
  rc=$(cat "$scratch/check/$name.rc" 2> /dev/null || echo missing)
  [ "$rc" = 0 ] || fail "fdroid $name failed ($rc); see $scratch/check/$name.log"
done
log=$scratch/check/build.log
grep -q "compared built binary to supplied reference binary successfully" "$log" \
  || fail "the build log does not say that the built APK matched the reference APK; see $log"
grep -q "supplied reference binary has allowed signer $fingerprint\$" "$log" \
  || fail "the build log does not say that the reference APK has the signer $fingerprint; see $log"
echo "F-Droid's build gave the released APK, signed by $fingerprint"
