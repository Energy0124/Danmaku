# Current State

Last reviewed: 2026-10-05.

Danmaku's active product is a Rust-native Windows library/player/server with
Android mobile, Android TV, iPhone/iPad, and browser clients on the same trusted-LAN API,
plus an experimental native Rust macOS build. The Kotlin Compose desktop app,
JVM server/host modules, JNA bridge, legacy desktop database importer, and old
Compose macOS artifact are retired.

## Implemented

### Rust Windows Player And Server

- egui/glow player with embedded libmpv video, playback controls, fullscreen,
  tracks, seeking, playback rate, native danmaku, local overlay attachment,
  discovery, library browsing, progress/resume, previous/next, and auto-next.
- English and Traditional Chinese UI, durable playback/danmaku preferences,
  a translucent in-player danmaku panel with independent scrolling/top/bottom
  visibility controls and remembered local roots and server URL.
- Unified local mode that starts the sibling Rust server, waits asynchronously
  for readiness, connects, and stops only a child it owns.
- Optional current-user Task Scheduler background host with install, atomic
  package refresh, root management, start/stop/status, uninstall, and
  non-mutating plan checks. Refresh and uninstall preserve server data,
  preferences, credentials, and configured roots.
- Multi-root scanning, normalized catalog snapshots, subtitles, posters,
  streaming/range requests, progress, UDP discovery, and data-directory locks.
- Asynchronous manual rescans of the current folder. The server
  replaces only the selected catalog subtree, preserves sibling folders and
  stable media IDs, and exposes live file counts and scan failures through its
  status endpoint.
- The native desktop Folders view supports persistent multiple-folder/file
  selection and an independent native organizer window with a saved draft and
  explicit destination across
  configured roots. Matching is asynchronous and independent of comment fetching;
  ordinary fingerprint failures fall back to title search. Quota/authentication
  failures pause the batch with a saved cooldown; three consecutive failures also
  stop the batch. Batch retry retains existing candidates, including ambiguous
  results. Provider error codes/messages remain visible. Ambiguous candidates
  require selection, and manual titles/seasons and grouping
  remain editable. The searchable per-series queue supports exclusion, skip,
  automatic unambiguous subtitle selection, and exact revision-bound approval.
  Same-volume moves and verified cross-volume transfers preserve catalog IDs;
  durable journals provide cancellation, rollback, recovery retry, and undo.
  Series-owned companions (including font files and subtitle archives) move
  directly into the series folder, once per source, independently of episode
  ownership. OVAs/specials can reuse a known provider series with no episode;
  explicitly saved series-only identities suppress automatic episode matching
  and old episode comments during playback. The review has four numbered steps,
  bulk-edit controls, and English/Traditional Chinese help. Autosave preserves
  the visible preview and ongoing edits while fresh validation gates approval.
  Identification can be saved separately from moves. Native loopback capability
  authentication rejects browser and LAN access. Automated fixture coverage is
  recorded in the [implementation log](design/progressive-library-organizer-implementation.md);
  supervised GUI and physical-drive release QA remain pending.
- dandanplay matching/comment cache and repair status; provider metadata,
  settings, secret storage, external mappings, list readback, conflict-aware
  previews, and explicitly acknowledged MAL/Bangumi writes.
- Native Windows Accounts & Tracking UI with MAL loopback OAuth/refresh,
  guided Bangumi token validation, series search/mapping, deliberate sync,
  provider-ahead local import, and an episode-completion review prompt.
- `/web/` administration and a standalone server package.

### Android Mobile And TV

- Discovery/manual connection, catalog browsing, series/episode presentation,
  Media3 streaming, subtitles, playback progress, resume, and danmaku.
- TV reconnects whenever it enters the foreground, opens cached Home immediately,
  and refreshes its saved PC in the background. First launch searches automatically;
  a single discovered PC connects directly, while multiple PCs use a remote-friendly
  picker. Failed reconnects search again and retry known saved PCs without silently
  switching to an unknown library. Manual entry accepts a PC name, IP address, or
  full URL with one Connect action. Tracking loads after Home opens; offline cached
  Home exposes connection recovery. Active playback and manual entry are preserved.
- TV keeps the screen on during active video playback to prevent the screen
  saver from interrupting viewing, and releases it when playback is paused,
  stops, or the player view is removed.
- TV playback controls include previous/next episode buttons with D-pad focus,
  following catalog order and disabling navigation at list boundaries or while
  preparing playback. Switching saves progress and applies the destination's
  resume policy; Back returns to the original library or folder view.
- Mobile and TV expose the server's original multi-root folder layout as a
  dedicated top-level destination backed by shared folder-listing rules. Both
  provide a manual current-folder refresh action and poll only while that
  requested server scan remains active.
- TV folder file rows show unwatched, in-progress, or watched status, elapsed
  playback time, and progress bars. Selecting a file plays that exact file with
  the shared resume policy; returning refreshes folder progress, and immediate
  replay waits for the previous checkpoint to finish saving.
- Mobile playback has a responsive side-panel for playback speed, audio and
  subtitle tracks, plus persistent danmaku visibility, opacity, size, travel
  speed, density, screen area, full-hour timing offset with exact entry and
  selectable adjustment steps, and per-type scrolling/top/bottom visibility
  controls. TV provides the same per-type controls in its translucent,
  D-pad-native danmaku panel, with Left/Right value sliders and text sizing down
  to 10%.
- Android mobile playback controls include previous/next video navigation in
  fullscreen and inline playback, following catalog order for streaming and
  ready downloads from the same PC for cached playback. Navigation stops at list
  boundaries; the file picker is shown only when no video is loaded.
- Android mobile folder file rows show unwatched, in-progress, or watched labels
  and playback progress bars, with live updates from the current playback session.
- Android mobile can explicitly cache one episode, every episode in a series,
  one file from the folder browser, or a one-time recursive folder snapshot.
  Its persistent background queue stores video, resolved danmaku, sidecar
  subtitles, posters, and item metadata in app-managed storage; cached entries
  remain browsable and playable away from the trusted LAN. The Android player
  recognizes the cache's `file:/...` video and subtitle URIs without treating
  them as filesystem paths or encoding them again. Downloads support
  byte-range resume, pause, retry, cancel, per-item deletion, and clear-all.
  WorkManager serializes transfers through one persistent chain and throttles
  durable progress updates. LAN downloads check actual free bytes with a 256 MiB
  reserve instead of waiting on internet validation or OEM low-storage flags.
  Opening the mobile app updates older queued requests in place without
  discarding partial downloads or queue dependencies.
  Startup errors enter the visible retry/failure state, interrupted transfers
  can resume, and a failed item does not block later queued items.
  Offline playback progress is checkpointed by the Media3 service in a separate
  journal that survives cache deletion; the newest
  pending checkpoint is uploaded after the corresponding PC reconnects.
- Native MAL/Bangumi account status, provider readback, exact progress preview,
  and explicitly confirmed sync; account/mapping/conflict administration stays
  on Windows and the web UI.
- ANI-RSS automatic-download administration through a normalized,
  trusted-LAN server API and responsive web panel. Desktop and mobile
  entry points open the same workflow for explicit source approval, series
  search, group selection, preview/confirm, subscription management, and
  download status. The ANI-RSS API key stays in protected server storage.
- Existing ANI-RSS installations can be attached by URL. Windows can also
  supervise a user-supplied official `ani-rss.exe` on loopback, with isolated
  configuration/logs and server-lifetime cleanup. Completed files are picked
  up by an optional five-minute library rescan loop.
- Dedicated Android TV navigation and D-pad focus, including folder-level back
  navigation, a fixed compact rail with focused-item labels and bounded
  border/color focus indicators, cached-first presentation,
  latest-request-wins refresh handling, queued playback startup, and benchmark
  journeys.
- Shared domain, LAN-client, and Media3 modules without a JVM server runtime
  dependency.
- Android mobile and TV stable-release checks through a shared Android updater.
  The apps check at most daily, expose manual checks, download only after
  approval, validate the APK hash/size/package/version/signing certificate,
  and invoke Android's user-confirmed package installer.

### Web UI

- Catalog playback and progress behavior.
- Provider account status/guided Bangumi connection, advanced endpoint
  settings, persistent mapping search, tracking readback, provider-ahead
  import, conflict-aware sync preview, and acknowledged writes. The former
  per-episode direct list editor is no longer exposed.
- Repeatable fixture-backed Rust server and headless browser QA.

### Packaging And CI

- Versioned native Windows player and standalone server zips with web assets,
  latest-release LGPL libmpv resolution, exact hash provenance, licenses, and
  generated dependency inventories.
- Velopack-based per-user Windows Setup, stable-channel full/delta update
  packages, quiet startup checks, release-note prompts, explicit
  update-and-restart approval, and portable-build detection.
- SemVer tag release automation validates versions/changelog, requires a CI
  Authenticode certificate, resolves and verifies the latest stable LGPL x64
  libmpv asset, preserves its recorded DLL hash, and publishes checksums only
  after all build and package checks pass.
- The same tag requires the durable Android signing key, derives monotonic
  Android version codes, verifies signed mobile/TV APK metadata, and publishes
  both APKs plus `android-update.json` in the unified GitHub Release.
- Windows CI for Rust, Android, web assets, packaging, and libmpv checks;
  separate Rust and Worker proxy jobs.
- Native macOS CI compiles and tests the Rust workspace, verifies Homebrew
  libmpv with `mpv-probe`, and publishes an ad-hoc-signed `.app` archive.
- No Compose desktop, JVM host, Java runtime, or JNA DLL.

### Experimental macOS Player

- The Rust egui player renders libmpv through the macOS OpenGL framework and
  uses real app-owned framebuffer IDs for video compositing.
- Native window decorations, Homebrew libmpv discovery for Apple Silicon and
  Intel, platform-standard Application Support/Caches storage, local server
  supervision, and `.app` packaging are implemented.
- The app bundle contains the Rust player/server and web UI but deliberately
  does not redistribute an unreviewed libmpv build; target Macs need
  `brew install mpv`.

### iPhone And iPad Development Client

- Native SwiftUI universal app for iOS/iPadOS 17+, iPad sidebar/library-detail
  presentation, compact iPhone navigation, and English/Traditional Chinese text.
- Bonjour/manual/saved connection management, cached catalog, Home/next-up,
  grouping, search/watch/favorite filters, multi-root folders and manual rescans.
- MobileVLCKit 3.7.2 streaming/local playback, fullscreen, seek/rate controls,
  audio and embedded/sidecar subtitle selection, previous/next, and resume.
- Clock-driven scrolling/top/bottom danmaku with persistent display and timing
  controls; collision scheduling is pure Swift and independent of frame callbacks.
- Serialized background URLSession episode/series/folder snapshot downloads,
  pause/resume/retry/cancel/deletion, cached metadata/danmaku/subtitles/posters,
  and a progress journal preserved across cache deletion.
- MAL/Bangumi status/readback, exact update review and confirmed sync; account,
  mapping and provider-ahead administration stay in server/web UI.
- Committed Xcode project/workspace/scheme, locked CocoaPods/Gem dependencies,
  PowerShell build/test/install tools, and macOS iOS CI coverage. Verification and
  device signing status are recorded in [iOS implementation](design/ios-ipad-client.md).
- Rust Bonjour advertising supplements UDP; media Last-Modified/ETag/If-Range supports
  resumable transfers without changing LAN API version 1.

## Partial Or Pending

- iOS signed installation and synthetic MP4/MKV playback passed on the connected
  Energy iPad Pro. Visual rotation/multitasking QA and release distribution remain
  pending; see the iOS implementation log for evidence and remaining checks.

- Supervised Windows fullscreen, multi-display, hardware decode, and broader
  real-media release matrices still require manual QA.
- Live MyAnimeList/Bangumi account read/write QA requires explicit approval and
  credentials.
- Android mobile/tablet viewport and replacement-class physical TV validation
  remain release gates.
- Richer danmaku filters/offsets, per-series playback preferences, metadata
  depth, collections, and notification surfaces remain planned.
- Offline copies of server-owned files are currently implemented only by the
  Android mobile trusted-LAN cache. ANI-RSS can now coordinate authorized
  external automatic-download subscriptions, but Danmaku does not bundle a
  downloader and TV has no subscription-management surface.
- macOS online-provider HTTPS and protected provider-token persistence are not
  implemented; the current slice supports local/LAN playback and local
  XML/JSON/ASS danmaku. Packaging is not notarized or release-signed.

## Compatibility Notes

- Existing Compose desktop database files are left untouched but are not read
  or imported. Users configure roots again and create a fresh Rust catalog.
- The old Compose macOS build remains unsupported and is not migrated. The new
  Rust `.app` uses fresh Rust settings and catalog state.
- LAN API/discovery version 1 remains compatible with active Android clients.

## Standard Verification

```powershell
cargo fmt --all --check
cargo test --workspace
.\gradlew.bat --no-daemon :shared:domain:jvmTest :shared:library-client:jvmTest :shared:library-client-android:testDebugUnitTest :shared:player-android-media3:assembleDebugAndroidTest :apps:android-mobile:assembleDebug :apps:android-tv:assembleDebug
cd apps\web-ui
npm run build
```

Connected Android, GUI playback, emulator, real-library, and live-provider QA
remain supervised/approval-gated checks.

On macOS, `./build-macos.sh` builds and verifies the native `.app` after
Homebrew `mpv` is installed. Interactive launch and playback remain supervised.
