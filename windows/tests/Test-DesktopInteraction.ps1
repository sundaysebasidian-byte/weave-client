param([Parameter(Mandatory)][string] $AppPath, [Parameter(Mandatory)][string] $OutputDirectory)
$ErrorActionPreference = 'Stop'
$AppPath = (Resolve-Path -LiteralPath $AppPath).Path
New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
$OutputDirectory = (Resolve-Path -LiteralPath $OutputDirectory).Path
$data = Join-Path $OutputDirectory ('user-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $data | Out-Null
Set-Content -LiteralPath (Join-Path $data 'close-behavior.txt') -Value 'exit'
$names = @('WEAVE_DATA_ROOT','WEAVE_UI_CAPTURE','WEAVE_STARTUP_DIAGNOSTIC','WEAVE_UI_SELF_TEST')
$saved = @{}
foreach ($name in $names) { $saved[$name] = [Environment]::GetEnvironmentVariable($name) }
$process = $null
try {
    $env:WEAVE_DATA_ROOT = $data
    $env:WEAVE_UI_CAPTURE = Join-Path $OutputDirectory 'interaction.png'
    $env:WEAVE_STARTUP_DIAGNOSTIC = Join-Path $data 'startup.txt'
    $env:WEAVE_UI_SELF_TEST = '1'
    $process = Start-Process -FilePath $AppPath -WorkingDirectory (Split-Path $AppPath -Parent) -WindowStyle Hidden -PassThru
    $deadline = [DateTime]::UtcNow.AddSeconds(30)
    while (-not (Test-Path ($env:WEAVE_UI_CAPTURE + '.ready')) -and -not $process.HasExited -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
    if ($process.HasExited -or -not (Test-Path ($env:WEAVE_UI_CAPTURE + '.ready'))) { throw 'Interaction startup did not complete' }
    Add-Type -AssemblyName UIAutomationClient
    $process.Refresh()
    $window = [System.Windows.Automation.AutomationElement]::FromHandle($process.MainWindowHandle)
    foreach ($id in @('Nav1','Nav2','Nav4','Nav5','Nav3','Nav0')) {
        $condition = [System.Windows.Automation.PropertyCondition]::new([System.Windows.Automation.AutomationElement]::AutomationIdProperty, $id)
        $element = $window.FindFirst([System.Windows.Automation.TreeScope]::Descendants, $condition)
        if (-not $element -or -not $element.Current.IsEnabled) { throw "Navigation unavailable: $id" }
        $selection = [System.Windows.Automation.SelectionItemPattern]$element.GetCurrentPattern([System.Windows.Automation.SelectionItemPattern]::Pattern)
        $selection.Select()
        if (-not $selection.Current.IsSelected) { throw "Navigation did not select: $id" }
    }
    if (-not $process.CloseMainWindow()) { throw 'Window rejected WM_CLOSE' }
    if (-not $process.WaitForExit(10000)) { throw 'Exit preference was ignored; the app remained in the tray' }
    if ($process.ExitCode -ne 0) { throw "Desktop exit failed: $($process.ExitCode)" }
    if (Test-Path $env:WEAVE_STARTUP_DIAGNOSTIC) { throw 'Desktop wrote a startup failure diagnostic' }
    Get-Content ($env:WEAVE_UI_CAPTURE + '.interaction.txt')
    'PASS: UI Automation navigated all six pages; saved close-to-exit preference exits cleanly.' | Tee-Object (Join-Path $OutputDirectory 'desktop-interaction.txt')
} finally {
    if ($process) {
        if (-not $process.HasExited) { $process.Kill(); $process.WaitForExit(10000) | Out-Null }
        $process.Dispose()
    }
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $saved[$name]) }
}
