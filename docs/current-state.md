# Current State

Updated on 2026-06-02.

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
- Shared scrolling danmaku lane scheduler with collision-aware tests, bounded
  visible-window lookup, backward-seek query coverage, and a 10,000-comment
  generated-track test.
- Compose Multiplatform 1.11.0 Windows desktop shell with a synthetic animated
  overlay demo backed by the shared scheduler.
- Recursive Windows anime-folder indexer and trusted-LAN HTTP server exposing a
  paired normalized JSON catalog plus paired seekable byte-range media
  responses.
- File-backed Windows library-folder selection, startup index restoration, and
  one-click rescanning.
- SQLDelight 2.3.2 SQLite catalog persistence with immediate cached serving on
  startup and unchanged-file reuse during background rescans.
- Windows UDP announcements and Android discovery actions for finding the
  library server on the local network without typing its IP address.
- Windows distributable includes the `jdk.httpserver` and `java.sql` runtime
  modules required by the packaged LAN server and SQLite catalog.
- Android mobile and dedicated Android TV Compose application modules.
- Shared Android Media3 ExoPlayer adapter, foreground playback service, and
  service-backed MediaController connection used by mobile and TV.
- Android catalog client used by mobile and TV to browse the Windows index and
  stream selected episodes.
- Android TV launch focus on `Discover PC` plus compiled Compose instrumentation
  coverage for the initial focus and left-arrow path.
- Workspace-local ignored Android SDK with API 36, Build Tools 36.0.0, and
  platform tools. `local.properties` is ignored and points to that SDK.
- Architecture decisions for Kotlin with focused Rust and audited libmpv
  distribution.

## Verification

Run these commands after architecture or build changes:

```powershell
cargo fmt --all --check
cargo test --workspace
.\gradlew.bat --no-daemon :shared:domain:jvmTest
.\gradlew.bat --no-daemon :apps:desktop-windows:desktopTest
.\gradlew.bat --no-daemon :apps:android-mobile:assembleDebug :apps:android-tv:assembleDebug
```

## Next Work

1. Run TV D-pad instrumentation tests on an emulator or physical TV device.
2. Extend SQLDelight storage for playback progress, settings, and downloads.
3. Select an audited Windows libmpv DLL bundle and run `mpv-probe`.
4. Connect native Windows video rendering and local-file playback.
5. Add playback-resumption metadata and persistence for Android and TV.

## Runtime Smoke Check

The packaged Windows executable was launched after adding pairing to the LAN
server. Its Compose window opened successfully, displayed the generated
pairing code, rejected an unpaired `GET http://127.0.0.1:8686/api/library`
request with HTTP `401`, and returned the expected empty initial catalog when
the displayed code was supplied. The packaged runtime was launched again after
adding SQLDelight storage; its paired endpoint started successfully from the
trimmed runtime image.
