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
- `[ ]` Align the desktop top header with the target shell: app identity, global
  search, refresh/rescan, diagnostics/notifications, help, settings, and local
  profile/device controls.
- `[ ]` Normalize the desktop left navigation rail around Home, Library,
  Downloads, Player, Tracking, and Settings.
- `[ ]` Add secondary library shortcuts in the rail or equivalent compact
  surface for Anime Series, Movies, OVAs / Specials, All Episodes,
  Collections, Favorites, Watch Later, and Completed.
- `[ ]` Add a home dashboard layout with Continue Watching, Recently Added, My
  Library, and compact now-playing sections.
- `[ ]` Add a right-side operational status column on desktop Home for server,
  metadata/posters, external sync, downloads, and cached danmaku.
- `[ ]` Make status cards actionable with Open Dashboard, Refresh Metadata, Open
  Tracking / Sync Ready Updates, Open Downloads, and Manage Cache.
- `[ ]` Add explicit in-progress, stale, partial, failed, and ready states for
  metadata/poster, external sync, download, and danmaku cache cards.
- `[ ]` Ensure poster fallback art and matched metadata are consistently used on
  Home, Library, Tracking, History, and details surfaces.
- `[ ]` Verify selected media cards and table rows update details consistently
  across Home, Library, History, Downloads, and Tracking where applicable.
- `[ ]` Keep the details side panel adjustable or responsive enough for long
  episode names and matched anime titles.

## Android Mobile and Tablet Adaptation

- `[ ]` Map Home, Library, Downloads, Tracking, and Settings into mobile
  navigation without copying the desktop rail directly.
- `[ ]` Add mobile Home sections for Continue Watching, Recently Added, and key
  operational status.
- `[ ]` Use a compact status strip or lower feed cards for server, metadata,
  downloads, tracking, and danmaku cache state.
- `[ ]` Add tablet two-pane behavior where width allows persistent browse and
  detail areas.
- `[ ]` Capture phone and tablet screenshots for loading, empty, populated, and
  failed states.

## Android TV Adaptation

- `[ ]` Map the Home shell into TV rails: hero/resume, Continue Watching,
  Recently Added, Library slices, and operational status.
- `[ ]` Ensure each TV Home card has one obvious primary action and visible
  focus state.
- `[ ]` Move dense operational details into a focused status panel instead of
  table-like desktop cards.
- `[ ]` Verify D-pad traversal between rail, hero, content rows, and status
  actions at 1080p and 4K.

## Visual System

- `[ ]` Consolidate shared colors, spacing, card radius, status colors, and icon
  sizing for the desktop shell.
- `[ ]` Prefer icon buttons with tooltips for compact commands.
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
- `[ ]` Run desktop compile/test after implementation changes.
- `[ ]` Run Android mobile and TV compile/instrumentation-source checks after
  platform UI changes.
