# Danmaku

[![CI](https://github.com/Energy0124/Danmaku/actions/workflows/ci.yml/badge.svg)](https://github.com/Energy0124/Danmaku/actions/workflows/ci.yml)

Danmaku is an early-stage cross-platform anime media app for local libraries,
trusted-LAN streaming, synchronized watch progress, and scrolling danmaku
overlays.

Danmaku's own source code is licensed under the [MIT License](LICENSE).
Third-party dependencies retain their own licenses; see
[Third-Party Notices](THIRD_PARTY_NOTICES.md). Release artifacts package the
applicable license text, notices, and versioned dependency information for the
distributed components.

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

An initial macOS desktop path is also available for local development. It
reuses the Compose Desktop shell and libmpv command bridge, but macOS playback
currently uses mpv-managed video output instead of the embedded Windows child
window.

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
  volume, fullscreen/aspect-ratio, and audio/subtitle track discovery and
  selection controls
- Player-first Windows playback screen with a stable embedded native mpv video
  host, compact icon controls, minimal fullscreen chrome, and an agent-friendly
  GUI playback smoke-test launch path
- Persisted Windows playback defaults for volume, playback rate, and aspect
  mode
- Windows continue-watching and recently-watched lists for local library
  episodes backed by saved playback progress
- Windows prepared-playback episode navigation for previous/next local catalog
  items
- Windows one-click local library play/resume while retaining explicit prepare
  actions for diagnostics
- Windows paired-library rows can prepare streams for diagnostics or one-click
  load them into the local mpv controller
- Optional Windows local-library auto-next playback persisted in app settings
- Android background playback service with resume lookup and periodic progress
  upload
- Shared Kotlin playback, library-client, and danmaku scheduling contracts
- Shared normalized danmaku event modes plus dependency-free local Bilibili XML
  and normalized JSON parsing
- Windows dandanplay-compatible danmaku API client foundation for
  first-16MB MD5 media matching, optional signed or credential-based
  弹弹play开放平台 authentication, and comment normalization into shared
  `DanmakuEvent` rows
- Profile-tab dandanplay provider settings with DPAPI-protected AppSecret
  storage and redacted diagnostics
- Windows local-library playback can fetch matched dandanplay comments from the
  configured provider and attach them as a cached ASS danmaku overlay
- Persistent desktop dandanplay match/comment cache keyed by local media item
  and file fingerprint, avoiding rematches when files are unchanged
- Prepared-playback controls to force-refresh or clear dandanplay cache for the
  selected local episode
- Configurable dandanplay cache max age plus expired-cache cleanup from the
  Profile tab
- Desktop preparation for local-file and paired-LAN playback requests, including
  resume seeks and native mpv command execution
- Rust libmpv loader/probe plus a small C ABI and Kotlin/JNA binding for the
  Windows native command executor
- Default Windows Compose distributable packaging for the Rust mpv bridge DLL
  and the approved local `libmpv-2.dll`
- Experimental macOS Compose desktop support with the same shared library
  server, catalog, and mpv command bridge. `:apps:desktop-windows:createDistributable`
  builds and copies `libplayer_windows_mpv.dylib` on macOS, while libmpv is
  resolved from `DANMAKU_LIBMPV_PATH`, the app directory, or common local
  library locations.
- Tested LAN server/client behavior, byte ranges, progress updates, reconnects,
  timeouts, slow streams, and Android/TV instrumentation paths

Not implemented yet:

- Broad Windows resize/fullscreen/4K/hardware-decoding validation
- Embedded macOS video-surface composition and release-ready libmpv packaging
- Authorized download engine
- Full provider plugins, MyAnimeList integration, manual local danmaku mounting,
  and per-episode danmaku offset controls

## Architecture

```text
apps/
  desktop-windows/       Compose Multiplatform desktop shell
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
  player-windows-mpv/    Desktop libmpv loader, probe, and C ABI

docs/
  Architecture, roadmap, task backlog, and decisions
```

The desktop app currently acts as the embedded library server. Android and
Android TV are clients for that server. A separate headless server is planned
only after API, lifecycle, diagnostics, settings, and firewall behavior are
stable.

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

- Windows for the release desktop target, or macOS for the experimental desktop
  development path
- JDK 17 or newer
- Android SDK for Android and Android TV builds
- Stable Rust toolchain for native crates
- An Android emulator or device for connected instrumentation tests

`local.properties` is intentionally ignored. Point it at your Android SDK:

```properties
sdk.dir=C\:\\path\\to\\Android\\Sdk
```

Local desktop development can also read ignored dandanplay credentials from
`local.properties` when no encrypted Profile-tab provider settings have been
saved yet:

```properties
danmaku.dandanplay.appId=your-app-id
danmaku.dandanplay.appSecret=your-app-secret
danmaku.dandanplay.proxyBaseUrl=https://your-worker.example.workers.dev
danmaku.dandanplay.authenticationMode=signed
danmaku.dandanplay.cacheMaxAgeDays=30
danmaku.myanimelist.clientId=your-client-id
danmaku.myanimelist.clientSecret=your-client-secret
```

The same fallback can be supplied through environment variables named
`DANMAKU_DANDANPLAY_APP_ID`, `DANMAKU_DANDANPLAY_APP_SECRET`,
`DANMAKU_DANDANPLAY_PROXY_BASE_URL`,
`DANMAKU_DANDANPLAY_AUTHENTICATION_MODE`, and
`DANMAKU_DANDANPLAY_CACHE_MAX_AGE_DAYS`, plus
`DANMAKU_MYANIMELIST_CLIENT_ID` and `DANMAKU_MYANIMELIST_CLIENT_SECRET`.
Direct AppId/AppSecret values win when present; the proxy URL is used only when
no direct AppSecret exists. These values are for local development, proxy
routing, and CI-only checks; do not embed provider secrets in distributable
client artifacts.

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

The optional Cloudflare Worker proxy under `tools/dandanplay-worker-proxy`
signs `/api/v2/match` and `/api/v2/comment/{episodeId}` requests at the edge so
public clients can fetch danmaku without shipping an AppSecret:

```powershell
cd tools\dandanplay-worker-proxy
npm ci
npm run typecheck
npm test
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

To build and run the experimental macOS shell:

```bash
./run-macos.sh
```

Set `DANMAKU_LIBMPV_PATH` to a `libmpv.2.dylib` or a directory containing it
when libmpv is not installed in a common local library location.

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
commands through the packaged Rust/JNA/libmpv chain, and host mpv in a stable
native child-window playback surface. The Playback tab is video-first: compact
top chrome, icon controls, progress scrubbing, play/pause, seek, playback-rate,
volume, fullscreen, aspect-ratio, and track controls stay close to the video and
can be minimized in fullscreen. Library playback saves watch progress from live
mpv position snapshots, while direct file playback remains a quick validation
path that does not create library progress records. Indexed sidecars and
dandanplay-fetched comments can be attached as mpv subtitle/ASS overlay tracks.

For a packaged local-file smoke test without driving the full UI, pass a real
media file to the Windows runtime probe:

```powershell
.\tools\windows\verify-windows-mpv-runtime.ps1 -MediaPath C:\media\sample.mkv
```

For a fast GUI smoke test of the actual packaged player surface, use:

```powershell
.\tools\windows\smoke-windows-playback.ps1 -MediaPath C:\media\sample.mkv -Seconds 6
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
and the exact approved DLL hash. `:apps:desktop-windows:createDistributable`
also builds the Rust bridge and copies the installed approved `libmpv-2.dll`
beside the app by default.

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
- Keep the quick packaged Windows playback smoke loop passing while changing the
  player UI or native host.
- Add manual danmaku mounting, offset controls, and filtering over the real
  playback clock.
- Exercise PC-to-mobile and PC-to-TV streaming on physical hardware.
- Validate Windows fullscreen/aspect-ratio behavior with resize, 4K media, and
  hardware decoding.
- Re-audit the libmpv bundle before changing its producer artifact or hashes.

Later priorities include authorized download management, ani-rss monitoring,
MyAnimeList progress/rating sync, provider plugins, danmaku parsing/filtering,
and additional desktop/mobile/web platforms.

## License

Danmaku is licensed under the [MIT License](LICENSE). The bundled Windows
libmpv dependency and all other third-party components remain under their own
licenses.
