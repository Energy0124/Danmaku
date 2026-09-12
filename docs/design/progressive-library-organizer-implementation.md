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
