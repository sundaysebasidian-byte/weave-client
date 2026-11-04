param([switch] $TestGuiProcesses)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$output = Join-Path $root 'artifacts\setup'
New-Item -ItemType Directory -Force $output | Out-Null
$vswhere = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe"
$vs = & $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
if ($LASTEXITCODE -ne 0 -or -not $vs) { throw 'The Windows C++ build tools are required for the native setup boundary.' }
$developer = Join-Path $vs 'Common7\Tools\VsDevCmd.bat'
$source = Join-Path $PSScriptRoot 'Weave.Setup.cpp'
$command = 'call "{0}" -no_logo -host_arch=x64 -arch=x64 && cl.exe /nologo /std:c++20 /W4 /WX /EHsc /O2 /MT /DUNICODE /D_UNICODE /guard:cf /Fe"{1}\Weave.Setup.exe" /Fo"{1}\Weave.Setup.obj" "{2}" /link /guard:cf /DYNAMICBASE /NXCOMPAT advapi32.lib shell32.lib shlwapi.lib ole32.lib uuid.lib' -f $developer, $output, $source
& $env:ComSpec /d /s /c $command
if ($LASTEXITCODE -ne 0) { throw 'Native setup build failed' }

if ($TestGuiProcesses) {
    $tests = Join-Path $root 'artifacts\setup-boundary-tests'
    New-Item -ItemType Directory -Force $tests | Out-Null
    $source = Join-Path $root 'tests\Test-GuiProcesses.cpp'
    $command = 'call "{0}" -no_logo -host_arch=x64 -arch=x64 && cl.exe /nologo /std:c++20 /W4 /WX /EHsc /O2 /MT /DUNICODE /D_UNICODE /guard:cf /Fe"{1}\Weave.Setup.GuiTests.exe" /Fo"{1}\Weave.Setup.GuiTests.obj" "{2}" /link /guard:cf /DYNAMICBASE /NXCOMPAT advapi32.lib shell32.lib shlwapi.lib ole32.lib uuid.lib' -f $developer, $tests, $source
    & $env:ComSpec /d /s /c $command
    if ($LASTEXITCODE -ne 0) { throw 'Native GUI-process regression build failed' }
    & (Join-Path $tests 'Weave.Setup.GuiTests.exe') $tests 2>&1 | Tee-Object (Join-Path $tests 'gui-process-tests.txt')
    if ($LASTEXITCODE -ne 0) { throw 'Native GUI-process regressions failed' }
}
