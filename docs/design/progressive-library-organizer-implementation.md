# Library organizer implementation checklist

Specification: [Selection, Identification, and Review](progressive-library-organizer.md).

## Delivery checklist

- [x] Server-owned draft CRUD and immutable selection snapshot.
- [x] Folder/file selection, explicit scope, destination picker, cross-root destinations.
- [x] Independent matching/search, manual assignment, candidate selection.
- [x] Persistent review queue, per-file seasons, split/reassign/exclude, approve/skip next.
- [x] Subtitle recommendations and explicit ambiguous-companion ownership.
- [x] Revision-bound previews, exact manifests, preserved/rebased local edits.
- [x] Cross-root catalog updates preserving IDs and shared mutation gate.
- [x] Cross-volume staging, identity-aware rollback/undo, recovery retry, legacy journal loading.
- [x] Native loopback capability; browser and LAN rejection.
- [x] Automated fixture verification and full workspace checks.
- [ ] Supervised desktop interaction/visual QA and physical cross-drive verification.

## Verification log

- Initial compile: `cargo check -p danmaku-player -p library-server --offline` passed.
- Initial organizer suite: 20 tests passed, including forced cross-volume copies,
  pre-commit interruption phases, committed-catalog recovery, changed-destination
  preservation, ID-preserving undo, draft restart, subtitle selection/exclusion,
  queue advancement, and stale identification results.
- Final verification (2026-09-07): `cargo fmt --all --check` passed;
  `cargo test --workspace` passed all 251 tests (4 core, 118 player,
  124 server, 5 mpv), plus doc-test targets. The existing unused-mut warning
  in `player-windows-mpv/src/locator.rs` remains unrelated to this change.
- Added regressions verify exact-manifest rejection, cancellation, true v1
  same-root history conversion and undo, restored draft signatures after an
  interrupted undo catalog commit, duplicate destinations across focused groups,
  and companion-only moves for already-organized videos.
- Additional fixture checks cover insufficient destination space, locked sources,
  destination races, unavailable roots and recovery retry, language subtitles,
  explicit metadata saving, fake-provider ambiguous and search fallback results,
  and cached large-list derivation. No elapsed-time benchmark is claimed.
- `git diff --check` and task-scoped diff/status review completed.
- No real library, live provider account, desktop GUI, emulator, or screenshot QA run.

## Maintenance notes

- The obsolete root-wide preview API and builder have been removed. Legacy
  journal recovery fixtures remain; executable plans are transient.
- Journal v1 history is read for recovery/undo. Journal v2 records file ownership,
  SHA-256, transfer state, and the intended catalog revision.
- Organizer capability authentication does not restore pairing for catalog,
  playback, Android, or trusted-LAN web administration APIs.

## Follow-up: identification failures and window independence (2026-09-08)

- [x] Preserve provider error codes/messages instead of displaying unknown error.
- [x] Search fallback after ordinary fingerprint failure; stop on quota/auth errors.
- [x] Persist cooldown and pause state; retain prior candidates during batch retry.
- [x] Stop after repeated failures and pace subsequent files with cancellable waits.
- [x] Native organizer viewport with independent position, size, and taskbar entry.
- [x] English/Traditional Chinese pause notice and retry countdown.
- [ ] Supervised detached-window interaction and live-provider quota QA.

Verification: fake-provider tests cover ordinary match failure followed by search,
HTTP-200 quota errors, HTTP-429 Retry-After, documented error fields, seconds and
HTTP-date cooldowns, persistence of paused drafts, untouched remaining files,
and retention of ambiguous candidates. `cargo fmt --all --check` passed;
`cargo test --workspace` passed all 255 tests (4 core, 118 player, 128 server,
5 mpv). `git diff --check` and task-scoped diff/status review passed. The existing
mpv locator unused-mut warning remains. No live-library retries, GUI QA, or
deployment were performed for this follow-up.

## Follow-up: series ownership and review usability (2026-09-14)

- [x] Explicit series ownership for nearby font files, subtitle ZIPs and artwork;
  files go directly in the series directory. Video ownership remains available
  for episode companions; each file has at most one owner and one move.
- [x] Per-video series-only identity and bulk reuse of known provider series;
  provider IDs stay separate from destination titles and seasons.
- [x] Persist series-only identification without an episode; automatic playback
  ignores old episode caches and never silently restores an episode mapping.
- [x] Preserve previews and permit editing during autosave/validation. Acknowledge
  the exact saved draft and rebase newer decisions against its submitted snapshot.
- [x] Numbered review steps, contextual bulk edits, selected counts, destination
  disclosure, and English/Traditional Chinese usage guide.
- [ ] Supervised GUI/localization/rapid-click QA and physical-drive checks.

Fixture verification covers multi-season series assets, restart, forced-copy
moves/undo, competing owner rejection, manual deselection, series-only candidate
reuse and rejection of unknown IDs, identity saving without moves, cached comment
suppression, explicit episode override, and edits made while a save is pending.
No live provider, real-library, desktop GUI, or screenshot QA was run.

Final verification: `cargo fmt --all --check` passed; `cargo test --workspace`
passed all 263 tests (4 core, 121 player, 133 server, 5 mpv), plus doc-test
targets. The existing mpv locator unused-mut warning remains unrelated.
Task-scoped diff/status review and `git diff --check` passed.

## Rework: review flow and window (2026-10-17)

Findings in the previous flow: any edit (including clicking a series) cancelled
background identification and discarded its results; files and the queue were
ordered by hashed IDs; a new selection was silently ignored while a draft
existed; approval was disabled without saying why; already-organized series and
unmatched folders could not be finished; whole-draft autosave raced provider
updates; the window was one scrolling column with nested scroll areas.

- [x] Intent operations (`/draft/ops`) replace whole-draft PUT and client rebasing.
- [x] Incremental identification merged per file; edits never cancel it; provider
  availability reported in status; one series search per group (`/search`).
- [x] Natural file order; title-sorted queue; groups keyed by normalized title;
  file-name series parsing for download folders; `第N季`/`SN`/`Nth Season` split.
- [x] Group-scoped previews bound to a decision signature; approval of one group
  survives edits elsewhere.
- [x] Mark done for in-place series (no identity required); identities saved on
  approval and mark done; explicit save endpoint removed.
- [x] Replace-review prompt; create with `replace`.
- [x] Queue/detail window with tables, fixed action bar and blocker list; finished
  summary; session notice for move/undo results.
- [x] Folder selection: most-specific rule, partial marks, subfolder exclusion.
- [x] English and Traditional Chinese strings.

Verification: `cargo fmt --all --check`, `cargo test --workspace` (4 core,
121 player, 145 server, 5 mpv) and `git diff --check` passed. GUI checks ran the
real server and player under a Linux virtual display with a fixture library and
a local fake dandanplay API, then on Windows 11 (4K display) with a throwaway
fixture library, an isolated profile and the live dandanplay API: selection with
partial folders, start, switching series and editing during identification,
rename/merge, move to series, series search and link, per-video episode choice,
season derivation (`S2`, `第二季`), extras ownership, approve, undo, skip,
mark done, replace prompt, finish, Organize/Resume entry points and zh-TW
layout. A small loopback API pass confirmed browser-origin (403) and
missing-capability (401) rejection, stale-draft/invalid-op/unknown-candidate
rejection, group preview, and removal of the old PUT and save endpoints.

Windows QA fixes: table columns share the window width instead of keeping
stale resizable widths; extras already in the series folder stay listed as
In place; owner choices follow episode order; Organize/Resume raises the
window; choosing one video's episode no longer splits it into a new series, and
a series match never renames a folder the user named; English singular forms;
Organize… with no review enters Select. Physical cross-drive QA remains pending.
