[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Version,

    [Parameter(Mandatory = $true)]
    [int64]$VersionCode,

    [Parameter(Mandatory = $true)]
    [string]$Repository,

    [Parameter(Mandatory = $true)]
    [string]$MobileApkPath,

    [Parameter(Mandatory = $true)]
    [string]$TvApkPath,

    [string]$OutputDirectory = ".\build\release\android",

    [string]$ApkSignerPath,

    [string]$ApkAnalyzerPath,

    [switch]$RequireVerification
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if ($Version -notmatch '^(\d+)\.(\d+)\.(\d+)$') {
    throw "Android release versions must use X.Y.Z format."
}
$major = [int64]$Matches[1]
$minor = [int64]$Matches[2]
$patch = [int64]$Matches[3]
if ($minor -gt 999 -or $patch -gt 999) {
    throw "Android release minor and patch components must be at most 999."
}
$expectedVersionCode = ($major * 1000000) + ($minor * 1000) + $patch
if ($expectedVersionCode -le 0 -or $expectedVersionCode -gt 2100000000) {
    throw "The derived Android version code is outside Android's supported range."
}
if ($VersionCode -ne $expectedVersionCode) {
    throw "Android version code $VersionCode does not match the derived value $expectedVersionCode for $Version."
}
if ($Repository -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') {
    throw "Repository must use owner/name format."
}

function Resolve-RequiredFile([string]$Path, [string]$Label) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "$Label was not found at $Path."
    }
    return (Resolve-Path -LiteralPath $Path).Path
}

function Read-ApkMetadata([string]$Path) {
    $applicationId = (& $ApkAnalyzerPath manifest application-id $Path | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { throw "Could not read application ID from $Path." }
    $actualVersionCode = (& $ApkAnalyzerPath manifest version-code $Path | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { throw "Could not read version code from $Path." }
    $actualVersionName = (& $ApkAnalyzerPath manifest version-name $Path | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { throw "Could not read version name from $Path." }
    return [pscustomobject]@{
        ApplicationId = $applicationId
        VersionCode = [int64]$actualVersionCode
        VersionName = $actualVersionName
    }
}

function Read-ApkSignerDigest([string]$Path) {
    $output = & $ApkSignerPath verify --verbose --print-certs $Path | Out-String
    if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed for $Path." }
    if ($output -notmatch '(?im)^Signer #1 certificate SHA-256 digest:\s*([0-9a-f]{64})\s*$') {
        throw "Could not read the APK signing certificate digest from $Path."
    }
    return $Matches[1].ToLowerInvariant()
}

$mobileSource = Resolve-RequiredFile $MobileApkPath "Android mobile APK"
$tvSource = Resolve-RequiredFile $TvApkPath "Android TV APK"

if ($RequireVerification) {
    $ApkSignerPath = Resolve-RequiredFile $ApkSignerPath "apksigner"
    $ApkAnalyzerPath = Resolve-RequiredFile $ApkAnalyzerPath "apkanalyzer"
    $mobileMetadata = Read-ApkMetadata $mobileSource
    $tvMetadata = Read-ApkMetadata $tvSource
    if ($mobileMetadata.ApplicationId -ne "app.danmaku.mobile") {
        throw "Android mobile APK has unexpected application ID $($mobileMetadata.ApplicationId)."
    }
    if ($tvMetadata.ApplicationId -ne "app.danmaku.tv") {
        throw "Android TV APK has unexpected application ID $($tvMetadata.ApplicationId)."
    }
    foreach ($metadata in @($mobileMetadata, $tvMetadata)) {
        if ($metadata.VersionCode -ne $VersionCode -or $metadata.VersionName -ne $Version) {
            throw "Android APK version metadata must match $Version ($VersionCode)."
        }
    }
    $mobileSigner = Read-ApkSignerDigest $mobileSource
    $tvSigner = Read-ApkSignerDigest $tvSource
    if ($mobileSigner -ne $tvSigner) {
        throw "Android mobile and TV APKs must use the same release signing certificate."
    }
}

$outputPath = [System.IO.Path]::GetFullPath($OutputDirectory)
[System.IO.Directory]::CreateDirectory($outputPath) | Out-Null
$mobileName = "danmaku-android-mobile.apk"
$tvName = "danmaku-android-tv.apk"
$mobileOutput = Join-Path $outputPath $mobileName
$tvOutput = Join-Path $outputPath $tvName
Copy-Item -LiteralPath $mobileSource -Destination $mobileOutput -Force
Copy-Item -LiteralPath $tvSource -Destination $tvOutput -Force

function New-Target([string]$Kind, [string]$ApplicationId, [string]$Name, [string]$Path) {
    $item = Get-Item -LiteralPath $Path
    return [ordered]@{
        kind = $Kind
        applicationId = $ApplicationId
        assetName = $Name
        apkUrl = "https://github.com/$Repository/releases/download/v$Version/$Name"
        sha256 = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
        sizeBytes = $item.Length
    }
}

$manifest = [ordered]@{
    schemaVersion = 1
    release = [ordered]@{
        tag = "v$Version"
        versionName = $Version
        versionCode = $VersionCode
        pageUrl = "https://github.com/$Repository/releases/tag/v$Version"
    }
    apps = @(
        (New-Target "mobile" "app.danmaku.mobile" $mobileName $mobileOutput)
        (New-Target "tv" "app.danmaku.tv" $tvName $tvOutput)
    )
}
$manifestPath = Join-Path $outputPath "android-update.json"
$manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $manifestPath -Encoding utf8NoBOM

Write-Host "Prepared Android release assets in $outputPath"
Write-Host "Android mobile SHA-256: $($manifest.apps[0].sha256)"
Write-Host "Android TV SHA-256: $($manifest.apps[1].sha256)"
