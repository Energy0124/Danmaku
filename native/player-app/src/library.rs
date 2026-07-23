//! LAN library client: wire models and the domain rules ported from
//! `shared/domain` (series grouping, continue-watching, next-up, resume).
//!
//! The Kotlin implementations are the reference; the JSON conformance
//! fixtures under `shared/domain/src/jvmTest/resources` assert identical
//! results in this crate's tests.

use serde::{Deserialize, Serialize};
use std::collections::HashMap;

use crate::net::{http_get, http_put_json, percent_encode_path_segment};

pub const DEFAULT_NEXT_UP_LIMIT: usize = 8;
pub const MINIMUM_RESUME_POSITION_MS: i64 = 10_000;
pub const MINIMUM_REMAINING_MS: i64 = 30_000;

#[derive(Clone, Debug, Default, Deserialize, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", default)]
pub struct LibraryCatalog {
    pub root_name: String,
    pub indexed_at_epoch_ms: i64,
    pub items: Vec<MediaItem>,
}

#[derive(Clone, Debug, Default, Deserialize, PartialEq, Serialize)]
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
    /// Absolute path of the library root this item was scanned from
    /// (e.g. `M:\Anime`); absent on servers that predate multi-root
    /// attribution.
    pub root_label: Option<String>,
    pub anime_metadata: Option<AnimeMetadata>,
}

#[derive(Clone, Debug, Default, Deserialize, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", default)]
pub struct SubtitleTrack {
    pub id: String,
    pub label: String,
    pub relative_path: String,
    pub media_type: String,
    pub stream_path: String,
}

#[derive(Clone, Debug, Default, Deserialize, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", default)]
pub struct AnimeMetadata {
    pub anime_id: ExternalAnimeId,
    pub display_title: String,
    pub chinese_title: Option<String>,
    pub english_title: Option<String>,
    pub japanese_title: Option<String>,
    pub alternate_names: Vec<String>,
    pub external_links: Vec<ExternalAnimeLink>,
    pub image_url: Option<String>,
    pub episode_count: Option<u32>,
    pub start_year: Option<i32>,
}

#[derive(Clone, Debug, Default, Deserialize, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", default)]
pub struct ExternalAnimeId {
    pub provider: String,
    pub value: i64,
}

impl ExternalAnimeId {
    /// Human-readable database name, mirroring the official client's
    /// online-database link row.
    pub fn provider_name(&self) -> &'static str {
        match self.provider.as_str() {
            "MY_ANIME_LIST" => "MyAnimeList",
            "BANGUMI" => "Bangumi.tv",
            "DANDANPLAY" => "dandanplay",
            _ => "Database",
        }
    }

    /// Public web page for this identity; mirrors the server's
    /// `ExternalAnimeId.web_url`, which the wire format omits when
    /// derivable.
    pub fn web_url(&self) -> Option<String> {
        match self.provider.as_str() {
            "MY_ANIME_LIST" => Some(format!("https://myanimelist.net/anime/{}", self.value)),
            "BANGUMI" => Some(format!("https://bangumi.tv/subject/{}", self.value)),
            "DANDANPLAY" => Some(format!("https://www.dandanplay.com/bangumi/{}", self.value)),
            _ => None,
        }
    }
}

/// A recognized identity in an external anime database. The server omits
/// `url` when it equals the provider's canonical web URL.
#[derive(Clone, Debug, Default, Deserialize, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", default)]
pub struct ExternalAnimeLink {
    pub anime_id: ExternalAnimeId,
    pub url: Option<String>,
}

impl ExternalAnimeLink {
    pub fn web_url(&self) -> Option<String> {
        self.url.clone().or_else(|| self.anime_id.web_url())
    }
}

#[derive(Clone, Debug, Deserialize, PartialEq, Serialize)]
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

/// Port of `LibraryCatalog.groupedSeries()`. Groups by recognized anime
/// identity when an item has one, falling back to its folder title
/// otherwise — the same item can therefore land in an anime-identified group
/// alongside folder-identified groups. Kept pixel-identical to the Kotlin
/// reference (conformance-tested); see `folder_grouped_series` for a view
/// that never mixes the two.
pub fn grouped_series(catalog: &LibraryCatalog) -> Vec<Series> {
    grouped_series_with_identity(catalog, SeriesIdentity::from_item)
}

/// Groups every item strictly by its folder (`series_title`), ignoring any
/// recognized anime metadata. Pairs with `matched_anime_series` to give the
/// library two separate, non-mixed views: "by folder" (this) and "by
/// matched anime".
pub fn folder_grouped_series(catalog: &LibraryCatalog) -> Vec<Series> {
    grouped_series_with_identity(catalog, SeriesIdentity::from_folder)
}

/// Groups only items with a recognized anime match, by that identity. Every
/// item passed to `grouped_series` here has `anime_metadata`, so it always
/// takes the anime-identity branch — never the folder-title fallback — which
/// is what keeps this view from mixing in unmatched folders.
pub fn matched_anime_series(catalog: &LibraryCatalog) -> Vec<Series> {
    let recognized: Vec<MediaItem> = catalog
        .items
        .iter()
        .filter(|item| item.anime_metadata.is_some())
        .cloned()
        .collect();
    grouped_series(&LibraryCatalog {
        root_name: catalog.root_name.clone(),
        indexed_at_epoch_ms: catalog.indexed_at_epoch_ms,
        items: recognized,
    })
}

// ---------------------------------------------------------------------------
// Folder explorer listing (file-manager style browse of relative paths)
// ---------------------------------------------------------------------------

#[derive(Clone, Debug, Default, PartialEq)]
pub struct FolderListing {
    pub folders: Vec<FolderEntry>,
    pub files: Vec<MediaItem>,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct FolderEntry {
    pub name: String,
    pub item_count: usize,
}

/// Lists the immediate children of `path` (folder components of the items'
/// `relative_path`s): subfolders with their recursive item counts, then the
/// media files directly inside. Both are sorted case-insensitively by name.
pub fn folder_listing(catalog: &LibraryCatalog, path: &[String]) -> FolderListing {
    folder_listing_of(catalog.items.iter(), path)
}

fn folder_listing_of<'a>(
    items: impl Iterator<Item = &'a MediaItem>,
    path: &[String],
) -> FolderListing {
    let mut folder_counts: Vec<FolderEntry> = Vec::new();
    let mut files: Vec<MediaItem> = Vec::new();
    for item in items {
        let segments: Vec<&str> = item
            .relative_path
            .split(['/', '\\'])
            .filter(|segment| !segment.is_empty())
            .collect();
        // Skip items outside the current path.
        if segments.len() <= path.len()
            || !path
                .iter()
                .zip(&segments)
                .all(|(wanted, actual)| wanted == actual)
        {
            continue;
        }
        if segments.len() == path.len() + 1 {
            files.push(item.clone());
        } else {
            let name = segments[path.len()];
            match folder_counts.iter_mut().find(|entry| entry.name == name) {
                Some(entry) => entry.item_count += 1,
                None => folder_counts.push(FolderEntry {
                    name: name.to_owned(),
                    item_count: 1,
                }),
            }
        }
    }
    folder_counts.sort_by(|left, right| left.name.to_lowercase().cmp(&right.name.to_lowercase()));
    files.sort_by(|left, right| {
        file_name(&left.relative_path)
            .to_lowercase()
            .cmp(&file_name(&right.relative_path).to_lowercase())
    });
    FolderListing {
        folders: folder_counts,
        files,
    }
}

/// Distinct library roots present in the catalog with their item counts,
/// in the server's stable item order. Empty when the server predates
/// per-root attribution.
pub fn library_root_labels(catalog: &LibraryCatalog) -> Vec<(String, usize)> {
    let mut labels: Vec<(String, usize)> = Vec::new();
    for item in &catalog.items {
        let Some(label) = item.root_label.as_deref().filter(|label| !label.is_empty()) else {
            continue;
        };
        match labels
            .iter_mut()
            .find(|(existing, _)| existing.eq_ignore_ascii_case(label))
        {
            Some((_, count)) => *count += 1,
            None => labels.push((label.to_owned(), 1)),
        }
    }
    labels
}

/// The sidebar/filter shortcuts for "browse one file system location":
/// the actual library roots when the server reports several (like the
/// official client's 本機文件夾 list), otherwise the catalog's top-level
/// folders. Mirrors `scoped_folder_listing`, whose explorer paths start
/// with a root label only in the multi-root case.
pub fn library_folder_shortcuts(catalog: &LibraryCatalog) -> Vec<(String, usize)> {
    let roots = library_root_labels(catalog);
    if roots.len() >= 2 {
        return roots;
    }
    let mut folders: Vec<(String, usize)> = Vec::new();
    for item in &catalog.items {
        let Some(folder) = top_level_folder(&item.relative_path) else {
            continue;
        };
        match folders
            .iter_mut()
            .find(|(existing, _)| existing.eq_ignore_ascii_case(folder))
        {
            Some((_, count)) => *count += 1,
            None => folders.push((folder.to_owned(), 1)),
        }
    }
    folders.sort_by(|(left, _), (right, _)| left.to_lowercase().cmp(&right.to_lowercase()));
    folders
}

/// First non-empty component of a relative path.
pub fn top_level_folder(relative_path: &str) -> Option<&str> {
    relative_path
        .split(['/', '\\'])
        .find(|component| !component.trim().is_empty())
}

/// Whether an item lives under the given folder shortcut (a library root
/// when the server reports them, otherwise a top-level folder name).
pub fn item_in_folder_shortcut(item: &MediaItem, shortcut: &str) -> bool {
    item.root_label
        .as_deref()
        .is_some_and(|label| label.eq_ignore_ascii_case(shortcut))
        || top_level_folder(&item.relative_path)
            .is_some_and(|folder| folder.eq_ignore_ascii_case(shortcut))
}

/// Folder-explorer listing that browses library roots as the top level
/// when the server publishes more than one root (matching the official
/// client, whose explorer starts at the selected local folder). With one
/// or no attributed root the plain relative-path listing is used and
/// `path` holds only relative components; with several, `path[0]` is the
/// root label.
pub fn scoped_folder_listing(catalog: &LibraryCatalog, path: &[String]) -> FolderListing {
    let roots = library_root_labels(catalog);
    if roots.len() < 2 {
        return folder_listing(catalog, path);
    }
    match path.split_first() {
        None => FolderListing {
            folders: roots
                .into_iter()
                .map(|(name, item_count)| FolderEntry { name, item_count })
                .collect(),
            files: Vec::new(),
        },
        Some((root, rest)) => folder_listing_of(
            catalog.items.iter().filter(|item| {
                item.root_label
                    .as_deref()
                    .is_some_and(|label| label.eq_ignore_ascii_case(root))
            }),
            rest,
        ),
    }
}

/// Last path component of a relative path (the file name).
pub fn file_name(relative_path: &str) -> &str {
    relative_path
        .rsplit(['/', '\\'])
        .find(|segment| !segment.is_empty())
        .unwrap_or(relative_path)
}

fn grouped_series_with_identity(
    catalog: &LibraryCatalog,
    identity_of: impl Fn(&MediaItem) -> SeriesIdentity,
) -> Vec<Series> {
    // Group items by series identity while preserving first-seen order for
    // deterministic tie-breaks that match Kotlin's stable groupBy.
    let mut order: Vec<String> = Vec::new();
    let mut groups: std::collections::HashMap<String, Vec<(SeriesIdentity, MediaItem)>> =
        std::collections::HashMap::new();
    for item in &catalog.items {
        let identity = identity_of(item);
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
        Self::from_folder(item)
    }

    /// Identity from the item's folder title alone, ignoring any recognized
    /// anime metadata.
    fn from_folder(item: &MediaItem) -> Self {
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

pub fn fetch_attention(base_url: &str) -> Result<LibraryAttentionDocument, String> {
    let body = http_get(base_url, "/api/library/attention")?;
    serde_json::from_str(&body).map_err(|error| format!("invalid library attention JSON: {error}"))
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
    fn folder_and_matched_anime_views_never_mix() {
        let catalog: LibraryCatalog = serde_json::from_value(serde_json::json!({
            "rootName": "root",
            "indexedAtEpochMs": 0,
            "items": [
                {
                    "id": "recognized",
                    "seriesTitle": "raw.folder.name",
                    "episodeTitle": "E1",
                    "relativePath": "raw.folder.name/E1.mkv",
                    "sizeBytes": 1,
                    "mediaType": "video/x-matroska",
                    "streamPath": "/media/recognized",
                    "animeMetadata": {
                        "animeId": {"provider": "DANDANPLAY", "value": 42},
                        "displayTitle": "Recognized Anime"
                    }
                },
                {
                    "id": "unrecognized",
                    "seriesTitle": "Other Folder",
                    "episodeTitle": "E1",
                    "relativePath": "Other Folder/E1.mkv",
                    "sizeBytes": 1,
                    "mediaType": "video/x-matroska",
                    "streamPath": "/media/unrecognized"
                }
            ]
        }))
        .expect("catalog parses");

        // The folder view groups by series_title regardless of recognition,
        // so the recognized item lands under its raw folder name, not its
        // matched anime title.
        let by_folder = folder_grouped_series(&catalog);
        assert_eq!(by_folder.len(), 2);
        assert!(
            by_folder
                .iter()
                .any(|series| series.title == "raw.folder.name"
                    && series.items().any(|item| item.id == "recognized"))
        );
        assert!(by_folder.iter().any(|series| series.title == "Other Folder"
            && series.items().any(|item| item.id == "unrecognized")));

        // The matched-anime view only contains recognized items, under their
        // matched title — the unmatched folder never appears here.
        let by_anime = matched_anime_series(&catalog);
        assert_eq!(by_anime.len(), 1);
        assert_eq!(by_anime[0].title, "Recognized Anime");
        let anime_items: Vec<&str> = by_anime[0].items().map(|item| item.id.as_str()).collect();
        assert_eq!(anime_items, vec!["recognized"]);
    }

    #[test]
    fn folder_listing_navigates_relative_paths_like_a_file_explorer() {
        let item = |id: &str, relative_path: &str| {
            serde_json::json!({
                "id": id,
                "seriesTitle": "S",
                "episodeTitle": id,
                "relativePath": relative_path,
                "sizeBytes": 1,
                "mediaType": "video/x-matroska",
                "streamPath": format!("/media/{id}")
            })
        };
        let catalog: LibraryCatalog = serde_json::from_value(serde_json::json!({
            "rootName": "root",
            "indexedAtEpochMs": 0,
            "items": [
                item("loose", "loose episode.mkv"),
                item("s1e1", "Show A/Season 1/ep1.mkv"),
                item("s1e2", "Show A/Season 1/ep2.mkv"),
                item("s2e1", "Show A/Season 2/ep1.mkv"),
                item("b1", "show b/ep1.mkv"),
            ]
        }))
        .expect("catalog parses");

        // Root: two folders (sorted case-insensitively) and one loose file.
        let root = folder_listing(&catalog, &[]);
        assert_eq!(
            root.folders,
            vec![
                FolderEntry {
                    name: "Show A".to_owned(),
                    item_count: 3
                },
                FolderEntry {
                    name: "show b".to_owned(),
                    item_count: 1
                },
            ]
        );
        assert_eq!(root.files.len(), 1);
        assert_eq!(root.files[0].id, "loose");

        // Inside "Show A": two season folders, no direct files.
        let show_a = folder_listing(&catalog, &["Show A".to_owned()]);
        assert_eq!(show_a.folders.len(), 2);
        assert!(show_a.files.is_empty());

        // Inside a season: files sorted by name.
        let season_1 = folder_listing(&catalog, &["Show A".to_owned(), "Season 1".to_owned()]);
        assert!(season_1.folders.is_empty());
        let ids: Vec<&str> = season_1.files.iter().map(|item| item.id.as_str()).collect();
        assert_eq!(ids, vec!["s1e1", "s1e2"]);

        // A path that no longer exists lists nothing.
        let gone = folder_listing(&catalog, &["Removed".to_owned()]);
        assert_eq!(gone, FolderListing::default());

        assert_eq!(file_name("Show A/Season 1/ep1.mkv"), "ep1.mkv");
        assert_eq!(file_name("plain.mkv"), "plain.mkv");
    }

    #[test]
    fn scoped_listing_browses_roots_then_their_relative_paths() {
        let item = |id: &str, root: &str, relative_path: &str| {
            serde_json::json!({
                "id": id,
                "seriesTitle": "S",
                "episodeTitle": id,
                "relativePath": relative_path,
                "rootLabel": root,
                "sizeBytes": 1,
                "mediaType": "video/x-matroska",
                "streamPath": format!("/media/{id}")
            })
        };
        let catalog: LibraryCatalog = serde_json::from_value(serde_json::json!({
            "rootName": "Headless Library",
            "indexedAtEpochMs": 0,
            "items": [
                item("m1", "M:\\Anime", "Show A/ep1.mkv"),
                item("m2", "M:\\Anime", "Show A/ep2.mkv"),
                item("d1", "D:\\AniRss", "Show B/ep1.mkv"),
            ]
        }))
        .expect("catalog parses");

        // Shortcuts and the explorer's top level are the roots themselves.
        assert_eq!(
            library_folder_shortcuts(&catalog),
            vec![("M:\\Anime".to_owned(), 2), ("D:\\AniRss".to_owned(), 1)]
        );
        let top = scoped_folder_listing(&catalog, &[]);
        assert_eq!(
            top.folders
                .iter()
                .map(|entry| (entry.name.as_str(), entry.item_count))
                .collect::<Vec<_>>(),
            vec![("M:\\Anime", 2), ("D:\\AniRss", 1)]
        );
        assert!(top.files.is_empty());

        // Entering a root browses only that root's relative paths,
        // case-insensitively.
        let inside_root = scoped_folder_listing(&catalog, &["m:\\anime".to_owned()]);
        assert_eq!(
            inside_root
                .folders
                .iter()
                .map(|entry| entry.name.as_str())
                .collect::<Vec<_>>(),
            vec!["Show A"]
        );
        let show_a =
            scoped_folder_listing(&catalog, &["M:\\Anime".to_owned(), "Show A".to_owned()]);
        assert_eq!(
            show_a
                .files
                .iter()
                .map(|item| item.id.as_str())
                .collect::<Vec<_>>(),
            vec!["m1", "m2"]
        );

        assert!(item_in_folder_shortcut(&catalog.items[0], "M:\\ANIME"));
        assert!(!item_in_folder_shortcut(&catalog.items[0], "D:\\AniRss"));
    }

    #[test]
    fn single_root_catalogs_keep_top_level_folder_shortcuts() {
        let catalog: LibraryCatalog = serde_json::from_value(serde_json::json!({
            "rootName": "Anime",
            "indexedAtEpochMs": 0,
            "items": [
                {"id": "a", "seriesTitle": "S", "episodeTitle": "E1", "relativePath": "Show A/E1.mkv", "rootLabel": "M:\\Anime", "sizeBytes": 1, "mediaType": "video/x-matroska", "streamPath": "/media/a"},
                {"id": "b", "seriesTitle": "S", "episodeTitle": "E2", "relativePath": "Show B/E2.mkv", "rootLabel": "M:\\Anime", "sizeBytes": 1, "mediaType": "video/x-matroska", "streamPath": "/media/b"}
            ]
        }))
        .expect("catalog parses");

        // One root: shortcuts fall back to top-level folders and the
        // explorer starts directly in the relative tree.
        assert_eq!(
            library_folder_shortcuts(&catalog),
            vec![("Show A".to_owned(), 1), ("Show B".to_owned(), 1)]
        );
        let top = scoped_folder_listing(&catalog, &[]);
        assert_eq!(top.folders.len(), 2);
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

    #[test]
    fn parses_attention_status_and_indexes_items() {
        let document: LibraryAttentionDocument = serde_json::from_str(
            r#"{
                "generatedAtEpochMs": 1700000000000,
                "provider": {
                    "available": false,
                    "reasonCode": "DANDANPLAY_UNAVAILABLE"
                },
                "summary": {
                    "total": 2,
                    "needingAttention": 1,
                    "unmapped": 1,
                    "missingCache": 1
                },
                "items": [
                    {
                        "mediaId": "ready",
                        "mappingStatus": "MAPPED",
                        "cacheStatus": "FRESH",
                        "animeId": 42,
                        "episodeId": 420001,
                        "issueCodes": []
                    },
                    {
                        "mediaId": "needs-match",
                        "mappingStatus": "UNMAPPED",
                        "cacheStatus": "MISSING",
                        "issueCodes": ["UNMAPPED_ANIME", "MISSING_DANMAKU_CACHE"]
                    }
                ]
            }"#,
        )
        .expect("attention document should parse");

        assert_eq!(document.item_index.len(), 2);
        assert!(!document.provider.available);
        assert_eq!(document.summary.needing_attention, 1);
        assert!(!document.item("ready").unwrap().needs_attention());
        assert!(document.item("needs-match").unwrap().needs_attention());
        assert_eq!(document.item("ready").unwrap().episode_id, Some(420001));
        assert!(document.item("missing").is_none());
    }
}

#[derive(Clone, Debug, PartialEq)]
pub struct LibraryAttentionDocument {
    pub generated_at_epoch_ms: u64,
    pub provider: AttentionProviderStatus,
    pub summary: AttentionSummary,
    pub items: Vec<LibraryAttentionItem>,
    item_index: HashMap<String, usize>,
}

impl LibraryAttentionDocument {
    pub fn new(
        generated_at_epoch_ms: u64,
        provider: AttentionProviderStatus,
        summary: AttentionSummary,
        items: Vec<LibraryAttentionItem>,
    ) -> Self {
        let item_index = items
            .iter()
            .enumerate()
            .map(|(index, item)| (item.media_id.clone(), index))
            .collect();
        Self {
            generated_at_epoch_ms,
            provider,
            summary,
            items,
            item_index,
        }
    }

    pub fn item(&self, media_id: &str) -> Option<&LibraryAttentionItem> {
        self.item_index
            .get(media_id)
            .and_then(|index| self.items.get(*index))
    }
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct LibraryAttentionDocumentWire {
    generated_at_epoch_ms: u64,
    provider: AttentionProviderStatus,
    summary: AttentionSummary,
    items: Vec<LibraryAttentionItem>,
}

impl<'de> Deserialize<'de> for LibraryAttentionDocument {
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: serde::Deserializer<'de>,
    {
        let wire = LibraryAttentionDocumentWire::deserialize(deserializer)?;
        Ok(Self::new(
            wire.generated_at_epoch_ms,
            wire.provider,
            wire.summary,
            wire.items,
        ))
    }
}

#[derive(Clone, Debug, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct AttentionProviderStatus {
    pub available: bool,
    pub reason_code: Option<String>,
}

#[derive(Clone, Debug, Default, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase", default)]
pub struct AttentionSummary {
    pub total: usize,
    pub needing_attention: usize,
    pub unmapped: usize,
    pub missing_cache: usize,
    pub stale_cache: usize,
    pub failed: usize,
    pub conflicting: usize,
}

#[derive(Clone, Debug, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct LibraryAttentionItem {
    pub media_id: String,
    pub mapping_status: AttentionMappingStatus,
    pub cache_status: AttentionCacheStatus,
    pub anime_id: Option<u64>,
    pub episode_id: Option<u64>,
    #[serde(default)]
    pub issue_codes: Vec<AttentionIssueCode>,
    pub last_failure: Option<AttentionFailure>,
}

impl LibraryAttentionItem {
    pub fn needs_attention(&self) -> bool {
        !self.issue_codes.is_empty()
    }
}

#[derive(Clone, Copy, Debug, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum AttentionMappingStatus {
    Mapped,
    Unmapped,
}

#[derive(Clone, Copy, Debug, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum AttentionCacheStatus {
    Fresh,
    Stale,
    Missing,
}

#[derive(Clone, Copy, Debug, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum AttentionIssueCode {
    UnmappedAnime,
    MissingDanmakuCache,
    StaleDanmakuCache,
    ConflictingAnimeIds,
    RefreshFailed,
}

#[derive(Clone, Debug, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct AttentionFailure {
    pub reason_code: String,
    pub message: String,
    pub failed_at_epoch_ms: u64,
    pub attempt_count: u32,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct AttentionRepairRequest {
    pub media_id: String,
    pub mapping_status: AttentionMappingStatus,
    pub anime_id: Option<u64>,
    pub episode_id: Option<u64>,
}
