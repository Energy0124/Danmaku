[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$OutputPath,
    [string]$Repository = "zhongfly/mpv-winbuild",
    [string]$ReleaseMetadataPath,
    [string]$GitHubToken = $env:GITHUB_TOKEN
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Assert-NonBlankProperty {
    param(
        [Parameter(Mandatory)]$Object,
        [Parameter(Mandatory)][string]$Name
    )

    if (
        $null -eq $Object.PSObject.Properties[$Name] -or
        [string]::IsNullOrWhiteSpace([string]$Object.$Name)
    ) {
        throw "Latest libmpv release property '$Name' must be a non-blank string."
    }
}

if ($Repository -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') {
    throw "GitHub repository must use owner/name format."
}

$releaseApiUrl = "https://api.github.com/repos/$Repository/releases/latest"
if ([string]::IsNullOrWhiteSpace($ReleaseMetadataPath)) {
    $headers = @{
        Accept = "application/vnd.github+json"
        "X-GitHub-Api-Version" = "2022-11-28"
        "User-Agent" = "Danmaku-libmpv-resolver"
    }
    if (-not [string]::IsNullOrWhiteSpace($GitHubToken)) {
        $headers.Authorization = "Bearer $GitHubToken"
    }
    $release = Invoke-RestMethod -Uri $releaseApiUrl -Headers $headers
} else {
    $metadataFullPath = [System.IO.Path]::GetFullPath($ReleaseMetadataPath)
    if (-not (Test-Path -LiteralPath $metadataFullPath -PathType Leaf)) {
        throw "Release metadata does not exist: $metadataFullPath"
    }
    $release = Get-Content -LiteralPath $metadataFullPath -Raw | ConvertFrom-Json
}

foreach ($property in @("tag_name", "html_url", "published_at")) {
    Assert-NonBlankProperty -Object $release -Name $property
}
if ([bool]$release.draft -or [bool]$release.prerelease) {
    throw "Latest libmpv resolution requires a published, non-prerelease GitHub release."
}

$assetPattern = '^mpv-dev-lgpl-x86_64-\d{8}-git-[0-9A-Fa-f]+\.7z$'
$assets = @(
    @($release.assets) | Where-Object {
        [string]$_.state -eq "uploaded" -and [string]$_.name -match $assetPattern
    }
)
if ($assets.Count -ne 1) {
    throw (
        "Latest release '$($release.tag_name)' must contain exactly one uploaded " +
        "mpv-dev-lgpl-x86_64 archive; found $($assets.Count)."
    )
}
$asset = $assets[0]
foreach ($property in @("name", "browser_download_url", "id")) {
    Assert-NonBlankProperty -Object $asset -Name $property
}
if ([string]$asset.digest -notmatch '^sha256:([0-9A-Fa-f]{64})$') {
    throw "Latest libmpv asset does not expose a valid GitHub SHA-256 digest."
}
$archiveSha256 = $Matches[1].ToLowerInvariant()

$archiveUri = [Uri][string]$asset.browser_download_url
$expectedPathPrefix = "/$Repository/releases/download/"
if (
    $archiveUri.Scheme -ne "https" -or
    $archiveUri.Host -ne "github.com" -or
    -not $archiveUri.AbsolutePath.StartsWith(
        $expectedPathPrefix,
        [System.StringComparison]::OrdinalIgnoreCase
    )
) {
    throw "Latest libmpv asset URL is not an HTTPS release asset for $Repository."
}

$releaseTarget = if (
    $null -ne $release.PSObject.Properties["target_commitish"] -and
    -not [string]::IsNullOrWhiteSpace([string]$release.target_commitish)
) {
    [string]$release.target_commitish
} else {
    "(not reported)"
}
$releasePublishedAt = ([DateTimeOffset]$release.published_at).ToUniversalTime().ToString("O")

$resolution = [ordered]@{
    schemaVersion = 2
    dependencyName = "libmpv-2.dll"
    distributionModel = "approved-direct-redistribution"
    selectionPolicy = "latest-stable-lgpl-x86_64"
    license = "LGPL-3.0-or-later"
    licenseUrl = "https://www.gnu.org/licenses/lgpl-3.0.html"
    projectUrl = "https://github.com/$Repository"
    releaseApiUrl = $releaseApiUrl
    releaseTag = [string]$release.tag_name
    releaseUrl = [string]$release.html_url
    releasePublishedAt = $releasePublishedAt
    releaseTarget = $releaseTarget
    assetId = [string]$asset.id
    archiveFileName = [string]$asset.name
    archiveUrl = [string]$asset.browser_download_url
    archiveSha256 = $archiveSha256
    dllArchivePath = "libmpv-2.dll"
    dllSha256 = $null
    approval = [ordered]@{
        status = "policy-approved"
        basis = (
            "The GitHub latest-release API returned one uploaded non-prerelease " +
            "mpv-dev-lgpl-x86_64 asset with a SHA-256 digest."
        )
        residualRisk = (
            "The producer states that it cannot guarantee every LGPL-incompatible " +
            "package has been disabled."
        )
    }
}

$destination = [System.IO.Path]::GetFullPath($OutputPath)
$parent = Split-Path -Parent $destination
if (-not [string]::IsNullOrWhiteSpace($parent)) {
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
}
[System.IO.File]::WriteAllText(
    $destination,
    ($resolution | ConvertTo-Json -Depth 6) + [Environment]::NewLine,
    [System.Text.UTF8Encoding]::new($false)
)
Write-Output $destination
