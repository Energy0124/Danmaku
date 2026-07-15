use std::collections::{BTreeMap, BTreeSet};
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex, RwLock};

use axum::Router;
use axum::body::Body;
use axum::extract::State;
use axum::http::header::{
    ACCEPT, ACCEPT_RANGES, AUTHORIZATION, CACHE_CONTROL, CONTENT_LENGTH, CONTENT_RANGE,
    CONTENT_TYPE, HeaderValue, LOCATION,
};
use axum::http::{HeaderMap, Method, Request, Response, StatusCode};
use axum::routing::any;
use serde::{Deserialize, Serialize};
use tokio::io::{AsyncReadExt, AsyncSeekExt};
use tokio_util::io::ReaderStream;

use crate::catalog::{PublishedLibrary, normalize_lexically};
use crate::catalog_metadata::CatalogMetadataStore;
use crate::dandanplay::{
    DandanplayResolveResult, DandanplayResolver, LanDanmakuTrack, apply_dandanplay_local_defaults,
};
use crate::domain::PlaybackProgress;
use crate::external_provider::{
    ExternalAnimeListEntry, ExternalAnimeMatchQuery, ExternalAnimeTrackingUpdate,
    ExternalProviderError, ExternalProviderService, parse_provider_alias, provider_runtime_status,
};
use crate::poster_cache::PosterCacheStore;
use crate::progress::PlaybackProgressStore;
use crate::provider_secrets::{ProviderSecretStore, ProviderSecrets};
use crate::settings::{
    HeadlessDandanplayAuthenticationMode, HeadlessServerSettings, SettingsStore,
    apply_external_anime_local_defaults, is_http_base_url, is_https_base_url,
};

const WEBHOOK_TOKEN_HEADER: &str = "X-Danmaku-Webhook-Token";
const HOST_MODE_EMBEDDED_DESKTOP: &str = "embedded-desktop";
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
    fn fixture_embedded(web_assets_root: PathBuf) -> Self {
        Self {
            web_assets_root: Some(web_assets_root),
            host_mode: HOST_MODE_EMBEDDED_DESKTOP.to_owned(),
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

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ProviderSettingsUpdate {
    dandanplay: DandanplaySettingsUpdate,
    external_anime: ExternalAnimeSettingsUpdate,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DandanplaySettingsUpdate {
    base_url: String,
    #[serde(default)]
    app_id: Option<String>,
    #[serde(default)]
    app_secret: Option<String>,
    #[serde(default)]
    clear_app_secret: bool,
    authentication_mode: String,
    cache_max_age_days: u32,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ExternalAnimeSettingsUpdate {
    #[serde(default)]
    my_anime_list_client_id: Option<String>,
    #[serde(default)]
    my_anime_list_client_secret: Option<String>,
    #[serde(default)]
    clear_my_anime_list_client_secret: bool,
    #[serde(default)]
    my_anime_list_access_token: Option<String>,
    #[serde(default)]
    clear_my_anime_list_access_token: bool,
    bangumi_base_url: String,
    bangumi_user_agent: String,
    #[serde(default)]
    bangumi_access_token: Option<String>,
    #[serde(default)]
    clear_bangumi_access_token: bool,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct ProviderSettingsAdminResponse {
    settings: LanProviderSettingsStatus,
    runtime: crate::external_provider::LanProviderRuntimeStatus,
}

#[derive(Debug)]
struct ProviderRuntimeResources {
    settings: LanProviderSettingsStatus,
    runtime: crate::external_provider::LanProviderRuntimeStatus,
    external_provider_service: Arc<ExternalProviderService>,
    dandanplay_resolver: Option<Arc<DandanplayResolver>>,
}

impl ProviderRuntimeResources {
    fn from_settings(
        settings: &HeadlessServerSettings,
        dandanplay_resolver: Option<Arc<DandanplayResolver>>,
    ) -> Self {
        Self {
            settings: LanProviderSettingsStatus::from(settings),
            runtime: provider_runtime_status(settings),
            external_provider_service: Arc::new(ExternalProviderService::from_settings(settings)),
            dandanplay_resolver,
        }
    }
}

#[derive(Debug)]
pub struct ProviderAdminState {
    expected_token: Vec<u8>,
    data_directory: PathBuf,
    settings_store: SettingsStore,
    secret_store: ProviderSecretStore,
    persisted_settings: Mutex<HeadlessServerSettings>,
    runtime: RwLock<ProviderRuntimeResources>,
}

impl ProviderAdminState {
    pub fn new(
        data_directory: PathBuf,
        persisted_settings: HeadlessServerSettings,
        effective_settings: HeadlessServerSettings,
        dandanplay_resolver: Option<Arc<DandanplayResolver>>,
    ) -> Self {
        let secret_store =
            ProviderSecretStore::platform(data_directory.join("provider-secrets.json"));
        Self::with_secret_store(
            data_directory,
            persisted_settings,
            effective_settings,
            dandanplay_resolver,
            secret_store,
        )
    }

    fn with_secret_store(
        data_directory: PathBuf,
        persisted_settings: HeadlessServerSettings,
        effective_settings: HeadlessServerSettings,
        dandanplay_resolver: Option<Arc<DandanplayResolver>>,
        secret_store: ProviderSecretStore,
    ) -> Self {
        Self {
            expected_token: persisted_settings.pairing_token.as_bytes().to_vec(),
            settings_store: SettingsStore::new(data_directory.join("server-settings.json")),
            secret_store,
            data_directory,
            persisted_settings: Mutex::new(persisted_settings),
            runtime: RwLock::new(ProviderRuntimeResources::from_settings(
                &effective_settings,
                dandanplay_resolver,
            )),
        }
    }

    #[cfg(test)]
    fn new_for_tests(
        data_directory: PathBuf,
        persisted_settings: HeadlessServerSettings,
        effective_settings: HeadlessServerSettings,
        secret_store: ProviderSecretStore,
    ) -> Self {
        Self::with_secret_store(
            data_directory,
            persisted_settings,
            effective_settings,
            None,
            secret_store,
        )
    }

    fn is_authorized(&self, headers: &HeaderMap) -> bool {
        let supplied = headers
            .get(AUTHORIZATION)
            .and_then(|value| value.to_str().ok())
            .and_then(|value| value.strip_prefix("Bearer "))
            .map(str::as_bytes);
        constant_time_eq(&self.expected_token, supplied)
    }

    fn snapshot(&self) -> ProviderSettingsAdminResponse {
        let runtime = self
            .runtime
            .read()
            .expect("provider runtime lock should not be poisoned");
        ProviderSettingsAdminResponse {
            settings: runtime.settings.clone(),
            runtime: runtime.runtime.clone(),
        }
    }

    fn provider_settings(&self) -> LanProviderSettingsStatus {
        self.runtime
            .read()
            .expect("provider runtime lock should not be poisoned")
            .settings
            .clone()
    }

    fn runtime_status(&self) -> crate::external_provider::LanProviderRuntimeStatus {
        self.runtime
            .read()
            .expect("provider runtime lock should not be poisoned")
            .runtime
            .clone()
    }

    fn external_provider_service(&self) -> Arc<ExternalProviderService> {
        Arc::clone(
            &self
                .runtime
                .read()
                .expect("provider runtime lock should not be poisoned")
                .external_provider_service,
        )
    }

    fn dandanplay_resolver(&self) -> Option<Arc<DandanplayResolver>> {
        self.runtime
            .read()
            .expect("provider runtime lock should not be poisoned")
            .dandanplay_resolver
            .clone()
    }

    fn update(
        &self,
        update: ProviderSettingsUpdate,
    ) -> crate::Result<ProviderSettingsAdminResponse> {
        let mut persisted = self
            .persisted_settings
            .lock()
            .map_err(|_| crate::LibraryServerError::new("provider settings lock is unavailable"))?;
        let mut next = persisted.clone();
        apply_provider_settings_update(&mut next, update)?;

        let previous_secrets = ProviderSecrets::from_settings(&persisted);
        let next_secrets = ProviderSecrets::from_settings(&next);
        self.secret_store.save(&next_secrets)?;
        if let Err(error) = self.settings_store.save(&next) {
            let _ = self.secret_store.save(&previous_secrets);
            return Err(error);
        }

        let effective =
            apply_external_anime_local_defaults(apply_dandanplay_local_defaults(next.clone()));
        let resolver =
            DandanplayResolver::from_settings(&effective, &self.data_directory).map(Arc::new);
        let resources = ProviderRuntimeResources::from_settings(&effective, resolver);
        let response = ProviderSettingsAdminResponse {
            settings: resources.settings.clone(),
            runtime: resources.runtime.clone(),
        };
        *self.runtime.write().map_err(|_| {
            crate::LibraryServerError::new("provider runtime lock is unavailable")
        })? = resources;
        *persisted = next;
        Ok(response)
    }
}

fn apply_provider_settings_update(
    settings: &mut HeadlessServerSettings,
    update: ProviderSettingsUpdate,
) -> crate::Result<()> {
    if !is_http_base_url(&update.dandanplay.base_url) || update.dandanplay.base_url.len() > 2_048 {
        return Err(crate::LibraryServerError::new(
            "dandanplay baseUrl must be a valid HTTP(S) URL",
        ));
    }
    if update.dandanplay.cache_max_age_days == 0 {
        return Err(crate::LibraryServerError::new(
            "dandanplay cacheMaxAgeDays must be at least 1",
        ));
    }
    let authentication_mode = match update
        .dandanplay
        .authentication_mode
        .trim()
        .to_ascii_uppercase()
        .as_str()
    {
        "SIGNED" => HeadlessDandanplayAuthenticationMode::Signed,
        "CREDENTIAL" => HeadlessDandanplayAuthenticationMode::Credential,
        _ => {
            return Err(crate::LibraryServerError::new(
                "dandanplay authenticationMode must be SIGNED or CREDENTIAL",
            ));
        }
    };
    settings.dandanplay.base_url = update.dandanplay.base_url.trim().to_owned();
    settings.dandanplay.app_id =
        normalized_optional(update.dandanplay.app_id, 512, "dandanplay appId")?;
    settings.dandanplay.authentication_mode = authentication_mode;
    settings.dandanplay.cache_max_age_days = update.dandanplay.cache_max_age_days;
    apply_secret_update(
        &mut settings.dandanplay.app_secret,
        update.dandanplay.app_secret,
        update.dandanplay.clear_app_secret,
        "dandanplay appSecret",
    )?;
    settings.dandanplay.has_app_secret = settings.dandanplay.app_secret.is_some();

    if !is_https_base_url(&update.external_anime.bangumi_base_url)
        || update.external_anime.bangumi_base_url.len() > 2_048
    {
        return Err(crate::LibraryServerError::new(
            "Bangumi baseUrl must be a valid HTTPS URL",
        ));
    }
    let bangumi_user_agent = update.external_anime.bangumi_user_agent.trim();
    if bangumi_user_agent.is_empty() || bangumi_user_agent.len() > 512 {
        return Err(crate::LibraryServerError::new(
            "Bangumi userAgent must be between 1 and 512 bytes",
        ));
    }
    settings.external_anime.my_anime_list_client_id = normalized_optional(
        update.external_anime.my_anime_list_client_id,
        512,
        "MyAnimeList clientId",
    )?;
    settings.external_anime.bangumi_base_url =
        update.external_anime.bangumi_base_url.trim().to_owned();
    settings.external_anime.bangumi_user_agent = bangumi_user_agent.to_owned();
    apply_secret_update(
        &mut settings.external_anime.my_anime_list_client_secret,
        update.external_anime.my_anime_list_client_secret,
        update.external_anime.clear_my_anime_list_client_secret,
        "MyAnimeList clientSecret",
    )?;
    apply_secret_update(
        &mut settings.external_anime.my_anime_list_access_token,
        update.external_anime.my_anime_list_access_token,
        update.external_anime.clear_my_anime_list_access_token,
        "MyAnimeList accessToken",
    )?;
    apply_secret_update(
        &mut settings.external_anime.bangumi_access_token,
        update.external_anime.bangumi_access_token,
        update.external_anime.clear_bangumi_access_token,
        "Bangumi accessToken",
    )?;
    settings.external_anime.has_my_anime_list_client_secret = settings
        .external_anime
        .my_anime_list_client_secret
        .is_some();
    settings.external_anime.has_my_anime_list_access_token =
        settings.external_anime.my_anime_list_access_token.is_some();
    settings.external_anime.has_bangumi_access_token =
        settings.external_anime.bangumi_access_token.is_some();
    Ok(())
}

fn normalized_optional(
    value: Option<String>,
    max_bytes: usize,
    label: &str,
) -> crate::Result<Option<String>> {
    let value = value.map(|value| value.trim().to_owned());
    let Some(value) = value.filter(|value| !value.is_empty()) else {
        return Ok(None);
    };
    if value.len() > max_bytes {
        return Err(crate::LibraryServerError::new(format!(
            "{label} must be no more than {max_bytes} bytes"
        )));
    }
    Ok(Some(value))
}

fn apply_secret_update(
    current: &mut Option<String>,
    replacement: Option<String>,
    clear: bool,
    label: &str,
) -> crate::Result<()> {
    if clear && replacement.is_some() {
        return Err(crate::LibraryServerError::new(format!(
            "{label} cannot be replaced and cleared in the same request"
        )));
    }
    if clear {
        *current = None;
        return Ok(());
    }
    let Some(replacement) = replacement else {
        return Ok(());
    };
    if replacement.trim().is_empty() || replacement.len() > 4_096 {
        return Err(crate::LibraryServerError::new(format!(
            "{label} must be between 1 and 4096 bytes"
        )));
    }
    *current = Some(replacement);
    Ok(())
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AuthenticatedPostHookConfig {
    pub path: String,
    pub token: String,
}

#[derive(Debug, Clone)]
pub struct HttpServerState {
    library: Arc<PublishedLibrary>,
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
            library: Arc::new(library),
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
        }
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
}

pub fn app(state: HttpServerState) -> Router {
    Router::new().fallback(any(dispatch)).with_state(state)
}

async fn dispatch(State(state): State<HttpServerState>, request: Request<Body>) -> Response<Body> {
    let (parts, body) = request.into_parts();
    let method = parts.method;
    let path = parts.uri.path().to_owned();
    let query = parts.uri.query().map(ToOwned::to_owned);
    let headers = parts.headers;

    if path.starts_with("/api/server/status") {
        return handle_server_status(&state, &method);
    }
    if path.starts_with("/api/library") {
        return handle_catalog(&state, &method);
    }
    if path == "/api/progress" || path.starts_with("/api/progress/") {
        return handle_progress(&state, method, &path, headers, body).await;
    }
    if path.starts_with("/api/progress") {
        return handle_progress_list_exact(&state, &method, &path);
    }
    if path.starts_with("/api/danmaku/") {
        return handle_danmaku(&state, &method, &path, query.as_deref()).await;
    }
    if path.starts_with("/api/providers/settings") {
        return handle_provider_settings(&state, method, &path, headers, body).await;
    }
    if path.starts_with("/api/providers/runtime") {
        return handle_provider_runtime(&state, &method, &path);
    }
    if path.starts_with("/api/providers/search") {
        return handle_provider_search(&state, &method, &path, query.as_deref()).await;
    }
    if path.starts_with("/api/providers/list/entry") {
        return handle_provider_list_entry(&state, method, &path, query.as_deref(), body).await;
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
            &state.library.subtitle_files_by_id,
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

fn handle_server_status(state: &HttpServerState, method: &Method) -> Response<Body> {
    if method != Method::GET {
        return empty_status(StatusCode::METHOD_NOT_ALLOWED);
    }
    if let Some(admin) = &state.provider_admin {
        let mut status = state.status.clone();
        status.provider_settings = Some(admin.provider_settings());
        return json_response(StatusCode::OK, &status);
    }
    json_response(StatusCode::OK, &state.status)
}

fn handle_catalog(state: &HttpServerState, method: &Method) -> Response<Body> {
    if method != Method::GET {
        return empty_status(StatusCode::METHOD_NOT_ALLOWED);
    }
    // Merge dandanplay-recognized anime identities onto items lacking provider
    // metadata so clients auto-group episodes under the matched anime.
    let Some(store) = &state.catalog_metadata else {
        return json_response(StatusCode::OK, &state.library.catalog);
    };
    let enriched = store.enrich_catalog(&state.library.catalog);
    // Best-effort retry for items that were recognized but never got a
    // poster cached — the local server can be hard-killed (the desktop host
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
    if let Err(error) = store.record(
        media_id,
        anime_id,
        anime_title.clone(),
        candidate.episode_title.clone(),
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
    _headers: HeaderMap,
    body: Body,
) -> Response<Body> {
    if path == "/api/progress" {
        return handle_progress_list_exact(state, &method, path);
    }
    if method != Method::GET && method != Method::PUT {
        return empty_status(StatusCode::METHOD_NOT_ALLOWED);
    }

    let media_id = path
        .strip_prefix("/api/progress/")
        .filter(|suffix| !suffix.is_empty())
        .and_then(url_decode)
        .filter(|id| {
            state
                .library
                .catalog
                .items
                .iter()
                .any(|item| item.id == *id)
        });
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
    let published_ids = state
        .library
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
    let media_id = path
        .strip_prefix("/api/danmaku/")
        .filter(|suffix| !suffix.is_empty())
        .and_then(url_decode)
        .filter(|id| {
            state
                .library
                .catalog
                .items
                .iter()
                .any(|item| item.id == *id)
        });
    let Some(media_id) = media_id else {
        return empty_status(StatusCode::NOT_FOUND);
    };
    let Some(path) = state.library.files_by_id.get(&media_id) else {
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
            record_recognized_identity(state, &media_id, &result);
            LanDanmakuTrack::from_resolve_result(media_id, result)
        }
        Err(error) => LanDanmakuTrack::failed(media_id, error),
    };
    json_response(StatusCode::OK, &track)
}

async fn handle_provider_settings(
    state: &HttpServerState,
    method: Method,
    path: &str,
    headers: HeaderMap,
    body: Body,
) -> Response<Body> {
    if path != "/api/providers/settings" {
        return empty_status(StatusCode::NOT_FOUND);
    }
    let Some(admin) = &state.provider_admin else {
        return empty_status(StatusCode::NOT_FOUND);
    };
    if !admin.is_authorized(&headers) {
        return empty_status(StatusCode::UNAUTHORIZED);
    }
    match method {
        Method::GET => json_response(StatusCode::OK, &admin.snapshot()),
        Method::PUT => {
            let Ok(bytes) = axum::body::to_bytes(body, 65_536).await else {
                return text_response(
                    StatusCode::BAD_REQUEST,
                    "Provider settings request body is too large.",
                );
            };
            let Ok(update) = serde_json::from_slice::<ProviderSettingsUpdate>(&bytes) else {
                return text_response(
                    StatusCode::BAD_REQUEST,
                    "Request body must be a provider settings JSON object.",
                );
            };
            match admin.update(update) {
                Ok(response) => json_response(StatusCode::OK, &response),
                Err(error) => text_response(StatusCode::BAD_REQUEST, &error.to_string()),
            }
        }
        _ => empty_status(StatusCode::METHOD_NOT_ALLOWED),
    }
}

fn handle_provider_runtime(state: &HttpServerState, method: &Method, path: &str) -> Response<Body> {
    if method != Method::GET {
        return empty_status(StatusCode::METHOD_NOT_ALLOWED);
    }
    if path != "/api/providers/runtime" {
        return empty_status(StatusCode::NOT_FOUND);
    }
    let Some(runtime_status) = state.provider_runtime_status() else {
        return empty_status(StatusCode::NOT_FOUND);
    };
    json_response(StatusCode::OK, &runtime_status)
}

async fn handle_provider_search(
    state: &HttpServerState,
    method: &Method,
    path: &str,
    query: Option<&str>,
) -> Response<Body> {
    if method != Method::GET {
        return empty_status(StatusCode::METHOD_NOT_ALLOWED);
    }
    if path != "/api/providers/search" {
        return empty_status(StatusCode::NOT_FOUND);
    }
    let Some(service) = state.external_provider_service() else {
        return json_response(StatusCode::OK, &Vec::<serde_json::Value>::new());
    };
    let query_parameters = parse_query_parameters(query);
    let Some(title) = query_parameters
        .get("title")
        .map(|value| value.trim())
        .filter(|value| !value.is_empty())
        .map(ToOwned::to_owned)
    else {
        return text_response(
            StatusCode::BAD_REQUEST,
            "Query parameter 'title' is required.",
        );
    };
    let limit = match query_parameters.get("limit") {
        Some(value) => match value
            .trim()
            .parse::<u32>()
            .ok()
            .filter(|value| (1..=50).contains(value))
        {
            Some(value) => value,
            None => {
                return text_response(
                    StatusCode::BAD_REQUEST,
                    "Query parameter 'limit' must be between 1 and 50.",
                );
            }
        },
        None => 10,
    };
    let episode_count = match query_parameters.get("episodeCount") {
        Some(value) => match value.trim().parse::<u32>().ok().filter(|value| *value > 0) {
            Some(value) => Some(value),
            None => {
                return text_response(
                    StatusCode::BAD_REQUEST,
                    "Query parameter 'episodeCount' must be positive.",
                );
            }
        },
        None => None,
    };
    let start_year = match query_parameters.get("startYear") {
        Some(value) => match value
            .trim()
            .parse::<u32>()
            .ok()
            .filter(|value| (1900..=2200).contains(value))
        {
            Some(value) => Some(value),
            None => {
                return text_response(
                    StatusCode::BAD_REQUEST,
                    "Query parameter 'startYear' must be between 1900 and 2200.",
                );
            }
        },
        None => None,
    };
    let providers = match parse_provider_filter(&query_parameters) {
        Ok(providers) => providers,
        Err(message) => return text_response(StatusCode::BAD_REQUEST, &message),
    };
    let matches = service
        .search(
            ExternalAnimeMatchQuery {
                title,
                alternate_titles: Vec::new(),
                episode_count,
                start_year,
            },
            providers,
            limit,
        )
        .await;
    json_response(StatusCode::OK, &matches)
}

async fn handle_provider_list_entry(
    state: &HttpServerState,
    method: Method,
    path: &str,
    query: Option<&str>,
    body: Body,
) -> Response<Body> {
    if path != "/api/providers/list/entry" {
        return empty_status(StatusCode::NOT_FOUND);
    }
    let Some(service) = state.external_provider_service() else {
        return text_response(
            StatusCode::CONFLICT,
            "Provider list sync credentials are not configured.",
        );
    };
    match method {
        Method::GET => handle_provider_list_read(&service, query).await,
        Method::POST => handle_provider_list_write(&service, body).await,
        _ => text_response(StatusCode::METHOD_NOT_ALLOWED, "Method not allowed."),
    }
}

async fn handle_provider_list_read(
    service: &Arc<ExternalProviderService>,
    query: Option<&str>,
) -> Response<Body> {
    let query_parameters = parse_query_parameters(query);
    let Some(anime_id) = external_anime_id_from_query(&query_parameters) else {
        return invalid_external_anime_id_response(&query_parameters);
    };
    if anime_id.provider == crate::catalog::ExternalAnimeProvider::Dandanplay {
        return text_response(
            StatusCode::BAD_REQUEST,
            "dandanplay does not support external list entries.",
        );
    }
    match service.fetch_list_entry(anime_id).await {
        Ok(Some(entry)) => provider_list_success_response(&entry),
        Ok(None) => text_response(StatusCode::NOT_FOUND, "External list entry was not found."),
        Err(error) => provider_list_error_response(error),
    }
}

async fn handle_provider_list_write(
    service: &Arc<ExternalProviderService>,
    body: Body,
) -> Response<Body> {
    let Ok(bytes) = axum::body::to_bytes(body, 1_048_576).await else {
        return text_response(
            StatusCode::BAD_REQUEST,
            "Request body must be an ExternalAnimeTrackingUpdate JSON object.",
        );
    };
    let Ok(update) = serde_json::from_slice::<ExternalAnimeTrackingUpdate>(&bytes) else {
        return text_response(
            StatusCode::BAD_REQUEST,
            "Request body must be an ExternalAnimeTrackingUpdate JSON object.",
        );
    };
    if update.anime_id.provider == crate::catalog::ExternalAnimeProvider::Dandanplay {
        return text_response(
            StatusCode::BAD_REQUEST,
            "dandanplay does not support external list entries.",
        );
    }
    match service.update_list_entry(update).await {
        Ok(entry) => provider_list_success_response(&entry),
        Err(error) => provider_list_error_response(error),
    }
}

async fn handle_dandanplay_resolve(
    state: &HttpServerState,
    method: &Method,
    path: &str,
    query: Option<&str>,
) -> Response<Body> {
    if method != Method::GET {
        return empty_status(StatusCode::METHOD_NOT_ALLOWED);
    }
    if path != "/api/providers/dandanplay/resolve" {
        return empty_status(StatusCode::NOT_FOUND);
    }
    let query = parse_query_parameters(query);
    let Some(media_id) = query
        .get("mediaId")
        .map(|value| value.trim())
        .filter(|value| !value.is_empty())
        .map(ToOwned::to_owned)
    else {
        return text_response(
            StatusCode::BAD_REQUEST,
            "Query parameter 'mediaId' is required.",
        );
    };
    let preferred_episode_id = match query.get("episodeId") {
        Some(value) => match value.trim().parse::<u64>().ok().filter(|value| *value > 0) {
            Some(value) => Some(value),
            None => {
                return text_response(
                    StatusCode::BAD_REQUEST,
                    "Query parameter 'episodeId' must be positive.",
                );
            }
        },
        None => None,
    };
    let with_related = match query.get("withRelated") {
        Some(value) => match parse_boolean_query_parameter(value) {
            Some(value) => value,
            None => {
                return text_response(
                    StatusCode::BAD_REQUEST,
                    "Query parameter 'withRelated' must be true or false.",
                );
            }
        },
        None => true,
    };
    // Selecting a specific episodeId already bypasses the single-candidate
    // cache (see `DandanplayResolver::resolve`), but listing candidates
    // (no episodeId) does not by default — a prior auto-match's cache entry
    // only remembers the one candidate it picked, not the full list, so a
    // match picker must force a fresh match to see alternatives.
    let force_refresh = match query.get("forceRefresh") {
        Some(value) => match parse_boolean_query_parameter(value) {
            Some(value) => value,
            None => {
                return text_response(
                    StatusCode::BAD_REQUEST,
                    "Query parameter 'forceRefresh' must be true or false.",
                );
            }
        },
        None => false,
    };
    let anime_id = match query.get("animeId") {
        Some(value) => match value.trim().parse::<u64>().ok().filter(|value| *value > 0) {
            Some(value) => Some(value),
            None => {
                return text_response(
                    StatusCode::BAD_REQUEST,
                    "Query parameter 'animeId' must be positive.",
                );
            }
        },
        None => None,
    };
    // An episode picked from a keyword search may not be among the hash
    // matches, so the caller passes the titles it saw; a synthesized match
    // candidate carries them through selection into the recognized-identity
    // record. animeId falls back to dandanplay's episodeId convention
    // (animeId * 10000 + episode index) when not given.
    let preferred_match = preferred_episode_id.map(|episode_id| {
        crate::dandanplay::DandanplayMatch::new(
            episode_id,
            anime_id.or_else(|| Some(episode_id / 10_000).filter(|id| *id > 0)),
            query
                .get("animeTitle")
                .map(|value| value.trim().to_owned())
                .filter(|value| !value.is_empty()),
            query
                .get("episodeTitle")
                .map(|value| value.trim().to_owned())
                .filter(|value| !value.is_empty()),
            None,
        )
    });
    let Some(path) = state.library.files_by_id.get(&media_id) else {
        return text_response(StatusCode::NOT_FOUND, "Media item was not found.");
    };
    if !path.is_file() {
        return text_response(StatusCode::NOT_FOUND, "Media file was not found.");
    }
    let Some(resolver) = state.dandanplay_resolver() else {
        return text_response(
            StatusCode::BAD_GATEWAY,
            "dandanplay request failed: Danmaku resolver is not available.",
        );
    };
    match resolver
        .resolve(
            &media_id,
            path,
            preferred_match,
            with_related,
            force_refresh,
        )
        .await
    {
        Ok(result) => {
            record_recognized_identity(state, &media_id, &result);
            json_response(StatusCode::OK, &result.to_provider_response(&media_id))
        }
        Err(error) => text_response(
            StatusCode::BAD_GATEWAY,
            &format!("dandanplay request failed: {error}"),
        ),
    }
}

/// Searches the dandanplay database by anime keyword for the manual match
/// picker, returning each anime with its full episode list.
async fn handle_dandanplay_search(
    state: &HttpServerState,
    method: &Method,
    path: &str,
    query: Option<&str>,
) -> Response<Body> {
    if method != Method::GET {
        return empty_status(StatusCode::METHOD_NOT_ALLOWED);
    }
    if path != "/api/providers/dandanplay/search" {
        return empty_status(StatusCode::NOT_FOUND);
    }
    let query = parse_query_parameters(query);
    let Some(keyword) = query
        .get("keyword")
        .map(|value| value.trim())
        .filter(|value| !value.is_empty())
        .map(ToOwned::to_owned)
    else {
        return text_response(
            StatusCode::BAD_REQUEST,
            "Query parameter 'keyword' is required.",
        );
    };
    let Some(resolver) = state.dandanplay_resolver() else {
        return text_response(
            StatusCode::BAD_GATEWAY,
            "dandanplay request failed: Danmaku resolver is not available.",
        );
    };
    match resolver.search_episodes(&keyword).await {
        Ok(animes) => json_response(StatusCode::OK, &serde_json::json!({ "animes": animes })),
        Err(error) => text_response(
            StatusCode::BAD_GATEWAY,
            &format!("dandanplay request failed: {error}"),
        ),
    }
}

/// Proxies one anime's full dandanplay bangumi profile (rating, synopsis,
/// tags, per-episode air dates, database links) for the library's anime
/// information page.
async fn handle_dandanplay_bangumi(
    state: &HttpServerState,
    method: &Method,
    path: &str,
    query: Option<&str>,
) -> Response<Body> {
    if method != Method::GET {
        return empty_status(StatusCode::METHOD_NOT_ALLOWED);
    }
    if path != "/api/providers/dandanplay/bangumi" {
        return empty_status(StatusCode::NOT_FOUND);
    }
    let query = parse_query_parameters(query);
    let Some(anime_id) = query
        .get("animeId")
        .and_then(|value| value.trim().parse::<u64>().ok())
        .filter(|value| *value > 0)
    else {
        return text_response(
            StatusCode::BAD_REQUEST,
            "Query parameter 'animeId' must be positive.",
        );
    };
    let Some(resolver) = state.dandanplay_resolver() else {
        return text_response(
            StatusCode::BAD_GATEWAY,
            "dandanplay request failed: Danmaku resolver is not available.",
        );
    };
    match resolver.bangumi_detail(anime_id).await {
        Ok(detail) => json_response(StatusCode::OK, &detail),
        Err(error) => text_response(
            StatusCode::BAD_GATEWAY,
            &format!("dandanplay request failed: {error}"),
        ),
    }
}

async fn handle_media(
    state: &HttpServerState,
    method: &Method,
    path: &str,
    headers: &HeaderMap,
) -> Response<Body> {
    if method != Method::GET && method != Method::HEAD {
        return empty_status(StatusCode::METHOD_NOT_ALLOWED);
    }
    let id = path.strip_prefix("/media/").unwrap_or_default();
    let Some(path) = state.library.files_by_id.get(id) else {
        return empty_status(StatusCode::NOT_FOUND);
    };
    let Ok(metadata) = tokio::fs::metadata(path).await else {
        return empty_status(StatusCode::NOT_FOUND);
    };
    if !metadata.is_file() {
        return empty_status(StatusCode::NOT_FOUND);
    }
    let file_size = metadata.len();
    let range_header = headers.get("range").and_then(|value| value.to_str().ok());
    let range = range_header.and_then(|header| parse_range(header, file_size));
    if headers.contains_key("range") && range.is_none() {
        let mut response_headers = HeaderMap::new();
        response_headers.insert(CONTENT_RANGE, header_value(format!("bytes */{file_size}")));
        response_headers.insert(CONTENT_LENGTH, HeaderValue::from_static("0"));
        return response_with_headers(
            StatusCode::RANGE_NOT_SATISFIABLE,
            response_headers,
            Body::empty(),
        );
    }

    let start = range.map(|range| range.0).unwrap_or(0);
    let content_length = range
        .map(|range| range.1 - range.0 + 1)
        .unwrap_or(file_size);
    let mut response_headers = HeaderMap::new();
    response_headers.insert(ACCEPT_RANGES, HeaderValue::from_static("bytes"));
    response_headers.insert(CONTENT_TYPE, header_value(content_type(path)));
    response_headers.insert(CONTENT_LENGTH, header_value(content_length.to_string()));
    let status = if let Some((range_start, range_end)) = range {
        response_headers.insert(
            CONTENT_RANGE,
            header_value(format!("bytes {range_start}-{range_end}/{file_size}")),
        );
        StatusCode::PARTIAL_CONTENT
    } else {
        StatusCode::OK
    };

    if method == Method::HEAD || content_length == 0 {
        return response_with_headers(status, response_headers, Body::empty());
    }

    let Ok(mut file) = tokio::fs::File::open(path).await else {
        return empty_status(StatusCode::NOT_FOUND);
    };
    if file.seek(std::io::SeekFrom::Start(start)).await.is_err() {
        return empty_status(StatusCode::INTERNAL_SERVER_ERROR);
    }
    let stream = ReaderStream::new(file.take(content_length));
    response_with_headers(status, response_headers, Body::from_stream(stream))
}

/// Serves `/posters/{mediaId}`, checking the scan-time static poster map
/// first and falling back to a poster cached from anime recognition (see
/// `spawn_poster_resolution`), which is not known until runtime.
async fn handle_poster(state: &HttpServerState, method: &Method, path: &str) -> Response<Body> {
    if method != Method::GET && method != Method::HEAD {
        return empty_status(StatusCode::METHOD_NOT_ALLOWED);
    }
    let id = path.strip_prefix("/posters/").unwrap_or_default();
    if let Some(file) = state.library.poster_files_by_id.get(id) {
        return serve_file(file, method, "private, max-age=3600").await;
    }
    if let Some(store) = &state.catalog_metadata
        && let Some(file) = store.poster_file(id)
    {
        return serve_file(&file, method, "private, max-age=3600").await;
    }
    empty_status(StatusCode::NOT_FOUND)
}

async fn handle_static_mapped_file(
    files_by_id: &BTreeMap<String, PathBuf>,
    prefix: &str,
    cache_control: &'static str,
    method: &Method,
    path: &str,
) -> Response<Body> {
    if method != Method::GET && method != Method::HEAD {
        return empty_status(StatusCode::METHOD_NOT_ALLOWED);
    }
    let id = path.strip_prefix(prefix).unwrap_or_default();
    let Some(path) = files_by_id.get(id) else {
        return empty_status(StatusCode::NOT_FOUND);
    };
    serve_file(path, method, cache_control).await
}

async fn handle_web_asset(
    state: &HttpServerState,
    method: &Method,
    request_path: &str,
    headers: &HeaderMap,
) -> Response<Body> {
    let Some(assets) = &state.web_assets else {
        return empty_status(StatusCode::NOT_FOUND);
    };
    if method != Method::GET && method != Method::HEAD {
        return empty_status(StatusCode::METHOD_NOT_ALLOWED);
    }

    if request_path == assets.path_prefix {
        let mut response_headers = HeaderMap::new();
        response_headers.insert(LOCATION, header_value(format!("{}/", assets.path_prefix)));
        response_headers.insert(CONTENT_LENGTH, HeaderValue::from_static("0"));
        return response_with_headers(StatusCode::FOUND, response_headers, Body::empty());
    }
    if request_path != format!("{}/", assets.path_prefix)
        && !request_path.starts_with(&format!("{}/", assets.path_prefix))
    {
        return empty_status(StatusCode::NOT_FOUND);
    }

    let Some(relative_path) = request_path
        .strip_prefix(&format!("{}/", assets.path_prefix))
        .map(|path| {
            if path.is_empty() {
                assets.index_file_name.as_str()
            } else {
                path
            }
        })
        .and_then(url_decode)
    else {
        return empty_status(StatusCode::NOT_FOUND);
    };
    let target = normalize_lexically(&assets.normalized_root.join(&relative_path));
    if !target.starts_with(&assets.normalized_root) {
        return empty_status(StatusCode::NOT_FOUND);
    }

    let file = if target.is_file() {
        Some(target)
    } else if should_serve_web_index(method, headers, &relative_path)
        && assets.index_file_path.is_file()
    {
        Some(assets.index_file_path.clone())
    } else {
        None
    };
    let Some(file) = file else {
        return empty_status(StatusCode::NOT_FOUND);
    };
    let cache_control = if file == assets.index_file_path {
        "no-store"
    } else {
        "public, max-age=3600"
    };
    serve_file(&file, method, cache_control).await
}

fn handle_authenticated_post_hook(
    _hook_path: &str,
    expected_token: &[u8],
    method: &Method,
    headers: &HeaderMap,
) -> Response<Body> {
    if method != Method::POST {
        return empty_status(StatusCode::METHOD_NOT_ALLOWED);
    }
    let supplied_token = headers
        .get(WEBHOOK_TOKEN_HEADER)
        .and_then(|value| value.to_str().ok())
        .map(str::as_bytes);
    if !constant_time_eq(expected_token, supplied_token) {
        return empty_status(StatusCode::UNAUTHORIZED);
    }
    response_with_headers(StatusCode::ACCEPTED, HeaderMap::new(), Body::empty())
}

async fn serve_file(path: &Path, method: &Method, cache_control: &'static str) -> Response<Body> {
    let Ok(metadata) = tokio::fs::metadata(path).await else {
        return empty_status(StatusCode::NOT_FOUND);
    };
    if !metadata.is_file() {
        return empty_status(StatusCode::NOT_FOUND);
    }
    let content_length = metadata.len();
    let mut response_headers = HeaderMap::new();
    response_headers.insert(CONTENT_TYPE, header_value(content_type(path)));
    response_headers.insert(CACHE_CONTROL, HeaderValue::from_static(cache_control));
    response_headers.insert(CONTENT_LENGTH, header_value(content_length.to_string()));
    if method == Method::HEAD {
        return response_with_headers(StatusCode::OK, response_headers, Body::empty());
    }

    match tokio::fs::read(path).await {
        Ok(bytes) => response_with_headers(StatusCode::OK, response_headers, Body::from(bytes)),
        Err(_) => empty_status(StatusCode::NOT_FOUND),
    }
}

fn parse_range(header: &str, file_size: u64) -> Option<(u64, u64)> {
    if !header.starts_with("bytes=") || file_size == 0 {
        return None;
    }
    let value = header.strip_prefix("bytes=")?;
    if value.contains(',') {
        return None;
    }
    let (start_text, end_text) = value.split_once('-')?;
    let (start, end_inclusive) = if start_text.is_empty() {
        let suffix_length = end_text.parse::<u64>().ok().filter(|value| *value > 0)?;
        (file_size.saturating_sub(suffix_length), file_size - 1)
    } else {
        let start = start_text.parse::<u64>().ok()?;
        let end_inclusive = if end_text.is_empty() {
            file_size - 1
        } else {
            end_text.parse::<u64>().ok()?.min(file_size - 1)
        };
        (start, end_inclusive)
    };
    if start < file_size && start <= end_inclusive {
        Some((start, end_inclusive))
    } else {
        None
    }
}

fn should_serve_web_index(method: &Method, headers: &HeaderMap, relative_path: &str) -> bool {
    method == Method::GET
        && (headers
            .get(ACCEPT)
            .and_then(|value| value.to_str().ok())
            .is_some_and(|value| value.contains("text/html"))
            || !relative_path
                .rsplit('/')
                .next()
                .unwrap_or_default()
                .contains('.'))
}

fn json_response<T: Serialize>(status: StatusCode, value: &T) -> Response<Body> {
    match serde_json::to_vec(value) {
        Ok(bytes) => {
            let mut headers = HeaderMap::new();
            headers.insert(
                CONTENT_TYPE,
                HeaderValue::from_static("application/json; charset=utf-8"),
            );
            headers.insert(CACHE_CONTROL, HeaderValue::from_static("no-store"));
            headers.insert(CONTENT_LENGTH, header_value(bytes.len().to_string()));
            response_with_headers(status, headers, Body::from(bytes))
        }
        Err(_) => empty_status(StatusCode::INTERNAL_SERVER_ERROR),
    }
}

fn text_response(status: StatusCode, value: &str) -> Response<Body> {
    let bytes = value.as_bytes().to_vec();
    let mut headers = HeaderMap::new();
    headers.insert(
        CONTENT_TYPE,
        HeaderValue::from_static("text/plain; charset=utf-8"),
    );
    headers.insert(CACHE_CONTROL, HeaderValue::from_static("no-store"));
    headers.insert(CONTENT_LENGTH, header_value(bytes.len().to_string()));
    response_with_headers(status, headers, Body::from(bytes))
}

fn empty_status(status: StatusCode) -> Response<Body> {
    let mut headers = HeaderMap::new();
    headers.insert(CONTENT_LENGTH, HeaderValue::from_static("0"));
    response_with_headers(status, headers, Body::empty())
}

fn response_with_headers(status: StatusCode, headers: HeaderMap, body: Body) -> Response<Body> {
    let mut response = Response::new(body);
    *response.status_mut() = status;
    for (name, value) in headers {
        if let Some(name) = name {
            response.headers_mut().insert(name, value);
        }
    }
    response
}

fn header_value(value: impl AsRef<str>) -> HeaderValue {
    HeaderValue::from_str(value.as_ref()).expect("generated header value should be valid")
}

fn content_type(path: &Path) -> &'static str {
    match path
        .extension()
        .and_then(|extension| extension.to_str())
        .unwrap_or_default()
        .to_ascii_lowercase()
        .as_str()
    {
        "ass" => "text/x-ass",
        "css" => "text/css; charset=utf-8",
        "gif" => "image/gif",
        "html" | "htm" => "text/html; charset=utf-8",
        "jpeg" | "jpg" => "image/jpeg",
        "js" => "text/javascript; charset=utf-8",
        "json" => "application/json; charset=utf-8",
        "m4v" | "mp4" => "video/mp4",
        "mkv" => "video/x-matroska",
        "png" => "image/png",
        "srt" => "application/x-subrip",
        "ssa" => "text/x-ssa",
        "svg" => "image/svg+xml",
        "ts" | "m2ts" => "video/mp2t",
        "vtt" => "text/vtt",
        "webm" => "video/webm",
        "webp" => "image/webp",
        _ => "application/octet-stream",
    }
}

fn url_decode(value: &str) -> Option<String> {
    let mut bytes = Vec::with_capacity(value.len());
    let mut input = value.as_bytes().iter().copied();
    while let Some(byte) = input.next() {
        match byte {
            b'+' => bytes.push(b' '),
            b'%' => {
                let high = input.next()?;
                let low = input.next()?;
                bytes.push((hex_value(high)? << 4) | hex_value(low)?);
            }
            other => bytes.push(other),
        }
    }
    String::from_utf8(bytes).ok()
}

fn parse_query_parameters(query: Option<&str>) -> BTreeMap<String, String> {
    let mut parameters = BTreeMap::new();
    let Some(query) = query else {
        return parameters;
    };
    for part in query.split('&') {
        let Some((key, value)) = part.split_once('=') else {
            continue;
        };
        if let (Some(key), Some(value)) = (url_decode(key), url_decode(value)) {
            parameters.insert(key, value);
        }
    }
    parameters
}

fn parse_boolean_query_parameter(value: &str) -> Option<bool> {
    match value.trim().to_ascii_lowercase().as_str() {
        "true" | "1" | "yes" => Some(true),
        "false" | "0" | "no" => Some(false),
        _ => None,
    }
}

fn parse_provider_filter(
    query_parameters: &BTreeMap<String, String>,
) -> std::result::Result<BTreeSet<crate::catalog::ExternalAnimeProvider>, String> {
    let Some(providers) = query_parameters.get("providers") else {
        return Ok(BTreeSet::new());
    };
    let mut parsed = BTreeSet::new();
    for provider in providers
        .split(',')
        .map(str::trim)
        .filter(|value| !value.is_empty())
    {
        let Some(provider_value) = parse_provider_alias(provider) else {
            return Err(format!("Unsupported provider '{provider}'."));
        };
        parsed.insert(provider_value);
    }
    Ok(parsed)
}

fn external_anime_id_from_query(
    query_parameters: &BTreeMap<String, String>,
) -> Option<crate::catalog::ExternalAnimeId> {
    let provider = query_parameters
        .get("provider")
        .map(|value| value.trim())
        .filter(|value| !value.is_empty())
        .and_then(parse_provider_alias)?;
    let value = query_parameters
        .get("animeId")
        .map(|value| value.trim())
        .and_then(|value| value.parse::<u64>().ok())
        .filter(|value| *value > 0)?;
    Some(crate::catalog::ExternalAnimeId { provider, value })
}

fn invalid_external_anime_id_response(
    query_parameters: &BTreeMap<String, String>,
) -> Response<Body> {
    let provider = query_parameters.get("provider").map(|value| value.trim());
    if provider.is_none_or(str::is_empty) {
        return text_response(
            StatusCode::BAD_REQUEST,
            "Query parameter 'provider' is required.",
        );
    }
    let provider = provider.expect("provider should exist");
    if parse_provider_alias(provider).is_none() {
        return text_response(
            StatusCode::BAD_REQUEST,
            &format!("Unsupported provider '{provider}'."),
        );
    }
    text_response(
        StatusCode::BAD_REQUEST,
        "Query parameter 'animeId' must be positive.",
    )
}

fn provider_list_success_response(entry: &ExternalAnimeListEntry) -> Response<Body> {
    json_response(StatusCode::OK, entry)
}

fn provider_list_error_response(error: ExternalProviderError) -> Response<Body> {
    let (status, body) = error.route_status_and_body();
    text_response(
        StatusCode::from_u16(status).unwrap_or(StatusCode::BAD_GATEWAY),
        &body,
    )
}

fn hex_value(byte: u8) -> Option<u8> {
    match byte {
        b'0'..=b'9' => Some(byte - b'0'),
        b'a'..=b'f' => Some(byte - b'a' + 10),
        b'A'..=b'F' => Some(byte - b'A' + 10),
        _ => None,
    }
}

fn constant_time_eq(expected: &[u8], supplied: Option<&[u8]>) -> bool {
    let Some(supplied) = supplied else {
        return false;
    };
    let max_len = expected.len().max(supplied.len());
    let mut diff = expected.len() ^ supplied.len();
    for index in 0..max_len {
        let left = expected.get(index).copied().unwrap_or(0);
        let right = supplied.get(index).copied().unwrap_or(0);
        diff |= usize::from(left ^ right);
    }
    diff == 0
}

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
    #[serde(skip_serializing_if = "is_false")]
    pairing_required: bool,
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
    #[serde(skip_serializing_if = "is_embedded_host_mode")]
    host_mode: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    provider_settings: Option<LanProviderSettingsStatus>,
}

impl Default for LanLibraryServerStatus {
    fn default() -> Self {
        Self {
            app_name: "Danmaku".to_owned(),
            api_version: 1,
            pairing_required: false,
            media_streaming: true,
            progress_sync: true,
            trusted_device_management: false,
            web_ui_available: false,
            web_ui_path: None,
            host_mode: HOST_MODE_EMBEDDED_DESKTOP.to_owned(),
            provider_settings: None,
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
                        .jvm_name()
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

fn is_embedded_host_mode(value: &String) -> bool {
    value == HOST_MODE_EMBEDDED_DESKTOP
}

#[cfg(test)]
mod tests {
    use std::fs;
    use std::io::{Read, Write};
    use std::net::{TcpListener, TcpStream};
    use std::path::PathBuf;
    use std::sync::Arc;
    use std::sync::Mutex;
    use std::sync::atomic::{AtomicU64, Ordering};
    use std::thread;
    use std::time::Duration;

    use axum::body::to_bytes;
    use axum::http::header::HeaderName;
    use axum::http::{HeaderValue, Request};
    use serde_json::{Value, json};
    use tower::ServiceExt;

    use super::*;
    use crate::catalog::{
        LibraryCatalog, LibraryMediaItem, LibrarySubtitleTrack, PathMap, PublishedLibrary,
    };
    use crate::dandanplay::{
        DandanplayCommentCacheStore, DandanplayConnection, DandanplayDanmakuClient,
        DandanplayResolver,
    };
    use crate::external_provider::{
        BangumiSearchClient, BangumiTrackingClient, ExternalAnimeInfo, ExternalAnimeSearchClient,
        ExternalAnimeTitleSet, ExternalProviderService, MyAnimeListSearchClient,
        MyAnimeListTrackingClient,
    };
    use crate::settings::HeadlessDandanplayAuthenticationMode;

    static TEMP_COUNTER: AtomicU64 = AtomicU64::new(0);

    #[tokio::test]
    async fn core_lan_protocol_http_fixtures_match_kotlin() {
        let fixture = FixtureEnvironment::new();
        let progress_store = Arc::new(PlaybackProgressStore::new(
            fixture.temp.join("progress.json"),
        ));
        let state = HttpServerState::new(
            fixture.library.clone(),
            progress_store,
            HttpServerConfig::fixture_embedded(fixture.web_root.clone()),
        );
        let app = app(state);

        let mut passed = 0_usize;
        for file_name in http_fixture_order() {
            let fixture = read_fixture(file_name);
            let request = request_from_fixture(&fixture);
            let response = app
                .clone()
                .oneshot(request)
                .await
                .unwrap_or_else(|error| panic!("fixture {file_name} request failed: {error}"));
            assert_response_matches_fixture(file_name, response, &fixture).await;
            passed += 1;
        }
        assert_eq!(19, passed);
    }

    #[tokio::test]
    async fn danmaku_route_resolves_ready_failed_unavailable_and_cache_paths() {
        let fixture = FixtureEnvironment::new();
        let server = MockDandanplayServer::start(MockDandanplayBehavior::default());
        let app = dandanplay_test_app(&fixture, Some(test_resolver(&fixture, &server)));

        let ready = request_json(&app, "/api/danmaku/episode-id").await;
        assert_eq!("READY", ready["status"]);
        assert_eq!("NETWORK", ready["source"]);
        assert_eq!(2, ready["comments"].as_array().expect("comments").len());
        assert_eq!(4294901760_u64, ready["comments"][1]["style"]["colorArgb"]);
        assert_eq!(1, server.count_path("/api/v2/match"));

        let cached = request_json(&app, "/api/danmaku/episode-id").await;
        assert_eq!("READY", cached["status"]);
        assert_eq!("CACHE", cached["source"]);
        assert_eq!(1, server.count_path("/api/v2/match"));

        let refreshed = request_json(&app, "/api/danmaku/episode-id?forceRefresh=true").await;
        assert_eq!("READY", refreshed["status"]);
        assert_eq!("NETWORK", refreshed["source"]);
        assert_eq!(2, server.count_path("/api/v2/match"));

        let unavailable = request_json(
            &dandanplay_test_app(&fixture, None),
            "/api/danmaku/episode-id",
        )
        .await;
        assert_eq!("UNAVAILABLE", unavailable["status"]);
        assert_eq!("Danmaku resolver is not available.", unavailable["message"]);

        let failed_server = MockDandanplayServer::start(MockDandanplayBehavior {
            match_status: 500,
            ..MockDandanplayBehavior::default()
        });
        let failed = request_json(
            &dandanplay_test_app(&fixture, Some(test_resolver(&fixture, &failed_server))),
            "/api/danmaku/episode-id",
        )
        .await;
        assert_eq!("FAILED", failed["status"]);
        assert!(
            failed["message"]
                .as_str()
                .expect("message")
                .contains("HTTP 500")
        );
    }

    #[tokio::test]
    async fn dandanplay_resolve_hook_returns_documented_status_shapes() {
        let fixture = FixtureEnvironment::new();
        let server = MockDandanplayServer::start(MockDandanplayBehavior::default());
        let app = dandanplay_test_app(&fixture, Some(test_resolver(&fixture, &server)));

        let bad = app
            .clone()
            .oneshot(get("/api/providers/dandanplay/resolve"))
            .await
            .expect("bad response");
        assert_eq!(StatusCode::BAD_REQUEST, bad.status());
        assert_text_body(bad, "Query parameter 'mediaId' is required.").await;

        let not_found = app
            .clone()
            .oneshot(get("/api/providers/dandanplay/resolve?mediaId=missing"))
            .await
            .expect("not found response");
        assert_eq!(StatusCode::NOT_FOUND, not_found.status());
        assert_text_body(not_found, "Media item was not found.").await;

        let invalid = app
            .clone()
            .oneshot(get(
                "/api/providers/dandanplay/resolve?mediaId=episode-id&withRelated=maybe",
            ))
            .await
            .expect("invalid response");
        assert_eq!(StatusCode::BAD_REQUEST, invalid.status());
        assert_text_body(
            invalid,
            "Query parameter 'withRelated' must be true or false.",
        )
        .await;

        let response = request_json(
            &app,
            "/api/providers/dandanplay/resolve?mediaId=episode-id&episodeId=222&withRelated=false",
        )
        .await;
        assert_eq!("episode-id", response["mediaId"]);
        assert_eq!(
            "danmaku-media-fixture.bin",
            response["fingerprint"]["fileName"]
        );
        assert_eq!(2, response["matches"].as_array().expect("matches").len());
        assert_eq!(222, response["selectedMatch"]["episodeId"]);
        assert_eq!(2, response["commentCount"]);
        assert_eq!("hello", response["comments"][0]["text"]);
        assert!(response["comments"][0]["style"]["colorArgb"].is_string());
        let comment_222 = server
            .requests()
            .into_iter()
            .find(|request| request.path == "/api/v2/comment/222")
            .expect("preferred comment request");
        assert_eq!(None, comment_222.query);

        let failed_server = MockDandanplayServer::start(MockDandanplayBehavior {
            comment_status: 500,
            ..MockDandanplayBehavior::default()
        });
        let failed = dandanplay_test_app(&fixture, Some(test_resolver(&fixture, &failed_server)))
            .oneshot(get(
                "/api/providers/dandanplay/resolve?mediaId=episode-id&episodeId=222",
            ))
            .await
            .expect("failed response");
        assert_eq!(StatusCode::BAD_GATEWAY, failed.status());
        let body = body_text(failed).await;
        assert!(body.contains("dandanplay request failed:"));
    }

    #[tokio::test]
    async fn force_refresh_returns_full_candidate_list_after_a_cached_single_pick() {
        let fixture = FixtureEnvironment::new();
        let server = MockDandanplayServer::start(MockDandanplayBehavior::default());
        let app = dandanplay_test_app(&fixture, Some(test_resolver(&fixture, &server)));

        // The first auto-resolve (no episodeId) caches only the one
        // candidate it ended up selecting.
        let _ = request_json(&app, "/api/providers/dandanplay/resolve?mediaId=episode-id").await;

        // Without forceRefresh, listing again just replays that single
        // cached pick instead of the original full candidate list — a match
        // picker cannot offer alternatives from this response.
        let cached =
            request_json(&app, "/api/providers/dandanplay/resolve?mediaId=episode-id").await;
        assert_eq!(1, cached["matches"].as_array().expect("matches").len());

        // forceRefresh bypasses that cache and returns every candidate again.
        let refreshed = request_json(
            &app,
            "/api/providers/dandanplay/resolve?mediaId=episode-id&forceRefresh=true",
        )
        .await;
        assert_eq!(2, refreshed["matches"].as_array().expect("matches").len());

        let invalid = app
            .clone()
            .oneshot(get(
                "/api/providers/dandanplay/resolve?mediaId=episode-id&forceRefresh=maybe",
            ))
            .await
            .expect("invalid response");
        assert_eq!(StatusCode::BAD_REQUEST, invalid.status());
        assert_text_body(
            invalid,
            "Query parameter 'forceRefresh' must be true or false.",
        )
        .await;
    }

    #[tokio::test]
    async fn dandanplay_search_lists_animes_with_episodes() {
        let fixture = FixtureEnvironment::new();
        let server = MockDandanplayServer::start(MockDandanplayBehavior::default());
        let app = dandanplay_test_app(&fixture, Some(test_resolver(&fixture, &server)));

        let missing = app
            .clone()
            .oneshot(get("/api/providers/dandanplay/search"))
            .await
            .expect("missing keyword response");
        assert_eq!(StatusCode::BAD_REQUEST, missing.status());
        assert_text_body(missing, "Query parameter 'keyword' is required.").await;

        let response = request_json(
            &app,
            "/api/providers/dandanplay/search?keyword=Searched%20Anime",
        )
        .await;
        let animes = response["animes"].as_array().expect("animes");
        assert_eq!(1, animes.len());
        assert_eq!(999, animes[0]["animeId"]);
        assert_eq!("Searched Anime", animes[0]["animeTitle"]);
        assert_eq!("TV Series", animes[0]["typeDescription"]);
        let episodes = animes[0]["episodes"].as_array().expect("episodes");
        assert_eq!(2, episodes.len());
        assert_eq!(9990001, episodes[0]["episodeId"]);
        assert_eq!("Episode 1", episodes[0]["episodeTitle"]);

        // The keyword reaches the dandanplay API URL-encoded.
        let search_request = server
            .requests()
            .into_iter()
            .find(|request| request.path == "/api/v2/search/episodes")
            .expect("search request");
        assert_eq!(
            Some("anime=Searched+Anime"),
            search_request.query.as_deref()
        );
    }

    #[tokio::test]
    async fn dandanplay_bangumi_returns_detail_profile() {
        let fixture = FixtureEnvironment::new();
        let server = MockDandanplayServer::start(MockDandanplayBehavior::default());
        let app = dandanplay_test_app(&fixture, Some(test_resolver(&fixture, &server)));

        let missing = app
            .clone()
            .oneshot(get("/api/providers/dandanplay/bangumi"))
            .await
            .expect("missing animeId response");
        assert_eq!(StatusCode::BAD_REQUEST, missing.status());
        assert_text_body(missing, "Query parameter 'animeId' must be positive.").await;

        let response = request_json(&app, "/api/providers/dandanplay/bangumi?animeId=999").await;
        assert_eq!(999, response["animeId"]);
        assert_eq!("Searched Anime", response["animeTitle"]);
        assert_eq!("TV Series", response["typeDescription"]);
        assert_eq!(
            "A town where half the residents have special powers.",
            response["summary"]
        );
        assert_eq!(7.7, response["rating"].as_f64().expect("rating"));
        assert_eq!(false, response["isOnAir"]);
        assert_eq!(
            vec!["Mystery", "School"],
            response["tags"]
                .as_array()
                .expect("tags")
                .iter()
                .filter_map(Value::as_str)
                .collect::<Vec<_>>()
        );
        let episodes = response["episodes"].as_array().expect("episodes");
        assert_eq!(2, episodes.len());
        assert_eq!("2017-04-05T00:00:00", episodes[0]["airDate"]);
        let databases = response["onlineDatabases"].as_array().expect("databases");
        assert_eq!("Bangumi.tv", databases[0]["name"]);
        assert_eq!("https://bangumi.tv/subject/179949", databases[0]["url"]);

        let unavailable = dandanplay_test_app(&fixture, None)
            .oneshot(get("/api/providers/dandanplay/bangumi?animeId=999"))
            .await
            .expect("unavailable response");
        assert_eq!(StatusCode::BAD_GATEWAY, unavailable.status());
    }

    #[tokio::test]
    async fn selecting_a_searched_episode_outside_hash_matches_pins_and_records_it() {
        let fixture = FixtureEnvironment::new();
        let server = MockDandanplayServer::start(MockDandanplayBehavior::default());
        let store = Arc::new(CatalogMetadataStore::new(
            fixture.temp.join("catalog-metadata-search-pin.json"),
        ));
        let app = dandanplay_test_app_with_metadata(
            &fixture,
            Some(test_resolver(&fixture, &server)),
            Some(store.clone()),
        );

        // Episode 9990002 comes from a keyword search; the mock hash match
        // only ever proposes 111/222, so pinning it must survive not being
        // among the candidates.
        let response = request_json(
            &app,
            "/api/providers/dandanplay/resolve?mediaId=episode-id&episodeId=9990002&animeId=999&animeTitle=Searched%20Anime&episodeTitle=Episode%202",
        )
        .await;
        assert_eq!(9990002, response["selectedMatch"]["episodeId"]);
        assert_eq!("Searched Anime", response["selectedMatch"]["animeTitle"]);
        assert_eq!(2, response["commentCount"]);

        let recorded = store.get("episode-id").expect("identity recorded");
        assert_eq!(999, recorded.dandanplay_anime_id);
        assert_eq!("Searched Anime", recorded.anime_title);
        assert_eq!(Some("Episode 2"), recorded.episode_title.as_deref());

        // The pinned selection becomes the cached match for later plain reads.
        let cached = request_json(&app, "/api/danmaku/episode-id").await;
        assert_eq!("READY", cached["status"]);
        assert_eq!(9990002, cached["episodeId"]);
        assert_eq!("Searched Anime", cached["animeTitle"]);
    }

    #[tokio::test]
    async fn resolving_danmaku_records_identity_and_enriches_catalog() {
        let fixture = FixtureEnvironment::new();
        let server = MockDandanplayServer::start(MockDandanplayBehavior::default());
        let store = Arc::new(CatalogMetadataStore::new(
            fixture.temp.join("catalog-metadata-route.json"),
        ));
        let app = dandanplay_test_app_with_metadata(
            &fixture,
            Some(test_resolver(&fixture, &server)),
            Some(store.clone()),
        );

        // The catalog item carries no provider metadata before any resolve.
        let before = request_json(&app, "/api/library").await;
        let item_before = find_catalog_item(&before, "episode-id");
        assert!(item_before.get("animeMetadata").is_none());

        // Resolving danmaku records the recognized dandanplay identity.
        let _ = request_json(&app, "/api/danmaku/episode-id").await;
        assert_eq!(
            333,
            store
                .get("episode-id")
                .expect("identity recorded")
                .dandanplay_anime_id
        );

        // The catalog route now groups the item under the matched anime.
        let after = request_json(&app, "/api/library").await;
        let item_after = find_catalog_item(&after, "episode-id");
        assert_eq!("Example Anime", item_after["animeMetadata"]["displayTitle"]);
        assert_eq!(
            "DANDANPLAY",
            item_after["animeMetadata"]["animeId"]["provider"]
        );
        assert_eq!(333, item_after["animeMetadata"]["animeId"]["value"]);
    }

    #[tokio::test]
    async fn resolving_danmaku_caches_and_serves_a_recognized_anime_poster() {
        const POSTER_BYTES: &[u8] = &[0xff, 0xd8, 0xff, 0xd9];

        let fixture = FixtureEnvironment::new();
        let dandanplay_server = MockDandanplayServer::start(MockDandanplayBehavior::default());
        let catalog_metadata = Arc::new(CatalogMetadataStore::new(
            fixture.temp.join("catalog-metadata-poster-route.json"),
        ));
        let poster_cache = Arc::new(PosterCacheStore::new(fixture.temp.join("poster-cache")));
        let image_url = start_test_image_server(POSTER_BYTES);
        let provider_service = Arc::new(ExternalProviderService::new_for_tests(
            vec![Arc::new(FixedAnimeSearchClient { image_url })],
            Vec::new(),
        ));
        let app = dandanplay_test_app_full(
            &fixture,
            Some(test_resolver(&fixture, &dandanplay_server)),
            Some(catalog_metadata.clone()),
            Some(poster_cache),
            Some(provider_service),
        );

        // Resolving danmaku records the identity and spawns a background
        // poster fetch; the danmaku response itself does not wait on it.
        let _ = request_json(&app, "/api/danmaku/episode-id").await;
        let poster_recorded = wait_for(Duration::from_secs(2), || {
            catalog_metadata.poster_file("episode-id").is_some()
        })
        .await;
        assert!(poster_recorded, "poster should be cached in the background");
        let cached_file = catalog_metadata
            .poster_file("episode-id")
            .expect("poster file recorded");
        assert_eq!(
            POSTER_BYTES,
            fs::read(&cached_file).expect("poster bytes").as_slice()
        );

        // The fixture item already carries a static scan-time poster, so the
        // published catalog and `/posters/` route keep serving that one
        // untouched rather than switching to the newly cached image.
        let catalog = request_json(&app, "/api/library").await;
        let item = find_catalog_item(&catalog, "episode-id");
        assert_eq!(Some("/posters/episode-id"), item["posterPath"].as_str());
        let response = app
            .clone()
            .oneshot(get("/posters/episode-id"))
            .await
            .expect("poster response");
        assert_eq!(StatusCode::OK, response.status());
        let bytes = to_bytes(response.into_body(), 1_048_576)
            .await
            .expect("poster bytes");
        assert_eq!([1_u8, 35, 69, 103], bytes.as_ref());
    }

    #[tokio::test]
    async fn catalog_reads_backfill_a_poster_left_unresolved_by_a_prior_process() {
        const POSTER_BYTES: &[u8] = &[0x89, 0x50, 0x4e, 0x47];

        // Simulates a real failure mode found in production data: the local
        // server is hard-killed (not gracefully shut down) whenever the
        // desktop host stops its managed sidecar, so a recognition's
        // fire-and-forget poster fetch can be lost with the identity already
        // recorded — here reproduced by recording the identity directly,
        // bypassing the danmaku route the initial fetch would have used.
        let temp = temp_dir("danmaku-poster-backfill");
        let item = LibraryMediaItem {
            id: "unposted-id".to_owned(),
            series_title: "Example Show".to_owned(),
            episode_title: "Episode 01".to_owned(),
            relative_path: "Example Show/Episode 01.bin".to_owned(),
            size_bytes: 6,
            media_type: "application/octet-stream".to_owned(),
            stream_path: "/media/unposted-id".to_owned(),
            indexed_at_epoch_ms: 1_700_000_000_000,
            subtitles: Vec::new(),
            poster_path: None,
            root_label: None,
            anime_metadata: None,
            metadata_status: Default::default(),
        };
        let library = PublishedLibrary {
            catalog: LibraryCatalog {
                root_name: "Fixture Library".to_owned(),
                indexed_at_epoch_ms: 1_700_000_000_000,
                items: vec![item],
            },
            files_by_id: PathMap::new(),
            subtitle_files_by_id: PathMap::new(),
            poster_files_by_id: PathMap::new(),
        };
        let catalog_metadata = Arc::new(CatalogMetadataStore::new(
            temp.join("catalog-metadata-backfill.json"),
        ));
        catalog_metadata
            .record("unposted-id", 42, "Backfill Anime".to_owned(), None)
            .expect("identity recorded as if by a prior process");
        assert!(
            catalog_metadata.poster_file("unposted-id").is_none(),
            "no poster recorded yet, matching the interrupted-process scenario"
        );

        let poster_cache = Arc::new(PosterCacheStore::new(temp.join("poster-cache")));
        let image_url = start_test_image_server(POSTER_BYTES);
        let provider_service = Arc::new(ExternalProviderService::new_for_tests(
            vec![Arc::new(FixedAnimeSearchClient { image_url })],
            Vec::new(),
        ));
        let state = HttpServerState::new(
            library,
            Arc::new(PlaybackProgressStore::new(
                temp.join("progress-backfill.json"),
            )),
            HttpServerConfig {
                web_assets_root: None,
                host_mode: HOST_MODE_HEADLESS_SERVER.to_owned(),
                provider_settings: None,
                provider_runtime_status: None,
                external_provider_service: Some(provider_service),
                authenticated_post_hooks: Vec::new(),
                dandanplay_resolver: None,
                catalog_metadata: Some(catalog_metadata.clone()),
                poster_cache: Some(poster_cache),
                provider_admin: None,
            },
        );

        // A plain catalog read (no danmaku resolve involved) is enough to
        // notice the missing poster and retry it in the background.
        let _ = handle_catalog(&state, &Method::GET);
        let poster_recorded = wait_for(Duration::from_secs(2), || {
            catalog_metadata.poster_file("unposted-id").is_some()
        })
        .await;
        assert!(
            poster_recorded,
            "a later catalog read should backfill the lost poster"
        );
        let cached_file = catalog_metadata
            .poster_file("unposted-id")
            .expect("poster file recorded");
        assert_eq!(
            POSTER_BYTES,
            fs::read(&cached_file).expect("poster bytes").as_slice()
        );

        let enriched = handle_catalog(&state, &Method::GET);
        let body = to_bytes(enriched.into_body(), 1_048_576)
            .await
            .expect("body");
        let catalog: Value = serde_json::from_slice(&body).expect("json body");
        let item = find_catalog_item(&catalog, "unposted-id");
        assert_eq!(Some("/posters/unposted-id"), item["posterPath"].as_str());

        fs::remove_dir_all(temp).ok();
    }

    #[tokio::test]
    async fn provider_search_merges_mock_mal_and_bangumi_results() {
        let fixture = FixtureEnvironment::new();
        let provider_server =
            MockExternalProviderServer::start(MockExternalProviderBehavior::default());
        let app = external_provider_test_app(
            &fixture,
            Arc::new(ExternalProviderService::new_for_tests(
                vec![
                    Arc::new(MyAnimeListSearchClient::new(
                        provider_server.base_url(),
                        "mal-client-id".to_owned(),
                    )),
                    Arc::new(BangumiSearchClient::new(
                        provider_server.base_url(),
                        "DanmakuTest/1.0".to_owned(),
                    )),
                ],
                Vec::new(),
            )),
        );

        let response = request_json(
            &app,
            "/api/providers/search?title=Frieren&providers=mal,bgm&limit=3&episodeCount=28&startYear=2023",
        )
        .await;
        let matches = response.as_array().expect("matches");
        assert_eq!(2, matches.len());
        assert!(
            matches
                .iter()
                .any(|item| item["anime"]["id"]["provider"] == "MY_ANIME_LIST")
        );
        assert!(
            matches
                .iter()
                .any(|item| item["anime"]["id"]["provider"] == "BANGUMI")
        );

        let requests = provider_server.requests();
        let mal_search = requests
            .iter()
            .find(|request| request.path == "/anime")
            .expect("MAL search request");
        assert_eq!("GET", mal_search.method);
        assert_eq!(
            Some(
                "q=Frieren&limit=3&fields=id%2Ctitle%2Calternative_titles%2Cnum_episodes%2Cstart_date%2Cmain_picture%2Csynopsis"
            ),
            mal_search.query.as_deref()
        );
        assert_eq!("mal-client-id", mal_search.headers["x-mal-client-id"]);
        let bangumi_search = requests
            .iter()
            .find(|request| request.path == "/v0/search/subjects")
            .expect("Bangumi search request");
        assert_eq!("POST", bangumi_search.method);
        assert_eq!(Some("limit=3&offset=0"), bangumi_search.query.as_deref());
        assert_eq!("DanmakuTest/1.0", bangumi_search.headers["user-agent"]);
        assert!(bangumi_search.body.contains("\"keyword\":\"Frieren\""));
        assert!(
            requests
                .iter()
                .any(|request| request.path == "/v0/subjects/400602")
        );
    }

    #[tokio::test]
    async fn provider_settings_require_auth_redact_secrets_and_reload_runtime() {
        #[derive(Debug)]
        struct ReversingSecretProtector;

        impl crate::provider_secrets::SecretProtector for ReversingSecretProtector {
            fn protect(&self, plaintext: &[u8]) -> crate::Result<Vec<u8>> {
                Ok(plaintext.iter().rev().map(|byte| byte ^ 0x5a).collect())
            }

            fn unprotect(&self, ciphertext: &[u8]) -> crate::Result<Vec<u8>> {
                Ok(ciphertext.iter().rev().map(|byte| byte ^ 0x5a).collect())
            }
        }

        let fixture = FixtureEnvironment::new();
        let settings = HeadlessServerSettings {
            pairing_token: "123456".to_owned(),
            library_roots: Vec::new(),
            dandanplay: crate::settings::HeadlessDandanplayProviderSettings::default(),
            external_anime: crate::settings::HeadlessExternalAnimeProviderSettings::default(),
        };
        let secret_store = ProviderSecretStore::with_protector(
            fixture.temp.join("provider-secrets.json"),
            Arc::new(ReversingSecretProtector),
        );
        let admin = Arc::new(ProviderAdminState::new_for_tests(
            fixture.temp.clone(),
            settings.clone(),
            settings.clone(),
            secret_store,
        ));
        let state = HttpServerState::new(
            fixture.library.clone(),
            Arc::new(PlaybackProgressStore::new(
                fixture.temp.join("progress-provider-settings.json"),
            )),
            HttpServerConfig::headless(None, &settings, None, None, None, admin),
        );
        let app = app(state);

        let unauthorized = app
            .clone()
            .oneshot(get("/api/providers/settings"))
            .await
            .expect("unauthorized response");
        assert_eq!(StatusCode::UNAUTHORIZED, unauthorized.status());

        let update = json!({
            "dandanplay": {
                "baseUrl": "https://api.dandanplay.net",
                "appId": "dandanplay-app",
                "appSecret": "dandanplay-secret",
                "authenticationMode": "CREDENTIAL",
                "cacheMaxAgeDays": 14
            },
            "externalAnime": {
                "myAnimeListClientId": "mal-client",
                "myAnimeListClientSecret": "mal-secret",
                "myAnimeListAccessToken": "mal-token",
                "bangumiBaseUrl": "https://api.bgm.tv/",
                "bangumiUserAgent": "DanmakuTest/1.0",
                "bangumiAccessToken": "bangumi-token"
            }
        });
        let response = app
            .clone()
            .oneshot(
                Request::builder()
                    .method("PUT")
                    .uri("/api/providers/settings")
                    .header(AUTHORIZATION, "Bearer 123456")
                    .header("content-type", "application/json")
                    .body(Body::from(update.to_string()))
                    .expect("settings request"),
            )
            .await
            .expect("settings response");
        assert_eq!(StatusCode::OK, response.status());
        let response_body = body_text(response).await;
        for secret in [
            "dandanplay-secret",
            "mal-secret",
            "mal-token",
            "bangumi-token",
        ] {
            assert!(!response_body.contains(secret));
        }
        let response: Value = serde_json::from_str(&response_body).expect("settings json");
        assert_eq!(true, response["settings"]["dandanplay"]["hasAppSecret"]);
        assert_eq!(true, response["runtime"]["dandanplay"]["authenticated"]);
        assert_eq!(
            true,
            response["runtime"]["myAnimeList"]["listWriteAvailable"]
        );
        assert_eq!(true, response["runtime"]["bangumi"]["listWriteAvailable"]);

        let runtime = request_json(&app, "/api/providers/runtime").await;
        assert_eq!(true, runtime["dandanplay"]["commentFetchAvailable"]);
        assert_eq!(true, runtime["myAnimeList"]["listReadAvailable"]);
        assert_eq!(true, runtime["bangumi"]["listReadAvailable"]);

        let status = request_json(&app, "/api/server/status").await;
        assert_eq!(
            "dandanplay-app",
            status["providerSettings"]["dandanplay"]["appId"]
        );
        assert_eq!(
            "DanmakuTest/1.0",
            status["providerSettings"]["externalAnime"]["bangumiUserAgent"]
        );

        let settings_file =
            fs::read_to_string(fixture.temp.join("server-settings.json")).expect("settings file");
        let secrets_file =
            fs::read_to_string(fixture.temp.join("provider-secrets.json")).expect("secret file");
        for secret in [
            "dandanplay-secret",
            "mal-secret",
            "mal-token",
            "bangumi-token",
        ] {
            assert!(!settings_file.contains(secret));
            assert!(!secrets_file.contains(secret));
        }
    }

    #[tokio::test]
    async fn provider_search_swallows_one_provider_failure() {
        let fixture = FixtureEnvironment::new();
        let provider_server = MockExternalProviderServer::start(MockExternalProviderBehavior {
            mal_search_status: 500,
            ..MockExternalProviderBehavior::default()
        });
        let app = external_provider_test_app(
            &fixture,
            Arc::new(ExternalProviderService::new_for_tests(
                vec![
                    Arc::new(MyAnimeListSearchClient::new(
                        provider_server.base_url(),
                        "mal-client-id".to_owned(),
                    )),
                    Arc::new(BangumiSearchClient::new(
                        provider_server.base_url(),
                        "DanmakuTest/1.0".to_owned(),
                    )),
                ],
                Vec::new(),
            )),
        );

        let response = request_json(
            &app,
            "/api/providers/search?title=Frieren&providers=mal,bgm&limit=3",
        )
        .await;
        let matches = response.as_array().expect("matches");
        assert_eq!(1, matches.len());
        assert_eq!("BANGUMI", matches[0]["anime"]["id"]["provider"]);
    }

    #[tokio::test]
    async fn provider_list_entry_routes_map_mock_provider_outcomes() {
        let fixture = FixtureEnvironment::new();
        let provider_server =
            MockExternalProviderServer::start(MockExternalProviderBehavior::default());
        let app = external_provider_test_app(
            &fixture,
            Arc::new(ExternalProviderService::new_for_tests(
                Vec::new(),
                vec![
                    Arc::new(MyAnimeListTrackingClient::new(
                        provider_server.base_url(),
                        "mal-access-token".to_owned(),
                    )),
                    Arc::new(BangumiTrackingClient::new(
                        provider_server.base_url(),
                        "DanmakuTest/1.0".to_owned(),
                        "bangumi-access-token".to_owned(),
                    )),
                ],
            )),
        );

        let entry =
            request_json(&app, "/api/providers/list/entry?provider=mal&animeId=52991").await;
        assert_eq!("MY_ANIME_LIST", entry["animeId"]["provider"]);
        assert_eq!("WATCHING", entry["status"]);
        assert_eq!(4, entry["watchedEpisodes"]);
        assert_eq!(8, entry["score"]);
        assert_eq!(1_704_164_645_000_i64, entry["updatedAtEpochMs"]);

        let update = json!({
            "animeId": { "provider": "BANGUMI", "value": 400602 },
            "status": "COMPLETED",
            "watchedEpisodes": 28,
            "score": 9,
            "trackingEnabled": true,
            "ratingEnabled": true
        });
        let written = request_json_with_body(
            &app,
            "POST",
            "/api/providers/list/entry",
            update.to_string(),
        )
        .await;
        assert_eq!("BANGUMI", written["animeId"]["provider"]);
        assert_eq!("COMPLETED", written["status"]);
        assert_eq!(28, written["watchedEpisodes"]);
        assert_eq!(9, written["score"]);
        let requests = provider_server.requests();
        let mal_read = requests
            .iter()
            .find(|request| request.path == "/anime/52991")
            .expect("MAL list read");
        assert_eq!(Some("fields=my_list_status"), mal_read.query.as_deref());
        assert_eq!("Bearer mal-access-token", mal_read.headers["authorization"]);
        let bangumi_write = requests
            .iter()
            .find(|request| request.path == "/v0/users/-/collections/400602")
            .expect("Bangumi list write");
        assert_eq!("PATCH", bangumi_write.method);
        assert_eq!(
            "Bearer bangumi-access-token",
            bangumi_write.headers["authorization"]
        );
        assert_eq!("DanmakuTest/1.0", bangumi_write.headers["user-agent"]);
        assert!(bangumi_write.body.contains("\"type\":2"));
        assert!(bangumi_write.body.contains("\"ep_status\":28"));
        assert!(bangumi_write.body.contains("\"rate\":9"));

        let dandanplay = app
            .clone()
            .oneshot(get(
                "/api/providers/list/entry?provider=dandanplay&animeId=333",
            ))
            .await
            .expect("dandanplay response");
        assert_eq!(StatusCode::BAD_REQUEST, dandanplay.status());
        assert_text_body(
            dandanplay,
            "dandanplay does not support external list entries.",
        )
        .await;

        let method = app
            .clone()
            .oneshot(
                Request::builder()
                    .method("PUT")
                    .uri("/api/providers/list/entry")
                    .body(Body::empty())
                    .expect("request"),
            )
            .await
            .expect("method response");
        assert_eq!(StatusCode::METHOD_NOT_ALLOWED, method.status());
        assert_text_body(method, "Method not allowed.").await;
    }

    #[tokio::test]
    async fn provider_list_entry_reports_missing_not_found_and_upstream_failures() {
        let fixture = FixtureEnvironment::new();
        let missing_credentials = external_provider_test_app(
            &fixture,
            Arc::new(ExternalProviderService::new_for_tests(
                Vec::new(),
                Vec::new(),
            )),
        )
        .oneshot(get("/api/providers/list/entry?provider=mal&animeId=52991"))
        .await
        .expect("missing credentials response");
        assert_eq!(StatusCode::CONFLICT, missing_credentials.status());

        let not_found_server = MockExternalProviderServer::start(MockExternalProviderBehavior {
            mal_list_read_status: 404,
            ..MockExternalProviderBehavior::default()
        });
        let not_found_app = external_provider_test_app(
            &fixture,
            Arc::new(ExternalProviderService::new_for_tests(
                Vec::new(),
                vec![Arc::new(MyAnimeListTrackingClient::new(
                    not_found_server.base_url(),
                    "mal-access-token".to_owned(),
                ))],
            )),
        );
        let not_found = not_found_app
            .oneshot(get("/api/providers/list/entry?provider=mal&animeId=52991"))
            .await
            .expect("not found response");
        assert_eq!(StatusCode::NOT_FOUND, not_found.status());
        assert_text_body(not_found, "External list entry was not found.").await;

        let failed_server = MockExternalProviderServer::start(MockExternalProviderBehavior {
            mal_list_read_status: 500,
            ..MockExternalProviderBehavior::default()
        });
        let failed_app = external_provider_test_app(
            &fixture,
            Arc::new(ExternalProviderService::new_for_tests(
                Vec::new(),
                vec![Arc::new(MyAnimeListTrackingClient::new(
                    failed_server.base_url(),
                    "mal-access-token".to_owned(),
                ))],
            )),
        );
        let failed = failed_app
            .oneshot(get("/api/providers/list/entry?provider=mal&animeId=52991"))
            .await
            .expect("failed response");
        assert_eq!(StatusCode::BAD_GATEWAY, failed.status());
        assert!(
            body_text(failed)
                .await
                .contains("external list request failed:")
        );
    }

    #[tokio::test]
    async fn provider_routes_validate_documented_parameter_edges() {
        let fixture = FixtureEnvironment::new();
        let app = external_provider_test_app(
            &fixture,
            Arc::new(ExternalProviderService::new_for_tests(
                Vec::new(),
                Vec::new(),
            )),
        );

        let cases = [
            (
                "/api/providers/search",
                "Query parameter 'title' is required.",
            ),
            (
                "/api/providers/search?title=Frieren&limit=0",
                "Query parameter 'limit' must be between 1 and 50.",
            ),
            (
                "/api/providers/search?title=Frieren&limit=51",
                "Query parameter 'limit' must be between 1 and 50.",
            ),
            (
                "/api/providers/search?title=Frieren&episodeCount=0",
                "Query parameter 'episodeCount' must be positive.",
            ),
            (
                "/api/providers/search?title=Frieren&startYear=1899",
                "Query parameter 'startYear' must be between 1900 and 2200.",
            ),
            (
                "/api/providers/search?title=Frieren&providers=unknown",
                "Unsupported provider 'unknown'.",
            ),
            (
                "/api/providers/list/entry?animeId=52991",
                "Query parameter 'provider' is required.",
            ),
            (
                "/api/providers/list/entry?provider=unknown&animeId=52991",
                "Unsupported provider 'unknown'.",
            ),
            (
                "/api/providers/list/entry?provider=mal&animeId=0",
                "Query parameter 'animeId' must be positive.",
            ),
        ];

        for (path, expected) in cases {
            let response = app.clone().oneshot(get(path)).await.expect("response");
            assert_eq!(StatusCode::BAD_REQUEST, response.status(), "path {path}");
            assert_text_body(response, expected).await;
        }

        let malformed_post = app
            .clone()
            .oneshot(
                Request::builder()
                    .method("POST")
                    .uri("/api/providers/list/entry")
                    .body(Body::from("{}"))
                    .expect("request"),
            )
            .await
            .expect("malformed response");
        assert_eq!(StatusCode::BAD_REQUEST, malformed_post.status());
        assert_text_body(
            malformed_post,
            "Request body must be an ExternalAnimeTrackingUpdate JSON object.",
        )
        .await;
    }

    #[test]
    fn discovery_fixture_payload_matches_encoder() {
        let fixture = read_fixture("discovery-announcement.json");
        let expected = fixture["text"].as_str().expect("fixture text");
        let actual = crate::discovery::discovery_payload(8_686).expect("payload");
        assert_eq!(expected.as_bytes(), actual.as_slice());
    }

    #[test]
    fn range_parser_matches_documented_edge_cases() {
        assert_eq!(Some((1, 3)), parse_range("bytes=1-3", 6));
        assert_eq!(Some((3, 5)), parse_range("bytes=3-", 6));
        assert_eq!(Some((4, 5)), parse_range("bytes=-2", 6));
        assert_eq!(Some((0, 5)), parse_range("bytes=-99", 6));
        assert_eq!(Some((2, 5)), parse_range("bytes=2-99", 6));
        assert_eq!(None, parse_range("items=1-2", 6));
        assert_eq!(None, parse_range("bytes=1-2,3-4", 6));
        assert_eq!(None, parse_range("bytes=-0", 6));
        assert_eq!(None, parse_range("bytes=6-6", 6));
        assert_eq!(None, parse_range("bytes=0-0", 0));
    }

    #[test]
    fn maps_matroska_stream_content_type() {
        assert_eq!(
            "video/x-matroska",
            content_type(Path::new("Episode 01.mkv"))
        );
    }

    #[tokio::test]
    async fn media_route_handles_mpv_open_ended_ranges_and_head() {
        let fixture = FixtureEnvironment::new();
        let state = HttpServerState::new(
            fixture.library.clone(),
            Arc::new(PlaybackProgressStore::new(
                fixture.temp.join("mpv-range-progress.json"),
            )),
            HttpServerConfig::fixture_embedded(fixture.web_root.clone()),
        );
        let app = app(state);

        let head = app
            .clone()
            .oneshot(
                Request::builder()
                    .method("HEAD")
                    .uri("/media/episode-id")
                    .body(Body::empty())
                    .expect("HEAD request"),
            )
            .await
            .expect("HEAD response");
        assert_eq!(StatusCode::OK, head.status());
        assert_eq!("bytes", head.headers()[ACCEPT_RANGES]);
        assert_eq!("6", head.headers()[CONTENT_LENGTH]);
        assert!(
            to_bytes(head.into_body(), 1_048_576)
                .await
                .unwrap()
                .is_empty()
        );

        let open_ended = app
            .clone()
            .oneshot(
                Request::builder()
                    .method("GET")
                    .uri("/media/episode-id")
                    .header("range", "bytes=0-")
                    .body(Body::empty())
                    .expect("open-ended range request"),
            )
            .await
            .expect("open-ended range response");
        assert_eq!(StatusCode::PARTIAL_CONTENT, open_ended.status());
        assert_eq!("bytes 0-5/6", open_ended.headers()[CONTENT_RANGE]);
        assert_eq!("6", open_ended.headers()[CONTENT_LENGTH]);
        assert_eq!(
            vec![0_u8, 1, 2, 3, 4, 5],
            to_bytes(open_ended.into_body(), 1_048_576)
                .await
                .unwrap()
                .to_vec(),
        );

        let mid_file = app
            .clone()
            .oneshot(
                Request::builder()
                    .method("GET")
                    .uri("/media/episode-id")
                    .header("range", "bytes=3-")
                    .body(Body::empty())
                    .expect("mid-file range request"),
            )
            .await
            .expect("mid-file range response");
        assert_eq!(StatusCode::PARTIAL_CONTENT, mid_file.status());
        assert_eq!("bytes 3-5/6", mid_file.headers()[CONTENT_RANGE]);
        assert_eq!(
            vec![3_u8, 4, 5],
            to_bytes(mid_file.into_body(), 1_048_576)
                .await
                .unwrap()
                .to_vec(),
        );

        let first = app.clone().oneshot(
            Request::builder()
                .method("GET")
                .uri("/media/episode-id")
                .header("range", "bytes=0-")
                .body(Body::empty())
                .expect("first concurrent request"),
        );
        let second = app.oneshot(
            Request::builder()
                .method("GET")
                .uri("/media/episode-id")
                .header("range", "bytes=0-")
                .body(Body::empty())
                .expect("second concurrent request"),
        );
        let (first, second) = tokio::join!(first, second);
        for response in [first.unwrap(), second.unwrap()] {
            assert_eq!(StatusCode::PARTIAL_CONTENT, response.status());
            assert_eq!("bytes 0-5/6", response.headers()[CONTENT_RANGE]);
            assert_eq!(
                vec![0_u8, 1, 2, 3, 4, 5],
                to_bytes(response.into_body(), 1_048_576)
                    .await
                    .unwrap()
                    .to_vec(),
            );
        }
    }

    struct FixtureEnvironment {
        temp: PathBuf,
        web_root: PathBuf,
        library: PublishedLibrary,
    }

    impl FixtureEnvironment {
        fn new() -> Self {
            let temp = temp_dir("danmaku-lan-fixture");
            let media_file = temp.join("danmaku-media-fixture.bin");
            let subtitle_file = temp.join("danmaku-subtitle-fixture.srt");
            let poster_file = temp.join("danmaku-poster-fixture.jpg");
            let web_root = temp.join("web");
            let web_assets = web_root.join("assets");

            fs::create_dir_all(&web_assets).expect("web assets should create");
            fs::write(&media_file, [0_u8, 1, 2, 3, 4, 5]).expect("media should write");
            fs::write(&subtitle_file, "1\n00:00:00,000 --> 00:00:01,000\nHello\n")
                .expect("subtitle should write");
            fs::write(&poster_file, [1_u8, 35, 69, 103]).expect("poster should write");
            fs::write(
                web_root.join("index.html"),
                "<!doctype html><title>Danmaku</title>",
            )
            .expect("index should write");
            fs::write(
                web_assets.join("app.js"),
                "window.__danmakuFixture = true;\n",
            )
            .expect("asset should write");

            let subtitle = LibrarySubtitleTrack {
                id: "subtitle-id".to_owned(),
                label: "English".to_owned(),
                relative_path: "Example Show/Episode 01.en.srt".to_owned(),
                media_type: "application/x-subrip".to_owned(),
                stream_path: "/subtitles/subtitle-id".to_owned(),
            };
            let item = LibraryMediaItem {
                id: "episode-id".to_owned(),
                series_title: "Example Show".to_owned(),
                episode_title: "Episode 01".to_owned(),
                relative_path: "Example Show/Episode 01.bin".to_owned(),
                size_bytes: 6,
                media_type: "application/octet-stream".to_owned(),
                stream_path: "/media/episode-id".to_owned(),
                indexed_at_epoch_ms: 1_700_000_000_000,
                subtitles: vec![subtitle.clone()],
                poster_path: Some("/posters/episode-id".to_owned()),
                root_label: None,
                anime_metadata: None,
                metadata_status: Default::default(),
            };
            let catalog = LibraryCatalog {
                root_name: "Fixture Library".to_owned(),
                indexed_at_epoch_ms: 1_700_000_000_000,
                items: vec![item],
            };
            let mut files_by_id = PathMap::new();
            files_by_id.insert("episode-id".to_owned(), media_file);
            let mut subtitle_files_by_id = PathMap::new();
            subtitle_files_by_id.insert("subtitle-id".to_owned(), subtitle_file);
            let mut poster_files_by_id = PathMap::new();
            poster_files_by_id.insert("episode-id".to_owned(), poster_file);

            Self {
                temp,
                web_root,
                library: PublishedLibrary {
                    catalog,
                    files_by_id,
                    subtitle_files_by_id,
                    poster_files_by_id,
                },
            }
        }
    }

    fn dandanplay_test_app(
        fixture: &FixtureEnvironment,
        resolver: Option<Arc<DandanplayResolver>>,
    ) -> Router {
        dandanplay_test_app_with_metadata(fixture, resolver, None)
    }

    fn dandanplay_test_app_with_metadata(
        fixture: &FixtureEnvironment,
        resolver: Option<Arc<DandanplayResolver>>,
        catalog_metadata: Option<Arc<CatalogMetadataStore>>,
    ) -> Router {
        dandanplay_test_app_full(fixture, resolver, catalog_metadata, None, None)
    }

    fn dandanplay_test_app_full(
        fixture: &FixtureEnvironment,
        resolver: Option<Arc<DandanplayResolver>>,
        catalog_metadata: Option<Arc<CatalogMetadataStore>>,
        poster_cache: Option<Arc<PosterCacheStore>>,
        external_provider_service: Option<Arc<ExternalProviderService>>,
    ) -> Router {
        let state = HttpServerState::new(
            fixture.library.clone(),
            Arc::new(PlaybackProgressStore::new(
                fixture.temp.join("progress-route-test.json"),
            )),
            HttpServerConfig {
                web_assets_root: None,
                host_mode: HOST_MODE_HEADLESS_SERVER.to_owned(),
                provider_settings: None,
                provider_runtime_status: None,
                external_provider_service,
                authenticated_post_hooks: Vec::new(),
                dandanplay_resolver: resolver,
                catalog_metadata,
                poster_cache,
                provider_admin: None,
            },
        );
        app(state)
    }

    fn external_provider_test_app(
        fixture: &FixtureEnvironment,
        service: Arc<ExternalProviderService>,
    ) -> Router {
        let state = HttpServerState::new(
            fixture.library.clone(),
            Arc::new(PlaybackProgressStore::new(
                fixture.temp.join("progress-provider-route-test.json"),
            )),
            HttpServerConfig {
                web_assets_root: None,
                host_mode: HOST_MODE_HEADLESS_SERVER.to_owned(),
                provider_settings: None,
                provider_runtime_status: None,
                external_provider_service: Some(service),
                authenticated_post_hooks: Vec::new(),
                dandanplay_resolver: None,
                catalog_metadata: None,
                poster_cache: None,
                provider_admin: None,
            },
        );
        app(state)
    }

    fn find_catalog_item<'a>(catalog: &'a Value, id: &str) -> &'a Value {
        catalog["items"]
            .as_array()
            .expect("catalog items")
            .iter()
            .find(|item| item["id"] == id)
            .expect("catalog item present")
    }

    fn test_resolver(
        fixture: &FixtureEnvironment,
        server: &MockDandanplayServer,
    ) -> Arc<DandanplayResolver> {
        let id = TEMP_COUNTER.fetch_add(1, Ordering::Relaxed);
        Arc::new(DandanplayResolver::new(
            DandanplayDanmakuClient::new(DandanplayConnection::new(
                server.base_url(),
                None,
                None,
                HeadlessDandanplayAuthenticationMode::Signed,
            )),
            DandanplayCommentCacheStore::new(
                fixture.temp.join(format!("dandanplay-cache-{id}.json")),
            ),
            30,
            || 2 * 24 * 60 * 60 * 1_000,
        ))
    }

    /// Polls `condition` until it is true or `timeout` elapses, for asserting
    /// on state set by a fire-and-forget background task.
    async fn wait_for(timeout: Duration, mut condition: impl FnMut() -> bool) -> bool {
        let deadline = tokio::time::Instant::now() + timeout;
        loop {
            if condition() {
                return true;
            }
            if tokio::time::Instant::now() >= deadline {
                return false;
            }
            tokio::time::sleep(Duration::from_millis(20)).await;
        }
    }

    /// A one-shot plain-HTTP server that returns `body` for any request, used
    /// to stand in for a provider's poster CDN in tests.
    fn start_test_image_server(body: &'static [u8]) -> String {
        let listener = TcpListener::bind("127.0.0.1:0").expect("mock image listener");
        let address = listener.local_addr().expect("mock image addr").to_string();
        thread::spawn(move || {
            for stream in listener.incoming().flatten() {
                serve_test_image(stream, body);
            }
        });
        thread::sleep(Duration::from_millis(25));
        format!("http://{address}/poster.jpg")
    }

    fn serve_test_image(mut stream: TcpStream, body: &[u8]) {
        let mut request = Vec::new();
        let mut chunk = [0_u8; 512];
        loop {
            let Ok(count) = stream.read(&mut chunk) else {
                return;
            };
            if count == 0 {
                return;
            }
            request.extend_from_slice(&chunk[..count]);
            if request.windows(4).any(|window| window == b"\r\n\r\n") {
                break;
            }
        }
        let _ = write!(
            stream,
            "HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",
            body.len(),
        );
        let _ = stream.write_all(body);
    }

    /// A fake external-provider search client that always matches with a
    /// fixed poster image URL, for exercising the poster-cache pipeline
    /// without a real MyAnimeList/Bangumi dependency.
    struct FixedAnimeSearchClient {
        image_url: String,
    }

    impl ExternalAnimeSearchClient for FixedAnimeSearchClient {
        fn provider(&self) -> crate::catalog::ExternalAnimeProvider {
            crate::catalog::ExternalAnimeProvider::Bangumi
        }

        fn search(
            &self,
            query: &ExternalAnimeMatchQuery,
            _limit: u32,
        ) -> crate::Result<Vec<ExternalAnimeInfo>> {
            Ok(vec![ExternalAnimeInfo {
                id: crate::catalog::ExternalAnimeId {
                    provider: crate::catalog::ExternalAnimeProvider::Bangumi,
                    value: 1,
                },
                titles: ExternalAnimeTitleSet {
                    primary: query.title.clone(),
                    chinese: None,
                    english: None,
                    japanese: None,
                    alternate_names: Vec::new(),
                },
                episode_count: None,
                start_year: None,
                image_url: Some(self.image_url.clone()),
                summary: None,
                external_links: Vec::new(),
            }])
        }
    }

    async fn request_json(app: &Router, path: &str) -> Value {
        let response = app.clone().oneshot(get(path)).await.expect("response");
        assert_eq!(StatusCode::OK, response.status(), "path {path}");
        let body = to_bytes(response.into_body(), 1_048_576)
            .await
            .expect("body");
        serde_json::from_slice::<Value>(&body).expect("json body")
    }

    async fn request_json_with_body(app: &Router, method: &str, path: &str, body: String) -> Value {
        let response = app
            .clone()
            .oneshot(
                Request::builder()
                    .method(method)
                    .uri(path)
                    .header("content-type", "application/json")
                    .body(Body::from(body))
                    .expect("request"),
            )
            .await
            .expect("response");
        assert_eq!(StatusCode::OK, response.status(), "path {path}");
        let body = to_bytes(response.into_body(), 1_048_576)
            .await
            .expect("body");
        serde_json::from_slice::<Value>(&body).expect("json body")
    }

    async fn assert_text_body(response: Response<Body>, expected: &str) {
        assert_eq!(expected, body_text(response).await);
    }

    async fn body_text(response: Response<Body>) -> String {
        let body = to_bytes(response.into_body(), 1_048_576)
            .await
            .expect("body");
        String::from_utf8(body.to_vec()).expect("utf8 body")
    }

    fn get(path: &str) -> Request<Body> {
        Request::builder()
            .method("GET")
            .uri(path)
            .body(Body::empty())
            .expect("request")
    }

    #[derive(Debug, Clone)]
    struct MockDandanplayBehavior {
        match_status: u16,
        comment_status: u16,
    }

    impl Default for MockDandanplayBehavior {
        fn default() -> Self {
            Self {
                match_status: 200,
                comment_status: 200,
            }
        }
    }

    #[derive(Clone, Debug)]
    struct MockDandanplayRequest {
        path: String,
        query: Option<String>,
    }

    struct MockDandanplayServer {
        address: String,
        requests: Arc<Mutex<Vec<MockDandanplayRequest>>>,
    }

    #[derive(Debug, Clone)]
    struct MockExternalProviderBehavior {
        mal_search_status: u16,
        bangumi_search_status: u16,
        bangumi_detail_status: u16,
        mal_list_read_status: u16,
        mal_list_write_status: u16,
        bangumi_list_read_status: u16,
        bangumi_list_write_status: u16,
    }

    impl Default for MockExternalProviderBehavior {
        fn default() -> Self {
            Self {
                mal_search_status: 200,
                bangumi_search_status: 200,
                bangumi_detail_status: 200,
                mal_list_read_status: 200,
                mal_list_write_status: 200,
                bangumi_list_read_status: 200,
                bangumi_list_write_status: 200,
            }
        }
    }

    #[derive(Clone, Debug)]
    struct MockExternalProviderRequest {
        method: String,
        path: String,
        query: Option<String>,
        headers: BTreeMap<String, String>,
        body: String,
    }

    struct MockExternalProviderServer {
        address: String,
        requests: Arc<Mutex<Vec<MockExternalProviderRequest>>>,
    }

    impl MockExternalProviderServer {
        fn start(behavior: MockExternalProviderBehavior) -> Self {
            let listener = TcpListener::bind("127.0.0.1:0").expect("mock bind");
            let address = listener.local_addr().expect("mock addr").to_string();
            let requests = Arc::new(Mutex::new(Vec::new()));
            let requests_for_thread = requests.clone();
            thread::spawn(move || {
                for stream in listener.incoming().flatten() {
                    let requests = requests_for_thread.clone();
                    let behavior = behavior.clone();
                    thread::spawn(move || {
                        handle_mock_external_provider_connection(stream, behavior, requests)
                    });
                }
            });
            thread::sleep(Duration::from_millis(25));
            Self { address, requests }
        }

        fn base_url(&self) -> String {
            format!("http://{}", self.address)
        }

        fn requests(&self) -> Vec<MockExternalProviderRequest> {
            self.requests.lock().expect("requests").clone()
        }
    }

    fn handle_mock_external_provider_connection(
        mut stream: TcpStream,
        behavior: MockExternalProviderBehavior,
        requests: Arc<Mutex<Vec<MockExternalProviderRequest>>>,
    ) {
        let request = read_full_mock_request(&mut stream);
        let (head, body) = request.split_once("\r\n\r\n").unwrap_or((&*request, ""));
        let mut lines = head.lines();
        let request_line = lines.next().unwrap_or_default();
        let mut request_parts = request_line.split_whitespace();
        let method = request_parts.next().unwrap_or("GET").to_owned();
        let target = request_parts.next().unwrap_or("/");
        let (path, query) = target
            .split_once('?')
            .map(|(path, query)| (path.to_owned(), Some(query.to_owned())))
            .unwrap_or((target.to_owned(), None));
        let headers = lines
            .filter_map(|line| {
                let (name, value) = line.split_once(':')?;
                Some((name.trim().to_ascii_lowercase(), value.trim().to_owned()))
            })
            .collect::<BTreeMap<_, _>>();
        requests
            .lock()
            .expect("requests")
            .push(MockExternalProviderRequest {
                method: method.clone(),
                path: path.clone(),
                query,
                headers,
                body: body.to_owned(),
            });

        let (status, body) = match (method.as_str(), path.as_str()) {
            ("GET", "/anime") => (
                behavior.mal_search_status,
                r#"{"data":[{"node":{"id":52991,"title":"Frieren","alternative_titles":{"en":"Frieren: Beyond Journey's End","ja":"葬送のフリーレン","synonyms":["Sousou no Frieren"]},"num_episodes":28,"start_date":"2023-09-29","main_picture":{"large":"https://img.example/mal.jpg"},"synopsis":"MAL summary"}}]}"#,
            ),
            ("POST", "/v0/search/subjects") => (
                behavior.bangumi_search_status,
                r#"{"data":[{"id":400602,"name":"葬送のフリーレン","name_cn":"葬送的芙莉莲","eps":28,"date":"2023-09-29","images":{"large":"https://img.example/bgm.jpg"},"summary":"Bangumi summary"}]}"#,
            ),
            ("GET", "/v0/subjects/400602") => (
                behavior.bangumi_detail_status,
                r#"{"id":400602,"name":"葬送のフリーレン","name_cn":"葬送的芙莉莲","eps":28,"date":"2023-09-29","images":{"large":"https://img.example/bgm-detail.jpg"},"summary":"Bangumi detail"}"#,
            ),
            ("GET", "/anime/52991") => (
                behavior.mal_list_read_status,
                r#"{"id":52991,"my_list_status":{"status":"watching","score":8,"num_episodes_watched":4,"updated_at":"2024-01-02T03:04:05+00:00"}}"#,
            ),
            ("PATCH", "/anime/52991/my_list_status") => (
                behavior.mal_list_write_status,
                r#"{"status":"watching","score":8,"num_episodes_watched":3}"#,
            ),
            ("GET", "/v0/users/-/collections/400602") => (
                behavior.bangumi_list_read_status,
                r#"{"type":3,"rate":9,"ep_status":12}"#,
            ),
            ("PATCH", "/v0/users/-/collections/400602") => (
                behavior.bangumi_list_write_status,
                r#"{"type":2,"rate":9,"ep_status":28}"#,
            ),
            _ => (404, r#"{"message":"not found"}"#),
        };
        let body = if status == 200 {
            body
        } else {
            r#"{"message":"mock failure"}"#
        };
        let status_text = match status {
            200 => "OK",
            404 => "Not Found",
            500 => "Internal Server Error",
            _ => "Error",
        };
        write!(
            stream,
            "HTTP/1.1 {status} {status_text}\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: {}\r\n\r\n{}",
            body.len(),
            body
        )
        .expect("mock write");
    }

    impl MockDandanplayServer {
        fn start(behavior: MockDandanplayBehavior) -> Self {
            let listener = TcpListener::bind("127.0.0.1:0").expect("mock bind");
            let address = listener.local_addr().expect("mock addr").to_string();
            let requests = Arc::new(Mutex::new(Vec::new()));
            let requests_for_thread = requests.clone();
            thread::spawn(move || {
                for stream in listener.incoming().flatten() {
                    let requests = requests_for_thread.clone();
                    let behavior = behavior.clone();
                    thread::spawn(move || {
                        handle_mock_dandanplay_connection(stream, behavior, requests)
                    });
                }
            });
            thread::sleep(Duration::from_millis(25));
            Self { address, requests }
        }

        fn base_url(&self) -> String {
            format!("http://{}", self.address)
        }

        fn requests(&self) -> Vec<MockDandanplayRequest> {
            self.requests.lock().expect("requests").clone()
        }

        fn count_path(&self, path: &str) -> usize {
            self.requests()
                .iter()
                .filter(|request| request.path == path)
                .count()
        }
    }

    // Reads a complete HTTP request (headers plus Content-Length body) from a
    // mock connection before the caller responds. Responding and closing while
    // request bytes are still in flight makes macOS send an RST that aborts
    // the client's response read, which flaked the multi-provider search test
    // on macOS CI.
    fn read_full_mock_request(stream: &mut TcpStream) -> String {
        let mut received = Vec::new();
        let mut chunk = [0_u8; 64 * 1024];
        let header_end = loop {
            let read = stream.read(&mut chunk).expect("mock read");
            if read == 0 {
                break received.len();
            }
            received.extend_from_slice(&chunk[..read]);
            if let Some(position) = received.windows(4).position(|window| window == b"\r\n\r\n") {
                break position + 4;
            }
        };
        let content_length = String::from_utf8_lossy(&received[..header_end])
            .lines()
            .find_map(|line| {
                let (name, value) = line.split_once(':')?;
                name.trim()
                    .eq_ignore_ascii_case("content-length")
                    .then(|| value.trim().parse::<usize>().ok())?
            })
            .unwrap_or(0);
        while received.len() < header_end + content_length {
            let read = stream.read(&mut chunk).expect("mock body read");
            if read == 0 {
                break;
            }
            received.extend_from_slice(&chunk[..read]);
        }
        String::from_utf8_lossy(&received).to_string()
    }

    fn handle_mock_dandanplay_connection(
        mut stream: TcpStream,
        behavior: MockDandanplayBehavior,
        requests: Arc<Mutex<Vec<MockDandanplayRequest>>>,
    ) {
        let request = read_full_mock_request(&mut stream);
        let request_line = request.lines().next().unwrap_or_default();
        let target = request_line.split_whitespace().nth(1).unwrap_or("/");
        let (path, query) = target
            .split_once('?')
            .map(|(path, query)| (path.to_owned(), Some(query.to_owned())))
            .unwrap_or((target.to_owned(), None));
        requests
            .lock()
            .expect("requests")
            .push(MockDandanplayRequest {
                path: path.clone(),
                query,
            });

        let (status, body) = match path.as_str() {
            "/api/v2/match" => (
                behavior.match_status,
                r#"{"success":true,"matches":[{"episodeId":111,"animeId":333,"animeTitle":"Example Anime","episodeTitle":"Episode 00"},{"episodeId":222,"animeId":333,"animeTitle":"Example Anime","episodeTitle":"Episode 01","shift":0.5}]}"#,
            ),
            "/api/v2/comment/111" | "/api/v2/comment/222" | "/api/v2/comment/9990002" => (
                behavior.comment_status,
                r#"{"success":true,"comments":[{"cid":"c-1","p":"1.5,1,25,16777215,0,0,user,row-1","m":"hello"},{"cid":"c-2","p":"2.0,5,18,16711680,0,0,user,row-2","m":"top"}]}"#,
            ),
            "/api/v2/search/episodes" => (
                200,
                r#"{"success":true,"animes":[{"animeId":999,"animeTitle":"Searched Anime","typeDescription":"TV Series","episodes":[{"episodeId":9990001,"episodeTitle":"Episode 1"},{"episodeId":9990002,"episodeTitle":"Episode 2"}]}]}"#,
            ),
            "/api/v2/bangumi/999" => (
                200,
                r#"{"success":true,"bangumi":{"animeId":999,"animeTitle":"Searched Anime","typeDescription":"TV Series","summary":"A town where half the residents have special powers.","rating":7.7,"isOnAir":false,"tags":[{"id":1,"name":"Mystery"},{"id":2,"name":"School"}],"episodes":[{"episodeId":9990001,"episodeTitle":"Episode 1","airDate":"2017-04-05T00:00:00"},{"episodeId":9990002,"episodeTitle":"Episode 2","airDate":"2017-04-12T00:00:00"}],"onlineDatabases":[{"name":"Bangumi.tv","url":"https://bangumi.tv/subject/179949"},{"name":"MyAnimeList","url":"https://myanimelist.net/anime/34102"}]}}"#,
            ),
            _ => (404, r#"{"success":false,"message":"not found"}"#),
        };
        let status_text = match status {
            200 => "OK",
            404 => "Not Found",
            500 => "Internal Server Error",
            _ => "Error",
        };
        write!(
            stream,
            "HTTP/1.1 {status} {status_text}\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: {}\r\n\r\n{}",
            body.len(),
            body
        )
        .expect("mock write");
    }

    fn request_from_fixture(fixture: &Value) -> Request<Body> {
        let request = &fixture["request"];
        let method = request["method"].as_str().expect("method");
        let path = request["path"].as_str().expect("path");
        let mut builder = Request::builder().method(method).uri(path);
        if let Some(headers) = request["headers"].as_object() {
            for (name, value) in headers {
                builder = builder.header(name.as_str(), value.as_str().expect("header value"));
            }
        }
        let body = request["body"]["text"]
            .as_str()
            .map(|text| Body::from(text.to_owned()))
            .unwrap_or_else(Body::empty);
        builder.body(body).expect("request should build")
    }

    async fn assert_response_matches_fixture(
        file_name: &str,
        response: Response<Body>,
        fixture: &Value,
    ) {
        let expected_response = &fixture["response"];
        let expected_status = expected_response["status"].as_u64().expect("status");
        assert_eq!(
            expected_status,
            response.status().as_u16() as u64,
            "fixture {file_name} status"
        );
        for (name, expected) in expected_response["headers"].as_object().expect("headers") {
            let actual = response
                .headers()
                .get(HeaderName::from_bytes(name.as_bytes()).expect("header name"))
                .unwrap_or_else(|| panic!("fixture {file_name} missing header {name}"));
            assert_eq!(
                HeaderValue::from_str(expected.as_str().expect("header value"))
                    .expect("expected header"),
                *actual,
                "fixture {file_name} header {name}"
            );
        }

        let body = to_bytes(response.into_body(), 1_048_576)
            .await
            .expect("body should read");
        let expected_body = &expected_response["body"];
        assert_eq!(
            expected_body["byteLength"].as_u64().expect("body length"),
            body.len() as u64,
            "fixture {file_name} body length"
        );
        if let Some(expected_json) = expected_body.get("json") {
            let actual_json = serde_json::from_slice::<Value>(&body)
                .unwrap_or_else(|error| panic!("fixture {file_name} body json: {error}"));
            assert_eq!(*expected_json, actual_json, "fixture {file_name} json body");
        } else if let Some(expected_text) = expected_body["text"].as_str() {
            assert_eq!(
                expected_text.as_bytes(),
                body.as_ref(),
                "fixture {file_name} text body"
            );
        } else if let Some(expected_hex) = expected_body["hex"].as_str() {
            assert_eq!(
                hex_decode(expected_hex),
                body.to_vec(),
                "fixture {file_name} binary body"
            );
        }
    }

    fn http_fixture_order() -> &'static [&'static str] {
        &[
            "server-status.json",
            "catalog.json",
            "pairing-token-auth-not-enforced.json",
            "media-full.json",
            "media-partial-range.json",
            "media-invalid-range.json",
            "subtitle-get.json",
            "subtitle-head.json",
            "poster-get.json",
            "poster-head.json",
            "progress-missing.json",
            "progress-put.json",
            "progress-get.json",
            "progress-list.json",
            "danmaku-unavailable.json",
            "web-redirect.json",
            "web-index.json",
            "web-asset.json",
            "webhook-auth-failure.json",
        ]
    }

    fn read_fixture(file_name: &str) -> Value {
        let path = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
            .join("..")
            .join("..")
            .join("shared")
            .join("library-server-core")
            .join("src")
            .join("jvmTest")
            .join("resources")
            .join("lan-protocol-fixtures")
            .join(file_name);
        serde_json::from_str(&fs::read_to_string(&path).expect("fixture should read"))
            .unwrap_or_else(|error| panic!("fixture {} should parse: {error}", path.display()))
    }

    fn hex_decode(value: &str) -> Vec<u8> {
        value
            .as_bytes()
            .chunks_exact(2)
            .map(|digits| {
                (hex_value(digits[0]).expect("hex") << 4) | hex_value(digits[1]).expect("hex")
            })
            .collect()
    }

    fn temp_dir(prefix: &str) -> PathBuf {
        let id = TEMP_COUNTER.fetch_add(1, Ordering::Relaxed);
        let path = std::env::temp_dir().join(format!("{prefix}-{}-{id}", std::process::id()));
        let _ = fs::remove_dir_all(&path);
        fs::create_dir_all(&path).expect("temp dir should create");
        path
    }

    #[test]
    fn fixture_status_keeps_embedded_defaults_omitted() {
        let status = LanLibraryServerStatus {
            web_ui_available: true,
            web_ui_path: Some("/web".to_owned()),
            ..LanLibraryServerStatus::default()
        };
        assert_eq!(
            json!({
                "webUiAvailable": true,
                "webUiPath": "/web"
            }),
            serde_json::to_value(status).expect("status")
        );
    }
}
