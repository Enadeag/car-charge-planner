#!/bin/sh
# Builds the Java app for Linux, all in target/dist:
# - the app image car-charge-planner/ (start bin/car-charge-planner);
# - car-charge-planner-<version>-linux.tar.gz of it, with its SBOM car-charge-planner-<version>-linux.cdx.json;
# - car-charge-planner_<version>_amd64.deb and car-charge-planner-<version>-1.x86_64.rpm of the same app image, which
#   install it in /opt/car-charge-planner with a menu entry (docs/adr/0007-linux-packages.md). They hold the same
#   libraries and runtime as the tar.gz, so its SBOM describes them too; only the licences are in lib/legal.
# Needs a JDK 25 or newer with jlink and jpackage, curl, unzip, GNU tar and git; for the .deb dpkg-deb and fakeroot,
# for the .rpm rpmbuild. "sh tools/package-linux.sh --tar-only" makes the app image and the tar.gz only.
set -eu
cd "$(dirname "$0")/.."

packages=yes
case "${1:-}" in
  "") ;;
  --tar-only) packages=no ;;
  *) echo "usage: sh tools/package-linux.sh [--tar-only]" >&2; exit 2 ;;
esac
# before the build, which takes minutes
if [ "$packages" = yes ]; then
  missing=""
  for tool in dpkg-deb fakeroot rpmbuild; do
    command -v "$tool" > /dev/null || missing="$missing $tool"
  done
  if [ -n "$missing" ]; then
    echo "The .deb and .rpm need:$missing. Install dpkg and fakeroot (Debian, Ubuntu: apt install fakeroot) and" \
      "rpmbuild (Ubuntu: apt install rpm; Fedora: dnf install rpm-build), or run with --tar-only." >&2
    exit 1
  fi
fi

# The jar, the SBOM and the tar.gz take the commit's time, so the same commit and JDK give the same tar.gz.
epoch=${SOURCE_DATE_EPOCH:-$(git log -1 --format=%ct)}
./mvnw -B -q clean package -Dproject.build.outputTimestamp="$epoch"
version=$(./mvnw -B -q help:evaluate -Dexpression=project.version -DforceStdout | sed 's/-SNAPSHOT//')

# JavaFX comes from the official jmods, not from its Maven jars: jlink then puts JavaFX's native libraries in the
# runtime, where JavaFX loads them, instead of JavaFX unpacking them from a jar into ~/.openjfx at every start.
# The version is javafx.version in pom.xml (PackagingTest checks it); a new version needs its checksum from the same
# folder of the download site.
jmods=openjfx-27_linux-x64_bin-jmods.zip
curl -fsSL --proto '=https' -o "target/$jmods" "https://download2.gluonhq.com/openjfx/27/$jmods"
echo "ea169b02460a38a2b44acf595e28c7abd3d1ab23075e4b03f324ebaf33920c5b  target/$jmods" | sha256sum -c --quiet -
unzip -q -j -d target/javafx "target/$jmods"

# The app's jar and its libraries; JavaFX is in the runtime.
mkdir -p target/input
rm target/lib/javafx-*.jar
cp target/lib/*.jar target/car-charge-planner.jar target/input/

jlink --module-path target/javafx @src/packaging/jlink.args --output target/runtime

icon=src/main/resources/nl/enadeag/carchargeplanner/desktop/system/icons/icon-256.png
jpackage --type app-image --dest target/dist \
  --name car-charge-planner --app-version "$version" \
  --input target/input --main-jar car-charge-planner.jar \
  --main-class nl.enadeag.carchargeplanner.Main \
  --runtime-image target/runtime \
  --icon "$icon" \
  --java-options "--enable-native-access=javafx.graphics"

# The licences: the app's own and one file per library; the runtime's are in lib/runtime/legal.
mkdir target/dist/car-charge-planner/legal
cp LICENSE NOTICE ADDITIONAL-TERMS.md src/packaging/legal/*.txt target/dist/car-charge-planner/legal/

java -cp 'target/lib/*' tools/SbomRuntime.java target/car-charge-planner.cdx.json target/runtime \
  "target/dist/car-charge-planner-$version-linux.cdx.json"

# Owned by root and not writable by group or others, whoever unpacks it; without the build's own times and order.
tar -C target/dist --sort=name --mtime="@$epoch" --owner=0 --group=0 --numeric-owner --mode=go-w \
  -cf "target/dist/car-charge-planner-$version-linux.tar" car-charge-planner
gzip -9n "target/dist/car-charge-planner-$version-linux.tar"

if [ "$packages" = yes ]; then
  # From src/packaging/linux: jpackage's menu entry, and the .deb's control file with the libraries the app links
  # (readelf -d on the app image), by their Debian and Ubuntu names. jpackage would take those names from the build
  # machine's own packages, so a .deb built on Ubuntu would ask for packages Debian does not have. The .rpm names
  # them by their file names (sonames), which Fedora and openSUSE both resolve. Without car-charge-planner.png in
  # the resource folder, jpackage would put its own default icon in place of the app's in lib/.
  mkdir target/linux-resources
  cp src/packaging/linux/car-charge-planner.desktop src/packaging/linux/control target/linux-resources/
  cp "$icon" target/linux-resources/car-charge-planner.png
  # jpackage puts only bin/ and lib/ of an app image in a package, so there the licences go in lib/legal.
  mkdir target/package-image
  cp -a target/dist/car-charge-planner target/package-image/
  mv target/package-image/car-charge-planner/legal target/package-image/car-charge-planner/lib/legal
  set -- --dest target/dist --app-image target/package-image/car-charge-planner \
    --name car-charge-planner --app-version "$version" --vendor "Car charge planner" \
    --description "Tells you when to charge your electric car on your own solar power" \
    --about-url https://github.com/Enadeag/car-charge-planner \
    --license-file LICENSE \
    --resource-dir target/linux-resources \
    --linux-package-name car-charge-planner \
    --linux-app-category utils \
    --linux-menu-group Utility \
    --linux-shortcut
  jpackage --type deb "$@" --linux-deb-maintainer 670644+Enadeag@users.noreply.github.com
  rpm_deps="libasound.so.2()(64bit), libfontconfig.so.1()(64bit), libfreetype.so.6()(64bit), libGL.so.1()(64bit)"
  rpm_deps="$rpm_deps, libgtk-3.so.0()(64bit), libX11.so.6()(64bit), libXext.so.6()(64bit), libXi.so.6()(64bit)"
  rpm_deps="$rpm_deps, libXrender.so.1()(64bit), libXtst.so.6()(64bit), libXxf86vm.so.1()(64bit)"
  jpackage --type rpm "$@" --linux-rpm-license-type GPL-3.0-or-later --linux-package-deps "$rpm_deps"
fi
du -sh target/dist/car-charge-planner target/dist/car-charge-planner*.tar.gz \
  $(ls target/dist/*.deb target/dist/*.rpm 2>/dev/null)
