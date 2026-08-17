use std::collections::{BTreeMap, BTreeSet};
use std::fs;
use std::io;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};
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

/// Live counters for an in-flight scan, shared with the HTTP status route so
/// clients can show indexing progress while the background scan runs.
#[derive(Debug, Default)]
pub struct ScanProgress {
    media_files_seen: AtomicU64,
}

impl ScanProgress {
    pub fn media_files_seen(&self) -> u64 {
        self.media_files_seen.load(Ordering::Relaxed)
    }

    fn record_media_file(&self) {
        self.media_files_seen.fetch_add(1, Ordering::Relaxed);
    }

    pub fn reset(&self) {
        self.media_files_seen.store(0, Ordering::Relaxed);
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum LibraryRescanTarget {
    All,
    Subtree { root: PathBuf, directory: PathBuf },
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct LibraryScan {
    pub published_library: PublishedLibrary,
    pub scanned_root_count: usize,
    pub reused_item_count: usize,
    pub refreshed_item_count: usize,
    pub skipped_unreadable_count: usize,
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
    scan_roots_with_progress(roots, previous, None)
}

pub fn scan_roots_with_progress(
    roots: &[PathBuf],
    previous: Option<&HeadlessStoredLibrary>,
    progress: Option<&ScanProgress>,
) -> Result<LibraryScan> {
    if roots.is_empty() {
        return Ok(LibraryScan {
            published_library: PublishedLibrary::empty(),
            scanned_root_count: 0,
            reused_item_count: 0,
            refreshed_item_count: 0,
            skipped_unreadable_count: 0,
            file_last_modified_epoch_ms_by_id: BTreeMap::new(),
        });
    }

    let normalized_roots = normalized_distinct_roots(roots)?;
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
    let mut skipped_unreadable_count = 0;
    let mut items = Vec::new();

    for root in &normalized_roots {
        if !root_is_readable_directory(root, &mut skipped_unreadable_count) {
            continue;
        }
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
            &mut skipped_unreadable_count,
            progress,
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
        skipped_unreadable_count,
        file_last_modified_epoch_ms_by_id,
    })
}

pub fn resolve_rescan_target(
    roots: &[PathBuf],
    logical_path: &[String],
) -> Result<LibraryRescanTarget> {
    let normalized_roots = normalized_distinct_roots(roots)?;
    if normalized_roots.is_empty() {
        return Err(LibraryServerError::new("no library folders are configured"));
    }
    if logical_path.is_empty() {
        return Ok(LibraryRescanTarget::All);
    }

    let (root, relative_segments) = if normalized_roots.len() >= 2 {
        let requested_root = &logical_path[0];
        let root = normalized_roots
            .iter()
            .find(|root| path_label_eq(&path_string(root), requested_root))
            .cloned()
            .ok_or_else(|| {
                LibraryServerError::new("folder path does not identify a library root")
            })?;
        (root, &logical_path[1..])
    } else {
        (normalized_roots[0].clone(), logical_path)
    };

    let mut directory = root.clone();
    for segment in relative_segments {
        if !valid_logical_path_segment(segment) {
            return Err(LibraryServerError::new(
                "folder path contains an invalid segment",
            ));
        }
        directory.push(segment);
    }
    Ok(LibraryRescanTarget::Subtree { root, directory })
}

pub fn rescan_target_with_progress(
    roots: &[PathBuf],
    target: &LibraryRescanTarget,
    previous: Option<&HeadlessStoredLibrary>,
    progress: Option<&ScanProgress>,
) -> Result<LibraryScan> {
    let LibraryRescanTarget::Subtree { root, directory } = target else {
        return scan_roots_with_progress(roots, previous, progress);
    };
    let Some(previous) = previous else {
        return scan_roots_with_progress(roots, None, progress);
    };

    let normalized_roots = normalized_distinct_roots(roots)?;
    let normalized_root = absolute_normalized_path(root)?;
    let normalized_directory = absolute_normalized_path(directory)?;
    if !path_is_within(&normalized_directory, &normalized_root) {
        return Err(LibraryServerError::new(
            "folder path is outside the library root",
        ));
    }

    let scan_started_at_epoch_ms = current_epoch_ms();
    let previous_items_by_id = previous
        .published_library
        .catalog
        .items
        .iter()
        .map(|item| (item.id.clone(), item.clone()))
        .collect::<BTreeMap<_, _>>();
    let previous_last_modified_by_id = previous.file_last_modified_epoch_ms_by_id.clone();

    let mut removed_media_ids = BTreeSet::new();
    let mut removed_subtitle_ids = BTreeSet::new();
    for item in &previous.published_library.catalog.items {
        let absolute_path = previous
            .published_library
            .files_by_id
            .get(&item.id)
            .cloned()
            .or_else(|| item_path(item, &normalized_root));
        if absolute_path
            .as_deref()
            .is_some_and(|path| path_is_within(path, &normalized_directory))
        {
            removed_media_ids.insert(item.id.clone());
            removed_subtitle_ids.extend(item.subtitles.iter().map(|subtitle| subtitle.id.clone()));
        }
    }

    let mut files_by_id = previous.published_library.files_by_id.clone();
    files_by_id.retain(|id, _| !removed_media_ids.contains(id));
    let mut subtitle_files_by_id = previous.published_library.subtitle_files_by_id.clone();
    subtitle_files_by_id.retain(|id, _| !removed_subtitle_ids.contains(id));
    let mut poster_files_by_id = previous.published_library.poster_files_by_id.clone();
    poster_files_by_id.retain(|id, _| !removed_media_ids.contains(id));
    let mut file_last_modified_epoch_ms_by_id = previous.file_last_modified_epoch_ms_by_id.clone();
    file_last_modified_epoch_ms_by_id.retain(|id, _| !removed_media_ids.contains(id));
    let mut items = previous
        .published_library
        .catalog
        .items
        .iter()
        .filter(|item| !removed_media_ids.contains(&item.id))
        .cloned()
        .collect::<Vec<_>>();

    let mut reused_item_count = 0;
    let mut refreshed_item_count = 0;
    let mut skipped_unreadable_count = 0;
    match fs::metadata(&normalized_directory) {
        Ok(metadata) if metadata.is_dir() => {
            items.extend(scan_directory(
                &normalized_root,
                &normalized_directory,
                scan_started_at_epoch_ms,
                &previous_items_by_id,
                &previous_last_modified_by_id,
                &mut files_by_id,
                &mut subtitle_files_by_id,
                &mut file_last_modified_epoch_ms_by_id,
                &mut reused_item_count,
                &mut refreshed_item_count,
                &mut skipped_unreadable_count,
                progress,
                true,
            )?);
        }
        Ok(_) => return Err(LibraryServerError::new("folder path is not a directory")),
        Err(error)
            if error.kind() == io::ErrorKind::NotFound
                && !paths_equal(&normalized_directory, &normalized_root) => {}
        Err(error) => {
            return Err(LibraryServerError::with_context(
                error,
                format!("failed to read folder {}", normalized_directory.display()),
            ));
        }
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
            poster_files_by_id,
        },
        scanned_root_count: 1,
        reused_item_count,
        refreshed_item_count,
        skipped_unreadable_count,
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
    skipped_unreadable_count: &mut usize,
    progress: Option<&ScanProgress>,
) -> Result<Vec<LibraryMediaItem>> {
    scan_directory(
        root,
        root,
        scan_started_at_epoch_ms,
        previous_items_by_id,
        previous_last_modified_by_id,
        files_by_id,
        subtitle_files_by_id,
        file_last_modified_epoch_ms_by_id,
        reused_item_count,
        refreshed_item_count,
        skipped_unreadable_count,
        progress,
        false,
    )
}

#[allow(clippy::too_many_arguments)]
fn scan_directory(
    root: &Path,
    directory: &Path,
    scan_started_at_epoch_ms: u64,
    previous_items_by_id: &BTreeMap<String, LibraryMediaItem>,
    previous_last_modified_by_id: &BTreeMap<String, u64>,
    files_by_id: &mut PathMap,
    subtitle_files_by_id: &mut PathMap,
    file_last_modified_epoch_ms_by_id: &mut BTreeMap<String, u64>,
    reused_item_count: &mut usize,
    refreshed_item_count: &mut usize,
    skipped_unreadable_count: &mut usize,
    progress: Option<&ScanProgress>,
    fail_on_unreadable: bool,
) -> Result<Vec<LibraryMediaItem>> {
    let id_namespace = path_string(root);
    let mut items = Vec::new();
    for path in regular_files_recursively(directory, skipped_unreadable_count, fail_on_unreadable)?
    {
        let extension = extension_lowercase(&path);
        if !VIDEO_EXTENSIONS.contains(&extension.as_str()) {
            continue;
        }
        if let Some(progress) = progress {
            progress.record_media_file();
        }

        let relative_path = relative_media_path(root, &path)?;
        let Some((size_bytes, last_modified_epoch_ms)) =
            file_metadata_snapshot(&path, skipped_unreadable_count, fail_on_unreadable)?
        else {
            continue;
        };
        let subtitles = sidecar_subtitles(
            root,
            &path,
            &id_namespace,
            skipped_unreadable_count,
            fail_on_unreadable,
        )?;
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
            item.root_label = Some(id_namespace.clone());
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
                root_label: Some(id_namespace.clone()),
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

fn valid_logical_path_segment(segment: &str) -> bool {
    !segment.is_empty()
        && segment != "."
        && segment != ".."
        && !segment.contains(['/', '\\'])
        && Path::new(segment).components().count() == 1
}

fn path_label_eq(left: &str, right: &str) -> bool {
    if cfg!(windows) {
        left.eq_ignore_ascii_case(right)
    } else {
        left == right
    }
}

fn path_is_within(path: &Path, parent: &Path) -> bool {
    let path_components = path.components().collect::<Vec<_>>();
    let parent_components = parent.components().collect::<Vec<_>>();
    path_components.len() >= parent_components.len()
        && parent_components
            .iter()
            .zip(path_components.iter())
            .all(|(left, right)| {
                path_label_eq(
                    &left.as_os_str().to_string_lossy(),
                    &right.as_os_str().to_string_lossy(),
                )
            })
}

fn paths_equal(left: &Path, right: &Path) -> bool {
    left.components().count() == right.components().count() && path_is_within(left, right)
}

fn item_path(item: &LibraryMediaItem, expected_root: &Path) -> Option<PathBuf> {
    let root_label = item.root_label.as_deref()?;
    if !path_label_eq(root_label, &path_string(expected_root)) {
        return None;
    }
    Some(expected_root.join(&item.relative_path))
}

fn root_is_readable_directory(root: &Path, skipped_unreadable_count: &mut usize) -> bool {
    match fs::metadata(root) {
        Ok(metadata) if metadata.is_dir() => true,
        Ok(_) => {
            warn_skipped_unreadable(root, "not a directory", skipped_unreadable_count);
            false
        }
        Err(error) => {
            warn_skipped_unreadable(root, error, skipped_unreadable_count);
            false
        }
    }
}

fn regular_files_recursively(
    root: &Path,
    skipped_unreadable_count: &mut usize,
    fail_on_unreadable: bool,
) -> Result<Vec<PathBuf>> {
    let mut stack = vec![root.to_path_buf()];
    let mut files = Vec::new();
    while let Some(directory) = stack.pop() {
        let read_dir = match fs::read_dir(&directory) {
            Ok(read_dir) => read_dir,
            Err(error) if fail_on_unreadable => {
                return Err(LibraryServerError::with_context(
                    error,
                    format!("failed to read folder {}", directory.display()),
                ));
            }
            Err(error) => {
                warn_skipped_unreadable(&directory, error, skipped_unreadable_count);
                continue;
            }
        };
        let mut entries = Vec::new();
        for entry in read_dir {
            match entry {
                Ok(entry) => entries.push(entry),
                Err(error) if fail_on_unreadable => {
                    return Err(LibraryServerError::with_context(
                        error,
                        format!("failed to enumerate folder {}", directory.display()),
                    ));
                }
                Err(error) => {
                    warn_skipped_unreadable(&directory, error, skipped_unreadable_count);
                }
            }
        }
        entries.sort_by_key(|entry| path_string(&entry.path()));
        for entry in entries.into_iter().rev() {
            let path = entry.path();
            let metadata = fs::metadata(&path);
            match classify_path(path, metadata, skipped_unreadable_count, fail_on_unreadable)? {
                Some(WalkEntry::Directory(path)) => stack.push(path),
                Some(WalkEntry::File(path)) => files.push(path),
                None => {}
            }
        }
    }
    Ok(files)
}

fn classify_path(
    path: PathBuf,
    metadata: io::Result<fs::Metadata>,
    skipped_unreadable_count: &mut usize,
    fail_on_unreadable: bool,
) -> Result<Option<WalkEntry>> {
    match metadata {
        Ok(metadata) if metadata.is_dir() => Ok(Some(WalkEntry::Directory(path))),
        Ok(metadata) if metadata.is_file() => Ok(Some(WalkEntry::File(path))),
        Ok(_) => Ok(None),
        Err(error) if fail_on_unreadable => Err(LibraryServerError::with_context(
            error,
            format!("failed to read path {}", path.display()),
        )),
        Err(error) => {
            warn_skipped_unreadable(&path, error, skipped_unreadable_count);
            Ok(None)
        }
    }
}

enum WalkEntry {
    Directory(PathBuf),
    File(PathBuf),
}

fn file_metadata_snapshot(
    path: &Path,
    skipped_unreadable_count: &mut usize,
    fail_on_unreadable: bool,
) -> Result<Option<(u64, u64)>> {
    let metadata = match path.metadata() {
        Ok(metadata) => metadata,
        Err(error) if fail_on_unreadable => {
            return Err(LibraryServerError::with_context(
                error,
                format!("failed to read media file {}", path.display()),
            ));
        }
        Err(error) => {
            warn_skipped_unreadable(path, error, skipped_unreadable_count);
            return Ok(None);
        }
    };
    let modified = match metadata.modified() {
        Ok(modified) => modified,
        Err(error) if fail_on_unreadable => {
            return Err(LibraryServerError::with_context(
                error,
                format!("failed to read media timestamp {}", path.display()),
            ));
        }
        Err(error) => {
            warn_skipped_unreadable(path, error, skipped_unreadable_count);
            return Ok(None);
        }
    };
    Ok(Some((metadata.len(), system_time_epoch_ms(modified))))
}

fn warn_skipped_unreadable(
    path: &Path,
    error: impl std::fmt::Display,
    skipped_unreadable_count: &mut usize,
) {
    *skipped_unreadable_count += 1;
    eprintln!(
        "Catalog scan warning: skipped unreadable path {}; error={error}",
        path.display()
    );
}

fn sidecar_subtitles(
    root: &Path,
    video_path: &Path,
    id_namespace: &str,
    skipped_unreadable_count: &mut usize,
    fail_on_unreadable: bool,
) -> Result<Vec<SubtitleFile>> {
    let Some(parent) = video_path.parent() else {
        return Ok(Vec::new());
    };
    let video_base_name = file_stem(video_path);
    let video_base_name_lowercase = video_base_name.to_lowercase();
    let mut subtitles = Vec::new();
    let read_dir = match fs::read_dir(parent) {
        Ok(read_dir) => read_dir,
        Err(error) if fail_on_unreadable => {
            return Err(LibraryServerError::with_context(
                error,
                format!("failed to read subtitle folder {}", parent.display()),
            ));
        }
        Err(error) => {
            warn_skipped_unreadable(parent, error, skipped_unreadable_count);
            return Ok(Vec::new());
        }
    };
    let mut entries = Vec::new();
    for entry in read_dir {
        match entry {
            Ok(entry) => entries.push(entry),
            Err(error) if fail_on_unreadable => {
                return Err(LibraryServerError::with_context(
                    error,
                    format!("failed to enumerate subtitle folder {}", parent.display()),
                ));
            }
            Err(error) => {
                warn_skipped_unreadable(parent, error, skipped_unreadable_count);
            }
        }
    }
    entries.sort_by_key(|entry| path_string(&entry.path()));

    for entry in entries {
        let path = entry.path();
        let metadata = match fs::metadata(&path) {
            Ok(metadata) => metadata,
            Err(error) if fail_on_unreadable => {
                return Err(LibraryServerError::with_context(
                    error,
                    format!("failed to read subtitle path {}", path.display()),
                ));
            }
            Err(error) => {
                warn_skipped_unreadable(&path, error, skipped_unreadable_count);
                continue;
            }
        };
        if !metadata.is_file() {
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
        "mkv" => "video/x-matroska",
        "webm" => "video/webm",
        "ts" | "m2ts" => "video/mp2t",
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
    use std::io;
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
        assert!(
            catalog
                .items
                .iter()
                .all(|item| item.root_label.as_deref() == Some(root_namespace.as_str()))
        );
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
        assert_eq!("video/x-matroska", catalog.items[0].media_type);
        assert_eq!("video/webm", catalog.items[2].media_type);
        assert_eq!("video/mp4", catalog.items[3].media_type);

        fs::remove_dir_all(temp).expect("temp should delete");
    }

    #[test]
    fn skips_nonexistent_root_among_valid_roots() {
        let temp = temp_dir("danmaku-scan-missing-root");
        let root = temp.join("Anime");
        let show = root.join("Example Show");
        fs::create_dir_all(&show).expect("dirs");
        write_bytes(&show.join("Episode 01.mp4"), &[1, 2, 3, 4]);
        let missing_root = temp.join("Missing");

        let scan = scan_roots(&[root.clone(), missing_root], None).expect("scan should succeed");

        assert_eq!(2, scan.scanned_root_count);
        assert_eq!(1, scan.skipped_unreadable_count);
        assert_eq!(1, scan.published_library.catalog.items.len());
        assert_eq!(
            "Example Show",
            scan.published_library.catalog.items[0].series_title
        );

        fs::remove_dir_all(temp).expect("temp should delete");
    }

    #[test]
    fn labels_items_with_their_own_root_across_multiple_roots() {
        let temp = temp_dir("danmaku-scan-multi-root-labels");
        let first_root = temp.join("Anime");
        let second_root = temp.join("AniRss");
        fs::create_dir_all(first_root.join("Alpha Show")).expect("first show dir");
        fs::create_dir_all(second_root.join("Beta Show")).expect("second show dir");
        write_bytes(&first_root.join("Alpha Show").join("Episode 01.mkv"), &[1]);
        write_bytes(&second_root.join("Beta Show").join("Episode 01.mkv"), &[2]);

        let scan =
            scan_roots(&[first_root.clone(), second_root.clone()], None).expect("scan succeeds");
        let catalog = &scan.published_library.catalog;

        assert_eq!("Headless Library", catalog.root_name);
        let labels = catalog
            .items
            .iter()
            .map(|item| {
                (
                    item.series_title.as_str(),
                    item.root_label.as_deref().expect("root label"),
                )
            })
            .collect::<Vec<_>>();
        let first_label = path_string(&absolute_normalized_path(&first_root).expect("first root"));
        let second_label =
            path_string(&absolute_normalized_path(&second_root).expect("second root"));
        assert_eq!(
            vec![
                ("Alpha Show", first_label.as_str()),
                ("Beta Show", second_label.as_str()),
            ],
            labels
        );

        fs::remove_dir_all(temp).expect("temp should delete");
    }

    #[test]
    fn rescans_only_the_selected_subtree_and_preserves_siblings() {
        let temp = temp_dir("danmaku-subtree-rescan");
        let root = temp.join("Anime");
        let selected = root.join("Selected Show");
        let sibling = root.join("Sibling Show");
        fs::create_dir_all(&selected).expect("selected dirs");
        fs::create_dir_all(&sibling).expect("sibling dirs");
        let removed = selected.join("Episode 01.mkv");
        write_bytes(&removed, &[1, 2, 3]);
        write_bytes(&sibling.join("Episode 01.mkv"), &[4, 5, 6]);

        let first = scan_roots(std::slice::from_ref(&root), None).expect("first scan");
        let sibling_before = first
            .published_library
            .catalog
            .items
            .iter()
            .find(|item| item.series_title == "Sibling Show")
            .expect("sibling item")
            .clone();
        let previous = stored_from_scan(first);

        fs::remove_file(removed).expect("old episode removes");
        write_bytes(&selected.join("Episode 02.mkv"), &[7, 8, 9, 10]);
        let target = resolve_rescan_target(&[root.clone()], &["Selected Show".to_owned()])
            .expect("target resolves");
        let rescanned = rescan_target_with_progress(
            std::slice::from_ref(&root),
            &target,
            Some(&previous),
            None,
        )
        .expect("subtree rescans");

        assert_eq!(2, rescanned.published_library.catalog.items.len());
        assert!(
            rescanned
                .published_library
                .catalog
                .items
                .iter()
                .any(|item| item.relative_path == "Selected Show/Episode 02.mkv")
        );
        let sibling_after = rescanned
            .published_library
            .catalog
            .items
            .iter()
            .find(|item| item.series_title == "Sibling Show")
            .expect("sibling remains");
        assert_eq!(sibling_before, *sibling_after);
        assert!(
            rescanned
                .published_library
                .files_by_id
                .contains_key(&sibling_before.id)
        );

        fs::remove_dir_all(temp).expect("temp should delete");
    }

    #[test]
    fn rescanning_a_deleted_subtree_removes_its_stale_items() {
        let temp = temp_dir("danmaku-deleted-subtree-rescan");
        let root = temp.join("Anime");
        let selected = root.join("Removed Show");
        fs::create_dir_all(&selected).expect("selected dirs");
        write_bytes(&selected.join("Episode 01.mkv"), &[1, 2, 3]);
        let previous =
            stored_from_scan(scan_roots(std::slice::from_ref(&root), None).expect("first scan"));
        fs::remove_dir_all(&selected).expect("folder removes");

        let target = resolve_rescan_target(&[root.clone()], &["Removed Show".to_owned()])
            .expect("target resolves");
        let rescanned = rescan_target_with_progress(
            std::slice::from_ref(&root),
            &target,
            Some(&previous),
            None,
        )
        .expect("missing subtree is an empty successful scan");

        assert!(rescanned.published_library.catalog.items.is_empty());
        assert!(rescanned.published_library.files_by_id.is_empty());
        fs::remove_dir_all(temp).expect("temp should delete");
    }

    #[test]
    fn resolves_multi_root_paths_and_rejects_traversal() {
        let temp = temp_dir("danmaku-rescan-paths");
        let first = temp.join("First");
        let second = temp.join("Second");
        fs::create_dir_all(&first).expect("first root");
        fs::create_dir_all(&second).expect("second root");
        let second_label = path_string(&absolute_normalized_path(&second).expect("normalizes"));

        let target = resolve_rescan_target(
            &[first.clone(), second.clone()],
            &[second_label, "Show".to_owned()],
        )
        .expect("multi-root target resolves");
        assert_eq!(
            LibraryRescanTarget::Subtree {
                root: absolute_normalized_path(&second).expect("root normalizes"),
                directory: absolute_normalized_path(&second.join("Show"))
                    .expect("directory normalizes"),
            },
            target
        );
        assert!(
            resolve_rescan_target(&[first], &["..".to_owned()]).is_err(),
            "parent traversal must be rejected"
        );

        fs::remove_dir_all(temp).expect("temp should delete");
    }

    #[test]
    fn classifies_vanished_entry_as_unreadable_skip() {
        let mut skipped_unreadable_count = 0;

        let classified = classify_path(
            PathBuf::from("vanished.mkv"),
            Err(io::Error::new(io::ErrorKind::NotFound, "file vanished")),
            &mut skipped_unreadable_count,
            false,
        )
        .expect("tolerant scans skip vanished entries");

        assert!(classified.is_none());
        assert_eq!(1, skipped_unreadable_count);
    }

    #[cfg(windows)]
    #[test]
    fn skips_unreadable_subdirectory_on_windows() {
        let temp = temp_dir("danmaku-scan-unreadable");
        let root = temp.join("Anime");
        let show = root.join("Example Show");
        let unreadable = root.join("Unreadable");
        fs::create_dir_all(&show).expect("show dirs");
        fs::create_dir_all(&unreadable).expect("unreadable dirs");
        write_bytes(&show.join("Episode 01.mp4"), &[1, 2, 3, 4]);
        write_bytes(&unreadable.join("Hidden Episode.mkv"), &[5, 6, 7, 8]);
        let Some(guard) = deny_windows_read(&unreadable) else {
            eprintln!(
                "skipping Windows unreadable-directory fixture; icacls could not deny reads for {}",
                unreadable.display()
            );
            fs::remove_dir_all(temp).expect("temp should delete");
            return;
        };
        if fs::read_dir(&unreadable).is_ok() {
            drop(guard);
            fs::remove_dir_all(temp).expect("temp should delete");
            panic!("unreadable fixture should reject directory reads");
        }

        let scan = scan_roots(std::slice::from_ref(&root), None).expect("scan should succeed");

        assert_eq!(1, scan.published_library.catalog.items.len());
        assert_eq!(1, scan.skipped_unreadable_count);
        assert_eq!(
            "Example Show",
            scan.published_library.catalog.items[0].series_title
        );

        drop(guard);
        fs::remove_dir_all(temp).expect("temp should delete");
    }

    #[cfg(windows)]
    #[test]
    fn scoped_rescan_rejects_unreadable_subtree_instead_of_deleting_cached_items() {
        let temp = temp_dir("danmaku-subtree-rescan-unreadable");
        let root = temp.join("Anime");
        let selected = root.join("Selected Show");
        fs::create_dir_all(&selected).expect("selected dirs");
        write_bytes(&selected.join("Episode 01.mkv"), &[1, 2, 3]);
        let previous =
            stored_from_scan(scan_roots(std::slice::from_ref(&root), None).expect("first scan"));
        let target = resolve_rescan_target(&[root.clone()], &["Selected Show".to_owned()])
            .expect("target resolves");
        let Some(guard) = deny_windows_read(&selected) else {
            eprintln!(
                "skipping Windows unreadable-directory fixture; icacls could not deny reads for {}",
                selected.display()
            );
            fs::remove_dir_all(temp).expect("temp should delete");
            return;
        };
        if fs::read_dir(&selected).is_ok() {
            drop(guard);
            fs::remove_dir_all(temp).expect("temp should delete");
            panic!("unreadable fixture should reject directory reads");
        }

        let result = rescan_target_with_progress(
            std::slice::from_ref(&root),
            &target,
            Some(&previous),
            None,
        );

        assert!(
            result.is_err(),
            "an unreadable subtree must not publish an empty replacement"
        );
        assert_eq!(1, previous.published_library.catalog.items.len());
        drop(guard);
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

    #[cfg(windows)]
    fn deny_windows_read(path: &Path) -> Option<WindowsAclDenyGuard> {
        let status = std::process::Command::new("icacls")
            .arg(path)
            .arg("/deny")
            .arg("*S-1-1-0:(RD)")
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .status()
            .ok()?;
        status
            .success()
            .then(|| WindowsAclDenyGuard { path: path.into() })
    }

    #[cfg(windows)]
    struct WindowsAclDenyGuard {
        path: PathBuf,
    }

    #[cfg(windows)]
    impl Drop for WindowsAclDenyGuard {
        fn drop(&mut self) {
            let _ = std::process::Command::new("icacls")
                .arg(&self.path)
                .arg("/remove:d")
                .arg("*S-1-1-0")
                .stdout(std::process::Stdio::null())
                .stderr(std::process::Stdio::null())
                .status();
        }
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
