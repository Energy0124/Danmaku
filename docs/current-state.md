# Current State

Updated on 2026-06-06.

## Implemented

- Gradle 9.4.1 wrapper and Kotlin 2.3.21 Multiplatform foundation.
- Shared Kotlin domain models for danmaku events, playback commands, playback
  sources, snapshots, and the platform-neutral playback controller contract.
- Dependency-free Rust danmaku timeline index with half-open window queries.
- Dependency-free Windows libmpv loader with DLL discovery, required-symbol
  loading, mpv context initialization, command dispatch, and clean shutdown.
- `mpv-probe` executable for validating an audited `libmpv-2.dll`.
- Opt-in Windows libmpv bundle manifest verifier for provenance, versions,
  configuration, license-file inventory, SHA-256 validation, probe execution,
  and copying only verified files into a desktop distributable. CI self-tests
  the generic verifier and directly packages only the approved pinned zhongfly
  bundle.
- `:apps:desktop-windows:createDistributable` now builds the release Rust mpv
  bridge and copies both `player_windows_mpv.dll` and the installed approved
  `libmpv-2.dll` into the Compose distributable app directory by default.
- Root MIT license, third-party notice, and approved Windows libmpv release
  packaging. Release preparation downloads the pinned zhongfly LGPL artifact,
  verifies both archive and DLL SHA-256 hashes, and directly bundles only
  `libmpv-2.dll` with GPL/LGPL texts plus a source and provenance notice.
- Android mobile and TV APKs include the MIT license, third-party notice, and
  Apache License 2.0 text as packaged assets. CI verifies the approved libmpv
  manifest and bundled DLL hash as part of the Windows release flow.
- The uploaded Windows distributable is runtime-free and requires
  user-installed Java 17 or newer, avoiding redistribution of an OpenJDK
  runtime inside the release archive.
- Local `mpv-probe` smoke testing against the shinchiro 20260604 x86-64
  development archive succeeded with client API version `131077`. The candidate
  is documented as unapproved for redistribution because its tagged FFmpeg
  build enables GPL/version-3 features and the archive omits license notices.
- Local `mpv-probe` smoke testing against zhongfly's
  `mpv-dev-lgpl-x86_64-20260604-git-1d82932cce.7z` also succeeded with client
  API version `131077`. Its LGPL build patch uses `-Dgpl=false`, removes
  FFmpeg's `--enable-gpl`, and omits known GPL dependencies. It is approved for
  direct redistribution as a separately licensed LGPLv3-or-later dependency.
- Windows libmpv Rust crate also builds a `cdylib` and exposes a small C ABI for
  creating an mpv context, executing coarse command arrays, and destroying the
  handle. The ABI uses explicit status codes and is covered for null pointers
  and missing-DLL failures.
- The same Rust/JNA mpv bridge now builds on macOS as
  `libplayer_windows_mpv.dylib`, loads libmpv through `dlopen`, and probes
  `libmpv.2.dylib`/`libmpv.dylib` through `DANMAKU_LIBMPV_PATH`, app-local
  files, and common local library directories.
- Desktop mpv command planner for loading local files or LAN streams and
  dispatching play, pause, absolute seek, and playback-rate commands.
- Desktop `PlaybackController` wrapper that executes planned mpv commands through
  an injectable command executor and tracks testable snapshot state for local
  files and LAN streams.
- Desktop playback session wiring that loads prepared local-file or paired-LAN
  requests into the controller and applies resume seeks in command order.
- Desktop JNA binding for the Rust libmpv C ABI, including native handle
  creation, command-array forwarding, destroy calls, and explicit native
  status-code failures behind `DesktopMpvCommandExecutor`.
- Rust and JNA creation APIs accept coarse pre-initialize mpv option maps,
  including an unsigned Windows `wid` value for the upcoming embedded video
  host.
- Desktop shell runtime selection that activates the JNA executor when the
  packaged Rust bridge and libmpv DLL are present, reports command-log-only
  fallback mode when they are unavailable, and closes the native handle on
  shutdown.
- Desktop runtime selection is platform-aware. Windows still waits for the
  embedded child-window handle and passes `wid`; macOS starts the native mpv
  runtime without `wid` and uses mpv-managed video output while the Compose
  window remains the control surface.
- Windows video host using a SwingPanel-backed native child window and libmpv's
  pre-initialize `wid` option. The host is kept stable across `loadfile` so mpv
  does not attach to a transient one-pixel placeholder. The desktop shell can
  attach generated ASS subtitle tracks and cached dandanplay ASS tracks so
  danmaku is rendered by mpv directly over the video.
- Player-first Windows Playback tab with compact top chrome, icon controls,
  bottom progress/control chrome, fullscreen support, and a minimized
  fullscreen surface. Shared play, pause, seek, progress-scrub, playback-rate,
  and volume controls route through the playback command contract, while
  desktop-only display controls route through the Windows mpv boundary. The
  Windows controller polls libmpv properties for live position, duration, pause
  state, EOF state, speed, volume, and runtime audio/subtitle track metadata.
- Desktop playback supports focused keyboard shortcuts for play/pause,
  small/large seeks, volume changes, fullscreen toggle/exit, and opening a
  direct media file, all routed through the same player callbacks as the
  on-screen controls.
- Windows playback persists volume, playback-rate, and aspect-ratio defaults in
  SQLDelight settings and applies them when the native mpv runtime starts.
- Windows playback attaches indexed local sidecar subtitles and paired-LAN
  tokenized sidecar subtitle streams with `sub-add`, then exposes libmpv
  audio/subtitle selection controls in the Playback tab.
- Windows playback saves local library progress into the desktop catalog and
  uploads paired-LAN progress to the paired server on a throttled five-second
  cadence, with forced saves after pause and seek.
- Windows media library shows continue-watching and recently-watched lists for
  local episodes backed by the persisted playback-progress table.
- Shared next-up helpers derive resumable episodes, the next catalog item after
  near-finished playback, and a first-start fallback from catalog order plus
  playback progress. The Windows media library exposes that as a local Next Up
  card with prepare/play actions.
- Shared watched-state helpers classify catalog episodes as new, in-progress,
  or watched from the latest persisted playback progress. Windows desktop,
  Android mobile, and Android TV render those labels in library episode rows.
- Shared catalog helpers expose previous/next item lookup in catalog order, and
  the Windows prepared-playback card can prepare the neighboring local episode.
- Windows local library rows, continue-watching rows, and recently-watched rows
  support one-click play/resume while keeping explicit prepare actions for
  diagnostics.
- Windows paired-library rows can prepare LAN streams for diagnostics or
  one-click load prepared streams into the local mpv controller. The paired
  browser supports search, title/path sorting, subtitle-only filtering, and a
  reset state when filters hide every remote episode.
- Windows local-library playback has an optional persisted auto-next setting
  that prepares and loads the next local catalog item when playback reaches EOF.
- Direct Windows media-file picker that loads arbitrary local video files into
  the native mpv host without requiring a library scan first.
- Windows player keyboard shortcuts for play/pause, 10-second and 30-second
  seek, volume steps, playback-rate cycling, audio/subtitle cycling, aspect
  mode cycling, and fullscreen toggling.
- Packaged Windows runtime probe can optionally load a supplied local media
  file through the same native mpv command path used by the desktop shell.
- Packaged Windows GUI smoke script can launch the Compose app directly into
  Playback with `--smoke-playback-media-env`, wait for `PLAYING`, verify that
  position advances, and auto-exit. Passing the media path through an
  environment variable keeps non-ASCII Windows paths intact through the
  packaged launcher.
- Shared scrolling danmaku lane scheduler with collision-aware tests, bounded
  visible-window lookup, backward-seek query coverage, and a 10,000-comment
  generated-track test. The scheduler also exposes sampled visibility metrics
  for measuring peak and average overlay density on large tracks.
- Shared local danmaku parser for common Bilibili-style XML `<d p="...">`
  comments and normalized JSON arrays/envelopes, producing provider-independent
  scrolling, top, and bottom `DanmakuEvent` modes.
- Shared danmaku display settings model for show/hide, opacity, font scale,
  speed, density, display area, global offset, keyword filters, and regex
  filters.
- Windows dandanplay-compatible danmaku API client foundation for calculating
  the first-16MB MD5 media fingerprint, matching files through `/api/v2/match`,
  fetching `/api/v2/comment/{episodeId}` comments, using optional signed or
  credential-based 弹弹play开放平台 headers, and normalizing comments into shared
  `DanmakuEvent` rows.
- Windows Profile-tab dandanplay provider settings for API base URL plus
  optional AppId/AppSecret credentials. AppSecret values are protected through
  the same Windows DPAPI-backed secret-protector path used by ani-rss, and the
  UI/diagnostics show only redacted provider status.
- Windows local-library playback can use the configured dandanplay-compatible
  provider to fingerprint the local media file, match it, fetch comments, render
  the fetched comments into a cached ASS overlay, and attach that overlay to
  mpv in place of the synthetic demo track. Fetching is skipped until the user
  either configures official credentials or points the app at a non-default
  compatible API server.
- Windows generated ASS overlays consume the shared danmaku display settings
  for synthetic and refreshed dandanplay tracks, including visibility,
  filtering, opacity, font scale, speed, density, display-area limits, and
  millisecond timestamp offset.
- Windows prepared local-library playback can attach a user-selected local
  danmaku XML, JSON, or `.danmaku` file. The file is parsed through the shared
  local danmaku parser, rendered into a generated ASS overlay with the current
  display settings, and replaces any existing fetched danmaku overlay for that
  prepared playback while preserving normal subtitle tracks.
- The desktop SQLite catalog now persists dandanplay match metadata, local media
  fingerprints, raw normalized comment JSON, rendered ASS cache paths, and fetch
  timestamps per local media item. The resolver reuses cached comments when the
  current fingerprint matches and refreshes from the provider when the file
  hash or size changes.
- The Windows prepared-playback card exposes dandanplay cache status plus
  controls to force-refresh provider comments or clear the cached match/comments
  for the prepared episode. It also has a separate prepared-overlay removal
  action, so manually attached or fetched danmaku can be removed without
  touching provider cache state.
- dandanplay provider settings include a configurable cache max age in days.
  Cached matches older than that age are treated as stale, and the Profile tab
  exposes a cleanup action for expired dandanplay cache rows.
- The Windows Profile tab exposes persisted danmaku display controls including
  millisecond offset. Renderer changes apply after media reload or cached
  danmaku refresh because ASS overlay files are generated artifacts.
- Compose Multiplatform 1.11.0 Windows desktop shell with synthetic danmaku
  scheduling backed by the shared scheduler and rendered over mpv as ASS.
- Experimental macOS Compose Desktop support using the same desktop shell,
  catalog, LAN server/client, SQLDelight storage, and native mpv command
  bridge. `run-macos.sh` builds the bridge, resolves libmpv when available,
  and starts the app. macOS release-grade libmpv redistribution and embedded
  video composition are not complete.
- Recursive Windows anime-folder indexer and trusted-LAN HTTP server exposing a
  paired normalized JSON catalog plus paired seekable byte-range media
  responses.
- Local indexer discovery and SQLDelight persistence for matching `ASS`, `SSA`,
  `SRT`, and `VTT` sidecar subtitles, with paired verified-ID
  `GET`/`HEAD /subtitles/{id}` streaming and JVM/Android client URL generation.
- Compose-free `shared/library-server-core` JVM module containing the reusable
  paired HTTP server, progress-store contract, verified media-ID publication
  boundary, and UDP discovery announcer. The Windows desktop shell starts it in
  embedded mode.
- Paired LAN servers expose an unauthenticated `GET /api/server/status`
  compatibility probe with API version, pairing requirement, streaming,
  progress-sync, and trusted-device-management capability flags. Windows
  paired-library browsing plus Android mobile and Android TV connection flows
  use the shared `LanLibraryConnectionSession` to read it before attempting a
  paired catalog request and surface compatibility failures before using the
  pairing code.
- File-backed Windows library-folder selection, startup index restoration, and
  one-click rescanning.
- SQLDelight 2.3.2 SQLite catalog persistence with immediate cached serving on
  startup and unchanged-file reuse during background rescans.
- Durable per-episode playback progress in the desktop SQLite database with a
  paired LAN `GET`/`PUT /api/progress/{id}` contract and Android client methods.
- Paired LAN servers also expose a token-protected `GET /api/progress` endpoint
  returning all published-media progress rows, with shared/JVM/Android client
  methods. This is the cross-device foundation for mobile and TV next-up,
  continue-watching, and recently-watched surfaces.
- Desktop SQLDelight storage primitives for app settings and download queue
  items, with compatibility creation for existing catalog databases.
- Desktop SQLDelight storage primitives for multiple Windows library roots,
  including root provenance and available/missing state.
- Desktop library-root registry helper for stable root IDs, user-selected roots,
  ani-rss output roots, and scan-result state updates.
- Desktop catalog storage can persist indexed media per registered root and
  publish a merged catalog with root-prefixed relative paths and namespaced
  media IDs.
- Shared catalog query helpers filter indexed episodes by multi-term search,
  title/path sort order, and subtitle availability. Windows desktop, Android
  mobile, and Android TV expose those filters in their library screens.
- Shared catalog grouping helpers expose deterministic series and season buckets
  with episode counts, subtitle-track totals, size totals, and episode ordering
  for future detail, next-up, favorite, and playlist screens.
- Shared series watch-summary helpers roll episode watch states up into watched,
  watching, and new counts for each grouped series. Windows desktop, Android
  mobile, and Android TV render those counts in series rails and detail panels.
- Shared episode-detail helpers resolve an episode's series, season, watch
  state, and previous/next catalog neighbors. Windows desktop, Android mobile,
  and Android TV expose compact episode detail panels with play plus
  previous/next navigation.
- Windows desktop library browsing now uses the shared series/season grouping
  model for a selectable series column, compact series detail panel with season
  episode previews, and shared episode detail panel with prepare/play plus
  previous/next actions from progress rails and episode lists.
- Windows desktop persists favorite local media IDs in the desktop settings
  table, exposes Favorite/Unfavorite actions in episode rows and episode
  detail, can filter the local episode list to favorites only, and shows a
  reset action when search/favorite/subtitle filters hide every episode.
- Android mobile library browsing includes a compact series rail that summarizes
  the largest connected-PC titles and narrows the episode list through the
  existing search field. Selecting a series now opens a compact series detail
  panel backed by the shared series/season grouping model. Catalog refresh also
  loads paired progress and exposes shared-derived Next Up, Continue Watching,
  and Recently Watched rails for detail, resume, next-episode, first-start, and
  recent activity actions.
- Android mobile persists recently used PC-library connection profiles after a
  successful refresh and exposes a saved-PC picker on the Connect screen.
  Remembered PCs can connect directly, be edited back into the manual URL/code
  fields, or be forgotten; manual setup remains available on demand.
- Android mobile and Android TV persist favorite media IDs with a shared Android
  preference store, expose Favorite/Unfavorite actions in episode rows and
  detail panels, and can filter connected-PC episode lists to favorites only.
- Android mobile and Android TV show actionable no-results states when library
  search, favorite, or subtitle filters hide every episode, with reset actions
  that return users to the full connected-PC episode list.
- Android mobile has compile-checked Compose instrumentation coverage for the
  Library page empty, connected-catalog, selected-series detail, Next Up,
  Continue Watching, Recently Watched, episode detail, watch-state labels,
  no-results filter reset, mini-player, and episode-selection states, plus
  Watch page empty, connected, and active-playback player-first states.
- Android TV library browsing includes a remote-friendly header, shared-grouped
  progress-backed Next Up, Continue Watching, and Recently Watched rails,
  detail actions on those progress rails, series rail, selected-series detail
  strip with season episode actions, empty/no-results messaging, fixed-height
  episode list, and metadata-rich episode buttons for connected-PC catalogs.
- Android TV shares the Android saved PC-library connection store and exposes
  remote-friendly saved-PC cards with use and forget actions.
- Desktop root scanner can import ani-rss output folders, reuse unchanged media
  rows during incremental rescans, mark unavailable roots missing, and produce
  a merged library for LAN publication.
- Windows desktop shell actions let users add normal library roots or ani-rss
  completed-media output folders, rescan all registered roots, and inspect each
  root's provenance and availability state.
- Windows-only read-only ani-rss HTTP adapter for health, version diagnostics,
  subscription inventory, resolved output folders, completed episode
  observations, and external downloader status. Provider responses are
  normalized without retaining API keys, RSS URLs, magnet links, torrent URLs,
  or arbitrary playable paths.
- Authenticated `POST /api/hooks/ani-rss/download-end` Windows-host webhook for
  debounced rescans of only ani-rss output roots. The desktop shell displays the
  generated `X-Danmaku-Webhook-Token` value and LAN webhook URLs.
- Windows DPAPI-backed ani-rss credential store that encrypts API keys and
  webhook tokens before placing them in SQLDelight settings. The webhook token
  is reused across restarts, while normalized adapter models and diagnostic
  string representations omit secrets and provider URLs.
- Non-Windows desktop hosts use a local AES-GCM secret protector backed by a
  per-user key file under the app data directory so the embedded server and
  provider settings can start without Windows DPAPI.
- Shared source and download domain contracts for authorized offline storage
  policy, source capabilities, and platform-independent download manifests.
- Android mobile and TV progress syncing from the background playback service
  with five-second uploads, resume seeking after 10 seconds, and near-end
  episode restart behavior.
- Windows UDP announcements and Android discovery actions for finding the
  library server on the local network without typing its IP address.
- The intermediate Compose app image includes the `jdk.httpserver` and
  `java.sql` modules required by the LAN server and SQLite catalog. The
  uploaded portable release omits that Java runtime and uses user-installed
  Java 17 or newer.
- Android mobile and dedicated Android TV Compose application modules.
- Shared Android Media3 ExoPlayer adapter, foreground playback service, and
  service-backed MediaController connection used by mobile and TV.
- Shared playback snapshots expose volume, runtime audio/subtitle track metadata,
  and selection state. Android mobile, Android TV, and Windows render volume and
  track controls; Media3 applies audio/subtitle overrides and libmpv uses
  `aid`/`sid` selection commands.
- Android mobile exposes playback position text, a duration-backed progress
  slider, and quick seek buttons. Android TV exposes playback position text and
  remote-friendly quick seek buttons using the shared seek-target clamp helper.
- Shared LAN playback preparation carries tokenized indexed sidecar subtitle
  sources, and Android mobile and TV attach them to Media3 playback items with
  stable track IDs, labels, and MIME types. ASS tracks are normalized to
  Media3's SSA/ASS MIME type at the player boundary.
- Android catalog client used by mobile and TV to browse the Windows index and
  stream selected episodes.
- Shared Kotlin `shared/library-client` contract for catalog browsing,
  server compatibility probing, stream-URL generation, progress upload, and
  resume lookup. Its JVM source set includes the HTTP adapter used by the
  Windows shell for same-PC or remote paired-server browsing and stream
  selection. Android HTTP and UDP discovery remain platform adapters. JVM and
  Android LAN clients use configurable HTTP connect/read timeouts with stable
  production defaults.
- Shared LAN playback preparation converts a paired catalog item into a
  tokenized `RemoteStream` plus resume position; the Windows shell uses it for
  same-PC or remote paired-server playback handoff through the native libmpv
  controller.
- Windows local playback preparation resolves indexed host files directly to
  `LocalFile` sources with resume lookup, preserving an efficient same-PC path.
- Android TV launch focus on `Discover PC` plus API 34 emulator-verified Compose
  instrumentation coverage for the initial focus and left-arrow path. The TV
  row uses an explicit focus graph for deterministic remote navigation.
- Workspace-local ignored Android SDK with API 36, Build Tools 36.0.0, and
  platform tools. The ignored SDK also has an API 34 emulator image for runtime
  instrumentation. `local.properties` is ignored and points to that SDK.
- Architecture decisions for Kotlin with focused Rust and audited libmpv
  distribution.
- Same-PC Windows host integration coverage for paired catalog requests,
  complete file reads, byte ranges, unauthorized media rejection,
  unsatisfiable ranges, and progress round trips.
- JVM client-to-server loopback coverage for paired catalog browsing, generated
  stream consumption, token encoding, missing progress, and progress round trips.
- JVM LAN client recovery coverage for an interrupted catalog connection that
  reconnects and completes on a fresh socket.
- JVM LAN client timeout coverage for slow catalog responses.
- Reusable LAN server reliability coverage for unauthorized media requests,
  malformed and unsatisfiable byte ranges, valid open-ended and suffix ranges,
  multi-megabyte media, concurrent streams, and sequential pause, seek, and
  completion-style progress updates.
- Android HTTP adapter loopback coverage against a live local server for paired
  catalog browsing, generated media and subtitle stream consumption, and
  progress round trips.
- API 34 emulator-verified Android Media3 instrumentation coverage with a
  deterministic one-second MP4 asset and loopback HTTP server, including
  prepared sidecar subtitle attachment, runtime subtitle discovery, selection
  and disable behavior, service-owned progress upload after the UI controller
  connection closes, and slow chunked HTTP media playback.

## Verification

Run these commands after architecture or build changes:

```powershell
.\tools\windows\test-verify-libmpv-bundle.ps1
.\tools\windows\test-install-libmpv-dependency.ps1
cargo fmt --all --check
cargo test --workspace
.\gradlew.bat --no-daemon :shared:domain:jvmTest
.\gradlew.bat --no-daemon :shared:library-client:jvmTest
.\gradlew.bat --no-daemon :shared:library-server-core:jvmTest
.\gradlew.bat --no-daemon :apps:desktop-windows:desktopTest
.\gradlew.bat --no-daemon :apps:android-mobile:assembleDebugAndroidTest
.\gradlew.bat --no-daemon :shared:player-android-media3:assembleDebugAndroidTest
.\gradlew.bat --no-daemon :apps:android-mobile:assembleDebug :apps:android-tv:assembleDebug
.\gradlew.bat --no-daemon :apps:desktop-windows:createDistributable
.\tools\windows\verify-windows-mpv-runtime.ps1
```

On macOS, the desktop bridge and packaging path can be verified with:

```bash
cargo fmt --all --check
cargo test --workspace
GRADLE_USER_HOME=/Users/energy/projects/Danmaku/.gradle-user-home bash ./gradlew --no-daemon :apps:desktop-windows:desktopTest
GRADLE_USER_HOME=/Users/energy/projects/Danmaku/.gradle-user-home bash ./gradlew --no-daemon :apps:desktop-windows:createDistributable
```

For Windows playback/UI changes, also smoke-test a real sample through the
packaged GUI:

```powershell
.\tools\windows\smoke-windows-playback.ps1 -MediaPath C:\media\sample.mkv -Seconds 6
```

With an Android emulator or device online, run:

```powershell
.\gradlew.bat --no-daemon :shared:player-android-media3:connectedDebugAndroidTest
.\gradlew.bat --no-daemon :apps:android-tv:connectedDebugAndroidTest
```

## Next Work

1. Exercise cross-device resume behavior on Android and TV hardware.
2. Stress-test the Windows player with resize, fullscreen transitions, hardware
   decoding, 10-bit files, and 4K media after the packaged GUI smoke passes.
3. Add manual local danmaku-file mounting, per-episode offset controls, and
   user-facing danmaku filters.
4. Continue trimming fullscreen/player chrome without destabilizing the native
   mpv host.
5. Re-audit the libmpv bundle before changing its producer artifact or hashes.
6. Design embedded macOS video composition and audit any release libmpv
   packaging before promoting macOS beyond experimental support.

## Runtime Smoke Check

The packaged Windows distributable was rebuilt after native packaging and player
host changes. `verify-windows-mpv-runtime.ps1` confirmed that the packaged app
directory contains `player_windows_mpv.dll` and `libmpv-2.dll` and that the
native executor starts successfully. `smoke-windows-playback.ps1` launched the
packaged Compose player against a real local sample, reached `PLAYING`, observed
an advancing playback position, and exited automatically. App logs are written
under `%LOCALAPPDATA%\Danmaku\logs`.
