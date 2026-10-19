#!/bin/sh
# Builds the Java app as a Linux app image: target/dist/car-charge-planner/ (start bin/car-charge-planner)
# and target/dist/car-charge-planner-<version>-linux.tar.gz, with its SBOM car-charge-planner-<version>-linux.cdx.json.
# Needs a JDK 25 or newer with jlink and jpackage, curl, unzip, GNU tar and git.
set -eu
cd "$(dirname "$0")/.."

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

jpackage --type app-image --dest target/dist \
  --name car-charge-planner --app-version "$version" \
  --input target/input --main-jar car-charge-planner.jar \
  --main-class nl.enadeag.carchargeplanner.Main \
  --runtime-image target/runtime \
  --icon src/main/resources/nl/enadeag/carchargeplanner/desktop/system/icons/icon-256.png \
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
du -sh target/dist/car-charge-planner "target/dist/car-charge-planner-$version-linux.tar.gz"
