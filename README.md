# Danmaku

[![CI](https://github.com/Energy0124/Danmaku/actions/workflows/ci.yml/badge.svg)](https://github.com/Energy0124/Danmaku/actions/workflows/ci.yml)

Danmaku is an early-stage cross-platform anime media app for local libraries,
trusted-LAN streaming, synchronized watch progress, and scrolling danmaku
overlays.

The first-class targets are:

- Windows desktop
- Android phones and tablets
- Android TV

The project uses Kotlin and Compose for app code, Media3 ExoPlayer on Android
and Android TV, libmpv on Windows, SQLDelight/SQLite for durable local state,
and focused Rust components where native code earns its boundary.

## Status

This repository is in active foundation work. It is not a finished media player
yet.

Implemented today:

- Windows Compose desktop shell
- Recursive Windows anime-folder indexing
- Multiple Windows library roots, including user-selected ani-rss output-folder
  import and incremental rescanning
- Optional read-only Windows ani-rss monitoring adapter
- Authenticated ani-rss completion webhook for bounded output-folder rescans
- SQLDelight catalog, playback-progress, settings, and download-queue storage
- Paired trusted-LAN library server with JSON catalog, byte-range media
  streaming, and progress API
- UDP discovery for finding the Windows library server from Android clients
- Android mobile and Android TV apps that browse the Windows catalog and stream
  selected episodes through Media3
- Android background playback service with resume lookup and periodic progress
  upload
- Shared Kotlin playback, library-client, and danmaku scheduling contracts
- Desktop preparation for local-file and paired-LAN playback requests, including
  resume seeks and planned mpv command output
- Rust libmpv loader/probe plus a small C ABI and Kotlin/JNA binding for the
  future Windows native command executor
- Tested LAN server/client behavior, byte ranges, progress updates, reconnects,
  timeouts, slow streams, and Android/TV instrumentation paths

Not implemented yet:

- Real Windows video rendering in the Compose shell
- Audited/pinned Windows libmpv runtime bundle
- Subtitle streaming and subtitle selection
- Authorized download engine
- Provider plugins, MyAnimeList integration, and danmaku provider integrations

## Architecture

```text
apps/
  desktop-windows/       Compose Multiplatform Windows shell
  android-mobile/        Android phone/tablet app
  android-tv/            Dedicated Android TV app

shared/
  domain/                Core models and playback contracts
  library-server-core/   Reusable trusted-LAN library server
  library-client/        Shared LAN client and progress-sync policy
  library-client-android Android HTTP/discovery adapter
  player-android-media3/ Shared Media3 playback adapter/service

native/
  rust-core/             Focused Rust timeline/indexing work
  player-windows-mpv/    Windows libmpv loader, probe, and C ABI

docs/
  Architecture, roadmap, task backlog, and decisions
```

The Windows desktop app currently acts as the embedded library server. Android
and Android TV are clients for that server. A separate headless Windows server
is planned only after API, lifecycle, diagnostics, settings, and firewall
behavior are stable.

For more detail, see:

- [Current state](docs/current-state.md)
- [Architecture](docs/architecture.md)
- [Roadmap](docs/roadmap.md)
- [Task backlog](docs/tasks.md)
- [NipaPlay reference feature review](docs/reference-nipaplay-feature-review.md)
- [ani-rss integration review](docs/ani-rss-integration-review.md)
- [ADR 0001: Kotlin, Compose, and focused Rust](docs/adr/0001-kotlin-compose-and-focused-rust.md)
- [ADR 0002: Windows libmpv distribution](docs/adr/0002-windows-libmpv-distribution.md)

## Requirements

- Windows for the desktop target
- JDK 17 or newer
- Android SDK for Android and Android TV builds
- Stable Rust toolchain for native crates
- An Android emulator or device for connected instrumentation tests

`local.properties` is intentionally ignored. Point it at your Android SDK:

```properties
sdk.dir=C\:\\path\\to\\Android\\Sdk
```

## Build And Test

Run the main JVM/native verification:

```powershell
cargo fmt --all --check
cargo test --workspace
.\gradlew.bat --no-daemon :shared:domain:jvmTest
.\gradlew.bat --no-daemon :shared:library-client:jvmTest
.\gradlew.bat --no-daemon :shared:library-server-core:jvmTest
.\gradlew.bat --no-daemon :apps:desktop-windows:desktopTest
.\gradlew.bat --no-daemon :shared:library-client-android:testDebugUnitTest
.\gradlew.bat --no-daemon :shared:player-android-media3:assembleDebugAndroidTest
.\gradlew.bat --no-daemon :apps:android-mobile:assembleDebug :apps:android-tv:assembleDebug
```

With an Android emulator or device online:

```powershell
.\gradlew.bat --no-daemon :shared:player-android-media3:connectedDebugAndroidTest
.\gradlew.bat --no-daemon :apps:android-tv:connectedDebugAndroidTest
```

## Run The Windows Shell

```powershell
.\gradlew.bat --no-daemon :apps:desktop-windows:run
```

In the Windows shell:

1. Choose an anime folder.
2. Let the app index it.
3. Note the displayed pairing code.
4. On Android or Android TV, use `Discover PC` or manually enter the LAN URL.
5. Enter the pairing code and refresh the PC library.

The Windows shell can currently prepare local or LAN playback requests and show
the planned mpv commands. Actual Windows video rendering is still pending.

## Probe A Windows libmpv Bundle

Set `DANMAKU_LIBMPV_PATH` to an audited `libmpv-2.dll` or to a directory
containing it, then run:

```powershell
cargo run -p player-windows-mpv --bin mpv-probe
```

The probe loads the DLL, prints the mpv client API version, initializes an mpv
context, and shuts it down cleanly.

## Security And Source Policy

The current LAN server is intended only for trusted local networks. Pairing
codes are required for catalog, media, and progress endpoints, but this is not
an internet-facing remote-access design.

Project rules:

- Support authorized media sources only.
- Do not implement DRM circumvention.
- Do not log pairing tokens, credentials, cookies, or signed media URLs.
- Store provider credentials only in platform-appropriate secure storage.
- Keep provider response models at plugin boundaries; persist normalized domain
  models in the library database.

## Roadmap Highlights

Near-term priorities:

- Select and audit a pinned Windows libmpv bundle.
- Wire the JNA mpv command executor into the Windows shell.
- Render libmpv video in the Compose desktop app.
- Synchronize the danmaku overlay to the real playback clock.
- Exercise PC-to-mobile and PC-to-TV streaming on physical hardware.
- Add subtitle endpoints and subtitle streaming tests.

Later priorities include authorized download management, ani-rss monitoring,
MyAnimeList progress/rating sync, provider plugins, danmaku parsing/filtering,
and additional desktop/mobile/web platforms.
