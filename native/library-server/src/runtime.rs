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
use crate::http::{self, HttpServerConfig, HttpServerState, ProviderAdminState};
use crate::lock::DataDirectoryLock;
use crate::logging::{CatalogScanSummary, StartupSummary};
use crate::poster_cache::PosterCacheStore;
use crate::progress::PlaybackProgressStore;
use crate::provider_secrets::ProviderSecretStore;
use crate::scanner::scan_roots_with_progress;
use crate::settings::{
    HeadlessServerSettings, SettingsStore, apply_external_anime_local_defaults,
    generate_pairing_token,
};
use crate::{LibraryServerError, Result};

#[derive(Debug)]
pub struct LoadedServer {
    options: ServerOptions,
    settings: HeadlessServerSettings,
    persisted_settings: HeadlessServerSettings,
    effective_library_roots: Vec<PathBuf>,
    stored_library: Option<HeadlessStoredLibrary>,
    _lock: DataDirectoryLock,
}

impl LoadedServer {
    /// Loads settings and the cached catalog snapshot only. The library roots
    /// are *not* scanned here — the server binds and answers requests from the
    /// cached snapshot immediately, and `BoundServer::serve_until_shutdown`
    /// runs the (potentially slow) scan in the background, publishing the
    /// fresh catalog when it completes.
    pub fn load(options: ServerOptions) -> Result<Self> {
        let lock = DataDirectoryLock::acquire(&options.data_directory)?;
        let settings_store =
            SettingsStore::new(options.data_directory.join("server-settings.json"));
        let mut persisted_settings = settings_store
            .load_or_create(options.pairing_token.as_deref(), generate_pairing_token)?;
        let secret_store =
            ProviderSecretStore::platform(options.data_directory.join("provider-secrets.json"));
        secret_store.load()?.merge_into(&mut persisted_settings);
        #[cfg(windows)]
        secret_store.save(&crate::provider_secrets::ProviderSecrets::from_settings(
            &persisted_settings,
        ))?;
        let settings = apply_external_anime_local_defaults(apply_dandanplay_local_defaults(
            persisted_settings.clone(),
        ));
        let effective_library_roots = if options.library_roots.is_empty() {
            settings.library_roots.clone()
        } else {
            options.library_roots.clone()
        };

        let catalog_store = CatalogStore::new(options.data_directory.join("catalog.json"));
        let stored_library = catalog_store.load()?;

        Ok(Self {
            options,
            settings,
            persisted_settings,
            effective_library_roots,
            stored_library,
            _lock: lock,
        })
    }

    pub fn startup_summary(&self) -> StartupSummary {
        StartupSummary::from_loaded_state(
            &self.options,
            &self.settings,
            &self.effective_library_roots,
            self.stored_library.as_ref(),
            None,
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
        let state = self.http_state()?;
        let app = http::app(state.clone());
        Ok(BoundServer {
            loaded: self,
            listener,
            local_port,
            app,
            state,
        })
    }

    fn http_state(&self) -> Result<HttpServerState> {
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
        let provider_admin = Arc::new(ProviderAdminState::new(
            self.options.data_directory.clone(),
            self.persisted_settings.clone(),
            self.settings.clone(),
            dandanplay_resolver.clone(),
        )?);
        let catalog_metadata = Some(Arc::new(CatalogMetadataStore::new(
            self.options.data_directory.join("catalog-metadata.json"),
        )));
        let poster_cache = Some(Arc::new(PosterCacheStore::new(
            self.options.data_directory.join("poster-cache"),
        )));
        Ok(HttpServerState::new(
            published_library,
            progress_store,
            HttpServerConfig::headless(
                self.options.web_assets_root.clone(),
                &self.settings,
                dandanplay_resolver,
                catalog_metadata,
                poster_cache,
                provider_admin,
            ),
        ))
    }
}

pub struct BoundServer {
    loaded: LoadedServer,
    listener: TcpListener,
    local_port: u16,
    app: Router,
    state: HttpServerState,
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
        Self::spawn_background_scan(&loaded, self.state);
        axum::serve(self.listener, self.app)
            .with_graceful_shutdown(shutdown)
            .await
            .map_err(|error| LibraryServerError::with_context(error, "HTTP server failed"))?;
        drop(loaded);
        Ok(())
    }

    /// Rescans the library roots off the request path, then persists and
    /// publishes the fresh catalog. The server keeps answering from the cached
    /// snapshot in the meantime; `/api/server/status` reports `scanning` with
    /// a live file count so clients can show indexing progress.
    fn spawn_background_scan(loaded: &LoadedServer, state: HttpServerState) {
        let roots = loaded.effective_library_roots.clone();
        if roots.is_empty() {
            return;
        }
        let previous = loaded.stored_library.clone();
        let catalog_store = CatalogStore::new(loaded.options.data_directory.join("catalog.json"));
        // Flag before serving starts so a client's very first status poll
        // already reports the scan.
        state.set_scanning(true);
        tokio::task::spawn_blocking(move || {
            scan_and_publish(&roots, previous, &catalog_store, &state);
        });
    }
}

/// Body of the background scan: walk the roots (reusing unchanged items from
/// `previous`), persist the snapshot, and hot-swap the served library. Always
/// clears the `scanning` status flag on the way out.
fn scan_and_publish(
    roots: &[PathBuf],
    previous: Option<HeadlessStoredLibrary>,
    catalog_store: &CatalogStore,
    state: &HttpServerState,
) {
    let progress = state.scan_progress();
    match scan_roots_with_progress(roots, previous.as_ref(), Some(&progress)) {
        Ok(scan) => {
            let summary = CatalogScanSummary::from(&scan);
            match catalog_store.save_scan(scan) {
                Ok(stored) => {
                    state.publish_library(stored.published_library);
                    println!("{}", summary.to_log_line());
                }
                Err(error) => {
                    eprintln!("failed to persist catalog scan: {error}");
                }
            }
        }
        Err(error) => {
            eprintln!("catalog scan failed: {error}");
        }
    }
    state.set_scanning(false);
}

#[cfg(test)]
mod tests {
    use std::fs;
    use std::sync::atomic::{AtomicU64, Ordering};

    use axum::body::{Body, to_bytes};
    use axum::http::{Request, StatusCode};
    use serde_json::Value;
    use tower::ServiceExt;

    use super::*;

    static TEMP_COUNTER: AtomicU64 = AtomicU64::new(0);

    fn temp_dir(prefix: &str) -> PathBuf {
        let id = TEMP_COUNTER.fetch_add(1, Ordering::Relaxed);
        let path = std::env::temp_dir().join(format!("{prefix}-{}-{id}", std::process::id()));
        let _ = fs::remove_dir_all(&path);
        fs::create_dir_all(&path).expect("temp dir should create");
        path
    }

    async fn get_json(app: &Router, path: &str) -> Value {
        let response = app
            .clone()
            .oneshot(
                Request::builder()
                    .uri(path)
                    .body(Body::empty())
                    .expect("request builds"),
            )
            .await
            .expect("request succeeds");
        assert_eq!(StatusCode::OK, response.status());
        let bytes = to_bytes(response.into_body(), usize::MAX)
            .await
            .expect("body reads");
        serde_json::from_slice(&bytes).expect("body is JSON")
    }

    #[tokio::test]
    async fn binds_before_scanning_and_publishes_the_scan_result() {
        let temp = temp_dir("danmaku-runtime-deferred-scan");
        let data_directory = temp.join("data");
        let root = temp.join("Anime");
        let show = root.join("Example Show");
        fs::create_dir_all(&show).expect("fixture dirs");
        fs::write(show.join("Episode 01.mp4"), [1, 2, 3, 4]).expect("media");

        let loaded = LoadedServer::load(ServerOptions {
            data_directory: data_directory.clone(),
            library_roots: vec![root],
            port: 0,
            pairing_token: Some("123456".to_owned()),
            web_assets_root: None,
            import_desktop_catalog: None,
        })
        .expect("server loads");
        let bound = loaded.bind().await.expect("server binds");
        assert!(bound.local_port() > 0);

        // The server is ready before any scan ran: the catalog is empty.
        let catalog = get_json(&bound.app, "/api/library").await;
        assert_eq!(0, catalog["items"].as_array().expect("items").len());

        // Run the deferred scan synchronously (production spawns it on a
        // blocking task once serving starts) and confirm it is published.
        bound.state.set_scanning(true);
        let status = get_json(&bound.app, "/api/server/status").await;
        assert_eq!(Value::Bool(true), status["scanning"]);
        scan_and_publish(
            &bound.loaded.effective_library_roots,
            bound.loaded.stored_library.clone(),
            &CatalogStore::new(data_directory.join("catalog.json")),
            &bound.state,
        );

        let status = get_json(&bound.app, "/api/server/status").await;
        assert_eq!(Value::Null, status["scanning"]);
        let catalog = get_json(&bound.app, "/api/library").await;
        assert_eq!(1, catalog["items"].as_array().expect("items").len());
        assert_eq!("Example Show", catalog["items"][0]["seriesTitle"]);
        assert!(
            CatalogStore::new(data_directory.join("catalog.json"))
                .load()
                .expect("catalog loads")
                .is_some()
        );

        drop(bound);
        fs::remove_dir_all(temp).expect("temp should delete");
    }
}
