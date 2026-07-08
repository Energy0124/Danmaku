use std::collections::BTreeMap;
use std::fs;
use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};

use crate::catalog::{
    HeadlessStoredLibrary, LibraryCatalog, LibraryMediaItem, LibrarySubtitleTrack, PathMap,
    PublishedLibrary, absolute_normalized_path, current_epoch_ms,
};
use crate::hash::sha256_hex;
use crate::{LibraryServerError, Result};

const VIDEO_EXTENSIONS: &[&str] = &[
    "avi", "flv", "m2ts", "m4v", "mkv", "mov", "mp4", "mpeg", "mpg", "ts", "webm", "wmv",
];
const SUBTITLE_EXTENSIONS: &[&str] = &["ass", "srt", "ssa", "vtt"];

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct LibraryScan {
    pub published_library: PublishedLibrary,
    pub scanned_root_count: usize,
    pub reused_item_count: usize,
    pub refreshed_item_count: usize,
    pub file_last_modified_epoch_ms_by_id: BTreeMap<String, u64>,
}

impl LibraryScan {
    pub fn subtitle_track_count(&self) -> usize {
        self.published_library
            .catalog
            .items
            .iter()
            .map(|item| item.subtitles.len())
            .sum()
    }
}

pub fn scan_roots(
    roots: &[PathBuf],
    previous: Option<&HeadlessStoredLibrary>,
) -> Result<LibraryScan> {
    if roots.is_empty() {
        return Ok(LibraryScan {
            published_library: PublishedLibrary::empty(),
            scanned_root_count: 0,
            reused_item_count: 0,
            refreshed_item_count: 0,
            file_last_modified_epoch_ms_by_id: BTreeMap::new(),
        });
    }

    let normalized_roots = normalized_distinct_roots(roots)?;
    for root in &normalized_roots {
        if !root.is_dir() {
            return Err(LibraryServerError::new(format!(
                "library root must be a directory: {}",
                root.display()
            )));
        }
    }

    let scan_started_at_epoch_ms = current_epoch_ms();
    let previous_items_by_id = previous
        .map(|stored| {
            stored
                .published_library
                .catalog
                .items
                .iter()
                .map(|item| (item.id.clone(), item.clone()))
                .collect::<BTreeMap<_, _>>()
        })
        .unwrap_or_default();
    let previous_last_modified_by_id = previous
        .map(|stored| stored.file_last_modified_epoch_ms_by_id.clone())
        .unwrap_or_default();

    let mut files_by_id = PathMap::new();
    let mut subtitle_files_by_id = PathMap::new();
    let mut file_last_modified_epoch_ms_by_id = BTreeMap::new();
    let mut reused_item_count = 0;
    let mut refreshed_item_count = 0;
    let mut items = Vec::new();

    for root in &normalized_roots {
        let root_items = scan_root(
            root,
            scan_started_at_epoch_ms,
            &previous_items_by_id,
            &previous_last_modified_by_id,
            &mut files_by_id,
            &mut subtitle_files_by_id,
            &mut file_last_modified_epoch_ms_by_id,
            &mut reused_item_count,
            &mut refreshed_item_count,
        )?;
        items.extend(root_items);
    }

    items.sort_by(|left, right| {
        left.series_title
            .cmp(&right.series_title)
            .then_with(|| left.relative_path.cmp(&right.relative_path))
    });

    Ok(LibraryScan {
        published_library: PublishedLibrary {
            catalog: LibraryCatalog {
                root_name: root_name(&normalized_roots),
                indexed_at_epoch_ms: scan_started_at_epoch_ms,
                items,
            },
            files_by_id,
            subtitle_files_by_id,
            poster_files_by_id: PathMap::new(),
        },
        scanned_root_count: normalized_roots.len(),
        reused_item_count,
        refreshed_item_count,
        file_last_modified_epoch_ms_by_id,
    })
}

fn normalized_distinct_roots(roots: &[PathBuf]) -> Result<Vec<PathBuf>> {
    let mut normalized = roots
        .iter()
        .map(|root| absolute_normalized_path(root))
        .collect::<Result<Vec<_>>>()?;
    normalized.sort_by_key(|path| path_string(path));
    normalized.dedup();
    Ok(normalized)
}

#[allow(clippy::too_many_arguments)]
fn scan_root(
    root: &Path,
    scan_started_at_epoch_ms: u64,
    previous_items_by_id: &BTreeMap<String, LibraryMediaItem>,
    previous_last_modified_by_id: &BTreeMap<String, u64>,
    files_by_id: &mut PathMap,
    subtitle_files_by_id: &mut PathMap,
    file_last_modified_epoch_ms_by_id: &mut BTreeMap<String, u64>,
    reused_item_count: &mut usize,
    refreshed_item_count: &mut usize,
) -> Result<Vec<LibraryMediaItem>> {
    let id_namespace = path_string(root);
    let mut items = Vec::new();
    for path in regular_files_recursively(root)? {
        let extension = extension_lowercase(&path);
        if !VIDEO_EXTENSIONS.contains(&extension.as_str()) {
            continue;
        }

        let relative_path = relative_media_path(root, &path)?;
        let size_bytes = path.metadata()?.len();
        let last_modified_epoch_ms = last_modified_epoch_ms(&path)?;
        let subtitles = sidecar_subtitles(root, &path, &id_namespace)?;
        let id = sha256_hex(&format!("{id_namespace}/{relative_path}"))
            .chars()
            .take(24)
            .collect::<String>();
        let series_title = series_title(root, &path);
        let episode_title = file_stem(&path);
        let media_type = media_type(&extension).to_owned();

        let cached_item = previous_items_by_id.get(&id).filter(|item| {
            item.size_bytes == size_bytes
                && previous_last_modified_by_id
                    .get(&id)
                    .is_some_and(|cached_last_modified| {
                        *cached_last_modified == last_modified_epoch_ms
                    })
        });

        let item = if let Some(cached_item) = cached_item {
            *reused_item_count += 1;
            let mut item = cached_item.clone();
            item.series_title = series_title;
            item.episode_title = episode_title;
            item.media_type = media_type;
            item.indexed_at_epoch_ms = if item.indexed_at_epoch_ms > 0 {
                item.indexed_at_epoch_ms
            } else {
                last_modified_epoch_ms
            };
            item.subtitles = subtitles
                .iter()
                .map(|subtitle| subtitle.track.clone())
                .collect();
            item
        } else {
            *refreshed_item_count += 1;
            LibraryMediaItem {
                id: id.clone(),
                series_title,
                episode_title,
                relative_path,
                size_bytes,
                media_type,
                stream_path: format!("/media/{id}"),
                indexed_at_epoch_ms: scan_started_at_epoch_ms,
                subtitles: subtitles
                    .iter()
                    .map(|subtitle| subtitle.track.clone())
                    .collect(),
                poster_path: None,
                anime_metadata: None,
                metadata_status: Default::default(),
            }
        };

        files_by_id.insert(item.id.clone(), path.clone());
        for subtitle in subtitles {
            subtitle_files_by_id.insert(subtitle.track.id, subtitle.path);
        }
        file_last_modified_epoch_ms_by_id.insert(item.id.clone(), last_modified_epoch_ms);
        items.push(item);
    }
    Ok(items)
}

fn regular_files_recursively(root: &Path) -> Result<Vec<PathBuf>> {
    let mut stack = vec![root.to_path_buf()];
    let mut files = Vec::new();
    while let Some(directory) = stack.pop() {
        let mut entries = fs::read_dir(&directory)
            .map_err(|error| {
                LibraryServerError::with_context(
                    error,
                    format!("failed to read library directory {}", directory.display()),
                )
            })?
            .collect::<std::result::Result<Vec<_>, _>>()
            .map_err(|error| {
                LibraryServerError::with_context(
                    error,
                    format!("failed to read library directory {}", directory.display()),
                )
            })?;
        entries.sort_by_key(|entry| path_string(&entry.path()));
        for entry in entries.into_iter().rev() {
            let path = entry.path();
            let metadata = fs::metadata(&path)?;
            if metadata.is_dir() {
                stack.push(path);
            } else if metadata.is_file() {
                files.push(path);
            }
        }
    }
    Ok(files)
}

fn sidecar_subtitles(
    root: &Path,
    video_path: &Path,
    id_namespace: &str,
) -> Result<Vec<SubtitleFile>> {
    let Some(parent) = video_path.parent() else {
        return Ok(Vec::new());
    };
    let video_base_name = file_stem(video_path);
    let video_base_name_lowercase = video_base_name.to_lowercase();
    let mut subtitles = Vec::new();
    let mut entries = fs::read_dir(parent)
        .map_err(|error| {
            LibraryServerError::with_context(
                error,
                format!("failed to read subtitle directory {}", parent.display()),
            )
        })?
        .collect::<std::result::Result<Vec<_>, _>>()
        .map_err(|error| {
            LibraryServerError::with_context(
                error,
                format!("failed to read subtitle directory {}", parent.display()),
            )
        })?;
    entries.sort_by_key(|entry| path_string(&entry.path()));

    for entry in entries {
        let path = entry.path();
        if !fs::metadata(&path)?.is_file() {
            continue;
        }
        let extension = extension_lowercase(&path);
        if !SUBTITLE_EXTENSIONS.contains(&extension.as_str()) {
            continue;
        }

        let subtitle_base_name = file_stem(&path);
        let subtitle_base_name_lowercase = subtitle_base_name.to_lowercase();
        let matches_video = subtitle_base_name_lowercase == video_base_name_lowercase
            || subtitle_base_name_lowercase.starts_with(&format!("{video_base_name_lowercase}."));
        if !matches_video {
            continue;
        }

        let relative_path = relative_media_path(root, &path)?;
        let id = sha256_hex(&format!("{id_namespace}/subtitle/{relative_path}"))
            .chars()
            .take(24)
            .collect::<String>();
        let suffix = subtitle_base_name
            .chars()
            .skip(video_base_name.chars().count())
            .collect::<String>()
            .trim_start_matches('.')
            .to_owned();
        let label = if suffix.is_empty() {
            extension.to_uppercase()
        } else {
            suffix
        };

        subtitles.push(SubtitleFile {
            track: LibrarySubtitleTrack {
                id: id.clone(),
                label,
                relative_path,
                media_type: subtitle_media_type(&extension).to_owned(),
                stream_path: format!("/subtitles/{id}"),
            },
            path,
        });
    }

    subtitles.sort_by(|left, right| left.track.relative_path.cmp(&right.track.relative_path));
    Ok(subtitles)
}

fn root_name(roots: &[PathBuf]) -> String {
    if let [root] = roots {
        root.file_name()
            .map(|name| name.to_string_lossy().into_owned())
            .unwrap_or_else(|| path_string(root))
    } else {
        "Headless Library".to_owned()
    }
}

fn relative_media_path(root: &Path, path: &Path) -> Result<String> {
    let relative = path.strip_prefix(root).map_err(|error| {
        LibraryServerError::with_context(
            error,
            format!(
                "failed to derive relative path from {} to {}",
                root.display(),
                path.display()
            ),
        )
    })?;
    Ok(relative
        .components()
        .map(|component| component.as_os_str().to_string_lossy())
        .collect::<Vec<_>>()
        .join("/"))
}

fn media_type(extension: &str) -> &'static str {
    match extension {
        "m4v" | "mp4" => "video/mp4",
        "webm" => "video/webm",
        "ts" | "m2ts" => "video/mp2t",
        // Kotlin does not special-case mkv/avi/mov today, so clients see
        // application/octet-stream for most local video containers.
        _ => "application/octet-stream",
    }
}

fn subtitle_media_type(extension: &str) -> &'static str {
    match extension {
        "srt" => "application/x-subrip",
        "vtt" => "text/vtt",
        "ass" => "text/x-ass",
        "ssa" => "text/x-ssa",
        _ => "text/plain",
    }
}

fn series_title(root: &Path, path: &Path) -> String {
    let parent = path
        .parent()
        .and_then(|parent| absolute_normalized_path(parent).ok());
    if parent.as_deref() == Some(root) {
        infer_root_file_series_title(&file_stem(path))
    } else {
        path.parent()
            .and_then(Path::file_name)
            .map(|name| name.to_string_lossy().into_owned())
            .or_else(|| {
                root.file_name()
                    .map(|name| name.to_string_lossy().into_owned())
            })
            .unwrap_or_else(|| path_string(root))
    }
}

fn infer_root_file_series_title(file_stem: &str) -> String {
    let trimmed = file_stem.trim();
    let candidate = strip_leading_release_group(trimmed);
    let marker_start = [
        find_root_file_hyphen_episode(candidate),
        find_root_file_bracket_episode(candidate),
        find_root_file_named_episode(candidate),
    ]
    .into_iter()
    .flatten()
    .min();

    let title = marker_start
        .map(|index| &candidate[..index])
        .unwrap_or(candidate);
    let inferred = trim_single_enclosing_brackets(&trim_series_title_delimiters(title));
    if inferred.is_empty() {
        trimmed.to_owned()
    } else {
        inferred
    }
}

fn strip_leading_release_group(candidate: &str) -> &str {
    let Some((_, first_end)) = leading_bracket_token(candidate) else {
        return candidate;
    };
    let after_first = candidate[first_end..].trim_start();
    if after_first.is_empty() {
        return candidate;
    }
    if !after_first.starts_with('[') {
        return after_first;
    }
    let Some((second_token, _)) = leading_bracket_token(after_first) else {
        return candidate;
    };
    if is_root_file_episode_token(second_token) {
        candidate
    } else {
        after_first
    }
}

fn leading_bracket_token(candidate: &str) -> Option<(&str, usize)> {
    if !candidate.starts_with('[') {
        return None;
    }
    let end = candidate.find(']')?;
    Some((&candidate[1..end], end + 1))
}

fn find_root_file_bracket_episode(candidate: &str) -> Option<usize> {
    let mut offset = 0;
    while let Some(start) = candidate[offset..].find('[') {
        let absolute_start = offset + start;
        let after_start = absolute_start + 1;
        let end = candidate[after_start..].find(']')?;
        let absolute_end = after_start + end;
        if is_root_file_episode_token(&candidate[after_start..absolute_end]) {
            return Some(absolute_start);
        }
        offset = absolute_end + 1;
    }
    None
}

fn is_root_file_episode_token(token: &str) -> bool {
    let token = token.trim_end_matches(']');
    let (digits, rest) = take_digits(token, 1, 4);
    if digits.is_empty() {
        return false;
    }
    if rest.is_empty() {
        return true;
    }
    let rest_lowercase = rest.to_ascii_lowercase();
    if let Some(version) = rest_lowercase.strip_prefix('v') {
        return !version.is_empty() && version.chars().all(|char| char.is_ascii_digit());
    }
    matches!(rest_lowercase.trim_start(), "end" | "fin" | "final")
}

fn find_root_file_hyphen_episode(candidate: &str) -> Option<usize> {
    let chars = candidate.char_indices().collect::<Vec<_>>();
    for index in 0..chars.len() {
        let (marker_start, char) = chars[index];
        if !char.is_whitespace() {
            continue;
        }
        let Some((_, '-')) = chars.get(index + 1) else {
            continue;
        };
        let Some((after_dash_index, after_dash)) = chars.get(index + 2) else {
            continue;
        };
        if !after_dash.is_whitespace() {
            continue;
        }

        let episode_start = after_dash_index + after_dash.len_utf8();
        if parses_hyphen_episode_suffix(&candidate[episode_start..]) {
            return Some(marker_start);
        }
    }
    None
}

fn parses_hyphen_episode_suffix(value: &str) -> bool {
    let lowercase = value.to_ascii_lowercase();
    let mut rest = lowercase.as_str();
    if let Some(after_s) = rest.strip_prefix('s') {
        let (season_digits, after_season) = take_digits(after_s, 1, 2);
        if !season_digits.is_empty() {
            let after_spaces = after_season.trim_start();
            if let Some(after_e) = after_spaces.strip_prefix('e') {
                rest = after_e;
            }
        }
    }

    let (episode_digits, after_episode) = take_digits(rest, 1, 4);
    if episode_digits.is_empty() {
        return false;
    }
    let after_version = if let Some(after_v) = after_episode.strip_prefix('v') {
        let (version_digits, after_version) = take_digits(after_v, 1, usize::MAX);
        if version_digits.is_empty() {
            return false;
        }
        after_version
    } else {
        after_episode
    };
    after_version.is_empty()
        || after_version
            .chars()
            .next()
            .is_some_and(|char| char.is_whitespace() || char == '[')
}

fn find_root_file_named_episode(candidate: &str) -> Option<usize> {
    find_named_number(candidate, &["episode", "ep"], 1, 4)
}

fn trim_series_title_delimiters(value: &str) -> String {
    value
        .trim()
        .trim_matches(|char| matches!(char, '-' | '_' | '.' | ' '))
        .to_owned()
}

fn trim_single_enclosing_brackets(value: &str) -> String {
    if value.starts_with('[')
        && value.ends_with(']')
        && !value[1..value.len() - 1].contains(['[', ']'])
    {
        value[1..value.len() - 1].trim().to_owned()
    } else {
        value.to_owned()
    }
}

pub(crate) fn find_season_number(searchable_text: &str) -> Option<u32> {
    find_named_number_value(searchable_text, &["season"], 1, 2)
        .or_else(|| find_prefixed_number_value(searchable_text, 's', 1, 2))
}

pub(crate) fn find_episode_number(searchable_text: &str) -> Option<u32> {
    find_named_number_value(searchable_text, &["episode", "ep"], 1, 4)
        .or_else(|| find_prefixed_number_value(searchable_text, 'e', 1, 4))
}

fn find_named_number_value(
    value: &str,
    names: &[&str],
    min_digits: usize,
    max_digits: usize,
) -> Option<u32> {
    find_named_number(value, names, min_digits, max_digits)
        .and_then(|index| number_after_name(&value[index..], names, min_digits, max_digits))
}

fn find_named_number(
    value: &str,
    names: &[&str],
    min_digits: usize,
    max_digits: usize,
) -> Option<usize> {
    let lowercase = value.to_lowercase();
    for (index, _) in lowercase.char_indices() {
        if !is_word_boundary_before(&lowercase, index) {
            continue;
        }
        for name in names {
            let Some(rest) = lowercase[index..].strip_prefix(name) else {
                continue;
            };
            if number_after_prefix(rest, min_digits, max_digits).is_some() {
                return Some(index);
            }
        }
    }
    None
}

fn number_after_name(
    value: &str,
    names: &[&str],
    min_digits: usize,
    max_digits: usize,
) -> Option<u32> {
    let lowercase = value.to_lowercase();
    for name in names {
        if let Some(rest) = lowercase.strip_prefix(name)
            && let Some((digits, _)) = number_after_prefix(rest, min_digits, max_digits)
        {
            return digits.parse().ok();
        }
    }
    None
}

fn number_after_prefix(rest: &str, min_digits: usize, max_digits: usize) -> Option<(&str, &str)> {
    let rest = rest.trim_start();
    let (digits, after_digits) = take_digits(rest, min_digits, max_digits);
    if digits.is_empty() || !is_word_boundary_after(after_digits) {
        None
    } else {
        Some((digits, after_digits))
    }
}

fn find_prefixed_number_value(
    value: &str,
    prefix: char,
    min_digits: usize,
    max_digits: usize,
) -> Option<u32> {
    let lowercase = value.to_lowercase();
    for (index, char) in lowercase.char_indices() {
        if char != prefix || !is_word_boundary_before(&lowercase, index) {
            continue;
        }
        let rest = &lowercase[index + char.len_utf8()..];
        let (digits, after_digits) = take_digits(rest, min_digits, max_digits);
        if !digits.is_empty() && is_word_boundary_after(after_digits) {
            return digits.parse().ok();
        }
    }
    None
}

fn take_digits(value: &str, min_digits: usize, max_digits: usize) -> (&str, &str) {
    let mut digit_count = 0;
    let mut end = 0;
    for (index, char) in value.char_indices() {
        if !char.is_ascii_digit() || digit_count == max_digits {
            break;
        }
        digit_count += 1;
        end = index + char.len_utf8();
    }
    if digit_count < min_digits {
        ("", value)
    } else {
        (&value[..end], &value[end..])
    }
}

fn is_word_boundary_before(value: &str, index: usize) -> bool {
    value[..index]
        .chars()
        .next_back()
        .is_none_or(|char| !is_regex_word_char(char))
}

fn is_word_boundary_after(value: &str) -> bool {
    value
        .chars()
        .next()
        .is_none_or(|char| !is_regex_word_char(char))
}

fn is_regex_word_char(char: char) -> bool {
    char.is_ascii_alphanumeric() || char == '_'
}

fn last_modified_epoch_ms(path: &Path) -> Result<u64> {
    let modified = path.metadata()?.modified().map_err(|error| {
        LibraryServerError::with_context(
            error,
            format!("failed to read file modified time {}", path.display()),
        )
    })?;
    Ok(system_time_epoch_ms(modified))
}

fn system_time_epoch_ms(time: SystemTime) -> u64 {
    let millis = time
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis();
    millis.min(u64::MAX as u128) as u64
}

fn extension_lowercase(path: &Path) -> String {
    path.extension()
        .map(|extension| extension.to_string_lossy().to_lowercase())
        .unwrap_or_default()
}

fn file_stem(path: &Path) -> String {
    path.file_stem()
        .map(|stem| stem.to_string_lossy().into_owned())
        .unwrap_or_default()
}

fn path_string(path: &Path) -> String {
    path.to_string_lossy().into_owned()
}

#[derive(Debug, Clone)]
struct SubtitleFile {
    track: LibrarySubtitleTrack,
    path: PathBuf,
}

#[cfg(test)]
mod tests {
    use std::fs;
    use std::path::{Path, PathBuf};
    use std::sync::atomic::{AtomicU64, Ordering};
    use std::thread;
    use std::time::Duration;

    use serde_json::Value;

    use super::*;
    use crate::catalog::CatalogStore;

    static TEMP_COUNTER: AtomicU64 = AtomicU64::new(0);

    #[test]
    fn scans_fixture_tree_with_kotlin_catalog_semantics() {
        let temp = temp_dir("danmaku-scan-fixture");
        let root = temp.join("Anime");
        fs::create_dir_all(root.join("Alpha Show")).expect("dirs");
        fs::create_dir_all(root.join("Beta Show")).expect("dirs");
        write_bytes(&root.join("Alpha Show").join("Episode 01.mkv"), &[1, 2, 3]);
        write_bytes(
            &root.join("Alpha Show").join("Episode 02.mp4"),
            &[4, 5, 6, 7],
        );
        write_text(
            &root.join("Alpha Show").join("Episode 02.ass"),
            "[Script Info]",
        );
        write_text(
            &root.join("Alpha Show").join("episode 02.en.srt"),
            "1\n00:00:00,000 --> 00:00:01,000\nHello",
        );
        write_text(
            &root.join("Alpha Show").join("Episode 02-en.srt"),
            "not a match",
        );
        write_text(&root.join("Alpha Show").join("notes.txt"), "ignored");
        write_bytes(&root.join("Beta Show").join("Episode 01.webm"), &[8, 9]);
        write_bytes(
            &root.join("[Fansub] Root Level Show - 03 [1080p].m4v"),
            &[10, 11, 12],
        );

        let scan = scan_roots(std::slice::from_ref(&root), None).expect("scan should succeed");
        let catalog = &scan.published_library.catalog;
        assert_eq!("Anime", catalog.root_name);
        assert_eq!(4, catalog.items.len());
        assert_eq!(2, scan.subtitle_track_count());
        assert_eq!(0, scan.reused_item_count);
        assert_eq!(4, scan.refreshed_item_count);

        let titles = catalog
            .items
            .iter()
            .map(|item| (item.series_title.as_str(), item.relative_path.as_str()))
            .collect::<Vec<_>>();
        assert_eq!(
            vec![
                ("Alpha Show", "Alpha Show/Episode 01.mkv"),
                ("Alpha Show", "Alpha Show/Episode 02.mp4"),
                ("Beta Show", "Beta Show/Episode 01.webm"),
                (
                    "Root Level Show",
                    "[Fansub] Root Level Show - 03 [1080p].m4v"
                ),
            ],
            titles
        );

        let root_namespace = path_string(&absolute_normalized_path(&root).expect("root"));
        let alpha_two = &catalog.items[1];
        let expected_id = sha256_hex(&format!("{root_namespace}/Alpha Show/Episode 02.mp4"))
            .chars()
            .take(24)
            .collect::<String>();
        assert_eq!(expected_id, alpha_two.id);
        assert_eq!(format!("/media/{expected_id}"), alpha_two.stream_path);
        assert_eq!("video/mp4", alpha_two.media_type);
        assert_eq!(4, alpha_two.size_bytes);
        assert_eq!(
            vec![
                ("ASS", "Alpha Show/Episode 02.ass", "text/x-ass"),
                ("en", "Alpha Show/episode 02.en.srt", "application/x-subrip"),
            ],
            alpha_two
                .subtitles
                .iter()
                .map(|track| {
                    (
                        track.label.as_str(),
                        track.relative_path.as_str(),
                        track.media_type.as_str(),
                    )
                })
                .collect::<Vec<_>>()
        );
        assert_eq!("application/octet-stream", catalog.items[0].media_type);
        assert_eq!("video/webm", catalog.items[2].media_type);
        assert_eq!("video/mp4", catalog.items[3].media_type);

        fs::remove_dir_all(temp).expect("temp should delete");
    }

    #[test]
    fn rescans_reuse_unchanged_items_and_reflect_added_removed_and_modified_files() {
        let temp = temp_dir("danmaku-incremental-scan");
        let root = temp.join("Anime");
        let show = root.join("Example Show");
        fs::create_dir_all(&show).expect("dirs");
        let first_episode = show.join("Episode 01.mkv");
        write_bytes(&first_episode, &[1, 2, 3]);

        let first = scan_roots(std::slice::from_ref(&root), None).expect("first scan");
        let first_stored = stored_from_scan(first.clone());
        let first_indexed_at = first.published_library.catalog.items[0].indexed_at_epoch_ms;
        let unchanged =
            scan_roots(std::slice::from_ref(&root), Some(&first_stored)).expect("unchanged scan");
        assert_eq!(1, unchanged.reused_item_count);
        assert_eq!(0, unchanged.refreshed_item_count);
        assert_eq!(
            first_indexed_at,
            unchanged.published_library.catalog.items[0].indexed_at_epoch_ms
        );

        let second_episode = show.join("Episode 02.mkv");
        write_bytes(&second_episode, &[4, 5, 6]);
        let added = scan_roots(
            std::slice::from_ref(&root),
            Some(&stored_from_scan(unchanged.clone())),
        )
        .expect("added scan");
        assert_eq!(1, added.reused_item_count);
        assert_eq!(1, added.refreshed_item_count);
        assert_eq!(2, added.published_library.catalog.items.len());

        fs::remove_file(&first_episode).expect("remove episode");
        let removed = scan_roots(std::slice::from_ref(&root), Some(&stored_from_scan(added)))
            .expect("removed scan");
        assert_eq!(1, removed.reused_item_count);
        assert_eq!(0, removed.refreshed_item_count);
        assert_eq!(
            vec!["Example Show/Episode 02.mkv"],
            removed
                .published_library
                .catalog
                .items
                .iter()
                .map(|item| item.relative_path.as_str())
                .collect::<Vec<_>>()
        );

        thread::sleep(Duration::from_millis(20));
        write_bytes(&second_episode, &[4, 5, 6, 7]);
        let modified = scan_roots(
            std::slice::from_ref(&root),
            Some(&stored_from_scan(removed.clone())),
        )
        .expect("modified scan");
        assert_eq!(0, modified.reused_item_count);
        assert_eq!(1, modified.refreshed_item_count);
        assert_eq!(4, modified.published_library.catalog.items[0].size_bytes);

        fs::remove_dir_all(temp).expect("temp should delete");
    }

    #[test]
    fn scan_snapshot_round_trips_with_incremental_metadata() {
        let temp = temp_dir("danmaku-scan-roundtrip");
        let data = temp.join("data");
        let root = temp.join("Anime");
        let show = root.join("Example Show");
        fs::create_dir_all(&show).expect("dirs");
        write_bytes(&show.join("Episode 01.mp4"), &[1, 2, 3, 4]);
        write_text(&show.join("Episode 01.en.vtt"), "WEBVTT\n\nHello");

        let scan = scan_roots(std::slice::from_ref(&root), None).expect("scan");
        let store = CatalogStore::new(data.join("catalog.json"));
        let stored = store.save_scan(scan.clone()).expect("save scan");
        let reloaded = store
            .load()
            .expect("load should succeed")
            .expect("snapshot should exist");

        assert_eq!(stored, reloaded);
        assert_eq!(
            scan.published_library.catalog,
            reloaded.published_library.catalog
        );
        assert_eq!(
            scan.file_last_modified_epoch_ms_by_id,
            reloaded.file_last_modified_epoch_ms_by_id
        );

        fs::remove_dir_all(temp).expect("temp should delete");
    }

    fn stored_from_scan(scan: LibraryScan) -> HeadlessStoredLibrary {
        HeadlessStoredLibrary {
            published_library: scan.published_library,
            saved_at_epoch_ms: current_epoch_ms(),
            file_last_modified_epoch_ms_by_id: scan.file_last_modified_epoch_ms_by_id,
        }
    }

    fn temp_dir(prefix: &str) -> PathBuf {
        let id = TEMP_COUNTER.fetch_add(1, Ordering::Relaxed);
        let path = std::env::temp_dir().join(format!("{prefix}-{}-{id}", std::process::id()));
        let _ = fs::remove_dir_all(&path);
        fs::create_dir_all(&path).expect("temp dir should create");
        path
    }

    fn write_bytes(path: &Path, bytes: &[u8]) {
        fs::write(path, bytes).unwrap_or_else(|error| panic!("write {}: {error}", path.display()));
    }

    fn write_text(path: &Path, text: &str) {
        fs::write(path, text).unwrap_or_else(|error| panic!("write {}: {error}", path.display()));
    }

    #[test]
    fn infers_root_release_titles_like_kotlin() {
        assert_eq!(
            "Jujutsu Kaisen",
            infer_root_file_series_title(
                "[BeanSub&FZSD&LoliHouse] Jujutsu Kaisen - 15 [WebRip 1080p]"
            )
        );
        assert_eq!(
            "Sono Bisque Doll wa Koi wo Suru",
            infer_root_file_series_title(
                "[HYSUB]Sono Bisque Doll wa Koi wo Suru[15][BIG5_MP4][1920X1080]"
            )
        );
        assert_eq!(
            "Re Zero kara Hajimeru Isekai Seikatsu S2",
            infer_root_file_series_title(
                "[Re Zero kara Hajimeru Isekai Seikatsu S2][01][BIG5][1080P]"
            )
        );
        assert_eq!(
            "86 - Eighty Six",
            infer_root_file_series_title(
                "[Comicat&KissSub][86 - Eighty Six][04][1080P][BIG5][MP4]"
            )
        );
        assert_eq!(Some(2), find_episode_number("Ep02"));
        assert_eq!(None, find_episode_number("S01E12"));
        assert_eq!(Some(12), find_episode_number("E12"));
        assert_eq!(Some(2), find_season_number("Season 2"));
    }

    #[test]
    fn scan_catalog_json_matches_wire_shape() {
        let temp = temp_dir("danmaku-scan-json");
        let root = temp.join("Anime");
        let show = root.join("Example Show");
        fs::create_dir_all(&show).expect("dirs");
        write_bytes(&show.join("Episode 01.mp4"), &[1, 2, 3, 4]);
        let scan = scan_roots(std::slice::from_ref(&root), None).expect("scan");

        let json = serde_json::to_value(&scan.published_library.catalog).expect("catalog json");
        assert_eq!("Anime", json["rootName"]);
        assert_eq!(1, json["items"].as_array().expect("items").len());
        assert_eq!(Value::Null, json["items"][0]["posterPath"]);
        assert_eq!(Value::Null, json["items"][0]["animeMetadata"]);

        fs::remove_dir_all(temp).expect("temp should delete");
    }
}
