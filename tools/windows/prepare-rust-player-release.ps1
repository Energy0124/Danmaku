[CmdletBinding()]
param(
    [string]$ReleaseRoot = (Join-Path $PSScriptRoot "..\..\build\release\rust-player"),
    [string]$LibmpvPath = (Join-Path $PSScriptRoot "..\..\runtime\windows\libmpv\libmpv-2.dll"),
    [bool]$ProbeLibmpv = $true
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$releaseRootFullPath = [System.IO.Path]::GetFullPath($ReleaseRoot)
$allowedReleaseRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot "build\release\rust-player"))
$allowedPrefix = $allowedReleaseRoot.TrimEnd(
    [System.IO.Path]::DirectorySeparatorChar,
    [System.IO.Path]::AltDirectorySeparatorChar
) + [System.IO.Path]::DirectorySeparatorChar
if (
    $releaseRootFullPath -ne $allowedReleaseRoot -and
    -not $releaseRootFullPath.StartsWith($allowedPrefix, [System.StringComparison]::OrdinalIgnoreCase)
) {
    throw "Rust player release path must remain inside $allowedReleaseRoot"
}

function Get-RustHostTarget {
    $hostLine = & rustc -vV | Where-Object { $_ -like "host:*" } | Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace($hostLine)) {
        throw "Could not determine the Rust host target."
    }
    return $hostLine.Split(":", 2)[1].Trim()
}

function Get-CargoTargetDir {
    if (-not [string]::IsNullOrWhiteSpace($env:CARGO_TARGET_DIR)) {
        if ([System.IO.Path]::IsPathRooted($env:CARGO_TARGET_DIR)) {
            return [System.IO.Path]::GetFullPath($env:CARGO_TARGET_DIR)
        }
        return [System.IO.Path]::GetFullPath((Join-Path $repoRoot $env:CARGO_TARGET_DIR))
    }
    return Join-Path $repoRoot "target"
}

function Get-ReleasePackageIds {
    param(
        [Parameter(Mandatory = $true)]$Metadata,
        [Parameter(Mandatory = $true)][string]$RootPackageId
    )

    $nodesById = @{}
    foreach ($node in $Metadata.resolve.nodes) {
        $nodesById[[string]$node.id] = $node
    }
    if (-not $nodesById.ContainsKey($RootPackageId)) {
        throw "cargo metadata did not resolve the danmaku-player package."
    }

    $scopeById = @{}
    $directIds = @{}
    $queue = [System.Collections.Generic.Queue[string]]::new()
    $scopeById[$RootPackageId] = "package"
    foreach ($dependencyId in $nodesById[$RootPackageId].dependencies) {
        $key = [string]$dependencyId
        $directIds[$key] = $true
        $queue.Enqueue($key)
    }
    while ($queue.Count -gt 0) {
        $current = $queue.Dequeue()
        if ($scopeById.ContainsKey($current)) {
            continue
        }
        $scopeById[$current] = if ($directIds.ContainsKey($current)) { "direct" } else { "transitive" }
        if ($nodesById.ContainsKey($current)) {
            foreach ($dependencyId in $nodesById[$current].dependencies) {
                $queue.Enqueue([string]$dependencyId)
            }
        }
    }
    return $scopeById
}

function ConvertTo-MarkdownCell {
    param([AllowNull()][object]$Value)

    if ($null -eq $Value -or [string]::IsNullOrWhiteSpace([string]$Value)) {
        return "(not specified)"
    }
    return ([string]$Value).Replace("|", "\|").Replace([char]13, " ").Replace([char]10, " ")
}

function Write-RustCrateLicenses {
    param(
        [Parameter(Mandatory = $true)]$Metadata,
        [Parameter(Mandatory = $true)]$PlayerPackage,
        [Parameter(Mandatory = $true)][string]$DestinationPath
    )

    $scopeById = Get-ReleasePackageIds -Metadata $Metadata -RootPackageId ([string]$PlayerPackage.id)
    $packagesById = @{}
    foreach ($package in $Metadata.packages) {
        $packagesById[[string]$package.id] = $package
    }

    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add("# Rust Crate Licenses")
    $lines.Add("")
    $lines.Add("Generated from cargo metadata --locked for the danmaku-player release dependency graph.")
    $lines.Add("")
    $lines.Add("| Crate | Version | Scope | License | Source |")
    $lines.Add("| --- | --- | --- | --- | --- |")
    $rows = foreach ($packageId in $scopeById.Keys) {
        if ($packagesById.ContainsKey($packageId)) {
            $package = $packagesById[$packageId]
            [pscustomobject]@{
                Name = [string]$package.name
                Version = [string]$package.version
                Scope = [string]$scopeById[$packageId]
                License = ConvertTo-MarkdownCell $package.license
                Source = ConvertTo-MarkdownCell $package.source
            }
        }
    }
    foreach ($row in ($rows | Sort-Object Name, Version, Scope)) {
        $lines.Add(
            "| $(ConvertTo-MarkdownCell $row.Name) | " +
            "$(ConvertTo-MarkdownCell $row.Version) | " +
            "$(ConvertTo-MarkdownCell $row.Scope) | " +
            "$($row.License) | $($row.Source) |"
        )
    }
    [System.IO.File]::WriteAllLines(
        $DestinationPath,
        $lines,
        [System.Text.UTF8Encoding]::new($false)
    )
}

function Write-PackageReadme {
    param(
        [Parameter(Mandatory = $true)][string]$DestinationPath,
        [Parameter(Mandatory = $true)][string]$Version
    )

    $readme = @'
# Danmaku Native Player __VERSION__

This package contains the Rust-native Danmaku Windows player. It is runtime
free: Java and the legacy JNA mpv bridge are not required.

## Run

Launch run-danmaku-player.ps1 or danmaku-player.exe. Starting without arguments
opens LAN discovery and library connection. For direct playback:

    .\run-danmaku-player.ps1 --media "D:\Anime\Episode 01.mkv"

Use --help for direct playback, danmaku, and QA options.

## Contents

- danmaku-player.exe: egui/libmpv player and LAN library client.
- libmpv-2.dll: pinned, separately licensed LGPL libmpv dependency.
- run-danmaku-player.ps1: launcher that selects the packaged libmpv.
- dependencies/libmpv/: pinned manifest and source provenance.
- RUST_CRATE_LICENSES.md: generated Rust dependency inventory.
- licenses/, LICENSE, and THIRD_PARTY_NOTICES.md: license texts and notices.

## Trust And Credentials

The player is intended for trusted LAN servers. Pairing tokens are session-only
and are not written to the preferences file. Server administration is handled
by the server's /web/ UI.
'@
    [System.IO.File]::WriteAllText(
        $DestinationPath,
        $readme.Replace("__VERSION__", $Version),
        [System.Text.UTF8Encoding]::new($false)
    )
}

$hostTarget = Get-RustHostTarget
Push-Location $repoRoot
try {
    $metadataJson = & cargo metadata --format-version 1 --locked --filter-platform $hostTarget
    if ($LASTEXITCODE -ne 0) {
        throw "cargo metadata failed."
    }
} finally {
    Pop-Location
}
$metadata = $metadataJson | ConvertFrom-Json
$playerPackage = $metadata.packages | Where-Object { $_.name -eq "danmaku-player" } | Select-Object -First 1
if ($null -eq $playerPackage) {
    throw "Cargo package 'danmaku-player' was not found."
}
$version = [string]$playerPackage.version
$packageName = "danmaku-player-$version-windows-x64"
$stagePath = Join-Path $releaseRootFullPath $packageName
$zipPath = Join-Path $releaseRootFullPath "$packageName.zip"

Push-Location $repoRoot
try {
    & cargo build --release -p danmaku-player
    if ($LASTEXITCODE -ne 0) {
        throw "danmaku-player release build failed."
    }
    & cargo build --release -p player-windows-mpv --bin mpv-probe
    if ($LASTEXITCODE -ne 0) {
        throw "mpv-probe release build failed."
    }
} finally {
    Pop-Location
}

$cargoTargetDir = Get-CargoTargetDir
$playerExecutable = Join-Path $cargoTargetDir "release\danmaku-player.exe"
$probeExecutable = Join-Path $cargoTargetDir "release\mpv-probe.exe"
foreach ($requiredExecutable in @($playerExecutable, $probeExecutable)) {
    if (-not (Test-Path -LiteralPath $requiredExecutable -PathType Leaf)) {
        throw "Rust release executable does not exist: $requiredExecutable"
    }
}

$manifestPath = Join-Path $repoRoot "third_party\windows\libmpv\zhongfly-lgpl-x86_64-20260708.json"
$sourcePath = Join-Path $repoRoot "third_party\windows\libmpv\SOURCE.md"
$libmpvFullPath = [System.IO.Path]::GetFullPath($LibmpvPath)
if (-not (Test-Path -LiteralPath $libmpvFullPath -PathType Leaf)) {
    & (Join-Path $repoRoot "tools\windows\install-libmpv-dependency.ps1") -ManifestPath $manifestPath -InstallPath (Split-Path -Parent $libmpvFullPath) -AcceptLicense
    if ($LASTEXITCODE -ne 0) {
        throw "Pinned libmpv installation failed."
    }
}

$dependencyManifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if ($dependencyManifest.approval.status -ne "approved") {
    throw "Pinned libmpv dependency is not approved for redistribution."
}
$actualDllHash = (Get-FileHash -LiteralPath $libmpvFullPath -Algorithm SHA256).Hash
if ($actualDllHash -ne $dependencyManifest.dllSha256) {
    throw "Pinned libmpv SHA-256 mismatch: expected $($dependencyManifest.dllSha256), got $actualDllHash."
}

if (Test-Path -LiteralPath $stagePath) {
    Remove-Item -LiteralPath $stagePath -Recurse -Force
}
if (Test-Path -LiteralPath $zipPath) {
    Remove-Item -LiteralPath $zipPath -Force
}
$dependencyStagePath = Join-Path $stagePath "dependencies\libmpv"
$licenseStagePath = Join-Path $stagePath "licenses"
New-Item -ItemType Directory -Path $dependencyStagePath -Force | Out-Null
New-Item -ItemType Directory -Path $licenseStagePath -Force | Out-Null

Copy-Item -LiteralPath $playerExecutable -Destination $stagePath -Force
Copy-Item -LiteralPath $libmpvFullPath -Destination $stagePath -Force
Copy-Item -LiteralPath (Join-Path $repoRoot "tools\windows\run-rust-player.ps1") -Destination (Join-Path $stagePath "run-danmaku-player.ps1") -Force
Copy-Item -LiteralPath $manifestPath -Destination $dependencyStagePath -Force
Copy-Item -LiteralPath $sourcePath -Destination $dependencyStagePath -Force
Copy-Item -LiteralPath (Join-Path $repoRoot "LICENSE") -Destination $stagePath -Force
Copy-Item -LiteralPath (Join-Path $repoRoot "THIRD_PARTY_NOTICES.md") -Destination $stagePath -Force
foreach ($licenseFile in @("APACHE-2.0.txt", "GPL-3.0.txt", "LGPL-2.1.txt", "LGPL-3.0.txt")) {
    Copy-Item -LiteralPath (Join-Path $repoRoot "third_party\licenses\$licenseFile") -Destination $licenseStagePath -Force
}

Write-RustCrateLicenses -Metadata $metadata -PlayerPackage $playerPackage -DestinationPath (Join-Path $stagePath "RUST_CRATE_LICENSES.md")
Write-PackageReadme -DestinationPath (Join-Path $stagePath "README.md") -Version $version

$verifyArguments = @{ WindowsDistributionPath = $stagePath }
if ($ProbeLibmpv) {
    $verifyArguments.ProbeExecutable = $probeExecutable
}
& (Join-Path $repoRoot "tools\windows\verify-rust-player-release.ps1") @verifyArguments
if ($LASTEXITCODE -ne 0) {
    throw "Rust player release verification failed."
}

Write-Host "Package content manifest:"
Get-ChildItem -LiteralPath $stagePath -Recurse -File | Sort-Object FullName | ForEach-Object {
    $relativePath = [System.IO.Path]::GetRelativePath($stagePath, $_.FullName)
    Write-Host ("  {0} ({1} bytes)" -f $relativePath, $_.Length)
}

Compress-Archive -LiteralPath $stagePath -DestinationPath $zipPath -Force
Write-Host "Prepared Rust native player release zip: $zipPath"

