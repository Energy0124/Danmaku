use std::collections::{BTreeMap, BTreeSet};
use std::fs;
use std::path::{Component, Path, PathBuf};
use std::sync::Mutex;
use std::sync::atomic::{AtomicBool, Ordering};

use serde::{Deserialize, Serialize};

use crate::catalog::{
    CatalogStore, HeadlessStoredLibrary, LibraryCatalog, LibraryMediaItem, PathMap,
    PublishedLibrary, current_epoch_ms,
};
use crate::hash::sha256_hex;
use crate::scanner::find_season_number;
use crate::{LibraryServerError, Result};

const JOURNAL_SCHEMA_VERSION: u32 = 1;
const COMPLETED_HISTORY_LIMIT: usize = 20;
const VIDEO_EXTENSIONS: &[&str] = &["mkv", "mp4", "m4v", "webm", "ts", "m2ts", "avi", "mov"];
const SUBTITLE_EXTENSIONS: &[&str] = &["ass", "ssa", "srt", "vtt", "sub"];

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct OrganizationPreviewRequest {
    pub root: String,
    #[serde(default)]
    pub base_relative_path: String,
    #[serde(default)]
    pub overrides: Vec<OrganizationSeriesOverride>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct OrganizationSeriesOverride {
    pub batch_id: String,
    pub series_title: String,
    pub season_number: u32,
    #[serde(default)]
    pub included_nearby_paths: Vec<String>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct OrganizationPlan {
    pub plan_id: String,
    pub catalog_revision: String,
    pub root: String,
    pub base_relative_path: String,
    pub batches: Vec<OrganizationSeriesBatch>,
    pub unassigned_count: usize,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct OrganizationSeriesBatch {
    pub batch_id: String,
    pub series_title: String,
    pub season_number: Option<u32>,
    pub confidence: OrganizationConfidence,
    pub reason: String,
    pub video_count: usize,
    pub executable: bool,
    pub already_organized: bool,
    pub conflicts: Vec<String>,
    pub moves: Vec<OrganizationMove>,
    pub nearby_files: Vec<OrganizationNearbyFile>,
}

#[derive(Debug, Clone, Copy, Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum OrganizationConfidence {
    Provider,
    Parsed,
    NeedsReview,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct OrganizationMove {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub media_id: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub subtitle_id: Option<String>,
    pub source_relative_path: String,
    pub destination_relative_path: String,
    pub size_bytes: u64,
    pub kind: OrganizationMoveKind,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub original_series_title: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub destination_series_title: Option<String>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum OrganizationMoveKind {
    Video,
    Subtitle,
    Nearby,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct OrganizationNearbyFile {
    pub relative_path: String,
    pub size_bytes: u64,
    pub recommended: bool,
    pub selected: bool,
    pub destination_relative_path: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct OrganizationExecuteRequest {
    pub plan_id: String,
    pub batch_id: String,
    pub expected_moves: Vec<OrganizationMove>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct OrganizationUndoRequest {
    pub completed_batch_id: String,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct OrganizationAccepted {
    pub batch_id: String,
    pub status: &'static str,
}

#[derive(Debug, Clone, Default, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct OrganizationStatus {
    pub state: OrganizationState,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub batch_id: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub series_title: Option<String>,
    pub completed_operations: usize,
    pub total_operations: usize,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub message: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub last_completed_batch_id: Option<String>,
    pub can_undo: bool,
}

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum OrganizationState {
    #[default]
    Idle,
    Running,
    RollingBack,
    Completed,
    Cancelled,
    Failed,
    RecoveryRequired,
}

#[derive(Debug, Clone)]
pub struct PreparedOrganization {
    batch: StoredBatch,
    undo: bool,
}

#[derive(Debug)]
pub struct LibraryOrganizer {
    roots: Vec<PathBuf>,
    catalog_store: CatalogStore,
    journal_file: PathBuf,
    runtime: Mutex<OrganizerRuntime>,
    cancel_requested: AtomicBool,
}

#[derive(Debug, Default)]
struct OrganizerRuntime {
    plans: BTreeMap<String, StoredPlan>,
    status: OrganizationStatus,
    journal: OrganizationJournal,
}

#[derive(Debug, Clone)]
struct StoredPlan {
    catalog_revision: String,
    root: PathBuf,
    batches: BTreeMap<String, StoredBatch>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct StoredBatch {
    batch_id: String,
    series_title: String,
    root: PathBuf,
    moves: Vec<OrganizationMove>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct OrganizationJournal {
    schema_version: u32,
    #[serde(default)]
    active: Option<JournalTransaction>,
    #[serde(default)]
    completed: Vec<CompletedBatch>,
    #[serde(default)]
    recovery_error: Option<String>,
}

impl Default for OrganizationJournal {
    fn default() -> Self {
        Self {
            schema_version: JOURNAL_SCHEMA_VERSION,
            active: None,
            completed: Vec::new(),
            recovery_error: None,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct JournalTransaction {
    batch: StoredBatch,
    moved_count: usize,
    undo: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct CompletedBatch {
    completed_batch_id: String,
    batch: StoredBatch,
    completed_at_epoch_ms: u64,
}

impl LibraryOrganizer {
    pub fn new(roots: Vec<PathBuf>, catalog_store: CatalogStore) -> Self {
        let journal_file = catalog_store
            .file_path()
            .parent()
            .unwrap_or_else(|| Path::new("."))
            .join("library-organization.json");
        let mut status = OrganizationStatus::default();
        let (mut journal, journal_loaded) = match load_journal(&journal_file) {
            Ok(journal) => (journal, true),
            Err(error) => {
                let message =
                    format!("The organization journal could not be loaded safely: {error}");
                status.state = OrganizationState::RecoveryRequired;
                status.message = Some(message.clone());
                let mut journal = OrganizationJournal::default();
                journal.recovery_error = Some(message);
                (journal, false)
            }
        };
        if journal_loaded && let Some(error) = recover_interrupted(&catalog_store, &mut journal) {
            status.state = OrganizationState::RecoveryRequired;
            status.message = Some(error.clone());
            journal.recovery_error = Some(error);
        }
        status.last_completed_batch_id = journal
            .completed
            .last()
            .map(|completed| completed.completed_batch_id.clone());
        status.can_undo = status.last_completed_batch_id.is_some()
            && status.state != OrganizationState::RecoveryRequired;
        let organizer = Self {
            roots,
            catalog_store,
            journal_file,
            runtime: Mutex::new(OrganizerRuntime {
                plans: BTreeMap::new(),
                status,
                journal,
            }),
            cancel_requested: AtomicBool::new(false),
        };
        if journal_loaded && let Err(error) = organizer.persist_journal() {
            organizer.finish_failed(
                format!("The organization journal is unavailable: {error}"),
                true,
            );
        }
        organizer
    }

    pub fn preview(
        &self,
        published: &PublishedLibrary,
        request: OrganizationPreviewRequest,
    ) -> Result<OrganizationPlan> {
        self.ensure_available()?;
        let root = self.resolve_root(&request.root)?;
        let base = validate_relative_directory(&request.base_relative_path)?;
        let catalog_revision = catalog_revision(&published.catalog);
        let overrides = request
            .overrides
            .into_iter()
            .map(|entry| (entry.batch_id.clone(), entry))
            .collect::<BTreeMap<_, _>>();
        let plan = build_plan(published, &root, &base, &catalog_revision, &overrides)?;
        let stored = StoredPlan {
            catalog_revision: plan.catalog_revision.clone(),
            root,
            batches: plan
                .batches
                .iter()
                .filter(|batch| batch.executable)
                .map(|batch| {
                    (
                        batch.batch_id.clone(),
                        StoredBatch {
                            batch_id: batch.batch_id.clone(),
                            series_title: batch.series_title.clone(),
                            root: PathBuf::from(&plan.root),
                            moves: batch.moves.clone(),
                        },
                    )
                })
                .collect(),
        };
        let mut runtime = self
            .runtime
            .lock()
            .expect("organizer lock should not poison");
        runtime.plans.clear();
        runtime.plans.insert(plan.plan_id.clone(), stored);
        Ok(plan)
    }

    pub fn prepare_execute(
        &self,
        current_catalog: &LibraryCatalog,
        request: OrganizationExecuteRequest,
    ) -> Result<PreparedOrganization> {
        self.ensure_available()?;
        let current_revision = catalog_revision(current_catalog);
        let mut runtime = self
            .runtime
            .lock()
            .expect("organizer lock should not poison");
        if runtime.status.state == OrganizationState::Running
            || runtime.status.state == OrganizationState::RollingBack
        {
            return Err(LibraryServerError::new(
                "An organization batch is already running.",
            ));
        }
        let plan = runtime.plans.get(&request.plan_id).ok_or_else(|| {
            LibraryServerError::new("The organization plan expired; preview again.")
        })?;
        if plan.catalog_revision != current_revision {
            return Err(LibraryServerError::new(
                "The library changed; preview the plan again.",
            ));
        }
        let batch = plan
            .batches
            .get(&request.batch_id)
            .ok_or_else(|| LibraryServerError::new("The selected series is not executable."))?
            .clone();
        if plan.root != batch.root || request.expected_moves != batch.moves {
            return Err(LibraryServerError::new(
                "The approved move list no longer matches the preview.",
            ));
        }
        self.cancel_requested.store(false, Ordering::Release);
        runtime.status = running_status(&batch, false);
        Ok(PreparedOrganization { batch, undo: false })
    }

    pub fn prepare_undo(&self, completed_batch_id: &str) -> Result<PreparedOrganization> {
        self.ensure_available()?;
        let mut runtime = self
            .runtime
            .lock()
            .expect("organizer lock should not poison");
        if runtime.status.state == OrganizationState::Running
            || runtime.status.state == OrganizationState::RollingBack
        {
            return Err(LibraryServerError::new(
                "An organization batch is already running.",
            ));
        }
        let completed = runtime
            .journal
            .completed
            .iter()
            .find(|entry| entry.completed_batch_id == completed_batch_id)
            .cloned()
            .ok_or_else(|| {
                LibraryServerError::new("The completed series is no longer available to undo.")
            })?;
        let mut batch = completed.batch;
        batch.batch_id = format!("undo-{}", batch.batch_id);
        batch.moves = batch.moves.into_iter().rev().map(reverse_move).collect();
        self.cancel_requested.store(false, Ordering::Release);
        runtime.status = running_status(&batch, true);
        Ok(PreparedOrganization { batch, undo: true })
    }

    pub fn execute(&self, prepared: PreparedOrganization) -> Result<PublishedLibrary> {
        let batch = prepared.batch;
        let transaction = JournalTransaction {
            batch: batch.clone(),
            moved_count: 0,
            undo: prepared.undo,
        };
        {
            let mut runtime = self
                .runtime
                .lock()
                .expect("organizer lock should not poison");
            runtime.journal.active = Some(transaction);
        }
        if let Err(error) = self.persist_journal() {
            self.finish_failed(
                format!("The organization journal is unavailable: {error}"),
                true,
            );
            return Err(error);
        }

        if let Err(error) = preflight_batch(&batch) {
            self.finish_failed(error.to_string(), false);
            return Err(error);
        }

        for (index, operation) in batch.moves.iter().enumerate() {
            if self.cancel_requested.load(Ordering::Acquire) {
                let error = LibraryServerError::new("Organization cancelled.");
                self.rollback_active(true)?;
                return Err(error);
            }
            let source = batch
                .root
                .join(path_from_wire(&operation.source_relative_path));
            let destination = batch
                .root
                .join(path_from_wire(&operation.destination_relative_path));
            if let Some(parent) = destination.parent() {
                if let Err(error) = fs::create_dir_all(parent).map_err(|error| {
                    LibraryServerError::with_context(
                        error,
                        format!("failed to create destination {}", parent.display()),
                    )
                }) {
                    self.rollback_active(false)?;
                    return Err(error);
                }
            }
            if let Err(error) = move_without_overwrite(&source, &destination) {
                self.rollback_active(false)?;
                return Err(error);
            }
            {
                let mut runtime = self
                    .runtime
                    .lock()
                    .expect("organizer lock should not poison");
                if let Some(active) = runtime.journal.active.as_mut() {
                    active.moved_count = index + 1;
                }
                runtime.status.completed_operations = index + 1;
            }
            if let Err(error) = self.persist_journal() {
                self.rollback_active(false)?;
                return Err(error);
            }
            if let Err(error) = verify_destination(&destination, operation.size_bytes) {
                self.rollback_active(false)?;
                return Err(error);
            }
        }

        let updated = match self.apply_catalog_moves(&batch) {
            Ok(updated) => updated,
            Err(error) => {
                self.rollback_active(false)?;
                return Err(error);
            }
        };
        self.finish_completed(batch, prepared.undo)?;
        Ok(updated.published_library)
    }

    pub fn cancel(&self) {
        self.cancel_requested.store(true, Ordering::Release);
    }

    pub fn status(&self) -> OrganizationStatus {
        self.runtime
            .lock()
            .expect("organizer lock should not poison")
            .status
            .clone()
    }

    fn resolve_root(&self, supplied: &str) -> Result<PathBuf> {
        let supplied = normalize_absolute(Path::new(supplied))?;
        self.roots
            .iter()
            .filter_map(|root| normalize_absolute(root).ok())
            .find(|root| paths_equal(root, &supplied))
            .ok_or_else(|| LibraryServerError::new("Select one of the configured library roots."))
    }

    fn ensure_available(&self) -> Result<()> {
        let runtime = self
            .runtime
            .lock()
            .expect("organizer lock should not poison");
        if matches!(
            runtime.status.state,
            OrganizationState::Running | OrganizationState::RollingBack
        ) {
            return Err(LibraryServerError::new(
                "Wait for the current organization batch to finish.",
            ));
        }
        if runtime.status.state == OrganizationState::RecoveryRequired {
            return Err(LibraryServerError::new(
                runtime
                    .status
                    .message
                    .clone()
                    .unwrap_or_else(|| "Organizer recovery is required.".to_owned()),
            ));
        }
        Ok(())
    }

    fn apply_catalog_moves(&self, batch: &StoredBatch) -> Result<HeadlessStoredLibrary> {
        let mut stored = self
            .catalog_store
            .load()?
            .ok_or_else(|| LibraryServerError::new("The catalog is unavailable."))?;
        apply_moves_to_stored(&mut stored, &batch.root, &batch.moves)?;
        stored.saved_at_epoch_ms = current_epoch_ms();
        stored.published_library.catalog.indexed_at_epoch_ms = stored.saved_at_epoch_ms;
        self.catalog_store.save_stored(&stored)?;
        Ok(stored)
    }

    fn rollback_active(&self, cancelled: bool) -> Result<()> {
        {
            let mut runtime = self
                .runtime
                .lock()
                .expect("organizer lock should not poison");
            runtime.status.state = OrganizationState::RollingBack;
        }
        let active = self
            .runtime
            .lock()
            .expect("organizer lock should not poison")
            .journal
            .active
            .clone()
            .ok_or_else(|| LibraryServerError::new("No organization transaction is active."))?;
        if let Err(error) = rollback_transaction(&active) {
            self.finish_failed(error.to_string(), true);
            return Err(error);
        }
        let mut runtime = self
            .runtime
            .lock()
            .expect("organizer lock should not poison");
        runtime.journal.active = None;
        runtime.status.state = if cancelled {
            OrganizationState::Cancelled
        } else {
            OrganizationState::Failed
        };
        runtime.status.message = Some(if cancelled {
            "The series was cancelled and rolled back.".to_owned()
        } else {
            "The series failed and was rolled back.".to_owned()
        });
        drop(runtime);
        self.persist_journal()
    }

    fn finish_completed(&self, batch: StoredBatch, undo: bool) -> Result<()> {
        let mut runtime = self
            .runtime
            .lock()
            .expect("organizer lock should not poison");
        runtime.journal.active = None;
        if undo {
            let original_id = batch.batch_id.trim_start_matches("undo-");
            runtime
                .journal
                .completed
                .retain(|entry| entry.batch.batch_id != original_id);
        } else {
            let completed_batch_id = format!("{}-{}", batch.batch_id, current_epoch_ms());
            runtime.journal.completed.push(CompletedBatch {
                completed_batch_id: completed_batch_id.clone(),
                batch: batch.clone(),
                completed_at_epoch_ms: current_epoch_ms(),
            });
            if runtime.journal.completed.len() > COMPLETED_HISTORY_LIMIT {
                let excess = runtime.journal.completed.len() - COMPLETED_HISTORY_LIMIT;
                runtime.journal.completed.drain(0..excess);
            }
            runtime.status.last_completed_batch_id = Some(completed_batch_id);
        }
        runtime.status.state = OrganizationState::Completed;
        runtime.status.completed_operations = batch.moves.len();
        runtime.status.total_operations = batch.moves.len();
        runtime.status.message = Some(if undo {
            "The completed series was restored to its original paths.".to_owned()
        } else {
            "The approved series was moved and verified.".to_owned()
        });
        runtime.status.can_undo = !runtime.journal.completed.is_empty();
        runtime.status.last_completed_batch_id = runtime
            .journal
            .completed
            .last()
            .map(|entry| entry.completed_batch_id.clone());
        drop(runtime);
        self.persist_journal()
    }

    fn finish_failed(&self, message: String, recovery_required: bool) {
        let mut runtime = self
            .runtime
            .lock()
            .expect("organizer lock should not poison");
        runtime.status.state = if recovery_required {
            OrganizationState::RecoveryRequired
        } else {
            OrganizationState::Failed
        };
        runtime.status.message = Some(message.clone());
        if recovery_required {
            runtime.journal.recovery_error = Some(message);
        } else {
            runtime.journal.active = None;
        }
        drop(runtime);
        let _ = self.persist_journal();
    }

    fn persist_journal(&self) -> Result<()> {
        let journal = self
            .runtime
            .lock()
            .expect("organizer lock should not poison")
            .journal
            .clone();
        write_json_atomically(&self.journal_file, &journal)
    }
}

fn build_plan(
    published: &PublishedLibrary,
    root: &Path,
    base: &Path,
    catalog_revision: &str,
    overrides: &BTreeMap<String, OrganizationSeriesOverride>,
) -> Result<OrganizationPlan> {
    let root_label = root.to_string_lossy().into_owned();
    let mut grouped: BTreeMap<String, Vec<&LibraryMediaItem>> = BTreeMap::new();
    for item in &published.catalog.items {
        if !item
            .root_label
            .as_deref()
            .is_some_and(|label| paths_equal(Path::new(label), root))
        {
            continue;
        }
        let series_key = item.anime_metadata.as_ref().map_or_else(
            || format!("title:{}", normalize_key(&item.series_title)),
            |metadata| {
                format!(
                    "provider:{:?}:{}",
                    metadata.anime_id.provider, metadata.anime_id.value
                )
            },
        );
        let detected_season =
            find_season_number(&format!("{} {}", item.relative_path, item.episode_title));
        let key = format!("{series_key}:season:{detected_season:?}");
        grouped.entry(key).or_default().push(item);
    }

    let all_video_paths = published
        .files_by_id
        .values()
        .filter_map(|path| normalize_absolute(path).ok())
        .collect::<BTreeSet<_>>();
    let subtitle_ids = published
        .subtitle_files_by_id
        .iter()
        .filter_map(|(id, path)| normalize_absolute(path).ok().map(|path| (path, id.clone())))
        .collect::<BTreeMap<_, _>>();
    let mut batches = Vec::new();
    let mut unassigned_count = 0;
    for (group_key, mut items) in grouped {
        items.sort_by(|left, right| left.relative_path.cmp(&right.relative_path));
        let batch_id = sha256_hex(&group_key).chars().take(16).collect::<String>();
        let default_title = items
            .iter()
            .find_map(|item| {
                item.anime_metadata
                    .as_ref()
                    .map(|metadata| metadata.display_title.clone())
            })
            .unwrap_or_else(|| items[0].series_title.clone());
        let detected_seasons = items
            .iter()
            .filter_map(|item| {
                find_season_number(&format!("{} {}", item.relative_path, item.episode_title))
            })
            .collect::<BTreeSet<_>>();
        let detected_season = (detected_seasons.len() == 1)
            .then(|| detected_seasons.iter().next().copied())
            .flatten();
        let override_value = overrides.get(&batch_id);
        let series_title = override_value
            .map(|value| value.series_title.trim().to_owned())
            .filter(|value| !value.is_empty())
            .unwrap_or(default_title);
        let season_number = override_value
            .map(|value| value.season_number)
            .or(detected_season);
        if season_number.is_none() {
            unassigned_count += items.len();
        }
        let safe_title = sanitize_component(&series_title)?;
        let selected_nearby = override_value
            .map(|value| {
                value
                    .included_nearby_paths
                    .iter()
                    .cloned()
                    .collect::<BTreeSet<_>>()
            })
            .unwrap_or_default();
        let confidence = if items.iter().any(|item| item.anime_metadata.is_some()) {
            OrganizationConfidence::Provider
        } else if season_number.is_some() {
            OrganizationConfidence::Parsed
        } else {
            OrganizationConfidence::NeedsReview
        };
        let reason = match confidence {
            OrganizationConfidence::Provider => {
                "Matched provider identity and parsed season".to_owned()
            }
            OrganizationConfidence::Parsed => {
                "Grouped from the current catalog title and filename".to_owned()
            }
            OrganizationConfidence::NeedsReview => {
                "Choose a series title and season before approval".to_owned()
            }
        };
        let nearby_files = collect_nearby_files(
            root,
            base,
            &safe_title,
            season_number,
            &items,
            &published.files_by_id,
            &all_video_paths,
            &selected_nearby,
        )?;
        let mut moves = Vec::new();
        let mut conflicts = Vec::new();
        let mut already_count = 0;
        if let Some(season) = season_number {
            for item in &items {
                let source = published.files_by_id.get(&item.id).ok_or_else(|| {
                    LibraryServerError::new(format!("Missing source path for {}", item.id))
                })?;
                let file_name = source.file_name().ok_or_else(|| {
                    LibraryServerError::new(format!(
                        "Media path has no file name: {}",
                        source.display()
                    ))
                })?;
                let destination_relative = base
                    .join(&safe_title)
                    .join(format!("Season {season}"))
                    .join(file_name);
                let source_relative = relative_wire_path(root, source)?;
                let destination_relative = wire_path(&destination_relative);
                if source_relative.eq_ignore_ascii_case(&destination_relative) {
                    already_count += 1;
                    continue;
                }
                let destination = root.join(path_from_wire(&destination_relative));
                if destination.exists() {
                    conflicts.push(format!(
                        "Destination already exists: {destination_relative}"
                    ));
                }
                moves.push(OrganizationMove {
                    media_id: Some(item.id.clone()),
                    subtitle_id: None,
                    source_relative_path: source_relative,
                    destination_relative_path: destination_relative,
                    size_bytes: item.size_bytes,
                    kind: OrganizationMoveKind::Video,
                    original_series_title: Some(item.series_title.clone()),
                    destination_series_title: Some(series_title.clone()),
                });
            }
            for nearby in nearby_files.iter().filter(|nearby| nearby.selected) {
                let Some(destination_relative_path) = nearby.destination_relative_path.clone()
                else {
                    continue;
                };
                let destination = root.join(path_from_wire(&destination_relative_path));
                if destination.exists() {
                    conflicts.push(format!(
                        "Destination already exists: {destination_relative_path}"
                    ));
                }
                let absolute = root.join(path_from_wire(&nearby.relative_path));
                moves.push(OrganizationMove {
                    media_id: None,
                    subtitle_id: subtitle_ids.get(&normalize_absolute(&absolute)?).cloned(),
                    source_relative_path: nearby.relative_path.clone(),
                    destination_relative_path,
                    size_bytes: nearby.size_bytes,
                    kind: if subtitle_ids.contains_key(&normalize_absolute(&absolute)?) {
                        OrganizationMoveKind::Subtitle
                    } else {
                        OrganizationMoveKind::Nearby
                    },
                    original_series_title: None,
                    destination_series_title: None,
                });
            }
        }
        let already_organized = already_count == items.len();
        let executable = season_number.is_some()
            && !moves.is_empty()
            && conflicts.is_empty()
            && !already_organized;
        batches.push(OrganizationSeriesBatch {
            batch_id,
            series_title,
            season_number,
            confidence,
            reason,
            video_count: items.len(),
            executable,
            already_organized,
            conflicts,
            moves,
            nearby_files,
        });
    }
    batches.sort_by(|left, right| {
        left.series_title
            .to_lowercase()
            .cmp(&right.series_title.to_lowercase())
            .then_with(|| left.batch_id.cmp(&right.batch_id))
    });
    let base_relative_path = wire_path(base);
    let plan_material = format!(
        "{}\n{}\n{}\n{}",
        catalog_revision,
        root.display(),
        base_relative_path,
        serde_json::to_string(&batches.iter().map(|batch| &batch.moves).collect::<Vec<_>>())?
    );
    Ok(OrganizationPlan {
        plan_id: sha256_hex(&plan_material).chars().take(24).collect(),
        catalog_revision: catalog_revision.to_owned(),
        root: root_label,
        base_relative_path,
        batches,
        unassigned_count,
    })
}

#[allow(clippy::too_many_arguments)]
fn collect_nearby_files(
    root: &Path,
    base: &Path,
    safe_title: &str,
    season_number: Option<u32>,
    items: &[&LibraryMediaItem],
    files_by_id: &PathMap,
    all_video_paths: &BTreeSet<PathBuf>,
    selected_nearby: &BTreeSet<String>,
) -> Result<Vec<OrganizationNearbyFile>> {
    let mut parents = BTreeSet::new();
    let mut video_stems = BTreeSet::new();
    for item in items {
        if let Some(path) = files_by_id.get(&item.id) {
            if let Some(parent) = path.parent() {
                parents.insert(parent.to_path_buf());
                if parent
                    .file_name()
                    .is_some_and(|name| looks_like_season_directory(&name.to_string_lossy()))
                    && let Some(series_parent) = parent.parent()
                    && series_parent.starts_with(root)
                {
                    parents.insert(series_parent.to_path_buf());
                }
            }
            if let Some(stem) = path.file_stem() {
                video_stems.insert(stem.to_string_lossy().to_lowercase());
            }
        }
    }
    let normalized_title = normalize_key(safe_title);
    let mut seen = BTreeSet::new();
    let mut nearby = Vec::new();
    for parent in parents {
        let entries = match fs::read_dir(&parent) {
            Ok(entries) => entries,
            Err(_) => continue,
        };
        for entry in entries.flatten() {
            let path = entry.path();
            if !path.is_file() {
                continue;
            }
            let absolute = normalize_absolute(&path)?;
            if all_video_paths.contains(&absolute) {
                continue;
            }
            let extension = extension_lowercase(&path);
            if VIDEO_EXTENSIONS.contains(&extension.as_str()) {
                continue;
            }
            let file_name = path
                .file_name()
                .map(|name| name.to_string_lossy().into_owned())
                .unwrap_or_default();
            let lower_name = file_name.to_lowercase();
            let exact_sidecar = video_stems.iter().any(|stem| lower_name.starts_with(stem))
                && (SUBTITLE_EXTENSIONS.contains(&extension.as_str())
                    || matches!(
                        extension.as_str(),
                        "mka" | "nfo" | "jpg" | "jpeg" | "png" | "webp"
                    ));
            let nested_release = !paths_equal(&parent, root);
            let title_related = normalize_key(&file_name).contains(&normalized_title);
            if !exact_sidecar && !nested_release && !title_related {
                continue;
            }
            let relative_path = relative_wire_path(root, &path)?;
            if !seen.insert(relative_path.clone()) {
                continue;
            }
            let series_asset = is_series_asset(&file_name);
            let destination_relative_path = season_number.map(|season| {
                let destination = if series_asset {
                    base.join(safe_title).join(&file_name)
                } else {
                    base.join(safe_title)
                        .join(format!("Season {season}"))
                        .join(&file_name)
                };
                wire_path(&destination)
            });
            nearby.push(OrganizationNearbyFile {
                relative_path: relative_path.clone(),
                size_bytes: entry.metadata().map(|metadata| metadata.len()).unwrap_or(0),
                recommended: exact_sidecar,
                selected: selected_nearby.contains(&relative_path),
                destination_relative_path,
            });
        }
    }
    nearby.sort_by(|left, right| left.relative_path.cmp(&right.relative_path));
    Ok(nearby)
}

fn preflight_batch(batch: &StoredBatch) -> Result<()> {
    let normalized_root = normalize_absolute(&batch.root)?;
    let mut destinations = BTreeSet::new();
    for operation in &batch.moves {
        let source = normalize_absolute(
            &batch
                .root
                .join(path_from_wire(&operation.source_relative_path)),
        )?;
        let destination = normalize_absolute(
            &batch
                .root
                .join(path_from_wire(&operation.destination_relative_path)),
        )?;
        ensure_within_root(&normalized_root, &source)?;
        ensure_within_root(&normalized_root, &destination)?;
        reject_reparse_ancestors(&normalized_root, &source)?;
        reject_reparse_ancestors(
            &normalized_root,
            destination.parent().unwrap_or(&destination),
        )?;
        if !source.is_file() {
            return Err(LibraryServerError::new(format!(
                "Source file is missing: {}",
                operation.source_relative_path
            )));
        }
        verify_destination(&source, operation.size_bytes)?;
        if destination.exists() {
            return Err(LibraryServerError::new(format!(
                "Destination already exists: {}",
                operation.destination_relative_path
            )));
        }
        let destination_key = destination.to_string_lossy().to_lowercase();
        if !destinations.insert(destination_key) {
            return Err(LibraryServerError::new(format!(
                "Two files target the same destination: {}",
                operation.destination_relative_path
            )));
        }
    }
    Ok(())
}

fn verify_destination(path: &Path, expected_size: u64) -> Result<()> {
    let metadata = path.metadata().map_err(|error| {
        LibraryServerError::with_context(error, format!("failed to verify {}", path.display()))
    })?;
    if !metadata.is_file() || metadata.len() != expected_size {
        return Err(LibraryServerError::new(format!(
            "File verification failed for {}",
            path.display()
        )));
    }
    Ok(())
}

#[cfg(windows)]
fn move_without_overwrite(source: &Path, destination: &Path) -> Result<()> {
    // Windows rename fails when the destination exists, preserving the
    // no-overwrite invariant even if another process races the preflight.
    fs::rename(source, destination).map_err(|error| {
        LibraryServerError::with_context(
            error,
            format!(
                "failed to move {} to {}",
                source.display(),
                destination.display()
            ),
        )
    })
}

#[cfg(not(windows))]
fn move_without_overwrite(source: &Path, destination: &Path) -> Result<()> {
    // POSIX rename may replace an existing destination. A same-filesystem
    // hard link provides create-new semantics; removing the source completes
    // the move without an overwrite window.
    fs::hard_link(source, destination).map_err(|error| {
        LibraryServerError::with_context(
            error,
            format!(
                "failed to reserve destination {} for {}",
                destination.display(),
                source.display()
            ),
        )
    })?;
    if let Err(error) = fs::remove_file(source) {
        let cleanup = fs::remove_file(destination);
        return Err(match cleanup {
            Ok(()) => LibraryServerError::with_context(
                error,
                format!("failed to remove moved source {}", source.display()),
            ),
            Err(cleanup_error) => LibraryServerError::new(format!(
                "failed to remove moved source {} ({error}); destination cleanup also failed: {cleanup_error}",
                source.display()
            )),
        });
    }
    Ok(())
}

fn rollback_transaction(active: &JournalTransaction) -> Result<()> {
    for operation in active.batch.moves.iter().take(active.moved_count).rev() {
        let original = active
            .batch
            .root
            .join(path_from_wire(&operation.source_relative_path));
        let moved = active
            .batch
            .root
            .join(path_from_wire(&operation.destination_relative_path));
        if original.exists() {
            return Err(LibraryServerError::new(format!(
                "Cannot roll back because the original path is occupied: {}",
                original.display()
            )));
        }
        if !moved.is_file() {
            return Err(LibraryServerError::new(format!(
                "Cannot roll back because the moved file is missing: {}",
                moved.display()
            )));
        }
        if let Some(parent) = original.parent() {
            fs::create_dir_all(parent)?;
        }
        move_without_overwrite(&moved, &original)?;
    }
    Ok(())
}

fn apply_moves_to_stored(
    stored: &mut HeadlessStoredLibrary,
    root: &Path,
    moves: &[OrganizationMove],
) -> Result<()> {
    for operation in moves {
        let destination = root.join(path_from_wire(&operation.destination_relative_path));
        if let Some(media_id) = &operation.media_id {
            stored
                .published_library
                .files_by_id
                .insert(media_id.clone(), destination.clone());
            let item = stored
                .published_library
                .catalog
                .items
                .iter_mut()
                .find(|item| item.id == *media_id)
                .ok_or_else(|| LibraryServerError::new(format!("Unknown media ID {media_id}")))?;
            item.relative_path = operation.destination_relative_path.clone();
            if let Some(series_title) = &operation.destination_series_title {
                item.series_title = series_title.clone();
            }
            if let Some(stem) = destination.file_stem() {
                item.episode_title = stem.to_string_lossy().into_owned();
            }
        }
        if let Some(subtitle_id) = &operation.subtitle_id {
            stored
                .published_library
                .subtitle_files_by_id
                .insert(subtitle_id.clone(), destination);
            for item in &mut stored.published_library.catalog.items {
                if let Some(track) = item
                    .subtitles
                    .iter_mut()
                    .find(|track| track.id == *subtitle_id)
                {
                    track.relative_path = operation.destination_relative_path.clone();
                }
            }
        }
    }
    stored
        .published_library
        .catalog
        .items
        .sort_by(|left, right| left.relative_path.cmp(&right.relative_path));
    Ok(())
}

fn recover_interrupted(
    catalog_store: &CatalogStore,
    journal: &mut OrganizationJournal,
) -> Option<String> {
    let mut active = journal.active.clone()?;
    let mut observed_moved_count = 0;
    for operation in &active.batch.moves {
        let source = active
            .batch
            .root
            .join(path_from_wire(&operation.source_relative_path));
        let destination = active
            .batch
            .root
            .join(path_from_wire(&operation.destination_relative_path));
        match (source.is_file(), destination.is_file()) {
            (false, true) => observed_moved_count += 1,
            (true, false) => break,
            (true, true) => {
                return Some(format!(
                    "Organizer recovery found both source and destination: {}",
                    operation.source_relative_path
                ));
            }
            (false, false) => {
                return Some(format!(
                    "Organizer recovery could not find source or destination: {}",
                    operation.source_relative_path
                ));
            }
        }
    }
    active.moved_count = observed_moved_count;
    let catalog_has_destinations = catalog_store.load().ok().flatten().is_some_and(|stored| {
        active
            .batch
            .moves
            .iter()
            .filter_map(|operation| operation.media_id.as_ref().map(|id| (id, operation)))
            .all(|(id, operation)| {
                stored.published_library.catalog.items.iter().any(|item| {
                    item.id == *id && item.relative_path == operation.destination_relative_path
                })
            })
    });
    if catalog_has_destinations && observed_moved_count == active.batch.moves.len() {
        if active.undo {
            let original_id = active.batch.batch_id.trim_start_matches("undo-");
            journal
                .completed
                .retain(|entry| entry.batch.batch_id != original_id);
        } else {
            journal.completed.push(CompletedBatch {
                completed_batch_id: format!("{}-recovered", active.batch.batch_id),
                batch: active.batch,
                completed_at_epoch_ms: current_epoch_ms(),
            });
        }
        journal.active = None;
        return None;
    }
    match rollback_transaction(&active) {
        Ok(()) => {
            journal.active = None;
            None
        }
        Err(error) => Some(error.to_string()),
    }
}

fn reverse_move(mut operation: OrganizationMove) -> OrganizationMove {
    std::mem::swap(
        &mut operation.source_relative_path,
        &mut operation.destination_relative_path,
    );
    std::mem::swap(
        &mut operation.original_series_title,
        &mut operation.destination_series_title,
    );
    operation
}

fn running_status(batch: &StoredBatch, undo: bool) -> OrganizationStatus {
    OrganizationStatus {
        state: OrganizationState::Running,
        batch_id: Some(batch.batch_id.clone()),
        series_title: Some(batch.series_title.clone()),
        completed_operations: 0,
        total_operations: batch.moves.len(),
        message: Some(if undo {
            "Restoring the approved series to its original paths.".to_owned()
        } else {
            "Moving the approved series.".to_owned()
        }),
        last_completed_batch_id: None,
        can_undo: false,
    }
}

fn catalog_revision(catalog: &LibraryCatalog) -> String {
    let material = catalog
        .items
        .iter()
        .map(|item| format!("{}:{}:{}", item.id, item.relative_path, item.size_bytes))
        .collect::<Vec<_>>()
        .join("\n");
    sha256_hex(&format!("{}\n{material}", catalog.indexed_at_epoch_ms))
        .chars()
        .take(24)
        .collect()
}

fn validate_relative_directory(value: &str) -> Result<PathBuf> {
    let trimmed = value.trim().trim_end_matches(['/', '\\']);
    if trimmed.is_empty() {
        return Ok(PathBuf::new());
    }
    if trimmed.starts_with(['/', '\\']) {
        return Err(LibraryServerError::new(
            "The destination base must be a relative folder inside the selected root.",
        ));
    }

    let mut path = PathBuf::new();
    for component in trimmed.split(['/', '\\']).filter(|value| !value.is_empty()) {
        let mut components = Path::new(component).components();
        if !matches!(components.next(), Some(Component::Normal(_)))
            || components.next().is_some()
            || (component.len() == 2
                && component.as_bytes()[0].is_ascii_alphabetic()
                && component.ends_with(':'))
        {
            return Err(LibraryServerError::new(
                "The destination base must be a relative folder inside the selected root.",
            ));
        }
        path.push(sanitize_component(component)?);
    }
    Ok(path)
}

fn sanitize_component(value: &str) -> Result<String> {
    let value = value.trim().trim_end_matches(['.', ' ']);
    if value.is_empty() {
        return Err(LibraryServerError::new("Folder names cannot be empty."));
    }
    let mut sanitized = String::new();
    for character in value.chars() {
        sanitized.push(match character {
            '\\' => '＼',
            '/' => '／',
            ':' => '：',
            '*' => '＊',
            '?' => '？',
            '"' => '＂',
            '<' => '＜',
            '>' => '＞',
            '|' => '｜',
            character if character.is_control() => '＿',
            character => character,
        });
    }
    if sanitized.encode_utf16().count() > 120 {
        return Err(LibraryServerError::new(
            "The generated series folder name is longer than 120 characters.",
        ));
    }
    let reserved = sanitized
        .split('.')
        .next()
        .unwrap_or_default()
        .to_ascii_uppercase();
    if matches!(reserved.as_str(), "CON" | "PRN" | "AUX" | "NUL")
        || reserved
            .strip_prefix("COM")
            .or_else(|| reserved.strip_prefix("LPT"))
            .is_some_and(|number| {
                number
                    .parse::<u8>()
                    .is_ok_and(|number| (1..=9).contains(&number))
            })
    {
        sanitized.insert(0, '_');
    }
    Ok(sanitized)
}

fn reject_reparse_ancestors(root: &Path, path: &Path) -> Result<()> {
    let relative = path.strip_prefix(root).map_err(|_| {
        LibraryServerError::new("An organization path escaped the selected library root.")
    })?;
    let mut current = root.to_path_buf();
    for component in relative.components() {
        current.push(component.as_os_str());
        if !current.exists() {
            break;
        }
        let metadata = fs::symlink_metadata(&current)?;
        if metadata.file_type().is_symlink() {
            return Err(LibraryServerError::new(format!(
                "Organization does not follow symbolic links or junctions: {}",
                current.display()
            )));
        }
        #[cfg(windows)]
        {
            use std::os::windows::fs::MetadataExt;
            const FILE_ATTRIBUTE_REPARSE_POINT: u32 = 0x0400;
            if metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT != 0 {
                return Err(LibraryServerError::new(format!(
                    "Organization does not follow symbolic links or junctions: {}",
                    current.display()
                )));
            }
        }
    }
    Ok(())
}

fn ensure_within_root(root: &Path, path: &Path) -> Result<()> {
    if path.strip_prefix(root).is_err() {
        return Err(LibraryServerError::new(
            "An organization path escaped the selected library root.",
        ));
    }
    Ok(())
}

fn normalize_absolute(path: &Path) -> Result<PathBuf> {
    let absolute = if path.is_absolute() {
        path.to_path_buf()
    } else {
        std::env::current_dir()?.join(path)
    };
    let mut normalized = PathBuf::new();
    for component in absolute.components() {
        match component {
            Component::CurDir => {}
            Component::ParentDir => {
                normalized.pop();
            }
            _ => normalized.push(component.as_os_str()),
        }
    }
    Ok(normalized)
}

fn paths_equal(left: &Path, right: &Path) -> bool {
    left.to_string_lossy()
        .eq_ignore_ascii_case(&right.to_string_lossy())
}

fn relative_wire_path(root: &Path, path: &Path) -> Result<String> {
    let path = normalize_absolute(path)?;
    let root = normalize_absolute(root)?;
    let relative = path.strip_prefix(&root).map_err(|_| {
        LibraryServerError::new(format!("{} is outside {}", path.display(), root.display()))
    })?;
    Ok(wire_path(relative))
}

fn wire_path(path: &Path) -> String {
    path.components()
        .filter_map(|component| match component {
            Component::Normal(value) => Some(value.to_string_lossy()),
            _ => None,
        })
        .collect::<Vec<_>>()
        .join("/")
}

fn path_from_wire(value: &str) -> PathBuf {
    value
        .split(['/', '\\'])
        .filter(|part| !part.is_empty())
        .collect()
}

fn normalize_key(value: &str) -> String {
    value
        .chars()
        .filter(|character| character.is_alphanumeric())
        .flat_map(char::to_lowercase)
        .collect()
}

fn extension_lowercase(path: &Path) -> String {
    path.extension()
        .map(|value| value.to_string_lossy().to_lowercase())
        .unwrap_or_default()
}

fn is_series_asset(name: &str) -> bool {
    let lower = name.to_ascii_lowercase();
    matches!(
        lower.as_str(),
        "poster.jpg" | "poster.png" | "fanart.jpg" | "fanart.png" | "clearlogo.png" | "tvshow.nfo"
    )
}

fn looks_like_season_directory(value: &str) -> bool {
    value
        .trim()
        .to_ascii_lowercase()
        .strip_prefix("season ")
        .is_some_and(|number| number.parse::<u32>().is_ok())
}

fn load_journal(file: &Path) -> Result<OrganizationJournal> {
    let body = match fs::read_to_string(file) {
        Ok(body) => body,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
            return Ok(OrganizationJournal::default());
        }
        Err(error) => {
            return Err(LibraryServerError::with_context(
                error,
                format!("failed to read {}", file.display()),
            ));
        }
    };
    let journal = serde_json::from_str::<OrganizationJournal>(&body).map_err(|error| {
        LibraryServerError::with_context(error, format!("failed to parse {}", file.display()))
    })?;
    if journal.schema_version != JOURNAL_SCHEMA_VERSION {
        return Err(LibraryServerError::new(format!(
            "unsupported organization journal schema {} in {}",
            journal.schema_version,
            file.display()
        )));
    }
    Ok(journal)
}

fn write_json_atomically<T: Serialize>(file: &Path, value: &T) -> Result<()> {
    if let Some(parent) = file.parent() {
        fs::create_dir_all(parent)?;
    }
    let file_name = file
        .file_name()
        .map(|name| name.to_string_lossy().into_owned())
        .ok_or_else(|| LibraryServerError::new("Journal path must include a file name."))?;
    let temporary = file.with_file_name(format!("{file_name}.tmp"));
    fs::write(&temporary, serde_json::to_string_pretty(value)?)?;
    fs::rename(&temporary, file)?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use std::sync::atomic::{AtomicU64, Ordering};

    use crate::catalog::{LibraryCatalog, LibraryItemMetadataStatus, LibraryMediaItem};
    use crate::scanner::scan_roots;

    use super::*;

    static TEMP_COUNTER: AtomicU64 = AtomicU64::new(0);

    #[test]
    fn relative_directory_uses_portable_separators_and_rejects_roots() {
        assert_eq!(
            PathBuf::from("Anime").join("Current Season"),
            validate_relative_directory("Anime/Current Season").expect("forward slash path")
        );
        assert_eq!(
            PathBuf::from("Anime").join("Current Season"),
            validate_relative_directory(r"Anime\Current Season").expect("backslash path")
        );
        assert_eq!(
            PathBuf::from("Anime： Shows").join("Current Season"),
            validate_relative_directory("Anime: Shows/Current Season")
                .expect("sanitized component")
        );

        for invalid in ["/Anime", r"\Anime", "../Anime", r"C:\Anime"] {
            assert!(
                validate_relative_directory(invalid).is_err(),
                "{invalid} must remain relative"
            );
        }
    }

    #[test]
    fn invalid_journals_require_recovery_without_overwriting_evidence() {
        for body in ["{not-json", "{}", r#"{"schemaVersion":99}"#] {
            let fixture = fixture();
            fs::create_dir_all(&fixture.data).expect("data directory creates");
            let journal_file = fixture.data.join("library-organization.json");
            fs::write(&journal_file, body).expect("invalid journal writes");

            let organizer = LibraryOrganizer::new(
                vec![fixture.root.clone()],
                CatalogStore::new(fixture.data.join("catalog.json")),
            );

            assert_eq!(
                OrganizationState::RecoveryRequired,
                organizer.status().state
            );
            assert!(
                organizer
                    .preview(
                        &fixture.published,
                        OrganizationPreviewRequest {
                            root: fixture.root.display().to_string(),
                            base_relative_path: String::new(),
                            overrides: Vec::new(),
                        },
                    )
                    .is_err()
            );
            assert_eq!(
                body,
                fs::read_to_string(&journal_file).expect("journal evidence remains")
            );
            cleanup(fixture.temp);
        }
    }

    #[test]
    fn initial_journal_write_failure_never_leaves_running_status_or_moves_files() {
        let fixture = fixture();
        let mut organizer = LibraryOrganizer::new(
            vec![fixture.root.clone()],
            CatalogStore::new(fixture.data.join("catalog.json")),
        );
        let batch = fixture_batch(&fixture);
        organizer.runtime.lock().expect("organizer lock").status = running_status(&batch, false);
        let blocked_parent = fixture.data.join("journal-parent-is-a-file");
        fs::write(&blocked_parent, b"blocked").expect("journal parent blocker writes");
        organizer.journal_file = blocked_parent.join("library-organization.json");

        let result = organizer.execute(PreparedOrganization { batch, undo: false });

        assert!(result.is_err());
        assert_eq!(
            OrganizationState::RecoveryRequired,
            organizer.status().state
        );
        assert!(fixture.root.join("[Group] Example Show - 01.mkv").is_file());
        assert!(
            !fixture
                .root
                .join("Example Show/Season 1/[Group] Example Show - 01.mkv")
                .exists()
        );
        cleanup(fixture.temp);
    }

    #[test]
    fn startup_recovery_rolls_back_a_partially_moved_batch() {
        let fixture = fixture();
        fs::create_dir_all(&fixture.data).expect("data directory creates");
        let batch = fixture_batch(&fixture);
        let source = fixture.root.join(&batch.moves[0].source_relative_path);
        let destination = fixture
            .root
            .join(path_from_wire(&batch.moves[0].destination_relative_path));
        fs::create_dir_all(destination.parent().expect("destination parent"))
            .expect("destination parent creates");
        let journal = OrganizationJournal {
            active: Some(JournalTransaction {
                batch,
                moved_count: 1,
                undo: false,
            }),
            ..OrganizationJournal::default()
        };
        write_json_atomically(&fixture.data.join("library-organization.json"), &journal)
            .expect("journal writes before move");
        move_without_overwrite(&source, &destination).expect("fixture move succeeds");

        let organizer = LibraryOrganizer::new(
            vec![fixture.root.clone()],
            CatalogStore::new(fixture.data.join("catalog.json")),
        );

        assert_eq!(OrganizationState::Idle, organizer.status().state);
        assert!(source.is_file());
        assert!(!destination.exists());
        cleanup(fixture.temp);
    }

    #[test]
    fn preview_requires_review_then_builds_exact_series_manifest() {
        let fixture = fixture();
        let organizer = LibraryOrganizer::new(
            vec![fixture.root.clone()],
            CatalogStore::new(fixture.data.join("catalog.json")),
        );
        let first = organizer
            .preview(
                &fixture.published,
                OrganizationPreviewRequest {
                    root: fixture.root.display().to_string(),
                    base_relative_path: "Anime".to_owned(),
                    overrides: Vec::new(),
                },
            )
            .expect("preview");
        assert_eq!(1, first.batches.len());
        assert!(!first.batches[0].executable);
        assert_eq!(2, first.unassigned_count);

        let second = organizer
            .preview(
                &fixture.published,
                OrganizationPreviewRequest {
                    root: fixture.root.display().to_string(),
                    base_relative_path: "Anime".to_owned(),
                    overrides: vec![OrganizationSeriesOverride {
                        batch_id: first.batches[0].batch_id.clone(),
                        series_title: "Example Show".to_owned(),
                        season_number: 1,
                        included_nearby_paths: Vec::new(),
                    }],
                },
            )
            .expect("reviewed preview");
        assert!(second.batches[0].executable);
        assert_eq!(
            "Anime/Example Show/Season 1/[Group] Example Show - 01.mkv",
            second.batches[0].moves[0].destination_relative_path
        );
        cleanup(fixture.temp);
    }

    #[test]
    fn execute_preserves_media_ids_and_undo_restores_original_paths() {
        let fixture = fixture();
        let store = CatalogStore::new(fixture.data.join("catalog.json"));
        store
            .save(fixture.published.clone())
            .expect("catalog writes");
        let organizer = LibraryOrganizer::new(vec![fixture.root.clone()], store.clone());
        let initial = organizer
            .preview(
                &fixture.published,
                OrganizationPreviewRequest {
                    root: fixture.root.display().to_string(),
                    base_relative_path: String::new(),
                    overrides: Vec::new(),
                },
            )
            .expect("preview");
        let reviewed = organizer
            .preview(
                &fixture.published,
                OrganizationPreviewRequest {
                    root: fixture.root.display().to_string(),
                    base_relative_path: String::new(),
                    overrides: vec![OrganizationSeriesOverride {
                        batch_id: initial.batches[0].batch_id.clone(),
                        series_title: "Example Show".to_owned(),
                        season_number: 1,
                        included_nearby_paths: Vec::new(),
                    }],
                },
            )
            .expect("reviewed preview");
        let batch = &reviewed.batches[0];
        let prepared = organizer
            .prepare_execute(
                &fixture.published.catalog,
                OrganizationExecuteRequest {
                    plan_id: reviewed.plan_id.clone(),
                    batch_id: batch.batch_id.clone(),
                    expected_moves: batch.moves.clone(),
                },
            )
            .expect("prepare");
        let updated = organizer.execute(prepared).expect("execute");
        assert!(
            fixture
                .root
                .join("Example Show/Season 1/[Group] Example Show - 01.mkv")
                .is_file()
        );
        assert_eq!("one", updated.catalog.items[0].id);
        let previous = store.load().expect("catalog loads");
        let rescanned = scan_roots(&[fixture.root.clone()], previous.as_ref()).expect("rescans");
        assert!(
            rescanned
                .published_library
                .catalog
                .items
                .iter()
                .any(|item| item.id == "one" && item.series_title == "Example Show")
        );
        let completed = organizer
            .status()
            .last_completed_batch_id
            .expect("completed batch");
        let undo = organizer.prepare_undo(&completed).expect("undo prepares");
        organizer.execute(undo).expect("undo executes");
        assert!(fixture.root.join("[Group] Example Show - 01.mkv").is_file());
        assert!(fixture.root.join("Example Show/Season 1").is_dir());
        cleanup(fixture.temp);
    }

    #[test]
    fn conflicts_never_overwrite_existing_destinations() {
        let fixture = fixture();
        let destination = fixture.root.join("Example Show/Season 1");
        fs::create_dir_all(&destination).expect("destination creates");
        fs::write(
            destination.join("[Group] Example Show - 01.mkv"),
            b"different",
        )
        .expect("conflict writes");
        let organizer = LibraryOrganizer::new(
            vec![fixture.root.clone()],
            CatalogStore::new(fixture.data.join("catalog.json")),
        );
        let first = organizer
            .preview(
                &fixture.published,
                OrganizationPreviewRequest {
                    root: fixture.root.display().to_string(),
                    base_relative_path: String::new(),
                    overrides: Vec::new(),
                },
            )
            .expect("preview");
        let reviewed = organizer
            .preview(
                &fixture.published,
                OrganizationPreviewRequest {
                    root: fixture.root.display().to_string(),
                    base_relative_path: String::new(),
                    overrides: vec![OrganizationSeriesOverride {
                        batch_id: first.batches[0].batch_id.clone(),
                        series_title: "Example Show".to_owned(),
                        season_number: 1,
                        included_nearby_paths: Vec::new(),
                    }],
                },
            )
            .expect("reviewed preview");
        assert!(!reviewed.batches[0].executable);
        assert!(!reviewed.batches[0].conflicts.is_empty());
        assert_eq!(
            b"different".to_vec(),
            fs::read(destination.join("[Group] Example Show - 01.mkv")).expect("read")
        );
        cleanup(fixture.temp);
    }

    #[test]
    fn exact_approval_is_required_and_cancellation_keeps_sources() {
        let fixture = fixture();
        let store = CatalogStore::new(fixture.data.join("catalog.json"));
        store
            .save(fixture.published.clone())
            .expect("catalog writes");
        let organizer = LibraryOrganizer::new(vec![fixture.root.clone()], store);
        let first = organizer
            .preview(
                &fixture.published,
                OrganizationPreviewRequest {
                    root: fixture.root.display().to_string(),
                    base_relative_path: String::new(),
                    overrides: Vec::new(),
                },
            )
            .expect("preview");
        let reviewed = organizer
            .preview(
                &fixture.published,
                OrganizationPreviewRequest {
                    root: fixture.root.display().to_string(),
                    base_relative_path: String::new(),
                    overrides: vec![OrganizationSeriesOverride {
                        batch_id: first.batches[0].batch_id.clone(),
                        series_title: "Example Show".to_owned(),
                        season_number: 1,
                        included_nearby_paths: Vec::new(),
                    }],
                },
            )
            .expect("reviewed preview");
        let batch = &reviewed.batches[0];
        let mut changed_moves = batch.moves.clone();
        changed_moves[0].destination_relative_path = "unexpected.mkv".to_owned();
        assert!(
            organizer
                .prepare_execute(
                    &fixture.published.catalog,
                    OrganizationExecuteRequest {
                        plan_id: reviewed.plan_id.clone(),
                        batch_id: batch.batch_id.clone(),
                        expected_moves: changed_moves,
                    },
                )
                .is_err()
        );

        let prepared = organizer
            .prepare_execute(
                &fixture.published.catalog,
                OrganizationExecuteRequest {
                    plan_id: reviewed.plan_id.clone(),
                    batch_id: batch.batch_id.clone(),
                    expected_moves: batch.moves.clone(),
                },
            )
            .expect("exact approval prepares");
        organizer.cancel();
        assert!(organizer.execute(prepared).is_err());
        assert!(fixture.root.join("[Group] Example Show - 01.mkv").is_file());
        assert_eq!(OrganizationState::Cancelled, organizer.status().state);
        cleanup(fixture.temp);
    }

    struct Fixture {
        temp: PathBuf,
        root: PathBuf,
        data: PathBuf,
        published: PublishedLibrary,
    }

    fn fixture_batch(fixture: &Fixture) -> StoredBatch {
        let source_relative_path = "[Group] Example Show - 01.mkv".to_owned();
        StoredBatch {
            batch_id: "fixture-batch".to_owned(),
            series_title: "Example Show".to_owned(),
            root: fixture.root.clone(),
            moves: vec![OrganizationMove {
                media_id: Some("one".to_owned()),
                subtitle_id: None,
                source_relative_path,
                destination_relative_path: "Example Show/Season 1/[Group] Example Show - 01.mkv"
                    .to_owned(),
                size_bytes: 3,
                kind: OrganizationMoveKind::Video,
                original_series_title: Some("Example Show".to_owned()),
                destination_series_title: Some("Example Show".to_owned()),
            }],
        }
    }

    fn fixture() -> Fixture {
        let temp = std::env::temp_dir().join(format!(
            "danmaku-organizer-{}-{}",
            std::process::id(),
            TEMP_COUNTER.fetch_add(1, Ordering::Relaxed)
        ));
        let _ = fs::remove_dir_all(&temp);
        let root = temp.join("Anime");
        let data = temp.join("data");
        fs::create_dir_all(&root).expect("root creates");
        let first = root.join("[Group] Example Show - 01.mkv");
        let second = root.join("[Group] Example Show - 02.mkv");
        fs::write(&first, b"one").expect("media one");
        fs::write(&second, b"two").expect("media two");
        let item = |id: &str, path: &Path| LibraryMediaItem {
            id: id.to_owned(),
            series_title: "Example Show".to_owned(),
            episode_title: path.file_stem().unwrap().to_string_lossy().into_owned(),
            relative_path: path.file_name().unwrap().to_string_lossy().into_owned(),
            size_bytes: path.metadata().expect("metadata").len(),
            media_type: "video/x-matroska".to_owned(),
            stream_path: format!("/media/{id}"),
            indexed_at_epoch_ms: 1,
            subtitles: Vec::new(),
            poster_path: None,
            root_label: Some(root.display().to_string()),
            anime_metadata: None,
            metadata_status: LibraryItemMetadataStatus::NotAvailable,
        };
        let items = vec![item("one", &first), item("two", &second)];
        let files_by_id = BTreeMap::from([("one".to_owned(), first), ("two".to_owned(), second)]);
        Fixture {
            temp,
            root,
            data,
            published: PublishedLibrary {
                catalog: LibraryCatalog {
                    root_name: "Anime".to_owned(),
                    indexed_at_epoch_ms: 1,
                    items,
                },
                files_by_id,
                subtitle_files_by_id: BTreeMap::new(),
                poster_files_by_id: BTreeMap::new(),
            },
        }
    }

    fn cleanup(path: PathBuf) {
        fs::remove_dir_all(path).expect("fixture deletes");
    }
}
