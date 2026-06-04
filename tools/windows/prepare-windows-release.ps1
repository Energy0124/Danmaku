[CmdletBinding()]
param(
    [string]$SourceDistributionPath = (
        Join-Path $PSScriptRoot (
            "..\..\apps\desktop-windows\build\compose\binaries\main\app\desktop-windows"
        )
    ),
    [string]$ReleasePath = (
        Join-Path $PSScriptRoot (
            "..\..\apps\desktop-windows\build\release\windows-portable"
        )
    )
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$sourceDistributionFullPath = [System.IO.Path]::GetFullPath($SourceDistributionPath)
if (-not (Test-Path -LiteralPath $sourceDistributionFullPath -PathType Container)) {
    throw "Windows desktop distributable does not exist: $sourceDistributionFullPath"
}

$sourceAppPath = Join-Path $sourceDistributionFullPath "app"
if (-not (Test-Path -LiteralPath $sourceAppPath -PathType Container)) {
    throw "Windows desktop application directory does not exist: $sourceAppPath"
}

$releaseFullPath = [System.IO.Path]::GetFullPath($ReleasePath)
$buildRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $repoRoot "apps\desktop-windows\build")
)
$buildRootPrefix = $buildRoot.TrimEnd(
    [System.IO.Path]::DirectorySeparatorChar,
    [System.IO.Path]::AltDirectorySeparatorChar
) + [System.IO.Path]::DirectorySeparatorChar
if (-not $releaseFullPath.StartsWith(
    $buildRootPrefix,
    [System.StringComparison]::OrdinalIgnoreCase
)) {
    throw "Windows release path must remain inside $buildRoot"
}

if (Test-Path -LiteralPath $releaseFullPath) {
    Remove-Item -LiteralPath $releaseFullPath -Recurse -Force
}

$dependencyPath = Join-Path $releaseFullPath "dependencies\libmpv"
$licensePath = Join-Path $releaseFullPath "licenses"
New-Item -ItemType Directory -Path $dependencyPath -Force | Out-Null
New-Item -ItemType Directory -Path $licensePath -Force | Out-Null
Copy-Item -LiteralPath $sourceAppPath -Destination $releaseFullPath -Recurse

Copy-Item -LiteralPath (Join-Path $repoRoot "LICENSE") -Destination $releaseFullPath -Force
Copy-Item `
    -LiteralPath (Join-Path $repoRoot "THIRD_PARTY_NOTICES.md") `
    -Destination $releaseFullPath `
    -Force
Copy-Item `
    -LiteralPath (Join-Path $repoRoot "third_party\licenses\APACHE-2.0.txt") `
    -Destination $licensePath `
    -Force
Copy-Item `
    -LiteralPath (
        Join-Path $repoRoot "apps\desktop-windows\build\reports\licensee\desktop\artifacts.json"
    ) `
    -Destination (Join-Path $releaseFullPath "THIRD_PARTY_DEPENDENCIES.json") `
    -Force
Copy-Item `
    -LiteralPath (Join-Path $repoRoot "tools\windows\run-danmaku.ps1") `
    -Destination $releaseFullPath `
    -Force
Copy-Item `
    -LiteralPath (Join-Path $repoRoot "tools\windows\install-libmpv-dependency.ps1") `
    -Destination $dependencyPath `
    -Force
Copy-Item `
    -LiteralPath (
        Join-Path $repoRoot "third_party\windows\libmpv\zhongfly-lgpl-x86_64-20260604.json"
    ) `
    -Destination $dependencyPath `
    -Force

$forbiddenDll = Get-ChildItem `
    -LiteralPath $releaseFullPath `
    -Recurse `
    -Filter "libmpv-2.dll" `
    -File `
    -ErrorAction SilentlyContinue |
    Select-Object -First 1
if ($null -ne $forbiddenDll) {
    throw "DLL-free Windows release must not contain libmpv-2.dll: $($forbiddenDll.FullName)"
}

if (Test-Path -LiteralPath (Join-Path $releaseFullPath "runtime")) {
    throw "Runtime-free Windows release must not contain a bundled Java runtime."
}

Write-Host "Prepared runtime-free, DLL-free Windows release at $releaseFullPath"
