# Builds the Java app for Windows: a per-user installer target\dist\car-charge-planner-<version>.exe, or with
# -Type app-image only the program folder target\dist\car-charge-planner\. Needs a JDK 25 or newer with jlink and
# jpackage; the installer also needs WiX 5 (see CONTRIBUTING.md).
# Usage: powershell -ExecutionPolicy Bypass -File tools\package-windows.ps1 [-Type exe|app-image]
param([ValidateSet("exe", "app-image")][string]$Type = "exe")
$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

cmd /c "mvnw.cmd -B -q clean package"
if ($LASTEXITCODE) { exit 1 }
$version = (cmd /c "mvnw.cmd -B -q help:evaluate -Dexpression=project.version -DforceStdout") -replace "-SNAPSHOT", ""
if ($LASTEXITCODE) { exit 1 }

# JavaFX's Maven artifacts are an empty jar plus a jar per system with the classes and native libraries.
# The system's jars become modules of the runtime; everything else goes next to the app's jar.
New-Item -ItemType Directory target\javafx, target\input | Out-Null
Move-Item target\lib\javafx-*-win.jar target\javafx\
Remove-Item target\lib\javafx-*.jar
Copy-Item target\lib\*.jar, target\car-charge-planner.jar target\input\

# The modules the app needs (jdeps --print-module-deps), plus java.logging for its log and JavaFX.
jlink --module-path target\javafx `
  --add-modules java.base,java.desktop,java.logging,java.net.http,java.sql,jdk.jfr,jdk.security.auth,javafx.controls `
  --strip-debug --no-header-files --no-man-pages --compress zip-6 `
  --output target\runtime
if ($LASTEXITCODE) { exit 1 }

$installer = @()
if ($Type -eq "exe") {
  # The upgrade code stays the same for every version, so a new installer replaces the old version.
  # The folder is under %LOCALAPPDATA%\Programs: jpackage's default, %LOCALAPPDATA%\car-charge-planner, is where
  # the Python version keeps its data.
  $installer = @("--win-per-user-install", "--win-menu", "--win-shortcut",
                 "--win-upgrade-uuid", "93c62923-67a1-4866-af93-ac179a22a9f0",
                 "--install-dir", "Programs\car-charge-planner")
}
jpackage --type $Type --dest target\dist `
  --name car-charge-planner --app-version $version --vendor "Car charge planner" `
  --input target\input --main-jar car-charge-planner.jar `
  --main-class io.github.enadeag.carchargeplanner.Main `
  --runtime-image target\runtime `
  --icon src\packaging\windows\car-charge-planner.ico `
  --java-options "--enable-native-access=javafx.graphics,ALL-UNNAMED" @installer
if ($LASTEXITCODE) { exit 1 }
Get-ChildItem target\dist
