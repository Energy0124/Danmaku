[CmdletBinding()]
param(
    [string]$ManifestPath,
    [string]$InstallPath,
    [string]$ArchivePath,
    [string]$SevenZipExecutable,
    [switch]$AcceptLicense
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

function Resolve-SevenZip {
    param([string]$ConfiguredPath)

    if (-not [string]::IsNullOrWhiteSpace($ConfiguredPath)) {
        $fullPath = Get-FullPath $ConfiguredPath
        if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
            throw "7-Zip executable does not exist: $fullPath"
        }
        return $fullPath
    }

    $command = Get-Command 7z.exe, 7z -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -eq $command) {
        throw "7-Zip is required to extract the pinned libmpv archive."
    }
    return $command.Source
}

function Resolve-ChildPath {
    param(
        [Parameter(Mandatory)][string]$RootPath,
        [Parameter(Mandatory)][string]$RelativePath
    )

    if (
        [string]::IsNullOrWhiteSpace($RelativePath) -or
        [System.IO.Path]::IsPathFullyQualified($RelativePath)
    ) {
        throw "Manifest archive path '$RelativePath' must be relative."
    }

    $fullPath = Get-FullPath (Join-Path $RootPath $RelativePath)
    $rootPrefix = $RootPath.TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar
    ) + [System.IO.Path]::DirectorySeparatorChar

    if (-not $fullPath.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Manifest archive path '$RelativePath' escapes the extraction directory."
    }
    return $fullPath
}

$packagedManifestPath = Join-Path $PSScriptRoot "zhongfly-lgpl-x86_64-20260708.json"
$repoManifestPath = Join-Path $PSScriptRoot (
    "..\..\third_party\windows\libmpv\zhongfly-lgpl-x86_64-20260708.json"
)
$isPackagedInstaller = Test-Path -LiteralPath $packagedManifestPath -PathType Leaf

if ([string]::IsNullOrWhiteSpace($ManifestPath)) {
    $ManifestPath = if ($isPackagedInstaller) {
        $packagedManifestPath
    } else {
        $repoManifestPath
    }
}
$manifestFullPath = Get-FullPath $ManifestPath
if (-not (Test-Path -LiteralPath $manifestFullPath -PathType Leaf)) {
    throw "libmpv dependency manifest does not exist: $manifestFullPath"
}

if ([string]::IsNullOrWhiteSpace($InstallPath)) {
    $InstallPath = if ($isPackagedInstaller) {
        Join-Path $PSScriptRoot "..\..\app"
    } else {
        Join-Path $PSScriptRoot "..\..\runtime\windows\libmpv"
    }
}
$installFullPath = Get-FullPath $InstallPath

$manifest = Get-Content -LiteralPath $manifestFullPath -Raw | ConvertFrom-Json
if ($manifest.schemaVersion -ne 1) {
    throw "Unsupported libmpv dependency manifest schemaVersion '$($manifest.schemaVersion)'."
}
@(
    "dependencyName",
    "distributionModel",
    "license",
    "licenseUrl",
    "projectUrl",
    "releaseUrl",
    "archiveFileName",
    "archiveUrl",
    "archiveSha256",
    "dllArchivePath",
    "dllSha256"
) | ForEach-Object {
    Assert-NonBlankProperty -Object $manifest -Name $_
}
Assert-Sha256 -Value $manifest.archiveSha256 -Description "archiveSha256"
Assert-Sha256 -Value $manifest.dllSha256 -Description "dllSha256"

if ($manifest.distributionModel -notin @(
    "optional-user-download",
    "approved-direct-redistribution"
)) {
    throw "Unsupported dependency distribution model '$($manifest.distributionModel)'."
}
if (-not $AcceptLicense) {
    throw "Review $($manifest.license) at $($manifest.licenseUrl), then rerun with -AcceptLicense."
}

$sevenZip = Resolve-SevenZip -ConfiguredPath $SevenZipExecutable
$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) (
    "danmaku-libmpv-install-" + [System.Guid]::NewGuid().ToString("N")
)

try {
    New-Item -ItemType Directory -Path $temporaryRoot -Force | Out-Null

    if ([string]::IsNullOrWhiteSpace($ArchivePath)) {
        $archiveFullPath = Join-Path $temporaryRoot $manifest.archiveFileName
        Write-Host "Downloading pinned dependency from $($manifest.archiveUrl)"
        try {
            Invoke-WebRequest -Uri $manifest.archiveUrl -OutFile $archiveFullPath -UseBasicParsing
        } catch {
            throw (
                "Could not download pinned libmpv archive from $($manifest.archiveUrl). " +
                "The upstream zhongfly/mpv-winbuild release probably rotated or was deleted, " +
                "and the libmpv pin needs updating. See docs/windows-libmpv-bundle.md. " +
                "Original error: $($_.Exception.Message)"
            )
        }
    } else {
        $archiveFullPath = Get-FullPath $ArchivePath
        if (-not (Test-Path -LiteralPath $archiveFullPath -PathType Leaf)) {
            throw "libmpv archive does not exist: $archiveFullPath"
        }
    }

    Assert-Hash `
        -Path $archiveFullPath `
        -ExpectedHash $manifest.archiveSha256 `
        -Description "archive '$($manifest.archiveFileName)'"

    $extractPath = Join-Path $temporaryRoot "extracted"
    New-Item -ItemType Directory -Path $extractPath -Force | Out-Null
    & $sevenZip x $archiveFullPath "-o$extractPath" -y $manifest.dllArchivePath | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "7-Zip failed to extract libmpv with exit code $LASTEXITCODE."
    }

    $dllPath = Resolve-ChildPath -RootPath $extractPath -RelativePath $manifest.dllArchivePath
    if (-not (Test-Path -LiteralPath $dllPath -PathType Leaf)) {
        throw "Pinned archive did not contain $($manifest.dllArchivePath)."
    }
    Assert-Hash `
        -Path $dllPath `
        -ExpectedHash $manifest.dllSha256 `
        -Description "extracted $($manifest.dependencyName)"

    New-Item -ItemType Directory -Path $installFullPath -Force | Out-Null
    $destinationPath = Join-Path $installFullPath $manifest.dependencyName
    Copy-Item -LiteralPath $dllPath -Destination $destinationPath -Force

    Write-Host "Installed $($manifest.dependencyName) dependency to $destinationPath"
    Write-Host "License: $($manifest.license) ($($manifest.licenseUrl))"
    Write-Host "Project: $($manifest.projectUrl)"
    Write-Host "Release: $($manifest.releaseUrl)"
} finally {
    $resolvedTemporaryRoot = Get-FullPath $temporaryRoot
    $systemTemporaryRoot = Get-FullPath ([System.IO.Path]::GetTempPath())
    if (
        $resolvedTemporaryRoot.StartsWith(
            $systemTemporaryRoot,
            [System.StringComparison]::OrdinalIgnoreCase
        ) -and
        (Test-Path -LiteralPath $resolvedTemporaryRoot)
    ) {
        Remove-Item -LiteralPath $resolvedTemporaryRoot -Recurse -Force
    }
}
