//! Background provider identification. Each result is merged into the current
//! draft as soon as it arrives, so user edits never discard or cancel it.
use std::sync::Arc;
use std::time::{Duration, Instant};

use super::draft::{IdentificationCandidate, OrganizationDraft, apply_candidate_season};
use super::naming::split_season_suffix;
use super::*;
use crate::dandanplay::{DandanplayMediaFingerprint, DandanplayResolver};

const PERSIST_INTERVAL: Duration = Duration::from_secs(2);
const MAX_CONSECUTIVE_FAILURES: usize = 3;

#[derive(Debug, Clone, Default, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct IdentificationStatus {
    pub running: bool,
    pub completed: usize,
    pub total: usize,
    pub media_id: Option<String>,
    /// Whether a provider is configured; set by the HTTP layer.
    pub available: bool,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct IdentifyRequest {
    pub draft_id: String,
    /// Explicit videos are identified again even when they already have results.
    #[serde(default)]
    pub media_ids: Vec<String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SearchRequest {
    pub draft_id: String,
    pub group_id: String,
    pub query: String,
}

struct RunProgress {
    consecutive_failures: usize,
    last_persist: Instant,
    dirty: bool,
}

#[derive(Debug, Clone)]
pub struct IdentificationRun {
    generation: u64,
    draft_id: String,
    jobs: Vec<(String, bool)>,
}

impl LibraryOrganizer {
    pub(super) fn stop_identification(&self, runtime: &mut OrganizerRuntime) {
        runtime.identification_generation += 1;
        runtime.identification.running = false;
        runtime.identification.media_id = None;
    }

    pub fn cancel_identification(&self) {
        let mut runtime = self.runtime.lock().expect("organizer lock");
        self.stop_identification(&mut runtime);
    }

    pub fn identification_status(&self) -> IdentificationStatus {
        self.runtime
            .lock()
            .expect("organizer lock")
            .identification
            .clone()
    }

    /// Validates and registers a run; the caller spawns [`Self::identify`].
    pub fn begin_identification(&self, request: &IdentifyRequest) -> Result<IdentificationRun> {
        self.ensure_available()?;
        let _ = self.draft()?;
        let mut runtime = self.runtime.lock().expect("organizer lock");
        if runtime.identification.running {
            return Err(LibraryServerError::new(
                "Identification is already running.",
            ));
        }
        let draft = runtime
            .draft
            .as_mut()
            .filter(|d| d.id == request.draft_id)
            .ok_or_else(|| LibraryServerError::new("This review was replaced or discarded."))?;
        if draft
            .retry_not_before_epoch_ms
            .is_some_and(|until| until > current_epoch_ms())
        {
            return Err(LibraryServerError::new(
                "Identification is paused. Wait for the retry cooldown before continuing.",
            ));
        }
        let jobs = draft
            .files
            .iter()
            .filter(|f| !draft.completed.contains_key(&f.group_id))
            .filter(|f| identification_requested(f, &request.media_ids))
            .map(|f| (f.media_id.clone(), request.media_ids.contains(&f.media_id)))
            .collect::<Vec<_>>();
        if jobs.is_empty() {
            return Err(LibraryServerError::new(
                "Every video already has a match. Select videos to identify them again.",
            ));
        }
        if draft.identification_pause.take().is_some()
            | draft.retry_not_before_epoch_ms.take().is_some()
        {
            draft.revision += 1;
            write_json_atomically(&self.draft_file(), &*draft)?;
        }
        let draft_id = draft.id.clone();
        runtime.identification_generation += 1;
        runtime.identification = IdentificationStatus {
            running: true,
            total: jobs.len(),
            ..Default::default()
        };
        Ok(IdentificationRun {
            generation: runtime.identification_generation,
            draft_id,
            jobs,
        })
    }

    fn identification_current(&self, generation: u64) -> bool {
        self.runtime
            .lock()
            .expect("organizer lock")
            .identification_generation
            == generation
    }

    pub async fn identify(
        self: Arc<Self>,
        run: IdentificationRun,
        resolver: Arc<DandanplayResolver>,
    ) {
        let mut progress = RunProgress {
            consecutive_failures: 0,
            last_persist: Instant::now(),
            dirty: false,
        };
        for (media_id, explicit) in &run.jobs {
            if !self.identification_current(run.generation) {
                break;
            }
            // Re-read the file: the user may have matched, excluded or finished it meanwhile.
            let job = {
                let mut runtime = self.runtime.lock().expect("organizer lock");
                runtime.identification.media_id = Some(media_id.clone());
                runtime
                    .draft
                    .as_ref()
                    .filter(|d| d.id == run.draft_id)
                    .and_then(|d| {
                        d.files
                            .iter()
                            .filter(|f| !d.completed.contains_key(&f.group_id))
                            .find(|f| {
                                &f.media_id == media_id
                                    && (*explicit || identification_requested(f, &[]))
                                    && !f.excluded
                            })
                    })
                    .map(|f| {
                        (
                            Path::new(&f.source_root).join(path_from_wire(&f.source_relative_path)),
                            f.series_title.clone(),
                            f.fingerprint.clone(),
                        )
                    })
            };
            let result = match job {
                None => Ok(None),
                Some((source, query, previous)) => {
                    identify_file(&resolver, source, &query, previous, *explicit).await
                }
            };
            let Some(stop) = self.record_result(&run, media_id, result, &mut progress) else {
                break;
            };
            if stop {
                break;
            }
            // Pace requests, checking cancellation between short waits. Never retry automatically.
            for _ in 0..5 {
                if !self.identification_current(run.generation) {
                    break;
                }
                tokio::time::sleep(Duration::from_millis(50)).await;
            }
        }
        let mut runtime = self.runtime.lock().expect("organizer lock");
        if progress.dirty
            && let Some(draft) = runtime.draft.as_ref().filter(|d| d.id == run.draft_id)
            && let Err(error) = write_json_atomically(&self.draft_file(), draft)
        {
            runtime.status.message = Some(error.to_string());
        }
        if runtime.identification_generation == run.generation {
            runtime.identification.running = false;
            runtime.identification.media_id = None;
        }
    }

    /// Merges one result into the current draft. Returns `None` when the run
    /// was superseded, otherwise whether the run must pause.
    fn record_result(
        &self,
        run: &IdentificationRun,
        media_id: &str,
        result: Result<Option<(String, Vec<IdentificationCandidate>)>>,
        progress: &mut RunProgress,
    ) -> Option<bool> {
        let mut runtime = self.runtime.lock().expect("organizer lock");
        if runtime.identification_generation != run.generation {
            return None;
        }
        runtime.identification.completed += 1;
        let draft = runtime.draft.as_mut().filter(|d| d.id == run.draft_id)?;
        let mut stop = false;
        match result {
            Ok(Some((key, candidates))) => {
                progress.consecutive_failures = 0;
                apply_identification(draft, media_id, key, candidates);
                draft.revision += 1;
                progress.dirty = true;
            }
            Ok(None) => progress.consecutive_failures = 0,
            Err(error) => {
                progress.consecutive_failures += 1;
                if let Some(file) = draft.files.iter_mut().find(|f| f.media_id == media_id) {
                    file.identification_error = Some(error.to_string());
                }
                if error.provider_retry_after_seconds.is_some()
                    || progress.consecutive_failures >= MAX_CONSECUTIVE_FAILURES
                {
                    draft.identification_pause = Some(error.to_string());
                    draft.retry_not_before_epoch_ms = Some(
                        current_epoch_ms().saturating_add(
                            error
                                .provider_retry_after_seconds
                                .unwrap_or(60)
                                .saturating_mul(1000),
                        ),
                    );
                    stop = true;
                }
                draft.revision += 1;
                progress.dirty = true;
            }
        }
        if progress.dirty && (stop || progress.last_persist.elapsed() >= PERSIST_INTERVAL) {
            if let Err(error) = write_json_atomically(&self.draft_file(), &*draft) {
                runtime.status.message = Some(error.to_string());
            }
            progress.dirty = false;
            progress.last_persist = Instant::now();
        }
        Some(stop)
    }

    /// Searches the provider once for a review group and stores the results
    /// as the group's selectable series candidates.
    pub async fn search_group(
        &self,
        request: SearchRequest,
        resolver: &DandanplayResolver,
    ) -> Result<OrganizationDraft> {
        let query = request.query.trim();
        if query.is_empty() {
            return Err(LibraryServerError::new("Enter a title to search for."));
        }
        let results = resolver.search_series(query).await?;
        let mut runtime = self.runtime.lock().expect("organizer lock");
        let draft = runtime
            .draft
            .as_mut()
            .filter(|d| d.id == request.draft_id)
            .ok_or_else(|| LibraryServerError::new("This review was replaced or discarded."))?;
        if !draft.files.iter().any(|f| f.group_id == request.group_id) {
            return Err(LibraryServerError::new(
                "This series is no longer in the review.",
            ));
        }
        draft.group_candidates.insert(request.group_id, results);
        draft.revision += 1;
        write_json_atomically(&self.draft_file(), &*draft)?;
        Ok(draft.clone())
    }
}

async fn identify_file(
    resolver: &DandanplayResolver,
    source: PathBuf,
    query: &str,
    previous: Option<String>,
    explicit: bool,
) -> Result<Option<(String, Vec<IdentificationCandidate>)>> {
    let fingerprint =
        tokio::task::spawn_blocking(move || DandanplayMediaFingerprint::from_path(&source))
            .await
            .map_err(|e| LibraryServerError::new(e.to_string()))??;
    let key = format!(
        "{}:{}",
        fingerprint.normalized_file_hash(),
        fingerprint.file_size_bytes
    );
    if !explicit && previous.as_ref() == Some(&key) {
        return Ok(None);
    }
    let candidates = resolver.identify_only(&fingerprint, query, false).await?;
    Ok(Some((key, candidates)))
}

/// Records provider results. A unique result links the file and, unless the
/// user chose its folder title, files it under the provider's series title.
pub(super) fn apply_identification(
    draft: &mut OrganizationDraft,
    media_id: &str,
    key: String,
    candidates: Vec<IdentificationCandidate>,
) {
    let Some(index) = draft
        .files
        .iter()
        .position(|f| f.media_id == media_id && !draft.completed.contains_key(&f.group_id))
    else {
        return;
    };
    let unique = (draft.files[index].candidate.is_none() && candidates.len() == 1)
        .then(|| candidates[0].clone());
    let file = &mut draft.files[index];
    file.fingerprint = Some(key);
    file.identification_error = None;
    file.candidates = candidates;
    let Some(candidate) = unique else {
        return;
    };
    apply_candidate_season(file, &candidate);
    file.series_only |= candidate.episode_id.is_none();
    let retitle = !file.manual_assignment;
    file.candidate = Some(candidate.clone());
    if retitle {
        let title = split_season_suffix(&candidate.series_title).0;
        let group = draft.group_for_title(&title, None);
        let file = &mut draft.files[index];
        file.group_id = group;
        file.series_title = title;
    }
}

fn identification_requested(file: &draft::DraftFile, explicit: &[String]) -> bool {
    let selected = explicit.contains(&file.media_id);
    !file.excluded
        && (explicit.is_empty() || selected)
        && (selected
            || (file.provider_title.is_none()
                && file.candidate.is_none()
                && file.candidates.is_empty()))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn batch_retry_keeps_ambiguous_candidates_until_explicitly_selected() {
        let mut file: draft::DraftFile = serde_json::from_value(serde_json::json!({
            "sourceSignature":"test", "mediaId":"one", "sourceRoot":"root", "sourceRelativePath":"one.mkv",
            "sizeBytes":1,"groupId":"group","seriesTitle":"Show","seasonNumber":1,"seasonEvidence":"SUGGESTED",
            "excluded":false,"manualAssignment":false,"providerTitle":null,"candidate":null,
            "candidates":[{"animeId":1,"seriesTitle":"First","episodeId":null,"episodeTitle":""},{"animeId":2,"seriesTitle":"Second","episodeId":null,"episodeTitle":""}],
            "identificationError":null,"fingerprint":"cached"
        })).unwrap();
        assert!(!identification_requested(&file, &[]));
        assert!(identification_requested(&file, &["one".into()]));
        file.excluded = true;
        assert!(!identification_requested(&file, &["one".into()]));
    }
}
