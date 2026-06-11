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

- `[~]` Create or refine reusable desktop shell components for header,
  navigation rail, page title/action row, status badges, icon buttons,
  tooltips, and page-level empty/error states.
- `[~]` Add a desktop localization resource structure with English and
  Traditional Chinese (`zh-TW`) strings for shell navigation, page actions,
  status labels, dialogs, tooltips, and empty/error states.
- `[~]` Replace hardcoded user-facing desktop UI chrome with localized string
  lookups while keeping provider/content titles unchanged.
  - Desktop now has a persisted English / `zh-TW` language setting under
    Settings > General.
  - The shared shell rail, compact header, Settings section rail, and General
    language card use the first desktop string layer.
  - Remaining: migrate page actions, status labels, dialogs, tooltips,
    inspector copy, and empty/error states.
- `[ ]` Add UI review screenshots or checks for English and `zh-TW` labels in
  dense surfaces such as the rail, toolbar, inspector, and settings forms.
- `[x]` Normalize route labels to Home, Library, Downloads, Player, Tracking,
  and Settings.
- `[x]` Decide whether Tracking becomes a first-class `DesktopShellTab` instead
  of remaining only inside the Library workspace.
- `[~]` Add consistent keyboard shortcuts for global search, refresh/rescan,
  route navigation, and primary page action.
  - Desktop shell now supports `Ctrl/Cmd+K` for global search focus,
    `Ctrl/Cmd+R` for library rescan, and `Ctrl/Cmd+1..6` for Home, Library,
    Downloads, Player, Tracking, and Settings routing.
  - Desktop shell now supports `Ctrl/Cmd+Enter` as a page primary action where
    the action is real and enabled: rescan from Home/Library, refresh Downloads,
    play/pause in Player, and sync ready Tracking updates.
  - Remaining: define Settings/profile primary actions once editable sections
    have a single safe save/submit surface.
- `[ ]` Add visual QA screenshots for default, narrow, and wide desktop window
  sizes.

## Library Page

- `[ ]` Refactor the Library page toward a stable three-pane layout: app rail,
  library subrail, center workspace, and resizable inspector.
- `[ ]` Keep the inspector above a useful minimum width for long episode names.
- `[~]` Ensure selecting a series or episode always refreshes the details panel.
  - Library episode lists now pass the active selected media ID through the
    center workspace, keep the selected row highlighted across Continue
    Watching, Next Up, History, Favorites, and Files, and update the inspector
    as keyboard selection moves.
- `[~]` Make rows/cards selectable in Continue Watching, Next Up, Recently
  Watched, Favorites, Files, History-style surfaces, and Paired views.
  - Continue Watching, Next Up, History, Favorites, and Files rows now use the
    shared selected-media highlight; Paired remote rows remain separate.
- `[~]` Show matched anime title, local series title, local file/folder title,
  and episode title together without ambiguity.
  - Home cards and Library episode rows now prefer matched anime display
    titles while preserving the local file group label when it differs.
- `[~]` Add explicit metadata/poster loading, stale, partial, failed, and ready
  states in the inspector and browse cards.
  - Desktop Library now uses shared readiness labels for metadata loading,
    failed, partial, ready, and needed states in the inspector, series poster
    cards, file/favorite rows, and compact inspector episode list.
  - Remaining: add a real freshness/stale signal once metadata cache age or
    provider revision data is available.
- `[~]` Add manual refresh metadata/poster actions at both series and episode
  scope.
  - Series cards and the inspector expose series metadata refresh; the
    inspector, Continue Watching, Next Up, History, Files, and Favorites rows
    now expose scoped episode metadata refresh without requiring playback
    preparation.
  - Remaining: align Paired remote-library metadata actions once remote
    metadata editing exists.
- `[x]` Ensure episode-level mapping updates only the selected episode.
  - Episode mapping actions now route through the selected inspector
    `LibraryMediaItem`, and the catalog store has regression coverage proving
    replacing one episode mapping does not alter sibling episode mappings or
    create a series-level mapping.
- `[~]` Add cache-state readback when an episode is selected.
  - The Library inspector now checks persisted dandanplay cache state whenever
    the selected inspector episode changes, including default next-playable
    selections and prepared playback selections, and exposes a compact manual
    check-cache action on the selected episode.
  - Remaining: add persistence/relaunch coverage and broader cache manager UI.
- `[~]` Add tests for selection propagation, metadata refresh action routing,
  and episode-only mapping updates.
  - Episode-only item mapping persistence is covered at the catalog store
    level.
  - Remaining: add UI/route tests for selection propagation and metadata
    refresh action routing.

## Player Page

- `[ ]` Align player overlays with the mockup: compact top overlay, bottom
  transport, and collapsible right panel.
- `[x]` Add current Player focus mode so playback can hide non-video chrome and
  use almost the full window.
- `[x]` Add clear restore controls and a keyboard shortcut for Player focus
  mode.
- `[x]` Persist Player focus visibility for the current player session without
  changing sidebar behavior on non-player pages.
- `[x]` Wire the future right player details/danmaku panel into focus mode once
  that panel is implemented.
  - Player now has a collapsible right danmaku/details panel backed by current
    playback state, selected audio/subtitle tracks, overlay status, cached
    dandanplay status, and persisted danmaku display preferences. Focus mode
    and fullscreen hide the panel so video takes over the workspace.
- `[~]` Keep video dominant in embedded and fullscreen modes.
  - Focus/fullscreen paths hide the right panel and non-video chrome; remaining
    work is visual QA on default, narrow, wide, fullscreen, and 4K content.
- `[~]` Add or verify controls for previous/next episode, skip, volume, rate,
  audio track, subtitle track, aspect, fullscreen, and danmaku visibility.
  - Player transport now includes local-library previous/next episode actions,
    10s/30s skip, play/pause, seek bar, volume, rate, audio/subtitle cycling,
    aspect, fullscreen, focus mode, and danmaku panel visibility. Previous/next
    actions are enabled only when the active playback item belongs to the local
    indexed catalog.
  - Remaining: visual and behavioral QA for every control across compact,
    default, fullscreen, and direct-file/remote playback contexts.
- `[~]` Add right-panel controls for danmaku opacity, density, font size, lane
  behavior, offset, and filter presets.
  - Right player panel now controls danmaku visibility, opacity, density, font
    scale, speed, display area, and offset through the persisted danmaku
    preference path.
  - Remaining: lane behavior and filter preset editing.
- `[~]` Show cached danmaku state after media selection and after relaunch.
  - Player now inspects persisted dandanplay cache state when the active local
    catalog episode changes, so the right panel and preparation overlay can
    show cached/no-cache/error state without requiring a fresh Prepare action.
  - Remaining: add relaunch/session-restoration coverage and broader cache
    manager UI for persisted entries.
- `[~]` Add preparing and error states that identify the failed step.
  - Player now shows a structured preparation/status overlay using the
    existing local preparation flag, player runtime error, library preparation
    error, and dandanplay cache status so failures point at the affected step.
  - Remaining: add visual QA around overlapping overlays and step-specific
    retry actions.
- `[ ]` Run fullscreen, resize, aspect, hardware decode, and 4K playback QA.

## Downloads Page

- `[ ]` Define authorized download source contracts before implementing queue
  execution.
- `[x]` Implement queue dashboard sections for active, queued, completed, and
  failed downloads.
- `[~]` Add per-item pause, resume, cancel, retry, remove, and open-folder
  actions.
  - Download rows and the selected inspector now support real open-output-folder
    and persisted queue-row removal with confirmation.
  - Pause, resume, cancel, and retry remain disabled/planned until authorized
    download source contracts and queue execution exist.
- `[~]` Add bandwidth limit, schedule, destination, storage warning, and source
  authorization UI.
- `[~]` Add selected-download inspector with metadata, path, progress, checksum
  or cache state, logs, and retry history.
  - Downloads now has selectable queue rows, active/queued/completed/failed
    filters, a selected-item inspector with redacted source URL, output path,
    progress, created/updated times, state, failure message, open-folder, and
    remove actions.
  - Remaining: checksum/cache state, execution logs, and retry history once
    download execution is implemented.
- `[x]` Ensure UI copy never implies unsupported scraping, torrent search, DRM
  circumvention, or unauthorized access.

## Tracking Page

- `[x]` Promote Tracking to a dedicated desktop destination if the product
  keeps MAL/Bangumi sync as a first-class workflow.
- `[x]` Add provider connection cards for MyAnimeList and Bangumi.
- `[x]` Add sync summary cards for ready updates, conflicts, failed retries,
  and manual mapping needed.
- `[x]` Build a tracking table with local series, matched provider anime, local
  progress, provider progress, planned action, confidence, and status.
- `[~]` Add a selected-mapping inspector with provider IDs, episode mapping,
  refresh provider state, sync now, remove mapping, and conflict resolution.
  - Tracking now has selectable rows for conflicts, ready updates, failures,
    and skipped mappings, plus an inspector showing provider IDs, URLs, local
    series IDs, local/provider progress, confidence, status, scoped sync for
    ready rows, and disabled planned controls for provider readback, mapping
    removal, and conflict resolution.
- `[ ]` Add provider readback before writes where APIs allow it.
- `[ ]` Decide whether sync failures should be persisted across relaunch.
- `[ ]` Add live-account QA checklist for OAuth, token refresh, sync preview,
  conflict handling, failure retry, and relaunch behavior.

## Settings Page

- `[x]` Split settings into General, Library, Playback, Danmaku, Providers,
  Server, Storage, Privacy, and Diagnostics groups.
- `[~]` Add dirty-state tracking and disable Save Changes until values change.
  - Danmaku display, dandanplay provider, and external list provider forms now
    derive validated drafts and only enable Save when editable values changed.
  - Remaining: carry the same behavior into future editable Library, Playback,
    Server, Storage, and destructive-action settings as those controls become
    real forms.
- `[~]` Add inline validation for provider URLs, local paths, ports, cache days,
  and numeric playback/danmaku values.
  - Danmaku numeric ranges, dandanplay/Bangumi provider URLs, Bangumi
    User-Agent, and dandanplay cache days now show inline validation and block
    invalid saves.
  - Remaining: local paths, ports, playback values, and download/cache storage
    values once those settings are editable in the desktop UI.
- `[x]` Add Test Connection actions for dandanplay, MyAnimeList, Bangumi, and
  server settings where applicable.
  - Provider settings now expose saved-configuration test actions for
    dandanplay, MyAnimeList, and Bangumi, using existing clients and writing
    success/failure details to diagnostics without logging secrets.
  - Settings > Server now exposes a local server health test that validates
    server status and the pairing-token catalog path, writing API version,
    streaming support, and catalog item count to diagnostics.
  - Provider and local server test actions now show inline testing, OK, and
    failed badges with the last result detail in the Settings cards.
- `[~]` Ensure credentials are masked, stored only in approved local stores, and
  omitted from diagnostics.
- `[~]` Add confirmation dialogs for clearing credentials and destructive cache
  or mapping actions.
  - Settings now confirms dandanplay credential clearing, dandanplay expired
    cache cleanup, MyAnimeList credential clearing, and Bangumi credential
    clearing before invoking destructive callbacks.
  - Remaining: confirm destructive mapping actions once mapping removal and
    conflict-resolution flows are implemented.

## Secondary Surfaces

- `[~]` Build Server Dashboard modal/panel with LAN URL, pairing code,
  connected clients, bandwidth, recent requests, and copy actions.
  - Settings > Server now opens a Server Dashboard dialog with copy actions
    for the base URL, pairing code, and LAN URLs, the local-server health test
    status/action, discovery port, and a bounded live recent-request log from
    server events.
  - Remaining: instrument client identity/session state and byte counters
    before showing real connected-client and bandwidth metrics.
- `[ ]` Build Metadata Match dialog with candidate search, confidence, poster
  previews, provider IDs, manual override, and save mapping actions.
- `[~]` Build Danmaku Cache Manager with persisted cache state, cleanup rules,
  selected episode state, and confirmation for bulk deletes.
  - Settings > Danmaku now opens a Danmaku Cache Manager backed by persisted
    dandanplay cache rows, with cached/expired/comment summaries, selectable
    entries, selected-entry details, refresh, per-entry delete confirmation,
    and confirmed cleanup using the configured cache-age rule.
  - Remaining: connect cache rows to richer library episode context and add
    persistence/relaunch UI tests around cache readback.
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
