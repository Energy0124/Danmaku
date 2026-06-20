# Danmaku Web UI

Trusted-LAN browser client for the Danmaku library server.

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
