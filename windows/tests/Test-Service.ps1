$ErrorActionPreference = 'Stop'
if ($env:WEAVE_ISOLATED_SERVICE_TEST -ne '1' -or $env:WEAVE_TEST_TUN -ne '1' -or $env:GITHUB_ACTIONS -ne 'true') {
    throw 'Service installation tests require explicit opt-in on a disposable GitHub Windows runner.'
}
$root = Split-Path $PSScriptRoot -Parent
$serviceName = 'WeaveCore'
$startedAt = Get-Date
if (Get-Service $serviceName -ErrorAction SilentlyContinue) { throw 'An existing Weave service must not be changed by this test.' }
$suffix = [Guid]::NewGuid().ToString('N').Substring(0, 12)
$install = Join-Path $env:ProgramFiles "Weave-CI-$suffix"
$results = Join-Path $root 'artifacts\service-tests'
$probePublish = Join-Path $root 'artifacts\service-probe'
$diagnosticPublish = Join-Path $root 'artifacts\synthetic-service'
New-Item -ItemType Directory -Path $results -Force | Out-Null
dotnet publish (Join-Path $PSScriptRoot 'Weave.ServiceProbe\Weave.ServiceProbe.csproj') -c Release -r win-x64 --self-contained true -o $probePublish
if ($LASTEXITCODE -ne 0) { throw 'Service test client build failed' }
dotnet publish (Join-Path $root 'src\Weave.Core.Service\Weave.Core.Service.csproj') -c Release -r win-x64 --self-contained true `
    -p:WeaveSyntheticDiagnostics=true -o $diagnosticPublish
if ($LASTEXITCODE -ne 0) { throw 'Synthetic service diagnostic build failed' }
$packagedAssembly = Join-Path $root 'artifacts\x64\service\Weave.Core.Service.dll'
if ([Text.Encoding]::ASCII.GetString([IO.File]::ReadAllBytes($packagedAssembly)).Contains('SyntheticDiagnostics')) {
    throw 'Synthetic diagnostics must never enter the packaged service'
}
if (-not [Text.Encoding]::ASCII.GetString([IO.File]::ReadAllBytes((Join-Path $diagnosticPublish 'Weave.Core.Service.dll'))).Contains('SyntheticDiagnostics')) {
    throw 'Diagnostic service did not include its bounded test observer'
}
if (Test-Path $install) { throw 'Unexpected existing installation path' }
New-Item -ItemType Directory -Path $install | Out-Null
Copy-Item "$probePublish\*" $install -Recurse
Copy-Item (Join-Path $probePublish 'Weave.ServiceProbe.exe') (Join-Path $install 'Weave.Windows.exe')
Copy-Item (Join-Path $probePublish 'Weave.ServiceProbe.exe') (Join-Path $install 'Unapproved.exe')
Copy-Item $diagnosticPublish (Join-Path $install 'service') -Recurse
foreach ($folder in @('runtime', 'geodata')) {
    Copy-Item (Join-Path $root "artifacts\x64\$folder") (Join-Path $install $folder) -Recurse
}
# Installed binaries are administrator-owned and writable only by administrators/SYSTEM.
# Do not repair existing paths; this is a newly created, disposable test tree.
$admins = [Security.Principal.SecurityIdentifier]::new('S-1-5-32-544')
$system = [Security.Principal.SecurityIdentifier]::new('S-1-5-18')
$users = [Security.Principal.SecurityIdentifier]::new('S-1-5-32-545')
$acl = [Security.AccessControl.DirectorySecurity]::new()
$acl.SetOwner($admins)
$acl.SetAccessRuleProtection($true, $false)
foreach ($sid in @($admins, $system)) {
    $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($sid, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'))
}
$acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($users, 'ReadAndExecute', 'ContainerInherit,ObjectInherit', 'None', 'Allow'))
Set-Acl $install $acl
foreach ($entry in Get-ChildItem $install -Recurse) {
    $childAcl = Get-Acl $entry.FullName
    $childAcl.SetOwner($admins)
    Set-Acl $entry.FullName $childAcl
}
$serviceExe = Join-Path $install 'service\Weave.Core.Service.exe'
$username = "Weave$suffix"
$createdUser = $null
$createdService = $false
$processes = [Collections.Generic.List[Diagnostics.Process]]::new()
$sc = Join-Path $env:windir 'System32\sc.exe'
function Invoke-ServiceConfig([string[]] $Arguments) {
    & $sc @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Service configuration failed ($LASTEXITCODE)" }
}
function Start-Probe($mode, $file = 'Weave.Windows.exe') {
    $out = Join-Path $results "$mode.out.txt"
    $err = Join-Path $results "$mode.err.txt"
    $process = Start-Process -FilePath (Join-Path $install $file) -ArgumentList @($mode, "`"$serviceExe`"") `
        -Credential $credential -LoadUserProfile -WorkingDirectory $install -PassThru `
        -RedirectStandardOutput $out -RedirectStandardError $err
    $processes.Add($process)
    return $process
}
function Assert-ProbeExit($process, $mode) {
    if (-not $process.WaitForExit(90000)) { throw "$mode client timed out" }
    $process.Refresh()
    Get-Content (Join-Path $results "$mode.out.txt")
    Get-Content (Join-Path $results "$mode.err.txt")
    if ($process.ExitCode -ne 0) { throw "$mode client exited $($process.ExitCode)" }
}
function Get-OwnedService {
    $service = Get-CimInstance Win32_Service -Filter "Name='$serviceName'"
    if ($service -and $service.PathName -ne "`"$serviceExe`"") { throw 'Service ownership changed; refusing to modify it' }
    return $service
}
try {
    $password = ConvertTo-SecureString ('Weave!' + [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))) -AsPlainText -Force
    $createdUser = New-LocalUser -Name $username -Password $password -AccountNeverExpires -PasswordNeverExpires -Description 'Disposable Weave service acceptance'
    Add-LocalGroupMember -SID $users -Member $createdUser
    $credential = [PSCredential]::new("$env:COMPUTERNAME\$username", $password)
    Invoke-ServiceConfig @('create', $serviceName, 'binPath=', "`"$serviceExe`"", 'start=', 'demand', 'obj=', 'LocalSystem')
    $createdService = $true
    Invoke-ServiceConfig @('sidtype', $serviceName, 'unrestricted')
    Invoke-ServiceConfig @('privs', $serviceName, 'SeChangeNotifyPrivilege/SeLoadDriverPrivilege')
    Start-Service $serviceName
    (Get-Service $serviceName).WaitForStatus('Running', [TimeSpan]::FromSeconds(30))
    Assert-ProbeExit (Start-Probe 'check') 'check'
    Assert-ProbeExit (Start-Probe 'reject' 'Unapproved.exe') 'reject'
    $crash = Start-Probe 'crash'
    $deadline = (Get-Date).AddSeconds(45)
    do {
        if ($crash.HasExited) { Assert-ProbeExit $crash 'crash'; throw 'Crash client exited before its core was ready' }
        if ((Get-Content (Join-Path $results 'crash.out.txt') -Raw) -match 'CORE_READY') { break }
        Start-Sleep -Milliseconds 200
    } while ((Get-Date) -lt $deadline)
    if ((Get-Content (Join-Path $results 'crash.out.txt') -Raw) -notmatch 'CORE_READY') { throw 'Core never became ready for service crash test' }
    $ownedService = Get-OwnedService
    $children = @(Get-CimInstance Win32_Process -Filter "ParentProcessId=$($ownedService.ProcessId)" | Where-Object Name -eq 'mihomo.exe')
    if ($children.Count -ne 1) { throw 'Expected exactly one service-owned core before crash' }
    Stop-Process -Id $ownedService.ProcessId -Force
    Assert-ProbeExit $crash 'crash'
    foreach ($child in $children) {
        $remaining = Get-CimInstance Win32_Process -Filter "ProcessId=$($child.ProcessId)"
        if ($remaining -and $remaining.CreationDate -eq $child.CreationDate) { throw 'Core survived its service job' }
    }
    'Ordinary-user TUN forwarding, authenticated endpoint, stop/reconnect and service-crash cleanup passed.' | Set-Content (Join-Path $results 'acceptance.txt')
}
finally {
    $coreLog = Join-Path $install 'synthetic-core.log'
    if (Test-Path $coreLog) { Copy-Item $coreLog (Join-Path $results 'synthetic-core.log') }
    Get-WinEvent -FilterHashtable @{LogName='Application'; StartTime=$startedAt} -ErrorAction SilentlyContinue |
        Where-Object { $_.ProviderName -in @('WeaveCore', '.NET Runtime') } |
        Select-Object TimeCreated, ProviderName, Id, LevelDisplayName, Message |
        ConvertTo-Json -Depth 4 | Set-Content (Join-Path $results 'service-events.json')
    @{ install = $install; service = $serviceExe; userSid = $createdUser.Sid.Value; sourceCommit = (git rev-parse HEAD); filesRetained = $true } |
        ConvertTo-Json | Set-Content (Join-Path $results 'cleanup-manifest.json')
    foreach ($process in $processes) {
        $process.Refresh()
        if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force; $process.WaitForExit(10000) | Out-Null }
        $process.Dispose()
    }
    if ($createdService) {
        $owned = Get-OwnedService
        if ($owned -and $owned.State -ne 'Stopped') { Stop-Service $serviceName; (Get-Service $serviceName).WaitForStatus('Stopped', [TimeSpan]::FromSeconds(30)) }
        Invoke-ServiceConfig @('delete', $serviceName)
    }
    if ($createdUser -and (Get-LocalUser $username).Sid.Value -eq $createdUser.Sid.Value) { Remove-LocalUser -SID $createdUser.Sid }
    # Keep the exact test binaries and private service directory for failure inspection;
    # the disposable runner owns their eventual teardown.
}
