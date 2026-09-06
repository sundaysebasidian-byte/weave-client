param(
    [ValidateSet("x64", "ARM64")]
    [string] $Platform = "x64",
    [ValidateSet("Debug", "Release")]
    [string] $Configuration = "Release"
)

$ErrorActionPreference = "Stop"

$solution = Join-Path $PSScriptRoot "Weave.Windows.sln"
$runtime = if ($Platform -eq "ARM64") { "win-arm64" } else { "win-x64" }

Write-Host "Restoring Windows solution..."
dotnet restore $solution
if ($LASTEXITCODE -ne 0) { throw "Restore failed" }
Write-Host "Building $Configuration|$Platform..."
dotnet build $solution -c $Configuration -p:Platform=$Platform --no-restore
if ($LASTEXITCODE -ne 0) { throw "Build failed" }
dotnet test (Join-Path $PSScriptRoot "src\Weave.Windows.Core\Weave.Windows.Core.Tests\Weave.Windows.Core.Tests.csproj") -c $Configuration
if ($LASTEXITCODE -ne 0) { throw "Core tests failed" }

$core = Join-Path $PSScriptRoot "src\Weave.Windows\runtime\mihomo.exe"
if (-not (Test-Path $core)) {
    Write-Warning "mihomo.exe not bundled. Put an audited Windows Mihomo binary at: $core"
}

$output = Join-Path $PSScriptRoot "artifacts\$Platform"
New-Item -ItemType Directory -Force -Path $output | Out-Null
dotnet publish (Join-Path $PSScriptRoot "src\Weave.Windows\Weave.Windows.csproj") `
    -c $Configuration -p:Platform=$Platform -r $runtime `
    --self-contained true -o $output
if ($LASTEXITCODE -ne 0) { throw "Publish failed" }

Write-Host "Published to $output"
