# Android TV Client Rewrite Plan

Date: 2026-07-29
Status: Implemented; emulator-qualified, physical-device QA deferred
Target: `apps/android-tv`

## Decision

Rewrite the Android TV presentation layer in place as a single product cutover.
Keep the existing native Android foundation:

- Kotlin and Jetpack Compose for Android with Compose for TV components;
- the dedicated `apps/android-tv` application module;
- Media3 playback;
- shared domain, LAN client, progress, and playback modules; and
- the existing trusted-LAN protocol and server contracts.

Do not move the TV client to Android Views, Kotlin Multiplatform, or Rust. The
current application is already a native Android app; its main problems are
presentation architecture, focus behavior, image loading, state ownership, and
work performed on the Compose/rendering path.

The visual goal is a TV-native adaptation of the Rust desktop consumer
experience, not pixel parity. It should share the same content hierarchy and
calm media-first character while using 10-foot spacing, D-pad navigation, TV
focus feedback, and remote-friendly actions.

## Scope

The cutover delivers the complete consumer TV experience:

- first-run onboarding and PC connection;
- Home;
- Library;
- Search;
- Favorites;
- series and episode details;
- playback and track selection;
- progress synchronization; and
- danmaku display and local viewing preferences.

Desktop/server administration is not part of this cutover. Provider setup,
mapping repair, tracking conflict resolution, downloads, and library
administration remain desktop/web responsibilities. A later TV administration
stage may add capability-gated routes, but this release must not ship empty or
disabled placeholders for them.

The supported performance floor is a budget ARM64 Android TV device with about
2 GB RAM at 1080p. The layout must remain correct at 4K.

## Why a Presentation Rewrite Is Needed

The current client proves the LAN and playback vertical slice, but it does not
yet provide a dependable living-room experience.

### Startup and playback latency

- Playback preparation waits for danmaku resolution before starting Media3 and
  can stall for up to 15 seconds.
- The UI polls a broad player snapshot every 250 ms from the root state, causing
  unrelated browse content to observe playback churn.
- Catalog presentation work is derived directly during composition.

### Rendering and memory pressure

- Poster loading uses raw `HttpURLConnection` and full-stream bitmap decoding
  without a shared bounded cache or size-aware requests.
- Home and Library repeatedly filter, sort, group, and summarize the catalog
  inside composables.
- Fixed danmaku events are filtered and allocated from the complete event list
  in the Canvas draw path.
- A monolithic mutable TV state lets frequent playback changes invalidate a
  much larger part of the UI than necessary.

### Navigation and usability

- The default theme does not define the intended dark TV color scheme, which
  can produce low-contrast text.
- Navigation uses an oversized text-heavy rail and Library contains a second
  competing rail.
- Home cards can discard the selected item and merely redirect to Library.
- Series selection is truncated to the first ten groups.
- Nested, fixed-height lazy containers make navigation and focus recovery
  unpredictable.
- Episode rows expose several adjacent focus targets for one item.
- Focus tests cover only a small part of the route graph.
- A returning user does not get a cached, media-first Home immediately.

### Experience gap

The Rust desktop UI now presents a compact navigation rail, a strong hero,
media rails, clear detail hierarchy, and restrained playback chrome. The TV
client has the same underlying catalog and playback capabilities but still
looks and behaves like a functional engineering surface.

## Product and Interaction Design

### App shell

- Use a custom dark TV theme with explicit background, surface, content,
  secondary-content, accent, focus, success, warning, and error colors.
- Use a compact icon-first navigation rail, approximately 96 dp collapsed and
  240 dp while focused.
- Keep Home, Library, Search, Favorites, and PC as the consumer destinations.
- Give every route a deterministic initial focus target.
- Remember focus per route and restore it when Back returns to that route.
- Left from the first item in a row enters the navigation rail.
- Back closes an overlay/detail layer first, then returns to the previous route;
  it exits the app only from the root route.

### Home

- Use one cinematic hero for the best Continue Watching or Next Up candidate.
- Follow with Continue Watching, Next Up, Recently Added, and Series rails only
  when they contain distinct useful content.
- Make the complete card the single primary focus/click target.
- Open the selected series or episode directly; never redirect generically to
  Library and discard selection.
- Keep connection/refresh status quiet unless action is required.

### Library

- Replace the nested rail/list structure with a series-first, virtualized poster
  grid.
- Remove the ten-series cap.
- Move episode browsing to a dedicated Series Detail route.
- Keep sort and filter controls in one compact toolbar/overlay rather than a
  second navigation rail.
- Offer title, path, newest-added, last-watched, release-year, and episode-count
  ordering, with a release-season/year filter derived from anime metadata.
- Add a dedicated, typed folder-browser route for Rust parity. Browse configured
  `rootLabel` roots when a catalog has several, then nested relative folders and
  files; opening a file resolves to its series detail route.
- Show file-level information only as secondary metadata when it differs from
  the matched title.

### Search and Favorites

- Give Search a dedicated route and focusable TV keyboard flow.
- Debounce query presentation work and cancel obsolete searches.
- Reuse the same poster grid, cards, empty states, and detail routes as Library.
- Give Favorites its own filtered route without maintaining a duplicate UI
  implementation.

### Series and episode detail

- Use a stable hero/detail region with poster/backdrop, title, progress, episode
  context, subtitle/danmaku availability, and a single primary Play/Resume
  action.
- Present episodes in a virtualized list with one focus target per row.
- Put Favorite and infrequent actions in the detail action row or overflow,
  rather than beside every episode row.
- Preserve previous/next navigation and progress-aware Next Up behavior.

### PC and onboarding

- First launch shows a dedicated onboarding path with one primary Discover
  action and an optional manual connection path.
- Returning users see cached Home immediately when possible while connection
  refresh happens in the background.
- The PC route owns discovery, saved connections, manual URL/token entry,
  connection diagnostics, and disconnect.
- Pairing tokens remain protected by the existing storage and logging rules.

### Playback

- Start Media3 as soon as the media request is prepared.
- Resolve and attach danmaku asynchronously. Danmaku failure or timeout must not
  delay or stop video.
- Use edge-to-edge video with a restrained top title band and bottom controls:
  progress, time, play/pause, seek, tracks, danmaku, settings, and next episode.
- Make the playback timeline a focusable remote target; Up enters it from every
  transport button, Left/Right seek in 10-second steps, and Down returns to Play.
- Back hides visible playback controls first, then exits from the controls-hidden
  state so an accidental Back press cannot immediately leave playback.
- Restore focus to the control that opened a track/settings panel.
- Keep playback controls visible after remote interaction and fade them after an
  idle interval.
- Show a Next Episode card but do not introduce new automatic playback behavior
  in this cutover.
- Store local preferences for danmaku enabled state, opacity, font size, speed,
  and maximum occupied screen area.

## Target Architecture

### State ownership

Replace the single mutable TV state with lifecycle-owned, immutable state
streams:

```text
TvSessionUiState
  connection, onboarding, saved PCs, refresh/offline status

TvBrowseUiState
  route-independent catalog data, derived rails/grids, filters, selections

TvPlaybackUiState
  current item, transport, position, duration, tracks, danmaku, chrome state
```

Use Android lifecycle `ViewModel` instances and `StateFlow`. Keep dependencies
explicit through a small application container; do not introduce a DI framework
for this rewrite.

Only the route that needs a state slice should collect it. Position updates must
not recompose Home, Library, Search, or the navigation rail.

### Navigation

Use an explicit, typed route model:

```kotlin
sealed interface TvRoute {
    data object Onboarding : TvRoute
    data object Home : TvRoute
    data object Library : TvRoute
    data object Search : TvRoute
    data object Favorites : TvRoute
    data object Pc : TvRoute
    data class SeriesDetail(val seriesKey: String) : TvRoute
    data class Player(val mediaId: String) : TvRoute
}
```

Add `TvNavigationState` to own the route stack, overlays, and saved focus keys.
Do not add Navigation Compose unless the explicit route stack proves
insufficient; the route graph is small and TV focus restoration needs direct
control.

### Catalog and presentation

Introduce these boundaries:

- `TvLibraryRepository`: cached-first catalog observation, refresh, favorites,
  and progress mutations;
- `TvCatalogCache`: versioned per saved connection and safe to discard;
- `TvBrowsePresenter`: derives Home rails, grids, filters, and detail models on
  `Dispatchers.Default`; and
- immutable, stable UI models containing only values required by composables.

Catalog grouping, sorting, search, Next Up, Continue Watching, and summary
calculation must happen outside composition. Recompute them only when their
source catalog, progress, favorites, or filters change.

The cache stores client-safe catalog and progress presentation data. It does not
change the LAN protocol, duplicate server authority, or cache credentials in
plain text. Cache schema changes use a version bump and discard/reload policy;
no user migration flow is required.

### Images

Replace the hand-written poster downloader with Coil for Compose:

- one application-scoped image loader;
- bounded memory and disk caches suitable for a 2 GB device;
- authenticated requests using the existing LAN authorization behavior;
- requests sized to the rendered card;
- deterministic fallback artwork and error states; and
- stable cache keys based on server identity plus poster identity/version.

Repeated navigation through a rail must not repeatedly download or decode the
same poster.

### Playback state

Drive transport, duration, tracks, and playback status from Media3 listener
callbacks. Sample only position while the Player route is visible. Keep progress
upload cadence separate from UI state cadence.
The shared Media3 service checkpoints positive positions every five seconds and
on pause, completed seek, and playback completion; TV also checkpoints the old
target before an episode switch and performs a final save-and-refresh on exit.
Resume seeks before Play when server progress is at least 10 seconds and leaves
at least 30 seconds remaining.

Change playback preparation into two independent jobs:

1. prepare and start media;
2. resolve, prepare, and attach danmaku.

Cancellation must follow the selected media item so a late danmaku response
cannot attach to a newer playback session.

### Danmaku

Create `PreparedDanmakuTimeline` outside the Canvas/render loop. It should:

- sort and normalize events once;
- index scrolling and fixed events by time window;
- expose only currently active events for a playback position;
- reuse layout/scheduling structures where practical; and
- enforce an explicit simultaneous-comment/screen-area budget.

The draw path must not scan or allocate from the complete event list each
frame.

## Implementation Sequence

The work is developed behind an internal replacement entry point or build-time
flag, but ships as one cutover. Do not release a mixture of old and new TV
routes.

### 1. Foundation and measurement

- Add benchmark fixtures for a 6,000-item catalog and a 10,000-event danmaku
  stream with 500 simultaneous comments.
- Record current cold/warm startup, catalog presentation, D-pad frame timing,
  playback start, image traffic, and danmaku frame timing.
- Add the custom TV theme and shared focus/card primitives.
- Define typed routes, navigation state, and immutable UI state contracts.

Exit: the replacement shell launches with deterministic focus and benchmark
baselines are recorded.

### 2. Data and state split

- Add the application container and lifecycle ViewModels.
- Add `TvLibraryRepository`, versioned `TvCatalogCache`, and
  `TvBrowsePresenter`.
- Move catalog derivation off the composition thread.
- Use cached-first startup followed by background refresh.
- Replace root snapshot polling with Media3 listener state and Player-only
  position sampling.

Exit: Home can render cached data without a live refresh, browse routes do not
recompose for each playback tick, and large-catalog presenter tests pass.

### 3. Consumer browse routes

- Implement onboarding/PC and the compact navigation shell.
- Implement Home hero and distinct content rails.
- Implement the virtualized Library grid.
- Implement dedicated Search and Favorites routes.
- Implement Series Detail and episode list.
- Replace poster loading with the shared Coil image loader.
- Add English and Traditional Chinese strings for every new visible label.

Exit: every consumer route is reachable by D-pad, selection is preserved across
route changes, and Back/focus restoration tests pass.

### 4. Playback and danmaku

- Make media start independent from danmaku resolution.
- Split playback state from browse/session state.
- Implement the redesigned playback chrome, panels, and focus restoration.
- Add persisted local danmaku preferences.
- Add prepared/indexed danmaku timeline rendering.
- Add Next Episode presentation without new autoplay.

Exit: slow/failed danmaku cannot block video, playback has no focus traps, and
the danmaku stress fixture remains within the frame/memory budget.

### 5. Cutover and removal

- Switch the release entry point to the replacement experience.
- Preserve saved connection and favorite storage formats.
- Discard/rebuild incompatible catalog caches automatically.
- Remove old routes, monolithic state, raw poster loader, root polling, and
  obsolete UI tests.
- Update screenshots, current-state notes, architecture documentation, and the
  canonical task log.

Exit: there is one shipped TV experience and no dormant legacy UI path.

### 6. Release qualification

- Run unit, Compose, screenshot, benchmark, and connected playback suites.
- Test English and `zh-TW` at 1080p and 4K.
- Test a physical budget-class Android TV device with a real LAN library.
- Run a 100-action remote traversal covering every route and player overlay.
- Capture final visual references for Home, Library, Series Detail, Search,
  onboarding/PC, and playback controls.

Exit: all acceptance gates below pass and the real-device results are recorded.

## Test Plan

### Unit tests

- Route stack, Back rules, route focus memory, and disconnected redirects.
- Cache versioning, per-server isolation, stale/offline behavior, and invalid
  cache recovery.
- Home rail de-duplication and ordering.
- Library grouping without truncation, filtering, sorting, and debounced search.
- Next Up/Continue Watching derivation and progress changes.
- Playback preparation cancellation and danmaku-late-result rejection.
- Prepared danmaku timeline windows, fixed events, collision/screen-area caps,
  and seek behavior.

### Compose and instrumentation tests

- Initial focus for connected, cached/offline, and first-run states.
- Complete D-pad traversal of Home, Library, Search, Favorites, PC, Series
  Detail, Player, track panels, and danmaku settings.
- Left-edge rail entry, Back behavior, and focus restoration.
- Single-target media cards and episode rows.
- Remote keyboard/query flow and search cancellation.
- Playback starts when danmaku is slow or fails.
- English and `zh-TW` text fit at 1080p and 4K safe areas.

### Performance and visual tests

- Add Macrobenchmark coverage and a Baseline Profile for startup, Home, Library
  browsing, detail opening, and Player entry.
- Use deterministic catalog, poster, progress, and danmaku fixtures.
- Capture reference screenshots at 1920x1080 and 3840x2160.
- Track poster request/decode counts while revisiting rails.
- Track slow frames and memory during continuous D-pad scrolling and danmaku
  playback.

## Acceptance Gates

On the budget-device target:

- cached Home is usable within 1.5 seconds of launch;
- media click to first video frame is under 2 seconds with the local fixture;
- slow or failed danmaku adds no delay to first video frame;
- continuous D-pad browsing produces no more than 5% slow frames in the agreed
  Macrobenchmark scenario;
- a 100-action route traversal has no focus trap or lost-focus state;
- the 10,000-event/500-simultaneous danmaku fixture remains responsive and
  respects the configured screen-area cap;
- revisiting unchanged poster rails causes no repeated network downloads;
- the 6,000-item catalog has no artificial series truncation;
- English and `zh-TW` layouts pass at 1080p and 4K; and
- saved PCs, favorites, playback progress, subtitles/audio selection, and
  danmaku behavior continue to work against the existing LAN server.

Required build verification:

```powershell
.\gradlew.bat --no-daemon :apps:android-tv:assembleDebug
.\gradlew.bat --no-daemon :apps:android-tv:compileDebugAndroidTestKotlin
git diff --check
```

Connected tests, emulator screenshots, and physical-device checks remain
explicit supervised QA steps.


## Implementation Record

The single-cutover rewrite landed on 2026-07-29. The shipped TV path now uses:

- typed routes with saved focus and explicit left-edge navigation;
- separate session, browse, navigation, and playback state owners;
- versioned, per-server cached-first catalogs and presentation derivation
  outside composition;
- bounded, size-aware Coil poster loading;
- the redesigned onboarding/PC, Home, Library, Search, Favorites, Series
  Detail, and Player routes in English and `zh-TW`;
- listener-driven Media3 playback that starts before asynchronous danmaku
  resolution; and
- `PreparedDanmakuTimeline` indexing with bounded per-frame work.

The replaced TV shell, raw poster path, monolithic route state, and obsolete
tests were removed. Unit and instrumentation coverage now includes the
6,000-item catalog, 10,000-event/500-simultaneous danmaku fixture, cache
version/isolation/recovery, poster reuse, per-pass scrolling entrance admission,
late danmaku rejection, deterministic
initial focus, left-edge rail entry, search input, and one-target episode rows.
The `apps/android-tv-benchmark` module supplies startup, route, Player, and
100-action Macrobenchmark journeys, and the app ships a Baseline Profile.
The emulator-only benchmark suite passed all three tests on 2026-07-29.
Across five samples, cold-start initial display was 944 ms median; the
100-action route/player traversal measured 20.4/34.9/37.4 ms frame CPU
duration at P50/P90/P95 and 92,784 KiB median peak anonymous RSS. These are
regression baselines, not substitutes for the deferred budget-device gate.

The 1080p/4K emulator wrapper rejects non-emulator serials, runs connected
tests, and captures English/`zh-TW` route references only while the app is
foregrounded. A real budget-class TV and real-LAN playback pass remains
explicitly deferred; it is not a completion blocker for this implementation.

A 2026-07-31 review follow-up made the dependency container application-scoped
so activity recreation cannot split retained view models from a new navigator.
Catalog refresh now uses a monotonic request generation: connection edits,
selection, fixture installation, or removal immediately clear loading and make
older success/failure callbacks inert. Home presentation also records whether
its hero came from Continue Watching or Next Up so labels and actions match the
actual playback state. Instrumentation and presenter regressions cover these
cases.
## Rollout and Compatibility

- Product rollout: single cutover.
- Engineering rollout: replacement entry point/flag until qualification.
- LAN protocol and server schema: unchanged.
- Saved connection and favorite state: preserved.
- Catalog cache: versioned and disposable.
- Old UI: removed at cutover, not retained as a user-facing fallback.
- Failure fallback: cached/offline browse where available, with a direct route
  to PC connection; playback errors remain item-scoped.

## Risks and Controls

- **Large one-time change:** keep milestones independently testable and require
  exit criteria before moving to cutover.
- **Focus regressions:** treat route focus maps and Back rules as state with
  unit and instrumentation coverage, not incidental modifier behavior.
- **Low-memory devices:** bound image caches, size requests, virtualize grids and
  lists, and include process-memory measurements in qualification.
- **Stale cached catalog:** show refresh/offline status, keep the server
  authoritative, and replace cache contents atomically.
- **Late asynchronous results:** key catalog, image authorization, playback, and
  danmaku work by server/session/media identity and reject stale results.
- **Connection races:** return an explicit applied/stale catalog outcome before
  navigation, and bind asynchronous progress updates to their source PC profile.
- **Playback attachment and seeks:** queue Play while Media3 attaches, expose the
  connecting state on browse routes, and reset only the danmaku entrance gate on
  playback discontinuities while preserving it during ordinary position samples.
- **Visual drift from desktop:** use the Rust mockups for hierarchy and tone,
  while TV focus, spacing, and control density remain platform-specific.

## Estimate

Expected implementation effort is six to nine engineer-weeks for one engineer,
including automated coverage and supervised emulator/device qualification.
The estimate assumes no LAN protocol changes and no addition of desktop/server
administration to the consumer cutover.

## Future TV Administration Stage

After the consumer cutover is stable, evaluate capability-gated TV routes for
safe desktop/server administration. Candidate areas are server health, library
refresh, metadata attention, and download status. Provider credentials,
mapping conflict resolution, and destructive or high-density workflows should
remain on desktop/web unless a remote-first design proves both safe and usable.
