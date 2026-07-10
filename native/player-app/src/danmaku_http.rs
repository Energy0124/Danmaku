//! Minimal HTTP/1.1 transport for the trusted-LAN danmaku client route.

use std::{
    io::{Read, Write},
    net::{TcpStream, ToSocketAddrs},
    time::Duration,
};

const CONNECT_TIMEOUT: Duration = Duration::from_secs(5);
const READ_TIMEOUT: Duration = Duration::from_secs(15);

pub(crate) fn http_get(base_url: &str, path_and_query: &str) -> Result<String, String> {
    let base = HttpBase::parse(base_url)?;
    let request_path = format!("{}{}", base.path_prefix, path_and_query);
    let mut addresses = (base.host.as_str(), base.port)
        .to_socket_addrs()
        .map_err(|error| format!("failed to resolve danmaku server: {error}"))?;
    let address = addresses
        .next()
        .ok_or_else(|| "danmaku server did not resolve to an address".to_owned())?;
    let mut stream = TcpStream::connect_timeout(&address, CONNECT_TIMEOUT)
        .map_err(|error| format!("failed to connect to danmaku server: {error}"))?;
    stream
        .set_read_timeout(Some(READ_TIMEOUT))
        .map_err(|error| format!("failed to configure danmaku server timeout: {error}"))?;
    stream
        .set_write_timeout(Some(CONNECT_TIMEOUT))
        .map_err(|error| format!("failed to configure danmaku server timeout: {error}"))?;
    write!(
        stream,
        "GET {request_path} HTTP/1.1\r\nHost: {}\r\nAccept: application/json\r\nConnection: close\r\n\r\n",
        base.host_header()
    )
    .map_err(|error| format!("failed to request danmaku from server: {error}"))?;
    let mut response = Vec::new();
    stream
        .read_to_end(&mut response)
        .map_err(|error| format!("failed to read danmaku server response: {error}"))?;
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
            .ok_or_else(|| "danmaku server URL must use http://".to_owned())?;
        let (authority, path_prefix) = authority_and_path
            .split_once('/')
            .map(|(authority, path)| (authority, format!("/{path}")))
            .unwrap_or((authority_and_path, String::new()));
        if authority.is_empty() {
            return Err("danmaku server URL is missing a host".to_owned());
        }
        let (host, port) = match authority.rsplit_once(':') {
            Some((host, port))
                if !host.is_empty() && port.chars().all(|value| value.is_ascii_digit()) =>
            {
                let port = port
                    .parse::<u16>()
                    .map_err(|_| "danmaku server URL has an invalid port".to_owned())?;
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

pub(crate) fn parse_http_response(response: &[u8]) -> Result<String, String> {
    let marker = b"\r\n\r\n";
    let Some(header_end) = response
        .windows(marker.len())
        .position(|window| window == marker)
    else {
        return Err("danmaku server returned an invalid HTTP response".to_owned());
    };
    let header = String::from_utf8_lossy(&response[..header_end]);
    let mut lines = header.lines();
    let status = lines
        .next()
        .and_then(|line| line.split_whitespace().nth(1))
        .and_then(|value| value.parse::<u16>().ok())
        .ok_or_else(|| "danmaku server returned an invalid HTTP status".to_owned())?;
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
    let body = String::from_utf8(body)
        .map_err(|_| "danmaku server returned non-UTF-8 content".to_owned())?;
    if status != 200 {
        let detail: String = body.chars().take(240).collect();
        return Err(if detail.trim().is_empty() {
            format!("danmaku server returned HTTP {status}")
        } else {
            format!("danmaku server returned HTTP {status}: {}", detail.trim())
        });
    }
    Ok(body)
}

fn decode_chunked(input: &[u8]) -> Result<Vec<u8>, String> {
    let mut cursor = 0;
    let mut output = Vec::new();
    loop {
        let line_end = input[cursor..]
            .windows(2)
            .position(|window| window == b"\r\n")
            .map(|value| cursor + value)
            .ok_or_else(|| "danmaku server returned invalid chunked content".to_owned())?;
        let size_text = String::from_utf8_lossy(&input[cursor..line_end]);
        let size = usize::from_str_radix(size_text.split(';').next().unwrap_or("").trim(), 16)
            .map_err(|_| "danmaku server returned an invalid chunk size".to_owned())?;
        cursor = line_end + 2;
        if size == 0 {
            return Ok(output);
        }
        let end = cursor.saturating_add(size);
        if end + 2 > input.len() || &input[end..end + 2] != b"\r\n" {
            return Err("danmaku server returned a truncated chunk".to_owned());
        }
        output.extend_from_slice(&input[cursor..end]);
        cursor = end + 2;
    }
}
