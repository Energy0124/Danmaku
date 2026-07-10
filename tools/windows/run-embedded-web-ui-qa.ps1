[CmdletBinding()]
param(
    [int]$Port = 18697,
    [string]$PairingToken = "123456",
    [string]$OutputDir,
    [string]$RustServerPath,
    [switch]$SkipBrowserInteractionQa,
    [switch]$KeepDesktopOpen
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $repoRoot "build\qa\sidecar-web-ui"
}
$OutputDir = [System.IO.Path]::GetFullPath($OutputDir)
$fixtureRoot = Join-Path $OutputDir "fixture-library"
$appDataRoot = Join-Path $OutputDir "local-app-data"
$reportPath = Join-Path $OutputDir "sidecar-web-ui-qa.md"
$webUiDir = Join-Path $repoRoot "apps\web-ui"
$webDist = Join-Path $webUiDir "dist"
$browserQaScript = Join-Path $webUiDir "scripts\check-browser-interactions.mjs"
$gradle = Join-Path $repoRoot "gradlew.bat"

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

function Invoke-JsonRequest {
    param(
        [string]$Uri,
        [string]$Method = "GET",
        [object]$Body = $null
    )

    $parameters = @{
        Uri = $Uri
        Method = $Method
        UseBasicParsing = $true
        Headers = @{ Accept = "application/json" }
    }
    if ($null -ne $Body) {
        $parameters.Body = ($Body | ConvertTo-Json -Depth 8)
        $parameters.ContentType = "application/json; charset=utf-8"
    }
    Invoke-WebRequest @parameters
}

function Wait-ForServer {
    param(
        [string]$BaseUrl,
        [int]$Attempts = 120
    )

    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            $response = Invoke-WebRequest -Uri "$BaseUrl/api/server/status" -UseBasicParsing
            if ($response.StatusCode -eq 200) {
                return
            }
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }
    throw "Timed out waiting for Rust sidecar at $BaseUrl"
}

function Wait-ForCatalogItem {
    param(
        [string]$BaseUrl,
        [string]$Token,
        [int]$Attempts = 120
    )

    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            $catalog = (Invoke-JsonRequest -Uri "$BaseUrl/api/library?token=$Token").Content | ConvertFrom-Json
            if ($null -ne $catalog.items -and $catalog.items.Count -gt 0) {
                return $catalog
            }
        } catch {
        }
        Start-Sleep -Milliseconds 500
    }
    throw "Timed out waiting for embedded desktop fixture catalog at $BaseUrl"
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

function Get-BrowserExecutable {
    $candidates = @(
        $env:CHROME_PATH,
        "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
        "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
        "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe",
        "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe"
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }

    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return $candidate
        }
    }
    return $null
}

function Assert-ChildPath {
    param(
        [string]$ChildPath,
        [string]$ParentPath
    )

    $parentFull = [System.IO.Path]::GetFullPath($ParentPath).TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar
    ) + [System.IO.Path]::DirectorySeparatorChar
    $childFull = [System.IO.Path]::GetFullPath($ChildPath)
    if (-not $childFull.StartsWith($parentFull, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to modify path outside output directory: $childFull"
    }
}

function Remove-QADirectory {
    param([string]$Path)

    Assert-ChildPath -ChildPath $Path -ParentPath $OutputDir
    if (Test-Path -LiteralPath $Path) {
        Remove-Item -LiteralPath $Path -Recurse -Force
    }
}

function Stop-DesktopHost {
    param([System.Diagnostics.Process]$Process)

    if ($null -ne $Process -and -not $Process.HasExited) {
        & taskkill.exe /PID $Process.Id /T /F | Out-Null
        if ($LASTEXITCODE -ne 0 -and -not $Process.HasExited) {
            Stop-Process -Id $Process.Id -Force
        }
        $Process.WaitForExit()
    }
}

function Start-SidecarDesktopHost {
    param(
        [string]$FixtureRoot,
        [string]$LocalAppDataRoot,
        [string]$WebDist
    )

    $desktopArgs = @(
        "--server-port", "$Port",
        "--server-pairing-token", $PairingToken,
        "--web-assets-dir", "`"$WebDist`"",
        "--qa-library-root", "`"$FixtureRoot`"",
        "--initial-tab", "library"
    )
    if (-not [string]::IsNullOrWhiteSpace($RustServerPath)) {
        $desktopArgs += @("--rust-server-path", "`"$RustServerPath`"")
    }
    $desktopArgs = $desktopArgs -join " "

    $processInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $processInfo.FileName = $gradle
    $processInfo.WorkingDirectory = $repoRoot
    $processInfo.UseShellExecute = $false
    $processInfo.CreateNoWindow = $true
    $processInfo.Environment["LOCALAPPDATA"] = $LocalAppDataRoot
    [void]$processInfo.ArgumentList.Add("--no-daemon")
    [void]$processInfo.ArgumentList.Add(":apps:desktop-windows:run")
    [void]$processInfo.ArgumentList.Add("--args=$desktopArgs")

    $process = [System.Diagnostics.Process]::Start($processInfo)
    if ($null -eq $process) {
        throw "Failed to start desktop with the Rust sidecar."
    }
    return $process
}

if (-not (Test-Path -LiteralPath $gradle -PathType Leaf)) {
    throw "Gradle wrapper does not exist: $gradle"
}
if (-not (Test-Path -LiteralPath (Join-Path $webUiDir "package.json") -PathType Leaf)) {
    throw "Web UI package does not exist: $webUiDir"
}
if (-not (Test-Path -LiteralPath $browserQaScript -PathType Leaf)) {
    throw "Browser interaction QA script does not exist: $browserQaScript"
}
if (-not (Test-TcpPortAvailable -Port $Port)) {
    throw "Port $Port is already in use. Pass -Port with a free port or stop the existing server."
}

if ([string]::IsNullOrWhiteSpace($RustServerPath)) {
    $cargoTargetRoot = if ([string]::IsNullOrWhiteSpace($env:CARGO_TARGET_DIR)) {
        Join-Path $repoRoot "target"
    } else {
        [System.IO.Path]::GetFullPath($env:CARGO_TARGET_DIR)
    }
    $RustServerPath = Join-Path $cargoTargetRoot "release\library-server.exe"
}
$RustServerPath = [System.IO.Path]::GetFullPath($RustServerPath)
if (-not (Test-Path -LiteralPath $RustServerPath -PathType Leaf)) {
    throw "Rust sidecar executable does not exist: $RustServerPath. Run cargo build --release -p library-server first."
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
Remove-QADirectory -Path $fixtureRoot
Remove-QADirectory -Path $appDataRoot

$showDir = Join-Path $fixtureRoot "QA Show"
New-Item -ItemType Directory -Force -Path $showDir | Out-Null
[System.IO.File]::WriteAllBytes((Join-Path $showDir "Episode 01.mp4"), [byte[]](0, 0, 0, 24, 102, 116, 121, 112))
Set-Content -LiteralPath (Join-Path $showDir "Episode 01.en.vtt") -Value "WEBVTT`n`n00:00:00.000 --> 00:00:01.000`nHello from sidecar QA`n" -Encoding UTF8
New-Item -ItemType Directory -Force -Path $appDataRoot | Out-Null

Push-Location $webUiDir
try {
    Invoke-RequiredCommand -Command { npm run build } -FailureMessage "Web UI build failed."
} finally {
    Pop-Location
}

$desktopProcess = Start-SidecarDesktopHost -FixtureRoot $fixtureRoot -LocalAppDataRoot $appDataRoot -WebDist $webDist
$baseUrl = "http://127.0.0.1:$Port"

try {
    Wait-ForServer -BaseUrl $baseUrl

    $status = (Invoke-JsonRequest -Uri "$baseUrl/api/server/status").Content | ConvertFrom-Json
    $hostModeProperty = $status.PSObject.Properties["hostMode"]
    $hostMode = if ($null -eq $hostModeProperty) { "<missing>" } else { $hostModeProperty.Value }
    if ($hostMode -ne "headless-server") {
        throw "Expected headless Rust sidecar host mode but got: $hostMode"
    }
    if ($status.webUiAvailable -ne $true) {
        throw "Expected webUiAvailable=true"
    }

    $webIndex = Invoke-WebRequest -Uri "$baseUrl/web/" -UseBasicParsing
    if ($webIndex.StatusCode -ne 200 -or $webIndex.Content -notmatch "Danmaku") {
        throw "Sidecar Web UI index did not render expected shell content."
    }

    $catalog = Wait-ForCatalogItem -BaseUrl $baseUrl -Token $PairingToken
    $item = $catalog.items[0]
    if ($item.seriesTitle -ne "QA Show") {
        throw "Unexpected series title: $($item.seriesTitle)"
    }
    if ($item.subtitles.Count -ne 1) {
        throw "Expected one sidecar subtitle track."
    }

    $mediaHead = Invoke-WebRequest -Uri "$baseUrl$($item.streamPath)?token=$PairingToken" -Method Head -UseBasicParsing
    if ($mediaHead.StatusCode -ne 200) {
        throw "Media HEAD failed with HTTP $($mediaHead.StatusCode)"
    }

    $progress = @{
        mediaId = $item.id
        positionMs = 42000
        durationMs = 90000
        updatedAtEpochMs = 1234567890
    }
    $progressResponse = Invoke-JsonRequest -Uri "$baseUrl/api/progress/$($item.id)?token=$PairingToken" -Method PUT -Body $progress
    if ($progressResponse.StatusCode -ne 204) {
        throw "Progress PUT failed with HTTP $($progressResponse.StatusCode)"
    }

    $progressReadback = (Invoke-JsonRequest -Uri "$baseUrl/api/progress?token=$PairingToken").Content | ConvertFrom-Json
    $saved = @($progressReadback) | Where-Object { $_.mediaId -eq $item.id } | Select-Object -First 1
    if ($null -eq $saved -or $saved.positionMs -ne 42000) {
        throw "Progress readback did not contain the saved QA position."
    }

    $browserInteractionQa = "SKIPPED (disabled)"
    if (-not $SkipBrowserInteractionQa) {
        $browserExecutable = Get-BrowserExecutable
        if ($null -ne $browserExecutable) {
            Invoke-RequiredCommand -Command {
                node $browserQaScript --base-url $baseUrl --token $PairingToken --browser $browserExecutable --output-dir $OutputDir
            } -FailureMessage "Browser interaction QA failed."
            $browserInteractionQa = "PASS ($browserExecutable)"
        } else {
            $browserInteractionQa = "SKIPPED (Chrome/Edge not found)"
        }
    }

    $report = @(
        "# Rust Sidecar Web UI QA",
        "",
        "- Base URL: $baseUrl",
        "- Web UI: $baseUrl/web/",
        "- Isolated app data: $appDataRoot",
        "- Fixture library: $fixtureRoot",
        "- Catalog items: $($catalog.items.Count)",
        "- First item: $($item.seriesTitle) / $($item.episodeTitle)",
        "- Subtitle tracks: $($item.subtitles.Count)",
        "- Host mode: $hostMode",
        "- Browser interaction QA: $browserInteractionQa",
        "- Progress readback: $($saved.positionMs) ms",
        "",
        "Result: PASS"
    ) -join "`n"
    Set-Content -LiteralPath $reportPath -Value $report -Encoding UTF8
    Write-Host "Rust sidecar Web UI QA complete."
    Write-Host "Report: $reportPath"

    if ($KeepDesktopOpen) {
        Write-Host "Leaving desktop process open: $($desktopProcess.Id)"
        $desktopProcess = $null
    }
} finally {
    Stop-DesktopHost -Process $desktopProcess
}
