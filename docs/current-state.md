# Current State

Updated on 2026-06-05.

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
- Root MIT license, third-party notice, and approved Windows libmpv release
  packaging. Release preparation downloads the pinned zhongfly LGPL artifact,
  verifies both archive and DLL SHA-256 hashes, and directly bundles only
  `libmpv-2.dll` with GPL/LGPL texts plus a source and provenance notice.
- Android mobile and TV APKs include the MIT license and third-party notice as
  packaged assets. Cash App Licensee validates the distributable Gradle
  dependency graphs, which currently resolve to Apache License 2.0
  dependencies, and produces versioned inventories. CI verifies those assets,
  the Windows legal files, the approved libmpv manifest, and the bundled DLL
  hash.
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
- Initial Windows video-host spike using a SwingPanel-backed native child
  window and libmpv's pre-initialize `wid` option. The desktop shell now
  attaches a generated ASS subtitle track so synthetic danmaku is rendered by
  mpv directly over the video. Full parsed-track synchronization and final
  renderer selection are still pending.
- Desktop shell play, pause, seek, progress-scrub, playback-rate, volume,
  fullscreen, and aspect-ratio controls. Shared controls route through the
  playback command contract, while desktop-only display controls route through
  the Windows mpv boundary. The Windows controller polls libmpv properties for
  live position, duration, pause state, EOF state, speed, volume, and runtime
  audio/subtitle track metadata.
- Windows playback attaches indexed local sidecar subtitles and paired-LAN
  tokenized sidecar subtitle streams with `sub-add`, then exposes libmpv
  audio/subtitle selection controls in the Playback tab.
- Windows playback saves local library progress into the desktop catalog and
  uploads paired-LAN progress to the paired server on a throttled five-second
  cadence, with forced saves after pause and seek.
- Windows media library shows continue-watching and recently-watched lists for
  local episodes backed by the persisted playback-progress table.
- Shared catalog helpers expose previous/next item lookup in catalog order, and
  the Windows prepared-playback card can prepare the neighboring local episode.
- Windows local library rows, continue-watching rows, and recently-watched rows
  support one-click play/resume while keeping explicit prepare actions for
  diagnostics.
- Windows local-library playback has an optional persisted auto-next setting
  that prepares and loads the next local catalog item when playback reaches EOF.
- Direct Windows media-file picker that loads arbitrary local video files into
  the native mpv host without requiring a library scan first.
- Packaged Windows runtime probe can optionally load a supplied local media
  file through the same native mpv command path used by the desktop shell.
- Shared scrolling danmaku lane scheduler with collision-aware tests, bounded
  visible-window lookup, backward-seek query coverage, and a 10,000-comment
  generated-track test. The scheduler also exposes sampled visibility metrics
  for measuring peak and average overlay density on large tracks.
- Compose Multiplatform 1.11.0 Windows desktop shell with synthetic danmaku
  scheduling backed by the shared scheduler and rendered over mpv as ASS.
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
- File-backed Windows library-folder selection, startup index restoration, and
  one-click rescanning.
- SQLDelight 2.3.2 SQLite catalog persistence with immediate cached serving on
  startup and unchanged-file reuse during background rescans.
- Durable per-episode playback progress in the desktop SQLite database with a
  paired LAN `GET`/`PUT /api/progress/{id}` contract and Android client methods.
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
  stream-URL generation, progress upload, and resume lookup. Its JVM source set
  includes the HTTP adapter used by the Windows shell for same-PC or remote
  paired-server browsing and stream selection. Android HTTP and UDP discovery
  remain platform adapters. JVM and Android LAN clients use configurable HTTP
  connect/read timeouts with stable production defaults.
- Shared LAN playback preparation converts a paired catalog item into a
  tokenized `RemoteStream` plus resume position; the Windows shell uses it for
  same-PC or remote paired-server playback handoff while embedded native libmpv
  rendering is still pending.
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
.\gradlew.bat --no-daemon :apps:desktop-windows:licensee :apps:android-mobile:licenseeDebug :apps:android-tv:licenseeDebug
.\gradlew.bat --no-daemon :shared:domain:jvmTest
.\gradlew.bat --no-daemon :shared:library-client:jvmTest
.\gradlew.bat --no-daemon :shared:library-server-core:jvmTest
.\gradlew.bat --no-daemon :apps:desktop-windows:desktopTest
.\gradlew.bat --no-daemon :shared:player-android-media3:assembleDebugAndroidTest
.\gradlew.bat --no-daemon :apps:android-mobile:assembleDebug :apps:android-tv:assembleDebug
```

With an Android emulator or device online, run:

```powershell
.\gradlew.bat --no-daemon :shared:player-android-media3:connectedDebugAndroidTest
.\gradlew.bat --no-daemon :apps:android-tv:connectedDebugAndroidTest
```

## Next Work

1. Exercise cross-device resume behavior on Android and TV hardware.
2. Validate the Windows child-window playback spike with local files, resize,
   fullscreen, hardware decoding, 4K media, and the mpv-rendered synthetic ASS
   overlay.
3. Replace the synthetic overlay with parsed danmaku tracks synchronized to the
   real playback clock.
4. Re-audit the libmpv bundle before changing its producer artifact or hashes.

## Runtime Smoke Check

The packaged Windows executable was launched after adding pairing to the LAN
server. Its Compose window opened successfully, displayed the generated
pairing code, rejected an unpaired `GET http://127.0.0.1:8686/api/library`
request with HTTP `401`, and returned the expected empty initial catalog when
the displayed code was supplied. The packaged runtime was launched again after
adding SQLDelight storage; its paired endpoint started successfully from the
trimmed runtime image.
