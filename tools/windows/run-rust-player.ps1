[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$AppArguments = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$playerPath = Join-Path $PSScriptRoot "danmaku-player.exe"
$libmpvPath = Join-Path $PSScriptRoot "libmpv-2.dll"
foreach ($requiredPath in @($playerPath, $libmpvPath)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Required native player file does not exist: $requiredPath"
    }
}

$env:DANMAKU_LIBMPV_PATH = $libmpvPath
& $playerPath @AppArguments
exit $LASTEXITCODE

