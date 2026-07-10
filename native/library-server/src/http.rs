use std::collections::{BTreeMap, BTreeSet};
use std::path::{Path, PathBuf};
use std::sync::Arc;

use axum::Router;
use axum::body::Body;
use axum::extract::State;
use axum::http::header::{
    ACCEPT, ACCEPT_RANGES, CACHE_CONTROL, CONTENT_LENGTH, CONTENT_RANGE, CONTENT_TYPE, HeaderValue,
    LOCATION,
};
use axum::http::{HeaderMap, Method, Request, Response, StatusCode};
use axum::routing::any;
use serde::Serialize;
use tokio::io::{AsyncReadExt, AsyncSeekExt};
use tokio_util::io::ReaderStream;

use crate::catalog::{PublishedLibrary, normalize_lexically};
use crate::dandanplay::{DandanplayResolver, LanDanmakuTrack};
use crate::domain::PlaybackProgress;
use crate::external_provider::{
    ExternalAnimeListEntry, ExternalAnimeMatchQuery, ExternalAnimeTrackingUpdate,
    ExternalProviderError, ExternalProviderService, parse_provider_alias, provider_runtime_status,
};
use crate::progress::PlaybackProgressStore;
use crate::settings::HeadlessServerSettings;

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
}

impl HttpServerConfig {
    pub fn headless(
        web_assets_root: Option<PathBuf>,
        settings: &HeadlessServerSettings,
        dandanplay_resolver: Option<Arc<DandanplayResolver>>,
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
        }
    }
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
        return handle_static_mapped_file(
            &state.library.poster_files_by_id,
            "/posters/",
            "private, max-age=3600",
            &method,
            &path,
        )
        .await;
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
    json_response(StatusCode::OK, &state.status)
}

fn handle_catalog(state: &HttpServerState, method: &Method) -> Response<Body> {
    if method != Method::GET {
        return empty_status(StatusCode::METHOD_NOT_ALLOWED);
    }
    json_response(StatusCode::OK, &state.library.catalog)
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

    let Some(resolver) = &state.dandanplay_resolver else {
        return json_response(StatusCode::OK, &LanDanmakuTrack::unavailable(media_id));
    };
    let force_refresh = parse_query_parameters(query)
        .get("forceRefresh")
        .is_some_and(|value| value.eq_ignore_ascii_case("true"));
    let track = match resolver
        .resolve(&media_id, path, None, true, force_refresh)
        .await
    {
        Ok(result) => LanDanmakuTrack::from_resolve_result(media_id, result),
        Err(error) => LanDanmakuTrack::failed(media_id, error),
    };
    json_response(StatusCode::OK, &track)
}

fn handle_provider_runtime(state: &HttpServerState, method: &Method, path: &str) -> Response<Body> {
    if method != Method::GET {
        return empty_status(StatusCode::METHOD_NOT_ALLOWED);
    }
    if path != "/api/providers/runtime" {
        return empty_status(StatusCode::NOT_FOUND);
    }
    let Some(runtime_status) = &state.provider_runtime_status else {
        return empty_status(StatusCode::NOT_FOUND);
    };
    json_response(StatusCode::OK, runtime_status)
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
    let Some(service) = &state.external_provider_service else {
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
    let Some(service) = &state.external_provider_service else {
        return text_response(
            StatusCode::CONFLICT,
            "Provider list sync credentials are not configured.",
        );
    };
    match method {
        Method::GET => handle_provider_list_read(service, query).await,
        Method::POST => handle_provider_list_write(service, body).await,
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
    let Some(path) = state.library.files_by_id.get(&media_id) else {
        return text_response(StatusCode::NOT_FOUND, "Media item was not found.");
    };
    if !path.is_file() {
        return text_response(StatusCode::NOT_FOUND, "Media file was not found.");
    }
    let Some(resolver) = &state.dandanplay_resolver else {
        return text_response(
            StatusCode::BAD_GATEWAY,
            "dandanplay request failed: Danmaku resolver is not available.",
        );
    };
    match resolver
        .resolve(&media_id, path, preferred_episode_id, with_related, false)
        .await
    {
        Ok(result) => json_response(StatusCode::OK, &result.to_provider_response(&media_id)),
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
        BangumiSearchClient, BangumiTrackingClient, ExternalProviderService,
        MyAnimeListSearchClient, MyAnimeListTrackingClient,
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
                external_provider_service: None,
                authenticated_post_hooks: Vec::new(),
                dandanplay_resolver: resolver,
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
            },
        );
        app(state)
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
            "/api/v2/comment/111" | "/api/v2/comment/222" => (
                behavior.comment_status,
                r#"{"success":true,"comments":[{"cid":"c-1","p":"1.5,1,25,16777215,0,0,user,row-1","m":"hello"},{"cid":"c-2","p":"2.0,5,18,16711680,0,0,user,row-2","m":"top"}]}"#,
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
