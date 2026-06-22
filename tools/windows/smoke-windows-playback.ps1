[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$MediaPath,
    [string]$WindowsDistributionPath = (
        Join-Path $PSScriptRoot (
            "..\..\apps\desktop-windows\build\compose\binaries\main\app\desktop-windows"
        )
    ),
    [ValidateRange(1, 60)]
    [int]$Seconds = 6,
    [ValidateRange(30, 300)]
    [int]$StartupTimeoutSeconds = 120,
    [switch]$KeepOpen
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

function Get-SmokeLogs {
    param([datetime]$StartedAt)

    $logDirectory = Join-Path $env:LOCALAPPDATA "Danmaku\logs"
    Get-ChildItem -LiteralPath $logDirectory -Filter "danmaku-*.log" -ErrorAction SilentlyContinue |
        Where-Object { $_.CreationTime -ge $StartedAt.AddMilliseconds(-250) } |
        Sort-Object LastWriteTime -Descending
}

function Test-SmokeLogPassed {
    param(
        [System.IO.FileInfo]$LogFile,
        [string]$ExpectedMediaPath = ""
    )

    if ($null -eq $LogFile -or -not (Test-Path -LiteralPath $LogFile.FullName -PathType Leaf)) {
        return $false
    }
    try {
        $logText = [string](Get-Content -LiteralPath $LogFile.FullName -Raw)
    } catch {
        return $false
    }
    if (-not [string]::IsNullOrWhiteSpace($ExpectedMediaPath) -and -not $logText.Contains($ExpectedMediaPath)) {
        return $false
    }
    return $logText -match "Smoke playback reached PLAYING" -and
        $logText -match "Smoke playback complete: status=PLAYING; position=([1-9][0-9]*)ms"
}

function Find-PassedSmokeLog {
    param(
        [datetime]$StartedAt,
        [string]$ExpectedMediaPath
    )

    try {
        foreach ($logFile in @(Get-SmokeLogs -StartedAt $StartedAt)) {
            if (Test-SmokeLogPassed -LogFile $logFile -ExpectedMediaPath $ExpectedMediaPath) {
                return $logFile
            }
        }
    } catch {
        return $null
    }
    return $null
}

function Stop-SmokeProcess {
    param([System.Diagnostics.Process]$Process)

    if ($null -eq $Process -or $Process.HasExited) {
        return
    }
    try {
        & taskkill.exe /PID $Process.Id /T /F | Out-Null
        [void]$Process.WaitForExit(5000)
    } catch {
        try {
            $Process.Kill()
            [void]$Process.WaitForExit(5000)
        } catch {
            Write-Warning "Could not stop smoke playback process $($Process.Id): $($_.Exception.Message)"
        }
    }
}

function Stop-RecentJavaProcesses {
    param(
        [datetime]$StartedAt,
        [string]$ExpectedPath = ""
    )

    $cutoff = $StartedAt.AddSeconds(-1)
    Get-Process -Name java,javaw -ErrorAction SilentlyContinue |
        Where-Object {
            try {
                $_.StartTime -ge $cutoff -and
                    ([string]::IsNullOrWhiteSpace($ExpectedPath) -or $_.Path -eq $ExpectedPath)
            } catch {
                $false
            }
        } |
        ForEach-Object {
            try {
                Stop-Process -Id $_.Id -Force -ErrorAction Stop
            } catch {
                Write-Warning "Could not stop Java smoke process $($_.Id): $($_.Exception.Message)"
            }
        }
}

$windowsFullPath = [System.IO.Path]::GetFullPath($WindowsDistributionPath)
$mediaFullPath = [System.IO.Path]::GetFullPath($MediaPath)
if (-not (Test-Path -LiteralPath $mediaFullPath -PathType Leaf)) {
    throw "Smoke media file does not exist: $mediaFullPath"
}

$appExecutable = Join-Path $windowsFullPath "desktop-windows.exe"
$portableLauncher = Join-Path $windowsFullPath "run-danmaku.ps1"
$launchesAppImage = Test-Path -LiteralPath $appExecutable -PathType Leaf
$launchesPortable = -not $launchesAppImage -and (Test-Path -LiteralPath $portableLauncher -PathType Leaf)
if (-not $launchesAppImage -and -not $launchesPortable) {
    throw "Windows desktop executable or portable launcher does not exist under: $windowsFullPath"
}

$appPath = Join-Path $windowsFullPath "app"
$mpvBridgePath = Join-Path $appPath "player_windows_mpv.dll"
$libmpvPath = Join-Path $appPath "libmpv-2.dll"
foreach ($nativePath in @($mpvBridgePath, $libmpvPath)) {
    if (-not (Test-Path -LiteralPath $nativePath -PathType Leaf)) {
        throw "Required Windows mpv runtime dependency does not exist: $nativePath"
    }
}

$startedAt = Get-Date
$arguments = @(
    "--smoke-playback-media-env"
    "--smoke-playback-seconds"
    "$Seconds"
)
if ($KeepOpen) {
    $arguments += "--smoke-keep-open"
} else {
    $arguments += "--smoke-exit"
}

$processInfo = [System.Diagnostics.ProcessStartInfo]::new()
$processInfo.UseShellExecute = $false
$processInfo.WorkingDirectory = $windowsFullPath
$processInfo.Environment["DANMAKU_SMOKE_PLAYBACK_MEDIA"] = $mediaFullPath
$processInfo.Environment["DANMAKU_SMOKE_PLAYBACK_SECONDS"] = "$Seconds"
$processInfo.Environment["DANMAKU_MPV_BRIDGE_PATH"] = $mpvBridgePath
$processInfo.Environment["DANMAKU_LIBMPV_PATH"] = $libmpvPath

if ($launchesAppImage) {
    $processInfo.FileName = $appExecutable
} else {
    $appConfigPath = Join-Path $appPath "desktop-windows.cfg"
    if (-not (Test-Path -LiteralPath $appConfigPath -PathType Leaf)) {
        throw "Danmaku jpackage config does not exist: $appConfigPath"
    }
    $javaCommand = Get-Command java.exe, javaw.exe -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -eq $javaCommand) {
        throw "Java 17 or newer is required to launch runtime-free portable smoke playback."
    }
    $appConfig = Read-JPackageConfig -ConfigPath $appConfigPath -AppPath $appPath
    $processInfo.FileName = $javaCommand.Source
    foreach ($javaOption in @($appConfig.JavaOptions)) {
        [void]$processInfo.ArgumentList.Add($javaOption)
    }
    [void]$processInfo.ArgumentList.Add("-Djava.library.path=$appPath")
    [void]$processInfo.ArgumentList.Add("-cp")
    [void]$processInfo.ArgumentList.Add($appConfig.ClassPath)
    [void]$processInfo.ArgumentList.Add($appConfig.MainClass)
}
foreach ($argument in $arguments) {
    [void]$processInfo.ArgumentList.Add($argument)
}

$process = [System.Diagnostics.Process]::Start($processInfo)
if ($null -eq $process) {
    throw "Failed to start Windows playback smoke process for: $windowsFullPath"
}
if ($KeepOpen) {
    Write-Host "Started Windows playback smoke and left the app open: process $($process.Id)"
    exit 0
}

$timeoutSeconds = $StartupTimeoutSeconds + $Seconds
$timeoutAt = (Get-Date).AddSeconds($timeoutSeconds)
$passedLog = $null
while ((Get-Date) -lt $timeoutAt) {
    $passedLog = Find-PassedSmokeLog -StartedAt $startedAt -ExpectedMediaPath $mediaFullPath
    if ($null -ne $passedLog) {
        break
    }
    Start-Sleep -Milliseconds 500
}

if ($null -eq $passedLog) {
    $latestLogs = @(Get-SmokeLogs -StartedAt $startedAt | Select-Object -First 1)
    $latestLog = if ($latestLogs.Count -gt 0) { $latestLogs[0] } else { $null }
    Stop-SmokeProcess -Process $process
    Stop-RecentJavaProcesses -StartedAt $startedAt -ExpectedPath $processInfo.FileName
    $latestLogDetail = if ($null -eq $latestLog) { "none" } else { $latestLog.FullName }
    throw "Windows playback smoke timed out after $($timeoutSeconds * 1000) ms. Latest log: $latestLogDetail"
}

if (-not $process.WaitForExit(10000)) {
    Stop-SmokeProcess -Process $process
    Stop-RecentJavaProcesses -StartedAt $startedAt -ExpectedPath $processInfo.FileName
}

Write-Host "Windows playback smoke passed: $($passedLog.FullName)"
