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
- `[x]` Add provider auth/settings for Bangumi.
- `[ ]` Convert local progress into provider list status, watched episode count, and optional score updates.
- `[ ]` Add dry-run preview before writing external list state.
- `[ ]` Add conflict handling for external progress ahead of local progress.
- `[ ]` Add retry/backoff and user-visible sync failure state.

## Phase 5 - QA

- `[ ]` Unit-test mapping ranking and tracking update derivation.
- `[ ]` Unit-test desktop mapping persistence migrations with MAL and Bangumi IDs.
- `[ ]` Integration-test provider clients with recorded/fake responses.
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
