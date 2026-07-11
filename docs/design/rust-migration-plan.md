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

- **Server stack:** tokio + axum + rusqlite + serde. No gRPC; the
  existing HTTP JSON + byte-range contract stays. Outbound provider HTTP
  currently uses WinHTTP (`windows-sys`) behind a transport trait instead
  of the originally planned reqwest — smaller dependency tree for the
  Windows-first server; swap to reqwest if non-Windows server targets
  materialize.
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

- `[x]` Write `docs/lan-protocol.md` documenting every HTTP route (method,
  auth, request/response bodies, status codes, range semantics) and the UDP
  discovery packet format, from the `shared/library-server-core` sources.
- `[x]` Add a fixture recorder (Kotlin test or script) that captures golden
  request/response pairs from the running Kotlin headless server into
  `docs/lan-protocol-fixtures/` (or a test-resources folder): server
  status, catalog, media range requests (including 206 semantics),
  subtitles, posters, progress read/write, discovery announce bytes, auth
  failure shapes.
- `[x]` Add domain conformance fixtures: JSON inputs plus expected outputs
  for series grouping, watch-state derivation, next-up, and
  continue-watching, generated from the Kotlin `shared/domain` tests, so
  the Rust port can assert identical results.

Compositing spike (new throwaway crate, e.g. `native/spike-egui-player`;
keep it out of release tooling). Exit gates, all on Windows 11:

- `[x]` P0-S1: egui/glow app hosting mpv through the libmpv render API:
  video renders into an app-owned FBO and is composited as a textured quad
  with egui UI drawn over the video region. 4K HEVC at full rate, smooth
  window resize, fullscreen enter/exit restoring the original window
  bounds, no dropped-frame regressions versus the current player on the
  same media. This gate decides the framework.
- `[x]` P0-S2: native danmaku overlay: drive comment layout from the
  `native/rust-core` timeline index and paint over the video at 60fps
  with 1,500+ simultaneous comments on 4K playback, correct behavior
  across seek, pause, and rate changes. Smooth motion required an
  interpolated overlay clock instead of raw per-frame `time-pos`
  readback; the smoke's `danmaku_velocity_jitter` metric guards this.
- `[x]` P0-S3: `zh-TW` text rendering plus IME input in a search field,
  with a bundled CJK font.
- `[x]` P0-S4: virtualized poster grid (`show_rows` or equivalent) with
  async image loading under fast scroll, 1,000+ items.
- `[x]` P0-S5: visual bar: build one polished screen (playback view with
  fade-over-video controls plus a library rail) against a design mockup —
  custom theme, typography, rounded poster cards, hover and transition
  animations — and make an explicit accept/reject call on the achievable
  look. Accepted 2026-07-08 on real media.
- `[x]` P0-S6: hardware-decode sanity within the render-API path (d3d11va
  hwdec interop through the GL context) on at least one discrete and one
  hybrid/integrated GPU machine. Verified on the primary discrete-GPU
  machine; re-verify on a hybrid/integrated laptop before M5 packaging.
- `[x]` Framework decision: egui/glow CONFIRMED for the Phase 3 player.
  Spike accepted 2026-07-08 with eframe/egui/egui_glow 0.32.3 pinned in
  `native/spike-egui-player/Cargo.lock`. Evidence: ~58fps composited
  1080p60 with 1,754 peak simultaneous danmaku, zero render failures,
  `danmaku_velocity_jitter` 0.0000, user-accepted visuals and IME on real
  media. Known issue to resolve before M1: first-paint stall when the
  window is created without foreground activation (documented in the
  spike README). The Slint fallback path is retired.

### Phase 1: Rust Library Server

New workspace crate `native/library-server` (binary), replacing
`apps/library-server-windows` feature-for-feature. Work in vertical
slices; the golden fixtures are the acceptance tests throughout.

Foundations:

- `[x]` CLI parity with the JVM headless server: `--data-dir`, `--root`,
  `--port`, `--pairing-token`, `--web-assets-dir`, and the matching
  environment fallbacks. (Also accepts the JVM `--web-ui-dist` alias;
  unlike the JVM parser, unknown arguments are rejected.)
- `[x]` Data-directory locking, `server-settings.json` readback,
  stable pairing-token persistence, catalog snapshot persistence.
  (JVM-adoptable: same `.danmaku-host.lock` byte-range lock, same
  schema-v1 settings file carrying the six-digit token, JSON-equivalent
  catalog snapshot verified against the golden fixture.)
- `[x]` Logging that never emits pairing tokens, credentials, cookies,
  signed URLs, or provider secrets.

Catalog:

- `[x]` Filesystem scan and incremental rescan, release-name parsing, and
  series grouping ported from `shared/domain` + desktop indexing, folding
  in or extending `native/rust-core`. Must pass the domain conformance
  fixtures.
- `[x]` SQLite persistence compatible with existing desktop catalog
  databases, or a one-time importer verified against a copied real catalog.
  (One-time importer chosen: `--import-desktop-catalog <db-copy>` reads
  the desktop SQLDelight DB read-only, preserves desktop media ids, and
  merges progress newest-wins. Verified 2026-07-09 against a copy of the
  real desktop `library.db`: 5 roots, 5,852 items, 333 subtitles, 136
  matched posters, 10 progress rows imported and served correctly from
  the snapshot boot path. Desktop-only tables — favorites, local
  watch-list, provider caches, download queue, quality decisions — are
  deliberately not imported; the LAN protocol has no surface for them.)
- `[x]` Sidecar subtitle discovery matching current behavior.

HTTP and discovery:

- `[x]` Server status, catalog JSON, byte-range media streaming, subtitle
  streaming, poster serving, progress read/write, `/web/` static serving,
  pairing-token auth — all validated against the golden fixtures.
- `[x]` UDP discovery announce after HTTP bind, matching the packet fixtures.

Providers:

- `[x]` dandanplay client: signed and proxy modes, match/comment resolve,
  cache storage and cleanup.
- `[x]` MAL OAuth flow and Bangumi clients, external list entry
  read/write, provider mapping search, non-secret provider status
  summaries, encrypted token storage (Windows DPAPI via `keyring` or the
  `windows` crate). Schedule this slice last in Phase 1; it must not block
  the streaming/catalog parity gate.

Parity gates:

- `[x]` `tools\windows\run-headless-web-ui-qa.ps1` passes against the Rust
  binary (add a switch to point the script at it). (Passed 2026-07-09 via
  `-RustServer`: full route/restart/persistence checks plus the Chrome
  browser interaction probe, unchanged assertions. The first run caught a
  real parity bug — the Rust server auto-discovered `local.properties`
  credentials, which the JVM headless host never reads; auto-discovery
  was removed and a properties file is now read only via an explicit
  `DANMAKU_LOCAL_PROPERTIES` path.)
- `[x]` Android mobile and Android TV emulator QA wrappers pass against a
  Rust server host. (Passed 2026-07-09 via new -RustServer wrapper modes:
  a real-host connectivity instrumentation test drives the actual
  library-client-android stack against a live Rust host through the
  emulator 10.0.2.2 boundary — status, catalog, byte-range media, and
  progress write/read/list — green on phone, tablet, TV 1080p, and TV 4K
  alongside the unchanged connected suites. The runs also flushed out
  nine stale TvLibraryItemsTest expectations broken earlier by the
  pairing-code removal, now repaired.)
- `[ ]` `tools\windows\run-live-external-sync-readback-qa.ps1` passes
  (user-attended; live accounts).
- `[x]` Release the Rust headless server as a standalone artifact.
  (tools/windows/prepare-rust-server-release.ps1 builds a versioned zip
  with the release exe, bundled web UI, licenses including a generated
  Rust-crate listing, and a usage README, gated by a staged-exe smoke
  check; CI packages and uploads it as a build artifact. Verified
  2026-07-09 by lead rebuild + smoke.)

### Phase 2: Desktop Runs The Rust Server As Sidecar

- `[x]` Desktop launches the Rust server binary as a child process and
  connects through its existing remote-client mode
  (`--remote-server-url`/`--remote-pairing-token` internally), with
  lifecycle ownership, port selection, and crash restart.
- `[x]` Desktop embedded-JVM-server/discovery code paths removed; local mode
  defaults to the bundled Rust sidecar, explicit remote mode skips it, and
  `shared/library-host-core` is unused by desktop.
- `[ ]` `shared/library-server-core` and its JVM provider clients become
  unused by desktop; remaining provider/progress/diagnostic contracts move
  behind the sidecar HTTP boundary.
- `[x]` `tools\windows\run-embedded-web-ui-qa.ps1` and the desktop test
  suite pass in the sidecar configuration. Verified 2026-07-10 with
  `headless-server` status, catalog/media/subtitle/progress assertions, and
  the Chrome provider/list plus overlay-persistence interaction probe.

### Phase 3: Rust Windows Player Client

New workspace crate (e.g. `native/player-app`), building on the spike.
Milestones, each shippable while the Compose app remains the default:

- `[x]` M1 playback core: window and chrome, mpv render-API compositing
  host from the spike, play/pause/seek/rate/volume, audio/subtitle track
  selection, fade-over-video controls, fullscreen with bounds restore,
  reusing `native/player-windows-mpv` command planning. (Shipped as the
  `native/player-app` workspace crate (`danmaku-player` binary) with unit
  tests for CLI/track/clock/smoke logic and a `--smoke` self-check;
  verified 2026-07-10 by smoke PASS (131 frames/6s, nvdec, 0 render
  failures) plus a supervised mid-playback capture of a real 1080p
  HEVC-10bit episode with `--start` honored and controls faded. Deeper
  interactive QA rides the M5 release gates.)
- `[x]` M2 danmaku: native overlay renderer at parity with the spike gate,
  dandanplay match/comment fetch through the server, manual local danmaku
  attachment, display settings (opacity, speed, density, lanes), seek
  correctness, and ASS import compatibility for existing cached overlays.
  Shipped in `native/player-app` with normalized
  `/api/danmaku/{mediaId}` loading, local XML/JSON startup and drag-and-drop
  attachment, mpv ASS passthrough, mode/color/size-aware egui painting, and a
  `D` shortcut/control menu. Focused tests cover 150-comments/second active
  density, subpixel motion, lane reuse, deterministic density, speed and lane
  limits, seek-window replacement, XML/JSON parsing, ASS selection, chunked
  HTTP, and the server wire shape. Deeper interactive media-matrix QA remains
  part of M5.
- `[x]` M2.5 design system: the P0-S5 theme promoted into a reusable
  styling layer (theme tokens, typography, cards, animation helpers)
  applied across all subsequent screens. (Shipped as
  `native/player-app/src/theme.rs`: palette/typography/metrics tokens,
  `theme::apply` owning fonts+widget style, fade/easing/color-mix
  helpers with unit tests, and card-outline hover helper for M3; the
  playback screen consumes tokens exclusively and the CJK font asset
  moved into `native/player-app/assets/`. Verified 2026-07-10 with a
  DPI-aware full-window capture showing the themed control bar, track
  and danmaku menus, and title ribbon over live video. Slider-rail
  color refinement noted for M3 screen work.)
- `[x]` M3 library client: pairing and server discovery, catalog
  browse/search/filter, posters, next-up/continue-watching rails, episode
  detail, resume lookup, progress upload, previous/next episode, auto-next.
  (Shipped 2026-07-11: UDP discovery + connect screen with manual
  URL/token, library home with continue-watching/next-up rails and a
  culled poster grid (async LAN poster loading with LRU texture cap,
  initials fallback), search over series/episodes, per-series season and
  episode lists with resume labels, playback via the sidecar-compatible
  stream URLs with resume lookup, server danmaku fetch, throttled +
  forced progress uploads, prev/next episode controls, opt-in auto-next
  on end-of-file, and a `--qa-play-first` hook. Domain rules (grouping,
  next-up, continue-watching, resume) are Rust ports asserted against
  the shared JSON conformance fixtures. Verified live against the Rust
  server: zero-config discovery connect, playback from stream URLs, EOF
  progress uploads for two auto-chained episodes on the server, and
  rail rendering from seeded progress. Deferred: series watch-state
  badges, attaching LAN sidecar subtitle tracks to mpv, and rail poster
  art for metadata-matched items without local posters.)
- `[x]` M4 settings and localization: English + `zh-TW` from the first
  screen, playback preferences, and server connection management. Shipped
  with typed bundled translations and an initial-screen language selector;
  user-scoped JSON preferences for volume, rate, auto-next, and danmaku
  display defaults; remembered server URLs without persisted pairing tokens;
  change/forget connection controls; and an explicit `/web/` administration
  link while keeping admin workflows out of the native client. Focused tests
  cover language selection plus preference round-trip, sanitization, and
  credential exclusion.
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
