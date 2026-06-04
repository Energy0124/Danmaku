[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$verifier = Join-Path $PSScriptRoot "verify-libmpv-bundle.ps1"
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) (
    "danmaku-libmpv-verifier-" + [System.Guid]::NewGuid().ToString("N")
)
$bundlePath = Join-Path $testRoot "bundle"
$distributionPath = Join-Path $testRoot "distribution"
$sourceArchivePath = Join-Path $testRoot "libmpv-fixture.7z"

try {
    New-Item -ItemType Directory -Path $bundlePath -Force | Out-Null
    Set-Content -LiteralPath $sourceArchivePath -Value "fake archive"
    Set-Content -LiteralPath (Join-Path $bundlePath "libmpv-2.dll") -Value "fake dll"
    Set-Content -LiteralPath (Join-Path $bundlePath "LICENSE.LGPL") -Value "fake license"

    $sourceArchiveHash = (Get-FileHash -LiteralPath $sourceArchivePath -Algorithm SHA256).Hash
    $dllHash = (Get-FileHash -LiteralPath (Join-Path $bundlePath "libmpv-2.dll") -Algorithm SHA256).Hash
    $licenseHash = (Get-FileHash -LiteralPath (Join-Path $bundlePath "LICENSE.LGPL") -Algorithm SHA256).Hash
    $manifest = @{
        schemaVersion = 1
        bundleName = "CI verifier fixture"
        sourceUrl = "https://example.invalid/libmpv-fixture.7z"
        sourceArchiveSha256 = $sourceArchiveHash
        configurationFlags = @("-Dgpl=false")
        licenseMode = "LGPL-2.1-or-later"
        components = @(
            @{
                name = "mpv"
                version = "fixture"
                license = "LGPL-2.1-or-later"
            },
            @{
                name = "FFmpeg"
                version = "fixture"
                license = "LGPL-2.1-or-later"
            }
        )
        licenseFiles = @("LICENSE.LGPL")
        files = @(
            @{
                path = "libmpv-2.dll"
                sha256 = $dllHash
            },
            @{
                path = "LICENSE.LGPL"
                sha256 = $licenseHash
            }
        )
    }
    $manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (
        Join-Path $bundlePath "bundle-manifest.json"
    )

    & $verifier `
        -BundlePath $bundlePath `
        -SourceArchivePath $sourceArchivePath `
        -DistributionPath $distributionPath

    if (-not (Test-Path -LiteralPath (Join-Path $distributionPath "libmpv-2.dll") -PathType Leaf)) {
        throw "Verifier did not copy libmpv-2.dll to the distribution."
    }
    if (-not (Test-Path -LiteralPath (Join-Path $distributionPath "LICENSE.LGPL") -PathType Leaf)) {
        throw "Verifier did not copy the license file to the distribution."
    }

    Set-Content -LiteralPath (Join-Path $bundlePath "libmpv-2.dll") -Value "tampered dll"
    $tamperRejected = $false
    try {
        & $verifier -BundlePath $bundlePath
    } catch {
        $tamperRejected = $_.Exception.Message -like "SHA-256 mismatch*"
    }
    if (-not $tamperRejected) {
        throw "Verifier did not reject a tampered bundle file."
    }

    $manifest.files[0].path = "..\outside.dll"
    $manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (
        Join-Path $bundlePath "bundle-manifest.json"
    )
    $pathTraversalRejected = $false
    try {
        & $verifier -BundlePath $bundlePath
    } catch {
        $pathTraversalRejected = $_.Exception.Message -like "*escapes the bundle directory*"
    }
    if (-not $pathTraversalRejected) {
        throw "Verifier did not reject a path traversal entry."
    }

    Set-Content -LiteralPath $sourceArchivePath -Value "tampered archive"
    $archiveTamperRejected = $false
    try {
        & $verifier -BundlePath $bundlePath -SourceArchivePath $sourceArchivePath
    } catch {
        $archiveTamperRejected = $_.Exception.Message -like "SHA-256 mismatch for source archive*"
    }
    if (-not $archiveTamperRejected) {
        throw "Verifier did not reject a tampered source archive."
    }

    Write-Host "libmpv bundle verifier self-test passed."
} finally {
    if (Test-Path -LiteralPath $testRoot) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force
    }
}
