import type { PlaybackProgress } from "./api";

export const MINIMUM_RESUME_POSITION_MS = 10_000;
export const MINIMUM_REMAINING_MS = 30_000;

export function resumePositionMs(progress?: PlaybackProgress): number | null {
  if (!progress || progress.positionMs < MINIMUM_RESUME_POSITION_MS) return null;
  if (
    progress.durationMs != null &&
    progress.durationMs - progress.positionMs < MINIMUM_REMAINING_MS
  ) {
    return null;
  }
  return progress.positionMs;
}

export function createPlaybackProgress(
  mediaId: string,
  currentTimeSeconds: number,
  durationSeconds: number,
  fallbackDurationMs: number | null,
  updatedAtEpochMs: number
): PlaybackProgress | null {
  const positionMs = Math.round(currentTimeSeconds * 1000);
  if (!Number.isFinite(positionMs) || positionMs <= 0) return null;
  return {
    mediaId,
    positionMs,
    durationMs: Number.isFinite(durationSeconds)
      ? Math.round(durationSeconds * 1000)
      : fallbackDurationMs,
    updatedAtEpochMs
  };
}
