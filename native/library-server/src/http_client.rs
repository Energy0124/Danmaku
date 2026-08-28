use std::collections::BTreeMap;
use std::io::{Read, Write};
use std::net::TcpStream;

use crate::{LibraryServerError, Result};

const MAX_RESPONSE_BYTES: usize = 16 * 1024 * 1024;

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
            "HTTPS outbound requests are only supported by the Windows server build",
        ))
    }
}

pub(crate) fn send_plain_http_request(request: HttpRequest) -> Result<HttpResponse> {
    let mut stream =
        TcpStream::connect((request.url.host.as_str(), request.url.port)).map_err(|error| {
            LibraryServerError::with_context(
                error,
                format!(
                    "failed to connect to HTTP endpoint {}",
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
pub(crate) fn send_winhttp_request(request: HttpRequest) -> Result<HttpResponse> {
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
    let body_len = u32::try_from(request.body.len())
        .map_err(|_| LibraryServerError::new("HTTP request body exceeded WinHTTP length limit"))?;
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
                "HTTP response exceeded {MAX_RESPONSE_BYTES} bytes"
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
pub(crate) fn winhttp_query_header_string(
    request: *mut std::ffi::c_void,
    query: u32,
) -> Option<String> {
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
pub(crate) fn wide_null(value: &str) -> Vec<u16> {
    value.encode_utf16().chain(std::iter::once(0)).collect()
}

#[cfg(windows)]
pub(crate) fn winhttp_last_error(operation: &str) -> LibraryServerError {
    let code = unsafe { windows_sys::Win32::Foundation::GetLastError() };
    LibraryServerError::new(format!("{operation} failed with Windows error {code}"))
}

pub(crate) fn parse_http_response(bytes: Vec<u8>) -> Result<HttpResponse> {
    let header_end = bytes
        .windows(4)
        .position(|window| window == b"\r\n\r\n")
        .ok_or_else(|| LibraryServerError::new("HTTP response was malformed"))?;
    let head = String::from_utf8_lossy(&bytes[..header_end]);
    let mut lines = head.split("\r\n");
    let status = lines
        .next()
        .and_then(|line| line.split_whitespace().nth(1))
        .and_then(|value| value.parse::<u16>().ok())
        .ok_or_else(|| LibraryServerError::new("HTTP status was malformed"))?;
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
            "HTTP response exceeded {MAX_RESPONSE_BYTES} bytes"
        )));
    }
    Ok(HttpResponse {
        status,
        headers,
        body,
    })
}

pub(crate) fn decode_chunked(input: &[u8]) -> Result<Vec<u8>> {
    let mut output = Vec::new();
    let mut offset = 0;
    loop {
        let line_end = input[offset..]
            .windows(2)
            .position(|window| window == b"\r\n")
            .ok_or_else(|| LibraryServerError::new("chunked HTTP response was malformed"))?
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
            return Err(LibraryServerError::new("chunked HTTP response ended early"));
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

pub(crate) fn endpoint_url(
    base_url: &str,
    api_path: &str,
    query: Option<&str>,
) -> Result<ParsedUrl> {
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

pub(crate) fn resolve_redirect(current: &ParsedUrl, location: &str) -> Result<ParsedUrl> {
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
        .ok_or_else(|| LibraryServerError::new("base URL must use http or https"))?;
    let scheme = scheme.to_ascii_lowercase();
    if scheme != "http" && scheme != "https" {
        return Err(LibraryServerError::new("base URL must use http or https"));
    }
    let (authority, path) = rest
        .split_once('/')
        .map(|(authority, path)| (authority, format!("/{path}")))
        .unwrap_or((rest, "/".to_owned()));
    if authority.contains('@') {
        return Err(LibraryServerError::new(
            "base URL must not include user info",
        ));
    }
    let (host, port) = authority
        .rsplit_once(':')
        .and_then(|(host, port)| Some((host, port.parse::<u16>().ok()?)))
        .unwrap_or((authority, if scheme == "https" { 443 } else { 80 }));
    if host.trim().is_empty() {
        return Err(LibraryServerError::new("base URL must include a host"));
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

pub(crate) fn normalize_base_url(value: &str) -> String {
    value.trim().trim_end_matches('/').to_owned()
}

pub(crate) fn should_follow_redirect(method: &str, body: Option<&str>, status: u16) -> bool {
    body.is_none()
        && method.eq_ignore_ascii_case("GET")
        && matches!(status, 301 | 302 | 303 | 307 | 308)
}
