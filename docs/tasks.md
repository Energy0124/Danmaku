# Tasks

This is the canonical high-level backlog. Detailed implementation records live
under `docs/design/`.

Status: `[x]` done, `[~]` in progress, `[ ]` not started.

## Active Priorities

- `[x]` Retire the Kotlin Compose desktop app, JVM library server/host modules,
  JNA bridge, legacy database importer, compatibility artifact, and macOS
  desktop job. Rust is the only desktop player/server implementation.
- `[x]` Add the desktop progressive library organizer: per-series review,
  editable title/season, opt-in nearby files, exact approval, durable rollback,
  stable catalog IDs, cancellation, and undo without overwrite or deletion.
- `[x]` Complete the Android TV single-cutover presentation rewrite with
  lifecycle-owned state, cached/off-composition catalog derivation,
  non-blocking danmaku, typed navigation, D-pad/focus coverage, screenshots,
  Macrobenchmarks, and Baseline Profiles.
- `[~]` Close remaining Windows native release QA: fullscreen restore, resize,
  aspect, track selection, hardware decode, 4K duration, multiple displays,
  resume, and background-host ownership.
- `[x]` Add a per-user Windows installer, stable-channel auto-updater,
  localized release-note approval UX, signing-gated SemVer release workflow,
  checksums, and background-host binary refresh that preserves data.
- `[~]` Complete live MyAnimeList/Bangumi readback and deliberate write QA.
  `tools/windows/run-live-external-sync-readback-qa.ps1` is read-only; every
  provider write still requires explicit preview acknowledgement and approval.
- `[x]` Add native Windows account connection, series mapping, readback,
  conflict import, preview/confirm sync, and episode-completion prompts; keep
  the web UI as an advanced mirror instead of the only usable surface.
- `[x]` Add Android mobile/TV tracking status, provider readback, full progress
  preview, and explicit exact-preview sync while keeping administration on
  Windows/web.
- `[~]` Complete one budget-class physical Android TV pass for safe areas,
  focus, responsiveness, and real-LAN playback.
- `[~]` Continue Android mobile/TV and native Windows library polish where
  title clarity, poster state, resume, search, and focus affect daily use.
- `[~]` Harden the experimental Rust macOS player: provider HTTPS, protected
  token storage, reviewed libmpv distribution policy, signing/notarization,
  and supervised Apple Silicon/Intel playback QA remain.

## Product Backlog

- `[ ]` Add per-series playback preferences for subtitle/audio tracks,
  subtitle requirement, rate, danmaku visibility, resume, and auto-next.
- `[ ]` Add manual OP/ED/recap markers followed by persisted reuse.
- `[ ]` Add richer danmaku blocklists, offsets, density/style presets, and
  quiet-mode controls across active clients.
- `[ ]` Improve metadata with alternate titles, studios, genres/tags, source,
  season/year, episode counts, and specials/OVA/movie ordering.
- `[ ]` Add favorites, watch-later, custom collections, and useful smart
  filters against normalized server-owned state.
- `[~]` Keep the Android mobile offline queue and ANI-RSS automatic-download
  administration reliable; TV subscription management and a Danmaku-owned
  native download engine remain unimplemented.
- `[ ]` Add notification surfaces for newly indexed episodes, provider
  failures, tracking conflicts, and server availability.

## Quality Gates

- Shared domain and LAN-client changes require their focused JVM tests.
- Rust player/server changes require `cargo fmt --all --check` and
  `cargo test --workspace`; server route changes should also run
  `tools/windows/run-headless-web-ui-qa.ps1` when practical.
- Web UI changes require `npm run build` in `apps/web-ui`.
- Android builds require the narrowest applicable unit/build tasks. Connected
  tests require an available emulator/device and must not be assumed.
- TV UI changes should include D-pad/focus instrumentation and benchmark
  coverage where practical.
- GUI playback, emulator control, real-library scanning, and live-provider QA
  remain explicitly supervised.

## Standard Verification

```powershell
cargo fmt --all --check
cargo test --workspace
.\gradlew.bat --no-daemon :shared:domain:jvmTest :shared:library-client:jvmTest :shared:library-client-android:testDebugUnitTest :shared:player-android-media3:assembleDebugAndroidTest :apps:android-mobile:assembleDebug :apps:android-tv:assembleDebug
```

Connected Android checks:

```powershell
.\gradlew.bat --no-daemon :shared:player-android-media3:connectedDebugAndroidTest
.\gradlew.bat --no-daemon :apps:android-mobile:connectedDebugAndroidTest
.\gradlew.bat --no-daemon :apps:android-tv:connectedDebugAndroidTest
```
