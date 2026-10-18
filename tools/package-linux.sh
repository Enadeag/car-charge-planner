#!/bin/sh
# Builds the Java app as a Linux app image: target/dist/car-charge-planner/ (start bin/car-charge-planner)
# and target/dist/car-charge-planner-<version>-linux.tar.gz. Needs a JDK 25 or newer with jlink and jpackage, curl
# and unzip.
set -eu
cd "$(dirname "$0")/.."

./mvnw -B -q clean package
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
  --main-class io.github.enadeag.carchargeplanner.Main \
  --runtime-image target/runtime \
  --icon src/main/resources/io/github/enadeag/carchargeplanner/desktop/system/icons/icon-256.png \
  --java-options "--enable-native-access=javafx.graphics"

# The licences: the app's own and one file per library; the runtime's are in lib/runtime/legal.
mkdir target/dist/car-charge-planner/legal
cp LICENSE NOTICE ADDITIONAL-TERMS.md src/packaging/legal/*.txt target/dist/car-charge-planner/legal/

tar -C target/dist -czf "target/dist/car-charge-planner-$version-linux.tar.gz" car-charge-planner
du -sh target/dist/car-charge-planner "target/dist/car-charge-planner-$version-linux.tar.gz"
