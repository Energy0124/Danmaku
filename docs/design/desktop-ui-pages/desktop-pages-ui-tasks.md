# Desktop Pages UI Tasks

Date: 2026-06-10

Status legend:

- `[x]` Done
- `[~]` In progress
- `[ ]` Not started

## Design Artifacts

- `[x]` Generate Library page mockup.
- `[x]` Generate Player page mockup.
- `[x]` Generate Downloads page mockup.
- `[x]` Generate Tracking page mockup.
- `[x]` Generate Settings page mockup.
- `[x]` Generate secondary surfaces mockup.
- `[x]` Save mockups under `docs/design/desktop-ui-pages/`.
- `[x]` Document expected behavior and UX requirements for each page.

## Shared Shell

- `[ ]` Create or refine reusable desktop shell components for header,
  navigation rail, page title/action row, status badges, icon buttons,
  tooltips, and page-level empty/error states.
- `[ ]` Add a desktop localization resource structure with English and
  Traditional Chinese (`zh-TW`) strings for shell navigation, page actions,
  status labels, dialogs, tooltips, and empty/error states.
- `[ ]` Replace hardcoded user-facing desktop UI chrome with localized string
  lookups while keeping provider/content titles unchanged.
- `[ ]` Add UI review screenshots or checks for English and `zh-TW` labels in
  dense surfaces such as the rail, toolbar, inspector, and settings forms.
- `[x]` Normalize route labels to Home, Library, Downloads, Player, Tracking,
  and Settings.
- `[x]` Decide whether Tracking becomes a first-class `DesktopShellTab` instead
  of remaining only inside the Library workspace.
- `[ ]` Add consistent keyboard shortcuts for global search, refresh/rescan,
  route navigation, and primary page action.
- `[ ]` Add visual QA screenshots for default, narrow, and wide desktop window
  sizes.

## Library Page

- `[ ]` Refactor the Library page toward a stable three-pane layout: app rail,
  library subrail, center workspace, and resizable inspector.
- `[ ]` Keep the inspector above a useful minimum width for long episode names.
- `[ ]` Ensure selecting a series or episode always refreshes the details panel.
- `[ ]` Make rows/cards selectable in Continue Watching, Next Up, Recently
  Watched, Favorites, Files, History-style surfaces, and Paired views.
- `[ ]` Show matched anime title, local series title, local file/folder title,
  and episode title together without ambiguity.
- `[ ]` Add explicit metadata/poster loading, stale, partial, failed, and ready
  states in the inspector and browse cards.
- `[ ]` Add manual refresh metadata/poster actions at both series and episode
  scope.
- `[ ]` Ensure episode-level mapping updates only the selected episode.
- `[ ]` Add cache-state readback when an episode is selected.
- `[ ]` Add tests for selection propagation, metadata refresh action routing,
  and episode-only mapping updates.

## Player Page

- `[ ]` Align player overlays with the mockup: compact top overlay, bottom
  transport, and collapsible right panel.
- `[x]` Add current Player focus mode so playback can hide non-video chrome and
  use almost the full window.
- `[x]` Add clear restore controls and a keyboard shortcut for Player focus
  mode.
- `[x]` Persist Player focus visibility for the current player session without
  changing sidebar behavior on non-player pages.
- `[ ]` Wire the future right player details/danmaku panel into focus mode once
  that panel is implemented.
- `[ ]` Keep video dominant in embedded and fullscreen modes.
- `[ ]` Add or verify controls for previous/next episode, skip, volume, rate,
  audio track, subtitle track, aspect, fullscreen, and danmaku visibility.
- `[ ]` Add right-panel controls for danmaku opacity, density, font size, lane
  behavior, offset, and filter presets.
- `[ ]` Show cached danmaku state after media selection and after relaunch.
- `[ ]` Add preparing and error states that identify the failed step.
- `[ ]` Run fullscreen, resize, aspect, hardware decode, and 4K playback QA.

## Downloads Page

- `[ ]` Define authorized download source contracts before implementing queue
  execution.
- `[~]` Implement queue dashboard sections for active, queued, completed, and
  failed downloads.
- `[ ]` Add per-item pause, resume, cancel, retry, remove, and open-folder
  actions.
- `[~]` Add bandwidth limit, schedule, destination, storage warning, and source
  authorization UI.
- `[ ]` Add selected-download inspector with metadata, path, progress, checksum
  or cache state, logs, and retry history.
- `[x]` Ensure UI copy never implies unsupported scraping, torrent search, DRM
  circumvention, or unauthorized access.

## Tracking Page

- `[x]` Promote Tracking to a dedicated desktop destination if the product
  keeps MAL/Bangumi sync as a first-class workflow.
- `[x]` Add provider connection cards for MyAnimeList and Bangumi.
- `[x]` Add sync summary cards for ready updates, conflicts, failed retries,
  and manual mapping needed.
- `[ ]` Build a tracking table with local series, matched provider anime, local
  progress, provider progress, planned action, confidence, and status.
- `[ ]` Add a selected-mapping inspector with provider IDs, episode mapping,
  refresh provider state, sync now, remove mapping, and conflict resolution.
- `[ ]` Add provider readback before writes where APIs allow it.
- `[ ]` Decide whether sync failures should be persisted across relaunch.
- `[ ]` Add live-account QA checklist for OAuth, token refresh, sync preview,
  conflict handling, failure retry, and relaunch behavior.

## Settings Page

- `[x]` Split settings into General, Library, Playback, Danmaku, Providers,
  Server, Storage, Privacy, and Diagnostics groups.
- `[ ]` Add dirty-state tracking and disable Save Changes until values change.
- `[ ]` Add inline validation for provider URLs, local paths, ports, cache days,
  and numeric playback/danmaku values.
- `[ ]` Add Test Connection actions for dandanplay, MyAnimeList, Bangumi, and
  server settings where applicable.
- `[~]` Ensure credentials are masked, stored only in approved local stores, and
  omitted from diagnostics.
- `[ ]` Add confirmation dialogs for clearing credentials and destructive cache
  or mapping actions.

## Secondary Surfaces

- `[ ]` Build Server Dashboard modal/panel with LAN URL, pairing code,
  connected clients, bandwidth, recent requests, and copy actions.
- `[ ]` Build Metadata Match dialog with candidate search, confidence, poster
  previews, provider IDs, manual override, and save mapping actions.
- `[ ]` Build Danmaku Cache Manager with persisted cache state, cleanup rules,
  selected episode state, and confirmation for bulk deletes.
- `[ ]` Build Library Import panel for root management, ani-rss output import,
  scan progress, and scan result summary.
- `[ ]` Standardize destructive, retry, confirmation, and error dialogs.

## Tests and QA

- `[ ]` Add desktop tests for page routing, shell state, and action wiring where
  Compose desktop tests can cover it.
- `[ ]` Add model-level tests for download queue state once source contracts
  exist.
- `[ ]` Add tests for tracking readback/plan/conflict/retry behavior.
- `[ ]` Add tests for cache-state persistence and episode selection readback.
- `[x]` Run `:apps:desktop-windows:compileKotlinDesktop` after UI wiring.
- `[x]` Run `:apps:desktop-windows:desktopTest` after behavior changes.
