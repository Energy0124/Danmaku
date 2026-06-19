[CmdletBinding()]
param(
    [string]$LibraryRoot = "W:\Anime",
    [string]$SourceDatabase,
    [string]$OutputDir,
    [int]$MaxExamples = 12
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $repoRoot "build\qa\library-quality"
}
$OutputDir = [System.IO.Path]::GetFullPath($OutputDir)
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

if ([string]::IsNullOrWhiteSpace($SourceDatabase)) {
    if (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
        $SourceDatabase = Join-Path $env:LOCALAPPDATA "Danmaku\library.db"
    } else {
        $SourceDatabase = Join-Path $HOME ".danmaku\Danmaku\library.db"
    }
}
$SourceDatabase = [System.IO.Path]::GetFullPath($SourceDatabase)
if (-not (Test-Path -LiteralPath $SourceDatabase -PathType Leaf)) {
    throw "Desktop library database does not exist: $SourceDatabase"
}

$gradle = Join-Path $repoRoot "gradlew.bat"
if (-not (Test-Path -LiteralPath $gradle -PathType Leaf)) {
    throw "Gradle wrapper does not exist: $gradle"
}

$databaseCopy = Join-Path $OutputDir "library-quality-live-qa.db"
$reportPath = Join-Path $OutputDir "live-apply-mappings.md"
if (Test-Path -LiteralPath $databaseCopy -PathType Leaf) {
    Remove-Item -LiteralPath $databaseCopy
}
Copy-Item -LiteralPath $SourceDatabase -Destination $databaseCopy

$resolvedLibraryRoot = $null
if (-not [string]::IsNullOrWhiteSpace($LibraryRoot)) {
    if (Test-Path -LiteralPath $LibraryRoot -PathType Container) {
        $resolvedLibraryRoot = [System.IO.Path]::GetFullPath($LibraryRoot)
    } else {
        Write-Warning "Library root does not exist; live QA will use registered roots from the database copy: $LibraryRoot"
    }
}

Push-Location $repoRoot
$previousDatabase = $env:DANMAKU_LIVE_QA_DATABASE
$previousLibraryRoot = $env:DANMAKU_LIVE_QA_LIBRARY_ROOT
$previousReport = $env:DANMAKU_LIVE_QA_REPORT
$previousMaxExamples = $env:DANMAKU_LIVE_QA_MAX_EXAMPLES
try {
    $env:DANMAKU_LIVE_QA_DATABASE = $databaseCopy
    $env:DANMAKU_LIVE_QA_REPORT = $reportPath
    $env:DANMAKU_LIVE_QA_MAX_EXAMPLES = [string]$MaxExamples
    if ($resolvedLibraryRoot) {
        $env:DANMAKU_LIVE_QA_LIBRARY_ROOT = $resolvedLibraryRoot
    } else {
        Remove-Item Env:\DANMAKU_LIVE_QA_LIBRARY_ROOT -ErrorAction SilentlyContinue
    }

    & $gradle `
        "--no-daemon" `
        "--rerun-tasks" `
        ":apps:desktop-windows:desktopTest" `
        "--tests" `
        "app.danmaku.desktop.DesktopLibraryQualityLiveQaTest"
    if ($LASTEXITCODE -ne 0) {
        throw "Library Quality live QA failed with exit code $LASTEXITCODE"
    }
} finally {
    if ($null -eq $previousDatabase) {
        Remove-Item Env:\DANMAKU_LIVE_QA_DATABASE -ErrorAction SilentlyContinue
    } else {
        $env:DANMAKU_LIVE_QA_DATABASE = $previousDatabase
    }
    if ($null -eq $previousLibraryRoot) {
        Remove-Item Env:\DANMAKU_LIVE_QA_LIBRARY_ROOT -ErrorAction SilentlyContinue
    } else {
        $env:DANMAKU_LIVE_QA_LIBRARY_ROOT = $previousLibraryRoot
    }
    if ($null -eq $previousReport) {
        Remove-Item Env:\DANMAKU_LIVE_QA_REPORT -ErrorAction SilentlyContinue
    } else {
        $env:DANMAKU_LIVE_QA_REPORT = $previousReport
    }
    if ($null -eq $previousMaxExamples) {
        Remove-Item Env:\DANMAKU_LIVE_QA_MAX_EXAMPLES -ErrorAction SilentlyContinue
    } else {
        $env:DANMAKU_LIVE_QA_MAX_EXAMPLES = $previousMaxExamples
    }
    Pop-Location
}

if (-not (Test-Path -LiteralPath $reportPath -PathType Leaf)) {
    throw "Live QA report was not written: $reportPath"
}

Write-Host "Library Quality live QA complete."
Write-Host "Report: $reportPath"
Write-Host "Scratch database: $databaseCopy"
