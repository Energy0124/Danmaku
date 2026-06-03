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
- [x] Add LAN playback progress upload and resume seeking.
- [x] Move LAN playback progress uploads into the background playback service.
- [ ] Verify playback and overlays on a physical Android TV device.

## Streaming Verification

- [x] Add same-PC Windows-to-Windows LAN streaming integration coverage that starts
  the local server and exercises paired catalog requests, full-file reads, byte
  ranges, and progress round trips through the public HTTP contract.
- [x] Add LAN-server tests for unauthorized media requests, invalid and
  unsatisfiable byte ranges, large files, and concurrent streams.
- [x] Add an Android client integration test against a live local-server fixture.
- [x] Add a compile-checked Android Media3 instrumentation fixture with a real
  short media asset and loopback HTTP server.
- [x] Execute the Media3 streaming fixture on a workspace-local API 34 emulator.
- [x] Execute the TV D-pad instrumentation suite on a workspace-local API 34
  emulator.
- [x] Test background-service progress uploads while the Android player UI is not
  active.
- [ ] Test pause, seek, episode completion, reconnect, interrupted-network, and
  slow-network behavior.
- [ ] Add LAN subtitle streaming tests when subtitle endpoints are implemented.
- [ ] Exercise PC-to-mobile and PC-to-TV streaming plus cross-device resume on
  physical hardware.

## Server Boundary

- [x] Extract the paired LAN server, index publication, progress API, and discovery
  lifecycle into a reusable `shared:library-server-core` module without Compose
  dependencies.
- [x] Define a shared LAN library-client contract for catalog browsing, stream URL
  generation, progress upload, and resume lookup.
- [x] Add a JVM HTTP transport adapter and loopback client-to-server fixture for
  the planned Windows remote-playback path.
- [x] Reuse the shared LAN client contract from Windows, Android, and Android TV
  while keeping platform transport adapters where required.
- [x] Keep the Windows desktop app starting an embedded library server by default.
- [x] Add Windows paired-server catalog browsing and stream URL selection with
  embedded same-PC defaults.
- [ ] Add Windows player support for browsing and streaming from a paired LAN
  server, including the same-PC integration path.
- [ ] Preserve direct local-file playback on the server host for efficiency.
- [ ] Add an optional headless server executable only after API, settings,
  lifecycle, diagnostics, startup, and firewall behavior are stable.

## Downloads

- [ ] Define authorized-download policy fields in source contracts.
- [ ] Define a platform-independent download manifest.
- [ ] Add multiple Windows library roots with provenance and missing-folder state.
- [ ] Import and incrementally rescan user-selected ani-rss-managed output folders.
- [ ] Add an optional read-only Windows ani-rss adapter for health, subscriptions,
  resolved download folders, completed episode lists, and download status.
- [ ] Add an authenticated ani-rss completion-webhook endpoint that triggers a
  bounded Windows library rescan.
- [ ] Store ani-rss connection credentials securely and redact them from logs.
- [ ] Decide whether ani-rss subscription and download-control actions are allowed
  after the authorized-source policy is approved.
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
