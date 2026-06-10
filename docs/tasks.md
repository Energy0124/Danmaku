# Tasks

This is the canonical high-level backlog. Detailed design logs remain under
`docs/design/`.

Status legend:

- `[x]` Done
- `[~]` In progress
- `[ ]` Not started

## Active Priorities

- `[~]` Finish desktop playback QA for fullscreen, resize, aspect, 4K media,
  hardware decoding, and multi-display behavior.
- `[~]` Complete Android mobile/tablet library viewport QA at phone and tablet
  sizes.
- `[~]` Complete Android TV safe-area, 1080p/4K, and D-pad focus QA.
- `[~]` Live QA for MyAnimeList/Bangumi mapping, OAuth, sync, conflict handling,
  relaunch behavior, and external list state.
- `[~]` Continue library UI polish where details, title clarity, poster states,
  and focus behavior affect everyday use.

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
- `[ ]` Add release checklist automation for Android APKs and Windows portable
  archives.

## Design Workstreams

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
