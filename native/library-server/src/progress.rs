use std::collections::BTreeMap;
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::Mutex;

use crate::domain::PlaybackProgress;
use crate::{LibraryServerError, Result};

#[derive(Debug)]
pub struct PlaybackProgressStore {
    file: PathBuf,
    progress_by_media_id: Mutex<BTreeMap<String, PlaybackProgress>>,
}

impl PlaybackProgressStore {
    pub fn new(file: impl Into<PathBuf>) -> Self {
        let file = file.into();
        let progress_by_media_id = load_snapshot(&file)
            .into_iter()
            .map(|progress| (progress.media_id.clone(), progress))
            .collect();
        Self {
            file,
            progress_by_media_id: Mutex::new(progress_by_media_id),
        }
    }

    pub fn load_progress(&self, media_id: &str) -> Option<PlaybackProgress> {
        self.progress_by_media_id
            .lock()
            .expect("progress store lock should not be poisoned")
            .get(media_id)
            .cloned()
    }

    pub fn load_all_progress(&self) -> Vec<PlaybackProgress> {
        let mut progress = self
            .progress_by_media_id
            .lock()
            .expect("progress store lock should not be poisoned")
            .values()
            .cloned()
            .collect::<Vec<_>>();
        progress.sort_by(|left, right| {
            right
                .updated_at_epoch_ms
                .cmp(&left.updated_at_epoch_ms)
                .then_with(|| left.media_id.cmp(&right.media_id))
        });
        progress
    }

    pub fn save_progress(&self, progress: PlaybackProgress) -> Result<()> {
        let snapshot = {
            let mut progress_by_media_id = self
                .progress_by_media_id
                .lock()
                .expect("progress store lock should not be poisoned");
            progress_by_media_id.insert(progress.media_id.clone(), progress);
            progress_by_media_id.values().cloned().collect::<Vec<_>>()
        };
        self.write_snapshot(snapshot)
    }

    fn write_snapshot(&self, mut progress: Vec<PlaybackProgress>) -> Result<()> {
        progress.sort_by(|left, right| left.media_id.cmp(&right.media_id));
        if let Some(parent) = self.file.parent() {
            fs::create_dir_all(parent).map_err(|error| {
                LibraryServerError::with_context(
                    error,
                    format!("failed to create progress directory {}", parent.display()),
                )
            })?;
        }

        let temp = self
            .file
            .with_file_name(format!("{}.tmp", file_name_for_temp(&self.file)?));
        let body = serde_json::to_string_pretty(&progress)?;
        fs::write(&temp, body).map_err(|error| {
            LibraryServerError::with_context(
                error,
                format!("failed to write progress snapshot {}", temp.display()),
            )
        })?;
        fs::rename(&temp, &self.file).map_err(|error| {
            LibraryServerError::with_context(
                error,
                format!(
                    "failed to replace progress snapshot {}",
                    self.file.display()
                ),
            )
        })
    }
}

fn load_snapshot(file: &PathBuf) -> Vec<PlaybackProgress> {
    if !file.is_file() {
        return Vec::new();
    }
    fs::read_to_string(file)
        .ok()
        .and_then(|text| serde_json::from_str::<Vec<PlaybackProgress>>(&text).ok())
        .unwrap_or_default()
}

fn file_name_for_temp(file: &Path) -> Result<String> {
    file.file_name()
        .map(|name| name.to_string_lossy().into_owned())
        .ok_or_else(|| {
            LibraryServerError::new(format!(
                "progress path must include a file name: {}",
                file.display()
            ))
        })
}

#[cfg(test)]
mod tests {
    use std::fs;
    use std::path::PathBuf;
    use std::sync::atomic::{AtomicU64, Ordering};

    use super::*;

    static TEMP_COUNTER: AtomicU64 = AtomicU64::new(0);

    #[test]
    fn file_store_adopts_and_rewrites_jvm_progress_snapshot() {
        let temp = temp_dir("danmaku-progress-store");
        let file = temp.join("progress.json");
        fs::write(
            &file,
            r#"[
  {
    "mediaId": "b",
    "positionMs": 2,
    "durationMs": null,
    "updatedAtEpochMs": 20
  },
  {
    "mediaId": "a",
    "positionMs": 1,
    "durationMs": 100,
    "updatedAtEpochMs": 30
  }
]"#,
        )
        .expect("progress fixture should write");

        let store = PlaybackProgressStore::new(&file);
        assert_eq!(
            Some(1),
            store
                .load_progress("a")
                .map(|progress| progress.position_ms)
        );
        assert_eq!(
            vec!["a", "b"],
            store
                .load_all_progress()
                .into_iter()
                .map(|progress| progress.media_id)
                .collect::<Vec<_>>()
        );

        store
            .save_progress(PlaybackProgress {
                media_id: "c".to_owned(),
                position_ms: 3,
                duration_ms: Some(300),
                updated_at_epoch_ms: 10,
            })
            .expect("progress should save");
        let saved = fs::read_to_string(&file).expect("progress should read");
        assert!(saved.contains("\"mediaId\": \"a\""));
        assert!(saved.contains("\"mediaId\": \"b\""));
        assert!(saved.contains("\"mediaId\": \"c\""));
        assert!(
            saved.find("\"mediaId\": \"a\"").expect("a")
                < saved.find("\"mediaId\": \"b\"").expect("b")
        );
        assert!(
            saved.find("\"mediaId\": \"b\"").expect("b")
                < saved.find("\"mediaId\": \"c\"").expect("c")
        );

        fs::remove_dir_all(temp).expect("temp dir should delete");
    }

    fn temp_dir(prefix: &str) -> PathBuf {
        let id = TEMP_COUNTER.fetch_add(1, Ordering::Relaxed);
        let path = std::env::temp_dir().join(format!("{prefix}-{}-{id}", std::process::id()));
        let _ = fs::remove_dir_all(&path);
        fs::create_dir_all(&path).expect("temp dir should create");
        path
    }
}
