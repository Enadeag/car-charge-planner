#!/bin/sh
# Builds the Java app as a Linux app image: target/dist/car-charge-planner/ (start bin/car-charge-planner)
# and target/dist/car-charge-planner-<version>-linux.tar.gz. Needs a JDK 25 or newer with jlink and jpackage.
set -eu
cd "$(dirname "$0")/.."

./mvnw -B -q clean package
version=$(./mvnw -B -q help:evaluate -Dexpression=project.version -DforceStdout | sed 's/-SNAPSHOT//')

# JavaFX's Maven artifacts are an empty jar plus a jar per system with the classes and native libraries.
# The system's jars become modules of the runtime; everything else goes next to the app's jar.
mkdir -p target/javafx target/input
mv target/lib/javafx-*-linux.jar target/javafx/
rm target/lib/javafx-*.jar
cp target/lib/*.jar target/car-charge-planner.jar target/input/

# The modules the app needs (jdeps --print-module-deps), plus java.logging for its log and JavaFX.
jlink --module-path target/javafx \
  --add-modules java.base,java.desktop,java.logging,java.net.http,java.sql,jdk.jfr,jdk.security.auth,javafx.controls \
  --strip-debug --no-header-files --no-man-pages --compress zip-6 \
  --output target/runtime

jpackage --type app-image --dest target/dist \
  --name car-charge-planner --app-version "$version" \
  --input target/input --main-jar car-charge-planner.jar \
  --main-class io.github.enadeag.carchargeplanner.Main \
  --runtime-image target/runtime \
  --icon src/main/resources/io/github/enadeag/carchargeplanner/desktop/icons/icon-256.png \
  --java-options "--enable-native-access=javafx.graphics"

tar -C target/dist -czf "target/dist/car-charge-planner-$version-linux.tar.gz" car-charge-planner
du -sh target/dist/car-charge-planner "target/dist/car-charge-planner-$version-linux.tar.gz"
