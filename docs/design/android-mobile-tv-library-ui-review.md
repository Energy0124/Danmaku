# Android Mobile and TV Library UI Review

Date: 2026-06-09

## Scope

This review covers the current LAN library UI in:

- `apps/android-mobile/src/main/kotlin/app/danmaku/mobile/MainActivity.kt`
- `apps/android-tv/src/main/kotlin/app/danmaku/tv/MainActivity.kt`

The goal is to define a professional target design for Android mobile/tablet and Android TV before doing another implementation pass. The desktop library has moved toward a poster-led three-pane experience; mobile and TV should now get platform-specific versions of that same product idea.

Mockups:

- [Android mobile library mockup](android-mobile-library-mockup.svg)
- [Android TV library mockup](android-tv-library-mockup.svg)

## Current State

### Android Mobile

The mobile app is a functional Compose Material 3 screen with three bottom tabs: Watch, Library, and Connect. The Library tab already includes a real content model:

- Search, sort, subtitle filter, and favorites filter.
- Next Up, Continue Watching, and Recently Watched rails.
- A series rail based on grouped catalog titles.
- Episode detail panel with previous/next navigation.
- Favorite toggling and watch status display.
- Mini-player bar when playback is active.
- Saved PC connection picker in the Connect tab.

The mobile UI is usable, and the test coverage is decent for behavior. It is not yet a polished media library:

- Series are still chip/list-driven, not poster-led.
- The selected episode detail appears inline in the scroll rather than as a stable secondary surface.
- Browse mode, detail mode, and playback mode are all mixed in one long feed.
- Filters are text-heavy and do not feel like a compact media browser toolbar.
- The UI does not yet distinguish phone from tablet layouts.
- Poster and matched anime metadata are not surfaced on mobile even though desktop now has that direction.

### Android TV

The TV app has the core library behaviors and test coverage:

- Search, sort, subtitle filter, and favorites filter.
- Next Up, Continue Watching, Recently Watched, series, selected series detail, episode detail, and episode list.
- Favorite toggling and progress-aware next-up logic.
- Basic saved PC cards and discovery/refresh controls.
- Basic focus test for Discover PC and Refresh PC library.

The TV UI is still a prototype:

- The entire app is a single vertical column with player, controls, pairing fields, saved PCs, and library sections stacked together.
- Panels use raw `Color.DarkGray` blocks and default TV buttons.
- Text fields are white boxes inside a dark TV surface.
- Browse hierarchy is not optimized for D-pad navigation.
- Focus behavior is only explicitly asserted for two top buttons.
- Rows are text-heavy and action-heavy, with Details/Favorite/Play competing for focus.
- There is no hero/detail area, poster rail, now-playing rail, or stable side navigation.

## Product Direction

Android mobile and Android TV should not mirror desktop one-to-one. They should share domain behavior but use platform-native browsing patterns:

- Mobile: a compact poster-led feed with sticky playback affordance, quick filters, and bottom-sheet style details.
- Tablet: two-pane library with rails on the left and detail/playback context on the right.
- TV: 10-foot UI with a stable left rail, large hero detail panel, horizontal content rails, strong focus rings, and remote-first actions.

The design should remain focused on authorized LAN library streaming from the Windows desktop host.

## Shared Design Principles

1. Poster first, text second.
   Use poster imagery for series and large episode/next-up surfaces. Fallback artwork can use a consistent gradient tile with title initials until posters sync to clients.

2. Keep playback close.
   Continue/resume should be visible near the top. Users should not dig through full episode lists to resume.

3. Separate browse and inspect.
   Episode lists should not be the only way to understand a series. Provide a stable detail area with title, progress, seasons, selected episode, and primary action.

4. Make metadata state explicit.
   If posters or metadata are still loading from the LAN host, show "Syncing metadata" or a subtle skeleton state instead of empty-looking cards.

5. Platform-specific navigation.
   Mobile optimizes thumb reach and scrolling. TV optimizes D-pad focus order, large hit targets, and predictable rail navigation.

## Android Mobile Target

### Phone Layout

Use a single-column Library tab with this order:

1. Compact top app bar
   - Title: Library
   - PC status chip
   - Search icon opens inline search or search sheet
   - Filter icon opens bottom sheet

2. Continue card
   - Large horizontal card for the most relevant Next Up or Continue Watching item
   - Poster thumbnail, series title, episode title, resume/progress
   - Primary button: Resume/Play
   - Secondary action: Details

3. Series poster rail
   - Poster cards, 2:3 ratio
   - Progress badge such as `3/12`
   - Metadata loading badge when relevant

4. Recent rails
   - Continue Watching
   - Recently Watched
   - Favorites when active or non-empty

5. Episode list
   - Dense rows with small poster/thumbnail, title, watch state, subtitle count, favorite affordance

6. Mini player
   - Bottom above nav bar when playback is active
   - Shows poster/thumbnail, title, play/pause, open player

### Tablet Layout

At wider widths, switch to two panes:

- Left/main pane: search, filters, rails, episode list.
- Right pane: selected series/episode detail with poster, progress, season list, actions, and now-playing status.

### Mobile Visual Changes

- Replace series filter chips with poster cards.
- Use compact icon chips for filters: Favorites, Subtitles, Sort.
- Keep active filter chips visible under the toolbar.
- Promote matched anime metadata: show anime title as primary and file/folder title as secondary when they differ.
- Show poster/metadata loading states.
- Prefer one primary action per row; move secondary actions into row trailing icons or detail panel.

## Android TV Target

### 10-Foot Layout

Use a full-screen TV shell:

- Left navigation rail, 220-260 dp wide:
  - Home/Now Playing
  - Library
  - Search
  - Favorites
  - PC

- Top hero/detail area:
  - Large selected series poster/backdrop
  - Series title, selected episode, watch progress, subtitle/danmaku status
  - Primary action: Resume/Play
  - Secondary actions: Episodes, Favorite, Refresh

- Horizontal rails:
  - Next Up
  - Continue Watching
  - Anime Series
  - Recently Watched
  - All Episodes

- Bottom/side context:
  - PC connection state and now playing
  - Pairing should be a dedicated PC screen, not always visible in the main library browse surface

### Focus Rules

- Initial focus:
  - If catalog connected: first Next Up card.
  - If not connected: Discover PC.

- D-pad:
  - Left/right moves within rails.
  - Up/down moves between rail headings and cards.
  - Left from first card moves to nav rail.
  - Back exits detail overlay or returns to rail.

- Focus visuals:
  - Focused card scales slightly, has a bright border, and reveals quick actions.
  - Do not rely on color alone; include scale, border, and shadow/elevation.

### TV Visual Changes

- Replace raw gray columns with TV Material surfaces and focus-aware cards.
- Move connection fields into a PC screen.
- Use poster cards for series and large episode cards for next-up.
- Avoid small text buttons inside every row. TV cards should have one primary click target; detail/actions can appear when focused.
- Keep episode rows large, with 64-80 dp height minimum and clear left/right focus targets.

## Data and API Needs

Mobile and TV can implement the target design with current domain behavior, but these additions would make it significantly better:

- Poster URL or cached poster path in published LAN catalog responses.
- Series-level matched anime metadata in client-visible catalog models.
- Per-item metadata loading/refresh state from desktop host, or at least a last refreshed timestamp.
- A stable client-safe distinction between:
  - matched anime title
  - original folder/file group title
  - episode filename/title
- Optional backdrop image in the future.

Until those exist, clients can use fallback poster tiles and existing `LibrarySeries`/`LibraryMediaItem` metadata.

## Implementation Plan

### Phase 1 - Mobile polish

- Keep the current bottom tab structure.
- Add a poster/fallback artwork component.
- Replace the series chip rail with poster cards.
- Add active filter chip row like desktop.
- Convert episode detail into a more stable card with poster, file-group context, and primary action.
- Add phone/tablet breakpoint using `BoxWithConstraints`.
- Extend mobile tests for poster rail, active filters, and detail selection.

### Phase 2 - TV shell

- Split TV into top-level surfaces: navigation rail, browse area, hero/detail area, PC pairing screen.
- Move PC setup out of the main library browse column.
- Add focus-aware rail cards and episode cards.
- Make Next Up the default focus when connected.
- Expand D-pad tests beyond Discover/Refresh.

### Phase 3 - Metadata/poster integration

- Extend LAN catalog payloads with poster/metadata references.
- Show loading/sync states on mobile and TV.
- Use matched anime grouping consistently with desktop.

## QA Checklist

Mobile:

- Phone portrait at 360x800 and 412x915.
- Tablet landscape at 1280x800.
- Empty catalog, connected catalog, empty filtered result.
- Playback active mini-player.
- Favorites filter and favorite toggle.
- Series selection and episode detail navigation.
- Long anime title, long episode filename, missing posters.

TV:

- 1080p and 4K layouts.
- D-pad focus from launch, connected and disconnected.
- Back behavior from detail/actions/search.
- Overscan-safe spacing.
- Long title truncation.
- Empty, loading, connected, no-results states.
- Remote playback start/resume from Next Up and episode card.

## Recommendation

Mobile is ready for a polish implementation pass now. TV needs a stronger layout refactor before small visual polish will pay off. The TV design should be treated as a dedicated app surface, not a stretched mobile/desktop library.
