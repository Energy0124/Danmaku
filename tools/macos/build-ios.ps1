param([ValidateSet('Device', 'Simulator')][string]$Platform = 'Device')
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Push-Location $repo
try {
    Push-Location apps/ios
    try {
        & bundle exec pod install --deployment
        if ($LASTEXITCODE -ne 0) { throw 'CocoaPods dependency installation failed. Run bundle install first.' }
    } finally { Pop-Location }
    $sdk = if ($Platform -eq 'Device') { 'iphoneos' } else { 'iphonesimulator' }
    $destination = if ($Platform -eq 'Device') { 'generic/platform=iOS' } else { 'generic/platform=iOS Simulator' }
    $derived = "build/ios-$($Platform.ToLowerInvariant())"
    & xcodebuild -workspace apps/ios/Danmaku.xcworkspace -scheme Danmaku -configuration Debug -sdk $sdk -destination $destination -derivedDataPath $derived CODE_SIGNING_ALLOWED=NO build
    if ($LASTEXITCODE -ne 0) { throw 'iOS build failed.' }
} finally { Pop-Location }
