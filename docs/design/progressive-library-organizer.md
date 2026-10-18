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

- Folders provides selection mode with checkboxes, Shift-click ranges, Select
  all here, and Clear selection; clicking a folder name still opens it.
- Preserve selection across navigation and display the resolved video count.
  The most specific rule wins: a folder can be selected while a subfolder or
  file inside it is left out, and folders show a partial mark.
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
  cancellation, retry, and errors that leave manual review usable. Each result is
  merged into the current draft as it arrives; edits never cancel a run. Reuse
  results for unchanged fingerprints. Results never retitle a group the user
  named, and never replace a match the user chose.
- Call matching/search primitives directly: no comment downloads or first-result
  selection. A unique candidate may populate a proposal; ambiguity requires a
  choice. Never identify an entire folder from a single episode.
- Display provider title and available episode information separately from folder
  title and season. Explicit season evidence takes precedence over the Season 1
  suggestion; contradictory evidence requires review.
- Allow new searches, alternative candidates, and manual series/season assignment
  without a provider match. Selected files can be excluded, split into a new group,
  or reassigned to an existing group. Folder grouping never merges provider IDs.
- OVAs/specials may link to a provider series without an episode. Users can
  explicitly reuse a known draft provider identity for selected videos, retaining
  their folder/season assignments. Series-only links persist without episode
  IDs/titles and suppress automatic episode matching/comment reuse during
  playback until an episode is explicitly chosen. Search-only series candidates
  have the same semantics; no arbitrary provider identity may be injected.
- Proposed identities remain draft data until the group is approved or marked
  done, which writes the metadata store without downloading comments. Undoing
  file moves does not undo saved identification.

## Persistent review queue

- Dedicated window, modeled on FileBot and Sonarr manual import: a searchable
  series queue (To do / Ready / Skipped / Done / All with counts), the selected
  series with tabs for Videos, Extras and Moves, and a fixed action bar. The bar
  always states what approval moves or lists every blocker.
- One review group represents one series folder and may include multiple seasons.
  Groups are keyed by normalized title without season markers; renaming a group
  to an open group's title merges them. Files are kept in natural path order and
  the queue is sorted by title.
- Initial titles: provider title, else the release title parsed from the file
  name when the folder holds several shows (download dumps), else the folder name.
  Season markers in titles (`S2`, `Season 2`, `2nd Season`, `第二季`) become season
  numbers; contradictory evidence requires review.
- A group links to one provider series (search result or fingerprint match);
  each video keeps its own fingerprint episode when it belongs to that series,
  otherwise it becomes series-only. **Use folder name** accepts the folder title
  without a provider identity.
- Explain automatically selected subtitles and preserve manual deselection.
  Excluding a video also excludes its dependent companions. Artwork and other
  nearby files remain opt-in; ticking one with a single candidate series assigns
  that series folder. Ambiguous companions require an explicit owner; each
  companion participates in at most one executed move.
- Edits are intent operations applied by the server to the current draft; the
  client never rebases a local copy. Operations are sent in order, one batch at a
  time; a rejected batch reports its reason and resynchronizes.
- Previews are requested per group when that group's decisions change. A plan
  stays executable while the group's decision signature and the catalog are
  unchanged, so edits and identification in other groups never expire it.
- **Approve & next** moves only the displayed series and advances to the next
  to-do series. **Skip** advances without moving. Series already in place offer
  **Mark done**, which needs no identity. Approving or marking done saves the
  group's identities to the metadata store.
- Detect existing/duplicate destinations before executing. Users may exclude
  conflicting files or correct assignments, never silently overwrite or rename.
- Opening a new selection while a review exists offers to keep it or to start
  over; finished moves stay undoable either way.
- Persist selection, assignments, exclusions, companion decisions, destination,
  search results, skipped and completed groups. Restore decisions, never
  executable approvals. Revalidate after restart, scans, and completed moves.

## Interfaces and ownership

- Organizer wire models, the sync state machine, the pure review model, the
  selection model and the window live in separate client modules.
- Persist one server-owned draft with ID/revision, immutable media snapshot,
  assignments/candidates, destination, companions, group search results, and
  skipped/completed groups.
- `/api/library/organize/draft`: POST create (`replace` discards an unfinished
  review), GET read, DELETE discard. `/draft/ops`: POST ordered intent operations.
- `/identify`: POST start/retry (explicit media IDs are identified again);
  `/identify/cancel`: POST stop. `/search`: POST one series search for a group.
- `/preview`: POST draft and group ID; returns a transient manifest with the
  group signature. `/execute` requires its plan ID, group ID, and exact approved
  move list, and saves the group's identities.
- `/status`: GET transfer/identification progress (including whether a provider
  is configured) and the saved draft. `/cancel`, `/undo`, `/recover`: POST.
- Require native loopback access and a bearer capability for organizer operations.
  `/session` bootstraps that memory-only capability for native loopback requests;
  browser Origin/Fetch Metadata requests and LAN peers are rejected. Do not log
  the capability. This boundary is isolated from pairing-free trusted-LAN access.
- Rendering never enumerates files, hashes videos, or calls providers.

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
