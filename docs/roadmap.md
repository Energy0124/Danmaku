# Roadmap

## Current Position

Phase 0 is complete. Phase 1 is active and has a working Windows vertical
slice: the Compose Desktop shell, shared playback contract, packaged
Rust/JNA/libmpv command executor, stable SwingPanel-backed native video host,
player-first Playback tab, and packaged GUI smoke launch are implemented. The
shared danmaku lane scheduler is in place, and Windows can attach indexed
sidecars plus generated or dandanplay-fetched ASS danmaku tracks through mpv.
Broad resize, fullscreen, hardware-decoding, 10-bit, and 4K validation remains
active Phase 1 work.

The Windows shell indexes anime folders and streams indexed files over HTTP
byte ranges to Android mobile and Android TV Media3 clients. The clients
discover Windows servers over LAN UDP announcements and require the pairing
code displayed by the Windows app. The desktop catalog is persisted in
SQLDelight SQLite storage and incrementally refreshed. Android and TV playback
run in a shared MediaSession foreground service. A pinned, hash-verified LGPL
libmpv bundle is approved for Windows packaging, and the Compose distributable
copies the Rust bridge and installed approved libmpv DLL into the app directory
by default.
The desktop database and paired LAN server also persist per-episode playback
progress. Android and TV player screens use that transport for five-second
uploads from the background playback service and resume seeking.
The paired HTTP server, progress-store contract, and UDP discovery announcer
have been extracted into a Compose-free JVM module while remaining embedded in
the Windows desktop app by default.
The portable LAN library-client contract and progress-sync policy have also
been extracted for reuse by Android, Android TV, and Windows. A JVM HTTP
adapter and live loopback fixture cover the planned Windows remote-playback
path. The desktop shell now browses a paired catalog and prepares stream URLs
through that adapter, then hands those sources to the native mpv command
executor.

An experimental macOS desktop path now reuses the same Compose shell, catalog,
LAN server/client, SQLDelight storage, and Rust/JNA mpv command bridge. It
builds `libplayer_windows_mpv.dylib` and can run through `run-macos.sh` when a
local libmpv dylib is available. macOS video is currently mpv-managed rather
than embedded in the Compose surface, and release-grade libmpv packaging still
needs a separate audit.

The dedicated TV shell requests an explicit `Discover PC` launch focus and has
API 34 emulator-verified Compose instrumentation coverage for its initial
remote-navigation path. Physical TV execution remains part of hardware
validation.

## Phase 0: Foundation

- Record architecture and contribution rules.
- Build and test a minimal Rust danmaku timeline index.
- Bootstrap Gradle with a committed wrapper.
- Add the shared Kotlin domain module.

## Phase 1: Windows Playback Vertical Slice

- Create a Compose Desktop shell.
- Integrate libmpv behind a playback contract.
- Play local files and remote HLS streams.
- Render sidecars and danmaku over playback through mpv subtitle/ASS tracks.
- Keep the player-first Windows UI minimal over video, especially in
  fullscreen.
- Validate resize, fullscreen, seeking, hardware decoding, 10-bit, and 4K
  playback.

## Phase 2: Android And Android TV Playback

- Create Android mobile and Android TV apps.
- Implement the shared playback contract with Media3 ExoPlayer.
- Add in-process MediaSession integration.
- Add a background MediaSession service.
- Build TV-specific screens, focus states, and D-pad navigation.
- Verify overlay synchronization on typical TV hardware.

## Phase 3: Library And Offline Downloads

- Add SQLDelight schema and repositories.
- Persist and manage the local-media index.
- Add trusted-device pairing and discovery for LAN streaming.
- Extract a reusable LAN library-server core while keeping it embedded in the
  Windows desktop app by default.
- Add a shared LAN library-client contract for Windows, Android, and Android TV.
- Add an optional headless server executable after the API, settings, lifecycle,
  diagnostics, and firewall behavior stabilize.
- Add queueing, pause, resume, retry, deletion, and disk quota behavior.
- Use Media3 DownloadService on Android and TV.
- Implement the Windows desktop download engine in Rust.

## Phase 4: Sources And Product Features

- Define authorized source plugin contracts.
- Complete subtitle and audio-track selection on Windows after Android and TV.
- Add danmaku filters, density controls, and keyword blocking.
- Add watch progress, favorites, playlists, and import/export.

## Phase 5: Expansion

- Promote macOS from experimental desktop support after embedded video
  composition and libmpv packaging are audited.
- Reuse the desktop app for Linux.
- Add iOS and iPadOS using shared KMP logic and AVPlayer.
- Build a streaming-first React and TypeScript web client.
- Add optional sync, accounts, live rooms, and moderation.
