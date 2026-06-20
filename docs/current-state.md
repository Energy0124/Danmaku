# Current State

Last reviewed: 2026-06-18.

Danmaku is in active foundation work. The strongest vertical slice is Windows
desktop as the local library host/player, with Android mobile and Android TV as
trusted-LAN clients.

## Implemented

### Shared Domain

- Playback contracts, snapshots, commands, positions, media sources, and track
  models.
- Library catalog models, query/filter/sort logic, series grouping, next-up,
  continue-watching, recently-watched, watch-state derivation, and episode
  detail helpers.
- Matched-anime-aware grouping that can use per-item provider metadata while
  keeping unmatched files in local series.
- Danmaku event models, display settings, lane scheduling, Bilibili XML parsing,
  and normalized JSON parsing.
- Provider-neutral external anime IDs, metadata, mappings, list status,
  progress updates, sync plans, conflict detection, and retry/backoff helpers.

### Windows Desktop

- Compose desktop shell with Home, Playback, Library, Downloads, and Profile
  areas. Shared desktop UI models, theme constants, localization strings,
  common formatting helpers, file dialogs, library presentation helpers, and
  playback presentation constants/helpers have been split out of the original
  shell file. The `Main.kt` decomposition is now tracked in
  `docs/design/desktop-main-refactor-plan.md`, with feature tab files being
  separated and the first remembered diagnostics/server-event state object
  extracted from the shell. Library UI has also been split into focused tab,
  workspace, list/progress, inspector/mapping, and helper files. Settings UI
  is split into routing/profile, danmaku controls, dialogs/cache/server, and
  provider-card files. Playback UI is split into tab composition, shortcuts,
  overlays, panels, and presentation helpers. Shell chrome is separated from
  Home content, and desktop Home route orchestration now lives in
  `DesktopHomeTab.kt` while reusable Home cards/status components live in
  `DesktopHomeContent.kt`. Shared primitives, local/remote library rows,
  library workspace, and library inspector surfaces are further split into
  focused files; the library workspace external-sync preview rows now live in
  `DesktopLibraryExternalSyncPreview.kt`, and metadata match dialog/candidate
  rendering lives in `DesktopLibraryMetadataMatchDialog.kt`. `DesktopShell.kt`
  has diagnostics/server-event and
  navigation/search/language state objects extracted, plus a playback session
  state object for queued playback/progress/smoke/autonext flags, and a
  settings state object for preferences/provider statuses/cache entries.
  Library roots/catalog/progress/favorites/download and refresh/sync flags are
  also in a shell library state object. Provider settings, connection tests,
  dandanplay cache-manager actions, and danmaku settings persistence are in a
  settings action object. Queued playback loading, smoke playback, progress
  persistence, auto-next persistence, and playback preference persistence are
  in a playback action object. Local playback preparation, dandanplay
  match/cache inspection, prepared danmaku overlay mutations, and manual
  danmaku attachment are in a local playback action object. Library root
  scan/import/remove, published-library application, poster/metadata refresh,
  favorites, external mapping/search, and tracking sync actions are in a
  library action object. Persisted download queue refresh/removal and output
  folder opening are in a download action object. Desktop catalog-store schema
  DDL and SQLDelight row mappers now live in
  `DesktopLibraryCatalogStoreSchema.kt`, leaving `DesktopLibraryCatalogStore.kt`
  focused on persistence operations. Window/fullscreen lifecycle and mpv OSC
  fullscreen sync are now owned by `DesktopShellWindowState.kt`, while QA
  screenshot launch handling is owned by `DesktopShellQaEffects.kt`.
  `DesktopShell.kt` remains the main orchestration hotspot for tab assembly,
  but it is now below the planned 1,000-line threshold. Desktop localization
  strings are initialized through a small DSL-backed holder instead of a giant
  constructor, avoiding JVM method-signature limits as English/`zh-TW` coverage
  grows. All current desktop `DesktopStrings` fields now have Compose
  Multiplatform resource adapter coverage using
  `commonMain/composeResources/values` and `values-zh-rTW`. The adapter sets
  the desktop Java default locale from the selected app language before reading
  generated resources so the in-app language selector controls resource
  resolution on desktop.
- Multi-root local anime library indexing, incremental rescanning, ani-rss
  output-folder import, and persistent SQLDelight/SQLite storage.
- Trusted-LAN library server with pairing token, JSON catalog, byte-range media
  streaming, subtitle streaming, poster serving, progress API, authenticated
  hooks, optional `/web/` static asset serving, additive web/status capability
  fields, and UDP discovery.
- Local and paired-LAN playback preparation, one-click play/resume, progress
  persistence, previous/next episode navigation, and optional auto-next.
- libmpv playback through a Rust/JNA bridge, embedded Windows video host,
  command planning, fullscreen/aspect controls, volume/rate controls, seeking,
  and runtime audio/subtitle track selection.
- dandanplay provider settings, signed/credential auth modes, optional Worker
  proxy usage, media matching, comment fetching, ASS overlay generation,
  cached match/comment storage, cache cleanup, and manual match correction.
- Manual local danmaku attachment path and cached/synthetic overlay rendering.
- Anime metadata/poster resolution and cache, poster loading states, and manual
  metadata refresh on series/episode surfaces.
- MyAnimeList and Bangumi provider settings, manual mapping UI, metadata
  search/cache clients, MAL OAuth callback flow, encrypted token storage,
  provider readback/write clients, External Sync preview, and explicit sync
  action with pre-write external-progress conflict checks. Imported provider
  list entries and sync failures persist in the desktop catalog database across
  relaunch, and provider-ahead conflicts can seed local watched progress from
  the Tracking inspector.
- Desktop local watch-list entries persist with status, score, and notes; the
  Library inspector can edit them, the Library toolbar has a direct status
  menu for browsing by local watch-list state including untracked series,
  series cards show saved local watch-list status badges and expose a quick
  status/clear menu, and summary chips expose the current saved watch-list
  count.

### Android Mobile

- Compose Android app that discovers or manually connects to the Windows
  library server.
- LAN catalog browsing with search, filters, poster rendering, matched metadata,
  favorites, progress rails, episode details, and explicit play actions.
- Media3 playback through the shared Android playback module with sidecar
  subtitles, resume lookup, progress upload, seek, volume, audio/subtitle track
  controls, and background service support.
- Android mobile emulator QA is now set up with
  `Pixel_3a_API_34_extension_level_7_x86_64` for phone layout and
  `Danmaku_Tablet_API_34` (`pixel_tablet`) for tablet layout using
  `system-images;android-34;google_apis;x86_64`. The 2026-06-18 emulator runs
  passed `:apps:android-mobile:connectedDebugAndroidTest` on both form factors;
  visual screenshots were captured under `build/qa/android-mobile/`. The
  repeatable Windows wrapper is
  `tools/windows/run-android-mobile-emulator-qa.ps1`.

### Android TV

- Dedicated Android TV app module with TV-specific layouts.
- Top-level 10-foot shell with Home, Library, Search, Favorites, and PC
  destinations.
- Persistent left rail, focused PC connection screen, search/favorites
  destination state, Next Up/progress rails, poster/fallback artwork, details,
  explicit Play/Resume actions, favorite toggles, and D-pad-focused tests.
- Media3 playback and LAN progress sync through the shared Android playback
  module.
- Android TV emulator QA is now set up with `Danmaku_TV_API_36`
  (`tv_1080p`) and `Danmaku_TV_4K_API_36` (`tv_4k`) using
  `system-images;android-36;android-tv;x86_64`. The 2026-06-18 emulator runs
  passed `:apps:android-tv:connectedDebugAndroidTest` at both 1080p and 4K, and
  visual screenshot QA caught a PC-screen pill layout collapse that is now
  fixed. The repeatable Windows wrapper is
  `tools/windows/run-android-tv-emulator-qa.ps1`.

### Native And Tooling

- Rust `player-windows-mpv` crate for libmpv loading/probing and the desktop
  native playback command bridge.
- Rust `rust-core` timeline/indexing foundation.
- Windows scripts for pinned libmpv install, bundle verification, portable
  release preparation, mpv runtime verification, and GUI playback smoke tests.
- Desktop app-level QA screenshot capture now raises the app window, waits for
  focus to settle, and restores the previous always-on-top state so captures are
  less likely to include unrelated foreground Windows apps.
- Cloudflare Worker proxy for dandanplay match/comment requests without
  shipping a dandanplay AppSecret in public clients.
- CI on Windows, Rust, Worker proxy, and macOS desktop build/test paths.
- A first server/client/web split foundation is in place: documented split
  plan, `shared:library-host-core` host contracts, opt-in desktop
  `--web-assets-dir`/`DANMAKU_WEB_UI_DIST` serving, a Vite TypeScript web UI
  scaffold for pairing/catalog/video/progress, and an
  `apps:library-server-windows` headless JVM host with data-directory locking,
  startup scanning for configured `--root` folders, JSON catalog publishing,
  sidecar subtitle discovery, shared LAN media/subtitle streaming, and
  file-backed playback progress persistence under the locked data directory.
  Headless hosts also announce themselves through the existing LAN discovery
  protocol after the HTTP server binds.

## Partial Or Needs More QA

- Windows fullscreen, resize, 4K, hardware-decoding, and multi-display playback
  behavior need broader manual validation.
- UI localization is now a design requirement for English and Traditional
  Chinese (`zh-TW`). Desktop `DesktopStrings` resource extraction and
  app-language-to-resource locale control are in place. Deterministic desktop
  launch overrides and an app-level Windows screenshot helper now exist for
  English/`zh-TW` Home, Library, Downloads, Tracking, and Settings review. A
  full English/`zh-TW` desktop capture pass was run on 2026-06-16; it found
  dynamic status text that still bypassed resources. The follow-up fix localized
  playback status chips, provider credential summaries, external sync summaries,
  skip/conflict reasons, external list statuses, watch-summary labels, and
  dandanplay auth-mode labels. A final full English/`zh-TW` screenshot pass was
  accepted after trimming the Kotlin fallback initializer down to the
  non-Compose error/default strings that still need direct access.
- A focused English Library screenshot pass on 2026-06-17 validated the new
  desktop series-card local watch-list quick actions after hardening app-window
  focus during capture.
- Android mobile/tablet emulator viewport QA passed on 2026-06-18. Mobile still
  needs one real-device smoke pass before release, preferably including LAN
  playback against a Windows library server.
- Android TV 1080p and 4K emulator QA passed on 2026-06-18. Android TV still
  needs one real-device focus/safe-area pass before release.
- External MAL/Bangumi sync has fake/integration-style client coverage and UI
  wiring, but still needs live-account manual QA.
- macOS desktop can build and run through the shared shell, but embedded video
  composition and release packaging are not first-class yet.
- Download queue storage exists; a full authorized download engine is not
  implemented.
- The desktop shell still has orchestration blocks for tab assembly; ongoing
  refactoring is moving them behind typed action/state boundaries without
  changing behavior. Window/fullscreen lifecycle and QA screenshot launch
  handling are now extracted. The immediate file-size target is met, so
  remaining work should be driven by coupling and testability.
- A 2026-06-15 full review found no local build/test blocker across Rust,
  Gradle, and Worker proxy checks. Expected user-facing failure paths are being
  moved out of crash-style control flow: LAN discovery/client HTTP failures,
  desktop missing indexed-media/no-match action failures, dandanplay provider
  failures, MAL OAuth callback/token failures, external anime search/write
  failures, ani-rss remote failures, external search with no configured
  provider, and poster fetch failures now use typed exceptions or
  optional-artwork fallbacks. The final `error(...)`/`check(...)` audit found
  only test sentinels and startup/developer invariants remaining. Metadata
  match no-provider and provider-search failures now use localized
  English/`zh-TW` dialog copy, and local playback preparation plus paired
  library catalog/remote playback failures now use localized visible error copy.
  Desktop screenshot QA also localized the remaining visible provider/status
  summaries on Home, Library, Tracking, and Settings. Broader diagnostic-log
  localization still needs release polish.
- Android mobile and Android TV entrypoint decomposition has moved the old
  monolithic screens into focused route, state, action, and component files.
  The remaining refactor risk is in larger extracted route/component files
  rather than in `MainActivity.kt`. Android mobile has started decomposition
  with poster loading,
  URL encoding, display-label, watch-status, size, and playback-time helpers
  extracted into `MobileUiHelpers.kt`; tab metadata, bottom navigation, shared
  page chrome, and the mini-player bar are also split into
  `MobileShellUi.kt`. Top-level tab routing is split into
  `MobileAppScaffold.kt` with explicit UI-state/action handoff objects.
  Remembered player/library state and derived catalog filtering/poster endpoint
  values now live in `MobilePlayerState.kt`. Service/store actions and scaffold
  callbacks are split into `MobilePlayerActionHandler.kt`; `MobilePlayerScreen`
  still owns the Android file-picker URI load and playback-service lifecycle.
  Mobile Home route composition is split into `HomePage.kt`, with shared rails
  kept package-internal for Library reuse. Mobile Watch route composition and
  player-control helpers are split into `WatchPage.kt`. Mobile library detail
  and Next Up UI are split into `LibraryDetailComponents.kt` and
  `LibraryNextUpComponents.kt`, and reusable library rails/cards are split into
  `LibraryRailComponents.kt`. Mobile Library route composition is split into
  `LibraryPage.kt`, and Connect route composition plus the connection form/rows
  are split into `ConnectPage.kt`. Android TV decomposition has started with
  poster loading, formatting, next-up, progress/watch-status, and metadata
  labels in `TvUiHelpers.kt`; player surface, seek controls, and audio/subtitle
  track controls are split into `TvPlayerPanel.kt`; app destination metadata,
  top-level rail/header, and shared rail pill/navigation items are split into
  `TvShellUi.kt`; Home route composition is split into `TvHomePanel.kt`, while
  Home-only recently-added, series, and status rails are split into
  `TvHomeRailComponents.kt`; PC connection route UI, text input, and saved-PC
  cards are split into `TvPcConnectionPanel.kt`; library navigation and
  empty-state panels are split into `TvLibraryPanels.kt`; Library route
  composition is split into `TvLibraryScreen.kt`, mutable filter/selection
  controls are split into `TvLibraryControlsState.kt`, and derived
  catalog/filter/progress view state is split into `TvLibraryViewState.kt`;
  header/search/filter controls are split into `TvLibraryFilterComponents.kt`;
  series picker rendering is split into `TvSeriesPickerRail.kt`; library
  poster rendering is split into `TvLibraryPosterComponents.kt`; episode list
  rows are split into `TvEpisodeRowComponents.kt`; episode/series detail panels are split into
  `TvLibraryEpisodeComponents.kt`; progress and next-up rail containers are
  split into `TvLibraryRails.kt`, while their focusable cards are split into
  `TvLibraryRailCards.kt`; duplicated remote playback
  preparation and resume handling are split into `TvPlaybackActions.kt`;
  remembered TV player/library state and PC/library/favorite/playback actions
  are split into `TvPlayerState.kt` and `TvPlayerActionHandler.kt`; shared TV
  colors, poster endpoint construction, and focus halo styling are split into
  `TvUiPrimitives.kt`; `TvPlaybackController` gives TV action code a testable
  playback boundary while the Media3 adapter remains at the app edge.
  `TvPlayerActionHandler` has instrumentation-source coverage for catalog
  refresh, catalog errors, saved connections, selection/forget actions,
  favorites, PC discovery success/no-server/failure paths, and playback
  preparation/dispatch with and without resume lookup. Top-level TV screen
  lifecycle/effect wiring is split into `TvPlayerScreen.kt`, route rendering is
  split into `TvPlayerContent.kt`, playback chrome and destination body
  rendering are separated into focused content helpers, and `MainActivity.kt`
  is a 17-line app entrypoint. TV Library state/focus/top-level layout is now
  separated from Library content-section rendering in
  `TvLibraryContentSections.kt`. TV Home recently-added, series, and status
  panels now live in separate files instead of the former catch-all Home rail
  component file. TV PC connection text input and saved-connection card
  rendering are also split into focused files, leaving the PC connection panel
  as the form/layout owner. Larger TV component splits remain. Both compile and
  have instrumentation-source coverage, but Android TV should be split further
  before more feature work lands there.
- Desktop localization now routes through generated resources. The duplicated
  Kotlin fallback initializer has been reduced to the small set of non-Compose
  error/default strings used by tests and default action paths, so normal UI
  string changes should happen in XML resources plus the resource adapter.
- From an anime-viewer workflow perspective, the current foundation covers
  local library playback, posters/metadata, progress, favorites, external
  mapping/sync, desktop local watch-list status/score/notes editing and
  filtering, provider readback status/score visibility, danmaku basics, and a
  shared library-quality scanner for duplicate/missing episodes, suspicious
  episode numbering, unmatched series, metadata episode-count mismatches, and
  metadata-assisted split/merge candidates, plus a desktop Library > Quality
  review view with persisted ignore/resolve state, localized split/merge
  guidance, inspector jumps for affected files, and a non-destructive Apply
  mappings action that persists metadata-derived item/series mappings with
  desktop test coverage for the apply-plan persistence path. Live `W:/Anime`
  QA covered 1,973 media items;
  after root-level title inference and release-name parser tuning, the scanner
  reports 130 review candidates, including 45 episode variant groups separated
  from 18 hard duplicate-number issues. Library Quality live QA is repeatable
  with `tools/windows/run-library-quality-live-qa.ps1`; the full `W:/Anime`
  fresh scan has no apply-capable split/merge rows because cached metadata is
  absent, while the copied mapped registered catalog applied one split-series
  plan with 20 item mappings and 2 series mappings and reduced open mapped
  issues from 39 to 38. Quality rows without an apply plan can now refresh
  dandanplay metadata for only their affected files, giving fresh scans a path
  from structural findings to metadata-backed mapping plans. Filesystem
  organization/rename tooling is still optional and should be preview-first.
  Missing high-value viewer workflows include
  seasonal/release-calendar views, OP/ED or recap skip markers, per-series
  subtitle/audio preferences, richer danmaku filtering and blocklists,
  optional filesystem organization/rename flows, richer external list-driven
  status/score workflows, and custom collections/tags.

## Not Implemented

- Release-ready headless standalone library server with durable catalog
  storage, provider settings, packaging, and desktop-remote migration.
- Release-ready macOS/Linux/iOS/iPadOS/web targets.
- Broad provider plugin marketplace or plugin sandboxing.
- DRM circumvention, unauthorized source scraping, or torrent/search behavior.
- Cloud account sync beyond external anime list integration foundations.

## Last Verified Commands

Recent local checks:

```powershell
.\gradlew.bat --no-daemon :apps:desktop-windows:compileKotlinDesktop
.\gradlew.bat --no-daemon :apps:desktop-windows:desktopTest
git diff --check
.\tools\windows\run-library-quality-live-qa.ps1
.\tools\windows\run-library-quality-live-qa.ps1 -LibraryRoot '' -OutputDir build\qa\library-quality-registered
.\tools\windows\capture-desktop-localization-screenshots.ps1
.\tools\windows\capture-desktop-localization-screenshots.ps1 -Languages zh-TW -Tabs home,library,tracking,settings
.\gradlew.bat --no-daemon :apps:android-mobile:assembleDebug
```

Recent Android TV emulator QA checks:

```powershell
.\tools\windows\run-android-tv-emulator-qa.ps1

# Equivalent manual commands per emulator:
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat --no-daemon :apps:android-tv:connectedDebugAndroidTest
.\gradlew.bat --no-daemon :apps:android-tv:installDebug
```

The 2026-06-18 run used `ANDROID_SERIAL=emulator-5554` against
`Danmaku_TV_API_36` and `Danmaku_TV_4K_API_36`; local visual QA screenshots
were captured under `build/qa/android-tv/`. The wrapper captures
`danmaku-tv-1080p.png` and `danmaku-tv-4k.png`.

Recent Android mobile emulator QA checks:

```powershell
.\tools\windows\run-android-mobile-emulator-qa.ps1

# Equivalent manual commands:
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat --no-daemon :apps:android-mobile:connectedDebugAndroidTest
$env:ANDROID_SERIAL='emulator-5556'
.\gradlew.bat --no-daemon :apps:android-mobile:connectedDebugAndroidTest
.\gradlew.bat --no-daemon :apps:android-mobile:installDebug
```

The 2026-06-18 mobile run used
`Pixel_3a_API_34_extension_level_7_x86_64` at 1080x2220 / 440 dpi and
`Danmaku_Tablet_API_34` at 2560x1600 / 320 dpi. Local visual QA screenshots
were captured under `build/qa/android-mobile/`.

Full project review checks run on 2026-06-15:

```powershell
cargo fmt --all --check
cargo test --workspace
.\gradlew.bat --no-daemon :shared:domain:jvmTest :shared:library-client:jvmTest :shared:library-server-core:jvmTest :apps:desktop-windows:desktopTest :shared:library-client-android:testDebugUnitTest :shared:player-android-media3:assembleDebugAndroidTest :apps:android-mobile:assembleDebug :apps:android-tv:assembleDebug
Push-Location tools\dandanplay-worker-proxy
npm run typecheck
npm test
Pop-Location
git diff --check
```

CI additionally runs the Rust, Worker proxy, Windows packaging, and macOS
desktop build/test jobs described in `.github/workflows/ci.yml`.
