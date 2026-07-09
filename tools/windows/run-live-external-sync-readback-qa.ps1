[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("MY_ANIME_LIST", "MAL", "MYANIMELIST", "BANGUMI", "BGM")]
    [string]$Provider,

    [Parameter(Mandatory = $true)]
    [long]$AnimeId,

    [string]$MyAnimeListAccessToken,
    [string]$BangumiAccessToken,
    [string]$BangumiBaseUrl = "https://api.bgm.tv/",
    [string]$BangumiUserAgent = "Danmaku/0.1 (https://github.com/Energy0124/Danmaku)",
    [string]$OutputDir,
    [switch]$RustServer,
    [ValidateRange(0, 65535)]
    [int]$Port = 0,
    [string]$PairingToken = "123456",
    [switch]$AllowMissingEntry
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $repoRoot "build\qa\live-external-sync"
}
$OutputDir = [System.IO.Path]::GetFullPath($OutputDir)
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

if ($AnimeId -le 0) {
    throw "AnimeId must be positive."
}

$normalizedProvider = switch ($Provider.ToUpperInvariant()) {
    "MAL" { "MY_ANIME_LIST" }
    "MYANIMELIST" { "MY_ANIME_LIST" }
    "MY_ANIME_LIST" { "MY_ANIME_LIST" }
    "BGM" { "BANGUMI" }
    "BANGUMI" { "BANGUMI" }
    default { throw "Unsupported provider: $Provider" }
}

if ($normalizedProvider -eq "MY_ANIME_LIST") {
    if ([string]::IsNullOrWhiteSpace($MyAnimeListAccessToken)) {
        $MyAnimeListAccessToken = $env:DANMAKU_MYANIMELIST_ACCESS_TOKEN
    }
    if ([string]::IsNullOrWhiteSpace($MyAnimeListAccessToken)) {
        throw "Provide -MyAnimeListAccessToken or set DANMAKU_MYANIMELIST_ACCESS_TOKEN."
    }
} elseif ($normalizedProvider -eq "BANGUMI") {
    if ([string]::IsNullOrWhiteSpace($BangumiAccessToken)) {
        $BangumiAccessToken = $env:DANMAKU_BANGUMI_ACCESS_TOKEN
    }
    if ([string]::IsNullOrWhiteSpace($BangumiAccessToken)) {
        throw "Provide -BangumiAccessToken or set DANMAKU_BANGUMI_ACCESS_TOKEN."
    }
}

$reportProvider = $normalizedProvider.ToLowerInvariant()
$reportPath = Join-Path $OutputDir "readback-$reportProvider-$AnimeId.md"
$gradle = Join-Path $repoRoot "gradlew.bat"
$rustServerExe = $null

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

function Get-AvailableTcpPort {
    if ($Port -ne 0) {
        if (-not (Test-TcpPortAvailable -Port $Port)) {
            throw "Port $Port is already in use. Pass -Port 0 for an ephemeral port or choose a free port."
        }
        return $Port
    }

    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    try {
        $listener.Start()
        return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port
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

function Quote-ProcessArgument {
    param([string]$Value)

    '"' + ($Value -replace '"', '\"') + '"'
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

function Remove-DirectoryWithRetry {
    param([string]$Path)

    if ([string]::IsNullOrWhiteSpace($Path) -or -not (Test-Path -LiteralPath $Path)) {
        return
    }

    $lastError = $null
    for ($attempt = 1; $attempt -le 5; $attempt++) {
        try {
            Remove-Item -LiteralPath $Path -Recurse -Force -ErrorAction Stop
            return
        } catch {
            $lastError = $_
            Start-Sleep -Milliseconds (200 * $attempt)
        }
    }
    throw "Failed to delete temp Rust server data directory '$Path': $($lastError.Exception.Message)"
}

function Invoke-JsonRequest {
    param([string]$Uri)

    $response = Invoke-WebRequest `
        -Uri $Uri `
        -UseBasicParsing `
        -Headers @{ Accept = "application/json" }
    @{
        StatusCode = [int]$response.StatusCode
        Content = $response.Content
    }
}

function Invoke-WebRequestAllowingFailure {
    param([string]$Uri)

    # -SkipHttpErrorCheck (PowerShell 7+) returns non-2xx responses normally
    # with a readable body; reading the body from a thrown
    # HttpResponseException fails because its content is already disposed.
    $response = Invoke-WebRequest `
        -Uri $Uri `
        -UseBasicParsing `
        -SkipHttpErrorCheck `
        -Headers @{ Accept = "application/json" }
    return @{
        StatusCode = [int]$response.StatusCode
        Content = [string]$response.Content
    }
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
    throw "Timed out waiting for Rust library server at $BaseUrl"
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

function Get-OptionalJsonProperty {
    param(
        [object]$Object,
        [string]$Name
    )

    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) {
        return $null
    }
    $property.Value
}

function Test-IntegerLike {
    param([object]$Value)

    $Value -is [byte] -or
        $Value -is [int16] -or
        $Value -is [int] -or
        $Value -is [long] -or
        $Value -is [uint16] -or
        $Value -is [uint32] -or
        $Value -is [uint64]
}

function Assert-OptionalNonNegativeInteger {
    param(
        [object]$Object,
        [string]$Name
    )

    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property -or $null -eq $property.Value) {
        return
    }
    if (-not (Test-IntegerLike -Value $property.Value) -or [long]$property.Value -lt 0) {
        throw "Expected '$Name' to be a non-negative integer when present."
    }
}

function Assert-RustReadbackEntryShape {
    param(
        [object]$Entry,
        [string]$ExpectedProvider,
        [long]$ExpectedAnimeId
    )

    $animeId = Get-RequiredJsonProperty -Object $Entry -Name "animeId"
    $provider = Get-RequiredJsonProperty -Object $animeId -Name "provider"
    $value = Get-RequiredJsonProperty -Object $animeId -Name "value"
    if ($provider -ne $ExpectedProvider) {
        throw "Expected animeId.provider '$ExpectedProvider' but got '$provider'."
    }
    if (-not (Test-IntegerLike -Value $value) -or [long]$value -ne $ExpectedAnimeId) {
        throw "Expected animeId.value '$ExpectedAnimeId' but got '$value'."
    }

    $status = Get-OptionalJsonProperty -Object $Entry -Name "status"
    if ($null -ne $status -and @("WATCHING", "COMPLETED", "ON_HOLD", "DROPPED", "PLAN_TO_WATCH") -notcontains $status) {
        throw "Unexpected entry status '$status'."
    }
    Assert-OptionalNonNegativeInteger -Object $Entry -Name "watchedEpisodes"
    Assert-OptionalNonNegativeInteger -Object $Entry -Name "score"
    Assert-OptionalNonNegativeInteger -Object $Entry -Name "updatedAtEpochMs"
}

function Format-Unknown {
    param([object]$Value)

    if ($null -eq $Value) {
        return "unknown"
    }
    [string]$Value
}

function Get-ProviderWebUrl {
    param(
        [string]$ProviderName,
        [long]$AnimeId
    )

    if ($ProviderName -eq "MY_ANIME_LIST") {
        return "https://myanimelist.net/anime/$AnimeId"
    }
    "https://bangumi.tv/subject/$AnimeId"
}

function Redact-SensitiveText {
    param([string]$Text)

    if ($null -eq $Text) {
        return $null
    }
    $redacted = $Text
    foreach ($secret in @($MyAnimeListAccessToken, $BangumiAccessToken, $PairingToken)) {
        if (-not [string]::IsNullOrWhiteSpace($secret)) {
            $redacted = $redacted.Replace($secret, "<redacted>")
        }
    }
    $redacted
}

function Write-RustReadbackReport {
    param(
        [string]$RequestResult,
        [bool]$EntryFound,
        [object]$Entry,
        [string]$FailureType,
        [string]$FailureMessage,
        [string]$BaseUrl,
        [string]$RuntimeReasonCode
    )

    $expectedEntry = if ($AllowMissingEntry) { "false" } else { "true" }
    $generatedAt = [System.DateTimeOffset]::UtcNow.ToString("o")
    $lines = @(
        '# Live External Sync Readback QA',
        "",
        ('- Generated at: `{0}`' -f $generatedAt),
        '- Host implementation: `Rust library-server`',
        ('- Provider: `{0}`' -f $normalizedProvider),
        ('- Anime ID: `{0}`' -f $AnimeId),
        ('- Provider URL: {0}' -f (Get-ProviderWebUrl -ProviderName $normalizedProvider -AnimeId $AnimeId)),
        ('- Expected entry: `{0}`' -f $expectedEntry),
        ('- Report path: `{0}`' -f $reportPath),
        '- Mode: `read-only`',
        ('- Server base URL: `{0}`' -f $BaseUrl),
        '- Credential passing: `preseeded temp server-settings.json`',
        ('- Provider runtime reason: `{0}`' -f (Format-Unknown $RuntimeReasonCode)),
        "",
        '## Result',
        "",
        ('- Request result: `{0}`' -f $RequestResult)
    )
    if ($RequestResult -eq "FAIL") {
        $lines += @(
            ('- Error type: `{0}`' -f (Format-Unknown $FailureType)),
            ('- Error message: `{0}`' -f (Redact-SensitiveText -Text (Format-Unknown $FailureMessage)))
        )
    } elseif (-not $EntryFound) {
        $lines += @(
            '- Entry found: `false`',
            '- Status: `missing from provider list or provider omitted list status`'
        )
    } else {
        $lines += @(
            '- Entry found: `true`',
            ('- Status: `{0}`' -f (Format-Unknown (Get-OptionalJsonProperty -Object $Entry -Name "status"))),
            ('- Watched episodes: `{0}`' -f (Format-Unknown (Get-OptionalJsonProperty -Object $Entry -Name "watchedEpisodes"))),
            ('- Score: `{0}`' -f (Format-Unknown (Get-OptionalJsonProperty -Object $Entry -Name "score"))),
            ('- Updated at epoch ms: `{0}`' -f (Format-Unknown (Get-OptionalJsonProperty -Object $Entry -Name "updatedAtEpochMs")))
        )
    }
    $lines += @(
        "",
        '## Safety',
        "",
        'This harness only called the Rust server''s external list read route; it did not send provider update requests.',
        'Provider access tokens were written only to the temporary Rust server settings file before launch, never to process command-line arguments or this report. The wrapper stops the server and deletes the temp data directory, including `server-settings.json`, in a finally cleanup path.'
    )
    Set-Content -LiteralPath $reportPath -Value ($lines -join "`n") -Encoding UTF8
}

function Write-RustServerSettings {
    param([string]$DataDir)

    New-Item -ItemType Directory -Force -Path $DataDir | Out-Null
    $externalAnime = [ordered]@{
        hasMyAnimeListClientSecret = $false
        hasMyAnimeListAccessToken = $false
        bangumiBaseUrl = $BangumiBaseUrl
        bangumiUserAgent = $BangumiUserAgent
        hasBangumiAccessToken = $false
    }
    if ($normalizedProvider -eq "MY_ANIME_LIST") {
        $externalAnime.myAnimeListAccessToken = $MyAnimeListAccessToken
        $externalAnime.hasMyAnimeListAccessToken = $true
    } else {
        $externalAnime.bangumiAccessToken = $BangumiAccessToken
        $externalAnime.hasBangumiAccessToken = $true
    }
    $settings = [ordered]@{
        schemaVersion = 1
        pairingToken = $PairingToken
        libraryRoots = @()
        dandanplay = [ordered]@{
            baseUrl = "https://api.dandanplay.net"
            hasAppSecret = $false
            authenticationMode = "SIGNED"
            cacheMaxAgeDays = 30
        }
        externalAnime = $externalAnime
    }
    Set-Content `
        -LiteralPath (Join-Path $DataDir "server-settings.json") `
        -Value ($settings | ConvertTo-Json -Depth 8) `
        -Encoding UTF8
}

function Start-RustServer {
    param(
        [string]$DataDir,
        [int]$ServerPort
    )

    $processStart = [System.Diagnostics.ProcessStartInfo]::new()
    $processStart.FileName = $rustServerExe
    $processStart.WorkingDirectory = $DataDir
    $processStart.UseShellExecute = $false
    $processStart.CreateNoWindow = $true
    $processStart.Arguments = (@("--data-dir", $DataDir, "--port", "$ServerPort") |
        ForEach-Object { Quote-ProcessArgument $_ }) -join " "

    $processEnvironment = $processStart.EnvironmentVariables
    foreach ($name in @(
        "DANMAKU_MYANIMELIST_CLIENT_ID",
        "DANMAKU_MYANIMELIST_CLIENT_SECRET",
        "DANMAKU_MYANIMELIST_ACCESS_TOKEN",
        "DANMAKU_BANGUMI_ACCESS_TOKEN",
        "DANMAKU_BANGUMI_BASE_URL",
        "DANMAKU_BANGUMI_USER_AGENT",
        "DANMAKU_LOCAL_PROPERTIES"
    )) {
        [void]$processEnvironment.Remove($name)
    }
    $processEnvironment["LOCALAPPDATA"] = Join-Path $DataDir "local-app-data"
    $processEnvironment["USERPROFILE"] = Join-Path $DataDir "user-profile"
    $processEnvironment["HOME"] = Join-Path $DataDir "home"

    [System.Diagnostics.Process]::Start($processStart)
}

function Invoke-RustLiveExternalSyncReadbackQa {
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

    $serverPort = Get-AvailableTcpPort
    $baseUrl = "http://127.0.0.1:$serverPort"
    $tempName = "danmaku-live-external-sync-rust-$PID-$([System.Guid]::NewGuid().ToString("N"))"
    $dataDir = Join-Path ([System.IO.Path]::GetTempPath()) $tempName
    $serverProcess = $null
    $runtimeReasonCode = $null
    $qaFailure = $null
    $cleanupFailure = $null

    try {
        Write-RustServerSettings -DataDir $dataDir
        $serverProcess = Start-RustServer -DataDir $dataDir -ServerPort $serverPort
        Wait-ForServer -BaseUrl $baseUrl

        $tokenQuery = [System.Uri]::EscapeDataString($PairingToken)
        $runtime = (Invoke-JsonRequest -Uri "$baseUrl/api/providers/runtime?token=$tokenQuery").Content | ConvertFrom-Json
        $runtimeProperty = if ($normalizedProvider -eq "MY_ANIME_LIST") { "myAnimeList" } else { "bangumi" }
        $providerRuntime = Get-RequiredJsonProperty -Object $runtime -Name $runtimeProperty
        $runtimeReasonCode = Get-RequiredJsonProperty -Object $providerRuntime -Name "reasonCode"
        $authenticated = Get-RequiredJsonProperty -Object $providerRuntime -Name "authenticated"
        $listReadAvailable = Get-RequiredJsonProperty -Object $providerRuntime -Name "listReadAvailable"
        if ($authenticated -ne $true -or $listReadAvailable -ne $true) {
            throw "Rust provider runtime is not ready for $normalizedProvider list readback (reasonCode=$runtimeReasonCode, authenticated=$authenticated, listReadAvailable=$listReadAvailable). Check the provider access token."
        }

        $providerQuery = if ($normalizedProvider -eq "MY_ANIME_LIST") { "mal" } else { "bangumi" }
        $entryUri = "$baseUrl/api/providers/list/entry?provider=$providerQuery&animeId=$AnimeId&token=$tokenQuery"
        $entryResponse = Invoke-WebRequestAllowingFailure -Uri $entryUri
        if ($entryResponse.StatusCode -eq 404) {
            if (-not $AllowMissingEntry) {
                throw "Expected ${normalizedProvider}:$AnimeId to exist in the provider list. Use -AllowMissingEntry for absent-entry smoke checks."
            }
            Write-RustReadbackReport `
                -RequestResult "PASS" `
                -EntryFound $false `
                -Entry $null `
                -FailureType $null `
                -FailureMessage $null `
                -BaseUrl $baseUrl `
                -RuntimeReasonCode $runtimeReasonCode
        } else {
            if ($entryResponse.StatusCode -ne 200) {
                throw "Rust external list readback failed with HTTP $($entryResponse.StatusCode): $($entryResponse.Content)"
            }

            $entry = $entryResponse.Content | ConvertFrom-Json
            Assert-RustReadbackEntryShape `
                -Entry $entry `
                -ExpectedProvider $normalizedProvider `
                -ExpectedAnimeId $AnimeId
            Write-RustReadbackReport `
                -RequestResult "PASS" `
                -EntryFound $true `
                -Entry $entry `
                -FailureType $null `
                -FailureMessage $null `
                -BaseUrl $baseUrl `
                -RuntimeReasonCode $runtimeReasonCode
        }
    } catch {
        $qaFailure = $_
        $failureType = $_.Exception.GetType().FullName
        $failureMessage = $_.Exception.Message
        try {
            Write-RustReadbackReport `
                -RequestResult "FAIL" `
                -EntryFound $false `
                -Entry $null `
                -FailureType $failureType `
                -FailureMessage $failureMessage `
                -BaseUrl $baseUrl `
                -RuntimeReasonCode $runtimeReasonCode
        } catch {
            Write-Warning "Failed to write Rust live external sync readback report: $($_.Exception.Message)"
        }
    } finally {
        try {
            Stop-HeadlessServer -Process $serverProcess
            Remove-DirectoryWithRetry -Path $dataDir
        } catch {
            $cleanupFailure = $_
        }
    }

    if ($null -ne $cleanupFailure) {
        if ($null -ne $qaFailure) {
            throw "Rust live external sync readback failed, and cleanup also failed. QA failure: $($qaFailure.Exception.Message). Cleanup failure: $($cleanupFailure.Exception.Message)"
        }
        throw $cleanupFailure
    }
    if ($null -ne $qaFailure) {
        throw $qaFailure
    }
}

if ($RustServer) {
    Invoke-RustLiveExternalSyncReadbackQa
    if (-not (Test-Path -LiteralPath $reportPath -PathType Leaf)) {
        throw "Live external sync readback QA report was not written: $reportPath"
    }
    Write-Host "Live external sync readback QA complete."
    Write-Host "Report: $reportPath"
    return
}

if (-not (Test-Path -LiteralPath $gradle -PathType Leaf)) {
    throw "Gradle wrapper does not exist: $gradle"
}

Push-Location $repoRoot
$previousProvider = $env:DANMAKU_LIVE_EXTERNAL_SYNC_QA_PROVIDER
$previousAnimeId = $env:DANMAKU_LIVE_EXTERNAL_SYNC_QA_ANIME_ID
$previousReport = $env:DANMAKU_LIVE_EXTERNAL_SYNC_QA_REPORT
$previousExpectEntry = $env:DANMAKU_LIVE_EXTERNAL_SYNC_QA_EXPECT_ENTRY
$previousMalAccessToken = $env:DANMAKU_MYANIMELIST_ACCESS_TOKEN
$previousBangumiAccessToken = $env:DANMAKU_BANGUMI_ACCESS_TOKEN
$previousBangumiBaseUrl = $env:DANMAKU_BANGUMI_BASE_URL
$previousBangumiUserAgent = $env:DANMAKU_BANGUMI_USER_AGENT
try {
    $env:DANMAKU_LIVE_EXTERNAL_SYNC_QA_PROVIDER = $normalizedProvider
    $env:DANMAKU_LIVE_EXTERNAL_SYNC_QA_ANIME_ID = [string]$AnimeId
    $env:DANMAKU_LIVE_EXTERNAL_SYNC_QA_REPORT = $reportPath
    $env:DANMAKU_LIVE_EXTERNAL_SYNC_QA_EXPECT_ENTRY = if ($AllowMissingEntry) { "false" } else { "true" }

    if ($normalizedProvider -eq "MY_ANIME_LIST") {
        $env:DANMAKU_MYANIMELIST_ACCESS_TOKEN = $MyAnimeListAccessToken
        Remove-Item Env:\DANMAKU_BANGUMI_ACCESS_TOKEN -ErrorAction SilentlyContinue
    } else {
        $env:DANMAKU_BANGUMI_ACCESS_TOKEN = $BangumiAccessToken
        $env:DANMAKU_BANGUMI_BASE_URL = $BangumiBaseUrl
        $env:DANMAKU_BANGUMI_USER_AGENT = $BangumiUserAgent
        Remove-Item Env:\DANMAKU_MYANIMELIST_ACCESS_TOKEN -ErrorAction SilentlyContinue
    }

    & $gradle `
        "--no-daemon" `
        "--rerun-tasks" `
        ":shared:library-server-core:jvmTest" `
        "--tests" `
        "app.danmaku.provider.external.LiveExternalAnimeReadbackQaTest"
    if ($LASTEXITCODE -ne 0) {
        throw "Live external sync readback QA failed with exit code $LASTEXITCODE"
    }
} finally {
    if ($null -eq $previousProvider) {
        Remove-Item Env:\DANMAKU_LIVE_EXTERNAL_SYNC_QA_PROVIDER -ErrorAction SilentlyContinue
    } else {
        $env:DANMAKU_LIVE_EXTERNAL_SYNC_QA_PROVIDER = $previousProvider
    }
    if ($null -eq $previousAnimeId) {
        Remove-Item Env:\DANMAKU_LIVE_EXTERNAL_SYNC_QA_ANIME_ID -ErrorAction SilentlyContinue
    } else {
        $env:DANMAKU_LIVE_EXTERNAL_SYNC_QA_ANIME_ID = $previousAnimeId
    }
    if ($null -eq $previousReport) {
        Remove-Item Env:\DANMAKU_LIVE_EXTERNAL_SYNC_QA_REPORT -ErrorAction SilentlyContinue
    } else {
        $env:DANMAKU_LIVE_EXTERNAL_SYNC_QA_REPORT = $previousReport
    }
    if ($null -eq $previousExpectEntry) {
        Remove-Item Env:\DANMAKU_LIVE_EXTERNAL_SYNC_QA_EXPECT_ENTRY -ErrorAction SilentlyContinue
    } else {
        $env:DANMAKU_LIVE_EXTERNAL_SYNC_QA_EXPECT_ENTRY = $previousExpectEntry
    }
    if ($null -eq $previousMalAccessToken) {
        Remove-Item Env:\DANMAKU_MYANIMELIST_ACCESS_TOKEN -ErrorAction SilentlyContinue
    } else {
        $env:DANMAKU_MYANIMELIST_ACCESS_TOKEN = $previousMalAccessToken
    }
    if ($null -eq $previousBangumiAccessToken) {
        Remove-Item Env:\DANMAKU_BANGUMI_ACCESS_TOKEN -ErrorAction SilentlyContinue
    } else {
        $env:DANMAKU_BANGUMI_ACCESS_TOKEN = $previousBangumiAccessToken
    }
    if ($null -eq $previousBangumiBaseUrl) {
        Remove-Item Env:\DANMAKU_BANGUMI_BASE_URL -ErrorAction SilentlyContinue
    } else {
        $env:DANMAKU_BANGUMI_BASE_URL = $previousBangumiBaseUrl
    }
    if ($null -eq $previousBangumiUserAgent) {
        Remove-Item Env:\DANMAKU_BANGUMI_USER_AGENT -ErrorAction SilentlyContinue
    } else {
        $env:DANMAKU_BANGUMI_USER_AGENT = $previousBangumiUserAgent
    }
    Pop-Location
}

if (-not (Test-Path -LiteralPath $reportPath -PathType Leaf)) {
    throw "Live external sync readback QA report was not written: $reportPath"
}

Write-Host "Live external sync readback QA complete."
Write-Host "Report: $reportPath"
