import { useEffect, useMemo, useRef, useState } from "react";
import {
  DanmakuApiError,
  DandanplayResolveResult,
  ExternalAnimeListEntry,
  ExternalAnimeListStatus,
  ExternalAnimeProvider,
  LanProviderRuntimeStatus,
  LibraryCatalog,
  LibraryMediaItem,
  PlaybackProgress,
  fetchDandanplayResolve,
  fetchExternalListEntry,
  fetchLibrarySnapshot,
  fetchProviderRuntime,
  mediaUrl,
  normalizeBaseUrl,
  posterUrl,
  saveExternalListEntry,
  saveProgress,
  subtitleUrl
} from "./api";

const tokenStoragePrefix = "danmaku.web.pairing.";
const externalListStatuses: ExternalAnimeListStatus[] = [
  "WATCHING",
  "COMPLETED",
  "ON_HOLD",
  "DROPPED",
  "PLAN_TO_WATCH"
];

export function App() {
  const defaultBaseUrl = window.location.origin;
  const [baseUrl, setBaseUrl] = useState(defaultBaseUrl);
  const [pairingToken, setPairingToken] = useState(() => loadStoredToken(defaultBaseUrl));
  const [catalog, setCatalog] = useState<LibraryCatalog | null>(null);
  const [progress, setProgress] = useState<PlaybackProgress[]>([]);
  const [providerRuntime, setProviderRuntime] = useState<LanProviderRuntimeStatus | null>(null);
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
      storeToken(normalizedBaseUrl, token);
      setMessage(
        `${snapshot.status.appName} ${snapshot.status.hostMode ?? "embedded-desktop"}: ` +
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
            Pairing code
            <input
              value={pairingToken}
              onChange={(event) => setPairingToken(event.target.value)}
              inputMode="numeric"
              autoComplete="one-time-code"
            />
          </label>
          <button disabled={isLoading || !pairingToken.trim()} type="submit">
            {isLoading ? "Connecting" : "Connect"}
          </button>
        </form>
      </header>

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
              token={pairingToken.trim()}
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
  const poster = posterUrl(baseUrl, token, item);
  const [dandanplay, setDandanplay] = useState<DandanplayResolveResult | null>(null);
  const [dandanplayMessage, setDandanplayMessage] = useState("");
  const [isDandanplayLoading, setIsDandanplayLoading] = useState(false);
  const [externalListProvider, setExternalListProvider] =
    useState<ExternalAnimeProvider>("MY_ANIME_LIST");
  const [externalAnimeId, setExternalAnimeId] = useState("");
  const [externalListStatus, setExternalListStatus] =
    useState<ExternalAnimeListStatus>("WATCHING");
  const [externalWatchedEpisodes, setExternalWatchedEpisodes] = useState("");
  const [externalScore, setExternalScore] = useState("");
  const [externalListEntry, setExternalListEntry] = useState<ExternalAnimeListEntry | null>(null);
  const [externalListMessage, setExternalListMessage] = useState("");
  const [isExternalListLoading, setIsExternalListLoading] = useState(false);
  const externalListCapability = externalListProvider === "MY_ANIME_LIST"
    ? providerRuntime?.myAnimeList
    : providerRuntime?.bangumi;
  const externalListCanRead = externalListCapability?.listReadAvailable ?? false;
  const externalListCanWrite = externalListCapability?.listWriteAvailable ?? false;
  const parsedExternalAnimeId = parsePositiveExternalAnimeId(externalAnimeId);

  useEffect(() => {
    lastSavedAtRef.current = 0;
    const video = videoRef.current;
    if (video && savedProgress?.positionMs && savedProgress.positionMs > 0) {
      video.currentTime = savedProgress.positionMs / 1000;
    }
  }, [item.id, savedProgress?.positionMs]);

  useEffect(() => {
    setDandanplay(null);
    setDandanplayMessage("");
    setExternalListEntry(null);
    setExternalListMessage("");
  }, [item.id]);

  useEffect(() => {
    setExternalListEntry(null);
    setExternalListMessage("");
  }, [externalListProvider, externalAnimeId]);

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
      setDandanplayMessage(error instanceof DanmakuApiError ? error.message : "Dandanplay lookup failed.");
    } finally {
      setIsDandanplayLoading(false);
    }
  }

  async function readExternalListEntry() {
    if (parsedExternalAnimeId == null) {
      setExternalListMessage("Enter a positive MAL or Bangumi anime ID.");
      return;
    }
    setIsExternalListLoading(true);
    setExternalListMessage("Reading external list entry...");
    try {
      const entry = await fetchExternalListEntry(baseUrl, token, {
        provider: externalListProvider,
        value: parsedExternalAnimeId
      });
      applyExternalListEntry(entry);
      setExternalListMessage(formatExternalListEntry(entry));
    } catch (error) {
      setExternalListEntry(null);
      setExternalListMessage(error instanceof DanmakuApiError ? error.message : "External list readback failed.");
    } finally {
      setIsExternalListLoading(false);
    }
  }

  async function writeExternalListEntry() {
    if (parsedExternalAnimeId == null) {
      setExternalListMessage("Enter a positive MAL or Bangumi anime ID.");
      return;
    }
    setIsExternalListLoading(true);
    setExternalListMessage("Updating external list entry...");
    try {
      const entry = await saveExternalListEntry(baseUrl, token, {
        animeId: {
          provider: externalListProvider,
          value: parsedExternalAnimeId
        },
        status: externalListStatus,
        watchedEpisodes: parseOptionalInteger(externalWatchedEpisodes, 0, 10_000, "Episodes"),
        score: parseOptionalInteger(externalScore, 0, 10, "Score")
      });
      applyExternalListEntry(entry);
      setExternalListMessage(formatExternalListEntry(entry));
    } catch (error) {
      setExternalListMessage(error instanceof Error ? error.message : "External list update failed.");
    } finally {
      setIsExternalListLoading(false);
    }
  }

  function applyExternalListEntry(entry: ExternalAnimeListEntry) {
    setExternalListEntry(entry);
    setExternalListStatus(entry.status ?? externalListStatus);
    setExternalWatchedEpisodes(entry.watchedEpisodes == null ? "" : String(entry.watchedEpisodes));
    setExternalScore(entry.score == null ? "" : String(entry.score));
  }

  async function persist(video: HTMLVideoElement) {
    const now = Date.now();
    if (now - lastSavedAtRef.current < 10_000 && !video.paused && !video.ended) return;
    lastSavedAtRef.current = now;
    const entry: PlaybackProgress = {
      mediaId: item.id,
      positionMs: Math.round(video.currentTime * 1000),
      durationMs: Number.isFinite(video.duration) ? Math.round(video.duration * 1000) : item.durationMs ?? null,
      updatedAtEpochMs: now
    };
    await saveProgress(baseUrl, token, entry);
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
          <button disabled={isDandanplayLoading} onClick={() => void loadDandanplay()} type="button">
            {isDandanplayLoading ? "Loading" : "Load danmaku"}
          </button>
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

      <section className="provider-panel external-list-panel">
        <div className="provider-panel-header">
          <div>
            <h3>External list</h3>
            <p>
              {externalListMessage ||
                (externalListCapability
                  ? externalListCanRead || externalListCanWrite
                    ? "List sync credentials are ready."
                    : `List sync unavailable: ${externalListCapability.reasonCode}`
                  : "Connect to see list sync readiness.")}
            </p>
          </div>
        </div>
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
              <dd>{externalListEntry.animeId.provider}</dd>
            </div>
            <div>
              <dt>Status</dt>
              <dd>{externalListEntry.status ? formatListStatus(externalListEntry.status) : "None"}</dd>
            </div>
            <div>
              <dt>Episodes</dt>
              <dd>{externalListEntry.watchedEpisodes ?? "None"}</dd>
            </div>
            <div>
              <dt>Score</dt>
              <dd>{externalListEntry.score ?? "None"}</dd>
            </div>
          </dl>
        ) : null}
      </section>

      <video
        ref={videoRef}
        controls
        playsInline
        poster={poster ?? undefined}
        src={mediaUrl(baseUrl, token, item)}
        onPause={(event) => void persist(event.currentTarget)}
        onEnded={(event) => void persist(event.currentTarget)}
        onTimeUpdate={(event) => void persist(event.currentTarget)}
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
    </article>
  );
}

function formatProgress(progress?: PlaybackProgress): string {
  if (!progress) return "Not started";
  const position = Math.round(progress.positionMs / 60_000);
  const duration = progress.durationMs ? Math.round(progress.durationMs / 60_000) : null;
  return duration ? `${position} / ${duration} min` : `${position} min`;
}

function formatDandanplayMatch(match?: DandanplayResolveResult["selectedMatch"]): string {
  if (!match) return "None";
  return match.displayTitle;
}

function formatExternalListEntry(entry: ExternalAnimeListEntry): string {
  const status = entry.status ? formatListStatus(entry.status) : "no status";
  const episodes = entry.watchedEpisodes == null ? "unknown episodes" : `${entry.watchedEpisodes} episodes`;
  const score = entry.score == null ? "no score" : `score ${entry.score}`;
  return `${status}, ${episodes}, ${score}`;
}

function formatListStatus(status: ExternalAnimeListStatus): string {
  return status
    .toLocaleLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toLocaleUpperCase() + part.slice(1))
    .join(" ");
}

function parsePositiveExternalAnimeId(value: string): number | null {
  const parsed = Number(value.trim());
  if (!Number.isInteger(parsed) || parsed <= 0) return null;
  return parsed;
}

function parseOptionalInteger(value: string, min: number, max: number, label: string): number | undefined {
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  const parsed = Number(trimmed);
  if (!Number.isInteger(parsed) || parsed < min || parsed > max) {
    throw new Error(`${label} must be an integer between ${min} and ${max}.`);
  }
  return parsed;
}

function formatTimestamp(timestampMs: number): string {
  const totalSeconds = Math.max(0, Math.floor(timestampMs / 1000));
  const minutes = Math.floor(totalSeconds / 60).toString().padStart(2, "0");
  const seconds = (totalSeconds % 60).toString().padStart(2, "0");
  return `${minutes}:${seconds}`;
}

function loadStoredToken(baseUrl: string): string {
  return window.localStorage.getItem(`${tokenStoragePrefix}${normalizeBaseUrl(baseUrl)}`) ?? "";
}

function storeToken(baseUrl: string, token: string): void {
  window.localStorage.setItem(`${tokenStoragePrefix}${normalizeBaseUrl(baseUrl)}`, token);
}
