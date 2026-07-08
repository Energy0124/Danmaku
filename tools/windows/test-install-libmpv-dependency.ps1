[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$installer = Join-Path $PSScriptRoot "install-libmpv-dependency.ps1"
$sevenZip = (Get-Command 7z.exe, 7z -ErrorAction Stop | Select-Object -First 1).Source
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) (
    "danmaku-libmpv-installer-test-" + [System.Guid]::NewGuid().ToString("N")
)
$fixturePath = Join-Path $testRoot "fixture"
$installPath = Join-Path $testRoot "install"
$archivePath = Join-Path $testRoot "fixture.7z"
$manifestPath = Join-Path $testRoot "manifest.json"
$packagedRoot = Join-Path $testRoot "packaged"
$packagedDependencyPath = Join-Path $packagedRoot "dependencies\libmpv"
$packagedAppPath = Join-Path $packagedRoot "app"

try {
    New-Item -ItemType Directory -Path $fixturePath -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $fixturePath "libmpv-2.dll") -Value "fixture dll"

    Push-Location $fixturePath
    try {
        & $sevenZip a $archivePath "libmpv-2.dll" | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Could not create installer fixture archive."
        }
    } finally {
        Pop-Location
    }

    $archiveHash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash
    $dllHash = (Get-FileHash -LiteralPath (Join-Path $fixturePath "libmpv-2.dll") -Algorithm SHA256).Hash
    $manifest = @{
        schemaVersion = 1
        dependencyName = "libmpv-2.dll"
        distributionModel = "optional-user-download"
        license = "LGPL-3.0-or-later"
        licenseUrl = "https://example.invalid/license"
        projectUrl = "https://example.invalid/project"
        releaseUrl = "https://example.invalid/release"
        archiveFileName = "fixture.7z"
        archiveUrl = "https://example.invalid/fixture.7z"
        archiveSha256 = $archiveHash
        dllArchivePath = "libmpv-2.dll"
        dllSha256 = $dllHash
    }
    $manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $manifestPath

    $licenseRejected = $false
    try {
        & $installer `
            -ManifestPath $manifestPath `
            -InstallPath $installPath `
            -ArchivePath $archivePath `
            -SevenZipExecutable $sevenZip
    } catch {
        $licenseRejected = $_.Exception.Message -like "*-AcceptLicense*"
    }
    if (-not $licenseRejected) {
        throw "Installer did not require explicit license acceptance."
    }

    & $installer `
        -ManifestPath $manifestPath `
        -InstallPath $installPath `
        -ArchivePath $archivePath `
        -SevenZipExecutable $sevenZip `
        -AcceptLicense

    $installedDll = Join-Path $installPath "libmpv-2.dll"
    if (-not (Test-Path -LiteralPath $installedDll -PathType Leaf)) {
        throw "Installer did not copy libmpv-2.dll."
    }
    if ((Get-FileHash -LiteralPath $installedDll -Algorithm SHA256).Hash -ne $dllHash) {
        throw "Installed libmpv-2.dll hash did not match the fixture."
    }

    New-Item -ItemType Directory -Path $packagedDependencyPath -Force | Out-Null
    New-Item -ItemType Directory -Path $packagedAppPath -Force | Out-Null
    Copy-Item -LiteralPath $installer -Destination $packagedDependencyPath
    Copy-Item `
        -LiteralPath $manifestPath `
        -Destination (Join-Path $packagedDependencyPath "zhongfly-lgpl-x86_64-20260708.json")

    & (Join-Path $packagedDependencyPath "install-libmpv-dependency.ps1") `
        -ArchivePath $archivePath `
        -SevenZipExecutable $sevenZip `
        -AcceptLicense

    if (-not (Test-Path -LiteralPath (Join-Path $packagedAppPath "libmpv-2.dll") -PathType Leaf)) {
        throw "Packaged installer did not install libmpv-2.dll into the app directory."
    }

    $manifest.archiveSha256 = ("0" * 64)
    $manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $manifestPath
    $tamperRejected = $false
    try {
        & $installer `
            -ManifestPath $manifestPath `
            -InstallPath $installPath `
            -ArchivePath $archivePath `
            -SevenZipExecutable $sevenZip `
            -AcceptLicense
    } catch {
        $tamperRejected = $_.Exception.Message -like "SHA-256 mismatch for archive*"
    }
    if (-not $tamperRejected) {
        throw "Installer did not reject a mismatched archive hash."
    }

    Write-Host "libmpv dependency installer self-test passed."
} finally {
    $resolvedTestRoot = [System.IO.Path]::GetFullPath($testRoot)
    $systemTemporaryRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    if (
        $resolvedTestRoot.StartsWith(
            $systemTemporaryRoot,
            [System.StringComparison]::OrdinalIgnoreCase
        ) -and
        (Test-Path -LiteralPath $resolvedTestRoot)
    ) {
        Remove-Item -LiteralPath $resolvedTestRoot -Recurse -Force
    }
}
