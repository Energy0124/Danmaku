pub mod catalog;
pub mod cli;
pub mod domain;
pub(crate) mod hash;
pub mod lock;
pub mod logging;
pub mod scanner;
pub mod settings;

use std::error::Error;
use std::fmt::{self, Display};

use catalog::CatalogStore;
use cli::ServerOptions;
use lock::DataDirectoryLock;
use logging::{CatalogScanSummary, StartupSummary};
use scanner::scan_roots;
use settings::{SettingsStore, generate_pairing_token};

pub type Result<T> = std::result::Result<T, LibraryServerError>;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct LibraryServerError {
    message: String,
}

impl LibraryServerError {
    pub fn new(message: impl Into<String>) -> Self {
        Self {
            message: message.into(),
        }
    }

    pub fn with_context(error: impl Display, context: impl Display) -> Self {
        Self::new(format!("{context}: {error}"))
    }
}

impl Display for LibraryServerError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(&self.message)
    }
}

impl Error for LibraryServerError {}

impl From<std::io::Error> for LibraryServerError {
    fn from(error: std::io::Error) -> Self {
        Self::new(error.to_string())
    }
}

impl From<serde_json::Error> for LibraryServerError {
    fn from(error: serde_json::Error) -> Self {
        Self::new(error.to_string())
    }
}

pub fn run_foundations(options: ServerOptions) -> Result<StartupSummary> {
    let _lock = DataDirectoryLock::acquire(&options.data_directory)?;
    let settings_store = SettingsStore::new(options.data_directory.join("server-settings.json"));
    let settings =
        settings_store.load_or_create(options.pairing_token.as_deref(), generate_pairing_token)?;
    let effective_library_roots = if options.library_roots.is_empty() {
        settings.library_roots.clone()
    } else {
        options.library_roots.clone()
    };

    let catalog_store = CatalogStore::new(options.data_directory.join("catalog.json"));
    let stored_library = catalog_store.load()?;
    let scan = if effective_library_roots.is_empty() {
        None
    } else {
        Some(scan_roots(
            &effective_library_roots,
            stored_library.as_ref(),
        )?)
    };
    let scan_summary = scan.as_ref().map(CatalogScanSummary::from);
    let stored_library = if let Some(scan) = scan {
        Some(catalog_store.save_scan(scan)?)
    } else {
        stored_library
    };

    Ok(StartupSummary::from_loaded_state(
        &options,
        &settings,
        &effective_library_roots,
        stored_library.as_ref(),
        scan_summary,
    ))
}

#[cfg(test)]
mod tests {
    use std::fs;
    use std::path::PathBuf;
    use std::sync::atomic::{AtomicU64, Ordering};

    use super::*;

    static TEMP_COUNTER: AtomicU64 = AtomicU64::new(0);

    #[test]
    fn run_foundations_scans_configured_roots_and_persists_catalog() {
        let temp = temp_dir("danmaku-run-foundations-scan");
        let data_directory = temp.join("data");
        let root = temp.join("Anime");
        let show = root.join("Example Show");
        fs::create_dir_all(&show).expect("fixture dirs");
        fs::write(show.join("Episode 01.mp4"), [1, 2, 3, 4]).expect("media");
        fs::write(show.join("Episode 01.en.vtt"), "WEBVTT\n\nHello").expect("subtitle");

        let summary = run_foundations(ServerOptions {
            data_directory: data_directory.clone(),
            library_roots: vec![root],
            port: 0,
            pairing_token: Some("123456".to_owned()),
            web_assets_root: None,
        })
        .expect("startup should scan");

        let output = summary.to_log_lines().join("\n");
        assert!(output.contains("Catalog scan: completed; roots=1; items=1; subtitles=1"));
        let stored = CatalogStore::new(data_directory.join("catalog.json"))
            .load()
            .expect("catalog load should succeed")
            .expect("catalog should exist");
        assert_eq!(1, stored.published_library.catalog.items.len());
        assert_eq!(
            "Example Show",
            stored.published_library.catalog.items[0].series_title
        );

        fs::remove_dir_all(temp).expect("temp should delete");
    }

    fn temp_dir(prefix: &str) -> PathBuf {
        let id = TEMP_COUNTER.fetch_add(1, Ordering::Relaxed);
        let path = std::env::temp_dir().join(format!("{prefix}-{}-{id}", std::process::id()));
        let _ = fs::remove_dir_all(&path);
        fs::create_dir_all(&path).expect("temp dir should create");
        path
    }
}
