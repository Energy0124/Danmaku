[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("MY_ANIME_LIST", "MAL", "MYANIMELIST", "BANGUMI", "BGM")]
    [string]$Provider,

    [Parameter(Mandatory = $true)]
    [long]$AnimeId,

    [string]$MyAnimeListAccessToken,
    [string]$BangumiAccessToken,
    [string]$BangumiBaseUrl = "https://api.bgm.tv/",
    [string]$BangumiUserAgent = "Danmaku/0.1 (https://github.com/Energy0124/Danmaku)",
    [string]$OutputDir,
    [switch]$AllowMissingEntry
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $repoRoot "build\qa\live-external-sync"
}
$OutputDir = [System.IO.Path]::GetFullPath($OutputDir)
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

if ($AnimeId -le 0) {
    throw "AnimeId must be positive."
}

$normalizedProvider = switch ($Provider.ToUpperInvariant()) {
    "MAL" { "MY_ANIME_LIST" }
    "MYANIMELIST" { "MY_ANIME_LIST" }
    "MY_ANIME_LIST" { "MY_ANIME_LIST" }
    "BGM" { "BANGUMI" }
    "BANGUMI" { "BANGUMI" }
    default { throw "Unsupported provider: $Provider" }
}

if ($normalizedProvider -eq "MY_ANIME_LIST") {
    if ([string]::IsNullOrWhiteSpace($MyAnimeListAccessToken)) {
        $MyAnimeListAccessToken = $env:DANMAKU_MYANIMELIST_ACCESS_TOKEN
    }
    if ([string]::IsNullOrWhiteSpace($MyAnimeListAccessToken)) {
        throw "Provide -MyAnimeListAccessToken or set DANMAKU_MYANIMELIST_ACCESS_TOKEN."
    }
} elseif ($normalizedProvider -eq "BANGUMI") {
    if ([string]::IsNullOrWhiteSpace($BangumiAccessToken)) {
        $BangumiAccessToken = $env:DANMAKU_BANGUMI_ACCESS_TOKEN
    }
    if ([string]::IsNullOrWhiteSpace($BangumiAccessToken)) {
        throw "Provide -BangumiAccessToken or set DANMAKU_BANGUMI_ACCESS_TOKEN."
    }
}

$reportProvider = $normalizedProvider.ToLowerInvariant()
$reportPath = Join-Path $OutputDir "readback-$reportProvider-$AnimeId.md"
$gradle = Join-Path $repoRoot "gradlew.bat"
if (-not (Test-Path -LiteralPath $gradle -PathType Leaf)) {
    throw "Gradle wrapper does not exist: $gradle"
}

Push-Location $repoRoot
$previousProvider = $env:DANMAKU_LIVE_EXTERNAL_SYNC_QA_PROVIDER
$previousAnimeId = $env:DANMAKU_LIVE_EXTERNAL_SYNC_QA_ANIME_ID
$previousReport = $env:DANMAKU_LIVE_EXTERNAL_SYNC_QA_REPORT
$previousExpectEntry = $env:DANMAKU_LIVE_EXTERNAL_SYNC_QA_EXPECT_ENTRY
$previousMalAccessToken = $env:DANMAKU_MYANIMELIST_ACCESS_TOKEN
$previousBangumiAccessToken = $env:DANMAKU_BANGUMI_ACCESS_TOKEN
$previousBangumiBaseUrl = $env:DANMAKU_BANGUMI_BASE_URL
$previousBangumiUserAgent = $env:DANMAKU_BANGUMI_USER_AGENT
try {
    $env:DANMAKU_LIVE_EXTERNAL_SYNC_QA_PROVIDER = $normalizedProvider
    $env:DANMAKU_LIVE_EXTERNAL_SYNC_QA_ANIME_ID = [string]$AnimeId
    $env:DANMAKU_LIVE_EXTERNAL_SYNC_QA_REPORT = $reportPath
    $env:DANMAKU_LIVE_EXTERNAL_SYNC_QA_EXPECT_ENTRY = if ($AllowMissingEntry) { "false" } else { "true" }

    if ($normalizedProvider -eq "MY_ANIME_LIST") {
        $env:DANMAKU_MYANIMELIST_ACCESS_TOKEN = $MyAnimeListAccessToken
        Remove-Item Env:\DANMAKU_BANGUMI_ACCESS_TOKEN -ErrorAction SilentlyContinue
    } else {
        $env:DANMAKU_BANGUMI_ACCESS_TOKEN = $BangumiAccessToken
        $env:DANMAKU_BANGUMI_BASE_URL = $BangumiBaseUrl
        $env:DANMAKU_BANGUMI_USER_AGENT = $BangumiUserAgent
        Remove-Item Env:\DANMAKU_MYANIMELIST_ACCESS_TOKEN -ErrorAction SilentlyContinue
    }

    & $gradle `
        "--no-daemon" `
        "--rerun-tasks" `
        ":shared:library-server-core:jvmTest" `
        "--tests" `
        "app.danmaku.provider.external.LiveExternalAnimeReadbackQaTest"
    if ($LASTEXITCODE -ne 0) {
        throw "Live external sync readback QA failed with exit code $LASTEXITCODE"
    }
} finally {
    if ($null -eq $previousProvider) {
        Remove-Item Env:\DANMAKU_LIVE_EXTERNAL_SYNC_QA_PROVIDER -ErrorAction SilentlyContinue
    } else {
        $env:DANMAKU_LIVE_EXTERNAL_SYNC_QA_PROVIDER = $previousProvider
    }
    if ($null -eq $previousAnimeId) {
        Remove-Item Env:\DANMAKU_LIVE_EXTERNAL_SYNC_QA_ANIME_ID -ErrorAction SilentlyContinue
    } else {
        $env:DANMAKU_LIVE_EXTERNAL_SYNC_QA_ANIME_ID = $previousAnimeId
    }
    if ($null -eq $previousReport) {
        Remove-Item Env:\DANMAKU_LIVE_EXTERNAL_SYNC_QA_REPORT -ErrorAction SilentlyContinue
    } else {
        $env:DANMAKU_LIVE_EXTERNAL_SYNC_QA_REPORT = $previousReport
    }
    if ($null -eq $previousExpectEntry) {
        Remove-Item Env:\DANMAKU_LIVE_EXTERNAL_SYNC_QA_EXPECT_ENTRY -ErrorAction SilentlyContinue
    } else {
        $env:DANMAKU_LIVE_EXTERNAL_SYNC_QA_EXPECT_ENTRY = $previousExpectEntry
    }
    if ($null -eq $previousMalAccessToken) {
        Remove-Item Env:\DANMAKU_MYANIMELIST_ACCESS_TOKEN -ErrorAction SilentlyContinue
    } else {
        $env:DANMAKU_MYANIMELIST_ACCESS_TOKEN = $previousMalAccessToken
    }
    if ($null -eq $previousBangumiAccessToken) {
        Remove-Item Env:\DANMAKU_BANGUMI_ACCESS_TOKEN -ErrorAction SilentlyContinue
    } else {
        $env:DANMAKU_BANGUMI_ACCESS_TOKEN = $previousBangumiAccessToken
    }
    if ($null -eq $previousBangumiBaseUrl) {
        Remove-Item Env:\DANMAKU_BANGUMI_BASE_URL -ErrorAction SilentlyContinue
    } else {
        $env:DANMAKU_BANGUMI_BASE_URL = $previousBangumiBaseUrl
    }
    if ($null -eq $previousBangumiUserAgent) {
        Remove-Item Env:\DANMAKU_BANGUMI_USER_AGENT -ErrorAction SilentlyContinue
    } else {
        $env:DANMAKU_BANGUMI_USER_AGENT = $previousBangumiUserAgent
    }
    Pop-Location
}

if (-not (Test-Path -LiteralPath $reportPath -PathType Leaf)) {
    throw "Live external sync readback QA report was not written: $reportPath"
}

Write-Host "Live external sync readback QA complete."
Write-Host "Report: $reportPath"