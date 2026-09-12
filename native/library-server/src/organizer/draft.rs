//! Persisted review decisions. Executable manifests are deliberately not persisted.
use super::*;
use crate::catalog_metadata::CatalogMetadataStore;
use crate::dandanplay::{DandanplayMediaFingerprint, DandanplayResolver};
use std::sync::Arc;

#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct IdentificationCandidate {
    pub anime_id: u64,
    pub episode_id: Option<u64>,
    pub series_title: String,
    pub episode_title: String,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DraftFile {
    pub source_signature: String,
    pub media_id: String,
    pub source_root: String,
    pub source_relative_path: String,
    pub size_bytes: u64,
    pub group_id: String,
    pub series_title: String,
    pub season_number: Option<u32>,
    pub season_evidence: String,
    pub excluded: bool,
    pub manual_assignment: bool,
    pub provider_title: Option<String>,
    pub candidates: Vec<IdentificationCandidate>,
    pub candidate: Option<IdentificationCandidate>,
    pub identification_error: Option<String>,
    pub fingerprint: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct OrganizationDraft {
    #[serde(default)]
    pub identification_pause: Option<String>,
    #[serde(default)]
    pub retry_not_before_epoch_ms: Option<u64>,
    pub id: String,
    pub revision: u64,
    pub destination: String,
    pub files: Vec<DraftFile>,
    pub companion_choices: BTreeMap<String, bool>,
    #[serde(default)]
    pub companion_owners: BTreeMap<String, String>,
    pub active_group: Option<String>,
    pub skipped: BTreeSet<String>,
    pub completed: BTreeMap<String, String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateDraftRequest {
    pub media_ids: Vec<String>,
    pub destination: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DraftPreviewRequest {
    #[serde(default)]
    pub group_id: Option<String>,
    pub draft_id: String,
    pub revision: u64,
}

#[derive(Debug, Clone, Default, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct IdentificationStatus {
    pub running: bool,
    pub completed: usize,
    pub total: usize,
    pub media_id: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct IdentifyRequest {
    pub draft_id: String,
    pub revision: u64,
    #[serde(default)]
    pub media_ids: Vec<String>,
    #[serde(default)]
    pub query: Option<String>,
}

impl LibraryOrganizer {
    fn draft_file(&self) -> PathBuf {
        self.journal_file
            .with_file_name("library-organization-draft.json")
    }

    pub fn draft(&self) -> Result<Option<OrganizationDraft>> {
        // Reloading is unnecessary: this process owns the data-directory lock.
        let mut runtime = self.runtime.lock().expect("organizer lock");
        if !runtime.draft_loaded {
            runtime.draft = if self.draft_file().exists() {
                Some(serde_json::from_slice(&fs::read(self.draft_file())?)?)
            } else {
                None
            };
            runtime.draft_loaded = true;
            let completed = runtime.journal.completed.clone();
            if let Some(draft) = runtime.draft.as_mut() {
                let before = draft.completed.clone();
                for batch in &completed {
                    if draft
                        .files
                        .iter()
                        .any(|f| f.group_id == batch.batch.batch_id)
                    {
                        draft.completed.insert(
                            batch.batch.batch_id.clone(),
                            batch.completed_batch_id.clone(),
                        );
                    }
                }
                if let Some(stored) = self.catalog_store.load()? {
                    let undone = draft
                        .completed
                        .keys()
                        .filter(|group| {
                            !completed.iter().any(|b| &b.batch.batch_id == *group)
                                && draft
                                    .files
                                    .iter()
                                    .filter(|f| &f.group_id == *group && !f.excluded)
                                    .all(|f| {
                                        stored
                                            .published_library
                                            .files_by_id
                                            .get(&f.media_id)
                                            .is_some_and(|p| {
                                                paths_equal(
                                                    p,
                                                    &Path::new(&f.source_root).join(
                                                        path_from_wire(&f.source_relative_path),
                                                    ),
                                                )
                                            })
                                    })
                        })
                        .cloned()
                        .collect::<Vec<_>>();
                    for group in undone {
                        for file in draft.files.iter_mut().filter(|f| f.group_id == group) {
                            if let Some(path) =
                                stored.published_library.files_by_id.get(&file.media_id)
                            {
                                file.source_signature = super::transfer::signature(path)?;
                            }
                        }
                        draft.completed.remove(&group);
                    }
                }
                if before != draft.completed {
                    draft.active_group = draft
                        .files
                        .iter()
                        .find(|f| {
                            !f.excluded
                                && !draft.completed.contains_key(&f.group_id)
                                && !draft.skipped.contains(&f.group_id)
                        })
                        .map(|f| f.group_id.clone());
                    draft.revision += 1;
                    write_json_atomically(&self.draft_file(), draft)?;
                }
            }
        }
        Ok(runtime.draft.clone())
    }

    pub fn create_draft(
        &self,
        library: &PublishedLibrary,
        request: CreateDraftRequest,
    ) -> Result<OrganizationDraft> {
        self.ensure_available()?;
        if self.draft()?.is_some() {
            return Err(LibraryServerError::new(
                "Resume or discard the existing review first.",
            ));
        }
        let ids = request.media_ids.into_iter().collect::<BTreeSet<_>>();
        if ids.is_empty() {
            return Err(LibraryServerError::new("Select at least one video."));
        }
        let mut files = Vec::new();
        for id in ids {
            let item = library
                .catalog
                .items
                .iter()
                .find(|item| item.id == id)
                .ok_or_else(|| {
                    LibraryServerError::new("A selected video is no longer in the catalog.")
                })?;
            let source = library
                .files_by_id
                .get(&id)
                .ok_or_else(|| LibraryServerError::new("Missing media path."))?;
            let root = self.containing_root(source)?;
            reject_reparse_ancestors(&root, source)?;
            let (season_number, season_evidence) =
                season_assignment(&item.relative_path, &item.episode_title);
            let title = item
                .anime_metadata
                .as_ref()
                .map(|m| m.display_title.clone())
                .unwrap_or_else(|| item.series_title.clone());
            let key = item
                .anime_metadata
                .as_ref()
                .map(|m| format!("{:?}:{}", m.anime_id.provider, m.anime_id.value))
                .unwrap_or_else(|| normalize_key(&title));
            files.push(DraftFile {
                source_signature: super::transfer::signature(source)?,
                media_id: id,
                source_root: root.to_string_lossy().into_owned(),
                source_relative_path: relative_wire_path(&root, source)?,
                size_bytes: item.size_bytes,
                group_id: item
                    .anime_metadata
                    .as_ref()
                    .filter(|m| {
                        matches!(
                            m.anime_id.provider,
                            crate::catalog::ExternalAnimeProvider::Dandanplay
                        )
                    })
                    .map(|m| format!("dandanplay-{}", m.anime_id.value))
                    .unwrap_or_else(|| sha256_hex(&key)[..16].to_owned()),
                series_title: title,
                season_number,
                season_evidence,
                excluded: false,
                manual_assignment: false,
                provider_title: item
                    .anime_metadata
                    .as_ref()
                    .map(|m| m.display_title.clone()),
                candidates: Vec::new(),
                candidate: None,
                identification_error: None,
                fingerprint: None,
            });
        }
        let draft = OrganizationDraft {
            identification_pause: None,
            retry_not_before_epoch_ms: None,
            id: unique_id()?,
            revision: 1,
            destination: request.destination,
            active_group: files.first().map(|f| f.group_id.clone()),
            files,
            companion_choices: BTreeMap::new(),
            companion_owners: BTreeMap::new(),
            skipped: BTreeSet::new(),
            completed: BTreeMap::new(),
        };
        let mut runtime = self.runtime.lock().expect("organizer lock");
        if runtime.draft.is_some() {
            return Err(LibraryServerError::new(
                "Resume or discard the existing review first.",
            ));
        }
        write_json_atomically(&self.draft_file(), &draft)?;
        runtime.draft = Some(draft.clone());
        runtime.draft_loaded = true;
        Ok(draft)
    }

    pub fn update_draft(&self, mut draft: OrganizationDraft) -> Result<OrganizationDraft> {
        self.ensure_available()?;
        let _ = self.draft()?;
        let mut runtime = self.runtime.lock().expect("organizer lock");
        let old = runtime
            .draft
            .as_ref()
            .ok_or_else(|| LibraryServerError::new("No saved review."))?;
        require_revision(old, &draft.id, draft.revision)?;
        if old.files.len() != draft.files.len() {
            return Err(LibraryServerError::new(
                "Create a new review to change the selection snapshot.",
            ));
        }
        let mut seen = BTreeSet::new();
        for file in &draft.files {
            let original = old
                .files
                .iter()
                .find(|f| f.media_id == file.media_id)
                .ok_or_else(|| LibraryServerError::new("Unknown selected video."))?;
            if !seen.insert(&file.media_id)
                || original.source_root != file.source_root
                || original.source_relative_path != file.source_relative_path
                || original.size_bytes != file.size_bytes
                || original.source_signature != file.source_signature
            {
                return Err(LibraryServerError::new("Source snapshot cannot be edited."));
            }
            if file.group_id.is_empty() {
                return Err(LibraryServerError::new("Choose a review group."));
            }
            if let Some(candidate) = &file.candidate {
                if !original.candidates.contains(candidate)
                    && original.candidate.as_ref() != Some(candidate)
                {
                    return Err(LibraryServerError::new(
                        "Select an identification candidate returned by the server.",
                    ));
                }
            }
        }
        for file in &mut draft.files {
            let original = old
                .files
                .iter()
                .find(|f| f.media_id == file.media_id)
                .expect("validated media ID");
            if old.completed.contains_key(&original.group_id) {
                *file = original.clone();
                continue;
            }
            file.candidates = original.candidates.clone();
            file.provider_title = original.provider_title.clone();
            file.fingerprint = original.fingerprint.clone();
            file.identification_error = original.identification_error.clone();
            if let Some(candidate) = file.candidate.clone() {
                apply_candidate_season(file, &candidate);
            }
        }
        draft.identification_pause = old.identification_pause.clone();
        draft.retry_not_before_epoch_ms = old.retry_not_before_epoch_ms;
        draft.completed = old.completed.clone();
        draft.revision += 1;
        write_json_atomically(&self.draft_file(), &draft)?;
        runtime.draft = Some(draft.clone());
        runtime.plans.clear();
        self.identification_cancel.store(true, Ordering::Release);
        Ok(draft)
    }

    pub fn discard_draft(&self) -> Result<()> {
        self.ensure_available()?;
        let mut runtime = self.runtime.lock().expect("organizer lock");
        if self.draft_file().exists() {
            fs::remove_file(self.draft_file())?;
        }
        runtime.draft = None;
        runtime.draft_loaded = true;
        runtime.plans.clear();
        self.identification_cancel.store(true, Ordering::Release);
        Ok(())
    }

    pub(super) fn containing_root(&self, path: &Path) -> Result<PathBuf> {
        let path = normalize_absolute(path)?;
        self.roots
            .iter()
            .filter_map(|r| normalize_absolute(r).ok())
            .filter(|r| path.starts_with(r))
            .max_by_key(|r| r.components().count())
            .ok_or_else(|| {
                LibraryServerError::new("Choose a path inside a configured library root.")
            })
    }

    pub fn preview_draft(
        &self,
        library: &PublishedLibrary,
        request: DraftPreviewRequest,
    ) -> Result<OrganizationPlan> {
        self.ensure_available()?;
        let draft = self
            .draft()?
            .ok_or_else(|| LibraryServerError::new("No saved review."))?;
        require_revision(&draft, &request.draft_id, request.revision)?;
        if !Path::new(&draft.destination).is_absolute() {
            return Err(LibraryServerError::new(
                "Choose an absolute destination path inside a configured root.",
            ));
        }
        let destination = normalize_absolute(Path::new(&draft.destination))?;
        let destination_root = self.containing_root(&destination)?;
        reject_reparse_ancestors(&destination_root, &destination)?;
        let mut plan = build_draft_plan(
            library,
            &draft,
            &destination_root,
            &destination,
            request.group_id.as_deref(),
        )?;
        plan.catalog_revision = catalog_revision(&library.catalog);
        plan.plan_id = sha256_hex(&format!(
            "{}:{}:{}:{}",
            draft.id,
            draft.revision,
            plan.catalog_revision,
            serde_json::to_string(&plan.batches)?
        ));
        let stored = StoredPlan {
            catalog_revision: plan.catalog_revision.clone(),
            root: destination_root.clone(),
            draft_revision: Some((draft.id.clone(), draft.revision)),
            batches: plan
                .batches
                .iter()
                .filter(|b| b.executable)
                .map(|b| {
                    (
                        b.batch_id.clone(),
                        StoredBatch {
                            batch_id: b.batch_id.clone(),
                            series_title: b.series_title.clone(),
                            root: destination_root.clone(),
                            moves: b.moves.clone(),
                        },
                    )
                })
                .collect(),
        };
        let mut runtime = self.runtime.lock().expect("organizer lock");
        require_revision(
            runtime
                .draft
                .as_ref()
                .ok_or_else(|| LibraryServerError::new("Review discarded."))?,
            &draft.id,
            draft.revision,
        )?;
        runtime.plans.clear();
        runtime.plans.insert(plan.plan_id.clone(), stored);
        Ok(plan)
    }

    pub fn identification_status(&self) -> IdentificationStatus {
        self.runtime
            .lock()
            .expect("organizer lock")
            .identification
            .clone()
    }

    pub fn cancel_identification(&self) {
        self.identification_cancel.store(true, Ordering::Release);
    }

    pub fn begin_identification(&self, request: &IdentifyRequest) -> Result<OrganizationDraft> {
        self.ensure_available()?;
        let mut draft = self
            .draft()?
            .ok_or_else(|| LibraryServerError::new("No saved review."))?;
        require_revision(&draft, &request.draft_id, request.revision)?;
        if draft
            .retry_not_before_epoch_ms
            .is_some_and(|until| until > current_epoch_ms())
        {
            return Err(LibraryServerError::new(
                "Identification is paused. Wait for the retry cooldown before continuing.",
            ));
        }
        draft.identification_pause = None;
        draft.retry_not_before_epoch_ms = None;
        let mut runtime = self.runtime.lock().expect("organizer lock");
        if runtime.identification.running {
            return Err(LibraryServerError::new(
                "Identification is already running.",
            ));
        }
        runtime.identification = IdentificationStatus {
            running: true,
            total: draft
                .files
                .iter()
                .filter(|file| {
                    identification_requested(file, &request)
                        && !draft.completed.contains_key(&file.group_id)
                })
                .count(),
            ..Default::default()
        };
        self.identification_cancel.store(false, Ordering::Release);
        Ok(draft)
    }

    pub async fn identify(
        self: Arc<Self>,
        mut draft: OrganizationDraft,
        request: IdentifyRequest,
        resolver: Option<Arc<DandanplayResolver>>,
    ) {
        let mut consecutive_failures = 0;
        for file in &mut draft.files {
            if self.identification_cancel.load(Ordering::Acquire) {
                break;
            }
            let explicit = request.media_ids.contains(&file.media_id);
            if !identification_requested(file, &request)
                || draft.completed.contains_key(&file.group_id)
            {
                continue;
            }
            self.runtime
                .lock()
                .expect("organizer lock")
                .identification
                .media_id = Some(file.media_id.clone());
            let source =
                Path::new(&file.source_root).join(path_from_wire(&file.source_relative_path));
            let result: Result<Option<(String, Vec<IdentificationCandidate>)>> = async {
                let resolver = resolver.as_ref().ok_or_else(|| {
                    LibraryServerError::new(
                        "Configure dandanplay to identify videos, or assign a series manually.",
                    )
                })?;
                let fingerprint = tokio::task::spawn_blocking(move || {
                    DandanplayMediaFingerprint::from_path(&source)
                })
                .await
                .map_err(|e| LibraryServerError::new(e.to_string()))??;
                let key = format!(
                    "{}:{}",
                    fingerprint.normalized_file_hash(),
                    fingerprint.file_size_bytes
                );
                if !explicit && file.fingerprint.as_ref() == Some(&key) {
                    return Ok(None);
                }
                let candidates = resolver
                    .identify_only(
                        &fingerprint,
                        request.query.as_deref().unwrap_or(&file.series_title),
                        request.query.is_some(),
                    )
                    .await?;
                Ok(Some((key, candidates)))
            }
            .await;
            self.runtime
                .lock()
                .expect("organizer lock")
                .identification
                .completed += 1;
            match result {
                Ok(Some((key, candidates))) => {
                    consecutive_failures = 0;
                    file.fingerprint = Some(key);
                    file.identification_error = None;
                    if candidates.len() == 1 {
                        let candidate = candidates[0].clone();
                        file.series_title = candidate.series_title.clone();
                        file.group_id = format!("dandanplay-{}", candidate.anime_id);
                        apply_candidate_season(file, &candidate);
                        file.candidate = Some(candidate);
                    } else {
                        file.candidate = None;
                    }
                    file.candidates = candidates;
                }
                Ok(None) => {}
                Err(error) => {
                    consecutive_failures += 1;
                    file.identification_error = Some(error.to_string());
                    if error.provider_retry_after_seconds.is_some() || consecutive_failures >= 3 {
                        draft.identification_pause = Some(error.to_string());
                        draft.retry_not_before_epoch_ms = Some(
                            current_epoch_ms().saturating_add(
                                error
                                    .provider_retry_after_seconds
                                    .unwrap_or(60)
                                    .saturating_mul(1000),
                            ),
                        );
                        break;
                    }
                }
            }

            // Pace batches, checking cancellation between short waits. Never retry automatically.
            for _ in 0..5 {
                if self.identification_cancel.load(Ordering::Acquire) {
                    break;
                }
                tokio::time::sleep(std::time::Duration::from_millis(50)).await;
            }
        }
        let mut runtime = self.runtime.lock().expect("organizer lock");
        if runtime
            .draft
            .as_ref()
            .is_some_and(|current| current.id == draft.id && current.revision == draft.revision)
        {
            draft.revision += 1;
            if !draft
                .files
                .iter()
                .any(|f| Some(&f.group_id) == draft.active_group.as_ref())
            {
                draft.active_group = draft
                    .files
                    .iter()
                    .find(|f| !f.excluded)
                    .map(|f| f.group_id.clone());
            }
            match write_json_atomically(&self.draft_file(), &draft) {
                Ok(()) => {
                    runtime.draft = Some(draft);
                    runtime.plans.clear();
                }
                Err(error) => {
                    runtime.status.message = Some(error.to_string());
                }
            }
        }
        runtime.identification.running = false;
    }

    pub fn save_identification(
        &self,
        request: DraftPreviewRequest,
        store: &CatalogMetadataStore,
    ) -> Result<()> {
        let draft = self
            .draft()?
            .ok_or_else(|| LibraryServerError::new("No saved review."))?;
        require_revision(&draft, &request.draft_id, request.revision)?;
        for file in draft.files.iter().filter(|f| !f.excluded) {
            if let Some(candidate) = &file.candidate {
                store.record_with_episode(
                    &file.media_id,
                    candidate.anime_id,
                    candidate.series_title.clone(),
                    (!candidate.episode_title.is_empty()).then(|| candidate.episode_title.clone()),
                    candidate.episode_id,
                )?;
            }
        }
        Ok(())
    }
}

fn require_revision(draft: &OrganizationDraft, id: &str, revision: u64) -> Result<()> {
    if draft.id != id || draft.revision != revision {
        return Err(LibraryServerError::new(
            "The review changed. Reload it before continuing.",
        ));
    }
    Ok(())
}

pub(super) fn unique_id() -> Result<String> {
    let mut bytes = [0u8; 16];
    getrandom::fill(&mut bytes).map_err(|e| LibraryServerError::new(e.to_string()))?;
    Ok(bytes.iter().map(|b| format!("{b:02x}")).collect())
}

fn season_assignment(path: &str, episode: &str) -> (Option<u32>, String) {
    let seasons = path
        .split(['/', '\\'])
        .chain(std::iter::once(episode))
        .flat_map(season_evidence)
        .collect::<BTreeSet<_>>();
    match seasons.len() {
        0 => (Some(1), "SUGGESTED".into()),
        1 => (seasons.first().copied(), "PARSED".into()),
        _ => (None, "CONFLICT".into()),
    }
}

fn season_evidence(value: &str) -> BTreeSet<u32> {
    if matches!(
        value.trim().to_ascii_lowercase().as_str(),
        "special" | "specials"
    ) {
        return BTreeSet::from([0]);
    }
    let mut normalized = String::new();
    let mut previous_digit = false;
    for ch in value.chars() {
        // The shared parser expects a word boundary after Sxx, including SxxExx releases.
        if previous_digit && matches!(ch, 'e' | 'E') {
            normalized.push(' ');
        }
        normalized.push(ch);
        previous_digit = ch.is_ascii_digit();
    }
    normalized
        .char_indices()
        .filter(|(index, _)| {
            *index == 0
                || normalized[..*index]
                    .chars()
                    .next_back()
                    .is_some_and(|ch| !ch.is_alphanumeric())
        })
        .filter_map(|(index, _)| find_season_number(&normalized[index..]))
        .collect()
}

fn apply_candidate_season(file: &mut DraftFile, candidate: &IdentificationCandidate) {
    if file.season_evidence == "MANUAL" {
        return;
    }
    if let Some(season) = find_season_number(&candidate.series_title) {
        if file.season_evidence == "SUGGESTED" {
            file.season_number = Some(season);
            file.season_evidence = "PROVIDER_TITLE".into();
        } else if file.season_number != Some(season) {
            file.season_number = None;
            file.season_evidence = "CONFLICT".into();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn batch_retry_keeps_ambiguous_candidates_until_explicitly_selected() {
        let mut file: DraftFile = serde_json::from_value(serde_json::json!({
            "sourceSignature":"test", "mediaId":"one", "sourceRoot":"root", "sourceRelativePath":"one.mkv",
            "sizeBytes":1,"groupId":"group","seriesTitle":"Show","seasonNumber":1,"seasonEvidence":"SUGGESTED",
            "excluded":false,"manualAssignment":false,"providerTitle":null,"candidate":null,
            "candidates":[{"animeId":1,"seriesTitle":"First","episodeId":null,"episodeTitle":""},{"animeId":2,"seriesTitle":"Second","episodeId":null,"episodeTitle":""}],
            "identificationError":null,"fingerprint":"cached"
        })).unwrap();
        let mut request = IdentifyRequest {
            draft_id: "draft".into(),
            revision: 1,
            media_ids: Vec::new(),
            query: None,
        };
        assert!(!identification_requested(&file, &request));
        request.media_ids.push("one".into());
        assert!(identification_requested(&file, &request));
        file.excluded = true;
        assert!(!identification_requested(&file, &request));
    }
    #[test]
    fn season_suggestions_do_not_hide_conflicts_or_specials() {
        assert_eq!(
            season_assignment("Show/episode.mkv", "episode"),
            (Some(1), "SUGGESTED".into())
        );
        assert_eq!(
            season_assignment("Show/Specials/episode.mkv", "episode"),
            (Some(0), "PARSED".into())
        );
        assert_eq!(
            season_assignment("Show/Season 0/episode.mkv", "episode"),
            (Some(0), "PARSED".into())
        );
        assert_eq!(
            season_assignment("Show/Season 2/S03E01.mkv", "episode"),
            (None, "CONFLICT".into())
        );
    }
    #[test]
    fn subtitle_matching_requires_a_filename_boundary() {
        assert!(subtitle_matches(
            "Show 01.mkv",
            Path::new("Show 01.zh-TW.ass")
        ));
        assert!(!subtitle_matches("Show 01.mkv", Path::new("Show 010.ass")));
        assert!(!subtitle_matches("Show 01.mkv", Path::new("Show 01.png")));
    }
}

fn build_draft_plan(
    library: &PublishedLibrary,
    draft: &OrganizationDraft,
    destination_root: &Path,
    destination: &Path,
    focus: Option<&str>,
) -> Result<OrganizationPlan> {
    let mut batches = BTreeMap::<String, OrganizationSeriesBatch>::new();
    let mut parents = BTreeMap::<PathBuf, Vec<&DraftFile>>::new();
    let mut nearby_owners = BTreeMap::<PathBuf, Vec<&DraftFile>>::new();
    for file in draft.files.iter().filter(|f| {
        !f.excluded
            && !draft.completed.contains_key(&f.group_id)
            && focus.is_none_or(|group| group == f.group_id)
    }) {
        let batch =
            batches
                .entry(file.group_id.clone())
                .or_insert_with(|| OrganizationSeriesBatch {
                    batch_id: file.group_id.clone(),
                    series_title: file.series_title.clone(),
                    season_number: file.season_number,
                    confidence: if file.provider_title.is_some() || file.candidate.is_some() {
                        OrganizationConfidence::Provider
                    } else {
                        OrganizationConfidence::Parsed
                    },
                    reason: String::new(),
                    video_count: 0,
                    executable: false,
                    already_organized: false,
                    conflicts: Vec::new(),
                    moves: Vec::new(),
                    nearby_files: Vec::new(),
                });
        batch.video_count += 1;
        if batch.series_title != file.series_title {
            batch
                .conflicts
                .push("Files in a review group must use the same series folder title.".into());
        }
        let Some(item) = library.catalog.items.iter().find(|i| i.id == file.media_id) else {
            batch.conflicts.push(format!(
                "Missing selected video: {}",
                file.source_relative_path
            ));
            continue;
        };
        let Some(source) = library.files_by_id.get(&file.media_id) else {
            batch
                .conflicts
                .push(format!("Missing media path: {}", file.source_relative_path));
            continue;
        };
        let expected =
            Path::new(&file.source_root).join(path_from_wire(&file.source_relative_path));
        if !paths_equal(source, &expected) || item.size_bytes != file.size_bytes {
            batch.conflicts.push(format!(
                "Selected video changed: {}",
                file.source_relative_path
            ));
            continue;
        }
        if super::transfer::signature(source).ok().as_ref() != Some(&file.source_signature) {
            batch.conflicts.push(format!(
                "Selected source is missing or changed: {}",
                file.source_relative_path
            ));
            continue;
        }
        if !file.manual_assignment && file.provider_title.is_none() && file.candidate.is_none() {
            batch.conflicts.push(format!(
                "Choose a match or confirm a manual assignment: {}",
                file.source_relative_path
            ));
        }
        let title = match sanitize_component(&file.series_title) {
            Ok(t) => t,
            Err(e) => {
                batch.conflicts.push(e.to_string());
                continue;
            }
        };
        let Some(season) = file.season_number else {
            batch
                .conflicts
                .push(format!("Choose a season: {}", file.source_relative_path));
            continue;
        };
        let target = destination
            .join(&title)
            .join(format!("Season {season}"))
            .join(source.file_name().unwrap_or_default());
        if !paths_equal(source, &target) {
            batch.moves.push(OrganizationMove {
                source_signature: Some(file.source_signature.clone()),
                content_hash: None,
                source_root: Some(PathBuf::from(&file.source_root)),
                destination_root: Some(destination_root.to_owned()),
                media_id: Some(file.media_id.clone()),
                subtitle_id: None,
                source_relative_path: file.source_relative_path.clone(),
                destination_relative_path: relative_wire_path(destination_root, &target)?,
                size_bytes: file.size_bytes,
                kind: OrganizationMoveKind::Video,
                original_series_title: Some(item.series_title.clone()),
                destination_series_title: Some(file.series_title.clone()),
            });
        }
        if let Some(parent) = source.parent() {
            parents.entry(parent.to_owned()).or_default().push(file);
        }
    }
    for (parent, files) in parents {
        if let Ok(entries) = fs::read_dir(&parent) {
            for entry in entries.flatten() {
                let path = entry.path();
                if path.is_file()
                    && !VIDEO_EXTENSIONS.contains(&extension_lowercase(&path).as_str())
                {
                    nearby_owners
                        .entry(path)
                        .or_default()
                        .extend(files.iter().copied());
                }
            }
        }
        if find_season_number(&parent.file_name().unwrap_or_default().to_string_lossy()).is_some() {
            if let Some(series_parent) = parent.parent() {
                if let Ok(entries) = fs::read_dir(series_parent) {
                    for entry in entries.flatten() {
                        let path = entry.path();
                        if path.is_file()
                            && is_series_asset(&entry.file_name().to_string_lossy())
                            && files.iter().all(|f| path.starts_with(&f.source_root))
                        {
                            nearby_owners
                                .entry(path)
                                .or_default()
                                .extend(files.iter().copied());
                        }
                    }
                }
            }
        }
    }
    for (path, owners) in nearby_owners {
        let mut owners = owners;
        owners.sort_by(|a, b| a.media_id.cmp(&b.media_id));
        owners.dedup_by_key(|f| &f.media_id);
        let matching = owners
            .iter()
            .copied()
            .filter(|f| subtitle_matches(&f.source_relative_path, &path))
            .collect::<Vec<_>>();
        let matching_all = library
            .files_by_id
            .iter()
            .filter(|(_, video)| {
                video.parent() == path.parent() && subtitle_matches(&video.to_string_lossy(), &path)
            })
            .map(|(id, _)| id)
            .collect::<Vec<_>>();
        // Excluded or unselected videos retain their own subtitles, even after a previous preview selected them.
        if !matching_all.is_empty() && matching.is_empty() {
            continue;
        }
        let key = path.to_string_lossy().into_owned();
        if draft
            .companion_owners
            .get(&key)
            .is_some_and(|id| !owners.iter().any(|f| &f.media_id == id))
        {
            continue;
        }
        let recommended = matching.len() == 1 && matching_all.len() == 1;
        let explicit_owner = draft
            .companion_owners
            .get(&key)
            .and_then(|id| owners.iter().find(|f| &f.media_id == id).copied());
        let owner =
            explicit_owner.unwrap_or_else(|| if recommended { matching[0] } else { owners[0] });
        let selected = draft
            .companion_choices
            .get(&key)
            .copied()
            .unwrap_or(recommended);
        let batch = batches.get_mut(&owner.group_id).expect("owner group");
        let root = Path::new(&owner.source_root);
        let title = match sanitize_component(&owner.series_title) {
            Ok(t) => t,
            Err(_) => continue,
        };
        let target = if is_series_asset(&path.file_name().unwrap_or_default().to_string_lossy()) {
            destination.join(title)
        } else {
            destination
                .join(title)
                .join(format!("Season {}", owner.season_number.unwrap_or(1)))
        }
        .join(path.file_name().unwrap_or_default());
        if paths_equal(&path, &target) {
            continue;
        }
        let size = fs::metadata(&path)?.len();
        batch.nearby_files.push(OrganizationNearbyFile {
            owner_media_ids: owners.iter().map(|f| f.media_id.clone()).collect(),
            owner_media_id: if recommended || explicit_owner.is_some() {
                Some(owner.media_id.clone())
            } else {
                None
            },
            relative_path: key,
            size_bytes: size,
            recommended,
            selected,
            destination_relative_path: Some(target.to_string_lossy().into_owned()),
        });
        if selected && !recommended && explicit_owner.is_none() {
            batch.conflicts.push(format!(
                "Assign an owner for the companion file: {}",
                path.display()
            ));
            continue;
        }
        if selected {
            let subtitle_id = library
                .subtitle_files_by_id
                .iter()
                .find(|(_, p)| paths_equal(p, &path))
                .map(|(id, _)| id.clone());
            batch.moves.push(OrganizationMove {
                source_signature: Some(super::transfer::signature(&path)?),
                content_hash: None,
                source_root: Some(root.to_owned()),
                destination_root: Some(destination_root.to_owned()),
                media_id: None,
                subtitle_id,
                source_relative_path: relative_wire_path(root, &path)?,
                destination_relative_path: relative_wire_path(destination_root, &target)?,
                size_bytes: size,
                kind: OrganizationMoveKind::Nearby,
                original_series_title: None,
                destination_series_title: None,
            });
        }
    }
    let mut other_destinations = BTreeSet::new();
    if let Some(focus) = focus {
        for file in draft.files.iter().filter(|f| {
            !f.excluded && f.group_id != focus && !draft.completed.contains_key(&f.group_id)
        }) {
            if let (Ok(title), Some(season)) =
                (sanitize_component(&file.series_title), file.season_number)
            {
                let filename = path_from_wire(&file.source_relative_path);
                other_destinations.insert(
                    destination
                        .join(title)
                        .join(format!("Season {season}"))
                        .join(filename.file_name().unwrap_or_default())
                        .to_string_lossy()
                        .to_lowercase(),
                );
            }
        }
    }
    let mut destinations = BTreeMap::<String, Vec<String>>::new();
    for batch in batches.values_mut() {
        for operation in &batch.moves {
            let target = operation.destination(destination_root);
            if other_destinations.contains(&target.to_string_lossy().to_lowercase()) {
                batch.conflicts.push(format!(
                    "Another selected group targets this destination: {}",
                    target.display()
                ));
            }
            if target.exists() {
                batch
                    .conflicts
                    .push(format!("Destination already exists: {}", target.display()));
            }
            destinations
                .entry(target.to_string_lossy().to_lowercase())
                .or_default()
                .push(batch.batch_id.clone());
        }
        batch.already_organized = batch.moves.is_empty() && batch.conflicts.is_empty();
    }
    for (path, groups) in destinations
        .into_iter()
        .filter(|(_, groups)| groups.len() > 1)
    {
        for group in groups {
            batches
                .get_mut(&group)
                .unwrap()
                .conflicts
                .push(format!("Two files target the same destination: {path}"));
        }
    }
    for batch in batches.values_mut() {
        batch.executable = !batch.moves.is_empty()
            && batch.conflicts.is_empty()
            && !draft.skipped.contains(&batch.batch_id);
    }
    Ok(OrganizationPlan {
        draft_id: draft.id.clone(),
        draft_revision: draft.revision,
        plan_id: String::new(),
        catalog_revision: String::new(),
        root: destination_root.to_string_lossy().into_owned(),
        base_relative_path: relative_wire_path(destination_root, destination)?,
        batches: batches.into_values().collect(),
        unassigned_count: 0,
    })
}

fn subtitle_matches(video: &str, subtitle: &Path) -> bool {
    if !SUBTITLE_EXTENSIONS.contains(&extension_lowercase(subtitle).as_str()) {
        return false;
    }
    let video = path_from_wire(video);
    let stem = video
        .file_stem()
        .unwrap_or_default()
        .to_string_lossy()
        .to_lowercase();
    let subtitle = subtitle
        .file_stem()
        .unwrap_or_default()
        .to_string_lossy()
        .to_lowercase();
    subtitle == stem
        || subtitle
            .strip_prefix(&stem)
            .is_some_and(|rest| rest.starts_with(['.', '_', '-']))
}

fn identification_requested(file: &DraftFile, request: &IdentifyRequest) -> bool {
    let selected = request.media_ids.contains(&file.media_id);
    !file.excluded
        && (request.media_ids.is_empty() || selected)
        && (selected
            || (file.provider_title.is_none()
                && !file.manual_assignment
                && file.candidate.is_none()
                && file.candidates.is_empty()))
}
