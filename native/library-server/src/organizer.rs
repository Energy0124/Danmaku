use std::collections::{BTreeMap, BTreeSet};
use std::fs;
use std::path::{Component, Path, PathBuf};
use std::sync::Mutex;
use std::sync::atomic::{AtomicBool, Ordering};

use serde::{Deserialize, Serialize};

use crate::catalog::{
    CatalogStore, HeadlessStoredLibrary, LibraryCatalog, PublishedLibrary, current_epoch_ms,
};
use crate::hash::sha256_hex;
use crate::scanner::find_season_number;
use crate::{LibraryServerError, Result};

pub mod draft;
mod transfer;
use draft::{IdentificationStatus, OrganizationDraft};

const JOURNAL_SCHEMA_VERSION: u32 = 2;
const COMPLETED_HISTORY_LIMIT: usize = 20;
const VIDEO_EXTENSIONS: &[&str] = &["mkv", "mp4", "m4v", "webm", "ts", "m2ts", "avi", "mov"];
const SUBTITLE_EXTENSIONS: &[&str] = &["ass", "ssa", "srt", "vtt", "sub"];

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct OrganizationPlan {
    pub draft_id: String,
    pub draft_revision: u64,
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
    #[serde(default)]
    pub source_signature: Option<String>,
    #[serde(default)]
    pub content_hash: Option<String>,
    #[serde(default)]
    pub source_root: Option<PathBuf>,
    #[serde(default)]
    pub destination_root: Option<PathBuf>,
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

impl OrganizationMove {
    fn source(&self, legacy_root: &Path) -> PathBuf {
        self.source_root
            .as_deref()
            .unwrap_or(legacy_root)
            .join(path_from_wire(&self.source_relative_path))
    }
    fn destination(&self, legacy_root: &Path) -> PathBuf {
        self.destination_root
            .as_deref()
            .unwrap_or(legacy_root)
            .join(path_from_wire(&self.destination_relative_path))
    }
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct OrganizationNearbyFile {
    pub owner_media_ids: Vec<String>,
    pub owner_media_id: Option<String>,
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
    pub completed_bytes: u64,
    pub total_bytes: u64,
    pub draft: Option<OrganizationDraft>,
    pub identification: IdentificationStatus,
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
    identification_cancel: AtomicBool,
    desktop_token: Mutex<Option<DesktopToken>>,
    #[cfg(test)]
    force_copy: AtomicBool,
    #[cfg(test)]
    transfer_failpoint: Mutex<Option<String>>,
    #[cfg(test)]
    available_space: Mutex<Option<u64>>,
}

struct DesktopToken(String);
impl std::fmt::Debug for DesktopToken {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str("DesktopToken([redacted])")
    }
}

#[derive(Debug, Default)]
struct OrganizerRuntime {
    plans: BTreeMap<String, StoredPlan>,
    status: OrganizationStatus,
    journal: OrganizationJournal,
    draft: Option<OrganizationDraft>,
    draft_loaded: bool,
    identification: IdentificationStatus,
}

#[derive(Debug, Clone)]
struct StoredPlan {
    catalog_revision: String,
    root: PathBuf,
    batches: BTreeMap<String, StoredBatch>,
    draft_revision: Option<(String, u64)>,
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
    #[serde(default)]
    catalog_commit_revision: Option<String>,
    #[serde(default)]
    transfers: Vec<transfer::TransferRecord>,
    #[serde(default)]
    catalog_committed: bool,
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
        if journal_loaded
            && let Some(error) = recover_interrupted(&catalog_store, &mut journal, &roots)
        {
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
                ..Default::default()
            }),
            cancel_requested: AtomicBool::new(false),
            identification_cancel: AtomicBool::new(false),
            desktop_token: Mutex::new(None),
            #[cfg(test)]
            force_copy: AtomicBool::new(false),
            #[cfg(test)]
            transfer_failpoint: Mutex::new(None),
            #[cfg(test)]
            available_space: Mutex::new(None),
        };
        if journal_loaded && let Err(error) = organizer.persist_journal() {
            organizer.finish_failed(
                format!("The organization journal is unavailable: {error}"),
                true,
            );
        }
        organizer
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
        if let Some((id, revision)) = &plan.draft_revision {
            if runtime
                .draft
                .as_ref()
                .is_none_or(|d| &d.id != id || d.revision != *revision)
            {
                return Err(LibraryServerError::new(
                    "The review changed; preview again.",
                ));
            }
        }
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
        if let Err(error) = self.execute_transfer(&batch, prepared.undo) {
            let active = self
                .runtime
                .lock()
                .expect("organizer lock")
                .journal
                .active
                .clone();
            if let Some(active) = active {
                self.runtime.lock().expect("organizer lock").status.state =
                    OrganizationState::RollingBack;
                if let Err(recovery) = self.rollback_transfer(active) {
                    self.finish_failed(format!("{error}; recovery: {recovery}"), true);
                    return Err(recovery);
                }
            }
            self.finish_failed(error.to_string(), false);
            if self.cancel_requested.load(Ordering::Acquire) {
                self.runtime.lock().expect("organizer lock").status.state =
                    OrganizationState::Cancelled;
            }
            return Err(error);
        }
        let recorded_batch = self
            .runtime
            .lock()
            .expect("organizer lock")
            .journal
            .active
            .as_ref()
            .unwrap()
            .batch
            .clone();
        let updated = match self.apply_catalog_moves(&recorded_batch) {
            Ok(updated) => updated,
            Err(error) => {
                let active = self
                    .runtime
                    .lock()
                    .expect("organizer lock")
                    .journal
                    .active
                    .clone()
                    .unwrap();
                if let Err(recovery) = self.rollback_transfer(active) {
                    self.finish_failed(recovery.to_string(), true);
                    return Err(recovery);
                }
                self.finish_failed(error.to_string(), false);
                return Err(error);
            }
        };
        self.transfer_checkpoint("catalog-committed")?;
        self.runtime
            .lock()
            .expect("organizer lock")
            .journal
            .active
            .as_mut()
            .unwrap()
            .catalog_committed = true;
        if let Err(error) = self
            .persist_journal()
            .and_then(|_| self.finish_completed(recorded_batch, prepared.undo))
        {
            self.finish_failed(error.to_string(), true);
            return Err(error);
        }
        Ok(updated.published_library)
    }

    pub fn cancel(&self) {
        self.cancel_requested.store(true, Ordering::Release);
    }

    pub fn desktop_token(&self) -> Result<String> {
        let mut token = self.desktop_token.lock().expect("desktop token lock");
        if token.is_none() {
            *token = Some(DesktopToken(draft::unique_id()?));
        }
        Ok(token.as_ref().unwrap().0.clone())
    }

    pub fn recovery_required(&self) -> bool {
        self.runtime.lock().expect("organizer lock").status.state
            == OrganizationState::RecoveryRequired
    }

    pub fn desktop_authorized(&self, supplied: Option<&str>) -> bool {
        let token = self.desktop_token.lock().expect("desktop token lock");
        token.as_ref().is_some_and(|token| {
            supplied.is_some_and(|value| value.strip_prefix("Bearer ") == Some(token.0.as_str()))
        })
    }

    pub fn status(&self) -> OrganizationStatus {
        let draft = self.draft();
        let mut status = self
            .runtime
            .lock()
            .expect("organizer lock should not poison")
            .status
            .clone();
        match draft {
            Ok(draft) => status.draft = draft,
            Err(error) => status.message = Some(error.to_string()),
        }
        status.identification = self.identification_status();
        status
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
        stored.saved_at_epoch_ms =
            current_epoch_ms().max(stored.saved_at_epoch_ms.saturating_add(1));
        stored.published_library.catalog.indexed_at_epoch_ms = stored.saved_at_epoch_ms;
        if let Some(active) = self
            .runtime
            .lock()
            .expect("organizer lock")
            .journal
            .active
            .as_mut()
        {
            active.catalog_commit_revision =
                Some(catalog_revision(&stored.published_library.catalog));
        }
        self.persist_journal()?;
        self.catalog_store.save_stored(&stored)?;
        Ok(stored)
    }

    fn finish_completed(&self, batch: StoredBatch, undo: bool) -> Result<()> {
        let mut runtime = self.runtime.lock().expect("organizer lock");
        let mut journal = runtime.journal.clone();
        journal.active = None;
        if undo {
            journal
                .completed
                .retain(|entry| entry.batch.batch_id != batch.batch_id.trim_start_matches("undo-"));
        } else {
            journal.completed.push(CompletedBatch {
                completed_batch_id: format!("{}-{}", batch.batch_id, current_epoch_ms()),
                batch: batch.clone(),
                completed_at_epoch_ms: current_epoch_ms(),
            });
            if journal.completed.len() > COMPLETED_HISTORY_LIMIT {
                journal
                    .completed
                    .drain(0..journal.completed.len() - COMPLETED_HISTORY_LIMIT);
            }
        }
        // Do not retire the active transaction in memory until its completion is durable.
        write_json_atomically(&self.journal_file, &journal)?;
        runtime.journal = journal;
        runtime.status.state = OrganizationState::Completed;
        runtime.status.completed_operations = batch.moves.len();
        runtime.status.total_operations = batch.moves.len();
        runtime.status.completed_bytes = batch.moves.iter().map(|m| m.size_bytes).sum();
        runtime.status.can_undo = !runtime.journal.completed.is_empty();
        runtime.status.last_completed_batch_id = runtime
            .journal
            .completed
            .last()
            .map(|entry| entry.completed_batch_id.clone());
        runtime.status.message = Some(
            if undo {
                "The series was restored to its original paths."
            } else {
                "The series was moved and verified."
            }
            .into(),
        );
        let completed_id = runtime.status.last_completed_batch_id.clone();
        if let Some(draft) = runtime.draft.as_mut() {
            if undo {
                draft
                    .completed
                    .remove(batch.batch_id.trim_start_matches("undo-"));
                for operation in &batch.moves {
                    if let Some(file) = draft
                        .files
                        .iter_mut()
                        .find(|f| Some(&f.media_id) == operation.media_id.as_ref())
                    {
                        file.source_signature =
                            transfer::signature(&operation.destination(&batch.root))?;
                    }
                }
            } else if draft.files.iter().any(|f| f.group_id == batch.batch_id) {
                draft
                    .completed
                    .insert(batch.batch_id.clone(), completed_id.unwrap_or_default());
            }
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
            if let Err(error) = write_json_atomically(
                &self
                    .journal_file
                    .with_file_name("library-organization-draft.json"),
                draft,
            ) {
                runtime.status.message = Some(format!(
                    "Files moved successfully; saving review position failed: {error}"
                ));
            }
        }
        runtime.plans.clear();
        Ok(())
    }

    pub fn retry_recovery(&self) -> Result<PublishedLibrary> {
        let mut journal = load_journal(&self.journal_file)?;
        if let Some(error) = recover_interrupted(&self.catalog_store, &mut journal, &self.roots) {
            self.finish_failed(error.clone(), true);
            return Err(LibraryServerError::new(error));
        }
        journal.recovery_error = None;
        {
            let mut runtime = self.runtime.lock().expect("organizer lock");
            runtime.journal = journal;
            runtime.status = OrganizationStatus::default();
            runtime.status.last_completed_batch_id = runtime
                .journal
                .completed
                .last()
                .map(|b| b.completed_batch_id.clone());
            runtime.status.can_undo = runtime.status.last_completed_batch_id.is_some();
        }
        self.persist_journal()?;
        self.catalog_store
            .load()?
            .map(|s| s.published_library)
            .ok_or_else(|| LibraryServerError::new("Catalog unavailable."))
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

fn preflight_batch(batch: &StoredBatch) -> Result<()> {
    let mut destinations = BTreeSet::new();
    for operation in &batch.moves {
        let source_root =
            normalize_absolute(operation.source_root.as_deref().unwrap_or(&batch.root))?;
        let destination_root =
            normalize_absolute(operation.destination_root.as_deref().unwrap_or(&batch.root))?;
        let source = normalize_absolute(&operation.source(&batch.root))?;
        let destination = normalize_absolute(&operation.destination(&batch.root))?;
        ensure_within_root(&source_root, &source)?;
        ensure_within_root(&destination_root, &destination)?;
        reject_reparse_ancestors(&source_root, &source)?;
        reject_reparse_ancestors(&destination_root, &destination)?;
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
        let destination = operation.destination(root);
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
            item.root_label = Some(
                operation
                    .destination_root
                    .as_deref()
                    .unwrap_or(root)
                    .to_string_lossy()
                    .into_owned(),
            );
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
    roots: &[PathBuf],
) -> Option<String> {
    let mut active = journal.active.clone()?;
    for operation in &active.batch.moves {
        for root in [
            operation.source_root.as_ref().unwrap_or(&active.batch.root),
            operation
                .destination_root
                .as_ref()
                .unwrap_or(&active.batch.root),
        ] {
            if !roots.iter().any(|configured| paths_equal(configured, root)) {
                return Some(
                    "Restore the transaction's configured roots before retrying recovery.".into(),
                );
            }
        }
    }
    if !active.transfers.is_empty() {
        let committed = catalog_store.load().ok().flatten().is_some_and(|stored| {
            active.catalog_commit_revision.as_ref()
                == Some(&catalog_revision(&stored.published_library.catalog))
        });
        if committed {
            for (operation, record) in active.batch.moves.iter().zip(&active.transfers) {
                if transfer::digest(&operation.destination(&active.batch.root))
                    .ok()
                    .as_ref()
                    != Some(&record.digest)
                {
                    return Some("A committed destination is unavailable or changed.".into());
                }
            }
            if !active.undo {
                journal.completed.push(CompletedBatch {
                    completed_batch_id: format!("{}-recovered", active.batch.batch_id),
                    batch: active.batch.clone(),
                    completed_at_epoch_ms: current_epoch_ms(),
                });
            } else {
                journal.completed.retain(|b| {
                    b.batch.batch_id != active.batch.batch_id.trim_start_matches("undo-")
                });
            }
            journal.active = None;
            journal.recovery_error = None;
            return None;
        }
        match transfer::rollback(&mut active, |tx| {
            journal.active = Some(tx.clone());
            write_json_atomically(
                &catalog_store
                    .file_path()
                    .with_file_name("library-organization.json"),
                journal,
            )
        }) {
            Ok(()) => {
                journal.active = None;
                journal.recovery_error = None;
                return None;
            }
            Err(error) => return Some(error.to_string()),
        }
    }

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
    operation.source_signature = None;
    std::mem::swap(&mut operation.source_root, &mut operation.destination_root);
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
        completed_bytes: 0,
        total_bytes: batch.moves.iter().map(|m| m.size_bytes).sum(),
        draft: None,
        identification: IdentificationStatus::default(),
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
        .map(|item| {
            format!(
                "{}:{:?}:{}:{}",
                item.id, item.root_label, item.relative_path, item.size_bytes
            )
        })
        .collect::<Vec<_>>()
        .join("\n");
    sha256_hex(&format!("{}\n{material}", catalog.indexed_at_epoch_ms))
        .chars()
        .take(24)
        .collect()
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
    if !root.is_dir() {
        return Err(LibraryServerError::new(
            "Reconnect the configured library root before organizing files.",
        ));
    }
    let mut paths = vec![root.to_path_buf()];
    let mut current = root.to_path_buf();
    for component in relative.components() {
        current.push(component.as_os_str());
        paths.push(current.clone());
    }
    for current in paths {
        let metadata = match fs::symlink_metadata(&current) {
            Ok(metadata) => metadata,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => break,
            Err(error) => return Err(error.into()),
        };
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
    #[cfg(windows)]
    {
        let mut left = left.components();
        let mut right = right.components();
        loop {
            match (left.next(), right.next()) {
                (None, None) => return true,
                (Some(a), Some(b))
                    if a.as_os_str()
                        .to_string_lossy()
                        .eq_ignore_ascii_case(&b.as_os_str().to_string_lossy()) => {}
                _ => return false,
            }
        }
    }
    #[cfg(not(windows))]
    {
        left == right
    }
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
    let mut journal = serde_json::from_str::<OrganizationJournal>(&body).map_err(|error| {
        LibraryServerError::with_context(error, format!("failed to parse {}", file.display()))
    })?;
    if journal.schema_version == 1 {
        // Legacy operations retain their original root until recovery finishes.
        journal.schema_version = JOURNAL_SCHEMA_VERSION;
    }
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
    use std::io::Write;
    let mut output = fs::File::create(&temporary)?;
    output.write_all(serde_json::to_string_pretty(value)?.as_bytes())?;
    output.sync_all()?;
    drop(output);
    #[cfg(windows)]
    {
        use std::os::windows::ffi::OsStrExt;
        use windows_sys::Win32::Storage::FileSystem::{
            MOVEFILE_REPLACE_EXISTING, MOVEFILE_WRITE_THROUGH, MoveFileExW,
        };
        let source = temporary
            .as_os_str()
            .encode_wide()
            .chain(Some(0))
            .collect::<Vec<_>>();
        let destination = file
            .as_os_str()
            .encode_wide()
            .chain(Some(0))
            .collect::<Vec<_>>();
        if unsafe {
            MoveFileExW(
                source.as_ptr(),
                destination.as_ptr(),
                MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH,
            )
        } == 0
        {
            return Err(std::io::Error::last_os_error().into());
        }
    }
    #[cfg(unix)]
    {
        fs::rename(&temporary, file)?;
        if let Some(parent) = file.parent() {
            fs::File::open(parent)?.sync_all()?;
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use std::sync::atomic::{AtomicU64, Ordering};

    use crate::catalog::{LibraryCatalog, LibraryItemMetadataStatus, LibraryMediaItem};

    use super::*;

    static TEMP_COUNTER: AtomicU64 = AtomicU64::new(0);

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
                    .create_draft(
                        &fixture.published,
                        draft::CreateDraftRequest {
                            media_ids: vec!["one".into()],
                            destination: fixture.root.to_string_lossy().into_owned(),
                        }
                    )
                    .is_err()
            );
            assert!(organizer.retry_recovery().is_err());
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
                transfers: Vec::new(),
                catalog_committed: false,
                catalog_commit_revision: None,
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

    pub(super) struct Fixture {
        pub(super) temp: PathBuf,
        pub(super) root: PathBuf,
        pub(super) data: PathBuf,
        pub(super) published: PublishedLibrary,
    }

    pub(super) fn fixture_batch(fixture: &Fixture) -> StoredBatch {
        let source_relative_path = "[Group] Example Show - 01.mkv".to_owned();
        StoredBatch {
            batch_id: "fixture-batch".to_owned(),
            series_title: "Example Show".to_owned(),
            root: fixture.root.clone(),
            moves: vec![OrganizationMove {
                source_signature: None,
                content_hash: None,
                source_root: None,
                destination_root: None,
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

    pub(super) fn fixture() -> Fixture {
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

    pub(super) fn cleanup(path: PathBuf) {
        fs::remove_dir_all(path).expect("fixture deletes");
    }
}

#[cfg(test)]
mod workflow_tests;
