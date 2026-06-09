# Android Mobile and TV Library UI Tasks

Source design: [Android Mobile and TV Library UI Review](android-mobile-tv-library-ui-review.md)

Status legend:

- `[x]` Done
- `[~]` In progress
- `[ ]` Not started

## Phase 1 - Mobile Polish

- `[~]` Keep current bottom tab structure while making the Library tab poster-led.
- `[~]` Add reusable fallback poster/artwork component for series and episodes, now with LAN poster image loading.
- `[~]` Replace the series chip rail with poster cards.
- `[~]` Add phone/tablet breakpoint and two-pane tablet layout.
- `[~]` Add compact active-filter status chips below the toolbar.
- `[~]` Convert episode detail into a stable detail surface or bottom-sheet style flow.
- `[~]` Surface matched anime metadata and original file/folder title separately once LAN catalog exposes it.
- `[~]` Show poster/metadata loading state once client-visible metadata status exists.
- `[ ]` Extend mobile instrumentation tests for poster rail, active filters, and detail selection.

## Phase 2 - TV Shell

- `[~]` Move TV library browsing toward a dedicated shell instead of a raw vertical stack.
- `[~]` Add TV visual system constants for background, panels, focus, muted text, and accent.
- `[~]` Replace raw gray library panels with composed TV panel/card surfaces.
- `[~]` Add poster-like fallback cards for Next Up, progress rails, and series, now with LAN poster image loading.
- `[~]` Move PC setup fields into a dedicated PC surface/screen.
- `[~]` Add stable left navigation rail for Home/Library/Search/Favorites/PC.
- `[~]` Make connected-library default focus land on Next Up.
- `[~]` Add focus-aware scaling/border states for TV cards.
- `[ ]` Reduce action-heavy TV rows to one primary card target plus focused quick actions.
- `[ ]` Expand D-pad tests beyond Discover/Refresh.

## Phase 3 - Metadata/Poster Integration

- `[~]` Extend published LAN catalog with poster references.
- `[~]` Extend published LAN catalog with matched anime metadata.
- `[~]` Expose metadata/poster loading or last-refreshed state to clients.
- `[ ]` Use matched anime grouping consistently on mobile and TV.

## QA Targets

- `[~]` Mobile compile and connected Android tests pass.
- `[~]` TV compile and connected Android tests pass.
- `[ ]` Phone portrait layout checked at 360x800 and 412x915.
- `[ ]` Tablet landscape layout checked at 1280x800.
- `[ ]` TV layout checked at 1080p and 4K safe-area spacing.

## Verification Log

- 2026-06-09: `:apps:android-mobile:compileDebugKotlin` passed.
- 2026-06-09: `:apps:android-tv:compileDebugKotlin` passed.
- 2026-06-09: `:apps:android-mobile:assembleDebug` passed.
- 2026-06-09: `:apps:android-tv:assembleDebug` passed.
- 2026-06-09: `:apps:android-mobile:testDebugUnitTest` completed with `NO-SOURCE`.
- 2026-06-09: `:apps:android-tv:testDebugUnitTest` completed with `NO-SOURCE`.
- 2026-06-09: Re-ran mobile/TV compile, assemble, unit-test tasks, and `git diff --check` after mobile detail and TV shell polish; all passed, with unit-test tasks still `NO-SOURCE`.
- 2026-06-09: `:apps:android-mobile:compileDebugKotlin`, `:apps:android-mobile:assembleDebug`, and `git diff --check` passed after adding the tablet two-pane library layout branch; `:apps:android-mobile:testDebugUnitTest` completed with `NO-SOURCE`.
- 2026-06-09: `:apps:android-tv:compileDebugKotlin`, `:apps:android-tv:assembleDebug`, `:apps:android-tv:compileDebugAndroidTestKotlin`, and `git diff --check` passed after adding focus halo states; `:apps:android-tv:testDebugUnitTest` completed with `NO-SOURCE`.
- 2026-06-09: `:apps:android-tv:compileDebugKotlin`, `:apps:android-tv:assembleDebug`, `:apps:android-tv:compileDebugAndroidTestKotlin`, and `git diff --check` passed after extracting the PC connection panel and wiring Next Up default focus; `:apps:android-tv:testDebugUnitTest` completed with `NO-SOURCE`.
- 2026-06-09: LAN catalog now carries cached matched anime metadata, poster paths, and metadata status; local grouping is preserved while mobile/TV episode rows and detail panels show matched titles and poster readiness separately.
- 2026-06-09: `:shared:domain:jvmTest`, `:shared:library-server-core:jvmTest`, `:apps:desktop-windows:desktopTest`, `:shared:library-client:jvmTest`, `:shared:library-client-android:testDebugUnitTest`, `:apps:android-mobile:assembleDebug`, `:apps:android-tv:assembleDebug`, `:apps:android-mobile:compileDebugAndroidTestKotlin`, and `:apps:android-tv:compileDebugAndroidTestKotlin` passed after LAN poster/metadata publication; mobile/TV unit-test tasks remain `NO-SOURCE`.
- 2026-06-09: Android mobile and TV now render authenticated LAN poster images in detail panels, episode rows, Next Up/progress rails, and series cards, with fallback initials and a visible loading pill while poster requests are in flight.
- 2026-06-09: `:apps:android-mobile:compileDebugKotlin`, `:apps:android-tv:compileDebugKotlin`, `:apps:android-mobile:assembleDebug`, `:apps:android-tv:assembleDebug`, `:apps:android-mobile:compileDebugAndroidTestKotlin`, and `:apps:android-tv:compileDebugAndroidTestKotlin` passed after adding mobile/TV poster image rendering; mobile/TV unit-test tasks remain `NO-SOURCE`.
- Connected Android instrumentation tests are still pending a device/emulator run.
