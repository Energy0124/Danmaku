# Dandanplay Worker Proxy

Cloudflare Worker proxy for public clients that need danmaku comments without
embedding a dandanplay AppSecret in the app bundle.

The Worker exposes a small dandanplay-compatible surface:

- `POST /api/v2/match`
- `GET /api/v2/comment/{episodeId}?withRelated=true`
- `GET /health`

Configure secrets in Cloudflare, not in `wrangler.toml`:

```powershell
npx wrangler secret put DANDANPLAY_APP_ID
npx wrangler secret put DANDANPLAY_APP_SECRET
```

After deployment, public app builds can set the Worker URL as
`danmaku.dandanplay.proxyBaseUrl` or `DANMAKU_DANDANPLAY_PROXY_BASE_URL`. Direct
local AppId/AppSecret credentials still win when present.
