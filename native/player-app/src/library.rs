//! LAN library client: wire models and the domain rules ported from
//! `shared/domain` (series grouping, continue-watching, next-up, resume).
//!
//! The Kotlin implementations are the reference; the JSON conformance
//! fixtures under `shared/domain/src/jvmTest/resources` assert identical
//! results in this crate's tests.

use serde::Deserialize;

use crate::net::{http_get, http_put_json, percent_encode_path_segment};

pub const DEFAULT_NEXT_UP_LIMIT: usize = 8;
pub const MINIMUM_RESUME_POSITION_MS: i64 = 10_000;
pub const MINIMUM_REMAINING_MS: i64 = 30_000;

#[derive(Clone, Debug, Default, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase", default)]
pub struct LibraryCatalog {
    pub root_name: String,
    pub indexed_at_epoch_ms: i64,
    pub items: Vec<MediaItem>,
}

#[derive(Clone, Debug, Default, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase", default)]
pub struct MediaItem {
    pub id: String,
    pub series_title: String,
    pub episode_title: String,
    pub relative_path: String,
    pub size_bytes: i64,
    pub media_type: String,
    pub stream_path: String,
    pub indexed_at_epoch_ms: i64,
    pub subtitles: Vec<SubtitleTrack>,
    pub poster_path: Option<String>,
    pub anime_metadata: Option<AnimeMetadata>,
}

#[derive(Clone, Debug, Default, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase", default)]
pub struct SubtitleTrack {
    pub id: String,
    pub label: String,
    pub relative_path: String,
    pub media_type: String,
    pub stream_path: String,
}

#[derive(Clone, Debug, Default, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase", default)]
pub struct AnimeMetadata {
    pub anime_id: ExternalAnimeId,
    pub display_title: String,
    pub image_url: Option<String>,
    pub episode_count: Option<u32>,
    pub start_year: Option<i32>,
}

#[derive(Clone, Debug, Default, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase", default)]
pub struct ExternalAnimeId {
    pub provider: String,
    pub value: i64,
}

#[derive(Clone, Debug, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct PlaybackProgress {
    pub media_id: String,
    pub position_ms: i64,
    #[serde(default)]
    pub duration_ms: Option<i64>,
    pub updated_at_epoch_ms: i64,
}

impl PlaybackProgress {
    /// Port of `PlaybackProgress.resumePositionMs`.
    pub fn resume_position_ms(
        &self,
        minimum_position_ms: i64,
        minimum_remaining_ms: i64,
    ) -> Option<i64> {
        let qualifies = self.position_ms >= minimum_position_ms
            && self
                .duration_ms
                .is_none_or(|duration| duration - self.position_ms >= minimum_remaining_ms);
        qualifies.then_some(self.position_ms)
    }

    fn is_near_end(&self, minimum_resume_position_ms: i64, minimum_remaining_ms: i64) -> bool {
        match self.duration_ms {
            Some(duration) => {
                self.position_ms >= minimum_resume_position_ms
                    && duration - self.position_ms < minimum_remaining_ms
            }
            None => false,
        }
    }

    fn to_progress_json(&self) -> String {
        // Manual encode keeps the wire shape explicit (no serde Serialize
        // derives needed for a single body type).
        match self.duration_ms {
            Some(duration) => format!(
                "{{\"mediaId\":{},\"positionMs\":{},\"durationMs\":{},\"updatedAtEpochMs\":{}}}",
                serde_json::to_string(&self.media_id).expect("string encodes"),
                self.position_ms,
                duration,
                self.updated_at_epoch_ms,
            ),
            None => format!(
                "{{\"mediaId\":{},\"positionMs\":{},\"updatedAtEpochMs\":{}}}",
                serde_json::to_string(&self.media_id).expect("string encodes"),
                self.position_ms,
                self.updated_at_epoch_ms,
            ),
        }
    }
}

impl LibraryCatalog {
    /// Port of `LibraryCatalog.previousItem`.
    pub fn previous_item(&self, current_item_id: &str) -> Option<&MediaItem> {
        let index = self
            .items
            .iter()
            .position(|item| item.id == current_item_id)?;
        index.checked_sub(1).and_then(|index| self.items.get(index))
    }

    /// Port of `LibraryCatalog.nextItem`.
    pub fn next_item(&self, current_item_id: &str) -> Option<&MediaItem> {
        let index = self
            .items
            .iter()
            .position(|item| item.id == current_item_id)?;
        self.items.get(index + 1)
    }

    pub fn item(&self, item_id: &str) -> Option<&MediaItem> {
        self.items.iter().find(|item| item.id == item_id)
    }
}

// ---------------------------------------------------------------------------
// Series grouping (port of LibrarySeries.kt)
// ---------------------------------------------------------------------------

#[derive(Clone, Debug, PartialEq)]
pub struct Series {
    pub id: String,
    pub title: String,
    pub seasons: Vec<Season>,
}

impl Series {
    pub fn episode_count(&self) -> usize {
        self.seasons.iter().map(|season| season.items.len()).sum()
    }

    pub fn items(&self) -> impl Iterator<Item = &MediaItem> {
        self.seasons.iter().flat_map(|season| season.items.iter())
    }
}

#[derive(Clone, Debug, PartialEq)]
pub struct Season {
    pub id: String,
    pub label: String,
    pub sort_key: i64,
    pub items: Vec<MediaItem>,
}

/// Port of `LibraryCatalog.groupedSeries()`.
pub fn grouped_series(catalog: &LibraryCatalog) -> Vec<Series> {
    // Group items by series identity while preserving first-seen order for
    // deterministic tie-breaks that match Kotlin's stable groupBy.
    let mut order: Vec<String> = Vec::new();
    let mut groups: std::collections::HashMap<String, Vec<(SeriesIdentity, MediaItem)>> =
        std::collections::HashMap::new();
    for item in &catalog.items {
        let identity = SeriesIdentity::from_item(item);
        let entry = groups.entry(identity.id.clone()).or_default();
        if entry.is_empty() {
            order.push(identity.id.clone());
        }
        entry.push((identity, item.clone()));
    }

    let mut series: Vec<Series> = order
        .into_iter()
        .map(|series_id| {
            let identified = groups.remove(&series_id).expect("group exists");
            let title = preferred_series_title(&identified);
            let mut season_order: Vec<SeasonIdentity> = Vec::new();
            let mut seasons: std::collections::HashMap<String, Vec<MediaItem>> =
                std::collections::HashMap::new();
            for (_, item) in &identified {
                let season_identity = season_identity(item);
                let entry = seasons.entry(season_identity.id.clone()).or_default();
                if entry.is_empty() {
                    season_order.push(season_identity.clone());
                }
                entry.push(item.clone());
            }
            let mut seasons: Vec<Season> = season_order
                .into_iter()
                .map(|identity| {
                    let mut items = seasons.remove(&identity.id).expect("season exists");
                    items.sort_by(|left, right| {
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
                    });
                    Season {
                        id: format!("{series_id}-{}", identity.id),
                        label: identity.label,
                        sort_key: identity.sort_key,
                        items,
                    }
                })
                .collect();
            seasons.sort_by(|left, right| {
                left.sort_key
                    .cmp(&right.sort_key)
                    .then_with(|| left.label.to_lowercase().cmp(&right.label.to_lowercase()))
            });
            Series {
                id: series_id,
                title,
                seasons,
            }
        })
        .collect();

    series.sort_by(|left, right| {
        right
            .episode_count()
            .cmp(&left.episode_count())
            .then_with(|| left.title.to_lowercase().cmp(&right.title.to_lowercase()))
            .then_with(|| left.id.cmp(&right.id))
    });
    series
}

#[derive(Clone, Debug)]
struct SeriesIdentity {
    id: String,
    title: String,
}

impl SeriesIdentity {
    fn from_item(item: &MediaItem) -> Self {
        if let Some(metadata) = &item.anime_metadata
            && !metadata.display_title.is_empty()
        {
            return Self {
                id: format!(
                    "anime-{}-{}",
                    metadata.anime_id.provider.to_lowercase(),
                    metadata.anime_id.value
                ),
                title: metadata.display_title.clone(),
            };
        }
        let normalized = item.series_title.trim();
        let normalized = if normalized.is_empty() {
            "Series"
        } else {
            normalized
        };
        Self {
            id: stable_library_id(normalized),
            title: normalized.to_owned(),
        }
    }
}

fn preferred_series_title(identified: &[(SeriesIdentity, MediaItem)]) -> String {
    let mut counts: Vec<(String, usize)> = Vec::new();
    for (identity, _) in identified {
        if let Some(entry) = counts
            .iter_mut()
            .find(|(title, _)| *title == identity.title)
        {
            entry.1 += 1;
        } else {
            counts.push((identity.title.clone(), 1));
        }
    }
    counts.sort_by(|left, right| {
        right
            .1
            .cmp(&left.1)
            .then_with(|| left.0.len().cmp(&right.0.len()))
            .then_with(|| left.0.cmp(&right.0))
    });
    counts
        .into_iter()
        .next()
        .map(|(title, _)| title)
        .unwrap_or_else(|| "Series".to_owned())
}

#[derive(Clone, Debug)]
struct SeasonIdentity {
    id: String,
    label: String,
    sort_key: i64,
}

fn season_identity(item: &MediaItem) -> SeasonIdentity {
    let searchable = format!("{} {}", item.relative_path, item.episode_title);
    let season_number = find_season_number(&searchable);
    match season_number {
        Some(number) => SeasonIdentity {
            id: format!("season-{number:02}"),
            label: format!("Season {number}"),
            sort_key: number,
        },
        None => SeasonIdentity {
            id: "season-unknown".to_owned(),
            label: "Season unknown".to_owned(),
            // Kotlin uses Int.MAX_VALUE; keep the value identical so sortKey
            // round-trips through the conformance fixtures.
            sort_key: i64::from(i32::MAX),
        },
    }
}

/// Port of the Kotlin season regexes `\bseason\s*([0-9]{1,2})\b` then
/// `\bs([0-9]{1,2})\b`, case-insensitive, without a regex dependency.
fn find_season_number(text: &str) -> Option<i64> {
    find_keyword_number(text, "season", 2, true)
        .or_else(|| find_keyword_number(text, "s", 2, false))
}

/// Port of the Kotlin episode regexes `\bepisode\s*(\d{1,4})`, `\bep\s*(\d{1,4})`,
/// `\be(\d{1,4})`.
fn episode_sort_key(item: &MediaItem) -> i64 {
    let searchable = format!("{} {}", item.episode_title, item.relative_path);
    find_keyword_number(&searchable, "episode", 4, true)
        .or_else(|| find_keyword_number(&searchable, "ep", 4, true))
        .or_else(|| find_keyword_number(&searchable, "e", 4, false))
        .unwrap_or(i64::MAX)
}

/// Finds `keyword` at a word boundary followed by (optional whitespace when
/// `allow_gap`) then 1..=max_digits digits ending at a word boundary.
fn find_keyword_number(
    text: &str,
    keyword: &str,
    max_digits: usize,
    allow_gap: bool,
) -> Option<i64> {
    let lower = text.to_lowercase();
    let bytes = lower.as_bytes();
    let mut search_from = 0;
    while let Some(offset) = lower[search_from..].find(keyword) {
        let start = search_from + offset;
        search_from = start + 1;
        // Word boundary before the keyword.
        if start > 0 {
            let previous = bytes[start - 1] as char;
            if previous.is_ascii_alphanumeric() {
                continue;
            }
        }
        let mut cursor = start + keyword.len();
        if allow_gap {
            while cursor < bytes.len() && (bytes[cursor] as char).is_whitespace() {
                cursor += 1;
            }
        }
        let digits_start = cursor;
        while cursor < bytes.len()
            && (bytes[cursor] as char).is_ascii_digit()
            && cursor - digits_start < max_digits
        {
            cursor += 1;
        }
        if cursor == digits_start {
            continue;
        }
        // Word boundary after the digits.
        if cursor < bytes.len() && (bytes[cursor] as char).is_ascii_alphanumeric() {
            continue;
        }
        if let Ok(value) = lower[digits_start..cursor].parse::<i64>() {
            return Some(value);
        }
    }
    None
}

fn stable_library_id(value: &str) -> String {
    let mut normalized = String::new();
    let mut previous_dash = false;
    for character in value.trim().to_lowercase().chars() {
        if character.is_alphanumeric() {
            normalized.push(character);
            previous_dash = false;
        } else if !previous_dash {
            normalized.push('-');
            previous_dash = true;
        }
    }
    let normalized = normalized.trim_matches('-').to_owned();
    if normalized.is_empty() {
        "series".to_owned()
    } else {
        normalized
    }
}

// ---------------------------------------------------------------------------
// Continue watching and next-up (ports of LibraryPlaybackProgress.kt and
// LibraryNextUp.kt)
// ---------------------------------------------------------------------------

#[derive(Clone, Debug, PartialEq)]
pub struct ProgressItem {
    pub item: MediaItem,
    pub progress: PlaybackProgress,
}

fn latest_by_media_id(
    progresses: &[PlaybackProgress],
) -> std::collections::HashMap<&str, &PlaybackProgress> {
    let mut latest: std::collections::HashMap<&str, &PlaybackProgress> =
        std::collections::HashMap::new();
    for progress in progresses {
        match latest.get(progress.media_id.as_str()) {
            Some(existing) if existing.updated_at_epoch_ms >= progress.updated_at_epoch_ms => {}
            _ => {
                latest.insert(progress.media_id.as_str(), progress);
            }
        }
    }
    latest
}

/// Port of `LibraryCatalog.continueWatchingItems`.
pub fn continue_watching_items(
    catalog: &LibraryCatalog,
    progresses: &[PlaybackProgress],
    limit: usize,
    minimum_resume_position_ms: i64,
    minimum_remaining_ms: i64,
) -> Vec<ProgressItem> {
    if limit == 0 {
        return Vec::new();
    }
    let latest = latest_by_media_id(progresses);
    let mut entries: Vec<ProgressItem> = catalog
        .items
        .iter()
        .filter_map(|item| {
            let progress = latest.get(item.id.as_str())?;
            progress
                .resume_position_ms(minimum_resume_position_ms, minimum_remaining_ms)
                .map(|_| ProgressItem {
                    item: item.clone(),
                    progress: (*progress).clone(),
                })
        })
        .collect();
    entries.sort_by_key(|entry| std::cmp::Reverse(entry.progress.updated_at_epoch_ms));
    entries.truncate(limit);
    entries
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum NextUpReason {
    Resume,
    NextEpisode,
    Start,
}

#[derive(Clone, Debug, PartialEq)]
pub struct NextUpItem {
    pub item: MediaItem,
    pub reason: NextUpReason,
    pub progress: Option<PlaybackProgress>,
    pub source_progress: Option<PlaybackProgress>,
}

/// Port of `LibraryCatalog.nextUpItems`.
pub fn next_up_items(
    catalog: &LibraryCatalog,
    progresses: &[PlaybackProgress],
    limit: usize,
    minimum_resume_position_ms: i64,
    minimum_remaining_ms: i64,
) -> Vec<NextUpItem> {
    if limit == 0 || catalog.items.is_empty() {
        return Vec::new();
    }
    let newest_by_media_id = latest_by_media_id(progresses);
    let mut candidates: Vec<NextUpItem> = Vec::new();

    let mut sorted: Vec<&PlaybackProgress> = progresses
        .iter()
        .filter(|progress| catalog.item(&progress.media_id).is_some())
        .collect();
    sorted.sort_by_key(|progress| std::cmp::Reverse(progress.updated_at_epoch_ms));

    for progress in sorted {
        let item = catalog.item(&progress.media_id).expect("filtered above");
        if progress
            .resume_position_ms(minimum_resume_position_ms, minimum_remaining_ms)
            .is_some()
        {
            candidates.push(NextUpItem {
                item: item.clone(),
                reason: NextUpReason::Resume,
                progress: Some(progress.clone()),
                source_progress: Some(progress.clone()),
            });
        } else if progress.is_near_end(minimum_resume_position_ms, minimum_remaining_ms)
            && let Some(next) = catalog.next_item(&progress.media_id)
            && !newest_by_media_id.contains_key(next.id.as_str())
        {
            candidates.push(NextUpItem {
                item: next.clone(),
                reason: NextUpReason::NextEpisode,
                progress: None,
                source_progress: Some(progress.clone()),
            });
        }
    }

    if candidates.is_empty() {
        candidates.push(NextUpItem {
            item: catalog.items[0].clone(),
            reason: NextUpReason::Start,
            progress: None,
            source_progress: None,
        });
    }

    let mut seen = std::collections::HashSet::new();
    candidates.retain(|candidate| seen.insert(candidate.item.id.clone()));
    candidates.truncate(limit);
    candidates
}

// ---------------------------------------------------------------------------
// Wire fetch/upload helpers (blocking; call from a background thread)
// ---------------------------------------------------------------------------

pub fn fetch_catalog(base_url: &str) -> Result<LibraryCatalog, String> {
    let body = http_get(base_url, "/api/library")?;
    serde_json::from_str(&body).map_err(|error| format!("invalid catalog JSON: {error}"))
}

pub fn fetch_progress_list(base_url: &str) -> Result<Vec<PlaybackProgress>, String> {
    let body = http_get(base_url, "/api/progress")?;
    serde_json::from_str(&body).map_err(|error| format!("invalid progress JSON: {error}"))
}

pub fn fetch_progress(base_url: &str, media_id: &str) -> Result<Option<PlaybackProgress>, String> {
    let path = format!("/api/progress/{}", percent_encode_path_segment(media_id));
    let response = crate::net::http_get_raw(base_url, &path)?;
    match response.status {
        200 => {
            let body = response.body_string()?;
            serde_json::from_str(&body)
                .map(Some)
                .map_err(|error| format!("invalid progress JSON: {error}"))
        }
        404 => Ok(None),
        status => Err(format!("server returned HTTP {status}")),
    }
}

pub fn upload_progress(base_url: &str, progress: &PlaybackProgress) -> Result<(), String> {
    let path = format!(
        "/api/progress/{}",
        percent_encode_path_segment(&progress.media_id)
    );
    http_put_json(base_url, &path, &progress.to_progress_json()).map(|_| ())
}

pub fn fetch_server_status(base_url: &str) -> Result<String, String> {
    http_get(base_url, "/api/server/status")
}

/// Absolute URL for a server-relative path such as `streamPath`/`posterPath`.
pub fn absolute_url(base_url: &str, server_path: &str) -> String {
    format!("{}{}", base_url.trim_end_matches('/'), server_path)
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::Value;

    fn fixture(name: &str) -> Value {
        let path = concat!(
            env!("CARGO_MANIFEST_DIR"),
            "/../../shared/domain/src/jvmTest/resources/domain-conformance-fixtures/"
        );
        let source =
            std::fs::read_to_string(format!("{path}{name}.json")).expect("fixture file readable");
        serde_json::from_str(&source).expect("fixture JSON parses")
    }

    fn catalog_from(input: &Value) -> LibraryCatalog {
        serde_json::from_value(input["catalog"].clone()).expect("catalog parses")
    }

    fn progresses_from(input: &Value) -> Vec<PlaybackProgress> {
        serde_json::from_value(input["progress"].clone()).expect("progress parses")
    }

    #[test]
    fn next_up_matches_domain_conformance_fixture() {
        let fixture = fixture("next-up");
        let input = &fixture["input"];
        let catalog = catalog_from(input);
        let progresses = progresses_from(input);
        let options = &input["options"];

        let actual = next_up_items(
            &catalog,
            &progresses,
            options["limit"].as_u64().unwrap() as usize,
            options["minimumResumePositionMs"].as_i64().unwrap(),
            options["minimumRemainingMs"].as_i64().unwrap(),
        );

        let expected = fixture["expected"]["nextUp"].as_array().unwrap();
        assert_eq!(actual.len(), expected.len());
        for (actual, expected) in actual.iter().zip(expected) {
            assert_eq!(actual.item.id, expected["mediaId"].as_str().unwrap());
            let reason = match actual.reason {
                NextUpReason::Resume => "RESUME",
                NextUpReason::NextEpisode => "NEXT_EPISODE",
                NextUpReason::Start => "START",
            };
            assert_eq!(reason, expected["reason"].as_str().unwrap());
            assert_eq!(
                actual
                    .progress
                    .as_ref()
                    .map(|progress| progress.media_id.as_str()),
                expected["progressMediaId"].as_str(),
            );
            assert_eq!(
                actual
                    .source_progress
                    .as_ref()
                    .map(|progress| progress.media_id.as_str()),
                expected["sourceProgressMediaId"].as_str(),
            );
        }
    }

    #[test]
    fn continue_watching_matches_domain_conformance_fixture() {
        let fixture = fixture("continue-watching");
        let input = &fixture["input"];
        let catalog = catalog_from(input);
        let progresses = progresses_from(input);
        let options = &input["options"];

        let actual = continue_watching_items(
            &catalog,
            &progresses,
            options["limit"].as_u64().unwrap() as usize,
            options["minimumResumePositionMs"].as_i64().unwrap(),
            options["minimumRemainingMs"].as_i64().unwrap(),
        );

        let expected = fixture["expected"]["continueWatching"].as_array().unwrap();
        assert_eq!(actual.len(), expected.len());
        for (actual, expected) in actual.iter().zip(expected) {
            assert_eq!(actual.item.id, expected["mediaId"].as_str().unwrap());
            assert_eq!(
                actual.progress.position_ms,
                expected["progress"]["positionMs"].as_i64().unwrap(),
            );
            assert_eq!(
                actual.progress.updated_at_epoch_ms,
                expected["progress"]["updatedAtEpochMs"].as_i64().unwrap(),
            );
        }
    }

    #[test]
    fn series_grouping_matches_domain_conformance_fixture() {
        let fixture = fixture("series-grouping");
        let catalog = catalog_from(&fixture["input"]);

        let actual = grouped_series(&catalog);

        let expected = fixture["expected"]["groupedSeries"].as_array().unwrap();
        assert_eq!(actual.len(), expected.len());
        for (actual, expected) in actual.iter().zip(expected) {
            assert_eq!(actual.id, expected["id"].as_str().unwrap());
            assert_eq!(actual.title, expected["title"].as_str().unwrap());
            assert_eq!(
                actual.episode_count() as u64,
                expected["episodeCount"].as_u64().unwrap(),
            );
            let expected_seasons = expected["seasons"].as_array().unwrap();
            assert_eq!(actual.seasons.len(), expected_seasons.len());
            for (actual_season, expected_season) in actual.seasons.iter().zip(expected_seasons) {
                assert_eq!(actual_season.id, expected_season["id"].as_str().unwrap());
                assert_eq!(
                    actual_season.label,
                    expected_season["label"].as_str().unwrap(),
                );
                assert_eq!(
                    actual_season.sort_key,
                    expected_season["sortKey"].as_i64().unwrap(),
                );
                let expected_items: Vec<&str> = expected_season["itemIds"]
                    .as_array()
                    .unwrap()
                    .iter()
                    .map(|value| value.as_str().unwrap())
                    .collect();
                let actual_items: Vec<&str> = actual_season
                    .items
                    .iter()
                    .map(|item| item.id.as_str())
                    .collect();
                assert_eq!(actual_items, expected_items);
            }
        }
    }

    #[test]
    fn resume_position_honors_minimums() {
        let progress = PlaybackProgress {
            media_id: "one".to_owned(),
            position_ms: 15_000,
            duration_ms: Some(1_200_000),
            updated_at_epoch_ms: 1,
        };
        assert_eq!(progress.resume_position_ms(10_000, 30_000), Some(15_000));
        assert_eq!(progress.resume_position_ms(20_000, 30_000), None);

        let near_end = PlaybackProgress {
            position_ms: 1_190_000,
            ..progress.clone()
        };
        assert_eq!(near_end.resume_position_ms(10_000, 30_000), None);
        assert!(near_end.is_near_end(10_000, 30_000));
    }

    #[test]
    fn next_and_previous_follow_catalog_order() {
        let catalog: LibraryCatalog = serde_json::from_value(serde_json::json!({
            "rootName": "root",
            "indexedAtEpochMs": 0,
            "items": [
                {"id": "a", "seriesTitle": "S", "episodeTitle": "E1", "relativePath": "S/E1.mkv", "sizeBytes": 1, "mediaType": "video/x-matroska", "streamPath": "/media/a"},
                {"id": "b", "seriesTitle": "S", "episodeTitle": "E2", "relativePath": "S/E2.mkv", "sizeBytes": 1, "mediaType": "video/x-matroska", "streamPath": "/media/b"}
            ]
        }))
        .expect("catalog parses");

        assert_eq!(
            catalog.next_item("a").map(|item| item.id.as_str()),
            Some("b")
        );
        assert_eq!(
            catalog.previous_item("b").map(|item| item.id.as_str()),
            Some("a")
        );
        assert_eq!(catalog.next_item("b"), None);
        assert_eq!(catalog.next_item("missing"), None);
    }

    #[test]
    fn progress_json_matches_wire_shape() {
        let progress = PlaybackProgress {
            media_id: "abc".to_owned(),
            position_ms: 12_345,
            duration_ms: Some(98_765),
            updated_at_epoch_ms: 1_700_000_100_000,
        };
        assert_eq!(
            progress.to_progress_json(),
            r#"{"mediaId":"abc","positionMs":12345,"durationMs":98765,"updatedAtEpochMs":1700000100000}"#,
        );
    }
}
