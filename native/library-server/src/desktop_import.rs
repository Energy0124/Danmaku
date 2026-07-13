use std::collections::{BTreeMap, BTreeSet};
use std::path::{Path, PathBuf};

use rusqlite::{Connection, OpenFlags, Row};
use serde::Deserialize;

use crate::catalog::{
    CatalogStore, ExternalAnimeExternalLink, ExternalAnimeId, LibraryAnimeMetadata, LibraryCatalog,
    LibraryItemMetadataStatus, LibraryMediaItem, LibrarySubtitleTrack, PathMap, PublishedLibrary,
    absolute_normalized_path, normalize_lexically,
};
use crate::domain::PlaybackProgress;
use crate::hash::sha256_hex;
use crate::lock::DataDirectoryLock;
use crate::progress::PlaybackProgressStore;
use crate::{LibraryServerError, Result};

const SUPPORTED_SQLDELIGHT_USER_VERSIONS: &[i64] = &[0, 1];
const FAVORITE_MEDIA_IDS_SETTING_KEY: &str = "library.favorite_media_ids";

/// Imports the current desktop SQLDelight catalog schema as a one-time
/// migration source. This supports the schema in
/// `apps/desktop-windows/src/commonMain/sqldelight/.../LibraryCatalog.sq`
/// and the additive startup migrations in `DesktopLibraryCatalogStore.kt`
/// as of 2026-07-08. Detection is intentionally structural: `PRAGMA
/// user_version` must be unset or 1, and the expected current tables/columns
/// must exist. Older desktop databases should first be opened once by the
/// desktop app so SQLDelight applies its migrations.
pub fn import_desktop_catalog(
    data_directory: impl AsRef<Path>,
    database_path: impl AsRef<Path>,
) -> Result<DesktopCatalogImportReport> {
    import_desktop_catalog_with_options(
        data_directory.as_ref(),
        database_path.as_ref(),
        default_poster_cache_directory(),
    )
}

fn import_desktop_catalog_with_options(
    data_directory: &Path,
    database_path: &Path,
    poster_cache_directory: PathBuf,
) -> Result<DesktopCatalogImportReport> {
    let _lock = DataDirectoryLock::acquire(data_directory)?;
    let connection = open_immutable_read_only(database_path)?;
    validate_supported_schema(&connection)?;

    let mut report = DesktopCatalogImportReport::new(database_path);
    report.roots = load_roots(&connection)?;

    let metadata_by_id = load_metadata_by_id(&connection, &mut report)?;
    let dandanplay_metadata_id_by_media_id =
        load_dandanplay_metadata_ids_by_media_id(&connection, &mut report)?;

    let roots = report.roots.clone();
    let imported = match load_registered_catalog(
        &connection,
        &roots,
        &metadata_by_id,
        &dandanplay_metadata_id_by_media_id,
        &poster_cache_directory,
        &mut report,
    )? {
        Some(imported) => imported,
        None => load_legacy_catalog(
            &connection,
            &metadata_by_id,
            &dandanplay_metadata_id_by_media_id,
            &poster_cache_directory,
            &mut report,
        )?
        .unwrap_or_else(ImportedLibrary::empty_registered),
    };

    report.items = imported.published_library.catalog.items.len();
    report.subtitles = imported
        .published_library
        .catalog
        .items
        .iter()
        .map(|item| item.subtitles.len())
        .sum();
    report.posters = imported.published_library.poster_files_by_id.len();
    report.metadata_ready = imported
        .published_library
        .catalog
        .items
        .iter()
        .filter(|item| item.anime_metadata.is_some())
        .count();

    let catalog_store = CatalogStore::new(data_directory.join("catalog.json"));
    catalog_store.save_stored(&imported.into_stored())?;

    let progress_rows = import_progress(&connection, data_directory, &mut report)?;
    report.progress_rows = progress_rows;
    report.record_deliberate_skips(&connection)?;

    Ok(report)
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DesktopCatalogImportReport {
    database_path: PathBuf,
    pub roots: Vec<DesktopRoot>,
    pub items: usize,
    pub subtitles: usize,
    pub posters: usize,
    pub metadata_ready: usize,
    pub progress_rows: ProgressImportCounts,
    pub skipped_rows: Vec<SkippedRow>,
    pub deliberate_skips: Vec<DeliberateSkip>,
}

impl DesktopCatalogImportReport {
    fn new(database_path: &Path) -> Self {
        Self {
            database_path: database_path.to_path_buf(),
            roots: Vec::new(),
            items: 0,
            subtitles: 0,
            posters: 0,
            metadata_ready: 0,
            progress_rows: ProgressImportCounts::default(),
            skipped_rows: Vec::new(),
            deliberate_skips: Vec::new(),
        }
    }

    pub fn to_log_lines(&self) -> Vec<String> {
        let mut lines = vec![
            format!(
                "Desktop catalog import: completed from {}",
                self.database_path.display()
            ),
            format!(
                "Imported: roots={}; items={}; subtitles={}; posters={}; metadataReady={}",
                self.roots.len(),
                self.items,
                self.subtitles,
                self.posters,
                self.metadata_ready
            ),
            format!(
                "Progress: read={}; inserted={}; updated={}; keptExisting={}; skipped={}",
                self.progress_rows.read,
                self.progress_rows.inserted,
                self.progress_rows.updated,
                self.progress_rows.kept_existing,
                self.progress_rows.skipped
            ),
            format!("Skipped unsupported rows: {}", self.skipped_rows.len()),
        ];
        for skipped in &self.skipped_rows {
            lines.push(format!(
                "  - {} {}: {}",
                skipped.table, skipped.key, skipped.reason
            ));
        }
        lines.push("Deliberately skipped tables without LAN protocol surface:".to_owned());
        for skipped in &self.deliberate_skips {
            lines.push(format!(
                "  - {}: {} rows ({})",
                skipped.table, skipped.rows, skipped.reason
            ));
        }
        lines
    }

    fn skip_row(
        &mut self,
        table: impl Into<String>,
        key: impl Into<String>,
        reason: impl Into<String>,
    ) {
        self.skipped_rows.push(SkippedRow {
            table: table.into(),
            key: key.into(),
            reason: reason.into(),
        });
    }

    fn record_deliberate_skips(&mut self, connection: &Connection) -> Result<()> {
        let favorites = favorite_media_id_count(connection)?;
        self.deliberate_skips.push(DeliberateSkip {
            table: "app_setting".to_owned(),
            rows: favorites,
            reason: "favorite media ids are desktop-local; LAN catalog has no favorites field"
                .to_owned(),
        });
        for (table, reason) in [
            (
                "local_anime_list_entry",
                "local watch-list entries have no LAN protocol surface",
            ),
            (
                "external_anime_mapping",
                "series provider mappings are for desktop tracking/admin workflows",
            ),
            (
                "external_anime_list_entry",
                "provider list cache is not exposed by /api/library",
            ),
            (
                "external_anime_sync_failure",
                "provider sync failures are desktop admin state",
            ),
            (
                "dandanplay_comment_cache",
                "provider comment cache is managed by the Rust server resolver separately",
            ),
            (
                "download_queue_item",
                "desktop download queue is not part of the LAN catalog contract",
            ),
            (
                "library_quality_issue_decision",
                "quality-review decisions are a desktop admin workflow",
            ),
        ] {
            self.deliberate_skips.push(DeliberateSkip {
                table: table.to_owned(),
                rows: count_rows(connection, table)?,
                reason: reason.to_owned(),
            });
        }
        Ok(())
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DesktopRoot {
    pub id: String,
    pub path: PathBuf,
    pub display_name: String,
    pub state: String,
    pub added_at_epoch_ms: u64,
    pub last_scanned_at_epoch_ms: Option<u64>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SkippedRow {
    pub table: String,
    pub key: String,
    pub reason: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DeliberateSkip {
    pub table: String,
    pub rows: usize,
    pub reason: String,
}

#[derive(Debug, Default, Clone, Copy, PartialEq, Eq)]
pub struct ProgressImportCounts {
    pub read: usize,
    pub inserted: usize,
    pub updated: usize,
    pub kept_existing: usize,
    pub skipped: usize,
}

struct ImportedLibrary {
    published_library: PublishedLibrary,
    file_last_modified_epoch_ms_by_id: BTreeMap<String, u64>,
}

impl ImportedLibrary {
    fn empty_registered() -> Self {
        Self {
            published_library: PublishedLibrary {
                catalog: LibraryCatalog {
                    root_name: "Registered Libraries".to_owned(),
                    indexed_at_epoch_ms: 0,
                    items: Vec::new(),
                },
                files_by_id: PathMap::new(),
                subtitle_files_by_id: PathMap::new(),
                poster_files_by_id: PathMap::new(),
            },
            file_last_modified_epoch_ms_by_id: BTreeMap::new(),
        }
    }

    fn into_stored(self) -> crate::catalog::HeadlessStoredLibrary {
        let saved_at_epoch_ms = self.published_library.catalog.indexed_at_epoch_ms;
        crate::catalog::HeadlessStoredLibrary {
            saved_at_epoch_ms,
            published_library: self.published_library,
            file_last_modified_epoch_ms_by_id: self.file_last_modified_epoch_ms_by_id,
        }
    }
}

#[derive(Debug, Clone)]
struct DesktopItemRow {
    root_path: PathBuf,
    published_relative_prefix: Option<String>,
    relative_path: String,
    id: String,
    series_title: String,
    episode_title: String,
    size_bytes: u64,
    last_modified_epoch_ms: u64,
    indexed_at_epoch_ms: u64,
    media_type: String,
    stream_path: String,
    subtitles_json: String,
}

#[derive(Debug, Clone, PartialEq, Eq, PartialOrd, Ord)]
struct MetadataKey {
    provider: crate::catalog::ExternalAnimeProvider,
    value: u64,
}

#[derive(Debug, Clone)]
struct CachedMetadata {
    anime_id: ExternalAnimeId,
    display_title: String,
    primary_title: String,
    chinese_title: Option<String>,
    english_title: Option<String>,
    japanese_title: Option<String>,
    alternate_names: Vec<String>,
    image_url: Option<String>,
    episode_count: Option<u32>,
    start_year: Option<u32>,
}

impl CachedMetadata {
    fn to_library_metadata(&self) -> LibraryAnimeMetadata {
        LibraryAnimeMetadata {
            anime_id: self.anime_id.clone(),
            display_title: self.display_title.clone(),
            primary_title: self.primary_title.clone(),
            chinese_title: self.chinese_title.clone(),
            english_title: self.english_title.clone(),
            japanese_title: self.japanese_title.clone(),
            alternate_names: self.alternate_names.clone(),
            external_links: Vec::<ExternalAnimeExternalLink>::new(),
            image_url: self.image_url.clone(),
            episode_count: self.episode_count,
            start_year: self.start_year,
        }
    }
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DesktopTitleSet {
    primary: String,
    #[serde(default)]
    chinese: Option<String>,
    #[serde(default)]
    english: Option<String>,
    #[serde(default)]
    japanese: Option<String>,
    #[serde(default)]
    alternate_names: Vec<String>,
}

fn open_immutable_read_only(path: &Path) -> Result<Connection> {
    if !path.is_file() {
        return Err(LibraryServerError::new(format!(
            "desktop catalog database copy does not exist: {}",
            path.display()
        )));
    }
    let uri = sqlite_file_uri(path)?;
    Connection::open_with_flags(
        &uri,
        OpenFlags::SQLITE_OPEN_READ_ONLY
            | OpenFlags::SQLITE_OPEN_URI
            | OpenFlags::SQLITE_OPEN_NO_MUTEX,
    )
    .map_err(|error| {
        LibraryServerError::with_context(
            error,
            format!(
                "failed to open desktop catalog read-only {}",
                path.display()
            ),
        )
    })
}

fn sqlite_file_uri(path: &Path) -> Result<String> {
    let absolute = absolute_normalized_path(path)?;
    let mut value = absolute.to_string_lossy().replace('\\', "/");
    if cfg!(windows) && value.as_bytes().get(1) == Some(&b':') {
        value = format!("/{value}");
    }
    Ok(format!(
        "file:{}?mode=ro&immutable=1",
        percent_encode_uri_path(&value)
    ))
}

fn percent_encode_uri_path(value: &str) -> String {
    let mut encoded = String::with_capacity(value.len());
    for byte in value.bytes() {
        let char = byte as char;
        if char.is_ascii_alphanumeric() || matches!(char, '/' | ':' | '_' | '-' | '.') {
            encoded.push(char);
        } else {
            encoded.push_str(&format!("%{byte:02X}"));
        }
    }
    encoded
}

fn validate_supported_schema(connection: &Connection) -> Result<()> {
    let user_version = pragma_user_version(connection)?;
    if !SUPPORTED_SQLDELIGHT_USER_VERSIONS.contains(&user_version) {
        return Err(LibraryServerError::new(format!(
            "unsupported desktop catalog schema version {user_version}; supported SQLDelight user_version values are 0 and 1"
        )));
    }

    for (table, columns) in required_schema() {
        require_columns(connection, table, columns)?;
    }
    Ok(())
}

fn pragma_user_version(connection: &Connection) -> Result<i64> {
    connection
        .query_row("PRAGMA user_version", [], |row| row.get(0))
        .map_err(|error| {
            LibraryServerError::with_context(error, "failed to read PRAGMA user_version")
        })
}

fn required_schema() -> &'static [(&'static str, &'static [&'static str])] {
    &[
        (
            "library_metadata",
            &[
                "singleton_id",
                "root_path",
                "root_name",
                "indexed_at_epoch_ms",
            ],
        ),
        (
            "local_media_item",
            &[
                "relative_path",
                "id",
                "series_title",
                "episode_title",
                "size_bytes",
                "last_modified_epoch_ms",
                "indexed_at_epoch_ms",
                "media_type",
                "stream_path",
                "subtitles_json",
            ],
        ),
        (
            "playback_progress",
            &[
                "media_id",
                "position_ms",
                "duration_ms",
                "updated_at_epoch_ms",
            ],
        ),
        (
            "app_setting",
            &["setting_key", "setting_value", "updated_at_epoch_ms"],
        ),
        (
            "library_root",
            &[
                "root_id",
                "path",
                "display_name",
                "provenance",
                "state",
                "added_at_epoch_ms",
                "last_scanned_at_epoch_ms",
                "last_error",
            ],
        ),
        (
            "library_root_media_item",
            &[
                "root_id",
                "relative_path",
                "id",
                "series_title",
                "episode_title",
                "size_bytes",
                "last_modified_epoch_ms",
                "indexed_at_epoch_ms",
                "media_type",
                "stream_path",
                "subtitles_json",
            ],
        ),
        ("download_queue_item", &["id"]),
        ("dandanplay_comment_cache", &["media_id", "anime_id"]),
        (
            "external_anime_metadata_cache",
            &[
                "provider",
                "anime_id",
                "titles_json",
                "episode_count",
                "start_year",
                "image_url",
            ],
        ),
        (
            "external_anime_mapping",
            &["local_series_id", "provider", "anime_id"],
        ),
        (
            "external_anime_item_mapping",
            &["local_media_id", "provider", "anime_id"],
        ),
        ("external_anime_list_entry", &["provider", "anime_id"]),
        ("external_anime_sync_failure", &["provider", "anime_id"]),
        ("local_anime_list_entry", &["local_series_id"]),
        ("library_quality_issue_decision", &["issue_key"]),
    ]
}

fn require_columns(connection: &Connection, table: &str, required_columns: &[&str]) -> Result<()> {
    let columns = table_columns(connection, table)?;
    if columns.is_empty() {
        return Err(LibraryServerError::new(format!(
            "unsupported desktop catalog schema: missing table {table}"
        )));
    }
    for column in required_columns {
        if !columns.contains(*column) {
            return Err(LibraryServerError::new(format!(
                "unsupported desktop catalog schema: missing column {table}.{column}"
            )));
        }
    }
    Ok(())
}

fn table_columns(connection: &Connection, table: &str) -> Result<BTreeSet<String>> {
    let mut statement = connection
        .prepare(&format!("PRAGMA table_info({table})"))
        .map_err(|error| {
            LibraryServerError::with_context(error, format!("failed to inspect table {table}"))
        })?;
    let rows = statement
        .query_map([], |row| row.get::<_, String>(1))
        .map_err(|error| {
            LibraryServerError::with_context(error, format!("failed to inspect table {table}"))
        })?;
    let mut columns = BTreeSet::new();
    for row in rows {
        columns.insert(row.map_err(|error| {
            LibraryServerError::with_context(error, format!("failed to read table {table} column"))
        })?);
    }
    Ok(columns)
}

fn load_roots(connection: &Connection) -> Result<Vec<DesktopRoot>> {
    let mut statement = connection
        .prepare(
            "SELECT root_id, path, display_name, state, added_at_epoch_ms, last_scanned_at_epoch_ms
             FROM library_root
             ORDER BY added_at_epoch_ms, display_name, root_id",
        )
        .map_err(|error| LibraryServerError::with_context(error, "failed to prepare root query"))?;
    let rows = statement
        .query_map([], |row| {
            Ok(DesktopRoot {
                id: row.get(0)?,
                path: normalize_lexically(&PathBuf::from(row.get::<_, String>(1)?)),
                display_name: row.get(2)?,
                state: row.get(3)?,
                added_at_epoch_ms: i64_to_u64(row.get(4)?),
                last_scanned_at_epoch_ms: row.get::<_, Option<i64>>(5)?.map(i64_to_u64),
            })
        })
        .map_err(|error| LibraryServerError::with_context(error, "failed to load roots"))?;
    collect_rows(rows, "library_root")
}

fn load_registered_catalog(
    connection: &Connection,
    roots: &[DesktopRoot],
    metadata_by_id: &BTreeMap<MetadataKey, CachedMetadata>,
    dandanplay_metadata_id_by_media_id: &BTreeMap<String, MetadataKey>,
    poster_cache_directory: &Path,
    report: &mut DesktopCatalogImportReport,
) -> Result<Option<ImportedLibrary>> {
    if count_rows(connection, "library_root_media_item")? == 0 && roots.is_empty() {
        return Ok(None);
    }

    let mut imported = ImportedLibrary::empty_registered();
    imported.published_library.catalog.indexed_at_epoch_ms = roots
        .iter()
        .map(|root| {
            root.last_scanned_at_epoch_ms
                .unwrap_or(root.added_at_epoch_ms)
        })
        .max()
        .unwrap_or(0);

    for root in roots {
        let rows = load_root_item_rows(connection, root)?;
        for row in rows {
            import_item_row(
                row,
                metadata_by_id,
                dandanplay_metadata_id_by_media_id,
                poster_cache_directory,
                &mut imported,
                report,
                "library_root_media_item",
            );
        }
    }
    imported
        .published_library
        .catalog
        .items
        .sort_by(|left, right| {
            left.series_title
                .cmp(&right.series_title)
                .then_with(|| left.relative_path.cmp(&right.relative_path))
        });
    Ok(Some(imported))
}

fn load_root_item_rows(connection: &Connection, root: &DesktopRoot) -> Result<Vec<DesktopItemRow>> {
    let mut statement = connection
        .prepare(
            "SELECT relative_path, id, series_title, episode_title, size_bytes,
                    last_modified_epoch_ms, indexed_at_epoch_ms, media_type, stream_path,
                    subtitles_json
             FROM library_root_media_item
             WHERE root_id = ?
             ORDER BY series_title, relative_path",
        )
        .map_err(|error| {
            LibraryServerError::with_context(error, "failed to prepare root item query")
        })?;
    let rows = statement
        .query_map([&root.id], |row| {
            Ok(DesktopItemRow {
                root_path: root.path.clone(),
                published_relative_prefix: Some(root.display_name.clone()),
                relative_path: row.get(0)?,
                id: row.get(1)?,
                series_title: row.get(2)?,
                episode_title: row.get(3)?,
                size_bytes: i64_to_u64(row.get(4)?),
                last_modified_epoch_ms: i64_to_u64(row.get(5)?),
                indexed_at_epoch_ms: i64_to_u64(row.get(6)?),
                media_type: row.get(7)?,
                stream_path: row.get(8)?,
                subtitles_json: row.get(9)?,
            })
        })
        .map_err(|error| LibraryServerError::with_context(error, "failed to load root items"))?;
    collect_rows(rows, "library_root_media_item")
}

fn load_legacy_catalog(
    connection: &Connection,
    metadata_by_id: &BTreeMap<MetadataKey, CachedMetadata>,
    dandanplay_metadata_id_by_media_id: &BTreeMap<String, MetadataKey>,
    poster_cache_directory: &Path,
    report: &mut DesktopCatalogImportReport,
) -> Result<Option<ImportedLibrary>> {
    let Some(metadata) = load_legacy_metadata(connection)? else {
        return Ok(None);
    };
    if count_rows(connection, "local_media_item")? == 0 {
        return Ok(Some(ImportedLibrary {
            published_library: PublishedLibrary {
                catalog: LibraryCatalog {
                    root_name: metadata.root_name,
                    indexed_at_epoch_ms: metadata.indexed_at_epoch_ms,
                    items: Vec::new(),
                },
                files_by_id: PathMap::new(),
                subtitle_files_by_id: PathMap::new(),
                poster_files_by_id: PathMap::new(),
            },
            file_last_modified_epoch_ms_by_id: BTreeMap::new(),
        }));
    }

    let mut imported = ImportedLibrary {
        published_library: PublishedLibrary {
            catalog: LibraryCatalog {
                root_name: metadata.root_name,
                indexed_at_epoch_ms: metadata.indexed_at_epoch_ms,
                items: Vec::new(),
            },
            files_by_id: PathMap::new(),
            subtitle_files_by_id: PathMap::new(),
            poster_files_by_id: PathMap::new(),
        },
        file_last_modified_epoch_ms_by_id: BTreeMap::new(),
    };
    let rows = load_legacy_item_rows(connection, &metadata.root_path)?;
    for row in rows {
        import_item_row(
            row,
            metadata_by_id,
            dandanplay_metadata_id_by_media_id,
            poster_cache_directory,
            &mut imported,
            report,
            "local_media_item",
        );
    }
    Ok(Some(imported))
}

struct LegacyMetadata {
    root_path: PathBuf,
    root_name: String,
    indexed_at_epoch_ms: u64,
}

fn load_legacy_metadata(connection: &Connection) -> Result<Option<LegacyMetadata>> {
    let mut statement = connection
        .prepare(
            "SELECT root_path, root_name, indexed_at_epoch_ms
             FROM library_metadata
             WHERE singleton_id = 1",
        )
        .map_err(|error| {
            LibraryServerError::with_context(error, "failed to prepare legacy metadata query")
        })?;
    match statement.query_row([], |row| {
        Ok(LegacyMetadata {
            root_path: normalize_lexically(&PathBuf::from(row.get::<_, String>(0)?)),
            root_name: row.get(1)?,
            indexed_at_epoch_ms: i64_to_u64(row.get(2)?),
        })
    }) {
        Ok(metadata) => Ok(Some(metadata)),
        Err(rusqlite::Error::QueryReturnedNoRows) => Ok(None),
        Err(error) => Err(LibraryServerError::with_context(
            error,
            "failed to load legacy metadata",
        )),
    }
}

fn load_legacy_item_rows(connection: &Connection, root_path: &Path) -> Result<Vec<DesktopItemRow>> {
    let mut statement = connection
        .prepare(
            "SELECT relative_path, id, series_title, episode_title, size_bytes,
                    last_modified_epoch_ms, indexed_at_epoch_ms, media_type, stream_path,
                    subtitles_json
             FROM local_media_item
             ORDER BY series_title, relative_path",
        )
        .map_err(|error| {
            LibraryServerError::with_context(error, "failed to prepare legacy item query")
        })?;
    let rows = statement
        .query_map([], |row| {
            Ok(DesktopItemRow {
                root_path: root_path.to_path_buf(),
                published_relative_prefix: None,
                relative_path: row.get(0)?,
                id: row.get(1)?,
                series_title: row.get(2)?,
                episode_title: row.get(3)?,
                size_bytes: i64_to_u64(row.get(4)?),
                last_modified_epoch_ms: i64_to_u64(row.get(5)?),
                indexed_at_epoch_ms: i64_to_u64(row.get(6)?),
                media_type: row.get(7)?,
                stream_path: row.get(8)?,
                subtitles_json: row.get(9)?,
            })
        })
        .map_err(|error| LibraryServerError::with_context(error, "failed to load legacy items"))?;
    collect_rows(rows, "local_media_item")
}

fn import_item_row(
    row: DesktopItemRow,
    metadata_by_id: &BTreeMap<MetadataKey, CachedMetadata>,
    dandanplay_metadata_id_by_media_id: &BTreeMap<String, MetadataKey>,
    poster_cache_directory: &Path,
    imported: &mut ImportedLibrary,
    report: &mut DesktopCatalogImportReport,
    table: &str,
) {
    let key = if row.id.trim().is_empty() {
        row.relative_path.clone()
    } else {
        row.id.clone()
    };
    if let Some(reason) = invalid_item_reason(&row) {
        report.skip_row(table, key, reason);
        return;
    }

    let original_relative_path = row.relative_path.replace('\\', "/");
    let published_relative_path = prefixed_relative_path(
        row.published_relative_prefix.as_deref(),
        &original_relative_path,
    );
    let media_path = normalize_lexically(&row.root_path.join(&original_relative_path));
    let mut subtitles = match serde_json::from_str::<Vec<LibrarySubtitleTrack>>(&row.subtitles_json)
    {
        Ok(subtitles) => subtitles,
        Err(error) => {
            report.skip_row(table, key, format!("invalid subtitles_json: {error}"));
            return;
        }
    };
    for subtitle in &mut subtitles {
        let original_subtitle_relative_path = subtitle.relative_path.replace('\\', "/");
        let subtitle_path =
            normalize_lexically(&row.root_path.join(&original_subtitle_relative_path));
        imported
            .published_library
            .subtitle_files_by_id
            .insert(subtitle.id.clone(), subtitle_path);
        subtitle.relative_path = prefixed_relative_path(
            row.published_relative_prefix.as_deref(),
            &original_subtitle_relative_path,
        );
    }

    let metadata = dandanplay_metadata_id_by_media_id
        .get(&row.id)
        .and_then(|key| metadata_by_id.get(key));
    let anime_metadata = metadata.map(CachedMetadata::to_library_metadata);
    let poster_file = metadata
        .and_then(|metadata| metadata.image_url.as_deref())
        .and_then(|url| cached_poster_path(poster_cache_directory, url));
    let poster_path = poster_file.as_ref().map(|_| format!("/posters/{}", row.id));
    if let Some(poster_file) = poster_file {
        imported
            .published_library
            .poster_files_by_id
            .insert(row.id.clone(), poster_file);
    }
    let metadata_status = if anime_metadata.is_some() || poster_path.is_some() {
        LibraryItemMetadataStatus::Ready
    } else {
        LibraryItemMetadataStatus::NotAvailable
    };

    imported
        .published_library
        .files_by_id
        .insert(row.id.clone(), media_path);
    imported
        .file_last_modified_epoch_ms_by_id
        .insert(row.id.clone(), row.last_modified_epoch_ms);
    imported
        .published_library
        .catalog
        .items
        .push(LibraryMediaItem {
            id: row.id,
            series_title: row.series_title,
            episode_title: row.episode_title,
            relative_path: published_relative_path,
            size_bytes: row.size_bytes,
            media_type: row.media_type,
            stream_path: row.stream_path,
            indexed_at_epoch_ms: if row.indexed_at_epoch_ms > 0 {
                row.indexed_at_epoch_ms
            } else {
                row.last_modified_epoch_ms
            },
            subtitles,
            poster_path,
            root_label: Some(row.root_path.to_string_lossy().into_owned()),
            anime_metadata,
            metadata_status,
        });
}

fn invalid_item_reason(row: &DesktopItemRow) -> Option<&'static str> {
    if row.id.trim().is_empty() {
        Some("blank media id")
    } else if row.relative_path.trim().is_empty() {
        Some("blank relative_path")
    } else if row.series_title.trim().is_empty() {
        Some("blank series_title")
    } else if row.episode_title.trim().is_empty() {
        Some("blank episode_title")
    } else if row.media_type.trim().is_empty() {
        Some("blank media_type")
    } else if !row.stream_path.starts_with('/') {
        Some("stream_path is not absolute")
    } else {
        None
    }
}

fn prefixed_relative_path(prefix: Option<&str>, relative_path: &str) -> String {
    match prefix {
        Some(prefix) if !prefix.is_empty() => format!("{prefix}/{relative_path}"),
        _ => relative_path.to_owned(),
    }
}

fn load_metadata_by_id(
    connection: &Connection,
    report: &mut DesktopCatalogImportReport,
) -> Result<BTreeMap<MetadataKey, CachedMetadata>> {
    let mut statement = connection
        .prepare(
            "SELECT provider, anime_id, titles_json, episode_count, start_year, image_url
             FROM external_anime_metadata_cache
             ORDER BY provider, anime_id",
        )
        .map_err(|error| {
            LibraryServerError::with_context(error, "failed to prepare metadata cache query")
        })?;
    let mut rows = statement.query([]).map_err(|error| {
        LibraryServerError::with_context(error, "failed to load metadata cache")
    })?;
    let mut metadata_by_id = BTreeMap::new();
    while let Some(row) = rows
        .next()
        .map_err(|error| LibraryServerError::with_context(error, "failed to read metadata row"))?
    {
        let provider_name: String = row.get(0).map_err(row_error)?;
        let anime_id = i64_to_u64(row.get(1).map_err(row_error)?);
        let key_label = format!("{provider_name}:{anime_id}");
        let Some(provider) = parse_provider(&provider_name) else {
            report.skip_row(
                "external_anime_metadata_cache",
                key_label,
                "unsupported provider",
            );
            continue;
        };
        let titles_json: String = row.get(2).map_err(row_error)?;
        let titles = match serde_json::from_str::<DesktopTitleSet>(&titles_json) {
            Ok(titles) if !titles.primary.trim().is_empty() => titles,
            Ok(_) => {
                report.skip_row(
                    "external_anime_metadata_cache",
                    key_label,
                    "blank primary title",
                );
                continue;
            }
            Err(error) => {
                report.skip_row(
                    "external_anime_metadata_cache",
                    key_label,
                    format!("invalid titles_json: {error}"),
                );
                continue;
            }
        };
        let episode_count = row
            .get::<_, Option<i64>>(3)
            .map_err(row_error)?
            .map(i64_to_u32);
        let start_year = row
            .get::<_, Option<i64>>(4)
            .map_err(row_error)?
            .map(i64_to_u32);
        let image_url = row.get::<_, Option<String>>(5).map_err(row_error)?;
        let display_title = titles
            .chinese
            .clone()
            .filter(|title| !title.trim().is_empty())
            .unwrap_or_else(|| titles.primary.clone());
        let key = MetadataKey {
            provider,
            value: anime_id,
        };
        metadata_by_id.insert(
            key,
            CachedMetadata {
                anime_id: ExternalAnimeId {
                    provider,
                    value: anime_id,
                },
                display_title,
                primary_title: titles.primary,
                chinese_title: titles.chinese,
                english_title: titles.english,
                japanese_title: titles.japanese,
                alternate_names: titles.alternate_names,
                image_url,
                episode_count,
                start_year,
            },
        );
    }
    Ok(metadata_by_id)
}

fn load_dandanplay_metadata_ids_by_media_id(
    connection: &Connection,
    report: &mut DesktopCatalogImportReport,
) -> Result<BTreeMap<String, MetadataKey>> {
    let mut mappings = BTreeMap::new();
    let mut statement = connection
        .prepare(
            "SELECT local_media_id, provider, anime_id
             FROM external_anime_item_mapping
             ORDER BY local_media_id, provider",
        )
        .map_err(|error| {
            LibraryServerError::with_context(error, "failed to prepare item mapping query")
        })?;
    let mut rows = statement
        .query([])
        .map_err(|error| LibraryServerError::with_context(error, "failed to load item mappings"))?;
    while let Some(row) = rows
        .next()
        .map_err(|error| LibraryServerError::with_context(error, "failed to read item mapping"))?
    {
        let media_id: String = row.get(0).map_err(row_error)?;
        let provider_name: String = row.get(1).map_err(row_error)?;
        let anime_id = i64_to_u64(row.get(2).map_err(row_error)?);
        let Some(provider) = parse_provider(&provider_name) else {
            report.skip_row(
                "external_anime_item_mapping",
                media_id,
                "unsupported provider",
            );
            continue;
        };
        if provider == crate::catalog::ExternalAnimeProvider::Dandanplay {
            mappings.insert(
                media_id,
                MetadataKey {
                    provider,
                    value: anime_id,
                },
            );
        }
    }

    let mut statement = connection
        .prepare(
            "SELECT media_id, anime_id
             FROM dandanplay_comment_cache
             WHERE anime_id IS NOT NULL AND anime_id > 0
             ORDER BY media_id",
        )
        .map_err(|error| {
            LibraryServerError::with_context(error, "failed to prepare dandanplay cache query")
        })?;
    let mut rows = statement.query([]).map_err(|error| {
        LibraryServerError::with_context(error, "failed to load dandanplay cache")
    })?;
    while let Some(row) = rows.next().map_err(|error| {
        LibraryServerError::with_context(error, "failed to read dandanplay cache")
    })? {
        let media_id: String = row.get(0).map_err(row_error)?;
        let anime_id = i64_to_u64(row.get(1).map_err(row_error)?);
        mappings.entry(media_id).or_insert_with(|| MetadataKey {
            provider: crate::catalog::ExternalAnimeProvider::Dandanplay,
            value: anime_id,
        });
    }
    Ok(mappings)
}

fn import_progress(
    connection: &Connection,
    data_directory: &Path,
    report: &mut DesktopCatalogImportReport,
) -> Result<ProgressImportCounts> {
    let imported_progress = load_progress(connection, report)?;
    let store = PlaybackProgressStore::new(data_directory.join("progress.json"));
    let mut existing_by_id = store
        .load_all_progress()
        .into_iter()
        .map(|progress| (progress.media_id.clone(), progress))
        .collect::<BTreeMap<_, _>>();
    let mut counts = ProgressImportCounts {
        read: imported_progress.len(),
        ..ProgressImportCounts::default()
    };
    for progress in imported_progress {
        match existing_by_id.get(&progress.media_id) {
            None => {
                store.save_progress(progress.clone())?;
                existing_by_id.insert(progress.media_id.clone(), progress);
                counts.inserted += 1;
            }
            Some(existing) if progress.updated_at_epoch_ms > existing.updated_at_epoch_ms => {
                store.save_progress(progress.clone())?;
                existing_by_id.insert(progress.media_id.clone(), progress);
                counts.updated += 1;
            }
            Some(_) => {
                counts.kept_existing += 1;
            }
        }
    }
    counts.skipped = report
        .skipped_rows
        .iter()
        .filter(|row| row.table == "playback_progress")
        .count();
    Ok(counts)
}

fn load_progress(
    connection: &Connection,
    report: &mut DesktopCatalogImportReport,
) -> Result<Vec<PlaybackProgress>> {
    let mut statement = connection
        .prepare(
            "SELECT media_id, position_ms, duration_ms, updated_at_epoch_ms
             FROM playback_progress
             ORDER BY updated_at_epoch_ms DESC, media_id",
        )
        .map_err(|error| {
            LibraryServerError::with_context(error, "failed to prepare progress query")
        })?;
    let mut rows = statement
        .query([])
        .map_err(|error| LibraryServerError::with_context(error, "failed to load progress"))?;
    let mut progress = Vec::new();
    while let Some(row) = rows
        .next()
        .map_err(|error| LibraryServerError::with_context(error, "failed to read progress row"))?
    {
        let media_id: String = row.get(0).map_err(row_error)?;
        if media_id.trim().is_empty() {
            report.skip_row("playback_progress", "<blank>", "blank media_id");
            continue;
        }
        progress.push(PlaybackProgress {
            media_id,
            position_ms: i64_to_u64(row.get(1).map_err(row_error)?),
            duration_ms: row
                .get::<_, Option<i64>>(2)
                .map_err(row_error)?
                .map(i64_to_u64),
            updated_at_epoch_ms: i64_to_u64(row.get(3).map_err(row_error)?),
        });
    }
    Ok(progress)
}

fn cached_poster_path(cache_directory: &Path, image_url: &str) -> Option<PathBuf> {
    let path = cache_directory.join(format!("{}.img", sha256_hex(image_url)));
    path.is_file().then_some(path)
}

fn default_poster_cache_directory() -> PathBuf {
    std::env::var_os("LOCALAPPDATA")
        .filter(|value| !value.is_empty())
        .map(PathBuf::from)
        .unwrap_or_else(std::env::temp_dir)
        .join("Danmaku")
        .join("poster-cache")
}

fn favorite_media_id_count(connection: &Connection) -> Result<usize> {
    let value = connection
        .query_row(
            "SELECT setting_value FROM app_setting WHERE setting_key = ?",
            [FAVORITE_MEDIA_IDS_SETTING_KEY],
            |row| row.get::<_, String>(0),
        )
        .ok();
    Ok(value
        .as_deref()
        .map(|value| {
            value
                .lines()
                .map(str::trim)
                .filter(|line| !line.is_empty())
                .count()
        })
        .unwrap_or(0))
}

fn count_rows(connection: &Connection, table: &str) -> Result<usize> {
    connection
        .query_row(&format!("SELECT COUNT(*) FROM {table}"), [], |row| {
            row.get::<_, i64>(0)
        })
        .map(|count| i64_to_u64(count) as usize)
        .map_err(|error| {
            LibraryServerError::with_context(error, format!("failed to count table {table}"))
        })
}

fn collect_rows<T, F>(rows: rusqlite::MappedRows<'_, F>, label: &str) -> Result<Vec<T>>
where
    F: FnMut(&Row<'_>) -> rusqlite::Result<T>,
{
    rows.collect::<rusqlite::Result<Vec<_>>>()
        .map_err(|error| LibraryServerError::with_context(error, format!("failed to read {label}")))
}

fn row_error(error: rusqlite::Error) -> LibraryServerError {
    LibraryServerError::with_context(error, "failed to read desktop catalog row")
}

fn parse_provider(name: &str) -> Option<crate::catalog::ExternalAnimeProvider> {
    match name {
        "MY_ANIME_LIST" => Some(crate::catalog::ExternalAnimeProvider::MyAnimeList),
        "BANGUMI" => Some(crate::catalog::ExternalAnimeProvider::Bangumi),
        "DANDANPLAY" => Some(crate::catalog::ExternalAnimeProvider::Dandanplay),
        _ => None,
    }
}

fn i64_to_u64(value: i64) -> u64 {
    u64::try_from(value).unwrap_or_default()
}

fn i64_to_u32(value: i64) -> u32 {
    u32::try_from(value).unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use std::fs;
    use std::sync::atomic::{AtomicU64, Ordering};

    use rusqlite::params;
    use serde_json::json;

    use super::*;
    use crate::catalog::CatalogStore;

    static TEMP_COUNTER: AtomicU64 = AtomicU64::new(0);

    #[test]
    fn imports_full_registered_catalog_with_ids_subtitles_metadata_and_posters() {
        let fixture = Fixture::new("desktop-import-full");
        let db = fixture.database();
        create_schema(&db);
        let poster_cache = fixture.path.join("poster-cache");
        fs::create_dir_all(&poster_cache).expect("poster cache");
        let image_url = "https://img.example/poster.jpg";
        let poster_path = poster_cache.join(format!("{}.img", sha256_hex(image_url)));
        fs::write(&poster_path, [1, 2, 3]).expect("poster");

        db.execute(
            "INSERT INTO library_root(root_id, path, display_name, provenance, state, added_at_epoch_ms, last_scanned_at_epoch_ms, last_error)
             VALUES('root-a', ?, 'Anime Root', 'USER_SELECTED', 'MISSING', 1000, 2000, NULL)",
            [fixture.root.to_string_lossy().as_ref()],
        )
        .expect("root");
        db.execute(
            "INSERT INTO library_root_media_item(root_id, relative_path, id, series_title, episode_title, size_bytes, last_modified_epoch_ms, indexed_at_epoch_ms, media_type, stream_path, subtitles_json)
             VALUES('root-a', 'Show/Episode 01.mkv', 'desktop-id-1', 'Show', 'Episode 01', 123, 1500, 1600, 'video/x-matroska', '/media/desktop-id-1', ?)",
            [json!([{
                "id": "subtitle-id-1",
                "label": "English",
                "relativePath": "Show/Episode 01.en.srt",
                "mediaType": "application/x-subrip",
                "streamPath": "/subtitles/subtitle-id-1"
            }]).to_string()],
        )
        .expect("item");
        insert_metadata(&db, image_url);
        db.execute(
            "INSERT INTO external_anime_item_mapping(local_media_id, provider, anime_id, source, confidence, mapped_at_epoch_ms)
             VALUES('desktop-id-1', 'DANDANPLAY', 777, 'AUTO', 1.0, 1700)",
            [],
        )
        .expect("item mapping");
        db.execute(
            "INSERT INTO playback_progress(media_id, position_ms, duration_ms, updated_at_epoch_ms)
             VALUES('desktop-id-1', 12000, 24000, 1800)",
            [],
        )
        .expect("progress");
        db.execute(
            "INSERT INTO app_setting(setting_key, setting_value, updated_at_epoch_ms)
             VALUES('library.favorite_media_ids', 'desktop-id-1\nother-id', 1900)",
            [],
        )
        .expect("favorite");
        drop(db);

        let report = import_desktop_catalog_with_options(&fixture.data, &fixture.db, poster_cache)
            .expect("import should succeed");

        assert_eq!(1, report.roots.len());
        assert_eq!(1, report.items);
        assert_eq!(1, report.subtitles);
        assert_eq!(1, report.posters);
        assert_eq!(1, report.metadata_ready);
        assert_eq!(1, report.progress_rows.inserted);

        let stored = CatalogStore::new(fixture.data.join("catalog.json"))
            .load()
            .expect("catalog load")
            .expect("catalog exists");
        let item = &stored.published_library.catalog.items[0];
        assert_eq!("desktop-id-1", item.id);
        assert_eq!("Anime Root/Show/Episode 01.mkv", item.relative_path);
        assert_eq!(
            "Anime Root/Show/Episode 01.en.srt",
            item.subtitles[0].relative_path
        );
        assert_eq!(
            fixture.root.join("Show").join("Episode 01.mkv"),
            stored.published_library.files_by_id["desktop-id-1"]
        );
        assert_eq!(
            fixture.root.join("Show").join("Episode 01.en.srt"),
            stored.published_library.subtitle_files_by_id["subtitle-id-1"]
        );
        assert_eq!(Some("/posters/desktop-id-1"), item.poster_path.as_deref());
        assert_eq!(LibraryItemMetadataStatus::Ready, item.metadata_status);
        let metadata = item.anime_metadata.as_ref().expect("metadata");
        assert_eq!("Dandanplay Title", metadata.primary_title);
        assert_eq!("Chinese Title", metadata.display_title);

        let progress = PlaybackProgressStore::new(fixture.data.join("progress.json"))
            .load_progress("desktop-id-1")
            .expect("progress");
        assert_eq!(12000, progress.position_ms);
    }

    #[test]
    fn imports_legacy_catalog_when_registered_roots_are_absent() {
        let fixture = Fixture::new("desktop-import-legacy");
        let db = fixture.database();
        create_schema(&db);
        db.execute(
            "INSERT INTO library_metadata(singleton_id, root_path, root_name, indexed_at_epoch_ms)
             VALUES(1, ?, 'Legacy Root', 3000)",
            [fixture.root.to_string_lossy().as_ref()],
        )
        .expect("metadata");
        db.execute(
            "INSERT INTO local_media_item(relative_path, id, series_title, episode_title, size_bytes, last_modified_epoch_ms, indexed_at_epoch_ms, media_type, stream_path, subtitles_json)
             VALUES('Legacy/Episode 01.mp4', 'legacy-id', 'Legacy', 'Episode 01', 55, 2500, 0, 'video/mp4', '/media/legacy-id', '[]')",
            [],
        )
        .expect("legacy item");
        drop(db);

        import_desktop_catalog_with_options(
            &fixture.data,
            &fixture.db,
            fixture.path.join("poster-cache"),
        )
        .expect("import");
        let stored = CatalogStore::new(fixture.data.join("catalog.json"))
            .load()
            .expect("catalog load")
            .expect("catalog exists");
        assert_eq!("Legacy Root", stored.published_library.catalog.root_name);
        let item = &stored.published_library.catalog.items[0];
        assert_eq!("legacy-id", item.id);
        assert_eq!("Legacy/Episode 01.mp4", item.relative_path);
        assert_eq!(2500, item.indexed_at_epoch_ms);
    }

    #[test]
    fn progress_merge_uses_newest_update_per_media_id() {
        let fixture = Fixture::new("desktop-import-progress-merge");
        let db = fixture.database();
        create_schema(&db);
        db.execute(
            "INSERT INTO playback_progress(media_id, position_ms, duration_ms, updated_at_epoch_ms)
             VALUES('older-existing', 1, 10, 100), ('newer-existing', 2, 20, 100)",
            [],
        )
        .expect("desktop progress");
        drop(db);
        let store = PlaybackProgressStore::new(fixture.data.join("progress.json"));
        store
            .save_progress(PlaybackProgress {
                media_id: "older-existing".to_owned(),
                position_ms: 9,
                duration_ms: Some(90),
                updated_at_epoch_ms: 50,
            })
            .expect("existing older");
        store
            .save_progress(PlaybackProgress {
                media_id: "newer-existing".to_owned(),
                position_ms: 99,
                duration_ms: Some(990),
                updated_at_epoch_ms: 150,
            })
            .expect("existing newer");

        let report = import_desktop_catalog_with_options(
            &fixture.data,
            &fixture.db,
            fixture.path.join("poster-cache"),
        )
        .expect("import");

        assert_eq!(1, report.progress_rows.updated);
        assert_eq!(1, report.progress_rows.kept_existing);
        let store = PlaybackProgressStore::new(fixture.data.join("progress.json"));
        assert_eq!(
            Some(1),
            store
                .load_progress("older-existing")
                .map(|progress| progress.position_ms)
        );
        assert_eq!(
            Some(99),
            store
                .load_progress("newer-existing")
                .map(|progress| progress.position_ms)
        );
    }

    #[test]
    fn imports_empty_database() {
        let fixture = Fixture::new("desktop-import-empty");
        let db = fixture.database();
        create_schema(&db);
        drop(db);

        let report = import_desktop_catalog_with_options(
            &fixture.data,
            &fixture.db,
            fixture.path.join("poster-cache"),
        )
        .expect("empty import");

        assert_eq!(0, report.items);
        assert_eq!(0, report.progress_rows.read);
        let stored = CatalogStore::new(fixture.data.join("catalog.json"))
            .load()
            .expect("catalog load")
            .expect("catalog exists");
        assert_eq!(
            "Registered Libraries",
            stored.published_library.catalog.root_name
        );
        assert!(stored.published_library.catalog.items.is_empty());
    }

    #[test]
    fn rejects_unsupported_schema_version() {
        let fixture = Fixture::new("desktop-import-unsupported");
        let db = fixture.database();
        create_schema(&db);
        db.pragma_update(None, "user_version", 999)
            .expect("user version");
        drop(db);

        let error = import_desktop_catalog_with_options(
            &fixture.data,
            &fixture.db,
            fixture.path.join("poster-cache"),
        )
        .expect_err("unsupported version should fail");

        assert!(
            error
                .to_string()
                .contains("unsupported desktop catalog schema version 999")
        );
    }

    #[test]
    fn rerun_is_idempotent() {
        let fixture = Fixture::new("desktop-import-idempotent");
        let db = fixture.database();
        create_schema(&db);
        db.execute(
            "INSERT INTO library_root(root_id, path, display_name, provenance, state, added_at_epoch_ms, last_scanned_at_epoch_ms, last_error)
             VALUES('root-a', ?, 'Anime Root', 'USER_SELECTED', 'AVAILABLE', 1000, 2000, NULL)",
            [fixture.root.to_string_lossy().as_ref()],
        )
        .expect("root");
        db.execute(
            "INSERT INTO library_root_media_item(root_id, relative_path, id, series_title, episode_title, size_bytes, last_modified_epoch_ms, indexed_at_epoch_ms, media_type, stream_path, subtitles_json)
             VALUES('root-a', 'Show/Episode 01.mkv', 'desktop-id-1', 'Show', 'Episode 01', 123, 1500, 1600, 'video/x-matroska', '/media/desktop-id-1', '[]')",
            [],
        )
        .expect("item");
        db.execute(
            "INSERT INTO playback_progress(media_id, position_ms, duration_ms, updated_at_epoch_ms)
             VALUES('desktop-id-1', 12000, 24000, 1800)",
            [],
        )
        .expect("progress");
        drop(db);

        let poster_cache = fixture.path.join("poster-cache");
        import_desktop_catalog_with_options(&fixture.data, &fixture.db, poster_cache.clone())
            .expect("first import");
        let first_catalog = fs::read_to_string(fixture.data.join("catalog.json")).expect("catalog");
        let first_progress =
            fs::read_to_string(fixture.data.join("progress.json")).expect("progress");

        import_desktop_catalog_with_options(&fixture.data, &fixture.db, poster_cache)
            .expect("second import");
        assert_eq!(
            first_catalog,
            fs::read_to_string(fixture.data.join("catalog.json")).expect("catalog")
        );
        assert_eq!(
            first_progress,
            fs::read_to_string(fixture.data.join("progress.json")).expect("progress")
        );
    }

    #[test]
    fn imported_catalog_serializes_to_library_catalog_wire_shape() {
        let fixture = Fixture::new("desktop-import-wire-shape");
        let db = fixture.database();
        create_schema(&db);
        db.execute(
            "INSERT INTO library_root(root_id, path, display_name, provenance, state, added_at_epoch_ms, last_scanned_at_epoch_ms, last_error)
             VALUES('root-a', ?, 'Anime Root', 'USER_SELECTED', 'AVAILABLE', 1000, 2000, NULL)",
            [fixture.root.to_string_lossy().as_ref()],
        )
        .expect("root");
        db.execute(
            "INSERT INTO library_root_media_item(root_id, relative_path, id, series_title, episode_title, size_bytes, last_modified_epoch_ms, indexed_at_epoch_ms, media_type, stream_path, subtitles_json)
             VALUES('root-a', 'Show/Episode 01.mkv', 'desktop-id-1', 'Show', 'Episode 01', 123, 1500, 1600, 'video/x-matroska', '/media/desktop-id-1', '[]')",
            [],
        )
        .expect("item");
        drop(db);

        import_desktop_catalog_with_options(
            &fixture.data,
            &fixture.db,
            fixture.path.join("poster-cache"),
        )
        .expect("import");
        let stored = CatalogStore::new(fixture.data.join("catalog.json"))
            .load()
            .expect("catalog load")
            .expect("catalog exists");
        let wire = serde_json::to_value(&stored.published_library.catalog).expect("wire json");

        assert_eq!("Registered Libraries", wire["rootName"]);
        assert_eq!("desktop-id-1", wire["items"][0]["id"]);
        assert_eq!("/media/desktop-id-1", wire["items"][0]["streamPath"]);
    }

    fn insert_metadata(db: &Connection, image_url: &str) {
        db.execute(
            "INSERT INTO external_anime_metadata_cache(provider, anime_id, titles_json, episode_count, start_year, image_url, summary, fetched_at_epoch_ms)
             VALUES('DANDANPLAY', 777, ?, 12, 2024, ?, 'summary', 1700)",
            params![
                json!({
                    "primary": "Dandanplay Title",
                    "chinese": "Chinese Title",
                    "english": "English Title",
                    "japanese": "Japanese Title",
                    "alternateNames": ["Alt Title"]
                })
                .to_string(),
                image_url
            ],
        )
        .expect("metadata");
    }

    fn create_schema(db: &Connection) {
        db.execute_batch(DESKTOP_SCHEMA_FIXTURE)
            .expect("schema should create");
    }

    struct Fixture {
        path: PathBuf,
        data: PathBuf,
        root: PathBuf,
        db: PathBuf,
    }

    impl Fixture {
        fn new(prefix: &str) -> Self {
            let id = TEMP_COUNTER.fetch_add(1, Ordering::Relaxed);
            let path = std::env::temp_dir().join(format!("{prefix}-{}-{id}", std::process::id()));
            let _ = fs::remove_dir_all(&path);
            fs::create_dir_all(&path).expect("temp dir");
            let data = path.join("server-data");
            let root = path.join("Anime");
            fs::create_dir_all(&root).expect("root dir");
            let db = path.join("library-copy.db");
            Self {
                path,
                data,
                root,
                db,
            }
        }

        fn database(&self) -> Connection {
            Connection::open(&self.db).expect("fixture database")
        }
    }

    impl Drop for Fixture {
        fn drop(&mut self) {
            let _ = fs::remove_dir_all(&self.path);
        }
    }

    const DESKTOP_SCHEMA_FIXTURE: &str = r#"
CREATE TABLE library_metadata (
  singleton_id INTEGER NOT NULL PRIMARY KEY CHECK (singleton_id = 1),
  root_path TEXT NOT NULL,
  root_name TEXT NOT NULL,
  indexed_at_epoch_ms INTEGER NOT NULL
);
CREATE TABLE local_media_item (
  relative_path TEXT NOT NULL PRIMARY KEY,
  id TEXT NOT NULL,
  series_title TEXT NOT NULL,
  episode_title TEXT NOT NULL,
  size_bytes INTEGER NOT NULL,
  last_modified_epoch_ms INTEGER NOT NULL,
  indexed_at_epoch_ms INTEGER NOT NULL DEFAULT 0,
  media_type TEXT NOT NULL,
  stream_path TEXT NOT NULL,
  subtitles_json TEXT NOT NULL DEFAULT '[]'
);
CREATE TABLE playback_progress (
  media_id TEXT NOT NULL PRIMARY KEY,
  position_ms INTEGER NOT NULL,
  duration_ms INTEGER,
  updated_at_epoch_ms INTEGER NOT NULL
);
CREATE TABLE app_setting (
  setting_key TEXT NOT NULL PRIMARY KEY,
  setting_value TEXT NOT NULL,
  updated_at_epoch_ms INTEGER NOT NULL
);
CREATE TABLE library_root (
  root_id TEXT NOT NULL PRIMARY KEY,
  path TEXT NOT NULL UNIQUE,
  display_name TEXT NOT NULL,
  provenance TEXT NOT NULL,
  state TEXT NOT NULL,
  added_at_epoch_ms INTEGER NOT NULL,
  last_scanned_at_epoch_ms INTEGER,
  last_error TEXT
);
CREATE TABLE library_root_media_item (
  root_id TEXT NOT NULL,
  relative_path TEXT NOT NULL,
  id TEXT NOT NULL UNIQUE,
  series_title TEXT NOT NULL,
  episode_title TEXT NOT NULL,
  size_bytes INTEGER NOT NULL,
  last_modified_epoch_ms INTEGER NOT NULL,
  indexed_at_epoch_ms INTEGER NOT NULL DEFAULT 0,
  media_type TEXT NOT NULL,
  stream_path TEXT NOT NULL,
  subtitles_json TEXT NOT NULL DEFAULT '[]',
  PRIMARY KEY(root_id, relative_path)
);
CREATE TABLE download_queue_item (
  id TEXT NOT NULL PRIMARY KEY,
  source_uri TEXT NOT NULL,
  output_path TEXT NOT NULL,
  state TEXT NOT NULL,
  position_bytes INTEGER NOT NULL,
  total_bytes INTEGER,
  created_at_epoch_ms INTEGER NOT NULL,
  updated_at_epoch_ms INTEGER NOT NULL,
  failure_message TEXT
);
CREATE TABLE dandanplay_comment_cache (
  media_id TEXT NOT NULL PRIMARY KEY,
  file_hash TEXT NOT NULL,
  file_name TEXT NOT NULL,
  file_size_bytes INTEGER NOT NULL,
  episode_id INTEGER,
  anime_id INTEGER,
  anime_title TEXT,
  episode_title TEXT,
  shift_seconds REAL,
  comments_json TEXT NOT NULL,
  rendered_ass_path TEXT,
  fetched_at_epoch_ms INTEGER NOT NULL
);
CREATE TABLE external_anime_metadata_cache (
  provider TEXT NOT NULL,
  anime_id INTEGER NOT NULL,
  titles_json TEXT NOT NULL,
  episode_count INTEGER,
  start_year INTEGER,
  image_url TEXT,
  summary TEXT,
  fetched_at_epoch_ms INTEGER NOT NULL,
  PRIMARY KEY(provider, anime_id)
);
CREATE TABLE external_anime_mapping (
  local_series_id TEXT NOT NULL,
  provider TEXT NOT NULL,
  anime_id INTEGER NOT NULL,
  source TEXT NOT NULL,
  confidence REAL NOT NULL,
  mapped_at_epoch_ms INTEGER NOT NULL,
  PRIMARY KEY(local_series_id, provider)
);
CREATE TABLE external_anime_item_mapping (
  local_media_id TEXT NOT NULL,
  provider TEXT NOT NULL,
  anime_id INTEGER NOT NULL,
  source TEXT NOT NULL,
  confidence REAL NOT NULL,
  mapped_at_epoch_ms INTEGER NOT NULL,
  PRIMARY KEY(local_media_id, provider)
);
CREATE TABLE external_anime_list_entry (
  provider TEXT NOT NULL,
  anime_id INTEGER NOT NULL,
  status TEXT,
  watched_episodes INTEGER,
  score INTEGER,
  updated_at_epoch_ms INTEGER,
  PRIMARY KEY(provider, anime_id)
);
CREATE TABLE external_anime_sync_failure (
  provider TEXT NOT NULL,
  anime_id INTEGER NOT NULL,
  message TEXT NOT NULL,
  failed_at_epoch_ms INTEGER NOT NULL,
  attempt_count INTEGER NOT NULL,
  retry_after_epoch_ms INTEGER NOT NULL,
  PRIMARY KEY(provider, anime_id)
);
CREATE TABLE local_anime_list_entry (
  local_series_id TEXT NOT NULL PRIMARY KEY,
  status TEXT NOT NULL,
  score INTEGER,
  notes TEXT,
  updated_at_epoch_ms INTEGER NOT NULL
);
CREATE TABLE library_quality_issue_decision (
  issue_key TEXT NOT NULL PRIMARY KEY,
  state TEXT NOT NULL,
  updated_at_epoch_ms INTEGER NOT NULL
);
"#;
}
