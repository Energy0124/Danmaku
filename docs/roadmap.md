# Roadmap

The roadmap is ordered by product dependency, not by platform parity. Windows,
Android mobile/tablet, and Android TV stay first-class until the core local
library and playback workflows are stable.

## Phase 1 - Windows Local Playback Foundation

Status: mostly implemented, broader QA still needed.

- Keep libmpv playback stable through the Rust/JNA bridge.
- Preserve the embedded Windows video host and compact playback controls.
- Keep local library play/resume and progress persistence reliable.
- Validate fullscreen, resize, aspect ratio, 4K media, hardware decoding, and
  multi-display behavior.

## Phase 2 - Local Library And Trusted LAN Streaming

Status: implemented vertical slice, continuing UI and QA polish.

- Maintain multi-root indexing and incremental rescans.
- Keep the desktop library server, pairing, byte-range streaming, subtitle
  streaming, poster serving, progress API, and discovery stable.
- Keep Android mobile and TV browsing/streaming/progress upload working against
  the Windows host.
- Finish mobile/tablet/TV viewport and focus QA.

## Phase 3 - Danmaku Overlay And Metadata Quality

Status: dandanplay overlay/cache path implemented; more controls remain.

- Improve danmaku filtering, offset controls, styling controls, and playback
  clock behavior.
- Keep cached dandanplay match/comment behavior explainable in UI.
- Continue improving matched anime metadata, poster freshness, and file-vs-anime
  title clarity.

## Phase 4 - External Anime Mapping And Tracking

Status: implementation complete enough for live manual QA.

- Complete live MyAnimeList and Bangumi account QA.
- Confirm conflicts, retry/failure messaging, and relaunch behavior.
- Decide whether external sync failures should persist across relaunches or
  remain session-only.

## Phase 5 - Authorized Download Manager

Status: queue storage and shell surfaces exist; download engine is not
implemented.

- Define authorized source contracts.
- Implement queue execution, pause/resume, retry, cache management, and
  diagnostics.
- Integrate ani-rss completed output and future provider plugins without
  bypassing authorization or service terms.

## Phase 6 - Packaging And Platform Expansion

Status: Windows packaging exists; macOS is experimental.

- Keep the runtime-free Windows portable artifact reproducible.
- Re-audit the pinned libmpv dependency before changing producer artifacts or
  hashes.
- Promote macOS only after embedded video and packaging are release-ready.
- Consider Linux/iOS/iPadOS/web only after first-class target workflows are
  dependable.
