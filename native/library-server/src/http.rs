use std::collections::{BTreeMap, BTreeSet};
use std::net::SocketAddr;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, RwLock};

use axum::Router;
use axum::body::{Body, to_bytes};
use axum::extract::{ConnectInfo, State};
use axum::http::header::{
    ACCEPT, ACCEPT_RANGES, CACHE_CONTROL, CONTENT_LENGTH, CONTENT_RANGE, CONTENT_TYPE, HeaderValue,
    LOCATION,
};
use axum::http::{HeaderMap, Method, Request, Response, StatusCode};
use axum::routing::any;
use serde::{Deserialize, Serialize};
use tokio::io::{AsyncReadExt, AsyncSeekExt};
use tokio_util::io::ReaderStream;

use crate::attention::build_attention_document;
use crate::catalog::{CatalogStore, PublishedLibrary, normalize_lexically};
use crate::catalog_metadata::CatalogMetadataStore;
use crate::dandanplay::{DandanplayResolveResult, DandanplayResolver, LanDanmakuTrack};
use crate::domain::PlaybackProgress;
#[cfg(test)]
use crate::external_provider::ExternalAnimeListEntry;
use crate::external_provider::{
    ExternalAnimeMatchQuery, ExternalProviderService, parse_provider_alias, provider_runtime_status,
};
use crate::logging::CatalogScanSummary;
use crate::organizer::{
    LibraryOrganizer, OrganizationAccepted, OrganizationExecuteRequest, OrganizationPreviewRequest,
    OrganizationUndoRequest,
};
use crate::poster_cache::PosterCacheStore;
use crate::progress::PlaybackProgressStore;
use crate::scanner::{
    LibraryRescanTarget, ScanProgress, rescan_target_with_progress, resolve_rescan_target,
};
use crate::settings::HeadlessServerSettings;
use crate::tracking::{
    ExternalAnimeMapping, ExternalAnimeMappingSource, current_epoch_ms, execute_tracking_sync,
    provider_progress_import, refresh_tracking_readback, tracking_document,
};

const WEBHOOK_TOKEN_HEADER: &str = "X-Danmaku-Webhook-Token";
pub const HOST_MODE_HEADLESS_SERVER: &str = "headless-server";

#[derive(Debug, Clone)]
pub struct HttpServerConfig {
    pub web_assets_root: Option<PathBuf>,
    pub host_mode: String,
    pub provider_settings: Option<LanProviderSettingsStatus>,
    pub provider_runtime_status: Option<crate::external_provider::LanProviderRuntimeStatus>,
    pub external_provider_service: Option<Arc<ExternalProviderService>>,
    pub authenticated_post_hooks: Vec<AuthenticatedPostHookConfig>,
    pub dandanplay_resolver: Option<Arc<DandanplayResolver>>,
    pub catalog_metadata: Option<Arc<CatalogMetadataStore>>,
    pub poster_cache: Option<Arc<PosterCacheStore>>,
    pub provider_admin: Option<Arc<ProviderAdminState>>,
}

impl HttpServerConfig {
    pub fn headless(
        web_assets_root: Option<PathBuf>,
        settings: &HeadlessServerSettings,
        dandanplay_resolver: Option<Arc<DandanplayResolver>>,
        catalog_metadata: Option<Arc<CatalogMetadataStore>>,
        poster_cache: Option<Arc<PosterCacheStore>>,
        provider_admin: Arc<ProviderAdminState>,
    ) -> Self {
        Self {
            web_assets_root,
            host_mode: HOST_MODE_HEADLESS_SERVER.to_owned(),
            provider_settings: Some(LanProviderSettingsStatus::from(settings)),
            provider_runtime_status: Some(provider_runtime_status(settings)),
            external_provider_service: Some(Arc::new(ExternalProviderService::from_settings(
                settings,
            ))),
            authenticated_post_hooks: Vec::new(),
            dandanplay_resolver,
            catalog_metadata,
            poster_cache,
            provider_admin: Some(provider_admin),
        }
    }

    #[cfg(test)]
    fn fixture(web_assets_root: PathBuf) -> Self {
        Self {
            web_assets_root: Some(web_assets_root),
            host_mode: HOST_MODE_HEADLESS_SERVER.to_owned(),
            provider_settings: None,
            provider_runtime_status: None,
            external_provider_service: None,
            authenticated_post_hooks: vec![AuthenticatedPostHookConfig {
                path: "/api/hooks/fixture".to_owned(),
                token: "0123456789abcdef".to_owned(),
            }],
            dandanplay_resolver: None,
            catalog_metadata: None,
            poster_cache: None,
            provider_admin: None,
        }
    }
}

mod provider_admin;
pub use provider_admin::ProviderAdminState;
use provider_admin::{
    BangumiAccountRequest, ExternalTrackingConflictImportRequest, ExternalTrackingMappingRequest,
    ExternalTrackingSyncRequest, MyAnimeListOAuthCompleteRequest, ProviderSettingsUpdate,
};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AuthenticatedPostHookConfig {
    pub path: String,
    pub token: String,
}

#[derive(Debug, Clone)]
struct LibraryScanConfig {
    roots: Vec<PathBuf>,
    catalog_store: CatalogStore,
}

#[derive(Debug, Clone)]
pub struct HttpServerState {
    /// Hot-swappable so a background rescan can publish a fresh library while
    /// the server keeps answering requests from the previous snapshot.
    library: Arc<RwLock<Arc<PublishedLibrary>>>,
    progress_store: Arc<PlaybackProgressStore>,
    web_assets: Option<StaticWebAssets>,
    status: LanLibraryServerStatus,
    authenticated_post_hooks: Arc<BTreeMap<String, Vec<u8>>>,
    provider_runtime_status: Option<crate::external_provider::LanProviderRuntimeStatus>,
    external_provider_service: Option<Arc<ExternalProviderService>>,
    dandanplay_resolver: Option<Arc<DandanplayResolver>>,
    catalog_metadata: Option<Arc<CatalogMetadataStore>>,
    poster_cache: Option<Arc<PosterCacheStore>>,
    provider_admin: Option<Arc<ProviderAdminState>>,
    /// Media IDs with a poster search/download currently in flight, so
    /// concurrent `/api/library` reads (which retry missing posters — see
    /// `handle_catalog`) don't pile up redundant external requests for the
    /// same item while one is already running.
    poster_resolution_in_flight: Arc<Mutex<BTreeSet<String>>>,
    /// True while a background catalog scan is running; surfaced on
    /// `/api/server/status` so clients can show indexing progress.
    scanning: Arc<AtomicBool>,
    library_mutating: Arc<AtomicBool>,
    scan_progress: Arc<ScanProgress>,
    scan_error: Arc<RwLock<Option<String>>>,
    library_scan: Option<Arc<LibraryScanConfig>>,
    organizer: Option<Arc<LibraryOrganizer>>,
}

impl HttpServerState {
    pub fn new(
        library: PublishedLibrary,
        progress_store: Arc<PlaybackProgressStore>,
        config: HttpServerConfig,
    ) -> Self {
        let web_assets = config.web_assets_root.map(StaticWebAssets::new);
        let status = LanLibraryServerStatus {
            web_ui_available: web_assets.is_some(),
            web_ui_path: web_assets.as_ref().map(|assets| assets.path_prefix.clone()),
            host_mode: config.host_mode,
            provider_settings: config.provider_settings,
            ..LanLibraryServerStatus::default()
        };
        let authenticated_post_hooks = config
            .authenticated_post_hooks
            .into_iter()
            .map(|hook| (hook.path, hook.token.into_bytes()))
            .collect();
        Self {
            library: Arc::new(RwLock::new(Arc::new(library))),
            progress_store,
            web_assets,
            status,
            authenticated_post_hooks: Arc::new(authenticated_post_hooks),
            provider_runtime_status: config.provider_runtime_status,
            external_provider_service: config.external_provider_service,
            dandanplay_resolver: config.dandanplay_resolver,
            catalog_metadata: config.catalog_metadata,
            poster_cache: config.poster_cache,
            provider_admin: config.provider_admin,
            poster_resolution_in_flight: Arc::new(Mutex::new(BTreeSet::new())),
            scanning: Arc::new(AtomicBool::new(false)),
            library_mutating: Arc::new(AtomicBool::new(false)),
            scan_progress: Arc::new(ScanProgress::default()),
            scan_error: Arc::new(RwLock::new(None)),
            library_scan: None,
            organizer: None,
        }
    }

    pub fn with_library_scan(mut self, roots: Vec<PathBuf>, catalog_store: CatalogStore) -> Self {
        self.organizer = Some(Arc::new(LibraryOrganizer::new(
            roots.clone(),
            catalog_store.clone(),
        )));
        self.library_scan = Some(Arc::new(LibraryScanConfig {
            roots,
            catalog_store,
        }));
        self
    }

    fn provider_runtime_status(
        &self,
    ) -> Option<crate::external_provider::LanProviderRuntimeStatus> {
        self.provider_admin
            .as_ref()
            .map(|admin| admin.runtime_status())
            .or_else(|| self.provider_runtime_status.clone())
    }

    fn external_provider_service(&self) -> Option<Arc<ExternalProviderService>> {
        self.provider_admin
            .as_ref()
            .map(|admin| admin.external_provider_service())
            .or_else(|| self.external_provider_service.clone())
    }

    fn dandanplay_resolver(&self) -> Option<Arc<DandanplayResolver>> {
        match &self.provider_admin {
            Some(admin) => admin.dandanplay_resolver(),
            None => self.dandanplay_resolver.clone(),
        }
    }

    /// Snapshot of the currently published library; requests keep using the
    /// snapshot they read even if a rescan swaps the library mid-request.
    fn library(&self) -> Arc<PublishedLibrary> {
        self.library
            .read()
            .map(|library| Arc::clone(&library))
            .unwrap_or_else(|poisoned| Arc::clone(&poisoned.into_inner()))
    }

    /// Publishes a freshly scanned library, replacing the served snapshot.
    pub fn publish_library(&self, library: PublishedLibrary) {
        let library = Arc::new(library);
        match self.library.write() {
            Ok(mut guard) => *guard = library,
            Err(poisoned) => *poisoned.into_inner() = library,
        }
    }

    pub fn try_start_scan(&self) -> bool {
        if self
            .library_mutating
            .compare_exchange(false, true, Ordering::AcqRel, Ordering::Relaxed)
            .is_err()
        {
            return false;
        }
        if self
            .scanning
            .compare_exchange(false, true, Ordering::AcqRel, Ordering::Relaxed)
            .is_err()
        {
            self.library_mutating.store(false, Ordering::Release);
            return false;
        }
        self.scan_progress.reset();
        self.set_scan_error(None);
        true
    }

    pub fn finish_scan(&self, error: Option<String>) {
        self.set_scan_error(error);
        self.scanning.store(false, Ordering::Release);
        self.library_mutating.store(false, Ordering::Release);
    }

    fn try_start_organization(&self) -> bool {
        self.library_mutating
            .compare_exchange(false, true, Ordering::AcqRel, Ordering::Relaxed)
            .is_ok()
    }

    fn finish_organization(&self) {
        self.library_mutating.store(false, Ordering::Release);
    }

    fn set_scan_error(&self, error: Option<String>) {
        match self.scan_error.write() {
            Ok(mut guard) => *guard = error,
            Err(poisoned) => *poisoned.into_inner() = error,
        }
    }

    fn scan_error(&self) -> Option<String> {
        self.scan_error
            .read()
            .map(|error| error.clone())
            .unwrap_or_else(|poisoned| poisoned.into_inner().clone())
    }

    pub fn scan_progress(&self) -> Arc<ScanProgress> {
        Arc::clone(&self.scan_progress)
    }
}

pub fn app(state: HttpServerState) -> Router {
    Router::new().fallback(any(dispatch)).with_state(state)
}

async fn dispatch(State(state): State<HttpServerState>, request: Request<Body>) -> Response<Body> {
    let (parts, body) = request.into_parts();
    let method = parts.method;
    let path = parts.uri.path().to_owned();
    let query = parts.uri.query().map(ToOwned::to_owned);
    let peer = parts
        .extensions
        .get::<ConnectInfo<SocketAddr>>()
        .map(|ConnectInfo(peer)| *peer);
    let headers = parts.headers;

    if path.starts_with("/api/server/status") {
        return handle_server_status(&state, &method);
    }
    if path == "/api/library/rescan" {
        return handle_library_rescan(&state, method, body).await;
    }
    if path.starts_with("/api/library/organize") {
        return handle_library_organize(&state, peer, method, &path, body).await;
    }
    if path == "/api/library/attention" {
        return handle_library_attention(&state, &method);
    }
    if path.starts_with("/api/library") {
        return handle_catalog(&state, &method);
    }
    if path == "/api/progress" || path.starts_with("/api/progress/") {
        return handle_progress(&state, method, &path, body).await;
    }
    if path.starts_with("/api/progress") {
        return handle_progress_list_exact(&state, &method, &path);
    }
    if path.starts_with("/api/danmaku/") {
        return handle_danmaku(&state, &method, &path, query.as_deref()).await;
    }
    if path.starts_with("/api/providers/settings") {
        return handle_provider_settings(&state, method, &path, body).await;
    }
    if path.starts_with("/api/providers/accounts") {
        return handle_provider_accounts(&state, method, &path, body).await;
    }
    if path.starts_with("/api/providers/runtime") {
        return handle_provider_runtime(&state, &method, &path);
    }
    if path.starts_with("/api/providers/tracking") {
        return handle_provider_tracking(&state, method, &path, body).await;
    }
    if path.starts_with("/api/providers/search") {
        return handle_provider_search(&state, &method, &path, query.as_deref()).await;
    }
    if path.starts_with("/api/providers/dandanplay/resolve") {
        return handle_dandanplay_resolve(&state, &method, &path, query.as_deref()).await;
    }
    if path.starts_with("/api/providers/dandanplay/search") {
        return handle_dandanplay_search(&state, &method, &path, query.as_deref()).await;
    }
    if path.starts_with("/api/providers/dandanplay/bangumi") {
        return handle_dandanplay_bangumi(&state, &method, &path, query.as_deref()).await;
    }
    if path.starts_with("/media/") {
        return handle_media(&state, &method, &path, &headers).await;
    }
    if path.starts_with("/subtitles/") {
        return handle_static_mapped_file(
            &state.library().subtitle_files_by_id,
            "/subtitles/",
            "no-store",
            &method,
            &path,
        )
        .await;
    }
    if path.starts_with("/posters/") {
        return handle_poster(&state, &method, &path).await;
    }
    if path.starts_with("/web") {
        return handle_web_asset(&state, &method, &path, &headers).await;
    }
    if let Some((hook_path, token)) = state
        .authenticated_post_hooks
        .iter()
        .find(|(hook_path, _)| path.starts_with(hook_path.as_str()))
    {
        return handle_authenticated_post_hook(hook_path, token, &method, &headers);
    }

    empty_status(StatusCode::NOT_FOUND)
}

async fn handle_library_organize(
    state: &HttpServerState,
    peer: Option<SocketAddr>,
    method: Method,
    path: &str,
    body: Body,
) -> Response<Body> {
    if state.provider_admin.is_none() {
        return empty_status(StatusCode::NOT_FOUND);
    }
    if !peer.is_some_and(|peer| peer.ip().is_loopback()) {
        return text_response(
            StatusCode::FORBIDDEN,
            "Library organization is available only from the desktop app on this PC.",
        );
    }
    let Some(organizer) = state.organizer.as_ref().map(Arc::clone) else {
        return empty_status(StatusCode::NOT_FOUND);
    };
    match (method, path) {
        (Method::GET, "/api/library/organize/status") => {
            json_response(StatusCode::OK, &organizer.status())
        }
        (Method::POST, "/api/library/organize/cancel") => {
            organizer.cancel();
            empty_status(StatusCode::ACCEPTED)
        }
        (Method::POST, "/api/library/organize/preview") => {
            let request = match parse_json_body::<OrganizationPreviewRequest>(body).await {
                Ok(request) => request,
                Err(response) => return response,
            };
            let library = state.library();
            let catalog_metadata = state.catalog_metadata.clone();
            match tokio::task::spawn_blocking(move || {
                let mut library = (*library).clone();
                if let Some(store) = catalog_metadata {
                    library.catalog = store.enrich_catalog(&library.catalog);
                }
                organizer.preview(&library, request)
            })
            .await
            {
                Ok(Ok(plan)) => json_response(StatusCode::OK, &plan),
                Ok(Err(error)) => text_response(StatusCode::CONFLICT, &error.to_string()),
                Err(error) => text_response(StatusCode::INTERNAL_SERVER_ERROR, &error.to_string()),
            }
        }
        (Method::POST, "/api/library/organize/execute") => {
            let request = match parse_json_body::<OrganizationExecuteRequest>(body).await {
                Ok(request) => request,
                Err(response) => return response,
            };
            if !state.try_start_organization() {
                return text_response(
                    StatusCode::CONFLICT,
                    "Wait for the current library operation to finish.",
                );
            }
            let batch_id = request.batch_id.clone();
            let prepared = match organizer.prepare_execute(&state.library().catalog, request) {
                Ok(prepared) => prepared,
                Err(error) => {
                    state.finish_organization();
                    return text_response(StatusCode::CONFLICT, &error.to_string());
                }
            };
            spawn_organization(state.clone(), organizer, prepared);
            json_response(
                StatusCode::ACCEPTED,
                &OrganizationAccepted {
                    batch_id,
                    status: "ACCEPTED",
                },
            )
        }
        (Method::POST, "/api/library/organize/undo") => {
            let request = match parse_json_body::<OrganizationUndoRequest>(body).await {
                Ok(request) => request,
                Err(response) => return response,
            };
            if !state.try_start_organization() {
                return text_response(
                    StatusCode::CONFLICT,
                    "Wait for the current library operation to finish.",
                );
            }
            let prepared = match organizer.prepare_undo(&request.completed_batch_id) {
                Ok(prepared) => prepared,
                Err(error) => {
                    state.finish_organization();
                    return text_response(StatusCode::CONFLICT, &error.to_string());
                }
            };
            let batch_id = format!("undo-{}", request.completed_batch_id);
            spawn_organization(state.clone(), organizer, prepared);
            json_response(
                StatusCode::ACCEPTED,
                &OrganizationAccepted {
                    batch_id,
                    status: "ACCEPTED",
                },
            )
        }
        (_, "/api/library/organize/status") => empty_status(StatusCode::METHOD_NOT_ALLOWED),
        (_, "/api/library/organize/cancel")
        | (_, "/api/library/organize/preview")
        | (_, "/api/library/organize/execute")
        | (_, "/api/library/organize/undo") => empty_status(StatusCode::METHOD_NOT_ALLOWED),
        _ => empty_status(StatusCode::NOT_FOUND),
    }
}

async fn parse_json_body<T: for<'de> Deserialize<'de>>(
    body: Body,
) -> std::result::Result<T, Response<Body>> {
    let bytes = to_bytes(body, 1024 * 1024)
        .await
        .map_err(|error| text_response(StatusCode::BAD_REQUEST, &error.to_string()))?;
    serde_json::from_slice(&bytes)
        .map_err(|error| text_response(StatusCode::BAD_REQUEST, &error.to_string()))
}

fn spawn_organization(
    state: HttpServerState,
    organizer: Arc<LibraryOrganizer>,
    prepared: crate::organizer::PreparedOrganization,
) {
    tokio::task::spawn_blocking(move || {
        match organizer.execute(prepared) {
            Ok(library) => state.publish_library(library),
            Err(error) => eprintln!("library organization failed: {error}"),
        }
        state.finish_organization();
    });
}

fn handle_server_status(state: &HttpServerState, method: &Method) -> Response<Body> {
    if method != Method::GET {
        return empty_status(StatusCode::METHOD_NOT_ALLOWED);
    }
    let mut status = state.status.clone();
    if let Some(admin) = &state.provider_admin {
        status.provider_settings = Some(admin.provider_settings());
    }
    if state.scanning.load(Ordering::Relaxed) {
        status.scanning = true;
        status.scan_files_seen = Some(state.scan_progress.media_files_seen());
    }
    status.scan_error = state.scan_error();
    json_response(StatusCode::OK, &status)
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct LibraryRescanRequest {
    #[serde(default)]
    path: Vec<String>,
}

async fn handle_library_rescan(
    state: &HttpServerState,
    method: Method,
    body: Body,
) -> Response<Body> {
    if method != Method::POST {
        return empty_status(StatusCode::METHOD_NOT_ALLOWED);
    }
    let Some(config) = state.library_scan.as_ref().map(Arc::clone) else {
        return empty_status(StatusCode::NOT_FOUND);
    };
    let bytes = match to_bytes(body, 64 * 1024).await {
        Ok(bytes) => bytes,
        Err(error) => return text_response(StatusCode::BAD_REQUEST, &error.to_string()),
    };
    let request = match serde_json::from_slice::<LibraryRescanRequest>(&bytes) {
        Ok(request) => request,
        Err(error) => return text_response(StatusCode::BAD_REQUEST, &error.to_string()),
    };
    let target = match resolve_rescan_target(&config.roots, &request.path) {
        Ok(target) => target,
        Err(error) => return text_response(StatusCode::BAD_REQUEST, &error.to_string()),
    };
    if !state.try_start_scan() {
        return text_response(
            StatusCode::CONFLICT,
            "A library operation is already running.",
        );
    }

    let scan_state = state.clone();
    tokio::task::spawn_blocking(move || {
        let result = rescan_and_publish(&scan_state, &config, &target);
        let error = result.err().map(|error| error.to_string());
        if let Some(error) = &error {
            eprintln!("catalog rescan failed: {error}");
        }
        scan_state.finish_scan(error);
    });
    empty_status(StatusCode::ACCEPTED)
}

fn rescan_and_publish(
    state: &HttpServerState,
    config: &LibraryScanConfig,
    target: &LibraryRescanTarget,
) -> crate::Result<()> {
    let previous = config.catalog_store.load()?;
    let progress = state.scan_progress();
    let scan =
        rescan_target_with_progress(&config.roots, target, previous.as_ref(), Some(&progress))?;
    let summary = CatalogScanSummary::from(&scan);
    let stored = config.catalog_store.save_scan(scan)?;
    state.publish_library(stored.published_library);
    println!("{}", summary.to_log_line());
    Ok(())
}

fn handle_catalog(state: &HttpServerState, method: &Method) -> Response<Body> {
    if method != Method::GET {
        return empty_status(StatusCode::METHOD_NOT_ALLOWED);
    }
    // Merge dandanplay-recognized anime identities onto items lacking provider
    // metadata so clients auto-group episodes under the matched anime.
    let library = state.library();
    let Some(store) = &state.catalog_metadata else {
        return json_response(StatusCode::OK, &library.catalog);
    };
    let enriched = store.enrich_catalog(&library.catalog);
    // Best-effort retry for items that were recognized but never got a
    // poster cached — the local server can be hard-killed (the native player
    // stops its managed sidecar with a process kill, not a graceful signal)
    // mid-download, so a one-shot fetch on recognition alone can be lost with
    // no other retry. Retrying here piggybacks on every catalog read instead.
    for item in &enriched.items {
        if item.poster_path.is_none()
            && let Some(metadata) = &item.anime_metadata
        {
            ensure_poster_resolved(
                state,
                &item.id,
                metadata.image_url.clone(),
                Some(metadata.display_title.clone()),
            );
        }
    }
    json_response(StatusCode::OK, &enriched)
}

fn handle_library_attention(state: &HttpServerState, method: &Method) -> Response<Body> {
    if method != Method::GET {
        return empty_status(StatusCode::METHOD_NOT_ALLOWED);
    }
    let library = state.library();
    let catalog = state.catalog_metadata.as_ref().map_or_else(
        || library.catalog.clone(),
        |store| store.enrich_catalog(&library.catalog),
    );
    let resolver = state.dandanplay_resolver();
    let failures = state
        .provider_admin
        .as_ref()
        .map(|admin| admin.attention_failures());
    json_response(
        StatusCode::OK,
        &build_attention_document(
            &catalog,
            resolver.as_deref(),
            state.catalog_metadata.as_deref(),
            failures,
        ),
    )
}

fn clear_attention_failure(state: &HttpServerState, media_id: &str) {
    if let Some(admin) = &state.provider_admin {
        let _ = admin.attention_failures().clear(media_id);
    }
}

fn record_attention_failure(state: &HttpServerState, media_id: &str) {
    if let Some(admin) = &state.provider_admin {
        let _ = admin.attention_failures().record_refresh_failure(media_id);
    }
}

/// Records the recognized dandanplay identity from a resolve result so the
/// catalog can categorize the item on the next `/api/library` read. Best-effort:
/// a persistence failure must not fail the danmaku response.
fn record_recognized_identity(
    state: &HttpServerState,
    media_id: &str,
    result: &DandanplayResolveResult,
) {
    let Some(store) = &state.catalog_metadata else {
        return;
    };
    let Some(track) = result.selected_track.as_ref() else {
        return;
    };
    let candidate = &track.match_candidate;
    let (Some(anime_id), Some(anime_title)) = (candidate.anime_id, candidate.anime_title.clone())
    else {
        return;
    };
    if let Err(error) = store.record_with_episode(
        media_id,
        anime_id,
        anime_title.clone(),
        candidate.episode_title.clone(),
        Some(candidate.episode_id),
    ) {
        eprintln!("failed to record catalog metadata for {media_id}: {error}");
        return;
    }
    // Dandanplay matches never carry a poster image (see `ensure_poster_resolved`'s
    // external-provider fallback); always attempt one here regardless of whether
    // this call changed the recorded identity; a no-op when a poster already
    // exists or another attempt is already in flight.
    ensure_poster_resolved(state, media_id, None, Some(anime_title));
}

/// Best-effort background fetch: caches a poster image for a recognized
/// item, either from `image_url_hint` (already known, e.g. from provider
/// metadata) or by searching the configured external providers by
/// `anime_title`. Deduplicated per media ID via `poster_resolution_in_flight`
/// so repeated retries (see `handle_catalog`) don't pile up redundant
/// requests while one is already running. Fire-and-forget (spawned, not
/// awaited) so the caller is never delayed by an external search or download.
fn ensure_poster_resolved(
    state: &HttpServerState,
    media_id: &str,
    image_url_hint: Option<String>,
    anime_title: Option<String>,
) {
    if image_url_hint
        .as_deref()
        .unwrap_or_default()
        .trim()
        .is_empty()
        && anime_title.as_deref().unwrap_or_default().trim().is_empty()
    {
        return;
    }
    let (Some(catalog_metadata), Some(poster_cache)) =
        (state.catalog_metadata.clone(), state.poster_cache.clone())
    else {
        return;
    };
    let media_id = media_id.to_owned();
    {
        let mut in_flight = state
            .poster_resolution_in_flight
            .lock()
            .expect("poster in-flight lock should not be poisoned");
        if !in_flight.insert(media_id.clone()) {
            return;
        }
    }
    let in_flight_set = Arc::clone(&state.poster_resolution_in_flight);
    let provider_service = state.external_provider_service();
    tokio::spawn(async move {
        resolve_and_cache_poster(
            &catalog_metadata,
            &poster_cache,
            provider_service.as_deref(),
            &media_id,
            image_url_hint,
            anime_title,
        )
        .await;
        in_flight_set
            .lock()
            .expect("poster in-flight lock should not be poisoned")
            .remove(&media_id);
    });
}

async fn resolve_and_cache_poster(
    catalog_metadata: &CatalogMetadataStore,
    poster_cache: &Arc<PosterCacheStore>,
    provider_service: Option<&ExternalProviderService>,
    media_id: &str,
    image_url_hint: Option<String>,
    anime_title: Option<String>,
) {
    let image_url = match image_url_hint.filter(|url| !url.trim().is_empty()) {
        Some(url) => Some(url),
        None => {
            let (Some(provider_service), Some(anime_title)) = (provider_service, anime_title)
            else {
                return;
            };
            let query = ExternalAnimeMatchQuery {
                title: anime_title,
                alternate_titles: Vec::new(),
                episode_count: None,
                start_year: None,
            };
            provider_service
                .search(query, BTreeSet::new(), 1)
                .await
                .into_iter()
                .find_map(|candidate| candidate.anime.image_url)
        }
    };
    let Some(image_url) = image_url else {
        return;
    };
    let cache = Arc::clone(poster_cache);
    let cached_path = tokio::task::spawn_blocking(move || cache.resolve(&image_url))
        .await
        .ok()
        .flatten();
    if let Some(path) = cached_path
        && let Err(error) = catalog_metadata.record_poster(media_id, path)
    {
        eprintln!("failed to record poster for {media_id}: {error}");
    }
}

async fn handle_progress(
    state: &HttpServerState,
    method: Method,
    path: &str,
    body: Body,
) -> Response<Body> {
    if path == "/api/progress" {
        return handle_progress_list_exact(state, &method, path);
    }
    if method != Method::GET && method != Method::PUT {
        return empty_status(StatusCode::METHOD_NOT_ALLOWED);
    }

    let library = state.library();
    let media_id = path
        .strip_prefix("/api/progress/")
        .filter(|suffix| !suffix.is_empty())
        .and_then(url_decode)
        .filter(|id| library.catalog.items.iter().any(|item| item.id == *id));
    let Some(media_id) = media_id else {
        return empty_status(StatusCode::NOT_FOUND);
    };

    if method == Method::GET {
        return match state.progress_store.load_progress(&media_id) {
            Some(progress) => json_response(StatusCode::OK, &progress),
            None => empty_status(StatusCode::NOT_FOUND),
        };
    }

    let Ok(bytes) = axum::body::to_bytes(body, 1_048_576).await else {
        return empty_status(StatusCode::BAD_REQUEST);
    };
    let progress = serde_json::from_slice::<PlaybackProgress>(&bytes).ok();
    let Some(progress) = progress.filter(|progress| progress.media_id == media_id) else {
        return empty_status(StatusCode::BAD_REQUEST);
    };
    match state.progress_store.save_progress(progress) {
        Ok(()) => response_with_headers(StatusCode::NO_CONTENT, HeaderMap::new(), Body::empty()),
        Err(_) => empty_status(StatusCode::INTERNAL_SERVER_ERROR),
    }
}

fn handle_progress_list_exact(
    state: &HttpServerState,
    method: &Method,
    path: &str,
) -> Response<Body> {
    if path != "/api/progress" {
        return empty_status(StatusCode::NOT_FOUND);
    }
    if method != Method::GET {
        return empty_status(StatusCode::METHOD_NOT_ALLOWED);
    }
    let library = state.library();
    let published_ids = library
        .catalog
        .items
        .iter()
        .map(|item| item.id.as_str())
        .collect::<BTreeSet<_>>();
    let progress = state
        .progress_store
        .load_all_progress()
        .into_iter()
        .filter(|progress| published_ids.contains(progress.media_id.as_str()))
        .collect::<Vec<_>>();
    json_response(StatusCode::OK, &progress)
}

async fn handle_danmaku(
    state: &HttpServerState,
    method: &Method,
    path: &str,
    query: Option<&str>,
) -> Response<Body> {
    if method != Method::GET {
        return empty_status(StatusCode::METHOD_NOT_ALLOWED);
    }
    let library = state.library();
    let media_id = path
        .strip_prefix("/api/danmaku/")
        .filter(|suffix| !suffix.is_empty())
        .and_then(url_decode)
        .filter(|id| library.catalog.items.iter().any(|item| item.id == *id));
    let Some(media_id) = media_id else {
        return empty_status(StatusCode::NOT_FOUND);
    };
    let Some(path) = library.files_by_id.get(&media_id) else {
        return empty_status(StatusCode::NOT_FOUND);
    };
    if !path.is_file() {
        return empty_status(StatusCode::NOT_FOUND);
    }

    let Some(resolver) = state.dandanplay_resolver() else {
        return json_response(StatusCode::OK, &LanDanmakuTrack::unavailable(media_id));
    };
    let force_refresh = parse_query_parameters(query)
        .get("forceRefresh")
        .is_some_and(|value| value.eq_ignore_ascii_case("true"));
    let track = match resolver
        .resolve(&media_id, path, None, true, force_refresh)
        .await
    {
        Ok(result) => {
            clear_attention_failure(state, &media_id);
            record_recognized_identity(state, &media_id, &result);
            LanDanmakuTrack::from_resolve_result(media_id, result)
        }
        Err(error) => {
            record_attention_failure(state, &media_id);
            LanDanmakuTrack::failed(media_id, error)
        }
    };
    json_response(StatusCode::OK, &track)
}

mod providers;
use providers::*;
mod assets;
use assets::*;
#[derive(Debug, Clone)]
struct StaticWebAssets {
    normalized_root: PathBuf,
    path_prefix: String,
    index_file_name: String,
    index_file_path: PathBuf,
}

impl StaticWebAssets {
    fn new(root: PathBuf) -> Self {
        let normalized_root = normalize_lexically(&root);
        let index_file_name = "index.html".to_owned();
        let index_file_path = normalized_root.join(&index_file_name);
        Self {
            normalized_root,
            path_prefix: "/web".to_owned(),
            index_file_name,
            index_file_path,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
struct LanLibraryServerStatus {
    #[serde(skip_serializing_if = "is_default_app_name")]
    app_name: String,
    #[serde(skip_serializing_if = "is_default_api_version")]
    api_version: u8,
    #[serde(skip_serializing_if = "is_true")]
    media_streaming: bool,
    #[serde(skip_serializing_if = "is_true")]
    progress_sync: bool,
    #[serde(skip_serializing_if = "is_false")]
    trusted_device_management: bool,
    #[serde(skip_serializing_if = "is_false")]
    web_ui_available: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    web_ui_path: Option<String>,
    host_mode: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    provider_settings: Option<LanProviderSettingsStatus>,
    /// True while a background catalog scan is indexing the library roots;
    /// omitted once the published catalog is current.
    #[serde(skip_serializing_if = "is_false")]
    scanning: bool,
    /// Media files discovered so far by the in-flight scan; only present
    /// while `scanning` is true.
    #[serde(skip_serializing_if = "Option::is_none")]
    scan_files_seen: Option<u64>,
    /// Most recent background scan failure. Cleared when a new scan starts.
    #[serde(skip_serializing_if = "Option::is_none")]
    scan_error: Option<String>,
}

impl Default for LanLibraryServerStatus {
    fn default() -> Self {
        Self {
            app_name: "Danmaku".to_owned(),
            api_version: 1,
            media_streaming: true,
            progress_sync: true,
            trusted_device_management: false,
            web_ui_available: false,
            web_ui_path: None,
            host_mode: HOST_MODE_HEADLESS_SERVER.to_owned(),
            provider_settings: None,
            scanning: false,
            scan_files_seen: None,
            scan_error: None,
        }
    }
}

#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LanProviderSettingsStatus {
    #[serde(skip_serializing_if = "LanDandanplayProviderStatus::is_default")]
    dandanplay: LanDandanplayProviderStatus,
    #[serde(skip_serializing_if = "LanExternalAnimeProviderStatus::is_default")]
    external_anime: LanExternalAnimeProviderStatus,
}

impl From<&HeadlessServerSettings> for LanProviderSettingsStatus {
    fn from(settings: &HeadlessServerSettings) -> Self {
        Self {
            dandanplay: LanDandanplayProviderStatus {
                base_url: Some(settings.dandanplay.base_url.clone()),
                app_id: settings.dandanplay.app_id.clone(),
                has_app_secret: settings.dandanplay.has_app_secret,
                authentication_mode: Some(
                    settings
                        .dandanplay
                        .authentication_mode
                        .wire_name()
                        .to_owned(),
                ),
                cache_max_age_days: Some(settings.dandanplay.cache_max_age_days),
            },
            external_anime: LanExternalAnimeProviderStatus {
                my_anime_list_client_id: settings.external_anime.my_anime_list_client_id.clone(),
                has_my_anime_list_client_secret: settings
                    .external_anime
                    .has_my_anime_list_client_secret,
                has_my_anime_list_access_token: settings
                    .external_anime
                    .has_my_anime_list_access_token,
                bangumi_base_url: Some(settings.external_anime.bangumi_base_url.clone()),
                bangumi_user_agent: Some(settings.external_anime.bangumi_user_agent.clone()),
                has_bangumi_access_token: settings.external_anime.has_bangumi_access_token,
            },
        }
    }
}

#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
struct LanDandanplayProviderStatus {
    #[serde(skip_serializing_if = "Option::is_none")]
    base_url: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    app_id: Option<String>,
    #[serde(skip_serializing_if = "is_false")]
    has_app_secret: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    authentication_mode: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    cache_max_age_days: Option<u32>,
}

impl LanDandanplayProviderStatus {
    fn is_default(value: &Self) -> bool {
        value == &Self::default()
    }
}

#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
struct LanExternalAnimeProviderStatus {
    #[serde(skip_serializing_if = "Option::is_none")]
    my_anime_list_client_id: Option<String>,
    #[serde(skip_serializing_if = "is_false")]
    has_my_anime_list_client_secret: bool,
    #[serde(skip_serializing_if = "is_false")]
    has_my_anime_list_access_token: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    bangumi_base_url: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    bangumi_user_agent: Option<String>,
    #[serde(skip_serializing_if = "is_false")]
    has_bangumi_access_token: bool,
}

impl LanExternalAnimeProviderStatus {
    fn is_default(value: &Self) -> bool {
        value == &Self::default()
    }
}

fn is_default_app_name(value: &String) -> bool {
    value == "Danmaku"
}

fn is_default_api_version(value: &u8) -> bool {
    *value == 1
}

fn is_false(value: &bool) -> bool {
    !*value
}

fn is_true(value: &bool) -> bool {
    *value
}

#[cfg(test)]
mod tests;
