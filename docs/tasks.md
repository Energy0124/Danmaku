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
  (`zh-TW`) across desktop, Android mobile, and Android TV UI chrome.
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
  and server-event state now have the first remembered state object.

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
