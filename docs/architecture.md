# Architecture

Danmaku uses Kotlin and Compose for app surfaces, shared Kotlin modules for
domain contracts and LAN behavior, Media3 for Android playback, libmpv for
desktop playback, SQLDelight/SQLite for durable desktop state, and focused Rust
only where native boundaries are useful.

## Platform Roles

### Windows Desktop

The Windows desktop app is currently the primary host:

- indexes local anime folders;
- persists catalog, settings, progress, provider cache, mappings, and queue
  state;
- serves paired devices over trusted LAN HTTP;
- plays local and paired LAN media through libmpv;
- resolves metadata/posters/danmaku from configured providers.

The desktop process is also the library server. A separate headless server is a
later step after API shape, lifecycle, diagnostics, settings, and firewall
behavior settle.

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

## Module Boundaries

```text
shared:domain
  Pure domain models and behavior. No platform playback or provider HTTP code.

shared:library-server-core
  JVM LAN server primitives, HTTP routes, discovery announcements, hooks, and
  progress-store contracts.

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

native:player-windows-mpv
  libmpv loader/probe and C ABI used by the desktop app.

native:rust-core
  Focused Rust timeline/indexing work.
```

## Data Flow

1. Desktop indexes local folders into normalized `LibraryCatalog` data.
2. Desktop enriches catalog items with cached provider metadata/poster state.
3. Desktop publishes the catalog over trusted LAN with a pairing token.
4. Android clients fetch catalog/progress and prepare playback URLs.
5. Media3 clients stream over HTTP byte ranges and upload progress.
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
