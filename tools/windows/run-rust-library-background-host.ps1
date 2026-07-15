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
    if ($config.schemaVersion -ne 1) {
        throw "Unsupported background-host schemaVersion '$($config.schemaVersion)'."
    }
    if ([string]$config.taskName -ne "\Danmaku\Library Server") {
        throw "Unsupported background-host taskName '$($config.taskName)'."
    }
    if ([string]$config.baseUrl -ne "http://127.0.0.1:8686") {
        throw "Unsupported background-host baseUrl '$($config.baseUrl)'."
    }
    $rawRoots = @($config.libraryRoots)
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

    & $serverPath @serverArguments 1>> $stdoutLog 2>> $stderrLog
    $exitCode = $LASTEXITCODE
    Write-RunnerLog "library-server.exe exited with code $exitCode"
    exit $exitCode
} catch {
    Write-RunnerLog "Background host failed: $($_.Exception.Message)"
    throw
}
