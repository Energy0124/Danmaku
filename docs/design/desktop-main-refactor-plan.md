# Desktop Main Refactor Plan

Last updated: 2026-06-11.

## Goal

Reduce the desktop entrypoint from a monolithic Compose file into focused
shell, state, tab, and shared UI modules without changing runtime behavior.
The target shape is:

- `Main.kt` owns only application/window startup and root invocation.
- The desktop shell owns cross-surface orchestration through an internal
  state/action boundary.
- Feature tabs own their own rendering and local presentation helpers.
- Shared UI primitives are reusable but still desktop-internal.

No new library or UI framework is required for this refactor. The main design
problem is ownership: the current desktop shell mixes dependency setup,
long-lived state, side effects, and feature rendering.

## Phase 1: File Ownership Split

Status: in progress.

Move top-level rendering functions out of `Main.kt` by surface while preserving
all behavior and signatures where practical:

- `DesktopShell.kt` for root shell composition and current orchestration.
- `DesktopHomeTab.kt`, `DesktopTrackingTab.kt`, `DesktopPlaybackTab.kt`,
  `DesktopLibraryTab.kt`, `DesktopDownloadsTab.kt`, and
  `DesktopSettingsTab.kt` for feature surfaces.
- `DesktopSharedUi.kt` for cards, rows, status pills, diagnostic panels, and
  remaining shared rows.

Acceptance:

- `Main.kt` stays below 250 lines.
- Desktop compile and desktop tests pass.
- No UI behavior, storage key, server, playback, or provider behavior changes.

## Phase 2: Shell State And Actions

Status: started.

Introduce an internal state/action boundary after the mechanical split
compiles:

- `DesktopShellState` exposes renderable state for selected tab, shell search,
  diagnostics, library state, playback state, download queue, settings, and
  server metadata.
- `DesktopShellActions` exposes user intents such as navigation, scan, play,
  favorite, sync, settings save/test, cache cleanup, and server checks.
- `rememberDesktopShellState(...)` owns remembered stores, controllers,
  coroutine launches, effects, and runtime lifecycle.
- `DesktopShell` becomes a thin bridge from window inputs to
  `DesktopShellContent(state, actions)`.

Initial slice:

- `DesktopShellDiagnosticsState` owns diagnostic log, mpv command log, server
  events, file logging, and trim behavior.
- `DesktopShell` delegates diagnostic/server-event actions to the remembered
  state object while the remaining shell state is still local.

Acceptance:

- Side effects are not launched from feature tab composables.
- Feature tabs receive data plus callbacks rather than reaching into stores or
  runtimes.
- `DesktopShell.kt` drops below 1,000 lines after the full state/action pass.

## Phase 3: Feature File Follow-Up

Status: next.

Further split oversized feature files once state/actions make their boundaries
clear:

- Split library workspace, inspector, external mapping, metadata match dialog,
  and progress/list rows into separate library-focused files.
- Split playback overlays, controls, shortcut handling, and right-panel content
  if `DesktopPlaybackTab.kt` remains above the target size.
- Split provider cards, server dashboard, cache manager, and validation helpers
  from settings if `DesktopSettingsTab.kt` remains above the target size.

Completed library split:

- `DesktopLibraryTab.kt` now keeps the top-level tab wrapper.
- `DesktopLibraryWorkspace.kt` owns workspace navigation, imports, toolbar, and
  external sync preview.
- `DesktopLibraryLists.kt` owns progress/list surfaces.
- `DesktopLibraryInspector.kt` owns inspector, mapping, metadata-match, and
  compact inspector rows.
- `DesktopLibraryUiHelpers.kt` owns shared library keyboard, poster,
  metadata-readiness, and progress/download helper functions.

Completed settings split:

- `DesktopSettingsTab.kt` now owns the settings rail, profile tab, language
  card, and section routing.
- `DesktopSettingsDanmaku.kt` owns danmaku display controls and shared settings
  validation/status rows.
- `DesktopSettingsDialogs.kt` owns server dashboard, cache manager,
  confirmation dialog, and range/URL validation helpers.
- `DesktopSettingsProviders.kt` owns dandanplay, MyAnimeList, and Bangumi
  provider cards.

Completed playback split:

- `DesktopPlaybackTab.kt` now owns the tab-level player composition.
- `DesktopPlaybackShortcuts.kt` owns desktop player shortcut key mapping.
- `DesktopPlaybackOverlays.kt` owns empty, navigation, top/bottom, and
  preparation-status overlays.
- `DesktopPlaybackPanels.kt` owns the right panel, danmaku controls, focus
  restore button, and shared playback action buttons.

Completed shell chrome split:

- `DesktopShellChrome.kt` owns the app header, navigation rail, rail item, and
  now-playing rail card.
- `DesktopHomeTab.kt` now owns Home content only.

Acceptance:

- Prefer files below roughly 700 lines unless a single cohesive surface
  clearly justifies more.
- Keep all moved declarations desktop-internal.

## Verification

Run after every phase:

```powershell
.\gradlew.bat --no-daemon :apps:desktop-windows:compileKotlinDesktop
.\gradlew.bat --no-daemon :apps:desktop-windows:desktopTest
git diff --check
```

When state/actions move behavior-adjacent logic, also run the relevant shared
JVM tests before commit.
