# Task Backlog

The backlog is ordered. Complete the earliest unblocked task before expanding
the platform surface.

## Active

- [x] Record the initial architecture and platform priorities.
- [x] Add a dependency-free Rust workspace and timeline index.
- [x] Bootstrap Gradle 9.4.1 with a committed wrapper.
- [x] Add a minimal shared Kotlin domain module.
- [ ] Decide the Windows libmpv distribution strategy.
- [ ] Build a Windows libmpv playback spike.

## Next

- [ ] Define the Kotlin playback contract.
- [ ] Build a Compose Desktop shell.
- [ ] Add local-file playback on Windows.
- [ ] Add a synthetic danmaku overlay demo.
- [ ] Measure overlay behavior with a large generated timeline.
- [ ] Add seek and playback-rate test cases.

## Android And TV

- [ ] Create the Android mobile application.
- [ ] Create the dedicated Android TV application.
- [ ] Add Media3 ExoPlayer adapters.
- [ ] Add MediaSession integration.
- [ ] Add TV D-pad and focus-navigation tests.
- [ ] Verify playback and overlays on a physical Android TV device.

## Downloads

- [ ] Define authorized-download policy fields in source contracts.
- [ ] Define a platform-independent download manifest.
- [ ] Add Media3 DownloadService for Android and TV.
- [ ] Add the Rust desktop download engine.
- [ ] Test interrupted transfers, retries, and disk-space failures.

## Later

- [ ] Add SQLDelight library storage.
- [ ] Add authorized source plugins.
- [ ] Add macOS and Linux desktop packaging.
- [ ] Add iOS and iPadOS.
- [ ] Add the web streaming client.
- [ ] Add optional backend services.
