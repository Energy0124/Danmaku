//! Disk cache of the last session's library data.
//!
//! Lets the app land on a fully populated Library screen immediately at
//! launch — before the local sidecar has even started — and refresh it in
//! the background once the server is reachable.

use std::{
    fs, io,
    path::{Path, PathBuf},
};

use serde::{Deserialize, Serialize};

use crate::library::{LibraryCatalog, PlaybackProgress};

#[derive(Clone, Debug, Deserialize, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SessionCacheData {
    pub base_url: String,
    pub saved_at_epoch_ms: u64,
    pub catalog: LibraryCatalog,
    #[serde(default)]
    pub progresses: Vec<PlaybackProgress>,
}

#[derive(Clone, Debug)]
pub struct SessionCacheStore {
    path: PathBuf,
}

impl SessionCacheStore {
    pub fn for_current_user() -> Self {
        let root = std::env::var_os("LOCALAPPDATA")
            .map(PathBuf::from)
            .or_else(|| std::env::current_dir().ok())
            .unwrap_or_else(|| PathBuf::from("."));
        Self {
            path: root.join("Danmaku").join("player-session-cache.json"),
        }
    }

    #[cfg(test)]
    fn at(path: PathBuf) -> Self {
        Self { path }
    }

    /// A corrupt or missing cache simply means no seed; never an error.
    pub fn load(&self) -> Option<SessionCacheData> {
        let json = fs::read_to_string(&self.path).ok()?;
        serde_json::from_str::<SessionCacheData>(&json)
            .ok()
            .filter(|cache| !cache.catalog.items.is_empty())
    }

    pub fn save(&self, cache: &SessionCacheData) -> io::Result<()> {
        let parent = self.path.parent().unwrap_or_else(|| Path::new("."));
        fs::create_dir_all(parent)?;
        let temporary = self.path.with_extension("json.tmp");
        let json = serde_json::to_vec(cache)
            .map_err(|error| io::Error::new(io::ErrorKind::InvalidData, error))?;
        fs::write(&temporary, json)?;
        if self.path.exists() {
            fs::remove_file(&self.path)?;
        }
        fs::rename(temporary, &self.path)
    }
}

pub fn current_epoch_ms() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|elapsed| elapsed.as_millis() as u64)
        .unwrap_or(0)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn catalog_with_one_item() -> LibraryCatalog {
        serde_json::from_value(serde_json::json!({
            "rootName": "Anime",
            "indexedAtEpochMs": 7,
            "items": [{
                "id": "a",
                "seriesTitle": "S",
                "episodeTitle": "E1",
                "relativePath": "S/E1.mkv",
                "sizeBytes": 1,
                "mediaType": "video/x-matroska",
                "streamPath": "/media/a"
            }]
        }))
        .expect("catalog parses")
    }

    #[test]
    fn round_trips_catalog_and_progress() {
        let path = std::env::temp_dir().join(format!(
            "danmaku-player-session-cache-{}.json",
            std::process::id()
        ));
        let store = SessionCacheStore::at(path.clone());
        let expected = SessionCacheData {
            base_url: "http://127.0.0.1:8686".to_owned(),
            saved_at_epoch_ms: 123,
            catalog: catalog_with_one_item(),
            progresses: vec![PlaybackProgress {
                media_id: "a".to_owned(),
                position_ms: 1000,
                duration_ms: Some(2000),
                updated_at_epoch_ms: 5,
            }],
        };
        store.save(&expected).expect("cache saves");
        assert_eq!(store.load(), Some(expected));
        let _ = fs::remove_file(path);
    }

    #[test]
    fn empty_or_corrupt_caches_load_as_none() {
        let path = std::env::temp_dir().join(format!(
            "danmaku-player-session-cache-invalid-{}.json",
            std::process::id()
        ));
        let store = SessionCacheStore::at(path.clone());
        assert_eq!(store.load(), None);

        fs::write(&path, "{not json").expect("fixture write");
        assert_eq!(store.load(), None);

        // A cache holding an empty catalog is useless as a seed.
        let empty = SessionCacheData {
            base_url: "http://127.0.0.1:8686".to_owned(),
            saved_at_epoch_ms: 1,
            catalog: LibraryCatalog::default(),
            progresses: Vec::new(),
        };
        store.save(&empty).expect("cache saves");
        assert_eq!(store.load(), None);
        let _ = fs::remove_file(path);
    }
}
