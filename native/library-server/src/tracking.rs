use std::collections::{BTreeMap, BTreeSet};
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::time::{SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};

use crate::catalog::{ExternalAnimeId, ExternalAnimeProvider, LibraryCatalog};
use crate::domain::{PlaybackProgress, WatchState, grouped_series, watch_status_by_media_id};
use crate::external_provider::{
    ExternalAnimeListEntry, ExternalAnimeListStatus, ExternalAnimeTrackingUpdate,
    ExternalProviderService,
};
use crate::{LibraryServerError, Result};

const SCHEMA_VERSION: u32 = 1;
const MINIMUM_STARTED_POSITION_MS: u64 = 10_000;
const WATCHED_REMAINING_MS: u64 = 30_000;
const RETRY_BASE_DELAY_MS: u64 = 30_000;
const RETRY_MAX_DELAY_MS: u64 = 30 * 60_000;

#[derive(Debug)]
pub struct ExternalTrackingStore {
    file: PathBuf,
    state: Mutex<ExternalTrackingState>,
}

impl ExternalTrackingStore {
    pub fn open(file: impl Into<PathBuf>) -> Result<Self> {
        let file = file.into();
        let state = load_state(&file)?;
        Ok(Self {
            file,
            state: Mutex::new(state),
        })
    }

    pub fn snapshot(&self) -> ExternalTrackingState {
        self.state
            .lock()
            .expect("tracking store lock should not be poisoned")
            .clone()
    }

    pub fn save_mapping(&self, mapping: ExternalAnimeMapping) -> Result<()> {
        validate_mapping(&mapping)?;
        self.update(|state| {
            state.mappings.retain(|candidate| {
                candidate.local_series_id != mapping.local_series_id
                    || candidate.anime_id.provider != mapping.anime_id.provider
            });
            state.mappings.push(mapping);
            sort_state(state);
        })
    }

    pub fn delete_mapping(
        &self,
        local_series_id: &str,
        anime_id: &ExternalAnimeId,
    ) -> Result<bool> {
        let mut removed = false;
        self.update(|state| {
            let original_len = state.mappings.len();
            state.mappings.retain(|mapping| {
                mapping.local_series_id != local_series_id || &mapping.anime_id != anime_id
            });
            removed = state.mappings.len() != original_len;
        })?;
        Ok(removed)
    }

    pub fn apply_readback(
        &self,
        requested: &BTreeSet<ExternalAnimeId>,
        entries: Vec<ExternalAnimeListEntry>,
    ) -> Result<()> {
        self.update(|state| {
            let entries_by_id = entries
                .into_iter()
                .map(|entry| (entry.anime_id.clone(), entry))
                .collect::<BTreeMap<_, _>>();
            state
                .list_entries
                .retain(|entry| !requested.contains(&entry.anime_id));
            state.list_entries.extend(entries_by_id.into_values());
            sort_state(state);
        })
    }

    pub fn replace_failures(&self, failures: Vec<ExternalAnimeSyncFailure>) -> Result<()> {
        self.update(|state| {
            state.failures = failures;
            sort_state(state);
        })
    }

    fn update(&self, mutate: impl FnOnce(&mut ExternalTrackingState)) -> Result<()> {
        let mut state = self
            .state
            .lock()
            .expect("tracking store lock should not be poisoned");
        let mut next = state.clone();
        mutate(&mut next);
        validate_state(&next)?;
        write_state(&self.file, &next)?;
        *state = next;
        Ok(())
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ExternalTrackingState {
    pub schema_version: u32,
    #[serde(default)]
    pub mappings: Vec<ExternalAnimeMapping>,
    #[serde(default)]
    pub list_entries: Vec<ExternalAnimeListEntry>,
    #[serde(default)]
    pub failures: Vec<ExternalAnimeSyncFailure>,
}

impl Default for ExternalTrackingState {
    fn default() -> Self {
        Self {
            schema_version: SCHEMA_VERSION,
            mappings: Vec::new(),
            list_entries: Vec::new(),
            failures: Vec::new(),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ExternalAnimeMapping {
    pub local_series_id: String,
    pub anime_id: ExternalAnimeId,
    pub source: ExternalAnimeMappingSource,
    pub confidence: f64,
    pub mapped_at_epoch_ms: u64,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum ExternalAnimeMappingSource {
    Auto,
    Manual,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ExternalAnimeSyncFailure {
    pub anime_id: ExternalAnimeId,
    pub message: String,
    pub failed_at_epoch_ms: u64,
    pub attempt_count: u32,
    pub retry_after_epoch_ms: u64,
}

#[derive(Debug, Clone, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ExternalTrackingDocument {
    pub generated_at_epoch_ms: u64,
    pub series: Vec<ExternalTrackingSeries>,
    pub mappings: Vec<ExternalAnimeMapping>,
    pub list_entries: Vec<ExternalAnimeListEntry>,
    pub plan: ExternalTrackingPlan,
}

#[derive(Debug, Clone, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ExternalTrackingSeries {
    pub id: String,
    pub title: String,
    pub episode_count: usize,
    pub mappings: Vec<ExternalAnimeMapping>,
}

#[derive(Debug, Clone, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ExternalTrackingPlan {
    pub summary: ExternalTrackingPlanSummary,
    pub updates: Vec<ExternalTrackingPlanUpdate>,
    pub skipped: Vec<ExternalTrackingPlanSkip>,
    pub conflicts: Vec<ExternalTrackingPlanConflict>,
    pub failures: Vec<ExternalAnimeSyncFailure>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ExternalTrackingPlanSummary {
    pub update_count: usize,
    pub skipped_count: usize,
    pub conflict_count: usize,
    pub failure_count: usize,
    pub my_anime_list_update_count: usize,
    pub bangumi_update_count: usize,
}

#[derive(Debug, Clone, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ExternalTrackingPlanUpdate {
    pub local_series_id: String,
    pub series_title: String,
    pub episode_count: usize,
    pub mapping: ExternalAnimeMapping,
    pub update: ExternalAnimeTrackingUpdate,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ExternalTrackingPlanSkip {
    pub local_series_id: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub series_title: Option<String>,
    pub provider: ExternalAnimeProvider,
    pub reason: ExternalTrackingPlanSkipReason,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum ExternalTrackingPlanSkipReason {
    MissingLocalSeries,
    UnmappedLocalSeries,
    AlreadyInSync,
}

#[derive(Debug, Clone, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ExternalTrackingPlanConflict {
    pub local_series_id: String,
    pub series_title: String,
    pub episode_count: usize,
    pub mapping: ExternalAnimeMapping,
    pub local_update: ExternalAnimeTrackingUpdate,
    pub external_entry: ExternalAnimeListEntry,
    pub reason: ExternalTrackingPlanConflictReason,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum ExternalTrackingPlanConflictReason {
    ExternalProgressAhead,
}

#[derive(Debug, Clone, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ExternalTrackingOperationResponse {
    pub document: ExternalTrackingDocument,
    pub success_count: usize,
    pub conflict_count: usize,
    pub missing_count: usize,
    pub errors: Vec<ExternalTrackingOperationError>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ExternalTrackingOperationError {
    pub anime_id: ExternalAnimeId,
    pub message: String,
}

pub fn tracking_document(
    catalog: &LibraryCatalog,
    progresses: &[PlaybackProgress],
    state: &ExternalTrackingState,
) -> ExternalTrackingDocument {
    let grouped = grouped_series(catalog);
    let mappings_by_series = state.mappings.iter().cloned().fold(
        BTreeMap::<String, Vec<ExternalAnimeMapping>>::new(),
        |mut mappings, mapping| {
            mappings
                .entry(mapping.local_series_id.clone())
                .or_default()
                .push(mapping);
            mappings
        },
    );
    let series = grouped
        .iter()
        .map(|series| ExternalTrackingSeries {
            id: series.id.clone(),
            title: series.title.clone(),
            episode_count: series.episode_count,
            mappings: mappings_by_series
                .get(&series.id)
                .cloned()
                .unwrap_or_default(),
        })
        .collect::<Vec<_>>();
    let status_by_media_id = watch_status_by_media_id(
        catalog,
        progresses,
        MINIMUM_STARTED_POSITION_MS,
        WATCHED_REMAINING_MS,
    )
    .into_iter()
    .map(|status| (status.media_id, status.state))
    .collect::<BTreeMap<_, _>>();
    let entry_by_anime_id = state
        .list_entries
        .iter()
        .map(|entry| (entry.anime_id.clone(), entry))
        .collect::<BTreeMap<_, _>>();
    let series_by_id = grouped
        .iter()
        .map(|series| (series.id.as_str(), series))
        .collect::<BTreeMap<_, _>>();

    let mut update_candidates = Vec::new();
    let mut skipped = Vec::new();
    for mapping in &state.mappings {
        let Some(series) = series_by_id.get(mapping.local_series_id.as_str()) else {
            skipped.push(ExternalTrackingPlanSkip {
                local_series_id: mapping.local_series_id.clone(),
                series_title: None,
                provider: mapping.anime_id.provider,
                reason: ExternalTrackingPlanSkipReason::MissingLocalSeries,
            });
            continue;
        };
        let states = series
            .seasons
            .iter()
            .flat_map(|season| &season.item_ids)
            .filter_map(|media_id| status_by_media_id.get(media_id).copied())
            .collect::<Vec<_>>();
        let watched_episodes = states
            .iter()
            .filter(|state| matches!(state, WatchState::Watched))
            .count() as u32;
        let status = if watched_episodes as usize == states.len() && !states.is_empty() {
            ExternalAnimeListStatus::Completed
        } else if states
            .iter()
            .any(|state| matches!(state, WatchState::Watched | WatchState::InProgress))
        {
            ExternalAnimeListStatus::Watching
        } else {
            ExternalAnimeListStatus::PlanToWatch
        };
        update_candidates.push(ExternalTrackingPlanUpdate {
            local_series_id: series.id.clone(),
            series_title: series.title.clone(),
            episode_count: series.episode_count,
            mapping: mapping.clone(),
            update: ExternalAnimeTrackingUpdate {
                anime_id: mapping.anime_id.clone(),
                status: Some(status),
                watched_episodes: Some(watched_episodes),
                score: None,
                tracking_enabled: true,
                rating_enabled: true,
            },
        });
    }

    for series in &grouped {
        for provider in trackable_providers() {
            if !state.mappings.iter().any(|mapping| {
                mapping.local_series_id == series.id && mapping.anime_id.provider == provider
            }) {
                skipped.push(ExternalTrackingPlanSkip {
                    local_series_id: series.id.clone(),
                    series_title: Some(series.title.clone()),
                    provider,
                    reason: ExternalTrackingPlanSkipReason::UnmappedLocalSeries,
                });
            }
        }
    }

    let mut conflicts = Vec::new();
    let mut updates = Vec::new();
    for candidate in update_candidates {
        let external_entry = entry_by_anime_id.get(&candidate.mapping.anime_id).copied();
        let external_watched = external_entry.and_then(|entry| entry.watched_episodes);
        let local_watched = candidate.update.watched_episodes;
        if external_entry.is_some_and(|entry| {
            entry.watched_episodes == candidate.update.watched_episodes
                && entry.status == candidate.update.status
        }) {
            skipped.push(ExternalTrackingPlanSkip {
                local_series_id: candidate.local_series_id,
                series_title: Some(candidate.series_title),
                provider: candidate.mapping.anime_id.provider,
                reason: ExternalTrackingPlanSkipReason::AlreadyInSync,
            });
            continue;
        }
        if matches!((external_watched, local_watched), (Some(external), Some(local)) if external > local)
        {
            conflicts.push(ExternalTrackingPlanConflict {
                local_series_id: candidate.local_series_id,
                series_title: candidate.series_title,
                episode_count: candidate.episode_count,
                mapping: candidate.mapping,
                local_update: candidate.update,
                external_entry: external_entry.expect("conflict requires entry").clone(),
                reason: ExternalTrackingPlanConflictReason::ExternalProgressAhead,
            });
        } else {
            updates.push(candidate);
        }
    }
    updates.sort_by(plan_update_order);
    conflicts.sort_by(|left, right| {
        left.series_title
            .to_lowercase()
            .cmp(&right.series_title.to_lowercase())
            .then_with(|| left.mapping.anime_id.cmp(&right.mapping.anime_id))
    });
    skipped.sort_by(|left, right| {
        left.local_series_id
            .cmp(&right.local_series_id)
            .then_with(|| left.provider.cmp(&right.provider))
    });
    let summary = ExternalTrackingPlanSummary {
        update_count: updates.len(),
        skipped_count: skipped.len(),
        conflict_count: conflicts.len(),
        failure_count: state.failures.len(),
        my_anime_list_update_count: updates
            .iter()
            .filter(|update| update.mapping.anime_id.provider == ExternalAnimeProvider::MyAnimeList)
            .count(),
        bangumi_update_count: updates
            .iter()
            .filter(|update| update.mapping.anime_id.provider == ExternalAnimeProvider::Bangumi)
            .count(),
    };

    ExternalTrackingDocument {
        generated_at_epoch_ms: current_epoch_ms(),
        series,
        mappings: state.mappings.clone(),
        list_entries: state.list_entries.clone(),
        plan: ExternalTrackingPlan {
            summary,
            updates,
            skipped,
            conflicts,
            failures: state.failures.clone(),
        },
    }
}

pub async fn refresh_tracking_readback(
    service: &ExternalProviderService,
    store: &ExternalTrackingStore,
    catalog: &LibraryCatalog,
    progresses: &[PlaybackProgress],
) -> Result<ExternalTrackingOperationResponse> {
    let requested = store
        .snapshot()
        .mappings
        .into_iter()
        .map(|mapping| mapping.anime_id)
        .collect::<BTreeSet<_>>();
    let mut entries = Vec::new();
    let mut resolved = BTreeSet::new();
    let mut missing_count = 0;
    let mut errors = Vec::new();
    for anime_id in &requested {
        match service.fetch_list_entry(anime_id.clone()).await {
            Ok(Some(entry)) => {
                resolved.insert(anime_id.clone());
                entries.push(entry);
            }
            Ok(None) => {
                resolved.insert(anime_id.clone());
                missing_count += 1;
            }
            Err(error) => errors.push(ExternalTrackingOperationError {
                anime_id: anime_id.clone(),
                message: error.to_string(),
            }),
        }
    }
    let success_count = entries.len();
    store.apply_readback(&resolved, entries)?;
    Ok(ExternalTrackingOperationResponse {
        document: tracking_document(catalog, progresses, &store.snapshot()),
        success_count,
        conflict_count: 0,
        missing_count,
        errors,
    })
}

pub async fn execute_tracking_sync(
    service: &ExternalProviderService,
    store: &ExternalTrackingStore,
    catalog: &LibraryCatalog,
    progresses: &[PlaybackProgress],
    expected_updates: &[ExternalAnimeTrackingUpdate],
) -> Result<ExternalTrackingOperationResponse> {
    let before = store.snapshot();
    let plan = tracking_document(catalog, progresses, &before).plan;
    let planned_updates = plan
        .updates
        .iter()
        .map(|candidate| candidate.update.clone())
        .collect::<Vec<_>>();
    if planned_updates != expected_updates {
        return Err(LibraryServerError::new(
            "tracking preview changed; reload and review it before syncing",
        ));
    }
    let previous_failures = before
        .failures
        .iter()
        .map(|failure| (failure.anime_id.clone(), failure))
        .collect::<BTreeMap<_, _>>();
    let mut requested = BTreeSet::new();
    let mut entries = Vec::new();
    let mut failures = Vec::new();
    let mut errors = Vec::new();
    let mut success_count = 0;
    let mut conflict_count = plan.conflicts.len();

    for candidate in plan.updates {
        let anime_id = candidate.mapping.anime_id.clone();
        let operation = match service.fetch_list_entry(anime_id.clone()).await {
            Ok(Some(entry))
                if entry.watched_episodes == candidate.update.watched_episodes
                    && entry.status == candidate.update.status =>
            {
                requested.insert(anime_id);
                entries.push(entry);
                continue;
            }
            Ok(Some(entry))
                if entry.watched_episodes.is_some()
                    && candidate.update.watched_episodes.is_some()
                    && entry.watched_episodes > candidate.update.watched_episodes =>
            {
                requested.insert(anime_id);
                entries.push(entry);
                conflict_count += 1;
                continue;
            }
            Ok(_) => service.update_list_entry(candidate.update).await,
            Err(error) => Err(error),
        };
        match operation {
            Ok(entry) => {
                requested.insert(anime_id);
                entries.push(entry);
                success_count += 1;
            }
            Err(error) => {
                let failed_at_epoch_ms = current_epoch_ms();
                let attempt_count = previous_failures
                    .get(&anime_id)
                    .map_or(1, |failure| failure.attempt_count.saturating_add(1));
                let message = error.to_string();
                failures.push(ExternalAnimeSyncFailure {
                    anime_id: anime_id.clone(),
                    message: message.clone(),
                    failed_at_epoch_ms,
                    attempt_count,
                    retry_after_epoch_ms: external_anime_sync_retry_after_epoch_ms(
                        failed_at_epoch_ms,
                        attempt_count,
                    ),
                });
                errors.push(ExternalTrackingOperationError { anime_id, message });
            }
        }
    }
    store.apply_readback(&requested, entries)?;
    store.replace_failures(failures)?;
    Ok(ExternalTrackingOperationResponse {
        document: tracking_document(catalog, progresses, &store.snapshot()),
        success_count,
        conflict_count,
        missing_count: 0,
        errors,
    })
}

pub fn external_anime_sync_retry_after_epoch_ms(
    failed_at_epoch_ms: u64,
    attempt_count: u32,
) -> u64 {
    let exponent = attempt_count.saturating_sub(1).min(30);
    let delay = RETRY_BASE_DELAY_MS
        .saturating_mul(1_u64 << exponent)
        .min(RETRY_MAX_DELAY_MS);
    failed_at_epoch_ms.saturating_add(delay)
}

pub fn current_epoch_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|duration| duration.as_millis() as u64)
        .unwrap_or(0)
}

fn trackable_providers() -> [ExternalAnimeProvider; 2] {
    [
        ExternalAnimeProvider::MyAnimeList,
        ExternalAnimeProvider::Bangumi,
    ]
}

fn plan_update_order(
    left: &ExternalTrackingPlanUpdate,
    right: &ExternalTrackingPlanUpdate,
) -> std::cmp::Ordering {
    left.series_title
        .to_lowercase()
        .cmp(&right.series_title.to_lowercase())
        .then_with(|| left.mapping.anime_id.cmp(&right.mapping.anime_id))
}

fn validate_mapping(mapping: &ExternalAnimeMapping) -> Result<()> {
    if mapping.local_series_id.trim().is_empty() {
        return Err(LibraryServerError::new("localSeriesId must not be blank"));
    }
    if mapping.anime_id.value == 0 {
        return Err(LibraryServerError::new(
            "external anime ID must be positive",
        ));
    }
    if !trackable_providers().contains(&mapping.anime_id.provider) {
        return Err(LibraryServerError::new(
            "only MyAnimeList and Bangumi series mappings are supported",
        ));
    }
    if !(0.0..=1.0).contains(&mapping.confidence) {
        return Err(LibraryServerError::new(
            "mapping confidence must be between 0 and 1",
        ));
    }
    Ok(())
}

fn validate_state(state: &ExternalTrackingState) -> Result<()> {
    if state.schema_version != SCHEMA_VERSION {
        return Err(LibraryServerError::new(format!(
            "unsupported external tracking schemaVersion {}",
            state.schema_version
        )));
    }
    let mut mapping_keys = BTreeSet::new();
    for mapping in &state.mappings {
        validate_mapping(mapping)?;
        if !mapping_keys.insert((mapping.local_series_id.clone(), mapping.anime_id.provider)) {
            return Err(LibraryServerError::new(
                "external mappings must be unique by local series and provider",
            ));
        }
    }
    let mut entry_ids = BTreeSet::new();
    for entry in &state.list_entries {
        if !entry_ids.insert(entry.anime_id.clone()) {
            return Err(LibraryServerError::new(
                "external list entries must be unique by anime ID",
            ));
        }
    }
    let mut failure_ids = BTreeSet::new();
    for failure in &state.failures {
        if failure.message.trim().is_empty()
            || failure.attempt_count == 0
            || failure.retry_after_epoch_ms < failure.failed_at_epoch_ms
        {
            return Err(LibraryServerError::new(
                "external sync failure state is invalid",
            ));
        }
        if !failure_ids.insert(failure.anime_id.clone()) {
            return Err(LibraryServerError::new(
                "external sync failures must be unique by anime ID",
            ));
        }
    }
    Ok(())
}

fn sort_state(state: &mut ExternalTrackingState) {
    state.mappings.sort_by(|left, right| {
        left.local_series_id
            .cmp(&right.local_series_id)
            .then_with(|| left.anime_id.provider.cmp(&right.anime_id.provider))
    });
    state
        .list_entries
        .sort_by(|left, right| left.anime_id.cmp(&right.anime_id));
    state
        .failures
        .sort_by(|left, right| left.anime_id.cmp(&right.anime_id));
}

fn load_state(file: &Path) -> Result<ExternalTrackingState> {
    if !file.is_file() {
        return Ok(ExternalTrackingState::default());
    }
    let text = fs::read_to_string(file).map_err(|error| {
        LibraryServerError::with_context(
            error,
            format!("failed to read external tracking state {}", file.display()),
        )
    })?;
    let mut state = serde_json::from_str::<ExternalTrackingState>(&text).map_err(|error| {
        LibraryServerError::with_context(
            error,
            format!("failed to parse external tracking state {}", file.display()),
        )
    })?;
    validate_state(&state)?;
    sort_state(&mut state);
    Ok(state)
}

fn write_state(file: &Path, state: &ExternalTrackingState) -> Result<()> {
    if let Some(parent) = file.parent() {
        fs::create_dir_all(parent).map_err(|error| {
            LibraryServerError::with_context(
                error,
                format!("failed to create tracking directory {}", parent.display()),
            )
        })?;
    }
    let file_name = file
        .file_name()
        .map(|name| name.to_string_lossy().into_owned())
        .ok_or_else(|| {
            LibraryServerError::new(format!(
                "tracking path must include a file name: {}",
                file.display()
            ))
        })?;
    let temp = file.with_file_name(format!("{file_name}.tmp"));
    fs::write(&temp, serde_json::to_string_pretty(state)?).map_err(|error| {
        LibraryServerError::with_context(
            error,
            format!("failed to write external tracking state {}", temp.display()),
        )
    })?;
    fs::rename(&temp, file).map_err(|error| {
        LibraryServerError::with_context(
            error,
            format!(
                "failed to replace external tracking state {}",
                file.display()
            ),
        )
    })
}

#[cfg(test)]
mod tests {
    use std::sync::atomic::{AtomicU64, Ordering};

    use crate::catalog::{LibraryItemMetadataStatus, LibraryMediaItem};

    use super::*;

    static TEMP_COUNTER: AtomicU64 = AtomicU64::new(0);

    #[test]
    fn store_persists_replacements_and_rejects_wrong_schema() {
        let temp = TempDirectory::new("danmaku-tracking-store");
        let file = temp.path.join("tracking.json");
        let store = ExternalTrackingStore::open(&file).expect("store opens");
        store
            .save_mapping(mapping("series-a", ExternalAnimeProvider::MyAnimeList, 42))
            .expect("first mapping saves");
        let replacement = mapping("series-a", ExternalAnimeProvider::MyAnimeList, 84);
        store
            .save_mapping(replacement.clone())
            .expect("replacement mapping saves");
        let failure = ExternalAnimeSyncFailure {
            anime_id: replacement.anime_id.clone(),
            message: "temporary upstream failure".to_owned(),
            failed_at_epoch_ms: 1_000,
            attempt_count: 2,
            retry_after_epoch_ms: 61_000,
        };
        store
            .replace_failures(vec![failure.clone()])
            .expect("failure state saves");

        let reopened = ExternalTrackingStore::open(&file).expect("store reopens");
        let snapshot = reopened.snapshot();
        assert_eq!(vec![replacement], snapshot.mappings);
        assert_eq!(vec![failure], snapshot.failures);

        fs::write(&file, r#"{"schemaVersion":2,"mappings":[]}"#)
            .expect("wrong schema fixture writes");
        let error = ExternalTrackingStore::open(&file).expect_err("wrong schema rejected");
        assert!(error.to_string().contains("schemaVersion 2"));
    }

    #[test]
    fn plan_derives_updates_conflicts_and_unmapped_skips() {
        let catalog = fixture_catalog();
        let series_id = grouped_series(&catalog)[0].id.clone();
        let progresses = vec![
            PlaybackProgress {
                media_id: "episode-1".to_owned(),
                position_ms: 100_000,
                duration_ms: Some(100_000),
                updated_at_epoch_ms: 10,
            },
            PlaybackProgress {
                media_id: "episode-2".to_owned(),
                position_ms: 20_000,
                duration_ms: Some(100_000),
                updated_at_epoch_ms: 20,
            },
        ];
        let state = ExternalTrackingState {
            mappings: vec![mapping(&series_id, ExternalAnimeProvider::MyAnimeList, 42)],
            list_entries: vec![ExternalAnimeListEntry {
                anime_id: ExternalAnimeId {
                    provider: ExternalAnimeProvider::MyAnimeList,
                    value: 42,
                },
                status: Some(ExternalAnimeListStatus::Watching),
                watched_episodes: Some(2),
                score: None,
                updated_at_epoch_ms: Some(30),
            }],
            ..ExternalTrackingState::default()
        };

        let document = tracking_document(&catalog, &progresses, &state);
        assert!(document.plan.updates.is_empty());
        assert_eq!(1, document.plan.conflicts.len());
        assert_eq!(
            Some(1),
            document.plan.conflicts[0].local_update.watched_episodes
        );
        assert_eq!(1, document.plan.skipped.len());
        assert_eq!(
            ExternalAnimeProvider::Bangumi,
            document.plan.skipped[0].provider
        );
    }

    #[test]
    fn retry_backoff_doubles_and_caps_at_thirty_minutes() {
        assert_eq!(31_000, external_anime_sync_retry_after_epoch_ms(1_000, 1));
        assert_eq!(61_000, external_anime_sync_retry_after_epoch_ms(1_000, 2));
        assert_eq!(
            1_801_000,
            external_anime_sync_retry_after_epoch_ms(1_000, 31)
        );
    }

    fn mapping(
        local_series_id: &str,
        provider: ExternalAnimeProvider,
        value: u64,
    ) -> ExternalAnimeMapping {
        ExternalAnimeMapping {
            local_series_id: local_series_id.to_owned(),
            anime_id: ExternalAnimeId { provider, value },
            source: ExternalAnimeMappingSource::Manual,
            confidence: 1.0,
            mapped_at_epoch_ms: 100,
        }
    }

    fn fixture_catalog() -> LibraryCatalog {
        LibraryCatalog {
            root_name: "Fixture".to_owned(),
            indexed_at_epoch_ms: 1,
            items: vec![
                fixture_item("episode-1", "Episode 01"),
                fixture_item("episode-2", "Episode 02"),
            ],
        }
    }

    fn fixture_item(id: &str, episode_title: &str) -> LibraryMediaItem {
        LibraryMediaItem {
            id: id.to_owned(),
            series_title: "Example Show".to_owned(),
            episode_title: episode_title.to_owned(),
            relative_path: format!("Example Show/{episode_title}.mkv"),
            size_bytes: 1,
            media_type: "video/x-matroska".to_owned(),
            stream_path: format!("/media/{id}"),
            indexed_at_epoch_ms: 1,
            subtitles: Vec::new(),
            poster_path: None,
            root_label: None,
            anime_metadata: None,
            metadata_status: LibraryItemMetadataStatus::NotAvailable,
        }
    }

    struct TempDirectory {
        path: PathBuf,
    }

    impl TempDirectory {
        fn new(prefix: &str) -> Self {
            let id = TEMP_COUNTER.fetch_add(1, Ordering::Relaxed);
            let path = std::env::temp_dir().join(format!("{prefix}-{}-{id}", std::process::id()));
            let _ = fs::remove_dir_all(&path);
            fs::create_dir_all(&path).expect("temp directory creates");
            Self { path }
        }
    }

    impl Drop for TempDirectory {
        fn drop(&mut self) {
            let _ = fs::remove_dir_all(&self.path);
        }
    }
}
