[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$AppArguments = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Read-JPackageConfig {
    param(
        [Parameter(Mandatory)]
        [string]$ConfigPath,
        [Parameter(Mandatory)]
        [string]$AppPath
    )

    $mainClass = $null
    $classPathEntries = @()
    $javaOptions = @()

    foreach ($line in Get-Content -LiteralPath $ConfigPath) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith("#")) {
            continue
        }

        if ($trimmed.StartsWith("app.mainclass=")) {
            $mainClass = $trimmed.Substring("app.mainclass=".Length)
            continue
        }

        if ($trimmed.StartsWith("app.classpath=")) {
            $classPathEntries += $trimmed.Substring("app.classpath=".Length).Replace('$APPDIR', $AppPath)
            continue
        }

        if ($trimmed.StartsWith("java-options=")) {
            $javaOptions += $trimmed.Substring("java-options=".Length).Replace('$APPDIR', $AppPath)
            continue
        }
    }

    if ([string]::IsNullOrWhiteSpace($mainClass)) {
        throw "JPackage config is missing app.mainclass: $ConfigPath"
    }
    if ($classPathEntries.Count -eq 0) {
        throw "JPackage config is missing app.classpath entries: $ConfigPath"
    }

    return [PSCustomObject]@{
        MainClass = $mainClass
        ClassPath = ($classPathEntries -join [System.IO.Path]::PathSeparator)
        JavaOptions = $javaOptions
    }
}

$javaCommand = Get-Command java.exe, javaw.exe -ErrorAction SilentlyContinue |
    Select-Object -First 1
if ($null -eq $javaCommand) {
    throw "Java 17 or newer is required. Install a Java runtime and ensure javaw.exe or java.exe is on PATH."
}

$appPath = Join-Path $PSScriptRoot "app"
if (-not (Test-Path -LiteralPath $appPath -PathType Container)) {
    throw "Danmaku application directory does not exist: $appPath"
}

$appConfigPath = Join-Path $appPath "desktop-windows.cfg"
if (-not (Test-Path -LiteralPath $appConfigPath -PathType Leaf)) {
    throw "Danmaku jpackage config does not exist: $appConfigPath"
}

$appConfig = Read-JPackageConfig -ConfigPath $appConfigPath -AppPath $appPath
$mpvBridgePath = Join-Path $appPath "player_windows_mpv.dll"
if (Test-Path -LiteralPath $mpvBridgePath -PathType Leaf) {
    $env:DANMAKU_MPV_BRIDGE_PATH = $mpvBridgePath
}
$libmpvPath = Join-Path $appPath "libmpv-2.dll"
if (Test-Path -LiteralPath $libmpvPath -PathType Leaf) {
    $env:DANMAKU_LIBMPV_PATH = $libmpvPath
}

$javaArgs = @()
$javaArgs += @($appConfig.JavaOptions)
$javaArgs += "-Djava.library.path=$appPath"
$javaArgs += "-cp"
$javaArgs += $appConfig.ClassPath
$javaArgs += $appConfig.MainClass
$javaArgs += $AppArguments

& $javaCommand.Source @javaArgs
exit $LASTEXITCODE
