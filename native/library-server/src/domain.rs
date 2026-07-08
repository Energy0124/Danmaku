use std::cmp::Ordering;
use std::collections::BTreeMap;

use serde::{Deserialize, Serialize};

use crate::catalog::{ExternalAnimeProvider, LibraryCatalog, LibraryMediaItem};
use crate::scanner::{find_episode_number, find_season_number};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PlaybackProgress {
    pub media_id: String,
    pub position_ms: u64,
    pub duration_ms: Option<u64>,
    pub updated_at_epoch_ms: u64,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct GroupedSeriesOutput {
    pub id: String,
    pub title: String,
    pub episode_count: usize,
    pub subtitle_track_count: usize,
    pub total_size_bytes: u64,
    pub latest_indexed_media_id: String,
    pub seasons: Vec<GroupedSeasonOutput>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct GroupedSeasonOutput {
    pub id: String,
    pub label: String,
    pub sort_key: i32,
    pub item_ids: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct WatchStatusOutput {
    pub media_id: String,
    pub state: WatchState,
    pub progress: Option<PlaybackProgress>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum WatchState {
    New,
    InProgress,
    Watched,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SeriesWatchSummaryOutput {
    pub series_id: String,
    pub total_count: usize,
    pub watched_count: usize,
    pub in_progress_count: usize,
    pub new_count: usize,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ContinueWatchingOutput {
    pub media_id: String,
    pub progress: PlaybackProgress,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct NextUpOutput {
    pub media_id: String,
    pub reason: NextUpReason,
    pub progress_media_id: Option<String>,
    pub source_progress_media_id: Option<String>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum NextUpReason {
    Resume,
    NextEpisode,
    Start,
}

pub fn grouped_series(catalog: &LibraryCatalog) -> Vec<GroupedSeriesOutput> {
    let mut groups = Vec::<SeriesGroup>::new();
    let mut group_index_by_id = BTreeMap::<String, usize>::new();
    for item in &catalog.items {
        let identity = SeriesIdentity::from_item(item);
        let index = if let Some(index) = group_index_by_id.get(&identity.id) {
            *index
        } else {
            let index = groups.len();
            group_index_by_id.insert(identity.id.clone(), index);
            groups.push(SeriesGroup {
                id: identity.id.clone(),
                identified_items: Vec::new(),
            });
            index
        };
        groups[index].identified_items.push((identity, item));
    }

    let mut outputs = groups
        .into_iter()
        .map(SeriesGroup::into_output)
        .collect::<Vec<_>>();
    outputs.sort_by(|left, right| {
        right
            .episode_count
            .cmp(&left.episode_count)
            .then_with(|| left.title.to_lowercase().cmp(&right.title.to_lowercase()))
            .then_with(|| left.id.cmp(&right.id))
    });
    outputs
}

pub fn watch_status_by_media_id(
    catalog: &LibraryCatalog,
    progresses: &[PlaybackProgress],
    minimum_started_position_ms: u64,
    watched_remaining_ms: u64,
) -> Vec<WatchStatusOutput> {
    let progress_by_media_id = latest_by_media_id(progresses);
    let mut statuses = catalog
        .items
        .iter()
        .map(|item| {
            let progress = progress_by_media_id.get(&item.id).cloned();
            WatchStatusOutput {
                media_id: item.id.clone(),
                state: watch_state(
                    progress.as_ref(),
                    minimum_started_position_ms,
                    watched_remaining_ms,
                ),
                progress,
            }
        })
        .collect::<Vec<_>>();
    statuses.sort_by(|left, right| left.media_id.cmp(&right.media_id));
    statuses
}

pub fn series_watch_summary_by_id(
    catalog: &LibraryCatalog,
    progresses: &[PlaybackProgress],
    minimum_started_position_ms: u64,
    watched_remaining_ms: u64,
) -> Vec<SeriesWatchSummaryOutput> {
    let status_by_media_id = watch_status_by_media_id(
        catalog,
        progresses,
        minimum_started_position_ms,
        watched_remaining_ms,
    )
    .into_iter()
    .map(|status| (status.media_id.clone(), status.state))
    .collect::<BTreeMap<_, _>>();

    let mut summaries = grouped_series(catalog)
        .into_iter()
        .map(|series| {
            let states = series
                .seasons
                .iter()
                .flat_map(|season| &season.item_ids)
                .filter_map(|id| status_by_media_id.get(id).copied())
                .collect::<Vec<_>>();
            SeriesWatchSummaryOutput {
                series_id: series.id,
                total_count: states.len(),
                watched_count: states
                    .iter()
                    .filter(|state| matches!(state, WatchState::Watched))
                    .count(),
                in_progress_count: states
                    .iter()
                    .filter(|state| matches!(state, WatchState::InProgress))
                    .count(),
                new_count: states
                    .iter()
                    .filter(|state| matches!(state, WatchState::New))
                    .count(),
            }
        })
        .collect::<Vec<_>>();
    summaries.sort_by(|left, right| left.series_id.cmp(&right.series_id));
    summaries
}

pub fn continue_watching(
    catalog: &LibraryCatalog,
    progresses: &[PlaybackProgress],
    limit: usize,
    minimum_resume_position_ms: u64,
    minimum_remaining_ms: u64,
) -> Vec<ContinueWatchingOutput> {
    let progress_by_media_id = latest_by_media_id(progresses);
    let mut items = catalog
        .items
        .iter()
        .filter_map(|item| {
            let progress = progress_by_media_id.get(&item.id)?;
            resume_position_ms(progress, minimum_resume_position_ms, minimum_remaining_ms)?;
            Some(ContinueWatchingOutput {
                media_id: item.id.clone(),
                progress: progress.clone(),
            })
        })
        .collect::<Vec<_>>();
    items.sort_by(|left, right| {
        right
            .progress
            .updated_at_epoch_ms
            .cmp(&left.progress.updated_at_epoch_ms)
    });
    items.truncate(limit);
    items
}

pub fn next_up_items(
    catalog: &LibraryCatalog,
    progresses: &[PlaybackProgress],
    limit: usize,
    minimum_resume_position_ms: u64,
    minimum_remaining_ms: u64,
) -> Vec<NextUpOutput> {
    if limit == 0 || catalog.items.is_empty() {
        return Vec::new();
    }

    let newest_progress_by_media_id = latest_by_media_id(progresses);
    let item_index_by_id = catalog
        .items
        .iter()
        .enumerate()
        .map(|(index, item)| (item.id.clone(), index))
        .collect::<BTreeMap<_, _>>();
    let mut ordered_progress = progresses.to_vec();
    ordered_progress
        .sort_by(|left, right| right.updated_at_epoch_ms.cmp(&left.updated_at_epoch_ms));

    let mut candidates = Vec::<NextUpOutput>::new();
    for progress in ordered_progress {
        let Some(item_index) = item_index_by_id.get(&progress.media_id).copied() else {
            continue;
        };
        if resume_position_ms(&progress, minimum_resume_position_ms, minimum_remaining_ms).is_some()
        {
            candidates.push(NextUpOutput {
                media_id: progress.media_id.clone(),
                reason: NextUpReason::Resume,
                progress_media_id: Some(progress.media_id.clone()),
                source_progress_media_id: Some(progress.media_id),
            });
        } else if is_near_end(&progress, minimum_resume_position_ms, minimum_remaining_ms)
            && let Some(next_item) = catalog.items.get(item_index + 1)
            && !newest_progress_by_media_id.contains_key(&next_item.id)
        {
            candidates.push(NextUpOutput {
                media_id: next_item.id.clone(),
                reason: NextUpReason::NextEpisode,
                progress_media_id: None,
                source_progress_media_id: Some(progress.media_id),
            });
        }
    }

    if candidates.is_empty() {
        candidates.push(NextUpOutput {
            media_id: catalog.items[0].id.clone(),
            reason: NextUpReason::Start,
            progress_media_id: None,
            source_progress_media_id: None,
        });
    }

    let mut seen = BTreeMap::<String, ()>::new();
    candidates
        .into_iter()
        .filter(|candidate| seen.insert(candidate.media_id.clone(), ()).is_none())
        .take(limit)
        .collect()
}

fn watch_state(
    progress: Option<&PlaybackProgress>,
    minimum_started_position_ms: u64,
    watched_remaining_ms: u64,
) -> WatchState {
    match progress {
        None => WatchState::New,
        Some(progress) if is_watched(progress, watched_remaining_ms) => WatchState::Watched,
        Some(progress)
            if resume_position_ms(progress, minimum_started_position_ms, watched_remaining_ms)
                .is_some() =>
        {
            WatchState::InProgress
        }
        Some(_) => WatchState::New,
    }
}

fn is_watched(progress: &PlaybackProgress, watched_remaining_ms: u64) -> bool {
    progress.duration_ms.is_some_and(|duration| {
        progress.position_ms > 0
            && remaining_ms(duration, progress.position_ms) <= watched_remaining_ms as i128
    })
}

fn resume_position_ms(
    progress: &PlaybackProgress,
    minimum_position_ms: u64,
    minimum_remaining_ms: u64,
) -> Option<u64> {
    let enough_position = progress.position_ms >= minimum_position_ms;
    let enough_remaining = progress
        .duration_ms
        .map(|duration| {
            remaining_ms(duration, progress.position_ms) >= minimum_remaining_ms as i128
        })
        .unwrap_or(true);
    if enough_position && enough_remaining {
        Some(progress.position_ms)
    } else {
        None
    }
}

fn is_near_end(
    progress: &PlaybackProgress,
    minimum_resume_position_ms: u64,
    minimum_remaining_ms: u64,
) -> bool {
    progress.duration_ms.is_some_and(|duration| {
        progress.position_ms >= minimum_resume_position_ms
            && remaining_ms(duration, progress.position_ms) < minimum_remaining_ms as i128
    })
}

fn remaining_ms(duration_ms: u64, position_ms: u64) -> i128 {
    duration_ms as i128 - position_ms as i128
}

fn latest_by_media_id(progresses: &[PlaybackProgress]) -> BTreeMap<String, PlaybackProgress> {
    let mut latest = BTreeMap::<String, PlaybackProgress>::new();
    for progress in progresses {
        latest
            .entry(progress.media_id.clone())
            .and_modify(|existing| {
                if progress.updated_at_epoch_ms > existing.updated_at_epoch_ms {
                    *existing = progress.clone();
                }
            })
            .or_insert_with(|| progress.clone());
    }
    latest
}

#[derive(Debug)]
struct SeriesGroup<'a> {
    id: String,
    identified_items: Vec<(SeriesIdentity, &'a LibraryMediaItem)>,
}

impl SeriesGroup<'_> {
    fn into_output(self) -> GroupedSeriesOutput {
        let title = preferred_series_title(&self.identified_items);
        let mut seasons = Vec::<SeasonGroup>::new();
        let mut season_index_by_id = BTreeMap::<String, usize>::new();
        for (_, item) in &self.identified_items {
            let season_identity = SeasonIdentity::from_item(item);
            let index = if let Some(index) = season_index_by_id.get(&season_identity.id) {
                *index
            } else {
                let index = seasons.len();
                season_index_by_id.insert(season_identity.id.clone(), index);
                seasons.push(SeasonGroup {
                    identity: season_identity,
                    items: Vec::new(),
                });
                index
            };
            seasons[index].items.push(*item);
        }

        let mut season_outputs = seasons
            .into_iter()
            .map(|season| season.into_output(&self.id))
            .collect::<Vec<_>>();
        season_outputs.sort_by(|left, right| {
            left.sort_key
                .cmp(&right.sort_key)
                .then_with(|| left.label.to_lowercase().cmp(&right.label.to_lowercase()))
        });

        let flattened_items = self
            .identified_items
            .iter()
            .map(|(_, item)| *item)
            .collect::<Vec<_>>();
        let latest_indexed_media_id = flattened_items
            .iter()
            .max_by(|left, right| {
                left.relative_path
                    .to_lowercase()
                    .cmp(&right.relative_path.to_lowercase())
            })
            .map(|item| item.id.clone())
            .unwrap_or_default();

        GroupedSeriesOutput {
            id: self.id,
            title,
            episode_count: flattened_items.len(),
            subtitle_track_count: flattened_items
                .iter()
                .map(|item| item.subtitles.len())
                .sum(),
            total_size_bytes: flattened_items.iter().map(|item| item.size_bytes).sum(),
            latest_indexed_media_id,
            seasons: season_outputs,
        }
    }
}

#[derive(Debug)]
struct SeasonGroup<'a> {
    identity: SeasonIdentity,
    items: Vec<&'a LibraryMediaItem>,
}

impl SeasonGroup<'_> {
    fn into_output(mut self, series_id: &str) -> GroupedSeasonOutput {
        self.items.sort_by(library_item_title_compare);
        GroupedSeasonOutput {
            id: format!("{series_id}-{}", self.identity.id),
            label: self.identity.label,
            sort_key: self.identity.sort_key,
            item_ids: self.items.into_iter().map(|item| item.id.clone()).collect(),
        }
    }
}

#[derive(Debug, Clone)]
struct SeriesIdentity {
    id: String,
    title: String,
}

impl SeriesIdentity {
    fn from_item(item: &LibraryMediaItem) -> Self {
        if let Some(metadata) = &item.anime_metadata {
            return Self {
                id: format!(
                    "anime-{}-{}",
                    provider_stable_name(metadata.anime_id.provider),
                    metadata.anime_id.value
                ),
                title: metadata.display_title.clone(),
            };
        }
        let normalized_title = item.series_title.trim();
        let title = if normalized_title.is_empty() {
            "Series"
        } else {
            normalized_title
        };
        Self {
            id: stable_library_id(title),
            title: title.to_owned(),
        }
    }
}

#[derive(Debug, Clone)]
struct SeasonIdentity {
    id: String,
    label: String,
    sort_key: i32,
}

impl SeasonIdentity {
    fn from_item(item: &LibraryMediaItem) -> Self {
        let searchable_text = format!("{} {}", item.relative_path, item.episode_title);
        if let Some(season_number) = find_season_number(&searchable_text) {
            Self {
                id: format!("season-{season_number:02}"),
                label: format!("Season {season_number}"),
                sort_key: season_number as i32,
            }
        } else {
            Self {
                id: "season-unknown".to_owned(),
                label: "Season unknown".to_owned(),
                sort_key: i32::MAX,
            }
        }
    }
}

fn preferred_series_title(items: &[(SeriesIdentity, &LibraryMediaItem)]) -> String {
    let mut counts = BTreeMap::<String, usize>::new();
    for (identity, _) in items {
        *counts.entry(identity.title.clone()).or_default() += 1;
    }
    counts
        .into_iter()
        .min_by(|(left_title, left_count), (right_title, right_count)| {
            right_count
                .cmp(left_count)
                .then_with(|| left_title.len().cmp(&right_title.len()))
                .then_with(|| left_title.cmp(right_title))
        })
        .map(|(title, _)| title)
        .unwrap_or_else(|| "Series".to_owned())
}

fn library_item_title_compare(left: &&LibraryMediaItem, right: &&LibraryMediaItem) -> Ordering {
    episode_sort_key(left)
        .cmp(&episode_sort_key(right))
        .then_with(|| {
            left.episode_title
                .to_lowercase()
                .cmp(&right.episode_title.to_lowercase())
        })
        .then_with(|| {
            left.relative_path
                .to_lowercase()
                .cmp(&right.relative_path.to_lowercase())
        })
}

fn episode_sort_key(item: &LibraryMediaItem) -> i32 {
    let searchable_text = format!("{} {}", item.episode_title, item.relative_path);
    find_episode_number(&searchable_text)
        .map(|value| value as i32)
        .unwrap_or(i32::MAX)
}

fn stable_library_id(value: &str) -> String {
    let mut normalized = String::new();
    let mut previous_dash = false;
    for char in value.trim().to_lowercase().chars() {
        let mapped = if char.is_alphanumeric() { char } else { '-' };
        if mapped == '-' {
            if !previous_dash {
                normalized.push(mapped);
                previous_dash = true;
            }
        } else {
            normalized.push(mapped);
            previous_dash = false;
        }
    }
    let trimmed = normalized.trim_matches('-').to_owned();
    if trimmed.is_empty() {
        "series".to_owned()
    } else {
        trimmed
    }
}

fn provider_stable_name(provider: ExternalAnimeProvider) -> &'static str {
    match provider {
        ExternalAnimeProvider::MyAnimeList => "my_anime_list",
        ExternalAnimeProvider::Bangumi => "bangumi",
        ExternalAnimeProvider::Dandanplay => "dandanplay",
    }
}

#[cfg(test)]
mod tests {
    use std::fs;
    use std::path::PathBuf;

    use serde::Deserialize;
    use serde_json::{Value, json};

    use super::*;

    #[derive(Debug, Deserialize)]
    #[serde(rename_all = "camelCase")]
    struct DomainFixture {
        schema_version: u32,
        feature: String,
        input: DomainFixtureInput,
        expected: Value,
    }

    #[derive(Debug, Deserialize)]
    struct DomainFixtureInput {
        catalog: LibraryCatalog,
        #[serde(default)]
        progress: Vec<PlaybackProgress>,
        #[serde(default)]
        options: DomainFixtureOptions,
    }

    #[derive(Debug, Default, Deserialize)]
    #[serde(rename_all = "camelCase")]
    struct DomainFixtureOptions {
        limit: Option<usize>,
        minimum_resume_position_ms: Option<u64>,
        minimum_remaining_ms: Option<u64>,
        minimum_started_position_ms: Option<u64>,
        watched_remaining_ms: Option<u64>,
    }

    #[test]
    fn domain_conformance_fixtures_match_kotlin_outputs() {
        for path in fixture_paths() {
            let text = fs::read_to_string(path).expect("fixture should read");
            let fixture =
                serde_json::from_str::<DomainFixture>(&text).expect("fixture should decode");
            assert_eq!(1, fixture.schema_version);
            let actual = actual_output(&fixture);
            assert_eq!(
                fixture.expected, actual,
                "fixture failed: {}",
                fixture.feature
            );
        }
    }

    fn actual_output(fixture: &DomainFixture) -> Value {
        let input = &fixture.input;
        match fixture.feature.as_str() {
            "series-grouping" => json!({
                "groupedSeries": grouped_series(&input.catalog),
            }),
            "watch-state" => json!({
                "watchStatusByMediaId": watch_status_by_media_id(
                    &input.catalog,
                    &input.progress,
                    input.options.minimum_started_position_ms.unwrap_or(10_000),
                    input.options.watched_remaining_ms.unwrap_or(30_000),
                ),
                "seriesWatchSummaryById": series_watch_summary_by_id(
                    &input.catalog,
                    &input.progress,
                    input.options.minimum_started_position_ms.unwrap_or(10_000),
                    input.options.watched_remaining_ms.unwrap_or(30_000),
                ),
            }),
            "next-up" => json!({
                "nextUp": next_up_items(
                    &input.catalog,
                    &input.progress,
                    input.options.limit.unwrap_or(8),
                    input.options.minimum_resume_position_ms.unwrap_or(10_000),
                    input.options.minimum_remaining_ms.unwrap_or(30_000),
                ),
            }),
            "continue-watching" => json!({
                "continueWatching": continue_watching(
                    &input.catalog,
                    &input.progress,
                    input.options.limit.unwrap_or(8),
                    input.options.minimum_resume_position_ms.unwrap_or(10_000),
                    input.options.minimum_remaining_ms.unwrap_or(30_000),
                ),
            }),
            other => panic!("unsupported fixture feature: {other}"),
        }
    }

    fn fixture_paths() -> Vec<PathBuf> {
        let root = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
            .join("..")
            .join("..")
            .join("shared")
            .join("domain")
            .join("src")
            .join("jvmTest")
            .join("resources")
            .join("domain-conformance-fixtures");
        vec![
            root.join("series-grouping.json"),
            root.join("watch-state.json"),
            root.join("next-up.json"),
            root.join("continue-watching.json"),
        ]
    }
}
