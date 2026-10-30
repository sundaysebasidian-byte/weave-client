param([string] $GuiPath, [string] $CapturePath, [string] $DiagnosticPath, [string] $StatePath, [string] $ExpectedSid)
$ErrorActionPreference = 'Stop'
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = [Security.Principal.WindowsPrincipal]::new($identity)
if ($identity.User.Value -ne $ExpectedSid -or $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Installed GUI acceptance must run under its designated ordinary account.'
}
$env:WEAVE_UI_CAPTURE = $CapturePath
$env:WEAVE_STARTUP_DIAGNOSTIC = $DiagnosticPath
$process = $null
try {
    $dataDirectory = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'Weave'
    New-Item -ItemType Directory -Force $dataDirectory | Out-Null
    $sentinel = Join-Path $dataDirectory 'installer-acceptance.txt'
    $stream = [IO.File]::Open($sentinel, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    $bytes = [Text.Encoding]::UTF8.GetBytes([Guid]::NewGuid().ToString())
    try { $stream.Write($bytes, 0, $bytes.Length) } finally { $stream.Dispose() }
    $process = Start-Process $GuiPath -PassThru -WorkingDirectory (Split-Path $GuiPath -Parent)
    $state = @{ pid = $process.Id; sid = $identity.User.Value; localAppData = [Environment]::GetFolderPath('LocalApplicationData'); sentinelPath = $sentinel; sentinelHash = (Get-FileHash $sentinel -Algorithm SHA256).Hash }
    [IO.File]::WriteAllText($StatePath + '.new', ($state | ConvertTo-Json))
    [IO.File]::Move($StatePath + '.new', $StatePath)
    $process.WaitForExit()
    exit $process.ExitCode
} catch {
    if ($process -and -not $process.HasExited) { $process.Kill(); $process.WaitForExit(10000) | Out-Null }
    throw
} finally { if ($process) { $process.Dispose() } }
