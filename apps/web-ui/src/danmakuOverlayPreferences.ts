import type { DanmakuDensity } from "./danmakuOverlay";

export interface DanmakuOverlayPreferences {
  density: DanmakuDensity;
  enabled: boolean;
  offsetSeconds: string;
}

export const defaultDanmakuOverlayPreferences: DanmakuOverlayPreferences = {
  density: "normal",
  enabled: true,
  offsetSeconds: "0"
};

const storageKey = "danmaku.web.danmakuOverlay";

export function loadDanmakuOverlayPreferences(storage = resolveStorage()): DanmakuOverlayPreferences {
  if (!storage) return defaultDanmakuOverlayPreferences;
  try {
    const rawValue = storage.getItem(storageKey);
    if (!rawValue) return defaultDanmakuOverlayPreferences;
    const parsed = JSON.parse(rawValue) as Partial<DanmakuOverlayPreferences>;
    return {
      density: isDanmakuDensity(parsed.density) ? parsed.density : defaultDanmakuOverlayPreferences.density,
      enabled: typeof parsed.enabled === "boolean" ? parsed.enabled : defaultDanmakuOverlayPreferences.enabled,
      offsetSeconds: normalizeOffsetSeconds(parsed.offsetSeconds)
    };
  } catch {
    return defaultDanmakuOverlayPreferences;
  }
}

export function saveDanmakuOverlayPreferences(
  preferences: DanmakuOverlayPreferences,
  storage = resolveStorage()
): void {
  if (!storage) return;
  try {
    storage.setItem(storageKey, JSON.stringify(preferences));
  } catch {
    // Browser storage can be unavailable or quota-limited; playback should keep working.
  }
}

function resolveStorage(): Storage | null {
  return typeof window === "undefined" ? null : window.localStorage;
}

function isDanmakuDensity(value: unknown): value is DanmakuDensity {
  return value === "low" || value === "normal" || value === "dense";
}

function normalizeOffsetSeconds(value: unknown): string {
  if (typeof value !== "string") return defaultDanmakuOverlayPreferences.offsetSeconds;
  const trimmed = value.trim();
  if (!trimmed) return defaultDanmakuOverlayPreferences.offsetSeconds;
  return Number.isFinite(Number(trimmed)) ? trimmed : defaultDanmakuOverlayPreferences.offsetSeconds;
}
