# Desktop Pages UI Spec

Date: 2026-06-10

## Scope

This spec defines expected behavior for the desktop pages beyond Home:

- Library
- Player
- Downloads
- Tracking
- Settings
- Secondary surfaces: server dashboard, metadata matching, cache manager,
  library import, and confirmation/error dialogs

Related documents:

- [Home and app shell UI design](../home-and-app-shell-ui-design.md)
- [Desktop UI page mockups](README.md)
- [Desktop pages UI tasks](desktop-pages-ui-tasks.md)

## Global Shell Rules

All desktop pages should share the same shell contract:

- Persistent top header with product identity, global search, refresh/rescan,
  diagnostics/notifications, help, settings, profile/device identity, and
  window controls.
- Persistent primary navigation with Home, Library, Downloads, Player,
  Tracking, and Settings.
- Warm amber active navigation state, teal/green success, blue sync/info, amber
  loading/conflict, and red failure/destructive state.
- Icon buttons for compact commands, with tooltips and accessible labels.
- No page-specific giant hero typography inside the app shell.
- No nested decorative cards; panels should be functional containers.
- Long titles must either wrap in details panels or have tooltip/read-more
  affordances.

## Library Page

Mockup: [Library page](library-page-mockup.png)

### Purpose

Library is the primary browse, inspect, metadata, and playback-preparation
surface. It should make local file identity and matched anime identity
understandable at the same time.

### Layout

- Left primary app rail remains visible.
- Library gets a secondary navigation rail:
  - Continue Watching
  - Next Up
  - All Series
  - Recently Watched
  - Favorites
  - Files
  - External Sync
  - Paired
- Center area contains:
  - Search field.
  - Filter, subtitle, favorite, sort, and grid/list controls.
  - Current filter chips.
  - Poster-led series grid for series views.
  - Dense episode table/list for file and episode views.
- Right inspector is resizable and never thinner than the long-title minimum.

### Selection Behavior

- Selecting a series updates the inspector immediately.
- Selecting an episode updates the episode section inside the inspector.
- Selecting rows in History, Files, Favorites, Continue Watching, and Next Up
  should use the same selection model as All Series.
- Double-clicking or pressing Enter on a playable row should run the primary
  playback action.
- Details must not remain stale when switching series, filters, or tabs.

### Inspector Content

Series inspector:

- Poster or deterministic fallback art.
- Matched anime title and local series/folder title.
- Provider metadata status: ready, loading, stale, failed, or not available.
- Episode count, watch progress, subtitles, size, and latest episode.
- Resume/Play next action.
- Refresh metadata/poster action.
- Favorite/watch-later controls where applicable.

Episode inspector:

- Episode title, local file name, relative path, matched anime title, and
  matched episode information if known.
- Prepare, Play/Resume, Inspect cache, Refresh episode metadata/poster.
- Cached danmaku status: cached, missing, loading, stale, failed.
- Manual mapping controls for provider IDs at series and episode level.

### Empty and Loading States

- No library: show add-folder and import ani-rss actions.
- Scanning: keep prior library visible if available and show scan progress.
- Metadata loading: show amber loading badge without hiding existing posters.
- Metadata failed: keep local content browsable and provide retry.

## Player Page

Mockup: [Player page](player-page-mockup.png)

### Purpose

Player should prioritize video while keeping playback, track, and danmaku
controls discoverable.

### Layout

- Video surface is dominant.
- Both sidebars must be hideable:
  - The left app/navigation rail can collapse or fully hide while in Player.
  - The right episode/details/danmaku panel can collapse or fully hide.
  - With both hidden, the video surface should take almost all available
    window space while preserving hover/keyboard access to essential controls.
- Top overlay:
  - Back/Home and Library controls.
  - Current series, episode, and source badge.
  - Fullscreen/window controls.
- Bottom overlay:
  - Timeline, current time, duration, buffered state.
  - Play/pause, previous/next, skip back/forward, volume, rate, aspect mode.
  - Audio, subtitle, danmaku, and settings controls.
- Right panel is collapsible:
  - Episode details.
  - Up Next.
  - Danmaku controls.
  - Cached danmaku status.
  - Audio/subtitle tracks.

### Behavior

- Controls appear on mouse movement, keyboard focus, pause, or explicit panel
  open; they fade when playback is active and idle.
- Player focus mode hides both sidebars and keeps playback controls available
  through hover, keyboard shortcuts, and a small restore-sidebar affordance.
- Sidebar visibility should persist for the current player session and should
  not unexpectedly affect non-player pages.
- Keyboard shortcuts remain available: play/pause, seek, volume, rate, track
  cycling, aspect, fullscreen.
- Up Next is visible near the end of an episode and can be dismissed.
- Danmaku controls update the real overlay immediately and persist to playback
  preferences.
- Cached danmaku state should be read when media is selected and after app
  relaunch.

### States

- Empty player: show Open File, Home, Library, and recent resume actions.
- Preparing: show source, metadata, danmaku, and subtitle preparation progress.
- Error: show failed step, retry, open file location, and diagnostics action.
- Fullscreen: reduce chrome but keep keyboard and hover controls.

## Downloads Page

Mockup: [Downloads page](downloads-page-mockup.png)

### Purpose

Downloads manages authorized downloads only. It should expose queue state,
storage behavior, retries, and source authorization clearly.

### Layout

- Summary row: active, queued, completed, failed, bandwidth, storage remaining.
- Central queue with tabs or filters:
  - Active
  - Queued
  - Completed
  - Failed
- Right inspector for selected item:
  - Anime/episode metadata.
  - Source authorization status.
  - Destination path.
  - Quality/track information.
  - Progress, speed, ETA, size, checksum/cache state.
  - Activity log and retry history.

### Behavior

- Pause/resume/cancel/retry controls are available per item.
- Pause All/Resume All are available when applicable.
- Failed items keep enough error detail to act without showing secrets.
- Open Folder works for completed and active destination paths.
- Bandwidth limit and schedule settings apply without restarting the app.
- No unsupported provider, scraping, torrent, or DRM-circumvention flows should
  be implied in UI text.

### States

- Empty: explain that authorized sources or import rules are needed.
- Source unauthorized: show connect/test action, not a broken queue.
- Storage low: amber warning; block new downloads only when necessary.
- Failed: red status with retry and remove actions.

## Tracking Page

Mockup: [Tracking page](tracking-page-mockup.png)

### Purpose

Tracking manages MyAnimeList and Bangumi provider connections, mappings,
progress sync plans, conflicts, retries, and sync history.

### Layout

- Provider status cards for MyAnimeList and Bangumi.
- Summary cards:
  - Sync ready
  - Conflicts
  - Failed retries
  - Manual mapping needed
- Main table:
  - Local series
  - Matched provider anime
  - Local progress
  - Provider progress
  - Planned action
  - Confidence
  - Status
- Right inspector:
  - Poster and matched anime details.
  - Local title and provider title.
  - Provider IDs.
  - Episode mapping.
  - Refresh provider state.
  - Sync now.
  - Remove mapping.
  - Conflict resolution.

### Behavior

- Sync plans are previewed before writes.
- Provider readback should compare current remote state before writing when
  available.
- Conflicts require explicit user choice.
- Failed sync attempts show retry timing and reason.
- Provider tokens and secrets are never shown in clear text.
- Manual mapping updates only the selected series or episode, never unrelated
  group members.

### States

- Not connected: show connect/OAuth and token setup actions.
- Connected but stale: show Refresh Provider State.
- Sync ready: show count and explicit Sync Now.
- Conflict: show local value, provider value, and resolution options.
- Failed: show retry, copy diagnostics, and clear mapping where relevant.

## Settings Page

Mockup: [Settings page](settings-page-mockup.png)

### Purpose

Settings centralizes app configuration without becoming a single long form.

### Layout

Settings subnavigation:

- General
- Library
- Playback
- Danmaku
- Providers
- Server
- Storage
- Privacy
- Diagnostics

Main area uses grouped forms, not one long list. A right summary panel should
show connection health, unsaved changes, and validation failures.

### Behavior

- Save Changes is disabled until values differ from persisted settings.
- Test Connection validates provider/server settings without saving secrets
  unless the user explicitly saves.
- Clear Credentials is destructive and requires confirmation.
- Provider credentials are masked and omitted from diagnostics.
- Local paths use native folder pickers.
- Settings that affect the current player explain whether they apply
  immediately or next playback.

### States

- Dirty: amber unsaved-changes state.
- Invalid: inline validation and disabled save when unsafe.
- Saved: short success state that does not require modal dismissal.
- Secret missing: clear setup action and explanation.

## Secondary Surfaces

Mockup: [Secondary surfaces](secondary-surfaces-mockup.png)

### Server Dashboard

Shows:

- LAN URL and copy action.
- Pairing code and refresh action.
- Connected clients.
- Bandwidth and recent requests.
- Server status, port, and discovery state.

Behavior:

- Copy actions never expose secrets beyond intended pairing values.
- Pairing code refresh invalidates old code.
- Client disconnect/kick is explicit and confirmable.

### Metadata Match Dialog

Shows:

- Local file/folder identity.
- Current matched metadata.
- Candidate results with poster, title, year, type, confidence, provider ID.
- Manual override field.
- Save mapping and save episode mapping actions.

Behavior:

- Applying a series mapping affects only that series.
- Applying an episode mapping affects only the selected episode.
- Replacing a mapping shows the old and new values.
- Failed searches preserve current mapping.

### Danmaku Cache Manager

Shows:

- Cached files, size, stale entries, failed entries.
- Selected episode cache state.
- Cleanup by age, missing library item, or failed fetch.
- Inspect cache and remove selected actions.

Behavior:

- Cache state persists after relaunch.
- Selecting an episode reads cache state before reporting missing.
- Cleanup actions require confirmation when deleting many files.

### Library Import

Shows:

- Registered roots.
- Add folder and ani-rss output import.
- Scan progress.
- Reused/refreshed/new item counts.
- Error rows for inaccessible files.

Behavior:

- Scanning keeps prior library usable.
- Import actions show progress and completion summary.
- Removing a root requires confirmation and does not delete media files.

### Dialogs

Dialog rules:

- Destructive actions use red primary/destructive style and clear object names.
- Retry dialogs show the failed operation and reason.
- Confirmation copy should be short and specific.
- Dialogs must be keyboard accessible and dismissible with Escape when safe.
