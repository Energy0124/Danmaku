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
  dandanplay?: LanDandanplayProviderStatus;
  externalAnime?: LanExternalAnimeProviderStatus;
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


export interface ProviderSettingsDocument {
  settings: LanProviderSettingsStatus;
  runtime: LanProviderRuntimeStatus;
}

export interface ProviderSettingsUpdate {
  dandanplay: {
    baseUrl: string;
    appId?: string;
    appSecret?: string;
    clearAppSecret?: boolean;
    authenticationMode: "SIGNED" | "CREDENTIAL";
    cacheMaxAgeDays: number;
  };
  externalAnime: {
    myAnimeListClientId?: string;
    myAnimeListClientSecret?: string;
    clearMyAnimeListClientSecret?: boolean;
    myAnimeListAccessToken?: string;
    clearMyAnimeListAccessToken?: boolean;
    bangumiBaseUrl: string;
    bangumiUserAgent: string;
    bangumiAccessToken?: string;
    clearBangumiAccessToken?: boolean;
  };
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

export type ExternalAnimeProvider = "MY_ANIME_LIST" | "BANGUMI" | "DANDANPLAY";

export interface ExternalAnimeId {
  provider: ExternalAnimeProvider;
  value: number;
}

export type ExternalAnimeListStatus =
  | "WATCHING"
  | "COMPLETED"
  | "ON_HOLD"
  | "DROPPED"
  | "PLAN_TO_WATCH";

export interface ExternalAnimeListEntry {
  animeId: ExternalAnimeId;
  status?: ExternalAnimeListStatus | null;
  watchedEpisodes?: number | null;
  score?: number | null;
  updatedAtEpochMs?: number | null;
}

export interface ExternalAnimeTrackingUpdate {
  animeId: ExternalAnimeId;
  status?: ExternalAnimeListStatus | null;
  watchedEpisodes?: number | null;
  score?: number | null;
  trackingEnabled?: boolean;
  ratingEnabled?: boolean;
}

export type ExternalAnimeMappingSource = "AUTO" | "MANUAL";

export interface ExternalAnimeMapping {
  localSeriesId: string;
  animeId: ExternalAnimeId;
  source: ExternalAnimeMappingSource;
  confidence: number;
  mappedAtEpochMs: number;
}

export interface ExternalAnimeSyncFailure {
  animeId: ExternalAnimeId;
  message: string;
  failedAtEpochMs: number;
  attemptCount: number;
  retryAfterEpochMs: number;
}

export interface ExternalTrackingSeries {
  id: string;
  title: string;
  localSeriesIds: string[];
  localSeriesTitles: string[];
  episodeCount: number;
  mappings: ExternalAnimeMapping[];
}

export interface ExternalTrackingPlanSummary {
  updateCount: number;
  skippedCount: number;
  conflictCount: number;
  failureCount: number;
  myAnimeListUpdateCount: number;
  bangumiUpdateCount: number;
}

export interface ExternalTrackingPlanUpdate {
  localSeriesId: string;
  localSeriesIds: string[];
  seriesTitle: string;
  episodeCount: number;
  mapping: ExternalAnimeMapping;
  update: ExternalAnimeTrackingUpdate;
}

export interface ExternalTrackingPlanSkip {
  localSeriesId: string;
  localSeriesIds: string[];
  seriesTitle?: string;
  provider: ExternalAnimeProvider;
  reason: "MISSING_LOCAL_SERIES" | "UNMAPPED_LOCAL_SERIES" | "ALREADY_IN_SYNC";
}

export interface ExternalTrackingPlanConflict {
  localSeriesId: string;
  localSeriesIds: string[];
  seriesTitle: string;
  episodeCount: number;
  mapping: ExternalAnimeMapping;
  localUpdate: ExternalAnimeTrackingUpdate;
  externalEntry: ExternalAnimeListEntry;
  reason: "EXTERNAL_PROGRESS_AHEAD";
}

export interface ExternalTrackingMappingConflict {
  localSeriesId: string;
  localSeriesIds: string[];
  seriesTitle: string;
  provider: ExternalAnimeProvider;
  animeIds: ExternalAnimeId[];
  reason: "CONFLICTING_PROVIDER_IDS";
}

export interface ExternalTrackingPlan {
  summary: ExternalTrackingPlanSummary;
  updates: ExternalTrackingPlanUpdate[];
  skipped: ExternalTrackingPlanSkip[];
  conflicts: ExternalTrackingPlanConflict[];
  mappingConflicts: ExternalTrackingMappingConflict[];
  failures: ExternalAnimeSyncFailure[];
}

export interface ExternalTrackingDocument {
  generatedAtEpochMs: number;
  series: ExternalTrackingSeries[];
  mappings: ExternalAnimeMapping[];
  listEntries: ExternalAnimeListEntry[];
  plan: ExternalTrackingPlan;
}

export interface ExternalTrackingOperationError {
  animeId: ExternalAnimeId;
  message: string;
}

export interface ExternalTrackingOperationResponse {
  document: ExternalTrackingDocument;
  successCount: number;
  conflictCount: number;
  missingCount: number;
  errors: ExternalTrackingOperationError[];
}

export interface ExternalAnimeTitleSet {
  primary: string;
  chinese?: string | null;
  english?: string | null;
  japanese?: string | null;
  alternateNames: string[];
}

export interface ExternalAnimeExternalLink {
  animeId: ExternalAnimeId;
  url: string;
}

export interface ExternalAnimeInfo {
  id: ExternalAnimeId;
  titles: ExternalAnimeTitleSet;
  episodeCount?: number | null;
  startYear?: number | null;
  imageUrl?: string | null;
  summary?: string | null;
  externalLinks: ExternalAnimeExternalLink[];
}

export interface ExternalAnimeMatchCandidate {
  anime: ExternalAnimeInfo;
  confidence: number;
  matchedTitle?: string | null;
  evidence: string[];
}

export interface ProviderSearchOptions {
  providers?: ExternalAnimeProvider[];
  limit?: number;
  episodeCount?: number;
  startYear?: number;
}

export interface DandanplayResolveOptions {
  episodeId?: number;
  withRelated?: boolean;
}

export interface DandanplayMediaFingerprint {
  fileName: string;
  fileHash: string;
  fileSizeBytes: number;
  videoDurationSeconds?: number | null;
}

export interface DandanplayMatch {
  episodeId: number;
  animeId?: number | null;
  animeTitle?: string | null;
  episodeTitle?: string | null;
  shiftSeconds?: number | null;
  displayTitle: string;
}

export interface DandanplayCommentStyle {
  colorArgb: string;
  mode: string;
  size: string;
}

export interface DandanplayComment {
  id: string;
  timestampMs: number;
  text: string;
  style: DandanplayCommentStyle;
}

export interface DandanplayResolveResult {
  mediaId: string;
  fingerprint: DandanplayMediaFingerprint;
  matches: DandanplayMatch[];
  selectedMatch?: DandanplayMatch | null;
  commentCount: number;
  comments: DandanplayComment[];
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
  animeMetadata?: LibraryAnimeMetadata | null;
}

export interface LibraryAnimeMetadata {
  animeId: ExternalAnimeId;
  displayTitle: string;
  primaryTitle: string;
  chineseTitle?: string | null;
  englishTitle?: string | null;
  japaneseTitle?: string | null;
  alternateNames?: string[];
  externalLinks?: ExternalAnimeExternalLink[];
  imageUrl?: string | null;
  episodeCount?: number | null;
  startYear?: number | null;
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


export async function fetchProviderSettings(
  baseUrl: string,
  token: string
): Promise<ProviderSettingsDocument> {
  return readJsonWithToken<ProviderSettingsDocument>(
    normalizeBaseUrl(baseUrl) + "/api/providers/settings",
    token
  );
}

export async function saveProviderSettings(
  baseUrl: string,
  token: string,
  update: ProviderSettingsUpdate
): Promise<ProviderSettingsDocument> {
  const response = await fetch(
    normalizeBaseUrl(baseUrl) + "/api/providers/settings",
    {
      method: "PUT",
      headers: {
        Accept: "application/json",
        Authorization: "Bearer " + token,
        "Content-Type": "application/json; charset=utf-8"
      },
      body: JSON.stringify(update)
    }
  );
  if (!response.ok) {
    const message = (await response.text()).trim();
    throw new DanmakuApiError(
      message || "Provider settings save failed with HTTP " + response.status,
      response.status
    );
  }
  return response.json() as Promise<ProviderSettingsDocument>;
}

export async function fetchProviderRuntime(
  baseUrl: string,
  token: string
): Promise<LanProviderRuntimeStatus> {
  return readJson<LanProviderRuntimeStatus>(
    `${normalizeBaseUrl(baseUrl)}/api/providers/runtime`
  );
}

export async function fetchProviderSearch(
  baseUrl: string,
  token: string,
  title: string,
  options: ProviderSearchOptions = {}
): Promise<ExternalAnimeMatchCandidate[]> {
  const params = new URLSearchParams({ title });
  if (options.providers?.length) {
    params.set("providers", options.providers.join(","));
  }
  if (options.limit !== undefined) {
    params.set("limit", String(options.limit));
  }
  if (options.episodeCount !== undefined) {
    params.set("episodeCount", String(options.episodeCount));
  }
  if (options.startYear !== undefined) {
    params.set("startYear", String(options.startYear));
  }
  return readJson<ExternalAnimeMatchCandidate[]>(
    `${normalizeBaseUrl(baseUrl)}/api/providers/search?${params.toString()}`
  );
}

export async function fetchExternalListEntry(
  baseUrl: string,
  token: string,
  animeId: ExternalAnimeId
): Promise<ExternalAnimeListEntry> {
  const params = new URLSearchParams({
    provider: animeId.provider,
    animeId: String(animeId.value)
  });
  return readJson<ExternalAnimeListEntry>(
    `${normalizeBaseUrl(baseUrl)}/api/providers/list/entry?${params.toString()}`
  );
}

export async function saveExternalListEntry(
  baseUrl: string,
  token: string,
  update: ExternalAnimeTrackingUpdate
): Promise<ExternalAnimeListEntry> {
  const url = `${normalizeBaseUrl(baseUrl)}/api/providers/list/entry`;
  const response = await fetch(url, {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json; charset=utf-8"
    },
    body: JSON.stringify(update)
  });
  if (!response.ok) {
    throw new DanmakuApiError(`Request failed with HTTP ${response.status}`, response.status);
  }
  return response.json() as Promise<ExternalAnimeListEntry>;
}

export async function fetchExternalTracking(
  baseUrl: string,
  token: string
): Promise<ExternalTrackingDocument> {
  return readJsonWithToken<ExternalTrackingDocument>(
    normalizeBaseUrl(baseUrl) + "/api/providers/tracking",
    token
  );
}

export async function saveExternalTrackingMapping(
  baseUrl: string,
  token: string,
  localSeriesId: string,
  animeId: ExternalAnimeId
): Promise<ExternalTrackingDocument> {
  return writeJsonWithToken<ExternalTrackingDocument>(
    normalizeBaseUrl(baseUrl) + "/api/providers/tracking/mapping",
    token,
    "PUT",
    { localSeriesId, animeId }
  );
}

export async function deleteExternalTrackingMapping(
  baseUrl: string,
  token: string,
  localSeriesId: string,
  animeId: ExternalAnimeId
): Promise<ExternalTrackingDocument> {
  return writeJsonWithToken<ExternalTrackingDocument>(
    normalizeBaseUrl(baseUrl) + "/api/providers/tracking/mapping",
    token,
    "DELETE",
    { localSeriesId, animeId }
  );
}

export async function refreshExternalTrackingReadback(
  baseUrl: string,
  token: string
): Promise<ExternalTrackingOperationResponse> {
  return writeJsonWithToken<ExternalTrackingOperationResponse>(
    normalizeBaseUrl(baseUrl) + "/api/providers/tracking/readback",
    token,
    "POST"
  );
}

export async function executeExternalTrackingSync(
  baseUrl: string,
  token: string,
  expectedUpdates: ExternalAnimeTrackingUpdate[]
): Promise<ExternalTrackingOperationResponse> {
  return writeJsonWithToken<ExternalTrackingOperationResponse>(
    normalizeBaseUrl(baseUrl) + "/api/providers/tracking/sync",
    token,
    "POST",
    { expectedUpdates }
  );
}

export async function fetchDandanplayResolve(
  baseUrl: string,
  token: string,
  mediaId: string,
  options: DandanplayResolveOptions = {}
): Promise<DandanplayResolveResult> {
  const params = new URLSearchParams({ mediaId });
  if (options.episodeId !== undefined) {
    params.set("episodeId", String(options.episodeId));
  }
  if (options.withRelated !== undefined) {
    params.set("withRelated", String(options.withRelated));
  }
  return readJson<DandanplayResolveResult>(
    `${normalizeBaseUrl(baseUrl)}/api/providers/dandanplay/resolve?${params.toString()}`
  );
}

export async function fetchLibrarySnapshot(baseUrl: string, token: string): Promise<LibrarySnapshot> {
  const normalizedBaseUrl = normalizeBaseUrl(baseUrl);
  const status = await fetchServerStatus(normalizedBaseUrl);
  const [catalog, progress] = await Promise.all([
    readJson<LibraryCatalog>(`${normalizedBaseUrl}/api/library`),
    readJson<PlaybackProgress[]>(`${normalizedBaseUrl}/api/progress`).catch(
      () => []
    )
  ]);
  return { status, catalog, progress };
}

export async function saveProgress(baseUrl: string, token: string, progress: PlaybackProgress): Promise<void> {
  const response = await fetch(
    `${normalizeBaseUrl(baseUrl)}/api/progress/${encodeURIComponent(progress.mediaId)}`,
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


async function writeJsonWithToken<T>(
  url: string,
  token: string,
  method: "POST" | "PUT" | "DELETE",
  body?: unknown
): Promise<T> {
  const response = await fetch(url, {
    method,
    headers: {
      Accept: "application/json",
      Authorization: "Bearer " + token,
      ...(body === undefined ? {} : { "Content-Type": "application/json; charset=utf-8" })
    },
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  if (!response.ok) {
    const message = (await response.text()).trim();
    throw new DanmakuApiError(
      message || "Request failed with HTTP " + response.status,
      response.status
    );
  }
  return response.json() as Promise<T>;
}

async function readJsonWithToken<T>(url: string, token: string): Promise<T> {
  const response = await fetch(url, {
    headers: {
      Accept: "application/json",
      Authorization: "Bearer " + token
    }
  });
  if (!response.ok) {
    throw new DanmakuApiError("Request failed with HTTP " + response.status, response.status);
  }
  return response.json() as Promise<T>;
}

async function readJson<T>(url: string): Promise<T> {
  const response = await fetch(url, { headers: { Accept: "application/json" } });
  if (!response.ok) {
    throw new DanmakuApiError(`Request failed with HTTP ${response.status}`, response.status);
  }
  return response.json() as Promise<T>;
}

function tokenizedUrl(baseUrl: string, token: string, path: string): string {
  return `${normalizeBaseUrl(baseUrl)}${path}`;
}
