# External Anime Mapping and Tracking Tasks

Scope: MyAnimeList, Bangumi, and future external list providers. Dandanplay remains the current metadata/poster source, but the domain and desktop store already support provider-neutral anime IDs and mappings.

Status legend:

- `[x]` Done
- `[~]` In progress
- `[ ]` Not started

## Phase 1 - Provider-Neutral Foundation

- `[x]` Model external anime providers, IDs, titles, metadata, mappings, and tracking updates.
- `[x]` Persist external anime metadata cache, series mappings, and item mappings in the desktop library DB.
- `[x]` Derive external list progress updates from local watch state and external mappings.
- `[x]` Build provider-neutral sync plans with eligible updates and skipped mapping reasons.
- `[x]` Add desktop task/status surfaces for pending external sync actions.
- `[x]` Add diagnostics for mapping confidence, sync eligibility, and skipped sync reasons.

## Phase 2 - Manual Mapping UX

- `[x]` Add manual "Link external anime" action on the desktop series detail panel.
- `[x]` Show current Dandanplay, MyAnimeList, and Bangumi IDs in the inspector.
- `[x]` Allow removing/replacing a manual mapping without deleting cached metadata.
- `[x]` Support item-level correction when one local folder contains episodes from multiple anime.

## Phase 3 - Provider Search

- `[x]` Implement MyAnimeList anime search client behind a provider contract.
- `[x]` Implement Bangumi anime search client behind the same contract.
- `[x]` Rank provider search results with existing `rankExternalAnimeMatches` domain logic.
- `[x]` Cache fetched MAL/Bangumi metadata with provider-specific IDs and source timestamps.

## Phase 4 - External Progress Sync

- `[x]` Add provider auth/settings for MyAnimeList.
- `[x]` Add desktop MyAnimeList OAuth authorization callback and encrypted token storage.
- `[x]` Add provider auth/settings for Bangumi.
- `[x]` Convert local progress into provider list status, watched episode count, and optional score updates.
- `[x]` Add dry-run preview before writing external list state.
- `[x]` Add provider write clients for MyAnimeList and Bangumi progress/list updates.
- `[x]` Add provider readback/import for MyAnimeList and Bangumi list entries before writes.
- `[x]` Surface provider readback status and score in the desktop Tracking inspector.
- `[x]` Add an explicit desktop sync action that writes ready updates and surfaces failures.
- `[x]` Add conflict handling for external progress ahead of local progress.
- `[x]` Resolve external-progress conflicts by importing provider watched counts into local progress.
- `[x]` Add retry/backoff and user-visible sync failure state.
- `[x]` Persist external list readback and sync failure state across desktop relaunch.

## Phase 5 - Auto Mapping Suggestions

- `[x]` Extend provider-neutral match queries so a local series can search by
  primary title plus alternate dandanplay/Bangumi/MAL titles.
- `[x]` Preserve provider external links in metadata models, including
  dandanplay `bangumiUrl`/`onlineDatabases` and parsed MAL/Bangumi URLs when
  present.
- `[x]` Improve title parsing: keep MAL `alternative_titles.en`,
  `alternative_titles.ja`, and synonyms; enrich Bangumi candidates by fetching
  `/v0/subjects/{id}` and parsing `infobox` aliases such as `别名`; classify
  dandanplay language-tagged titles into Japanese, English, Chinese, and
  alternates.
- `[x]` Upgrade match ranking to score across all query/candidate titles and
  emit evidence labels for title, episode-count, year, and trusted external-link
  matches.
- `[x]` Add a desktop suggestion service that scans unmapped series on demand,
  uses cached dandanplay metadata as an extra signal, searches configured MAL
  and Bangumi providers, and returns provider-grouped candidates.
- `[x]` Expose read-only MAL/Bangumi mapping search through the headless
  server as authenticated `/api/providers/search`, with a typed web API
  helper for later web UI mapping controls.
- `[x]` Auto-save only high-confidence suggestions as
  `ExternalAnimeMappingSource.AUTO`; never overwrite manual mappings, and send
  ambiguous or medium-confidence candidates to review.
- `[x]` Add desktop UI actions for selected-series suggestions and a bulk
  "suggest missing mappings" workflow with confidence/evidence display.

Default thresholds:

- Auto-link when confidence is at least `0.93` and the top-candidate margin is
  at least `0.12`, or when a trusted provider URL maps exactly and no evidence
  contradicts it.
- Review when confidence is at least `0.60`.
- Reject candidates below `0.60`.

## Phase 6 - QA

- `[x]` Unit-test mapping ranking and tracking update derivation.
- `[x]` Unit-test external-progress conflict import into local watched progress.
- `[x]` Unit-test desktop mapping, readback, and sync-failure persistence migrations with MAL and Bangumi IDs.
- `[x]` Integration-test provider clients with recorded/fake responses.
- `[x]` Unit-test auto-mapping confidence bands, ambiguous-result rejection,
  provider alias parsing, and manual-mapping overwrite protection.
- `[ ]` Manual QA: map series to MAL and Bangumi, play episodes, sync progress, relaunch, and verify persisted state.

## Verification Log

- 2026-06-09: Created this milestone after confirming MAL/Bangumi provider IDs, mapping models, and desktop DB storage exist, while live provider search/auth/list sync is not implemented yet.
- 2026-06-09: Added provider-neutral domain logic to derive external tracking updates from local series watch state and mappings; this intentionally stops before provider auth/API writes.
- 2026-06-09: Added provider-neutral external tracking sync plans that report update candidates, unmapped local series, and stale mappings that no longer match a local series.
- 2026-06-09: Added provider-neutral tracking plan summaries and labels for UI/diagnostic surfaces, including provider names, update counts, skip counts, and human-readable skip reasons.
- 2026-06-10: Added desktop library rail and workspace status surfaces for provider-neutral external tracking sync plan summaries.
- 2026-06-10: Added desktop inspector controls for manual MAL/Bangumi series links, Dandanplay episode corrections, and mapping removal/replacement without deleting cached metadata.
- 2026-06-10: Added desktop provider-search clients for MyAnimeList and Bangumi, a provider-neutral search/cache service, and parser tests with fake HTTP responses. MyAnimeList search requires a client ID; Bangumi search uses its public v0 subject search endpoint with a User-Agent.
- 2026-06-10: Added desktop Profile settings and encrypted credential storage for MyAnimeList client/access tokens and Bangumi base URL/User-Agent/access tokens.
- 2026-06-10: Added a desktop External Sync dry-run view that previews provider-neutral status and watched-episode updates plus skipped mapping reasons before any external list write is attempted.
- 2026-06-10: Added provider-neutral external-progress conflict detection, sync failure retry/backoff metadata, and desktop External Sync sections for conflicts and failures.
- 2026-06-10: Added the desktop MyAnimeList OAuth browser flow with local callback handling, encrypted access/refresh token persistence, fake token-exchange tests, public callback hook tests, and verified Phase 5 automated coverage. Manual live-account QA remains.
- 2026-06-10: Added provider write clients for MyAnimeList `my_list_status` updates and Bangumi collection progress updates, plus fake HTTP coverage for auth headers, payload mapping, and response parsing. `:apps:desktop-windows:desktopTest` passed.
- 2026-06-10: Wired the desktop External Sync screen to a deliberate `Sync ready updates` action backed by saved MAL/Bangumi tokens, with session-visible failures and retry timing. `:apps:desktop-windows:compileKotlinDesktop` and `:apps:desktop-windows:desktopTest` passed.
- 2026-06-17: Added MyAnimeList/Bangumi list readback/import for mapped anime, tracking-table provider progress, manual readback refresh, and pre-write readback conflict checks so newer provider progress is not overwritten. `:apps:desktop-windows:compileKotlinDesktop` and `:apps:desktop-windows:desktopTest` passed.
- 2026-06-17: Persisted external list entries and sync failures in the desktop catalog database, loaded them into shell state on startup, and added migration/relaunch-style store coverage. `:apps:desktop-windows:compileKotlinDesktop` and `:apps:desktop-windows:desktopTest` passed.
- 2026-06-17: Added a conservative local progress import for provider-ahead conflicts: the Tracking inspector's conflict resolution writes watched progress rows for the provider-reported watched episode count without changing unrelated local episodes. `:shared:domain:jvmTest`, `:apps:desktop-windows:compileKotlinDesktop`, and `:apps:desktop-windows:desktopTest` passed.
- 2026-06-19: Logged the auto mapping/suggestions plan. The next implementation
  pass should enrich MAL/Bangumi/dandanplay aliases, add confidence/evidence
  scoring, add an on-demand suggestion service, and keep auto-save conservative
  with manual mappings protected.
- 2026-06-19: Implemented auto mapping suggestions. Match queries now carry
  alternate titles and trusted MAL/Bangumi links from cached metadata; MAL,
  Bangumi, and dandanplay parsers preserve richer titles/provider links;
  candidate ranking emits evidence and trusts exact external links; the desktop
  metadata dialog shows evidence; and the Tracking tab can run a conservative
  "Suggest missing" scan that auto-saves only safe `AUTO` mappings while
  keeping ambiguous matches in diagnostics for review. Targeted verification:
  `:shared:domain:jvmTest` and `:apps:desktop-windows:desktopTest` with
  `ExternalAnimeTrackingTest`, `ExternalAnimeSearchClientsTest`,
  `DandanplayDanmakuClientTest`, and `DesktopExternalAnimeMappingSuggesterTest`.
- 2026-06-19: Ran non-mutating live provider QA against MyAnimeList and
  Bangumi search. Raw provider search found expected candidates for Frieren
  English/romaji/Chinese cases, but also proved raw title order is not reliable:
  MAL ranks Chinese Frieren fourth, Bangumi ranks Frieren season 2 above season
  1 for romaji-only search, and a messy Apothecary Diaries S02 release filename
  ranks season 1 above season 2 on MAL while Bangumi returns no useful result.
  This confirms auto-link should depend on enriched metadata titles, trusted
  provider links, and conservative margins rather than raw search order. The QA
  pass also found and fixed a partial-library issue: suggestion queries now use
  cached provider metadata episode counts, not local file counts, so a folder
  with only one downloaded episode is not treated as a one-episode anime.
  Verification after the fix: `.\gradlew.bat --no-daemon :shared:domain:jvmTest
  :apps:desktop-windows:desktopTest`.
- 2026-06-19: Ran app-level Tracking-tab screenshot QA and a non-mutating live
  suggestion pass against the current desktop catalog. The live pass found that
  one MyAnimeList `invalid q` provider response could abort the whole bulk
  suggestion scan; the suggester now bounds bulk title variants, queries each
  provider independently, and records provider failures as diagnostics while
  preserving suggestions from healthy providers. Current live read-only result:
  3 cached dandanplay-backed series scanned, 2 safe auto-link suggestions for
  Witch Hat Atelier, and 4 review suggestions where episode counts disagree.
  Verification: `.\gradlew.bat --no-daemon :shared:domain:jvmTest
  :apps:desktop-windows:compileKotlinDesktop :apps:desktop-windows:desktopTest`
  plus `:apps:desktop-windows:run --args="--tab=Tracking
  --qa-screenshot-dir=S:\Projects\Danmaku\build\qa-screenshots
  --qa-screenshot-name=tracking-qa-after-fix.png --qa-screenshot-delay-seconds=4"`.
- 2026-06-20: Added the headless server's authenticated read-only provider
  mapping search endpoint for MAL/Bangumi suggestions, a typed web API helper,
  and server tests for token/parameter validation plus provider-failure
  isolation. Dandanplay resolve is now covered by a follow-up endpoint;
  remaining provider network work is external list read/write actions.
- 2026-06-20: Added the headless server's authenticated dandanplay match/comment
  resolve endpoint for catalog media, backed by the shared JVM dandanplay client
  and covered by server tests for auth, parameter validation, library lookup,
  selected match, and serialized comments. Remaining provider network work is
  external list read/write actions plus broader provider admin controls.
- 2026-06-20: Added typed web API models/helpers for the headless dandanplay
  resolve route plus provider runtime readiness chips and a web player dandanplay
  preview panel that shows selected match, match count, comment count, and
  sample comments for the current media.
- 2026-06-20: Moved MAL/Bangumi external list tracking clients from the desktop
  package into `shared:library-server-core` under `app.danmaku.provider.external`,
  leaving desktop compatibility typealiases in place and adding shared JVM tests
  for list readback/write payloads. Next headless step is route wiring around
  these shared clients, not another provider implementation.
- 2026-06-20: Added the headless server's authenticated `/api/providers/list/entry`
  route for MAL/Bangumi list readback and updates, backed by the shared tracking
  clients. Headless settings can load raw provider access tokens in memory while
  persisting only non-secret credential flags, and the web API module now has
  typed read/write helpers for later provider admin controls. Verification:
  `.\gradlew.bat --no-daemon :shared:domain:jvmTest :shared:library-server-core:jvmTest :apps:library-server-windows:test`.
- 2026-06-20: Added manual MAL/Bangumi external-list read/write controls to
  the web player panel, gated by provider runtime list readiness and backed by
  the typed web API helpers. Verification: `npm run build` in `apps/web-ui`.
- 2026-06-20: Made the web external-list panel mapping-aware by using
  catalog `animeMetadata.externalLinks` to auto-fill MAL/Bangumi IDs and expose
  quick-select mapped-ID buttons when a selected episode has provider links.
  Verification: `npm run build` in `apps/web-ui`.
- 2026-06-20: Added web provider mapping search controls for the selected
  episode. The web player can query MAL/Bangumi through the authenticated
  headless provider search route, show candidate titles/evidence, and copy a
  chosen candidate into the external-list read/write controls. Verification:
  `npm run build` in `apps/web-ui` and
  `tools/windows/run-headless-web-ui-qa.ps1`.
- 2026-06-21: Added a basic web danmaku video overlay for loaded dandanplay
  comments, with overlay enable, density, and offset controls in the web player.
  The overlay is client-side and uses the existing authenticated dandanplay
  resolve route. Verification: `npm run build` in `apps/web-ui` and
  `tools/windows/run-headless-web-ui-qa.ps1`.
- 2026-06-21: Split the web danmaku overlay timing, density, lane, mode, and
  color mapping logic into `apps/web-ui/src/danmakuOverlay.ts`, keeping
  `App.tsx` focused on player state and rendering. Verification: `npm run build`
  in `apps/web-ui` and `tools/windows/run-headless-web-ui-qa.ps1`.
