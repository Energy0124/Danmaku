[CmdletBinding()]
param(
    [string]$PackagePath,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$AppArguments = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$releaseRoot = Join-Path $repoRoot "build\release\rust-player"
$buildHint = "Build it first with build-rust-player.bat."

function Resolve-PlayerPackage {
    param([AllowNull()][string]$RequestedPath)

    if (-not [string]::IsNullOrWhiteSpace($RequestedPath)) {
        $candidate = [System.IO.Path]::GetFullPath($RequestedPath)
        if (-not (Test-Path -LiteralPath (Join-Path $candidate "danmaku-player.exe") -PathType Leaf)) {
            throw "No packaged player exists in ${candidate}. $buildHint"
        }
        return $candidate
    }

    if (-not (Test-Path -LiteralPath $releaseRoot -PathType Container)) {
        throw "No packaged player exists under ${releaseRoot}. $buildHint"
    }

    $package = Get-ChildItem -LiteralPath $releaseRoot -Directory |
        Where-Object { $_.Name -like "danmaku-player-*-windows-x64" } |
        Where-Object {
            Test-Path -LiteralPath (Join-Path $_.FullName "danmaku-player.exe") -PathType Leaf
        } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $package) {
        throw "No packaged player exists under ${releaseRoot}. $buildHint"
    }
    return $package.FullName
}

$resolvedPackagePath = Resolve-PlayerPackage -RequestedPath $PackagePath
$launcher = Join-Path $resolvedPackagePath "run-danmaku-player.ps1"
if (-not (Test-Path -LiteralPath $launcher -PathType Leaf)) {
    throw "Packaged player launcher does not exist: $launcher"
}

Write-Host "==> Running packaged player from $resolvedPackagePath"
& $launcher @AppArguments
exit $LASTEXITCODE
