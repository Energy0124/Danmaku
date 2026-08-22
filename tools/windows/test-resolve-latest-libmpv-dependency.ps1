[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$resolver = Join-Path $PSScriptRoot "resolve-latest-libmpv-dependency.ps1"
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) (
    "danmaku-libmpv-resolver-test-" + [System.Guid]::NewGuid().ToString("N")
)
$metadataPath = Join-Path $testRoot "release.json"
$outputPath = Join-Path $testRoot "resolution.json"

try {
    New-Item -ItemType Directory -Path $testRoot -Force | Out-Null
    $release = [ordered]@{
        tag_name = "2026-08-21-fixture"
        html_url = "https://github.com/zhongfly/mpv-winbuild/releases/tag/2026-08-21-fixture"
        published_at = "2026-08-21T00:00:00Z"
        target_commitish = "main"
        draft = $false
        prerelease = $false
        assets = @(
            [ordered]@{
                id = 12345
                name = "mpv-dev-lgpl-x86_64-20260821-git-abcdef1234.7z"
                state = "uploaded"
                digest = "sha256:" + ("a" * 64)
                browser_download_url = (
                    "https://github.com/zhongfly/mpv-winbuild/releases/download/" +
                    "2026-08-21-fixture/mpv-dev-lgpl-x86_64-20260821-git-abcdef1234.7z"
                )
            },
            [ordered]@{
                id = 12346
                name = "mpv-dev-x86_64-20260821-git-abcdef1234.7z"
                state = "uploaded"
                digest = "sha256:" + ("b" * 64)
                browser_download_url = "https://example.invalid/gpl.7z"
            },
            [ordered]@{
                id = 12347
                name = "mpv-dev-lgpl-x86_64-v3-20260821-git-abcdef1234.7z"
                state = "uploaded"
                digest = "sha256:" + ("c" * 64)
                browser_download_url = "https://example.invalid/v3.7z"
            }
        )
    }
    $release | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $metadataPath

    & $resolver -ReleaseMetadataPath $metadataPath -OutputPath $outputPath
    $resolution = Get-Content -LiteralPath $outputPath -Raw | ConvertFrom-Json
    if (
        $resolution.schemaVersion -ne 2 -or
        $resolution.selectionPolicy -ne "latest-stable-lgpl-x86_64" -or
        $resolution.archiveFileName -ne "mpv-dev-lgpl-x86_64-20260821-git-abcdef1234.7z" -or
        $resolution.archiveSha256 -ne ("a" * 64) -or
        $resolution.dllSha256 -ne $null
    ) {
        throw "Latest libmpv resolver produced an invalid resolution document."
    }

    $release.assets[0].digest = $null
    $release | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $metadataPath
    $digestRejected = $false
    try {
        & $resolver -ReleaseMetadataPath $metadataPath -OutputPath $outputPath
    } catch {
        $digestRejected = $_.Exception.Message -like "*valid GitHub SHA-256 digest*"
    }
    if (-not $digestRejected) {
        throw "Latest libmpv resolver accepted an asset without a SHA-256 digest."
    }

    $release.assets[0].digest = "sha256:" + ("a" * 64)
    $release.assets += [ordered]@{
        id = 99999
        name = "mpv-dev-lgpl-x86_64-20260821-git-fedcba9876.7z"
        state = "uploaded"
        digest = "sha256:" + ("d" * 64)
        browser_download_url = (
            "https://github.com/zhongfly/mpv-winbuild/releases/download/" +
            "2026-08-21-fixture/mpv-dev-lgpl-x86_64-20260821-git-fedcba9876.7z"
        )
    }
    $release | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $metadataPath
    $ambiguousRejected = $false
    try {
        & $resolver -ReleaseMetadataPath $metadataPath -OutputPath $outputPath
    } catch {
        $ambiguousRejected = $_.Exception.Message -like "*exactly one uploaded*"
    }
    if (-not $ambiguousRejected) {
        throw "Latest libmpv resolver accepted ambiguous matching assets."
    }

    Write-Host "Latest libmpv resolver self-test passed."
} finally {
    if (Test-Path -LiteralPath $testRoot) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force
    }
}
