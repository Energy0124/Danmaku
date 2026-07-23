use std::collections::{BTreeMap, BTreeSet};
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::time::{SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};

use crate::catalog::{ExternalAnimeProvider, LibraryCatalog};
use crate::catalog_metadata::CatalogMetadataStore;
use crate::dandanplay::DandanplayResolver;
use crate::{LibraryServerError, Result};

const SCHEMA_VERSION: u32 = 1;
const FAILURE_MESSAGE: &str = "The last danmaku refresh failed.";

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AttentionFailure {
    pub media_id: String,
    pub reason_code: String,
    pub message: String,
    pub failed_at_epoch_ms: u64,
    pub attempt_count: u32,
}

#[derive(Debug, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct FailureSnapshot {
    schema_version: u32,
    #[serde(default)]
    failures: Vec<AttentionFailure>,
}

#[derive(Debug)]
pub struct AttentionFailureStore {
    file: PathBuf,
    failures: Mutex<BTreeMap<String, AttentionFailure>>,
}

impl AttentionFailureStore {
    pub fn open(file: impl Into<PathBuf>) -> Result<Self> {
        let file = file.into();
        let failures = load_snapshot(&file)?
            .failures
            .into_iter()
            .map(|failure| (failure.media_id.clone(), failure))
            .collect();
        Ok(Self {
            file,
            failures: Mutex::new(failures),
        })
    }

    pub fn record_refresh_failure(&self, media_id: &str) -> Result<()> {
        let mut failures = self
            .failures
            .lock()
            .map_err(|_| LibraryServerError::new("attention failure lock poisoned"))?;
        let attempt_count = failures
            .get(media_id)
            .map_or(1, |failure| failure.attempt_count.saturating_add(1));
        failures.insert(
            media_id.to_owned(),
            AttentionFailure {
                media_id: media_id.to_owned(),
                reason_code: "DANMAKU_REFRESH_FAILED".to_owned(),
                message: FAILURE_MESSAGE.to_owned(),
                failed_at_epoch_ms: current_epoch_ms(),
                attempt_count,
            },
        );
        self.write_locked(&failures)
    }

    pub fn clear(&self, media_id: &str) -> Result<()> {
        let mut failures = self
            .failures
            .lock()
            .map_err(|_| LibraryServerError::new("attention failure lock poisoned"))?;
        if failures.remove(media_id).is_some() {
            self.write_locked(&failures)?;
        }
        Ok(())
    }

    pub fn snapshot_for(&self, published_ids: &BTreeSet<&str>) -> Vec<AttentionFailure> {
        self.failures
            .lock()
            .map(|failures| {
                failures
                    .values()
                    .filter(|failure| published_ids.contains(failure.media_id.as_str()))
                    .cloned()
                    .collect()
            })
            .unwrap_or_default()
    }

    fn write_locked(&self, failures: &BTreeMap<String, AttentionFailure>) -> Result<()> {
        if let Some(parent) = self.file.parent() {
            fs::create_dir_all(parent).map_err(|error| {
                LibraryServerError::with_context(
                    error,
                    format!(
                        "failed to create attention state directory {}",
                        parent.display()
                    ),
                )
            })?;
        }
        let snapshot = FailureSnapshot {
            schema_version: SCHEMA_VERSION,
            failures: failures.values().cloned().collect(),
        };
        let temp = self.file.with_extension("json.tmp");
        fs::write(&temp, serde_json::to_string_pretty(&snapshot)?).map_err(|error| {
            LibraryServerError::with_context(
                error,
                format!("failed to write attention state {}", temp.display()),
            )
        })?;
        fs::rename(&temp, &self.file).map_err(|error| {
            LibraryServerError::with_context(
                error,
                format!("failed to replace attention state {}", self.file.display()),
            )
        })
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LibraryAttentionDocument {
    pub generated_at_epoch_ms: u64,
    pub provider: AttentionProviderStatus,
    pub summary: AttentionSummary,
    pub items: Vec<LibraryAttentionItem>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AttentionProviderStatus {
    pub available: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub reason_code: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct AttentionSummary {
    pub total: usize,
    pub needing_attention: usize,
    pub unmapped: usize,
    pub missing_cache: usize,
    pub stale_cache: usize,
    pub failed: usize,
    pub conflicting: usize,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LibraryAttentionItem {
    pub media_id: String,
    pub mapping_status: AttentionMappingStatus,
    pub cache_status: AttentionCacheStatus,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub anime_id: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub episode_id: Option<u64>,
    pub issue_codes: Vec<AttentionIssueCode>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub last_failure: Option<AttentionFailure>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum AttentionMappingStatus {
    Mapped,
    Unmapped,
}
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum AttentionCacheStatus {
    Fresh,
    Stale,
    Missing,
}
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum AttentionIssueCode {
    UnmappedAnime,
    MissingDanmakuCache,
    StaleDanmakuCache,
    ConflictingAnimeIds,
    RefreshFailed,
}

pub fn build_attention_document(
    catalog: &LibraryCatalog,
    resolver: Option<&DandanplayResolver>,
    metadata_store: Option<&CatalogMetadataStore>,
    failure_store: Option<&AttentionFailureStore>,
) -> LibraryAttentionDocument {
    let published_ids = catalog
        .items
        .iter()
        .map(|item| item.id.as_str())
        .collect::<BTreeSet<_>>();
    let failures = failure_store
        .map(|store| store.snapshot_for(&published_ids))
        .unwrap_or_default()
        .into_iter()
        .map(|failure| (failure.media_id.clone(), failure))
        .collect::<BTreeMap<_, _>>();
    let mut ids_by_series = BTreeMap::<String, BTreeSet<u64>>::new();
    for item in &catalog.items {
        if let Some(id) = dandanplay_anime_id(item.anime_metadata.as_ref()) {
            ids_by_series
                .entry(item.series_title.clone())
                .or_default()
                .insert(id);
        }
    }
    let conflicts = ids_by_series
        .into_iter()
        .filter_map(|(series, ids)| (ids.len() > 1).then_some(series))
        .collect::<BTreeSet<_>>();
    let mut summary = AttentionSummary {
        total: catalog.items.len(),
        ..Default::default()
    };
    let mut items = Vec::with_capacity(catalog.items.len());
    for item in &catalog.items {
        let inspection = resolver.and_then(|resolver| {
            resolver
                .inspect_cache(&item.id, item.size_bytes)
                .ok()
                .flatten()
        });
        let stored_identity = metadata_store.and_then(|store| store.get(&item.id));
        let anime_id = inspection
            .as_ref()
            .and_then(|entry| entry.anime_id)
            .or_else(|| {
                stored_identity
                    .as_ref()
                    .map(|entry| entry.dandanplay_anime_id)
            })
            .or_else(|| dandanplay_anime_id(item.anime_metadata.as_ref()));
        let episode_id = inspection
            .as_ref()
            .and_then(|entry| entry.episode_id)
            .or_else(|| stored_identity.and_then(|entry| entry.dandanplay_episode_id));
        let mapping_status = if anime_id.is_some() {
            AttentionMappingStatus::Mapped
        } else {
            summary.unmapped += 1;
            AttentionMappingStatus::Unmapped
        };
        let cache_status = match inspection {
            Some(entry) if entry.fresh => AttentionCacheStatus::Fresh,
            Some(_) => {
                summary.stale_cache += 1;
                AttentionCacheStatus::Stale
            }
            None => {
                summary.missing_cache += 1;
                AttentionCacheStatus::Missing
            }
        };
        let last_failure = failures.get(&item.id).cloned();
        if last_failure.is_some() {
            summary.failed += 1;
        }
        let conflicting = conflicts.contains(&item.series_title);
        if conflicting {
            summary.conflicting += 1;
        }
        let mut issues = BTreeSet::new();
        if mapping_status == AttentionMappingStatus::Unmapped {
            issues.insert(AttentionIssueCode::UnmappedAnime);
        }
        match cache_status {
            AttentionCacheStatus::Fresh => {}
            AttentionCacheStatus::Stale => {
                issues.insert(AttentionIssueCode::StaleDanmakuCache);
            }
            AttentionCacheStatus::Missing => {
                issues.insert(AttentionIssueCode::MissingDanmakuCache);
            }
        }
        if conflicting {
            issues.insert(AttentionIssueCode::ConflictingAnimeIds);
        }
        if last_failure.is_some() {
            issues.insert(AttentionIssueCode::RefreshFailed);
        }
        if !issues.is_empty() {
            summary.needing_attention += 1;
        }
        items.push(LibraryAttentionItem {
            media_id: item.id.clone(),
            mapping_status,
            cache_status,
            anime_id,
            episode_id,
            issue_codes: issues.into_iter().collect(),
            last_failure,
        });
    }
    LibraryAttentionDocument {
        generated_at_epoch_ms: current_epoch_ms(),
        provider: AttentionProviderStatus {
            available: resolver.is_some(),
            reason_code: resolver
                .is_none()
                .then(|| "DANDANPLAY_UNAVAILABLE".to_owned()),
        },
        summary,
        items,
    }
}

fn dandanplay_anime_id(metadata: Option<&crate::catalog::LibraryAnimeMetadata>) -> Option<u64> {
    metadata
        .filter(|metadata| metadata.anime_id.provider == ExternalAnimeProvider::Dandanplay)
        .map(|metadata| metadata.anime_id.value)
}

fn load_snapshot(file: &Path) -> Result<FailureSnapshot> {
    if !file.exists() {
        return Ok(FailureSnapshot {
            schema_version: SCHEMA_VERSION,
            failures: Vec::new(),
        });
    }
    let body = fs::read_to_string(file).map_err(|error| {
        LibraryServerError::with_context(
            error,
            format!("failed to read attention state {}", file.display()),
        )
    })?;
    let snapshot: FailureSnapshot = serde_json::from_str(&body)?;
    if snapshot.schema_version != SCHEMA_VERSION {
        return Err(LibraryServerError::new(format!(
            "unsupported attention state schema version {}",
            snapshot.schema_version
        )));
    }
    Ok(snapshot)
}

fn current_epoch_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis()
        .min(u128::from(u64::MAX)) as u64
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::catalog::{
        ExternalAnimeId, LibraryAnimeMetadata, LibraryItemMetadataStatus, LibraryMediaItem,
    };
    struct TestDirectory(PathBuf);
    impl TestDirectory {
        fn new(label: &str) -> Self {
            let path = std::env::temp_dir().join(format!(
                "danmaku-attention-{label}-{}-{}",
                std::process::id(),
                current_epoch_ms()
            ));
            fs::create_dir_all(&path).unwrap();
            Self(path)
        }
    }
    impl Drop for TestDirectory {
        fn drop(&mut self) {
            let _ = fs::remove_dir_all(&self.0);
        }
    }

    fn item(id: &str, series_title: &str, anime_id: Option<u64>) -> LibraryMediaItem {
        let anime_metadata = anime_id.map(|value| LibraryAnimeMetadata {
            anime_id: ExternalAnimeId {
                provider: ExternalAnimeProvider::Dandanplay,
                value,
            },
            display_title: series_title.to_owned(),
            primary_title: series_title.to_owned(),
            chinese_title: None,
            english_title: None,
            japanese_title: None,
            alternate_names: Vec::new(),
            external_links: Vec::new(),
            image_url: None,
            episode_count: None,
            start_year: None,
        });
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
            root_label: None,
            anime_metadata,
            metadata_status: LibraryItemMetadataStatus::NotAvailable,
        }
    }

    fn catalog(items: Vec<LibraryMediaItem>) -> LibraryCatalog {
        LibraryCatalog {
            root_name: "Anime".to_owned(),
            indexed_at_epoch_ms: 0,
            items,
        }
    }

    #[test]
    fn failure_store_round_trips_increments_and_clears() {
        let directory = TestDirectory::new("round-trip");
        let file = directory.0.join("attention.json");
        let store = AttentionFailureStore::open(&file).unwrap();
        store.record_refresh_failure("media-1").unwrap();
        store.record_refresh_failure("media-1").unwrap();
        let reopened = AttentionFailureStore::open(&file).unwrap();
        let ids = BTreeSet::from(["media-1"]);
        assert_eq!(reopened.snapshot_for(&ids)[0].attempt_count, 2);
        reopened.clear("media-1").unwrap();
        assert!(reopened.snapshot_for(&ids).is_empty());
    }

    #[test]
    fn unavailable_provider_reports_unmapped_and_missing_cache() {
        let document = build_attention_document(
            &catalog(vec![item("media-1", "Frieren", None)]),
            None,
            None,
            None,
        );

        assert!(!document.provider.available);
        assert_eq!(
            document.provider.reason_code.as_deref(),
            Some("DANDANPLAY_UNAVAILABLE")
        );
        assert_eq!(document.summary.needing_attention, 1);
        assert_eq!(document.summary.unmapped, 1);
        assert_eq!(document.summary.missing_cache, 1);
        assert_eq!(
            document.items[0].issue_codes,
            vec![
                AttentionIssueCode::UnmappedAnime,
                AttentionIssueCode::MissingDanmakuCache,
            ]
        );
    }

    #[test]
    fn conflicting_dandanplay_ids_flag_every_episode_in_the_series() {
        let document = build_attention_document(
            &catalog(vec![
                item("media-1", "Frieren", Some(42)),
                item("media-2", "Frieren", Some(84)),
            ]),
            None,
            None,
            None,
        );

        assert_eq!(document.summary.conflicting, 2);
        assert!(document.items.iter().all(|item| {
            item.issue_codes
                .contains(&AttentionIssueCode::ConflictingAnimeIds)
        }));
    }

    #[test]
    fn persisted_episode_identity_is_returned_when_cache_is_missing() {
        let directory = TestDirectory::new("preserved-identity");
        let metadata = CatalogMetadataStore::new(directory.0.join("metadata.json"));
        metadata
            .record_with_episode(
                "media-1",
                42,
                "Frieren".to_owned(),
                Some("Episode 01".to_owned()),
                Some(420001),
            )
            .unwrap();

        let document = build_attention_document(
            &catalog(vec![item("media-1", "Frieren", None)]),
            None,
            Some(&metadata),
            None,
        );
        assert_eq!(
            document.items[0].mapping_status,
            AttentionMappingStatus::Mapped
        );
        assert_eq!(document.items[0].anime_id, Some(42));
        assert_eq!(document.items[0].episode_id, Some(420001));
        assert_eq!(
            document.items[0].cache_status,
            AttentionCacheStatus::Missing
        );
    }

    #[test]
    fn rejects_unknown_schema() {
        let directory = TestDirectory::new("schema");
        let file = directory.0.join("attention.json");
        fs::write(&file, r#"{"schemaVersion":99,"failures":[]}"#).unwrap();
        assert!(
            AttentionFailureStore::open(file)
                .unwrap_err()
                .to_string()
                .contains("schema version 99")
        );
    }
}
