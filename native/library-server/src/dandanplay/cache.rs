use std::{
    collections::BTreeMap,
    fs,
    path::{Path, PathBuf},
    sync::Mutex,
};

use serde::{Deserialize, Serialize};

use crate::{LibraryServerError, Result};

use super::{
    DandanplayCommentTrack, DandanplayMatch, MILLIS_PER_DAY, parse_normalized_comments_json,
};

#[derive(Debug)]
pub struct DandanplayCommentCacheStore {
    file: PathBuf,
    entries: Mutex<BTreeMap<String, DandanplayCommentCache>>,
}

impl DandanplayCommentCacheStore {
    pub fn new(file: impl Into<PathBuf>) -> Self {
        let file = file.into();
        let entries = load_cache_snapshot(&file).unwrap_or_default();
        Self {
            file,
            entries: Mutex::new(entries),
        }
    }

    pub fn load(&self, media_id: &str) -> Result<Option<DandanplayCommentCache>> {
        Ok(self
            .entries
            .lock()
            .map_err(|_| LibraryServerError::new("dandanplay cache lock poisoned"))?
            .get(media_id)
            .cloned())
    }

    pub fn save(&self, cache: DandanplayCommentCache) -> Result<()> {
        let mut entries = self
            .entries
            .lock()
            .map_err(|_| LibraryServerError::new("dandanplay cache lock poisoned"))?;
        entries.insert(cache.media_id.clone(), cache);
        write_cache_snapshot(&self.file, &entries)
    }

    pub fn delete_older_than(&self, cutoff_epoch_ms: u64) -> Result<()> {
        let mut entries = self
            .entries
            .lock()
            .map_err(|_| LibraryServerError::new("dandanplay cache lock poisoned"))?;
        let original_len = entries.len();
        entries.retain(|_, cache| cache.fetched_at_epoch_ms >= cutoff_epoch_ms);
        if entries.len() != original_len {
            write_cache_snapshot(&self.file, &entries)?;
        }
        Ok(())
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DandanplayCommentCache {
    pub media_id: String,
    pub file_hash: String,
    pub file_name: String,
    pub file_size_bytes: u64,
    pub episode_id: Option<u64>,
    pub anime_id: Option<u64>,
    pub anime_title: Option<String>,
    pub episode_title: Option<String>,
    pub shift_seconds: Option<f64>,
    pub comments_json: String,
    pub rendered_ass_path: Option<String>,
    pub fetched_at_epoch_ms: u64,
}

impl DandanplayCommentCache {
    pub(super) fn is_expired(&self, now_epoch_ms: u64, max_age_days: u32) -> bool {
        now_epoch_ms.saturating_sub(self.fetched_at_epoch_ms) > max_age_days as u64 * MILLIS_PER_DAY
    }

    pub(super) fn to_comment_track(&self) -> Option<DandanplayCommentTrack> {
        let episode_id = self.episode_id?;
        Some(DandanplayCommentTrack {
            match_candidate: DandanplayMatch::new(
                episode_id,
                self.anime_id,
                self.anime_title.clone(),
                self.episode_title.clone(),
                self.shift_seconds,
            ),
            events: parse_normalized_comments_json(&self.comments_json),
        })
    }
}

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DandanplayCommentCacheSnapshot {
    schema_version: u32,
    entries: Vec<DandanplayCommentCache>,
}

fn load_cache_snapshot(file: &Path) -> Option<BTreeMap<String, DandanplayCommentCache>> {
    if !file.is_file() {
        return Some(BTreeMap::new());
    }
    let snapshot =
        serde_json::from_str::<DandanplayCommentCacheSnapshot>(&fs::read_to_string(file).ok()?)
            .ok()?;
    if snapshot.schema_version != 1 {
        return None;
    }
    Some(
        snapshot
            .entries
            .into_iter()
            .filter(|entry| !entry.media_id.trim().is_empty())
            .map(|entry| (entry.media_id.clone(), entry))
            .collect(),
    )
}

fn write_cache_snapshot(
    file: &Path,
    entries: &BTreeMap<String, DandanplayCommentCache>,
) -> Result<()> {
    if let Some(parent) = file.parent() {
        fs::create_dir_all(parent).map_err(|error| {
            LibraryServerError::with_context(
                error,
                format!(
                    "failed to create dandanplay cache directory {}",
                    parent.display()
                ),
            )
        })?;
    }
    let file_name = file
        .file_name()
        .ok_or_else(|| {
            LibraryServerError::new(format!(
                "dandanplay cache path must include a file name: {}",
                file.display()
            ))
        })?
        .to_string_lossy();
    let temp = file.with_file_name(format!("{file_name}.tmp"));
    let snapshot = DandanplayCommentCacheSnapshot {
        schema_version: 1,
        entries: entries.values().cloned().collect(),
    };
    fs::write(&temp, serde_json::to_string_pretty(&snapshot)?).map_err(|error| {
        LibraryServerError::with_context(
            error,
            format!("failed to write dandanplay cache {}", temp.display()),
        )
    })?;
    fs::rename(&temp, file).map_err(|error| {
        LibraryServerError::with_context(
            error,
            format!("failed to replace dandanplay cache {}", file.display()),
        )
    })
}
