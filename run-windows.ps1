[CmdletBinding()]
param(
    [switch]$Portable,
    [switch]$SkipBridgeBuild,
    [switch]$SkipDependencyInstall,
    [switch]$ReleaseBridge
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = $PSScriptRoot

function Invoke-RepoCommand {
    param(
        [Parameter(Mandatory)][string]$Description,
        [Parameter(Mandatory)][string]$FilePath,
        [string[]]$Arguments = @()
    )

    Write-Host ""
    Write-Host "==> $Description"
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Description failed with exit code $LASTEXITCODE."
    }
}

function Invoke-RepoScript {
    param(
        [Parameter(Mandatory)][string]$Description,
        [Parameter(Mandatory)][string]$ScriptPath,
        [string[]]$Arguments = @()
    )

    $powershellArgs = @(
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        $ScriptPath
    ) + $Arguments

    Invoke-RepoCommand `
        -Description $Description `
        -FilePath "powershell.exe" `
        -Arguments $powershellArgs
}

Push-Location $repoRoot
try {
    $gradle = Join-Path $repoRoot "gradlew.bat"
    if (-not (Test-Path -LiteralPath $gradle -PathType Leaf)) {
        throw "Gradle wrapper does not exist: $gradle"
    }

    if ($Portable) {
        if (-not $SkipBridgeBuild) {
            Invoke-RepoCommand `
                -Description "Build Windows mpv bridge (release)" `
                -FilePath "cargo" `
                -Arguments @("build", "--release", "-p", "player-windows-mpv", "--lib")
        }

        Invoke-RepoCommand `
            -Description "Build Windows desktop distributable" `
            -FilePath $gradle `
            -Arguments @(
                "--no-daemon",
                ":apps:desktop-windows:createDistributable"
            )

        Invoke-RepoScript `
            -Description "Prepare portable Windows release" `
            -ScriptPath (Join-Path $repoRoot "tools\windows\prepare-windows-release.ps1")

        $portableLauncher = Join-Path $repoRoot (
            "apps\desktop-windows\build\release\windows-portable\run-danmaku.ps1"
        )
        if (-not (Test-Path -LiteralPath $portableLauncher -PathType Leaf)) {
            throw "Portable launcher does not exist: $portableLauncher"
        }

        Invoke-RepoScript `
            -Description "Run portable Windows app" `
            -ScriptPath $portableLauncher
        return
    }

    $bridgeProfile = if ($ReleaseBridge) { "release" } else { "debug" }
    if (-not $SkipBridgeBuild) {
        $bridgeArgs = @("build", "-p", "player-windows-mpv", "--lib")
        if ($ReleaseBridge) {
            $bridgeArgs = @("build", "--release", "-p", "player-windows-mpv", "--lib")
        }
        Invoke-RepoCommand `
            -Description "Build Windows mpv bridge ($bridgeProfile)" `
            -FilePath "cargo" `
            -Arguments $bridgeArgs
    }
    $serverArgs = @("build", "-p", "library-server")
    if ($ReleaseBridge) {
        $serverArgs = @("build", "--release", "-p", "library-server")
    }
    Invoke-RepoCommand `
        -Description "Build Rust library server sidecar ($bridgeProfile)" `
        -FilePath "cargo" `
        -Arguments $serverArgs

    $libmpvPath = Join-Path $repoRoot "runtime\windows\libmpv\libmpv-2.dll"
    if (-not $SkipDependencyInstall -and -not (Test-Path -LiteralPath $libmpvPath -PathType Leaf)) {
        Invoke-RepoScript `
            -Description "Install pinned LGPL libmpv dependency" `
            -ScriptPath (Join-Path $repoRoot "tools\windows\install-libmpv-dependency.ps1") `
            -Arguments @("-AcceptLicense")
    } elseif (Test-Path -LiteralPath $libmpvPath -PathType Leaf) {
        Write-Host ""
        Write-Host "==> Using existing libmpv dependency at $libmpvPath"
    }

    $bridgePath = Join-Path $repoRoot "target\$bridgeProfile\player_windows_mpv.dll"
    $rustServerPath = Join-Path $repoRoot "target\$bridgeProfile\library-server.exe"
    foreach ($nativePath in @($bridgePath, $libmpvPath, $rustServerPath)) {
        if (-not (Test-Path -LiteralPath $nativePath -PathType Leaf)) {
            throw "Required native Windows dependency does not exist: $nativePath"
        }
    }

    $previousBridgePath = $env:DANMAKU_MPV_BRIDGE_PATH
    $previousLibmpvPath = $env:DANMAKU_LIBMPV_PATH
    $previousRustServerPath = $env:DANMAKU_RUST_SERVER_PATH
    try {
        $env:DANMAKU_MPV_BRIDGE_PATH = $bridgePath
        $env:DANMAKU_LIBMPV_PATH = $libmpvPath

        $env:DANMAKU_RUST_SERVER_PATH = $rustServerPath
        Invoke-RepoCommand `
            -Description "Run Windows desktop app" `
            -FilePath $gradle `
            -Arguments @("--no-daemon", ":apps:desktop-windows:run")
    } finally {
        $env:DANMAKU_MPV_BRIDGE_PATH = $previousBridgePath
        $env:DANMAKU_LIBMPV_PATH = $previousLibmpvPath
        $env:DANMAKU_RUST_SERVER_PATH = $previousRustServerPath
    }
} finally {
    Pop-Location
}
