# Android Mobile and TV Library UI Tasks

Source design: [Android Mobile and TV Library UI Review](android-mobile-tv-library-ui-review.md)

Status legend:

- `[x]` Done
- `[~]` In progress
- `[ ]` Not started

## Phase 1 - Mobile Polish

- `[~]` Keep current bottom tab structure while making the Library tab poster-led.
- `[~]` Add reusable fallback poster/artwork component for series and episodes.
- `[~]` Replace the series chip rail with poster cards.
- `[ ]` Add phone/tablet breakpoint and two-pane tablet layout.
- `[~]` Add compact active-filter status chips below the toolbar.
- `[ ]` Convert episode detail into a stable detail surface or bottom-sheet style flow.
- `[ ]` Surface matched anime metadata and original file/folder title separately once LAN catalog exposes it.
- `[ ]` Show poster/metadata loading state once client-visible metadata status exists.
- `[ ]` Extend mobile instrumentation tests for poster rail, active filters, and detail selection.

## Phase 2 - TV Shell

- `[~]` Move TV library browsing toward a dedicated shell instead of a raw vertical stack.
- `[~]` Add TV visual system constants for background, panels, focus, muted text, and accent.
- `[~]` Replace raw gray library panels with composed TV panel/card surfaces.
- `[~]` Add poster-like fallback cards for Next Up, progress rails, and series.
- `[ ]` Move PC setup fields into a dedicated PC surface/screen.
- `[ ]` Add stable left navigation rail for Home/Library/Search/Favorites/PC.
- `[ ]` Make connected-library default focus land on Next Up.
- `[ ]` Add focus-aware scaling/border states for TV cards.
- `[ ]` Reduce action-heavy TV rows to one primary card target plus focused quick actions.
- `[ ]` Expand D-pad tests beyond Discover/Refresh.

## Phase 3 - Metadata/Poster Integration

- `[ ]` Extend published LAN catalog with poster references.
- `[ ]` Extend published LAN catalog with matched anime metadata.
- `[ ]` Expose metadata/poster loading or last-refreshed state to clients.
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
- Connected Android instrumentation tests are still pending a device/emulator run.
