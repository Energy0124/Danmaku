import { FormEvent, useCallback, useEffect, useState } from "react";
import {
  AniRssDownloadJob,
  AniRssGroup,
  AniRssMode,
  AniRssSearchResult,
  AniRssSettings,
  AniRssSource,
  AniRssSubscription,
  AniRssSubscriptionPreview,
  AniRssSubscriptionRequest,
  createAniRssSubscription,
  deleteAniRssSubscription,
  fetchAniRssDownloads,
  fetchAniRssGroups,
  fetchAniRssSettings,
  fetchAniRssStatus,
  fetchAniRssSubscriptions,
  previewAniRssSubscription,
  refreshAniRssSubscription,
  saveAniRssSettings,
  searchAniRss,
  setAniRssSourceApproval,
  setAniRssSubscriptionEnabled
} from "./api";

type Props = { baseUrl: string; token: string };

const sourceLabels: Record<AniRssSource, string> = {
  MIKAN: "Mikan",
  ANIBT: "AniBT",
  ANIME_GARDEN: "Anime Garden",
  CUSTOM_RSS: "Custom RSS"
};

const emptySubscription: AniRssSubscriptionRequest = {
  source: "MIKAN",
  title: "",
  rssUrl: "",
  enabled: true
};

export function AniRssPanel({ baseUrl, token }: Props) {
  const [panelOpen, setPanelOpen] = useState(window.location.hash === "#ani-rss");
  const [settings, setSettings] = useState<AniRssSettings | null>(null);
  const [mode, setMode] = useState<AniRssMode>("DISABLED");
  const [endpoint, setEndpoint] = useState("http://127.0.0.1:7789");
  const [apiKey, setApiKey] = useState("");
  const [status, setStatus] = useState("Load ANI-RSS settings to begin.");
  const [subscriptions, setSubscriptions] = useState<AniRssSubscription[]>([]);
  const [downloads, setDownloads] = useState<AniRssDownloadJob[]>([]);
  const [source, setSource] = useState<AniRssSource>("MIKAN");
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<AniRssSearchResult[]>([]);
  const [groups, setGroups] = useState<AniRssGroup[]>([]);
  const [draft, setDraft] = useState<AniRssSubscriptionRequest>(emptySubscription);
  const [preview, setPreview] = useState<AniRssSubscriptionPreview | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    if (!token.trim()) return;
    setBusy(true);
    try {
      const current = await fetchAniRssSettings(baseUrl, token);
      setSettings(current);
      setMode(current.mode);
      setEndpoint(current.baseUrl);
      const firstSearchSource = current.approvedSources.find((value) => value !== "CUSTOM_RSS");
      setSource((selected) =>
        selected !== "CUSTOM_RSS" && current.approvedSources.includes(selected)
          ? selected
          : firstSearchSource ?? selected
      );
      setDraft((selected) =>
        current.approvedSources.includes(selected.source)
          ? selected
          : { ...selected, source: current.approvedSources[0] ?? selected.source }
      );
      const [connection, currentSubscriptions, currentDownloads] = await Promise.all([
        fetchAniRssStatus(baseUrl, token),
        current.mode === "DISABLED" ? Promise.resolve([]) : fetchAniRssSubscriptions(baseUrl, token),
        current.mode === "DISABLED" ? Promise.resolve([]) : fetchAniRssDownloads(baseUrl, token)
      ]);
      setSubscriptions(currentSubscriptions);
      setDownloads(currentDownloads);
      setStatus(connection.message + (connection.version ? ` (${connection.version})` : ""));
    } catch (error) {
      setStatus(describe(error));
    } finally {
      setBusy(false);
    }
  }, [baseUrl, token]);

  useEffect(() => void load(), [load]);

  async function saveConfiguration(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    try {
      const next = await saveAniRssSettings(baseUrl, token, {
        mode,
        baseUrl: endpoint,
        apiKey: apiKey.trim() || undefined,
        managedPort: settings?.managedPort ?? 7789,
        automaticRescan: settings?.automaticRescan ?? true,
        pathMappings: settings?.pathMappings ?? []
      });
      setSettings(next);
      setApiKey("");
      setStatus("ANI-RSS settings saved. Source approvals reset when the endpoint changes.");
      await load();
    } catch (error) {
      setStatus(describe(error));
      setBusy(false);
    }
  }

  async function toggleApproval(candidate: AniRssSource) {
    if (!settings) return;
    setBusy(true);
    try {
      const approved = settings.approvedSources.includes(candidate);
      const next = await setAniRssSourceApproval(baseUrl, token, candidate, !approved);
      setSettings(next);
      const firstSearchSource = next.approvedSources.find((value) => value !== "CUSTOM_RSS");
      setSource((selected) =>
        selected !== "CUSTOM_RSS" && next.approvedSources.includes(selected)
          ? selected
          : firstSearchSource ?? selected
      );
      setDraft((selected) =>
        next.approvedSources.includes(selected.source)
          ? selected
          : { ...selected, source: next.approvedSources[0] ?? selected.source }
      );
      setStatus(approved ? `${sourceLabels[candidate]} access revoked.` : `${sourceLabels[candidate]} approved.`);
    } catch (error) {
      setStatus(describe(error));
    } finally {
      setBusy(false);
    }
  }

  async function search(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setGroups([]);
    setPreview(null);
    try {
      setResults(await searchAniRss(baseUrl, token, { source, query }));
      setStatus("Search complete. Choose a series to select its release group.");
    } catch (error) {
      setStatus(describe(error));
    } finally {
      setBusy(false);
    }
  }

  async function chooseResult(result: AniRssSearchResult) {
    setBusy(true);
    try {
      const choices = await fetchAniRssGroups(baseUrl, token, result.source, result.locator);
      setGroups(choices);
      setDraft({ ...emptySubscription, source: result.source, title: result.title, bgmUrl: result.bgmUrl ?? undefined });
      setStatus(choices.length ? "Choose a release group." : "No release groups were returned.");
    } catch (error) {
      setStatus(describe(error));
    } finally {
      setBusy(false);
    }
  }

  function chooseGroup(group: AniRssGroup) {
    setDraft((current) => ({
      ...current,
      rssUrl: group.rssUrl,
      subgroup: group.name,
      bgmUrl: group.bgmUrl ?? current.bgmUrl
    }));
    setPreview(null);
  }

  async function previewDraft(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    try {
      setPreview(await previewAniRssSubscription(baseUrl, token, draft));
      setStatus("Preview ready. Confirm to enable automatic downloads.");
    } catch (error) {
      setStatus(describe(error));
    } finally {
      setBusy(false);
    }
  }

  async function confirmSubscription() {
    setBusy(true);
    try {
      await createAniRssSubscription(baseUrl, token, draft);
      setDraft(emptySubscription);
      setPreview(null);
      setGroups([]);
      setResults([]);
      setStatus("Automatic download added.");
      await load();
    } catch (error) {
      setStatus(describe(error));
      setBusy(false);
    }
  }

  async function mutateSubscription(action: () => Promise<unknown>, message: string) {
    setBusy(true);
    try {
      await action();
      setStatus(message);
      await load();
    } catch (error) {
      setStatus(describe(error));
      setBusy(false);
    }
  }

  return (
    <details
      className="ani-rss-panel"
      id="ani-rss"
      open={panelOpen}
      onToggle={(event) => setPanelOpen(event.currentTarget.open)}
    >
      <summary>
        <span>Automatic anime downloads</span>
        <small>ANI-RSS · desktop and mobile administration</small>
      </summary>
      <div className="ani-rss-content">
        <p className="ani-rss-message" role="status">{status}</p>

        <form className="ani-rss-settings" onSubmit={saveConfiguration}>
          <label>Mode
            <select value={mode} onChange={(event) => setMode(event.target.value as AniRssMode)}>
              <option value="DISABLED">Disabled</option>
              <option value="EXTERNAL">External ANI-RSS</option>
              <option value="MANAGED_WINDOWS">Managed on Windows</option>
            </select>
          </label>
          <label>ANI-RSS URL
            <input disabled={mode === "MANAGED_WINDOWS"} value={endpoint} onChange={(event) => setEndpoint(event.target.value)} />
          </label>
          {mode === "MANAGED_WINDOWS" && settings ? <label>Managed port
            <input
              min={1}
              max={65535}
              type="number"
              value={settings.managedPort}
              onChange={(event) => setSettings({ ...settings, managedPort: Number(event.target.value) })}
            />
          </label> : null}
          <label>API key {settings?.hasApiKey ? "(saved)" : ""}
            <input autoComplete="off" type="password" value={apiKey} onChange={(event) => setApiKey(event.target.value)} />
          </label>
          <label className="ani-rss-checkbox">
            <input
              checked={settings?.automaticRescan ?? true}
              onChange={(event) => setSettings((current) => current ? { ...current, automaticRescan: event.target.checked } : current)}
              type="checkbox"
            />
            Rescan the library automatically
          </label>
          <button disabled={busy || !token.trim()} type="submit">Save and test</button>
          {settings?.advancedUiUrl ? <a href={settings.advancedUiUrl} target="_blank" rel="noreferrer">Open advanced ANI-RSS UI</a> : null}
          {settings?.mode === "MANAGED_WINDOWS" ? <small>The managed ANI-RSS UI stays on the Windows host; use this authenticated panel from mobile.</small> : null}
          {settings ? <div className="ani-rss-mappings">
            <strong>Download path mappings</strong>
            {settings.pathMappings.map((mapping, index) => <div key={index}>
              <input
                aria-label="ANI-RSS path"
                placeholder="ANI-RSS path, e.g. /media"
                value={mapping.remotePrefix}
                onChange={(event) => setSettings({ ...settings, pathMappings: settings.pathMappings.map((value, candidate) => candidate === index ? { ...value, remotePrefix: event.target.value } : value) })}
              />
              <input
                aria-label="Local library path"
                placeholder="Local path, e.g. D:\\Anime"
                value={mapping.localPrefix}
                onChange={(event) => setSettings({ ...settings, pathMappings: settings.pathMappings.map((value, candidate) => candidate === index ? { ...value, localPrefix: event.target.value } : value) })}
              />
              <button onClick={() => setSettings({ ...settings, pathMappings: settings.pathMappings.filter((_, candidate) => candidate !== index) })} type="button">Remove</button>
            </div>)}
            <button onClick={() => setSettings({ ...settings, pathMappings: [...settings.pathMappings, { remotePrefix: "", localPrefix: "" }] })} type="button">Add mapping</button>
          </div> : null}
        </form>

        {settings && settings.mode !== "DISABLED" ? (
          <>
            <section>
              <h3>Approved sources</h3>
              <p>Approval is stored for this ANI-RSS endpoint. Danmaku only searches sources you explicitly approve.</p>
              <div className="ani-rss-actions">
                {settings.supportedSources.map((candidate) => (
                  <button disabled={busy} key={candidate} onClick={() => void toggleApproval(candidate)} type="button">
                    {settings.approvedSources.includes(candidate) ? "✓ " : "+ "}{sourceLabels[candidate]}
                  </button>
                ))}
              </div>
            </section>

            <section>
              <h3>Add a series</h3>
              <form className="ani-rss-search" onSubmit={search}>
                <select value={source} onChange={(event) => setSource(event.target.value as AniRssSource)}>
                  {settings.approvedSources.filter((value) => value !== "CUSTOM_RSS").map((value) => <option key={value}>{value}</option>)}
                </select>
                <input required value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Anime title" />
                <button disabled={busy || !settings.approvedSources.includes(source)} type="submit">Search</button>
              </form>
              <div className="ani-rss-results">
                {results.map((result) => <button key={`${result.source}-${result.id}`} onClick={() => void chooseResult(result)} type="button">{result.title}</button>)}
                {groups.map((group) => <button key={group.rssUrl} onClick={() => chooseGroup(group)} type="button">{group.name}</button>)}
              </div>
              <form className="ani-rss-draft" onSubmit={previewDraft}>
                <label>Title<input required value={draft.title} onChange={(event) => setDraft({ ...draft, title: event.target.value })} /></label>
                <label>RSS URL<input required value={draft.rssUrl} onChange={(event) => setDraft({ ...draft, rssUrl: event.target.value })} /></label>
                <label>Source
                  <select value={draft.source} onChange={(event) => setDraft({ ...draft, source: event.target.value as AniRssSource })}>
                    {settings.approvedSources.map((value) => <option key={value}>{value}</option>)}
                  </select>
                </label>
                <button disabled={busy || !settings.approvedSources.includes(draft.source)} type="submit">Preview</button>
              </form>
              {preview ? <div className="ani-rss-preview"><strong>{preview.title}</strong><span>{preview.downloadPath ?? "ANI-RSS default download folder"}</span><span>{preview.sampleTitles.slice(0, 3).join(" · ")}</span><button disabled={busy} onClick={() => void confirmSubscription()} type="button">Confirm auto download</button></div> : null}
            </section>

            <section>
              <div className="ani-rss-section-heading"><h3>Subscriptions</h3><button disabled={busy} onClick={() => void load()} type="button">Refresh</button></div>
              <div className="ani-rss-list">
                {subscriptions.map((item) => <article key={item.id}><div><strong>{item.title}</strong><small>{sourceLabels[item.source]}{item.subgroup ? ` · ${item.subgroup}` : ""}</small></div><div className="ani-rss-actions"><button onClick={() => void mutateSubscription(() => setAniRssSubscriptionEnabled(baseUrl, token, item.id, !item.enabled), item.enabled ? "Subscription paused." : "Subscription resumed.")} type="button">{item.enabled ? "Pause" : "Resume"}</button><button onClick={() => void mutateSubscription(() => refreshAniRssSubscription(baseUrl, token, item.id), "Refresh requested.")} type="button">Check now</button><button className="danger" onClick={() => void mutateSubscription(() => deleteAniRssSubscription(baseUrl, token, item.id), "Subscription removed; downloaded files were kept.")} type="button">Remove</button></div></article>)}
                {!subscriptions.length ? <p>No ANI-RSS subscriptions yet.</p> : null}
              </div>
            </section>

            <section>
              <h3>Downloads</h3>
              <div className="ani-rss-list">{downloads.map((job) => <article key={job.id}><div><strong>{job.name}</strong><small>{job.state} · {Math.round(job.progressPercent)}%</small></div><progress max={100} value={job.progressPercent} /></article>)}{!downloads.length ? <p>No active downloads.</p> : null}</div>
            </section>
          </>
        ) : null}
      </div>
    </details>
  );
}

function describe(error: unknown): string {
  return error instanceof Error ? error.message : "ANI-RSS request failed.";
}
