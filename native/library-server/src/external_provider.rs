use std::collections::{BTreeMap, BTreeSet};
use std::fmt;
use std::sync::Arc;
use std::time::{SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};
use serde_json::{Value, json};
use tokio::task;

use crate::catalog::{ExternalAnimeExternalLink, ExternalAnimeId, ExternalAnimeProvider};
use crate::dandanplay::{HttpRequest, ParsedUrl, parse_url, send_http_request, url_encode};
use crate::settings::HeadlessServerSettings;
use crate::{LibraryServerError, Result};

const MAX_SEARCH_LIMIT: u32 = 50;
const MAX_RESPONSE_BYTES: usize = 1_000_000;
const MAL_BASE_URL: &str = "https://api.myanimelist.net/v2/";

#[derive(Clone)]
pub struct ExternalProviderService {
    search_clients: Vec<Arc<dyn ExternalAnimeSearchClient>>,
    tracking_clients: BTreeMap<ExternalAnimeProvider, Arc<dyn ExternalAnimeTrackingClient>>,
}

impl fmt::Debug for ExternalProviderService {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_struct("ExternalProviderService")
            .field("search_client_count", &self.search_clients.len())
            .field(
                "tracking_providers",
                &self.tracking_clients.keys().collect::<Vec<_>>(),
            )
            .finish()
    }
}

impl ExternalProviderService {
    pub fn from_settings(settings: &HeadlessServerSettings) -> Self {
        let mut search_clients: Vec<Arc<dyn ExternalAnimeSearchClient>> = Vec::new();
        if let Some(client_id) = settings.external_anime.my_anime_list_client_id.clone() {
            search_clients.push(Arc::new(MyAnimeListSearchClient::new(
                MAL_BASE_URL.to_owned(),
                client_id,
            )));
        }
        search_clients.push(Arc::new(BangumiSearchClient::new(
            settings.external_anime.bangumi_base_url.clone(),
            settings.external_anime.bangumi_user_agent.clone(),
        )));

        let mut tracking_clients: BTreeMap<
            ExternalAnimeProvider,
            Arc<dyn ExternalAnimeTrackingClient>,
        > = BTreeMap::new();
        if let Some(access_token) = settings.external_anime.my_anime_list_access_token.clone() {
            tracking_clients.insert(
                ExternalAnimeProvider::MyAnimeList,
                Arc::new(MyAnimeListTrackingClient::new(
                    MAL_BASE_URL.to_owned(),
                    access_token,
                )),
            );
        }
        if let Some(access_token) = settings.external_anime.bangumi_access_token.clone() {
            tracking_clients.insert(
                ExternalAnimeProvider::Bangumi,
                Arc::new(BangumiTrackingClient::new(
                    settings.external_anime.bangumi_base_url.clone(),
                    settings.external_anime.bangumi_user_agent.clone(),
                    access_token,
                )),
            );
        }

        Self {
            search_clients,
            tracking_clients,
        }
    }

    #[cfg(test)]
    pub(crate) fn new_for_tests(
        search_clients: Vec<Arc<dyn ExternalAnimeSearchClient>>,
        tracking_clients: Vec<Arc<dyn ExternalAnimeTrackingClient>>,
    ) -> Self {
        Self {
            search_clients,
            tracking_clients: tracking_clients
                .into_iter()
                .map(|client| (client.provider(), client))
                .collect(),
        }
    }

    pub async fn search(
        &self,
        query: ExternalAnimeMatchQuery,
        providers: BTreeSet<ExternalAnimeProvider>,
        limit_per_provider: u32,
    ) -> Vec<ExternalAnimeMatchCandidate> {
        let mut anime_by_id = BTreeMap::<ExternalAnimeId, ExternalAnimeInfo>::new();
        for client in &self.search_clients {
            if !providers.is_empty() && !providers.contains(&client.provider()) {
                continue;
            }
            for search_title in query.search_titles() {
                let client = client.clone();
                let mut query = query.clone();
                query.title = search_title;
                query.alternate_titles.clear();
                let result =
                    task::spawn_blocking(move || client.search(&query, limit_per_provider)).await;
                if let Ok(Ok(results)) = result {
                    for anime in results {
                        anime_by_id.entry(anime.id.clone()).or_insert(anime);
                    }
                }
            }
        }
        rank_external_anime_matches(&query, anime_by_id.into_values().collect())
    }

    pub async fn fetch_list_entry(
        &self,
        anime_id: ExternalAnimeId,
    ) -> std::result::Result<Option<ExternalAnimeListEntry>, ExternalProviderError> {
        let client = self.client_for(anime_id.provider)?;
        task::spawn_blocking(move || client.fetch_list_entry(anime_id))
            .await
            .map_err(|error| ExternalProviderError::Upstream(error.to_string()))?
    }

    pub async fn update_list_entry(
        &self,
        update: ExternalAnimeTrackingUpdate,
    ) -> std::result::Result<ExternalAnimeListEntry, ExternalProviderError> {
        let client = self.client_for(update.anime_id.provider)?;
        task::spawn_blocking(move || client.update_list_entry(update))
            .await
            .map_err(|error| ExternalProviderError::Upstream(error.to_string()))?
    }

    fn client_for(
        &self,
        provider: ExternalAnimeProvider,
    ) -> std::result::Result<Arc<dyn ExternalAnimeTrackingClient>, ExternalProviderError> {
        self.tracking_clients
            .get(&provider)
            .cloned()
            .ok_or(ExternalProviderError::NotConfigured(provider))
    }
}

pub trait ExternalAnimeSearchClient: Send + Sync {
    fn provider(&self) -> ExternalAnimeProvider;
    fn search(&self, query: &ExternalAnimeMatchQuery, limit: u32)
    -> Result<Vec<ExternalAnimeInfo>>;
}

pub trait ExternalAnimeTrackingClient: Send + Sync {
    fn provider(&self) -> ExternalAnimeProvider;
    fn fetch_list_entry(
        &self,
        anime_id: ExternalAnimeId,
    ) -> std::result::Result<Option<ExternalAnimeListEntry>, ExternalProviderError>;
    fn update_list_entry(
        &self,
        update: ExternalAnimeTrackingUpdate,
    ) -> std::result::Result<ExternalAnimeListEntry, ExternalProviderError>;
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ExternalProviderError {
    NotConfigured(ExternalAnimeProvider),
    NotFound(String),
    Upstream(String),
}

impl ExternalProviderError {
    pub fn route_status_and_body(&self) -> (u16, String) {
        match self {
            Self::NotConfigured(provider) => (
                409,
                format!(
                    "{} list sync credentials are not configured",
                    provider_display_name(*provider)
                ),
            ),
            Self::NotFound(_) => (404, "External list entry was not found.".to_owned()),
            Self::Upstream(message) => (502, format!("external list request failed: {message}")),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ExternalAnimeMatchQuery {
    pub title: String,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub alternate_titles: Vec<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub episode_count: Option<u32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub start_year: Option<u32>,
}

impl ExternalAnimeMatchQuery {
    fn search_titles(&self) -> Vec<String> {
        let mut titles = vec![self.title.clone()];
        titles.extend(self.alternate_titles.clone());
        let mut seen = BTreeSet::new();
        titles
            .into_iter()
            .filter(|title| seen.insert(title.trim().to_ascii_lowercase()))
            .collect()
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ExternalAnimeMatchCandidate {
    pub anime: ExternalAnimeInfo,
    pub confidence: f64,
    pub matched_title: String,
    pub evidence: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ExternalAnimeInfo {
    pub id: ExternalAnimeId,
    pub titles: ExternalAnimeTitleSet,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub episode_count: Option<u32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub start_year: Option<u32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub image_url: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub summary: Option<String>,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub external_links: Vec<ExternalAnimeExternalLink>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ExternalAnimeTitleSet {
    pub primary: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub chinese: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub english: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub japanese: Option<String>,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub alternate_names: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ExternalAnimeListEntry {
    pub anime_id: ExternalAnimeId,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub status: Option<ExternalAnimeListStatus>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub watched_episodes: Option<u32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub score: Option<u32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub updated_at_epoch_ms: Option<u64>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum ExternalAnimeListStatus {
    Watching,
    Completed,
    OnHold,
    Dropped,
    PlanToWatch,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ExternalAnimeTrackingUpdate {
    pub anime_id: ExternalAnimeId,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub status: Option<ExternalAnimeListStatus>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub watched_episodes: Option<u32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub score: Option<u32>,
    #[serde(default = "default_true")]
    pub tracking_enabled: bool,
    #[serde(default = "default_true")]
    pub rating_enabled: bool,
}

fn default_true() -> bool {
    true
}

#[derive(Debug, Clone)]
pub struct MyAnimeListSearchClient {
    base_url: String,
    client_id: String,
}

impl MyAnimeListSearchClient {
    pub fn new(base_url: String, client_id: String) -> Self {
        Self {
            base_url: normalize_base_url(&base_url),
            client_id,
        }
    }
}

impl ExternalAnimeSearchClient for MyAnimeListSearchClient {
    fn provider(&self) -> ExternalAnimeProvider {
        ExternalAnimeProvider::MyAnimeList
    }

    fn search(
        &self,
        query: &ExternalAnimeMatchQuery,
        limit: u32,
    ) -> Result<Vec<ExternalAnimeInfo>> {
        if !(1..=MAX_SEARCH_LIMIT).contains(&limit) {
            return Err(LibraryServerError::new("limit must be between 1 and 50"));
        }
        let fields = "id,title,alternative_titles,num_episodes,start_date,main_picture,synopsis";
        let path = format!(
            "anime?q={}&limit={limit}&fields={}",
            url_encode(&query.title),
            url_encode(fields)
        );
        let response = request_json(
            "GET",
            &self.base_url,
            &path,
            BTreeMap::from([
                ("Accept".to_owned(), "application/json".to_owned()),
                ("X-MAL-CLIENT-ID".to_owned(), self.client_id.clone()),
            ]),
            None,
        )?;
        Ok(response["data"]
            .as_array()
            .into_iter()
            .flatten()
            .filter_map(|item| item.get("node"))
            .filter_map(to_mal_anime_info)
            .collect())
    }
}

#[derive(Debug, Clone)]
pub struct BangumiSearchClient {
    base_url: String,
    user_agent: String,
}

impl BangumiSearchClient {
    pub fn new(base_url: String, user_agent: String) -> Self {
        Self {
            base_url: normalize_base_url(&base_url),
            user_agent,
        }
    }

    fn fetch_subject(&self, anime_id: u64) -> Option<ExternalAnimeInfo> {
        request_json(
            "GET",
            &self.base_url,
            &format!("v0/subjects/{anime_id}"),
            BTreeMap::from([
                ("Accept".to_owned(), "application/json".to_owned()),
                ("User-Agent".to_owned(), self.user_agent.clone()),
            ]),
            None,
        )
        .ok()
        .and_then(|value| to_bangumi_anime_info(&value))
    }
}

impl ExternalAnimeSearchClient for BangumiSearchClient {
    fn provider(&self) -> ExternalAnimeProvider {
        ExternalAnimeProvider::Bangumi
    }

    fn search(
        &self,
        query: &ExternalAnimeMatchQuery,
        limit: u32,
    ) -> Result<Vec<ExternalAnimeInfo>> {
        if !(1..=MAX_SEARCH_LIMIT).contains(&limit) {
            return Err(LibraryServerError::new("limit must be between 1 and 50"));
        }
        let body = json!({
            "keyword": query.title,
            "filter": {
                "type": [2]
            }
        })
        .to_string();
        let response = request_json(
            "POST",
            &self.base_url,
            &format!("v0/search/subjects?limit={limit}&offset=0"),
            BTreeMap::from([
                ("Accept".to_owned(), "application/json".to_owned()),
                ("Content-Type".to_owned(), "application/json".to_owned()),
                ("User-Agent".to_owned(), self.user_agent.clone()),
            ]),
            Some(body.into_bytes()),
        )?;
        let results = response["data"]
            .as_array()
            .into_iter()
            .flatten()
            .filter_map(to_bangumi_anime_info)
            .collect::<Vec<_>>();
        Ok(results
            .into_iter()
            .enumerate()
            .map(|(index, anime)| {
                if index < 5 {
                    self.fetch_subject(anime.id.value).unwrap_or(anime)
                } else {
                    anime
                }
            })
            .collect())
    }
}

#[derive(Debug, Clone)]
pub struct MyAnimeListTrackingClient {
    base_url: String,
    access_token: String,
}

impl MyAnimeListTrackingClient {
    pub fn new(base_url: String, access_token: String) -> Self {
        Self {
            base_url: normalize_base_url(&base_url),
            access_token,
        }
    }
}

impl ExternalAnimeTrackingClient for MyAnimeListTrackingClient {
    fn provider(&self) -> ExternalAnimeProvider {
        ExternalAnimeProvider::MyAnimeList
    }

    fn fetch_list_entry(
        &self,
        anime_id: ExternalAnimeId,
    ) -> std::result::Result<Option<ExternalAnimeListEntry>, ExternalProviderError> {
        let response = tracking_request_json(
            "GET",
            &self.base_url,
            &format!("anime/{}?fields=my_list_status", anime_id.value),
            BTreeMap::from([
                ("Accept".to_owned(), "application/json".to_owned()),
                (
                    "Authorization".to_owned(),
                    format!("Bearer {}", self.access_token),
                ),
            ]),
            None,
        )?;
        Ok(response
            .get("my_list_status")
            .and_then(|value| to_mal_list_entry(anime_id, value)))
    }

    fn update_list_entry(
        &self,
        update: ExternalAnimeTrackingUpdate,
    ) -> std::result::Result<ExternalAnimeListEntry, ExternalProviderError> {
        let mut fields = Vec::<(String, String)>::new();
        if let Some(status) = update.status {
            fields.push(("status".to_owned(), mal_status(status).to_owned()));
        }
        if update.tracking_enabled
            && let Some(watched) = update.watched_episodes
        {
            fields.push(("num_watched_episodes".to_owned(), watched.to_string()));
        }
        if update.rating_enabled
            && let Some(score) = update.score
        {
            fields.push(("score".to_owned(), score.to_string()));
        }
        if fields.is_empty() {
            return Err(ExternalProviderError::Upstream(
                "MyAnimeList update must include at least one field".to_owned(),
            ));
        }
        let response = tracking_request_json(
            "PATCH",
            &self.base_url,
            &format!("anime/{}/my_list_status", update.anime_id.value),
            BTreeMap::from([
                ("Accept".to_owned(), "application/json".to_owned()),
                (
                    "Authorization".to_owned(),
                    format!("Bearer {}", self.access_token),
                ),
                (
                    "Content-Type".to_owned(),
                    "application/x-www-form-urlencoded".to_owned(),
                ),
            ]),
            Some(form_encode(&fields).into_bytes()),
        )?;
        Ok(ExternalAnimeListEntry {
            anime_id: update.anime_id,
            status: response
                .get("status")
                .and_then(Value::as_str)
                .and_then(parse_mal_status),
            watched_episodes: value_u32(&response, "num_episodes_watched")
                .or(update.watched_episodes),
            score: value_u32(&response, "score").or(update.score),
            updated_at_epoch_ms: None,
        })
    }
}

#[derive(Debug, Clone)]
pub struct BangumiTrackingClient {
    base_url: String,
    user_agent: String,
    access_token: String,
}

impl BangumiTrackingClient {
    pub fn new(base_url: String, user_agent: String, access_token: String) -> Self {
        Self {
            base_url: normalize_base_url(&base_url),
            user_agent,
            access_token,
        }
    }
}

impl ExternalAnimeTrackingClient for BangumiTrackingClient {
    fn provider(&self) -> ExternalAnimeProvider {
        ExternalAnimeProvider::Bangumi
    }

    fn fetch_list_entry(
        &self,
        anime_id: ExternalAnimeId,
    ) -> std::result::Result<Option<ExternalAnimeListEntry>, ExternalProviderError> {
        let response = tracking_request_json(
            "GET",
            &self.base_url,
            &format!("v0/users/-/collections/{}", anime_id.value),
            self.headers(None),
            None,
        )?;
        Ok(to_bangumi_list_entry(anime_id, &response))
    }

    fn update_list_entry(
        &self,
        update: ExternalAnimeTrackingUpdate,
    ) -> std::result::Result<ExternalAnimeListEntry, ExternalProviderError> {
        let mut object = serde_json::Map::new();
        if let Some(status) = update.status {
            object.insert("type".to_owned(), json!(bangumi_status(status)));
        }
        if update.tracking_enabled
            && let Some(watched) = update.watched_episodes
        {
            object.insert("ep_status".to_owned(), json!(watched));
        }
        if update.rating_enabled
            && let Some(score) = update.score
        {
            object.insert("rate".to_owned(), json!(score));
        }
        if object.is_empty() {
            return Err(ExternalProviderError::Upstream(
                "Bangumi update must include at least one field".to_owned(),
            ));
        }
        let response = tracking_request_json(
            "PATCH",
            &self.base_url,
            &format!("v0/users/-/collections/{}", update.anime_id.value),
            self.headers(Some("application/json")),
            Some(Value::Object(object).to_string().into_bytes()),
        )?;
        Ok(ExternalAnimeListEntry {
            anime_id: update.anime_id,
            status: value_u32(&response, "type").and_then(parse_bangumi_status),
            watched_episodes: value_u32(&response, "ep_status").or(update.watched_episodes),
            score: value_u32(&response, "rate").or(update.score),
            updated_at_epoch_ms: None,
        })
    }
}

impl BangumiTrackingClient {
    fn headers(&self, content_type: Option<&str>) -> BTreeMap<String, String> {
        let mut headers = BTreeMap::from([
            ("Accept".to_owned(), "application/json".to_owned()),
            (
                "Authorization".to_owned(),
                format!("Bearer {}", self.access_token),
            ),
            ("User-Agent".to_owned(), self.user_agent.clone()),
        ]);
        if let Some(content_type) = content_type {
            headers.insert("Content-Type".to_owned(), content_type.to_owned());
        }
        headers
    }
}

pub fn parse_provider_alias(value: &str) -> Option<ExternalAnimeProvider> {
    match value.trim().to_ascii_lowercase().replace('-', "_").as_str() {
        "myanimelist" | "my_anime_list" | "mal" => Some(ExternalAnimeProvider::MyAnimeList),
        "bangumi" | "bgm" => Some(ExternalAnimeProvider::Bangumi),
        "dandanplay" | "dan_dan_play" => Some(ExternalAnimeProvider::Dandanplay),
        _ => None,
    }
}

pub fn provider_runtime_status(settings: &HeadlessServerSettings) -> LanProviderRuntimeStatus {
    LanProviderRuntimeStatus {
        dandanplay: LanDandanplayRuntimeCapability {
            match_available: settings.dandanplay.is_fetch_enabled(),
            comment_fetch_available: settings.dandanplay.is_fetch_enabled(),
            authenticated: settings.dandanplay.has_credentials(),
            reason_code: if settings.dandanplay.has_credentials() {
                "credentials-configured".to_owned()
            } else if settings.dandanplay.is_fetch_enabled() {
                "compatible-api-server".to_owned()
            } else {
                "missing-credentials".to_owned()
            },
        },
        my_anime_list: {
            let search_available = settings.external_anime.my_anime_list_client_id.is_some();
            let list_available = settings.external_anime.my_anime_list_access_token.is_some();
            LanExternalAnimeRuntimeCapability {
                search_available,
                list_read_available: list_available,
                list_write_available: list_available,
                authenticated: list_available,
                reason_code: if list_available {
                    "oauth-token-saved".to_owned()
                } else if search_available
                    && settings.external_anime.has_my_anime_list_client_secret
                {
                    "client-secret-saved".to_owned()
                } else if search_available {
                    "client-id-saved".to_owned()
                } else {
                    "missing-client-id".to_owned()
                },
            }
        },
        bangumi: {
            let list_available = settings.external_anime.bangumi_access_token.is_some();
            LanExternalAnimeRuntimeCapability {
                search_available: true,
                list_read_available: list_available,
                list_write_available: list_available,
                authenticated: list_available,
                reason_code: if list_available {
                    "access-token-saved".to_owned()
                } else {
                    "public-search".to_owned()
                },
            }
        },
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LanProviderRuntimeStatus {
    pub dandanplay: LanDandanplayRuntimeCapability,
    #[serde(rename = "myAnimeList")]
    pub my_anime_list: LanExternalAnimeRuntimeCapability,
    pub bangumi: LanExternalAnimeRuntimeCapability,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LanDandanplayRuntimeCapability {
    pub match_available: bool,
    pub comment_fetch_available: bool,
    pub authenticated: bool,
    pub reason_code: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LanExternalAnimeRuntimeCapability {
    pub search_available: bool,
    pub list_read_available: bool,
    pub list_write_available: bool,
    pub authenticated: bool,
    pub reason_code: String,
}

fn request_json(
    method: &str,
    base_url: &str,
    path_and_query: &str,
    mut headers: BTreeMap<String, String>,
    body: Option<Vec<u8>>,
) -> Result<Value> {
    let url = resolve_url(base_url, path_and_query)?;
    if let Some(body) = &body {
        headers.insert("Content-Length".to_owned(), body.len().to_string());
    }
    let response = send_http_request(HttpRequest {
        method: method.to_owned(),
        url: url.clone(),
        headers,
        body: body.unwrap_or_default(),
    })?;
    if response.body.len() > MAX_RESPONSE_BYTES {
        return Err(LibraryServerError::new(format!(
            "external anime response exceeded {MAX_RESPONSE_BYTES} bytes"
        )));
    }
    if !(200..=299).contains(&response.status) {
        return Err(LibraryServerError::new(format!(
            "external anime request failed with HTTP {}: {}",
            response.status,
            String::from_utf8_lossy(&response.body)
                .chars()
                .take(200)
                .collect::<String>()
        )));
    }
    serde_json::from_slice(&response.body)
        .map_err(|error| LibraryServerError::with_context(error, "external anime JSON was invalid"))
}

fn tracking_request_json(
    method: &str,
    base_url: &str,
    path_and_query: &str,
    mut headers: BTreeMap<String, String>,
    body: Option<Vec<u8>>,
) -> std::result::Result<Value, ExternalProviderError> {
    let url = resolve_url(base_url, path_and_query)
        .map_err(|error| ExternalProviderError::Upstream(error.to_string()))?;
    if let Some(body) = &body {
        headers.insert("Content-Length".to_owned(), body.len().to_string());
    }
    let response = send_http_request(HttpRequest {
        method: method.to_owned(),
        url,
        headers,
        body: body.unwrap_or_default(),
    })
    .map_err(|error| ExternalProviderError::Upstream(error.to_string()))?;
    if response.status == 404 {
        return Err(ExternalProviderError::NotFound("HTTP 404".to_owned()));
    }
    if !(200..=299).contains(&response.status) {
        return Err(ExternalProviderError::Upstream(format!(
            "external anime tracking request failed with HTTP {}: {}",
            response.status,
            String::from_utf8_lossy(&response.body)
                .chars()
                .take(200)
                .collect::<String>()
        )));
    }
    serde_json::from_slice(&response.body).map_err(|error| {
        ExternalProviderError::Upstream(format!(
            "external anime tracking JSON was invalid: {error}"
        ))
    })
}

fn resolve_url(base_url: &str, path_and_query: &str) -> Result<ParsedUrl> {
    let base = parse_url(base_url)?;
    let base_path = base.path_and_query.split('?').next().unwrap_or("/");
    let path = format!(
        "{}/{}",
        base_path.trim_end_matches('/'),
        path_and_query.trim_start_matches('/')
    );
    Ok(ParsedUrl {
        path_and_query: path,
        ..base
    })
}

fn normalize_base_url(value: &str) -> String {
    value.trim().trim_end_matches('/').to_owned()
}

fn to_mal_anime_info(value: &Value) -> Option<ExternalAnimeInfo> {
    let id = value.get("id")?.as_u64().filter(|value| *value > 0)?;
    let primary_title = value.get("title")?.as_str()?.trim().to_owned();
    if primary_title.is_empty() {
        return None;
    }
    let alternative_titles = value.get("alternative_titles");
    let synonyms = alternative_titles
        .and_then(|value| value.get("synonyms"))
        .and_then(Value::as_array)
        .into_iter()
        .flatten()
        .filter_map(Value::as_str)
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .map(ToOwned::to_owned)
        .collect();
    let anime_id = ExternalAnimeId {
        provider: ExternalAnimeProvider::MyAnimeList,
        value: id,
    };
    Some(ExternalAnimeInfo {
        id: anime_id.clone(),
        titles: ExternalAnimeTitleSet {
            primary: primary_title,
            chinese: None,
            english: alternative_titles
                .and_then(|value| value.get("en"))
                .and_then(Value::as_str)
                .filter(|value| !value.trim().is_empty())
                .map(ToOwned::to_owned),
            japanese: alternative_titles
                .and_then(|value| value.get("ja"))
                .and_then(Value::as_str)
                .filter(|value| !value.trim().is_empty())
                .map(ToOwned::to_owned),
            alternate_names: synonyms,
        },
        episode_count: value_u32(value, "num_episodes").filter(|value| *value > 0),
        start_year: value
            .get("start_date")
            .and_then(Value::as_str)
            .and_then(|value| value.get(0..4))
            .and_then(|value| value.parse::<u32>().ok())
            .filter(|value| (1900..=2200).contains(value)),
        image_url: value.get("main_picture").and_then(|picture| {
            https_url(picture.get("large")).or_else(|| https_url(picture.get("medium")))
        }),
        summary: value
            .get("synopsis")
            .and_then(Value::as_str)
            .filter(|value| !value.trim().is_empty())
            .map(ToOwned::to_owned),
        external_links: vec![ExternalAnimeExternalLink {
            anime_id,
            url: format!("https://myanimelist.net/anime/{id}"),
        }],
    })
}

fn to_bangumi_anime_info(value: &Value) -> Option<ExternalAnimeInfo> {
    let id = value.get("id")?.as_u64().filter(|value| *value > 0)?;
    let primary_title = value.get("name")?.as_str()?.trim().to_owned();
    if primary_title.is_empty() {
        return None;
    }
    let aliases = infobox_values(value, &["别名", "英文名", "英语名", "日文名", "原名"]);
    let chinese_title = value
        .get("name_cn")
        .and_then(Value::as_str)
        .filter(|value| !value.trim().is_empty())
        .map(ToOwned::to_owned)
        .or_else(|| infobox_values(value, &["中文名"]).into_iter().next());
    let english_title = aliases
        .iter()
        .find(|title| looks_english_title(title))
        .cloned();
    let japanese_title = aliases
        .iter()
        .find(|title| contains_japanese(title))
        .cloned()
        .or_else(|| contains_japanese(&primary_title).then(|| primary_title.clone()));
    let mut alternate_names = Vec::new();
    for title in aliases
        .into_iter()
        .chain(infobox_values(value, &["中文名"]))
    {
        if title != primary_title
            && Some(&title) != chinese_title.as_ref()
            && Some(&title) != english_title.as_ref()
            && Some(&title) != japanese_title.as_ref()
            && !alternate_names
                .iter()
                .any(|existing: &String| existing.eq_ignore_ascii_case(&title))
        {
            alternate_names.push(title);
        }
    }
    let anime_id = ExternalAnimeId {
        provider: ExternalAnimeProvider::Bangumi,
        value: id,
    };
    Some(ExternalAnimeInfo {
        id: anime_id.clone(),
        titles: ExternalAnimeTitleSet {
            primary: primary_title,
            chinese: chinese_title,
            english: english_title,
            japanese: japanese_title,
            alternate_names,
        },
        episode_count: value_u32(value, "eps").filter(|value| *value > 0),
        start_year: value
            .get("date")
            .or_else(|| value.get("air_date"))
            .and_then(Value::as_str)
            .and_then(|value| value.get(0..4))
            .and_then(|value| value.parse::<u32>().ok())
            .filter(|value| (1900..=2200).contains(value)),
        image_url: value.get("images").and_then(|images| {
            https_url(images.get("large"))
                .or_else(|| https_url(images.get("common")))
                .or_else(|| https_url(images.get("medium")))
        }),
        summary: value
            .get("summary")
            .or_else(|| value.get("short_summary"))
            .and_then(Value::as_str)
            .filter(|value| !value.trim().is_empty())
            .map(ToOwned::to_owned),
        external_links: vec![ExternalAnimeExternalLink {
            anime_id,
            url: format!("https://bangumi.tv/subject/{id}"),
        }],
    })
}

fn infobox_values(root: &Value, keys: &[&str]) -> Vec<String> {
    let mut values = Vec::new();
    let Some(items) = root.get("infobox").and_then(Value::as_array) else {
        return values;
    };
    for item in items {
        let Some(key) = item.get("key").and_then(Value::as_str) else {
            continue;
        };
        if !keys.contains(&key) {
            continue;
        }
        collect_infobox_text_values(item.get("value"), &mut values);
    }
    values.sort();
    values.dedup();
    values
}

fn collect_infobox_text_values(value: Option<&Value>, values: &mut Vec<String>) {
    match value {
        Some(Value::String(text)) => push_non_blank(values, text),
        Some(Value::Array(items)) => {
            for item in items {
                match item {
                    Value::String(text) => push_non_blank(values, text),
                    Value::Object(object) => {
                        for key in ["v", "value", "name"] {
                            if let Some(Value::String(text)) = object.get(key) {
                                push_non_blank(values, text);
                                break;
                            }
                        }
                    }
                    _ => {}
                }
            }
        }
        Some(Value::Object(object)) => {
            for key in ["v", "value", "name"] {
                if let Some(Value::String(text)) = object.get(key) {
                    push_non_blank(values, text);
                    break;
                }
            }
        }
        _ => {}
    }
}

fn push_non_blank(values: &mut Vec<String>, text: &str) {
    let trimmed = text.trim();
    if !trimmed.is_empty() {
        values.push(trimmed.to_owned());
    }
}

fn rank_external_anime_matches(
    query: &ExternalAnimeMatchQuery,
    anime: Vec<ExternalAnimeInfo>,
) -> Vec<ExternalAnimeMatchCandidate> {
    let mut candidates = anime
        .into_iter()
        .map(|anime| {
            let (matched_title, mut confidence) = best_title_match(&query.title, &anime);
            let mut evidence = Vec::new();
            if confidence >= 0.8 {
                evidence.push("title-match".to_owned());
            }
            if query.episode_count.is_some() && query.episode_count == anime.episode_count {
                confidence += 0.08;
                evidence.push("episode-count-match".to_owned());
            }
            if query.start_year.is_some() && query.start_year == anime.start_year {
                confidence += 0.06;
                evidence.push("start-year-match".to_owned());
            }
            ExternalAnimeMatchCandidate {
                anime,
                confidence: confidence.min(1.0),
                matched_title,
                evidence,
            }
        })
        .collect::<Vec<_>>();
    candidates.sort_by(|left, right| {
        right
            .confidence
            .partial_cmp(&left.confidence)
            .unwrap_or(std::cmp::Ordering::Equal)
            .then_with(|| left.anime.id.value.cmp(&right.anime.id.value))
    });
    candidates
}

fn best_title_match(query_title: &str, anime: &ExternalAnimeInfo) -> (String, f64) {
    anime
        .titles()
        .into_iter()
        .map(|title| {
            let score = title_similarity(query_title, &title);
            (title, score)
        })
        .max_by(|left, right| {
            left.1
                .partial_cmp(&right.1)
                .unwrap_or(std::cmp::Ordering::Equal)
        })
        .unwrap_or_else(|| (anime.titles.primary.clone(), 0.0))
}

impl ExternalAnimeInfo {
    fn titles(&self) -> Vec<String> {
        let mut values = vec![self.titles.primary.clone()];
        values.extend(self.titles.chinese.clone());
        values.extend(self.titles.english.clone());
        values.extend(self.titles.japanese.clone());
        values.extend(self.titles.alternate_names.clone());
        values
    }
}

fn title_similarity(left: &str, right: &str) -> f64 {
    let left = normalize_title(left);
    let right = normalize_title(right);
    if left.is_empty() || right.is_empty() {
        return 0.0;
    }
    if left == right {
        return 0.9;
    }
    if left.contains(&right) || right.contains(&left) {
        return 0.75;
    }
    let left_terms = left.split_whitespace().collect::<BTreeSet<_>>();
    let right_terms = right.split_whitespace().collect::<BTreeSet<_>>();
    let overlap = left_terms.intersection(&right_terms).count();
    let union = left_terms.union(&right_terms).count().max(1);
    0.5 * (overlap as f64 / union as f64)
}

fn normalize_title(value: &str) -> String {
    value
        .to_ascii_lowercase()
        .chars()
        .map(|ch| if ch.is_alphanumeric() { ch } else { ' ' })
        .collect::<String>()
        .split_whitespace()
        .collect::<Vec<_>>()
        .join(" ")
}

fn to_mal_list_entry(anime_id: ExternalAnimeId, value: &Value) -> Option<ExternalAnimeListEntry> {
    Some(ExternalAnimeListEntry {
        anime_id,
        status: value
            .get("status")
            .and_then(Value::as_str)
            .and_then(parse_mal_status),
        watched_episodes: value_u32(value, "num_episodes_watched"),
        score: value_u32(value, "score"),
        updated_at_epoch_ms: value
            .get("updated_at")
            .and_then(Value::as_str)
            .and_then(parse_epoch_ms),
    })
}

fn to_bangumi_list_entry(
    anime_id: ExternalAnimeId,
    value: &Value,
) -> Option<ExternalAnimeListEntry> {
    Some(ExternalAnimeListEntry {
        anime_id,
        status: value_u32(value, "type").and_then(parse_bangumi_status),
        watched_episodes: value_u32(value, "ep_status"),
        score: value_u32(value, "rate"),
        updated_at_epoch_ms: None,
    })
}

fn mal_status(status: ExternalAnimeListStatus) -> &'static str {
    match status {
        ExternalAnimeListStatus::Watching => "watching",
        ExternalAnimeListStatus::Completed => "completed",
        ExternalAnimeListStatus::OnHold => "on_hold",
        ExternalAnimeListStatus::Dropped => "dropped",
        ExternalAnimeListStatus::PlanToWatch => "plan_to_watch",
    }
}

fn parse_mal_status(value: &str) -> Option<ExternalAnimeListStatus> {
    match value {
        "watching" => Some(ExternalAnimeListStatus::Watching),
        "completed" => Some(ExternalAnimeListStatus::Completed),
        "on_hold" => Some(ExternalAnimeListStatus::OnHold),
        "dropped" => Some(ExternalAnimeListStatus::Dropped),
        "plan_to_watch" => Some(ExternalAnimeListStatus::PlanToWatch),
        _ => None,
    }
}

fn bangumi_status(status: ExternalAnimeListStatus) -> u32 {
    match status {
        ExternalAnimeListStatus::PlanToWatch => 1,
        ExternalAnimeListStatus::Completed => 2,
        ExternalAnimeListStatus::Watching => 3,
        ExternalAnimeListStatus::OnHold => 4,
        ExternalAnimeListStatus::Dropped => 5,
    }
}

fn parse_bangumi_status(value: u32) -> Option<ExternalAnimeListStatus> {
    match value {
        1 => Some(ExternalAnimeListStatus::PlanToWatch),
        2 => Some(ExternalAnimeListStatus::Completed),
        3 => Some(ExternalAnimeListStatus::Watching),
        4 => Some(ExternalAnimeListStatus::OnHold),
        5 => Some(ExternalAnimeListStatus::Dropped),
        _ => None,
    }
}

fn provider_display_name(provider: ExternalAnimeProvider) -> &'static str {
    match provider {
        ExternalAnimeProvider::MyAnimeList => "MyAnimeList",
        ExternalAnimeProvider::Bangumi => "Bangumi",
        ExternalAnimeProvider::Dandanplay => "dandanplay",
    }
}

fn value_u32(value: &Value, key: &str) -> Option<u32> {
    value
        .get(key)
        .and_then(Value::as_u64)
        .and_then(|value| u32::try_from(value).ok())
}

fn https_url(value: Option<&Value>) -> Option<String> {
    value
        .and_then(Value::as_str)
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .map(|value| value.replacen("http://", "https://", 1))
        .filter(|value| value.starts_with("https://"))
}

fn looks_english_title(value: &str) -> bool {
    value.chars().any(|ch| ch.is_ascii_alphabetic()) && !value.chars().any(is_cjk_or_kana)
}

fn contains_japanese(value: &str) -> bool {
    value.chars().any(|ch| {
        ('\u{3040}'..='\u{30ff}').contains(&ch) || ('\u{31f0}'..='\u{31ff}').contains(&ch)
    })
}

fn is_cjk_or_kana(ch: char) -> bool {
    ('\u{3040}'..='\u{30ff}').contains(&ch)
        || ('\u{31f0}'..='\u{31ff}').contains(&ch)
        || ('\u{3400}'..='\u{4dbf}').contains(&ch)
        || ('\u{4e00}'..='\u{9fff}').contains(&ch)
}

fn parse_epoch_ms(value: &str) -> Option<u64> {
    let (date, time_zone) = value.split_once('T')?;
    let mut date_parts = date.split('-');
    let year = date_parts.next()?.parse::<i32>().ok()?;
    let month = date_parts.next()?.parse::<u32>().ok()?;
    let day = date_parts.next()?.parse::<u32>().ok()?;
    let time = time_zone.strip_suffix('Z').unwrap_or(time_zone);
    let time = time.strip_suffix("+00:00").unwrap_or(time);
    let mut time_parts = time.split(':');
    let hour = time_parts.next()?.parse::<u32>().ok()?;
    let minute = time_parts.next()?.parse::<u32>().ok()?;
    let second = time_parts.next()?.parse::<u32>().ok()?;
    let days = days_from_civil(year, month, day)?;
    Some((((days * 24 + hour as i64) * 60 + minute as i64) * 60 + second as i64) as u64 * 1_000)
}

fn days_from_civil(year: i32, month: u32, day: u32) -> Option<i64> {
    if !(1..=12).contains(&month) || !(1..=31).contains(&day) {
        return None;
    }
    let year = year - i32::from(month <= 2);
    let era = if year >= 0 { year } else { year - 399 } / 400;
    let yoe = year - era * 400;
    let month = month as i32;
    let day = day as i32;
    let doy = (153 * (month + if month > 2 { -3 } else { 9 }) + 2) / 5 + day - 1;
    let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    Some((era * 146_097 + doe - 719_468) as i64)
}

fn form_encode(fields: &[(String, String)]) -> String {
    fields
        .iter()
        .map(|(key, value)| format!("{}={}", url_encode(key), url_encode(value)))
        .collect::<Vec<_>>()
        .join("&")
}

pub fn current_epoch_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis()
        .min(u64::MAX as u128) as u64
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn provider_aliases_match_headless_route_contract() {
        for alias in ["myanimelist", "my_anime_list", "mal"] {
            assert_eq!(
                Some(ExternalAnimeProvider::MyAnimeList),
                parse_provider_alias(alias)
            );
        }
        for alias in ["bangumi", "bgm"] {
            assert_eq!(
                Some(ExternalAnimeProvider::Bangumi),
                parse_provider_alias(alias)
            );
        }
        for alias in ["dandanplay", "dan_dan_play"] {
            assert_eq!(
                Some(ExternalAnimeProvider::Dandanplay),
                parse_provider_alias(alias)
            );
        }
        assert_eq!(None, parse_provider_alias("unknown"));
    }

    #[test]
    fn runtime_reason_codes_match_headless_settings() {
        let mut settings = HeadlessServerSettings {
            pairing_token: "123456".to_owned(),
            library_roots: Vec::new(),
            dandanplay: Default::default(),
            external_anime: Default::default(),
        };
        let runtime = provider_runtime_status(&settings);
        assert_eq!("missing-client-id", runtime.my_anime_list.reason_code);
        assert_eq!("public-search", runtime.bangumi.reason_code);

        settings.external_anime.my_anime_list_client_id = Some("client".to_owned());
        settings.external_anime.my_anime_list_access_token = Some("token".to_owned());
        settings.external_anime.bangumi_access_token = Some("token".to_owned());
        let runtime = provider_runtime_status(&settings);
        assert_eq!("oauth-token-saved", runtime.my_anime_list.reason_code);
        assert_eq!("access-token-saved", runtime.bangumi.reason_code);
    }
}
