import { useEffect, useMemo, useRef, useState } from "react";
import {
  DanmakuApiError,
  DandanplayResolveResult,
  LanProviderRuntimeStatus,
  LibraryCatalog,
  LibraryMediaItem,
  PlaybackProgress,
  fetchDandanplayResolve,
  fetchLibrarySnapshot,
  fetchProviderRuntime,
  mediaUrl,
  normalizeBaseUrl,
  posterUrl,
  saveProgress,
  subtitleUrl
} from "./api";
import type { DanmakuDensity, VisibleDanmakuComment } from "./danmakuOverlay";
import { danmakuDensityOptions, resolveVisibleDanmakuComments } from "./danmakuOverlay";
import {
  loadDanmakuOverlayPreferences,
  saveDanmakuOverlayPreferences
} from "./danmakuOverlayPreferences";
import { createPlaybackProgress, resumePositionMs } from "./playbackProgress";
import { ProviderSettingsPanel } from "./ProviderSettingsPanel";
import { ProviderAccountsPanel } from "./ProviderAccountsPanel";
import { TrackingAdminPanel } from "./TrackingAdminPanel";

export function App() {
  const defaultBaseUrl = window.location.origin;
  const [baseUrl, setBaseUrl] = useState(defaultBaseUrl);
  const [pairingToken, setPairingToken] = useState("");
  const [catalog, setCatalog] = useState<LibraryCatalog | null>(null);
  const [progress, setProgress] = useState<PlaybackProgress[]>([]);
  const [providerRuntime, setProviderRuntime] = useState<LanProviderRuntimeStatus | null>(null);
  const [providerAccountRefreshVersion, setProviderAccountRefreshVersion] = useState(0);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [message, setMessage] = useState("Connect to a Danmaku library host.");
  const normalizedBaseUrl = useMemo(() => normalizeBaseUrl(baseUrl), [baseUrl]);
  const selectedItem = catalog?.items.find((item) => item.id === selectedId) ?? catalog?.items[0] ?? null;
  const progressById = useMemo(
    () => new Map(progress.map((entry) => [entry.mediaId, entry])),
    [progress]
  );
  const visibleItems = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase();
    const items = catalog?.items ?? [];
    if (!needle) return items;
    return items.filter((item) =>
      `${item.seriesTitle} ${item.episodeTitle} ${item.relativePath}`.toLocaleLowerCase().includes(needle)
    );
  }, [catalog, query]);

  async function connect() {
    setIsLoading(true);
    setMessage("Connecting...");
    try {
      const token = pairingToken.trim();
      const [snapshot, runtime] = await Promise.all([
        fetchLibrarySnapshot(normalizedBaseUrl, token),
        fetchProviderRuntime(normalizedBaseUrl, token).catch(() => null)
      ]);
      setCatalog(snapshot.catalog);
      setProgress(snapshot.progress);
      setProviderRuntime(runtime);
      setSelectedId((current) => current ?? snapshot.catalog.items[0]?.id ?? null);
      setMessage(
        `${snapshot.status.appName} ${snapshot.status.hostMode ?? "headless-server"}: ` +
          `${snapshot.catalog.items.length} media items`
      );
    } catch (error) {
      setProviderRuntime(null);
      setMessage(error instanceof DanmakuApiError ? error.message : "Could not connect to the library host.");
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <main className="app-shell">
      <header className="top-bar">
        <div>
          <h1>Danmaku</h1>
          <p>{message}</p>
          {providerRuntime ? <ProviderRuntimeStrip runtime={providerRuntime} /> : null}
        </div>
        <form
          className="connection-form"
          onSubmit={(event) => {
            event.preventDefault();
            void connect();
          }}
        >
          <label>
            Host
            <input value={baseUrl} onChange={(event) => setBaseUrl(event.target.value)} />
          </label>
          <label>
            Pairing token
            <input
              autoComplete="off"
              inputMode="numeric"
              type="password"
              value={pairingToken}
              onChange={(event) => setPairingToken(event.target.value)}
              placeholder="Required for administration"
            />
          </label>

          <button disabled={isLoading} type="submit">
            {isLoading ? "Connecting" : "Connect"}
          </button>
        </form>
      </header>

      {catalog ? (
        <ProviderSettingsPanel
          baseUrl={normalizedBaseUrl}
          token={pairingToken}
          onRuntimeUpdated={setProviderRuntime}
        />
      ) : null}

      {catalog ? (
        <ProviderAccountsPanel
          baseUrl={normalizedBaseUrl}
          refreshVersion={providerAccountRefreshVersion}
          token={pairingToken}
        />
      ) : null}

      {catalog ? (
        <TrackingAdminPanel
          baseUrl={normalizedBaseUrl}
          onAccountStatusMayHaveChanged={() =>
            setProviderAccountRefreshVersion((version) => version + 1)
          }
          token={pairingToken}
        />
      ) : null}

      <section className="workspace">
        <aside className="library-pane">
          <div className="library-toolbar">
            <strong>{catalog?.rootName ?? "Library"}</strong>
            <span>{visibleItems.length} shown</span>
          </div>
          <input
            className="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search title, episode, or path"
          />
          <div className="episode-list">
            {visibleItems.map((item) => (
              <button
                key={item.id}
                className={item.id === selectedItem?.id ? "episode-row selected" : "episode-row"}
                onClick={() => setSelectedId(item.id)}
                type="button"
              >
                <span>{item.seriesTitle}</span>
                <small>{item.episodeTitle || item.relativePath}</small>
                <progress
                  max={Math.max(progressById.get(item.id)?.durationMs ?? item.durationMs ?? 1, 1)}
                  value={progressById.get(item.id)?.positionMs ?? 0}
                />
              </button>
            ))}
          </div>
        </aside>

        <section className="detail-pane">
          {selectedItem ? (
            <PlayerPanel
              baseUrl={normalizedBaseUrl}
              token={pairingToken}
              providerRuntime={providerRuntime}
              item={selectedItem}
              savedProgress={progressById.get(selectedItem.id)}
              onProgressSaved={(entry) => {
                setProgress((current) => [
                  ...current.filter((candidate) => candidate.mediaId !== entry.mediaId),
                  entry
                ]);
              }}
            />
          ) : (
            <div className="empty-state">Connect to a host and select an episode.</div>
          )}
        </section>
      </section>
    </main>
  );
}

function ProviderRuntimeStrip({ runtime }: { runtime: LanProviderRuntimeStatus }) {
  const providers = [
    {
      name: "Dandanplay",
      ready: runtime.dandanplay.matchAvailable && runtime.dandanplay.commentFetchAvailable,
      detail: runtime.dandanplay.reasonCode
    },
    {
      name: "MAL",
      ready: runtime.myAnimeList.searchAvailable,
      detail: externalRuntimeDetail(runtime.myAnimeList)
    },
    {
      name: "Bangumi",
      ready: runtime.bangumi.searchAvailable,
      detail: externalRuntimeDetail(runtime.bangumi)
    }
  ];
  return (
    <div className="provider-runtime-strip" aria-label="Provider runtime status">
      {providers.map((provider) => (
        <span
          key={provider.name}
          className={provider.ready ? "provider-runtime-pill ready" : "provider-runtime-pill limited"}
          title={provider.detail}
        >
          {provider.name}
        </span>
      ))}
    </div>
  );
}

function externalRuntimeDetail(runtime: LanProviderRuntimeStatus["myAnimeList"]): string {
  if (runtime.listReadAvailable && runtime.listWriteAvailable) return "list-sync-ready";
  if (runtime.searchAvailable) return runtime.reasonCode;
  return runtime.reasonCode;
}

function PlayerPanel({
  baseUrl,
  token,
  providerRuntime,
  item,
  savedProgress,
  onProgressSaved
}: {
  baseUrl: string;
  token: string;
  providerRuntime: LanProviderRuntimeStatus | null;
  item: LibraryMediaItem;
  savedProgress?: PlaybackProgress;
  onProgressSaved: (progress: PlaybackProgress) => void;
}) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const lastSavedAtRef = useRef(0);
  const resumeAppliedForItemRef = useRef<string | null>(null);
  const poster = posterUrl(baseUrl, token, item);
  const [dandanplay, setDandanplay] = useState<DandanplayResolveResult | null>(null);
  const [dandanplayMessage, setDandanplayMessage] = useState("");
  const [isDandanplayLoading, setIsDandanplayLoading] = useState(false);
  const [danmakuOverlayPreferences, setDanmakuOverlayPreferences] =
    useState(loadDanmakuOverlayPreferences);
  const [visibleDanmakuComments, setVisibleDanmakuComments] = useState<VisibleDanmakuComment[]>([]);
  const danmakuOverlayEnabled = danmakuOverlayPreferences.enabled;
  const danmakuDensity = danmakuOverlayPreferences.density;
  const danmakuOffsetSeconds = danmakuOverlayPreferences.offsetSeconds;

  useEffect(() => {
    lastSavedAtRef.current = 0;
    resumeAppliedForItemRef.current = null;
  }, [item.id]);

  useEffect(() => {
    setDandanplay(null);
    setDandanplayMessage("");
    setVisibleDanmakuComments([]);
  }, [item.id]);

  useEffect(() => {
    const video = videoRef.current;
    if (video) updateDanmakuOverlay(video);
  }, [dandanplay, danmakuOverlayEnabled, danmakuDensity, danmakuOffsetSeconds]);

  // Auto-load danmaku when the selected episode changes so the browser client
  // matches the native player: no manual button press is required. The fetch is
  // skipped (with a quiet note) when the server reports dandanplay is not ready.
  const dandanplayReady = providerRuntime?.dandanplay.commentFetchAvailable ?? true;
  useEffect(() => {
    if (!dandanplayReady) {
      setDandanplayMessage(
        "Danmaku provider is not configured on this server. Add dandanplay credentials in the player settings."
      );
      return;
    }
    void loadDandanplay();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [item.id, dandanplayReady]);

  useEffect(() => {
    saveDanmakuOverlayPreferences(danmakuOverlayPreferences);
  }, [danmakuOverlayPreferences]);

  useEffect(() => {
    const handlePageHide = () => {
      const video = videoRef.current;
      if (video) void persist(video, true, true);
    };
    window.addEventListener("pagehide", handlePageHide);
    return () => window.removeEventListener("pagehide", handlePageHide);
  }, [baseUrl, item.id, token]);

  function applySavedResume(video: HTMLVideoElement) {
    if (resumeAppliedForItemRef.current === item.id) return;
    resumeAppliedForItemRef.current = item.id;
    const resumePosition = resumePositionMs(savedProgress);
    if (resumePosition != null) {
      video.currentTime = resumePosition / 1000;
    }
  }

  function handleVideoTimeUpdate(video: HTMLVideoElement) {
    updateDanmakuOverlay(video);
    void persist(video);
  }

  function updateDanmakuOverlay(video: HTMLVideoElement) {
    if (!danmakuOverlayEnabled || !dandanplay?.comments.length) {
      setVisibleDanmakuComments((current) => (current.length > 0 ? [] : current));
      return;
    }
    setVisibleDanmakuComments(resolveVisibleDanmakuComments({
      comments: dandanplay.comments,
      currentTimeMs: Math.round(video.currentTime * 1000),
      density: danmakuDensity,
      offsetSeconds: danmakuOffsetSeconds
    }));
  }

  async function loadDandanplay() {
    setIsDandanplayLoading(true);
    setDandanplayMessage("Loading dandanplay...");
    try {
      const result = await fetchDandanplayResolve(baseUrl, token, item.id);
      setDandanplay(result);
      setDandanplayMessage(
        result.selectedMatch
          ? `${result.selectedMatch.displayTitle}: ${result.commentCount} comments`
          : "No dandanplay match returned."
      );
    } catch (error) {
      setDandanplay(null);
      setDandanplayMessage(describeDandanplayError(error));
    } finally {
      setIsDandanplayLoading(false);
    }
  }

  async function persist(video: HTMLVideoElement, force = false, keepalive = false) {
    const now = Date.now();
    if (!force && now - lastSavedAtRef.current < 10_000) return;
    const entry = createPlaybackProgress(
      item.id,
      video.currentTime,
      video.duration,
      item.durationMs ?? null,
      now
    );
    if (!entry) return;
    lastSavedAtRef.current = now;
    await saveProgress(baseUrl, token, entry, keepalive);
    onProgressSaved(entry);
  }

  return (
    <article className="player-panel">
      <div className="media-header">
        {poster ? <img src={poster} alt="" /> : <div className="poster-fallback">{item.seriesTitle.slice(0, 2)}</div>}
        <div>
          <h2>{item.seriesTitle}</h2>
          <p>{item.episodeTitle || item.relativePath}</p>
          <dl>
            <div>
              <dt>Progress</dt>
              <dd>{formatProgress(savedProgress)}</dd>
            </div>
            <div>
              <dt>File</dt>
              <dd>{item.relativePath}</dd>
            </div>
          </dl>
        </div>
      </div>

      <section className="provider-panel">
        <div className="provider-panel-header">
          <div>
            <h3>Dandanplay</h3>
            <p>{dandanplayMessage || "No danmaku loaded for this episode."}</p>
          </div>
          <button
            disabled={isDandanplayLoading || !dandanplayReady}
            onClick={() => void loadDandanplay()}
            type="button"
          >
            {isDandanplayLoading ? "Loading" : dandanplay ? "Reload danmaku" : "Load danmaku"}
          </button>
        </div>
        <div className="danmaku-controls">
          <label className="danmaku-toggle">
            <input
              checked={danmakuOverlayEnabled}
              onChange={(event) =>
                setDanmakuOverlayPreferences((current) => ({
                  ...current,
                  enabled: event.target.checked
                }))
              }
              type="checkbox"
            />
            Overlay
          </label>
          <label>
            Density
            <select
              value={danmakuDensity}
              onChange={(event) =>
                setDanmakuOverlayPreferences((current) => ({
                  ...current,
                  density: event.target.value as DanmakuDensity
                }))
              }
            >
              {danmakuDensityOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
          <label>
            Offset
            <input
              inputMode="decimal"
              onChange={(event) =>
                setDanmakuOverlayPreferences((current) => ({
                  ...current,
                  offsetSeconds: event.target.value
                }))
              }
              step="0.5"
              type="number"
              value={danmakuOffsetSeconds}
            />
          </label>
        </div>
        {dandanplay ? (
          <div className="provider-result">
            <dl className="provider-summary">
              <div>
                <dt>File</dt>
                <dd>{dandanplay.fingerprint.fileName}</dd>
              </div>
              <div>
                <dt>Matches</dt>
                <dd>{dandanplay.matches.length}</dd>
              </div>
              <div>
                <dt>Selected</dt>
                <dd>{formatDandanplayMatch(dandanplay.selectedMatch)}</dd>
              </div>
              <div>
                <dt>Comments</dt>
                <dd>{dandanplay.commentCount}</dd>
              </div>
            </dl>
            {dandanplay.comments.length > 0 ? (
              <ol className="danmaku-preview">
                {dandanplay.comments.slice(0, 6).map((comment) => (
                  <li key={comment.id}>
                    <time>{formatTimestamp(comment.timestampMs)}</time>
                    <span>{comment.text}</span>
                    <small>{comment.style.mode}</small>
                  </li>
                ))}
              </ol>
            ) : null}
          </div>
        ) : null}
      </section>

      {/* Direct one-off list editing was removed from the consumer surface.
          Persistent mapping, readback, conflict handling, and confirmed writes
          now live in Tracking administration.
      <section className="provider-panel external-list-panel" aria-hidden="true">
        <div className="provider-panel-header">
          <div>
            <h3>External list</h3>
            <p>
              {externalListMessage ||
                (externalListCapability
                  ? externalListCanRead || externalListCanWrite
                    ? "List sync credentials are ready."
                    : `List sync unavailable: ${externalListCapability!.reasonCode}`
                  : "Connect to see list sync readiness.")}
            </p>
          </div>
        </div>
        {mappedExternalAnimeIds.length > 0 ? (
          <div className="external-list-mappings" aria-label="Mapped external IDs">
            {mappedExternalAnimeIds.map((animeId) => (
              <button
                key={`${animeId.provider}-${animeId.value}`}
                onClick={() => {
                  setExternalListProvider(animeId.provider);
                  setExternalAnimeId(String(animeId.value));
                }}
                type="button"
              >
                {formatExternalAnimeId(animeId)}
              </button>
            ))}
          </div>
        ) : null}
        <div className="provider-search-form">
          <label>
            Search provider
            <select
              value={providerSearchProvider}
              onChange={(event) => setProviderSearchProvider(event.target.value as ExternalAnimeProvider)}
            >
              <option value="MY_ANIME_LIST">MyAnimeList</option>
              <option value="BANGUMI">Bangumi</option>
            </select>
          </label>
          <label className="provider-search-title">
            Title
            <input
              value={providerSearchQuery}
              onChange={(event) => setProviderSearchQuery(event.target.value)}
              placeholder={item.seriesTitle}
            />
          </label>
          <button
            disabled={isProviderSearchLoading || !providerSearchAvailable || !providerSearchQuery.trim()}
            onClick={() => void searchProviderMappings()}
            type="button"
          >
            {isProviderSearchLoading ? "Searching" : "Search"}
          </button>
        </div>
        {providerSearchMessage ? <p className="provider-search-message">{providerSearchMessage}</p> : null}
        {providerSearchResults.length > 0 ? (
          <ol className="provider-search-results" aria-label="Provider search candidates">
            {providerSearchResults.map((candidate) => (
              <li key={`${candidate.anime.id.provider}-${candidate.anime.id.value}`}>
                <div>
                  <strong>{formatProviderCandidateTitle(candidate)}</strong>
                  <small>
                    {formatExternalAnimeId(candidate.anime.id)} | {formatProviderCandidateMeta(candidate)}
                  </small>
                  {candidate.evidence.length > 0 ? <p>{candidate.evidence.slice(0, 3).join(" | ")}</p> : null}
                </div>
                <button onClick={() => selectProviderCandidate(candidate)} type="button">
                  Use ID
                </button>
              </li>
            ))}
          </ol>
        ) : null}
        <div className="external-list-form">
          <label>
            Provider
            <select
              value={externalListProvider}
              onChange={(event) => setExternalListProvider(event.target.value as ExternalAnimeProvider)}
            >
              <option value="MY_ANIME_LIST">MyAnimeList</option>
              <option value="BANGUMI">Bangumi</option>
            </select>
          </label>
          <label>
            Anime ID
            <input
              value={externalAnimeId}
              onChange={(event) => setExternalAnimeId(event.target.value)}
              inputMode="numeric"
              placeholder="52991"
            />
          </label>
          <label>
            Status
            <select
              value={externalListStatus}
              onChange={(event) => setExternalListStatus(event.target.value as ExternalAnimeListStatus)}
            >
              {externalListStatuses.map((status) => (
                <option key={status} value={status}>
                  {formatListStatus(status)}
                </option>
              ))}
            </select>
          </label>
          <label>
            Episodes
            <input
              value={externalWatchedEpisodes}
              onChange={(event) => setExternalWatchedEpisodes(event.target.value)}
              inputMode="numeric"
              placeholder="12"
            />
          </label>
          <label>
            Score
            <input
              value={externalScore}
              onChange={(event) => setExternalScore(event.target.value)}
              inputMode="numeric"
              placeholder="0-10"
            />
          </label>
          <div className="external-list-actions">
            <button
              disabled={isExternalListLoading || parsedExternalAnimeId == null || !externalListCanRead}
              onClick={() => void readExternalListEntry()}
              type="button"
            >
              {isExternalListLoading ? "Working" : "Read"}
            </button>
            <button
              disabled={isExternalListLoading || parsedExternalAnimeId == null || !externalListCanWrite}
              onClick={() => void writeExternalListEntry()}
              type="button"
            >
              Save
            </button>
          </div>
        </div>
        {externalListEntry ? (
          <dl className="provider-summary external-list-summary">
            <div>
              <dt>Provider</dt>
              <dd>{externalListEntry!.animeId.provider}</dd>
            </div>
            <div>
              <dt>Status</dt>
              <dd>{externalListEntry!.status ? formatListStatus(externalListEntry!.status!) : "None"}</dd>
            </div>
            <div>
              <dt>Episodes</dt>
              <dd>{externalListEntry!.watchedEpisodes ?? "None"}</dd>
            </div>
            <div>
              <dt>Score</dt>
              <dd>{externalListEntry!.score ?? "None"}</dd>
            </div>
          </dl>
        ) : null}
      </section>
      */}

      <div className="video-stage">
        <video
          ref={videoRef}
          controls
          playsInline
          poster={poster ?? undefined}
          src={mediaUrl(baseUrl, token, item)}
          onLoadedMetadata={(event) => applySavedResume(event.currentTarget)}
          onPause={(event) => void persist(event.currentTarget, true)}
          onEnded={(event) => void persist(event.currentTarget, true)}
          onSeeked={(event) => {
            updateDanmakuOverlay(event.currentTarget);
            void persist(event.currentTarget, true);
          }}
          onTimeUpdate={(event) => handleVideoTimeUpdate(event.currentTarget)}
        >
          {(item.subtitles ?? [])
            .filter((subtitle) => subtitle.mediaType === "text/vtt" || subtitle.streamPath.endsWith(".vtt"))
            .map((subtitle) => (
              <track
                key={subtitle.id}
                kind="subtitles"
                label={subtitle.label}
                src={subtitleUrl(baseUrl, token, subtitle)}
              />
            ))}
        </video>
        {danmakuOverlayEnabled && visibleDanmakuComments.length > 0 ? (
          <div aria-hidden="true" className="danmaku-overlay">
            {visibleDanmakuComments.map((entry) => (
              <span
                className={`danmaku-comment ${entry.className}`}
                key={`${entry.comment.id}-${entry.comment.timestampMs}`}
                style={{
                  animationDuration: `${entry.animationSeconds}s`,
                  color: entry.color,
                  fontSize: entry.fontSize,
                  top: `${entry.laneTopPercent}%`
                }}
              >
                {entry.comment.text}
              </span>
            ))}
          </div>
        ) : null}
      </div>
    </article>
  );
}

function formatProgress(progress?: PlaybackProgress): string {
  if (!progress) return "Not started";
  const position = Math.round(progress.positionMs / 60_000);
  const duration = progress.durationMs ? Math.round(progress.durationMs / 60_000) : null;
  return duration ? `${position} / ${duration} min` : `${position} min`;
}

function describeDandanplayError(error: unknown): string {
  if (error instanceof DanmakuApiError) {
    if (error.status === 502) {
      return "Danmaku is unavailable: the server could not reach dandanplay (check credentials or try again).";
    }
    if (error.status === 404) {
      return "This episode is not published on the server anymore.";
    }
    return error.message;
  }
  return "Dandanplay lookup failed.";
}

function formatDandanplayMatch(match?: DandanplayResolveResult["selectedMatch"]): string {
  if (!match) return "None";
  return match.displayTitle;
}

function formatTimestamp(timestampMs: number): string {
  const totalSeconds = Math.max(0, Math.floor(timestampMs / 1000));
  const minutes = Math.floor(totalSeconds / 60).toString().padStart(2, "0");
  const seconds = (totalSeconds % 60).toString().padStart(2, "0");
  return `${minutes}:${seconds}`;
}
