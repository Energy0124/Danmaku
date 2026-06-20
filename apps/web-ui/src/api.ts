export interface LanLibraryServerStatus {
  appName: string;
  apiVersion: number;
  pairingRequired: boolean;
  mediaStreaming: boolean;
  progressSync: boolean;
  trustedDeviceManagement: boolean;
  webUiAvailable?: boolean;
  webUiPath?: string | null;
  hostMode?: string;
  providerSettings?: LanProviderSettingsStatus | null;
}

export interface LanProviderSettingsStatus {
  dandanplay: LanDandanplayProviderStatus;
  externalAnime: LanExternalAnimeProviderStatus;
}

export interface LanDandanplayProviderStatus {
  baseUrl?: string | null;
  appId?: string | null;
  hasAppSecret: boolean;
  authenticationMode?: string | null;
  cacheMaxAgeDays?: number | null;
}

export interface LanExternalAnimeProviderStatus {
  myAnimeListClientId?: string | null;
  hasMyAnimeListClientSecret: boolean;
  hasMyAnimeListAccessToken: boolean;
  bangumiBaseUrl?: string | null;
  bangumiUserAgent?: string | null;
  hasBangumiAccessToken: boolean;
}

export interface LanProviderRuntimeStatus {
  dandanplay: LanDandanplayRuntimeCapability;
  myAnimeList: LanExternalAnimeRuntimeCapability;
  bangumi: LanExternalAnimeRuntimeCapability;
}

export interface LanDandanplayRuntimeCapability {
  matchAvailable: boolean;
  commentFetchAvailable: boolean;
  authenticated: boolean;
  reasonCode: string;
}

export interface LanExternalAnimeRuntimeCapability {
  searchAvailable: boolean;
  listReadAvailable: boolean;
  listWriteAvailable: boolean;
  authenticated: boolean;
  reasonCode: string;
}

export interface LibraryCatalog {
  rootName: string;
  indexedAtEpochMs: number;
  items: LibraryMediaItem[];
}

export interface LibraryMediaItem {
  id: string;
  seriesTitle: string;
  episodeTitle: string;
  relativePath: string;
  sizeBytes: number;
  mediaType: string;
  streamPath: string;
  posterPath?: string | null;
  subtitles?: LibrarySubtitleTrack[];
  durationMs?: number | null;
  episodeNumber?: number | null;
}

export interface LibrarySubtitleTrack {
  id: string;
  label: string;
  mediaType: string;
  streamPath: string;
}

export interface PlaybackProgress {
  mediaId: string;
  positionMs: number;
  durationMs?: number | null;
  updatedAtEpochMs: number;
}

export interface LibrarySnapshot {
  status: LanLibraryServerStatus;
  catalog: LibraryCatalog;
  progress: PlaybackProgress[];
}

export class DanmakuApiError extends Error {
  constructor(
    message: string,
    readonly status?: number
  ) {
    super(message);
  }
}

export async function fetchServerStatus(baseUrl: string): Promise<LanLibraryServerStatus> {
  return readJson<LanLibraryServerStatus>(`${normalizeBaseUrl(baseUrl)}/api/server/status`);
}

export async function fetchProviderRuntime(
  baseUrl: string,
  token: string
): Promise<LanProviderRuntimeStatus> {
  return readJson<LanProviderRuntimeStatus>(
    `${normalizeBaseUrl(baseUrl)}/api/providers/runtime?token=${encodeURIComponent(token)}`
  );
}

export async function fetchLibrarySnapshot(baseUrl: string, token: string): Promise<LibrarySnapshot> {
  const normalizedBaseUrl = normalizeBaseUrl(baseUrl);
  const status = await fetchServerStatus(normalizedBaseUrl);
  const [catalog, progress] = await Promise.all([
    readJson<LibraryCatalog>(`${normalizedBaseUrl}/api/library?token=${encodeURIComponent(token)}`),
    readJson<PlaybackProgress[]>(`${normalizedBaseUrl}/api/progress?token=${encodeURIComponent(token)}`).catch(
      () => []
    )
  ]);
  return { status, catalog, progress };
}

export async function saveProgress(baseUrl: string, token: string, progress: PlaybackProgress): Promise<void> {
  const response = await fetch(
    `${normalizeBaseUrl(baseUrl)}/api/progress/${encodeURIComponent(progress.mediaId)}?token=${encodeURIComponent(token)}`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json; charset=utf-8" },
      body: JSON.stringify(progress)
    }
  );
  if (response.status !== 204) {
    throw new DanmakuApiError(`Progress save failed with HTTP ${response.status}`, response.status);
  }
}

export function mediaUrl(baseUrl: string, token: string, item: LibraryMediaItem): string {
  return tokenizedUrl(baseUrl, token, item.streamPath);
}

export function posterUrl(baseUrl: string, token: string, item: LibraryMediaItem): string | null {
  return item.posterPath ? tokenizedUrl(baseUrl, token, item.posterPath) : null;
}

export function subtitleUrl(baseUrl: string, token: string, subtitle: LibrarySubtitleTrack): string {
  return tokenizedUrl(baseUrl, token, subtitle.streamPath);
}

export function normalizeBaseUrl(baseUrl: string): string {
  return baseUrl.trim().replace(/\/+$/, "");
}

async function readJson<T>(url: string): Promise<T> {
  const response = await fetch(url, { headers: { Accept: "application/json" } });
  if (!response.ok) {
    throw new DanmakuApiError(`Request failed with HTTP ${response.status}`, response.status);
  }
  return response.json() as Promise<T>;
}

function tokenizedUrl(baseUrl: string, token: string, path: string): string {
  const separator = path.includes("?") ? "&" : "?";
  return `${normalizeBaseUrl(baseUrl)}${path}${separator}token=${encodeURIComponent(token)}`;
}
