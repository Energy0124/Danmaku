# NipaPlay Reference Feature Review

Updated on 2026-06-03.

Reference project:

```text
https://github.com/AimesSoft/NipaPlay-Reload
```

Analyzed reference snapshot:

```text
8bba3234f35cde999ccd3b669ec50de2f4c242a0
```

## Purpose

NipaPlay-Reload is a useful product reference for the end goal: a cross-platform
anime-focused media center with local playback, LAN sharing, danmaku, subtitles,
remote media libraries, history, and device-specific UX.

This is a feature review, not an implementation port. Danmaku keeps its current
Kotlin, Compose, Media3, libmpv, SQLDelight, and focused-Rust architecture. The
reference project's Flutter architecture and individual implementation choices
are not requirements.

## Current Danmaku Baseline

Already implemented:

- Windows anime-folder indexing with incremental SQLite catalog persistence.
- Paired trusted-LAN catalog and seekable byte-range streaming.
- Windows paired-server playback preparation with resume lookup.
- Windows host direct local-file playback preparation with resume lookup.
- Desktop mpv command planning for local-file and LAN-stream playback sources.
- Desktop playback-controller wrapper for the planned Windows mpv command
  boundary.
- Windows LAN discovery for Android and Android TV clients.
- Android and Android TV Media3 playback through a background service.
- Durable cross-device episode progress with resume seeking.
- Desktop SQLDelight storage primitives for app settings and download queue
  items.
- Shared danmaku timeline indexing and collision-aware scrolling-lane scheduling.
- Windows libmpv dynamic-loader probe.
- Dedicated Android TV application module with initial D-pad focus coverage.

## Reference Capabilities

The NipaPlay repository exposes or documents these relevant product areas:

- Local video scanning and categorized media browsing.
- PC-to-device LAN media-library sharing and discovery.
- Emby and Jellyfin integration with playback-history synchronization.
- SMB and WebDAV browsing, scanning, streaming, and subtitle access.
- Local and remote subtitles, external audio, embedded fonts, and track selection.
- Danmaku auto-match, manual match, local XML/JSON mounting, caching, filtering,
  density controls, timeline offset, top/bottom comments, and spoiler filtering.
- Watch history, one-click resume, episode navigation, and automatic next episode.
- Playback rate, seek controls, volume, brightness, fullscreen, buffering, and
  decoder or rendering settings.
- Web remote access, QR access, remote playback commands, and browser UI work.
- Picture-in-picture, file associations, desktop tray behavior, keyboard shortcuts,
  backup/restore, update checks, diagnostics, and localization.
- Bangumi progress, collections, ratings, comments, and seasonal-anime views.
- MyAnimeList watch-progress sync and manual anime rating updates are required
  Danmaku end-goal integrations.
- Plugin manifests, permissions, lifecycle hooks, settings, storage, and chained
  danmaku filtering.
- Experimental Anime4K, CRT shaders, GPU danmaku rendering, HDR work, AI spoiler
  filtering, torrent downloads, and additional platforms.

## Recommended Product Backlog

### P0: Finish The First Usable Vertical Slice

These remain the highest priority because they validate the platform foundation.

- [ ] Select and audit a pinned Windows `libmpv-2.dll` bundle.
- [ ] Run `mpv-probe` against the audited DLL in CI and a packaged Windows build.
- [ ] Render libmpv video in the Windows Compose shell.
- [ ] Play indexed local Windows episodes through the shared playback contract.
- [ ] Synchronize the Compose danmaku overlay to the real playback clock.
- [ ] Test Windows resize, fullscreen, seeking, pause/resume, rate changes,
  hardware decoding, 10-bit media, and 4K media.
- [x] Run Android TV D-pad instrumentation tests on a workspace-local API 34
  emulator.
- [ ] Exercise PC-to-mobile and PC-to-TV streaming plus cross-device resume on
  physical hardware.
- [x] Add same-PC Windows-to-Windows LAN streaming integration coverage for paired
  catalog requests, full-file reads, byte ranges, and progress round trips.
- [x] Test unauthorized media requests, invalid and unsatisfiable ranges, large
  files, and concurrent streams.
- [x] Add an Android client integration test against a live local-server fixture.
- [x] Add a compile-checked Android Media3 instrumentation fixture with a real
  short asset and loopback HTTP server.
- [x] Execute the Media3 streaming fixture on a workspace-local API 34 emulator.
- [x] Test background-service progress uploads while the Android player UI is not
  active.
- [x] Add LAN-server integration tests for progress updates during pause, seek,
  and episode completion.
- [x] Add LAN-client integration tests for reconnect after an interrupted
  catalog request.
- [x] Add LAN-client integration tests for slow catalog response timeouts.
- [x] Add LAN integration tests for slow media-stream playback and buffering.
- [ ] Add LAN subtitle streaming tests when subtitle endpoints are implemented.

### P1: Local Anime Library And Player

This turns the technical slice into a usable local media-center application.

- [ ] Model series, seasons, episodes, artwork, metadata source, and watched state.
- [ ] Support multiple indexed folders, removable drives, and missing-folder state.
- [ ] Add library search, sort, filters, recently watched, continue watching, and
  scan status.
- [ ] Add episode detail and series detail screens.
- [ ] Add previous episode, next episode, and configurable auto-next behavior.
- [ ] Add favorites and playlists after normalized episode identity is stable.
- [ ] Add embedded audio-track and subtitle-track selection to the playback contract.
- [ ] Load local `ASS`, `SSA`, `SRT`, and `VTT` subtitles where supported.
- [ ] Add local subtitle discovery next to indexed media files.
- [ ] Add playback controls for seek, rate, volume, fullscreen, and aspect ratio.
- [ ] Add Android TV player controls with visible focus states and remote-friendly
  seek behavior.
- [ ] Persist player, library, and UI settings in SQLDelight.

### P1: Danmaku Core

Danmaku is a first-class product feature, not a visual add-on.

- [ ] Define normalized scrolling, top, and bottom danmaku event types.
- [ ] Parse local XML and JSON danmaku files.
- [ ] Add manual danmaku-file mounting for an episode.
- [ ] Add timeline offset controls and remember per-episode offsets.
- [ ] Add show/hide, opacity, font-size, speed, density, and display-area settings.
- [ ] Add keyword, regular-expression, and user-defined filtering.
- [ ] Add danmaku cache storage with explicit expiry and cleanup behavior.
- [ ] Test seek reconstruction, pause/resume, playback-rate changes, and large tracks.
- [ ] Benchmark the existing scheduler and renderer before introducing GPU-specific
  complexity.
- [ ] Add a GPU or native rendering path only if profiling shows a real bottleneck;
  retain a stable Compose fallback.

### P2: Authorized Remote Media Sources

Use normalized source plugins. Do not leak provider response models into the
library database or UI.

- [ ] Define a `MediaSourcePlugin` contract for connection tests, browse, search,
  item details, playable variants, subtitles, progress reporting, and download
  policy.
- [ ] Add secure credential storage per platform.
- [ ] Implement WebDAV browse, scan, stream, and subtitle discovery.
- [ ] Implement SMB browse, scan, stream, and subtitle discovery.
- [ ] Implement Jellyfin libraries, item browsing, direct play, transcode fallback,
  subtitle selection, and progress sync.
- [ ] Implement Emby libraries, item browsing, direct play, transcode fallback,
  subtitle selection, and progress sync.
- [ ] Add source connection health, retry, timeout, and offline-cache behavior.
- [ ] Add unified search across configured sources.
- [ ] Deduplicate the same episode across local and remote sources only after
  normalized identity and conflict rules are defined.

### P2: Trusted-LAN Remote Experience

The existing LAN server should evolve deliberately instead of becoming a public
remote-access server by accident.

- [ ] Add pairing lifecycle: revoke device, rotate token, list paired devices, and
  expire temporary pairings.
- [ ] Persist trusted-device settings without logging tokens.
- [ ] Add QR pairing for mobile setup.
- [ ] Add API version negotiation and compatibility errors.
- [ ] Stream subtitles and selected external audio over the LAN.
- [ ] Add thumbnail endpoints for seek previews.
- [ ] Add a remote-control API for play, pause, seek, volume, and current state.
- [ ] Add a responsive local-network web remote after the remote-control API is
  stable.
- [ ] Require a separate threat model, authentication design, and TLS strategy
  before supporting access outside a trusted LAN or VPN.

### P3: Offline Downloads

Downloads remain authorized-source-only.

- [ ] Define source policy fields: offline allowed, expiry, DRM status, attribution,
  and delete requirements.
- [ ] Define a platform-independent download manifest and queue state machine.
- [ ] Import and incrementally rescan user-selected ani-rss-managed output folders
  through the Windows local-library indexer.
- [ ] Add an optional read-only ani-rss adapter for subscriptions, completed episode
  lists, resolved output folders, download status, and completion-triggered rescans.
- [ ] Keep ani-rss subscription and download-control actions disabled until the
  authorized-source policy explicitly permits them.
- [x] Add desktop SQLDelight storage primitives for queue items and app settings.
- [ ] Persist queue, progress, retries, failures, and verified output paths.
- [ ] Add Media3 `DownloadService` for Android and Android TV.
- [ ] Add the Rust Windows download engine behind coarse-grained Kotlin APIs.
- [ ] Add pause, resume, retry, deletion, disk quota, and low-space handling.
- [ ] Test interrupted transfers, corrupted output, expired URLs, and app restarts.
- [ ] Keep torrent support out of the default backlog unless a concrete authorized
  distribution use case and compliance policy are approved.

### P3: Product Operations And Polish

- [ ] Add structured diagnostics with token and URL redaction.
- [ ] Add exportable support bundles and platform/build metadata.
- [ ] Add database backup, restore, and schema-migration tests.
- [ ] Add Windows file associations and open-with behavior.
- [ ] Add desktop tray, close behavior, startup behavior, and optional single-instance
  handling.
- [ ] Add desktop picture-in-picture after Windows video rendering is stable.
- [ ] Add configurable desktop shortcuts and TV remote mappings.
- [ ] Add update checks and release-channel metadata.
- [ ] Add localization infrastructure before large UI expansion.
- [ ] Add light/dark themes and restrained appearance customization after core flows
  are stable.

### P4: Anime Metadata And Community Integrations

These improve anime-specific UX but require external APIs, credential handling,
rate-limit handling, and clear user consent.

- [ ] Define metadata-provider contracts separately from media-source contracts.
- [ ] Add series metadata matching and manual correction.
- [ ] Add seasonal and weekly anime calendar views.
- [ ] Add MyAnimeList OAuth account authorization and secure token storage.
- [ ] Add MyAnimeList anime identity mapping with manual correction.
- [ ] Automatically sync watched episode progress to MyAnimeList after meaningful
  playback progress and episode completion.
- [ ] Add manual MyAnimeList anime rating submission.
- [ ] Add MyAnimeList sync retry behavior, last-success status, and user-visible
  errors without logging credentials.
- [ ] Evaluate Bangumi integration for collections, progress, ratings, and comments.
- [ ] Evaluate an authorized danmaku provider integration for matching, fetching,
  progress sync, and comment submission.
- [ ] Add provider-specific account flows only after secure token storage exists.
- [ ] Make all external sync opt-in and expose the last successful sync time.

### P4: Plugin System

Start with typed contracts. Arbitrary scripting should be a later decision.

- [ ] Define plugin categories: media source, metadata provider, danmaku provider,
  filter, and optional UI extension.
- [ ] Define manifest fields, host-version compatibility, permissions, enablement,
  and isolated per-plugin settings.
- [ ] Define lifecycle events and error isolation.
- [ ] Implement built-in Kotlin plugins first.
- [ ] Add external plugin loading only after signature, sandbox, update, and
  permission-review rules are designed.
- [ ] Treat JavaScript plugins as an optional implementation choice, not a product
  requirement.

### P5: Expansion

- [ ] Add macOS and Linux desktop packaging after Windows libmpv behavior is stable.
- [ ] Add iOS and iPadOS after shared contracts settle.
- [ ] Add a browser streaming client after the LAN web remote is proven.
- [ ] Evaluate optional backend sync for accounts, device history, and cloud backup.
- [ ] Evaluate public remote access only with a hardened backend or explicit VPN-only
  positioning.

## Explicit Review Decisions

Review these before adding them to the committed roadmap:

- [ ] Approve WebDAV as the first remote source after local playback.
- [ ] Approve SMB as the second remote source.
- [ ] Approve Jellyfin before Emby, or reverse the order.
- [ ] Decide whether the local-network browser remote is a first-release feature.
- [ ] Decide whether Bangumi integration is in scope for the first public release.
- [x] Include automatic MyAnimeList watch-progress sync and manual anime rating in
  the end goal.
- [ ] Decide which authorized danmaku provider integrations are acceptable.
- [ ] Decide whether external plugins are required or built-in plugins are enough.
- [ ] Decide whether torrent support is excluded, deferred, or allowed only for
  explicitly authorized sources.
- [ ] Decide whether ani-rss is import-only, read-only monitored, or allowed to
  receive subscription and download-control commands from Danmaku.
- [ ] Decide whether Anime4K and CRT shaders are experiments or release features.
- [ ] Decide whether AI spoiler filtering is worth the privacy, credential, and UX
  cost.
- [ ] Decide whether cloud backup and public remote access are product goals or
  later optional services.

## Features To Avoid Copying Directly

- Multiple playback engines on the same platform. Danmaku should prove libmpv on
  Windows and Media3 on Android before adding fallback engines.
- Public remote access built on the trusted-LAN server. The security requirements
  are materially different.
- Torrent search or downloading without an authorized-source policy.
- Arbitrary JavaScript execution before plugin isolation and permission enforcement
  are designed.
- GPU danmaku rendering before measurements show that Compose rendering is
  insufficient.
- Provider-specific models in shared domain storage.

## Reference Evidence

- Repository README:
  `https://github.com/AimesSoft/NipaPlay-Reload`
- User guide:
  `https://github.com/AimesSoft/NipaPlay-Reload/blob/main/Documentation/user-guide.md`
- Advanced settings:
  `https://github.com/AimesSoft/NipaPlay-Reload/blob/main/Documentation/settings.md`
- Media server integration:
  `https://github.com/AimesSoft/NipaPlay-Reload/blob/main/Documentation/server-integration.md`
- JavaScript plugin API:
  `https://github.com/AimesSoft/NipaPlay-Reload/blob/main/Documentation/js-plugin-api.md`
- Web server design:
  `https://github.com/AimesSoft/NipaPlay-Reload/blob/main/docs/WEB_SERVER_IMPLEMENTATION.md`
- Media aggregation design:
  `https://github.com/AimesSoft/NipaPlay-Reload/blob/main/docs/MEDIA_AGGREGATION_GUIDE.md`
- GPU danmaku engine design:
  `https://github.com/AimesSoft/NipaPlay-Reload/blob/main/docs/GPU_DANMAKU_ENGINE_DESIGN.md`
