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

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DandanplayCacheInspection {
    pub episode_id: Option<u64>,
    pub anime_id: Option<u64>,
    pub fetched_at_epoch_ms: u64,
    pub fresh: bool,
}

impl DandanplayResolver {
    pub fn inspect_cache(
        &self,
        media_id: &str,
        expected_size_bytes: u64,
    ) -> Result<Option<DandanplayCacheInspection>> {
        let Some(cache) = self.cache_store.load(media_id)? else {
            return Ok(None);
        };
        let fresh = cache.episode_id.is_some()
            && cache.file_size_bytes == expected_size_bytes
            && !cache.is_expired((self.now_epoch_ms)(), self.cache_max_age_days);
        Ok(Some(DandanplayCacheInspection {
            episode_id: cache.episode_id,
            anime_id: cache.anime_id,
            fetched_at_epoch_ms: cache.fetched_at_epoch_ms,
            fresh,
        }))
    }

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

    /// Resolves danmaku for a media file. `preferred_match` pins a specific
    /// episode: when its ID is among the hash-match candidates that richer
    /// candidate is used, otherwise `preferred_match` itself is (so an
    /// episode chosen from a keyword search — which hash matching may never
    /// propose — can still be selected, cached, and recorded).
    pub async fn resolve(
        &self,
        media_id: &str,
        media_path: &Path,
        preferred_match: Option<DandanplayMatch>,
        with_related: bool,
        force_refresh: bool,
    ) -> Result<DandanplayResolveResult> {
        if media_id.trim().is_empty() {
            return Err(LibraryServerError::new("mediaId must not be blank"));
        }
        let fingerprint = DandanplayMediaFingerprint::from_path(media_path)?;
        self.cleanup_expired_caches()?;
        if !force_refresh
            && preferred_match.is_none()
            && let Some(cached) = self.resolve_cached(media_id, &fingerprint)?
        {
            return Ok(cached);
        }

        let matches = self.client.match_media(&fingerprint).await?;
        let selected_match = preferred_match
            .map(|preferred| {
                matches
                    .iter()
                    .find(|candidate| candidate.episode_id == preferred.episode_id)
                    .cloned()
                    .unwrap_or(preferred)
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

    /// Searches the dandanplay database by anime keyword, returning each
    /// matching anime with its full episode list (for a manual match picker).
    pub async fn search_episodes(&self, keyword: &str) -> Result<Vec<DandanplaySearchAnime>> {
        self.client.search_episodes(keyword).await
    }

    /// Fetches the full bangumi profile of one anime (rating, synopsis,
    /// tags, per-episode air dates, database links) for the detail page.
    pub async fn bangumi_detail(&self, anime_id: u64) -> Result<DandanplayBangumiDetail> {
        self.client.bangumi_detail(anime_id).await
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

    pub async fn search_episodes(&self, keyword: &str) -> Result<Vec<DandanplaySearchAnime>> {
        let keyword = keyword.trim();
        if keyword.is_empty() {
            return Err(LibraryServerError::new("search keyword must not be blank"));
        }
        let query = format!("anime={}", url_encode(keyword));
        let data = self
            .request_json("GET", "/api/v2/search/episodes", Some(&query), None)
            .await?;
        if json_bool(&data, "success") == Some(false) {
            return Err(LibraryServerError::new(format!(
                "dandanplay search failed: {}",
                json_string(&data, "message").unwrap_or_else(|| "unknown error".to_owned())
            )));
        }
        Ok(data
            .get("animes")
            .and_then(Value::as_array)
            .into_iter()
            .flatten()
            .filter_map(DandanplaySearchAnime::from_json)
            .collect())
    }

    pub async fn bangumi_detail(&self, anime_id: u64) -> Result<DandanplayBangumiDetail> {
        if anime_id == 0 {
            return Err(LibraryServerError::new("animeId must be positive"));
        }
        let api_path = format!("/api/v2/bangumi/{anime_id}");
        let data = self.request_json("GET", &api_path, None, None).await?;
        if json_bool(&data, "success") == Some(false) {
            return Err(LibraryServerError::new(format!(
                "dandanplay bangumi fetch failed: {}",
                json_string(&data, "message").unwrap_or_else(|| "unknown error".to_owned())
            )));
        }
        data.get("bangumi")
            .and_then(DandanplayBangumiDetail::from_json)
            .ok_or_else(|| {
                LibraryServerError::new("dandanplay bangumi response was missing the anime")
            })
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
    pub(crate) fn new(
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

/// One anime from a dandanplay keyword search (`/api/v2/search/episodes`),
/// carrying its full episode list so a picker can drill down to an episode.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DandanplaySearchAnime {
    pub anime_id: u64,
    pub anime_title: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub type_description: Option<String>,
    pub episodes: Vec<DandanplaySearchEpisode>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DandanplaySearchEpisode {
    pub episode_id: u64,
    pub episode_title: String,
}

impl DandanplaySearchAnime {
    fn from_json(value: &Value) -> Option<Self> {
        let anime_id = json_u64_any(value, &["animeId", "AnimeId"])?;
        let anime_title = json_string_any(value, &["animeTitle", "AnimeTitle"])?;
        let episodes = value
            .get("episodes")
            .and_then(Value::as_array)
            .into_iter()
            .flatten()
            .filter_map(|episode| {
                Some(DandanplaySearchEpisode {
                    episode_id: json_u64_any(episode, &["episodeId", "EpisodeId"])?,
                    episode_title: json_string_any(episode, &["episodeTitle", "EpisodeTitle"])
                        .unwrap_or_default(),
                })
            })
            .collect();
        Some(Self {
            anime_id,
            anime_title,
            type_description: json_string_any(value, &["typeDescription", "TypeDescription"]),
            episodes,
        })
    }
}

/// Full profile of one anime from `/api/v2/bangumi/{animeId}`: the fields
/// the library's information page shows beyond what hash matching stores —
/// community rating, synopsis, tags, per-episode air dates, and links into
/// the public anime databases.
#[derive(Debug, Clone, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DandanplayBangumiDetail {
    pub anime_id: u64,
    pub anime_title: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub type_description: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub summary: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub rating: Option<f64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub is_on_air: Option<bool>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub tags: Vec<String>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub episodes: Vec<DandanplayBangumiEpisode>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub online_databases: Vec<DandanplayOnlineDatabase>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DandanplayBangumiEpisode {
    pub episode_id: u64,
    pub episode_title: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub air_date: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DandanplayOnlineDatabase {
    pub name: String,
    pub url: String,
}

impl DandanplayBangumiDetail {
    fn from_json(value: &Value) -> Option<Self> {
        let anime_id = json_u64_any(value, &["animeId", "AnimeId"])?;
        let anime_title = json_string_any(value, &["animeTitle", "AnimeTitle"])?;
        let tags = value
            .get("tags")
            .and_then(Value::as_array)
            .into_iter()
            .flatten()
            .filter_map(|tag| json_string_any(tag, &["name", "Name"]))
            .collect();
        let episodes = value
            .get("episodes")
            .and_then(Value::as_array)
            .into_iter()
            .flatten()
            .filter_map(|episode| {
                Some(DandanplayBangumiEpisode {
                    episode_id: json_u64_any(episode, &["episodeId", "EpisodeId"])?,
                    episode_title: json_string_any(episode, &["episodeTitle", "EpisodeTitle"])
                        .unwrap_or_default(),
                    air_date: json_string_any(episode, &["airDate", "AirDate"]),
                })
            })
            .collect();
        let online_databases = value
            .get("onlineDatabases")
            .and_then(Value::as_array)
            .into_iter()
            .flatten()
            .filter_map(|database| {
                Some(DandanplayOnlineDatabase {
                    name: json_string_any(database, &["name", "Name"])?,
                    url: json_string_any(database, &["url", "Url"])?,
                })
            })
            .collect();
        Some(Self {
            anime_id,
            anime_title,
            type_description: json_string_any(value, &["typeDescription", "TypeDescription"]),
            summary: json_string_any(value, &["summary", "Summary"]),
            // dandanplay reports 0 for titles nobody has rated yet.
            rating: json_f64_any(value, &["rating", "Rating"]).filter(|rating| *rating > 0.0),
            is_on_air: value
                .get("isOnAir")
                .or_else(|| value.get("IsOnAir"))
                .and_then(Value::as_bool),
            tags,
            episodes,
            online_databases,
        })
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
    /// The matched anime's title alone (no episode suffix), matching the
    /// `displayTitle` the catalog's recognized `animeMetadata` will carry —
    /// clients compare the two to detect a stale catalog grouping.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub anime_title: Option<String>,
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
            anime_title: None,
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
            anime_title: None,
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
            anime_title: track
                .as_ref()
                .and_then(|track| track.match_candidate.anime_title.clone()),
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
// matching the player and external-anime credential paths. This lets users keep
// dandanplay credentials outside server-settings.json.
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

mod http_transport;
pub(crate) use http_transport::{HttpRequest, ParsedUrl, parse_url, send_http_request};
use http_transport::{
    endpoint_url, http_error_message, normalize_base_url, resolve_redirect, should_follow_redirect,
};
fn current_epoch_seconds() -> u64 {
    current_epoch_ms() / 1_000
}

fn non_blank(value: String) -> Option<String> {
    let trimmed = value.trim();
    (!trimmed.is_empty()).then(|| trimmed.to_owned())
}

pub(crate) fn url_encode(value: &str) -> String {
    let mut encoded = String::new();
    for byte in value.as_bytes() {
        match *byte {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'.' | b'-' | b'*' | b'_' => {
                encoded.push(*byte as char);
            }
            b' ' => encoded.push('+'),
            other => encoded.push_str(&format!("%{other:02X}")),
        }
    }
    encoded
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
mod tests;
