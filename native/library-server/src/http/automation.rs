use super::provider_admin::{AniRssEnabledRequest, AniRssSettingsUpdate};
use super::*;
use crate::ani_rss::{
    AniRssGroupsRequest, AniRssSearchRequest, AniRssService, AniRssStatus,
    AniRssSubscriptionRequest,
};

pub(super) async fn handle_ani_rss(
    state: &HttpServerState,
    method: Method,
    path: &str,
    body: Body,
) -> Response<Body> {
    let Some(admin) = &state.provider_admin else {
        return empty_status(StatusCode::NOT_FOUND);
    };

    if path == "/api/automation/ani-rss/settings" {
        return match method {
            Method::GET => match admin.ani_rss_settings() {
                Ok(settings) => json_response(StatusCode::OK, &settings),
                Err(error) => text_response(StatusCode::INTERNAL_SERVER_ERROR, &error.to_string()),
            },
            Method::PUT => {
                let Ok(bytes) = to_bytes(body, 65_536).await else {
                    return text_response(
                        StatusCode::BAD_REQUEST,
                        "ANI-RSS settings body is too large.",
                    );
                };
                let Ok(update) = serde_json::from_slice::<AniRssSettingsUpdate>(&bytes) else {
                    return text_response(
                        StatusCode::BAD_REQUEST,
                        "Request body must be ANI-RSS settings JSON.",
                    );
                };
                let admin = Arc::clone(admin);
                match tokio::task::spawn_blocking(move || admin.update_ani_rss(update)).await {
                    Ok(Ok(settings)) => json_response(StatusCode::OK, &settings),
                    Ok(Err(error)) => text_response(StatusCode::BAD_REQUEST, &error.to_string()),
                    Err(error) => text_response(
                        StatusCode::INTERNAL_SERVER_ERROR,
                        &format!("ANI-RSS settings task failed: {error}"),
                    ),
                }
            }
            _ => empty_status(StatusCode::METHOD_NOT_ALLOWED),
        };
    }

    if let Some(source) = path
        .strip_prefix("/api/automation/ani-rss/sources/")
        .and_then(|value| value.strip_suffix("/approval"))
        .and_then(url_decode)
    {
        let approved = match method {
            Method::POST => true,
            Method::DELETE => false,
            _ => return empty_status(StatusCode::METHOD_NOT_ALLOWED),
        };
        return match admin.set_ani_rss_source_approval(&source, approved) {
            Ok(settings) => json_response(StatusCode::OK, &settings),
            Err(error) => text_response(StatusCode::BAD_REQUEST, &error.to_string()),
        };
    }

    if path == "/api/automation/ani-rss/status" && method == Method::GET {
        let settings = match admin.ani_rss_settings() {
            Ok(settings) => settings,
            Err(error) => {
                return text_response(StatusCode::INTERNAL_SERVER_ERROR, &error.to_string());
            }
        };
        let service = admin.ani_rss_service();
        let status = match service {
            Ok((service, mode)) => tokio::task::spawn_blocking(move || service.status(mode))
                .await
                .unwrap_or_else(|error| AniRssStatus {
                    configured: true,
                    reachable: false,
                    mode: settings.mode,
                    version: None,
                    message: format!("ANI-RSS status task failed: {error}"),
                }),
            Err(error) => AniRssStatus {
                configured: false,
                reachable: false,
                mode: settings.mode,
                version: None,
                message: error.to_string(),
            },
        };
        return json_response(StatusCode::OK, &status);
    }

    if path == "/api/automation/ani-rss/subscriptions" && method == Method::GET {
        return run_ani_rss_read(admin, |service| service.subscriptions()).await;
    }
    if path == "/api/automation/ani-rss/downloads" && method == Method::GET {
        return run_ani_rss_read(admin, |service| service.downloads()).await;
    }

    if path == "/api/automation/ani-rss/search" && method == Method::POST {
        let Ok(bytes) = to_bytes(body, 65_536).await else {
            return text_response(StatusCode::BAD_REQUEST, "ANI-RSS search body is too large.");
        };
        let Ok(request) = serde_json::from_slice::<AniRssSearchRequest>(&bytes) else {
            return text_response(
                StatusCode::BAD_REQUEST,
                "Request body must contain source and query.",
            );
        };
        if !approved_ani_rss_source(admin, &request.source) {
            return text_response(
                StatusCode::FORBIDDEN,
                "Approve this ANI-RSS source before searching it.",
            );
        }
        return run_ani_rss_read(admin, move |service| service.search(&request)).await;
    }

    if path == "/api/automation/ani-rss/groups" && method == Method::POST {
        let Ok(bytes) = to_bytes(body, 65_536).await else {
            return text_response(StatusCode::BAD_REQUEST, "ANI-RSS group body is too large.");
        };
        let Ok(request) = serde_json::from_slice::<AniRssGroupsRequest>(&bytes) else {
            return text_response(
                StatusCode::BAD_REQUEST,
                "Request body must contain source and locator.",
            );
        };
        if !approved_ani_rss_source(admin, &request.source) {
            return text_response(
                StatusCode::FORBIDDEN,
                "Approve this ANI-RSS source before using it.",
            );
        }
        return run_ani_rss_read(admin, move |service| service.groups(&request)).await;
    }

    if (path == "/api/automation/ani-rss/preview"
        || path == "/api/automation/ani-rss/subscriptions")
        && method == Method::POST
    {
        let Ok(bytes) = to_bytes(body, 65_536).await else {
            return text_response(
                StatusCode::BAD_REQUEST,
                "ANI-RSS subscription body is too large.",
            );
        };
        let Ok(request) = serde_json::from_slice::<AniRssSubscriptionRequest>(&bytes) else {
            return text_response(
                StatusCode::BAD_REQUEST,
                "Request body must describe an ANI-RSS subscription.",
            );
        };
        if !approved_ani_rss_source(admin, &request.source) {
            return text_response(
                StatusCode::FORBIDDEN,
                "Approve this ANI-RSS source before subscribing.",
            );
        }
        if path.ends_with("/preview") {
            return run_ani_rss_read(admin, move |service| service.preview(&request)).await;
        }
        return run_ani_rss_mutation(admin, StatusCode::CREATED, move |service| {
            service.add_subscription(&request)
        })
        .await;
    }

    if let Some(suffix) = path.strip_prefix("/api/automation/ani-rss/subscriptions/") {
        if let Some(id) = suffix.strip_suffix("/enabled").and_then(url_decode) {
            if method != Method::PUT {
                return empty_status(StatusCode::METHOD_NOT_ALLOWED);
            }
            let Ok(bytes) = to_bytes(body, 4_096).await else {
                return text_response(
                    StatusCode::BAD_REQUEST,
                    "ANI-RSS enabled body is too large.",
                );
            };
            let Ok(request) = serde_json::from_slice::<AniRssEnabledRequest>(&bytes) else {
                return text_response(
                    StatusCode::BAD_REQUEST,
                    "Request body must contain enabled.",
                );
            };
            return run_ani_rss_mutation(admin, StatusCode::OK, move |service| {
                service.set_enabled(&id, request.enabled)
            })
            .await;
        }
        if let Some(id) = suffix.strip_suffix("/refresh").and_then(url_decode) {
            if method != Method::POST {
                return empty_status(StatusCode::METHOD_NOT_ALLOWED);
            }
            return run_ani_rss_mutation(admin, StatusCode::ACCEPTED, move |service| {
                service.refresh(&id)
            })
            .await;
        }
        if method == Method::DELETE {
            let Some(id) = url_decode(suffix).filter(|value| !value.is_empty()) else {
                return empty_status(StatusCode::NOT_FOUND);
            };
            return run_ani_rss_mutation(admin, StatusCode::OK, move |service| service.remove(&id))
                .await;
        }
    }

    empty_status(StatusCode::NOT_FOUND)
}

fn approved_ani_rss_source(admin: &ProviderAdminState, source: &str) -> bool {
    admin.ani_rss_source_is_approved(source).unwrap_or(false)
}

async fn run_ani_rss_read<T, F>(admin: &Arc<ProviderAdminState>, action: F) -> Response<Body>
where
    T: Serialize + Send + 'static,
    F: FnOnce(AniRssService) -> crate::Result<T> + Send + 'static,
{
    let (service, _) = match admin.ani_rss_service() {
        Ok(service) => service,
        Err(error) => return text_response(StatusCode::SERVICE_UNAVAILABLE, &error.to_string()),
    };
    match tokio::task::spawn_blocking(move || action(service)).await {
        Ok(Ok(value)) => json_response(StatusCode::OK, &value),
        Ok(Err(error)) => text_response(StatusCode::BAD_GATEWAY, &error.to_string()),
        Err(error) => text_response(
            StatusCode::INTERNAL_SERVER_ERROR,
            &format!("ANI-RSS task failed: {error}"),
        ),
    }
}

async fn run_ani_rss_mutation<F>(
    admin: &Arc<ProviderAdminState>,
    status: StatusCode,
    action: F,
) -> Response<Body>
where
    F: FnOnce(AniRssService) -> crate::Result<()> + Send + 'static,
{
    let (service, _) = match admin.ani_rss_service() {
        Ok(service) => service,
        Err(error) => return text_response(StatusCode::SERVICE_UNAVAILABLE, &error.to_string()),
    };
    match tokio::task::spawn_blocking(move || action(service)).await {
        Ok(Ok(())) => json_response(status, &serde_json::json!({ "accepted": true })),
        Ok(Err(error)) => text_response(StatusCode::BAD_GATEWAY, &error.to_string()),
        Err(error) => text_response(
            StatusCode::INTERNAL_SERVER_ERROR,
            &format!("ANI-RSS task failed: {error}"),
        ),
    }
}
