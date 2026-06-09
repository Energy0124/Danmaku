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
- `[ ]` Add desktop task/status surfaces for pending external sync actions.
- `[ ]` Add diagnostics for mapping confidence, sync eligibility, and skipped sync reasons.

## Phase 2 - Manual Mapping UX

- `[ ]` Add manual "Link external anime" action on the desktop series detail panel.
- `[ ]` Show current Dandanplay, MyAnimeList, and Bangumi IDs in the inspector.
- `[ ]` Allow removing/replacing a manual mapping without deleting cached metadata.
- `[ ]` Support item-level correction when one local folder contains episodes from multiple anime.

## Phase 3 - Provider Search

- `[ ]` Implement MyAnimeList anime search client behind a provider contract.
- `[ ]` Implement Bangumi anime search client behind the same contract.
- `[ ]` Rank provider search results with existing `rankExternalAnimeMatches` domain logic.
- `[ ]` Cache fetched MAL/Bangumi metadata with provider-specific IDs and source timestamps.

## Phase 4 - External Progress Sync

- `[ ]` Add provider auth/settings for MyAnimeList.
- `[ ]` Add provider auth/settings for Bangumi.
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
