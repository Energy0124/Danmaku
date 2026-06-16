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

Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;

public struct DanmakuQaRect {
    public int Left;
    public int Top;
    public int Right;
    public int Bottom;
}

public static class DanmakuQaUser32 {
    [DllImport("user32.dll", SetLastError = true)]
    public static extern IntPtr FindWindow(string lpClassName, string lpWindowName);

    [DllImport("user32.dll")]
    public static extern bool GetWindowRect(IntPtr hWnd, out DanmakuQaRect lpRect);

    [DllImport("user32.dll")]
    public static extern bool SetForegroundWindow(IntPtr hWnd);

    [DllImport("user32.dll")]
    public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);

    [DllImport("user32.dll")]
    public static extern bool PostMessage(IntPtr hWnd, UInt32 Msg, IntPtr wParam, IntPtr lParam);
}
"@

function Get-DanmakuWindow {
    [DanmakuQaUser32]::FindWindow($null, "Danmaku")
}

function Wait-DanmakuWindow {
    param([int]$TimeoutSeconds)

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $window = Get-DanmakuWindow
        if ($window -ne [IntPtr]::Zero) {
            return $window
        }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)

    throw "Timed out waiting for the Danmaku window."
}

function Save-WindowScreenshot {
    param(
        [IntPtr]$Window,
        [string]$Path
    )

    $rect = New-Object DanmakuQaRect
    if (-not [DanmakuQaUser32]::GetWindowRect($Window, [ref]$rect)) {
        throw "Could not read Danmaku window bounds."
    }

    $width = $rect.Right - $rect.Left
    $height = $rect.Bottom - $rect.Top
    if ($width -le 0 -or $height -le 0) {
        throw "Danmaku window bounds are invalid: ${width}x${height}."
    }

    $bitmap = New-Object System.Drawing.Bitmap($width, $height)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.CopyFromScreen($rect.Left, $rect.Top, 0, 0, $bitmap.Size)
        $bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

function Start-DanmakuForQa {
    param(
        [string]$Language,
        [string]$Tab,
        [string]$StdOutPath,
        [string]$StdErrPath
    )

    $args = @(
        "--no-daemon",
        ":apps:desktop-windows:run",
        "--args=""--ui-language=$Language --initial-tab=$Tab --server-port=0"""
    )
    Start-Process `
        -FilePath (Join-Path $repoRoot "gradlew.bat") `
        -ArgumentList $args `
        -WorkingDirectory $repoRoot `
        -RedirectStandardOutput $StdOutPath `
        -RedirectStandardError $StdErrPath `
        -PassThru
}

function Stop-DanmakuForQa {
    param(
        [System.Diagnostics.Process]$Process,
        [IntPtr]$Window
    )

    if ($Window -ne [IntPtr]::Zero) {
        [void][DanmakuQaUser32]::PostMessage($Window, 0x0010, [IntPtr]::Zero, [IntPtr]::Zero)
    }
    if (-not $Process.WaitForExit(15000)) {
        Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue
    }
}

if ((Get-DanmakuWindow) -ne [IntPtr]::Zero) {
    throw "Close the existing Danmaku window before running screenshot QA."
}

$captured = @()
foreach ($language in $Languages) {
    foreach ($tab in $Tabs) {
        Write-Host "Capturing $language / $tab"
        $safeLanguage = $language.Replace("-", "_")
        $logPath = Join-Path $OutputDir "gradle-$safeLanguage-$tab.log"
        $errorLogPath = Join-Path $OutputDir "gradle-$safeLanguage-$tab.err.log"
        $process = Start-DanmakuForQa `
            -Language $language `
            -Tab $tab `
            -StdOutPath $logPath `
            -StdErrPath $errorLogPath
        $window = [IntPtr]::Zero
        try {
            $window = Wait-DanmakuWindow -TimeoutSeconds $StartupTimeoutSeconds
            [void][DanmakuQaUser32]::ShowWindow($window, 9)
            [void][DanmakuQaUser32]::SetForegroundWindow($window)
            Start-Sleep -Seconds $SettleSeconds

            $screenshotPath = Join-Path $OutputDir "desktop-localization-$safeLanguage-$tab.png"
            Save-WindowScreenshot -Window $window -Path $screenshotPath
            $captured += $screenshotPath
        } catch {
            throw "$($_.Exception.Message) Check $logPath and $errorLogPath. If Gradle reaches :apps:desktop-windows:run but no Danmaku window appears, rerun this helper from the interactive desktop session rather than an elevated or background shell."
        } finally {
            Stop-DanmakuForQa -Process $process -Window $window
            Start-Sleep -Seconds 2
        }
    }
}

$manifestPath = Join-Path $OutputDir "manifest.txt"
@(
    "Desktop localization screenshot QA"
    "Captured: $((Get-Date).ToString("yyyy-MM-dd HH:mm:ss zzz"))"
    "Languages: $($Languages -join ', ')"
    "Tabs: $($Tabs -join ', ')"
    ""
    "Screenshots:"
    $captured
) | Set-Content -Path $manifestPath

Write-Host "Wrote screenshots to $OutputDir"
