[CmdletBinding()]
param(
    [string]$ManifestPath,
    [string]$InstallPath,
    [string]$ArchivePath,
    [string]$ProvenancePath,
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
        throw "7-Zip is required to extract the resolved libmpv archive."
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

if (-not $AcceptLicense) {
    throw "Review the libmpv LGPL terms, then rerun with -AcceptLicense."
}

if ([string]::IsNullOrWhiteSpace($InstallPath)) {
    $InstallPath = Join-Path $PSScriptRoot "..\..\runtime\windows\libmpv"
}
$installFullPath = Get-FullPath $InstallPath
if ([string]::IsNullOrWhiteSpace($ProvenancePath)) {
    $ProvenancePath = Join-Path $installFullPath "libmpv-provenance.json"
}
$provenanceFullPath = Get-FullPath $ProvenancePath

$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) (
    "danmaku-libmpv-install-" + [System.Guid]::NewGuid().ToString("N")
)

try {
    New-Item -ItemType Directory -Path $temporaryRoot -Force | Out-Null

    if ([string]::IsNullOrWhiteSpace($ManifestPath)) {
        $ManifestPath = Join-Path $temporaryRoot "libmpv-resolution.json"
        & (Join-Path $PSScriptRoot "resolve-latest-libmpv-dependency.ps1") `
            -OutputPath $ManifestPath | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Latest libmpv dependency resolution failed."
        }
    }
    $manifestFullPath = Get-FullPath $ManifestPath
    if (-not (Test-Path -LiteralPath $manifestFullPath -PathType Leaf)) {
        throw "libmpv dependency manifest does not exist: $manifestFullPath"
    }

    $manifest = Get-Content -LiteralPath $manifestFullPath -Raw | ConvertFrom-Json
    if ($manifest.schemaVersion -ne 2) {
        throw "Unsupported libmpv dependency manifest schemaVersion '$($manifest.schemaVersion)'."
    }
    @(
        "dependencyName",
        "distributionModel",
        "selectionPolicy",
        "license",
        "licenseUrl",
        "projectUrl",
        "releaseTag",
        "releaseUrl",
        "archiveFileName",
        "archiveUrl",
        "archiveSha256",
        "dllArchivePath"
    ) | ForEach-Object {
        Assert-NonBlankProperty -Object $manifest -Name $_
    }
    Assert-Sha256 -Value $manifest.archiveSha256 -Description "archiveSha256"
    if ($manifest.distributionModel -ne "approved-direct-redistribution") {
        throw "Unsupported dependency distribution model '$($manifest.distributionModel)'."
    }
    if ($manifest.selectionPolicy -ne "latest-stable-lgpl-x86_64") {
        throw "Unsupported libmpv selection policy '$($manifest.selectionPolicy)'."
    }
    if (
        $null -eq $manifest.PSObject.Properties["approval"] -or
        [string]$manifest.approval.status -ne "policy-approved"
    ) {
        throw "Resolved libmpv dependency is not approved by the release selection policy."
    }

    if ([string]::IsNullOrWhiteSpace($ArchivePath)) {
        $archiveFullPath = Join-Path $temporaryRoot $manifest.archiveFileName
        Write-Host "Downloading resolved libmpv dependency from $($manifest.archiveUrl)"
        Invoke-WebRequest -Uri $manifest.archiveUrl -OutFile $archiveFullPath -UseBasicParsing
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

    $sevenZip = Resolve-SevenZip -ConfiguredPath $SevenZipExecutable
    $extractPath = Join-Path $temporaryRoot "extracted"
    New-Item -ItemType Directory -Path $extractPath -Force | Out-Null
    & $sevenZip x $archiveFullPath "-o$extractPath" -y $manifest.dllArchivePath | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "7-Zip failed to extract libmpv with exit code $LASTEXITCODE."
    }

    $dllPath = Resolve-ChildPath -RootPath $extractPath -RelativePath $manifest.dllArchivePath
    if (-not (Test-Path -LiteralPath $dllPath -PathType Leaf)) {
        throw "Resolved archive did not contain $($manifest.dllArchivePath)."
    }
    $dllHash = (Get-FileHash -LiteralPath $dllPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if (
        $null -ne $manifest.PSObject.Properties["dllSha256"] -and
        -not [string]::IsNullOrWhiteSpace([string]$manifest.dllSha256)
    ) {
        Assert-Sha256 -Value $manifest.dllSha256 -Description "dllSha256"
        if ($dllHash -ne [string]$manifest.dllSha256) {
            throw (
                "SHA-256 mismatch for extracted $($manifest.dependencyName): " +
                "expected $($manifest.dllSha256), got $dllHash."
            )
        }
    }

    New-Item -ItemType Directory -Path $installFullPath -Force | Out-Null
    $destinationPath = Join-Path $installFullPath $manifest.dependencyName
    Copy-Item -LiteralPath $dllPath -Destination $destinationPath -Force

    $manifest | Add-Member -NotePropertyName dllSha256 -NotePropertyValue $dllHash -Force
    $manifest | Add-Member `
        -NotePropertyName resolvedAtUtc `
        -NotePropertyValue ([DateTimeOffset]::UtcNow.ToString("O")) `
        -Force
    $provenanceParent = Split-Path -Parent $provenanceFullPath
    if (-not [string]::IsNullOrWhiteSpace($provenanceParent)) {
        New-Item -ItemType Directory -Path $provenanceParent -Force | Out-Null
    }
    [System.IO.File]::WriteAllText(
        $provenanceFullPath,
        ($manifest | ConvertTo-Json -Depth 8) + [Environment]::NewLine,
        [System.Text.UTF8Encoding]::new($false)
    )

    Write-Host "Installed $($manifest.dependencyName) to $destinationPath"
    Write-Host "Resolved release: $($manifest.releaseTag) ($($manifest.releaseUrl))"
    Write-Host "Archive SHA-256: $($manifest.archiveSha256)"
    Write-Host "DLL SHA-256: $dllHash"
    Write-Host "Provenance: $provenanceFullPath"
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
