<#
.SYNOPSIS
Runs the repeatable headless web UI QA gate against the JVM headless server by
default, or against the Rust library server when -RustServer is supplied.
#>
[CmdletBinding()]
param(
    [int]$Port = 18686,
    [string]$PairingToken = "123456",
    [string]$OutputDir,
    [switch]$RustServer,
    [switch]$SkipBrowserInteractionQa
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $repoRoot "build\qa\headless-web-ui"
}
$OutputDir = [System.IO.Path]::GetFullPath($OutputDir)
$fixtureRoot = Join-Path $OutputDir "fixture-library"
$dataDir = Join-Path $OutputDir "server-data"
$reportPath = Join-Path $OutputDir "headless-web-ui-qa.md"
$webUiDir = Join-Path $repoRoot "apps\web-ui"
$webDist = Join-Path $webUiDir "dist"
$browserQaScript = Join-Path $webUiDir "scripts\check-browser-interactions.mjs"
$gradle = Join-Path $repoRoot "gradlew.bat"
$rustServerExe = $null
$hostImplementation = if ($RustServer) { "Rust library-server" } else { "JVM apps:library-server-windows" }

# Keep this fixture gate deterministic and prevent ignored local.properties or
# user-level provider credentials from leaking into the isolated host.
@(
    "DANMAKU_DANDANPLAY_BASE_URL",
    "DANMAKU_DANDANPLAY_PROXY_BASE_URL",
    "DANMAKU_DANDANPLAY_APP_ID",
    "DANMAKU_DANDANPLAY_APP_SECRET",
    "DANMAKU_DANDANPLAY_AUTHENTICATION_MODE",
    "DANMAKU_DANDANPLAY_CACHE_MAX_AGE_DAYS",
    "DANMAKU_MYANIMELIST_CLIENT_ID",
    "DANMAKU_MYANIMELIST_CLIENT_SECRET",
    "DANMAKU_MYANIMELIST_ACCESS_TOKEN",
    "DANMAKU_BANGUMI_BASE_URL",
    "DANMAKU_BANGUMI_USER_AGENT",
    "DANMAKU_BANGUMI_ACCESS_TOKEN"
) | ForEach-Object { Set-Item -Path "Env:$_" -Value "" }

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
        [int]$Attempts = 60
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
    throw "Timed out waiting for headless server at $BaseUrl"
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

function Get-RequiredJsonProperty {
    param(
        [object]$Object,
        [string]$Name
    )

    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) {
        throw "Expected JSON property '$Name'."
    }
    $property.Value
}

function Get-FirstItem {
    param([object]$Catalog)

    if ($null -eq $Catalog.items -or $Catalog.items.Count -lt 1) {
        throw "Catalog did not contain any media items."
    }
    $Catalog.items[0]
}

function Stop-HeadlessServer {
    param([System.Diagnostics.Process]$Process)

    if ($null -ne $Process -and -not $Process.HasExited) {
        & taskkill.exe /PID $Process.Id /T /F | Out-Null
        if ($LASTEXITCODE -ne 0 -and -not $Process.HasExited) {
            Stop-Process -Id $Process.Id -Force
        }
        $Process.WaitForExit()
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

function Quote-ProcessArgument {
    param([string]$Value)

    '"' + ($Value -replace '"', '\"') + '"'
}

function New-HeadlessServerCliArguments {
    param(
        [switch]$IncludeRoot,
        [switch]$IncludePairingToken,
        [switch]$QuotePathValues
    )

    $dataDirArgument = if ($QuotePathValues) { Quote-ProcessArgument $dataDir } else { $dataDir }
    $fixtureRootArgument = if ($QuotePathValues) { Quote-ProcessArgument $fixtureRoot } else { $fixtureRoot }
    $webDistArgument = if ($QuotePathValues) { Quote-ProcessArgument $webDist } else { $webDist }

    $arguments = @("--data-dir", $dataDirArgument)
    if ($IncludeRoot) {
        $arguments += @("--root", $fixtureRootArgument)
    }
    $arguments += @("--port", "$Port")
    if ($IncludePairingToken) {
        $arguments += @("--pairing-token", $PairingToken)
    }
    $arguments += @("--web-assets-dir", $webDistArgument)
    $arguments
}

function New-GradleRunArguments {
    param([string[]]$ServerArguments)

    @(
        "--no-daemon",
        ":apps:library-server-windows:run",
        "--args=`"$($ServerArguments -join " ")`""
    )
}

function Start-HeadlessServer {
    param([string[]]$ServerArguments)

    if ($RustServer) {
        return Start-Process -FilePath $rustServerExe -ArgumentList $ServerArguments -WorkingDirectory $repoRoot -PassThru -WindowStyle Hidden
    }

    $gradleArguments = New-GradleRunArguments -ServerArguments $ServerArguments
    Start-Process -FilePath $gradle -ArgumentList $gradleArguments -WorkingDirectory $repoRoot -PassThru -WindowStyle Hidden
}

if (-not $RustServer -and -not (Test-Path -LiteralPath $gradle -PathType Leaf)) {
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

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
if (Test-Path -LiteralPath $fixtureRoot) {
    Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
}
if (Test-Path -LiteralPath $dataDir) {
    Remove-Item -LiteralPath $dataDir -Recurse -Force
}

$showDir = Join-Path $fixtureRoot "QA Show"
New-Item -ItemType Directory -Force -Path $showDir | Out-Null
[System.IO.File]::WriteAllBytes((Join-Path $showDir "Episode 01.mp4"), [byte[]](0, 0, 0, 24, 102, 116, 121, 112))
Set-Content -LiteralPath (Join-Path $showDir "Episode 01.en.vtt") -Value "WEBVTT`n`n00:00:00.000 --> 00:00:01.000`nHello from QA`n" -Encoding UTF8

Push-Location $webUiDir
try {
    Invoke-RequiredCommand -Command { npm run build } -FailureMessage "Web UI build failed."
} finally {
    Pop-Location
}

if ($RustServer) {
    Push-Location $repoRoot
    try {
        Invoke-RequiredCommand -Command { cargo build --release -p library-server } -FailureMessage "Rust library server build failed."
    } finally {
        Pop-Location
    }
    $rustServerExe = Get-RustLibraryServerExecutable
    if (-not (Test-Path -LiteralPath $rustServerExe -PathType Leaf)) {
        throw "Rust library server executable does not exist after build: $rustServerExe"
    }
}

$firstStartArguments = New-HeadlessServerCliArguments -IncludeRoot -IncludePairingToken -QuotePathValues
$serverProcess = Start-HeadlessServer -ServerArguments $firstStartArguments
$baseUrl = "http://127.0.0.1:$Port"

try {
    Wait-ForServer -BaseUrl $baseUrl

    $status = (Invoke-JsonRequest -Uri "$baseUrl/api/server/status").Content | ConvertFrom-Json
    if ($status.hostMode -ne "headless-server") {
        throw "Expected headless-server host mode but got: $($status.hostMode)"
    }
    if ($status.webUiAvailable -ne $true) {
        throw "Expected webUiAvailable=true"
    }
    $providerSettings = Get-RequiredJsonProperty -Object $status -Name "providerSettings"
    if ($null -eq $providerSettings) {
        throw "Expected providerSettings summary in headless server status."
    }
    $dandanplayProvider = Get-RequiredJsonProperty -Object $providerSettings -Name "dandanplay"
    $dandanplayBaseUrl = Get-RequiredJsonProperty -Object $dandanplayProvider -Name "baseUrl"
    if ($dandanplayBaseUrl -ne "https://api.dandanplay.net") {
        throw "Unexpected dandanplay status base URL: $dandanplayBaseUrl"
    }
    $providerRuntime = (Invoke-JsonRequest -Uri "$baseUrl/api/providers/runtime?token=$PairingToken").Content | ConvertFrom-Json
    if ($providerRuntime.dandanplay.reasonCode -ne "missing-credentials") {
        throw "Unexpected dandanplay runtime reason: $($providerRuntime.dandanplay.reasonCode)"
    }
    if ($providerRuntime.bangumi.searchAvailable -ne $true) {
        throw "Expected Bangumi public search readiness."
    }

    $webIndex = Invoke-WebRequest -Uri "$baseUrl/web/" -UseBasicParsing
    if ($webIndex.StatusCode -ne 200 -or $webIndex.Content -notmatch "Danmaku") {
        throw "Web UI index did not render expected shell content."
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

    $catalog = (Invoke-JsonRequest -Uri "$baseUrl/api/library?token=$PairingToken").Content | ConvertFrom-Json
    $item = Get-FirstItem -Catalog $catalog
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

    Stop-HeadlessServer -Process $serverProcess
    $serverProcess = $null
    Start-Sleep -Milliseconds 500

    $restartArguments = New-HeadlessServerCliArguments -QuotePathValues
    $serverProcess = Start-HeadlessServer -ServerArguments $restartArguments
    Wait-ForServer -BaseUrl $baseUrl

    $restartCatalog = (Invoke-JsonRequest -Uri "$baseUrl/api/library?token=$PairingToken").Content | ConvertFrom-Json
    $restartItem = Get-FirstItem -Catalog $restartCatalog
    if ($restartItem.id -ne $item.id) {
        throw "Restart catalog did not preserve the expected first media item."
    }

    $restartMediaHead = Invoke-WebRequest -Uri "$baseUrl$($restartItem.streamPath)?token=$PairingToken" -Method Head -UseBasicParsing
    if ($restartMediaHead.StatusCode -ne 200) {
        throw "Restart media HEAD failed with HTTP $($restartMediaHead.StatusCode)"
    }

    $restartProgressReadback = (Invoke-JsonRequest -Uri "$baseUrl/api/progress?token=$PairingToken").Content | ConvertFrom-Json
    $restartSaved = @($restartProgressReadback) | Where-Object { $_.mediaId -eq $item.id } | Select-Object -First 1
    if ($null -eq $restartSaved -or $restartSaved.positionMs -ne 42000) {
        throw "Restart progress readback did not contain the saved QA position."
    }

    $report = @(
        "# Headless Web UI QA",
        "",
        "- Host implementation: $hostImplementation",
        "- Base URL: $baseUrl",
        "- Web UI: $baseUrl/web/",
        "- Catalog items: $($catalog.items.Count)",
        "- First item: $($item.seriesTitle) / $($item.episodeTitle)",
        "- Subtitle tracks: $($item.subtitles.Count)",
        "- Provider status: dandanplay $dandanplayBaseUrl",
        "- Provider runtime: dandanplay $($providerRuntime.dandanplay.reasonCode), bangumiSearch=$($providerRuntime.bangumi.searchAvailable)",
        "- Browser interaction QA: $browserInteractionQa",
        "- Progress readback: $($saved.positionMs) ms",
        "- Restart catalog items: $($restartCatalog.items.Count)",
        "- Restart progress readback: $($restartSaved.positionMs) ms",
        "",
        "Result: PASS"
    ) -join "`n"
    Set-Content -LiteralPath $reportPath -Value $report -Encoding UTF8
    Write-Host "Headless Web UI QA complete."
    Write-Host "Report: $reportPath"
} finally {
    Stop-HeadlessServer -Process $serverProcess
}
