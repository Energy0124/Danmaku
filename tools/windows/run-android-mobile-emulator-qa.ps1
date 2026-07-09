[CmdletBinding()]
param(
    [string]$AndroidSdkPath,
    [string]$OutputDir,
    [string]$PhoneAvd = "Pixel_3a_API_34_extension_level_7_x86_64",
    [string]$TabletAvd = "Danmaku_Tablet_API_34",
    [int]$BootTimeoutSeconds = 240,
    [int]$SettleSeconds = 3,
    [switch]$SkipConnectedTests,
    [switch]$SkipScreenshots,
    [switch]$KeepEmulators,
    [switch]$RustServer,
    [int]$RustServerPort = 18687,
    [string]$RustServerPairingToken = "123456"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $repoRoot "build\qa\android-mobile"
}
$OutputDir = [System.IO.Path]::GetFullPath($OutputDir)
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

if ([string]::IsNullOrWhiteSpace($AndroidSdkPath)) {
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
        $AndroidSdkPath = $env:ANDROID_HOME
    } elseif (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT)) {
        $AndroidSdkPath = $env:ANDROID_SDK_ROOT
    } elseif (Test-Path -LiteralPath "W:\Android\Sdk" -PathType Container) {
        $AndroidSdkPath = "W:\Android\Sdk"
    } else {
        throw "Set -AndroidSdkPath or ANDROID_HOME/ANDROID_SDK_ROOT."
    }
}
$AndroidSdkPath = [System.IO.Path]::GetFullPath($AndroidSdkPath)

$adb = Join-Path $AndroidSdkPath "platform-tools\adb.exe"
$emulator = Join-Path $AndroidSdkPath "emulator\emulator.exe"
$gradle = Join-Path $repoRoot "gradlew.bat"
$rustServerExe = $null
$rustServerProcess = $null
$rustQaRoot = Join-Path $OutputDir "rust-server"
$rustFixtureRoot = Join-Path $rustQaRoot "fixture-library"
$rustDataDir = Join-Path $rustQaRoot "server-data"
$rustReportPath = Join-Path $OutputDir "android-mobile-rust-server-qa.md"
$rustHostMode = "n/a"
$rustCatalogItems = 0
$rustConnectivityResults = [System.Collections.Generic.List[string]]::new()
foreach ($tool in @($adb, $emulator, $gradle)) {
    if (-not (Test-Path -LiteralPath $tool -PathType Leaf)) {
        throw "Required tool does not exist: $tool"
    }
}

function Get-RunningAvdSerial {
    param([string]$AvdName)

    $deviceLines = & $adb devices |
        Select-String -Pattern "^emulator-\d+\s+device$" |
        ForEach-Object { $_.Line }
    foreach ($line in $deviceLines) {
        $serial = ($line -split "\s+")[0]
        $runningName = & $adb -s $serial emu avd name 2>$null | Select-Object -First 1
        if ($runningName -eq $AvdName) {
            return $serial
        }
    }
    return $null
}

function Wait-ForAvdBoot {
    param(
        [string]$AvdName,
        [int]$TimeoutSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $serial = $null
    do {
        Start-Sleep -Seconds 5
        $serial = Get-RunningAvdSerial -AvdName $AvdName
    } until ($serial -or (Get-Date) -gt $deadline)

    if (-not $serial) {
        & $adb devices
        throw "AVD did not appear as an adb device before timeout: $AvdName"
    }

    & $adb -s $serial wait-for-device
    do {
        Start-Sleep -Seconds 5
        $booted = (& $adb -s $serial shell getprop sys.boot_completed).Trim()
    } until ($booted -eq "1" -or (Get-Date) -gt $deadline)

    if ($booted -ne "1") {
        throw "AVD did not finish booting before timeout: $AvdName ($serial)"
    }

    return $serial
}

function Start-OrReuseAvd {
    param([string]$AvdName)

    $serial = Get-RunningAvdSerial -AvdName $AvdName
    if ($serial) {
        Write-Host "Reusing $AvdName on $serial"
        return @{ Serial = $serial; Started = $false }
    }

    Write-Host "Starting $AvdName"
    Start-Process -FilePath $emulator -ArgumentList @(
        "-avd", $AvdName,
        "-no-window",
        "-no-audio",
        "-no-snapshot",
        "-no-boot-anim",
        "-gpu", "swiftshader_indirect"
    ) -WindowStyle Hidden

    $serial = Wait-ForAvdBoot -AvdName $AvdName -TimeoutSeconds $BootTimeoutSeconds
    Write-Host "$AvdName booted on $serial"
    return @{ Serial = $serial; Started = $true }
}

function Invoke-GradleForSerial {
    param(
        [string]$Serial,
        [string[]]$GradleArgs,
        [string[]]$DisplayArgs = $null
    )

    Push-Location $repoRoot
    $previousSerial = $env:ANDROID_SERIAL
    try {
        $env:ANDROID_SERIAL = $Serial
        & $gradle @GradleArgs
        if ($LASTEXITCODE -ne 0) {
            if ($null -eq $DisplayArgs) {
                $DisplayArgs = $GradleArgs
            }
            throw "Gradle failed with exit code $LASTEXITCODE for ${Serial}: $($DisplayArgs -join ' ')"
        }
    } finally {
        $env:ANDROID_SERIAL = $previousSerial
        Pop-Location
    }
}

function Invoke-RequiredCommand {
    param(
        [scriptblock]$Command,
        [string]$FailureMessage
    )

    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$FailureMessage Exit code: $LASTEXITCODE"
    }
}

function Test-TcpPortAvailable {
    param([int]$Port)

    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
    try {
        $listener.Start()
        return $true
    } catch [System.Net.Sockets.SocketException] {
        return $false
    } finally {
        $listener.Stop()
    }
}

function Get-RustTargetDirectory {
    if ([string]::IsNullOrWhiteSpace($env:CARGO_TARGET_DIR)) {
        return Join-Path $repoRoot "target"
    }

    if ([System.IO.Path]::IsPathRooted($env:CARGO_TARGET_DIR)) {
        return [System.IO.Path]::GetFullPath($env:CARGO_TARGET_DIR)
    }

    [System.IO.Path]::GetFullPath((Join-Path $repoRoot $env:CARGO_TARGET_DIR))
}

function Get-RustLibraryServerExecutable {
    $targetDir = Get-RustTargetDirectory
    [System.IO.Path]::GetFullPath((Join-Path $targetDir "release\library-server.exe"))
}

function Stop-RustServer {
    param([System.Diagnostics.Process]$Process)

    if ($null -ne $Process -and -not $Process.HasExited) {
        & taskkill.exe /PID $Process.Id /T /F | Out-Null
        if ($LASTEXITCODE -ne 0 -and -not $Process.HasExited) {
            Stop-Process -Id $Process.Id -Force
        }
        $Process.WaitForExit()
    }
}

function New-RustServerFixtureLibrary {
    if (Test-Path -LiteralPath $rustQaRoot) {
        Remove-Item -LiteralPath $rustQaRoot -Recurse -Force
    }

    $showDir = Join-Path $rustFixtureRoot "QA Show"
    New-Item -ItemType Directory -Force -Path $showDir | Out-Null
    $mediaBytes = [byte[]](0, 1, 2, 3, 4, 5, 6, 7)
    [System.IO.File]::WriteAllBytes((Join-Path $showDir "Episode 01.mp4"), $mediaBytes)
    [System.IO.File]::WriteAllBytes((Join-Path $showDir "Episode 02.mkv"), $mediaBytes)
    Set-Content -LiteralPath (Join-Path $showDir "Episode 01.en.vtt") `
        -Value "WEBVTT`n`n00:00:00.000 --> 00:00:01.000`nHello from Android Rust QA`n" `
        -Encoding UTF8
    New-Item -ItemType Directory -Force -Path $rustDataDir | Out-Null
}

function Wait-ForRustServer {
    param(
        [string]$BaseUrl,
        [int]$Attempts = 60
    )

    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            $statusResponse = Invoke-WebRequest -Uri "$BaseUrl/api/server/status" -UseBasicParsing
            $catalogResponse = Invoke-WebRequest -Uri "$BaseUrl/api/library" -UseBasicParsing
            if ($statusResponse.StatusCode -eq 200 -and $catalogResponse.StatusCode -eq 200) {
                $status = $statusResponse.Content | ConvertFrom-Json
                $catalog = $catalogResponse.Content | ConvertFrom-Json
                if ($null -ne $catalog.items -and $catalog.items.Count -gt 0) {
                    return @{
                        HostMode = $status.hostMode
                        CatalogItems = $catalog.items.Count
                    }
                }
            }
        } catch {
        }
        Start-Sleep -Milliseconds 500
    }
    throw "Timed out waiting for Rust library server fixture catalog at $BaseUrl"
}

function Start-RustServerHost {
    if (-not (Test-TcpPortAvailable -Port $RustServerPort)) {
        throw "Port $RustServerPort is already in use. Pass -RustServerPort with a free port or stop the existing server."
    }

    New-RustServerFixtureLibrary

    Push-Location $repoRoot
    try {
        Invoke-RequiredCommand -Command {
            cargo build --release -p library-server
        } -FailureMessage "Rust library server build failed."
    } finally {
        Pop-Location
    }

    $script:rustServerExe = Get-RustLibraryServerExecutable
    if (-not (Test-Path -LiteralPath $rustServerExe -PathType Leaf)) {
        throw "Rust library server executable does not exist after build: $rustServerExe"
    }

    $arguments = @(
        "--data-dir", $rustDataDir,
        "--root", $rustFixtureRoot,
        "--port", "$RustServerPort",
        "--pairing-token", $RustServerPairingToken
    )
    $script:rustServerProcess = Start-Process `
        -FilePath $rustServerExe `
        -ArgumentList $arguments `
        -WorkingDirectory $repoRoot `
        -PassThru `
        -WindowStyle Hidden

    $baseUrl = "http://127.0.0.1:$RustServerPort"
    $readiness = Wait-ForRustServer -BaseUrl $baseUrl
    $script:rustHostMode = $readiness.HostMode
    $script:rustCatalogItems = $readiness.CatalogItems
    Write-Host "Rust library server ready at $baseUrl (hostMode=$rustHostMode, catalogItems=$rustCatalogItems)"
}

function Invoke-RustServerConnectivityTestForSerial {
    param(
        [string]$Serial,
        [string]$AvdName
    )

    $emulatorBaseUrl = "http://10.0.2.2:$RustServerPort"
    $testClass = "app.danmaku.mobile.RustServerParityInstrumentedTest"
    $gradleArgs = @(
        "--no-daemon",
        ":apps:android-mobile:connectedDebugAndroidTest",
        "-Pandroid.testInstrumentationRunnerArguments.class=$testClass",
        "-Pandroid.testInstrumentationRunnerArguments.danmakuServerBaseUrl=$emulatorBaseUrl",
        "-Pandroid.testInstrumentationRunnerArguments.danmakuPairingToken=$RustServerPairingToken"
    )
    $displayArgs = @(
        "--no-daemon",
        ":apps:android-mobile:connectedDebugAndroidTest",
        "-Pandroid.testInstrumentationRunnerArguments.class=$testClass",
        "-Pandroid.testInstrumentationRunnerArguments.danmakuServerBaseUrl=$emulatorBaseUrl",
        "-Pandroid.testInstrumentationRunnerArguments.danmakuPairingToken=<redacted>"
    )

    Invoke-GradleForSerial -Serial $Serial -GradleArgs $gradleArgs -DisplayArgs $displayArgs
    $rustConnectivityResults.Add("$AvdName ($Serial): PASS") | Out-Null
    Write-Host "Rust server connectivity instrumentation passed for $AvdName on $Serial"
}

function Write-RustServerReport {
    $baseUrl = "http://127.0.0.1:$RustServerPort"
    $emulatorBaseUrl = "http://10.0.2.2:$RustServerPort"
    $results = if ($rustConnectivityResults.Count -gt 0) {
        $rustConnectivityResults | ForEach-Object { "- Connectivity: $_" }
    } else {
        @("- Connectivity: SKIPPED")
    }
    $report = @(
        "# Android Mobile Rust Server Emulator QA",
        "",
        "- Host implementation: Rust library-server",
        "- Host mode: $rustHostMode",
        "- Host base URL: $baseUrl",
        "- Emulator base URL: $emulatorBaseUrl",
        "- Fixture catalog items: $rustCatalogItems"
    ) + $results + @(
        "",
        "Result: PASS"
    )
    Set-Content -LiteralPath $rustReportPath -Value ($report -join "`n") -Encoding UTF8
    Write-Host "Rust server report: $rustReportPath"
}

function Capture-MobileScreenshot {
    param(
        [string]$Serial,
        [string]$Name
    )

    $screenshotPath = Join-Path $OutputDir "$Name.png"
    & $adb -s $Serial shell am start `
        -a android.intent.action.MAIN `
        -c android.intent.category.LAUNCHER `
        -n app.danmaku.mobile/.MainActivity | Write-Host
    Start-Sleep -Seconds $SettleSeconds
    & $adb -s $Serial exec-out screencap -p > $screenshotPath
    if (-not (Test-Path -LiteralPath $screenshotPath -PathType Leaf)) {
        throw "Screenshot was not written: $screenshotPath"
    }
    Write-Host "Captured $screenshotPath"
}

function Invoke-MobileQaForAvd {
    param(
        [string]$AvdName,
        [string]$ScreenshotName
    )

    $startedDevice = Start-OrReuseAvd -AvdName $AvdName
    $serial = $startedDevice.Serial
    try {
        $size = (& $adb -s $serial shell wm size).Trim()
        $density = (& $adb -s $serial shell wm density).Trim()
        Write-Host "$AvdName is ready on $serial ($size, $density)"

        if (-not $SkipConnectedTests) {
            Invoke-GradleForSerial -Serial $serial -GradleArgs @(
                "--no-daemon",
                ":apps:android-mobile:connectedDebugAndroidTest"
            )
            if ($RustServer) {
                Invoke-RustServerConnectivityTestForSerial -Serial $serial -AvdName $AvdName
            }
        } elseif ($RustServer) {
            $rustConnectivityResults.Add("$AvdName ($serial): SKIPPED (-SkipConnectedTests)") | Out-Null
        }

        if (-not $SkipScreenshots) {
            Invoke-GradleForSerial -Serial $serial -GradleArgs @(
                "--no-daemon",
                ":apps:android-mobile:installDebug"
            )
            Capture-MobileScreenshot -Serial $serial -Name $ScreenshotName
        }
    } finally {
        if ($startedDevice.Started -and -not $KeepEmulators) {
            & $adb -s $serial emu kill | Write-Host
        }
    }
}

try {
    if ($RustServer) {
        Start-RustServerHost
    }

    Invoke-MobileQaForAvd -AvdName $PhoneAvd -ScreenshotName "danmaku-mobile-phone"
    Invoke-MobileQaForAvd -AvdName $TabletAvd -ScreenshotName "danmaku-mobile-tablet"

    if ($RustServer) {
        Write-RustServerReport
    }
} finally {
    if ($RustServer) {
        Stop-RustServer -Process $rustServerProcess
        if (Test-Path -LiteralPath $rustQaRoot) {
            Remove-Item -LiteralPath $rustQaRoot -Recurse -Force
        }
    }
}

Write-Host "Android mobile emulator QA complete. Output: $OutputDir"
