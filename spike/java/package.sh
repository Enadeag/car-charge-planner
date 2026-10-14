#!/bin/sh
# Question 7, Linux: jlink a runtime with JavaFX, then jpackage an app-image (and optionally --type deb/rpm).
# Usage: ./package.sh            -> target/dist/car-charge-planner-spike/
set -e
cd "$(dirname "$0")"
./mvnw -q -B package -DskipTests
rm -rf target/pkg target/runtime target/dist
mkdir -p target/pkg
cp target/java-spike-0.1.jar target/pkg/
./mvnw -q -B dependency:copy-dependencies -DoutputDirectory=target/deps
mkdir -p target/fx-tmp
mv target/deps/javafx-*-linux.jar target/fx-tmp/   # the platform jars; the plain ones are empty shells
rm target/deps/javafx-*.jar
cp target/deps/*.jar target/pkg/
rm -rf target/deps

# JavaFX's Maven jars are modular jars with the native libraries inside; jlink takes them as modules.
jlink --module-path target/fx-tmp \
  --add-modules java.base,java.desktop,java.logging,jdk.net,jdk.security.auth,jdk.unsupported,javafx.controls \
  --strip-debug --no-header-files --no-man-pages --compress zip-6 \
  --output target/runtime
rm -rf target/fx-tmp

jpackage --type app-image --dest target/dist \
  --name car-charge-planner-spike --app-version 0.1 \
  --input target/pkg --main-jar java-spike-0.1.jar --main-class spike.App \
  --runtime-image target/runtime \
  --icon src/main/resources/spike/icon-256.png \
  --java-options "--enable-native-access=ALL-UNNAMED"

du -sh target/runtime target/dist/car-charge-planner-spike
tar -C target/dist -czf target/dist/car-charge-planner-spike-linux.tar.gz car-charge-planner-spike
ls -l target/dist/car-charge-planner-spike-linux.tar.gz
