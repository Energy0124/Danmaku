# iPhone / iPad Client Implementation

Implementation branch: `codex/ios-ipad`, based on current `origin/main`.

## Decisions

- Native SwiftUI universal application, iOS/iPadOS 17+, with a Swift core.
- MobileVLCKit 3.7.2, CocoaPods 1.16.2, committed Xcode project/workspace/scheme,
  and locked CocoaPods/Ruby dependencies. Android modules remain unchanged.
- Share normalized wire contracts and canonical Android/Rust test fixtures;
  no native per-frame/per-comment bridge and no second desktop/server runtime.
- Bonjour service `_danmaku._tcp.local.` supplements the server's UDP discovery.
  Apple clients use declared Bonjour browsing and manual/saved addresses.
- Media Last-Modified/ETag/If-Range enables native URLSession download resume;
  precise tags distinguish same-second source modifications.
- Free Personal Team development installation on a paired device; distribution
  through App Store/TestFlight is outside this implementation.

## Delivered Behaviors

Library/Home/series/folders, posters, search and favorite/watch filters, rescans,
streaming and app-private cached playback, Files import, fullscreen, previous/next,
seeking, playback speed, audio and embedded/sidecar subtitle controls, and resume.
Danmaku is batched into collision-safe scrolling/fixed lanes and rendered against
the playback clock with durable per-type, appearance, density, area, and offset
settings. English and Traditional Chinese resources cover application text.

Background URLSession owns serialized video/subtitle/poster transfers. Catalog
metadata and resolved comments are captured with each explicit download snapshot.
The cache manifest stores resume data, task identity, asset completion, and queue
state atomically. Pause/retry/cancel/delete/clear controls respect active playback.
Progress is journaled independently and only reconciled with its originating
server. Provider account status, readback, and preview review use existing server
routes; confirmed writes preserve the exact preview object and reject stale previews.

## Verification Record

- Swift core: 10 tests passed against the Rust LAN and Android grouping,
  continue-watching, next-up, and watch-state fixtures, plus persistence,
  checkpoint isolation, stale responses, and collision scheduling.
- Rust formatting/workspace tests passed after Bonjour and resume-validator changes
  (261 unit tests, plus empty binary/doc-test targets).
- Unsigned physical-device build passed.
- iPad simulator (iPadOS 26.5): 9 application tests passed, including stale
  connection rejection, interrupted download relaunch, byte-range resume, changed
  source dates/tags, storage failures, optional assets, and cache deletion guards.
- iPhone simulator (iOS 26.5): the reproducible test script passed all 10 core
  and 9 application tests with locked dependencies and regenerated media.
  MP4/MKV local and loopback streaming, two audio tracks, SRT/ASS selection,
  seek, pause, rate, and resumed clocks were verified.
- Debug-only device fixture launch was exercised headlessly on the iPad simulator;
  MP4/MKV reports confirmed playback with two audio tracks and sidecar subtitles.
  The strengthened probe also requires decoded and displayed video frames;
  the playback suite passed with these checks before physical deployment.
  It uses isolated state and skips saved-library connections. The deployment tool
  copies synthetic fixtures and retrieves playback reports without screenshots.
- Download/tracking tests use a loopback-only fixture HTTP server. Generated
  fixture media and test results live under ignored `build/` directories.
- Signed installation and normal launch passed on the Energy iPad Pro.
  Synthetic MP4 reported 114 decoded/34 displayed video frames, two audio tracks,
  and four subtitle tracks; MKV reported 102 decoded/28 displayed frames, two
  audio tracks, and five subtitle tracks. Both advanced past one second without
  errors. Fixture runs used isolated state; the tool then launched the normal app.
- No real-library, live provider account, or desktop screenshot QA was performed.

## Remaining Gates

- Confirm portrait/landscape/narrow iPad multitasking, iPhone compact navigation,
  physical decoder performance, permissions, and background suspension/resume.
- Native resume data is owned by iOS; if it becomes unavailable, retry restarts
  that asset. Force-quitting requires opening the app before background relaunch.
- App Store/TestFlight packaging, PiP, AirPlay, Apple TV, and release promotion
  are deferred.

## Evidence and Handoff

Ignored local verification output:
`build/ios-core-tests-final.log`, `build/ios-rust-tests-final.log`,
`build/ios-device-build-final.log`, `build/ios-ipad-tests-final.xcresult`,
`build/ios-iphone-tests.xcresult`, `build/ios-reproducible-tests.log`,
`build/ios-fixture-simulator-{mp4,mkv}.json`, `build/ios-device-handoff-tests.log`,
`build/ios-device-build-handoff.log`, `build/ios-device-deployment-final.log`,
and `build/ios-fixture-device-{mp4,mkv}.json`.
The GitHub macOS job builds unsigned device output and runs the same fixture suite;
CI itself has not run for this local branch yet.

The paired Energy iPad Pro (12.9-inch, fifth generation) is running iPadOS 26.5.
Developer Mode is now enabled and an Apple Development certificate exists.
The initial signing attempt rejected the saved Apple account session; the user
reauthenticated and created the certificate. The signed retry succeeded and
installed the application. Local signature validation passed, and the provisioning
profile includes the paired device. After the user trusted the developer profile
on iPadOS, both fixture launches and the final normal app launch succeeded.
The local provisioning profile expires October 12, 2026. Run the documented deploy
command with the Personal Team and paired device identifier to renew it.
Reinstall the same bundle/team without
uninstalling to preserve app data; Personal Team profiles need renewal after seven
days. Real-library/live-provider and visual QA remain separately approved gates.

## Connection Protocol Follow-up

The first deployed iOS build required `appName` and `apiVersion` in server status.
Rust intentionally omits those fields when they match LAN v1 defaults, so valid
servers failed with a missing-data decoding error before the catalog request.
The iOS decoder now applies the documented app name, API version, streaming, and
scan defaults while preserving explicit values and rejecting unsupported versions.
The connection regression uses the committed Rust status/catalog/progress responses;
the application stale-connection fixture also returns omitted default status fields.
Verification passed all 12 Swift core tests, all 9 iPad application tests, and an
unsigned device build. Logs are under `build/ios-status-{core-tests,app-tests,device-build}.log`.
The signed update installed without uninstalling the app; launch was initially
blocked by the iPad lock screen and succeeded after the user unlocked it.
Deployment output is recorded in `build/ios-status-device-deployment.log` and
`build/ios-status-device-launch.json`. No real-library or live-account QA was run.
