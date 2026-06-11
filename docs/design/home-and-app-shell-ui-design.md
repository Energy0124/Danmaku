# Home and App Shell UI Design

Date: 2026-06-10

## Scope

This document defines the target home page and shared app-shell UX for Danmaku
across the first-class targets, with the desktop shell as the reference design.
The goal is to make the app feel like one coherent media product while still
allowing Android mobile/tablet and Android TV to use platform-specific layouts.

Mockup:

- [Home and app shell mockup](home-and-app-shell-mockup.png)
- [Desktop UI page mockups](desktop-ui-pages/README.md)

Related work:

- [Home and app shell tasks](home-and-app-shell-ui-tasks.md)
- [Desktop pages UI spec](desktop-ui-pages/desktop-pages-ui-spec.md)
- [Desktop pages UI tasks](desktop-ui-pages/desktop-pages-ui-tasks.md)
- [Android mobile and TV library UI review](android-mobile-tv-library-ui-review.md)
- [Android mobile and TV library UI tasks](android-mobile-tv-library-ui-tasks.md)
- [External anime mapping and tracking tasks](external-anime-tracking-tasks.md)

## Product Goal

The home page should answer four questions within a few seconds:

1. What can I continue watching?
2. What changed in my local library?
3. Is my Windows library server healthy?
4. Are metadata, posters, danmaku cache, downloads, and external tracking ready?

The shell should make those answers visible without turning the first screen
into a settings dashboard. The app remains a media browser first, with system
state available in a compact operational column.

## Information Architecture

### Top Header

The header should be stable across major desktop destinations:

- App mark and product name.
- Global search for anime, episodes, tags, and file names.
- Refresh/rescan action.
- Notification or diagnostics entry point.
- Help/about entry point.
- Settings shortcut where space allows.
- Current profile or local device identity.
- Window controls on desktop.

Search should be keyboard-first on desktop and should keep enough width for
real queries. Header actions should use icons with tooltips; labels are only
needed for primary workflow commands inside content areas.

### Primary Navigation

Use a persistent left rail on desktop and TV. Use bottom navigation or a compact
top/bottom hybrid on mobile.

Desktop destinations:

- Home
- Library
- Downloads
- Player
- Tracking
- Settings

Secondary rail groups can expose library slices without becoming top-level
destinations:

- Anime Series
- Movies
- OVAs / Specials
- All Episodes
- Collections
- Favorites
- Watch Later
- Completed

The selected destination should have a strong but restrained active state:
accent strip, icon color, and slightly raised surface. Avoid oversized pills
that waste horizontal space.

### Home Dashboard

The desktop home page uses a three-zone layout:

- Left: persistent navigation and compact now-playing control.
- Center: media-first content with resume, recently added, and library summary.
- Right: operational status cards for server, metadata, sync, downloads, and
  danmaku cache.

The center column is the emotional and task center of the app. The right column
is for confidence and repair actions, not a second navigation menu.

### Content Hierarchy

1. Continue Watching
   - Wide thumbnails with poster/backdrop art.
   - Play overlay.
   - Episode title, progress, time remaining, and percentage.
   - Previous/next carousel controls where overflow exists.

2. Recently Added
   - Poster or square card grid.
   - Media type badge, new badge, latest episode, and added date.
   - Uses matched anime metadata where available.

3. My Library
   - Dense table/list for scanning.
   - Title, progress, latest episode, added date, status, and row menu.
   - Grid/list toggle for users who prefer poster browsing.

4. Now Playing
   - Compact persistent control at the bottom of the rail on desktop.
   - Should not steal focus from browsing.
   - Shows episode identity, progress, and basic controls.

## Operational Status Column

The right status column should be visible on desktop home and collapsible or
secondary on smaller layouts.

Cards:

- Server Status
  - Online/offline state, local-network label, clients, bandwidth, uptime.
  - Primary action: Open Dashboard.

- Metadata and Posters
  - Loading, ready, stale, failed, or partial states.
  - Poster count, matched-series count, last update.
  - Primary action: Refresh Metadata.

- External Sync
  - MyAnimeList and Bangumi connection state.
  - Ready updates count or sync failure summary.
  - Primary action: Open Tracking or Sync Ready Updates.

- Downloads
  - Active count, compact active queue, pause/cancel controls.
  - Primary action: Open Downloads.

- Cached Danmaku
  - Cached file count, total size, stale/healthy state.
  - Primary action: Manage Cache.

Status cards should use color sparingly. Green means healthy, amber means work
is running or attention is needed, red means failed or blocked. Loading states
should include animated progress in the app implementation, but the static
mockup only establishes placement and hierarchy.

## Visual System

The target feel is quiet, dense, and media-forward:

- Dark charcoal app background with slightly lifted panels.
- Warm amber primary accent for active navigation and playback progress.
- Teal/green success states and blue sync/connection accents.
- Small icons for commands and categories.
- Poster art carries color; chrome should stay restrained.
- Cards should use modest radius and subtle borders.
- Tables should be scan-friendly, not decorative.

Avoid:

- Marketing hero layout on the app home screen.
- Giant typography in dashboard panels.
- Cards nested inside larger decorative cards.
- Text-heavy buttons where an icon with tooltip is clearer.
- One-hue purple, beige, brown, or slate-only palettes.

## Localization

Danmaku should be designed for localization from the start. The first supported
UI languages are:

- English (`en`)
- Traditional Chinese (`zh-TW`)

All user-facing shell text, navigation labels, buttons, empty states, error
messages, tooltips, settings labels, provider status messages, and dialog copy
should come from localized resources instead of hardcoded strings.

The UI must allow for longer translated labels without breaking layout:

- Navigation and toolbar labels should have tooltip or accessible-label
  fallbacks when space is tight.
- Buttons should use icons plus localized accessible labels where possible.
- Tables and inspectors should wrap or elide non-critical text while preserving
  full values in details or tooltips.
- Generated/provider anime titles are content, not UI chrome, and should be
  displayed in the source language returned by metadata unless a provider
  supplies localized titles.

## Platform Adaptation

### Windows Desktop

Desktop is the reference shell:

- Persistent left rail.
- Header search and utility actions.
- Center dashboard plus right status column.
- Resizable details panels in Library and Tracking.
- Player-specific focus mode may collapse or hide the left rail and right
  player panel so video can occupy almost the full window.
- Keyboard shortcuts for search, refresh, play/pause, and navigation.

### Android Mobile and Tablet

Mobile should reuse the hierarchy, not the exact shell:

- Bottom navigation for Home, Library, Downloads, Tracking, and Settings.
- Search entry near the top of Home and Library.
- Continue Watching and Recently Added as vertical feed sections.
- Operational cards move below media content or into a compact status strip.
- Tablet can use a two-pane layout with navigation/detail split.

### Android TV

TV should use a 10-foot version:

- Persistent left rail with large focus states.
- Home hero/resume area at the top.
- Horizontal rails for Continue Watching, Recently Added, and Library slices.
- Server/metadata/download/sync status as a row or panel reachable from Home.
- No dense tables; use cards and details panels optimized for D-pad movement.

## Interaction Notes

- Selecting a media card opens the detail surface; double-click or primary
  action plays/resumes.
- Refresh actions should show explicit in-progress state and leave prior data
  visible until replacement data is ready.
- Failed poster/metadata refresh should be visible but non-blocking.
- External sync should preview changes before writing to providers, then show a
  clear success/failure result.
- Downloads should allow pause/resume/cancel from the home card, with deeper
  controls in Downloads.
- Danmaku cache state should update when an episode is selected and after
  relaunch; stale state should not masquerade as missing cache.

## Accessibility and QA

- Navigation and command icons need tooltips or accessible labels.
- Keyboard tab order should follow rail, header, content, status column.
- TV focus rings must be visible at 1080p and 4K.
- Text must not truncate critical episode names without a tooltip or secondary
  detail surface.
- Posters need deterministic fallback art when metadata is unavailable.
- Loading, empty, partial, and failed states need screenshots before release.
