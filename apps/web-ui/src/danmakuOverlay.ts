import type { DandanplayComment } from "./api";

export type DanmakuDensity = "low" | "normal" | "dense";

export interface DanmakuDensityOption {
  value: DanmakuDensity;
  label: string;
}

export interface VisibleDanmakuComment {
  animationSeconds: number;
  className: string;
  color: string;
  comment: DandanplayComment;
  fontSize: string;
  laneTopPercent: number;
}

export const danmakuDensityOptions: DanmakuDensityOption[] = [
  { value: "low", label: "Low" },
  { value: "normal", label: "Normal" },
  { value: "dense", label: "Dense" }
];

export function resolveVisibleDanmakuComments({
  comments,
  currentTimeMs,
  density,
  offsetSeconds
}: {
  comments: DandanplayComment[];
  currentTimeMs: number;
  density: DanmakuDensity;
  offsetSeconds: string;
}): VisibleDanmakuComment[] {
  const adjustedTimeMs = Math.max(0, currentTimeMs + parseDanmakuOffsetMs(offsetSeconds));
  const windowStartMs = adjustedTimeMs - danmakuWindowMs(density);
  const windowEndMs = adjustedTimeMs + 500;
  return comments
    .filter((comment) => comment.timestampMs >= windowStartMs && comment.timestampMs <= windowEndMs)
    .sort((left, right) => left.timestampMs - right.timestampMs)
    .slice(0, danmakuMaxVisible(density))
    .map((comment, index) => ({
      animationSeconds: danmakuAnimationSeconds(density),
      className: danmakuModeClass(comment.style.mode),
      color: cssColorFromArgb(comment.style.colorArgb),
      comment,
      fontSize: danmakuFontSize(comment.style.size),
      laneTopPercent: danmakuLaneTop(index, density)
    }));
}

function parseDanmakuOffsetMs(value: string): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? Math.round(parsed * 1000) : 0;
}

function danmakuWindowMs(density: DanmakuDensity): number {
  switch (density) {
    case "low":
      return 3000;
    case "dense":
      return 6000;
    case "normal":
      return 4500;
  }
}

function danmakuMaxVisible(density: DanmakuDensity): number {
  switch (density) {
    case "low":
      return 8;
    case "dense":
      return 28;
    case "normal":
      return 16;
  }
}

function danmakuLaneTop(index: number, density: DanmakuDensity): number {
  const lanes = density === "dense" ? 14 : density === "low" ? 6 : 10;
  return 7 + (index % lanes) * (86 / lanes);
}

function danmakuAnimationSeconds(density: DanmakuDensity): number {
  switch (density) {
    case "low":
      return 9;
    case "dense":
      return 7;
    case "normal":
      return 8;
  }
}

function danmakuFontSize(size: string): string {
  const normalized = size.toLocaleLowerCase();
  if (normalized.includes("small")) return "18px";
  if (normalized.includes("large")) return "25px";
  return "21px";
}

function danmakuModeClass(mode: string): string {
  const normalized = mode.toLocaleLowerCase();
  if (normalized.includes("top")) return "mode-top";
  if (normalized.includes("bottom")) return "mode-bottom";
  return "mode-scroll";
}

function cssColorFromArgb(value: string): string {
  const hex = value.replace(/^#/, "").replace(/^0x/i, "");
  if (/^[0-9a-fA-F]{8}$/.test(hex)) return `#${hex.slice(2)}`;
  if (/^[0-9a-fA-F]{6}$/.test(hex)) return `#${hex}`;
  return "#ffffff";
}
