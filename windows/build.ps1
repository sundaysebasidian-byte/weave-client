param(
    [ValidateSet("x64", "ARM64")]
    [string] $Platform = "x64",
    [ValidateSet("Debug", "Release")]
    [string] $Configuration = "Release"
)

$ErrorActionPreference = "Stop"

# Wrap the existing 256px Android-matching PNG in an ICO container without changing pixels.
$png = [IO.File]::ReadAllBytes((Join-Path $PSScriptRoot 'src\Weave.Windows\Assets\Weave.png'))
$iconPath = Join-Path $PSScriptRoot 'src\Weave.Windows\Assets\Weave.ico'
$icon = [IO.BinaryWriter]::new([IO.File]::Create($iconPath))
try {
    $icon.Write([uint16]0); $icon.Write([uint16]1); $icon.Write([uint16]1)
    $icon.Write([byte]0); $icon.Write([byte]0); $icon.Write([byte]0); $icon.Write([byte]0)
    $icon.Write([uint16]1); $icon.Write([uint16]32)
    $icon.Write([uint32]$png.Length); $icon.Write([uint32]22); $icon.Write($png)
} finally { $icon.Dispose() }

$solution = Join-Path $PSScriptRoot "Weave.Windows.sln"
$runtime = if ($Platform -eq "ARM64") { "win-arm64" } else { "win-x64" }

Write-Host "Building $Configuration|$Platform..."
msbuild $solution /restore /p:Configuration=$Configuration /p:Platform=$Platform
if ($LASTEXITCODE -ne 0) { throw "Build failed" }
$env:WEAVE_TEST_CORE = Join-Path $PSScriptRoot 'src\Weave.Windows\runtime\mihomo.exe'
$env:WEAVE_TEST_GEODATA = Join-Path $PSScriptRoot '..\app\src\main\assets\geodata'
$env:WEAVE_INTEROP_FIXTURE = Join-Path ([IO.Path]::GetTempPath()) 'weave-android-reference.bin'
java '-Dfile.encoding=UTF-8' (Join-Path $PSScriptRoot 'tests\AndroidTransferInterop.java') $env:WEAVE_INTEROP_FIXTURE
if ($LASTEXITCODE -ne 0) { throw 'Android wire-format fixture failed' }
dotnet test (Join-Path $PSScriptRoot "src\Weave.Windows.Core\Weave.Windows.Core.Tests\Weave.Windows.Core.Tests.csproj") -c $Configuration
if ($LASTEXITCODE -ne 0) { throw "Core tests failed" }
java '-Dfile.encoding=UTF-8' (Join-Path $PSScriptRoot 'tests\AndroidTransferInterop.java') ($env:WEAVE_INTEROP_FIXTURE + '.windows') verify
if ($LASTEXITCODE -ne 0) { throw 'Windows-to-Android wire-format verification failed' }

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
Copy-Item (Join-Path $PSScriptRoot 'THIRD-PARTY.md') $output
Copy-Item (Join-Path $PSScriptRoot '..\LICENSE') (Join-Path $output 'LICENSE-Weave.txt')
Copy-Item (Join-Path $PSScriptRoot '..\geodata-lock.properties') $output
Invoke-WebRequest 'https://raw.githubusercontent.com/MetaCubeX/mihomo/v1.19.30/LICENSE' -OutFile (Join-Path $output 'LICENSE-Mihomo.txt')

Write-Host "Published to $output"
