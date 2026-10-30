# Builds the Java app for Windows: a per-user installer target\dist\car-charge-planner-<version>.exe, or with
# -Type app-image only the program folder target\dist\car-charge-planner\, and its SBOM
# target\dist\car-charge-planner-<version>-windows.cdx.json. Needs a JDK 25 or newer with jlink and jpackage, git, and
# curl.exe (part of Windows 10 and later); the installer also needs WiX 5 (tools\install-wix.ps1).
# Usage: powershell -ExecutionPolicy Bypass -File tools\package-windows.ps1 [-Type exe|app-image]
param([ValidateSet("exe", "app-image")][string]$Type = "exe")
$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

# The jar and the SBOM take the commit's time, as on Linux.
$epoch = $env:SOURCE_DATE_EPOCH
if (-not $epoch) {
  $epoch = git log -1 --format=%ct
  if ($LASTEXITCODE) { exit 1 }
}
cmd /c "mvnw.cmd -B -q clean package -Dproject.build.outputTimestamp=$epoch"
if ($LASTEXITCODE) { exit 1 }
$version = (cmd /c "mvnw.cmd -B -q -N help:evaluate -Dexpression=project.version -DforceStdout") -replace "-SNAPSHOT", ""
if ($LASTEXITCODE) { exit 1 }
# the packaging's own work, beside the modules' builds (the desktop module's is in desktop\target); clean empties it
New-Item -ItemType Directory -Force target | Out-Null

# JavaFX comes from the official jmods, not from its Maven jars: jlink then puts JavaFX's native libraries in the
# runtime, where JavaFX loads them, instead of JavaFX unpacking them from a jar into the user's .openjfx folder at every
# start. The version is javafx.version in the parent pom.xml (PackagingTest checks it); a new version needs its checksum from the
# same folder of the download site.
$jmods = "openjfx-27_windows-x64_bin-jmods.zip"
$sha256 = "0ad5830f31608363e1e239b84bb1ce875f5edf834f0ceda67feead9cfbaf2b41"
curl.exe -fsSL --proto "=https" -o "target\$jmods" "https://download2.gluonhq.com/openjfx/27/$jmods"
if ($LASTEXITCODE) { exit 1 }
if ((Get-FileHash "target\$jmods" -Algorithm SHA256).Hash -ne $sha256) {
  throw "target\$jmods does not have the expected SHA-256"
}
Expand-Archive "target\$jmods" target\javafx
Move-Item target\javafx\*\*.jmod target\javafx

# The app's jar and its libraries, the engine among them; JavaFX is in the runtime.
New-Item -ItemType Directory target\input | Out-Null
Remove-Item desktop\target\lib\javafx-*.jar
Copy-Item desktop\target\lib\*.jar, desktop\target\car-charge-planner.jar target\input\

jlink --module-path target\javafx "@src\packaging\jlink.args" --output target\runtime
if ($LASTEXITCODE) { exit 1 }

# The licences: the app's own and one file per library; the runtime's are in runtime\legal.
New-Item -ItemType Directory target\legal | Out-Null
Copy-Item LICENSE, NOTICE, ADDITIONAL-TERMS.md, src\packaging\legal\*.txt target\legal\

$installer = @()
if ($Type -eq "exe") {
  # The upgrade code stays the same for every version, so a new installer replaces the old version.
  # The folder is under %LOCALAPPDATA%\Programs: jpackage's default, %LOCALAPPDATA%\car-charge-planner, is where
  # the app keeps its data. The installer shows the GPL before it installs.
  $installer = @("--license-file", "LICENSE",
                 "--win-per-user-install", "--win-menu", "--win-menu-group", "Car charge planner",
                 "--win-shortcut",
                 "--win-upgrade-uuid", "93c62923-67a1-4866-af93-ac179a22a9f0",
                 "--install-dir", "Programs\car-charge-planner")
}
jpackage --type $Type --dest target\dist `
  --name car-charge-planner --app-version $version --vendor "Car charge planner" `
  --input target\input --main-jar car-charge-planner.jar `
  --main-class nl.enadeag.carchargeplanner.Main `
  --runtime-image target\runtime --app-content target\legal `
  --icon src\packaging\windows\car-charge-planner.ico `
  --java-options "--enable-native-access=javafx.graphics,ALL-UNNAMED" @installer
if ($LASTEXITCODE) { exit 1 }
java -cp "desktop\target\lib\*" tools\SbomRuntime.java desktop\target\car-charge-planner.cdx.json target\runtime `
  "target\dist\car-charge-planner-$version-windows.cdx.json"
if ($LASTEXITCODE) { exit 1 }
Get-ChildItem target\dist
