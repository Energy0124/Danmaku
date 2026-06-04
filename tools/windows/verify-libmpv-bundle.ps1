[CmdletBinding()]
param(
    [string]$BundlePath = (Join-Path $PSScriptRoot "..\..\runtime\windows\libmpv"),
    [string]$ManifestPath,
    [string]$SourceArchivePath,
    [string]$DistributionPath,
    [string]$ProbeExecutable
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-FullPath {
    param([Parameter(Mandatory)][string]$Path)

    return [System.IO.Path]::GetFullPath($Path)
}

function Assert-NonBlankProperty {
    param(
        [Parameter(Mandatory)]$Object,
        [Parameter(Mandatory)][string]$Name
    )

    if (
        $null -eq $Object.PSObject.Properties[$Name] -or
        [string]::IsNullOrWhiteSpace([string]$Object.$Name)
    ) {
        throw "Manifest property '$Name' must be a non-blank string."
    }
}

function Assert-Sha256 {
    param(
        [Parameter(Mandatory)][string]$Value,
        [Parameter(Mandatory)][string]$Description
    )

    if ($Value -notmatch "^[0-9a-fA-F]{64}$") {
        throw "$Description must be a 64-character SHA-256 hash."
    }
}

function Resolve-BundleFile {
    param(
        [Parameter(Mandatory)][string]$RootPath,
        [Parameter(Mandatory)][string]$RelativePath
    )

    if ([string]::IsNullOrWhiteSpace($RelativePath)) {
        throw "Manifest file paths must be non-blank."
    }
    if ([System.IO.Path]::IsPathFullyQualified($RelativePath)) {
        throw "Manifest file path '$RelativePath' must be relative to the bundle."
    }

    $fullPath = Get-FullPath (Join-Path $RootPath $RelativePath)
    $rootPrefix = $RootPath.TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar
    ) + [System.IO.Path]::DirectorySeparatorChar

    if (-not $fullPath.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Manifest file path '$RelativePath' escapes the bundle directory."
    }

    return $fullPath
}

$bundleFullPath = Get-FullPath $BundlePath
if (-not (Test-Path -LiteralPath $bundleFullPath -PathType Container)) {
    throw "libmpv bundle directory does not exist: $bundleFullPath"
}

if ([string]::IsNullOrWhiteSpace($ManifestPath)) {
    $ManifestPath = Join-Path $bundleFullPath "bundle-manifest.json"
}
$manifestFullPath = Get-FullPath $ManifestPath
if (-not (Test-Path -LiteralPath $manifestFullPath -PathType Leaf)) {
    throw "libmpv bundle manifest does not exist: $manifestFullPath"
}

$manifest = Get-Content -LiteralPath $manifestFullPath -Raw | ConvertFrom-Json
if ($manifest.schemaVersion -ne 1) {
    throw "Unsupported libmpv bundle manifest schemaVersion '$($manifest.schemaVersion)'."
}

@(
    "bundleName",
    "sourceUrl",
    "sourceArchiveSha256",
    "licenseMode"
) | ForEach-Object {
    Assert-NonBlankProperty -Object $manifest -Name $_
}
Assert-Sha256 -Value $manifest.sourceArchiveSha256 -Description "sourceArchiveSha256"

if (-not [string]::IsNullOrWhiteSpace($SourceArchivePath)) {
    $sourceArchiveFullPath = Get-FullPath $SourceArchivePath
    if (-not (Test-Path -LiteralPath $sourceArchiveFullPath -PathType Leaf)) {
        throw "Source archive does not exist: $sourceArchiveFullPath"
    }

    $actualArchiveHash = (Get-FileHash -LiteralPath $sourceArchiveFullPath -Algorithm SHA256).Hash
    if ($actualArchiveHash -ne $manifest.sourceArchiveSha256) {
        throw "SHA-256 mismatch for source archive: expected $($manifest.sourceArchiveSha256), got $actualArchiveHash."
    }
}

if ($null -eq $manifest.configurationFlags -or $manifest.configurationFlags.Count -eq 0) {
    throw "Manifest property 'configurationFlags' must contain at least one build flag."
}
foreach ($configurationFlag in $manifest.configurationFlags) {
    if ([string]::IsNullOrWhiteSpace([string]$configurationFlag)) {
        throw "Manifest property 'configurationFlags' must not contain blank entries."
    }
}
if ($null -eq $manifest.components -or $manifest.components.Count -eq 0) {
    throw "Manifest property 'components' must contain the bundled dependency inventory."
}
if ($null -eq $manifest.files -or $manifest.files.Count -eq 0) {
    throw "Manifest property 'files' must contain at least one redistributed file."
}
if ($null -eq $manifest.licenseFiles -or $manifest.licenseFiles.Count -eq 0) {
    throw "Manifest property 'licenseFiles' must contain at least one license or notice file."
}

$seenComponents = [System.Collections.Generic.HashSet[string]]::new(
    [System.StringComparer]::OrdinalIgnoreCase
)
foreach ($component in $manifest.components) {
    Assert-NonBlankProperty -Object $component -Name "name"
    Assert-NonBlankProperty -Object $component -Name "version"
    Assert-NonBlankProperty -Object $component -Name "license"

    if (-not $seenComponents.Add([string]$component.name)) {
        throw "Manifest component '$($component.name)' is duplicated."
    }
}
foreach ($requiredComponent in @("mpv", "FFmpeg")) {
    if (-not $seenComponents.Contains($requiredComponent)) {
        throw "Manifest component inventory must include $requiredComponent."
    }
}

$seenPaths = [System.Collections.Generic.HashSet[string]]::new(
    [System.StringComparer]::OrdinalIgnoreCase
)
$verifiedFiles = [System.Collections.Generic.List[object]]::new()

foreach ($file in $manifest.files) {
    Assert-NonBlankProperty -Object $file -Name "path"
    Assert-NonBlankProperty -Object $file -Name "sha256"
    Assert-Sha256 -Value $file.sha256 -Description "SHA-256 for '$($file.path)'"

    if (-not $seenPaths.Add([string]$file.path)) {
        throw "Manifest file path '$($file.path)' is duplicated."
    }

    $sourcePath = Resolve-BundleFile -RootPath $bundleFullPath -RelativePath $file.path
    if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
        throw "Manifest file does not exist: $sourcePath"
    }

    $actualHash = (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash
    if ($actualHash -ne $file.sha256) {
        throw "SHA-256 mismatch for '$($file.path)': expected $($file.sha256), got $actualHash."
    }

    $verifiedFiles.Add([PSCustomObject]@{
        RelativePath = [string]$file.path
        SourcePath = $sourcePath
    })
}

if (-not $seenPaths.Contains("libmpv-2.dll")) {
    throw "Manifest must include libmpv-2.dll at the bundle root."
}

foreach ($licenseFile in $manifest.licenseFiles) {
    if ([string]::IsNullOrWhiteSpace([string]$licenseFile)) {
        throw "Manifest property 'licenseFiles' must not contain blank entries."
    }
    if (-not $seenPaths.Contains([string]$licenseFile)) {
        throw "License file '$licenseFile' must also be listed in manifest.files."
    }
}

if (-not [string]::IsNullOrWhiteSpace($ProbeExecutable)) {
    $probeFullPath = Get-FullPath $ProbeExecutable
    if (-not (Test-Path -LiteralPath $probeFullPath -PathType Leaf)) {
        throw "mpv-probe executable does not exist: $probeFullPath"
    }

    $previousLibmpvPath = $env:DANMAKU_LIBMPV_PATH
    try {
        $env:DANMAKU_LIBMPV_PATH = Join-Path $bundleFullPath "libmpv-2.dll"
        & $probeFullPath
        if ($LASTEXITCODE -ne 0) {
            throw "mpv-probe failed with exit code $LASTEXITCODE."
        }
    } finally {
        $env:DANMAKU_LIBMPV_PATH = $previousLibmpvPath
    }
}

if (-not [string]::IsNullOrWhiteSpace($DistributionPath)) {
    $distributionFullPath = Get-FullPath $DistributionPath
    New-Item -ItemType Directory -Path $distributionFullPath -Force | Out-Null

    foreach ($file in $verifiedFiles) {
        $destinationPath = Resolve-BundleFile `
            -RootPath $distributionFullPath `
            -RelativePath $file.RelativePath
        $destinationDirectory = Split-Path -Parent $destinationPath
        New-Item -ItemType Directory -Path $destinationDirectory -Force | Out-Null
        Copy-Item -LiteralPath $file.SourcePath -Destination $destinationPath -Force
    }
}

Write-Host "Verified libmpv bundle '$($manifest.bundleName)' with $($verifiedFiles.Count) files."
if (-not [string]::IsNullOrWhiteSpace($DistributionPath)) {
    Write-Host "Copied verified libmpv files to $distributionFullPath"
}
