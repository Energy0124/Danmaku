# Rust Server And Windows Player Migration Plan

Last updated: 2026-07-08.

## Direction

Migrate the library server core and the Windows player client to Rust-only
implementations, retiring the JVM server modules and the Compose desktop app
once their Rust replacements pass the existing QA gates. Android mobile and
Android TV stay Kotlin/Media3 and must not notice the server changed.

This is a strangler migration along the trusted-LAN protocol boundary:

1. Freeze and document the LAN HTTP + UDP discovery protocol as the contract.
2. Build a Rust headless library server that passes the existing headless QA
   suite and serves the existing Android/web clients unchanged.
3. Switch the Compose desktop app to run the Rust server as a local sidecar,
   retiring the embedded JVM server path while the Kotlin UI keeps working.
4. Build a Rust Windows player client focused on playback and library
   browsing; admin/management workflows move to the web UI.
5. Retire the JVM server modules and the Compose desktop app; update docs,
   CI, and release tooling.

Each phase ships something usable on its own. The Kotlin implementations
stay runnable until the matching Rust replacement passes its parity gate.

## Accepted Decisions

- **Server stack:** tokio + axum + rusqlite + serde + reqwest. No gRPC; the
  existing HTTP JSON + byte-range contract stays.
- **Video integration is the migration's core requirement:** mpv renders
  through the libmpv render API (OpenGL) into an app-owned framebuffer, in
  the same GL context as the UI, so controls and danmaku composite directly
  over video in one swapchain. No child-HWND (wid) embedding, no airspace
  restriction, no z-order or fullscreen-sync workarounds. The existing
  `native/player-windows-mpv` loader and command planning are reused; the
  JNA/child-window hosting path is not.
- **Player UI framework:** egui on the glow (OpenGL) backend, chosen
  because it is the proven, lowest-risk host for libmpv render-API
  compositing and because an immediate-mode painter is the best rendering
  model for danmaku. GPUI/gpui-component was evaluated and rejected: GPUI
  has no public external-texture API on Windows, so real compositing would
  require a maintained gpui fork plus GL/D3D interop. Slint with its
  official OpenGL-underlay pattern is the named fallback, accepting
  ASS-rendered danmaku as its permanent trade-off.
- **Visual bar:** the player must look like a consumer product, not a tool.
  A dedicated design-system workstream (custom egui theme, bundled fonts
  including CJK, poster card styling, easing-based animations, controls
  that fade over video) is planned work, and the achievable look is an
  explicit Phase 0 spike gate (P0-S5), not an assumption.
- **Danmaku rendering:** drawn natively by the UI painter over the
  composited video frame, driven by the `native/rust-core` timeline index.
  The existing ASS/libass generation path is kept for compatibility (web
  UI, export, fallback) but is no longer the primary desktop renderer.
- **Domain logic sharing:** port server-relevant `shared/domain` logic to
  Rust and keep Android's Kotlin copy. Drift is controlled with shared JSON
  conformance fixtures asserting identical grouping/next-up/watch-state
  results. UniFFI bindings for Android are explicitly out of scope unless
  dual maintenance proves painful.
- **Admin surface:** the desktop tracking inspector, library quality
  review, provider settings, and external sync workflows are NOT ported to
  the native player. They move to the web UI. The native player is a
  player + library browser only.

## Contracts

- The wire contract is the existing trusted-LAN HTTP JSON API, byte-range
  media/subtitle/poster streaming, progress API, pairing-token auth,
  authenticated provider routes, `/web/` static serving, and the UDP
  discovery announce protocol, exactly as implemented today by
  `shared/library-server-core` and `apps/library-server-windows`.
- `LanLibraryServerStatus.apiVersion` stays `1`. The Rust server may add
  additive status fields only. Reserve a version bump for incompatible
  route, body, or media behavior; this migration must not need one.
- Golden fixtures recorded from the Kotlin server are the conformance
  test suite for the Rust server (see Phase 0).
- Existing catalog databases must survive: the Rust server either reads the
  current SQLite schema or ships a one-time importer verified against a
  real desktop catalog copy.

## Phases

### Phase 0: Contract Freeze And UI Spike

Protocol freeze:

- `[ ]` Write `docs/lan-protocol.md` documenting every HTTP route (method,
  auth, request/response bodies, status codes, range semantics) and the UDP
  discovery packet format, from the `shared/library-server-core` sources.
- `[ ]` Add a fixture recorder (Kotlin test or script) that captures golden
  request/response pairs from the running Kotlin headless server into
  `docs/lan-protocol-fixtures/` (or a test-resources folder): server
  status, catalog, media range requests (including 206 semantics),
  subtitles, posters, progress read/write, discovery announce bytes, auth
  failure shapes.
- `[ ]` Add domain conformance fixtures: JSON inputs plus expected outputs
  for series grouping, watch-state derivation, next-up, and
  continue-watching, generated from the Kotlin `shared/domain` tests, so
  the Rust port can assert identical results.

Compositing spike (new throwaway crate, e.g. `native/spike-egui-player`;
keep it out of release tooling). Exit gates, all on Windows 11:

- `[ ]` P0-S1: egui/glow app hosting mpv through the libmpv render API:
  video renders into an app-owned FBO and is composited as a textured quad
  with egui UI drawn over the video region. 4K HEVC at full rate, smooth
  window resize, fullscreen enter/exit restoring the original window
  bounds, no dropped-frame regressions versus the current player on the
  same media. This gate decides the framework.
- `[ ]` P0-S2: native danmaku overlay: drive comment layout from the
  `native/rust-core` timeline index and paint over the video at 60fps
  with 1,500+ simultaneous comments on 4K playback, correct behavior
  across seek, pause, and rate changes.
- `[ ]` P0-S3: `zh-TW` text rendering plus IME input in a search field,
  with a bundled CJK font.
- `[ ]` P0-S4: virtualized poster grid (`show_rows` or equivalent) with
  async image loading under fast scroll, 1,000+ items.
- `[ ]` P0-S5: visual bar: build one polished screen (playback view with
  fade-over-video controls plus a library rail) against a design mockup —
  custom theme, typography, rounded poster cards, hover and transition
  animations — and make an explicit accept/reject call on the achievable
  look.
- `[ ]` P0-S6: hardware-decode sanity within the render-API path (d3d11va
  hwdec interop through the GL context) on at least one discrete and one
  hybrid/integrated GPU machine.
- `[ ]` Record the framework decision and pinned crate revisions in this
  document. If P0-S1 fails after honest effort, rerun the gates on Slint
  with the OpenGL-underlay pattern (accepting ASS danmaku) before
  committing.

### Phase 1: Rust Library Server

New workspace crate `native/library-server` (binary), replacing
`apps/library-server-windows` feature-for-feature. Work in vertical
slices; the golden fixtures are the acceptance tests throughout.

Foundations:

- `[ ]` CLI parity with the JVM headless server: `--data-dir`, `--root`,
  `--port`, `--pairing-token`, `--web-assets-dir`, and the matching
  environment fallbacks.
- `[ ]` Data-directory locking, `server-settings.json` readback,
  stable pairing-token persistence, catalog snapshot persistence.
- `[ ]` Logging that never emits pairing tokens, credentials, cookies,
  signed URLs, or provider secrets.

Catalog:

- `[ ]` Filesystem scan and incremental rescan, release-name parsing, and
  series grouping ported from `shared/domain` + desktop indexing, folding
  in or extending `native/rust-core`. Must pass the domain conformance
  fixtures.
- `[ ]` SQLite persistence compatible with existing desktop catalog
  databases, or a one-time importer verified against a copied real catalog.
- `[ ]` Sidecar subtitle discovery matching current behavior.

HTTP and discovery:

- `[ ]` Server status, catalog JSON, byte-range media streaming, subtitle
  streaming, poster serving, progress read/write, `/web/` static serving,
  pairing-token auth — all validated against the golden fixtures.
- `[ ]` UDP discovery announce after HTTP bind, matching the packet fixtures.

Providers:

- `[ ]` dandanplay client: signed and proxy modes, match/comment resolve,
  cache storage and cleanup.
- `[ ]` MAL OAuth flow and Bangumi clients, external list entry
  read/write, provider mapping search, non-secret provider status
  summaries, encrypted token storage (Windows DPAPI via `keyring` or the
  `windows` crate). Schedule this slice last in Phase 1; it must not block
  the streaming/catalog parity gate.

Parity gates:

- `[ ]` `tools\windows\run-headless-web-ui-qa.ps1` passes against the Rust
  binary (add a switch to point the script at it).
- `[ ]` Android mobile and Android TV emulator QA wrappers pass against a
  Rust server host.
- `[ ]` `tools\windows\run-live-external-sync-readback-qa.ps1` passes
  (user-attended; live accounts).
- `[ ]` Release the Rust headless server as a standalone artifact.

### Phase 2: Desktop Runs The Rust Server As Sidecar

- `[ ]` Desktop launches the Rust server binary as a child process and
  connects through its existing remote-client mode
  (`--remote-server-url`/`--remote-pairing-token` internally), with
  lifecycle ownership, port selection, and crash restart.
- `[ ]` Desktop embedded-JVM-server code paths removed;
  `shared/library-server-core`, `shared/library-host-core`, and the JVM
  provider clients become unused by desktop.
- `[ ]` `tools\windows\run-embedded-web-ui-qa.ps1` and the desktop test
  suite pass in the sidecar configuration.

### Phase 3: Rust Windows Player Client

New workspace crate (e.g. `native/player-app`), building on the spike.
Milestones, each shippable while the Compose app remains the default:

- `[ ]` M1 playback core: window and chrome, mpv render-API compositing
  host from the spike, play/pause/seek/rate/volume, audio/subtitle track
  selection, fade-over-video controls, fullscreen with bounds restore,
  reusing `native/player-windows-mpv` command planning.
- `[ ]` M2 danmaku: native overlay renderer at parity with the spike gate,
  dandanplay match/comment fetch through the server, manual local danmaku
  attachment, display settings (opacity, speed, density, lanes), seek
  correctness, and ASS import compatibility for existing cached overlays.
- `[ ]` M2.5 design system: the P0-S5 theme promoted into a reusable
  styling layer (theme tokens, typography, cards, animation helpers)
  applied across all subsequent screens.
- `[ ]` M3 library client: pairing and server discovery, catalog
  browse/search/filter, posters, next-up/continue-watching rails, episode
  detail, resume lookup, progress upload, previous/next episode, auto-next.
- `[ ]` M4 settings and localization: English + `zh-TW` from the first
  screen (e.g. Fluent), playback preferences, server connection
  management. Admin workflows intentionally absent; link out to the web UI.
- `[ ]` M5 packaging: portable build with the pinned libmpv bundle; port
  the relevant `tools/windows` release/verify scripts; pass
  `tools\windows\run-windows-playback-release-qa.ps1` media matrix (1080p
  H.264 MP4, HEVC/ASS MKV, 4K HEVC MKV, large BD MKV) against the Rust
  player.

### Phase 4: Retirement And Cleanup

- `[ ]` Web UI covers the admin workflows the native player dropped
  (tracking sync, library quality review, provider settings) — coordinate
  with the server/client/web split plan.
- `[ ]` Remove `apps/desktop-windows`, `apps/library-server-windows`,
  `shared/library-server-core`, `shared/library-host-core`, and JVM-only
  provider code; keep Kotlin only for Android and the `shared/domain`
  pieces Android still needs.
- `[ ]` Remove the JNA bridging surface from `native/player-windows-mpv`.
- `[ ]` Update CI: drop desktop-JVM and macOS-desktop jobs, add Rust
  build/test/release matrix for server and player.
- `[ ]` Update `AGENTS.md` (application-layer rule, verification table),
  `docs/architecture.md`, `docs/current-state.md`, `docs/roadmap.md`,
  `README.md`, and the QA wrappers.

## Agent Working Notes

- Follow `AGENTS.md` for environment, verification, and unattended-script
  rules. Rust changes verify with `cargo fmt --all --check` and
  `cargo test --workspace`; do not run live/emulator/GUI QA scripts
  unattended.
- Never break the wire contract: any Rust server change that fails a golden
  fixture is a bug in the change, not in the fixture, unless the fixture is
  demonstrably wrong against the running Kotlin server.
- Keep phases independent: do not start Phase 3 feature work on screens
  whose spike gate has not passed, and do not delete Kotlin code before the
  matching parity gate in that phase is checked off.
- Update the checklists in this file and `docs/tasks.md` as work lands;
  keep completed-work narrative in `docs/current-state.md`, not here.

## Risks

- **OpenGL on Windows:** the render-API path puts the whole app on GL.
  mpv's GL renderer is mature on Windows, but hybrid-GPU laptops, HDR
  output, and driver quirks need coverage — hence spike gate P0-S6 and the
  M5 media-matrix QA on more than one GPU class.
- **Visual polish is effort, not a library:** egui ships utilitarian
  defaults; the consumer look depends on the M2.5 design-system
  workstream. P0-S5 exists to fail fast if the achievable look is not
  acceptable, while the Slint fallback is still cheap.
- **Rewrite scope:** the Compose desktop admin surface is intentionally not
  ported; if that decision is reversed, Phase 3 roughly doubles.
- **Dual domain logic drift** between Rust server and Kotlin Android:
  conformance fixtures are mandatory in Phase 1, not optional polish.
- **mpv edge cases** (multi-display, hardware decode, fullscreen restore)
  have regressed before; they are explicit spike and M1/M5 gates.
- **MAL OAuth + encrypted token storage** is fiddly; it is sequenced last
  in Phase 1 so it cannot block streaming/catalog parity.

## Out Of Scope

- UniFFI bindings for Android (revisit only if fixture-based parity hurts).
- macOS/Linux Rust player targets (nothing should preclude them, but no
  work is scheduled).
- Any change to the Android apps beyond pointing QA at the Rust server.
- DRM circumvention or unauthorized source behavior, as always.
