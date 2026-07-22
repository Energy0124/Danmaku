# Danmaku Web UI

Trusted-LAN browser client for the Danmaku library server. It supports pairing,
catalog playback, progress sync, provider readiness, dandanplay match/comment
preview, provider settings, and bearer-authenticated tracking administration.
Tracking administration persists series mappings, reads external list state,
coalesces local series that share an exact provider anime identity, shows
conflict-aware write previews, and requires explicit preview acknowledgement
before syncing to MyAnimeList or Bangumi. A logical series may have one identity
per provider; contradictory IDs for the same provider are blocked until an
administrator removes the incorrect mapping.

## Local Development

```powershell
npm install
npm run dev
```

The dev server expects a Danmaku library host URL and pairing token in the UI.
Production builds use `base: "/web/"` so the app can be served by the library
server under `/web/`.

## Build

```powershell
npm run build
```

The server can serve the generated `dist/` directory through
`StaticWebAssets(root = Path.of("apps/web-ui/dist"))`.

## QA

```powershell
.\tools\windows\run-headless-web-ui-qa.ps1
.\tools\windows\run-embedded-web-ui-qa.ps1
```

The headless Windows QA wrapper builds the web UI, starts a fixture-backed
headless library host, verifies the served `/web/` shell and HTTP API routes,
then uses headless Chrome or Edge through CDP to verify danmaku overlay
preference persistence, provider search, Use ID, and external-list read/save
form behavior. Pass `-SkipBrowserInteractionQa` to keep the older route-only
check when a browser is not available.

The embedded Windows QA wrapper uses an isolated `LOCALAPPDATA`, starts the
Compose desktop host with `--web-assets-dir`, `--server-pairing-token`, and
`--qa-library-root`, then runs the same browser interaction checks against the
real embedded desktop server.
