param([Parameter(Mandatory)][string]$SimulatorId)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Push-Location $repo
try {
    & swift test --package-path apps/ios --scratch-path build/ios-swift
    if ($LASTEXITCODE -ne 0) { throw 'Swift core tests failed.' }
    & (Join-Path $PSScriptRoot 'make-ios-playback-fixtures.ps1')
    Push-Location apps/ios
    try {
        & bundle exec pod install --deployment
        if ($LASTEXITCODE -ne 0) { throw 'CocoaPods dependency installation failed.' }
    } finally { Pop-Location }
    $results = "build/ios-tests-$([DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss')).xcresult"
    & xcodebuild -workspace apps/ios/Danmaku.xcworkspace -scheme Danmaku -configuration Debug -destination "platform=iOS Simulator,id=$SimulatorId" -derivedDataPath build/ios-simulator -resultBundlePath $results -parallel-testing-enabled NO test
    if ($LASTEXITCODE -ne 0) { throw 'iOS application tests failed.' }
} finally { Pop-Location }
