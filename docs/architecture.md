# Architecture

Danmaku uses a Rust-native Windows player and server, dedicated Kotlin Android
clients, and a TypeScript web administration UI. Shared Kotlin modules contain
only platform-neutral domain and LAN-client behavior; desktop hosting and
provider integrations live in Rust.

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
It owns MAL OAuth state/token exchange and encrypted refresh tokens; the native
player owns only the fixed loopback browser callback and forwards the short-
lived authorization code. Bangumi tokens are validated against `/v0/me`
before encrypted storage.

`native/player-windows-mpv` is an ordinary Rust library used by the player. It
owns libmpv discovery/loading and the OpenGL render API bindings plus the
`mpv-probe` diagnostic binary. It exposes no JVM/JNA-compatible dynamic-library
ABI.

### Android Mobile And TV

Android applications are trusted-LAN clients. They share domain, connection,
Media3, and authenticated tracking transport code while retaining
platform-specific UI. They may read provider state and submit an explicitly
reviewed, server-validated tracking preview, but provider credentials, mappings,
and conflict reconciliation remain server-owned Windows/web administration.
Android TV remains a
dedicated module with TV layouts, D-pad focus, remote navigation, and
Macrobenchmark coverage.

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
  Android HTTP, discovery, connection, and persistence adapters.

shared:player-android-media3
  Android Media3 playback service and adapter.

apps:android-mobile / apps:android-tv
  Dedicated Android presentation and navigation.

native/library-server
  Authoritative desktop catalog/provider/progress host.

native/player-app
  Windows UI, library client, playback, and server supervision.

native/player-windows-mpv
  Rust-only libmpv loader/render integration and probe.

apps/web-ui
  Browser client and server administration.
```

## Data Flow

1. The Rust server scans configured authorized roots and persists normalized
   catalog state.
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
and experimental macOS desktop target are retired. The Rust server does not
read or migrate their SQLite database. Old files are left untouched on user
machines; a native installation starts from Rust settings and a fresh scan.

The trusted-LAN wire contract remains version 1 so existing Android clients
continue to interoperate. Contract fixtures are owned by the Rust server test
tree and mirrored by client-module fixtures where needed.
