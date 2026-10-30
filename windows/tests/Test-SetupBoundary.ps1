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
    & $helper rollback $install $id 2>&1 | Tee-Object (Join-Path $results 'rollback.txt')
    if ($LASTEXITCODE -ne 0) { throw 'Native setup could not recover its prepared transaction.' }
    if ((Test-Path (Join-Path $install 'setup-pending.txt')) -or (Get-Service WeaveCore -ErrorAction SilentlyContinue)) {
        throw 'Native preparation recovery left active state.'
    }
    if (-not (Test-Path (Join-Path $install "setup-history\$id-rolled-back.txt"))) { throw 'Native setup lost its recovery evidence.' }
    $retained = Join-Path $env:ProgramFiles ('Weave-preflight-' + [Guid]::NewGuid().ToString('N'))
    [IO.Directory]::Move($install, $retained)
    'Fresh protected setup storage and prepared-transaction recovery passed.' | Set-Content (Join-Path $results 'acceptance.txt')
} finally {
    @{ sourceCommit = (git rev-parse HEAD); originalPath = $install; retainedPath = $retained; filesRetained = $true } |
        ConvertTo-Json | Set-Content (Join-Path $results 'manifest.json')
}
