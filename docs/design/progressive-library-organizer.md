# Library Organizer: Selection, Identification, and Review

Implementation status and verification: [implementation checklist](progressive-library-organizer-implementation.md).

## Product contract

Replace the root-wide preview dialog with **select → identify → review → organize**.
Use the established separation between loading, matching, and applying changes
shown by [FileBot](https://www.filebot.net/forums/viewtopic.php?t=11790).

Users select multiple folders/files, correct identification and grouping, and
approve one series at a time without losing their place or edits. Sources may
span configured roots; destinations may be in another root or on another drive.

Confirmed defaults:

- Per-series **Approve and next**; never implicit approval of the full queue.
- Automatically selected unambiguous external subtitles, including language variants.
- Server-owned review drafts survive desktop/server restarts.
- Unknown seasons display **Season 1 — suggested**; Season 0 supports specials.
- Preserve filenames: `<destination>/<series>/Season <number>/<filename>`.
- Never overwrite, automatically rename conflicts, or remove empty folders.

## Selection and destination

- Folders provides selection mode, checkboxes, Ctrl-click toggles, Shift-click
  ranges, Select all visible, and Clear selection. Normal browsing/playback
  remains available outside selection mode.
- Preserve selection across navigation and display selected folder/resolved video
  counts across all folders. Folder selection includes catalogued descendants;
  overlapping folders/files deduplicate by media ID. Individual exclusions win.
- **Organize selected** and **Organize this folder** explicitly define scope.
  Empty selection never falls back to the whole library.
- Snapshot media IDs and source identities when creating a draft; later scans
  cannot silently add files. Refresh the folder first for uncatalogued videos.
- Display an editable absolute destination with a native folder picker. Require
  containment in a configured root. Default a single-root selection to that root;
  mixed-root selections require a choice. Do not append an `Anime` folder.

## Identification and correction

- Reuse catalog identities. Automatically attempt dandanplay fingerprint matching
  for unmatched selected videos, followed by parsed-title series search if no
  usable fingerprint candidate is found.
- Identification runs sequentially off the UI thread, with per-file progress,
  cancellation, retry, and errors that leave manual review usable. Reuse results
  for unchanged fingerprints and reject results from superseded draft revisions.
- Call matching/search primitives directly: no comment downloads or first-result
  selection. A unique candidate may populate a proposal; ambiguity requires a
  choice. Never identify an entire folder from a single episode.
- Display provider title and available episode information separately from folder
  title and season. Explicit season evidence takes precedence over the Season 1
  suggestion; contradictory evidence requires review.
- Allow new searches, alternative candidates, and manual series/season assignment
  without a provider match. Selected files can be excluded, split into a new group,
  or reassigned to an existing group. Folder grouping never merges provider IDs.
- Proposed identities remain draft data until **Save identification** writes the
  metadata store. Saving identity requires neither moves nor comment downloads.
  Undoing file moves does not undo independently saved identification.

## Persistent review queue

- Dedicated workspace: searchable queue, per-file details, source/destination
  columns, and Pending / Needs review / Blocked / Skipped / Completed filters.
- One review group represents one series folder and may include multiple seasons.
  Preserve per-file assignments and every other group's edits.
- Explain automatically selected subtitles and preserve manual deselection.
  Excluding a video also excludes its dependent companions. Artwork and other
  nearby files remain opt-in. Ambiguous companions require an explicit owner;
  each companion participates in at most one executed move.
- Debounce valid edits before saving/previewing; show validation inline. Disable
  approval while edits, identification, or preview generation are unresolved.
  Any selection, assignment, destination, or companion change invalidates approval.
- Detect existing/duplicate destinations before executing. Users may exclude
  conflicting files or correct assignments, never silently overwrite or rename.
- **Approve and next** moves only the displayed series, marks it Completed,
  refreshes catalog data, and advances while preserving other edits. Skip advances
  without moving. Failed/skipped groups remain reviewable and retryable.
- Already-organized videos do not prevent moving missing companions.
- Persist selection, assignments, exclusions, companion decisions, destination,
  queue position, and completion references. Restore decisions, never executable
  approvals. Revalidate after restart, scans, and completed moves.

## Interfaces and ownership

- Keep organizer wire models, selection logic, and UI state in focused modules.
- Persist one server-owned draft with ID/revision, immutable media snapshot, stable
  group IDs, assignments/candidates, destination, companions, skipped/completed
  groups, and active group. Updates require the current revision and atomic storage.
- `/api/library/organize/draft`: POST create, GET read, PUT update, DELETE discard.
- `/identify`: POST start/retry; `/identify/cancel`: POST cancel;
  `/identify/save`: POST explicitly save selected candidate identities.
- `/preview`: POST draft ID, revision, and optional review group. Return a transient
  manifest bound to draft/catalog revisions. `/execute` requires its plan ID,
  group ID, and exact approved move list.
- `/status`: GET transfer/identification progress and saved draft. `/cancel`,
  `/undo`, and `/recover`: POST the corresponding operation.
- Require native loopback access and a bearer capability for organizer operations.
  `/session` bootstraps that memory-only capability for native loopback requests;
  browser Origin/Fetch Metadata requests and LAN peers are rejected. Do not log
  the capability. This boundary is isolated from pairing-free trusted-LAN access.
- Cache folder expansion/queue membership by catalog/draft revision and virtualize
  long lists. Rendering never enumerates files, hashes videos, or calls providers.

## Transfer and recovery

- Journal explicit source/destination roots and paths. Include roots in catalog
  revision validation; preserve media/subtitle IDs and playback progress while
  updating root labels, relative paths, and file maps.
- Serialize scans and transfers. Recovery-required state blocks scans and new
  transfers until the transaction is resolved.
- Validate configured-root containment, symlinks/reparse points, source identity,
  free space, and conflicts. Same-volume transfers use no-overwrite moves.
- Cross-volume transfers create exclusive destination-side staging files, copy
  with cancellation checkpoints, flush, and verify size/SHA-256 against the unchanged
  source. Stage/verify the complete series before removing any original. Publish
  without overwrite, then remove the verified source.
- Journal intent/completion around staging, publication, removal, and catalog commit.
  Record the intended catalog revision before saving it. Show file and byte progress.
- Cancel/failure rolls back the current series. Undo uses the same machinery in
  reverse and rejects changed content or occupied restore paths. Cleanup removes
  only transaction-owned files, never the only verified copy.
- Disconnected drives/changed files require recovery. Preserve evidence and offer
  retry rather than claiming restoration succeeded.
- Recover active legacy journals and preserve undo history before adopting v2.
  Legacy history retains only its originally recorded verification evidence;
  new transfers record SHA-256 and file identity.

## Delivery and acceptance

Deliver four working layers: selection/drafts; identification/review;
cross-root/cross-drive transfers; integrated localization/documentation.

Automated coverage must exercise overlapping selections/exclusions and multiple
roots; unique/ambiguous/missing matches and failures; cancellation/stale results;
manual assignments and season suggestions/conflicts; subtitle ownership; retained
edits/restart; approval invalidation; queue advancement; conflicts/changed sources;
cross-volume copy/undo; insufficient space, locked files, disconnected roots, and
interruption at every durable transfer phase. Use temporary libraries, local fake
providers, and injected volume boundaries.

Run `cargo fmt --all --check`, `cargo test --workspace`, and `git diff --check`, then
review task-scoped diff/status. English and Traditional Chinese are required.
Desktop GUI, physical cross-drive, and live-provider QA remain separately
approval-gated by AGENTS.md.

## Provider failures and independent window (2026-09-08)

The desktop organizer uses a separate native window that can be moved/resized
independently and appears on the taskbar. Closing review keeps the main player
open and allows pending draft writes to complete. Platforms without native
viewports use egui's embedded fallback.

Read the documented `errorCode` and `errorMessage` from HTTP-200 provider
failures, with `message` accepted for proxy responses. Ordinary match failures
fall back to series search. Quota/authentication/HTTP-503 failures pause instead
of generating a second request. After three consecutive other failures, stop
processing the batch as well. Preserve the pause reason and retry time in the
draft across restart; edits cannot clear the server-owned cooldown. Honor
`Retry-After` seconds or HTTP dates, otherwise require at least 60 seconds before
an explicit retry. This cooldown is not a promise that a daily quota has reset.
No automatic retry or provider-quota bypass occurs.

Pace files with cancellable waits. Batch retry skips existing candidates,
including ambiguous results; explicitly selected files can be searched again.
Keep the current review group when it still exists.

Provider contract: [dandanplay error handling](https://doc.dandanplay.com/open/).
