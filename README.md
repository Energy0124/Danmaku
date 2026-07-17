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
  desktop-windows/       Compose Multiplatform shell; owns the Rust server sidecar
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
  library-server/        Headless and desktop-sidecar LAN library server
  player-app/            Native egui/libmpv Windows player migration client
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

- [Rust server and Windows player migration plan](docs/design/rust-migration-plan.md)
- [Rust player consumer UI redesign](docs/design/rust-player-ui-redesign-plan.md)
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

The launcher builds the Rust `library-server` sidecar and starts it by default.

Native Windows player migration preview:

```powershell
cargo run -p danmaku-player -- --media "W:\Anime\Show\Episode 01.mkv" --danmaku "comments.xml"
```

The native player accepts local Bilibili XML, normalized JSON, and existing ASS
overlays. XML/JSON comments use the native egui renderer; ASS files use mpv's
subtitle renderer for compatibility. XML, JSON, `.danmaku`, or ASS files can
also be dropped onto the running player. Display opacity, speed, density, and
lane count are available from the Danmaku control menu. Starting without
`--media` uses the packaged `library-server.exe`: on first run, choose a local
library folder and the player starts, waits for, and connects to the server
automatically. Existing LAN discovery and manual connection remain available.
English and Traditional Chinese can be selected on the first screen; Settings
can change the library folder, restart or stop a player-owned server, and
persists volume, playback rate, auto-next, danmaku defaults, local roots, and
the last server URL in
`%LOCALAPPDATA%\\Danmaku\\player-preferences.json`. Pairing tokens are never
written there. Server administration opens the connected server's `/web/` UI.

To resolve dandanplay comments through a running Rust library server, provide
the catalog media ID:

```powershell
cargo run -p danmaku-player -- --media "W:\Anime\Show\Episode 01.mkv" `
  --server-url http://127.0.0.1:8686 --media-id episode-id
```

Use `--help` for the full playback and danmaku option list.

Rust-native runtime-free Windows package:

```powershell
.\tools\windows\prepare-rust-player-release.ps1
.\build\release\rust-player\danmaku-player-0.1.0-windows-x64\run-danmaku-player.ps1
```

The versioned zip is written under `build/release/rust-player/` and contains
`danmaku-player.exe`, `library-server.exe`, the server web UI, libmpv, and
the optional per-user background-host scripts. Closing the player stops only a
server process it started itself.

To keep the library server available after the player closes, install the
packaged current-user logon task (no administrator access is required):

```powershell
$package = ".\build\release\rust-player\danmaku-player-0.1.0-windows-x64"
& "$package\manage-rust-library-background-host.ps1" -Action Install `
  -LibraryRoot "W:\Anime"
& "$package\manage-rust-library-background-host.ps1" -Action Status
```

Repeat `-LibraryRoot` for multiple folders. `SetRoots`, `Start`, `Stop`,
and `Uninstall` manage the task; `-PlanOnly` previews Install/SetRoots without
changing Task Scheduler. The player detects
`%LOCALAPPDATA%\Danmaku\server\background-host.json`, attaches to the
background server, and leaves its lifecycle under script control. The manual
registration/ownership checklist is in
[Windows background-host QA](docs/qa/windows-background-host-qa.md).

The legacy Compose-compatible portable build remains available during
migration through `.\run-windows.ps1 -Portable`.

Experimental macOS desktop shell:

```bash
./run-macos.sh
```

Windows app workflow:

1. Add one or more anime library folders.
2. Let the desktop app index the library.
3. Use local playback directly from the Library tab, or note the Rust sidecar's
   LAN URL and pairing code.
4. On Android or Android TV, use discovery or manual connection to pair with
   the Windows library server.
5. Browse, stream, and save playback progress from the client.

Experimental headless Windows server:

```powershell
.\gradlew.bat --no-daemon :apps:library-server-windows:run --args="--data-dir build/headless-data --root W:/Anime --port 8686 --pairing-token 123456 --web-assets-dir apps/web-ui/dist"
```

The headless server scans configured roots into a JSON catalog, streams
media/subtitles through the same trusted-LAN routes as desktop, and persists
catalog snapshots, stable pairing tokens, and playback progress under the
locked data directory. It can read roots and non-secret provider setting
summaries from `server-settings.json`, exposes those summaries through server
status, exposes provider runtime readiness, read-only provider mapping
search, and dandanplay match/comment resolve for catalog media. The Rust host
also exposes a bearer-authenticated, secret-redacted provider settings API; its
web administration form edits dandanplay, MyAnimeList, and Bangumi settings,
stores write-only credentials with Windows DPAPI, and refreshes provider
clients without restarting the server. The server boots from cached catalog
when no roots are configured while using the same LAN discovery announcements
as the desktop sidecar. Broader tracking automation and library-quality admin
workflows remain planned.

Desktop can launch directly into the remote-library browser against a running
headless/sidecar host. Supplying a remote URL skips the local sidecar:

```powershell
.\gradlew.bat --no-daemon :apps:desktop-windows:run --args="--remote-server-url http://127.0.0.1:8686 --remote-pairing-token 123456"
```

Repeatable headless web UI QA:

```powershell
.\tools\windows\run-headless-web-ui-qa.ps1
.\tools\windows\run-headless-web-ui-qa.ps1 -RustServer
```

Repeatable desktop-sidecar web UI QA:

```powershell
.\tools\windows\run-embedded-web-ui-qa.ps1
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
