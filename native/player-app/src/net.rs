//! Minimal HTTP/1.1 transport for the trusted-LAN protocol.
//!
//! Deliberately dependency-free (std TCP): the player only ever talks to a
//! LAN server over plain HTTP with `Connection: close` semantics.

use std::{
    io::{Read, Write},
    net::{TcpStream, ToSocketAddrs},
    time::Duration,
};

const CONNECT_TIMEOUT: Duration = Duration::from_secs(5);
const READ_TIMEOUT: Duration = Duration::from_secs(15);

pub(crate) struct HttpResponse {
    pub status: u16,
    pub body: Vec<u8>,
}

impl HttpResponse {
    pub fn body_string(self) -> Result<String, String> {
        String::from_utf8(self.body).map_err(|_| "server returned non-UTF-8 content".to_owned())
    }
}

/// GET returning the raw response (any status); callers decide how to treat
/// non-200s.
pub(crate) fn http_get_raw(base_url: &str, path_and_query: &str) -> Result<HttpResponse, String> {
    http_request(base_url, "GET", path_and_query, None)
}

/// GET that requires a 200 and returns the body as UTF-8 text.
pub(crate) fn http_get(base_url: &str, path_and_query: &str) -> Result<String, String> {
    let response = http_get_raw(base_url, path_and_query)?;
    require_status(response, &[200])?.body_string()
}

/// PUT with a JSON body; returns the response status on any 2xx.
pub(crate) fn http_put_json(
    base_url: &str,
    path_and_query: &str,
    json_body: &str,
) -> Result<u16, String> {
    let response = http_request(
        base_url,
        "PUT",
        path_and_query,
        Some(("application/json; charset=utf-8", json_body.as_bytes())),
    )?;
    if (200..300).contains(&response.status) {
        Ok(response.status)
    } else {
        Err(status_error(&response))
    }
}

fn require_status(response: HttpResponse, accepted: &[u16]) -> Result<HttpResponse, String> {
    if accepted.contains(&response.status) {
        Ok(response)
    } else {
        Err(status_error(&response))
    }
}

fn status_error(response: &HttpResponse) -> String {
    let detail: String = String::from_utf8_lossy(&response.body)
        .chars()
        .take(240)
        .collect();
    if detail.trim().is_empty() {
        format!("server returned HTTP {}", response.status)
    } else {
        format!(
            "server returned HTTP {}: {}",
            response.status,
            detail.trim()
        )
    }
}

fn http_request(
    base_url: &str,
    method: &str,
    path_and_query: &str,
    body: Option<(&str, &[u8])>,
) -> Result<HttpResponse, String> {
    let base = HttpBase::parse(base_url)?;
    let request_path = format!("{}{}", base.path_prefix, path_and_query);
    let mut addresses = (base.host.as_str(), base.port)
        .to_socket_addrs()
        .map_err(|error| format!("failed to resolve server: {error}"))?;
    let address = addresses
        .next()
        .ok_or_else(|| "server did not resolve to an address".to_owned())?;
    let mut stream = TcpStream::connect_timeout(&address, CONNECT_TIMEOUT)
        .map_err(|error| format!("failed to connect to server: {error}"))?;
    stream
        .set_read_timeout(Some(READ_TIMEOUT))
        .map_err(|error| format!("failed to configure server timeout: {error}"))?;
    stream
        .set_write_timeout(Some(CONNECT_TIMEOUT))
        .map_err(|error| format!("failed to configure server timeout: {error}"))?;
    let mut head = format!(
        "{method} {request_path} HTTP/1.1\r\nHost: {}\r\nAccept: */*\r\nConnection: close\r\n",
        base.host_header()
    );
    if let Some((content_type, content)) = body {
        head.push_str(&format!(
            "Content-Type: {content_type}\r\nContent-Length: {}\r\n",
            content.len()
        ));
    }
    head.push_str("\r\n");
    stream
        .write_all(head.as_bytes())
        .map_err(|error| format!("failed to send request: {error}"))?;
    if let Some((_, content)) = body {
        stream
            .write_all(content)
            .map_err(|error| format!("failed to send request body: {error}"))?;
    }
    let mut response = Vec::new();
    stream
        .read_to_end(&mut response)
        .map_err(|error| format!("failed to read server response: {error}"))?;
    parse_http_response(&response)
}

#[derive(Clone, Debug, Eq, PartialEq)]
struct HttpBase {
    host: String,
    port: u16,
    path_prefix: String,
}

impl HttpBase {
    fn parse(value: &str) -> Result<Self, String> {
        let value = value.trim().trim_end_matches('/');
        let authority_and_path = value
            .strip_prefix("http://")
            .ok_or_else(|| "server URL must use http://".to_owned())?;
        let (authority, path_prefix) = authority_and_path
            .split_once('/')
            .map(|(authority, path)| (authority, format!("/{path}")))
            .unwrap_or((authority_and_path, String::new()));
        if authority.is_empty() {
            return Err("server URL is missing a host".to_owned());
        }
        let (host, port) = match authority.rsplit_once(':') {
            Some((host, port))
                if !host.is_empty() && port.chars().all(|value| value.is_ascii_digit()) =>
            {
                let port = port
                    .parse::<u16>()
                    .map_err(|_| "server URL has an invalid port".to_owned())?;
                (host.to_owned(), port)
            }
            _ => (authority.to_owned(), 80),
        };
        Ok(Self {
            host,
            port,
            path_prefix,
        })
    }

    fn host_header(&self) -> String {
        if self.port == 80 {
            self.host.clone()
        } else {
            format!("{}:{}", self.host, self.port)
        }
    }
}

pub(crate) fn parse_http_response(response: &[u8]) -> Result<HttpResponse, String> {
    let marker = b"\r\n\r\n";
    let Some(header_end) = response
        .windows(marker.len())
        .position(|window| window == marker)
    else {
        return Err("server returned an invalid HTTP response".to_owned());
    };
    let header = String::from_utf8_lossy(&response[..header_end]);
    let mut lines = header.lines();
    let status = lines
        .next()
        .and_then(|line| line.split_whitespace().nth(1))
        .and_then(|value| value.parse::<u16>().ok())
        .ok_or_else(|| "server returned an invalid HTTP status".to_owned())?;
    let chunked = lines.any(|line| {
        line.split_once(':').is_some_and(|(name, value)| {
            name.eq_ignore_ascii_case("transfer-encoding")
                && value.trim().eq_ignore_ascii_case("chunked")
        })
    });
    let raw_body = &response[header_end + marker.len()..];
    let body = if chunked {
        decode_chunked(raw_body)?
    } else {
        raw_body.to_vec()
    };
    Ok(HttpResponse { status, body })
}

fn decode_chunked(input: &[u8]) -> Result<Vec<u8>, String> {
    let mut cursor = 0;
    let mut output = Vec::new();
    loop {
        let line_end = input[cursor..]
            .windows(2)
            .position(|window| window == b"\r\n")
            .map(|value| cursor + value)
            .ok_or_else(|| "server returned invalid chunked content".to_owned())?;
        let size_text = String::from_utf8_lossy(&input[cursor..line_end]);
        let size = usize::from_str_radix(size_text.split(';').next().unwrap_or("").trim(), 16)
            .map_err(|_| "server returned an invalid chunk size".to_owned())?;
        cursor = line_end + 2;
        if size == 0 {
            return Ok(output);
        }
        let end = cursor.saturating_add(size);
        if end + 2 > input.len() || &input[end..end + 2] != b"\r\n" {
            return Err("server returned a truncated chunk".to_owned());
        }
        output.extend_from_slice(&input[cursor..end]);
        cursor = end + 2;
    }
}

/// Percent-encodes a path segment (RFC 3986 unreserved characters pass).
pub(crate) fn percent_encode_path_segment(value: &str) -> String {
    let mut encoded = String::with_capacity(value.len());
    for byte in value.bytes() {
        match byte {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                encoded.push(byte as char);
            }
            _ => encoded.push_str(&format!("%{byte:02X}")),
        }
    }
    encoded
}

#[cfg(test)]
mod tests {
    use super::{HttpBase, parse_http_response, percent_encode_path_segment};

    #[test]
    fn parses_base_urls_with_ports_and_prefixes() {
        assert_eq!(
            HttpBase::parse("http://127.0.0.1:8686/"),
            Ok(HttpBase {
                host: "127.0.0.1".to_owned(),
                port: 8686,
                path_prefix: String::new(),
            }),
        );
        assert_eq!(
            HttpBase::parse("http://nas.local/danmaku"),
            Ok(HttpBase {
                host: "nas.local".to_owned(),
                port: 80,
                path_prefix: "/danmaku".to_owned(),
            }),
        );
        assert!(HttpBase::parse("https://secure.example").is_err());
    }

    #[test]
    fn parses_plain_and_chunked_bodies_with_status() {
        let plain = parse_http_response(b"HTTP/1.1 204 No Content\r\nContent-Length: 0\r\n\r\n")
            .expect("parse plain");
        assert_eq!(plain.status, 204);
        assert!(plain.body.is_empty());

        let chunked = parse_http_response(
            b"HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n4\r\nbody\r\n0\r\n\r\n",
        )
        .expect("parse chunked");
        assert_eq!(chunked.status, 200);
        assert_eq!(chunked.body, b"body");
    }

    #[test]
    fn percent_encodes_reserved_bytes() {
        assert_eq!(percent_encode_path_segment("abc-123_.~"), "abc-123_.~");
        assert_eq!(percent_encode_path_segment("a b/c"), "a%20b%2Fc");
        assert_eq!(percent_encode_path_segment("日"), "%E6%97%A5");
    }
}
