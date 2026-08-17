# LAN Protocol

This document defines the trusted-LAN wire contract implemented by
`native/library-server` and consumed by the Android, Windows, and web clients.
The Rust server and its fixtures are authoritative for server behavior.

## Version Policy

- HTTP status uses `LanLibraryServerStatus.apiVersion`.
- The current API version is `1`.
- UDP discovery uses `LanLibraryServerAnnouncement.version`.
- The current discovery version is `1`.
- The server may add fields that old clients can ignore.
- Incompatible route, body, status, or media behavior requires a version
  bump.

## Shared Encoding

- JSON is UTF-8.
- Server JSON uses serde camel-case field names. Fields marked optional or
  default are omitted according to the route's response contract.
- Simple status errors have no JSON envelope unless a route documents one.
- Public hook validation errors are `text/plain; charset=utf-8` bodies with
  `Cache-Control: no-store`.
- Pairing-token route auth is not enforced today. The server stores a pairing
  token, but catalog, media, subtitle, poster, danmaku, and progress routes
  remain available on the trusted LAN. `pairingRequired` is therefore false.
- `AuthenticatedPostHook` token auth is separate from pairing. It uses
  `X-Danmaku-Webhook-Token`.

## Core HTTP Routes

### `GET /api/server/status`

Auth: none.

Success: `200 application/json; charset=utf-8`, `Cache-Control: no-store`.

Body shape:

```json
{
  "appName": "Danmaku",
  "apiVersion": 1,
  "pairingRequired": false,
  "mediaStreaming": true,
  "progressSync": true,
  "trustedDeviceManagement": false,
  "webUiAvailable": false,
  "webUiPath": null,
  "hostMode": "headless-server",
  "scanning": false,
  "scanFilesSeen": null,
  "scanError": null,
  "providerSettings": null
}
```

Default fields are omitted on the wire except `hostMode`, which is always
`headless-server`. A configured server may include web and provider fields.
While a startup or manual library scan is running, `scanning` is true and
`scanFilesSeen` reports its current discovered-file count. `scanError` holds
the last scan failure until the next scan starts.

Status codes:

- `200`: status returned.
- `405`: method is not `GET`; empty body.

### `GET /api/library`

Auth: none in current code, despite the stored pairing token.

Success: `200 application/json; charset=utf-8`, `Cache-Control: no-store`.

Body shape is `LibraryCatalog`:

```json
{
  "rootName": "Example Library",
  "indexedAtEpochMs": 1700000000000,
  "items": [
    {
      "id": "episode-id",
      "seriesTitle": "Example Show",
      "episodeTitle": "Episode 01",
      "relativePath": "Example Show/Episode 01.mkv",
      "sizeBytes": 123,
      "mediaType": "video/x-matroska",
      "streamPath": "/media/episode-id",
      "indexedAtEpochMs": 1700000000000,
      "subtitles": [],
      "posterPath": "/posters/episode-id",
      "rootLabel": "M:\\Anime",
      "animeMetadata": null,
      "metadataStatus": "NOT_AVAILABLE"
    }
  ]
}
```

Default item fields may be omitted by the core JSON encoder.

`rootLabel` is a Rust-server extension (2026-07-13): the absolute path of
the library root the item was scanned from, so clients can browse and
filter per configured folder when several roots are merged into one
catalog. Servers that predate it simply omit the field.

Status codes:

- `200`: catalog returned.
- `405`: method is not `GET`; empty body.
- `401`: unreachable with current `isAuthorized()` implementation.

Quirk: the handler does not check the exact path.

### `POST /api/library/rescan`

Auth: `Authorization: Bearer <pairing-token>`.

Requests an asynchronous scan of the folder currently shown by a native
client. The body is a JSON object containing the folder browser's logical path:

```json
{
  "path": ["M:\\Anime", "Example Show"]
}
```

For a single configured root, `path` contains only relative folder segments.
For multiple roots, the first segment is the root label returned in
`LibraryMediaItem.rootLabel`. An empty array requests a full scan of every
configured root. The accepted request returns immediately; clients use
`GET /api/server/status` while `scanning` is true, then reload
`GET /api/library`. The server merges a folder scan into the existing catalog
so unrelated roots and sibling folders are preserved. A folder removed from
disk produces an empty subtree and removes its stale catalog entries.

Status codes:

- `202`: scan accepted.
- `400`: malformed JSON, invalid path segments, or a path outside configured
  roots.
- `401`: missing or incorrect pairing token.
- `409`: another scan is already running.
- `405`: method is not `POST`.
- `404`: this server instance was not configured with authenticated
  administration or scan roots/storage.

### `GET /api/progress`

Auth: none.

Success: `200 application/json; charset=utf-8`, `Cache-Control: no-store`.

Body shape is a JSON array of `PlaybackProgress` for currently published
media ids only:

```json
[
  {
    "mediaId": "episode-id",
    "positionMs": 12345,
    "durationMs": 98765,
    "updatedAtEpochMs": 1700000100000
  }
]
```

Rows are sorted newest-first by the built-in progress stores.

Status codes:

- `200`: list returned.
- `404`: prefix matched a path other than exactly `/api/progress`; empty
  body.
- `405`: method is not `GET`; empty body.
- `401`: unreachable with current `isAuthorized()` implementation.

### `GET /api/progress/{mediaId}`

Auth: none.

`mediaId` is taken from the path after `/api/progress/` and decoded with
UTF-8 URL decoding. The id must exist in the currently published catalog.

Success: `200 application/json; charset=utf-8`, `Cache-Control: no-store`.

Body shape is `PlaybackProgress`:

```json
{
  "mediaId": "episode-id",
  "positionMs": 12345,
  "durationMs": 98765,
  "updatedAtEpochMs": 1700000100000
}
```

Status codes:

- `200`: saved progress returned.
- `404`: blank, unknown, unpublished media id, or no saved progress; empty
  body.
- `405`: method is not `GET` or `PUT`; empty body.
- `401`: unreachable with current `isAuthorized()` implementation.

### `PUT /api/progress/{mediaId}`

Auth: none.

Request body: `PlaybackProgress` JSON. `mediaId` in the request body must
match the URL-decoded path id.

Success: `204`, empty body.

Status codes:

- `204`: progress saved.
- `400`: malformed JSON or body media id mismatch; empty body.
- `404`: blank, unknown, or unpublished media id; empty body.
- `405`: method is not `GET` or `PUT`; empty body.
- `401`: unreachable with current `isAuthorized()` implementation.

### `GET /api/danmaku/{mediaId}`

Auth: none.

`mediaId` is URL-decoded from the path. The media id must exist in the
catalog and have a regular file in `PublishedLibrary.filesById`.

Query parameters:

- `forceRefresh=true`: passes `true` to the resolver, case-insensitive.
- Any other value, missing value, or missing parameter is treated as `false`.

Success: `200 application/json; charset=utf-8`, `Cache-Control: no-store`.

Body shape is `LanDanmakuTrack`:

```json
{
  "mediaId": "episode-id",
  "status": "READY",
  "source": "NETWORK",
  "comments": [
    {
      "id": "comment-1",
      "timestampMs": 1000,
      "text": "Hello",
      "style": {
        "colorArgb": 4294967295,
        "mode": "SCROLLING",
        "size": "NORMAL"
      }
    }
  ],
  "matchTitle": "Example Show",
  "episodeId": 123,
  "fetchedAtEpochMs": 456,
  "message": null
}
```

If no resolver is configured, the route still returns `200` with
`status = "UNAVAILABLE"` and message `Danmaku resolver is not available.`
If the resolver throws, the route returns `200` with `status = "FAILED"`.

Status codes:

- `200`: danmaku track, unavailable result, or failed result returned.
- `404`: unknown media id, unpublished file, or missing file; empty body.
- `405`: method is not `GET`; empty body.
- `401`: unreachable with current `isAuthorized()` implementation.

### `GET|HEAD /media/{id}`

Auth: none.

`id` is the raw path suffix after `/media/`. It is not URL-decoded by the
handler. The id must exist in `PublishedLibrary.filesById` and point to a
regular file.

Success headers:

- `Accept-Ranges: bytes`
- `Content-Type: <mapped type or Files.probeContentType or
  application/octet-stream>`
- `Content-Range: bytes <start>-<end>/<size>` on `206` only

Status codes:

- `200`: full file response.
- `206`: valid single byte range response.
- `404`: unknown id or missing file; empty body.
- `405`: method is not `GET` or `HEAD`; empty body.
- `416`: invalid range; empty body and `Content-Range: bytes */<size>`.
- `401`: unreachable with current `isAuthorized()` implementation.

For `HEAD`, the server sends the same status and deterministic headers but
closes without writing a body.

Byte-range semantics:

- Only `Range` headers starting with `bytes=` are accepted.
- Only one range is accepted. A comma makes the range invalid.
- `bytes=start-end` requires non-negative `start`.
- `end` is clamped to `fileSize - 1` when it is beyond the file size.
- `bytes=start-` runs through the end of the file.
- `bytes=-suffixLength` returns the last `suffixLength` bytes.
- A suffix length larger than the file size returns the whole file.
- `suffixLength` must be greater than zero.
- `start` must be less than file size and no greater than the final end.
- Any range on an empty file is invalid and returns `416`.

### `GET|HEAD /subtitles/{id}`

Auth: none.

`id` is the raw path suffix after `/subtitles/`. It is not URL-decoded by
the handler. The id must exist in `PublishedLibrary.subtitleFilesById` and
point to a regular file.

Success headers:

- `Content-Type` from the file extension:
  - `.ass`: `text/x-ass`
  - `.srt`: `application/x-subrip`
  - `.ssa`: `text/x-ssa`
  - `.vtt`: `text/vtt`
  - otherwise probed or `application/octet-stream`
- `Cache-Control: no-store`
- `Content-Length` is explicitly set for `HEAD`.

Status codes:

- `200`: subtitle file returned, or headers only for `HEAD`.
- `404`: unknown id or missing file; empty body.
- `405`: method is not `GET` or `HEAD`; empty body.
- `401`: unreachable with current `isAuthorized()` implementation.

Range requests are ignored; this route does not implement byte ranges.

### `GET|HEAD /posters/{id}`

Auth: none.

`id` is the raw path suffix after `/posters/`. It is not URL-decoded by the
handler. The id must exist in `PublishedLibrary.posterFilesById` and point
to a regular file.

Success headers:

- `Content-Type` from mapped extension, probe result, or
  `application/octet-stream`
- `Cache-Control: private, max-age=3600`
- `Content-Length` is explicitly set for `HEAD`.

Status codes:

- `200`: poster file returned, or headers only for `HEAD`.
- `404`: unknown id or missing file; empty body.
- `405`: method is not `GET` or `HEAD`; empty body.
- `401`: unreachable with current `isAuthorized()` implementation.

Range requests are ignored; this route does not implement byte ranges.

## Static Web Routes

These routes exist only when `StaticWebAssets` is configured.

### `GET|HEAD /web` and `/web/...`

Auth: none.

The default path prefix is `/web`. The root is normalized, and requests that
escape it after URL decoding return `404`.

Status codes:

- `302`: exact `/web` redirects to `/web/` with `Location: /web/`.
- `200`: file served.
- `404`: disabled, missing file, escaped root, or unmatched path; empty body.
- `405`: method is not `GET` or `HEAD`; empty body.

Serving rules:

- `/web/` serves `index.html`.
- A regular target file is served directly.
- For `GET`, the server falls back to `index.html` when `Accept` contains
  `text/html` or when the final path segment has no dot.
- `HEAD` only serves a regular target or `/web/`; it does not use SPA
  fallback for extensionless paths.
- `index.html` uses `Cache-Control: no-store`.
- Other files use `Cache-Control: public, max-age=3600`.
- `Content-Length` is explicitly set for `HEAD`.

Known content-type mappings include `.css`, `.html`, `.js`, `.json`, `.svg`,
subtitle extensions, and probed fallback.

## Hook Routes

Hook routes are configured by the Rust host. The server provides
the dispatch behavior; the Windows headless server installs provider hooks.

### `POST /api/hooks/...`

Configured by `AuthenticatedPostHook`.

Auth: `X-Danmaku-Webhook-Token` must exactly match the hook token using
`MessageDigest.isEqual`.

Status codes:

- `202`: hook accepted; empty body.
- `401`: missing or wrong webhook token; empty body.
- `405`: method is not `POST`; empty body.
- `500`: hook callback threw; empty body.

### Public GET Hooks

Configured by `PublicGetHook`.

Auth: none.

Only `GET` is accepted by the core dispatcher. The hook chooses the response
status, content type, and text body. The core adds `Cache-Control: no-store`.
Query strings are parsed as `key=value` pairs, URL-decoded with UTF-8, and
entries without `=` are ignored. Duplicate keys keep the last value.

Status codes:

- Hook-defined success or validation status.
- `405`: method is not `GET`; empty body.
- `500`: hook callback threw; `text/plain; charset=utf-8` body
  `Request failed.`

### Public Request Hooks

Configured by `PublicRequestHook`.

Auth: none.

All methods reach the hook. The core reads a text body only for `POST`,
`PUT`, and `PATCH`; all other methods pass an empty body. The hook chooses
the response status, content type, and text body. The core adds
`Cache-Control: no-store`.

Status codes:

- Hook-defined success or validation status.
- `500`: hook callback threw; `text/plain; charset=utf-8` body
  `Request failed.`

## Provider Administration Routes

These routes are implemented by `native/library-server`.

### `GET|PUT /api/providers/settings`

Rust headless-server only. Auth is required even though the legacy catalog and
media routes do not yet enforce pairing: send the server pairing token as
`Authorization: Bearer <token>`. Token comparison is constant-time.

`GET` returns a secret-redacted settings document plus current runtime
capabilities. `PUT` accepts the same non-secret settings plus write-only
`appSecret`, `myAnimeListClientSecret`, `myAnimeListAccessToken`, and
`bangumiAccessToken` fields. Omitting a secret keeps its current protected
value. The matching clear boolean removes it; a request cannot replace and
clear the same value together.

On Windows, secrets are stored under the locked server data directory in a
DPAPI-protected `provider-secrets.json` snapshot. Raw secrets are never
returned by the API or written to `server-settings.json`. A successful save
swaps the in-memory provider service, dandanplay resolver, and runtime status,
so no server restart is required.

Status codes:

- `200`: settings read or saved; response is always secret-redacted.
- `400`: malformed or invalid settings, or protected storage failed.
- `401`: missing or incorrect bearer token.
- `404`: route is unavailable when provider administration is not configured.
- `405`: method is not `GET` or `PUT`.

### `GET /api/providers/runtime`

Response: `200 application/json; charset=utf-8`.

Body shape:

```json
{
  "dandanplay": {
    "matchAvailable": false,
    "commentFetchAvailable": false,
    "authenticated": false,
    "reasonCode": "missing-credentials"
  },
  "myAnimeList": {
    "searchAvailable": false,
    "listReadAvailable": false,
    "listWriteAvailable": false,
    "authenticated": false,
    "reasonCode": "missing-client-id"
  },
  "bangumi": {
    "searchAvailable": true,
    "listReadAvailable": false,
    "listWriteAvailable": false,
    "authenticated": false,
    "reasonCode": "public-search"
  }
}
```

The exact booleans and reason codes depend on `server-settings.json`.

### `GET /api/providers/search`

Query parameters:

- `title`: required, non-blank.
- `limit`: optional integer `1..50`, default `10`.
- `episodeCount`: optional positive integer.
- `startYear`: optional integer `1900..2200`.
- `providers`: optional comma-separated provider list. Accepted names are
  `myanimelist`, `my_anime_list`, `mal`, `bangumi`, `bgm`, `dandanplay`,
  and `dan_dan_play`.

Success: `200 application/json; charset=utf-8`.

Body shape: array of `ExternalAnimeMatchCandidate`:

```json
[
  {
    "anime": {
      "id": { "provider": "BANGUMI", "value": 1 },
      "titles": { "primary": "Title", "alternateNames": [] },
      "episodeCount": 12,
      "startYear": 2024,
      "imageUrl": "https://example.invalid/poster.jpg",
      "summary": "Text",
      "externalLinks": []
    },
    "confidence": 0.8,
    "matchedTitle": "Title",
    "evidence": []
  }
]
```

Status codes:

- `200`: search completed.
- `400`: invalid `title`, `limit`, `episodeCount`, `startYear`, or
  provider; text body.
- `500`: unhandled hook exception; text body `Request failed.`

Provider client failures are swallowed inside the search service; healthy
provider results are still returned.

### `GET /api/providers/dandanplay/resolve`

Query parameters:

- `mediaId`: required, non-blank.
- `episodeId`: optional positive integer.
- `withRelated`: optional boolean. Accepted true values are `true`, `1`,
  `yes`; accepted false values are `false`, `0`, `no`; default is `true`.

Success: `200 application/json; charset=utf-8`.

Body shape:

```json
{
  "mediaId": "episode-id",
  "fingerprint": {
    "fileName": "Episode 01.mkv",
    "fileHash": "normalized-hash",
    "fileSizeBytes": 123,
    "videoDurationSeconds": 1440
  },
  "matches": [],
  "selectedMatch": null,
  "commentCount": 0,
  "comments": []
}
```

Comment objects use:

```json
{
  "id": "comment-id",
  "timestampMs": 1000,
  "text": "Hello",
  "style": {
    "colorArgb": "4294967295",
    "mode": "SCROLLING",
    "size": "NORMAL"
  }
}
```

Quirk: provider-route comment `style.colorArgb` is a string. The
`/api/danmaku/{mediaId}` route uses a numeric `colorArgb`.

Status codes:

- `200`: resolve completed.
- `400`: invalid `mediaId`, `episodeId`, or `withRelated`; text body.
- `404`: media item is unpublished or the file is missing; text body.
- `502`: dandanplay request failed; text body.
- `500`: unhandled hook exception; text body `Request failed.`

### `GET /api/providers/dandanplay/search`

Rust-server extension for the manual match picker.

Query parameters:

- `keyword`: required, non-blank anime title keyword.

Success: `200 application/json; charset=utf-8` with
`{"animes": [{"animeId": 999, "animeTitle": "…", "typeDescription": "…",
"episodes": [{"episodeId": 9990001, "episodeTitle": "…"}]}]}`.

Status codes:

- `200`: search completed.
- `400`: missing `keyword`; text body.
- `502`: dandanplay request failed or the resolver is unavailable; text
  body.

### `GET /api/providers/dandanplay/bangumi`

Rust-server extension (2026-07-13) for the library's anime information
page. Proxies dandanplay `/api/v2/bangumi/{animeId}`.

Query parameters:

- `animeId`: required positive integer.

Success: `200 application/json; charset=utf-8`.

Body shape:

```json
{
  "animeId": 999,
  "animeTitle": "Example Anime",
  "typeDescription": "TV Series",
  "summary": "Synopsis text.",
  "rating": 7.7,
  "isOnAir": false,
  "tags": ["Mystery", "School"],
  "episodes": [
    {
      "episodeId": 9990001,
      "episodeTitle": "Episode 1",
      "airDate": "2017-04-05T00:00:00"
    }
  ],
  "onlineDatabases": [
    { "name": "Bangumi.tv", "url": "https://bangumi.tv/subject/1" }
  ]
}
```

Empty collections and unknown fields are omitted; `rating` is omitted
when dandanplay reports `0` (unrated).

Status codes:

- `200`: profile returned.
- `400`: missing or non-positive `animeId`; text body.
- `502`: dandanplay request failed or the resolver is unavailable; text
  body.

### Provider account routes

All account routes require `Authorization: Bearer <pairing token>`.

- `GET /api/providers/accounts` returns normalized MAL and Bangumi connection
  states (`CONNECTED`, `DISCONNECTED`, `NEEDS_RECONNECT`, or `UNAVAILABLE`),
  redacted identity, last verification time, and the official Bangumi token
  URL.
- `POST /api/providers/accounts/myanimelist/oauth/start` creates a single-use,
  ten-minute OAuth state and PKCE verifier. It returns `flowId`,
  `authorizationUrl`, and the fixed callback
  `http://127.0.0.1:18765/api/oauth/myanimelist/callback`.
- `POST /api/providers/accounts/myanimelist/oauth/complete` accepts `flowId`,
  `state`, and `code`; the server validates state, exchanges the code,
  validates `/users/@me`, and stores encrypted access/refresh tokens.
- `DELETE /api/providers/accounts/myanimelist` disconnects MAL.
- `PUT /api/providers/accounts/bangumi` accepts `{ "accessToken": "..." }`,
  validates `/v0/me`, and stores the token encrypted.
- `DELETE /api/providers/accounts/bangumi` disconnects Bangumi.

Disconnecting never deletes local mappings or playback progress. MAL tokens
are refreshed before readback/sync when they are within one minute of expiry.

### Provider tracking routes

All tracking routes require the pairing bearer token. `GET
/api/providers/tracking` returns persisted mappings plus a no-write preview.
`PUT|DELETE /api/providers/tracking/mapping` persists or removes a series
mapping. `POST /api/providers/tracking/readback` reads mapped provider state.
`POST /api/providers/tracking/sync` accepts the exact previewed
`expectedUpdates`; it returns `409` if the preview changed.

`POST /api/providers/tracking/conflicts/import` accepts:

```json
{
  "localSeriesId": "series-id",
  "animeId": { "provider": "BANGUMI", "value": 1 },
  "expectedExternalWatchedEpisodes": 3
}
```

The route succeeds only while that exact provider-ahead conflict still exists.
It marks the first N canonical local episodes watched, skips already-watched
episodes, preserves known duration, and otherwise uses a 60-second synthetic
duration. It never writes provider-ahead progress back to the provider.

The former `/api/providers/list/entry` direct read/write route is removed;
provider writes only occur through previewed tracking sync.

## UDP Discovery

`LocalLibraryDiscoveryAnnouncer` sends UDP datagrams every 1,500 ms after
the host starts it. The default destination port is `8687`.

Destination addresses:

- `255.255.255.255`
- each non-loopback, up network interface broadcast address

Packet encoding:

- UTF-8 JSON bytes
- produced by `Json.encodeToString(LanLibraryServerAnnouncement(port))`
- no trailing newline

Domain shape:

```json
{
  "protocol": "danmaku-library",
  "version": 1,
  "port": 8686
}
```

Actual current bytes omit default fields. For the default HTTP port, the
payload is:

```json
{"port":8686}
```

Receivers decode the omitted defaults as `protocol = "danmaku-library"` and
`version = 1`. If present, `protocol` must equal `danmaku-library`,
`version` must equal `1`, and `port` must be in `1..65535`.
