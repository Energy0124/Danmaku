use std::io::Write;
use std::net::{TcpListener, TcpStream};
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

fn handle_test_connection(mut stream: TcpStream, requests: Arc<StdMutex<Vec<CapturedRequest>>>) {
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
