use super::*;

#[tokio::test]
async fn organizer_requires_desktop_mode_and_loopback_peer() {
    let fixture = FixtureEnvironment::new();
    let settings = HeadlessServerSettings {
        library_roots: vec![fixture.temp.clone()],
        dandanplay: crate::settings::HeadlessDandanplayProviderSettings::default(),
        external_anime: crate::settings::HeadlessExternalAnimeProviderSettings::default(),
    };
    let admin = Arc::new(
        ProviderAdminState::new(fixture.temp.clone(), settings.clone(), settings, None)
            .expect("organizer admin creates"),
    );
    let mut library = fixture.library.clone();
    library.catalog.items[0].root_label = Some(fixture.temp.display().to_string());
    library.catalog.items[0].series_title = "First Release Name".to_owned();
    let second_file = fixture.temp.join("second-release.bin");
    fs::write(&second_file, b"second").expect("second release writes");
    let mut second_item = library.catalog.items[0].clone();
    second_item.id = "episode-id-2".to_owned();
    second_item.series_title = "Completely Different Release".to_owned();
    second_item.episode_title = "Episode 02".to_owned();
    second_item.relative_path = "Completely Different Release/Episode 02.bin".to_owned();
    second_item.stream_path = "/media/episode-id-2".to_owned();
    second_item.size_bytes = second_file.metadata().expect("second metadata").len();
    second_item.subtitles.clear();
    second_item.poster_path = None;
    library.catalog.items.push(second_item);
    library
        .files_by_id
        .insert("episode-id-2".to_owned(), second_file);
    let catalog_metadata = Arc::new(CatalogMetadataStore::new(
        fixture.temp.join("organizer-metadata.json"),
    ));
    for media_id in ["episode-id", "episode-id-2"] {
        catalog_metadata
            .record(media_id, 42, "Unified Anime".to_owned(), None)
            .expect("organizer identity records");
    }
    let mut config = HttpServerConfig::fixture(fixture.web_root.clone());
    config.provider_admin = Some(admin);
    config.catalog_metadata = Some(catalog_metadata);
    let store = CatalogStore::new(fixture.temp.join("organizer-catalog.json"));
    let state = HttpServerState::new(
        library,
        Arc::new(PlaybackProgressStore::new(
            fixture.temp.join("organizer-progress.json"),
        )),
        config,
    )
    .with_library_scan(vec![fixture.temp.clone()], store);
    assert!(state.try_start_organization());
    assert!(!state.try_start_scan());
    state.finish_organization();
    let router = app(state);
    let body = json!({
        "root": fixture.temp.display().to_string(),
        "baseRelativePath": "Anime",
        "overrides": []
    })
    .to_string();
    let request = |peer: [u8; 4]| {
        Request::builder()
            .method(Method::POST)
            .uri("/api/library/organize/preview")
            .header(CONTENT_TYPE, "application/json")
            .extension(ConnectInfo(std::net::SocketAddr::from((peer, 5000))))
            .body(Body::from(body.clone()))
            .expect("request builds")
    };
    let remote = router
        .clone()
        .oneshot(request([192, 168, 2, 20]))
        .await
        .expect("remote response");
    assert_eq!(StatusCode::FORBIDDEN, remote.status());
    let local = router
        .oneshot(request([127, 0, 0, 1]))
        .await
        .expect("local response");
    assert_eq!(StatusCode::OK, local.status());
    let local_body = to_bytes(local.into_body(), 1_048_576)
        .await
        .expect("organizer preview body");
    let plan: Value = serde_json::from_slice(&local_body).expect("organizer preview json");
    let batches = plan["batches"].as_array().expect("organizer batches");
    assert_eq!(1, batches.len());
    assert_eq!(Some("PROVIDER"), batches[0]["confidence"].as_str());
    assert_eq!(Some("Unified Anime"), batches[0]["seriesTitle"].as_str());
}

#[tokio::test]
async fn lan_protocol_http_fixtures_match_contract() {
    let fixture = FixtureEnvironment::new();
    let progress_store = Arc::new(PlaybackProgressStore::new(
        fixture.temp.join("progress.json"),
    ));
    let state = HttpServerState::new(
        fixture.library.clone(),
        progress_store,
        HttpServerConfig::fixture(fixture.web_root.clone()),
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
    assert_eq!(18, passed);
}

#[tokio::test]
async fn library_attention_route_is_not_swallowed_by_catalog_prefix() {
    let fixture = FixtureEnvironment::new();
    let progress_store = Arc::new(PlaybackProgressStore::new(
        fixture.temp.join("attention-progress.json"),
    ));
    let state = HttpServerState::new(
        fixture.library.clone(),
        progress_store,
        HttpServerConfig::fixture(fixture.web_root.clone()),
    );
    let document = request_json(&app(state), "/api/library/attention").await;

    assert_eq!(false, document["provider"]["available"]);
    assert_eq!("DANDANPLAY_UNAVAILABLE", document["provider"]["reasonCode"]);
    assert_eq!(1, document["summary"]["total"]);
    assert_eq!("episode-id", document["items"][0]["mediaId"]);
    assert_eq!("MISSING", document["items"][0]["cacheStatus"]);
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
    let cached = request_json(&app, "/api/providers/dandanplay/resolve?mediaId=episode-id").await;
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
    // native player stops its managed sidecar, so a recognition's
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
async fn tracking_admin_persists_mapping_and_syncs_previewed_update() {
    #[derive(Debug)]
    struct RecordingTrackingClient {
        writes: Arc<Mutex<Vec<ExternalAnimeTrackingUpdate>>>,
    }

    impl crate::external_provider::ExternalAnimeTrackingClient for RecordingTrackingClient {
        fn provider(&self) -> crate::catalog::ExternalAnimeProvider {
            crate::catalog::ExternalAnimeProvider::Bangumi
        }

        fn fetch_list_entry(
            &self,
            anime_id: ExternalAnimeId,
        ) -> std::result::Result<
            Option<ExternalAnimeListEntry>,
            crate::external_provider::ExternalProviderError,
        > {
            Ok(Some(ExternalAnimeListEntry {
                anime_id,
                status: Some(crate::external_provider::ExternalAnimeListStatus::PlanToWatch),
                watched_episodes: Some(0),
                score: None,
                updated_at_epoch_ms: Some(10),
            }))
        }

        fn update_list_entry(
            &self,
            update: ExternalAnimeTrackingUpdate,
        ) -> std::result::Result<
            ExternalAnimeListEntry,
            crate::external_provider::ExternalProviderError,
        > {
            self.writes
                .lock()
                .expect("writes lock")
                .push(update.clone());
            Ok(ExternalAnimeListEntry {
                anime_id: update.anime_id,
                status: update.status,
                watched_episodes: update.watched_episodes,
                score: update.score,
                updated_at_epoch_ms: Some(20),
            })
        }
    }

    let fixture = FixtureEnvironment::new();
    let settings = HeadlessServerSettings {
        library_roots: Vec::new(),
        dandanplay: crate::settings::HeadlessDandanplayProviderSettings::default(),
        external_anime: crate::settings::HeadlessExternalAnimeProviderSettings::default(),
    };
    let admin = Arc::new(
        ProviderAdminState::new(
            fixture.temp.clone(),
            settings.clone(),
            settings.clone(),
            None,
        )
        .expect("tracking admin creates"),
    );
    let writes = Arc::new(Mutex::new(Vec::new()));
    admin
        .runtime
        .write()
        .expect("runtime lock")
        .external_provider_service = Arc::new(ExternalProviderService::new_for_tests(
        Vec::new(),
        vec![Arc::new(RecordingTrackingClient {
            writes: Arc::clone(&writes),
        })],
    ));
    let progress_store = Arc::new(PlaybackProgressStore::new(
        fixture.temp.join("progress-tracking-admin.json"),
    ));
    progress_store
        .save_progress(PlaybackProgress {
            media_id: "episode-id".to_owned(),
            position_ms: 100_000,
            duration_ms: Some(100_000),
            updated_at_epoch_ms: 100,
        })
        .expect("progress saves");
    let app = app(HttpServerState::new(
        fixture.library.clone(),
        progress_store,
        HttpServerConfig::headless(None, &settings, None, None, None, admin),
    ));

    let initial = app
        .clone()
        .oneshot(get("/api/providers/tracking"))
        .await
        .expect("tracking response");
    assert_eq!(StatusCode::OK, initial.status());
    let initial: Value =
        serde_json::from_str(&body_text(initial).await).expect("tracking document");
    let series_id = initial["series"][0]["id"]
        .as_str()
        .expect("series ID")
        .to_owned();
    assert_eq!(
        1,
        initial["series"][0]["localSeriesIds"]
            .as_array()
            .expect("logical series members")
            .len()
    );

    let mapping = json!({
        "localSeriesId": series_id,
        "animeId": { "provider": "BANGUMI", "value": 400602 }
    });
    let mapped = app
        .clone()
        .oneshot(
            Request::builder()
                .method("PUT")
                .uri("/api/providers/tracking/mapping")
                .header("content-type", "application/json")
                .body(Body::from(mapping.to_string()))
                .expect("mapping request"),
        )
        .await
        .expect("mapping response");
    assert_eq!(StatusCode::OK, mapped.status());
    let mapped: Value = serde_json::from_str(&body_text(mapped).await).expect("mapped document");
    assert_eq!(1, mapped["plan"]["summary"]["updateCount"]);
    assert_eq!(1, mapped["plan"]["updates"][0]["update"]["watchedEpisodes"]);
    assert_eq!(
        "COMPLETED",
        mapped["plan"]["updates"][0]["update"]["status"]
    );

    let readback = app
        .clone()
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/api/providers/tracking/readback")
                .body(Body::empty())
                .expect("readback request"),
        )
        .await
        .expect("readback response");
    assert_eq!(StatusCode::OK, readback.status());
    let readback: Value =
        serde_json::from_str(&body_text(readback).await).expect("readback document");
    assert_eq!(1, readback["successCount"]);
    assert_eq!(1, readback["document"]["plan"]["summary"]["updateCount"]);
    let expected_updates = readback["document"]["plan"]["updates"]
        .as_array()
        .expect("preview updates")
        .iter()
        .map(|candidate| candidate["update"].clone())
        .collect::<Vec<_>>();
    let stale = app
        .clone()
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/api/providers/tracking/sync")
                .header("content-type", "application/json")
                .body(Body::from(json!({ "expectedUpdates": [] }).to_string()))
                .expect("stale sync request"),
        )
        .await
        .expect("stale sync response");
    assert_eq!(StatusCode::CONFLICT, stale.status());
    assert!(writes.lock().expect("writes lock").is_empty());

    let synced = app
        .clone()
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/api/providers/tracking/sync")
                .header("content-type", "application/json")
                .body(Body::from(
                    json!({ "expectedUpdates": expected_updates }).to_string(),
                ))
                .expect("sync request"),
        )
        .await
        .expect("sync response");
    assert_eq!(StatusCode::OK, synced.status());
    let synced: Value = serde_json::from_str(&body_text(synced).await).expect("sync document");
    assert_eq!(1, synced["successCount"]);
    assert_eq!(0, synced["errors"].as_array().expect("sync errors").len());
    let writes = writes.lock().expect("writes lock");
    assert_eq!(0, synced["document"]["plan"]["summary"]["updateCount"]);
    assert_eq!(1, writes.len());
    assert_eq!(Some(1), writes[0].watched_episodes);
    assert_eq!(
        Some(crate::external_provider::ExternalAnimeListStatus::Completed),
        writes[0].status
    );
    let persisted =
        fs::read_to_string(fixture.temp.join("external-tracking.json")).expect("tracking state");
    assert!(persisted.contains("400602"));
}

#[tokio::test]
async fn provider_accounts_start_mal_oauth_without_network_access() {
    #[derive(Debug)]
    struct PassthroughSecretProtector;

    impl crate::provider_secrets::SecretProtector for PassthroughSecretProtector {
        fn protect(&self, plaintext: &[u8]) -> crate::Result<Vec<u8>> {
            Ok(plaintext.to_vec())
        }

        fn unprotect(&self, ciphertext: &[u8]) -> crate::Result<Vec<u8>> {
            Ok(ciphertext.to_vec())
        }
    }

    let fixture = FixtureEnvironment::new();
    let mut settings = HeadlessServerSettings {
        library_roots: Vec::new(),
        dandanplay: crate::settings::HeadlessDandanplayProviderSettings::default(),
        external_anime: crate::settings::HeadlessExternalAnimeProviderSettings::default(),
    };
    settings.external_anime.my_anime_list_client_id = Some("mal-client".to_owned());
    let secret_store = ProviderSecretStore::with_protector(
        fixture.temp.join("provider-account-secrets.json"),
        Arc::new(PassthroughSecretProtector),
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
            fixture.temp.join("progress-provider-accounts.json"),
        )),
        HttpServerConfig::headless(None, &settings, None, None, None, admin),
    );
    let app = app(state);

    let accounts = app
        .clone()
        .oneshot(get("/api/providers/accounts"))
        .await
        .expect("accounts response");
    assert_eq!(StatusCode::OK, accounts.status());
    let accounts: Value =
        serde_json::from_str(&body_text(accounts).await).expect("accounts document");
    assert_eq!("DISCONNECTED", accounts["myAnimeList"]["state"]);
    assert_eq!("DISCONNECTED", accounts["bangumi"]["state"]);

    let oauth = app
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/api/providers/accounts/myanimelist/oauth/start")
                .body(Body::empty())
                .expect("OAuth start request"),
        )
        .await
        .expect("OAuth start response");
    assert_eq!(StatusCode::OK, oauth.status());
    let oauth: Value = serde_json::from_str(&body_text(oauth).await).expect("OAuth start document");
    assert_eq!(MAL_OAUTH_CALLBACK_URL, oauth["callbackUrl"]);
    assert!(
        oauth["flowId"]
            .as_str()
            .is_some_and(|value| !value.is_empty())
    );
    assert!(oauth["authorizationUrl"].as_str().is_some_and(|value| {
        value.starts_with("https://myanimelist.net/v1/oauth2/authorize?")
    }));
}

#[test]
fn rejected_mal_refresh_requires_reconnect_but_transient_failure_stays_connected() {
    #[derive(Debug)]
    struct PassthroughSecretProtector;

    impl crate::provider_secrets::SecretProtector for PassthroughSecretProtector {
        fn protect(&self, plaintext: &[u8]) -> crate::Result<Vec<u8>> {
            Ok(plaintext.to_vec())
        }

        fn unprotect(&self, ciphertext: &[u8]) -> crate::Result<Vec<u8>> {
            Ok(ciphertext.to_vec())
        }
    }

    let fixture = FixtureEnvironment::new();
    let mut settings = HeadlessServerSettings {
        library_roots: Vec::new(),
        dandanplay: crate::settings::HeadlessDandanplayProviderSettings::default(),
        external_anime: crate::settings::HeadlessExternalAnimeProviderSettings::default(),
    };
    let external = &mut settings.external_anime;
    external.my_anime_list_client_id = Some("mal-client".to_owned());
    external.my_anime_list_access_token = Some("access".to_owned());
    external.has_my_anime_list_access_token = true;
    external.my_anime_list_refresh_token = Some("refresh".to_owned());
    external.has_my_anime_list_refresh_token = true;
    external.my_anime_list_token_expires_at_epoch_ms = Some(0);
    external.my_anime_list_user_id = Some("42".to_owned());
    external.my_anime_list_user_name = Some("qa-user".to_owned());
    let admin = ProviderAdminState::new_for_tests(
        fixture.temp.clone(),
        settings.clone(),
        settings,
        ProviderSecretStore::with_protector(
            fixture.temp.join("provider-refresh-secrets.json"),
            Arc::new(PassthroughSecretProtector),
        ),
    );

    let transient = admin.handle_my_anime_list_refresh_error(MyAnimeListTokenError::Other(
        crate::LibraryServerError::new("temporary failure"),
    ));
    assert_eq!(
        "temporary failure",
        transient.expect_err("transient error").to_string()
    );
    assert_eq!("CONNECTED", admin.accounts().my_anime_list.state);

    let rejected = admin.handle_my_anime_list_refresh_error(MyAnimeListTokenError::InvalidGrant);
    assert_eq!(
        "MyAnimeList authorization expired; reconnect the account",
        rejected.expect_err("reconnect error").to_string()
    );
    let account = admin.accounts().my_anime_list;
    assert_eq!("NEEDS_RECONNECT", account.state);
    assert_eq!(Some("AUTHORIZATION_EXPIRED"), account.reason_code);
    let persisted = admin.persisted_settings.lock().expect("settings lock");
    assert!(
        persisted
            .external_anime
            .my_anime_list_access_token
            .is_none()
    );
    assert!(
        persisted
            .external_anime
            .my_anime_list_refresh_token
            .is_none()
    );
    assert_eq!(
        Some("42"),
        persisted.external_anime.my_anime_list_user_id.as_deref()
    );
}

#[tokio::test]
async fn provider_settings_redact_secrets_and_reload_runtime() {
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
async fn tracking_clients_treat_provider_not_found_as_a_missing_entry() {
    let provider_server = MockExternalProviderServer::start(MockExternalProviderBehavior {
        mal_list_read_status: 404,
        bangumi_list_read_status: 404,
        ..MockExternalProviderBehavior::default()
    });
    let service = ExternalProviderService::new_for_tests(
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
    );

    for anime_id in [
        ExternalAnimeId {
            provider: crate::catalog::ExternalAnimeProvider::MyAnimeList,
            value: 52_991,
        },
        ExternalAnimeId {
            provider: crate::catalog::ExternalAnimeProvider::Bangumi,
            value: 400_602,
        },
    ] {
        assert_eq!(
            None,
            service
                .fetch_list_entry(anime_id)
                .await
                .expect("missing entry")
        );
    }
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
    assert_eq!(StatusCode::NOT_FOUND, malformed_post.status());

    let removed_get = app
        .oneshot(get("/api/providers/list/entry?provider=mal&animeId=52991"))
        .await
        .expect("removed route response");
    assert_eq!(StatusCode::NOT_FOUND, removed_get.status());
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
        HttpServerConfig::fixture(fixture.web_root.clone()),
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
