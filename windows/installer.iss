[Setup]
AppId={{C6EC73B9-A01E-493F-A9E3-BC1747C219F1}
AppName=Weave
AppVersion=0.3.0
AppVerName=Weave Windows 0.3 Preview
DefaultDirName={localappdata}\Programs\Weave
DefaultGroupName=Weave
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
MinVersion=10.0.19041
OutputDir=artifacts\installer
OutputBaseFilename=Weave-Windows-0.3.0-preview-x64-Setup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
UninstallDisplayIcon={app}\Weave.Windows.exe
SetupIconFile=src\Weave.Windows\Assets\Weave.ico
CloseApplications=yes

[Files]
Source: "artifacts\x64\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\Weave"; Filename: "{app}\Weave.Windows.exe"
Name: "{autodesktop}\Weave"; Filename: "{app}\Weave.Windows.exe"

; Never remove LocalAppData/Weave: subscriptions belong to the user.
