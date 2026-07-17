[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ConfigPath,
    [int]$RootPollSeconds = 10
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$configFullPath = [System.IO.Path]::GetFullPath($ConfigPath)
$dataDirectory = Split-Path -Parent $configFullPath
$serverPath = Join-Path $PSScriptRoot "library-server.exe"
$webAssetsPath = Join-Path $PSScriptRoot "web"
$runnerLog = Join-Path $dataDirectory "background-host-runner.log"
$stdoutLog = Join-Path $dataDirectory "background-host.stdout.log"
$stderrLog = Join-Path $dataDirectory "background-host.stderr.log"

New-Item -ItemType Directory -Path $dataDirectory -Force | Out-Null

function Write-RunnerLog {
    param([string]$Message)
    $timestamp = [DateTimeOffset]::Now.ToString("o")
    Add-Content -LiteralPath $runnerLog -Value "$timestamp $Message" -Encoding utf8
}

function Get-JsonPropertyValue {
    param(
        [AllowNull()]$Object,
        [Parameter(Mandatory = $true)][string]$Name
    )

    if ($null -eq $Object) {
        return $null
    }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) {
        return $null
    }
    return $property.Value
}

try {
    if (-not (Test-Path -LiteralPath $configFullPath -PathType Leaf)) {
        throw "Background-host configuration does not exist: $configFullPath"
    }
    if (-not (Test-Path -LiteralPath $serverPath -PathType Leaf)) {
        throw "Installed library server does not exist: $serverPath"
    }
    if (-not (Test-Path -LiteralPath (Join-Path $webAssetsPath "index.html") -PathType Leaf)) {
        throw "Installed web assets do not exist: $webAssetsPath"
    }

    $config = Get-Content -LiteralPath $configFullPath -Raw | ConvertFrom-Json
    $configuredSchemaVersion = Get-JsonPropertyValue -Object $config -Name "schemaVersion"
    if ($null -eq $configuredSchemaVersion) {
        throw "Background-host configuration has no schemaVersion."
    }
    if ($configuredSchemaVersion -ne 1) {
        throw "Unsupported background-host schemaVersion '$configuredSchemaVersion'."
    }
    $configuredTaskName = Get-JsonPropertyValue -Object $config -Name "taskName"
    if ($null -eq $configuredTaskName) {
        throw "Background-host configuration has no taskName."
    }
    if ([string]$configuredTaskName -ne "\Danmaku\Library Server") {
        throw "Unsupported background-host taskName '$configuredTaskName'."
    }
    $configuredBaseUrl = Get-JsonPropertyValue -Object $config -Name "baseUrl"
    if ($null -eq $configuredBaseUrl) {
        throw "Background-host configuration has no baseUrl."
    }
    if ([string]$configuredBaseUrl -ne "http://127.0.0.1:8686") {
        throw "Unsupported background-host baseUrl '$configuredBaseUrl'."
    }
    $configuredRoots = Get-JsonPropertyValue -Object $config -Name "libraryRoots"
    if ($null -eq $configuredRoots) {
        throw "Background-host configuration has no library roots."
    }
    $rawRoots = @($configuredRoots)
    if (@($rawRoots | Where-Object { -not [System.IO.Path]::IsPathRooted([string]$_) }).Count -gt 0) {
        throw "Background-host library roots must be absolute."
    }
    $roots = @($rawRoots | ForEach-Object { [System.IO.Path]::GetFullPath([string]$_) })
    if ($roots.Count -eq 0) {
        throw "Background-host configuration has no library roots."
    }

    $waitingLogged = $false
    while ($true) {
        $missingRoots = @($roots | Where-Object { -not (Test-Path -LiteralPath $_ -PathType Container) })
        if ($missingRoots.Count -eq 0) {
            break
        }
        if (-not $waitingLogged) {
            Write-RunnerLog ("Waiting for library roots: " + ($missingRoots -join ", "))
            $waitingLogged = $true
        }
        Start-Sleep -Seconds ([Math]::Max(1, $RootPollSeconds))
    }

    Write-RunnerLog "Starting library-server.exe on http://127.0.0.1:8686"
    $serverArguments = @(
        "--data-dir", $dataDirectory,
        "--port", "8686",
        "--web-assets-dir", $webAssetsPath
    )
    foreach ($root in $roots) {
        $serverArguments += @("--root", $root)
    }

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        # Windows PowerShell 5.1 surfaces native stderr as NativeCommandError.
        # Keep stderr redirected, but do not let benign server diagnostics
        # terminate the long-running task before its real exit code is logged.
        $ErrorActionPreference = "Continue"
        & $serverPath @serverArguments 1>> $stdoutLog 2>> $stderrLog
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    Write-RunnerLog "library-server.exe exited with code $exitCode"
    exit $exitCode
} catch {
    Write-RunnerLog "Background host failed: $($_.Exception.Message)"
    throw
}
