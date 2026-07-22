pub mod catalog;
pub mod catalog_metadata;
pub mod cli;
pub mod dandanplay;
pub mod desktop_import;
pub mod discovery;
pub mod domain;
pub mod external_provider;
pub(crate) mod hash;
pub mod http;
pub mod lock;
pub mod logging;
pub mod poster_cache;
pub mod progress;
pub mod provider_secrets;
pub mod runtime;
pub mod scanner;
pub mod settings;
pub mod tracking;

use std::error::Error;
use std::fmt::{self, Display};

use cli::ServerOptions;
use logging::StartupSummary;
use runtime::LoadedServer;

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
    LoadedServer::load(options).map(|server| server.startup_summary())
}

#[cfg(test)]
mod tests {
    use std::fs;
    use std::path::PathBuf;
    use std::sync::atomic::{AtomicU64, Ordering};

    use super::*;
    use crate::catalog::CatalogStore;

    static TEMP_COUNTER: AtomicU64 = AtomicU64::new(0);

    #[test]
    fn run_foundations_loads_without_scanning_so_startup_stays_fast() {
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
            import_desktop_catalog: None,
        })
        .expect("startup should load");

        // Scanning is deferred to a background task after the HTTP server
        // binds (see `BoundServer::serve_until_shutdown`), so loading alone
        // performs no scan and persists no catalog.
        let output = summary.to_log_lines().join("\n");
        assert!(output.contains("Catalog scan: not run"));
        assert!(output.contains("Catalog snapshot: absent"));
        assert!(
            CatalogStore::new(data_directory.join("catalog.json"))
                .load()
                .expect("catalog load should succeed")
                .is_none()
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
