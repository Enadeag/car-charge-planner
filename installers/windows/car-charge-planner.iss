; Inno Setup script: wraps the PyInstaller folder in car-charge-planner-<version>-setup.exe, installed per user (no admin).
; tools/build_windows.py compiles it and passes the values below with /D, taken from the app's own code, so that the
; installer and the app agree on the name, the AppUserModelID and the login entry.

#ifndef AppVersion
  #error Build with tools/build_windows.py, which defines AppVersion, AppName, AppUserModelID, ExeName, BundleDir, NoticesFile, LicenseFile, NoticeFile, TermsFile, IconFile, OutputDir, Homepage and Comment
#endif

#define RunKey "Software\Microsoft\Windows\CurrentVersion\Run"

[Setup]
; The AppId ties upgrades and the uninstaller to this app: never change it.
AppId={{6D793C76-B229-4463-93AD-A34843EFCA66}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher={#AppName}
AppPublisherURL={#Homepage}
AppSupportURL={#Homepage}/issues
VersionInfoVersion={#AppVersion}
PrivilegesRequired=lowest
DefaultDirName={autopf}\{#AppName}
DisableProgramGroupPage=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
SetupIconFile={#IconFile}
UninstallDisplayIcon={app}\{#ExeName}
UninstallDisplayName={#AppName}
WizardStyle=modern
Compression=lzma2/max
SolidCompression=yes
OutputDir={#OutputDir}
OutputBaseFilename=car-charge-planner-{#AppVersion}-setup

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "startatlogin"; Description: "Start when I log in"

[InstallDelete]
; the libraries of an earlier version must not mix with this one's
Type: filesandordirs; Name: "{app}\_internal"

[Files]
Source: "{#BundleDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#NoticesFile}"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#LicenseFile}"; DestDir: "{app}"; DestName: "LICENSE.txt"; Flags: ignoreversion
Source: "{#NoticeFile}"; DestDir: "{app}"; DestName: "NOTICE.txt"; Flags: ignoreversion
Source: "{#TermsFile}"; DestDir: "{app}"; DestName: "ADDITIONAL-TERMS.txt"; Flags: ignoreversion

[Icons]
; the same name and folder as the app's own menu entry, so that the app sees it as installed; the AppUserModelID is
; the one the program sets at start, which gives its notifications this shortcut's name and icon
Name: "{userprograms}\{#AppName}"; Filename: "{app}\{#ExeName}"; Comment: "{#Comment}"; AppUserModelID: "{#AppUserModelID}"

[Registry]
; the value the app's "Start when I log in" setting writes, so that the setting shows it as on
Root: HKCU; Subkey: "{#RunKey}"; ValueType: string; ValueName: "{#AppName}"; ValueData: """{app}\{#ExeName}"" --background"; Tasks: startatlogin; Flags: uninsdeletevalue

[Run]
Filename: "{app}\{#ExeName}"; Description: "Start {#AppName} now"; Flags: nowait postinstall skipifsilent

[Code]
// The setting in the app may have written the login entry after installation: remove it too when it starts this
// installation. The settings, the state and the keychain entries are the user's and stay.
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  Command: String;
begin
  if (CurUninstallStep = usUninstall) and RegQueryStringValue(HKCU, '{#RunKey}', '{#AppName}', Command) then
    if Pos(Lowercase(ExpandConstant('{app}\{#ExeName}')), Lowercase(Command)) > 0 then
      RegDeleteValue(HKCU, '{#RunKey}', '{#AppName}');
end;
