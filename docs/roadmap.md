# Roadmap

Windows, Android mobile/tablet, and Android TV are the first-class targets.
macOS now has an experimental Rust-native development slice; promotion remains
deferred until its release and provider gaps are closed.

## 1. Native Windows Release Quality

Status: implemented vertical slice; broader manual QA remains.

- Keep the unified Rust player/server package reproducible.
- Keep the signed per-user installer, stable updater feed, delta generation,
  and explicit update/restart UX reproducible; exercise two-version upgrade,
  corruption rejection, rollback, and uninstall preservation in release QA.
- Validate fullscreen, resize, aspect ratio, hardware decoding, 4K playback,
  multiple displays, resume, and background-host ownership.
- Improve startup, catalog, playback, and failure diagnostics without adding a
  second desktop runtime.

## 2. Trusted-LAN Client Reliability

Status: implemented; continuing QA and polish.

- Keep catalog, streaming, subtitles, posters, discovery, and progress stable.
- Complete mobile/tablet viewport QA and replacement-class physical TV QA.
- Keep Android TV focus, cache, danmaku, screenshot, and Macrobenchmark gates
  green.

## 3. Danmaku And Metadata Quality

Status: core provider/cache path implemented.

- Add richer filtering, offset, density, style, and per-series preferences.
- Keep unmatched/stale/conflicting provider state explainable and repairable.
- Improve metadata, poster freshness, alternate titles, and episode ordering.

## 4. External Tracking

Status: implementation ready for approved live-account QA.

- Keep the native Accounts & Tracking journey, completion prompt, persistent
  mappings, and conservative provider-ahead import easy to understand.
- Validate MyAnimeList and Bangumi read/write flows with real accounts.
- Confirm conflict, retry, failure, and relaunch behavior.
- Keep every write previewed and explicitly acknowledged.

## 5. Authorized Downloads

Status: not implemented in the active applications.

- Define authorized source contracts before adding an engine.
- Implement queue execution, pause/resume, retry, cache management, and
  diagnostics without DRM circumvention or unauthorized search behavior.

## 6. Platform Expansion

Status: macOS development slice implemented; release promotion deferred.

- Re-audit the pinned libmpv dependency before changing its producer or hash.
- Add a reviewed redistributable macOS libmpv artifact or a documented external
  dependency policy, then release signing and notarization.
- Add cross-platform provider HTTPS and protected credential persistence before
  treating the local macOS server as feature-complete.
- Run supervised Apple Silicon and Intel playback/resize/fullscreen/hardware
  decode QA before promotion.
- Consider Linux, iOS/iPadOS, and broader web delivery only after the
  first-class release gates pass.
