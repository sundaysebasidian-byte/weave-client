#ifndef BuildId
  #error BuildId must identify the exact source commit.
#endif
#if VER < 0x06070000
  #error Inno Setup 6.7 or later is required.
#endif

[Setup]
AppId={{C6EC73B9-A01E-493F-A9E3-BC1747C219F1}
AppName=Weave
AppVersion=0.5.0
AppVerName=Weave Windows 0.5.0 RC1
DefaultDirName={autopf}\Weave
UsePreviousAppDir=no
DisableDirPage=yes
DefaultGroupName=Weave
DisableProgramGroupPage=yes
UsePreviousGroup=no
AllowNoIcons=no
PrivilegesRequired=admin
ArchitecturesAllowed=x64os
ArchitecturesInstallIn64BitMode=x64os
MinVersion=10.0.19041
OutputDir=artifacts\installer
#ifdef SyntheticServiceFailure
OutputBaseFilename=Weave-Windows-synthetic-failure-test
#else
OutputBaseFilename=Weave-Windows-0.5.0-rc1-{#Copy(BuildId, 1, 8)}-x64-Setup
#endif
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
UninstallDisplayIcon={code:GetReleaseDir}\Weave.Windows.exe
SetupIconFile=src\Weave.Windows\Assets\Weave.ico
CloseApplications=yes
RestartApplications=no

[Files]
; Setup extracts this native, statically linked boundary into its protected temp directory.
Source: "artifacts\setup\Weave.Setup.exe"; Flags: dontcopy
Source: "artifacts\setup\Weave.Setup.exe"; DestDir: "{app}\setup"; Flags: ignoreversion
#ifdef SyntheticServiceFailure
Source: "artifacts\x64\*"; DestDir: "{code:GetReleaseDir}"; Excludes: "service\Weave.Core.Service.exe"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "artifacts\setup\Weave.Setup.exe"; DestDir: "{code:GetReleaseDir}\service"; DestName: "Weave.Core.Service.exe"; Flags: ignoreversion
#else
Source: "artifacts\x64\*"; DestDir: "{code:GetReleaseDir}"; Flags: ignoreversion recursesubdirs createallsubdirs
#endif

[Icons]
Name: "{group}\Weave"; Filename: "{code:GetReleaseDir}\Weave.Windows.exe"; WorkingDir: "{code:GetReleaseDir}"
Name: "{autodesktop}\Weave"; Filename: "{code:GetReleaseDir}\Weave.Windows.exe"; WorkingDir: "{code:GetReleaseDir}"

[Run]
Filename: "{code:GetReleaseDir}\Weave.Windows.exe"; WorkingDir: "{code:GetReleaseDir}"; Description: "Launch Weave"; Check: InstallationCommitted; Flags: postinstall nowait skipifsilent unchecked runasoriginaluser

[Code]
var
  ReleaseId: String;
  Attempt: Integer;
  TransactionStarted: Boolean;
  TransactionCommitted: Boolean;
  LastOperationError: String;
  MaintenanceMutex: THandle;
  InstallationError: String;

function CreateMaintenanceMutex(Attributes: THandle; InitialOwner: Integer; Name: String): THandle;
  external 'CreateMutexW@kernel32.dll stdcall';
function CloseMaintenanceMutex(Handle: THandle): Integer;
  external 'CloseHandle@kernel32.dll stdcall';

function AcquireMaintenanceMutex: Boolean;
var
  ErrorCode: LongInt;
begin
  // The same atomic existence lock covers both setup and uninstall, across user sessions.
  MaintenanceMutex := CreateMaintenanceMutex(0, 0, 'Global\WeaveMachineSetup');
  ErrorCode := DLLGetLastError;
  Result := (MaintenanceMutex <> 0) and (ErrorCode <> 183);
  if not Result then begin
    if MaintenanceMutex <> 0 then CloseMaintenanceMutex(MaintenanceMutex);
    MaintenanceMutex := 0;
    SuppressibleMsgBox('Weave maintenance is already running or its lock is unavailable. Close the other installer or uninstaller, then retry.' + #13#10 + SysErrorMessage(ErrorCode), mbError, MB_OK, IDOK);
  end;
end;

function InitializeSetup: Boolean;
begin
  Result := AcquireMaintenanceMutex;
end;

function InitializeUninstall: Boolean;
begin
  Result := AcquireMaintenanceMutex;
end;

procedure DeinitializeUninstall;
begin
  if MaintenanceMutex <> 0 then CloseMaintenanceMutex(MaintenanceMutex);
end;

function GetReleaseId: String;
begin
  if ReleaseId = '' then
    ReleaseId := '{#BuildId}-' + Copy(Lowercase(GetSHA256OfString(ExpandConstant('{tmp}') + IntToStr(Attempt))), 1, 16);
  Result := ReleaseId;
end;

function GetReleaseDir(Param: String): String;
begin
  Result := ExpandConstant('{app}\versions\') + GetReleaseId;
end;

function SetupOperation(Operation, Helper, Id: String): Boolean;
var
  Code, I: Integer;
  Output: TExecOutput;
begin
  LastOperationError := '';
  Result := ExecAndCaptureOutput(Helper,
    Operation + ' "' + ExpandConstant('{app}') + '" "' + Id + '"',
    ExtractFileDir(Helper), SW_HIDE, ewWaitUntilTerminated, Code, Output);
  for I := 0 to GetArrayLength(Output.StdOut) - 1 do Log(Output.StdOut[I]);
  for I := 0 to GetArrayLength(Output.StdErr) - 1 do begin
    Log(Output.StdErr[I]);
    LastOperationError := LastOperationError + Output.StdErr[I] + #13#10;
  end;
  Result := Result and (Code = 0) and not Output.Error;
  if not Result and (LastOperationError = '') then
    LastOperationError := 'Weave service setup failed (' + IntToStr(Code) + '). Existing files were retained.';
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  if CompareText(ExpandConstant('{group}'), ExpandConstant('{commonprograms}\Weave')) <> 0 then begin
    Result := 'Weave requires its fixed machine Start Menu folder.';
    Exit;
  end;
  ExtractTemporaryFile('Weave.Setup.exe');
  Attempt := Attempt + 1;
  ReleaseId := '';
  TransactionStarted := True;
  if SetupOperation('begin', ExpandConstant('{tmp}\Weave.Setup.exe'), GetReleaseId) then Result := ''
  else Result := LastOperationError;
end;

procedure RegisterExtraCloseApplicationsResources;
var
  Entry: TFindRec;
  Path: String;
begin
  if FindFirst(ExpandConstant('{app}\versions\*'), Entry) then begin
    try
      repeat
        if (Entry.Attributes and FILE_ATTRIBUTE_DIRECTORY) <> 0 then begin
          Path := ExpandConstant('{app}\versions\') + Entry.Name + '\Weave.Windows.exe';
          if FileExists(Path) then begin
#if VER >= 0x07000000
            RegisterExtraCloseApplicationsResource(Path);
#else
            RegisterExtraCloseApplicationsResource(True, Path);
#endif
          end;
        end;
      until not FindNext(Entry);
    finally
      FindClose(Entry);
    end;
  end;
end;

procedure RecoverAfterFailure;
var
  OriginalError: String;
begin
  OriginalError := LastOperationError;
  if SetupOperation('rollback', ExpandConstant('{tmp}\Weave.Setup.exe'), GetReleaseId) then
    TransactionStarted := False
  else OriginalError := OriginalError + #13#10 + 'Recovery is pending: ' + LastOperationError;
  InstallationError := OriginalError;
  Log(InstallationError);
  SuppressibleMsgBox(InstallationError, mbError, MB_OK, IDOK);
end;

function InstallationCommitted: Boolean;
begin
  Result := TransactionCommitted;
end;

function GetCustomSetupExitCode: Integer;
begin
  // Inno handles exceptions in ssPostInstall/ssDone without failing the exit code.
  if TransactionCommitted then Result := 0 else Result := 1;
end;

procedure CurPageChanged(CurPageID: Integer);
begin
  if (CurPageID = wpFinished) and not TransactionCommitted then begin
    WizardForm.FinishedLabel.Caption := 'Weave installation failed.' + #13#10 + InstallationError;
    WizardForm.RunList.Visible := False;
  end;
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssPostInstall then begin
    if not SetupOperation('activate', ExpandConstant('{tmp}\Weave.Setup.exe'), GetReleaseId) then begin
      RecoverAfterFailure;
      Exit;
    end;
    if not SetupOperation('commit', ExpandConstant('{tmp}\Weave.Setup.exe'), GetReleaseId) then begin
      RecoverAfterFailure;
      Exit;
    end;
    TransactionCommitted := True;
  end;
end;

procedure DeinitializeSetup;
begin
  if TransactionStarted and not TransactionCommitted then begin
    if not SetupOperation('rollback', ExpandConstant('{tmp}\Weave.Setup.exe'), GetReleaseId) then
      Log('Recovery remains pending: ' + LastOperationError);
  end;
  if MaintenanceMutex <> 0 then CloseMaintenanceMutex(MaintenanceMutex);
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usUninstall then begin
    if not SetupOperation('uninstall', ExpandConstant('{app}\setup\Weave.Setup.exe'), '-') then
      RaiseException(LastOperationError);
  end;
end;

// LocalAppData/Weave, ProgramData recovery files and unknown files are never removed here.
