[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string[]]$MediaPath,

    [string]$WindowsDistributionPath = (
        Join-Path $PSScriptRoot "..\..\apps\desktop-windows\build\release\windows-portable"
    ),
    [string]$OutputDir,
    [ValidateRange(1, 300)]
    [int]$SmokeSeconds = 10,
    [switch]$SkipRuntimeProbe
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $repoRoot "build\qa\windows-playback"
}
$OutputDir = [System.IO.Path]::GetFullPath($OutputDir)
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$windowsFullPath = [System.IO.Path]::GetFullPath($WindowsDistributionPath)
$verifyScript = Join-Path $PSScriptRoot "verify-windows-mpv-runtime.ps1"
$smokeScript = Join-Path $PSScriptRoot "smoke-windows-playback.ps1"
foreach ($requiredScript in @($verifyScript, $smokeScript)) {
    if (-not (Test-Path -LiteralPath $requiredScript -PathType Leaf)) {
        throw "Required QA script does not exist: $requiredScript"
    }
}

$mediaFullPaths = @()
foreach ($path in $MediaPath) {
    $mediaFullPath = [System.IO.Path]::GetFullPath($path)
    if (-not (Test-Path -LiteralPath $mediaFullPath -PathType Leaf)) {
        throw "QA media file does not exist: $mediaFullPath"
    }
    $mediaFullPaths += $mediaFullPath
}
if ($mediaFullPaths.Count -eq 0) {
    throw "At least one media path is required."
}

$results = [System.Collections.Generic.List[object]]::new()

function ConvertTo-MarkdownCell {
    param([object]$Value)

    if ($null -eq $Value) {
        return ""
    }
    return ([string]$Value).Replace("`r", " ").Replace("`n", " ").Replace("|", "\|").Trim()
}

function Add-QaResult {
    param(
        [string]$Step,
        [string]$Target,
        [string]$Result,
        [string]$Detail
    )

    $null = $results.Add([pscustomobject]@{
        Step = $Step
        Target = $Target
        Result = $Result
        Detail = $Detail
    })
}

function Invoke-QaStep {
    param(
        [string]$Step,
        [string]$Target,
        [scriptblock]$Command
    )

    try {
        $output = & $Command 2>&1
        $detail = ($output | Out-String).Trim()
        if ([string]::IsNullOrWhiteSpace($detail)) {
            $detail = "completed"
        }
        Add-QaResult -Step $Step -Target $Target -Result "PASS" -Detail $detail
    } catch {
        Add-QaResult -Step $Step -Target $Target -Result "FAIL" -Detail $_.Exception.Message
    }
}

if (-not $SkipRuntimeProbe) {
    Invoke-QaStep `
        -Step "Runtime probe" `
        -Target $windowsFullPath `
        -Command {
            & $verifyScript -WindowsDistributionPath $windowsFullPath -MediaPath $mediaFullPaths[0]
        }
}

foreach ($mediaFullPath in $mediaFullPaths) {
    Invoke-QaStep `
        -Step "Smoke playback" `
        -Target $mediaFullPath `
        -Command {
            & $smokeScript -WindowsDistributionPath $windowsFullPath -MediaPath $mediaFullPath -Seconds $SmokeSeconds
        }
}

$reportPath = Join-Path $OutputDir "windows-playback-release-qa.md"
$generatedAt = (Get-Date).ToString("o")
$failureCount = @($results | Where-Object { $_.Result -ne "PASS" }).Count
$automatedResult = if ($failureCount -eq 0) { "PASS" } else { "FAIL" }
$reportLines = [System.Collections.Generic.List[string]]::new()
$null = $reportLines.Add("# Windows Playback Release QA")
$null = $reportLines.Add("")
$null = $reportLines.Add("- Generated at: $generatedAt")
$null = $reportLines.Add("- Distribution path: $windowsFullPath")
$null = $reportLines.Add("- Smoke seconds per media: $SmokeSeconds")
$null = $reportLines.Add("- Media files: $($mediaFullPaths.Count)")
$null = $reportLines.Add("- Automated result: $automatedResult")
$null = $reportLines.Add("")
$null = $reportLines.Add("## Automated Checks")
$null = $reportLines.Add("")
$null = $reportLines.Add("| Step | Target | Result | Detail |")
$null = $reportLines.Add("| --- | --- | --- | --- |")
foreach ($result in $results) {
    $null = $reportLines.Add(
        "| $(ConvertTo-MarkdownCell $result.Step) | $(ConvertTo-MarkdownCell $result.Target) | $(ConvertTo-MarkdownCell $result.Result) | $(ConvertTo-MarkdownCell $result.Detail) |"
    )
}
$null = $reportLines.Add("")
$null = $reportLines.Add("## Manual Sign-Off Still Required")
$null = $reportLines.Add("")
$null = $reportLines.Add("- Fullscreen on/off from UI and keyboard shortcut: PASS/FAIL")
$null = $reportLines.Add("- Resize and aspect behavior while media is playing: PASS/FAIL")
$null = $reportLines.Add("- Seek, pause/resume, playback rate, audio track, and subtitle track controls: PASS/FAIL")
$null = $reportLines.Add("- Sidecar subtitle and danmaku overlay attachment: PASS/FAIL")
$null = $reportLines.Add("- 4K playback for at least two minutes: PASS/FAIL")
$null = $reportLines.Add("- Hardware decode status from mpv logs where supported: PASS/FAIL/NA")
$null = $reportLines.Add("- Multi-display fullscreen and resize behavior: PASS/FAIL/NA")
$null = $reportLines.Add("- Relaunch resume position and recently watched state: PASS/FAIL")
[System.IO.File]::WriteAllLines($reportPath, $reportLines, [System.Text.UTF8Encoding]::new($false))

Write-Host "Windows playback release QA report: $reportPath"
if ($failureCount -gt 0) {
    throw "Windows playback release QA completed with $failureCount automated failure(s)."
}
Write-Host "Windows playback release QA automated checks passed."
