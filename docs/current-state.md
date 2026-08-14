# Current State

Last reviewed: 2026-08-07.

Danmaku's active product is a Rust-native Windows library/player/server with
Android mobile, Android TV, and browser clients on the same trusted-LAN API.
The Kotlin Compose desktop app, JVM server/host modules, JNA bridge, legacy
desktop database importer, and experimental macOS artifact are retired.

## Implemented

### Rust Windows Player And Server

- egui/glow player with embedded libmpv video, playback controls, fullscreen,
  tracks, seeking, playback rate, native danmaku, local overlay attachment,
  discovery, library browsing, progress/resume, previous/next, and auto-next.
- English and Traditional Chinese UI, durable playback/danmaku preferences,
  remembered local roots and server URL, and session-only pairing tokens.
- Unified local mode that starts the sibling Rust server, waits asynchronously
  for readiness, connects, and stops only a child it owns.
- Optional current-user Task Scheduler background host with install, root
  management, start/stop/status, uninstall, and non-mutating plan checks.
- Multi-root scanning, normalized catalog snapshots, subtitles, posters,
  streaming/range requests, progress, UDP discovery, and data-directory locks.
- dandanplay matching/comment cache and repair status; provider metadata,
  settings, secret storage, external mappings, list readback, conflict-aware
  previews, and explicitly acknowledged MAL/Bangumi writes.
- Native Windows Accounts & Tracking UI with MAL loopback OAuth/refresh,
  guided Bangumi token validation, series search/mapping, deliberate sync,
  provider-ahead local import, and an episode-completion review prompt.
- `/web/` administration and a standalone server package.

### Android Mobile And TV

- Discovery/manual connection, catalog browsing, series/episode presentation,
  Media3 streaming, subtitles, playback progress, resume, and danmaku.
- Native MAL/Bangumi account status, provider readback, exact progress preview,
  and explicitly confirmed sync; account/mapping/conflict administration stays
  on Windows and the web UI.
- Dedicated Android TV navigation and D-pad focus, cached-first presentation,
  latest-request-wins refresh handling, queued playback startup, and benchmark
  journeys.
- Shared domain, LAN-client, and Media3 modules without a JVM server runtime
  dependency.

### Web UI

- Catalog playback and progress behavior.
- Provider account status/guided Bangumi connection, advanced endpoint
  settings, persistent mapping search, tracking readback, provider-ahead
  import, conflict-aware sync preview, and acknowledged writes. The former
  per-episode direct list editor is no longer exposed.
- Repeatable fixture-backed Rust server and headless browser QA.

### Packaging And CI

- Versioned native Windows player and standalone server zips with web assets,
  pinned libmpv, provenance, licenses, and generated dependency inventories.
- Windows CI for Rust, Android, web assets, packaging, and libmpv checks;
  separate Rust and Worker proxy jobs.
- No Compose desktop, JVM host, Java runtime, JNA DLL, or macOS desktop build.

## Partial Or Pending

- Supervised Windows fullscreen, multi-display, hardware decode, and broader
  real-media release matrices still require manual QA.
- Live MyAnimeList/Bangumi account read/write QA requires explicit approval and
  credentials.
- Android mobile/tablet viewport and replacement-class physical TV validation
  remain release gates.
- Richer danmaku filters/offsets, per-series playback preferences, metadata
  depth, collections, and notification surfaces remain planned.
- Authorized download execution is not implemented in an active application.

## Compatibility Notes

- Existing Compose desktop database files are left untouched but are not read
  or imported. Users configure roots again and create a fresh Rust catalog.
- macOS has no supported desktop build.
- LAN API/discovery version 1 remains compatible with active Android clients.

## Standard Verification

```powershell
cargo fmt --all --check
cargo test --workspace
.\gradlew.bat --no-daemon :shared:domain:jvmTest :shared:library-client:jvmTest :shared:library-client-android:testDebugUnitTest :shared:player-android-media3:assembleDebugAndroidTest :apps:android-mobile:assembleDebug :apps:android-tv:assembleDebug
cd apps\web-ui
npm run build
```

Connected Android, GUI playback, emulator, real-library, and live-provider QA
remain supervised/approval-gated checks.
