use super::*;

pub(super) async fn handle_media(
    state: &HttpServerState,
    method: &Method,
    path: &str,
    headers: &HeaderMap,
) -> Response<Body> {
    if method != Method::GET && method != Method::HEAD {
        return empty_status(StatusCode::METHOD_NOT_ALLOWED);
    }
    let id = path.strip_prefix("/media/").unwrap_or_default();
    let library = state.library();
    let Some(path) = library.files_by_id.get(id) else {
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

/// Serves `/posters/{mediaId}`, checking the scan-time static poster map
/// first and falling back to a poster cached from anime recognition (see
/// `spawn_poster_resolution`), which is not known until runtime.
pub(super) async fn handle_poster(
    state: &HttpServerState,
    method: &Method,
    path: &str,
) -> Response<Body> {
    if method != Method::GET && method != Method::HEAD {
        return empty_status(StatusCode::METHOD_NOT_ALLOWED);
    }
    let id = path.strip_prefix("/posters/").unwrap_or_default();
    let library = state.library();
    if let Some(file) = library.poster_files_by_id.get(id) {
        return serve_file(file, method, "private, max-age=3600").await;
    }
    if let Some(store) = &state.catalog_metadata
        && let Some(file) = store.poster_file(id)
    {
        return serve_file(&file, method, "private, max-age=3600").await;
    }
    empty_status(StatusCode::NOT_FOUND)
}

pub(super) async fn handle_static_mapped_file(
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

pub(super) async fn handle_web_asset(
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

pub(super) fn handle_authenticated_post_hook(
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

pub(super) async fn serve_file(
    path: &Path,
    method: &Method,
    cache_control: &'static str,
) -> Response<Body> {
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

pub(super) fn parse_range(header: &str, file_size: u64) -> Option<(u64, u64)> {
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

pub(super) fn should_serve_web_index(
    method: &Method,
    headers: &HeaderMap,
    relative_path: &str,
) -> bool {
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

pub(super) fn json_response<T: Serialize>(status: StatusCode, value: &T) -> Response<Body> {
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

pub(super) fn text_response(status: StatusCode, value: &str) -> Response<Body> {
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

pub(super) fn empty_status(status: StatusCode) -> Response<Body> {
    let mut headers = HeaderMap::new();
    headers.insert(CONTENT_LENGTH, HeaderValue::from_static("0"));
    response_with_headers(status, headers, Body::empty())
}

pub(super) fn response_with_headers(
    status: StatusCode,
    headers: HeaderMap,
    body: Body,
) -> Response<Body> {
    let mut response = Response::new(body);
    *response.status_mut() = status;
    for (name, value) in headers {
        if let Some(name) = name {
            response.headers_mut().insert(name, value);
        }
    }
    response
}

pub(super) fn header_value(value: impl AsRef<str>) -> HeaderValue {
    HeaderValue::from_str(value.as_ref()).expect("generated header value should be valid")
}

pub(super) fn content_type(path: &Path) -> &'static str {
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

pub(super) fn url_decode(value: &str) -> Option<String> {
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

pub(super) fn parse_query_parameters(query: Option<&str>) -> BTreeMap<String, String> {
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

pub(super) fn parse_boolean_query_parameter(value: &str) -> Option<bool> {
    match value.trim().to_ascii_lowercase().as_str() {
        "true" | "1" | "yes" => Some(true),
        "false" | "0" | "no" => Some(false),
        _ => None,
    }
}

pub(super) fn parse_provider_filter(
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

pub(super) fn hex_value(byte: u8) -> Option<u8> {
    match byte {
        b'0'..=b'9' => Some(byte - b'0'),
        b'a'..=b'f' => Some(byte - b'a' + 10),
        b'A'..=b'F' => Some(byte - b'A' + 10),
        _ => None,
    }
}

pub(super) fn constant_time_eq(expected: &[u8], supplied: Option<&[u8]>) -> bool {
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
