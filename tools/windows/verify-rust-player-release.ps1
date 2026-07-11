[CmdletBinding()]
param(
    [string]$WindowsDistributionPath = (
        Join-Path $PSScriptRoot "..\..\build\release\rust-player\danmaku-player-0.1.0-windows-x64"
    ),
    [string]$ProbeExecutable
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$distributionPath = [System.IO.Path]::GetFullPath($WindowsDistributionPath)
if (-not (Test-Path -LiteralPath $distributionPath -PathType Container)) {
    throw "Rust player distribution does not exist: $distributionPath"
}

$playerPath = Join-Path $distributionPath "danmaku-player.exe"
$serverPath = Join-Path $distributionPath "library-server.exe"
$libmpvPath = Join-Path $distributionPath "libmpv-2.dll"
$launcherPath = Join-Path $distributionPath "run-danmaku-player.ps1"
$dependencyPath = Join-Path $distributionPath "dependencies\libmpv"
$manifestPath = Join-Path $dependencyPath "zhongfly-lgpl-x86_64-20260708.json"
$requiredFiles = @(
    $playerPath,
    $serverPath,
    $libmpvPath,
    $launcherPath,
    (Join-Path $distributionPath "LICENSE"),
    (Join-Path $distributionPath "THIRD_PARTY_NOTICES.md"),
    (Join-Path $distributionPath "RUST_CRATE_LICENSES.md"),
    (Join-Path $distributionPath "RUST_SERVER_CRATE_LICENSES.md"),
    (Join-Path $distributionPath "web\index.html"),
    (Join-Path $distributionPath "README.md"),
    $manifestPath,
    (Join-Path $dependencyPath "SOURCE.md"),
    (Join-Path $distributionPath "licenses\LGPL-3.0.txt")
)
foreach ($requiredPath in $requiredFiles) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Required Rust player release file does not exist: $requiredPath"
    }
}

$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if ($manifest.schemaVersion -ne 1) {
    throw "Unsupported libmpv dependency manifest schemaVersion '$($manifest.schemaVersion)'."
}
if ($manifest.approval.status -ne "approved") {
    throw "libmpv dependency manifest is not approved for redistribution."
}
if ([string]$manifest.dllSha256 -notmatch "^[0-9a-fA-F]{64}$") {
    throw "libmpv dependency manifest dllSha256 is invalid."
}
$actualDllHash = (Get-FileHash -LiteralPath $libmpvPath -Algorithm SHA256).Hash
if ($actualDllHash -ne $manifest.dllSha256) {
    throw "Packaged libmpv SHA-256 mismatch: expected $($manifest.dllSha256), got $actualDllHash."
}

foreach ($forbiddenPath in @(
    (Join-Path $distributionPath "runtime"),
    (Join-Path $distributionPath "app"),
    (Join-Path $distributionPath "player_windows_mpv.dll")
)) {
    if (Test-Path -LiteralPath $forbiddenPath) {
        throw "Rust-native release must not contain legacy runtime path: $forbiddenPath"
    }
}

$helpOutput = & $playerPath --help 2>&1
if ($LASTEXITCODE -ne 0 -or ($helpOutput | Out-String) -notmatch "Usage: danmaku-player") {
    throw "Packaged native player --help check failed."
}

$serverHelpOutput = & $serverPath --help 2>&1
if ($LASTEXITCODE -ne 0 -or ($serverHelpOutput | Out-String) -notmatch "Usage: library-server") {
    throw "Packaged library server --help check failed."
}

if (-not [string]::IsNullOrWhiteSpace($ProbeExecutable)) {
    $probePath = [System.IO.Path]::GetFullPath($ProbeExecutable)
    if (-not (Test-Path -LiteralPath $probePath -PathType Leaf)) {
        throw "mpv probe executable does not exist: $probePath"
    }
    $previousLibmpvPath = $env:DANMAKU_LIBMPV_PATH
    try {
        $env:DANMAKU_LIBMPV_PATH = $libmpvPath
        & $probePath
        if ($LASTEXITCODE -ne 0) {
            throw "Packaged libmpv probe failed with exit code $LASTEXITCODE."
        }
    } finally {
        if ($null -eq $previousLibmpvPath) {
            Remove-Item Env:\DANMAKU_LIBMPV_PATH -ErrorAction SilentlyContinue
        } else {
            $env:DANMAKU_LIBMPV_PATH = $previousLibmpvPath
        }
    }
}

Write-Host "Verified Rust-native player release at $distributionPath"
Write-Host "  player: $playerPath"
Write-Host "  server: $serverPath"
Write-Host "  libmpv SHA-256: $actualDllHash"

