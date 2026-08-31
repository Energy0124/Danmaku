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

export type ProviderAccountState =
  | "CONNECTED"
  | "DISCONNECTED"
  | "NEEDS_RECONNECT"
  | "UNAVAILABLE";

export interface ProviderAccountStatus {
  state: ProviderAccountState;
  userId?: string | null;
  displayName?: string | null;
  lastVerifiedAtEpochMs?: number | null;
  reasonCode?: string | null;
}

export interface ProviderAccountsDocument {
  myAnimeList: ProviderAccountStatus;
  bangumi: ProviderAccountStatus;
  bangumiTokenUrl: string;
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

export type AniRssMode = "DISABLED" | "EXTERNAL" | "MANAGED_WINDOWS";
export type AniRssSource = "MIKAN" | "ANIBT" | "ANIME_GARDEN" | "CUSTOM_RSS";

export interface AniRssPathMapping {
  remotePrefix: string;
  localPrefix: string;
}

export interface AniRssSettings {
  mode: AniRssMode;
  baseUrl: string;
  hasApiKey: boolean;
  managedPort: number;
  automaticRescan: boolean;
  pathMappings: AniRssPathMapping[];
  approvedSources: AniRssSource[];
  supportedSources: AniRssSource[];
  advancedUiUrl?: string | null;
}

export interface AniRssSettingsUpdate {
  mode: AniRssMode;
  baseUrl: string;
  apiKey?: string;
  clearApiKey?: boolean;
  managedPort: number;
  automaticRescan: boolean;
  pathMappings: AniRssPathMapping[];
}

export interface AniRssStatus {
  configured: boolean;
  reachable: boolean;
  mode: AniRssMode;
  version?: string | null;
  message: string;
}

export interface AniRssSearchRequest {
  source: AniRssSource;
  query: string;
  year?: number;
  season?: string;
  bgmUrl?: string;
}

export interface AniRssSearchResult {
  source: AniRssSource;
  id: string;
  title: string;
  coverUrl?: string | null;
  bgmUrl?: string | null;
  locator: string;
  alreadySubscribed: boolean;
}

export interface AniRssGroup {
  name: string;
  rssUrl: string;
  bgmUrl?: string | null;
  sampleTitles: string[];
}

export interface AniRssSubscriptionRequest {
  source: AniRssSource;
  title: string;
  rssUrl: string;
  bgmUrl?: string;
  subgroup?: string;
  enabled: boolean;
}

export interface AniRssSubscriptionPreview extends AniRssSubscriptionRequest {
  downloadPath?: string | null;
  sampleTitles: string[];
}

export interface AniRssSubscription {
  id: string;
  title: string;
  source: AniRssSource;
  rssUrl?: string | null;
  subgroup?: string | null;
  enabled: boolean;
  currentEpisode?: number | null;
  totalEpisodes?: number | null;
  lastDownloadAtEpochMs?: number | null;
  weekLabel?: string | null;
}

export interface AniRssDownloadJob {
  id: string;
  name: string;
  state: string;
  progressPercent: number;
  completedBytes?: number | null;
  totalBytes?: number | null;
  savePath?: string | null;
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

const aniRssApi = (baseUrl: string, path: string) =>
  normalizeBaseUrl(baseUrl) + "/api/automation/ani-rss" + path;

export function fetchAniRssSettings(baseUrl: string, token: string): Promise<AniRssSettings> {
  return readJsonWithToken(aniRssApi(baseUrl, "/settings"), token);
}

export function saveAniRssSettings(
  baseUrl: string,
  token: string,
  update: AniRssSettingsUpdate
): Promise<AniRssSettings> {
  return writeJsonWithToken(aniRssApi(baseUrl, "/settings"), token, "PUT", update);
}

export function fetchAniRssStatus(baseUrl: string, token: string): Promise<AniRssStatus> {
  return readJsonWithToken(aniRssApi(baseUrl, "/status"), token);
}

export function setAniRssSourceApproval(
  baseUrl: string,
  token: string,
  source: AniRssSource,
  approved: boolean
): Promise<AniRssSettings> {
  return writeJsonWithToken(
    aniRssApi(baseUrl, `/sources/${encodeURIComponent(source)}/approval`),
    token,
    approved ? "POST" : "DELETE"
  );
}

export function searchAniRss(
  baseUrl: string,
  token: string,
  request: AniRssSearchRequest
): Promise<AniRssSearchResult[]> {
  return writeJsonWithToken(aniRssApi(baseUrl, "/search"), token, "POST", request);
}

export function fetchAniRssGroups(
  baseUrl: string,
  token: string,
  source: AniRssSource,
  locator: string
): Promise<AniRssGroup[]> {
  return writeJsonWithToken(aniRssApi(baseUrl, "/groups"), token, "POST", { source, locator });
}

export function previewAniRssSubscription(
  baseUrl: string,
  token: string,
  request: AniRssSubscriptionRequest
): Promise<AniRssSubscriptionPreview> {
  return writeJsonWithToken(aniRssApi(baseUrl, "/preview"), token, "POST", request);
}

export function createAniRssSubscription(
  baseUrl: string,
  token: string,
  request: AniRssSubscriptionRequest
): Promise<{ accepted: boolean }> {
  return writeJsonWithToken(aniRssApi(baseUrl, "/subscriptions"), token, "POST", request);
}

export function fetchAniRssSubscriptions(
  baseUrl: string,
  token: string
): Promise<AniRssSubscription[]> {
  return readJsonWithToken(aniRssApi(baseUrl, "/subscriptions"), token);
}

export function setAniRssSubscriptionEnabled(
  baseUrl: string,
  token: string,
  id: string,
  enabled: boolean
): Promise<{ accepted: boolean }> {
  return writeJsonWithToken(
    aniRssApi(baseUrl, `/subscriptions/${encodeURIComponent(id)}/enabled`),
    token,
    "PUT",
    { enabled }
  );
}

export function refreshAniRssSubscription(
  baseUrl: string,
  token: string,
  id: string
): Promise<{ accepted: boolean }> {
  return writeJsonWithToken(
    aniRssApi(baseUrl, `/subscriptions/${encodeURIComponent(id)}/refresh`),
    token,
    "POST"
  );
}

export function deleteAniRssSubscription(
  baseUrl: string,
  token: string,
  id: string
): Promise<{ accepted: boolean }> {
  return writeJsonWithToken(
    aniRssApi(baseUrl, `/subscriptions/${encodeURIComponent(id)}`),
    token,
    "DELETE"
  );
}

export function fetchAniRssDownloads(baseUrl: string, token: string): Promise<AniRssDownloadJob[]> {
  return readJsonWithToken(aniRssApi(baseUrl, "/downloads"), token);
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

export async function fetchProviderAccounts(
  baseUrl: string,
  token: string
): Promise<ProviderAccountsDocument> {
  return readJsonWithToken<ProviderAccountsDocument>(
    normalizeBaseUrl(baseUrl) + "/api/providers/accounts",
    token
  );
}

export async function connectBangumiAccount(
  baseUrl: string,
  token: string,
  accessToken: string
): Promise<ProviderAccountsDocument> {
  return writeJsonWithToken<ProviderAccountsDocument>(
    normalizeBaseUrl(baseUrl) + "/api/providers/accounts/bangumi",
    token,
    "PUT",
    { accessToken }
  );
}

export async function disconnectProviderAccount(
  baseUrl: string,
  token: string,
  provider: "myanimelist" | "bangumi"
): Promise<ProviderAccountsDocument> {
  return writeJsonWithToken<ProviderAccountsDocument>(
    normalizeBaseUrl(baseUrl) + "/api/providers/accounts/" + provider,
    token,
    "DELETE"
  );
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

export async function importExternalTrackingConflict(
  baseUrl: string,
  token: string,
  localSeriesId: string,
  animeId: ExternalAnimeId,
  expectedExternalWatchedEpisodes: number
): Promise<{ importedCount: number; document: ExternalTrackingDocument }> {
  return writeJsonWithToken(
    normalizeBaseUrl(baseUrl) + "/api/providers/tracking/conflicts/import",
    token,
    "POST",
    { localSeriesId, animeId, expectedExternalWatchedEpisodes }
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

export async function saveProgress(
  baseUrl: string,
  token: string,
  progress: PlaybackProgress,
  keepalive = false
): Promise<void> {
  const response = await fetch(
    `${normalizeBaseUrl(baseUrl)}/api/progress/${encodeURIComponent(progress.mediaId)}`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json; charset=utf-8" },
      body: JSON.stringify(progress),
      keepalive
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
