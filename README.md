# Danmaku

[![CI](https://github.com/Energy0124/Danmaku/actions/workflows/ci.yml/badge.svg)](https://github.com/Energy0124/Danmaku/actions/workflows/ci.yml)

Danmaku is an early-stage cross-platform anime media app for local libraries,
trusted-LAN streaming, synchronized watch progress, and scrolling danmaku
overlays.

Danmaku's own source code is licensed under the [MIT License](LICENSE).
Third-party dependencies retain their own licenses; see
[Third-Party Notices](THIRD_PARTY_NOTICES.md). Release builds validate their
transitive dependency graphs and package the applicable license text plus a
versioned dependency inventory.

The Windows desktop archive is runtime-free and requires a user-installed Java
17 or newer runtime. This keeps the published archive from redistributing an
OpenJDK runtime; see [Releasing](docs/releasing.md) for the artifact boundaries.

The Windows release uses code from
[FFmpeg](https://ffmpeg.org/) under LGPLv3-or-later terms. Its corresponding
source and build provenance are documented in
[libmpv source information](third_party/windows/libmpv/SOURCE.md).

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
- Windows DPAPI-backed ani-rss API-key and webhook-token storage
- SQLDelight catalog, playback-progress, settings, and download-queue storage
- Paired trusted-LAN library server with JSON catalog, byte-range media
  streaming, sidecar subtitle streaming, and progress API
- UDP discovery for finding the Windows library server from Android clients
- Android mobile and Android TV apps that browse the Windows catalog and stream
  selected episodes through Media3 with indexed sidecar subtitle attachment and
  progress, seek, volume, and runtime audio/subtitle track controls
- Shared library search, title/path sorting, and subtitle-only filtering in the
  Windows, Android mobile, and Android TV catalog screens
- Windows playback with indexed sidecar subtitle attachment plus runtime
  volume and audio/subtitle track discovery and selection controls
- Windows continue-watching and recently-watched lists for local library
  episodes backed by saved playback progress
- Android background playback service with resume lookup and periodic progress
  upload
- Shared Kotlin playback, library-client, and danmaku scheduling contracts
- Desktop preparation for local-file and paired-LAN playback requests, including
  resume seeks and native mpv command execution
- Rust libmpv loader/probe plus a small C ABI and Kotlin/JNA binding for the
  Windows native command executor
- Tested LAN server/client behavior, byte ranges, progress updates, reconnects,
  timeouts, slow streams, and Android/TV instrumentation paths

Not implemented yet:

- Windows fullscreen/aspect-ratio controls and broad resize/4K/hardware-decoding
  validation
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

To build and run the Windows shell in a developer checkout:

```powershell
.\run-windows.ps1
```

This builds the Rust mpv bridge, installs the pinned LGPL libmpv dependency
only when it is missing, exports the native paths for the current process, and
starts the Compose Desktop app.

To build and run the runtime-free Windows portable release:

```powershell
.\run-windows.ps1 -Portable
```

The prepared portable launcher is
`apps\desktop-windows\build\release\windows-portable\run-danmaku.ps1`.

With an Android emulator or device online:

```powershell
.\gradlew.bat --no-daemon :shared:player-android-media3:connectedDebugAndroidTest
.\gradlew.bat --no-daemon :apps:android-tv:connectedDebugAndroidTest
```

## Run The Windows Shell

```powershell
.\run-windows.ps1
```

In the Windows shell:

1. Choose an anime folder.
2. Let the app index it.
3. Note the displayed pairing code.
4. On Android or Android TV, use `Discover PC` or manually enter the LAN URL.
5. Enter the pairing code and refresh the PC library.

The Windows shell can prepare local or LAN playback requests, execute their
commands through the packaged Rust/JNA/libmpv chain, and host mpv in an initial
native child-window playback surface with play, pause, seek, progress-bar
scrubbing, playback-rate, volume, and track controls. Library playback saves
watch progress from live mpv position snapshots, while direct file playback
remains a quick validation path that does not create library progress records.
Full parsed danmaku synchronization is still pending.

For a packaged local-file smoke test without driving the full UI, pass a real
media file to the Windows runtime probe:

```powershell
.\tools\windows\verify-windows-mpv-runtime.ps1 -MediaPath C:\media\sample.mkv
```

## Probe A Windows libmpv Bundle

Danmaku's Windows release directly redistributes a pinned, hash-verified LGPL
libmpv build as a separately licensed dependency. For local development, review
its license and install the same dependency into the ignored runtime directory:

```powershell
.\tools\windows\install-libmpv-dependency.ps1 -AcceptLicense
```

Then set `DANMAKU_LIBMPV_PATH` to the installed `libmpv-2.dll` or its
directory and run the Rust probe:

```powershell
cargo run -p player-windows-mpv --bin mpv-probe
```

The probe loads the DLL, prints the mpv client API version, initializes an mpv
context, and shuts it down cleanly.

If you need to run each native setup step manually, build the Rust bridge and
expose both native paths:

```powershell
cargo build -p player-windows-mpv --lib
$env:DANMAKU_MPV_BRIDGE_PATH = (Resolve-Path .\target\debug\player_windows_mpv.dll).Path
$env:DANMAKU_LIBMPV_PATH = (Resolve-Path .\runtime\windows\libmpv\libmpv-2.dll).Path
.\gradlew.bat --no-daemon :apps:desktop-windows:run
```

For the pinned-bundle manifest, checksum verification, probe, and release
packaging workflow, see
[Windows libmpv Bundle Audit](docs/windows-libmpv-bundle.md). The release
artifact includes the required GPL/LGPL texts, source and provenance notice,
and the exact approved DLL hash.

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

- Use the approved pinned Windows libmpv bundle for development and releases.
- Synchronize the danmaku overlay to the real playback clock.
- Exercise PC-to-mobile and PC-to-TV streaming on physical hardware.
- Add Windows fullscreen/aspect-ratio controls and validate resize, 4K media,
  and hardware decoding.
- Re-audit the libmpv bundle before changing its producer artifact or hashes.

Later priorities include authorized download management, ani-rss monitoring,
MyAnimeList progress/rating sync, provider plugins, danmaku parsing/filtering,
and additional desktop/mobile/web platforms.

## License

Danmaku is licensed under the [MIT License](LICENSE). The bundled Windows
libmpv dependency and all other third-party components remain under their own
licenses.
