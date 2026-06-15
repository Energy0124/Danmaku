# Tasks

This is the canonical high-level backlog. Detailed design logs remain under
`docs/design/`.

Status legend:

- `[x]` Done
- `[~]` In progress
- `[ ]` Not started

## Active Priorities

- `[~]` Align the desktop Home page and shared app shell with the new
  header/navigation/status-column design direction.
- `[~]` Extend the desktop page implementation toward the Library, Player,
  Downloads, Tracking, Settings, and secondary-surface mockups.
- `[~]` Ensure the desktop Player can hide both sidebars so video uses almost
  the full window in focus mode.
- `[~]` Add first-pass localization support for English and Traditional Chinese
  (`zh-TW`) across desktop, Android mobile, and Android TV UI chrome; desktop
  strings now use a DSL-backed holder so the growing string set does not hit
  JVM method-signature limits, and the first desktop shell/settings/playback
  chrome strings plus Home/Library placeholder/count, Home dashboard body,
  Library workspace navigation/filter, Library import/inspector action, Library
  metadata/playback inspector, and Downloads queue/import strings are backed by
  Compose Multiplatform generated resources under `commonMain/composeResources`.
- `[~]` Finish desktop playback QA for fullscreen, resize, aspect, 4K media,
  hardware decoding, and multi-display behavior.
- `[~]` Complete Android mobile/tablet library viewport QA at phone and tablet
  sizes.
- `[~]` Complete Android TV safe-area, 1080p/4K, and D-pad focus QA.
- `[~]` Live QA for MyAnimeList/Bangumi mapping, OAuth, sync, conflict handling,
  relaunch behavior, and external list state.
- `[~]` Continue library UI polish where details, title clarity, poster states,
  and focus behavior affect everyday use.
- `[~]` Continue decomposing desktop `Main.kt` into focused shell, tab,
  settings, player, library, and shared UI modules while preserving behavior.
- `[~]` Introduce a desktop shell state/action boundary so orchestration moves
  out of feature rendering after the first file-ownership split; diagnostics
  and server-event state plus navigation/search/language state now have
  remembered state objects, and playback session/progress flags have a
  remembered state object. Settings/preferences/provider status also now have
  a remembered state object, and library/catalog/progress/indexing/sync flags
  are in a remembered state object. Settings/provider/cache actions are also
  extracted into a typed action object, and generic playback session/progress
  actions are in a playback action object. Local playback preparation and
  dandanplay overlay/cache actions are also split into a typed action object.
  Library root, metadata, favorite, external mapping/search, and tracking sync
  actions are now split into a typed library action object. Download queue
  refresh/remove/open actions are also split into a typed action object.
- `[~]` Reduce shell/lifecycle/window wiring in `DesktopShell.kt`; UI
  files, state holders, settings actions, playback actions, local
  playback/danmaku actions, library actions, and download actions are now
  split, playback command callbacks are delegated, and stale monolith imports
  have been trimmed. `DesktopShell.kt` is now below the 1,000-line milestone;
  remaining work is about coupling, not only file length.

## Next Engineering Work

- `[ ]` Decide whether external anime sync failures should be persisted in the
  desktop database or kept session-only.
- `[ ]` Add durable external list entry fetch/readback so sync plans can compare
  current provider state before writing.
- `[ ]` Add user-facing danmaku offset/filter controls over the real playback
  clock.
- `[ ]` Define authorized download source contracts and queue execution
  behavior.
- `[ ]` Add QA scripts or checklists for Windows fullscreen/4K/hardware decode.
- `[ ]` Add localization QA checks for English and `zh-TW` screenshots on
  dense desktop, mobile, and TV surfaces.
- `[ ]` Continue migrating desktop `DesktopStrings` into Compose Multiplatform
  resources by feature slice, continuing with provider settings placeholders,
  danmaku panel labels, paired-library labels, and the desktop locale-owner
  strategy.
- `[ ]` Add release checklist automation for Android APKs and Windows portable
  archives.
- `[x]` Move the remaining desktop player surface out of `Main.kt`; playback
  tab, shortcut, overlay, panel, constants, and cycling helpers are now split
  into focused desktop playback files.
- `[x]` Move the desktop library workspace and shared library row/card
  composables out of `Main.kt`; library UI is now split into tab, workspace,
  lists, inspector, and helper files.
- `[x]` Move desktop settings, server dashboard, and cache-management surfaces
  out of `Main.kt`; settings UI is now split into tab, danmaku, dialogs, and
  provider-card files.

## Design Workstreams

- [Home and app shell UI tasks](design/home-and-app-shell-ui-tasks.md)
- [Desktop pages UI tasks](design/desktop-ui-pages/desktop-pages-ui-tasks.md)
- [Android mobile and TV library UI tasks](design/android-mobile-tv-library-ui-tasks.md)
- [External anime mapping and tracking tasks](design/external-anime-tracking-tasks.md)

## Quality Gates

- Shared domain changes should include common tests.
- LAN server/client behavior should include JVM tests and Android adapter tests
  where relevant.
- Desktop storage/provider/native changes should include desktop tests.
- TV UI changes should include D-pad/focus instrumentation coverage where
  practical.
- Android playback changes should run connected tests on a real device or
  emulator before release.

## Standard Verification

```powershell
cargo fmt --all --check
cargo test --workspace
.\gradlew.bat --no-daemon :shared:domain:jvmTest :shared:library-client:jvmTest :shared:library-server-core:jvmTest :apps:desktop-windows:desktopTest :shared:library-client-android:testDebugUnitTest :shared:player-android-media3:assembleDebugAndroidTest :apps:android-mobile:assembleDebug :apps:android-tv:assembleDebug
```

Connected Android checks:

```powershell
.\gradlew.bat --no-daemon :shared:player-android-media3:connectedDebugAndroidTest
.\gradlew.bat --no-daemon :apps:android-mobile:connectedDebugAndroidTest
.\gradlew.bat --no-daemon :apps:android-tv:connectedDebugAndroidTest
```
