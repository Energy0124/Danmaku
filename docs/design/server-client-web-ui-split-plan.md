# Server, Client, And Web UI Split Plan

Last updated: 2026-06-20.

## Direction

Danmaku should move from "desktop app that also hosts the server" to a
library-host architecture:

1. Keep the current Windows desktop embedded host as the default release path.
2. Extract host lifecycle and diagnostics behind a shared boundary.
3. Add a trusted-LAN web UI served by the same HTTP server.
4. Add an opt-in headless Windows server that reuses the same host boundary.
5. Let desktop run as a remote client when pointed at a headless host.
6. Prototype a Rust native client only after the HTTP API is stable.

This is a compatibility-first split. Android mobile, Android TV, desktop
paired playback, media streaming, subtitle/poster serving, progress sync,
ani-rss hooks, and MyAnimeList OAuth callbacks must keep working while the
host boundary grows.

## Contracts

- Kotlin modules share domain and app logic inside one process.
- Separate processes and devices communicate through the existing HTTP JSON
  API plus normal HTTP byte-range media URLs.
- Do not introduce gRPC for the main product split. It complicates browser
  playback, byte-range media streaming, and simple trusted-LAN clients.
- Optional browser/static capabilities are additive status fields while
  `LanLibraryServerStatus.apiVersion` remains `1`.
- Reserve an API version bump for incompatible route, body, or media behavior.

## Phases

### Phase 1: Host Boundary

- Add `shared:library-host-core` with host config, mode, runtime status, and
  service contracts.
- Keep desktop storage/indexing/provider implementations in
  `apps:desktop-windows` until they have a second real host implementation.
- Make `DesktopLibraryServerRuntime` report host status through the shared
  contract without moving database ownership yet.

### Phase 2: Web UI Serving

- Add optional static web serving to `shared:library-server-core`.
- Serve the web shell under `/web/` and keep all existing API/media routes
  unchanged.
- Serve the web shell without a pairing token; require the token for catalog,
  media, posters, subtitles, and progress routes as today.
- Add status fields for `webUiAvailable`, `webUiPath`, and `hostMode`.

### Phase 3: TypeScript Web UI

- Add `apps/web-ui` as a Vite TypeScript SPA.
- Use the same HTTP endpoints as Android/TV:
  `/api/server/status`, `/api/library`, `/api/progress`, `/media`,
  `/subtitles`, and `/posters`.
- Implement pairing, catalog browsing, poster display, detail view, HTML5 video
  playback, and progress read/write first.
- Keep web danmaku overlay, admin settings, provider sync controls, and library
  quality workflows as follow-up web features.

### Phase 4: Headless Server

- Add an opt-in `apps:library-server-windows` JVM app.
- Reuse the host boundary and LAN server.
- Add CLI config for data directory, roots, port, pairing token, and web UI.
- Add a data-directory lock before any durable database writes.
- First implementation scans configured `--root` folders at startup and on
  `rescan`, publishes a basic catalog, and streams media/subtitles through the
  existing LAN routes.
- Headless catalog snapshots, stable pairing tokens, and playback progress are
  persisted under the locked data directory so catalog readback, existing
  pairings, and web/mobile/TV resume points survive server restarts.
- Headless hosts can read roots and non-secret provider settings from
  `server-settings.json` when CLI roots are absent, expose non-secret provider
  summaries through server status, expose authenticated provider runtime readiness,
  expose authenticated read-only provider mapping search, and serve a cached
  catalog when no roots are configured.
- Headless hosts announce themselves through the existing LAN discovery
  protocol after the HTTP server binds.
- Later work wires the remaining provider settings into headless provider
  network operations such as danmaku fetch and list sync, then adds release
  packaging.

### Phase 5: Desktop Remote Client

- Keep embedded desktop host as default.
- Desktop now accepts `--remote-server-url`/`--remote-pairing-token` and
  `DANMAKU_REMOTE_SERVER_URL`/`DANMAKU_REMOTE_PAIRING_TOKEN` launch settings,
  opens the Library tab, and auto-loads the remote catalog when a token is
  provided.
- The desktop remote browser uses `JvmLanLibraryClient`, prepares remote media
  URLs, and can load those URLs into the libmpv-backed desktop controller.
- Later work can add a stricter remote-only mode that skips starting the
  embedded desktop host after the UX and packaging behavior are validated.

### Phase 6: Rust Native Client Spike

- Prototype only after the HTTP API has settled.
- Consume the same LAN HTTP API and pass media URLs to mpv/libmpv.
- Do not duplicate indexing, provider sync, metadata, or library persistence
  in Rust for the first spike.

## Acceptance Gates

- Existing LAN server/client tests still pass.
- Android mobile and Android TV connect to both embedded and headless hosts.
- Desktop embedded playback still works.
- Desktop remote playback streams from a host.
- Web UI works against embedded and headless hosts. The headless path is covered
  by `tools/windows/run-headless-web-ui-qa.ps1`, including a restart probe for
  cached catalog and persisted progress readback; embedded-host browser QA
  still needs a dedicated pass before release.
- Rust tests remain green, and any Rust client prototype stays behind a
  separate experimental target.
