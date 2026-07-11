//! Async poster loading for the library grid.
//!
//! A background worker fetches poster bytes over the LAN route and decodes
//! them into RGBA thumbnails; the UI thread turns finished decodes into
//! textures during `poll`. The texture cache is LRU-capped so a 5,000-item
//! catalog cannot exhaust memory by scrolling.

use std::{
    collections::{HashMap, VecDeque},
    sync::{
        Arc, Mutex,
        mpsc::{Receiver, Sender, channel},
    },
    thread::JoinHandle,
};

use eframe::egui;

use crate::net::http_get_raw;

const MAX_TEXTURES: usize = 400;
const THUMBNAIL_MAX_WIDTH: u32 = 260;
const THUMBNAIL_MAX_HEIGHT: u32 = 390;

#[derive(Clone)]
pub enum PosterState {
    Loading,
    Ready(egui::TextureHandle),
    Failed,
}

struct DecodedPoster {
    media_id: String,
    image: Result<egui::ColorImage, String>,
}

struct PosterRequest {
    media_id: String,
    poster_url: String,
}

pub struct PosterCache {
    requests: Sender<PosterRequest>,
    decoded: Arc<Mutex<Vec<DecodedPoster>>>,
    textures: HashMap<String, PosterState>,
    recency: VecDeque<String>,
    base_url: Option<String>,
    _worker: WorkerGuard,
}

impl PosterCache {
    pub fn new(egui_context: egui::Context) -> Self {
        let (requests, receiver) = channel::<PosterRequest>();
        let decoded: Arc<Mutex<Vec<DecodedPoster>>> = Arc::new(Mutex::new(Vec::new()));
        let worker = spawn_worker(receiver, Arc::clone(&decoded), egui_context);
        Self {
            requests,
            decoded,
            textures: HashMap::new(),
            recency: VecDeque::new(),
            base_url: None,
            _worker: worker,
        }
    }

    /// Points the cache at a server; changing servers clears cached art.
    pub fn set_base_url(&mut self, base_url: Option<String>) {
        if self.base_url != base_url {
            self.base_url = base_url;
            self.textures.clear();
            self.recency.clear();
        }
    }

    /// Requests a poster if it is not already cached or in flight, and
    /// returns its current state. `None` means the item has no poster (or
    /// no server is configured).
    pub fn poster(&mut self, media_id: &str, poster_path: Option<&str>) -> Option<PosterState> {
        let poster_path = poster_path?;
        let base_url = self.base_url.clone()?;
        self.touch(media_id);
        if let Some(state) = self.textures.get(media_id) {
            return Some(state.clone());
        }
        self.textures
            .insert(media_id.to_owned(), PosterState::Loading);
        let _ = self.requests.send(PosterRequest {
            media_id: media_id.to_owned(),
            poster_url: format!("{}{poster_path}", base_url.trim_end_matches('/')),
        });
        Some(PosterState::Loading)
    }

    /// Converts finished decodes into textures. Call once per frame.
    pub fn poll(&mut self, ctx: &egui::Context) {
        let finished: Vec<DecodedPoster> = match self.decoded.lock() {
            Ok(mut decoded) => decoded.drain(..).collect(),
            Err(_) => Vec::new(),
        };
        for poster in finished {
            let state = match poster.image {
                Ok(image) => PosterState::Ready(ctx.load_texture(
                    format!("poster-{}", poster.media_id),
                    image,
                    egui::TextureOptions::LINEAR,
                )),
                Err(_) => PosterState::Failed,
            };
            self.textures.insert(poster.media_id, state);
        }
        self.evict_if_needed();
    }

    fn touch(&mut self, media_id: &str) {
        if let Some(position) = self.recency.iter().position(|entry| entry == media_id) {
            self.recency.remove(position);
        }
        self.recency.push_back(media_id.to_owned());
    }

    fn evict_if_needed(&mut self) {
        while self.textures.len() > MAX_TEXTURES {
            let Some(oldest) = self.recency.pop_front() else {
                return;
            };
            // Keep in-flight loads; their results arrive regardless.
            if matches!(self.textures.get(&oldest), Some(PosterState::Loading)) {
                self.recency.push_back(oldest);
                return;
            }
            self.textures.remove(&oldest);
        }
    }
}

struct WorkerGuard {
    _thread: JoinHandle<()>,
}

fn spawn_worker(
    receiver: Receiver<PosterRequest>,
    decoded: Arc<Mutex<Vec<DecodedPoster>>>,
    egui_context: egui::Context,
) -> WorkerGuard {
    let thread = std::thread::Builder::new()
        .name("danmaku-posters".to_owned())
        .spawn(move || {
            // The channel disconnecting (cache dropped) ends the worker.
            while let Ok(request) = receiver.recv() {
                let image = fetch_and_decode(&request.poster_url);
                if let Ok(mut inbox) = decoded.lock() {
                    inbox.push(DecodedPoster {
                        media_id: request.media_id,
                        image,
                    });
                }
                egui_context.request_repaint();
            }
        })
        .expect("poster worker spawns");
    WorkerGuard { _thread: thread }
}

fn fetch_and_decode(poster_url: &str) -> Result<egui::ColorImage, String> {
    let (base, path) = split_url(poster_url)?;
    let response = http_get_raw(base, path)?;
    if response.status != 200 {
        return Err(format!("poster request returned HTTP {}", response.status));
    }
    decode_thumbnail(&response.body)
}

pub(crate) fn decode_thumbnail(bytes: &[u8]) -> Result<egui::ColorImage, String> {
    let image =
        image::load_from_memory(bytes).map_err(|error| format!("poster decode failed: {error}"))?;
    let thumbnail = image.thumbnail(THUMBNAIL_MAX_WIDTH, THUMBNAIL_MAX_HEIGHT);
    let rgba = thumbnail.to_rgba8();
    let size = [rgba.width() as usize, rgba.height() as usize];
    Ok(egui::ColorImage::from_rgba_unmultiplied(
        size,
        rgba.as_raw(),
    ))
}

/// Splits an absolute `http://host:port/path` URL into base and path parts
/// compatible with the transport helpers.
fn split_url(url: &str) -> Result<(&str, &str), String> {
    let after_scheme = url
        .strip_prefix("http://")
        .ok_or_else(|| "poster URL must use http://".to_owned())?;
    match after_scheme.find('/') {
        Some(slash) => {
            let split_at = "http://".len() + slash;
            Ok((&url[..split_at], &url[split_at..]))
        }
        None => Ok((url, "/")),
    }
}

#[cfg(test)]
mod tests {
    use super::{decode_thumbnail, split_url};

    #[test]
    fn splits_absolute_urls() {
        assert_eq!(
            split_url("http://127.0.0.1:8686/posters/abc").expect("splits"),
            ("http://127.0.0.1:8686", "/posters/abc"),
        );
        assert!(split_url("ftp://x/y").is_err());
    }

    #[test]
    fn decodes_and_downscales_png_posters() {
        // Encode a tall PNG in-memory so decode + downscale run end to end.
        let mut png = Vec::new();
        let source = image::RgbaImage::from_pixel(600, 1200, image::Rgba([200, 40, 40, 255]));
        image::DynamicImage::ImageRgba8(source)
            .write_to(&mut std::io::Cursor::new(&mut png), image::ImageFormat::Png)
            .expect("test PNG encodes");

        let image = decode_thumbnail(&png).expect("decodes");
        assert!(image.size[0] <= 260 && image.size[1] <= 390);
        assert!(image.size[0] > 0 && image.size[1] > 0);
        assert!(decode_thumbnail(b"not an image").is_err());
    }
}
