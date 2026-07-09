[CmdletBinding()]
param(
    [string]$ReleaseRoot = (
        Join-Path $PSScriptRoot "..\..\build\release\rust-library-server"
    ),
    [string]$WebUiPath = (
        Join-Path $PSScriptRoot "..\..\apps\web-ui"
    ),
    [bool]$SmokeCheck = $true
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))

function Get-FullPathFromRepo {
    param([Parameter(Mandatory = $true)][string]$Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path $repoRoot $Path))
}

function Assert-PathInside {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)][string]$Description
    )

    $rootFullPath = [System.IO.Path]::GetFullPath($Root)
    $pathFullPath = [System.IO.Path]::GetFullPath($Path)
    $rootPrefix = $rootFullPath.TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar
    ) + [System.IO.Path]::DirectorySeparatorChar

    if (
        $pathFullPath -ne $rootFullPath -and
        -not $pathFullPath.StartsWith(
            $rootPrefix,
            [System.StringComparison]::OrdinalIgnoreCase
        )
    ) {
        throw "$Description must remain inside $rootFullPath"
    }
}

function Get-RustHostTarget {
    $hostLine = & rustc -vV | Where-Object { $_ -like "host:*" } | Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace($hostLine)) {
        throw "Could not determine the Rust host target with rustc -vV."
    }
    return $hostLine.Split(":", 2)[1].Trim()
}

function Get-CargoMetadata {
    param([Parameter(Mandatory = $true)][string]$TargetTriple)

    $metadataJson = & cargo metadata `
        --format-version 1 `
        --locked `
        --filter-platform $TargetTriple
    if ($LASTEXITCODE -ne 0) {
        throw "cargo metadata failed."
    }
    return $metadataJson | ConvertFrom-Json
}

function Get-CargoTargetDir {
    if (-not [string]::IsNullOrWhiteSpace($env:CARGO_TARGET_DIR)) {
        return Get-FullPathFromRepo $env:CARGO_TARGET_DIR
    }
    return Join-Path $repoRoot "target"
}

function Escape-MarkdownTableCell {
    param([AllowNull()][object]$Value)

    if ($null -eq $Value -or [string]::IsNullOrWhiteSpace([string]$Value)) {
        return "(not specified)"
    }
    return ([string]$Value).Replace("|", "\|").Replace("`r", " ").Replace("`n", " ")
}

function Get-ReleasePackageIds {
    param(
        [Parameter(Mandatory = $true)]$Metadata,
        [Parameter(Mandatory = $true)][string]$RootPackageId
    )

    $nodesById = @{}
    foreach ($node in $Metadata.resolve.nodes) {
        $nodesById[[string]$node.id] = $node
    }
    if (-not $nodesById.ContainsKey($RootPackageId)) {
        throw "cargo metadata did not include a resolve node for $RootPackageId."
    }

    $directIds = @{}
    $seenIds = @{}
    $queue = [System.Collections.Generic.Queue[string]]::new()
    $seenIds[$RootPackageId] = "package"

    foreach ($dependencyId in $nodesById[$RootPackageId].dependencies) {
        $dependencyKey = [string]$dependencyId
        $directIds[$dependencyKey] = $true
        $queue.Enqueue($dependencyKey)
    }

    while ($queue.Count -gt 0) {
        $currentId = $queue.Dequeue()
        if ($seenIds.ContainsKey($currentId)) {
            continue
        }
        $seenIds[$currentId] = if ($directIds.ContainsKey($currentId)) {
            "direct"
        } else {
            "transitive"
        }

        if ($nodesById.ContainsKey($currentId)) {
            foreach ($dependencyId in $nodesById[$currentId].dependencies) {
                $queue.Enqueue([string]$dependencyId)
            }
        }
    }

    return $seenIds
}

function Write-RustCrateLicenses {
    param(
        [Parameter(Mandatory = $true)]$Metadata,
        [Parameter(Mandatory = $true)]$ServerPackage,
        [Parameter(Mandatory = $true)][string]$DestinationPath
    )

    $releasePackageIds = Get-ReleasePackageIds `
        -Metadata $Metadata `
        -RootPackageId ([string]$ServerPackage.id)

    $packagesById = @{}
    foreach ($package in $Metadata.packages) {
        $packagesById[[string]$package.id] = $package
    }

    $rows = foreach ($packageId in $releasePackageIds.Keys) {
        if (-not $packagesById.ContainsKey($packageId)) {
            continue
        }
        $package = $packagesById[$packageId]
        [pscustomobject]@{
            Name = [string]$package.name
            Version = [string]$package.version
            License = Escape-MarkdownTableCell $package.license
            Scope = [string]$releasePackageIds[$packageId]
            Source = Escape-MarkdownTableCell $package.source
        }
    }

    $content = [System.Collections.Generic.List[string]]::new()
    $content.Add("# Rust Crate Licenses")
    $content.Add("")
    $content.Add(
        "Generated from ``cargo metadata --locked`` for the " +
        "``library-server`` release dependency graph. No extra license " +
        "scanner or third-party tooling was used."
    )
    $content.Add("")
    $content.Add("| Crate | Version | Scope | License | Source |")
    $content.Add("| --- | --- | --- | --- | --- |")
    foreach ($row in ($rows | Sort-Object Name, Version, Scope)) {
        $line = "| {0} | {1} | {2} | {3} | {4} |" -f @(
            (Escape-MarkdownTableCell $row.Name)
            (Escape-MarkdownTableCell $row.Version)
            (Escape-MarkdownTableCell $row.Scope)
            $row.License
            $row.Source
        )
        $content.Add($line)
    }
    Set-Content -LiteralPath $DestinationPath -Value $content -Encoding UTF8
}

function Write-PackageReadme {
    param(
        [Parameter(Mandatory = $true)][string]$DestinationPath,
        [Parameter(Mandatory = $true)][string]$Version
    )

    $readme = @'
# Danmaku Rust Library Server __VERSION__

This package contains the Rust headless Danmaku library server for Windows.
It serves the existing trusted-LAN HTTP and UDP discovery protocol used by
the Android mobile app, Android TV app, and bundled web UI.

## Contents

- `library-server.exe` - headless Rust library server.
- `web/` - bundled Vite web UI assets served under `/web/`.
- `LICENSE` - Danmaku project license.
- `THIRD_PARTY_NOTICES.md` - third-party notices maintained by the project.
- `RUST_CRATE_LICENSES.md` - direct and transitive Rust crate names,
  versions, licenses, and sources generated from Cargo metadata.

## Run

```powershell
.\library-server.exe --data-dir <dir> --root <folder> --web-assets-dir .\web
```

Open `http://127.0.0.1:8686/web/` on the host, or connect Android/web
clients over the trusted LAN.

## CLI Flags

- `--data-dir <PATH>`: data directory for `server-settings.json`,
  `catalog.json`, progress, provider settings, and the data-directory lock.
  Falls back to `DANMAKU_SERVER_DATA_DIR`, then `data/library-server`.
- `--root <PATH>`: library root to scan and publish. May be repeated. When
  omitted, saved roots from `server-settings.json` are used.
- `--port <PORT>`: HTTP bind port. Default is `8686`. Use `0` only for
  local smoke runs where the selected port is read from process output.
- `--pairing-token <TOKEN>`: pairing token to persist in
  `server-settings.json` when settings are created.
- `--web-assets-dir <PATH>`: web UI asset directory. For this package, point
  it at the bundled `web/` directory. Falls back to `DANMAKU_WEB_UI_DIST`.
- `--web-ui-dist <PATH>`: legacy alias for `--web-assets-dir`.
- `--import-desktop-catalog <DB_COPY>`: import a read-only copy of the
  existing desktop SQLite catalog into the Rust server data directory, then
  exit. Use it with `--data-dir <dir>` and pass a copied database file, not
  the live desktop database.
- `--help`: print the current command-line help.

## Desktop Catalog Import

```powershell
.\library-server.exe --data-dir <dir> --import-desktop-catalog <path-to-library.db-copy>
```

The importer preserves desktop media ids and merges progress newest-wins for
the LAN-visible catalog and playback progress. Desktop-only admin data such
as favorites, provider caches, download queues, and quality decisions is not
published by the LAN protocol and is not imported.

## Network Defaults

- HTTP API and media streaming: `0.0.0.0:8686` by default.
- Web UI: `/web/` when `--web-assets-dir .\web` is supplied.
- UDP discovery: announces every 1.5 seconds to UDP port `8687` using the
  existing `danmaku-library` version 1 discovery payload.
- API version: `1`.

## Trust Model

The LAN server is for trusted local networks only. Pairing tokens protect
catalog, media, and progress routes but are not an internet-facing auth
design. Do not expose this server directly to the public internet. Do not log
credentials, pairing tokens, cookies, signed URLs, or raw provider secrets.

'@

    Set-Content `
        -LiteralPath $DestinationPath `
        -Value ($readme.Replace("__VERSION__", $Version)) `
        -Encoding UTF8
}

function Get-FreeTcpPort {
    $listener = [System.Net.Sockets.TcpListener]::new(
        [System.Net.IPAddress]::Loopback,
        0
    )
    $listener.Start()
    try {
        return $listener.LocalEndpoint.Port
    } finally {
        $listener.Stop()
    }
}

function Invoke-SmokeWebRequest {
    param(
        [Parameter(Mandatory = $true)][string]$Uri,
        [int]$TimeoutSec = 5
    )

    return Invoke-WebRequest `
        -Uri $Uri `
        -UseBasicParsing `
        -TimeoutSec $TimeoutSec
}

function Wait-SmokeServer {
    param(
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)]$Process
    )

    $statusUri = "http://127.0.0.1:$Port/api/server/status"
    $deadline = [DateTimeOffset]::Now.AddSeconds(30)
    $lastError = $null
    while ([DateTimeOffset]::Now -lt $deadline) {
        if ($Process.HasExited) {
            throw "Smoke server exited before responding. ExitCode=$($Process.ExitCode)"
        }
        try {
            return Invoke-SmokeWebRequest -Uri $statusUri -TimeoutSec 2
        } catch {
            $lastError = $_
            Start-Sleep -Milliseconds 500
        }
    }
    throw "Timed out waiting for $statusUri. Last error: $lastError"
}

function Stop-SmokeServer {
    param([Parameter(Mandatory = $true)]$Process)

    if ($Process.HasExited) {
        return
    }
    if ($Process.CloseMainWindow()) {
        if ($Process.WaitForExit(5000)) {
            return
        }
    }
    $Process.Kill($true)
    $Process.WaitForExit(5000) | Out-Null
}

function Invoke-SmokeCheck {
    param([Parameter(Mandatory = $true)][string]$PackagePath)

    $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) (
        "danmaku-rust-server-smoke-{0}-{1}" -f
        $PID,
        ([System.Guid]::NewGuid().ToString("N"))
    )
    $dataDir = Join-Path $tempRoot "data"
    $libraryRoot = Join-Path $tempRoot "library"
    $showDir = Join-Path $libraryRoot "Example Show"
    $webDir = Join-Path $PackagePath "web"
    $exePath = Join-Path $PackagePath "library-server.exe"
    $port = Get-FreeTcpPort

    New-Item -ItemType Directory -Path $showDir -Force | Out-Null
    [System.IO.File]::WriteAllBytes(
        (Join-Path $showDir "Episode 01.mp4"),
        [byte[]](0, 1, 2, 3, 4, 5, 6, 7)
    )

    $psi = [System.Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $exePath
    foreach ($argument in @(
        "--data-dir", $dataDir,
        "--root", $libraryRoot,
        "--port", [string]$port,
        "--web-assets-dir", $webDir
    )) {
        [void]$psi.ArgumentList.Add($argument)
    }
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.CreateNoWindow = $true

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $psi
    [void]$process.Start()

    try {
        $statusResponse = Wait-SmokeServer -Port $port -Process $process
        $status = $statusResponse.Content | ConvertFrom-Json
        if ($status.webUiAvailable -ne $true -or $status.webUiPath -ne "/web") {
            throw "Smoke status response did not report the bundled web UI."
        }

        $libraryResponse = Invoke-SmokeWebRequest `
            -Uri "http://127.0.0.1:$port/api/library"
        $library = $libraryResponse.Content | ConvertFrom-Json
        if ($library.items.Count -ne 1) {
            throw "Smoke library response published $($library.items.Count) items instead of 1."
        }

        $webResponse = Invoke-SmokeWebRequest `
            -Uri "http://127.0.0.1:$port/web/"
        if (
            $webResponse.Content -notlike "*<div id=`"root`"></div>*" -or
            $webResponse.Content -notlike "*/web/assets/*"
        ) {
            throw "Smoke web response did not look like the bundled Vite shell."
        }

        Write-Host "Smoke check passed:"
        Write-Host "  GET /api/server/status -> $($statusResponse.StatusCode); webUiAvailable=$($status.webUiAvailable); hostMode=$($status.hostMode)"
        Write-Host "  GET /api/library -> $($libraryResponse.StatusCode); items=$($library.items.Count); rootName=$($library.rootName)"
        Write-Host "  GET /web/ -> $($webResponse.StatusCode); bytes=$($webResponse.RawContentLength)"
    } finally {
        Stop-SmokeServer -Process $process
        $stdout = $process.StandardOutput.ReadToEnd().Trim()
        $stderr = $process.StandardError.ReadToEnd().Trim()
        if (-not [string]::IsNullOrWhiteSpace($stdout)) {
            Write-Host "Smoke server stdout:"
            Write-Host $stdout
        }
        if (-not [string]::IsNullOrWhiteSpace($stderr)) {
            Write-Host "Smoke server stderr:"
            Write-Host $stderr
        }
        Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

function Write-ContentManifest {
    param([Parameter(Mandatory = $true)][string]$PackagePath)

    Write-Host "Package content manifest:"
    Get-ChildItem -LiteralPath $PackagePath -Recurse -File |
        Sort-Object FullName |
        ForEach-Object {
            $relativePath = [System.IO.Path]::GetRelativePath($PackagePath, $_.FullName)
            Write-Host ("  {0} ({1} bytes)" -f $relativePath, $_.Length)
        }
}

$releaseRootFullPath = [System.IO.Path]::GetFullPath($ReleaseRoot)
$releaseBasePath = Join-Path $repoRoot "build\release\rust-library-server"
Assert-PathInside `
    -Path $releaseRootFullPath `
    -Root $releaseBasePath `
    -Description "Rust server release path"

$webUiFullPath = [System.IO.Path]::GetFullPath($WebUiPath)
if (-not (Test-Path -LiteralPath $webUiFullPath -PathType Container)) {
    throw "Web UI path does not exist: $webUiFullPath"
}

$hostTarget = Get-RustHostTarget
$cargoMetadata = Get-CargoMetadata -TargetTriple $hostTarget
$serverPackage = $cargoMetadata.packages |
    Where-Object { $_.name -eq "library-server" } |
    Select-Object -First 1
if ($null -eq $serverPackage) {
    throw "Cargo package 'library-server' was not found."
}
$packageVersion = [string]$serverPackage.version
$packageName = "danmaku-rust-library-server-$packageVersion-windows-x64"
$stagePath = Join-Path $releaseRootFullPath $packageName
$zipPath = Join-Path $releaseRootFullPath "$packageName.zip"

Write-Host "Building Rust library server..."
Push-Location $repoRoot
try {
    & cargo build --release -p library-server
    if ($LASTEXITCODE -ne 0) {
        throw "cargo build failed."
    }
} finally {
    Pop-Location
}

$cargoTargetDir = Get-CargoTargetDir
$serverExecutable = Join-Path $cargoTargetDir "release\library-server.exe"
if (-not (Test-Path -LiteralPath $serverExecutable -PathType Leaf)) {
    throw "Rust server executable does not exist: $serverExecutable"
}

Write-Host "Building web UI..."
$previousNpmCache = $env:npm_config_cache
$previousNodeOptions = $env:NODE_OPTIONS
Push-Location $webUiFullPath
try {
    $npmCachePath = Join-Path $repoRoot ".npm-cache\rust-server-release-web-ui"
    New-Item -ItemType Directory -Path $npmCachePath -Force | Out-Null
    $env:npm_config_cache = $npmCachePath
    & npm install
    if ($LASTEXITCODE -ne 0) {
        throw "npm install failed."
    }
    if ([string]::IsNullOrWhiteSpace($previousNpmCache)) {
        Remove-Item Env:\npm_config_cache -ErrorAction SilentlyContinue
    } else {
        $env:npm_config_cache = $previousNpmCache
    }

    $viteNetUsePatch = Join-Path $npmCachePath "vite-net-use-noop.cjs"
    Set-Content `
        -LiteralPath $viteNetUsePatch `
        -Encoding UTF8 `
        -Value @'
const childProcess = require("node:child_process");
const moduleApi = require("node:module");
const originalExec = childProcess.exec;

childProcess.exec = function patchedExec(command, ...args) {
  if (process.platform === "win32" && command === "net use") {
    const callback = args.find((arg) => typeof arg === "function");
    if (callback) {
      process.nextTick(() => callback(null, "", ""));
    }
    return {
      on() {
        return this;
      },
      once() {
        return this;
      },
      kill() {
        return false;
      }
    };
  }
  return originalExec.call(this, command, ...args);
};

moduleApi.syncBuiltinESMExports();
'@
    $nodeOptionsPatch = "--require=$viteNetUsePatch"
    if ([string]::IsNullOrWhiteSpace($previousNodeOptions)) {
        $env:NODE_OPTIONS = $nodeOptionsPatch
    } else {
        $env:NODE_OPTIONS = "$previousNodeOptions $nodeOptionsPatch"
    }
    & npm run build
    if ($LASTEXITCODE -ne 0) {
        throw "npm run build failed."
    }
} finally {
    if ([string]::IsNullOrWhiteSpace($previousNpmCache)) {
        Remove-Item Env:\npm_config_cache -ErrorAction SilentlyContinue
    } else {
        $env:npm_config_cache = $previousNpmCache
    }
    if ([string]::IsNullOrWhiteSpace($previousNodeOptions)) {
        Remove-Item Env:\NODE_OPTIONS -ErrorAction SilentlyContinue
    } else {
        $env:NODE_OPTIONS = $previousNodeOptions
    }
    Pop-Location
}

$webDistPath = Join-Path $webUiFullPath "dist"
if (-not (Test-Path -LiteralPath $webDistPath -PathType Container)) {
    throw "Web UI dist directory does not exist: $webDistPath"
}

if (Test-Path -LiteralPath $stagePath) {
    Remove-Item -LiteralPath $stagePath -Recurse -Force
}
if (Test-Path -LiteralPath $zipPath) {
    Remove-Item -LiteralPath $zipPath -Force
}

New-Item -ItemType Directory -Path $stagePath -Force | Out-Null
Copy-Item -LiteralPath $serverExecutable -Destination $stagePath -Force
Copy-Item -LiteralPath (Join-Path $repoRoot "LICENSE") -Destination $stagePath -Force
Copy-Item `
    -LiteralPath (Join-Path $repoRoot "THIRD_PARTY_NOTICES.md") `
    -Destination $stagePath `
    -Force
Copy-Item `
    -LiteralPath $webDistPath `
    -Destination (Join-Path $stagePath "web") `
    -Recurse `
    -Force

Write-RustCrateLicenses `
    -Metadata $cargoMetadata `
    -ServerPackage $serverPackage `
    -DestinationPath (Join-Path $stagePath "RUST_CRATE_LICENSES.md")
Write-PackageReadme `
    -DestinationPath (Join-Path $stagePath "README.md") `
    -Version $packageVersion

Write-ContentManifest -PackagePath $stagePath

if ($SmokeCheck) {
    Invoke-SmokeCheck -PackagePath $stagePath
} else {
    Write-Host "Smoke check skipped because -SmokeCheck:`$false was supplied."
}

Compress-Archive -LiteralPath $stagePath -DestinationPath $zipPath -Force
Write-Host "Prepared Rust library server release zip: $zipPath"
