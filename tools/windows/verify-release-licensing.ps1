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
    "run-danmaku.ps1",
    "dependencies\libmpv\install-libmpv-dependency.ps1",
    "dependencies\libmpv\zhongfly-lgpl-x86_64-20260604.json"
)) {
    Assert-File `
        -Path (Join-Path $windowsFullPath $requiredFile) `
        -Description "Windows release file '$requiredFile'"
}

$forbiddenDll = Get-ChildItem `
    -LiteralPath $windowsFullPath `
    -Recurse `
    -Filter "libmpv-2.dll" `
    -File `
    -ErrorAction SilentlyContinue |
    Select-Object -First 1
if ($null -ne $forbiddenDll) {
    throw "DLL-free Windows release must not contain libmpv-2.dll."
}

if (Test-Path -LiteralPath (Join-Path $windowsFullPath "runtime")) {
    throw "Runtime-free Windows release must not contain a bundled Java runtime."
}

Assert-ApkLegalAssets -ApkPath $MobileApkPath -Description "Android mobile APK"
Assert-ApkLegalAssets -ApkPath $TvApkPath -Description "Android TV APK"

Write-Host "Release licensing verification passed."
