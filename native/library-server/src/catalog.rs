use std::collections::BTreeMap;
use std::fs;
use std::path::{Component, Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Deserializer, Serialize, Serializer, ser::SerializeMap};
use serde_json::Value;

use crate::scanner::LibraryScan;
use crate::{LibraryServerError, Result};

const SCHEMA_VERSION: u32 = 1;

pub type PathMap = BTreeMap<String, PathBuf>;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CatalogStore {
    file: PathBuf,
}

impl CatalogStore {
    pub fn new(file: impl Into<PathBuf>) -> Self {
        Self { file: file.into() }
    }

    pub fn load(&self) -> Result<Option<HeadlessStoredLibrary>> {
        if !self.file.is_file() {
            return Ok(None);
        }

        // The JVM store returns null for malformed or unsupported catalog
        // snapshots. Keep that behavior so a bad cache does not block startup.
        let text = match fs::read_to_string(&self.file) {
            Ok(text) => text,
            Err(_) => return Ok(None),
        };
        let value = match serde_json::from_str::<Value>(&text) {
            Ok(value) => value,
            Err(_) => return Ok(None),
        };
        Ok(stored_library_from_value(&value))
    }

    pub fn save(&self, published_library: PublishedLibrary) -> Result<HeadlessStoredLibrary> {
        let stored = HeadlessStoredLibrary {
            published_library,
            saved_at_epoch_ms: current_epoch_ms(),
            file_last_modified_epoch_ms_by_id: BTreeMap::new(),
        };
        self.save_stored(&stored)?;
        Ok(stored)
    }

    pub fn save_scan(&self, scan: LibraryScan) -> Result<HeadlessStoredLibrary> {
        let stored = HeadlessStoredLibrary {
            published_library: scan.published_library,
            saved_at_epoch_ms: current_epoch_ms(),
            file_last_modified_epoch_ms_by_id: scan.file_last_modified_epoch_ms_by_id,
        };
        self.save_stored(&stored)?;
        Ok(stored)
    }

    pub fn save_stored(&self, stored: &HeadlessStoredLibrary) -> Result<()> {
        if let Some(parent) = self.file.parent() {
            fs::create_dir_all(parent).map_err(|error| {
                LibraryServerError::with_context(
                    error,
                    format!("failed to create catalog directory {}", parent.display()),
                )
            })?;
        }

        let temp = self
            .file
            .with_file_name(format!("{}.tmp", self.file_name_for_temp()?));
        let snapshot = StoredLibrarySnapshot::try_from(stored)?;
        let body = serde_json::to_string_pretty(&snapshot)?;
        fs::write(&temp, body).map_err(|error| {
            LibraryServerError::with_context(
                error,
                format!("failed to write catalog snapshot {}", temp.display()),
            )
        })?;
        fs::rename(&temp, &self.file).map_err(|error| {
            LibraryServerError::with_context(
                error,
                format!("failed to replace catalog snapshot {}", self.file.display()),
            )
        })
    }

    fn file_name_for_temp(&self) -> Result<String> {
        self.file
            .file_name()
            .map(|name| name.to_string_lossy().into_owned())
            .ok_or_else(|| {
                LibraryServerError::new(format!(
                    "catalog path must include a file name: {}",
                    self.file.display()
                ))
            })
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct HeadlessStoredLibrary {
    pub published_library: PublishedLibrary,
    pub saved_at_epoch_ms: u64,
    pub file_last_modified_epoch_ms_by_id: BTreeMap<String, u64>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PublishedLibrary {
    pub catalog: LibraryCatalog,
    pub files_by_id: PathMap,
    pub subtitle_files_by_id: PathMap,
    pub poster_files_by_id: PathMap,
}

impl PublishedLibrary {
    pub fn empty() -> Self {
        Self {
            catalog: LibraryCatalog {
                root_name: "No folder selected".to_owned(),
                indexed_at_epoch_ms: 0,
                items: Vec::new(),
            },
            files_by_id: PathMap::new(),
            subtitle_files_by_id: PathMap::new(),
            poster_files_by_id: PathMap::new(),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LibraryCatalog {
    pub root_name: String,
    pub indexed_at_epoch_ms: u64,
    pub items: Vec<LibraryMediaItem>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LibraryMediaItem {
    pub id: String,
    pub series_title: String,
    pub episode_title: String,
    pub relative_path: String,
    pub size_bytes: u64,
    pub media_type: String,
    pub stream_path: String,
    #[serde(default, skip_serializing_if = "is_zero")]
    pub indexed_at_epoch_ms: u64,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub subtitles: Vec<LibrarySubtitleTrack>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub poster_path: Option<String>,
    /// Absolute path of the library root this item was scanned from
    /// (e.g. `M:\Anime`), so clients can offer per-root browsing when the
    /// server publishes several roots merged into one catalog.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub root_label: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub anime_metadata: Option<LibraryAnimeMetadata>,
    #[serde(
        default,
        skip_serializing_if = "LibraryItemMetadataStatus::is_not_available"
    )]
    pub metadata_status: LibraryItemMetadataStatus,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum LibraryItemMetadataStatus {
    #[default]
    NotAvailable,
    Loading,
    Ready,
    Failed,
}

impl LibraryItemMetadataStatus {
    fn is_not_available(&self) -> bool {
        matches!(self, Self::NotAvailable)
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LibraryAnimeMetadata {
    pub anime_id: ExternalAnimeId,
    pub display_title: String,
    pub primary_title: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub chinese_title: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub english_title: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub japanese_title: Option<String>,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub alternate_names: Vec<String>,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub external_links: Vec<ExternalAnimeExternalLink>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub image_url: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub episode_count: Option<u32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub start_year: Option<u32>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LibrarySubtitleTrack {
    pub id: String,
    pub label: String,
    pub relative_path: String,
    pub media_type: String,
    pub stream_path: String,
}

#[derive(Debug, Clone, PartialEq, Eq, PartialOrd, Ord, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ExternalAnimeId {
    pub provider: ExternalAnimeProvider,
    pub value: u64,
}

impl ExternalAnimeId {
    fn web_url(&self) -> String {
        match self.provider {
            ExternalAnimeProvider::MyAnimeList => {
                format!("https://myanimelist.net/anime/{}", self.value)
            }
            ExternalAnimeProvider::Bangumi => format!("https://bangumi.tv/subject/{}", self.value),
            ExternalAnimeProvider::Dandanplay => {
                format!("https://www.dandanplay.com/bangumi/{}", self.value)
            }
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum ExternalAnimeProvider {
    MyAnimeList,
    Bangumi,
    Dandanplay,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ExternalAnimeExternalLink {
    pub anime_id: ExternalAnimeId,
    pub url: String,
}

impl Serialize for ExternalAnimeExternalLink {
    fn serialize<S>(&self, serializer: S) -> std::result::Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        let include_url = self.url != self.anime_id.web_url();
        let mut map = serializer.serialize_map(Some(if include_url { 2 } else { 1 }))?;
        map.serialize_entry("animeId", &self.anime_id)?;
        if include_url {
            map.serialize_entry("url", &self.url)?;
        }
        map.end()
    }
}

impl<'de> Deserialize<'de> for ExternalAnimeExternalLink {
    fn deserialize<D>(deserializer: D) -> std::result::Result<Self, D::Error>
    where
        D: Deserializer<'de>,
    {
        #[derive(Deserialize)]
        #[serde(rename_all = "camelCase")]
        struct Wire {
            anime_id: ExternalAnimeId,
            url: Option<String>,
        }

        let wire = Wire::deserialize(deserializer)?;
        let url = wire.url.unwrap_or_else(|| wire.anime_id.web_url());
        Ok(Self {
            anime_id: wire.anime_id,
            url,
        })
    }
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct StoredLibrarySnapshot<'a> {
    schema_version: u32,
    saved_at_epoch_ms: u64,
    catalog: &'a LibraryCatalog,
    files_by_id: BTreeMap<String, String>,
    subtitle_files_by_id: BTreeMap<String, String>,
    poster_files_by_id: BTreeMap<String, String>,
    #[serde(skip_serializing_if = "BTreeMap::is_empty")]
    file_last_modified_epoch_ms_by_id: &'a BTreeMap<String, u64>,
}

impl<'a> TryFrom<&'a HeadlessStoredLibrary> for StoredLibrarySnapshot<'a> {
    type Error = LibraryServerError;

    fn try_from(stored: &'a HeadlessStoredLibrary) -> Result<Self> {
        Ok(Self {
            schema_version: SCHEMA_VERSION,
            saved_at_epoch_ms: stored.saved_at_epoch_ms,
            catalog: &stored.published_library.catalog,
            files_by_id: absolute_path_map(&stored.published_library.files_by_id)?,
            subtitle_files_by_id: absolute_path_map(
                &stored.published_library.subtitle_files_by_id,
            )?,
            poster_files_by_id: absolute_path_map(&stored.published_library.poster_files_by_id)?,
            file_last_modified_epoch_ms_by_id: &stored.file_last_modified_epoch_ms_by_id,
        })
    }
}

fn stored_library_from_value(value: &Value) -> Option<HeadlessStoredLibrary> {
    let root = value.as_object()?;
    let schema_version = root.get("schemaVersion")?.as_i64()?;
    if schema_version != SCHEMA_VERSION as i64 {
        return None;
    }
    let catalog = serde_json::from_value::<LibraryCatalog>(root.get("catalog")?.clone()).ok()?;
    let files_by_id = path_map_from_value(root.get("filesById"))?;
    let subtitle_files_by_id = path_map_from_value(root.get("subtitleFilesById"))?;
    let poster_files_by_id = path_map_from_value(root.get("posterFilesById"))?;
    let saved_at_epoch_ms = root
        .get("savedAtEpochMs")
        .and_then(Value::as_u64)
        .unwrap_or(catalog.indexed_at_epoch_ms);

    Some(HeadlessStoredLibrary {
        published_library: PublishedLibrary {
            catalog,
            files_by_id,
            subtitle_files_by_id,
            poster_files_by_id,
        },
        saved_at_epoch_ms,
        file_last_modified_epoch_ms_by_id: u64_map_from_value(
            root.get("fileLastModifiedEpochMsById"),
        )?,
    })
}

fn path_map_from_value(value: Option<&Value>) -> Option<PathMap> {
    match value {
        None => Some(PathMap::new()),
        Some(Value::Object(object)) => object
            .iter()
            .map(|(id, path)| Some((id.clone(), PathBuf::from(path.as_str()?))))
            .collect(),
        Some(_) => None,
    }
}

fn u64_map_from_value(value: Option<&Value>) -> Option<BTreeMap<String, u64>> {
    match value {
        None => Some(BTreeMap::new()),
        Some(Value::Object(object)) => object
            .iter()
            .map(|(id, value)| Some((id.clone(), value.as_u64()?)))
            .collect(),
        Some(_) => None,
    }
}

fn absolute_path_map(paths: &PathMap) -> Result<BTreeMap<String, String>> {
    paths
        .iter()
        .map(|(id, path)| {
            Ok((
                id.clone(),
                absolute_normalized_path(path)?
                    .to_string_lossy()
                    .into_owned(),
            ))
        })
        .collect()
}

pub(crate) fn absolute_normalized_path(path: &Path) -> Result<PathBuf> {
    let absolute = if path.is_absolute() {
        path.to_path_buf()
    } else {
        std::env::current_dir()
            .map_err(|error| {
                LibraryServerError::with_context(error, "failed to resolve current directory")
            })?
            .join(path)
    };
    Ok(normalize_lexically(&absolute))
}

pub(crate) fn normalize_lexically(path: &Path) -> PathBuf {
    let mut normalized = PathBuf::new();
    for component in path.components() {
        match component {
            Component::CurDir => {}
            Component::ParentDir => {
                if !normalized.pop() {
                    normalized.push(component.as_os_str());
                }
            }
            Component::Prefix(_) | Component::RootDir | Component::Normal(_) => {
                normalized.push(component.as_os_str());
            }
        }
    }
    normalized
}

pub(crate) fn current_epoch_ms() -> u64 {
    let millis = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis();
    millis.min(u64::MAX as u128) as u64
}

fn is_zero(value: &u64) -> bool {
    *value == 0
}

#[cfg(test)]
mod tests {
    use std::fs;
    use std::sync::atomic::{AtomicU64, Ordering};

    use serde_json::{Value, json};

    use super::*;

    static TEMP_COUNTER: AtomicU64 = AtomicU64::new(0);

    #[test]
    fn loads_and_persists_catalog_snapshot_from_golden_catalog_body() {
        let temp = temp_dir("danmaku-catalog-roundtrip");
        let catalog_file = temp.join("catalog.json");
        let media_path = temp.join("media").join("Episode 01.bin");
        let subtitle_path = temp.join("media").join("Episode 01.en.srt");
        let poster_path = temp.join("posters").join("episode-id.jpg");
        let golden_catalog = golden_catalog_json();
        let snapshot = json!({
            "schemaVersion": 1,
            "savedAtEpochMs": 1700000001234_u64,
            "catalog": golden_catalog,
            "filesById": {
                "episode-id": media_path.to_string_lossy()
            },
            "subtitleFilesById": {
                "subtitle-id": subtitle_path.to_string_lossy()
            },
            "posterFilesById": {
                "episode-id": poster_path.to_string_lossy()
            }
        });
        fs::write(
            &catalog_file,
            serde_json::to_string_pretty(&snapshot).expect("snapshot should encode"),
        )
        .expect("snapshot should write");

        let store = CatalogStore::new(&catalog_file);
        let loaded = store
            .load()
            .expect("catalog load should succeed")
            .expect("catalog should be present");

        assert_eq!(1700000001234, loaded.saved_at_epoch_ms);
        assert_eq!(
            "Fixture Library",
            loaded.published_library.catalog.root_name
        );
        assert_eq!(1, loaded.published_library.catalog.items.len());
        assert_eq!(
            media_path,
            loaded.published_library.files_by_id["episode-id"]
        );

        store
            .save_stored(&loaded)
            .expect("catalog should save again");
        let saved = serde_json::from_str::<Value>(
            &fs::read_to_string(&catalog_file).expect("catalog should read"),
        )
        .expect("saved catalog should parse");
        assert_eq!(golden_catalog_json(), saved["catalog"]);

        let reloaded = store
            .load()
            .expect("catalog reload should succeed")
            .expect("catalog should still be present");
        assert_eq!(loaded, reloaded);

        fs::remove_dir_all(temp).expect("temp dir should delete");
    }

    #[test]
    fn save_persists_json_equivalent_snapshot_with_absolute_paths() {
        let temp = temp_dir("danmaku-catalog-save");
        let catalog_file = temp.join("catalog.json");
        let media_path = temp.join("Episode 01.bin");
        let store = CatalogStore::new(&catalog_file);
        let catalog = serde_json::from_value::<LibraryCatalog>(golden_catalog_json())
            .expect("golden catalog should decode");
        let mut files_by_id = PathMap::new();
        files_by_id.insert("episode-id".to_owned(), media_path.clone());

        let stored = store
            .save(PublishedLibrary {
                catalog,
                files_by_id,
                subtitle_files_by_id: PathMap::new(),
                poster_files_by_id: PathMap::new(),
            })
            .expect("catalog should save");

        let saved = serde_json::from_str::<Value>(
            &fs::read_to_string(&catalog_file).expect("catalog should read"),
        )
        .expect("saved catalog should parse");
        assert_eq!(1, saved["schemaVersion"]);
        assert_eq!(golden_catalog_json(), saved["catalog"]);
        assert_eq!(
            absolute_normalized_path(&media_path)
                .expect("path should normalize")
                .to_string_lossy(),
            saved["filesById"]["episode-id"]
                .as_str()
                .expect("path string")
        );
        assert!(stored.saved_at_epoch_ms > 0);

        fs::remove_dir_all(temp).expect("temp dir should delete");
    }

    fn golden_catalog_json() -> Value {
        let fixture = serde_json::from_str::<Value>(include_str!(
            "../../../shared/library-server-core/src/jvmTest/resources/lan-protocol-fixtures/catalog.json"
        ))
        .expect("fixture should parse");
        fixture["response"]["body"]["json"].clone()
    }

    fn temp_dir(prefix: &str) -> PathBuf {
        let id = TEMP_COUNTER.fetch_add(1, Ordering::Relaxed);
        let path = std::env::temp_dir().join(format!("{prefix}-{}-{id}", std::process::id()));
        let _ = fs::remove_dir_all(&path);
        fs::create_dir_all(&path).expect("temp dir should create");
        path
    }
}
