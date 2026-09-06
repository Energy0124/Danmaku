# Architecture

Danmaku uses a Rust-native Windows player/server with an experimental native
macOS build, dedicated Kotlin Android clients, and a TypeScript web
administration UI. Shared Kotlin modules contain only platform-neutral domain
and LAN-client behavior; desktop hosting and provider integrations live in
Rust.

## Platform Roles

### Windows

`native/player-app` is the only desktop application. It renders its egui UI and
danmaku over libmpv, browses local or remote libraries, and synchronizes
progress through the LAN API. In local mode it supervises the packaged
`library-server.exe`; it may instead attach to an already-running loopback or
background host. Only a child started by the player is stopped by the player.

`native/library-server` owns local roots, scanning, normalized catalog state,
metadata/posters, progress, dandanplay resolution/cache, provider credentials,
external tracking state, HTTP media delivery, UDP discovery, and `/web/`
assets. Its data-directory lock prevents two writers from using the same
state. The server is also distributable as a standalone headless package.
Manual current-folder rescans are trusted-LAN server operations: the server
resolves a client folder path against configured roots, scans in the
background, and atomically merges that subtree into the persisted catalog.
They follow the catalog's code-free LAN access rather than provider
administration authentication.
Library organization is a separate desktop-only mutation boundary. The egui
client requests a read-only plan, submits the exact approved series manifest,
and polls progress. `native/library-server` validates the catalog revision,
configured root, loopback peer, bearer token, and every source/destination;
then it journals each rename, updates catalog paths without changing media IDs,
and provides rollback and one-series undo. Android, TV, and browser clients do
not receive this capability.
It owns MAL OAuth state/token exchange and encrypted refresh tokens; the native
player owns only the fixed loopback browser callback and forwards the short-
lived authorization code. Bangumi tokens are validated against `/v0/me`
before encrypted storage.

`native/player-windows-mpv` is an ordinary Rust library used by the player. It
owns libmpv discovery/loading and the OpenGL render API bindings plus the
`mpv-probe` diagnostic binary. It exposes no JVM/JNA-compatible dynamic-library
ABI.

Windows installation and updates use Velopack. `native/player-app::updater`
owns the non-blocking stable-channel state machine and invokes the external
updater only after user approval. The installed app lives below the current
user's LocalAppData; durable player/server state remains in the separate
`%LOCALAPPDATA%\Danmaku` tree. A restarted updated app refreshes an installed
background-host copy with an atomic directory swap while preserving its task
and configuration. Portable and QA builds do not contact the update feed.

### macOS

macOS reuses `native/player-app`, `native/library-server`, and the Rust libmpv
render boundary. It uses native window decorations, the macOS OpenGL framework
for libmpv render-API symbol lookup, and platform-standard Application Support
and Caches directories. A development `.app` bundle packages the player,
server, web UI, icons, and notices; it discovers a separately installed
Homebrew libmpv from Apple Silicon or Intel prefixes.

The macOS slice supports local and trusted-LAN library playback and local
danmaku files. It is not yet a first-class release: the app is ad-hoc signed,
not notarized, does not redistribute libmpv, and the Rust server's provider
HTTPS and protected provider-token persistence remain Windows-only.

### Android Mobile And TV

Android applications are trusted-LAN clients. They share domain, connection,
Media3, and authenticated tracking transport code while retaining
platform-specific UI. Shared domain folder projections keep mobile and TV
multi-root browsing consistent while each app owns its navigation and input
behavior. Their manual folder refresh actions request a server-side subtree
rescan and poll status only until that requested scan finishes. They may read
provider state and submit an explicitly
reviewed, server-validated tracking preview, but provider credentials, mappings,
and conflict reconciliation remain server-owned Windows/web administration.
Android mobile also owns an app-private, versioned offline-cache index and a
persistent WorkManager download queue. Cache manifests contain only authorized
user-owned, DRM-free media assets and never persist pairing tokens. Offline
Media3 playback records progress locally and reconciles it with the library
server after the trusted LAN becomes reachable again.
Android TV remains a
dedicated module with TV layouts, D-pad focus, remote navigation, and
Macrobenchmark coverage.

`shared/app-update-android` owns the stable GitHub Release manifest contract,
daily check policy, bounded APK download, checksum and package/signing
verification, unknown-source settings intent, and system-installer handoff.
The mobile and TV apps own their localized Material/TV dialogs and settings
cards. Debug builds have no update endpoint by default. Android always retains
the final installation confirmation; the updater does not attempt silent
installation.

### Web UI

`apps/web-ui` is served by the Rust server under `/web/`. It provides catalog
playback and authenticated advanced administration for provider status,
settings, mappings, tracking readback, provider-ahead import, conflict-aware
previews, and explicit provider writes. Consumer sign-in and completion
prompts live in the native Windows player. The web UI does not own server
state.

## Module Boundaries

```text
shared:domain
  Normalized domain models and pure behavior.

shared:library-client
  Platform-neutral LAN session, catalog, and progress policy.

shared:library-client-android
  Android HTTP, discovery, connection, persistence, offline cache, and
  background download adapters.

shared:app-update-android
  GitHub release manifest, verified APK download, and installer handoff.

shared:player-android-media3
  Android Media3 playback service and adapter.

apps:android-mobile / apps:android-tv
  Dedicated Android presentation and navigation.

native/library-server
  Authoritative desktop catalog/provider/progress host.

native/player-app
  Windows/macOS UI, library client, playback, and server supervision.

native/player-windows-mpv
  Cross-platform Rust-only libmpv loader/render integration and probe.

apps/web-ui
  Browser client and server administration.
```

## Data Flow

1. The Rust server scans configured authorized roots and persists normalized
   catalog state. A manual folder rescan replaces only the selected subtree;
   an empty path intentionally requests a full-root refresh.
2. Windows, Android, TV, and web clients consume the same LAN catalog and
   progress contracts.
3. Media, subtitle, poster, danmaku, and progress requests use coarse-grained
   server routes; native boundaries are never crossed per rendered comment.
4. Provider response objects remain at Rust provider boundaries. Persisted
   state uses normalized catalog, mapping, and tracking models.
5. Pairing tokens and provider secrets must not appear in logs, reports,
   preferences, or committed fixtures.

## Compatibility

The Kotlin Compose desktop application, JVM server/host modules, JNA bridge,
and old Compose macOS target are retired. The Rust server does not read or
migrate their SQLite database. Old files are left untouched on user machines;
a native installation starts from Rust settings and a fresh scan. The current
macOS build is the Rust player, not the retired Compose artifact.

The trusted-LAN wire contract remains version 1 so existing Android clients
continue to interoperate. Contract fixtures are owned by the Rust server test
tree and mirrored by client-module fixtures where needed.
