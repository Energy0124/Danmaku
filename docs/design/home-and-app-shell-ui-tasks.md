# Home and App Shell UI Tasks

Date: 2026-06-10

Status legend:

- `[x]` Done
- `[~]` In progress
- `[ ]` Not started

## Design Artifacts

- `[x]` Generate a desktop home/app-shell mockup for review.
- `[x]` Save the mockup under `docs/design/home-and-app-shell-mockup.png`.
- `[x]` Write the target design document for header, navigation, home content,
  operational status, platform adaptation, and QA expectations.
- `[x]` Generate desktop destination mockups for Library, Player, Downloads,
  Tracking, Settings, and secondary surfaces.
- `[x]` Write a page-level desktop UI behavior spec and task log.
- `[x]` Link the new design stream from the documentation index and canonical
  task backlog.

## Desktop Implementation

- `[ ]` Use [Desktop pages UI tasks](desktop-ui-pages/desktop-pages-ui-tasks.md)
  as the detailed implementation backlog for non-Home desktop pages.
- `[~]` Add localization support for English and Traditional Chinese (`zh-TW`)
  across the desktop shell and shared page chrome.
  - Desktop shell language is now selectable and persisted from Settings >
    General, with English and `zh-TW` strings for the app rail, compact header,
    settings rail, and language card.
  - Library metadata match dialog chrome, actions, empty/error states, poster
    labels, and candidate status copy now use the same English / `zh-TW`
    desktop string layer.
  - Library secondary navigation, toolbar search/action copy, active filter
    chips, source status labels, and details-pane resize affordance now use the
    same desktop string layer.
  - Downloads dashboard, queue filters, setup/status panel, selected-item
    inspector, and confirmation copy now use the same desktop string layer.
  - Tracking provider cards, summary cards, table headers/actions, generated
    row labels, selected-mapping inspector, and planned provider controls now
    use the same desktop string layer.
  - Tracking/Library External Sync preview summaries, actions, empty states,
    conflict/failure rows, skipped overflow copy, and local/external progress
    labels now use the same string layer.
  - Home operational status cards now use the same desktop string layer for
    server, metadata/posters, external sync, downloads, and cached danmaku
    labels/actions while keeping runtime values as source data.
  - The Settings Danmaku cache card and Danmaku Cache Manager dialog now use
    the same string layer for summaries, entry rows, details, empty states, and
    cleanup/delete confirmations.
  - The Settings Local Server card, Server Dashboard dialog, copy actions,
    health/readiness rows, and shared connection-test status badges now use the
    same string layer.
  - The Settings Danmaku Display card now uses the same string layer for
    summaries, input labels, visibility controls, filters, and save/reset
    actions.
  - Settings utility cards for Library, Playback Runtime, Storage, Privacy, and
    Diagnostics now use the same string layer for titles, descriptions, and
    metadata labels.
  - Home main dashboard and app rail chrome now use the same desktop string
    layer for library slices, now-playing fallback, Continue Watching,
    Recently Added/Watched, My Library summaries, empty states, and Home-only
    resume/next-up labels.
  - Settings General privacy copy and the real dandanplay/MyAnimeList/Bangumi
    provider forms now use the same desktop string layer for labels, actions,
    validation copy, and clear/cleanup confirmations.
  - Player preparation/status overlay and right danmaku/details panel chrome
    now use the same desktop string layer for labels, actions, cache rows,
    track fallbacks, sliders, and preparation step labels.
  - Player empty state, top navigation chrome, and bottom transport
    labels/tooltips now use the same desktop string layer.
  - Desktop native media/library/ani-rss picker titles and shell search icon
    descriptions now use the same string layer.
  - Home and Library file-group labels plus Library metadata readiness badges
    now use the same English / `zh-TW` desktop string layer.
  - Library inspector and local row actions now use the same string layer for
    empty/readiness copy, Details/Favorite, metadata refresh, playback prepare,
    Play/Resume, and advanced danmaku/cache actions.
  - Library paired-browse heading, Favorites/Files/All Series/Next Up empty
    states, All Series heading, and series-card refresh/play actions now use
    the same string layer.
  - Library recently-watched empty state, external ID mapping controls, provider
    episode labels, remove buttons, and Diagnostics panel title/empty state now
    use the same string layer.
  - Library Import summary cards, folder/import/rescan actions, registered-root
    rows, empty state, and remove-folder confirmation now use the same string
    layer.
  - Paired remote-library browse chrome now uses the same string layer for
    connection, search/filter, empty/error, prepared playback, and row actions.
- `[~]` Align the desktop top header with the target shell: app identity, global
  search, refresh/rescan, diagnostics/notifications, help, settings, and local
  profile/device controls.
  - Global search now accepts typed input, submits into the Library workspace,
    and can be focused with `Ctrl/Cmd+K`; refresh/rescan is also available from
    `Ctrl/Cmd+R`.
- `[x]` Normalize the desktop left navigation rail around Home, Library,
  Downloads, Player, Tracking, and Settings.
- `[~]` Add secondary library shortcuts in the rail or equivalent compact
  surface for Anime Series, Movies, OVAs / Specials, All Episodes,
  Collections, Favorites, Watch Later, and Completed.
- `[x]` Add a home dashboard layout with Continue Watching, Recently Added,
  My Library, and compact now-playing sections.
  - Desktop Home now shows Continue Watching, timestamp-backed Recently Added,
    Recently Watched, My Library, operational status, and compact now-playing
    context.
  - Existing local DB rows without the new timestamp fall back to file modified
    time during migration/load; new and unchanged scanned items preserve their
    first indexed time.
  - Home dashboard section headers, summary captions, empty states, card
    action labels, and Home-only resume/next-up detail labels now route through
    English / `zh-TW` desktop strings.
- `[x]` Add a right-side operational status column on desktop Home for server,
  metadata/posters, external sync, downloads, and cached danmaku.
- `[x]` Make status cards actionable with Open Dashboard, Refresh Metadata, Open
  Tracking / Sync Ready Updates, Open Downloads, and Manage Cache.
- `[~]` Add explicit in-progress, stale, partial, failed, and ready states for
  metadata/poster, external sync, download, and danmaku cache cards.
- `[~]` Ensure poster fallback art and matched metadata are consistently used on
  Home, Library, Tracking, History, and details surfaces.
  - Desktop Home episode and resume cards now prefer matched anime display
    titles, keep the local file group visible when it differs, and use matched
    titles for poster fallback text.
  - The local file-group label is now localized through the desktop string
    layer on Home cards and Library rows.
  - Library metadata search results now show provider poster previews from the
    cached candidate image URL before the user saves a MyAnimeList or Bangumi
    mapping.
- `[ ]` Verify selected media cards and table rows update details consistently
  across Home, Library, History, Downloads, and Tracking where applicable.
- `[x]` Keep the details side panel adjustable or responsive enough for long
  episode names and matched anime titles.
  - The Library details pane has compact/default minimum widths, a visible
    draggable resize handle, and a click-to-reset state after manual resize.

## Android Mobile and Tablet Adaptation

- `[x]` Add Android string resources for English and Traditional Chinese
  (`zh-TW`) before broad UI polish locks in hardcoded copy.
  - Android mobile now has English and `zh-TW` resource files for app label,
    primary navigation, Home/Library/Player/Downloads/Tracking/Settings chrome,
    operational status headings, and common actions.
  - Mobile bottom navigation, Watch/Library/Connect headers, library filter
    chrome, connection setup labels, player empty state, and common empty
    panels now read from Android resources.
  - Mobile episode-detail controls, track labels, poster metadata badges,
    next-up/series rails, and diagnostic error prefixes now read from Android
    resources. Residual literals are numeric controls and domain-generated
    status/progress labels.
- `[~]` Map Home, Library, Downloads, Tracking, and Settings into mobile
  navigation without copying the desktop rail directly.
- `[x]` Add mobile Home sections for Continue Watching, Recently Added, and key
  operational status.
- `[~]` Use a compact status strip or lower feed cards for server, metadata,
  downloads, tracking, and danmaku cache state.
  - Mobile now starts on a Home tab with Next Up, Continue Watching, Recently
    Added, Recently Watched, a mini-player, and a compact library
    connection/status card.
    Remaining mobile navigation/feed work: Downloads, Tracking, Settings,
    and richer server/metadata/download/tracking/cache status.
- `[ ]` Add tablet two-pane behavior where width allows persistent browse and
  detail areas.
- `[ ]` Capture phone and tablet screenshots for loading, empty, populated, and
  failed states.

## Android TV Adaptation

- `[x]` Add TV string resources for English and Traditional Chinese (`zh-TW`)
  and verify translated labels in focused TV controls.
  - Android TV now has English and `zh-TW` resource files for app label, TV
    navigation, Home hero/rows, Library/Player empty states, operational status
    headings, and common remote-friendly actions.
  - TV navigation rail, destination header, player controls, Home next-up
    panel, Library rail/search/filter chrome, and Library empty states now read
    from Android resources.
  - TV PC connection form, saved-PC actions, episode-detail controls, progress
    rail item counts, selected-track labels, poster/loading labels, and
    diagnostic error prefixes now read from Android resources. Residual literals
    are brand text, animation debug labels, file/progress values, and
    domain-generated watch-status strings.
- `[ ]` Map the Home shell into TV rails: hero/resume, Continue Watching,
  Recently Added, Library slices, and operational status.
- `[ ]` Ensure each TV Home card has one obvious primary action and visible
  focus state.
- `[ ]` Move dense operational details into a focused status panel instead of
  table-like desktop cards.
- `[ ]` Verify D-pad traversal between rail, hero, content rows, and status
  actions at 1080p and 4K.

## Visual System

- `[~]` Consolidate shared colors, spacing, card radius, status colors, and icon
  sizing for the desktop shell.
- `[~]` Prefer icon buttons with tooltips for compact commands.
- `[ ]` Audit text sizes so dashboard cards, tables, side panels, and buttons do
  not use hero-scale type.
- `[ ]` Add screenshots or visual QA notes for default, narrow, and wide desktop
  windows.

## Tests and QA

- `[ ]` Add focused desktop UI state tests for Home dashboard model selection,
  status card state mapping, and refresh action wiring where testable.
- `[ ]` Add Android mobile Compose tests for Home navigation and status state
  display.
- `[ ]` Add Android TV focus tests for Home rail and status panel traversal.
- `[x]` Run desktop compile/test after implementation changes.
- `[x]` Run Android mobile and TV compile/instrumentation-source checks after
  platform UI changes.
  - `:apps:android-mobile:compileDebugKotlin` and
    `:apps:android-tv:compileDebugKotlin` passed after the mobile and TV
    localization slices.
