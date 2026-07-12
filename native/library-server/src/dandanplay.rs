use std::collections::{BTreeMap, HashMap};
use std::env;
use std::fs;
use std::io::{Read, Write};
use std::net::TcpStream;
use std::path::{Path, PathBuf};
use std::sync::Mutex;

use serde::{Deserialize, Serialize};
use serde_json::{Value, json};

use crate::catalog::{absolute_normalized_path, current_epoch_ms};
use crate::hash::{md5_hex, sha256_base64};
use crate::settings::{
    HeadlessDandanplayAuthenticationMode, HeadlessDandanplayProviderSettings,
    HeadlessServerSettings,
};
use crate::{LibraryServerError, Result};

pub const DEFAULT_DANDANPLAY_BASE_URL: &str = "https://api.dandanplay.net";
const HASH_PREFIX_BYTES: usize = 16 * 1024 * 1024;
const MAX_RESPONSE_BYTES: usize = 16 * 1024 * 1024;
const MAX_REDIRECTS: usize = 5;
const MILLIS_PER_DAY: u64 = 24 * 60 * 60 * 1_000;

#[derive(Debug)]
pub struct DandanplayResolver {
    client: DandanplayDanmakuClient,
    cache_store: DandanplayCommentCacheStore,
    cache_max_age_days: u32,
    now_epoch_ms: fn() -> u64,
}

impl DandanplayResolver {
    pub fn from_settings(settings: &HeadlessServerSettings, data_directory: &Path) -> Option<Self> {
        if !settings.dandanplay.is_fetch_enabled() {
            return None;
        }
        Some(Self::new(
            DandanplayDanmakuClient::new(DandanplayConnection::from(&settings.dandanplay)),
            DandanplayCommentCacheStore::new(data_directory.join("dandanplay-comment-cache.json")),
            settings.dandanplay.cache_max_age_days,
            current_epoch_ms,
        ))
    }

    pub fn new(
        client: DandanplayDanmakuClient,
        cache_store: DandanplayCommentCacheStore,
        cache_max_age_days: u32,
        now_epoch_ms: fn() -> u64,
    ) -> Self {
        Self {
            client,
            cache_store,
            cache_max_age_days,
            now_epoch_ms,
        }
    }

    pub async fn resolve(
        &self,
        media_id: &str,
        media_path: &Path,
        preferred_episode_id: Option<u64>,
        with_related: bool,
        force_refresh: bool,
    ) -> Result<DandanplayResolveResult> {
        if media_id.trim().is_empty() {
            return Err(LibraryServerError::new("mediaId must not be blank"));
        }
        let fingerprint = DandanplayMediaFingerprint::from_path(media_path)?;
        self.cleanup_expired_caches()?;
        if !force_refresh
            && preferred_episode_id.is_none()
            && let Some(cached) = self.resolve_cached(media_id, &fingerprint)?
        {
            return Ok(cached);
        }

        let matches = self.client.match_media(&fingerprint).await?;
        let selected_match = preferred_episode_id
            .and_then(|episode_id| {
                matches
                    .iter()
                    .find(|candidate| candidate.episode_id == episode_id)
                    .cloned()
            })
            .or_else(|| matches.first().cloned());
        let selected_track = match selected_match {
            Some(selected_match) => Some(DandanplayCommentTrack {
                events: self
                    .client
                    .fetch_comments(selected_match.episode_id, with_related)
                    .await?,
                match_candidate: selected_match,
            }),
            None => None,
        };
        let result = DandanplayResolveResult {
            fingerprint,
            match_candidates: matches,
            selected_track,
            source: LanDanmakuSource::Network,
            fetched_at_epoch_ms: (self.now_epoch_ms)(),
        };
        if let Some(cache) = result.to_cache(media_id) {
            self.cache_store.save(cache)?;
        }
        Ok(result)
    }

    fn resolve_cached(
        &self,
        media_id: &str,
        fingerprint: &DandanplayMediaFingerprint,
    ) -> Result<Option<DandanplayResolveResult>> {
        let Some(cache) = self.cache_store.load(media_id)? else {
            return Ok(None);
        };
        if !cache
            .file_hash
            .eq_ignore_ascii_case(&fingerprint.normalized_file_hash())
            || cache.file_size_bytes != fingerprint.file_size_bytes
            || cache.is_expired((self.now_epoch_ms)(), self.cache_max_age_days)
        {
            return Ok(None);
        }
        let Some(track) = cache.to_comment_track() else {
            return Ok(None);
        };
        Ok(Some(DandanplayResolveResult {
            fingerprint: fingerprint.clone(),
            match_candidates: vec![track.match_candidate.clone()],
            selected_track: Some(track),
            source: LanDanmakuSource::Cache,
            fetched_at_epoch_ms: cache.fetched_at_epoch_ms,
        }))
    }

    fn cleanup_expired_caches(&self) -> Result<()> {
        let cutoff =
            (self.now_epoch_ms)().saturating_sub(self.cache_max_age_days as u64 * MILLIS_PER_DAY);
        self.cache_store.delete_older_than(cutoff)
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DandanplayConnection {
    base_url: String,
    app_id: Option<String>,
    app_secret: Option<String>,
    authentication_mode: HeadlessDandanplayAuthenticationMode,
}

impl DandanplayConnection {
    pub fn new(
        base_url: impl Into<String>,
        app_id: Option<String>,
        app_secret: Option<String>,
        authentication_mode: HeadlessDandanplayAuthenticationMode,
    ) -> Self {
        Self {
            base_url: normalize_base_url(&base_url.into()),
            app_id: app_id.and_then(non_blank),
            app_secret: app_secret.and_then(non_blank),
            authentication_mode,
        }
    }

    fn has_credentials(&self) -> bool {
        self.app_id.is_some() && self.app_secret.is_some()
    }
}

impl From<&HeadlessDandanplayProviderSettings> for DandanplayConnection {
    fn from(settings: &HeadlessDandanplayProviderSettings) -> Self {
        Self::new(
            settings.base_url.clone(),
            settings.app_id.clone(),
            settings.app_secret.clone(),
            settings.authentication_mode,
        )
    }
}

#[derive(Debug, Clone)]
pub struct DandanplayDanmakuClient {
    connection: DandanplayConnection,
    now_epoch_seconds: fn() -> u64,
}

impl DandanplayDanmakuClient {
    pub fn new(connection: DandanplayConnection) -> Self {
        Self {
            connection,
            now_epoch_seconds: current_epoch_seconds,
        }
    }

    #[cfg(test)]
    fn with_clock(connection: DandanplayConnection, now_epoch_seconds: fn() -> u64) -> Self {
        Self {
            connection,
            now_epoch_seconds,
        }
    }

    pub async fn match_media(
        &self,
        fingerprint: &DandanplayMediaFingerprint,
    ) -> Result<Vec<DandanplayMatch>> {
        let data = self
            .request_json(
                "POST",
                "/api/v2/match",
                None,
                Some(fingerprint.to_match_request()),
            )
            .await?;
        if json_bool(&data, "success") == Some(false) {
            return Err(LibraryServerError::new(format!(
                "dandanplay match failed: {}",
                json_string(&data, "message").unwrap_or_else(|| "unknown error".to_owned())
            )));
        }
        let matches = data
            .get("matches")
            .and_then(Value::as_array)
            .into_iter()
            .flatten()
            .filter_map(DandanplayMatch::from_json)
            .collect();
        Ok(matches)
    }

    pub async fn fetch_comments(
        &self,
        episode_id: u64,
        with_related: bool,
    ) -> Result<Vec<DanmakuComment>> {
        if episode_id == 0 {
            return Err(LibraryServerError::new("episodeId must be positive"));
        }
        let api_path = format!("/api/v2/comment/{episode_id}");
        let query = with_related.then_some("withRelated=true");
        let data = self.request_json("GET", &api_path, query, None).await?;
        if json_bool(&data, "success") == Some(false) {
            return Err(LibraryServerError::new(format!(
                "dandanplay comment fetch failed: {}",
                json_string(&data, "message").unwrap_or_else(|| "unknown error".to_owned())
            )));
        }
        let comments = data
            .get("comments")
            .and_then(Value::as_array)
            .into_iter()
            .flatten()
            .enumerate()
            .filter_map(|(index, value)| DanmakuComment::from_dandanplay_json(index, value))
            .collect();
        Ok(comments)
    }

    async fn request_json(
        &self,
        method: &str,
        api_path: &str,
        query: Option<&str>,
        body: Option<Value>,
    ) -> Result<Value> {
        let mut url = endpoint_url(&self.connection.base_url, api_path, query)?;
        let body_text = body.map(|body| body.to_string());
        for redirect_count in 0..=MAX_REDIRECTS {
            let authenticate = redirect_count == 0;
            let request =
                self.http_request(method, api_path, &url, body_text.as_deref(), authenticate)?;
            let response = tokio::task::spawn_blocking(move || send_http_request(request))
                .await
                .map_err(|error| {
                    LibraryServerError::with_context(error, "dandanplay HTTP task failed")
                })??;
            if should_follow_redirect(method, body_text.as_deref(), response.status)
                && let Some(location) = response.headers.get("location")
            {
                if redirect_count >= MAX_REDIRECTS {
                    return Err(LibraryServerError::new(format!(
                        "dandanplay redirect limit exceeded for {}",
                        url.redacted()
                    )));
                }
                url = resolve_redirect(&url, location)?;
                continue;
            }
            if response.status != 200 {
                return Err(LibraryServerError::new(http_error_message(
                    response.status,
                    &url,
                    response.headers.get("location"),
                    &response.body,
                )));
            }
            return serde_json::from_slice(&response.body).map_err(|error| {
                LibraryServerError::with_context(error, "dandanplay response was not JSON")
            });
        }
        Err(LibraryServerError::new(format!(
            "dandanplay redirect limit exceeded for {}",
            url.redacted()
        )))
    }

    fn http_request(
        &self,
        method: &str,
        api_path: &str,
        url: &ParsedUrl,
        body: Option<&str>,
        authenticate: bool,
    ) -> Result<HttpRequest> {
        let mut headers = BTreeMap::from([("Accept".to_owned(), "application/json".to_owned())]);
        if let Some(body) = body {
            headers.insert(
                "Content-Type".to_owned(),
                "application/json; charset=utf-8".to_owned(),
            );
            headers.insert("Content-Length".to_owned(), body.len().to_string());
        }
        if authenticate && self.connection.has_credentials() {
            let app_id = self.connection.app_id.as_deref().unwrap_or_default();
            let app_secret = self.connection.app_secret.as_deref().unwrap_or_default();
            headers.insert("X-AppId".to_owned(), app_id.to_owned());
            match self.connection.authentication_mode {
                HeadlessDandanplayAuthenticationMode::Credential => {
                    headers.insert("X-AppSecret".to_owned(), app_secret.to_owned());
                }
                HeadlessDandanplayAuthenticationMode::Signed => {
                    let timestamp = (self.now_epoch_seconds)();
                    headers.insert("X-Timestamp".to_owned(), timestamp.to_string());
                    headers.insert(
                        "X-Signature".to_owned(),
                        generate_signature(
                            app_id,
                            timestamp,
                            &api_path.to_ascii_lowercase(),
                            app_secret,
                        ),
                    );
                }
            }
        }
        Ok(HttpRequest {
            method: method.to_owned(),
            url: url.clone(),
            headers,
            body: body.unwrap_or_default().as_bytes().to_vec(),
        })
    }
}

pub fn generate_signature(
    app_id: &str,
    timestamp: u64,
    api_path: &str,
    app_secret: &str,
) -> String {
    sha256_base64(&format!(
        "{app_id}{timestamp}{}{app_secret}",
        api_path.to_ascii_lowercase()
    ))
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DandanplayMediaFingerprint {
    pub file_name: String,
    pub file_hash: String,
    pub file_size_bytes: u64,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub video_duration_seconds: Option<u64>,
}

impl DandanplayMediaFingerprint {
    pub fn from_path(path: &Path) -> Result<Self> {
        if !path.is_file() {
            return Err(LibraryServerError::new(format!(
                "media path must be a file: {}",
                path.display()
            )));
        }
        let mut file = fs::File::open(path).map_err(|error| {
            LibraryServerError::with_context(
                error,
                format!("failed to open media for fingerprint {}", path.display()),
            )
        })?;
        let mut remaining = HASH_PREFIX_BYTES;
        let mut buffer = [0_u8; 64 * 1024];
        let mut prefix = Vec::with_capacity(HASH_PREFIX_BYTES.min(64 * 1024));
        while remaining > 0 {
            let read_size = remaining.min(buffer.len());
            let read = file.read(&mut buffer[..read_size]).map_err(|error| {
                LibraryServerError::with_context(
                    error,
                    format!("failed to read media fingerprint {}", path.display()),
                )
            })?;
            if read == 0 {
                break;
            }
            prefix.extend_from_slice(&buffer[..read]);
            remaining -= read;
        }
        Ok(Self {
            file_name: path
                .file_name()
                .map(|name| name.to_string_lossy().into_owned())
                .filter(|name| !name.trim().is_empty())
                .ok_or_else(|| {
                    LibraryServerError::new(format!(
                        "media path must include a file name: {}",
                        path.display()
                    ))
                })?,
            file_hash: md5_hex(&prefix),
            file_size_bytes: fs::metadata(path)?.len(),
            video_duration_seconds: None,
        })
    }

    pub fn normalized_file_hash(&self) -> String {
        self.file_hash.to_ascii_lowercase()
    }

    fn to_match_request(&self) -> Value {
        let mut body = json!({
            "fileName": self.file_name,
            "fileHash": self.normalized_file_hash(),
            "fileSize": self.file_size_bytes,
            "matchMode": "hashAndFileName"
        });
        if let Some(duration) = self.video_duration_seconds {
            body["videoDuration"] = json!(duration);
        }
        body
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DandanplayMatch {
    pub episode_id: u64,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub anime_id: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub anime_title: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub episode_title: Option<String>,
    #[serde(rename = "shiftSeconds", skip_serializing_if = "Option::is_none")]
    pub shift_seconds: Option<f64>,
    pub display_title: String,
}

impl DandanplayMatch {
    fn new(
        episode_id: u64,
        anime_id: Option<u64>,
        anime_title: Option<String>,
        episode_title: Option<String>,
        shift_seconds: Option<f64>,
    ) -> Self {
        let display_title = [anime_title.as_deref(), episode_title.as_deref()]
            .into_iter()
            .flatten()
            .collect::<Vec<_>>()
            .join(" - ");
        Self {
            episode_id,
            anime_id,
            anime_title,
            episode_title,
            shift_seconds,
            display_title: if display_title.trim().is_empty() {
                episode_id.to_string()
            } else {
                display_title
            },
        }
    }

    fn from_json(value: &Value) -> Option<Self> {
        Some(Self::new(
            json_u64_any(value, &["episodeId", "EpisodeId"])?,
            json_u64_any(value, &["animeId", "AnimeId"]),
            json_string_any(value, &["animeTitle", "AnimeTitle"]),
            json_string_any(value, &["episodeTitle", "EpisodeTitle"]),
            json_f64_any(value, &["shift", "Shift"]),
        ))
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DanmakuComment {
    pub id: String,
    pub timestamp_ms: u64,
    pub text: String,
    pub style: DanmakuStyle,
}

impl DanmakuComment {
    fn from_dandanplay_json(index: usize, value: &Value) -> Option<Self> {
        let parameter = json_string_any(value, &["p", "P", "parameter"])?;
        let text = json_string_any(value, &["m", "M", "text", "Text"])?;
        let fallback_id = json_string_any(value, &["cid", "id", "Id"])
            .unwrap_or_else(|| format!("dandanplay-{index}"));
        parse_bilibili_parameter_string(&parameter, &text, &fallback_id)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum DanmakuMode {
    Scrolling,
    Top,
    Bottom,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum DanmakuSize {
    Small,
    Normal,
    Large,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DanmakuStyle {
    pub color_argb: u32,
    pub mode: DanmakuMode,
    pub size: DanmakuSize,
}

impl Default for DanmakuStyle {
    fn default() -> Self {
        Self {
            color_argb: 0xffff_ffff,
            mode: DanmakuMode::Scrolling,
            size: DanmakuSize::Normal,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LanDanmakuTrack {
    pub media_id: String,
    pub status: LanDanmakuLoadStatus,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub source: Option<LanDanmakuSource>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub comments: Vec<DanmakuComment>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub match_title: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub episode_id: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub fetched_at_epoch_ms: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub message: Option<String>,
}

impl LanDanmakuTrack {
    pub fn unavailable(media_id: String) -> Self {
        Self {
            media_id,
            status: LanDanmakuLoadStatus::Unavailable,
            source: None,
            comments: Vec::new(),
            match_title: None,
            episode_id: None,
            fetched_at_epoch_ms: None,
            message: Some("Danmaku resolver is not available.".to_owned()),
        }
    }

    pub fn failed(media_id: String, error: impl ToString) -> Self {
        let message = error.to_string();
        Self {
            media_id,
            status: LanDanmakuLoadStatus::Failed,
            source: None,
            comments: Vec::new(),
            match_title: None,
            episode_id: None,
            fetched_at_epoch_ms: None,
            message: Some(if message.trim().is_empty() {
                "Danmaku resolution failed.".to_owned()
            } else {
                message
            }),
        }
    }

    pub fn from_resolve_result(media_id: String, result: DandanplayResolveResult) -> Self {
        let track = result.selected_track;
        let comments = track
            .as_ref()
            .map(|track| track.events.clone())
            .unwrap_or_default();
        let status = if comments.is_empty() {
            LanDanmakuLoadStatus::NoMatch
        } else {
            LanDanmakuLoadStatus::Ready
        };
        let message = match (&track, comments.is_empty()) {
            (_, false) => None,
            (None, true) => Some("No Dandanplay match found.".to_owned()),
            (Some(_), true) => Some("Dandanplay match has no comments.".to_owned()),
        };
        Self {
            media_id,
            status,
            source: Some(result.source),
            comments,
            match_title: track
                .as_ref()
                .map(|track| track.match_candidate.display_title.clone()),
            episode_id: track.as_ref().map(|track| track.match_candidate.episode_id),
            fetched_at_epoch_ms: Some(result.fetched_at_epoch_ms),
            message,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum LanDanmakuLoadStatus {
    Ready,
    NoMatch,
    Unavailable,
    Failed,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum LanDanmakuSource {
    Cache,
    Network,
}

#[derive(Debug, Clone, PartialEq)]
pub struct DandanplayCommentTrack {
    pub match_candidate: DandanplayMatch,
    pub events: Vec<DanmakuComment>,
}

#[derive(Debug, Clone, PartialEq)]
pub struct DandanplayResolveResult {
    pub fingerprint: DandanplayMediaFingerprint,
    pub match_candidates: Vec<DandanplayMatch>,
    pub selected_track: Option<DandanplayCommentTrack>,
    pub source: LanDanmakuSource,
    pub fetched_at_epoch_ms: u64,
}

impl DandanplayResolveResult {
    pub fn to_provider_response(&self, media_id: &str) -> DandanplayResolveResponse {
        DandanplayResolveResponse {
            media_id: media_id.to_owned(),
            fingerprint: self.fingerprint.clone(),
            matches: self.match_candidates.clone(),
            selected_match: self
                .selected_track
                .as_ref()
                .map(|track| track.match_candidate.clone()),
            comment_count: self
                .selected_track
                .as_ref()
                .map(|track| track.events.len())
                .unwrap_or(0),
            comments: self
                .selected_track
                .as_ref()
                .map(|track| {
                    track
                        .events
                        .iter()
                        .cloned()
                        .map(DandanplayResolveComment::from)
                        .collect()
                })
                .unwrap_or_default(),
        }
    }

    fn to_cache(&self, media_id: &str) -> Option<DandanplayCommentCache> {
        let track = self.selected_track.as_ref()?;
        Some(DandanplayCommentCache {
            media_id: media_id.to_owned(),
            file_hash: self.fingerprint.normalized_file_hash(),
            file_name: self.fingerprint.file_name.clone(),
            file_size_bytes: self.fingerprint.file_size_bytes,
            episode_id: Some(track.match_candidate.episode_id),
            anime_id: track.match_candidate.anime_id,
            anime_title: track.match_candidate.anime_title.clone(),
            episode_title: track.match_candidate.episode_title.clone(),
            shift_seconds: track.match_candidate.shift_seconds,
            comments_json: normalized_comments_json(&track.events),
            rendered_ass_path: None,
            fetched_at_epoch_ms: self.fetched_at_epoch_ms,
        })
    }
}

#[derive(Debug, Clone, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DandanplayResolveResponse {
    pub media_id: String,
    pub fingerprint: DandanplayMediaFingerprint,
    pub matches: Vec<DandanplayMatch>,
    pub selected_match: Option<DandanplayMatch>,
    pub comment_count: usize,
    pub comments: Vec<DandanplayResolveComment>,
}

#[derive(Debug, Clone, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DandanplayResolveComment {
    pub id: String,
    pub timestamp_ms: u64,
    pub text: String,
    pub style: DandanplayResolveCommentStyle,
}

impl From<DanmakuComment> for DandanplayResolveComment {
    fn from(value: DanmakuComment) -> Self {
        Self {
            id: value.id,
            timestamp_ms: value.timestamp_ms,
            text: value.text,
            style: DandanplayResolveCommentStyle {
                color_argb: value.style.color_argb.to_string(),
                mode: value.style.mode,
                size: value.style.size,
            },
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DandanplayResolveCommentStyle {
    pub color_argb: String,
    pub mode: DanmakuMode,
    pub size: DanmakuSize,
}

#[derive(Debug)]
pub struct DandanplayCommentCacheStore {
    file: PathBuf,
    entries: Mutex<BTreeMap<String, DandanplayCommentCache>>,
}

impl DandanplayCommentCacheStore {
    pub fn new(file: impl Into<PathBuf>) -> Self {
        let file = file.into();
        let entries = load_cache_snapshot(&file).unwrap_or_default();
        Self {
            file,
            entries: Mutex::new(entries),
        }
    }

    pub fn load(&self, media_id: &str) -> Result<Option<DandanplayCommentCache>> {
        Ok(self
            .entries
            .lock()
            .map_err(|_| LibraryServerError::new("dandanplay cache lock poisoned"))?
            .get(media_id)
            .cloned())
    }

    pub fn save(&self, cache: DandanplayCommentCache) -> Result<()> {
        let mut entries = self
            .entries
            .lock()
            .map_err(|_| LibraryServerError::new("dandanplay cache lock poisoned"))?;
        entries.insert(cache.media_id.clone(), cache);
        write_cache_snapshot(&self.file, &entries)
    }

    pub fn delete_older_than(&self, cutoff_epoch_ms: u64) -> Result<()> {
        let mut entries = self
            .entries
            .lock()
            .map_err(|_| LibraryServerError::new("dandanplay cache lock poisoned"))?;
        let original_len = entries.len();
        entries.retain(|_, cache| cache.fetched_at_epoch_ms >= cutoff_epoch_ms);
        if entries.len() != original_len {
            write_cache_snapshot(&self.file, &entries)?;
        }
        Ok(())
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DandanplayCommentCache {
    pub media_id: String,
    pub file_hash: String,
    pub file_name: String,
    pub file_size_bytes: u64,
    pub episode_id: Option<u64>,
    pub anime_id: Option<u64>,
    pub anime_title: Option<String>,
    pub episode_title: Option<String>,
    pub shift_seconds: Option<f64>,
    pub comments_json: String,
    pub rendered_ass_path: Option<String>,
    pub fetched_at_epoch_ms: u64,
}

impl DandanplayCommentCache {
    fn is_expired(&self, now_epoch_ms: u64, max_age_days: u32) -> bool {
        now_epoch_ms.saturating_sub(self.fetched_at_epoch_ms) > max_age_days as u64 * MILLIS_PER_DAY
    }

    fn to_comment_track(&self) -> Option<DandanplayCommentTrack> {
        let episode_id = self.episode_id?;
        Some(DandanplayCommentTrack {
            match_candidate: DandanplayMatch::new(
                episode_id,
                self.anime_id,
                self.anime_title.clone(),
                self.episode_title.clone(),
                self.shift_seconds,
            ),
            events: parse_normalized_comments_json(&self.comments_json),
        })
    }
}

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DandanplayCommentCacheSnapshot {
    schema_version: u32,
    entries: Vec<DandanplayCommentCache>,
}

fn load_cache_snapshot(file: &Path) -> Option<BTreeMap<String, DandanplayCommentCache>> {
    if !file.is_file() {
        return Some(BTreeMap::new());
    }
    let snapshot =
        serde_json::from_str::<DandanplayCommentCacheSnapshot>(&fs::read_to_string(file).ok()?)
            .ok()?;
    if snapshot.schema_version != 1 {
        return None;
    }
    Some(
        snapshot
            .entries
            .into_iter()
            .filter(|entry| !entry.media_id.trim().is_empty())
            .map(|entry| (entry.media_id.clone(), entry))
            .collect(),
    )
}

fn write_cache_snapshot(
    file: &Path,
    entries: &BTreeMap<String, DandanplayCommentCache>,
) -> Result<()> {
    if let Some(parent) = file.parent() {
        fs::create_dir_all(parent).map_err(|error| {
            LibraryServerError::with_context(
                error,
                format!(
                    "failed to create dandanplay cache directory {}",
                    parent.display()
                ),
            )
        })?;
    }
    let file_name = file
        .file_name()
        .ok_or_else(|| {
            LibraryServerError::new(format!(
                "dandanplay cache path must include a file name: {}",
                file.display()
            ))
        })?
        .to_string_lossy();
    let temp = file.with_file_name(format!("{file_name}.tmp"));
    let snapshot = DandanplayCommentCacheSnapshot {
        schema_version: 1,
        entries: entries.values().cloned().collect(),
    };
    fs::write(&temp, serde_json::to_string_pretty(&snapshot)?).map_err(|error| {
        LibraryServerError::with_context(
            error,
            format!("failed to write dandanplay cache {}", temp.display()),
        )
    })?;
    fs::rename(&temp, file).map_err(|error| {
        LibraryServerError::with_context(
            error,
            format!("failed to replace dandanplay cache {}", file.display()),
        )
    })
}

pub fn apply_dandanplay_local_defaults(
    mut settings: HeadlessServerSettings,
) -> HeadlessServerSettings {
    let defaults = DandanplayLocalCredentialDefaults::load_from_process();
    settings.dandanplay = merge_dandanplay_settings(settings.dandanplay, defaults);
    settings
}

fn merge_dandanplay_settings(
    mut settings: HeadlessDandanplayProviderSettings,
    defaults: Option<DandanplayLocalCredentialDefaults>,
) -> HeadlessDandanplayProviderSettings {
    let Some(defaults) = defaults else {
        return settings;
    };
    if settings.app_secret.is_none()
        && settings.has_app_secret
        && settings.app_id.is_some()
        && defaults.app_secret.is_some()
    {
        settings.app_secret = defaults.app_secret.clone();
    }
    if settings.app_id.is_none()
        && settings.app_secret.is_none()
        && !settings.has_app_secret
        && settings.base_url == DEFAULT_DANDANPLAY_BASE_URL
    {
        settings.base_url = defaults.base_url;
        settings.app_id = defaults.app_id;
        settings.app_secret = defaults.app_secret;
        settings.has_app_secret = settings.app_secret.is_some();
        settings.authentication_mode = defaults.authentication_mode;
        if let Some(cache_max_age_days) = defaults.cache_max_age_days {
            settings.cache_max_age_days = cache_max_age_days;
        }
    }
    settings
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DandanplayLocalCredentialDefaults {
    pub base_url: String,
    pub proxy_base_url: Option<String>,
    pub app_id: Option<String>,
    pub app_secret: Option<String>,
    pub authentication_mode: HeadlessDandanplayAuthenticationMode,
    pub cache_max_age_days: Option<u32>,
}

impl DandanplayLocalCredentialDefaults {
    pub fn load_from_process() -> Option<Self> {
        Self::load(&env::vars().collect(), None)
    }

    pub fn load(
        environment: &HashMap<String, String>,
        properties_path: Option<&Path>,
    ) -> Option<Self> {
        let properties = load_local_properties(environment, properties_path);
        let value = |property_name: &str, environment_name: &str| -> Option<String> {
            environment
                .get(environment_name)
                .or_else(|| properties.get(property_name))
                .and_then(|value| non_blank(value.clone()))
        };

        let base_url = value("danmaku.dandanplay.baseUrl", "DANMAKU_DANDANPLAY_BASE_URL")
            .unwrap_or_else(|| DEFAULT_DANDANPLAY_BASE_URL.to_owned());
        let proxy_base_url = value(
            "danmaku.dandanplay.proxyBaseUrl",
            "DANMAKU_DANDANPLAY_PROXY_BASE_URL",
        );
        let app_id = value("danmaku.dandanplay.appId", "DANMAKU_DANDANPLAY_APP_ID");
        let app_secret = value(
            "danmaku.dandanplay.appSecret",
            "DANMAKU_DANDANPLAY_APP_SECRET",
        );
        let authentication_mode = value(
            "danmaku.dandanplay.authenticationMode",
            "DANMAKU_DANDANPLAY_AUTHENTICATION_MODE",
        )
        .as_deref()
        .map(authentication_mode_or_default)
        .unwrap_or(HeadlessDandanplayAuthenticationMode::Signed);
        let cache_max_age_days = value(
            "danmaku.dandanplay.cacheMaxAgeDays",
            "DANMAKU_DANDANPLAY_CACHE_MAX_AGE_DAYS",
        )
        .and_then(|value| value.parse::<u32>().ok())
        .filter(|value| *value >= 1);
        let has_direct_credentials = app_id.is_some() && app_secret.is_some();
        let effective_base_url = if has_direct_credentials {
            base_url.clone()
        } else {
            proxy_base_url.clone().unwrap_or_else(|| base_url.clone())
        };

        if app_id.is_none()
            && app_secret.is_none()
            && proxy_base_url.is_none()
            && base_url == DEFAULT_DANDANPLAY_BASE_URL
        {
            return None;
        }
        Some(Self {
            base_url: effective_base_url,
            proxy_base_url,
            app_id,
            app_secret,
            authentication_mode,
            cache_max_age_days,
        })
    }
}

fn load_local_properties(
    environment: &HashMap<String, String>,
    properties_path: Option<&Path>,
) -> HashMap<String, String> {
    let mut values = HashMap::new();
    let paths = properties_path
        .map(|path| vec![path.to_path_buf()])
        .unwrap_or_else(|| default_local_properties_paths(environment));
    for path in paths {
        let Ok(text) = fs::read_to_string(path) else {
            continue;
        };
        for line in text.lines() {
            let trimmed = line.trim();
            if trimmed.is_empty() || trimmed.starts_with('#') || trimmed.starts_with('!') {
                continue;
            }
            let Some((key, value)) = trimmed.split_once('=').or_else(|| trimmed.split_once(':'))
            else {
                continue;
            };
            if let Some(value) = non_blank(value.to_owned()) {
                values.insert(key.trim().to_owned(), value);
            }
        }
    }
    values
}

// Auto-discover local.properties from the working directory and user profile,
// matching both the Kotlin desktop app and the external-anime credential path in
// settings.rs. This lets a user who configured dandanplay via local.properties
// get working danmaku without duplicating credentials into server-settings.json.
fn default_local_properties_paths(environment: &HashMap<String, String>) -> Vec<PathBuf> {
    let mut paths = vec![
        env::current_dir()
            .unwrap_or_else(|_| PathBuf::from("."))
            .join("local.properties"),
    ];
    if let Some(local_app_data) = environment
        .get("LOCALAPPDATA")
        .and_then(|value| non_blank(value.clone()))
    {
        paths.push(
            PathBuf::from(local_app_data)
                .join("Danmaku")
                .join("local.properties"),
        );
    }
    if let Some(home) = environment
        .get("USERPROFILE")
        .or_else(|| environment.get("HOME"))
        .and_then(|value| non_blank(value.clone()))
    {
        paths.push(
            PathBuf::from(home)
                .join(".danmaku")
                .join("local.properties"),
        );
    }
    if let Some(path) = environment
        .get("DANMAKU_LOCAL_PROPERTIES")
        .and_then(|value| non_blank(value.clone()))
    {
        paths.push(PathBuf::from(path));
    }
    paths.sort();
    paths.dedup();
    paths
}

fn parse_bilibili_parameter_string(
    parameter: &str,
    text: &str,
    fallback_id: &str,
) -> Option<DanmakuComment> {
    let parts = parameter.split(',').map(str::trim).collect::<Vec<_>>();
    let timestamp_ms = parts
        .first()
        .and_then(|value| value.parse::<f64>().ok())
        .filter(|value| *value >= 0.0)
        .map(|value| (value * 1_000.0) as u64)?;
    let text = non_blank(text.to_owned())?;
    Some(DanmakuComment {
        id: parts
            .get(7)
            .and_then(|value| non_blank((*value).to_owned()))
            .unwrap_or_else(|| fallback_id.to_owned()),
        timestamp_ms,
        text,
        style: DanmakuStyle {
            color_argb: parts
                .get(3)
                .and_then(|value| parse_argb_color(value))
                .unwrap_or(0xffff_ffff),
            mode: parts
                .get(1)
                .and_then(|value| parse_bilibili_mode(value))
                .unwrap_or(DanmakuMode::Scrolling),
            size: parts
                .get(2)
                .and_then(|value| parse_danmaku_size(value))
                .unwrap_or(DanmakuSize::Normal),
        },
    })
}

fn normalized_comments_json(comments: &[DanmakuComment]) -> String {
    json!({
        "events": comments.iter().map(|comment| {
            json!({
                "id": comment.id,
                "timestampMs": comment.timestamp_ms,
                "text": comment.text,
                "style": {
                    "colorArgb": comment.style.color_argb.to_string(),
                    "mode": normalized_mode(comment.style.mode),
                    "size": normalized_size(comment.style.size)
                }
            })
        }).collect::<Vec<_>>()
    })
    .to_string()
}

fn parse_normalized_comments_json(source: &str) -> Vec<DanmakuComment> {
    let Ok(root) = serde_json::from_str::<Value>(source) else {
        return Vec::new();
    };
    let items = match &root {
        Value::Array(items) => items.as_slice(),
        Value::Object(object) => object
            .get("events")
            .and_then(Value::as_array)
            .map(Vec::as_slice)
            .unwrap_or(&[]),
        _ => &[],
    };
    items
        .iter()
        .enumerate()
        .filter_map(|(index, item)| {
            let text = json_string(item, "text").and_then(non_blank)?;
            let timestamp_ms = json_u64(item, "timestampMs")
                .or_else(|| json_u64(item, "timeMs"))
                .or_else(|| {
                    json_f64(item, "time")
                        .filter(|value| *value >= 0.0)
                        .map(|value| (value * 1_000.0) as u64)
                })?;
            let style = item.get("style").unwrap_or(item);
            Some(DanmakuComment {
                id: json_string(item, "id")
                    .and_then(non_blank)
                    .unwrap_or_else(|| format!("json-{index}")),
                timestamp_ms,
                text,
                style: DanmakuStyle {
                    color_argb: json_string(style, "colorArgb")
                        .or_else(|| json_string(style, "color"))
                        .as_deref()
                        .and_then(parse_argb_color)
                        .unwrap_or(0xffff_ffff),
                    mode: json_string(style, "mode")
                        .as_deref()
                        .and_then(parse_normalized_mode)
                        .unwrap_or(DanmakuMode::Scrolling),
                    size: json_string(style, "size")
                        .as_deref()
                        .and_then(parse_normalized_size)
                        .unwrap_or(DanmakuSize::Normal),
                },
            })
        })
        .collect()
}

fn parse_argb_color(value: &str) -> Option<u32> {
    let trimmed = value.trim();
    if let Some(hex) = trimmed.strip_prefix('#') {
        return u32::from_str_radix(hex, 16)
            .ok()
            .filter(|value| *value <= 0x00ff_ffff)
            .map(|value| 0xff00_0000 | value);
    }
    trimmed
        .parse::<u64>()
        .ok()
        .filter(|value| *value <= 0xffff_ffff)
        .map(|value| {
            if value <= 0x00ff_ffff {
                0xff00_0000 | value as u32
            } else {
                value as u32
            }
        })
}

fn parse_bilibili_mode(value: &str) -> Option<DanmakuMode> {
    match value.parse::<u32>().ok()? {
        4 => Some(DanmakuMode::Bottom),
        5 => Some(DanmakuMode::Top),
        1..=3 => Some(DanmakuMode::Scrolling),
        _ => None,
    }
}

fn parse_danmaku_size(value: &str) -> Option<DanmakuSize> {
    match value.to_ascii_uppercase().as_str() {
        "SMALL" => Some(DanmakuSize::Small),
        "NORMAL" => Some(DanmakuSize::Normal),
        "LARGE" => Some(DanmakuSize::Large),
        _ => {
            let size = value.parse::<i64>().ok()?;
            Some(match size.cmp(&25) {
                std::cmp::Ordering::Less => DanmakuSize::Small,
                std::cmp::Ordering::Equal => DanmakuSize::Normal,
                std::cmp::Ordering::Greater => DanmakuSize::Large,
            })
        }
    }
}

fn parse_normalized_mode(value: &str) -> Option<DanmakuMode> {
    match value.to_ascii_uppercase().as_str() {
        "SCROLLING" | "SCROLL" | "ROLLING" => Some(DanmakuMode::Scrolling),
        "TOP" => Some(DanmakuMode::Top),
        "BOTTOM" => Some(DanmakuMode::Bottom),
        _ => None,
    }
}

fn parse_normalized_size(value: &str) -> Option<DanmakuSize> {
    parse_danmaku_size(value)
}

fn normalized_mode(value: DanmakuMode) -> &'static str {
    match value {
        DanmakuMode::Scrolling => "scrolling",
        DanmakuMode::Top => "top",
        DanmakuMode::Bottom => "bottom",
    }
}

fn normalized_size(value: DanmakuSize) -> &'static str {
    match value {
        DanmakuSize::Small => "small",
        DanmakuSize::Normal => "normal",
        DanmakuSize::Large => "large",
    }
}

#[derive(Debug, Clone)]
pub(crate) struct HttpRequest {
    pub(crate) method: String,
    pub(crate) url: ParsedUrl,
    pub(crate) headers: BTreeMap<String, String>,
    pub(crate) body: Vec<u8>,
}

#[derive(Debug, Clone)]
pub(crate) struct HttpResponse {
    pub(crate) status: u16,
    pub(crate) headers: BTreeMap<String, String>,
    pub(crate) body: Vec<u8>,
}

pub(crate) fn send_http_request(request: HttpRequest) -> Result<HttpResponse> {
    if request.url.scheme == "http" {
        return send_plain_http_request(request);
    }
    #[cfg(windows)]
    {
        send_winhttp_request(request)
    }
    #[cfg(not(windows))]
    {
        Err(LibraryServerError::new(
            "HTTPS dandanplay requests are only supported by the Windows server build",
        ))
    }
}

fn send_plain_http_request(request: HttpRequest) -> Result<HttpResponse> {
    let mut stream =
        TcpStream::connect((request.url.host.as_str(), request.url.port)).map_err(|error| {
            LibraryServerError::with_context(
                error,
                format!(
                    "failed to connect to dandanplay endpoint {}",
                    request.url.redacted()
                ),
            )
        })?;
    let host = if request.url.is_default_port() {
        request.url.host.clone()
    } else {
        format!("{}:{}", request.url.host, request.url.port)
    };
    let mut head = format!(
        "{} {} HTTP/1.1\r\nHost: {host}\r\nConnection: close\r\n",
        request.method, request.url.path_and_query
    );
    for (name, value) in &request.headers {
        head.push_str(name);
        head.push_str(": ");
        head.push_str(value);
        head.push_str("\r\n");
    }
    head.push_str("\r\n");
    stream.write_all(head.as_bytes())?;
    if !request.body.is_empty() {
        stream.write_all(&request.body)?;
    }

    let mut bytes = Vec::new();
    stream.read_to_end(&mut bytes)?;
    parse_http_response(bytes)
}

#[cfg(windows)]
fn send_winhttp_request(request: HttpRequest) -> Result<HttpResponse> {
    use std::ffi::c_void;
    use std::mem::size_of;
    use std::ptr::{null, null_mut};

    use windows_sys::Win32::Networking::WinHttp::{
        WINHTTP_ACCESS_TYPE_AUTOMATIC_PROXY, WINHTTP_FLAG_SECURE, WINHTTP_QUERY_FLAG_NUMBER,
        WINHTTP_QUERY_LOCATION, WINHTTP_QUERY_STATUS_CODE, WinHttpCloseHandle, WinHttpConnect,
        WinHttpOpen, WinHttpOpenRequest, WinHttpQueryHeaders, WinHttpReadData,
        WinHttpReceiveResponse, WinHttpSendRequest,
    };

    struct WinHttpHandle(*mut c_void);

    impl WinHttpHandle {
        fn new(handle: *mut c_void, operation: &str) -> Result<Self> {
            if handle.is_null() {
                Err(winhttp_last_error(operation))
            } else {
                Ok(Self(handle))
            }
        }
    }

    impl Drop for WinHttpHandle {
        fn drop(&mut self) {
            unsafe {
                WinHttpCloseHandle(self.0);
            }
        }
    }

    let agent = wide_null("Danmaku library-server");
    let session = WinHttpHandle::new(
        unsafe {
            WinHttpOpen(
                agent.as_ptr(),
                WINHTTP_ACCESS_TYPE_AUTOMATIC_PROXY,
                null(),
                null(),
                0,
            )
        },
        "WinHttpOpen",
    )?;
    let host = wide_null(&request.url.host);
    let connection = WinHttpHandle::new(
        unsafe { WinHttpConnect(session.0, host.as_ptr(), request.url.port, 0) },
        "WinHttpConnect",
    )?;
    let method = wide_null(&request.method);
    let path = wide_null(&request.url.path_and_query);
    let flags = if request.url.scheme == "https" {
        WINHTTP_FLAG_SECURE
    } else {
        0
    };
    let win_request = WinHttpHandle::new(
        unsafe {
            WinHttpOpenRequest(
                connection.0,
                method.as_ptr(),
                path.as_ptr(),
                null(),
                null(),
                null(),
                flags,
            )
        },
        "WinHttpOpenRequest",
    )?;
    let headers = wide_null(
        &request
            .headers
            .iter()
            .map(|(name, value)| format!("{name}: {value}"))
            .collect::<Vec<_>>()
            .join("\r\n"),
    );
    let body_ptr = if request.body.is_empty() {
        null()
    } else {
        request.body.as_ptr().cast::<c_void>()
    };
    let body_len = u32::try_from(request.body.len()).map_err(|_| {
        LibraryServerError::new("dandanplay request body exceeded WinHTTP length limit")
    })?;
    if unsafe {
        WinHttpSendRequest(
            win_request.0,
            headers.as_ptr(),
            headers.len().saturating_sub(1) as u32,
            body_ptr,
            body_len,
            body_len,
            0,
        )
    } == 0
    {
        return Err(winhttp_last_error("WinHttpSendRequest"));
    }
    if unsafe { WinHttpReceiveResponse(win_request.0, null_mut()) } == 0 {
        return Err(winhttp_last_error("WinHttpReceiveResponse"));
    }

    let mut status = 0_u32;
    let mut status_len = size_of::<u32>() as u32;
    if unsafe {
        WinHttpQueryHeaders(
            win_request.0,
            WINHTTP_QUERY_STATUS_CODE | WINHTTP_QUERY_FLAG_NUMBER,
            null(),
            (&mut status as *mut u32).cast::<c_void>(),
            &mut status_len,
            null_mut(),
        )
    } == 0
    {
        return Err(winhttp_last_error("WinHttpQueryHeaders(status)"));
    }
    let mut headers = BTreeMap::new();
    if let Some(location) = winhttp_query_header_string(win_request.0, WINHTTP_QUERY_LOCATION) {
        headers.insert("location".to_owned(), location);
    }

    let mut body = Vec::new();
    loop {
        let mut buffer = [0_u8; 8192];
        let mut bytes_read = 0_u32;
        if unsafe {
            WinHttpReadData(
                win_request.0,
                buffer.as_mut_ptr().cast::<c_void>(),
                buffer.len() as u32,
                &mut bytes_read,
            )
        } == 0
        {
            return Err(winhttp_last_error("WinHttpReadData"));
        }
        if bytes_read == 0 {
            break;
        }
        body.extend_from_slice(&buffer[..bytes_read as usize]);
        if body.len() > MAX_RESPONSE_BYTES {
            return Err(LibraryServerError::new(format!(
                "dandanplay response exceeded {MAX_RESPONSE_BYTES} bytes"
            )));
        }
    }
    Ok(HttpResponse {
        status: status as u16,
        headers,
        body,
    })
}

#[cfg(windows)]
fn winhttp_query_header_string(request: *mut std::ffi::c_void, query: u32) -> Option<String> {
    use std::ptr::{null, null_mut};

    use windows_sys::Win32::Foundation::{ERROR_INSUFFICIENT_BUFFER, GetLastError};
    use windows_sys::Win32::Networking::WinHttp::WinHttpQueryHeaders;

    let mut length = 0_u32;
    let mut index = 0_u32;
    if unsafe { WinHttpQueryHeaders(request, query, null(), null_mut(), &mut length, &mut index) }
        != 0
    {
        return None;
    }
    if unsafe { GetLastError() } != ERROR_INSUFFICIENT_BUFFER || length == 0 {
        return None;
    }
    let mut buffer = vec![0_u16; (length as usize).div_ceil(2)];
    index = 0;
    if unsafe {
        WinHttpQueryHeaders(
            request,
            query,
            null(),
            buffer.as_mut_ptr().cast::<std::ffi::c_void>(),
            &mut length,
            &mut index,
        )
    } == 0
    {
        return None;
    }
    while buffer.last().copied() == Some(0) {
        buffer.pop();
    }
    Some(String::from_utf16_lossy(&buffer))
}

#[cfg(windows)]
fn wide_null(value: &str) -> Vec<u16> {
    value.encode_utf16().chain(std::iter::once(0)).collect()
}

#[cfg(windows)]
fn winhttp_last_error(operation: &str) -> LibraryServerError {
    let code = unsafe { windows_sys::Win32::Foundation::GetLastError() };
    LibraryServerError::new(format!("{operation} failed with Windows error {code}"))
}

fn parse_http_response(bytes: Vec<u8>) -> Result<HttpResponse> {
    let header_end = bytes
        .windows(4)
        .position(|window| window == b"\r\n\r\n")
        .ok_or_else(|| LibraryServerError::new("dandanplay HTTP response was malformed"))?;
    let head = String::from_utf8_lossy(&bytes[..header_end]);
    let mut lines = head.split("\r\n");
    let status = lines
        .next()
        .and_then(|line| line.split_whitespace().nth(1))
        .and_then(|value| value.parse::<u16>().ok())
        .ok_or_else(|| LibraryServerError::new("dandanplay HTTP status was malformed"))?;
    let headers = lines
        .filter_map(|line| {
            let (name, value) = line.split_once(':')?;
            Some((name.trim().to_ascii_lowercase(), value.trim().to_owned()))
        })
        .collect::<BTreeMap<_, _>>();
    let mut body = bytes[header_end + 4..].to_vec();
    if headers
        .get("transfer-encoding")
        .is_some_and(|value| value.eq_ignore_ascii_case("chunked"))
    {
        body = decode_chunked(&body)?;
    }
    if body.len() > MAX_RESPONSE_BYTES {
        return Err(LibraryServerError::new(format!(
            "dandanplay response exceeded {MAX_RESPONSE_BYTES} bytes"
        )));
    }
    Ok(HttpResponse {
        status,
        headers,
        body,
    })
}

fn decode_chunked(input: &[u8]) -> Result<Vec<u8>> {
    let mut output = Vec::new();
    let mut offset = 0;
    loop {
        let line_end = input[offset..]
            .windows(2)
            .position(|window| window == b"\r\n")
            .ok_or_else(|| LibraryServerError::new("chunked dandanplay response was malformed"))?
            + offset;
        let size_text = std::str::from_utf8(&input[offset..line_end])
            .map_err(|error| LibraryServerError::with_context(error, "chunk size was not UTF-8"))?
            .split(';')
            .next()
            .unwrap_or_default();
        let size = usize::from_str_radix(size_text.trim(), 16)
            .map_err(|error| LibraryServerError::with_context(error, "chunk size was invalid"))?;
        offset = line_end + 2;
        if size == 0 {
            break;
        }
        if offset + size + 2 > input.len() {
            return Err(LibraryServerError::new(
                "chunked dandanplay response ended early",
            ));
        }
        output.extend_from_slice(&input[offset..offset + size]);
        offset += size + 2;
    }
    Ok(output)
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) struct ParsedUrl {
    pub(crate) scheme: String,
    pub(crate) host: String,
    pub(crate) port: u16,
    pub(crate) path_and_query: String,
}

impl ParsedUrl {
    fn is_default_port(&self) -> bool {
        (self.scheme == "http" && self.port == 80) || (self.scheme == "https" && self.port == 443)
    }

    pub(crate) fn redacted(&self) -> String {
        format!("{}://{}{}", self.scheme, self.host, self.path_and_query)
    }
}

fn endpoint_url(base_url: &str, api_path: &str, query: Option<&str>) -> Result<ParsedUrl> {
    let base = parse_url(base_url)?;
    let base_path = base.path_and_query.split('?').next().unwrap_or("/");
    let path = format!(
        "{}/{}",
        base_path.trim_end_matches('/'),
        api_path.trim_start_matches('/')
    );
    Ok(ParsedUrl {
        path_and_query: query.map(|query| format!("{path}?{query}")).unwrap_or(path),
        ..base
    })
}

fn resolve_redirect(current: &ParsedUrl, location: &str) -> Result<ParsedUrl> {
    if location.starts_with("http://") || location.starts_with("https://") {
        return parse_url(location);
    }
    if location.starts_with('/') {
        return Ok(ParsedUrl {
            path_and_query: location.to_owned(),
            ..current.clone()
        });
    }
    let directory = current
        .path_and_query
        .split('?')
        .next()
        .unwrap_or("/")
        .rsplit_once('/')
        .map(|(directory, _)| directory)
        .unwrap_or("");
    Ok(ParsedUrl {
        path_and_query: format!("{directory}/{location}"),
        ..current.clone()
    })
}

pub(crate) fn parse_url(value: &str) -> Result<ParsedUrl> {
    let trimmed = value.trim();
    let (scheme, rest) = trimmed
        .split_once("://")
        .ok_or_else(|| LibraryServerError::new("dandanplay base URL must use http or https"))?;
    let scheme = scheme.to_ascii_lowercase();
    if scheme != "http" && scheme != "https" {
        return Err(LibraryServerError::new(
            "dandanplay base URL must use http or https",
        ));
    }
    let (authority, path) = rest
        .split_once('/')
        .map(|(authority, path)| (authority, format!("/{path}")))
        .unwrap_or((rest, "/".to_owned()));
    if authority.contains('@') {
        return Err(LibraryServerError::new(
            "dandanplay base URL must not include user info",
        ));
    }
    let (host, port) = authority
        .rsplit_once(':')
        .and_then(|(host, port)| Some((host, port.parse::<u16>().ok()?)))
        .unwrap_or((authority, if scheme == "https" { 443 } else { 80 }));
    if host.trim().is_empty() {
        return Err(LibraryServerError::new(
            "dandanplay base URL must include a host",
        ));
    }
    Ok(ParsedUrl {
        scheme,
        host: host.to_owned(),
        port,
        path_and_query: if path.is_empty() {
            "/".to_owned()
        } else {
            path
        },
    })
}

fn normalize_base_url(value: &str) -> String {
    value.trim().trim_end_matches('/').to_owned()
}

fn should_follow_redirect(method: &str, body: Option<&str>, status: u16) -> bool {
    body.is_none()
        && method.eq_ignore_ascii_case("GET")
        && matches!(status, 301 | 302 | 303 | 307 | 308)
}

fn http_error_message(
    status: u16,
    url: &ParsedUrl,
    location: Option<&String>,
    body: &[u8],
) -> String {
    let mut message = format!("dandanplay returned HTTP {status} for {}", url.redacted());
    if let Some(location) = location.filter(|location| !location.trim().is_empty()) {
        message.push_str("; redirected to ");
        message.push_str(location);
    }
    let body = String::from_utf8_lossy(body)
        .replace(['\r', '\n'], " ")
        .chars()
        .take(256)
        .collect::<String>();
    if !body.trim().is_empty() {
        message.push_str("; body=");
        message.push_str(&body);
    }
    message
}

fn current_epoch_seconds() -> u64 {
    current_epoch_ms() / 1_000
}

fn non_blank(value: String) -> Option<String> {
    let trimmed = value.trim();
    (!trimmed.is_empty()).then(|| trimmed.to_owned())
}

fn authentication_mode_or_default(value: &str) -> HeadlessDandanplayAuthenticationMode {
    match value.trim().to_ascii_uppercase().as_str() {
        "CREDENTIAL" => HeadlessDandanplayAuthenticationMode::Credential,
        _ => HeadlessDandanplayAuthenticationMode::Signed,
    }
}

fn json_bool(value: &Value, key: &str) -> Option<bool> {
    value.get(key).and_then(Value::as_bool)
}

fn json_string(value: &Value, key: &str) -> Option<String> {
    value
        .get(key)
        .and_then(Value::as_str)
        .map(ToOwned::to_owned)
}

fn json_string_any(value: &Value, keys: &[&str]) -> Option<String> {
    keys.iter().find_map(|key| json_string(value, key))
}

fn json_u64(value: &Value, key: &str) -> Option<u64> {
    value.get(key).and_then(Value::as_u64)
}

fn json_u64_any(value: &Value, keys: &[&str]) -> Option<u64> {
    keys.iter().find_map(|key| json_u64(value, key))
}

fn json_f64(value: &Value, key: &str) -> Option<f64> {
    value
        .get(key)?
        .as_f64()
        .or_else(|| value.get(key)?.as_str()?.parse::<f64>().ok())
}

fn json_f64_any(value: &Value, keys: &[&str]) -> Option<f64> {
    keys.iter().find_map(|key| json_f64(value, key))
}

#[allow(dead_code)]
fn _absolute_media_path(path: &Path) -> Result<PathBuf> {
    absolute_normalized_path(path)
}

#[cfg(test)]
mod tests {
    use std::net::TcpListener;
    use std::sync::{Arc, Mutex as StdMutex};
    use std::thread;
    use std::time::Duration;

    use super::*;

    fn fixed_epoch_seconds() -> u64 {
        1_735_660_800
    }

    fn fixed_epoch_ms() -> u64 {
        2 * MILLIS_PER_DAY
    }

    #[test]
    fn signs_like_kotlin_client() {
        assert_eq!(
            "qJ4Zl5JADrNh5ujPoe1zh0ObjCrdvdE1EL6VR5y1XMg=",
            generate_signature("test-app", 1_735_660_800, "/api/v2/match", "test-secret")
        );
    }

    #[test]
    fn computes_fingerprint_from_first_sixteen_megabytes() {
        let temp = temp_dir("danmaku-dandanplay-fingerprint");
        let file = temp.join("Episode 01.mkv");
        fs::write(&file, b"hello").expect("media");

        let fingerprint = DandanplayMediaFingerprint::from_path(&file).expect("fingerprint");

        assert_eq!("Episode 01.mkv", fingerprint.file_name);
        assert_eq!(
            "5d41402abc4b2a76b9719d911017c592",
            fingerprint.normalized_file_hash()
        );
        assert_eq!(5, fingerprint.file_size_bytes);
        fs::remove_dir_all(temp).expect("temp delete");
    }

    #[test]
    fn parses_match_and_comment_responses() {
        let response = json!({
            "success": true,
            "matches": [{
                "EpisodeId": 123450001,
                "AnimeId": 12345,
                "AnimeTitle": "Example Anime",
                "EpisodeTitle": "Episode 01",
                "Shift": "0.5"
            }],
            "comments": [
                {"cid": "c-1", "p": "1.5,1,25,16777215,0,0,user,row-1", "m": "hello"},
                {"cid": "c-2", "p": "2.0,5,18,16711680,0,0,user,row-2", "m": "top"},
                {"cid": "bad", "p": "-1,1,25,16777215", "m": "skip"}
            ]
        });

        let candidate = DandanplayMatch::from_json(&response["matches"][0]).expect("match");
        assert_eq!(123450001, candidate.episode_id);
        assert_eq!("Example Anime - Episode 01", candidate.display_title);
        assert_eq!(Some(0.5), candidate.shift_seconds);

        let comments = response["comments"]
            .as_array()
            .expect("comments")
            .iter()
            .enumerate()
            .filter_map(|(index, value)| DanmakuComment::from_dandanplay_json(index, value))
            .collect::<Vec<_>>();
        assert_eq!(2, comments.len());
        assert_eq!("hello", comments[0].text);
        assert_eq!(1_500, comments[0].timestamp_ms);
        assert_eq!(DanmakuMode::Top, comments[1].style.mode);
        assert_eq!(DanmakuSize::Small, comments[1].style.size);
        assert_eq!(0xffff_0000, comments[1].style.color_argb);
    }

    #[test]
    fn cleans_up_expired_cache_rows() {
        let temp = temp_dir("danmaku-dandanplay-cache");
        let store = DandanplayCommentCacheStore::new(temp.join("dandanplay-comment-cache.json"));
        store
            .save(DandanplayCommentCache {
                media_id: "old".to_owned(),
                file_hash: "5d41402abc4b2a76b9719d911017c592".to_owned(),
                file_name: "old.mkv".to_owned(),
                file_size_bytes: 5,
                episode_id: Some(1),
                anime_id: None,
                anime_title: None,
                episode_title: None,
                shift_seconds: None,
                comments_json: normalized_comments_json(&[]),
                rendered_ass_path: None,
                fetched_at_epoch_ms: 1,
            })
            .expect("save old");
        store
            .save(DandanplayCommentCache {
                media_id: "fresh".to_owned(),
                fetched_at_epoch_ms: 3 * MILLIS_PER_DAY,
                ..store.load("old").expect("load").expect("old")
            })
            .expect("save fresh");

        store.delete_older_than(MILLIS_PER_DAY).expect("cleanup");

        assert!(store.load("old").expect("load old").is_none());
        assert!(store.load("fresh").expect("load fresh").is_some());
        fs::remove_dir_all(temp).expect("temp delete");
    }

    #[test]
    fn merges_local_defaults_with_server_setting_precedence() {
        let settings = HeadlessDandanplayProviderSettings {
            base_url: DEFAULT_DANDANPLAY_BASE_URL.to_owned(),
            app_id: None,
            app_secret: None,
            has_app_secret: false,
            authentication_mode: HeadlessDandanplayAuthenticationMode::Signed,
            cache_max_age_days: 30,
        };
        let merged = merge_dandanplay_settings(
            settings,
            Some(DandanplayLocalCredentialDefaults {
                base_url: "https://proxy.example.test".to_owned(),
                proxy_base_url: Some("https://proxy.example.test".to_owned()),
                app_id: Some("app".to_owned()),
                app_secret: None,
                authentication_mode: HeadlessDandanplayAuthenticationMode::Credential,
                cache_max_age_days: Some(9),
            }),
        );
        assert_eq!("https://proxy.example.test", merged.base_url);
        assert_eq!(Some("app".to_owned()), merged.app_id);
        assert_eq!(None, merged.app_secret);
        assert_eq!(9, merged.cache_max_age_days);

        let summarized = HeadlessDandanplayProviderSettings {
            base_url: DEFAULT_DANDANPLAY_BASE_URL.to_owned(),
            app_id: Some("stored-app".to_owned()),
            app_secret: None,
            has_app_secret: true,
            authentication_mode: HeadlessDandanplayAuthenticationMode::Signed,
            cache_max_age_days: 30,
        };
        let summarized = merge_dandanplay_settings(
            summarized,
            Some(DandanplayLocalCredentialDefaults {
                base_url: "https://proxy.example.test".to_owned(),
                proxy_base_url: Some("https://proxy.example.test".to_owned()),
                app_id: Some("local-app".to_owned()),
                app_secret: Some("local-secret".to_owned()),
                authentication_mode: HeadlessDandanplayAuthenticationMode::Credential,
                cache_max_age_days: Some(3),
            }),
        );
        assert_eq!(Some("stored-app".to_owned()), summarized.app_id);
        assert_eq!(Some("local-secret".to_owned()), summarized.app_secret);
        assert_eq!(30, summarized.cache_max_age_days);
    }

    #[test]
    fn loads_local_defaults_with_environment_over_properties_and_proxy_precedence() {
        let temp = temp_dir("danmaku-dandanplay-local-defaults");
        let properties = temp.join("local.properties");
        fs::write(
            &properties,
            "danmaku.dandanplay.appId=local-app\n\
             danmaku.dandanplay.appSecret=local-secret\n\
             danmaku.dandanplay.proxyBaseUrl=https://proxy.example.test\n\
             danmaku.dandanplay.authenticationMode=credential\n\
             danmaku.dandanplay.cacheMaxAgeDays=9\n",
        )
        .expect("properties");
        let defaults = DandanplayLocalCredentialDefaults::load(
            &HashMap::from([(
                "DANMAKU_DANDANPLAY_APP_ID".to_owned(),
                "environment-app".to_owned(),
            )]),
            Some(&properties),
        )
        .expect("defaults");
        assert_eq!("environment-app", defaults.app_id.as_deref().unwrap());
        assert_eq!("local-secret", defaults.app_secret.as_deref().unwrap());
        assert_eq!(DEFAULT_DANDANPLAY_BASE_URL, defaults.base_url);
        assert_eq!(
            Some("https://proxy.example.test"),
            defaults.proxy_base_url.as_deref()
        );
        assert_eq!(
            HeadlessDandanplayAuthenticationMode::Credential,
            defaults.authentication_mode
        );
        assert_eq!(Some(9), defaults.cache_max_age_days);
        fs::remove_dir_all(temp).expect("temp delete");
    }

    #[tokio::test]
    async fn client_sends_signed_and_credential_requests_to_local_server() {
        let server = TestServer::start();
        let signed = DandanplayDanmakuClient::with_clock(
            DandanplayConnection::new(
                server.base_url(),
                Some("test-app".to_owned()),
                Some("test-secret".to_owned()),
                HeadlessDandanplayAuthenticationMode::Signed,
            ),
            fixed_epoch_seconds,
        );

        let fingerprint = DandanplayMediaFingerprint {
            file_name: "Example S01E01.mkv".to_owned(),
            file_hash: "658d05841b9476ccc7420b3f0bb21c3b".to_owned(),
            file_size_bytes: 123_456,
            video_duration_seconds: Some(1_420),
        };
        let matches = signed.match_media(&fingerprint).await.expect("match");
        assert_eq!(123450001, matches[0].episode_id);
        let comments = signed
            .fetch_comments(matches[0].episode_id, true)
            .await
            .expect("comments");
        assert_eq!(2, comments.len());

        let captured = server.requests();
        let match_request = captured
            .iter()
            .find(|request| request.path == "/api/v2/match")
            .unwrap();
        assert_eq!("test-app", match_request.headers["x-appid"]);
        assert_eq!("1735660800", match_request.headers["x-timestamp"]);
        assert_eq!(
            "qJ4Zl5JADrNh5ujPoe1zh0ObjCrdvdE1EL6VR5y1XMg=",
            match_request.headers["x-signature"]
        );
        assert!(
            match_request
                .body
                .contains("\"matchMode\":\"hashAndFileName\"")
        );
        let comment_request = captured
            .iter()
            .find(|request| request.path == "/api/v2/comment/123450001")
            .unwrap();
        assert_eq!(Some("withRelated=true"), comment_request.query.as_deref());

        let credential = DandanplayDanmakuClient::new(DandanplayConnection::new(
            server.base_url(),
            Some("app".to_owned()),
            Some("secret".to_owned()),
            HeadlessDandanplayAuthenticationMode::Credential,
        ));
        credential
            .fetch_comments(123450001, false)
            .await
            .expect("credential");
        let captured = server.requests();
        let last = captured.last().unwrap();
        assert_eq!("app", last.headers["x-appid"]);
        assert_eq!("secret", last.headers["x-appsecret"]);
        assert!(!last.headers.contains_key("x-signature"));
    }

    #[tokio::test]
    async fn resolver_uses_cache_unless_forced() {
        let server = TestServer::start();
        let temp = temp_dir("danmaku-dandanplay-resolver-cache");
        let media = temp.join("Episode 01.mkv");
        fs::write(&media, b"hello").expect("media");
        let resolver = DandanplayResolver::new(
            DandanplayDanmakuClient::new(DandanplayConnection::new(
                server.base_url(),
                None,
                None,
                HeadlessDandanplayAuthenticationMode::Signed,
            )),
            DandanplayCommentCacheStore::new(temp.join("cache.json")),
            30,
            fixed_epoch_ms,
        );

        let first = resolver
            .resolve("media-id", &media, None, true, false)
            .await
            .expect("first");
        assert_eq!(LanDanmakuSource::Network, first.source);
        assert_eq!(2, first.selected_track.as_ref().unwrap().events.len());
        let second = resolver
            .resolve("media-id", &media, None, true, false)
            .await
            .expect("second");
        assert_eq!(LanDanmakuSource::Cache, second.source);
        let refreshed = resolver
            .resolve("media-id", &media, None, true, true)
            .await
            .expect("forced");
        assert_eq!(LanDanmakuSource::Network, refreshed.source);
        assert_eq!(
            2,
            server
                .requests()
                .iter()
                .filter(|request| request.path == "/api/v2/match")
                .count()
        );
        fs::remove_dir_all(temp).expect("temp delete");
    }

    #[derive(Clone, Debug)]
    struct CapturedRequest {
        path: String,
        query: Option<String>,
        headers: BTreeMap<String, String>,
        body: String,
    }

    struct TestServer {
        address: String,
        requests: Arc<StdMutex<Vec<CapturedRequest>>>,
    }

    impl TestServer {
        fn start() -> Self {
            let listener = TcpListener::bind("127.0.0.1:0").expect("bind");
            let address = listener.local_addr().expect("addr").to_string();
            let requests = Arc::new(StdMutex::new(Vec::new()));
            let requests_for_thread = requests.clone();
            thread::spawn(move || {
                for stream in listener.incoming().flatten() {
                    let requests = requests_for_thread.clone();
                    thread::spawn(move || handle_test_connection(stream, requests));
                }
            });
            thread::sleep(Duration::from_millis(25));
            Self { address, requests }
        }

        fn base_url(&self) -> String {
            format!("http://{}", self.address)
        }

        fn requests(&self) -> Vec<CapturedRequest> {
            self.requests.lock().expect("requests").clone()
        }
    }

    fn handle_test_connection(
        mut stream: TcpStream,
        requests: Arc<StdMutex<Vec<CapturedRequest>>>,
    ) {
        let request = read_test_request(&mut stream);
        let (head, body) = request.split_once("\r\n\r\n").unwrap_or((&request, ""));
        let mut lines = head.split("\r\n");
        let request_line = lines.next().unwrap_or_default();
        let target = request_line.split_whitespace().nth(1).unwrap_or("/");
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
        requests.lock().expect("requests").push(CapturedRequest {
            path: path.clone(),
            query,
            headers,
            body: body.to_owned(),
        });
        let response = match path.as_str() {
            "/api/v2/match" => {
                r#"{"success":true,"matches":[{"episodeId":123450001,"animeId":12345,"animeTitle":"Example Anime","episodeTitle":"Episode 01","shift":0}]}"#
            }
            "/api/v2/comment/123450001" => {
                r#"{"success":true,"comments":[{"cid":"c-1","p":"1.5,1,25,16777215,0,0,user,row-1","m":"hello"},{"cid":"c-2","p":"2.0,5,18,16711680,0,0,user,row-2","m":"top"}]}"#
            }
            _ => r#"{"success":false,"message":"not found"}"#,
        };
        let status = if path.starts_with("/api/v2/") {
            "HTTP/1.1 200 OK"
        } else {
            "HTTP/1.1 404 Not Found"
        };
        let bytes = response.as_bytes();
        write!(
            stream,
            "{status}\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: {}\r\n\r\n{}",
            bytes.len(),
            response
        )
        .expect("write response");
    }

    fn read_test_request(stream: &mut TcpStream) -> String {
        let mut request = Vec::new();
        let mut expected_length = None;
        loop {
            let mut buffer = [0_u8; 4 * 1024];
            let read = stream.read(&mut buffer).expect("read request");
            assert!(read > 0, "connection closed before request completed");
            request.extend_from_slice(&buffer[..read]);

            if expected_length.is_none()
                && let Some(header_end) = request.windows(4).position(|bytes| bytes == b"\r\n\r\n")
            {
                let head = String::from_utf8_lossy(&request[..header_end]);
                let content_length = head
                    .lines()
                    .find_map(|line| {
                        let (name, value) = line.split_once(':')?;
                        name.eq_ignore_ascii_case("content-length")
                            .then(|| value.trim().parse::<usize>().expect("content length"))
                    })
                    .unwrap_or(0);
                expected_length = Some(header_end + 4 + content_length);
            }

            if expected_length.is_some_and(|length| request.len() >= length) {
                break;
            }
            assert!(request.len() <= 64 * 1024, "test request exceeded limit");
        }
        String::from_utf8(request).expect("UTF-8 request")
    }

    fn temp_dir(prefix: &str) -> PathBuf {
        let path = env::temp_dir().join(format!("{prefix}-{}", std::process::id()));
        let _ = fs::remove_dir_all(&path);
        fs::create_dir_all(&path).expect("temp dir");
        path
    }
}
