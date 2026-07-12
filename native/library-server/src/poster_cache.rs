//! On-disk cache for recognized-anime poster images.
//!
//! Dandanplay matches do not carry a poster image, so once an item is
//! recognized (see `catalog_metadata`), the server best-effort looks up an
//! image URL from an external provider (MyAnimeList/Bangumi) and caches the
//! bytes here. Downloads are keyed by the image URL so episodes that share an
//! anime never re-fetch the same artwork.

use std::fs;
use std::path::{Path, PathBuf};

use crate::dandanplay::{HttpRequest, parse_url, send_http_request};
use crate::hash::sha256_hex;
use crate::{LibraryServerError, Result};

const MAX_POSTER_BYTES: usize = 8 * 1024 * 1024;
const DEFAULT_EXTENSION: &str = "jpg";

#[derive(Debug)]
pub struct PosterCacheStore {
    directory: PathBuf,
}

impl PosterCacheStore {
    pub fn new(directory: impl Into<PathBuf>) -> Self {
        Self {
            directory: directory.into(),
        }
    }

    /// Returns a local file path with the poster's bytes, downloading and
    /// caching it first when not already present. Blocking (performs
    /// synchronous network I/O); call from a background/blocking task.
    /// Best-effort: returns `None` on any failure rather than propagating an
    /// error, since a missing poster must never fail catalog enrichment.
    pub fn resolve(&self, image_url: &str) -> Option<PathBuf> {
        let image_url = image_url.trim();
        if image_url.is_empty() {
            return None;
        }
        let destination = self.cache_path_for(image_url);
        if destination.is_file() {
            return Some(destination);
        }
        self.download(image_url, &destination).ok()?;
        Some(destination)
    }

    fn cache_path_for(&self, image_url: &str) -> PathBuf {
        let extension = extension_from_url(image_url).unwrap_or(DEFAULT_EXTENSION);
        self.directory
            .join(format!("{}.{extension}", sha256_hex(image_url)))
    }

    fn download(&self, image_url: &str, destination: &Path) -> Result<()> {
        let url = parse_url(image_url)?;
        let response = send_http_request(HttpRequest {
            method: "GET".to_owned(),
            url,
            headers: std::collections::BTreeMap::from([
                ("Accept".to_owned(), "image/*,*/*;q=0.8".to_owned()),
                ("User-Agent".to_owned(), "Danmaku/1.0".to_owned()),
            ]),
            body: Vec::new(),
        })?;
        if !(200..=299).contains(&response.status) {
            return Err(LibraryServerError::new(format!(
                "poster fetch returned HTTP {}",
                response.status
            )));
        }
        if response.body.len() > MAX_POSTER_BYTES {
            return Err(LibraryServerError::new(format!(
                "poster response exceeded {MAX_POSTER_BYTES} bytes"
            )));
        }
        if response.body.is_empty() {
            return Err(LibraryServerError::new("poster response was empty"));
        }
        fs::create_dir_all(&self.directory).map_err(|error| {
            LibraryServerError::with_context(
                error,
                format!(
                    "failed to create poster cache directory {}",
                    self.directory.display()
                ),
            )
        })?;
        let temp = PathBuf::from(format!("{}.tmp", destination.display()));
        fs::write(&temp, &response.body).map_err(|error| {
            LibraryServerError::with_context(
                error,
                format!("failed to write poster cache file {}", temp.display()),
            )
        })?;
        fs::rename(&temp, destination).map_err(|error| {
            LibraryServerError::with_context(
                error,
                format!(
                    "failed to replace poster cache file {}",
                    destination.display()
                ),
            )
        })
    }
}

fn extension_from_url(url: &str) -> Option<&'static str> {
    let without_query = url.split(['?', '#']).next().unwrap_or(url);
    let file_name = without_query.rsplit('/').next()?;
    let extension = file_name.rsplit_once('.').map(|(_, extension)| extension)?;
    match extension.to_ascii_lowercase().as_str() {
        "jpg" => Some("jpg"),
        "jpeg" => Some("jpeg"),
        "png" => Some("png"),
        "webp" => Some("webp"),
        "gif" => Some("gif"),
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use std::io::{Read, Write};
    use std::net::TcpListener;
    use std::sync::atomic::{AtomicU64, Ordering};
    use std::thread;

    use super::*;

    static TEMP_COUNTER: AtomicU64 = AtomicU64::new(0);

    fn temp_dir(prefix: &str) -> PathBuf {
        let id = TEMP_COUNTER.fetch_add(1, Ordering::Relaxed);
        let path = std::env::temp_dir().join(format!("{prefix}-{}-{id}", std::process::id()));
        let _ = fs::remove_dir_all(&path);
        path
    }

    fn serve_once(body: &'static [u8], content_type: &'static str) -> String {
        let listener = TcpListener::bind("127.0.0.1:0").expect("listener");
        let address = listener.local_addr().expect("address");
        thread::spawn(move || {
            let (mut stream, _) = listener.accept().expect("accept");
            let mut request = Vec::new();
            let mut chunk = [0_u8; 512];
            loop {
                let count = stream.read(&mut chunk).expect("request");
                request.extend_from_slice(&chunk[..count]);
                if request.windows(4).any(|window| window == b"\r\n\r\n") || count == 0 {
                    break;
                }
            }
            write!(
                stream,
                "HTTP/1.1 200 OK\r\nContent-Type: {content_type}\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",
                body.len(),
            )
            .expect("headers");
            stream.write_all(body).expect("body");
        });
        format!("http://{address}/posters/example.jpg")
    }

    #[test]
    fn downloads_and_caches_poster_bytes() {
        let directory = temp_dir("danmaku-poster-cache");
        let store = PosterCacheStore::new(&directory);
        let url = serve_once(&[0xff, 0xd8, 0xff, 0xd9], "image/jpeg");

        let cached = store.resolve(&url).expect("poster should download");
        assert!(cached.is_file());
        assert_eq!(
            fs::read(&cached).expect("poster bytes"),
            vec![0xff, 0xd8, 0xff, 0xd9]
        );
        assert_eq!(
            cached.extension().and_then(|value| value.to_str()),
            Some("jpg")
        );

        // A second resolve reuses the cached file without another request.
        let cached_again = store.resolve(&url).expect("poster should reuse cache");
        assert_eq!(cached, cached_again);

        fs::remove_dir_all(&directory).ok();
    }

    #[test]
    fn rejects_oversized_and_invalid_urls() {
        let directory = temp_dir("danmaku-poster-cache-invalid");
        let store = PosterCacheStore::new(&directory);
        assert!(store.resolve("").is_none());
        assert!(store.resolve("not a url").is_none());
        assert!(store.resolve("ftp://example.com/a.jpg").is_none());
    }

    #[test]
    fn extension_is_derived_from_the_url_path() {
        assert_eq!(
            extension_from_url("https://cdn.example/img/poster.png?w=200"),
            Some("png")
        );
        assert_eq!(extension_from_url("https://cdn.example/img/poster"), None);
        assert_eq!(
            extension_from_url("https://cdn.example/img/poster.unknownextension"),
            None
        );
    }
}
