use std::future::Future;
use std::path::PathBuf;
use std::sync::Arc;

use axum::Router;
use tokio::net::TcpListener;

use crate::catalog::{CatalogStore, HeadlessStoredLibrary, PublishedLibrary};
use crate::catalog_metadata::CatalogMetadataStore;
use crate::cli::ServerOptions;
use crate::dandanplay::{DandanplayResolver, apply_dandanplay_local_defaults};
use crate::discovery::DiscoveryAnnouncer;
use crate::http::{self, HttpServerConfig, HttpServerState};
use crate::lock::DataDirectoryLock;
use crate::logging::{CatalogScanSummary, StartupSummary};
use crate::poster_cache::PosterCacheStore;
use crate::progress::PlaybackProgressStore;
use crate::scanner::scan_roots;
use crate::settings::{
    HeadlessServerSettings, SettingsStore, apply_external_anime_local_defaults,
    generate_pairing_token,
};
use crate::{LibraryServerError, Result};

#[derive(Debug)]
pub struct LoadedServer {
    options: ServerOptions,
    settings: HeadlessServerSettings,
    effective_library_roots: Vec<PathBuf>,
    stored_library: Option<HeadlessStoredLibrary>,
    scan_summary: Option<CatalogScanSummary>,
    _lock: DataDirectoryLock,
}

impl LoadedServer {
    pub fn load(options: ServerOptions) -> Result<Self> {
        let lock = DataDirectoryLock::acquire(&options.data_directory)?;
        let settings_store =
            SettingsStore::new(options.data_directory.join("server-settings.json"));
        let settings = settings_store
            .load_or_create(options.pairing_token.as_deref(), generate_pairing_token)?;
        let settings =
            apply_external_anime_local_defaults(apply_dandanplay_local_defaults(settings));
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

        Ok(Self {
            options,
            settings,
            effective_library_roots,
            stored_library,
            scan_summary,
            _lock: lock,
        })
    }

    pub fn startup_summary(&self) -> StartupSummary {
        StartupSummary::from_loaded_state(
            &self.options,
            &self.settings,
            &self.effective_library_roots,
            self.stored_library.as_ref(),
            self.scan_summary.clone(),
        )
    }

    pub async fn bind(self) -> Result<BoundServer> {
        let listener = TcpListener::bind(("0.0.0.0", self.options.port))
            .await
            .map_err(|error| {
                LibraryServerError::with_context(
                    error,
                    format!("failed to bind HTTP server on port {}", self.options.port),
                )
            })?;
        let local_port = listener
            .local_addr()
            .map_err(|error| {
                LibraryServerError::with_context(error, "failed to read HTTP socket address")
            })?
            .port();
        let app = self.router();
        Ok(BoundServer {
            loaded: self,
            listener,
            local_port,
            app,
        })
    }

    fn router(&self) -> Router {
        let published_library = self
            .stored_library
            .as_ref()
            .map(|stored| stored.published_library.clone())
            .unwrap_or_else(PublishedLibrary::empty);
        let progress_store = Arc::new(PlaybackProgressStore::new(
            self.options.data_directory.join("progress.json"),
        ));
        let dandanplay_resolver =
            DandanplayResolver::from_settings(&self.settings, &self.options.data_directory)
                .map(Arc::new);
        let catalog_metadata = Some(Arc::new(CatalogMetadataStore::new(
            self.options.data_directory.join("catalog-metadata.json"),
        )));
        let poster_cache = Some(Arc::new(PosterCacheStore::new(
            self.options.data_directory.join("poster-cache"),
        )));
        let state = HttpServerState::new(
            published_library,
            progress_store,
            HttpServerConfig::headless(
                self.options.web_assets_root.clone(),
                &self.settings,
                dandanplay_resolver,
                catalog_metadata,
                poster_cache,
            ),
        );
        http::app(state)
    }
}

pub struct BoundServer {
    loaded: LoadedServer,
    listener: TcpListener,
    local_port: u16,
    app: Router,
}

impl BoundServer {
    pub fn local_port(&self) -> u16 {
        self.local_port
    }

    pub async fn serve_until_shutdown<F>(self, shutdown: F) -> Result<()>
    where
        F: Future<Output = ()> + Send + 'static,
    {
        let _discovery = DiscoveryAnnouncer::start(self.local_port).await?;
        let loaded = self.loaded;
        axum::serve(self.listener, self.app)
            .with_graceful_shutdown(shutdown)
            .await
            .map_err(|error| LibraryServerError::with_context(error, "HTTP server failed"))?;
        drop(loaded);
        Ok(())
    }
}
