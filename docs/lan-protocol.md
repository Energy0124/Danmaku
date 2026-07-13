# LAN Protocol

This document freezes the trusted-LAN wire contract implemented today by
`shared/library-server-core` and `apps/library-server-windows`. It records
current behavior, including quirks, for the Rust server migration.

The HTTP server is `com.sun.net.httpserver.HttpServer`. Routes are registered
as prefix contexts, so some handlers accept paths beyond the literal route
when they do not perform their own exact path check.

## Version Policy

- HTTP status uses `LanLibraryServerStatus.apiVersion`.
- The current API version is `1`.
- UDP discovery uses `LanLibraryServerAnnouncement.version`.
- The current discovery version is `1`.
- The Rust server may add fields that old clients can ignore.
- Incompatible route, body, status, or media behavior requires a version
  bump. The Rust migration must not need one.

## Shared Encoding

- JSON is UTF-8.
- Core server JSON responses use `Json.encodeToString` with default
  `kotlinx.serialization` settings. Default-valued fields are omitted.
- Headless provider hook JSON uses `Json { encodeDefaults = true }`.
- Core `sendStatus` errors have no response body and no JSON error envelope.
- Public hook validation errors are `text/plain; charset=utf-8` bodies with
  `Cache-Control: no-store`.
- Pairing-token route auth is not enforced today. `LocalLibraryServer`
  stores and persists a pairing token, but `HttpExchange.isAuthorized()`
  currently returns `true` for all core catalog, media, subtitle, poster,
  danmaku, and progress routes. `LanLibraryServerStatus.pairingRequired`
  is always `false`.
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
  "hostMode": "embedded-desktop",
  "providerSettings": null
}
```

Default fields are omitted on the wire. An embedded server with default
status currently emits `{}`. A web-enabled server emits only the changed
web fields. The headless server sets `hostMode` to `headless-server` and
may include `providerSettings`.

Status codes:

- `200`: status returned.
- `405`: method is not `GET`; empty body.

Quirk: the handler does not check the exact path, so the `/api/server/status`
context can answer prefix-matched paths the JDK server routes to it.

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

Hook routes are configured by the embedding host. The core server provides
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

## Headless Provider Hook Routes

These are installed by `apps/library-server-windows` on top of the core
public hook dispatch. They do not require pairing-token auth.

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

### `GET /api/providers/list/entry`

Query parameters:

- `provider`: required. Accepted names match provider search.
- `animeId`: required positive integer.

Success: `200 application/json; charset=utf-8`.

Body shape is `ExternalAnimeListEntry`:

```json
{
  "animeId": { "provider": "BANGUMI", "value": 1 },
  "status": "WATCHING",
  "watchedEpisodes": 3,
  "score": 8,
  "updatedAtEpochMs": 1700000000000
}
```

Status codes:

- `200`: entry returned.
- `400`: missing/unsupported provider, invalid `animeId`, or dandanplay
  provider; text body.
- `404`: external list entry was not found; text body.
- `409`: provider credentials are not configured; text body.
- `502`: provider request failed; text body.
- `500`: unhandled hook exception; text body `Request failed.`

### `POST /api/providers/list/entry`

Request body: `ExternalAnimeTrackingUpdate` JSON:

```json
{
  "animeId": { "provider": "BANGUMI", "value": 1 },
  "status": "WATCHING",
  "watchedEpisodes": 3,
  "score": 8,
  "trackingEnabled": true,
  "ratingEnabled": true
}
```

Success: `200 application/json; charset=utf-8`, body is the resulting
`ExternalAnimeListEntry`.

Status codes:

- `200`: update succeeded.
- `400`: malformed body or dandanplay provider; text body.
- `409`: provider credentials are not configured; text body.
- `502`: provider request failed; text body.
- `500`: unhandled hook exception; text body `Request failed.`

Other methods on `/api/providers/list/entry` reach the hook and return
`405 text/plain; charset=utf-8` with body `Method not allowed.`

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
