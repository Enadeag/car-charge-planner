# Installs WiX 5 and the two WiX extensions jpackage uses, globally for this user, from packages checked against pinned
# SHA-256 sums: dotnet and wix only see a folder with exactly these packages, not nuget.org. Needs the .NET SDK 8 and
# curl.exe. WiX 6 and later ask to accept a maintenance fee agreement first.
# Usage: powershell -ExecutionPolicy Bypass -File tools\install-wix.ps1
$ErrorActionPreference = "Stop"

# A new version needs the new sums: download each package from nuget.org, compare its SHA-512 with the packageHash in
# its catalog entry there, check its signatures with "dotnet nuget verify --all", and take its SHA-256.
$version = "5.0.2"
$packages = [ordered]@{
  "wix"                    = "f30ef0c74e2a986126539c5780be93ac24e8136eaf723b1937b26272703ae173"
  "wixtoolset.util.wixext" = "dda1cc1b4d3b2305b1246f167c6af6fcded5000be84b3bbff3dda4f442bf4740"
  "wixtoolset.ui.wixext"   = "5ef2c707614b9f70b6bbadd2d4abcb4124efee215e9b16bfbc80113079a604c7"
}

$feed = Join-Path ([IO.Path]::GetTempPath()) "wix-$version-$([guid]::NewGuid())"
New-Item -ItemType Directory $feed | Out-Null
try {
  foreach ($id in $packages.Keys) {
    $file = Join-Path $feed "$id.$version.nupkg"
    curl.exe -fsSL --proto "=https" -o $file "https://api.nuget.org/v3-flatcontainer/$id/$version/$id.$version.nupkg"
    if ($LASTEXITCODE) { throw "could not download $id $version" }
    if ((Get-FileHash $file -Algorithm SHA256).Hash -ne $packages[$id]) {
      throw "$file does not have the expected SHA-256"
    }
  }
  # dotnet and wix read the nuget.config of the folder they run in; this one names the folder as the only source
  Set-Content (Join-Path $feed "nuget.config") -Encoding UTF8 -Value @"
<?xml version="1.0" encoding="utf-8"?>
<configuration>
  <packageSources>
    <clear />
    <add key="pinned" value="$feed" />
  </packageSources>
</configuration>
"@
  Push-Location $feed
  try {
    dotnet tool install --global wix --version $version
    if ($LASTEXITCODE) { throw "dotnet tool install wix: exit code $LASTEXITCODE" }
    $wix = Join-Path $env:USERPROFILE ".dotnet\tools\wix.exe"
    foreach ($extension in "WixToolset.Util.wixext", "WixToolset.UI.wixext") {
      & $wix extension add -g "$extension/$version"
      if ($LASTEXITCODE) { throw "wix extension add $extension`: exit code $LASTEXITCODE" }
    }
  } finally {
    Pop-Location
  }
} finally {
  Remove-Item -Recurse -Force $feed
}
