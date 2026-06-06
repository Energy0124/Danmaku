[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$MediaPath,
    [string]$WindowsDistributionPath = (
        Join-Path $PSScriptRoot (
            "..\..\apps\desktop-windows\build\compose\binaries\main\app\desktop-windows"
        )
    ),
    [ValidateRange(1, 60)]
    [int]$Seconds = 6,
    [switch]$KeepOpen
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$windowsFullPath = [System.IO.Path]::GetFullPath($WindowsDistributionPath)
$mediaFullPath = [System.IO.Path]::GetFullPath($MediaPath)
if (-not (Test-Path -LiteralPath $mediaFullPath -PathType Leaf)) {
    throw "Smoke media file does not exist: $mediaFullPath"
}

$appExecutable = Join-Path $windowsFullPath "desktop-windows.exe"
if (-not (Test-Path -LiteralPath $appExecutable -PathType Leaf)) {
    throw "Windows desktop executable does not exist: $appExecutable"
}

$appPath = Join-Path $windowsFullPath "app"
foreach ($nativeName in @("player_windows_mpv.dll", "libmpv-2.dll")) {
    $nativePath = Join-Path $appPath $nativeName
    if (-not (Test-Path -LiteralPath $nativePath -PathType Leaf)) {
        throw "Required Windows mpv runtime dependency does not exist: $nativePath"
    }
}

$startedAt = Get-Date
$arguments = @(
    "--smoke-playback-media-env"
    "--smoke-playback-seconds"
    "$Seconds"
)
if ($KeepOpen) {
    $arguments += "--smoke-keep-open"
} else {
    $arguments += "--smoke-exit"
}

$processInfo = [System.Diagnostics.ProcessStartInfo]::new()
$processInfo.FileName = $appExecutable
$processInfo.UseShellExecute = $false
$processInfo.Environment["DANMAKU_SMOKE_PLAYBACK_MEDIA"] = $mediaFullPath
foreach ($argument in $arguments) {
    [void]$processInfo.ArgumentList.Add($argument)
}

$process = [System.Diagnostics.Process]::Start($processInfo)
if ($null -eq $process) {
    throw "Failed to start Windows desktop executable: $appExecutable"
}
if ($KeepOpen) {
    Write-Host "Started Windows playback smoke and left the app open: process $($process.Id)"
    exit 0
}

$timeoutMs = ($Seconds + 45) * 1000
if (-not $process.WaitForExit($timeoutMs)) {
    $process.Kill()
    throw "Windows playback smoke timed out after $timeoutMs ms."
}
if ($process.ExitCode -ne 0) {
    throw "Windows playback smoke app exited with code $($process.ExitCode)."
}

$logDirectory = Join-Path $env:LOCALAPPDATA "Danmaku\logs"
$latestLog = Get-ChildItem -LiteralPath $logDirectory -Filter "danmaku-*.log" -ErrorAction SilentlyContinue |
    Where-Object { $_.LastWriteTime -ge $startedAt.AddSeconds(-2) } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if ($null -eq $latestLog) {
    throw "Windows playback smoke did not create a Danmaku app log in $logDirectory"
}

$logText = Get-Content -LiteralPath $latestLog.FullName -Raw
if ($logText -notmatch "Smoke playback reached PLAYING") {
    throw "Windows playback smoke did not reach PLAYING. Log: $($latestLog.FullName)"
}
if ($logText -notmatch "Smoke playback complete: status=PLAYING; position=([1-9][0-9]*)ms") {
    throw "Windows playback smoke did not advance playback position. Log: $($latestLog.FullName)"
}

Write-Host "Windows playback smoke passed: $($latestLog.FullName)"
