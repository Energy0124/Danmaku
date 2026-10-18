//! Persisted review decisions. Executable manifests are deliberately not persisted.
//!
//! The draft is edited through small intent operations ([`DraftOp`]) applied to
//! the current server state, so background identification and user edits never
//! conflict and no client-side rebasing is required.
use super::naming::{natural_cmp, series_key, split_season_suffix, title_from_file_stem};
use super::*;
use crate::catalog_metadata::CatalogMetadataStore;

/// `completed` value for a group the user marked done without moving files.
pub const MARKED_DONE: &str = "";

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
    #[serde(default)]
    pub series_only: bool,
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
    /// The user chose this file's folder title; identification never retitles it.
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
    /// Selection snapshot in natural path order.
    pub files: Vec<DraftFile>,
    pub companion_choices: BTreeMap<String, bool>,
    #[serde(default)]
    pub companion_owners: BTreeMap<String, String>,
    #[serde(default)]
    pub companion_series_owners: BTreeMap<String, String>,
    pub skipped: BTreeSet<String>,
    pub completed: BTreeMap<String, String>,
    /// Series search results per review group; the only provider identities
    /// besides fingerprint matches that a group may be linked to.
    #[serde(default)]
    pub group_candidates: BTreeMap<String, Vec<IdentificationCandidate>>,
}

impl OrganizationDraft {
    pub(super) fn editable(&self, file: &DraftFile) -> bool {
        !self.completed.contains_key(&file.group_id)
    }

    fn group_title(&self, group_id: &str) -> Option<&str> {
        self.files
            .iter()
            .find(|f| f.group_id == group_id)
            .map(|f| f.series_title.as_str())
    }

    /// Existing open group whose folder title matches `title`, else a new ID.
    pub(super) fn group_for_title(&self, title: &str, except: Option<&str>) -> String {
        let key = series_key(title);
        if let Some(existing) = self.files.iter().find(|f| {
            self.editable(f)
                && Some(f.group_id.as_str()) != except
                && series_key(&f.series_title) == key
        }) {
            return existing.group_id.clone();
        }
        let base = format!("series:{key}");
        let mut id = base.clone();
        let mut suffix = 2;
        // No open group uses this title, so any group already holding the ID is
        // a different series (renamed or finished) and must not be merged.
        while self.completed.contains_key(&id) || self.files.iter().any(|f| f.group_id == id) {
            id = format!("{base}#{suffix}");
            suffix += 1;
        }
        id
    }

    fn known_candidate(&self, candidate: &IdentificationCandidate) -> bool {
        let series_level = candidate.episode_id.is_none() && candidate.episode_title.is_empty();
        self.files
            .iter()
            .flat_map(|f| f.candidates.iter().chain(f.candidate.iter()))
            .chain(self.group_candidates.values().flatten())
            .any(|known| {
                known == candidate
                    || (series_level
                        && known.anime_id == candidate.anime_id
                        && known.series_title == candidate.series_title)
            })
    }
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateDraftRequest {
    pub media_ids: Vec<String>,
    pub destination: String,
    /// Replace an unfinished review instead of failing.
    #[serde(default)]
    pub replace: bool,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DraftPreviewRequest {
    pub draft_id: String,
    pub group_id: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DraftOpsRequest {
    pub draft_id: String,
    pub ops: Vec<DraftOp>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(tag = "op", rename_all = "camelCase", rename_all_fields = "camelCase")]
pub enum DraftOp {
    SetDestination {
        destination: String,
    },
    /// Rename a series folder; merges into an open group with the same title.
    RenameGroup {
        group_id: String,
        title: String,
    },
    /// Accept the folder title for files without a provider match.
    ConfirmGroup {
        group_id: String,
    },
    /// Move files to an existing group or to the group for `title`.
    MoveFiles {
        media_ids: Vec<String>,
        #[serde(default)]
        group_id: Option<String>,
        #[serde(default)]
        title: Option<String>,
    },
    SetSeason {
        media_ids: Vec<String>,
        season: u32,
    },
    SetExcluded {
        media_ids: Vec<String>,
        excluded: bool,
    },
    /// Link files to a server-provided candidate, or clear the link.
    SetMatch {
        media_ids: Vec<String>,
        candidate: Option<IdentificationCandidate>,
    },
    SetSeriesOnly {
        media_ids: Vec<String>,
        series_only: bool,
    },
    SetCompanion {
        path: String,
        selected: bool,
    },
    SetCompanionOwner {
        path: String,
        owner: CompanionOwner,
    },
    SetSkipped {
        group_id: String,
        skipped: bool,
    },
    /// Finish a group whose files are already in place.
    MarkDone {
        group_id: String,
    },
}

#[derive(Debug, Clone, Deserialize)]
#[serde(
    tag = "kind",
    rename_all = "camelCase",
    rename_all_fields = "camelCase"
)]
pub enum CompanionOwner {
    Automatic,
    Video { media_id: String },
    Series { group_id: String },
}

impl LibraryOrganizer {
    pub(super) fn draft_file(&self) -> PathBuf {
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
                        .iter()
                        .filter(|(_, completion)| completion.as_str() != MARKED_DONE)
                        .map(|(group, _)| group)
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
        if self.draft()?.is_some() && !request.replace {
            return Err(LibraryServerError::new(
                "Resume or discard the existing review first.",
            ));
        }
        let ids = request.media_ids.into_iter().collect::<BTreeSet<_>>();
        if ids.is_empty() {
            return Err(LibraryServerError::new("Select at least one video."));
        }
        if request.destination.trim().is_empty() {
            return Err(LibraryServerError::new("Choose a destination folder."));
        }
        let mut entries = Vec::new();
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
            entries.push((item, source.clone(), root));
        }
        let folder_titles = folder_titles(&entries);
        let mut files = Vec::new();
        for (item, source, root) in entries {
            let provider = item.anime_metadata.as_ref();
            let raw_title = provider
                .map(|m| m.display_title.clone())
                .unwrap_or_else(|| {
                    folder_titles
                        .get(&source.parent().map(Path::to_path_buf))
                        .and_then(|titles| titles.get(&item.id))
                        .cloned()
                        .unwrap_or_else(|| item.series_title.clone())
                });
            let (title, title_season) = split_season_suffix(&raw_title);
            let (mut season_number, mut season_evidence) =
                season_assignment(&item.relative_path, &item.episode_title);
            if season_evidence == "SUGGESTED"
                && let Some(season) = title_season
            {
                season_number = Some(season);
                season_evidence = "PARSED".into();
            }
            let candidates = provider
                .filter(|m| {
                    matches!(
                        m.anime_id.provider,
                        crate::catalog::ExternalAnimeProvider::Dandanplay
                    )
                })
                .map(|m| {
                    vec![IdentificationCandidate {
                        anime_id: m.anime_id.value,
                        series_title: m.display_title.clone(),
                        episode_id: None,
                        episode_title: String::new(),
                    }]
                })
                .unwrap_or_default();
            files.push(DraftFile {
                series_only: false,
                source_signature: super::transfer::signature(&source)?,
                media_id: item.id.clone(),
                source_root: root.to_string_lossy().into_owned(),
                source_relative_path: relative_wire_path(&root, &source)?,
                size_bytes: item.size_bytes,
                group_id: format!("series:{}", series_key(&title)),
                series_title: title,
                season_number,
                season_evidence,
                excluded: false,
                manual_assignment: false,
                provider_title: provider.map(|m| m.display_title.clone()),
                candidates,
                candidate: None,
                identification_error: None,
                fingerprint: None,
            });
        }
        files.sort_by(|a, b| {
            natural_cmp(&a.source_root, &b.source_root)
                .then_with(|| natural_cmp(&a.source_relative_path, &b.source_relative_path))
        });
        let draft = OrganizationDraft {
            identification_pause: None,
            retry_not_before_epoch_ms: None,
            id: unique_id()?,
            revision: 1,
            destination: request.destination.trim().to_owned(),
            files,
            companion_choices: BTreeMap::new(),
            companion_owners: BTreeMap::new(),
            companion_series_owners: BTreeMap::new(),
            skipped: BTreeSet::new(),
            completed: BTreeMap::new(),
            group_candidates: BTreeMap::new(),
        };
        let mut runtime = self.runtime.lock().expect("organizer lock");
        if runtime.draft.is_some() && !request.replace {
            return Err(LibraryServerError::new(
                "Resume or discard the existing review first.",
            ));
        }
        self.stop_identification(&mut runtime);
        write_json_atomically(&self.draft_file(), &draft)?;
        runtime.draft = Some(draft.clone());
        runtime.draft_loaded = true;
        runtime.plans.clear();
        Ok(draft)
    }

    /// Applies user decisions atomically to the current draft.
    pub fn apply_ops(&self, request: DraftOpsRequest) -> Result<OrganizationDraft> {
        self.ensure_available()?;
        let _ = self.draft()?;
        let mut runtime = self.runtime.lock().expect("organizer lock");
        let current = runtime
            .draft
            .as_ref()
            .filter(|d| d.id == request.draft_id)
            .ok_or_else(|| LibraryServerError::new("This review was replaced or discarded."))?;
        let mut draft = current.clone();
        for op in request.ops {
            apply_op(&mut draft, op)?;
        }
        draft.revision += 1;
        write_json_atomically(&self.draft_file(), &draft)?;
        runtime.draft = Some(draft.clone());
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
        self.stop_identification(&mut runtime);
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

    /// Records identities for the listed groups (or all groups) in the
    /// metadata store. Moving files never requires this; it runs on approval.
    pub fn save_identification(
        &self,
        groups: Option<&[String]>,
        store: &CatalogMetadataStore,
    ) -> Result<()> {
        let Some(draft) = self.draft()? else {
            return Ok(());
        };
        for file in draft
            .files
            .iter()
            .filter(|f| !f.excluded && groups.is_none_or(|groups| groups.contains(&f.group_id)))
        {
            if let Some(candidate) = &file.candidate {
                if file.series_only || candidate.episode_id.is_none() {
                    store.record_series_only(
                        &file.media_id,
                        candidate.anime_id,
                        candidate.series_title.clone(),
                    )?;
                } else {
                    store.record_with_episode(
                        &file.media_id,
                        candidate.anime_id,
                        candidate.series_title.clone(),
                        (!candidate.episode_title.is_empty())
                            .then(|| candidate.episode_title.clone()),
                        candidate.episode_id,
                    )?;
                }
            } else if file.series_only
                && let Some(existing) = store.get(&file.media_id)
            {
                store.record_series_only(
                    &file.media_id,
                    existing.dandanplay_anime_id,
                    existing.anime_title,
                )?;
            }
        }
        Ok(())
    }
}

/// Folder title preferred per source folder: a folder holding one show keeps
/// the user's folder name; download dumps use titles parsed from file names.
fn folder_titles(
    entries: &[(&crate::catalog::LibraryMediaItem, PathBuf, PathBuf)],
) -> BTreeMap<Option<PathBuf>, BTreeMap<String, String>> {
    let mut by_folder = BTreeMap::<Option<PathBuf>, Vec<(&String, Option<String>, &String)>>::new();
    for (item, source, _) in entries {
        let stem = source
            .file_stem()
            .map(|s| s.to_string_lossy().into_owned())
            .unwrap_or_default();
        by_folder
            .entry(source.parent().map(Path::to_path_buf))
            .or_default()
            .push((&item.id, title_from_file_stem(&stem), &item.series_title));
    }
    by_folder
        .into_iter()
        .map(|(folder, items)| {
            let keys = items
                .iter()
                .filter_map(|(_, title, _)| title.as_deref().map(series_key))
                .collect::<BTreeSet<_>>();
            let titles = items
                .iter()
                .map(|(id, parsed, folder_title)| {
                    let folder_key = series_key(folder_title);
                    let title = match parsed {
                        Some(parsed)
                            if keys.len() > 1
                                || !(series_key(parsed).contains(&folder_key)
                                    || folder_key.contains(&series_key(parsed))) =>
                        {
                            parsed.clone()
                        }
                        _ => (*folder_title).clone(),
                    };
                    ((*id).clone(), title)
                })
                .collect();
            (folder, titles)
        })
        .collect()
}

fn files_mut<'a>(
    draft: &'a mut OrganizationDraft,
    media_ids: &'a [String],
) -> impl Iterator<Item = &'a mut DraftFile> + 'a {
    let completed = draft.completed.clone();
    draft
        .files
        .iter_mut()
        .filter(move |f| media_ids.contains(&f.media_id) && !completed.contains_key(&f.group_id))
}

fn require_group(draft: &OrganizationDraft, group_id: &str) -> Result<()> {
    if draft.completed.contains_key(group_id) {
        return Err(LibraryServerError::new("This series is already finished."));
    }
    if !draft.files.iter().any(|f| f.group_id == group_id) {
        return Err(LibraryServerError::new(
            "This series is no longer in the review.",
        ));
    }
    Ok(())
}

fn require_title(title: &str) -> Result<String> {
    let title = title.trim();
    sanitize_component(title)?;
    Ok(title.to_owned())
}

pub(super) fn apply_op(draft: &mut OrganizationDraft, op: DraftOp) -> Result<()> {
    match op {
        DraftOp::SetDestination { destination } => {
            let destination = destination.trim();
            if destination.is_empty() {
                return Err(LibraryServerError::new("Choose a destination folder."));
            }
            draft.destination = destination.to_owned();
        }
        DraftOp::RenameGroup { group_id, title } => {
            require_group(draft, &group_id)?;
            let title = require_title(&title)?;
            let target = draft.group_for_title(&title, Some(&group_id));
            let target = if draft.files.iter().any(|f| f.group_id == target) {
                target
            } else {
                group_id.clone()
            };
            for file in draft.files.iter_mut().filter(|f| f.group_id == group_id) {
                file.group_id = target.clone();
                file.series_title = title.clone();
                file.manual_assignment = true;
            }
            // The user named this folder: identification never retitles it.
            for file in draft.files.iter_mut().filter(|f| f.group_id == target) {
                file.series_title = title.clone();
                file.manual_assignment = true;
            }
            if target != group_id
                && let Some(results) = draft.group_candidates.remove(&group_id)
            {
                draft.group_candidates.entry(target).or_insert(results);
            }
        }
        DraftOp::ConfirmGroup { group_id } => {
            require_group(draft, &group_id)?;
            for file in draft.files.iter_mut().filter(|f| f.group_id == group_id) {
                file.manual_assignment = true;
            }
        }
        DraftOp::MoveFiles {
            media_ids,
            group_id,
            title,
        } => {
            let (target, title) = match (group_id, title) {
                (Some(group), _) => {
                    require_group(draft, &group)?;
                    let title = draft.group_title(&group).unwrap_or_default().to_owned();
                    (group, title)
                }
                (None, Some(title)) => {
                    let title = require_title(&title)?;
                    (draft.group_for_title(&title, None), title)
                }
                (None, None) => {
                    return Err(LibraryServerError::new("Choose a series for the videos."));
                }
            };
            for file in files_mut(draft, &media_ids) {
                file.group_id = target.clone();
                file.series_title = title.clone();
                file.manual_assignment = true;
            }
            draft.skipped.remove(&target);
        }
        DraftOp::SetSeason { media_ids, season } => {
            if season > 9999 {
                return Err(LibraryServerError::new(
                    "Enter a season between 0 and 9999.",
                ));
            }
            for file in files_mut(draft, &media_ids) {
                file.season_number = Some(season);
                file.season_evidence = "MANUAL".into();
            }
        }
        DraftOp::SetExcluded {
            media_ids,
            excluded,
        } => {
            for file in files_mut(draft, &media_ids) {
                file.excluded = excluded;
            }
        }
        DraftOp::SetMatch {
            media_ids,
            candidate,
        } => {
            if let Some(candidate) = &candidate
                && !draft.known_candidate(candidate)
            {
                return Err(LibraryServerError::new(
                    "Select an identification candidate returned by the server.",
                ));
            }
            // Only a match for a whole series renames its folder, and never one
            // the user named; picking one video's episode leaves it in place.
            let retitle = media_ids
                .iter()
                .filter_map(|id| draft.files.iter().find(|f| &f.media_id == id))
                .map(|f| f.group_id.clone())
                .collect::<BTreeSet<_>>()
                .into_iter()
                .filter(|group| {
                    let mut members = draft
                        .files
                        .iter()
                        .filter(|f| &f.group_id == group && draft.editable(f));
                    members.clone().all(|f| !f.manual_assignment)
                        && members.all(|f| f.excluded || media_ids.contains(&f.media_id))
                })
                .collect::<Vec<_>>();
            for file in files_mut(draft, &media_ids) {
                match &candidate {
                    None => {
                        file.candidate = None;
                        file.series_only = false;
                    }
                    Some(candidate) => {
                        let own_episode = file
                            .candidates
                            .iter()
                            .find(|c| c.anime_id == candidate.anime_id && c.episode_id.is_some())
                            .filter(|_| candidate.episode_id.is_none())
                            .cloned();
                        let chosen = own_episode.unwrap_or_else(|| candidate.clone());
                        file.series_only = chosen.episode_id.is_none();
                        apply_candidate_season(file, &chosen);
                        file.candidate = Some(chosen);
                    }
                }
            }
            if let Some(candidate) = candidate {
                let title = split_season_suffix(&candidate.series_title).0;
                for group in retitle {
                    // Join an open series with this title, else keep the group's ID.
                    let target = draft.group_for_title(&title, Some(&group));
                    let target = if draft.files.iter().any(|f| f.group_id == target) {
                        target
                    } else {
                        group.clone()
                    };
                    for file in draft.files.iter_mut().filter(|f| f.group_id == group) {
                        file.group_id = target.clone();
                        file.series_title = title.clone();
                    }
                }
            }
        }
        DraftOp::SetSeriesOnly {
            media_ids,
            series_only,
        } => {
            for file in files_mut(draft, &media_ids) {
                file.series_only = series_only;
            }
        }
        DraftOp::SetCompanion { path, selected } => {
            draft.companion_choices.insert(path, selected);
        }
        DraftOp::SetCompanionOwner { path, owner } => match owner {
            CompanionOwner::Automatic => {
                draft.companion_owners.remove(&path);
                draft.companion_series_owners.remove(&path);
            }
            CompanionOwner::Video { media_id } => {
                if !draft.files.iter().any(|f| f.media_id == media_id) {
                    return Err(LibraryServerError::new("Unknown selected video."));
                }
                draft.companion_series_owners.remove(&path);
                draft.companion_owners.insert(path, media_id);
            }
            CompanionOwner::Series { group_id } => {
                require_group(draft, &group_id)?;
                draft.companion_owners.remove(&path);
                draft.companion_series_owners.insert(path, group_id);
            }
        },
        DraftOp::SetSkipped { group_id, skipped } => {
            require_group(draft, &group_id)?;
            if skipped {
                draft.skipped.insert(group_id);
            } else {
                draft.skipped.remove(&group_id);
            }
        }
        DraftOp::MarkDone { group_id } => {
            require_group(draft, &group_id)?;
            draft.skipped.remove(&group_id);
            draft.completed.insert(group_id, MARKED_DONE.into());
        }
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

pub(super) fn apply_candidate_season(file: &mut DraftFile, candidate: &IdentificationCandidate) {
    let derived = matches!(
        file.season_evidence.as_str(),
        "SUGGESTED" | "PROVIDER_TITLE"
    );
    match split_season_suffix(&candidate.series_title).1 {
        _ if file.season_evidence == "MANUAL" => {}
        Some(season) if derived => {
            file.season_number = Some(season);
            file.season_evidence = "PROVIDER_TITLE".into();
        }
        Some(season) if file.season_number != Some(season) => {
            file.season_number = None;
            file.season_evidence = "CONFLICT".into();
        }
        // A previous provider title's season no longer applies.
        None if file.season_evidence == "PROVIDER_TITLE" => {
            file.season_number = Some(1);
            file.season_evidence = "SUGGESTED".into();
        }
        _ => {}
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn file(id: &str, group: &str, title: &str) -> DraftFile {
        DraftFile {
            series_only: false,
            source_signature: "sig".into(),
            media_id: id.into(),
            source_root: "/root".into(),
            source_relative_path: format!("{id}.mkv"),
            size_bytes: 1,
            group_id: group.into(),
            series_title: title.into(),
            season_number: Some(1),
            season_evidence: "SUGGESTED".into(),
            excluded: false,
            manual_assignment: false,
            provider_title: None,
            candidates: Vec::new(),
            candidate: None,
            identification_error: None,
            fingerprint: None,
        }
    }

    fn draft(files: Vec<DraftFile>) -> OrganizationDraft {
        OrganizationDraft {
            identification_pause: None,
            retry_not_before_epoch_ms: None,
            id: "draft".into(),
            revision: 1,
            destination: "/root".into(),
            files,
            companion_choices: BTreeMap::new(),
            companion_owners: BTreeMap::new(),
            companion_series_owners: BTreeMap::new(),
            skipped: BTreeSet::new(),
            completed: BTreeMap::new(),
            group_candidates: BTreeMap::new(),
        }
    }

    #[test]
    fn ops_deserialize_from_tagged_json() {
        let request: DraftOpsRequest = serde_json::from_value(serde_json::json!({
            "draftId": "d",
            "ops": [
                {"op": "setSeason", "mediaIds": ["a"], "season": 2},
                {"op": "moveFiles", "mediaIds": ["a"], "title": "Show"},
                {"op": "setCompanionOwner", "path": "x.ass", "owner": {"kind": "series", "groupId": "g"}},
                {"op": "setMatch", "mediaIds": ["a"], "candidate": null}
            ]
        }))
        .unwrap();
        assert_eq!(request.ops.len(), 4);
    }

    #[test]
    fn renaming_to_an_existing_title_merges_groups_and_marks_manual() {
        let mut draft = draft(vec![file("a", "g1", "Show"), file("b", "g2", "Other")]);
        apply_op(
            &mut draft,
            DraftOp::RenameGroup {
                group_id: "g2".into(),
                title: "show".into(),
            },
        )
        .unwrap();
        assert!(draft.files.iter().all(|f| f.group_id == "g1"));
        assert!(draft.files.iter().all(|f| f.series_title == "show"));
        assert!(draft.files[1].manual_assignment);
    }

    #[test]
    fn moving_to_a_new_title_creates_a_group_and_completed_groups_are_frozen() {
        let mut draft = draft(vec![file("a", "g1", "Show"), file("b", "g1", "Show")]);
        apply_op(
            &mut draft,
            DraftOp::MoveFiles {
                media_ids: vec!["b".into()],
                group_id: None,
                title: Some("Movie".into()),
            },
        )
        .unwrap();
        assert_eq!(draft.files[1].group_id, "series:movie");
        draft.completed.insert("g1".into(), "done".into());
        apply_op(
            &mut draft,
            DraftOp::SetExcluded {
                media_ids: vec!["a".into()],
                excluded: true,
            },
        )
        .unwrap();
        assert!(!draft.files[0].excluded);
    }

    #[test]
    fn group_match_keeps_each_files_fingerprint_episode_and_rejects_unknown_ids() {
        let mut a = file("a", "g1", "Folder");
        a.candidates = vec![IdentificationCandidate {
            anime_id: 7,
            episode_id: Some(70),
            series_title: "Show 第二季".into(),
            episode_title: "Ep 1".into(),
        }];
        let mut draft = draft(vec![a, file("b", "g1", "Folder")]);
        let series = IdentificationCandidate {
            anime_id: 7,
            episode_id: None,
            series_title: "Show 第二季".into(),
            episode_title: String::new(),
        };
        apply_op(
            &mut draft,
            DraftOp::SetMatch {
                media_ids: vec!["a".into(), "b".into()],
                candidate: Some(series.clone()),
            },
        )
        .unwrap();
        assert_eq!(
            draft.files[0].candidate.as_ref().unwrap().episode_id,
            Some(70)
        );
        assert!(!draft.files[0].series_only);
        assert!(draft.files[1].series_only);
        assert!(draft.files.iter().all(|f| f.series_title == "Show"));
        assert!(draft.files.iter().all(|f| f.season_number == Some(2)));
        // Switching to a title without a season drops the provider-derived season.
        let first = IdentificationCandidate {
            anime_id: 6,
            series_title: "Show".into(),
            ..series.clone()
        };
        draft
            .group_candidates
            .insert("g1".into(), vec![first.clone()]);
        apply_op(
            &mut draft,
            DraftOp::SetMatch {
                media_ids: vec!["b".into()],
                candidate: Some(first),
            },
        )
        .unwrap();
        assert_eq!(draft.files[1].season_number, Some(1));
        assert_eq!(draft.files[1].season_evidence, "SUGGESTED");
        let unknown = IdentificationCandidate {
            anime_id: 9,
            ..series
        };
        assert!(
            apply_op(
                &mut draft,
                DraftOp::SetMatch {
                    media_ids: vec!["a".into()],
                    candidate: Some(unknown),
                },
            )
            .is_err()
        );
    }

    #[test]
    fn matching_never_splits_a_series_or_renames_a_folder_the_user_named() {
        let episode = |id: u64, n: u64| IdentificationCandidate {
            anime_id: id,
            episode_id: Some(id * 100 + n),
            series_title: "葬送的芙莉莲".into(),
            episode_title: format!("第{n}话"),
        };
        let mut files = vec![
            file("01", "g", "Sousou no Frieren"),
            file("02", "g", "Sousou no Frieren"),
            file("03", "g", "Sousou no Frieren"),
        ];
        for (n, file) in files.iter_mut().enumerate() {
            file.candidates = vec![episode(7, n as u64 + 1)];
        }
        let mut draft = draft(files);
        // One video's episode: the series stays together under its folder name.
        apply_op(
            &mut draft,
            DraftOp::SetMatch {
                media_ids: vec!["01".into()],
                candidate: Some(episode(7, 1)),
            },
        )
        .unwrap();
        assert!(draft.files.iter().all(|f| f.group_id == "g"));
        assert!(
            draft
                .files
                .iter()
                .all(|f| f.series_title == "Sousou no Frieren")
        );
        // After the user names the folder, a series match keeps every video there.
        apply_op(
            &mut draft,
            DraftOp::RenameGroup {
                group_id: "g".into(),
                title: "Frieren".into(),
            },
        )
        .unwrap();
        let series = IdentificationCandidate {
            episode_id: None,
            episode_title: String::new(),
            ..episode(7, 0)
        };
        apply_op(
            &mut draft,
            DraftOp::SetMatch {
                media_ids: vec!["01".into(), "02".into(), "03".into()],
                candidate: Some(series),
            },
        )
        .unwrap();
        assert!(draft.files.iter().all(|f| f.group_id == "g"));
        assert!(draft.files.iter().all(|f| f.series_title == "Frieren"));
        assert_eq!(
            draft.files[2].candidate.as_ref().unwrap().episode_id,
            Some(703)
        );
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
}
