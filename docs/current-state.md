# Current State

Last reviewed: 2026-07-29.

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
- Desktop-owned Rust library-server sidecar, enabled by default for local mode,
  with lifecycle shutdown, crash restart/backoff, root/rescan restart, packaged
  binary resolution, pairing token, JSON catalog, byte-range media/subtitle/
  poster serving, progress API, optional `/web/` assets, and UDP discovery.
  The embedded JVM desktop server/discovery runtime has been removed.
- Local and paired-LAN playback preparation, one-click play/resume, progress
  persistence, previous/next episode navigation, and optional auto-next. Desktop
  can also launch into the remote-library browser with
  `--remote-server-url`/`--remote-pairing-token` or the matching
  `DANMAKU_REMOTE_*` environment variables; explicit remote mode skips the
  local sidecar, auto-loads a remote catalog, and streams remote media URLs
  through the libmpv-backed controller.
- libmpv playback through a Rust/JNA bridge, embedded Windows video host,
  command planning, fullscreen/aspect controls, volume/rate controls, seeking,
  and runtime audio/subtitle track selection.
- dandanplay provider settings, signed/credential auth modes, optional Worker
  proxy usage, shared JVM API client/parsing code, shared MAL/Bangumi list
  tracking clients, media matching, comment
  fetching, ASS overlay generation, cached match/comment storage, cache
  cleanup, and manual match correction.
- Manual local danmaku attachment path and cached/synthetic overlay rendering.
- Anime metadata/poster resolution and cache, poster loading states, and manual
  metadata refresh on series/episode surfaces.
- MyAnimeList and Bangumi provider settings, manual mapping UI, metadata
  search/cache clients, dedicated loopback MAL OAuth callback, encrypted token
  storage, provider readback/write clients, External Sync preview, and explicit
  sync action with pre-write external-progress conflict checks. Imported
  provider list entries and sync failures persist in the desktop catalog
  database across
  relaunch, and provider-ahead conflicts can seed local watched progress from
  the Tracking inspector.
- Desktop local watch-list entries persist with status, score, and notes; the
  Library inspector can edit them, the Library toolbar has a direct status
  menu for browsing by local watch-list state including untracked series,
  series cards show saved local watch-list status badges and expose a quick
  status/clear menu, and summary chips expose the current saved watch-list
  count.

### Rust Native Player (Migration Preview)

- The `native/player-app` crate provides the `danmaku-player` egui/glow
  Windows client. Its M1 playback core composites libmpv's OpenGL render API
  beneath native controls and supports play/pause, seeking, rate, volume,
  mute, audio/subtitle selection, faded controls, and fullscreen restore.
- The M2 native danmaku renderer loads normalized comments from the Rust
  server's client-facing `/api/danmaku/{mediaId}` route or from local
  Bilibili XML/normalized JSON. It preserves scroll/top/bottom modes, ARGB
  colors, and small/normal/large sizes; deterministic display controls cover
  visibility, opacity, speed, density, and lane count.
- Danmaku timing uses the M1 interpolated overlay clock, so pause, rate changes,
  direct seeks, and large drift corrections recompute the active layout from
  the new media position. Dense-layout parity, subpixel motion, lane reuse,
  density/speed/lane controls, and seek-window behavior have focused Rust
  tests.
- XML, JSON, `.danmaku`, and ASS files can be attached at startup or dropped
  onto the player. XML/JSON render natively; existing cached ASS overlays use
  mpv's subtitle renderer for compatibility.
- The M3 library client covers discovery/manual pairing, catalog browsing and
  search, progress/resume, previous/next, and auto-next. M4 adds English and
  Traditional Chinese from the initial connect screen, durable playback and
  danmaku preferences, remembered server URL management without storing pairing
  tokens, and a web-admin link. M5 builds and non-interactively verifies a
  Rust-native portable zip. Its four-file real-media matrix passes with zero
  render failures and NVDEC active, completing the Phase 3 release gate.
- Phase 4 adds unified local hosting to that player: the package carries
  `library-server.exe` and built `/web/` assets; first-run folder selection
  starts the server asynchronously, waits for readiness, and connects
  automatically. The player can attach to an already-running local server,
  restart or stop one it owns, and stops its child on exit. Local roots are
  remembered without persisting pairing tokens.
- The optional always-on Windows mode is implemented as a per-user Task
  Scheduler logon task, not a privileged service. The packaged
  `manage-rust-library-background-host.ps1` provides Install/Start/Stop/Status/
  SetRoots/Uninstall plus a non-mutating `-PlanOnly` path. It installs the same
  Rust server/web assets under `%LOCALAPPDATA%\Programs\Danmaku`, stores a
  non-secret schema-1 marker beside the existing server data, waits for mapped
  roots, and preserves the database/settings on uninstall. The native player
  recognizes that marker, attaches only after a compatible headless health
  response, labels background ownership in English and Traditional Chinese,
  and does not expose player-owned root/credential/process controls for it.
- The Phase 4 consumer UI pass replaces the native client's tool-like hierarchy:
  first-run local hosting is a single primary folder action with advanced
  connection fields collapsed; the library uses an icon-first rail, content
  header, online state, featured Continue Watching hero, and larger poster
  cards; playback uses fading title and control surfaces with technical options
  kept in menus; Settings uses grouped consumer cards. Supervised default and
  maximized review plus repeatable 960x600 default, hover, and keyboard-focus
  captures pass. The minimum layout scrolls vertically without horizontal
  clipping, and interactive controls now retain visible focus outlines. The
  approved references and QA results are tracked in
  `docs/design/rust-player-ui-redesign-plan.md`. Compose desktop remains
  available during the remaining retirement work.
- The native player Settings now configures the danmaku provider directly: a
  dandanplay App ID/App Secret card persists credentials to a user-scoped
  `player-credentials.json` (kept out of the general preferences file) and
  injects `DANMAKU_DANDANPLAY_APP_ID`/`DANMAKU_DANDANPLAY_APP_SECRET` into the
  managed sidecar so its signed resolver is built and danmaku auto-loads on
  play without any web-UI step. The sidecar's dandanplay credential path now
  also auto-discovers `local.properties` (working dir, `%LOCALAPPDATA%/Danmaku`,
  `~/.danmaku`, `DANMAKU_LOCAL_PROPERTIES`), matching the external-anime path
  and the Kotlin desktop, so credentials configured there work in any sidecar
  launch (player-owned, standalone, or web-UI-facing) without re-entry. The Local server card manages multiple library
  folders (add/remove) persisted to `local_library_roots`, restarting the
  sidecar with every root. The web UI now auto-loads danmaku when an episode is
  selected and surfaces resolver/upstream failures as readable status text
  instead of a raw HTTP 502. The sidecar now auto-recognizes and categorizes
  the catalog from dandanplay matches: whenever danmaku resolves a selected
  match, the recognized anime identity is persisted to a `catalog-metadata.json`
  overlay and merged onto items lacking provider metadata when `/api/library`
  is served, so clients group episodes under the matched anime with no web-UI
  step. Existing provider metadata is preserved. The native player refreshes
  its catalog in the background as soon as a danmaku resolve reports a
  recognition the cached catalog does not reflect yet (and again when
  returning to the library screen), so the new series/grouping appears
  without an app restart. The sidecar also best-effort resolves and caches a
  poster image for a newly recognized anime (looked up via the configured
  MyAnimeList/Bangumi providers when dandanplay's match has none) under the
  data directory, served at `/posters/{mediaId}` and exposed as `posterPath`
  on `/api/library` items that do not already have a scan-time poster; the
  native player and web UI both render it through their existing poster
  loading paths. The library screen's grouped-series cache now keys off a
  session-tracked catalog version rather than the catalog's scan
  timestamp/item count, since server-side enrichment can change items
  without touching either. A series page also exposes a "match episodes"
  action that resolves danmaku (and records the anime association) for its
  still-unmatched episodes in the background, without navigating to
  playback. Poster resolution now also retries on every `/api/library` read
  for a recognized item still missing one (deduplicated in-flight per media
  ID), since the local server can be hard-killed by the desktop host
  mid-download and a one-shot fetch on recognition alone has no other retry.
  The dandanplay resolve route (`/api/providers/dandanplay/resolve`) now
  accepts `forceRefresh` to bypass the single-candidate comment cache, plus
  `animeId`/`animeTitle`/`episodeTitle` so an episode picked from a keyword
  search — which file-hash matching may never propose — can still be pinned,
  cached, and recorded. A sibling `/api/providers/dandanplay/search` route
  searches the dandanplay database by anime keyword and returns each anime
  with its full episode list. The native player uses both to power a manual
  match picker from the library: each episode row (series pages, search
  results, and the folder explorer) has a small danmaku-icon button opening
  a floating window that shows the file-hash candidates and a keyword search
  with an anime → episode drill-down, like the official dandanplay client's
  manual matching; picking an episode pins it, records the anime
  association, and reloads danmaku. Danmaku responses now also carry a bare
  `animeTitle` so the player's catalog-staleness check compares like with
  like (`matchTitle` embeds the episode suffix and never equals the
  catalog's recognized title). The "All series" library page now separates
  "Matched anime" (poster grid, grouped by recognized identity only) from
  "Folders", a file-explorer-style list of the on-disk layout modeled on the
  official client's media library: folder rows navigate with an up-one-level
  row, file rows show file name and size plus the matched anime and episode
  titles in columns, and each file row keeps the change-match button.
  The additive `/api/library/attention` status route now reports provider
  availability plus per-item mapped/unmapped, fresh/stale/missing cache,
  conflicting anime-ID, and last-refresh-failure state without hashing media or
  contacting a provider. Refresh failures persist as non-secret fixed diagnostics
  in `library-attention.json`. The native library renders series badges, a
  "Needs attention" filter, and episode-level status text, and offers queued
  match/refresh actions with visible progress. Existing mapped episodes are
  refreshed only through their persisted dandanplay episode ID; legacy mappings
  without that ID require the existing manual "Change match" action, so an
  automatic repair cannot silently replace their anime association.
- The July 13 Rust library polish adds a full-width media navigation sidebar
  plus Recent, Season, Matched Anime, and Folder views. The library toolbar now
  applies real inline text, match state, watch progress, top-level folder,
  title/newest/year/episode-count sort, and compact/comfortable/large poster
  filters. Recent groups by latest indexed month without discarding the chosen
  sort; Season groups recognized titles by release year; Folder keeps the
  hierarchical drill-down and filters visible files and directories while
  searching. Series pages now use a poster-led overview card with alternate
  title, episode/watch counts, release year, total size, subtitle count, and
  library-root context. The comprehensive dandanplay picker follows the same
  native card system: it identifies the current episode, separates file
  suggestions from full database search, presents explicit selectable candidate
  rows, and keeps anime-to-episode drill-down in a resizable, scrollable dialog.
  English and Traditional Chinese are covered for all new visible copy.
- The second July 13 pass brings the Rust client closer to the official
  dandanplay client. The Rust headless server now labels every catalog item
  with the absolute library root it was scanned from (`rootLabel`, additive)
  and proxies dandanplay bangumi profiles at
  `/api/providers/dandanplay/bangumi`. The player sidebar lists the actual
  configured folders (like the official client's 本機文件夾 section), and the
  folder explorer plus the folder filter browse per root when the server
  merges several roots. A new Recently Played view groups titles by
  last-played month; the toolbar adds a release-year filter and a
  grouped-display toggle. Series pages fetch the bangumi profile in the
  background and show rating, type, airing state, synopsis, tags, and
  online-database links alongside alternate titles and the item's real
  on-disk root; episode rows gain a watched checkmark, file size, and
  last-watched date. Watch-state classification now counts a series with
  some episodes finished as in progress instead of unwatched.

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
- Library browsing includes TV-native title, path, newest-added, last-watched,
  release-year, and episode-count ordering, plus a release-season/year filter.
  A dedicated D-pad folder/files route mirrors the Rust client's hierarchy:
  multi-root Rust catalogs begin with their `rootLabel` folders, older
  single-root catalogs fall back to relative paths, and selecting an episode
  file opens its anime detail route.
- Media3 playback and LAN progress sync through the shared Android playback
  module.
- Android TV emulator QA is now set up with `Danmaku_TV_API_36`
  (`tv_1080p`) and `Danmaku_TV_4K_API_36` (`tv_4k`) using
  `system-images;android-36;android-tv;x86_64`. The 2026-07-29 rewrite
  qualification passed the connected focus/cache/poster suite and captured
  English and `zh-TW` references for onboarding, Home, Library, Series Detail,
  Player, Search, Favorites, and PC at both resolutions. The wrapper rejects
  non-emulator serials and is `tools/windows/run-android-tv-emulator-qa.ps1`.
- The 2026-07-29 single-cutover rewrite replaced the old presentation path
  while retaining native Kotlin, Compose for TV, Media3, and the existing
  LAN/domain modules.
- Session, browse, navigation, and playback state are lifecycle-owned and
  separate. Catalog startup is cached-first, browse derivation runs outside
  composition, and posters use a bounded size-aware Coil loader.
- Video startup no longer waits for danmaku. Media3 listeners drive playback
  state, late preparation results are rejected, and indexed prepared timelines
  avoid whole-list per-frame scans.
- Unit, Compose, instrumentation, stress, poster-cache, screenshot, Baseline
  Profile, and Macrobenchmark coverage now protect the replacement. Fixtures
  cover 6,000 catalog items and 10,000 danmaku events with 500 simultaneous
  events. See `docs/design/android-tv-client-rewrite-plan.md`.
- The emulator Macrobenchmark suite passed all three tests on 2026-07-29.
  Five-sample results were 944 ms median initial display, 20.4/34.9/37.4 ms
  P50/P90/P95 frame CPU duration for the 100-action traversal, and 92,784 KiB
  median peak anonymous RSS. These emulator numbers are regression baselines;
  the budget-device performance gate remains deferred.

### Native And Tooling

- Rust `player-windows-mpv` crate for libmpv loading/probing and the desktop
  native playback command bridge.
- Rust `rust-core` timeline/indexing foundation.
- Windows scripts for pinned libmpv install and verification plus separate
  Compose-compatibility and Rust-native portable release preparation. The
  Rust-native package is a versioned runtime-free zip with the player, local
  server, built web UI, approved DLL, launcher, background-host manager/runner,
  provenance, license texts, and generated player/server crate inventories.
  Package verification exercises the background installer's non-mutating plan
  path; real task registration remains an explicit manual QA step.
  Runtime verification and supervised GUI smoke/release-QA scripts auto-detect
  both layouts. `tools/windows/run-rust-player-ui-qa.ps1` adds deterministic
  native-player onboarding captures at the supported 960x600 minimum for
  default, hover, and keyboard-focus states.
- Desktop app-level QA screenshot capture now raises the app window, waits for
  focus to settle, and restores the previous always-on-top state so captures are
  less likely to include unrelated foreground Windows apps.
- Cloudflare Worker proxy for dandanplay match/comment requests without
  shipping a dandanplay AppSecret in public clients.
- CI on Windows, Rust, Worker proxy, and macOS desktop build/test paths.
- A first server/client/web split foundation is in place: documented split
  plan, `shared:library-host-core` host contracts, opt-in desktop
  `--web-assets-dir`/`DANMAKU_WEB_UI_DIST` serving, a Vite TypeScript web UI
  scaffold for pairing/catalog/video/progress, provider readiness,
  provider mapping search, manual external-list read/write controls with
  metadata-link ID auto-fill, dandanplay match/comment preview with basic
  web video overlay controls and persisted browser preferences, and an
  `apps:library-server-windows` headless JVM host with data-directory locking,
  startup scanning for configured `--root` folders, JSON catalog publishing,
  durable catalog snapshots for startup readback, sidecar subtitle discovery,
  shared LAN media/subtitle streaming, non-secret provider status summaries,
  authenticated provider runtime readiness, authenticated read-only provider
  mapping search, authenticated external list entry read/write for MAL/Bangumi
  IDs, authenticated dandanplay match/comment resolve for catalog media,
  settings-file root/provider readback, and file-backed playback
  progress plus stable pairing-token persistence under
  the locked data directory. Headless hosts also announce themselves through
  the existing LAN discovery
  protocol after the HTTP server binds. The repeatable
  `tools/windows/run-headless-web-ui-qa.ps1` helper builds the web UI, launches a
  fixture-backed headless host, verifies the served `/web/` shell plus catalog,
  media, subtitle metadata, and progress readback routes, then restarts without
  explicit roots/token to verify cached catalog and persisted progress readback
  and runs a Chrome/Edge browser interaction probe for web danmaku overlay
  preference persistence, provider search, Use ID, and external-list form
  read/save behavior before writing PASS/FAIL reports under
  `build/qa/headless-web-ui/`. The
  `tools/windows/run-embedded-web-ui-qa.ps1` helper launches the Compose desktop
  with its Rust sidecar, isolated `LOCALAPPDATA`, deterministic pairing token,
  fixture root, and local web assets so the same web/browser surface is checked
  against the default desktop-owned sidecar. The compatibility script name is
  retained while its report now identifies the sidecar host.

- Desktop still consumes `shared/library-server-core` for JVM provider clients
  and a few progress/diagnostic contracts. Moving those calls behind the Rust
  sidecar HTTP API is the remaining Phase 2 dependency-removal slice; it is not
  part of the removed embedded-host runtime.

- Rust headless provider administration now uses a bearer-authenticated,
  secret-redacted settings route; the web form writes dandanplay, MAL, and
  Bangumi credentials to Windows DPAPI storage and reloads provider clients
  immediately after a successful save.

- Rust headless tracking administration now persists non-secret series mappings,
  provider readback, sync failures, and retry metadata in schema-1
  `external-tracking.json`. Bearer-authenticated routes derive a sync preview
  from the Rust catalog and progress stores, coalesce local series linked by an
  exact provider anime ID, and emit one update per provider for each logical
  group. Groups can carry one MAL and one Bangumi identity; contradictory IDs
  for the same provider are blocked as mapping conflicts. Sync also blocks
  provider-ahead conflicts and re-reads each external entry before an explicit
  write. The web UI exposes the mapping/readback workflow and requires the
  preview acknowledgement checkbox
  before enabling sync. Mock-provider Rust tests cover persistence, conflicts,
  readback, and writes; live MAL/Bangumi account QA remains user-attended.

## Partial Or Needs More QA

- Windows runtime probing and automated smoke playback passed on 2026-06-22
  against the runtime-free Windows portable package with four real media
  samples covering 1080p H.264 MP4, 1080p HEVC/ASS MKV, 4K HEVC MKV, and a
  large BD MKV with sidecar ASS available. A follow-up 2026-06-22 Computer Use
  pass fixed and verified fullscreen exit restore on the rebuilt distributable:
  the playback window returned from fullscreen to the original `1588x954`
  bounds at `(81,72)` with zero delta. Resize, seek/pause, track switching,
  longer 4K playback, hardware-decoding, and multi-display behavior still need
  broader manual validation. The release checklist and automated runtime/smoke
  report runner live in docs/qa/windows-playback-release-qa.md.
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
- The rewritten Android TV client passed English and `zh-TW` 1080p/4K visual
  QA plus its connected emulator suite on 2026-07-29. A budget-class physical
  TV focus, safe-area, performance, and real-LAN playback pass remains release
  QA.
- External MAL/Bangumi sync has fake/integration-style client coverage and UI
  wiring, plus an opt-in read-only live list-entry harness at
  `tools/windows/run-live-external-sync-readback-qa.ps1`. It still needs
  live-account manual write/restore QA. The live read/write and restore
  checklist lives in `docs/qa/live-external-sync-qa.md`.
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
- Android mobile has focused shell, route, state, action, and component files,
  although `MobilePlayerScreen` still owns file-picker URI loading and playback
  service lifecycle. Mobile Home, Watch, Library, and Connect route composition
  and their common rails are split into focused files.
- The Android TV cutover replaced its former decomposed presentation path
  entirely. `MainActivity` now hosts `TvApp`; typed navigation and route-focus
  memory are lifecycle-owned; session, browse, and playback state use separate
  immutable flows; and dedicated route files own the TV layouts. The old TV
  routes, monolithic state, raw poster loader, polling path, and obsolete
  action-handler UI were removed.
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

- Release-ready headless standalone library server with provider/admin web
  controls, completed live-account external sync QA, completed packaging QA,
  and remote-only desktop migration. The packaging checklist lives in
  `docs/qa/remote-headless-packaging-qa.md`.
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
.\tools\windows\run-headless-web-ui-qa.ps1
.\tools\windows\run-embedded-web-ui-qa.ps1
.\tools\windows\run-windows-playback-release-qa.ps1 -WindowsDistributionPath apps\desktop-windows\build\release\windows-portable -SmokeSeconds 8 -MediaPath <media-matrix>
```

Recent Android TV emulator QA checks:

```powershell
.\tools\windows\run-android-tv-emulator-qa.ps1

# Equivalent manual commands per emulator:
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat --no-daemon :apps:android-tv:connectedDebugAndroidTest
.\gradlew.bat --no-daemon :apps:android-tv:installDebug
```

The 2026-07-29 rewrite qualification used emulator-only serials against
`Danmaku_TV_API_36` and `Danmaku_TV_4K_API_36`. Visual references are under
`build/qa/android-tv/` and use
`danmaku-tv-{1080p|4k}-{en|zh-TW}-{route}.png`. The wrapper validates every
serial against `^emulator-\d+$` before installation, connected tests, input,
or capture.

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
