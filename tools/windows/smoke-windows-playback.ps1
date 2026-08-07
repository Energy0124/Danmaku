[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$MediaPath,
    [string]$DistributionPath,
    [ValidateRange(1, 60)]
    [int]$Seconds = 6,
    [ValidateRange(30, 300)]
    [int]$StartupTimeoutSeconds = 120,
    [switch]$KeepOpen
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
if ([string]::IsNullOrWhiteSpace($DistributionPath)) {
    $releaseRoot = Join-Path $repoRoot "build\release\rust-player"
    $package = Get-ChildItem -LiteralPath $releaseRoot -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like "danmaku-player-*-windows-x64" } |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName "danmaku-player.exe") -PathType Leaf } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $package) {
        throw "No packaged Rust player exists under $releaseRoot. Build it first with build-rust-player.bat."
    }
    $DistributionPath = $package.FullName
}

$distributionFullPath = [System.IO.Path]::GetFullPath($DistributionPath)
$mediaFullPath = [System.IO.Path]::GetFullPath($MediaPath)
if (-not (Test-Path -LiteralPath $mediaFullPath -PathType Leaf)) {
    throw "Smoke media file does not exist: $mediaFullPath"
}

$playerExecutable = Join-Path $distributionFullPath "danmaku-player.exe"
$libmpvPath = Join-Path $distributionFullPath "libmpv-2.dll"
foreach ($requiredPath in @($playerExecutable, $libmpvPath)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Required packaged Rust player file does not exist: $requiredPath"
    }
}

$processInfo = [System.Diagnostics.ProcessStartInfo]::new()
$processInfo.FileName = $playerExecutable
$processInfo.WorkingDirectory = $distributionFullPath
$processInfo.UseShellExecute = $false
$processInfo.Environment["DANMAKU_LIBMPV_PATH"] = $libmpvPath
[void]$processInfo.ArgumentList.Add("--media")
[void]$processInfo.ArgumentList.Add($mediaFullPath)
if (-not $KeepOpen) {
    [void]$processInfo.ArgumentList.Add("--smoke")
    [void]$processInfo.ArgumentList.Add([string]$Seconds)
    $processInfo.RedirectStandardOutput = $true
    $processInfo.RedirectStandardError = $true
}

$process = [System.Diagnostics.Process]::Start($processInfo)
if ($null -eq $process) {
    throw "Failed to start packaged Rust player smoke playback."
}
if ($KeepOpen) {
    Write-Host "Started Rust player playback and left it open: process $($process.Id)"
    exit 0
}

$timeoutMilliseconds = ($StartupTimeoutSeconds + $Seconds) * 1000
if (-not $process.WaitForExit($timeoutMilliseconds)) {
    if (-not $process.HasExited) {
        $process.Kill($true)
        $process.WaitForExit()
    }
    throw "Rust player smoke timed out after $timeoutMilliseconds ms."
}

$stdout = $process.StandardOutput.ReadToEnd()
$stderr = $process.StandardError.ReadToEnd()
if ($process.ExitCode -ne 0 -or $stdout -notmatch "danmaku-player smoke: PASS") {
    throw "Rust player smoke failed with exit code $($process.ExitCode). stdout=$stdout stderr=$stderr"
}

Write-Host $stdout.Trim()
Write-Host "Windows playback smoke passed for packaged Rust player."
