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
$rustPlayerPath = Join-Path $windowsFullPath "danmaku-player.exe"
if (Test-Path -LiteralPath $rustPlayerPath -PathType Leaf) {
    $rustVerifier = Join-Path $PSScriptRoot "verify-rust-player-release.ps1"
    if (-not (Test-Path -LiteralPath $rustVerifier -PathType Leaf)) {
        throw "Rust player release verifier does not exist: $rustVerifier"
    }
    & $rustVerifier -WindowsDistributionPath $windowsFullPath
    if ($LASTEXITCODE -ne 0) {
        throw "Packaged Rust player runtime verification failed."
    }
    Write-Host "Packaged Rust player runtime probe passed."
    exit 0
}

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
$previousProbeMedia = $env:DANMAKU_MPV_PROBE_MEDIA
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
        $env:DANMAKU_MPV_PROBE_MEDIA = $mediaFullPath
    } else {
        Remove-Item Env:\DANMAKU_MPV_PROBE_MEDIA -ErrorAction SilentlyContinue
    }
    & $javaCommand.Source @javaArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Packaged Windows mpv runtime probe failed with exit code $LASTEXITCODE."
    }
} finally {
    $env:DANMAKU_MPV_BRIDGE_PATH = $previousBridgePath
    $env:DANMAKU_LIBMPV_PATH = $previousLibmpvPath
    if ($null -eq $previousProbeMedia) {
        Remove-Item Env:\DANMAKU_MPV_PROBE_MEDIA -ErrorAction SilentlyContinue
    } else {
        $env:DANMAKU_MPV_PROBE_MEDIA = $previousProbeMedia
    }
}

Write-Host "Packaged Windows mpv runtime probe passed."
