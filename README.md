# Danmaku

[![CI](https://github.com/Energy0124/Danmaku/actions/workflows/ci.yml/badge.svg)](https://github.com/Energy0124/Danmaku/actions/workflows/ci.yml)

Danmaku is a local-first anime library, player, and danmaku application. The
first-class targets are the Rust-native Windows player, Android mobile/tablet,
and Android TV, with an experimental native macOS build. A Rust library server
publishes authorized local media to the clients over a trusted LAN and serves
the browser administration UI.

The project is still in active foundation work. Local library scanning,
Windows libmpv playback, Android Media3 playback, progress synchronization,
dandanplay matching/comments, provider metadata, and explicit external-list
sync are implemented; broader product polish and release QA remain.

## Repository Layout

```text
apps/
  android-mobile/         Android phone/tablet client
  android-tv/             Dedicated Android TV client
  android-tv-benchmark/   Android TV Macrobenchmark journeys
  web-ui/                 Trusted-LAN server administration UI

shared/
  domain/                 Platform-neutral models and behavior
  library-client/         Shared LAN client/session/progress policy
  library-client-android/ Android HTTP/discovery/storage adapters
  player-android-media3/  Shared Media3 playback adapter/service

native/
  library-server/         Rust library, provider, progress, and web server
  player-app/             Rust egui/libmpv Windows and macOS player
  player-windows-mpv/     Cross-platform Rust libmpv loader, renderer, and probe
  rust-core/              Shared Rust timeline/indexing behavior
```

The former Kotlin Compose desktop app and JVM library-server modules have been
retired. Existing Compose database files are not read or migrated; configure
library roots again in the native player or server and let the Rust catalog
rescan them.

## Requirements

- Windows x64 or macOS 12+ and a stable Rust toolchain for the desktop player/server
- Homebrew `mpv` (`brew install mpv`) for macOS playback and packaging
- JDK 17 and an Android SDK for Android builds
- Node 22 for the web UI and dandanplay Worker proxy
- A connected emulator/device only for Android instrumentation tests

`local.properties` is ignored and may hold the Android SDK path and local
provider credentials. Never commit credentials, pairing tokens, cookies, or
signed URLs.

```properties
sdk.dir=C\:\\path\\to\\Android\\Sdk
danmaku.dandanplay.appId=your-app-id
danmaku.dandanplay.appSecret=your-app-secret
danmaku.myanimelist.clientId=your-client-id
danmaku.myanimelist.clientSecret=your-client-secret
```

## Build And Test

```powershell
cargo fmt --all --check
cargo test --workspace
.\gradlew.bat --no-daemon :shared:domain:jvmTest :shared:library-client:jvmTest :shared:library-client-android:testDebugUnitTest :shared:player-android-media3:assembleDebugAndroidTest :apps:android-mobile:assembleDebug :apps:android-tv:assembleDebug
```

Web UI:

```powershell
cd apps\web-ui
npm install
npm run build
```

Worker proxy:

```powershell
cd tools\dandanplay-worker-proxy
npm install
npm run typecheck
npm test
```

## Windows Player

Tagged releases publish a signed, per-user `app.danmaku.player-Setup.exe`.
It installs without administrator access, creates Desktop and Start Menu
shortcuts, and checks the stable GitHub release channel quietly at startup.
An available update shows its release notes and is downloaded/applied only
after **Update and restart** is selected. Player preferences, server settings,
and library data remain under `%LOCALAPPDATA%\Danmaku` across updates and
uninstall. Portable zips remain available but do not update themselves.

Build and verify the unified portable package, then launch the newest package:

```powershell
.\build-rust-player.bat
.\run-rust-player.bat
```

Build an unsigned local installer after preparing the portable stage and
installing the pinned Velopack 1.2.0 `vpk` tool:

```powershell
.\tools\windows\prepare-windows-installer.ps1
```

The package contains `danmaku-player.exe`, `library-server.exe`, the web UI,
the release-resolved and digest-verified `libmpv-2.dll`, background-host
scripts, licenses, and dependency inventories. On first launch, select one or
more local library folders. The
player starts and connects to the sibling server automatically. It can also
discover or manually connect to another trusted-LAN server.

Direct playback is available from a development build or package:

```powershell
cargo run -p danmaku-player -- --media "W:\Anime\Show\Episode 01.mkv"
.\run-rust-player.bat --media "W:\Anime\Show\Episode 01.mkv"
```

Use `--help` for playback, danmaku, server, and QA options. Pairing tokens are
session-only and are not written to player preferences.

To keep the packaged server available after the player closes, use its
current-user background-host manager:

```powershell
$package = ".\build\release\rust-player\danmaku-player-0.1.0-windows-x64"
& "$package\manage-rust-library-background-host.ps1" -Action Install -LibraryRoot "W:\Anime"
& "$package\manage-rust-library-background-host.ps1" -Action Status
```

## macOS Player (Experimental)

The active Rust player also builds as a native macOS application. Install the
runtime playback dependency and build the verified app bundle:

```bash
brew install mpv
./build-macos.sh
open build/release/macos/danmaku-player-0.1.0-macos-*/Danmaku.app
```

The package contains `Danmaku.app`, its sibling Rust library server, and the
web UI. The app discovers Homebrew libmpv on both Apple Silicon and Intel Macs,
uses native macOS window decorations, stores durable state under
`~/Library/Application Support/Danmaku`, and stores its session cache under
`~/Library/Caches/Danmaku`. The package is ad-hoc signed for development, is
not notarized, and requires Homebrew `mpv` on the target Mac.

Local and trusted-LAN library playback plus local XML/JSON/ASS danmaku are
available. Online provider HTTPS and secure provider-token persistence remain
Windows-only in this initial macOS slice; see [Current state](docs/current-state.md).

## Standalone Library Server

```powershell
cd apps\web-ui
npm run build
cd ..\..
cargo run -p library-server -- --data-dir build\server-data --root W:\Anime --web-assets-dir apps\web-ui\dist
```

The server owns scanning, catalog snapshots, progress, provider settings,
dandanplay resolution/cache, external tracking state, HTTP media/subtitle/
poster routes, UDP discovery, and `/web/` administration. It does not import
the retired Compose desktop database.

The Windows, Android mobile, and Android TV folder views include a manual
refresh action. It asks the server to rescan only the folder currently being
viewed (or all configured roots at the folder-browser root), shows live scan
progress, and reloads the catalog when the scan finishes. There is no constant
background full-library polling.

The Windows desktop Folders view also offers **Organize library** for local
libraries. It creates an AniRss-style `<series>/Season <number>` preview and
requires approval one series at a time. Filenames are preserved; existing
destinations block approval; companion files are opt-in; nothing is deleted or
overwritten. Moves are journaled and verified, with cancel/rollback and undo for
the last completed series. This control is intentionally unavailable over LAN.

### MyAnimeList and Bangumi tracking

In the Windows player, open **Settings → Accounts & tracking**. MyAnimeList
uses browser sign-in and returns to the player automatically; Bangumi links to
its official token page, then validates the pasted token before saving it.
Search for each series instead of entering provider IDs by hand, review the
readback/preview, and choose **Confirm and sync** before any provider write.
When an episode finishes, the player offers **Review update** or **Not now**.
If provider progress is ahead, the only resolution is to import that watched
count locally—the app never overwrites newer provider progress.

Release packaging accepts `-MyAnimeListClientId` (or
`DANMAKU_MYANIMELIST_CLIENT_ID`) and embeds that public OAuth client ID in the
server binary. Development builds without one show MAL sign-in as unavailable.

Repeatable local QA:

```powershell
.\tools\windows\run-headless-web-ui-qa.ps1
```

## Android Clients

Android mobile/tablet and Android TV discover or manually connect to the Rust
server, browse its catalog, stream through Media3, render danmaku, and
synchronize playback progress. Their Connect/PC screens can also read MAL and
Bangumi state, review pending progress updates, and explicitly confirm the
exact preview before syncing. Account connection, series mapping, and conflict
import remain in the Windows app or web administration UI. Android TV remains
a dedicated module with TV-specific focus and remote-navigation behavior.

Connected checks require an emulator or physical device and are documented in
[CONTRIBUTING.md](CONTRIBUTING.md).

## Documentation

- [Current state](docs/current-state.md)
- [Architecture](docs/architecture.md)
- [Roadmap](docs/roadmap.md)
- [Tasks and QA gates](docs/tasks.md)
- [Releasing](docs/releasing.md)
- [LAN protocol](docs/lan-protocol.md)
- [Windows libmpv bundle](docs/windows-libmpv-bundle.md)
- [Contributing](CONTRIBUTING.md)

## Security And License

Support authorized media sources only. Do not add DRM circumvention or expose
provider secrets. Treat the server as trusted-LAN software rather than an
Internet-facing service.

Danmaku is licensed under the MIT License. Third-party components retain their
licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
