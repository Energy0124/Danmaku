use std::collections::{BTreeMap, BTreeSet};

use serde::{Deserialize, Serialize};
use serde_json::{Value, json};

use crate::dandanplay::{HttpRequest, parse_url, send_http_request};
use crate::settings::{AniRssPathMapping, HeadlessAniRssMode, HeadlessAniRssSettings};
use crate::{LibraryServerError, Result};

pub const SUPPORTED_SOURCES: [&str; 4] = ["MIKAN", "ANIBT", "ANIME_GARDEN", "CUSTOM_RSS"];

#[derive(Clone)]
pub struct AniRssService {
    base_url: String,
    api_key: String,
    path_mappings: Vec<AniRssPathMapping>,
}

impl std::fmt::Debug for AniRssService {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        formatter
            .debug_struct("AniRssService")
            .field("base_url", &self.base_url)
            .finish_non_exhaustive()
    }
}

impl AniRssService {
    pub fn from_settings(settings: &HeadlessAniRssSettings) -> Result<Self> {
        if settings.mode == HeadlessAniRssMode::Disabled {
            return Err(LibraryServerError::new("ANI-RSS integration is disabled"));
        }
        let api_key = settings
            .api_key
            .as_deref()
            .map(str::trim)
            .filter(|value| !value.is_empty())
            .ok_or_else(|| LibraryServerError::new("ANI-RSS API key is not configured"))?;
        Ok(Self {
            base_url: settings.base_url.trim_end_matches('/').to_owned(),
            api_key: api_key.to_owned(),
            path_mappings: settings.path_mappings.clone(),
        })
    }

    pub fn status(&self, mode: HeadlessAniRssMode) -> AniRssStatus {
        match self.post_value("/api/about", None) {
            Ok(value) => AniRssStatus {
                configured: true,
                reachable: true,
                mode: mode.wire_name().to_owned(),
                version: value
                    .get("version")
                    .and_then(Value::as_str)
                    .map(ToOwned::to_owned),
                message: "ANI-RSS is ready".to_owned(),
            },
            Err(error) => AniRssStatus {
                configured: true,
                reachable: false,
                mode: mode.wire_name().to_owned(),
                version: None,
                message: error.to_string(),
            },
        }
    }

    pub fn subscriptions(&self) -> Result<Vec<AniRssSubscriptionSummary>> {
        let value = self.post_value("/api/listAni", None)?;
        let mut subscriptions = Vec::new();
        if let Some(weeks) = value.get("weekList").and_then(Value::as_array) {
            for week in weeks {
                let week_label = string(week, "weekLabel");
                if let Some(items) = week.get("items").and_then(Value::as_array) {
                    subscriptions.extend(items.iter().filter_map(|item| {
                        let id = string(item, "id")?;
                        Some(AniRssSubscriptionSummary {
                            id,
                            title: string(item, "title").unwrap_or_else(|| "Untitled".to_owned()),
                            source: normalize_source(string(item, "type").as_deref()),
                            rss_url: string(item, "url"),
                            subgroup: string(item, "subgroup"),
                            enabled: item.get("enable").and_then(Value::as_bool).unwrap_or(false),
                            current_episode: item
                                .get("currentEpisodeNumber")
                                .and_then(Value::as_u64)
                                .and_then(|value| u32::try_from(value).ok()),
                            total_episodes: item
                                .get("totalEpisodeNumber")
                                .and_then(Value::as_u64)
                                .and_then(|value| u32::try_from(value).ok()),
                            last_download_at_epoch_ms: item
                                .get("lastDownloadTime")
                                .and_then(Value::as_u64),
                            week_label: week_label.clone(),
                        })
                    }));
                }
            }
        }
        Ok(subscriptions)
    }

    pub fn search(&self, request: &AniRssSearchRequest) -> Result<Vec<AniRssSearchResult>> {
        let source = approved_source_name(&request.source)?;
        let query = request.query.trim().to_lowercase();
        let value = match source {
            "MIKAN" => {
                let path = format!("/api/mikan?text={}", percent_encode(request.query.trim()));
                self.post_value(
                    &path,
                    Some(json!({
                        "year": request.year,
                        "season": request.season,
                        "select": true
                    })),
                )?
            }
            "ANIBT" => self.post_value(
                "/api/aniBT",
                Some(json!({
                    "season": request.season,
                    "bgmUrl": request.bgm_url,
                    "title": request.query
                })),
            )?,
            "ANIME_GARDEN" => {
                let path = request
                    .bgm_url
                    .as_deref()
                    .filter(|value| !value.trim().is_empty())
                    .map(|value| format!("/api/animeGardenList?bgmUrl={}", percent_encode(value)))
                    .unwrap_or_else(|| "/api/animeGardenList".to_owned());
                self.post_value(&path, None)?
            }
            _ => {
                return Err(LibraryServerError::new(
                    "custom RSS does not support search",
                ));
            }
        };
        Ok(normalize_search_results(source, &query, &value))
    }

    pub fn groups(&self, request: &AniRssGroupsRequest) -> Result<Vec<AniRssGroup>> {
        let source = approved_source_name(&request.source)?;
        let path = match source {
            "MIKAN" => format!("/api/mikanGroup?url={}", percent_encode(&request.locator)),
            "ANIBT" => format!("/api/aniBTGroup?bgmId={}", percent_encode(&request.locator)),
            "ANIME_GARDEN" => format!(
                "/api/animeGardenGroup?bgmId={}",
                percent_encode(&request.locator)
            ),
            _ => {
                return Err(LibraryServerError::new(
                    "custom RSS does not have group discovery",
                ));
            }
        };
        let value = self.post_value(&path, None)?;
        Ok(value
            .as_array()
            .into_iter()
            .flatten()
            .filter_map(|item| {
                let rss_url = string(item, "rss")?;
                let name = string(item, "label")
                    .or_else(|| string(item, "name"))
                    .unwrap_or_else(|| "Default".to_owned());
                let sample_titles = item
                    .get("items")
                    .and_then(Value::as_array)
                    .map(|items| {
                        items
                            .iter()
                            .filter_map(|item| string(item, "title"))
                            .take(5)
                            .collect()
                    })
                    .unwrap_or_default();
                Some(AniRssGroup {
                    name,
                    rss_url,
                    bgm_url: string(item, "bgmUrl"),
                    sample_titles,
                })
            })
            .collect())
    }

    pub fn preview(
        &self,
        request: &AniRssSubscriptionRequest,
    ) -> Result<AniRssSubscriptionPreview> {
        let candidate = self.subscription_candidate(request)?;
        let preview = self.post_value("/api/previewAni", Some(candidate.clone()))?;
        Ok(AniRssSubscriptionPreview {
            title: string(&candidate, "title").unwrap_or_else(|| request.title.clone()),
            rss_url: request.rss_url.clone(),
            source: approved_source_name(&request.source)?.to_owned(),
            subgroup: string(&candidate, "subgroup"),
            enabled: candidate
                .get("enable")
                .and_then(Value::as_bool)
                .unwrap_or(true),
            download_path: preview
                .get("downloadPath")
                .and_then(Value::as_str)
                .map(ToOwned::to_owned),
            sample_titles: collect_preview_titles(&preview),
        })
    }

    pub fn add_subscription(&self, request: &AniRssSubscriptionRequest) -> Result<()> {
        let candidate = self.subscription_candidate(request)?;
        self.post_value("/api/addAni", Some(candidate))?;
        Ok(())
    }

    pub fn set_enabled(&self, id: &str, enabled: bool) -> Result<()> {
        let path = format!("/api/batchEnable?value={enabled}");
        self.post_value(&path, Some(json!([id])))?;
        Ok(())
    }

    pub fn refresh(&self, id: &str) -> Result<()> {
        self.post_value("/api/refreshAni", Some(json!({ "id": id })))?;
        Ok(())
    }

    pub fn remove(&self, id: &str) -> Result<()> {
        self.post_value("/api/deleteAni?deleteFiles=false", Some(json!([id])))?;
        Ok(())
    }

    pub fn downloads(&self) -> Result<Vec<AniRssDownloadJob>> {
        let value = self.post_value("/api/torrentsInfos", None)?;
        Ok(value
            .as_array()
            .into_iter()
            .flatten()
            .filter_map(|item| {
                Some(AniRssDownloadJob {
                    id: string(item, "id").or_else(|| string(item, "hash"))?,
                    name: string(item, "name").unwrap_or_else(|| "Download".to_owned()),
                    state: string(item, "state").unwrap_or_else(|| "UNKNOWN".to_owned()),
                    progress_percent: item.get("progress").and_then(Value::as_f64).unwrap_or(0.0),
                    completed_bytes: item.get("completed").and_then(Value::as_u64),
                    total_bytes: item.get("size").and_then(Value::as_u64),
                    save_path: string(item, "savePath")
                        .or_else(|| string(item, "save_path"))
                        .map(|path| self.map_path(&path)),
                })
            })
            .collect())
    }

    fn map_path(&self, path: &str) -> String {
        self.path_mappings
            .iter()
            .filter(|mapping| path.starts_with(mapping.remote_prefix.trim_end_matches(['/', '\\'])))
            .max_by_key(|mapping| mapping.remote_prefix.len())
            .map(|mapping| {
                let remote = mapping.remote_prefix.trim_end_matches(['/', '\\']);
                let suffix = path.strip_prefix(remote).unwrap_or_default();
                format!(
                    "{}{}",
                    mapping.local_prefix.trim_end_matches(['/', '\\']),
                    suffix
                )
            })
            .unwrap_or_else(|| path.to_owned())
    }

    fn subscription_candidate(&self, request: &AniRssSubscriptionRequest) -> Result<Value> {
        let source = approved_source_name(&request.source)?;
        let mut candidate = self.post_value(
            "/api/rssToAni",
            Some(json!({
                "url": request.rss_url,
                "type": source_api_name(source),
                "bgmUrl": request.bgm_url,
                "subgroup": request.subgroup,
                "enable": request.enabled
            })),
        )?;
        let object = candidate
            .as_object_mut()
            .ok_or_else(|| LibraryServerError::new("ANI-RSS returned an invalid subscription"))?;
        if !request.title.trim().is_empty() {
            object.insert(
                "title".to_owned(),
                Value::String(request.title.trim().to_owned()),
            );
        }
        object.insert("enable".to_owned(), Value::Bool(request.enabled));
        Ok(candidate)
    }

    fn post_value(&self, path: &str, body: Option<Value>) -> Result<Value> {
        let url = parse_url(&format!("{}{}", self.base_url, path))?;
        let mut headers = BTreeMap::new();
        headers.insert("x-api-key".to_owned(), self.api_key.clone());
        if body.is_some() {
            headers.insert("Content-Type".to_owned(), "application/json".to_owned());
        }
        let response = send_http_request(HttpRequest {
            method: "POST".to_owned(),
            url,
            headers,
            body: body
                .map(|value| serde_json::to_vec(&value))
                .transpose()?
                .unwrap_or_default(),
        })?;
        if !(200..300).contains(&response.status) {
            return Err(LibraryServerError::new(format!(
                "ANI-RSS returned HTTP {}",
                response.status
            )));
        }
        let envelope: AniRssEnvelope = serde_json::from_slice(&response.body)
            .map_err(|error| LibraryServerError::with_context(error, "invalid ANI-RSS response"))?;
        if !(200..300).contains(&envelope.code) {
            return Err(LibraryServerError::new(format!(
                "ANI-RSS request failed: {}",
                envelope.message
            )));
        }
        Ok(envelope.data.unwrap_or(Value::Null))
    }
}

#[derive(Debug, Deserialize)]
struct AniRssEnvelope {
    code: u16,
    #[serde(default)]
    message: String,
    #[serde(default)]
    data: Option<Value>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AniRssStatus {
    pub configured: bool,
    pub reachable: bool,
    pub mode: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub version: Option<String>,
    pub message: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AniRssSearchRequest {
    pub source: String,
    pub query: String,
    #[serde(default)]
    pub year: Option<u32>,
    #[serde(default)]
    pub season: Option<String>,
    #[serde(default)]
    pub bgm_url: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AniRssSearchResult {
    pub source: String,
    pub id: String,
    pub title: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub cover_url: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub bgm_url: Option<String>,
    pub locator: String,
    pub already_subscribed: bool,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AniRssGroupsRequest {
    pub source: String,
    pub locator: String,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AniRssGroup {
    pub name: String,
    pub rss_url: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub bgm_url: Option<String>,
    pub sample_titles: Vec<String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AniRssSubscriptionRequest {
    pub source: String,
    pub title: String,
    pub rss_url: String,
    #[serde(default)]
    pub bgm_url: Option<String>,
    #[serde(default)]
    pub subgroup: Option<String>,
    #[serde(default = "default_true")]
    pub enabled: bool,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AniRssSubscriptionPreview {
    pub title: String,
    pub rss_url: String,
    pub source: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub subgroup: Option<String>,
    pub enabled: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub download_path: Option<String>,
    pub sample_titles: Vec<String>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AniRssSubscriptionSummary {
    pub id: String,
    pub title: String,
    pub source: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub rss_url: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub subgroup: Option<String>,
    pub enabled: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub current_episode: Option<u32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub total_episodes: Option<u32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub last_download_at_epoch_ms: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub week_label: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AniRssDownloadJob {
    pub id: String,
    pub name: String,
    pub state: String,
    pub progress_percent: f64,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub completed_bytes: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub total_bytes: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub save_path: Option<String>,
}

pub fn approved_source_name(value: &str) -> Result<&'static str> {
    let normalized = value.trim().replace(['-', ' '], "_").to_ascii_uppercase();
    if normalized == "ANI_BT" {
        return Ok("ANIBT");
    }
    SUPPORTED_SOURCES
        .into_iter()
        .find(|source| *source == normalized)
        .ok_or_else(|| LibraryServerError::new("unsupported ANI-RSS source"))
}

fn normalize_search_results(source: &str, query: &str, value: &Value) -> Vec<AniRssSearchResult> {
    let mut candidates = Vec::new();
    match source {
        "MIKAN" => collect_nested(value, &["weeks", "items"], &mut candidates),
        "ANIBT" => collect_nested(value, &["byWeekday", "animes"], &mut candidates),
        "ANIME_GARDEN" => collect_nested(value, &["", "subjects"], &mut candidates),
        _ => {}
    }
    let mut seen = BTreeSet::new();
    candidates
        .into_iter()
        .filter_map(|item| normalize_search_result(source, item))
        .filter(|item| query.is_empty() || item.title.to_lowercase().contains(query))
        .filter(|item| seen.insert(format!("{}:{}", item.source, item.id)))
        .collect()
}

fn collect_nested<'a>(value: &'a Value, path: &[&str], output: &mut Vec<&'a Value>) {
    let mut level: Vec<&Value> = if path.first() == Some(&"") {
        value.as_array().into_iter().flatten().collect()
    } else {
        value
            .get(path[0])
            .and_then(Value::as_array)
            .into_iter()
            .flatten()
            .collect()
    };
    for key in path.iter().skip(1) {
        level = level
            .into_iter()
            .flat_map(|value| {
                value
                    .get(*key)
                    .and_then(Value::as_array)
                    .into_iter()
                    .flatten()
            })
            .collect();
    }
    output.extend(level);
}

fn normalize_search_result(source: &str, value: &Value) -> Option<AniRssSearchResult> {
    let (id, title, locator) = match source {
        "MIKAN" => (
            string(value, "bgmId")?,
            string(value, "title")?,
            string(value, "url")?,
        ),
        "ANIBT" => {
            let id = string(value, "bgmId").or_else(|| string(value, "animeId"))?;
            let title = value
                .get("title")
                .and_then(|title| string(title, "primary"))
                .or_else(|| string(value, "title"))?;
            (id.clone(), title, id)
        }
        "ANIME_GARDEN" => {
            let id = string(value, "id")?;
            (id.clone(), string(value, "name")?, id)
        }
        _ => return None,
    };
    Some(AniRssSearchResult {
        source: source.to_owned(),
        id,
        title,
        cover_url: string(value, "cover"),
        bgm_url: string(value, "bgmUrl"),
        locator,
        already_subscribed: value
            .get("exists")
            .and_then(Value::as_bool)
            .unwrap_or(false),
    })
}

fn collect_preview_titles(value: &Value) -> Vec<String> {
    let mut titles = Vec::new();
    fn visit(value: &Value, output: &mut Vec<String>) {
        if output.len() >= 8 {
            return;
        }
        match value {
            Value::Array(values) => values.iter().for_each(|value| visit(value, output)),
            Value::Object(object) => {
                if let Some(title) = object.get("title").and_then(Value::as_str) {
                    if !title.trim().is_empty() {
                        output.push(title.to_owned());
                    }
                }
                object.values().for_each(|value| visit(value, output));
            }
            _ => {}
        }
    }
    visit(value, &mut titles);
    titles.truncate(8);
    titles
}

fn string(value: &Value, key: &str) -> Option<String> {
    value
        .get(key)
        .and_then(Value::as_str)
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .map(ToOwned::to_owned)
}

fn normalize_source(value: Option<&str>) -> String {
    value
        .and_then(|value| approved_source_name(value).ok())
        .unwrap_or("CUSTOM_RSS")
        .to_owned()
}

fn source_api_name(source: &str) -> &'static str {
    match source {
        "MIKAN" => "mikan",
        "ANIBT" => "ani-bt",
        "ANIME_GARDEN" => "anime-garden",
        _ => "other",
    }
}

fn percent_encode(value: &str) -> String {
    let mut encoded = String::new();
    for byte in value.bytes() {
        match byte {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                encoded.push(char::from(byte));
            }
            _ => encoded.push_str(&format!("%{byte:02X}")),
        }
    }
    encoded
}

fn default_true() -> bool {
    true
}

#[cfg(test)]
mod tests {
    use std::io::{Read, Write};
    use std::net::TcpListener;

    use super::*;

    #[test]
    fn source_names_are_strictly_normalized() {
        assert_eq!(
            "ANIME_GARDEN",
            approved_source_name("anime-garden").unwrap()
        );
        assert_eq!("ANIBT", approved_source_name("ani-bt").unwrap());
        assert!(approved_source_name("unknown").is_err());
    }

    #[test]
    fn mikan_results_are_normalized_without_leaking_provider_objects() {
        let value = json!({"weeks":[{"items":[{
            "bgmId":"42","title":"Example","url":"https://mikan.example/show",
            "cover":"https://image.example/cover.jpg","exists":false
        }]}]});
        let results = normalize_search_results("MIKAN", "example", &value);
        assert_eq!(1, results.len());
        assert_eq!("42", results[0].id);
        assert_eq!("Example", results[0].title);
    }

    #[test]
    fn percent_encoding_does_not_allow_query_injection() {
        assert_eq!("a%26b%3Dc", percent_encode("a&b=c"));
    }

    #[test]
    fn maps_remote_download_paths_using_the_longest_prefix() {
        let service = AniRssService {
            base_url: "http://127.0.0.1:7789".to_owned(),
            api_key: "secret".to_owned(),
            path_mappings: vec![
                AniRssPathMapping {
                    remote_prefix: "/media".to_owned(),
                    local_prefix: "D:\\Anime".to_owned(),
                },
                AniRssPathMapping {
                    remote_prefix: "/media/seasonal".to_owned(),
                    local_prefix: "E:\\Seasonal".to_owned(),
                },
            ],
        };
        assert_eq!(
            "E:\\Seasonal/Frieren",
            service.map_path("/media/seasonal/Frieren")
        );
        assert_eq!("/downloads/Frieren", service.map_path("/downloads/Frieren"));
    }

    #[test]
    fn client_posts_with_api_key_and_unwraps_the_ani_rss_envelope() {
        let listener = TcpListener::bind(("127.0.0.1", 0)).expect("mock ANI-RSS binds");
        let port = listener.local_addr().expect("mock address").port();
        let server = std::thread::spawn(move || {
            let (mut stream, _) = listener.accept().expect("mock request");
            let mut request = [0_u8; 4096];
            let read = stream.read(&mut request).expect("request reads");
            let request = String::from_utf8_lossy(&request[..read]);
            assert!(request.starts_with("POST /api/about HTTP/1.1"));
            assert!(request.to_ascii_lowercase().contains("x-api-key: test-key"));
            let body = r#"{"code":200,"message":"ok","data":{"version":"v3.2.18"}}"#;
            write!(
                stream,
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                body.len(),
                body
            )
            .expect("response writes");
        });
        let service = AniRssService {
            base_url: format!("http://127.0.0.1:{port}"),
            api_key: "test-key".to_owned(),
            path_mappings: Vec::new(),
        };
        let status = service.status(HeadlessAniRssMode::External);
        assert!(status.reachable, "{}", status.message);
        assert_eq!(Some("v3.2.18"), status.version.as_deref());
        server.join().expect("mock server joins");
    }
}
