# Danmaku

[![CI](https://github.com/Energy0124/Danmaku/actions/workflows/ci.yml/badge.svg)](https://github.com/Energy0124/Danmaku/actions/workflows/ci.yml)

Danmaku is a cross-platform anime media app in active foundation work. The
current product focus is a Windows desktop library/server/player, Android
mobile and tablet clients, Android TV playback, synchronized watch progress,
provider metadata, danmaku overlays, and a compatibility-preserving split
toward a reusable library host plus a trusted-LAN web UI.

This is not a finished media player yet. The repository currently contains a
working vertical slice for local Windows libraries, trusted-LAN streaming to
Android clients, libmpv desktop playback, Media3 Android playback, dandanplay
danmaku matching/cache, and external anime list mapping/sync foundations.

## Targets

First-class targets:

- Windows desktop
- Android mobile and tablet
- Android TV

Experimental target:

- macOS desktop development build using the shared Compose desktop shell and
  mpv command bridge

Later targets may include Linux, iOS, iPadOS, and web after the first-class
targets are stable.

## Repository Layout

```text
apps/
  desktop-windows/       Compose Multiplatform desktop shell and library host
  android-mobile/        Android phone/tablet app
  android-tv/            Dedicated Android TV app
  web-ui/                Planned trusted-LAN TypeScript browser client

shared/
  domain/                Core models, catalog logic, playback contracts, danmaku logic
  library-server-core/   Trusted-LAN HTTP server and discovery primitives
  library-host-core/     Shared host lifecycle/config/status contracts
  library-client/        Shared LAN client/session/progress policy
  library-client-android Android HTTP/discovery/storage adapters
  player-android-media3/ Shared Media3 playback adapter/service

native/
  rust-core/             Focused Rust timeline/indexing experiments
  player-windows-mpv/    libmpv loader, probe, and desktop playback bridge

tools/
  windows/               Windows release, libmpv, and playback smoke scripts
  dandanplay-worker-proxy Cloudflare Worker proxy for signed dandanplay requests

docs/
  Current state, architecture, roadmap, task backlog, and design work
```

## Documentation

- [Documentation index](docs/README.md)
- [Current state](docs/current-state.md)
- [Architecture](docs/architecture.md)
- [Roadmap](docs/roadmap.md)
- [Tasks](docs/tasks.md)
- [Releasing](docs/releasing.md)
- [Windows libmpv bundle](docs/windows-libmpv-bundle.md)
- [Contributing](CONTRIBUTING.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)

Active design/task tracks:

- [Home and app shell UI tasks](docs/design/home-and-app-shell-ui-tasks.md)
- [Desktop UI page mockups and specs](docs/design/desktop-ui-pages/README.md)
- [Android mobile and TV library UI tasks](docs/design/android-mobile-tv-library-ui-tasks.md)
- [External anime mapping and tracking tasks](docs/design/external-anime-tracking-tasks.md)
- [Server, client, and web UI split](docs/design/server-client-web-ui-split-plan.md)

## Requirements

- JDK 17 or newer
- Android SDK for Android and Android TV builds
- Stable Rust toolchain for native crates
- Windows for the release desktop target
- Android emulator or device for connected instrumentation tests
- Optional: Node 22 for the web UI and dandanplay Worker proxy

`local.properties` is intentionally ignored. At minimum, point it at the
Android SDK:

```properties
sdk.dir=C\:\\path\\to\\Android\\Sdk
```

Local provider credentials may also be supplied through ignored
`local.properties` or environment variables. Do not commit real credentials.

```properties
danmaku.dandanplay.appId=your-app-id
danmaku.dandanplay.appSecret=your-app-secret
danmaku.dandanplay.proxyBaseUrl=https://your-worker.example.workers.dev
danmaku.dandanplay.authenticationMode=signed
danmaku.dandanplay.cacheMaxAgeDays=30
danmaku.myanimelist.clientId=your-client-id
danmaku.myanimelist.clientSecret=your-client-secret
```

Supported environment fallbacks:

- `DANMAKU_DANDANPLAY_APP_ID`
- `DANMAKU_DANDANPLAY_APP_SECRET`
- `DANMAKU_DANDANPLAY_PROXY_BASE_URL`
- `DANMAKU_DANDANPLAY_AUTHENTICATION_MODE`
- `DANMAKU_DANDANPLAY_CACHE_MAX_AGE_DAYS`
- `DANMAKU_MYANIMELIST_CLIENT_ID`
- `DANMAKU_MYANIMELIST_CLIENT_SECRET`
- `DANMAKU_LOCAL_PROPERTIES`

## Build And Test

Main verification commands:

```powershell
cargo fmt --all --check
cargo test --workspace
.\gradlew.bat --no-daemon :shared:domain:jvmTest :shared:library-client:jvmTest :shared:library-server-core:jvmTest :apps:desktop-windows:desktopTest :shared:library-client-android:testDebugUnitTest :shared:player-android-media3:assembleDebugAndroidTest :apps:android-mobile:assembleDebug :apps:android-tv:assembleDebug
```

With a connected Android emulator or device:

```powershell
.\gradlew.bat --no-daemon :shared:player-android-media3:connectedDebugAndroidTest
.\gradlew.bat --no-daemon :apps:android-mobile:connectedDebugAndroidTest
.\gradlew.bat --no-daemon :apps:android-tv:connectedDebugAndroidTest
```

Worker proxy checks:

```powershell
cd tools\dandanplay-worker-proxy
npm ci
npm run typecheck
npm test
```

Web UI checks:

```powershell
cd apps\web-ui
npm install
npm run build
```

## Run Locally

Windows desktop development shell:

```powershell
.\run-windows.ps1
```

Runtime-free Windows portable build/run:

```powershell
.\run-windows.ps1 -Portable
```

Experimental macOS desktop shell:

```bash
./run-macos.sh
```

Windows app workflow:

1. Add one or more anime library folders.
2. Let the desktop app index the library.
3. Use local playback directly from the Library tab, or note the LAN URL and
   pairing code.
4. On Android or Android TV, use discovery or manual connection to pair with
   the Windows library server.
5. Browse, stream, and save playback progress from the client.

Experimental headless Windows server:

```powershell
.\gradlew.bat --no-daemon :apps:library-server-windows:run --args="--data-dir build/headless-data --root W:/Anime --port 8686 --pairing-token 123456 --web-assets-dir apps/web-ui/dist"
```

The headless server scans configured roots into a JSON catalog, streams
media/subtitles through the same trusted-LAN routes as desktop, and persists
catalog snapshots plus playback progress under the locked data directory. It
can boot from the cached catalog when no roots are configured and uses the same
LAN discovery announcements as the embedded desktop host. Provider settings are
still planned work.

Desktop can launch directly into the remote-library browser against a running
headless or embedded host:

```powershell
.\gradlew.bat --no-daemon :apps:desktop-windows:run --args="--remote-server-url http://127.0.0.1:8686 --remote-pairing-token 123456"
```

Repeatable headless web UI QA:

```powershell
.\tools\windows\run-headless-web-ui-qa.ps1
```

## Security And Source Policy

- Support authorized media sources only.
- Do not implement DRM circumvention.
- Do not log pairing tokens, credentials, cookies, or signed media URLs.
- Store provider credentials only in platform-appropriate secure storage or
  ignored local development files.
- Keep provider response objects at plugin/client boundaries; persist
  normalized domain models.
- Treat the LAN server as trusted-local-network only.

## License

Danmaku source code is licensed under the [MIT License](LICENSE). Third-party
components keep their own licenses; see [Third-party notices](THIRD_PARTY_NOTICES.md).
The Windows release packages a pinned LGPL libmpv dependency with source and
license provenance documented in [Windows libmpv bundle](docs/windows-libmpv-bundle.md).
