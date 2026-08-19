[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [string]$ChangelogPath = (Join-Path $PSScriptRoot "..\..\CHANGELOG.md"),
    [Parameter(Mandatory = $true)][string]$OutputPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ($Version -notmatch '^\d+\.\d+\.\d+([+-][0-9A-Za-z.-]+)?$') {
    throw "Release version is not SemVer: $Version"
}
$content = Get-Content -LiteralPath $ChangelogPath -Raw
$escaped = [Regex]::Escape($Version)
$match = [Regex]::Match(
    $content,
    "(?ms)^## \[$escaped\](?: - \d{4}-\d{2}-\d{2})?\s*\r?\n(?<notes>.*?)(?=^## \[|\z)"
)
if (-not $match.Success -or [string]::IsNullOrWhiteSpace($match.Groups['notes'].Value)) {
    throw "CHANGELOG.md does not contain release notes for version $Version."
}
$destination = [System.IO.Path]::GetFullPath($OutputPath)
$parent = Split-Path -Parent $destination
if (-not [string]::IsNullOrWhiteSpace($parent)) {
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
}
[System.IO.File]::WriteAllText(
    $destination,
    $match.Groups['notes'].Value.Trim() + [Environment]::NewLine,
    [System.Text.UTF8Encoding]::new($false)
)
Write-Output $destination
