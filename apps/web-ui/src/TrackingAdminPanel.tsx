import { useEffect, useMemo, useState } from "react";
import {
  DanmakuApiError,
  ExternalAnimeId,
  ExternalAnimeProvider,
  ExternalAnimeMatchCandidate,
  ExternalTrackingDocument,
  ExternalTrackingOperationResponse,
  deleteExternalTrackingMapping,
  executeExternalTrackingSync,
  fetchProviderSearch,
  fetchExternalTracking,
  importExternalTrackingConflict,
  refreshExternalTrackingReadback,
  saveExternalTrackingMapping
} from "./api";

const trackingProviders: ExternalAnimeProvider[] = ["MY_ANIME_LIST", "BANGUMI"];

export function TrackingAdminPanel({
  baseUrl,
  onAccountStatusMayHaveChanged
}: {
  baseUrl: string;
  onAccountStatusMayHaveChanged: () => void;
}) {
  const [document, setDocument] = useState<ExternalTrackingDocument | null>(null);
  const [message, setMessage] = useState("Loading tracking administration...");
  const [isBusy, setIsBusy] = useState(false);
  const [selectedSeriesId, setSelectedSeriesId] = useState("");
  const [provider, setProvider] = useState<ExternalAnimeProvider>("MY_ANIME_LIST");
  const [animeId, setAnimeId] = useState("");
  const [searchQuery, setSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState<ExternalAnimeMatchCandidate[]>([]);
  const [previewReviewed, setPreviewReviewed] = useState(false);
  const selectedSeries = useMemo(
    () => document?.series.find((series) => series.id === selectedSeriesId) ?? null,
    [document, selectedSeriesId]
  );
  const mappingRows = useMemo(
    () =>
      document?.series.flatMap((series) =>
        series.mappings.map((mapping) => ({ mapping, series }))
      ) ?? [],
    [document]
  );

  useEffect(() => {
    setDocument(null);
    setPreviewReviewed(false);
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [baseUrl]);

  async function load() {
    setIsBusy(true);
    setMessage("Loading tracking preview...");
    try {
      updateDocument(await fetchExternalTracking(baseUrl));
      setMessage("Tracking preview loaded. No provider data was changed.");
    } catch (error) {
      setMessage(describeError(error, "Tracking preview could not be loaded."));
    } finally {
      setIsBusy(false);
    }
  }

  function updateDocument(next: ExternalTrackingDocument) {
    setDocument(next);
    setPreviewReviewed(false);
    setSelectedSeriesId((current) =>
      next.series.some((series) => series.id === current)
        ? current
        : next.series[0]?.id ?? ""
    );
  }

  async function saveMapping() {
    const parsedAnimeId = parsePositiveId(animeId);
    if (!selectedSeries || parsedAnimeId == null) {
      setMessage("Choose a series and enter a positive provider anime ID.");
      return;
    }
    setIsBusy(true);
    setMessage("Saving the series mapping...");
    try {
      updateDocument(
        await saveExternalTrackingMapping(baseUrl, selectedSeries.id, {
          provider,
          value: parsedAnimeId
        })
      );
      setAnimeId("");
      setMessage("Series mapping saved. Review the regenerated preview before syncing.");
    } catch (error) {
      setMessage(describeError(error, "Series mapping could not be saved."));
    } finally {
      setIsBusy(false);
    }
  }

  async function searchMappings() {
    const query = searchQuery.trim() || selectedSeries?.title || "";
    if (!query) return;
    setIsBusy(true);
    setMessage("Searching " + providerLabel(provider) + "...");
    try {
      const results = await fetchProviderSearch(baseUrl, query, {
        providers: [provider],
        limit: 8,
        episodeCount: selectedSeries?.episodeCount
      });
      setSearchResults(results);
      setMessage(results.length ? "Choose the matching series below." : "No matches found.");
    } catch (error) {
      setMessage(describeError(error, "Provider search failed."));
    } finally {
      setIsBusy(false);
    }
  }

  async function useSearchResult(candidate: ExternalAnimeMatchCandidate) {
    if (!selectedSeries) return;
    setIsBusy(true);
    setMessage("Saving the selected series mapping...");
    try {
      updateDocument(
        await saveExternalTrackingMapping(
          baseUrl,
          selectedSeries.id,
          candidate.anime.id
        )
      );
      setSearchResults([]);
      setMessage("Series mapping saved. Review the regenerated preview before syncing.");
    } catch (error) {
      setMessage(describeError(error, "Series mapping could not be saved."));
    } finally {
      setIsBusy(false);
    }
  }

  async function importConflict(
    localSeriesId: string,
    animeId: ExternalAnimeId,
    expectedEpisodes: number
  ) {
    setIsBusy(true);
    setMessage("Importing provider progress into the local library...");
    try {
      const response = await importExternalTrackingConflict(
        baseUrl,
        localSeriesId,
        animeId,
        expectedEpisodes
      );
      updateDocument(response.document);
      setMessage(String(response.importedCount) + " local episode(s) marked watched.");
    } catch (error) {
      setMessage(describeError(error, "Provider progress could not be imported."));
    } finally {
      setIsBusy(false);
    }
  }

  async function removeMapping(localSeriesId: string, mappedAnimeId: ExternalAnimeId) {
    setIsBusy(true);
    setMessage("Removing the series mapping...");
    try {
      updateDocument(
        await deleteExternalTrackingMapping(baseUrl, localSeriesId, mappedAnimeId)
      );
      setMessage("Series mapping removed. The tracking preview was regenerated.");
    } catch (error) {
      setMessage(describeError(error, "Series mapping could not be removed."));
    } finally {
      setIsBusy(false);
    }
  }

  async function refreshReadback() {
    setIsBusy(true);
    setMessage("Reading mapped entries from MAL and Bangumi...");
    try {
      const response = await refreshExternalTrackingReadback(baseUrl);
      updateDocument(response.document);
      setMessage(operationMessage("Readback", response));
    } catch (error) {
      setMessage(describeError(error, "External list readback failed."));
      onAccountStatusMayHaveChanged();
    } finally {
      setIsBusy(false);
    }
  }

  async function syncPreviewedUpdates() {
    if (!previewReviewed || !document) {
      setMessage("Review and acknowledge the preview before syncing.");
      return;
    }
    setIsBusy(true);
    setMessage("Writing the previewed updates to external providers...");
    try {
      const response = await executeExternalTrackingSync(
        baseUrl,
        document.plan.updates.map((candidate) => candidate.update)
      );
      updateDocument(response.document);
      setMessage(operationMessage("Sync", response));
    } catch (error) {
      setMessage(describeError(error, "External list sync failed."));
      onAccountStatusMayHaveChanged();
    } finally {
      setIsBusy(false);
    }
  }

  const summary = document?.plan.summary;
  return (
    <section className="tracking-admin-shell">
      <details>
        <summary>
          <span>
            <strong>Tracking administration</strong>
            <small>Persist mappings, preview changes, read provider state, and sync deliberately.</small>
          </span>
          <span className={document ? "admin-state ready" : "admin-state limited"}>
            {document ? String(summary?.updateCount ?? 0) + " ready" : "Locked"}
          </span>
        </summary>

        <div className="tracking-admin-content">
          <p className="tracking-admin-message">{message}</p>
          <div className="tracking-summary" aria-label="Tracking preview summary">
            <SummaryCard label="Ready updates" value={summary?.updateCount ?? 0} />
            <SummaryCard label="Conflicts" value={summary?.conflictCount ?? 0} />
            <SummaryCard label="Skipped/no-op" value={summary?.skippedCount ?? 0} />
            <SummaryCard label="Failed retries" value={summary?.failureCount ?? 0} />
          </div>

          {document ? (
            <>
              <div className="tracking-actions">
                <button disabled={isBusy} onClick={() => void load()} type="button">
                  Reload preview
                </button>
                <button disabled={isBusy || document.mappings.length === 0} onClick={() => void refreshReadback()} type="button">
                  Read provider state
                </button>
              </div>

              <fieldset className="tracking-mapping-form">
                <legend>Series mapping</legend>
                <label>
                  Local series
                  <select value={selectedSeriesId} onChange={(event) => setSelectedSeriesId(event.target.value)}>
                    {document.series.map((series) => (
                      <option key={series.id} value={series.id}>
                        {series.title} ({series.episodeCount} episodes)
                        {groupLabel(series.localSeriesIds.length)}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Provider
                  <select value={provider} onChange={(event) => setProvider(event.target.value as ExternalAnimeProvider)}>
                    {trackingProviders.map((candidate) => (
                      <option key={candidate} value={candidate}>{providerLabel(candidate)}</option>
                    ))}
                  </select>
                </label>
                <label>
                  Search title
                  <input
                    value={searchQuery}
                    onChange={(event) => setSearchQuery(event.target.value)}
                    placeholder={selectedSeries?.title || "Anime title"}
                  />
                </label>
                <button
                  disabled={isBusy || !selectedSeriesId}
                  onClick={() => void searchMappings()}
                  type="button"
                >
                  Search provider
                </button>
                <label>
                  Anime ID
                  <input inputMode="numeric" value={animeId} onChange={(event) => setAnimeId(event.target.value)} placeholder="52991" />
                </label>
                <button disabled={isBusy || !selectedSeriesId || parsePositiveId(animeId) == null} onClick={() => void saveMapping()} type="button">
                  Save mapping
                </button>
              </fieldset>

              {searchResults.length > 0 ? (
                <ol className="provider-search-results" aria-label="Series mapping candidates">
                  {searchResults.map((candidate) => (
                    <li key={candidate.anime.id.provider + "-" + String(candidate.anime.id.value)}>
                      <div>
                        <strong>{candidate.anime.titles.english || candidate.anime.titles.primary}</strong>
                        <small>
                          {formatAnimeId(candidate.anime.id)}
                          {candidate.anime.startYear ? " · " + String(candidate.anime.startYear) : ""}
                          {candidate.anime.episodeCount ? " · " + String(candidate.anime.episodeCount) + " episodes" : ""}
                        </small>
                      </div>
                      <button disabled={isBusy} onClick={() => void useSearchResult(candidate)} type="button">
                        Use this match
                      </button>
                    </li>
                  ))}
                </ol>
              ) : null}

              <div className="tracking-mapping-list">
                {mappingRows.length === 0 ? (
                  <p>No persisted series mappings yet.</p>
                ) : (
                  mappingRows.map(({ mapping, series }) => (
                    <div
                      key={
                        series.id +
                        "-" +
                        mapping.animeId.provider +
                        "-" +
                        String(mapping.animeId.value)
                      }
                    >
                      <span>
                        <strong>{series.title}</strong>
                        <small>
                          {formatAnimeId(mapping.animeId)} · {mapping.source.toLocaleLowerCase()}
                          {groupLabel(series.localSeriesIds.length)}
                        </small>
                      </span>
                      <button
                        disabled={isBusy}
                        onClick={() => void removeMapping(series.id, mapping.animeId)}
                        type="button"
                      >
                        Remove
                      </button>
                    </div>
                  ))
                )}
              </div>

              <section className="tracking-preview">
                <h3>Sync preview</h3>
                {document.plan.updates.length === 0 ? (
                  <p>No provider writes are currently ready.</p>
                ) : (
                  <ol>
                    {document.plan.updates.map((update) => (
                      <li key={update.localSeriesId + "-" + update.mapping.animeId.provider}>
                        <strong>{update.seriesTitle}</strong>
                        <span>
                          {providerLabel(update.mapping.animeId.provider)} ·{" "}
                          {statusLabel(update.update.status)} · {update.update.watchedEpisodes ?? 0}/
                          {update.episodeCount} watched
                          {groupLabel(update.localSeriesIds.length)}
                        </span>
                      </li>
                    ))}
                  </ol>
                )}
              </section>

              {document.plan.mappingConflicts.length > 0 ? (
                <section className="tracking-conflicts">
                  <h3>Mapping conflicts blocked from sync</h3>
                  <p>Remove the incorrect provider ID to resolve each logical series group.</p>
                  <ol>
                    {document.plan.mappingConflicts.map((conflict) => (
                      <li key={conflict.localSeriesId + "-" + conflict.provider}>
                        <strong>{conflict.seriesTitle}</strong>
                        <span>
                          {providerLabel(conflict.provider)} has multiple IDs:{" "}
                          {conflict.animeIds
                            .map((candidate) => "#" + String(candidate.value))
                            .join(", ")}
                          {groupLabel(conflict.localSeriesIds.length)}
                        </span>
                        <div className="tracking-conflict-actions">
                          {conflict.animeIds.map((candidate) => (
                            <button
                              disabled={isBusy}
                              key={candidate.provider + "-" + String(candidate.value)}
                              onClick={() =>
                                void removeMapping(conflict.localSeriesId, candidate)
                              }
                              type="button"
                            >
                              Remove {formatAnimeId(candidate)}
                            </button>
                          ))}
                        </div>
                      </li>
                    ))}
                  </ol>
                </section>
              ) : null}

              {document.plan.conflicts.length > 0 ? (
                <section className="tracking-conflicts">
                  <h3>Conflicts blocked from sync</h3>
                  <ol>
                    {document.plan.conflicts.map((conflict) => (
                      <li key={conflict.localSeriesId + "-" + conflict.mapping.animeId.provider}>
                        <strong>{conflict.seriesTitle}</strong>
                        <span>
                          {providerLabel(conflict.mapping.animeId.provider)} has{" "}
                          {conflict.externalEntry.watchedEpisodes ?? 0}; local has{" "}
                          {conflict.localUpdate.watchedEpisodes ?? 0}
                          {groupLabel(conflict.localSeriesIds.length)}.
                        </span>
                        <button
                          disabled={isBusy || conflict.externalEntry.watchedEpisodes == null}
                          onClick={() =>
                            void importConflict(
                              conflict.localSeriesId,
                              conflict.mapping.animeId,
                              conflict.externalEntry.watchedEpisodes ?? 0
                            )
                          }
                          type="button"
                        >
                          Import provider progress
                        </button>
                      </li>
                    ))}
                  </ol>
                </section>
              ) : null}

              {document.plan.failures.length > 0 ? (
                <section className="tracking-failures">
                  <h3>Previous failures</h3>
                  <ol>
                    {document.plan.failures.map((failure) => (
                      <li key={failure.animeId.provider + "-" + String(failure.animeId.value)}>
                        <strong>{formatAnimeId(failure.animeId)}</strong>
                        <span>{failure.message} · attempt {failure.attemptCount}</span>
                      </li>
                    ))}
                  </ol>
                </section>
              ) : null}

              <div className="tracking-sync-gate">
                <label>
                  <input
                    checked={previewReviewed}
                    disabled={isBusy || document.plan.updates.length === 0}
                    onChange={(event) => setPreviewReviewed(event.target.checked)}
                    type="checkbox"
                  />
                  I reviewed the preview and authorize these provider writes.
                </label>
                <button
                  className="tracking-sync-button"
                  disabled={isBusy || !previewReviewed || document.plan.updates.length === 0}
                  onClick={() => void syncPreviewedUpdates()}
                  type="button"
                >
                  Sync previewed updates
                </button>
              </div>
            </>
          ) : null}
        </div>
      </details>
    </section>
  );
}

function SummaryCard({ label, value }: { label: string; value: number }) {
  return (
    <div>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function operationMessage(label: string, response: ExternalTrackingOperationResponse): string {
  const pieces = [
    String(response.successCount) + " succeeded",
    String(response.conflictCount) + " conflicts",
    String(response.missingCount) + " missing",
    String(response.errors.length) + " failed"
  ];
  return label + " complete: " + pieces.join(", ") + ".";
}

function parsePositiveId(value: string): number | null {
  const parsed = Number(value.trim());
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}

function describeError(error: unknown, fallback: string): string {
  if (error instanceof DanmakuApiError || error instanceof Error) return error.message;
  return fallback;
}

function formatAnimeId(animeId: ExternalAnimeId): string {
  return providerLabel(animeId.provider) + " #" + String(animeId.value);
}

function groupLabel(localSeriesCount: number): string {
  return localSeriesCount > 1 ? " · " + String(localSeriesCount) + " local series" : "";
}

function providerLabel(provider: ExternalAnimeProvider): string {
  switch (provider) {
    case "MY_ANIME_LIST":
      return "MyAnimeList";
    case "BANGUMI":
      return "Bangumi";
    case "DANDANPLAY":
      return "dandanplay";
  }
}

function statusLabel(status?: string | null): string {
  return status
    ? status.toLocaleLowerCase().replaceAll("_", " ")
    : "unchanged";
}
