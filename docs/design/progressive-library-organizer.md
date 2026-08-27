# Progressive library organizer

## Goal

Give the desktop app a manual, approval-driven way to reorganize an existing
library root into the proven AniRss layout:

```text
<chosen base>/<series>/Season <number>/<original filename>
```

The organizer must never delete or overwrite user content. It works one series
at a time, displays the exact move manifest before approval, verifies every
move, and supports cancelling or undoing the last completed series.

## User flow

1. Open **Folders** on the desktop and choose **Organize library**.
2. Select a configured library root and a destination base inside that root.
3. Generate a read-only preview. The app groups catalogued videos by provider
   identity when available, otherwise by the current parsed series title.
4. Review one series. Confirm or edit its title and season, optionally select
   nearby sidecar/artwork files, then regenerate the exact preview.
5. Approve that series only. Progress and rollback state remain visible while
   files move. Repeat for another series when ready.
6. Undo the last completed series if needed.

Ambiguous groups cannot be executed until the user supplies a title and season.
Filenames are preserved. Empty source directories are deliberately retained.

## Safety model

- The mutation API requires the desktop access token and accepts requests only
  from a loopback peer. Phone, TV, and other LAN clients cannot organize files.
- Source and destination paths must remain inside one configured root. Symlinks,
  junctions, and reparse points are rejected.
- Existing destinations are conflicts; there is no overwrite or deduplication.
- Execution validates the catalog revision and the exact move list that the UI
  approved. Stale previews are rejected.
- A durable journal is updated after each rename. Cancellation or failure rolls
  completed operations back in reverse order. Startup attempts recovery before
  allowing another operation.
- Catalog paths are updated while media IDs remain stable, so playback progress,
  provider mappings, and metadata continue to refer to the same episodes.
- Companion files are opt-in. The preview enumerates nearby candidates but moves
  only paths explicitly selected by the user.

## Scope

The first release is desktop-only and manual. It does not continuously watch the
filesystem, infer destructive merges, rename files, remove empty folders, move
between drives, or bypass destination conflicts. A normal library refresh remains
available before and after organization.

## Verification

Automated coverage exercises ambiguous previews, exact-manifest validation,
conflicts, successful moves, stable media IDs, cancellation/rollback, undo, local
authentication, and scanner reuse after path changes. Tests use temporary fixture
directories only; release QA must never reorganize a real library without a
separate explicit approval.
