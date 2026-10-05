param(
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9]+$')][string]$TeamId,
    [Parameter(Mandatory)][string]$DeviceId,
    [switch]$FixturePlayback
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Push-Location $repo
try {
    Push-Location apps/ios
    try {
        & bundle exec pod install --deployment
        if ($LASTEXITCODE -ne 0) { throw 'CocoaPods dependency installation failed.' }
    } finally { Pop-Location }
    & xcodebuild -workspace apps/ios/Danmaku.xcworkspace -scheme Danmaku -configuration Debug -destination "platform=iOS,id=$DeviceId" -derivedDataPath build/ios-signed -allowProvisioningUpdates -allowProvisioningDeviceRegistration "DEVELOPMENT_TEAM=$TeamId" build
    if ($LASTEXITCODE -ne 0) { throw 'Signing/build failed. Sign into Xcode, select your Personal Team, and enable Developer Mode on the device.' }
    $app = Join-Path $repo 'build/ios-signed/Build/Products/Debug-iphoneos/Danmaku.app'
    & xcrun devicectl device install app --device $DeviceId $app
    if ($LASTEXITCODE -ne 0) { throw 'Installation failed. Check device trust, Developer Mode, and provisioning.' }
    if ($FixturePlayback) {
        & (Join-Path $PSScriptRoot 'make-ios-playback-fixtures.ps1')
        & xcrun devicectl device copy to --device $DeviceId --source build/ios-fixtures --destination Documents/Fixture --domain-type appDataContainer --domain-identifier app.danmaku.ios
        if ($LASTEXITCODE -ne 0) { throw 'Could not copy synthetic playback fixtures.' }
        foreach ($format in @('mp4', 'mkv')) {
            $runId = [Guid]::NewGuid().ToString()
            & xcrun devicectl device process launch --terminate-existing --device $DeviceId app.danmaku.ios -- --qa-fixture $format $runId
            if ($LASTEXITCODE -ne 0) { throw 'Fixture launch failed. Check certificate trust and Developer Mode.' }
            $report = Join-Path $repo "build/ios-fixture-device-$format.json"
            Remove-Item -LiteralPath $report -Force -ErrorAction SilentlyContinue
            $deadline = [DateTime]::UtcNow.AddSeconds(20)
            $result = $null
            do {
                Start-Sleep -Seconds 2
                & xcrun devicectl device copy from --quiet --device $DeviceId --source "Documents/Fixture/result-$format.json" --destination $report --domain-type appDataContainer --domain-identifier app.danmaku.ios
                if ($LASTEXITCODE -eq 0) { $result = Get-Content -LiteralPath $report -Raw | ConvertFrom-Json }
            } while ($result.runId -ne $runId -and [DateTime]::UtcNow -lt $deadline)
            if ($result.runId -ne $runId) { throw "No current $format playback report arrived from the device." }
            if ($result.playing -ne 'true' -or [long]$result.positionMs -lt 1000 -or [long]$result.decodedVideoFrames -le 0 -or [long]$result.displayedVideoFrames -le 0 -or [long]$result.audioTracks -lt 2 -or [long]$result.subtitleTracks -lt 2 -or $result.error -ne '') { throw "The $format fixture did not play successfully." }
            Write-Host "$format fixture played: $($result.displayedVideoFrames) displayed video frames, $($result.audioTracks) audio tracks, $($result.subtitleTracks) subtitle tracks. Report: $report"
        }
    }
    & xcrun devicectl device process launch --terminate-existing --device $DeviceId app.danmaku.ios
    if ($LASTEXITCODE -ne 0) { throw 'Launch failed. Check developer certificate trust on the device.' }
} finally { Pop-Location }
