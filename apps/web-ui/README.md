# Danmaku Web UI

Trusted-LAN browser client for the Danmaku library server. It supports pairing, catalog playback, progress sync, provider readiness chips, and dandanplay match/comment preview for selected media when the host has provider access configured.

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
```

The Windows QA wrapper builds the web UI, starts a fixture-backed headless
library host, verifies the served `/web/` shell and HTTP API routes, then uses
headless Chrome or Edge through CDP to verify danmaku overlay preference
persistence across reload. Pass `-SkipBrowserInteractionQa` to keep the older
route-only check when a browser is not available.
