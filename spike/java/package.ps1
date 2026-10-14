# Question 7, Windows: jlink a runtime with JavaFX, then jpackage an app-image and (with WiX on PATH) an installer.
# Usage: powershell -ExecutionPolicy Bypass -File package.ps1 [-Type app-image|exe|msi]
param([string]$Type = "app-image")
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

cmd /c "mvnw.cmd -q -B package -DskipTests" ; if ($LASTEXITCODE) { exit 1 }
Remove-Item -Recurse -Force target\pkg, target\runtime, target\dist, target\deps, target\fx-tmp -ErrorAction SilentlyContinue
New-Item -ItemType Directory target\pkg, target\fx-tmp | Out-Null
Copy-Item target\java-spike-0.1.jar target\pkg\
cmd /c "mvnw.cmd -q -B dependency:copy-dependencies -DoutputDirectory=target\deps" ; if ($LASTEXITCODE) { exit 1 }
Move-Item target\deps\javafx-*-win.jar target\fx-tmp\     # platform jars; the plain ones are empty shells
Remove-Item target\deps\javafx-*.jar
Copy-Item target\deps\*.jar target\pkg\

jlink --module-path target\fx-tmp `
  --add-modules java.base,java.desktop,java.logging,jdk.net,jdk.security.auth,jdk.unsupported,javafx.controls `
  --strip-debug --no-header-files --no-man-pages --compress zip-6 `
  --output target\runtime
if ($LASTEXITCODE) { exit 1 }

$extra = @()
if ($Type -ne "app-image") { $extra = @("--win-menu", "--win-shortcut", "--win-per-user-install", "--win-dir-chooser") }
jpackage --type $Type --dest target\dist `
  --name car-charge-planner-spike --app-version 0.1 --vendor "Example" `
  --input target\pkg --main-jar java-spike-0.1.jar --main-class spike.App `
  --runtime-image target\runtime `
  --icon src\main\resources\spike\icon.ico `
  --java-options "--enable-native-access=ALL-UNNAMED" @extra
if ($LASTEXITCODE) { exit 1 }
Get-ChildItem target\dist
