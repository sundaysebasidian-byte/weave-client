$ErrorActionPreference = 'Stop'
if ($env:WEAVE_ISOLATED_INSTALLER_TEST -ne '1' -or $env:GITHUB_ACTIONS -ne 'true') {
    throw 'Native setup acceptance requires a disposable GitHub Windows runner.'
}
$root = Split-Path $PSScriptRoot -Parent
$install = Join-Path $env:ProgramFiles 'Weave'
$results = Join-Path $root 'artifacts\setup-boundary-tests'
New-Item -ItemType Directory -Force $results | Out-Null
if ((Test-Path $install) -or (Get-Service WeaveCore -ErrorAction SilentlyContinue)) {
    throw 'An existing installation must not be used for native setup acceptance.'
}
$paths = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($folder in @($env:ProgramFiles, [Environment]::GetFolderPath('CommonPrograms'), [Environment]::GetFolderPath('CommonDesktopDirectory'))) {
    for ($directory = [IO.DirectoryInfo]::new($folder); $null -ne $directory; $directory = $directory.Parent) { $null = $paths.Add($directory.FullName) }
}
@($paths | Sort-Object | ForEach-Object {
    $acl = Get-Acl $_
    @{ path = $_; owner = $acl.Owner; sddl = $acl.Sddl }
}) | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $results 'folder-security.json')
$id = ('0' * 40) + '-' + ('0' * 16)
$helper = Join-Path $root 'artifacts\setup\Weave.Setup.exe'
$retained = $null
try {
    & $helper begin $install $id 2>&1 | Tee-Object (Join-Path $results 'begin.txt')
    if ($LASTEXITCODE -ne 0) { throw 'Native setup could not prepare its protected storage.' }
    if (-not (Test-Path (Join-Path $install 'setup-pending.txt'))) { throw 'Setup did not persist its recovery record.' }
    # Simulate interruption after first-install shortcuts were written, before service activation.
    $shortcuts = @((Join-Path ([Environment]::GetFolderPath('CommonPrograms')) 'Weave\Weave.lnk'),
        (Join-Path ([Environment]::GetFolderPath('CommonDesktopDirectory')) 'Weave.lnk'))
    $shell = New-Object -ComObject WScript.Shell
    try {
        foreach ($path in $shortcuts) {
            if (Test-Path $path) { throw 'An existing shortcut must not be overwritten by setup acceptance.' }
            $link = $shell.CreateShortcut($path)
            try {
                $link.TargetPath = Join-Path $install "versions\$id\Weave.Windows.exe"
                $link.Save()
            } finally { [Runtime.InteropServices.Marshal]::FinalReleaseComObject($link) | Out-Null }
        }
    } finally { [Runtime.InteropServices.Marshal]::FinalReleaseComObject($shell) | Out-Null }
    $desktopAcl = Get-Acl $shortcuts[1]
    foreach ($sid in @('S-1-5-32-545', 'S-1-5-11')) {
        $desktopAcl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new(
            [Security.Principal.SecurityIdentifier]::new($sid), 'Modify', 'Allow'))
    }
    Set-Acl $shortcuts[1] $desktopAcl
    & $helper rollback $install $id 2>&1 | Tee-Object (Join-Path $results 'rollback.txt')
    if ($LASTEXITCODE -ne 0) { throw 'Native setup could not recover its prepared transaction.' }
    foreach ($path in $shortcuts) { if (Test-Path $path) { throw 'Recovery left an incomplete first-install shortcut.' } }
    if ((Test-Path (Join-Path $install 'setup-pending.txt')) -or (Get-Service WeaveCore -ErrorAction SilentlyContinue)) {
        throw 'Native preparation recovery left active state.'
    }
    if (-not (Test-Path (Join-Path $install "setup-history\$id-rolled-back.txt"))) { throw 'Native setup lost its recovery evidence.' }
    $retained = Join-Path $env:ProgramFiles ('Weave-preflight-' + [Guid]::NewGuid().ToString('N'))
    [IO.Directory]::Move($install, $retained)
    # A chosen install folder: a new Weave folder directly under the system drive is created protected and accepted;
    # one below a parent that ordinary users may modify (the inherited default for new top-level folders) is refused.
    $drive = [IO.Path]::GetPathRoot($env:SystemRoot)
    $custom = Join-Path $drive 'Weave'
    if (-not (Test-Path $custom)) {
        try {
            & $helper begin $custom $id 2>&1 | Tee-Object (Join-Path $results 'custom-begin.txt')
            if ($LASTEXITCODE -ne 0) { throw 'Native setup refused a protected custom installation folder.' }
            & $helper rollback $custom $id 2>&1 | Tee-Object (Join-Path $results 'custom-rollback.txt')
            if ($LASTEXITCODE -ne 0) { throw 'Native setup could not recover a custom installation folder.' }
        } finally { if (Test-Path $custom) { Remove-Item $custom -Recurse -Force } }
    }
    $loose = Join-Path $drive ('WeaveCI-' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory $loose | Out-Null
    try {
        & $helper begin (Join-Path $loose 'Weave') $id 2>&1 | Tee-Object (Join-Path $results 'custom-unsafe.txt')
        if ($LASTEXITCODE -eq 0) { throw 'Native setup accepted a folder below a user-modifiable parent.' }
        if (Test-Path (Join-Path $loose 'Weave')) { throw 'A refused custom folder was still created.' }
    } finally { Remove-Item $loose -Recurse -Force }
    'Fresh protected setup storage and prepared-transaction recovery passed.' | Set-Content (Join-Path $results 'acceptance.txt')
} finally {
    @{ sourceCommit = (git rev-parse HEAD); originalPath = $install; retainedPath = $retained; filesRetained = $true } |
        ConvertTo-Json | Set-Content (Join-Path $results 'manifest.json')
}
