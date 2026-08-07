# Danmaku Bug List

Last reconciled: 2026-08-07.

The June 2026 static-review findings that targeted the retired Compose desktop,
JVM server, or JNA bridge were removed with those implementations. Remaining
open findings should be revalidated against current code before fixing them.

## Security

### S1 (High) — Worker proxy is an unauthenticated open proxy

`tools/dandanplay-worker-proxy/src/index.ts` signs allowed dandanplay requests
for any caller that knows the Worker URL and permits every CORS origin. Require
a client credential or another explicit access policy and add rate limiting.

### S2 (Medium) — Worker may cache upstream error responses

Revalidate that only successful upstream GET responses receive the long public
cache policy. Provider failures and rate limits should be `no-store` or use a
short failure TTL.

### S3 (Low) — Pairing tokens can appear in media query strings

The version-1 LAN API uses query tokens for URLs consumed by media players.
Prefer authorization headers where clients support them, avoid logging full
URLs, and treat query-token compatibility as a deliberate protocol constraint.

## Correctness And Quality

### C1 (Low) — Android error streams may inhibit connection reuse

Revalidate non-success paths in
`shared/library-client-android/.../LanLibraryClient.kt`; response bodies and
error streams should be consumed or closed before disconnecting.

### Q1 (Low) — Invalid danmaku regexes are silently ignored

`shared/domain/.../DanmakuDisplaySettings.kt` should surface malformed regular
expressions when settings are validated instead of silently dropping them.

### Q2 (Low) — Android cleartext permission is broad

Android requires HTTP for trusted-LAN playback, but its network security policy
should be narrowed where platform support permits without breaking private-LAN
hosts.
