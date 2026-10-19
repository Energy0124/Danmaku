<#
Builds the Danmaku portable package and Velopack installer from main, then
installs it over the current per-user installation and refreshes the library
server background host.

  .\deploy.bat                                                 # build + install
  powershell -ExecutionPolicy Bypass -File .\deploy.ps1        # build + install
  powershell -ExecutionPolicy Bypass -File .\deploy.ps1 -BuildOnly

Switches: -BuildOnly stops after the installer, -SkipPull keeps the current
checkout, -Yes closes the running player and server without asking.

A full transcript is written to deploy.log next to this script.
#>
[CmdletBinding()]
param(
    [switch]$BuildOnly,
    [switch]$SkipPull,
    [switch]$Yes
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = $PSScriptRoot
$log = Join-Path $repo 'deploy.log'
$vpkDir = Join-Path $repo 'build\tools\vpk'
$installDir = Join-Path $env:LOCALAPPDATA 'app.danmaku.player'
$serverInstallDir = Join-Path $env:LOCALAPPDATA 'Programs\Danmaku\LibraryServer'
Start-Transcript -LiteralPath $log -Force | Out-Null

function Step($text) { Write-Host "`n=== $text ===" -ForegroundColor Cyan }

try {
    Set-Location $repo

    Step 'Tools'
    foreach ($tool in 'git', 'cargo', 'npm') {
        if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "$tool is not on PATH." }
    }
    $vpkCommand = Get-Command vpk -ErrorAction SilentlyContinue
    $vpk = if ($vpkCommand) { $vpkCommand.Source } else { $null }
    if (-not $vpk) {
        $local = Join-Path $vpkDir 'vpk.exe'
        if (-not (Test-Path $local)) {
            if (-not (Get-Command dotnet -ErrorAction SilentlyContinue)) {
                throw 'vpk is missing and dotnet is not installed. Install the .NET 8 SDK, then rerun.'
            }
            Write-Host "Installing pinned Velopack CLI 1.2.0 into $vpkDir"
            & dotnet tool install --tool-path $vpkDir vpk --version 1.2.0
            if ($LASTEXITCODE -ne 0) { throw 'vpk installation failed.' }
        }
        $vpk = $local
    }
    Write-Host "vpk: $vpk"

    if (-not $SkipPull) {
        Step 'Update main'
        if (git status --porcelain --untracked-files=no) { throw 'Tracked files have local changes; commit or stash them first.' }
        git checkout main
        if ($LASTEXITCODE -ne 0) { throw 'git checkout main failed.' }
        git pull --ff-only origin main
        if ($LASTEXITCODE -ne 0) { throw 'git pull failed.' }
    }
    git log --oneline -1

    Step 'Build portable package (web UI + release player/server + libmpv)'
    & cmd /c "$repo\build-rust-player.bat"
    if ($LASTEXITCODE -ne 0) { throw "build-rust-player.bat failed with exit code $LASTEXITCODE." }

    Step 'Build installer'
    $installerOutput = Join-Path $repo 'build\release\windows-installer'
    if (Test-Path $installerOutput) { Remove-Item $installerOutput -Recurse -Force }
    $result = & (Join-Path $repo 'tools\windows\prepare-windows-installer.ps1') -VpkPath $vpk | Out-String
    Write-Host $result
    # vpk logs precede the script's JSON summary; parse only the summary.
    $summary = [regex]::Match($result, '(?s)\{\s*"version".*\}\s*$').Value
    if (-not $summary) { throw 'Installer script did not report its outputs.' }
    $setup = ($summary | ConvertFrom-Json).setup
    if (-not (Test-Path $setup)) { throw 'Setup executable was not produced.' }

    if ($BuildOnly) {
        Write-Host "`nInstaller ready: $setup" -ForegroundColor Green
        return
    }

    Step 'Stop the installed player and server'
    $running = Get-CimInstance Win32_Process | Where-Object {
        $_.ExecutablePath -and $_.ExecutablePath.StartsWith($installDir, [StringComparison]::OrdinalIgnoreCase)
    }
    if ($running) {
        $running | ForEach-Object { Write-Host "  $($_.ProcessId) $($_.ExecutablePath)" }
        if (-not $Yes) {
            $answer = Read-Host 'Close these to install? Unsaved playback position is saved by the server. [y/N]'
            if ($answer -notmatch '^(y|yes)$') { throw 'Install cancelled.' }
        }
        $running | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
        Start-Sleep -Seconds 2
    } else {
        Write-Host 'Nothing running from the install folder.'
    }

    Step 'Install'
    $before = Get-Item (Join-Path $installDir 'current\danmaku-player.exe') -ErrorAction SilentlyContinue
    Write-Host "Before: $(if ($before) { $before.LastWriteTime } else { 'not installed' })"
    $process = Start-Process -FilePath $setup -ArgumentList '--silent' -PassThru -Wait
    Write-Host "Setup exit code: $($process.ExitCode)"
    $after = Get-Item (Join-Path $installDir 'current\danmaku-player.exe') -ErrorAction SilentlyContinue
    Write-Host "After:  $(if ($after) { $after.LastWriteTime } else { 'missing' })"
    $built = Get-Item (Join-Path $repo 'build\release\rust-player\danmaku-player-*-windows-x64\danmaku-player.exe') | Select-Object -First 1
    if ($after -and (Get-FileHash $after.FullName).Hash -eq (Get-FileHash $built.FullName).Hash) {
        Write-Host 'Installed player matches the new build.' -ForegroundColor Green
    } else {
        Write-Warning 'Installed player does not match the new build. Check the Setup log in %LOCALAPPDATA%\app.danmaku.player.'
    }

    Step 'Refresh the library server background host'
    # The installer only replaces the player client. The library server runs as a
    # per-user scheduled task out of its own install folder, so refresh it from the
    # same build. Orphaned server processes outlive the task stop and lock that
    # folder, which makes Refresh fail on the directory swap, so stop them first.
    $package = (Get-Item (Join-Path $repo 'build\release\rust-player\danmaku-player-*-windows-x64') | Select-Object -First 1).FullName
    $hostTask = Get-ScheduledTask -TaskPath '\Danmaku\' -TaskName 'Library Server' -ErrorAction SilentlyContinue
    if (-not $hostTask) {
        Write-Host 'Background host is not installed; skipping. Use manage-rust-library-background-host.ps1 -Action Install to set it up.'
    } else {
        $servers = Get-CimInstance Win32_Process -Filter "Name='library-server.exe'" | Where-Object {
            $_.ExecutablePath -and $_.ExecutablePath.StartsWith($serverInstallDir, [StringComparison]::OrdinalIgnoreCase)
        }
        if ($servers) {
            $servers | ForEach-Object { Write-Host "  $($_.ProcessId) $($_.ExecutablePath)" }
            if (-not $Yes) {
                $answer = Read-Host 'Stop the running library server to refresh it? Progress and settings are kept. [y/N]'
                if ($answer -notmatch '^(y|yes)$') { throw 'Server refresh cancelled.' }
            }
            $servers | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
            Start-Sleep -Seconds 3
        } else {
            Write-Host 'No library server process is running.'
        }
        # Refresh stages the new files, swaps them in and restarts the task, rolling
        # back the old folder if any of that fails.
        & (Join-Path $package 'manage-rust-library-background-host.ps1') -Action Refresh
        $installedServer = Join-Path $serverInstallDir 'library-server.exe'
        if ((Test-Path $installedServer) -and
            (Get-FileHash $installedServer).Hash -eq (Get-FileHash (Join-Path $package 'library-server.exe')).Hash) {
            Write-Host 'Installed library server matches the new build.' -ForegroundColor Green
        } else {
            Write-Warning 'Installed library server does not match the new build.'
        }
    }

    Step 'Start Danmaku'
    Start-Process (Join-Path $installDir 'current\danmaku-player.exe')
    Write-Host 'Done.' -ForegroundColor Green
} catch {
    Write-Host "FAILED: $_" -ForegroundColor Red
    exit 1
} finally {
    Stop-Transcript | Out-Null
}
