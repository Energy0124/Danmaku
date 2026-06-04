[CmdletBinding()]
param(
    [string]$WindowsDistributionPath = (
        Join-Path $PSScriptRoot (
            "..\..\apps\desktop-windows\build\release\windows-portable"
        )
    ),
    [string]$MediaPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$windowsFullPath = [System.IO.Path]::GetFullPath($WindowsDistributionPath)
$appPath = Join-Path $windowsFullPath "app"
if (-not (Test-Path -LiteralPath $appPath -PathType Container)) {
    throw "Windows desktop application directory does not exist: $appPath"
}

$mpvBridgePath = Join-Path $appPath "player_windows_mpv.dll"
$libmpvPath = Join-Path $appPath "libmpv-2.dll"
foreach ($nativePath in @($mpvBridgePath, $libmpvPath)) {
    if (-not (Test-Path -LiteralPath $nativePath -PathType Leaf)) {
        throw "Required Windows mpv runtime dependency does not exist: $nativePath"
    }
}

$javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue |
    Select-Object -First 1
if ($null -eq $javaCommand) {
    throw "Java 17 or newer is required to verify the Windows mpv runtime."
}

$previousBridgePath = $env:DANMAKU_MPV_BRIDGE_PATH
$previousLibmpvPath = $env:DANMAKU_LIBMPV_PATH
try {
    $env:DANMAKU_MPV_BRIDGE_PATH = $mpvBridgePath
    $env:DANMAKU_LIBMPV_PATH = $libmpvPath
    $javaArgs = @(
        "-Djava.library.path=$appPath"
        "-cp"
        (Join-Path $appPath "*")
        "app.danmaku.desktop.DesktopMpvNativeProbe"
    )
    if (-not [string]::IsNullOrWhiteSpace($MediaPath)) {
        $mediaFullPath = [System.IO.Path]::GetFullPath($MediaPath)
        if (-not (Test-Path -LiteralPath $mediaFullPath -PathType Leaf)) {
            throw "Probe media file does not exist: $mediaFullPath"
        }
        $javaArgs += $mediaFullPath
    }
    & $javaCommand.Source @javaArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Packaged Windows mpv runtime probe failed with exit code $LASTEXITCODE."
    }
} finally {
    $env:DANMAKU_MPV_BRIDGE_PATH = $previousBridgePath
    $env:DANMAKU_LIBMPV_PATH = $previousLibmpvPath
}

Write-Host "Packaged Windows mpv runtime probe passed."
