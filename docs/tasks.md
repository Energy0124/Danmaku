# Task Backlog

The backlog is ordered. Complete the earliest unblocked task before expanding
the platform surface.

## Current Focus

Windows playback now has a packaged Rust/JNA/libmpv command path, stable native
child-window host, player-first Playback tab, packaged mpv DLLs in the default
Compose distributable, and a fast GUI smoke script for real local files. The
shared scrolling danmaku lane scheduler is implemented, and Windows can attach
indexed sidecars plus generated or dandanplay-fetched ASS danmaku tracks through
mpv. Android mobile and TV clients can browse the LAN catalog and pass selected
streams plus indexed sidecar subtitles to Media3.

## Feature-Complete Product Tracks

These tracks define the current target for a NipaPlay-like first public release
without relaxing the platform priorities. Keep implementing the earliest
unblocked slices inside these tracks as the vertical slice hardens.

- [ ] Promote the library model from flat media files to normalized series,
  seasons, episodes, artwork, metadata source, watched state, favorites, and
  playlist membership.
- [x] Add shared deterministic series and season grouping helpers as the first
  normalized library-model foundation.
- [ ] Add series detail, episode detail, continue-watching, recently-watched,
  and next-up surfaces on Windows, Android mobile, and Android TV.
- [ ] Add first-class danmaku controls across playback screens: local file
  mounting, offset, opacity, font size, speed, density, display area,
  show/hide, keyword filters, and regex filters.
- [ ] Add trusted-device management for LAN pairing: device list, revoke,
  rotate token, temporary pairings, QR pairing, API compatibility errors, and
  token-redacted diagnostics.
- [ ] Add authorized remote-source plugin contracts, then implement WebDAV,
  SMB, Jellyfin, and Emby as normalized source adapters with progress sync.
- [ ] Add offline-download queue behavior for authorized sources on Windows,
  Android mobile, and Android TV: pause, resume, retry, delete, disk quota,
  low-space handling, and restart recovery.
- [ ] Add macOS desktop support after the desktop media boundary is portable:
  packaging, native playback implementation choice, filesystem permissions,
  code-signing/notarization notes, and smoke verification.
- [ ] Add polished platform UX passes: Windows desktop tray/file associations,
  Android phone/tablet adaptive layouts, Android TV remote-first library and
  player screens, support bundles, update checks, and localization plumbing.

## Active

- [x] Record the initial architecture and platform priorities.
- [x] Add a dependency-free Rust workspace and timeline index.
- [x] Bootstrap Gradle 9.4.1 with a committed wrapper.
- [x] Add a minimal shared Kotlin domain module.
- [x] Decide the Windows libmpv distribution strategy.
- [x] Add a reproducible Windows libmpv bundle manifest verifier and opt-in
  distributable packaging path.
- [x] Add a dependency-free Windows libmpv dynamic-loader spike.
- [x] Expose a Rust C ABI for creating an mpv context, executing command arrays,
  and destroying the handle.
- [x] Add a desktop mpv command planner for local files, LAN streams, seek, and
  playback-rate commands.
- [x] Add a desktop `PlaybackController` wrapper around the mpv command boundary.
- [x] Connect prepared local and paired-LAN playback requests to the desktop
  `PlaybackController` with resume seeking.
- [x] Bind the desktop Kotlin command executor to the Rust C ABI with JNA.
- [x] Verify the dynamic-loader probe against the approved pinned LGPL libmpv
  dependency.
- [x] Smoke-test the dynamic-loader probe against a pinned third-party libmpv
  candidate without approving it for redistribution.
- [x] Identify and smoke-test a pinned LGPL libmpv candidate for the MIT
  application distribution model.
- [x] License Danmaku under MIT and add a pinned, hash-verified libmpv
  dependency installer.
- [x] Include MIT and third-party notices in Windows, Android mobile, and
  Android TV build artifacts.
- [x] Package the Apache License 2.0 text with Android mobile, Android TV, and
  Windows artifacts.
- [x] Approve direct redistribution of the pinned zhongfly LGPL libmpv build
  with GPL/LGPL texts, source/provenance notice, and CI hash verification.
- [x] Wire the JNA mpv command executor into the desktop shell using the pinned
  approved native dependency.
- [x] Build a Windows libmpv playback spike using a native child-window host.
- [x] Stabilize the Windows native mpv host across `loadfile` playback.
- [x] Package the Rust mpv bridge and approved local `libmpv-2.dll` in the
  default Windows Compose distributable.
- [x] Replace the diagnostic-heavy Windows Playback tab with a player-first
  video surface and compact icon controls.
- [x] Add a shared scrolling danmaku lane scheduler with collision-aware tests.
- [x] Add recursive Windows anime-folder indexing.
- [x] Add a paired trusted-LAN catalog server with HTTP byte-range streaming.
- [x] Remember the selected Windows anime folder and add one-click rescanning.
- [x] Persist the Windows catalog in SQLDelight and reuse unchanged rows.

## Next

- [x] Define the Kotlin playback contract.
- [x] Build a Compose Desktop shell.
- [x] Add local-file playback on Windows.
- [x] Add a packaged Windows local-media `loadfile` smoke probe.
- [x] Add a packaged Windows GUI playback smoke script for quick real-video
  verification.
- [x] Smoke-test packaged Windows GUI playback with a real local media file.
- [ ] Validate the Windows child-window player with resize, fullscreen
  transitions, hardware decoding, 10-bit files, and 4K media.
- [x] Render a synthetic danmaku overlay on top of Windows mpv playback.
- [x] Add bounded visible-window queries and generated-track scheduler tests.
- [x] Measure overlay behavior with a large generated timeline.
- [x] Define normalized scrolling, top, and bottom danmaku event modes.
- [x] Parse local Bilibili-style XML and normalized JSON danmaku files into the
  shared domain model.
- [x] Add a Windows dandanplay-compatible API client for first-16MB MD5 media
  matching, optional 弹弹play开放平台 authentication headers, and fetched-comment
  normalization into shared danmaku events.
- [x] Add Windows settings for dandanplay-compatible API base URL and optional
  AppId/AppSecret credentials with secure storage and redacted diagnostics.
- [x] Attach matched dandanplay-fetched tracks to Windows local-library playback
  by rendering fetched comments into a cached ASS overlay file.
- [x] Persist chosen dandanplay `episodeId` values and fetched raw comments per
  local catalog item so rematches are avoidable when file fingerprints match.
- [x] Add Windows prepared-playback controls to force-refresh or clear the
  cached dandanplay match/comments for an episode.
- [x] Add explicit dandanplay cache-expiry policy and cleanup controls.
- [x] Add shared danmaku display settings for visibility, opacity, font scale,
  speed, density, display area, keyword filters, and regex filters, and wire
  Windows ASS overlay rendering to those persisted controls.
- [x] Add persisted Windows danmaku offset controls and apply the offset while
  rendering synthetic and fetched ASS overlay timestamps.
- [x] Add Windows prepared-playback controls to attach a local danmaku XML or
  JSON file as a generated ASS overlay while preserving normal subtitle tracks.
- [x] Split Windows prepared-playback danmaku overlay removal from dandanplay
  cache clearing so manual overlays can be removed without touching provider
  cache state.
- [x] Add seek and playback-rate test cases.
- [x] Add Windows progress-bar scrubbing, live mpv position polling, and
  throttled local/paired progress persistence.
- [x] Attach indexed local and paired-LAN sidecar subtitles to Windows playback.
- [x] Add Windows runtime audio/subtitle track discovery and selection controls.
- [x] Add shared volume command/state plus Windows, Android mobile, and Android
  TV volume controls.
- [x] Add shared library search, title/path sorting, and subtitle-only filtering
  across Windows, Android mobile, and Android TV catalog screens.
- [x] Back the Windows desktop library browser with shared series grouping and
  add a compact series detail panel.
- [x] Add a Windows continue-watching list backed by persisted local playback
  progress.
- [x] Add a Windows recently-watched list backed by persisted local playback
  progress.
- [x] Add Windows prepared-playback previous/next episode navigation for local
  catalog items.
- [x] Add Windows one-click local library play/resume actions while retaining
  explicit prepare actions for diagnostics.
- [x] Add shared next-up derivation and expose a Windows local-library Next Up
  card backed by persisted playback progress.
- [x] Add shared watched-state derivation and display new, in-progress, and
  watched labels across Windows, Android mobile, and Android TV library rows.
- [x] Extract shared continue-watching and recently-watched derivation, then
  expose those progress rails on Android mobile and Android TV.
- [x] Add shared series watch-summary derivation and expose watched, watching,
  and new counts in Windows, Android mobile, and Android TV series surfaces.
- [x] Add shared episode-detail derivation and expose Windows desktop, Android
  mobile, and Android TV episode detail panels with previous/next navigation.
- [x] Add optional persisted Windows local-library auto-next playback.
- [x] Add persisted Windows local-library favorites with episode-row/detail
  toggles and a favorites-only episode filter.
- [x] Add Android mobile and Android TV local favorite episode toggles and
  favorites-only library filters backed by Android preference storage.
- [x] Add Windows fullscreen and aspect-ratio playback controls.
- [x] Add Windows paired-catalog one-click stream playback while retaining
  explicit prepare actions for diagnostics.
- [x] Add Windows paired-catalog search, title/path sorting, subtitle filtering,
  and a filter-reset empty state.
- [x] Add structured Windows paired-catalog episode rows with media metadata and
  compact prepare/play actions.
- [x] Persist Windows playback defaults for volume, playback rate, and aspect
  mode.
- [x] Add Windows player keyboard shortcuts for play/pause, seek, volume,
  playback rate, track cycling, aspect mode, and fullscreen.
- [ ] Continue tightening fullscreen player chrome around the stable native
  video host.

## Android And TV

- [x] Create the Android mobile application.
- [x] Create the dedicated Android TV application.
- [x] Add a shared Media3 ExoPlayer adapter.
- [x] Browse the Windows LAN catalog and stream selected episodes.
- [x] Add in-process MediaSession integration.
- [x] Add trusted-LAN pairing codes.
- [x] Add background MediaSession service integration.
- [x] Add LAN server discovery.
- [x] Add an unauthenticated LAN server compatibility/status probe and shared
  JVM/Android client methods as a foundation for pairing compatibility errors
  and trusted-device management.
- [x] Wire Windows paired browsing plus Android mobile and Android TV connection
  flows to preflight LAN server compatibility before paired catalog requests.
- [x] Centralize LAN server compatibility preflight and catalog/progress fetch
  coordination in the shared library-client connection session.
- [x] Add TV D-pad and focus-navigation instrumentation tests.
- [x] Add LAN playback progress upload and resume seeking.
- [x] Move LAN playback progress uploads into the background playback service.
- [x] Attach indexed LAN sidecar subtitle tracks to Media3 playback items.
- [x] Add shared runtime audio/subtitle track state and Android/TV selection
  controls.
- [x] Add Android mobile and Android TV catalog search, title/path sorting, and
  subtitle-only filtering backed by the shared domain query helper.
- [x] Add an Android mobile series rail for faster browsing within large
  connected-PC catalogs.
- [x] Back the Android mobile series rail with shared series grouping and add a
  compact series detail panel.
- [x] Add an Android mobile Next Up rail backed by the LAN progress list and
  shared next-up derivation.
- [x] Add an Android TV Next Up rail backed by the LAN progress list and shared
  next-up derivation.
- [x] Add Android mobile and Android TV Continue Watching and Recently Watched
  rails backed by the LAN progress list.
- [x] Add Android mobile and Android TV episode detail panels backed by shared
  series, season, watch-state, and neighbor-episode derivation.
- [x] Add Android mobile and Android TV progress-rail detail actions while
  preserving one-tap play and resume actions.
- [x] Add saved PC-library connection profiles on Android mobile and Android TV
  while keeping manual URL and pairing-code entry.
- [x] Add an Android TV remote-friendly library header, shared-grouped series
  rail, selected-series detail strip, and richer episode rows.
- [x] Add Android mobile and Android TV no-results library states with reset
  actions for search, subtitle, and favorites filters.
- [x] Add a Windows local-library no-results reset state for search, subtitle,
  and favorites filters.
- [x] Add Android mobile progress scrubbing and Android TV remote-friendly seek
  controls backed by shared seek-target clamping.
- [x] Add compile-checked Android mobile Library page Compose instrumentation
  coverage for empty, connected-catalog, selected-series, mini-player, and
  episode-selection states.
- [x] Add Android phone/tablet screenshot or instrumentation coverage for the
  player-first mobile layout, including empty, connected, and active-playback
  states.
- [x] Replace the Android mobile inline server fields with a saved PC-library
  connection picker or settings sheet once pairing persistence is available.
- [ ] Verify playback and overlays on a physical Android TV device.

## Streaming Verification

- [x] Add same-PC Windows-to-Windows LAN streaming integration coverage that starts
  the local server and exercises paired catalog requests, full-file reads, byte
  ranges, and progress round trips through the public HTTP contract.
- [x] Add LAN-server tests for unauthorized media requests, invalid and
  unsatisfiable byte ranges, large files, and concurrent streams.
- [x] Add an Android client integration test against a live local-server fixture.
- [x] Add a compile-checked Android Media3 instrumentation fixture with a real
  short media asset and loopback HTTP server.
- [x] Execute the Media3 streaming fixture on a workspace-local API 34 emulator.
- [x] Execute the TV D-pad instrumentation suite on a workspace-local API 34
  emulator.
- [x] Test background-service progress uploads while the Android player UI is not
  active.
- [x] Test pause, seek, and episode-completion progress persistence through
  sequential LAN progress updates.
- [x] Test reconnect after an interrupted LAN catalog request.
- [x] Test slow LAN catalog response timeout behavior.
- [x] Test slow media-stream playback and buffering behavior.
- [x] Add LAN subtitle streaming tests for verified sidecar subtitle endpoints.
- [ ] Exercise PC-to-mobile and PC-to-TV streaming plus cross-device resume on
  physical hardware.

## Server Boundary

- [x] Extract the paired LAN server, index publication, progress API, and discovery
  lifecycle into a reusable `shared:library-server-core` module without Compose
  dependencies.
- [x] Define a shared LAN library-client contract for catalog browsing, stream URL
  generation, progress upload, and resume lookup.
- [x] Add a token-protected catalog-wide LAN progress endpoint and shared,
  JVM, and Android client methods for cross-device next-up surfaces.
- [x] Add a shared LAN server status contract plus server, JVM client, and
  Android client support for pre-pairing compatibility checks.
- [x] Add shared library-client session coverage for compatible catalog fetches,
  catalog-wide progress loading, and incompatible-server short-circuiting.
- [x] Add a JVM HTTP transport adapter and loopback client-to-server fixture for
  the planned Windows remote-playback path.
- [x] Reuse the shared LAN client contract from Windows, Android, and Android TV
  while keeping platform transport adapters where required.
- [x] Keep the Windows desktop app starting an embedded library server by default.
- [x] Add Windows paired-server catalog browsing and stream URL selection with
  embedded same-PC defaults.
- [x] Add Windows paired-server catalog search, title/path sorting, subtitle
  filtering, and filter-reset empty state.
- [x] Add shared LAN playback preparation for Windows remote-stream handoff with
  resume lookup.
- [x] Add Windows host direct local-file playback preparation with resume lookup.
- [x] Connect prepared local and paired-LAN playback requests to the desktop
  playback controller.
- [x] Add Windows player support for browsing and streaming from a paired LAN
  server, including the same-PC integration path.
- [x] Preserve direct local-file playback on the server host for efficiency.
- [ ] Add an optional headless server executable only after API, settings,
  lifecycle, diagnostics, startup, and firewall behavior are stable.

## Downloads

- [x] Add desktop SQLDelight storage primitives for app settings and download
  queue items.
- [x] Define authorized-download policy fields in source contracts.
- [x] Define a platform-independent download manifest.
- [x] Add multiple Windows library roots with provenance and missing-folder state.
- [x] Import and incrementally rescan user-selected ani-rss-managed output folders.
- [x] Add an optional read-only Windows ani-rss adapter for health, subscriptions,
  resolved download folders, completed episode lists, and download status.
- [x] Add an authenticated ani-rss completion-webhook endpoint that triggers a
  bounded Windows library rescan.
- [x] Store ani-rss connection credentials securely and redact them from logs.
- [ ] Decide whether ani-rss subscription and download-control actions are allowed
  after the authorized-source policy is approved.
- [ ] Add Media3 DownloadService for Android and TV.
- [ ] Add the Rust desktop download engine.
- [ ] Test interrupted transfers, retries, and disk-space failures.

## Later

- [x] Add initial SQLDelight library storage.
- [x] Extend SQLDelight storage for playback progress.
- [x] Extend SQLDelight storage for settings and downloads.
- [ ] Add authorized source plugins.
- [ ] Add macOS and Linux desktop packaging.
- [ ] Add iOS and iPadOS.
- [ ] Add the web streaming client.
- [ ] Add optional backend services.
