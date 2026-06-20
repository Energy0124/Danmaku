import { useEffect, useMemo, useRef, useState } from "react";
import {
  DanmakuApiError,
  DandanplayResolveResult,
  LibraryCatalog,
  LibraryMediaItem,
  PlaybackProgress,
  fetchDandanplayResolve,
  fetchLibrarySnapshot,
  mediaUrl,
  normalizeBaseUrl,
  posterUrl,
  saveProgress,
  subtitleUrl
} from "./api";

const tokenStoragePrefix = "danmaku.web.pairing.";

export function App() {
  const defaultBaseUrl = window.location.origin;
  const [baseUrl, setBaseUrl] = useState(defaultBaseUrl);
  const [pairingToken, setPairingToken] = useState(() => loadStoredToken(defaultBaseUrl));
  const [catalog, setCatalog] = useState<LibraryCatalog | null>(null);
  const [progress, setProgress] = useState<PlaybackProgress[]>([]);
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
      const snapshot = await fetchLibrarySnapshot(normalizedBaseUrl, pairingToken.trim());
      setCatalog(snapshot.catalog);
      setProgress(snapshot.progress);
      setSelectedId((current) => current ?? snapshot.catalog.items[0]?.id ?? null);
      storeToken(normalizedBaseUrl, pairingToken.trim());
      setMessage(
        `${snapshot.status.appName} ${snapshot.status.hostMode ?? "embedded-desktop"}: ` +
          `${snapshot.catalog.items.length} media items`
      );
    } catch (error) {
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

function PlayerPanel({
  baseUrl,
  token,
  item,
  savedProgress,
  onProgressSaved
}: {
  baseUrl: string;
  token: string;
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
  }, [item.id]);

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
