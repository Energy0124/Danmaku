# Current State

Last reviewed: 2026-06-15.

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
  Home content. Shared primitives, local/remote library rows, library
  workspace, and library inspector surfaces are further split into focused
  files. `DesktopShell.kt` has diagnostics/server-event and
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
  folder opening are in a download action object. `DesktopShell.kt` remains
  the main orchestration hotspot for shell/window effects and tab assembly,
  but it is now below the planned 1,000-line threshold. Desktop localization
  strings are initialized through a small DSL-backed holder instead of a giant
  constructor, avoiding JVM method-signature limits as English/`zh-TW` coverage
  grows. Compose Multiplatform resource-backed desktop string slices are in
  place for shell navigation/header chrome, settings section/general settings
  labels, playback/dialog control strings, Home/Library placeholder/count
  strings, Home dashboard body labels, Library workspace navigation/filter
  labels, Library import/inspector action labels, Library metadata/playback
  inspector labels, and Downloads queue/import labels with XML templates, using
  `commonMain/composeResources/values` and `values-zh-rTW`; the adapter keeps
  selected-language fallback strings when Compose's current locale does not
  match the selected desktop language.
- Multi-root local anime library indexing, incremental rescanning, ani-rss
  output-folder import, and persistent SQLDelight/SQLite storage.
- Trusted-LAN library server with pairing token, JSON catalog, byte-range media
  streaming, subtitle streaming, poster serving, progress API, authenticated
  hooks, and UDP discovery.
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
  provider write clients, External Sync preview, and explicit sync action.

### Android Mobile

- Compose Android app that discovers or manually connects to the Windows
  library server.
- LAN catalog browsing with search, filters, poster rendering, matched metadata,
  favorites, progress rails, episode details, and explicit play actions.
- Media3 playback through the shared Android playback module with sidecar
  subtitles, resume lookup, progress upload, seek, volume, audio/subtitle track
  controls, and background service support.

### Android TV

- Dedicated Android TV app module with TV-specific layouts.
- Top-level 10-foot shell with Home, Library, Search, Favorites, and PC
  destinations.
- Persistent left rail, focused PC connection screen, search/favorites
  destination state, Next Up/progress rails, poster/fallback artwork, details,
  explicit Play/Resume actions, favorite toggles, and D-pad-focused tests.
- Media3 playback and LAN progress sync through the shared Android playback
  module.

### Native And Tooling

- Rust `player-windows-mpv` crate for libmpv loading/probing and the desktop
  native playback command bridge.
- Rust `rust-core` timeline/indexing foundation.
- Windows scripts for pinned libmpv install, bundle verification, portable
  release preparation, mpv runtime verification, and GUI playback smoke tests.
- Cloudflare Worker proxy for dandanplay match/comment requests without
  shipping a dandanplay AppSecret in public clients.
- CI on Windows, Rust, Worker proxy, and macOS desktop build/test paths.

## Partial Or Needs More QA

- Windows fullscreen, resize, 4K, hardware-decoding, and multi-display playback
  behavior need broader manual validation.
- UI localization is now a design requirement for English and Traditional
  Chinese (`zh-TW`). Desktop shell chrome, settings chrome/general labels,
  playback/dialog controls, Home/Library placeholder/count strings, Home
  dashboard body labels, Library workspace navigation/filter labels, and Library
  import/inspector action labels, Library metadata/playback inspector labels,
  and Downloads queue/import labels have started moving to generated Compose
  resources, but broad resource extraction, app-language-to-resource locale
  control, remaining body-label migration, and screenshot QA are not complete.
- Android mobile/tablet layouts need final viewport QA on phone and tablet
  sizes.
- Android TV layouts need 1080p and 4K safe-area/focus QA on real or emulated
  TV targets.
- External MAL/Bangumi sync has fake/integration-style client coverage and UI
  wiring, but still needs live-account manual QA.
- macOS desktop can build and run through the shared shell, but embedded video
  composition and release packaging are not first-class yet.
- Download queue storage exists; a full authorized download engine is not
  implemented.
- The desktop shell still has orchestration blocks for shell/window effects
  and tab assembly; ongoing refactoring is moving them behind typed action
  boundaries without changing behavior. The immediate file-size target is met,
  so remaining work should be driven by coupling and testability.

## Not Implemented

- Headless standalone library server separate from the desktop app.
- Release-ready macOS/Linux/iOS/iPadOS/web targets.
- Broad provider plugin marketplace or plugin sandboxing.
- DRM circumvention, unauthorized source scraping, or torrent/search behavior.
- Cloud account sync beyond external anime list integration foundations.

## Last Verified Commands

Recent local checks during the 2026-06-12 desktop localization refactor work:

```powershell
.\gradlew.bat --no-daemon :apps:desktop-windows:compileKotlinDesktop
.\gradlew.bat --no-daemon :apps:desktop-windows:desktopTest
git diff --check
```

CI additionally runs the Rust, Worker proxy, Windows packaging, and macOS
desktop build/test jobs described in `.github/workflows/ci.yml`.
