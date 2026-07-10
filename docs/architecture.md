# Architecture

Danmaku uses Kotlin and Compose for app surfaces, shared Kotlin modules for
domain contracts and LAN behavior, Media3 for Android playback, libmpv for
desktop playback, SQLDelight/SQLite for durable desktop state, and focused Rust
only where native boundaries are useful.

## Platform Roles

### Windows Desktop

The Windows desktop app is the primary player and starts the Rust library
server as an owned sidecar by default:

- indexes local anime folders;
- persists catalog, settings, progress, provider cache, mappings, and queue
  state;
- connects to the sidecar over the existing LAN client boundary while the
  sidecar serves paired devices over trusted LAN HTTP;
- plays local and paired LAN media through libmpv;
- sends external provider search/read/write work through Rust server routes.

There is no embedded JVM server in the desktop process. With no host flags the
app owns a `library-server` sidecar; `--remote-server-url` selects an existing
host instead. The sidecar executable and web assets are staged under the
packaged application's `app/server` directory.

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

The Rust `native/library-server` is the standalone and desktop-sidecar host. It
owns indexing, catalog, progress, provider, HTTP/Web UI, and discovery behavior
behind a data-directory lock. The experimental JVM headless app remains only
for compatibility until Phase 4 of the Rust migration.

### Rust Native Client

A Rust native client is a later experiment for native playback feel. It should
consume the same LAN HTTP API as other clients and hand media URLs to mpv/libmpv
rather than duplicating library hosting, provider sync, or metadata storage.

## Module Boundaries

```text
shared:domain
  Pure domain models and behavior. No platform playback or provider HTTP code.

shared:library-server-core
  JVM LAN server primitives, HTTP routes, discovery announcements, hooks, and
  progress-store contracts used by the legacy headless JVM host and tests; the
  desktop does not depend on it.

shared:library-host-core
  Legacy JVM host lifecycle/config/status contracts; the desktop does not
  depend on it.

shared:library-client
  Common LAN client models, connection sessions, playback preparation, and
  progress-sync policy.

shared:library-client-android
  Android HTTP, discovery, saved connection, and favorite-store adapters.

shared:player-android-media3
  Media3 controller and playback service implementation behind shared playback
  contracts.

apps:desktop-windows
  Compose desktop UI, SQLDelight store, local indexing, provider clients,
  desktop library server runtime, mpv bridge integration, and release surface.

apps:android-mobile
  Compose mobile/tablet UI.

apps:android-tv
  Compose TV UI and focus/navigation behavior.

apps:web-ui
  Planned Vite TypeScript browser client served by the trusted-LAN server.

apps:library-server-windows
  Planned opt-in headless JVM host for Windows library/server workflows.

native:player-windows-mpv
  libmpv loader/probe and C ABI used by the desktop app.

native:rust-core
  Focused Rust timeline/indexing work.
```

## Data Flow

1. Desktop indexes local folders into normalized `LibraryCatalog` data.
2. Desktop enriches catalog items with cached provider metadata/poster state.
3. Desktop publishes the catalog over trusted LAN with a pairing token.
4. Android, TV, web, desktop-remote, and future Rust clients fetch
   catalog/progress and prepare playback URLs over HTTP.
5. Clients stream over HTTP byte ranges and upload progress.
6. Desktop local playback uses the same catalog/progress concepts and writes
   local progress directly.
7. External tracking derives provider-neutral updates from local progress and
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
