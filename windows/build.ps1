param(
    [ValidateSet("x64", "ARM64")]
    [string] $Platform = "x64",
    [ValidateSet("Debug", "Release")]
    [string] $Configuration = "Release"
)

$ErrorActionPreference = "Stop"

$solution = Join-Path $PSScriptRoot "Weave.Windows.sln"
$runtime = if ($Platform -eq "ARM64") { "win-arm64" } else { "win-x64" }

Write-Host "Building $Configuration|$Platform..."
msbuild $solution /restore /p:Configuration=$Configuration /p:Platform=$Platform
if ($LASTEXITCODE -ne 0) { throw "Build failed" }
dotnet test (Join-Path $PSScriptRoot "src\Weave.Windows.Core\Weave.Windows.Core.Tests\Weave.Windows.Core.Tests.csproj") -c $Configuration
if ($LASTEXITCODE -ne 0) { throw "Core tests failed" }

$core = Join-Path $PSScriptRoot "src\Weave.Windows\runtime\mihomo.exe"
if (-not (Test-Path $core)) {
    Write-Warning "mihomo.exe not bundled. Put an audited Windows Mihomo binary at: $core"
}

$output = Join-Path $PSScriptRoot "artifacts\$Platform"
New-Item -ItemType Directory -Force -Path $output | Out-Null
msbuild (Join-Path $PSScriptRoot "src\Weave.Windows\Weave.Windows.csproj") `
    /restore /t:Publish /p:Configuration=$Configuration /p:Platform=$Platform `
    /p:RuntimeIdentifier=$runtime /p:SelfContained=true /p:PublishDir="$output\"
if ($LASTEXITCODE -ne 0) { throw "Publish failed" }
Copy-Item (Join-Path $PSScriptRoot 'START-HERE.txt') $output
Copy-Item (Join-Path $PSScriptRoot '..\LICENSE') (Join-Path $output 'LICENSE-Weave.txt')
Invoke-WebRequest 'https://raw.githubusercontent.com/MetaCubeX/mihomo/v1.19.30/LICENSE' -OutFile (Join-Path $output 'LICENSE-Mihomo.txt')

Write-Host "Published to $output"
