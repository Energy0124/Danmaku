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
    DandanplayCommentCacheStore, DandanplayConnection, DandanplayDanmakuClient, DandanplayResolver,
};
use crate::external_provider::{
    BangumiSearchClient, BangumiTrackingClient, ExternalAnimeInfo, ExternalAnimeSearchClient,
    ExternalAnimeTitleSet, ExternalProviderService, MyAnimeListSearchClient,
    MyAnimeListTrackingClient,
};
use crate::settings::HeadlessDandanplayAuthenticationMode;

static TEMP_COUNTER: AtomicU64 = AtomicU64::new(0);

mod cases;
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
        DandanplayCommentCacheStore::new(fixture.temp.join(format!("dandanplay-cache-{id}.json"))),
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
        .join("tests")
        .join("fixtures")
        .join("lan-protocol")
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
fn fixture_status_reports_the_only_supported_host_mode() {
    let status = LanLibraryServerStatus {
        web_ui_available: true,
        web_ui_path: Some("/web".to_owned()),
        ..LanLibraryServerStatus::default()
    };
    assert_eq!(
        json!({
            "webUiAvailable": true,
            "webUiPath": "/web",
            "hostMode": "headless-server"
        }),
        serde_json::to_value(status).expect("status")
    );
}
