# Task Backlog

The backlog is ordered. Complete the earliest unblocked task before expanding
the platform surface.

## Current Focus

The checkout is `S:\Projects\Danmaku` on branch
`codex/windows-playback-foundation`.

Native render integration requires an audited Windows libmpv DLL bundle. The
shared scrolling danmaku lane scheduler and synthetic Compose overlay demo are
implemented. The Windows app also indexes local anime folders and streams
indexed files over the LAN. Android mobile and TV clients can browse the
catalog and pass selected streams to Media3.

## Active

- [x] Record the initial architecture and platform priorities.
- [x] Add a dependency-free Rust workspace and timeline index.
- [x] Bootstrap Gradle 9.4.1 with a committed wrapper.
- [x] Add a minimal shared Kotlin domain module.
- [x] Decide the Windows libmpv distribution strategy.
- [x] Add a dependency-free Windows libmpv dynamic-loader spike.
- [ ] Verify the dynamic-loader probe against an audited libmpv DLL.
- [ ] Build a Windows libmpv playback spike.
- [x] Add a shared scrolling danmaku lane scheduler with collision-aware tests.
- [x] Add recursive Windows anime-folder indexing.
- [x] Add a paired trusted-LAN catalog server with HTTP byte-range streaming.
- [x] Remember the selected Windows anime folder and add one-click rescanning.
- [x] Persist the Windows catalog in SQLDelight and reuse unchanged rows.

## Next

- [x] Define the Kotlin playback contract.
- [x] Build a Compose Desktop shell.
- [ ] Add local-file playback on Windows.
- [x] Add a synthetic danmaku overlay demo.
- [x] Add bounded visible-window queries and generated-track scheduler tests.
- [ ] Measure overlay behavior with a large generated timeline.
- [ ] Add seek and playback-rate test cases.

## Android And TV

- [x] Create the Android mobile application.
- [x] Create the dedicated Android TV application.
- [x] Add a shared Media3 ExoPlayer adapter.
- [x] Browse the Windows LAN catalog and stream selected episodes.
- [x] Add in-process MediaSession integration.
- [x] Add trusted-LAN pairing codes.
- [x] Add background MediaSession service integration.
- [x] Add LAN server discovery.
- [x] Add TV D-pad and focus-navigation instrumentation tests.
- [x] Add foreground LAN playback progress upload and resume seeking.
- [ ] Move LAN playback progress uploads into the background playback service.
- [ ] Verify playback and overlays on a physical Android TV device.

## Downloads

- [ ] Define authorized-download policy fields in source contracts.
- [ ] Define a platform-independent download manifest.
- [ ] Add Media3 DownloadService for Android and TV.
- [ ] Add the Rust desktop download engine.
- [ ] Test interrupted transfers, retries, and disk-space failures.

## Later

- [x] Add initial SQLDelight library storage.
- [x] Extend SQLDelight storage for playback progress.
- [ ] Extend SQLDelight storage for settings and downloads.
- [ ] Add authorized source plugins.
- [ ] Add macOS and Linux desktop packaging.
- [ ] Add iOS and iPadOS.
- [ ] Add the web streaming client.
- [ ] Add optional backend services.
