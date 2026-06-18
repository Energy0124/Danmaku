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
    [switch]$KeepEmulators
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
        [string[]]$GradleArgs
    )

    Push-Location $repoRoot
    $previousSerial = $env:ANDROID_SERIAL
    try {
        $env:ANDROID_SERIAL = $Serial
        & $gradle @GradleArgs
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle failed with exit code $LASTEXITCODE for ${Serial}: $($GradleArgs -join ' ')"
        }
    } finally {
        $env:ANDROID_SERIAL = $previousSerial
        Pop-Location
    }
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

Invoke-MobileQaForAvd -AvdName $PhoneAvd -ScreenshotName "danmaku-mobile-phone"
Invoke-MobileQaForAvd -AvdName $TabletAvd -ScreenshotName "danmaku-mobile-tablet"

Write-Host "Android mobile emulator QA complete. Output: $OutputDir"
