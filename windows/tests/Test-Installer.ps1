param([Parameter(Mandatory)][string] $Compiler, [Parameter(Mandatory)][string] $BuildId)
$ErrorActionPreference = 'Stop'
if ($env:WEAVE_ISOLATED_INSTALLER_TEST -ne '1' -or $env:GITHUB_ACTIONS -ne 'true') {
    throw 'Installer acceptance requires a disposable GitHub Windows runner.'
}
$root = Split-Path $PSScriptRoot -Parent
$install = Join-Path $env:ProgramFiles 'Weave'
$results = Join-Path $root 'artifacts\installer-tests'
New-Item -ItemType Directory -Force $results | Out-Null
if ((Test-Path $install) -or (Get-Service WeaveCore -ErrorAction SilentlyContinue)) {
    throw 'Existing installation/service must not be changed by installer acceptance.'
}
$packages = @(Get-ChildItem (Join-Path $root 'artifacts\installer') -Filter '*-Setup.exe')
if ($packages.Count -ne 1) { throw 'Expected exactly one production installer.' }
$package = $packages[0].FullName
$sc = Join-Path $env:windir 'System32\sc.exe'
$steps = [Collections.Generic.List[object]]::new()
$gui = $null
$testUser = $null
$sentinel = Join-Path $env:LOCALAPPDATA 'Weave\installer-acceptance.txt'
if (Test-Path $sentinel) { throw 'Existing user-data sentinel must not be overwritten.' }
New-Item -ItemType Directory -Force (Split-Path $sentinel -Parent) | Out-Null
[IO.File]::WriteAllText($sentinel, [Guid]::NewGuid().ToString())
$sentinelHash = (Get-FileHash $sentinel -Algorithm SHA256).Hash
function Invoke-Setup($file, $name, [bool] $success = $true) {
    $log = Join-Path $results "$name.log"
    $args = @('/VERYSILENT', '/SUPPRESSMSGBOXES', '/NORESTART', '/SP-', "/LOG=`"$log`"")
    $process = Start-Process -FilePath $file -ArgumentList $args -PassThru -WorkingDirectory (Split-Path $file -Parent)
    try {
        if (-not $process.WaitForExit(240000)) {
            $steps.Add(@{ name = $name; timedOut = $true; pid = $process.Id })
            $process.Kill($true)
            if (-not $process.WaitForExit(10000)) { throw "$name setup exit is unconfirmed; files retained." }
            throw "$name setup timed out; recovery files retained."
        }
        $process.Refresh()
        $steps.Add(@{ name = $name; exitCode = $process.ExitCode; expectedSuccess = $success })
        if (($process.ExitCode -eq 0) -ne $success) { Get-Content $log -Tail 70; throw "$name returned unexpected exit code $($process.ExitCode)" }
    } finally { $process.Dispose() }
}
function Service-Image {
    $service = Get-CimInstance Win32_Service -Filter "Name='WeaveCore'"
    if (-not $service -or $service.State -ne 'Running' -or $service.StartName -ne 'LocalSystem') { throw 'Installed service is not running as LocalSystem.' }
    $prefix = [Regex]::Escape((Join-Path $install 'versions') + '\')
    if ($service.PathName -notmatch ('^"' + $prefix + '[0-9a-f]{40}-[0-9a-f]{16}\\service\\Weave.Core.Service.exe"$')) { throw 'Unexpected installed service image.' }
    return $service.PathName
}
function Assert-UserData {
    if ((Get-FileHash $sentinel -Algorithm SHA256).Hash -ne $sentinelHash) { throw 'Setup changed user data.' }
}
function Invoke-Sc([string[]] $Arguments) {
    & $sc @Arguments
    if ($LASTEXITCODE -ne 0) { throw "SCM test operation failed: $LASTEXITCODE" }
}
try {
    # A planted writable directory must be rejected, not repaired or executed.
    New-Item -ItemType Directory $install | Out-Null
    $acl = Get-Acl $install
    $users = [Security.Principal.SecurityIdentifier]::new('S-1-5-32-545')
    $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($users, 'Modify', 'ContainerInherit,ObjectInherit', 'None', 'Allow'))
    Set-Acl $install $acl
    [IO.File]::WriteAllText((Join-Path $install 'unknown.txt'), 'retained planted-directory fixture')
    Invoke-Setup $package 'reject-writable-root' $false
    if (Get-Service WeaveCore -ErrorAction SilentlyContinue) { throw 'Unsafe installation created a service.' }
    if (@(Get-ChildItem $install).Count -ne 1) { throw 'Unsafe installation modified the planted directory.' }
    $retained = Join-Path $env:ProgramFiles ('Weave-rejected-' + [Guid]::NewGuid().ToString('N'))
    Move-Item $install $retained
    $steps.Add(@{ retainedRejectedDirectory = $retained })
    $junctionTarget = Join-Path $env:ProgramFiles ('Weave-junction-target-' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory $junctionTarget | Out-Null
    $junctionMarker = Join-Path $junctionTarget 'untouched.txt'
    [IO.File]::WriteAllText($junctionMarker, 'junction target must remain untouched')
    $junctionHash = (Get-FileHash $junctionMarker -Algorithm SHA256).Hash
    New-Item -ItemType Junction -Path $install -Target $junctionTarget | Out-Null
    Invoke-Setup $package 'reject-junction-root' $false
    if ((Get-FileHash $junctionMarker -Algorithm SHA256).Hash -ne $junctionHash -or @(Get-ChildItem $junctionTarget).Count -ne 1) {
        throw 'Setup traversed or changed a rejected junction.'
    }
    $junctionBackup = Join-Path $env:ProgramFiles ('Weave-rejected-junction-' + [Guid]::NewGuid().ToString('N'))
    [IO.Directory]::Move($install, $junctionBackup)
    $steps.Add(@{ retainedJunction = $junctionBackup; target = $junctionTarget })

    $username = 'WeaveInstall' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
    $password = ConvertTo-SecureString ('Weave!' + [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))) -AsPlainText -Force
    $testUser = New-LocalUser -Name $username -Password $password -AccountNeverExpires -PasswordNeverExpires -Description 'Disposable Weave installer acceptance'
    Add-LocalGroupMember -SID $users -Member $testUser
    $credential = [PSCredential]::new("$env:COMPUTERNAME\$username", $password)
    $resultsAcl = Get-Acl $results
    $resultsAcl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($testUser.Sid, 'Modify', 'ContainerInherit,ObjectInherit', 'None', 'Allow'))
    Set-Acl $results $resultsAcl
    Invoke-Setup $package 'install' 
    $first = Service-Image
    $firstRelease = Split-Path (Split-Path $first.Trim('"') -Parent) -Parent
    $firstGui = Join-Path $firstRelease 'Weave.Windows.exe'
    $firstHashes = @(Get-ChildItem $firstRelease -File -Recurse | ForEach-Object { Get-FileHash $_.FullName -Algorithm SHA256 })
    Assert-UserData
    $env:WEAVE_UI_CAPTURE = Join-Path $results 'installed-gui.png'
    $env:WEAVE_STARTUP_DIAGNOSTIC = Join-Path $results 'installed-gui-error.txt'
    $gui = Start-Process $firstGui -Credential $credential -LoadUserProfile -PassThru -WorkingDirectory $firstRelease
    $deadline = (Get-Date).AddSeconds(25)
    while (-not (Test-Path ($env:WEAVE_UI_CAPTURE + '.ready'))) {
        if ($gui.HasExited -or (Get-Date) -ge $deadline) { throw 'Installed ordinary GUI failed to render.' }
        Start-Sleep -Milliseconds 100
    }
    Invoke-Setup $package 'upgrade'
    $second = Service-Image
    if ($first -eq $second) { throw 'Upgrade did not stage a distinct version.' }
    if (-not $gui.WaitForExit(10000)) { throw 'Upgrade left the previous GUI running.' }
    foreach ($entry in $firstHashes) {
        if ((Get-FileHash $entry.Path -Algorithm SHA256).Hash -ne $entry.Hash) { throw 'Upgrade overwrote the rollback version.' }
    }
    Assert-UserData
    $gui.Dispose(); $gui = $null

    & $Compiler "/DBuildId=$BuildId" '/DSyntheticServiceFailure=1' (Join-Path $root 'installer.iss')
    if ($LASTEXITCODE -ne 0) { throw 'Failure fixture installer compilation failed.' }
    Invoke-Setup (Join-Path $root 'artifacts\installer\Weave-Windows-synthetic-failure-test.exe') 'activation-failure' $false
    if ((Service-Image) -ne $second) { throw 'Failed activation did not restore the preceding service.' }
    if (Test-Path (Join-Path $install 'setup-pending.txt')) { throw 'Failed activation left unresolved recovery state.' }
    Assert-UserData

    $uninstaller = Join-Path $install 'unins000.exe'
    if (-not (Test-Path $uninstaller)) { throw 'Machine uninstaller is missing.' }
    $unknown = Join-Path $install 'keep-unknown.txt'
    [IO.File]::WriteAllText($unknown, 'unknown application-root file must survive uninstall')
    $unknownHash = (Get-FileHash $unknown -Algorithm SHA256).Hash
    Stop-Service WeaveCore
    (Get-Service WeaveCore).WaitForStatus('Stopped', [TimeSpan]::FromSeconds(30))
    # A foreign service path must prevent uninstall from removing any binaries.
    Invoke-Sc @('config', 'WeaveCore', 'binPath=', '"C:\Windows\System32\notepad.exe"')
    Invoke-Setup $uninstaller 'reject-foreign-service' $false
    if ((Get-CimInstance Win32_Service -Filter "Name='WeaveCore'").PathName -ne '"C:\Windows\System32\notepad.exe"') { throw 'Uninstall changed a foreign service.' }
    if (-not (Test-Path $firstGui)) { throw 'Rejected uninstall removed application files.' }
    Invoke-Sc @('config', 'WeaveCore', 'binPath=', $second)
    Start-Service WeaveCore
    (Get-Service WeaveCore).WaitForStatus('Running', [TimeSpan]::FromSeconds(30))
    Invoke-Setup $uninstaller 'uninstall' $true
    if (Get-Service WeaveCore -ErrorAction SilentlyContinue) { throw 'Uninstall left the registered service.' }
    if ((Get-FileHash $unknown -Algorithm SHA256).Hash -ne $unknownHash) { throw 'Uninstall removed unknown files.' }
    Assert-UserData
    'Protected installation, upgrade, activation rollback, foreign-service rejection and uninstall/data preservation passed.' | Set-Content (Join-Path $results 'acceptance.txt')
}
finally {
    @{ sourceCommit = (git rev-parse HEAD); install = $install; userSentinel = $sentinel; sentinelHash = $sentinelHash; steps = $steps; filesRetained = $true } |
        ConvertTo-Json -Depth 6 | Set-Content (Join-Path $results 'manifest.json')
    if ($gui) {
        $gui.Refresh()
        if (-not $gui.HasExited) {
            $gui.CloseMainWindow() | Out-Null
            if (-not $gui.WaitForExit(10000)) { $gui.Kill($true); $gui.WaitForExit(10000) | Out-Null }
        }
        $gui.Dispose()
    }
    if ($testUser -and (Get-LocalUser $testUser.Name).Sid.Value -eq $testUser.Sid.Value) {
        Remove-LocalUser -SID $testUser.Sid
    }
    # Do not sweep versions, recovery records, unknown files or another service after a failure.
    # The disposable runner retains them for its own teardown and artifact inspection.
}
