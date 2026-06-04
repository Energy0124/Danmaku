[CmdletBinding()]
param(
    [string]$WindowsDistributionPath = (
        Join-Path $PSScriptRoot (
            "..\..\apps\desktop-windows\build\release\windows-portable"
        )
    ),
    [string]$MobileApkPath = (
        Join-Path $PSScriptRoot (
            "..\..\apps\android-mobile\build\outputs\apk\debug\android-mobile-debug.apk"
        )
    ),
    [string]$TvApkPath = (
        Join-Path $PSScriptRoot (
            "..\..\apps\android-tv\build\outputs\apk\debug\android-tv-debug.apk"
        )
    )
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-FullPath {
    param([Parameter(Mandatory)][string]$Path)

    return [System.IO.Path]::GetFullPath($Path)
}

function Assert-File {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Description
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "$Description does not exist: $Path"
    }
}

function Assert-Hash {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$ExpectedHash,
        [Parameter(Mandatory)][string]$Description
    )

    $actualHash = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
    if ($actualHash -ne $ExpectedHash) {
        throw "SHA-256 mismatch for ${Description}: expected $ExpectedHash, got $actualHash."
    }
}

function Assert-ApkLegalAssets {
    param(
        [Parameter(Mandatory)][string]$ApkPath,
        [Parameter(Mandatory)][string]$Description
    )

    $fullPath = Get-FullPath $ApkPath
    Assert-File -Path $fullPath -Description $Description

    $archive = [System.IO.Compression.ZipFile]::OpenRead($fullPath)
    try {
        $entryNames = $archive.Entries.FullName
        foreach ($requiredEntry in @(
            "assets/LICENSE",
            "assets/THIRD_PARTY_NOTICES.md",
            "assets/APACHE-2.0.txt",
            "assets/app/cash/licensee/artifacts.json"
        )) {
            if ($requiredEntry -notin $entryNames) {
                throw "$Description is missing $requiredEntry."
            }
        }
    } finally {
        $archive.Dispose()
    }
}

Add-Type -AssemblyName System.IO.Compression.FileSystem

$windowsFullPath = Get-FullPath $WindowsDistributionPath
if (-not (Test-Path -LiteralPath $windowsFullPath -PathType Container)) {
    throw "Windows desktop distributable does not exist: $windowsFullPath"
}

foreach ($requiredFile in @(
    "LICENSE",
    "THIRD_PARTY_NOTICES.md",
    "THIRD_PARTY_DEPENDENCIES.json",
    "licenses\APACHE-2.0.txt",
    "licenses\GPL-3.0.txt",
    "licenses\LGPL-2.1.txt",
    "licenses\LGPL-3.0.txt",
    "run-danmaku.ps1",
    "app\libmpv-2.dll",
    "dependencies\libmpv\install-libmpv-dependency.ps1",
    "dependencies\libmpv\zhongfly-lgpl-x86_64-20260604.json",
    "dependencies\libmpv\SOURCE.md"
)) {
    Assert-File `
        -Path (Join-Path $windowsFullPath $requiredFile) `
        -Description "Windows release file '$requiredFile'"
}

$manifestPath = Join-Path (
    $windowsFullPath
) "dependencies\libmpv\zhongfly-lgpl-x86_64-20260604.json"
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if ($manifest.distributionModel -ne "approved-direct-redistribution") {
    throw "Windows release libmpv manifest is not approved for direct redistribution."
}
if ($manifest.approval.status -ne "approved") {
    throw "Windows release libmpv manifest approval status is not approved."
}
Assert-Hash `
    -Path (Join-Path $windowsFullPath "app\libmpv-2.dll") `
    -ExpectedHash $manifest.dllSha256 `
    -Description "bundled libmpv-2.dll"

if (Test-Path -LiteralPath (Join-Path $windowsFullPath "runtime")) {
    throw "Runtime-free Windows release must not contain a bundled Java runtime."
}

Assert-ApkLegalAssets -ApkPath $MobileApkPath -Description "Android mobile APK"
Assert-ApkLegalAssets -ApkPath $TvApkPath -Description "Android TV APK"

Write-Host "Release licensing verification passed."
