# Architecture

Danmaku uses Kotlin and Compose for app surfaces, shared Kotlin modules for
domain contracts and LAN behavior, Media3 for Android playback, libmpv for
desktop playback, SQLDelight/SQLite for durable desktop state, and Rust for the
library server plus the in-progress native Windows player migration where
native boundaries are useful.

## Platform Roles

### Windows Desktop

The Windows desktop app is the primary local application shell:

- indexes local anime folders;
- persists catalog, settings, progress, provider cache, mappings, and queue
  state;
- owns a Rust `library-server` child process that serves paired devices over
  trusted LAN HTTP;
- plays local and paired LAN media through libmpv;
- resolves metadata/posters/danmaku from configured providers.

Local desktop mode starts the Rust server sidecar by default and restarts it
after root changes or rescans. Supplying an explicit remote-server URL selects
remote-only mode and skips the local child process. The retired embedded JVM
server is no longer a desktop runtime path.

### Android Mobile And Tablet

The mobile app is a LAN client. It discovers or manually connects to a trusted
desktop server, browses the published catalog, prepares Media3 playback, and
uploads progress.

### Android TV

Android TV is a separate app module with TV-specific layout, focus behavior,
D-pad flows, and remote-friendly information density. It shares domain,
library-client, and Media3 playback code with mobile where practical.

### macOS Desktop

macOS reuses the Compose desktop shell and native mpv command bridge as an
experimental development path. It is not yet a first-class release target.

### Web UI

The web UI is a planned trusted-LAN client served by the library server under
`/web/`. It uses the same HTTP JSON catalog/progress API and normal HTTP media,
subtitle, and poster URLs as Android/TV. Browser support is additive; existing
clients must not require the web UI to be present.

### Headless Library Server

The Rust `native/library-server` binary is both the standalone headless server
and the desktop-owned sidecar. It owns LAN HTTP/discovery, scanning, catalog
snapshots, progress, and provider routes behind a data-directory lock. The
experimental JVM headless application remains available during migration.

### Rust Native Client

The `native/player-app` migration client is an egui/glow Windows player that
composites libmpv's OpenGL render API output beneath native controls and
danmaku. Its current playback/danmaku slice accepts direct media paths or URLs,
loads normalized comments through the Rust server's client-facing
`/api/danmaku/{mediaId}` route, supports local XML/JSON drag-and-drop, and
keeps existing ASS overlays playable through mpv. Library browsing, discovery,
progress sync, localization, and packaging remain later Phase 3 milestones.
The client must not duplicate library hosting, provider settings, sync, or
metadata storage.

## Module Boundaries

```text
shared:domain
  Pure domain models and behavior. No platform playback or provider HTTP code.

shared:library-server-core
  Legacy JVM LAN server/provider primitives retained for the experimental JVM
  headless host and remaining desktop provider-contract migration.

shared:library-host-core
  JVM host lifecycle/config/status contracts; no longer used by desktop.

shared:library-client
  Common LAN client models, connection sessions, playback preparation, and
  progress-sync policy.

shared:library-client-android
  Android HTTP, discovery, saved connection, and favorite-store adapters.

shared:player-android-media3
  Media3 controller and playback service implementation behind shared playback
  contracts.

apps:desktop-windows
  Compose desktop UI, SQLDelight store, local indexing/provider clients, Rust
  sidecar lifecycle ownership, mpv bridge integration, and release surface.

apps:android-mobile
  Compose mobile/tablet UI.

apps:android-tv
  Compose TV UI and focus/navigation behavior.

apps:web-ui
  Planned Vite TypeScript browser client served by the trusted-LAN server.

apps:library-server-windows
  Experimental opt-in headless JVM host retained during the Rust migration.

native:library-server
  Rust headless LAN server and default desktop-owned sidecar.

native:player-app
  In-progress egui/glow Windows client with libmpv compositing, playback
  controls, and native danmaku rendering.

native:player-windows-mpv
  libmpv loader/probe and C ABI used by the desktop app.

native:rust-core
  Focused Rust timeline/indexing work.
```

## Data Flow

1. Desktop indexes local folders into normalized `LibraryCatalog` data.
2. Desktop enriches catalog items with cached provider metadata/poster state.
3. The desktop-owned Rust sidecar scans the registered roots and publishes its
   catalog over trusted LAN with a pairing token; desktop rescans restart the
   sidecar so the child process sees current roots and files.
4. Android, TV, web, desktop-remote, and native clients fetch catalog/progress
   and prepare playback URLs over HTTP.
5. Clients stream over HTTP byte ranges and upload progress.
6. Compose desktop local playback uses the same catalog/progress concepts and
   writes local progress directly.
7. The native player currently accepts a direct media source and can request
   normalized dandanplay comments from the Rust server by catalog media ID;
   Phase 3 M3 will add catalog/progress client flows.
8. External tracking derives provider-neutral updates from local progress and
   mapped series, then writes through provider-specific clients only when the
   user triggers sync.

## Provider Boundary

Provider integrations are treated as plugins conceptually, even when currently
implemented in the desktop app:

- dandanplay: metadata, match, comment, poster/cache support.
- MyAnimeList: search, OAuth, mapping, metadata cache, progress write client.
- Bangumi: search, mapping, metadata cache, progress write client.
- ani-rss: read-only monitoring and authenticated completion webhook support.

Provider response objects should stay at the client boundary. Durable storage
uses normalized domain models and provider IDs.

## Security Rules

- The LAN server is for trusted local networks only.
- Pairing tokens protect catalog/media/progress routes but are not an
  internet-facing auth design.
- The web UI shell may be served without a token, but data/media/progress
  routes still require the pairing token.
- Do not log credentials, pairing tokens, cookies, signed URLs, or raw provider
  secrets.
- Store credentials through DPAPI/encrypted app settings or ignored local
  development files.
- Support authorized media sources only.
- Do not add DRM circumvention.

## Native Boundary

Rust APIs must stay coarse-grained. Do not cross the Kotlin/Rust boundary per
frame or per danmaku comment. Use Rust for native loading/probing, high-throughput
parsing/indexing, and platform helper APIs only when it earns the complexity.

## Process Boundary

Danmaku uses shared Kotlin modules inside one process and HTTP JSON plus normal
byte-range media URLs across process/device boundaries. gRPC is not part of the
main split because browsers, Media3, and mpv already handle HTTP media streams
well, while gRPC would add friction for browser playback and LAN debugging.
