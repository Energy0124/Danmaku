[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [ValidateSet("Install", "Start", "Stop", "Status", "SetRoots", "Uninstall")]
    [string]$Action = "Status",
    [string[]]$LibraryRoot = @(),
    [switch]$NoStart,
    [switch]$PlanOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$taskPath = "\Danmaku\"
$taskName = "Library Server"
$taskDisplayName = "Danmaku Library Server"
$baseUrl = "http://127.0.0.1:8686"
$schemaVersion = 1

if ([string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
    throw "LOCALAPPDATA is required for the per-user background host."
}

$dataDirectory = Join-Path $env:LOCALAPPDATA "Danmaku\server"
$configPath = Join-Path $dataDirectory "background-host.json"
$installDirectory = Join-Path $env:LOCALAPPDATA "Programs\Danmaku\LibraryServer"
$installedServer = Join-Path $installDirectory "library-server.exe"
$installedRunner = Join-Path $installDirectory "run-rust-library-background-host.ps1"
$installedManager = Join-Path $installDirectory "manage-rust-library-background-host.ps1"
$sourceServer = Join-Path $PSScriptRoot "library-server.exe"
$sourceWeb = Join-Path $PSScriptRoot "web"
$sourceRunner = Join-Path $PSScriptRoot "run-rust-library-background-host.ps1"
$sourceManager = $PSCommandPath

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

function Get-NormalizedRoots {
    param([string[]]$ExplicitRoots)

    $roots = @($ExplicitRoots | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($roots.Count -eq 0) {
        $preferencesPath = Join-Path $env:LOCALAPPDATA "Danmaku\player-preferences.json"
        if (Test-Path -LiteralPath $preferencesPath -PathType Leaf) {
            $preferences = Get-Content -LiteralPath $preferencesPath -Raw | ConvertFrom-Json
            $preferenceRoots = Get-JsonPropertyValue -Object $preferences -Name "local_library_roots"
            if ($null -ne $preferenceRoots) {
                $roots = @($preferenceRoots)
            }
        }
    }
    if ($roots.Count -eq 0) {
        $settingsPath = Join-Path $dataDirectory "server-settings.json"
        if (Test-Path -LiteralPath $settingsPath -PathType Leaf) {
            $settings = Get-Content -LiteralPath $settingsPath -Raw | ConvertFrom-Json
            $settingsRoots = Get-JsonPropertyValue -Object $settings -Name "libraryRoots"
            if ($null -ne $settingsRoots) {
                $roots = @($settingsRoots)
            }
        }
    }
    if ($roots.Count -eq 0) {
        throw "No library roots were supplied or found in player/server settings. Pass -LibraryRoot."
    }

    $normalized = [System.Collections.Generic.List[string]]::new()
    $seen = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::OrdinalIgnoreCase
    )
    foreach ($root in $roots) {
        $fullPath = [System.IO.Path]::GetFullPath([string]$root)
        if (-not (Test-Path -LiteralPath $fullPath -PathType Container)) {
            throw "Library root does not exist or is not a directory: $fullPath"
        }
        if ($seen.Add($fullPath)) {
            $normalized.Add($fullPath)
        }
    }
    return @($normalized)
}

function New-BackgroundConfig {
    param([string[]]$Roots)

    [ordered]@{
        schemaVersion = $schemaVersion
        taskName = "$taskPath$taskName"
        baseUrl = $baseUrl
        libraryRoots = @($Roots)
    }
}

function Write-AtomicJson {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]$Value
    )

    $parent = Split-Path -Parent $Path
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
    $temporary = "$Path.tmp"
    $json = $Value | ConvertTo-Json -Depth 6
    [System.IO.File]::WriteAllText(
        $temporary,
        $json,
        [System.Text.UTF8Encoding]::new($false)
    )
    Move-Item -LiteralPath $temporary -Destination $Path -Force
}

function Get-Task {
    Get-ScheduledTask -TaskPath $taskPath -TaskName $taskName -ErrorAction SilentlyContinue
}

function Stop-BackgroundTask {
    $task = Get-Task
    if ($null -eq $task) {
        return
    }
    if ($task.State -ne "Ready" -and $task.State -ne "Disabled") {
        Stop-ScheduledTask -TaskPath $taskPath -TaskName $taskName
        $deadline = [DateTime]::UtcNow.AddSeconds(20)
        do {
            Start-Sleep -Milliseconds 250
            $task = Get-Task
        } while (
            $null -ne $task -and
            $task.State -ne "Ready" -and
            $task.State -ne "Disabled" -and
            [DateTime]::UtcNow -lt $deadline
        )
        if ($null -ne $task -and $task.State -ne "Ready" -and $task.State -ne "Disabled") {
            throw "Timed out waiting for the background-host task to stop."
        }
    }
}

function Show-Status {
    $task = Get-Task
    $config = $null
    if (Test-Path -LiteralPath $configPath -PathType Leaf) {
        $config = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
    }
    $configRoots = Get-JsonPropertyValue -Object $config -Name "libraryRoots"
    [pscustomobject]@{
        installed = $null -ne $task
        state = if ($null -eq $task) { "NotInstalled" } else { [string]$task.State }
        task = "$taskPath$taskName"
        baseUrl = $baseUrl
        installDirectory = $installDirectory
        dataDirectory = $dataDirectory
        libraryRoots = if ($null -eq $configRoots) { @() } else { @($configRoots) }
    }
}

function Show-Plan {
    param([string[]]$Roots)

    [pscustomobject]@{
        action = $Action
        task = "$taskPath$taskName"
        displayName = $taskDisplayName
        baseUrl = $baseUrl
        installDirectory = $installDirectory
        dataDirectory = $dataDirectory
        configPath = $configPath
        libraryRoots = @($Roots)
        trigger = "CurrentUserLogon"
        logonDelaySeconds = 15
        multipleInstances = "IgnoreNew"
        restartCount = 3
        restartIntervalSeconds = 60
        startNow = -not $NoStart
    } | ConvertTo-Json -Depth 6
}

if ($Action -eq "Status") {
    Show-Status
    exit 0
}

if ($Action -eq "Install") {
    foreach ($requiredPath in @(
        $sourceServer,
        (Join-Path $sourceWeb "index.html"),
        $sourceRunner,
        $sourceManager
    )) {
        if (-not (Test-Path -LiteralPath $requiredPath)) {
            throw "Required background-host package file does not exist: $requiredPath"
        }
    }

    $roots = Get-NormalizedRoots -ExplicitRoots $LibraryRoot
    if ($PlanOnly) {
        Show-Plan -Roots $roots
        exit 0
    }

    if ($PSCmdlet.ShouldProcess($taskDisplayName, "Install per-user background host")) {
        Stop-BackgroundTask
        if ($null -ne (Get-Task)) {
            Unregister-ScheduledTask -TaskPath $taskPath -TaskName $taskName -Confirm:$false
        }
        if (Test-Path -LiteralPath $configPath) {
            Remove-Item -LiteralPath $configPath -Force
        }

        try {
            New-Item -ItemType Directory -Path $installDirectory -Force | Out-Null
            Copy-Item -LiteralPath $sourceServer -Destination $installedServer -Force
            Copy-Item -LiteralPath $sourceRunner -Destination $installedRunner -Force
            Copy-Item -LiteralPath $sourceManager -Destination $installedManager -Force
            $installedWeb = Join-Path $installDirectory "web"
            if (Test-Path -LiteralPath $installedWeb) {
                Remove-Item -LiteralPath $installedWeb -Recurse -Force
            }
            Copy-Item -LiteralPath $sourceWeb -Destination $installedWeb -Recurse -Force

            $windowsPowerShell = Join-Path $env:SystemRoot "System32\WindowsPowerShell\v1.0\powershell.exe"
            $runnerArguments = (
                '-NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass ' +
                '-WindowStyle Hidden -File "' + $installedRunner + '" ' +
                '-ConfigPath "' + $configPath + '"'
            )
            $taskActionParams = @{
                Execute = $windowsPowerShell
                Argument = $runnerArguments
                WorkingDirectory = $installDirectory
            }
            $taskAction = New-ScheduledTaskAction @taskActionParams
            $currentSid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
            $taskTrigger = New-ScheduledTaskTrigger -AtLogOn -User $currentSid
            $taskTrigger.Delay = "PT15S"
            $taskPrincipalParams = @{
                UserId = $currentSid
                LogonType = "Interactive"
                RunLevel = "Limited"
            }
            $taskPrincipal = New-ScheduledTaskPrincipal @taskPrincipalParams
            $taskSettingsParams = @{
                AllowStartIfOnBatteries = $true
                DontStopIfGoingOnBatteries = $true
                StartWhenAvailable = $true
                ExecutionTimeLimit = [TimeSpan]::Zero
                MultipleInstances = "IgnoreNew"
                RestartCount = 3
                RestartInterval = [TimeSpan]::FromMinutes(1)
            }
            $taskSettings = New-ScheduledTaskSettingsSet @taskSettingsParams
            $registerParams = @{
                TaskPath = $taskPath
                TaskName = $taskName
                Description = "Keeps the Danmaku Rust library server available after the player closes."
                Action = $taskAction
                Trigger = $taskTrigger
                Principal = $taskPrincipal
                Settings = $taskSettings
                Force = $true
            }
            Register-ScheduledTask @registerParams | Out-Null
            Write-AtomicJson -Path $configPath -Value (New-BackgroundConfig -Roots $roots)

            if (-not $NoStart) {
                Start-ScheduledTask -TaskPath $taskPath -TaskName $taskName
            }
        } catch {
            if (Test-Path -LiteralPath $configPath) {
                Remove-Item -LiteralPath $configPath -Force -ErrorAction SilentlyContinue
            }
            if ($null -ne (Get-Task)) {
                Stop-ScheduledTask -TaskPath $taskPath -TaskName $taskName -ErrorAction SilentlyContinue
                Unregister-ScheduledTask -TaskPath $taskPath -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
            }
            throw
        }
    }
    Show-Status
    exit 0
}

if ($Action -eq "SetRoots") {
    $roots = Get-NormalizedRoots -ExplicitRoots $LibraryRoot
    if ($PlanOnly) {
        Show-Plan -Roots $roots
        exit 0
    }
    if ($null -eq (Get-Task)) {
        throw "The Danmaku background host is not installed."
    }
    if ($PSCmdlet.ShouldProcess($taskDisplayName, "Update library roots and restart")) {
        Stop-BackgroundTask
        Write-AtomicJson -Path $configPath -Value (New-BackgroundConfig -Roots $roots)
        Start-ScheduledTask -TaskPath $taskPath -TaskName $taskName
    }
    Show-Status
    exit 0
}

if ($PlanOnly) {
    Show-Plan -Roots @()
    exit 0
}

if ($Action -eq "Start") {
    if ($null -eq (Get-Task)) {
        throw "The Danmaku background host is not installed."
    }
    if ($PSCmdlet.ShouldProcess($taskDisplayName, "Start")) {
        Start-ScheduledTask -TaskPath $taskPath -TaskName $taskName
    }
    Show-Status
    exit 0
}

if ($Action -eq "Stop") {
    if ($PSCmdlet.ShouldProcess($taskDisplayName, "Stop")) {
        Stop-BackgroundTask
    }
    Show-Status
    exit 0
}

if ($Action -eq "Uninstall") {
    if ($PSCmdlet.ShouldProcess($taskDisplayName, "Uninstall and preserve server data")) {
        Stop-BackgroundTask
        if ($null -ne (Get-Task)) {
            Unregister-ScheduledTask -TaskPath $taskPath -TaskName $taskName -Confirm:$false
        }
        if (Test-Path -LiteralPath $configPath) {
            Remove-Item -LiteralPath $configPath -Force
        }
        if (Test-Path -LiteralPath $installDirectory) {
            Remove-Item -LiteralPath $installDirectory -Recurse -Force
        }
    }
    Show-Status
    exit 0
}
