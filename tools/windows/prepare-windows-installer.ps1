[CmdletBinding()]
param(
    [string]$PlayerStagePath,
    [string]$OutputDirectory = (Join-Path $PSScriptRoot "..\..\build\release\windows-installer"),
    [string]$ReleaseNotesPath,
    [string]$VpkPath = "vpk",
    [string]$SigningPfxPath,
    [string]$SigningPfxPassword = $env:WINDOWS_SIGNING_PFX_PASSWORD,
    [switch]$RequireSigning
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
Push-Location $repoRoot
try {
    $metadataJson = & cargo metadata --format-version 1 --locked --no-deps
    if ($LASTEXITCODE -ne 0) {
        throw "cargo metadata failed."
    }
} finally {
    Pop-Location
}
$metadata = $metadataJson | ConvertFrom-Json
$playerPackage = $metadata.packages | Where-Object { $_.name -eq "danmaku-player" } | Select-Object -First 1
if ($null -eq $playerPackage) {
    throw "Cargo package 'danmaku-player' was not found."
}
$version = [string]$playerPackage.version
if ([string]::IsNullOrWhiteSpace($PlayerStagePath)) {
    $PlayerStagePath = Join-Path $repoRoot "build\release\rust-player\danmaku-player-$version-windows-x64"
}
$stagePath = [System.IO.Path]::GetFullPath($PlayerStagePath)
$outputPath = [System.IO.Path]::GetFullPath($OutputDirectory)
$allowedOutput = [System.IO.Path]::GetFullPath((Join-Path $repoRoot "build\release\windows-installer"))
$allowedPrefix = $allowedOutput.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
if ($outputPath -ne $allowedOutput -and -not $outputPath.StartsWith($allowedPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Installer output must remain inside $allowedOutput"
}
foreach ($requiredPath in @(
    (Join-Path $stagePath "danmaku-player.exe"),
    (Join-Path $stagePath "library-server.exe"),
    (Join-Path $stagePath "libmpv-2.dll"),
    (Join-Path $stagePath "web\index.html")
)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Verified player stage is incomplete: $requiredPath"
    }
}
if (-not [string]::IsNullOrWhiteSpace($ReleaseNotesPath) -and -not (Test-Path -LiteralPath $ReleaseNotesPath -PathType Leaf)) {
    throw "Release notes do not exist: $ReleaseNotesPath"
}
if ($RequireSigning -and ([string]::IsNullOrWhiteSpace($SigningPfxPath) -or [string]::IsNullOrWhiteSpace($SigningPfxPassword))) {
    throw "Production installer packaging requires SigningPfxPath and SigningPfxPassword."
}
if (-not [string]::IsNullOrWhiteSpace($SigningPfxPath) -and -not (Test-Path -LiteralPath $SigningPfxPath -PathType Leaf)) {
    throw "Signing certificate does not exist: $SigningPfxPath"
}

New-Item -ItemType Directory -Path $outputPath -Force | Out-Null
$libmpvPath = Join-Path $stagePath "libmpv-2.dll"
$libmpvHashBefore = (Get-FileHash -LiteralPath $libmpvPath -Algorithm SHA256).Hash
$arguments = @(
    "-y", "--skip-updates", "true", "pack",
    "--packId", "app.danmaku.player",
    "--packVersion", $version,
    "--packDir", $stagePath,
    "--mainExe", "danmaku-player.exe",
    "--packTitle", "Danmaku",
    "--packAuthors", "Danmaku",
    "--runtime", "win-x64",
    "--channel", "win-x64-stable",
    "--outputDir", $outputPath,
    "--icon", (Join-Path $repoRoot "native\player-app\assets\app.ico"),
    "--noPortable", "true",
    "--signExclude", "(?i)libmpv-2\.dll$"
)
if (-not [string]::IsNullOrWhiteSpace($ReleaseNotesPath)) {
    $arguments += @("--releaseNotes", [System.IO.Path]::GetFullPath($ReleaseNotesPath))
}

$previousSignParams = $env:VPK_SIGN_PARAMS
try {
    if (-not [string]::IsNullOrWhiteSpace($SigningPfxPath)) {
        $pfx = [System.IO.Path]::GetFullPath($SigningPfxPath)
        $env:VPK_SIGN_PARAMS = "/td sha256 /fd sha256 /f `"$pfx`" /p `"$SigningPfxPassword`" /tr http://timestamp.digicert.com"
    } else {
        Remove-Item Env:VPK_SIGN_PARAMS -ErrorAction SilentlyContinue
    }
    & $VpkPath @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Velopack installer packaging failed with exit code $LASTEXITCODE."
    }
} finally {
    if ($null -eq $previousSignParams) {
        Remove-Item Env:VPK_SIGN_PARAMS -ErrorAction SilentlyContinue
    } else {
        $env:VPK_SIGN_PARAMS = $previousSignParams
    }
}

$libmpvHashAfter = (Get-FileHash -LiteralPath $libmpvPath -Algorithm SHA256).Hash
if ($libmpvHashAfter -ne $libmpvHashBefore) {
    throw "Installer packaging modified the separately licensed libmpv binary."
}
$setup = Get-ChildItem -LiteralPath $outputPath -Filter "*-Setup.exe" -File | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
$fullPackage = Get-ChildItem -LiteralPath $outputPath -Filter "*-full.nupkg" -File | Where-Object { $_.Name -like "*-$version-*" } | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
$feed = Join-Path $outputPath "releases.win-x64-stable.json"
if ($null -eq $setup -or $null -eq $fullPackage -or -not (Test-Path -LiteralPath $feed -PathType Leaf)) {
    throw "Velopack did not produce the Setup executable, full update package, and stable feed."
}
$feedDocument = Get-Content -LiteralPath $feed -Raw | ConvertFrom-Json
$feedAsset = @($feedDocument.Assets) | Where-Object { $_.FileName -eq $fullPackage.Name } | Select-Object -First 1
$fullPackageHash = (Get-FileHash -LiteralPath $fullPackage.FullName -Algorithm SHA256).Hash
if ($null -eq $feedAsset -or [string]$feedAsset.SHA256 -ne $fullPackageHash) {
    throw "Stable feed does not contain the current full package with its exact SHA-256 hash."
}
if ($RequireSigning) {
    $signature = Get-AuthenticodeSignature -LiteralPath $setup.FullName
    if ($signature.Status -ne "Valid") {
        throw "Installer Authenticode signature is not valid: $($signature.StatusMessage)"
    }
    $verificationDirectory = Join-Path $outputPath ("signature-verification-" + [Guid]::NewGuid().ToString("N"))
    try {
        [System.IO.Compression.ZipFile]::ExtractToDirectory($fullPackage.FullName, $verificationDirectory)
        $packagedApp = Join-Path $verificationDirectory "lib\app"
        foreach ($executableName in @(
            "danmaku-player.exe",
            "library-server.exe",
            "danmaku-player_ExecutionStub.exe",
            "Squirrel.exe"
        )) {
            $executablePath = Join-Path $packagedApp $executableName
            $executableSignature = Get-AuthenticodeSignature -LiteralPath $executablePath
            if ($executableSignature.Status -ne "Valid") {
                throw "Packaged $executableName Authenticode signature is not valid: $($executableSignature.StatusMessage)"
            }
        }
        $packagedLibmpvHash = (Get-FileHash -LiteralPath (Join-Path $packagedApp "libmpv-2.dll") -Algorithm SHA256).Hash
        if ($packagedLibmpvHash -ne $libmpvHashBefore) {
            throw "The full update package does not preserve the release-resolved libmpv hash."
        }
    } finally {
        if (Test-Path -LiteralPath $verificationDirectory) {
            Remove-Item -LiteralPath $verificationDirectory -Recurse -Force
        }
    }
}

[pscustomobject]@{
    version = $version
    channel = "win-x64-stable"
    setup = $setup.FullName
    fullPackage = $fullPackage.FullName
    feed = $feed
} | ConvertTo-Json -Depth 3
