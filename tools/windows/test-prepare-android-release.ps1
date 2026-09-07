[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$workspace = Join-Path ([System.IO.Path]::GetTempPath()) ("danmaku-android-release-test-" + [guid]::NewGuid())
[System.IO.Directory]::CreateDirectory($workspace) | Out-Null
try {
    $mobile = Join-Path $workspace "mobile.apk"
    $tv = Join-Path $workspace "tv.apk"
    [System.IO.File]::WriteAllText($mobile, "mobile-test-apk")
    [System.IO.File]::WriteAllText($tv, "tv-test-apk")
    $output = Join-Path $workspace "output"

    & (Join-Path $PSScriptRoot "prepare-android-release.ps1") `
        -Version "0.2.3" `
        -VersionCode 2003 `
        -Repository "Energy0124/Danmaku" `
        -MobileApkPath $mobile `
        -TvApkPath $tv `
        -OutputDirectory $output

    $manifestPath = Join-Path $output "android-update.json"
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
        throw "Android update manifest was not created."
    }
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    if ($manifest.schemaVersion -ne 1 -or $manifest.release.tag -ne "v0.2.3" -or $manifest.release.versionCode -ne 2003) {
        throw "Android update manifest release metadata is incorrect."
    }
    if ($manifest.apps.Count -ne 2) {
        throw "Android update manifest must contain mobile and TV targets."
    }
    foreach ($target in $manifest.apps) {
        $assetPath = Join-Path $output $target.assetName
        $actualHash = (Get-FileHash -LiteralPath $assetPath -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actualHash -ne $target.sha256 -or (Get-Item -LiteralPath $assetPath).Length -ne $target.sizeBytes) {
            throw "Android update manifest checksum/size did not match $($target.assetName)."
        }
        if ($target.apkUrl -notlike "https://github.com/Energy0124/Danmaku/releases/download/v0.2.3/*") {
            throw "Android update manifest contains an unexpected asset URL."
        }
    }

    $badVersionFailed = $false
    try {
        & (Join-Path $PSScriptRoot "prepare-android-release.ps1") `
            -Version "0.2.3" `
            -VersionCode 2004 `
            -Repository "Energy0124/Danmaku" `
            -MobileApkPath $mobile `
            -TvApkPath $tv `
            -OutputDirectory (Join-Path $workspace "bad")
    } catch {
        $badVersionFailed = $true
    }
    if (-not $badVersionFailed) {
        throw "Android release preparation accepted an inconsistent version code."
    }

    Write-Host "Android release preparation self-test passed."
} finally {
    if (Test-Path -LiteralPath $workspace) {
        Remove-Item -LiteralPath $workspace -Recurse -Force
    }
}
