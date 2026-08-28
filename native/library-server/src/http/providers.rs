use super::*;

pub(super) async fn handle_provider_settings(
    state: &HttpServerState,
    method: Method,
    path: &str,
    body: Body,
) -> Response<Body> {
    if path != "/api/providers/settings" {
        return empty_status(StatusCode::NOT_FOUND);
    }
    let Some(admin) = &state.provider_admin else {
        return empty_status(StatusCode::NOT_FOUND);
    };
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

pub(super) async fn handle_provider_accounts(
    state: &HttpServerState,
    method: Method,
    path: &str,
    body: Body,
) -> Response<Body> {
    let Some(admin) = &state.provider_admin else {
        return empty_status(StatusCode::NOT_FOUND);
    };
    match (method, path) {
        (Method::GET, "/api/providers/accounts") => {
            json_response(StatusCode::OK, &admin.accounts())
        }
        (Method::POST, "/api/providers/accounts/myanimelist/oauth/start") => {
            match admin.start_my_anime_list_oauth() {
                Ok(response) => json_response(StatusCode::OK, &response),
                Err(error) => text_response(StatusCode::CONFLICT, &error.to_string()),
            }
        }
        (Method::POST, "/api/providers/accounts/myanimelist/oauth/complete") => {
            let Ok(bytes) = axum::body::to_bytes(body, 65_536).await else {
                return text_response(StatusCode::BAD_REQUEST, "OAuth request body is too large.");
            };
            let Ok(request) = serde_json::from_slice::<MyAnimeListOAuthCompleteRequest>(&bytes)
            else {
                return text_response(
                    StatusCode::BAD_REQUEST,
                    "Request body must contain flowId, state, and code.",
                );
            };
            let admin = Arc::clone(admin);
            match tokio::task::spawn_blocking(move || admin.complete_my_anime_list_oauth(request))
                .await
            {
                Ok(Ok(response)) => json_response(StatusCode::OK, &response),
                Ok(Err(error)) => text_response(StatusCode::BAD_REQUEST, &error.to_string()),
                Err(error) => text_response(
                    StatusCode::INTERNAL_SERVER_ERROR,
                    &format!("OAuth account task failed: {error}"),
                ),
            }
        }
        (Method::DELETE, "/api/providers/accounts/myanimelist") => {
            match admin.disconnect_account("myanimelist") {
                Ok(response) => json_response(StatusCode::OK, &response),
                Err(error) => text_response(StatusCode::BAD_REQUEST, &error.to_string()),
            }
        }
        (Method::PUT, "/api/providers/accounts/bangumi") => {
            let Ok(bytes) = axum::body::to_bytes(body, 65_536).await else {
                return text_response(
                    StatusCode::BAD_REQUEST,
                    "Bangumi account request body is too large.",
                );
            };
            let Ok(request) = serde_json::from_slice::<BangumiAccountRequest>(&bytes) else {
                return text_response(
                    StatusCode::BAD_REQUEST,
                    "Request body must contain accessToken.",
                );
            };
            let admin = Arc::clone(admin);
            match tokio::task::spawn_blocking(move || admin.connect_bangumi(request.access_token))
                .await
            {
                Ok(Ok(response)) => json_response(StatusCode::OK, &response),
                Ok(Err(error)) => text_response(StatusCode::BAD_REQUEST, &error.to_string()),
                Err(error) => text_response(
                    StatusCode::INTERNAL_SERVER_ERROR,
                    &format!("Bangumi account task failed: {error}"),
                ),
            }
        }
        (Method::DELETE, "/api/providers/accounts/bangumi") => {
            match admin.disconnect_account("bangumi") {
                Ok(response) => json_response(StatusCode::OK, &response),
                Err(error) => text_response(StatusCode::BAD_REQUEST, &error.to_string()),
            }
        }
        _ => empty_status(StatusCode::NOT_FOUND),
    }
}

pub(super) fn handle_provider_runtime(
    state: &HttpServerState,
    method: &Method,
    path: &str,
) -> Response<Body> {
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

pub(super) async fn handle_provider_search(
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

pub(super) async fn refresh_my_anime_list_without_blocking(
    admin: Arc<ProviderAdminState>,
) -> crate::Result<()> {
    tokio::task::spawn_blocking(move || admin.refresh_my_anime_list_if_needed())
        .await
        .map_err(|error| {
            crate::LibraryServerError::with_context(
                error,
                "MyAnimeList refresh task could not complete",
            )
        })?
}

pub(super) async fn handle_provider_tracking(
    state: &HttpServerState,
    method: Method,
    path: &str,
    body: Body,
) -> Response<Body> {
    let Some(admin) = &state.provider_admin else {
        return empty_status(StatusCode::NOT_FOUND);
    };

    let library = state.library();
    let progress = state.progress_store.load_all_progress();
    let document = || {
        tracking_document(
            &library.catalog,
            &progress,
            &admin.tracking_store().snapshot(),
        )
    };
    match (method, path) {
        (Method::GET, "/api/providers/tracking") => json_response(StatusCode::OK, &document()),
        (Method::PUT, "/api/providers/tracking/mapping") => {
            let Ok(bytes) = axum::body::to_bytes(body, 65_536).await else {
                return text_response(
                    StatusCode::BAD_REQUEST,
                    "Tracking mapping body is too large.",
                );
            };
            let Ok(request) = serde_json::from_slice::<ExternalTrackingMappingRequest>(&bytes)
            else {
                return text_response(
                    StatusCode::BAD_REQUEST,
                    "Request body must contain localSeriesId and animeId.",
                );
            };
            let current_document = document();
            let Some(series) = current_document
                .series
                .iter()
                .find(|series| series.local_series_ids.contains(&request.local_series_id))
            else {
                return text_response(
                    StatusCode::BAD_REQUEST,
                    "The selected local series is not in the current library.",
                );
            };
            let mapped_at_epoch_ms = current_epoch_ms();
            let mappings = series
                .local_series_ids
                .iter()
                .map(|local_series_id| ExternalAnimeMapping {
                    local_series_id: local_series_id.clone(),
                    anime_id: request.anime_id.clone(),
                    source: ExternalAnimeMappingSource::Manual,
                    confidence: 1.0,
                    mapped_at_epoch_ms,
                })
                .collect();
            match admin.tracking_store().save_mappings(mappings) {
                Ok(()) => json_response(StatusCode::OK, &document()),
                Err(error) => text_response(StatusCode::BAD_REQUEST, &error.to_string()),
            }
        }
        (Method::DELETE, "/api/providers/tracking/mapping") => {
            let Ok(bytes) = axum::body::to_bytes(body, 65_536).await else {
                return text_response(
                    StatusCode::BAD_REQUEST,
                    "Tracking mapping body is too large.",
                );
            };
            let Ok(request) = serde_json::from_slice::<ExternalTrackingMappingRequest>(&bytes)
            else {
                return text_response(
                    StatusCode::BAD_REQUEST,
                    "Request body must contain localSeriesId and animeId.",
                );
            };
            let local_series_ids = document()
                .series
                .iter()
                .find(|series| series.local_series_ids.contains(&request.local_series_id))
                .map(|series| series.local_series_ids.clone())
                .unwrap_or_else(|| vec![request.local_series_id.clone()]);
            match admin
                .tracking_store()
                .delete_mappings(&local_series_ids, &request.anime_id)
            {
                Ok(true) => json_response(StatusCode::OK, &document()),
                Ok(false) => {
                    text_response(StatusCode::NOT_FOUND, "Tracking mapping was not found.")
                }
                Err(error) => text_response(StatusCode::BAD_REQUEST, &error.to_string()),
            }
        }
        (Method::POST, "/api/providers/tracking/conflicts/import") => {
            let Ok(bytes) = axum::body::to_bytes(body, 65_536).await else {
                return text_response(
                    StatusCode::BAD_REQUEST,
                    "Tracking conflict import body is too large.",
                );
            };
            let Ok(request) =
                serde_json::from_slice::<ExternalTrackingConflictImportRequest>(&bytes)
            else {
                return text_response(
                    StatusCode::BAD_REQUEST,
                    "Request body must identify the reviewed tracking conflict.",
                );
            };
            let imports = match provider_progress_import(
                &library.catalog,
                &progress,
                &admin.tracking_store().snapshot(),
                &request.local_series_id,
                &request.anime_id,
                request.expected_external_watched_episodes,
            ) {
                Ok(imports) => imports,
                Err(error) => return text_response(StatusCode::CONFLICT, &error.to_string()),
            };
            for imported in &imports {
                if let Err(error) = state.progress_store.save_progress(imported.clone()) {
                    return text_response(StatusCode::INTERNAL_SERVER_ERROR, &error.to_string());
                }
            }
            let updated_progress = state.progress_store.load_all_progress();
            json_response(
                StatusCode::OK,
                &serde_json::json!({
                    "importedCount": imports.len(),
                    "document": tracking_document(
                        &library.catalog,
                        &updated_progress,
                        &admin.tracking_store().snapshot(),
                    ),
                }),
            )
        }
        (Method::POST, "/api/providers/tracking/readback") => {
            if let Err(error) = refresh_my_anime_list_without_blocking(Arc::clone(admin)).await {
                return text_response(StatusCode::BAD_GATEWAY, &error.to_string());
            }
            let service = state
                .external_provider_service()
                .expect("provider admin always has an external provider service");
            match refresh_tracking_readback(
                &service,
                admin.tracking_store(),
                &library.catalog,
                &progress,
            )
            .await
            {
                Ok(response) => json_response(StatusCode::OK, &response),
                Err(error) => text_response(StatusCode::INTERNAL_SERVER_ERROR, &error.to_string()),
            }
        }
        (Method::POST, "/api/providers/tracking/sync") => {
            let Ok(bytes) = axum::body::to_bytes(body, 262_144).await else {
                return text_response(StatusCode::BAD_REQUEST, "Tracking sync body is too large.");
            };
            let Ok(request) = serde_json::from_slice::<ExternalTrackingSyncRequest>(&bytes) else {
                return text_response(
                    StatusCode::BAD_REQUEST,
                    "Request body must contain the previewed expectedUpdates.",
                );
            };
            if let Err(error) = refresh_my_anime_list_without_blocking(Arc::clone(admin)).await {
                return text_response(StatusCode::BAD_GATEWAY, &error.to_string());
            }
            let service = state
                .external_provider_service()
                .expect("provider admin always has an external provider service");
            match execute_tracking_sync(
                &service,
                admin.tracking_store(),
                &library.catalog,
                &progress,
                &request.expected_updates,
            )
            .await
            {
                Ok(response) => json_response(StatusCode::OK, &response),
                Err(error) => {
                    let message = error.to_string();
                    let status = if message.starts_with("tracking preview changed;") {
                        StatusCode::CONFLICT
                    } else {
                        StatusCode::INTERNAL_SERVER_ERROR
                    };
                    text_response(status, &message)
                }
            }
        }
        _ => empty_status(StatusCode::NOT_FOUND),
    }
}
pub(super) async fn handle_dandanplay_resolve(
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
    let library = state.library();
    let Some(path) = library.files_by_id.get(&media_id) else {
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
            clear_attention_failure(state, &media_id);
            record_recognized_identity(state, &media_id, &result);
            json_response(StatusCode::OK, &result.to_provider_response(&media_id))
        }
        Err(error) => {
            record_attention_failure(state, &media_id);
            text_response(
                StatusCode::BAD_GATEWAY,
                &format!("dandanplay request failed: {error}"),
            )
        }
    }
}

/// Searches the dandanplay database by anime keyword for the manual match
/// picker, returning each anime with its full episode list.
pub(super) async fn handle_dandanplay_search(
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
pub(super) async fn handle_dandanplay_bangumi(
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
