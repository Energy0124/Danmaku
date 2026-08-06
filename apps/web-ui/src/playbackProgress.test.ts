import assert from "node:assert/strict";
import test from "node:test";

import { createPlaybackProgress, resumePositionMs } from "./playbackProgress.ts";

test("resumes meaningful progress and restarts near the edges", () => {
  assert.equal(
    resumePositionMs({ mediaId: "a", positionMs: 42_000, durationMs: 120_000, updatedAtEpochMs: 1 }),
    42_000
  );
  assert.equal(
    resumePositionMs({ mediaId: "a", positionMs: 9_999, durationMs: 120_000, updatedAtEpochMs: 1 }),
    null
  );
  assert.equal(
    resumePositionMs({ mediaId: "a", positionMs: 91_000, durationMs: 120_000, updatedAtEpochMs: 1 }),
    null
  );
});

test("does not create a zero-position checkpoint", () => {
  assert.equal(createPlaybackProgress("a", 0, 120, null, 1), null);
  assert.deepEqual(createPlaybackProgress("a", 12.345, 120, null, 2), {
    mediaId: "a",
    positionMs: 12_345,
    durationMs: 120_000,
    updatedAtEpochMs: 2
  });
});
