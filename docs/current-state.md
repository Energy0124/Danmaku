# Current State

Updated on 2026-06-03.

## Workspace

The canonical checkout is:

```text
S:\Projects\Danmaku
```

The previous `C:\Users\energy\OneDrive\Documents\Danmaku` directory is an
empty placeholder retained by an open desktop-session handle. It is not a Git
checkout and must not be used for project commands.

## Git

Current branch:

```text
codex/windows-playback-foundation
```

Committed checkpoints:

```text
991e4f4 feat: add Windows playback foundation
6ee283a chore: establish danmaku project foundation
```

## Implemented

- Gradle 9.4.1 wrapper and Kotlin 2.3.21 Multiplatform foundation.
- Shared Kotlin domain models for danmaku events, playback commands, playback
  sources, snapshots, and the platform-neutral playback controller contract.
- Dependency-free Rust danmaku timeline index with half-open window queries.
- Dependency-free Windows libmpv loader with DLL discovery, required-symbol
  loading, mpv context initialization, command dispatch, and clean shutdown.
- `mpv-probe` executable for validating an audited `libmpv-2.dll`.
- Desktop mpv command planner for loading local files or LAN streams and
  dispatching play, pause, absolute seek, and playback-rate commands before the
  Kotlin-to-native controller bridge is wired.
- Desktop `PlaybackController` wrapper that executes planned mpv commands through
  an injectable command executor and tracks testable snapshot state for local
  files and LAN streams.
- Shared scrolling danmaku lane scheduler with collision-aware tests, bounded
  visible-window lookup, backward-seek query coverage, and a 10,000-comment
  generated-track test.
- Compose Multiplatform 1.11.0 Windows desktop shell with a synthetic animated
  overlay demo backed by the shared scheduler.
- Recursive Windows anime-folder indexer and trusted-LAN HTTP server exposing a
  paired normalized JSON catalog plus paired seekable byte-range media
  responses.
- Compose-free `shared/library-server-core` JVM module containing the reusable
  paired HTTP server, progress-store contract, verified media-ID publication
  boundary, and UDP discovery announcer. The Windows desktop shell starts it in
  embedded mode.
- File-backed Windows library-folder selection, startup index restoration, and
  one-click rescanning.
- SQLDelight 2.3.2 SQLite catalog persistence with immediate cached serving on
  startup and unchanged-file reuse during background rescans.
- Durable per-episode playback progress in the desktop SQLite database with a
  paired LAN `GET`/`PUT /api/progress/{id}` contract and Android client methods.
- Desktop SQLDelight storage primitives for app settings and download queue
  items, with compatibility creation for existing catalog databases.
- Android mobile and TV progress syncing from the background playback service
  with five-second uploads, resume seeking after 10 seconds, and near-end
  episode restart behavior.
- Windows UDP announcements and Android discovery actions for finding the
  library server on the local network without typing its IP address.
- Windows distributable includes the `jdk.httpserver` and `java.sql` runtime
  modules required by the packaged LAN server and SQLite catalog.
- Android mobile and dedicated Android TV Compose application modules.
- Shared Android Media3 ExoPlayer adapter, foreground playback service, and
  service-backed MediaController connection used by mobile and TV.
- Android catalog client used by mobile and TV to browse the Windows index and
  stream selected episodes.
- Shared Kotlin `shared/library-client` contract for catalog browsing,
  stream-URL generation, progress upload, and resume lookup. Its JVM source set
  includes the HTTP adapter used by the Windows shell for same-PC or remote
  paired-server browsing and stream selection. Android HTTP and UDP discovery
  remain platform adapters. JVM and Android LAN clients use configurable HTTP
  connect/read timeouts with stable production defaults.
- Shared LAN playback preparation converts a paired catalog item into a
  tokenized `RemoteStream` plus resume position; the Windows shell uses it for
  same-PC or remote paired-server playback handoff state while native libmpv
  rendering is still pending.
- Android TV launch focus on `Discover PC` plus API 34 emulator-verified Compose
  instrumentation coverage for the initial focus and left-arrow path. The TV
  row uses an explicit focus graph for deterministic remote navigation.
- Workspace-local ignored Android SDK with API 36, Build Tools 36.0.0, and
  platform tools. The ignored SDK also has an API 34 emulator image for runtime
  instrumentation. `local.properties` is ignored and points to that SDK.
- Architecture decisions for Kotlin with focused Rust and audited libmpv
  distribution.
- Same-PC Windows host integration coverage for paired catalog requests,
  complete file reads, byte ranges, unauthorized media rejection,
  unsatisfiable ranges, and progress round trips.
- JVM client-to-server loopback coverage for paired catalog browsing, generated
  stream consumption, token encoding, missing progress, and progress round trips.
- JVM LAN client recovery coverage for an interrupted catalog connection that
  reconnects and completes on a fresh socket.
- JVM LAN client timeout coverage for slow catalog responses.
- Reusable LAN server reliability coverage for unauthorized media requests,
  malformed and unsatisfiable byte ranges, valid open-ended and suffix ranges,
  multi-megabyte media, concurrent streams, and sequential pause, seek, and
  completion-style progress updates.
- Android HTTP adapter loopback coverage against a live local server for paired
  catalog browsing, generated stream consumption, and progress round trips.
- API 34 emulator-verified Android Media3 instrumentation coverage with a
  deterministic one-second MP4 asset and loopback HTTP server, including
  service-owned progress upload after the UI controller connection closes and
  slow chunked HTTP media playback.

## Verification

Run these commands after architecture or build changes:

```powershell
cargo fmt --all --check
cargo test --workspace
.\gradlew.bat --no-daemon :shared:domain:jvmTest
.\gradlew.bat --no-daemon :shared:library-client:jvmTest
.\gradlew.bat --no-daemon :shared:library-server-core:jvmTest
.\gradlew.bat --no-daemon :apps:desktop-windows:desktopTest
.\gradlew.bat --no-daemon :shared:player-android-media3:assembleDebugAndroidTest
.\gradlew.bat --no-daemon :apps:android-mobile:assembleDebug :apps:android-tv:assembleDebug
```

With an Android emulator or device online, run:

```powershell
.\gradlew.bat --no-daemon :shared:player-android-media3:connectedDebugAndroidTest
.\gradlew.bat --no-daemon :apps:android-tv:connectedDebugAndroidTest
```

## Next Work

1. Exercise cross-device resume behavior on Android and TV hardware.
2. Select an audited Windows libmpv DLL bundle and run `mpv-probe`.
3. Implement the native Windows libmpv command executor for the desktop
   `PlaybackController`.
4. Connect native Windows video rendering and local-file playback.

## Runtime Smoke Check

The packaged Windows executable was launched after adding pairing to the LAN
server. Its Compose window opened successfully, displayed the generated
pairing code, rejected an unpaired `GET http://127.0.0.1:8686/api/library`
request with HTTP `401`, and returned the expected empty initial catalog when
the displayed code was supplied. The packaged runtime was launched again after
adding SQLDelight storage; its paired endpoint started successfully from the
trimmed runtime image.
