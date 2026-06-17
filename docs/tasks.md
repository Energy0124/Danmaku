# Tasks

This is the canonical high-level backlog. Detailed design logs remain under
`docs/design/`.

Status legend:

- `[x]` Done
- `[~]` In progress
- `[ ]` Not started

## Active Priorities

- `[~]` Resolve P1 review findings from the 2026-06-15 full project review:
  convert expected LAN/provider/media failures away from hard
  `error(...)`/`check(...)` paths, split oversized Android entrypoints, and
  close release-blocking QA gaps for Windows desktop, Android mobile/tablet,
  Android TV, localization, and live external sync.
- `[~]` Align the desktop Home page and shared app shell with the new
  header/navigation/status-column design direction.
- `[~]` Extend the desktop page implementation toward the Library, Player,
  Downloads, Tracking, Settings, and secondary-surface mockups.
- `[~]` Ensure the desktop Player can hide both sidebars so video uses almost
  the full window in focus mode.
- `[~]` Add first-pass localization support for English and Traditional Chinese
  (`zh-TW`) across desktop, Android mobile, and Android TV UI chrome; desktop
  strings now use a DSL-backed holder so the growing string set does not hit
  JVM method-signature limits, and all current desktop `DesktopStrings` fields
  have Compose Multiplatform generated resource adapter coverage under
  `commonMain/composeResources`.
- `[~]` Finish desktop playback QA for fullscreen, resize, aspect, 4K media,
  hardware decoding, and multi-display behavior.
- `[~]` Complete Android mobile/tablet library viewport QA at phone and tablet
  sizes.
- `[~]` Complete Android TV safe-area, 1080p/4K, and D-pad focus QA.
- `[~]` Live QA for MyAnimeList/Bangumi mapping, OAuth, sync, conflict handling,
  relaunch behavior, and external list state.
- `[~]` Continue library UI polish where details, title clarity, poster states,
  and focus behavior affect everyday use.
- `[~]` Continue decomposing desktop `Main.kt` into focused shell, tab,
  settings, player, library, and shared UI modules while preserving behavior.
- `[~]` Introduce a desktop shell state/action boundary so orchestration moves
  out of feature rendering after the first file-ownership split; diagnostics
  and server-event state plus navigation/search/language state now have
  remembered state objects, and playback session/progress flags have a
  remembered state object. Settings/preferences/provider status also now have
  a remembered state object, and library/catalog/progress/indexing/sync flags
  are in a remembered state object. Settings/provider/cache actions are also
  extracted into a typed action object, and generic playback session/progress
  actions are in a playback action object. Local playback preparation and
  dandanplay overlay/cache actions are also split into a typed action object.
  Library root, metadata, favorite, external mapping/search, and tracking sync
  actions are now split into a typed library action object. Download queue
  refresh/remove/open actions are also split into a typed action object.
- `[~]` Reduce shell/lifecycle/window wiring in `DesktopShell.kt`; UI
  files, state holders, settings actions, playback actions, local
  playback/danmaku actions, library actions, and download actions are now
  split, playback command callbacks are delegated, and stale monolith imports
  have been trimmed. `DesktopShell.kt` is now below the 1,000-line milestone;
  remaining work is about coupling, not only file length.

## Next Engineering Work

- `[~]` P1: Replace expected user-facing crashes with recoverable typed
  failures and localized UI states. LAN discovery with no PC found and LAN
  client non-OK HTTP responses now use typed exceptions. Desktop missing
  indexed-media and dandanplay no-match action failures now use typed desktop
  user-action exceptions. Dandanplay, MAL OAuth, external anime search/write,
  ani-rss remote failures, external search with no configured provider, and
  poster fetch failures now use typed exceptions or optional-artwork fallbacks.
  A final `error(...)`/`check(...)` audit found only test sentinels and
  startup/developer invariants remaining. Metadata match no-provider and
  provider-search failures now surface localized English/`zh-TW` dialog copy;
  local playback preparation and paired library catalog/remote playback failures
  now surface localized visible error copy. Remaining work is broader localized
  copy for action diagnostics and screenshot QA.
- `[~]` P1: Split Android mobile `MainActivity.kt` into focused app shell,
  connection/library, home, playback, and shared UI/state files before adding
  more mobile features. First low-risk extraction moved poster loading, URL
  encoding, display labels, size formatting, watch-status labels, and playback
  time helpers into `MobileUiHelpers.kt`; shell chrome is now also split into
  `MobileShellUi.kt` with tab routing metadata, bottom navigation, shared page
  column/header, and the mini-player bar. Top-level tab routing now uses
  `MobileAppScaffold.kt` with explicit `MobileAppUiState` and
  `MobileAppActions` handoff objects. Remembered player/library state and
  derived catalog filtering/poster endpoints now live in `MobilePlayerState.kt`.
  Service/store side effects and scaffold callbacks now live in
  `MobilePlayerActionHandler.kt`, with only the Android file-picker URI load
  and playback-service lifecycle still in `MobilePlayerScreen`. Next pass
  should continue splitting the large page composables into screen files.
  `HomePage.kt` now owns the mobile Home route composition while shared rails
  remain package-internal for Library reuse. `WatchPage.kt` now owns the
  mobile Watch route and player-control helpers. Library detail and Next Up
  UI are now split into `LibraryDetailComponents.kt` and
  `LibraryNextUpComponents.kt`; reusable library rails/cards now live in
  `LibraryRailComponents.kt`, `LibraryPage.kt` owns the mobile Library route,
  and `ConnectPage.kt` owns the Connect route plus connection form/rows. The
  remaining entrypoint refactor work should move to Android TV.
- `[~]` P1: Split Android TV `MainActivity.kt` into focused TV shell,
  PC connection, home, library/search/favorites, playback controls, and shared
  focus/visual primitives before adding more TV features. First low-risk split
  moved poster loading, URL encoding, playback-time formatting, progress/watch
  labels, next-up labels, and metadata labels into `TvUiHelpers.kt`. Player
  surface, seek controls, and audio/subtitle track controls now live in
  `TvPlayerPanel.kt`. App destination metadata, top-level rail/header, and
  shared rail pill/navigation items now live in `TvShellUi.kt`. Home route
  composition and Home-only rails now live in `TvHomePanel.kt`. PC connection
  route UI, text input, and saved-PC cards now live in
  `TvPcConnectionPanel.kt`. Library navigation and empty-state panels now live
  in `TvLibraryPanels.kt`, and the Library route state/search/filter
  composition now lives in `TvLibraryScreen.kt`. Library poster tiles,
  episode/detail rows, series detail, progress rails, and next-up rails now
  live in `TvLibraryEpisodeComponents.kt`. Shared TV remote playback
  preparation, resume lookup, seek, and play dispatch now live in
  `TvPlaybackActions.kt`. Remembered TV player/library state now lives in
  `TvPlayerState.kt`, and PC discovery, library refresh, saved-connection,
  favorite, and playback item actions now live in `TvPlayerActionHandler.kt`.
  Shared TV colors, poster endpoint construction, and focus halo styling now
  live in `TvUiPrimitives.kt`. `TvPlayerActionHandler` now has androidTest
  source coverage for catalog refresh, catalog errors, saved connections,
  selection/forget actions, favorites, and PC discovery success/no-server/
  failure paths through a testable `TvLibraryDiscovery` boundary.
- `[ ]` P1: Add connected Android test runs to the release checklist and record
  the required device/emulator matrix for mobile playback, LAN sync, TV focus,
  and Media3 streaming.
- `[ ]` Decide whether external anime sync failures should be persisted in the
  desktop database or kept session-only.
- `[ ]` Add durable external list entry fetch/readback so sync plans can compare
  current provider state before writing.
- `[ ]` Extend user-facing danmaku controls beyond the current desktop offset
  path: add richer filters/blocklists/presets and bring quick controls to
  mobile and TV where practical.
- `[ ]` Define authorized download source contracts and queue execution
  behavior.
- `[ ]` Add QA scripts or checklists for Windows fullscreen/4K/hardware decode.
- `[~]` Add localization QA checks for English and `zh-TW` screenshots on
  dense desktop, mobile, and TV surfaces. Desktop now has deterministic launch
  overrides plus app-level screenshot capture for Home, Library, Downloads,
  Tracking, and Settings. A full English/`zh-TW` desktop baseline pass was run
  on 2026-06-16, then `zh-TW` Home, Library, Tracking, and Settings were
  recaptured after fixing dynamic provider/status/watch-summary localization.
  Remaining: final accepted cross-language desktop review plus mobile/TV
  screenshots.
- `[x]` P2: Finish English and `zh-TW` screenshot QA for desktop generated
  resources, then remove duplicated migrated fallback text from the Kotlin
  initializer. Dynamic desktop status strings are localized, the final full
  desktop screenshot pass rendered successfully, and the Kotlin fallback now
  keeps only the non-Compose error/default strings still used directly.
- `[ ]` P2: After cross-platform localization QA passes, audit residual
  hardcoded mobile/TV/desktop literals and move user-visible copy into the
  platform resource layers.
- `[ ]` P2: Continue reducing desktop orchestration hotspots by extracting
  shell lifecycle/window effects, catalog-store persistence helpers, Home tab
  presentation, and remaining large library/settings/playback surfaces where
  it improves testability.
- `[ ]` Add release checklist automation for Android APKs and Windows portable
  archives.
- `[x]` Move the remaining desktop player surface out of `Main.kt`; playback
  tab, shortcut, overlay, panel, constants, and cycling helpers are now split
  into focused desktop playback files.
- `[x]` Move the desktop library workspace and shared library row/card
  composables out of `Main.kt`; library UI is now split into tab, workspace,
  lists, inspector, and helper files.
- `[x]` Move desktop settings, server dashboard, and cache-management surfaces
  out of `Main.kt`; settings UI is now split into tab, danmaku, dialogs, and
  provider-card files.

## Design Workstreams

- [Home and app shell UI tasks](design/home-and-app-shell-ui-tasks.md)
- [Desktop pages UI tasks](design/desktop-ui-pages/desktop-pages-ui-tasks.md)
- [Android mobile and TV library UI tasks](design/android-mobile-tv-library-ui-tasks.md)
- [External anime mapping and tracking tasks](design/external-anime-tracking-tasks.md)

## Review Findings

Full review date: 2026-06-15.

- `[x]` P0: No local build, Rust test, Gradle JVM/Android/desktop test, or
  Worker proxy typecheck/test blocker found in the review run.
- `[~]` P1: Expected user-facing failures are being moved out of crash-style
  control flow. LAN discovery/client errors, desktop missing indexed-media and
  no-match action failures, dandanplay provider failures, MAL OAuth callback
  and token failures, external anime search/write failures, and ani-rss remote
  failures, external search with no configured provider, and poster fetch
  failures now use typed exceptions or optional fallbacks. The remaining
  `error(...)`/`check(...)` hits are test sentinels or startup/developer
  invariants. Metadata match no-provider and provider-search failure dialogs now
  use localized English/`zh-TW` copy, and local playback preparation plus paired
  library catalog/remote playback failures now use localized visible error copy.
  Broader diagnostic-log localization and screenshot QA remain.
- `[~]` P1: Android mobile and Android TV app entrypoints are monolithic
  enough to slow safe feature work and review. Android mobile helper,
  formatting, shell-chrome, top-level route mapping, remembered state,
  service/store action handling, and Home/Watch/Library/Connect route
  composition are now split out of `MainActivity.kt`. Android TV decomposition
  has started with shared UI helpers in `TvUiHelpers.kt`, player controls in
  `TvPlayerPanel.kt`, shell chrome in `TvShellUi.kt`, and Home route
  composition in `TvHomePanel.kt`, plus PC connection UI in
  `TvPcConnectionPanel.kt` and library navigation/empty states in
  `TvLibraryPanels.kt`; Library route state/search/filter composition now
  lives in `TvLibraryScreen.kt`, and episode/series/progress rail components
  live in `TvLibraryEpisodeComponents.kt`; duplicated remote playback
  preparation and resume handling now live in `TvPlaybackActions.kt`; remembered
  TV state and PC/library/favorite/playback actions now live in
  `TvPlayerState.kt` and `TvPlayerActionHandler.kt`; shared TV colors, poster
  endpoint construction, and focus halo styling now live in `TvUiPrimitives.kt`.
- `[ ]` P1: Release confidence still depends on manual QA for Windows
  fullscreen/resize/4K/hardware decoding, Android phone/tablet layouts,
  Android TV 1080p/4K focus traversal, desktop localization screenshots, and
  live MAL/Bangumi sync.
- `[ ]` P1: Download queue storage exists, but authorized download source
  contracts and queue execution behavior are not implemented.
- `[ ]` P1: External sync can write updates, but provider readback/durable
  failure handling still needs product decisions and implementation.
- `[x]` P2: Desktop localization resource migration is functionally wired, and
  screenshot QA flushed and fixed dynamic status/watch-summary leaks. The
  duplicated fallback Kotlin initializer has been reduced to the small
  non-Compose fallback set after the final accepted cross-language screenshot
  review proved the XML resources.
- `[ ]` P2: Desktop code is much healthier than the original monolith, but
  `DesktopShell.kt`, `DesktopLibraryCatalogStore.kt`, Home, Library,
  Settings, Playback, and string-resource adapter files remain review-heavy.
- `[ ]` P2: Mobile/TV unit-test tasks are sparse or `NO-SOURCE` in places;
  keep adding JVM/unit-level coverage for presentation/state logic as it is
  extracted from Compose entrypoints. Android TV action-handler instrumentation
  coverage now protects catalog refresh, saved connection, favorite state, and
  PC discovery behavior after the TV state/action split.

## Anime Lover Feature Backlog

- `[ ]` P1: Add first-class watch status workflows: Plan to Watch, Watching,
  Completed, Dropped, On Hold, Rewatching, score/rating, private notes, and
  status filters across desktop/mobile/TV.
- `[ ]` P1: Add external list import/readback so MAL/Bangumi can seed local
  watch status, watched episode counts, scores, and conflicts before writes.
- `[ ]` P1: Add release-calendar and seasonal anime views for "new this week",
  airing day, next episode, season/year, and recently updated local episodes.
- `[ ]` P1: Add library quality tools for duplicate files, missing episodes,
  suspicious episode numbering, unmatched files, bad filenames, and local
  series split/merge review.
- `[ ]` P1: Add per-series playback preferences for preferred subtitle track,
  audio track, subtitle requirement, playback speed, danmaku visibility, and
  resume/autonext behavior.
- `[ ]` P2: Add OP/ED/recap skip marker support with manual markers first,
  then persisted per-series/per-episode reuse.
- `[ ]` P2: Add richer danmaku controls: blocklist keywords/users, density
  presets, style presets, per-series offset, quiet mode, and quick toggle
  parity on desktop/mobile/TV.
- `[ ]` P2: Improve anime metadata display with alternate title preferences
  (Japanese, romaji, English, Chinese), studios, genres/tags, source material,
  year/season, episode count, and specials/OVA/movie ordering.
- `[ ]` P2: Add custom collections and smart filters such as Favorites,
  Watch Later, by studio, by season, by tag, unwatched, almost finished,
  downloaded/imported, and has local subtitles.
- `[ ]` P2: Add notification-style surfaces for newly indexed episodes,
  metadata refresh failures, external sync conflicts, and PC/server
  availability while keeping local-first privacy defaults.

## Quality Gates

- Shared domain changes should include common tests.
- LAN server/client behavior should include JVM tests and Android adapter tests
  where relevant.
- Desktop storage/provider/native changes should include desktop tests.
- TV UI changes should include D-pad/focus instrumentation coverage where
  practical.
- Android playback changes should run connected tests on a real device or
  emulator before release.

## Standard Verification

```powershell
cargo fmt --all --check
cargo test --workspace
.\gradlew.bat --no-daemon :shared:domain:jvmTest :shared:library-client:jvmTest :shared:library-server-core:jvmTest :apps:desktop-windows:desktopTest :shared:library-client-android:testDebugUnitTest :shared:player-android-media3:assembleDebugAndroidTest :apps:android-mobile:assembleDebug :apps:android-tv:assembleDebug
```

Connected Android checks:

```powershell
.\gradlew.bat --no-daemon :shared:player-android-media3:connectedDebugAndroidTest
.\gradlew.bat --no-daemon :apps:android-mobile:connectedDebugAndroidTest
.\gradlew.bat --no-daemon :apps:android-tv:connectedDebugAndroidTest
```
