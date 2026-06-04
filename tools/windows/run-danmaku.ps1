[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$javaCommand = Get-Command java.exe, javaw.exe -ErrorAction SilentlyContinue |
    Select-Object -First 1
if ($null -eq $javaCommand) {
    throw "Java 17 or newer is required. Install a Java runtime and ensure javaw.exe or java.exe is on PATH."
}

$appPath = Join-Path $PSScriptRoot "app"
if (-not (Test-Path -LiteralPath $appPath -PathType Container)) {
    throw "Danmaku application directory does not exist: $appPath"
}

$mainClass = "app.danmaku.desktop.MainKt"
$classPath = Join-Path $appPath "*"
$mpvBridgePath = Join-Path $appPath "player_windows_mpv.dll"
if (Test-Path -LiteralPath $mpvBridgePath -PathType Leaf) {
    $env:DANMAKU_MPV_BRIDGE_PATH = $mpvBridgePath
}
$libmpvPath = Join-Path $appPath "libmpv-2.dll"
if (Test-Path -LiteralPath $libmpvPath -PathType Leaf) {
    $env:DANMAKU_LIBMPV_PATH = $libmpvPath
}

& $javaCommand.Source `
    "-Djava.library.path=$appPath" `
    -cp $classPath `
    $mainClass

exit $LASTEXITCODE
