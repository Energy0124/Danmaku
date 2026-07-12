//! Dandanplay-driven catalog recognition overlay.
//!
//! When danmaku is resolved for a media item the selected match carries the
//! dandanplay anime identity. Recording that identity here lets the published
//! catalog auto-group episodes under the recognized anime (clients group by
//! `anime_metadata` when present) without any manual web-UI step. The overlay
//! is applied non-destructively: items that already carry provider metadata are
//! left untouched.

use std::collections::BTreeMap;
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::time::{SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};

use crate::catalog::{
    ExternalAnimeId, ExternalAnimeProvider, LibraryAnimeMetadata, LibraryCatalog,
    LibraryItemMetadataStatus,
};
use crate::{LibraryServerError, Result};

/// A dandanplay-recognized identity for one media item.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CatalogMetadataEntry {
    pub media_id: String,
    pub dandanplay_anime_id: u64,
    pub anime_title: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub episode_title: Option<String>,
    pub recorded_at_epoch_ms: u64,
}

impl CatalogMetadataEntry {
    /// True when both entries describe the same recognized anime/episode,
    /// ignoring the record timestamp, so unchanged resolves avoid a disk write.
    fn same_identity(&self, other: &Self) -> bool {
        self.dandanplay_anime_id == other.dandanplay_anime_id
            && self.anime_title == other.anime_title
            && self.episode_title == other.episode_title
    }

    fn to_anime_metadata(&self) -> LibraryAnimeMetadata {
        LibraryAnimeMetadata {
            anime_id: ExternalAnimeId {
                provider: ExternalAnimeProvider::Dandanplay,
                value: self.dandanplay_anime_id,
            },
            display_title: self.anime_title.clone(),
            primary_title: self.anime_title.clone(),
            chinese_title: None,
            english_title: None,
            japanese_title: None,
            alternate_names: Vec::new(),
            external_links: Vec::new(),
            image_url: None,
            episode_count: None,
            start_year: None,
        }
    }
}

#[derive(Debug)]
pub struct CatalogMetadataStore {
    file: PathBuf,
    entries_by_media_id: Mutex<BTreeMap<String, CatalogMetadataEntry>>,
}

impl CatalogMetadataStore {
    pub fn new(file: impl Into<PathBuf>) -> Self {
        let file = file.into();
        let entries_by_media_id = load_snapshot(&file)
            .into_iter()
            .map(|entry| (entry.media_id.clone(), entry))
            .collect();
        Self {
            file,
            entries_by_media_id: Mutex::new(entries_by_media_id),
        }
    }

    /// Records the recognized identity for a media item. Returns `Ok(false)`
    /// when the stored identity is unchanged (no write performed).
    pub fn record(
        &self,
        media_id: &str,
        dandanplay_anime_id: u64,
        anime_title: String,
        episode_title: Option<String>,
    ) -> Result<bool> {
        let entry = CatalogMetadataEntry {
            media_id: media_id.to_owned(),
            dandanplay_anime_id,
            anime_title: anime_title.trim().to_owned(),
            episode_title: episode_title
                .map(|title| title.trim().to_owned())
                .filter(|title| !title.is_empty()),
            recorded_at_epoch_ms: current_epoch_ms(),
        };
        if entry.anime_title.is_empty() || entry.dandanplay_anime_id == 0 {
            return Ok(false);
        }
        let snapshot = {
            let mut entries = self
                .entries_by_media_id
                .lock()
                .expect("catalog metadata lock should not be poisoned");
            if entries
                .get(media_id)
                .is_some_and(|existing| existing.same_identity(&entry))
            {
                return Ok(false);
            }
            entries.insert(media_id.to_owned(), entry);
            entries.values().cloned().collect::<Vec<_>>()
        };
        self.write_snapshot(snapshot)?;
        Ok(true)
    }

    pub fn get(&self, media_id: &str) -> Option<CatalogMetadataEntry> {
        self.entries_by_media_id
            .lock()
            .expect("catalog metadata lock should not be poisoned")
            .get(media_id)
            .cloned()
    }

    /// Returns a catalog clone with recognized identities merged onto items that
    /// do not already carry provider metadata.
    pub fn enrich_catalog(&self, catalog: &LibraryCatalog) -> LibraryCatalog {
        let entries = self
            .entries_by_media_id
            .lock()
            .expect("catalog metadata lock should not be poisoned");
        if entries.is_empty() {
            return catalog.clone();
        }
        let mut enriched = catalog.clone();
        for item in &mut enriched.items {
            if item.anime_metadata.is_some() {
                continue;
            }
            let Some(entry) = entries.get(&item.id) else {
                continue;
            };
            item.anime_metadata = Some(entry.to_anime_metadata());
            item.metadata_status = LibraryItemMetadataStatus::Ready;
            if let Some(episode_title) = &entry.episode_title
                && !episode_title.is_empty()
            {
                item.episode_title = episode_title.clone();
            }
        }
        enriched
    }

    fn write_snapshot(&self, mut entries: Vec<CatalogMetadataEntry>) -> Result<()> {
        entries.sort_by(|left, right| left.media_id.cmp(&right.media_id));
        if let Some(parent) = self.file.parent() {
            fs::create_dir_all(parent).map_err(|error| {
                LibraryServerError::with_context(
                    error,
                    format!(
                        "failed to create catalog metadata directory {}",
                        parent.display()
                    ),
                )
            })?;
        }
        let temp = self
            .file
            .with_file_name(format!("{}.tmp", file_name_for_temp(&self.file)?));
        let body = serde_json::to_string_pretty(&entries)?;
        fs::write(&temp, body).map_err(|error| {
            LibraryServerError::with_context(
                error,
                format!(
                    "failed to write catalog metadata snapshot {}",
                    temp.display()
                ),
            )
        })?;
        fs::rename(&temp, &self.file).map_err(|error| {
            LibraryServerError::with_context(
                error,
                format!(
                    "failed to replace catalog metadata snapshot {}",
                    self.file.display()
                ),
            )
        })
    }
}

fn load_snapshot(file: &Path) -> Vec<CatalogMetadataEntry> {
    if !file.is_file() {
        return Vec::new();
    }
    fs::read_to_string(file)
        .ok()
        .and_then(|text| serde_json::from_str::<Vec<CatalogMetadataEntry>>(&text).ok())
        .unwrap_or_default()
}

fn file_name_for_temp(file: &Path) -> Result<String> {
    file.file_name()
        .map(|name| name.to_string_lossy().into_owned())
        .ok_or_else(|| {
            LibraryServerError::new(format!(
                "catalog metadata path must include a file name: {}",
                file.display()
            ))
        })
}

fn current_epoch_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis()
        .min(u64::MAX as u128) as u64
}

#[cfg(test)]
mod tests {
    use std::sync::atomic::{AtomicU64, Ordering};

    use super::*;
    use crate::catalog::{LibraryCatalog, LibraryMediaItem};

    static TEMP_COUNTER: AtomicU64 = AtomicU64::new(0);

    fn temp_dir(prefix: &str) -> PathBuf {
        let id = TEMP_COUNTER.fetch_add(1, Ordering::Relaxed);
        let path = std::env::temp_dir().join(format!("{prefix}-{}-{id}", std::process::id()));
        let _ = fs::remove_dir_all(&path);
        fs::create_dir_all(&path).expect("temp dir should create");
        path
    }

    fn item(id: &str, series_title: &str) -> LibraryMediaItem {
        LibraryMediaItem {
            id: id.to_owned(),
            series_title: series_title.to_owned(),
            episode_title: "Episode 01".to_owned(),
            relative_path: format!("{series_title}/{id}.mkv"),
            size_bytes: 1,
            media_type: "video/x-matroska".to_owned(),
            stream_path: format!("/media/{id}"),
            indexed_at_epoch_ms: 0,
            subtitles: Vec::new(),
            poster_path: None,
            anime_metadata: None,
            metadata_status: LibraryItemMetadataStatus::NotAvailable,
        }
    }

    #[test]
    fn records_persist_and_enrich_only_unmatched_items() {
        let temp = temp_dir("danmaku-catalog-metadata");
        let file = temp.join("catalog-metadata.json");
        let store = CatalogMetadataStore::new(&file);

        assert!(
            store
                .record("m1", 42, "Frieren".to_owned(), Some("Ep 1".to_owned()))
                .expect("record m1")
        );
        // Same identity again is a no-op write.
        assert!(
            !store
                .record("m1", 42, "Frieren".to_owned(), Some("Ep 1".to_owned()))
                .expect("record m1 again")
        );
        // Blank/zero identities are ignored.
        assert!(
            !store
                .record("m2", 0, "x".to_owned(), None)
                .expect("zero id")
        );
        assert!(
            !store
                .record("m2", 7, "   ".to_owned(), None)
                .expect("blank title")
        );

        let catalog = LibraryCatalog {
            root_name: "Anime".to_owned(),
            indexed_at_epoch_ms: 0,
            items: vec![item("m1", "raw.folder.name"), item("m3", "Other")],
        };
        let enriched = store.enrich_catalog(&catalog);
        let m1 = &enriched.items[0];
        let metadata = m1.anime_metadata.as_ref().expect("m1 has metadata");
        assert_eq!(metadata.display_title, "Frieren");
        assert_eq!(metadata.anime_id.value, 42);
        assert_eq!(
            metadata.anime_id.provider,
            ExternalAnimeProvider::Dandanplay
        );
        assert_eq!(m1.episode_title, "Ep 1");
        assert_eq!(m1.metadata_status, LibraryItemMetadataStatus::Ready);
        // Unrecognized item is untouched.
        assert!(enriched.items[1].anime_metadata.is_none());

        // A fresh store reloads persisted entries.
        let reloaded = CatalogMetadataStore::new(&file);
        assert_eq!(
            reloaded.get("m1").expect("reloaded m1").anime_title,
            "Frieren"
        );

        fs::remove_dir_all(temp).expect("temp dir should delete");
    }

    #[test]
    fn enrich_preserves_existing_provider_metadata() {
        let temp = temp_dir("danmaku-catalog-metadata-existing");
        let store = CatalogMetadataStore::new(temp.join("catalog-metadata.json"));
        store
            .record("m1", 42, "Frieren".to_owned(), None)
            .expect("record");
        let mut existing = item("m1", "raw");
        existing.anime_metadata = Some(LibraryAnimeMetadata {
            anime_id: ExternalAnimeId {
                provider: ExternalAnimeProvider::MyAnimeList,
                value: 999,
            },
            display_title: "Existing".to_owned(),
            primary_title: "Existing".to_owned(),
            chinese_title: None,
            english_title: None,
            japanese_title: None,
            alternate_names: Vec::new(),
            external_links: Vec::new(),
            image_url: None,
            episode_count: None,
            start_year: None,
        });
        let catalog = LibraryCatalog {
            root_name: "Anime".to_owned(),
            indexed_at_epoch_ms: 0,
            items: vec![existing],
        };
        let enriched = store.enrich_catalog(&catalog);
        let metadata = enriched.items[0]
            .anime_metadata
            .as_ref()
            .expect("existing metadata kept");
        assert_eq!(metadata.anime_id.value, 999);

        fs::remove_dir_all(temp).expect("temp dir should delete");
    }
}
