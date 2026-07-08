pub mod catalog;
pub mod cli;
pub mod lock;
pub mod logging;
pub mod settings;

use std::error::Error;
use std::fmt::{self, Display};

use catalog::CatalogStore;
use cli::ServerOptions;
use lock::DataDirectoryLock;
use logging::StartupSummary;
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

    Ok(StartupSummary::from_loaded_state(
        &options,
        &settings,
        &effective_library_roots,
        stored_library.as_ref(),
    ))
}
