[CmdletBinding()]
param(
    [string]$OutputDir,
    [string[]]$Languages = @("en", "zh-TW"),
    [string[]]$Tabs = @("home", "library", "downloads", "tracking", "settings"),
    [int]$StartupTimeoutSeconds = 60,
    [int]$SettleSeconds = 4
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $repoRoot "build\qa\desktop-localization"
}
$OutputDir = [System.IO.Path]::GetFullPath($OutputDir)
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$manifestPath = Join-Path $OutputDir "manifest.txt"
Remove-Item -LiteralPath $manifestPath -Force -ErrorAction SilentlyContinue

# Kept for compatibility with the previous external-window capture helper.
# App-level capture now exits from the launched Compose app after SettleSeconds.
$null = $StartupTimeoutSeconds

function Invoke-DanmakuForAppLevelQa {
    param(
        [string]$Language,
        [string]$Tab,
        [string]$ScreenshotName,
        [string]$StdOutPath,
        [string]$StdErrPath
    )

    $appArgs = @(
        "--ui-language=$Language",
        "--initial-tab=$Tab",
        "--server-port=0",
        "--qa-screenshot-dir=$OutputDir",
        "--qa-screenshot-name=$ScreenshotName",
        "--qa-screenshot-delay-seconds=$SettleSeconds",
        "--qa-screenshot-exit"
    ) -join " "
    $gradleArgs = @(
        "--no-daemon",
        ":apps:desktop-windows:run",
        "--args=""$appArgs"""
    )

    Push-Location $repoRoot
    try {
        $gradlePath = Join-Path $repoRoot "gradlew.bat"
        & $gradlePath @gradleArgs > $StdOutPath 2> $StdErrPath
        return $LASTEXITCODE
    } finally {
        Pop-Location
    }
}

$captured = @()
foreach ($language in $Languages) {
    foreach ($tab in $Tabs) {
        Write-Host "Capturing $language / $tab"
        $safeLanguage = $language.Replace("-", "_")
        $screenshotName = "desktop-localization-$safeLanguage-$tab"
        $screenshotPath = Join-Path $OutputDir "$screenshotName.png"
        $logPath = Join-Path $OutputDir "gradle-$safeLanguage-$tab.log"
        $errorLogPath = Join-Path $OutputDir "gradle-$safeLanguage-$tab.err.log"
        $exitCode = Invoke-DanmakuForAppLevelQa `
            -Language $language `
            -Tab $tab `
            -ScreenshotName $screenshotName `
            -StdOutPath $logPath `
            -StdErrPath $errorLogPath

        if ($exitCode -ne 0) {
            throw "Desktop screenshot QA launch failed with exit code $exitCode. Check $logPath and $errorLogPath."
        }
        if (-not (Test-Path -LiteralPath $screenshotPath -PathType Leaf)) {
            throw "Desktop app exited without writing $screenshotPath. Check $logPath and $errorLogPath for the in-app QA diagnostic."
        }
        $captured += $screenshotPath
        Start-Sleep -Seconds 1
    }
}

@(
    ""
    "Desktop localization screenshot QA wrapper"
    "Completed: $((Get-Date).ToString("yyyy-MM-dd HH:mm:ss zzz"))"
    "Languages: $($Languages -join ', ')"
    "Tabs: $($Tabs -join ', ')"
    ""
    "Screenshots:"
    $captured
) | Add-Content -Path $manifestPath

Write-Host "Wrote screenshots to $OutputDir"
