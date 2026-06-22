# Live External Sync QA

Use this checklist for real MyAnimeList and Bangumi readback/writeback validation. These checks are deliberately manual because they can mutate real anime list state.

## Safety Rules

- Use dedicated provider QA accounts when possible.
- Do not write to a real account until the operator confirms the provider, anime ID, current provider state, requested new state, and restore plan.
- Capture the original provider state before any write and restore it before closing the QA task unless the operator explicitly wants to keep the new state.
- Store local reports under `build/qa/live-external-sync/`; do not commit credentials, tokens, screenshots with private account data, or generated reports.

## Prerequisites

- Desktop app can start and open Tracking/Profile provider settings.
- MyAnimeList OAuth or Bangumi token is configured for the test account.
- A small local fixture or real catalog item is mapped to one MAL ID and one Bangumi ID.
- The mapped anime should be safe to edit, preferably a test/low-risk entry with known episode count.

## Readback Flow

1. Start the desktop app with an isolated test library when possible.
2. Confirm provider runtime status shows authenticated list read access.
3. Map one local series to the provider anime ID.
4. Run external list readback/import.
5. Verify the Tracking inspector shows provider status, watched episodes, score, and last-read timestamp.
6. Close and relaunch the desktop app.
7. Verify imported provider entries and any conflict state persist after relaunch.

## Write/Readback Flow

1. Record provider, anime ID, original status, original watched episodes, original score, and timestamp in the QA report.
2. Confirm the exact write target with the operator before continuing.
3. Write a harmless update, such as changing watched episodes by one within the valid range or changing score to a pre-approved value.
4. Read the same provider entry back through the app.
5. Verify the app shows the updated provider state and sync plan no longer reports stale local assumptions.
6. Relaunch the app and verify the updated provider entry is still visible from persisted readback.
7. Restore the original provider state and read it back again.

## Report Template

```text
Provider:
Anime ID:
Local series:
Original provider state:
Temporary QA state:
Restore state:
Readback before write: PASS/FAIL
Write result: PASS/FAIL
Readback after write: PASS/FAIL
Relaunch persistence: PASS/FAIL
Restore result: PASS/FAIL
Notes:
```