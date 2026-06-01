# Architecture

## Goals

Danmaku manages a local media library, streams authorized media sources,
downloads permitted content for offline playback, and renders synchronized
danmaku overlays.

The architecture prioritizes Windows, Android, and Android TV while leaving a
clear path to macOS, Linux, iOS, iPadOS, and web.

## System Shape

```mermaid
flowchart TD
    Apps["Compose apps"] --> Shared["Shared Kotlin modules"]
    Apps --> Players["Platform player adapters"]
    Shared --> Native["Focused Rust core"]
    Shared --> Db["SQLDelight / SQLite"]
    Shared --> Sources["Authorized source plugins"]
    Players --> Mpv["Windows: libmpv"]
    Players --> Media3["Android and TV: Media3 ExoPlayer"]
    Shared --> Api["Optional sync API"]
```

## Platform Matrix

| Target | UI | Playback | Downloads | Priority |
| --- | --- | --- | --- | --- |
| Windows | Compose Multiplatform Desktop | libmpv | Rust download engine | First class |
| Android | Jetpack Compose | Media3 ExoPlayer | Media3 DownloadService | First class |
| Android TV | Compose for TV | Media3 ExoPlayer | Media3 DownloadService | First class |
| macOS and Linux | Compose Multiplatform Desktop | libmpv | Rust download engine | Later |
| iOS and iPadOS | Compose Multiplatform or SwiftUI | AVPlayer | AVAssetDownloadTask | Later |
| Web | React and TypeScript | HTML video and hls.js | Limited browser storage | Later |

Android TV is a dedicated application module. It shares domain behavior with
Android mobile but owns its 10-foot layouts, focus states, and D-pad navigation.

## Repository Modules

```text
apps/
  desktop-windows/       Compose desktop app and Windows packaging
  android-mobile/        Android phone and tablet app
  android-tv/            TV-specific Compose app

shared/
  domain/                Normalized models and contracts
  database/              SQLDelight schema and repositories
  networking/            Ktor clients and source transport
  danmaku/               Kotlin-facing scheduling and filtering facade

native/
  rust-core/             Parsing, indexing, and later desktop download helpers
  player-windows-mpv/    Windows libmpv adapter
```

Create modules when their first vertical slice needs them. Empty placeholder
modules are avoided.

## Domain Model

The library should normalize provider-specific data into these core concepts:

```text
MediaItem
Episode
PlaybackVariant
AudioTrack
SubtitleTrack
DanmakuTrack
DanmakuEvent
DownloadManifest
DownloadJob
PlaybackProgress
```

Provider plugins may browse, search, resolve playable variants, retrieve
danmaku tracks, and report whether downloads are allowed. Provider response
types must remain at the plugin boundary.

## Playback Boundary

Shared application code owns playback intent and observable state:

```text
load(media)
play()
pause()
seek(position)
setPlaybackRate(rate)
selectAudioTrack(track)
selectSubtitleTrack(track)
observeState()
```

Platform adapters own codecs, rendering surfaces, lifecycle integration,
media sessions, hardware decoding, and DRM-capable platform APIs.

The Windows adapter loads libmpv dynamically. Developer builds locate
`libmpv-2.dll` from `DANMAKU_LIBMPV_PATH` or beside the packaged executable.
Release packaging must use an audited, pinned libmpv bundle and include the
applicable license notices. See ADR 0002.

## Danmaku Pipeline

```mermaid
flowchart LR
    Input["XML / JSON / provider events"] --> Parse["Parse and normalize"]
    Parse --> Index["Time index"]
    Index --> Query["Query playback window"]
    Query --> Filter["Apply user filters"]
    Filter --> Layout["Allocate lanes"]
    Layout --> Canvas["Compose Canvas overlay"]
```

Rust initially owns a compact time index for normalized events. Kotlin should
request batches for a playback window. Rendering and animation remain in
Compose so the native boundary is not crossed per frame or per comment.

## Storage

Use SQLite through SQLDelight for the local library, playback progress,
settings, source metadata, and download state. Store downloaded media outside
the database and record verified paths and manifests.

## Rust Boundary

Rust is a supporting component, not the application framework. Candidate APIs:

```text
buildTimeline(events) -> Timeline
eventsInWindow(timeline, start, end) -> EventBatch
parseDanmaku(input) -> Timeline
startDesktopDownload(plan) -> DownloadId
observeDesktopDownload(id) -> ProgressEvents
```

Add Kotlin bindings after the Rust API has proven useful. Prefer UniFFI or a
small C ABI. Keep allocations and serialization visible in performance tests.

## Backend

Cloud services are optional for the first local vertical slice. When needed,
use a small Ktor service with PostgreSQL, Redis, S3-compatible storage, and
WebSockets for live danmaku rooms and synchronization.

## Security And Compliance

- Download content only when the source permits offline storage.
- Keep credentials in platform secure storage.
- Do not log tokens, cookies, or signed media URLs.
- Do not implement DRM circumvention.
- Validate filenames, paths, manifests, and remote input.
