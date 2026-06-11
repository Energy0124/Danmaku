# Danmaku Bug List

Review date: 2026-06-10
Scope: Static review of the current `main` branch — desktop library server,
shared/Android library clients, dandanplay/MAL/ani-rss integration, native
libmpv FFI bridge, Rust timeline crate, and the Cloudflare Worker proxy. No
tests were executed; findings come from reading the code. Each item lists a
severity, the location, the problem, and a suggested fix.

Severity legend: **High** = security or data-loss risk, **Medium** = wrong
behavior under realistic conditions, **Low** = robustness/quality nit.

---

## Security

### S1 (High) — Worker proxy is an unauthenticated open proxy

[tools/dandanplay-worker-proxy/src/index.ts](../tools/dandanplay-worker-proxy/src/index.ts)

`handleRequest` accepts any caller. It signs every request with the owner's
`DANDANPLAY_APP_ID`/`DANDANPLAY_APP_SECRET` and forwards it to dandanplay, while
`withCors` sets `Access-Control-Allow-Origin: *`. Anyone who learns the Worker
URL can spend the owner's dandanplay quota and proxy arbitrary `/api/v2/match`
and `/api/v2/comment/{id}` calls from any origin. The whole point of the proxy
(README: "without embedding a dandanplay AppSecret in the app bundle") is
defeated if the proxy itself is open.

Fix: require a shared client token (e.g. an `Authorization` header or signed
client key validated before `loadCredentials`), and/or restrict CORS to known
origins. At minimum add per-IP rate limiting.

### S2 (Medium) — Worker caches upstream error responses publicly

[tools/dandanplay-worker-proxy/src/index.ts:108](../tools/dandanplay-worker-proxy/src/index.ts) (`proxyDandanplay` → `cacheControlHeader`)

`cacheControlHeader` returns `public, max-age=<ttl>` for *every* GET regardless
of upstream status. A transient upstream `429`/`5xx`, or an empty/garbage body,
is then cached (and CDN-cached, since `public`) for a full day by default. Users
hit stale errors with no way to refresh.

Fix: only apply the long cache TTL on `2xx` responses; use `no-store` (or a short
TTL) for non-success statuses.

### S3 (Medium) — AES key file is written before its permissions are restricted

[apps/desktop-windows/.../AniRssCredentialStore.kt:143](../apps/desktop-windows/src/desktopMain/kotlin/app/danmaku/desktop/AniRssCredentialStore.kt) (`LocalAesGcmSecretProtector.loadOrCreateKey`)

```
Files.writeString(keyPath, Base64...encodeToString(key))
restrictOwnerAccess(keyPath)
```

The secret key is created with default (group/other-readable) permissions and
only narrowed afterward — a TOCTOU window where another local user can read it.
Additionally `restrictOwnerAccess` sets POSIX permissions inside `runCatching`,
so on any filesystem that does not support POSIX perms the restriction silently
does nothing and no fallback (ACL) is attempted.

Fix: create the file with owner-only permissions atomically (e.g. create with a
`PosixFilePermissions` `FileAttribute`, or create in a 0700 directory), and on
Windows apply an ACL rather than swallowing the failure.

### S4 (Low) — Pairing token and signed media URLs travel in the query string

[shared/library-server-core/.../LocalLibraryServer.kt:439](../shared/library-server-core/src/jvmMain/kotlin/app/danmaku/server/LocalLibraryServer.kt) (`isAuthorized`), [shared/library-client-android/.../LanLibraryClient.kt:64](../shared/library-client-android/src/main/kotlin/app/danmaku/library/android/LanLibraryClient.kt) (`streamUrl`)

Authorization is `?token=<pairingToken>` on every request, including `/media/`,
`/subtitles/`, and `/posters/`. Query strings are the part of a URL most likely
to be logged by intermediaries, written to player history, or leaked via
`Referer`. The README security policy explicitly says not to log signed media
URLs, but putting the token in the URL makes that leak the default. Trusted-LAN
scope mitigates this, but a header-based token would be safer.

Fix: accept the token via an `Authorization`/custom header (keep query support
only where a header is impossible, e.g. an external player).

### S5 (Low) — dandanplay client follows redirects to arbitrary hosts

[apps/desktop-windows/.../DandanplayDanmakuClient.kt:183](../apps/desktop-windows/src/desktopMain/kotlin/app/danmaku/desktop/DandanplayDanmakuClient.kt) (`requestJson` / `shouldFollowDandanplayRedirect`)

GET redirects are followed up to 5 hops to whatever `Location` points at, with
only an http/https scheme check (`resolveHttpRedirect`). Credentials are dropped
after the first hop (good), but the client will still issue requests to any host
the upstream names. Combined with a user-configurable `baseUrl`, this is a mild
SSRF/redirect-following surface.

Fix: restrict redirects to the configured dandanplay host (or an allowlist), or
make redirect-following opt-in.

---

## Correctness

### C1 (Medium) — Empty media files are served with chunked framing then closed empty

[shared/library-server-core/.../LocalLibraryServer.kt:171](../shared/library-server-core/src/jvmMain/kotlin/app/danmaku/server/LocalLibraryServer.kt) (`handleMedia`)

For a zero-byte file, `contentLength` is `0` and the code calls
`sendResponseHeaders(200, 0)`. In `com.sun.net.httpserver`, a response length of
`0` means "unknown length, use chunked transfer," not "empty body." The handler
then closes without writing, so the client receives a chunked response with no
terminating chunk semantics matching the declared body. Clients may hang or
error on a 0-byte media item.

Fix: for the empty/HEAD case send `sendResponseHeaders(status, -1)` (explicit
"no response body") instead of `0`.

### C2 (Low) — `MyAnimeListOAuthService` keeps only one pending authorization

[apps/desktop-windows/.../MyAnimeListOAuth.kt:21](../apps/desktop-windows/src/desktopMain/kotlin/app/danmaku/desktop/MyAnimeListOAuth.kt) (`pendingSession` `AtomicReference`)

`beginAuthorization` overwrites a single `AtomicReference`. If a user starts the
OAuth flow twice (e.g. clicks "connect" again before finishing), the first
session's `state`/`codeVerifier` is discarded and the first callback fails with
"No MyAnimeList authorization is pending" or a state mismatch. Not dangerous, but
confusing.

Fix: key pending sessions by `state` in a small map with expiry, or document the
single-flight limitation in the UI.

### C3 (Low) — PKCE uses the `plain` method

[apps/desktop-windows/.../MyAnimeListOAuth.kt:43](../apps/desktop-windows/src/desktopMain/kotlin/app/danmaku/desktop/MyAnimeListOAuth.kt) (`beginAuthorization`)

`code_challenge=session.codeVerifier` with `code_challenge_method=plain` sends
the verifier in the clear in the authorization request. MyAnimeList historically
only supports `plain`, so this may be a provider constraint rather than a bug —
but it is worth a code comment so a future reader does not "fix" it to S256 and
break the exchange, and so the weaker guarantee is explicit.

Fix: add a comment noting MAL requires `plain`; revisit if/when S256 is
supported.

### C4 (Low) — Android `LanLibraryClient` does not drain error streams

[shared/library-client-android/.../LanLibraryClient.kt:28](../shared/library-client-android/src/main/kotlin/app/danmaku/library/android/LanLibraryClient.kt) (`fetchServerStatus`, `fetchCatalog`, `fetchAllProgress`, `saveProgress`)

On a non-success status these methods `check(...)` and throw before reading the
body, and never touch `errorStream`. With `HttpURLConnection` keep-alive, an
undrained error stream can prevent socket reuse. `disconnect()` in `finally`
mitigates it (forces close), but that also defeats connection pooling on every
error. Minor.

Fix: read/close `errorStream` on failure paths, mirroring the desktop client's
`responseStream(status)` helper.

---

## Robustness / Quality

### Q1 (Low) — `addColumnIfMissing` swallows all errors

[apps/desktop-windows/.../DesktopLibraryCatalogStore.kt:522](../apps/desktop-windows/src/desktopMain/kotlin/app/danmaku/desktop/DesktopLibraryCatalogStore.kt)

The migration helper wraps `ALTER TABLE` in `runCatching {}` with no inspection
of the failure. It is intended to ignore "duplicate column" on re-run, but it
will equally hide a genuinely failed migration (locked DB, disk error), after
which queries against the missing column fail later with a confusing error.

Fix: catch only the "duplicate column name" case (check the SQLite message/code)
and rethrow anything else.

### Q2 (Low) — `DanmakuDisplaySettings.filter` silently drops invalid regexes

[shared/domain/.../DanmakuDisplaySettings.kt:35](../shared/domain/src/commonMain/kotlin/app/danmaku/domain/DanmakuDisplaySettings.kt) (`filter`)

`regexFilters.mapNotNull { runCatching { Regex(it) }.getOrNull() }` discards a
malformed pattern with no signal. A user who typos a filter regex sees "no
filtering happening" with no explanation. The constructor validates that filters
are non-blank but not that regexes compile.

Fix: validate regex compilation in `init` (reject or surface the invalid
pattern) so the failure is visible at configuration time.

### Q3 (Low) — `fetchSubscriptions` issues N×2 sequential HTTP calls

[apps/desktop-windows/.../AniRssReadOnlyClient.kt:76](../apps/desktop-windows/src/desktopMain/kotlin/app/danmaku/desktop/AniRssReadOnlyClient.kt)

For each subscription, two more blocking POSTs (`downloadPath`, `playList`) run
serially. With many subscriptions this is slow and, since it is a read-only
snapshot, easily parallelizable. Functional, but a latency cliff as a library
grows.

Fix: batch or parallelize the per-subscription detail fetches, or fetch them
lazily on expand.

### Q4 (Low) — Broad cleartext + exported activity on Android

[apps/android-mobile/src/main/AndroidManifest.xml:6](../apps/android-mobile/src/main/AndroidManifest.xml), [apps/android-tv/src/main/AndroidManifest.xml:13](../apps/android-tv/src/main/AndroidManifest.xml)

`android:usesCleartextTraffic="true"` is set globally and the launcher activity
is `exported="true"`. Cleartext is required for trusted-LAN HTTP streaming, but
enabling it app-wide is broader than needed. A `network-security-config` scoped
to private LAN ranges would keep HTTP only where intended.

Fix: replace the global flag with a `networkSecurityConfig` permitting cleartext
only for RFC-1918 hosts; confirm the exported activity exposes no sensitive
intent extras.

---

## Notes / Non-issues confirmed during review

- The native libmpv FFI bridge
  ([native/player-windows-mpv/src/ffi.rs](../native/player-windows-mpv/src/ffi.rs),
  [windows.rs](../native/player-windows-mpv/src/windows.rs)) does careful null
  checks, UTF-8 validation, buffer-size checks, and frees the string returned by
  `mpv_get_property_string`. No memory-safety issues spotted, though every entry
  point is necessarily `unsafe` and relies on callers honoring the contract.
- The Rust `Timeline` half-open window query
  ([timeline.rs](../native/rust-core/src/timeline.rs)) is correct and
  well-tested (stable sort, `partition_point` bounds, reversed/empty windows).
- HTTP range parsing in `handleMedia`/`parseRange` correctly handles suffix
  ranges, unsatisfiable ranges (416 + `Content-Range: bytes */size`), and
  multi-range rejection.
- Token comparisons use `MessageDigest.isEqual` (constant-time for equal-length
  inputs) in both the server auth check and the webhook hook.
- Path traversal is guarded in the catalog store: resolved media/subtitle paths
  must `startsWith` the normalized root before being served.

---

## Suggested priority

1. **S1** — close the open proxy (security + cost).
2. **S2** — stop caching upstream errors publicly.
3. **S3** — fix key-file permission ordering.
4. **C1** — empty-file media response framing.
5. Everything else as cleanup.
