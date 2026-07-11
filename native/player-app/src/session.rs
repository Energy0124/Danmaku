//! Library session: connection state plus background wire calls.
//!
//! All HTTP happens on short-lived background threads that post events into
//! an inbox; the UI thread drains the inbox once per frame. No async runtime
//! is needed for a trusted-LAN client with a handful of in-flight calls.

use std::sync::{Arc, Mutex};

use eframe::egui;

use crate::{
    danmaku::{DanmakuLoad, fetch_server_danmaku},
    library::{
        LibraryCatalog, PlaybackProgress, fetch_catalog, fetch_progress, fetch_progress_list,
        upload_progress,
    },
};

pub enum SessionEvent {
    Catalog(Result<LibraryCatalog, String>),
    ProgressList(Result<Vec<PlaybackProgress>, String>),
    ResumeLookup {
        media_id: String,
        progress: Option<PlaybackProgress>,
    },
    Danmaku {
        media_id: String,
        load: Result<DanmakuLoad, String>,
    },
}

pub struct LibrarySession {
    pub base_url: String,
    pub pairing_token: Option<String>,
    pub catalog: Option<LibraryCatalog>,
    pub progresses: Vec<PlaybackProgress>,
    pub catalog_error: Option<String>,
    pub loading_catalog: bool,
    inbox: Arc<Mutex<Vec<SessionEvent>>>,
    egui_context: egui::Context,
}

impl LibrarySession {
    pub fn connect(
        base_url: String,
        pairing_token: Option<String>,
        egui_context: egui::Context,
    ) -> Self {
        let mut session = Self {
            base_url,
            pairing_token,
            catalog: None,
            progresses: Vec::new(),
            catalog_error: None,
            loading_catalog: false,
            inbox: Arc::new(Mutex::new(Vec::new())),
            egui_context,
        };
        session.refresh_catalog();
        session.refresh_progress();
        session
    }

    pub fn refresh_catalog(&mut self) {
        self.loading_catalog = true;
        self.catalog_error = None;
        let base_url = self.base_url.clone();
        self.spawn(
            move |base| SessionEvent::Catalog(fetch_catalog(&base)),
            base_url,
        );
    }

    pub fn refresh_progress(&mut self) {
        let base_url = self.base_url.clone();
        self.spawn(
            move |base| SessionEvent::ProgressList(fetch_progress_list(&base)),
            base_url,
        );
    }

    /// Looks up saved progress for an item ahead of playback.
    pub fn lookup_resume(&self, media_id: String) {
        let base_url = self.base_url.clone();
        self.spawn(
            move |base| {
                let progress = fetch_progress(&base, &media_id).ok().flatten();
                SessionEvent::ResumeLookup { media_id, progress }
            },
            base_url,
        );
    }

    pub fn fetch_danmaku(&self, media_id: String, force_refresh: bool) {
        let base_url = self.base_url.clone();
        self.spawn(
            move |base| {
                let load = fetch_server_danmaku(&base, &media_id, force_refresh);
                SessionEvent::Danmaku { media_id, load }
            },
            base_url,
        );
    }

    /// Fire-and-forget progress upload; errors only surface in stderr since
    /// the next list refresh reconciles state anyway.
    pub fn upload_progress(&mut self, progress: PlaybackProgress) {
        // Keep the local copy current so rails update without a round-trip.
        self.progresses
            .retain(|existing| existing.media_id != progress.media_id);
        self.progresses.insert(0, progress.clone());
        let base_url = self.base_url.clone();
        std::thread::spawn(move || {
            if let Err(error) = upload_progress(&base_url, &progress) {
                eprintln!("progress upload failed for {}: {error}", progress.media_id);
            }
        });
    }

    /// Drains background events into session state and returns the ones the
    /// app itself must react to (resume lookups, danmaku loads).
    pub fn drain_events(&mut self) -> Vec<SessionEvent> {
        let events: Vec<SessionEvent> = match self.inbox.lock() {
            Ok(mut inbox) => inbox.drain(..).collect(),
            Err(_) => Vec::new(),
        };
        let mut for_app = Vec::new();
        for event in events {
            match event {
                SessionEvent::Catalog(result) => {
                    self.loading_catalog = false;
                    match result {
                        Ok(catalog) => {
                            self.catalog = Some(catalog);
                            self.catalog_error = None;
                        }
                        Err(error) => self.catalog_error = Some(error),
                    }
                }
                SessionEvent::ProgressList(result) => {
                    if let Ok(progresses) = result {
                        self.progresses = progresses;
                    }
                }
                other => for_app.push(other),
            }
        }
        for_app
    }

    /// Absolute stream URL for an item, carrying the pairing token when one
    /// is configured (matching the desktop remote client's URLs).
    pub fn stream_url(&self, stream_path: &str) -> String {
        let base = format!("{}{stream_path}", self.base_url.trim_end_matches('/'));
        match self
            .pairing_token
            .as_deref()
            .filter(|token| !token.is_empty())
        {
            Some(token) => format!("{base}?token={token}"),
            None => base,
        }
    }

    fn spawn(&self, job: impl FnOnce(String) -> SessionEvent + Send + 'static, base_url: String) {
        let inbox = Arc::clone(&self.inbox);
        let egui_context = self.egui_context.clone();
        std::thread::spawn(move || {
            let event = job(base_url);
            if let Ok(mut inbox) = inbox.lock() {
                inbox.push(event);
            }
            egui_context.request_repaint();
        });
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn test_session(base_url: &str) -> LibrarySession {
        LibrarySession {
            base_url: base_url.to_owned(),
            pairing_token: Some("123456".to_owned()),
            catalog: None,
            progresses: Vec::new(),
            catalog_error: None,
            loading_catalog: false,
            inbox: Arc::new(Mutex::new(Vec::new())),
            egui_context: egui::Context::default(),
        }
    }

    #[test]
    fn stream_urls_carry_the_pairing_token() {
        let session = test_session("http://127.0.0.1:8686/");
        assert_eq!(
            session.stream_url("/media/abc"),
            "http://127.0.0.1:8686/media/abc?token=123456",
        );

        let mut anonymous = test_session("http://127.0.0.1:8686");
        anonymous.pairing_token = None;
        assert_eq!(
            anonymous.stream_url("/media/abc"),
            "http://127.0.0.1:8686/media/abc",
        );
    }

    #[test]
    fn upload_keeps_local_progress_newest_first() {
        let mut session = test_session("http://127.0.0.1:1");
        session.upload_progress(PlaybackProgress {
            media_id: "one".to_owned(),
            position_ms: 100,
            duration_ms: None,
            updated_at_epoch_ms: 1,
        });
        session.upload_progress(PlaybackProgress {
            media_id: "one".to_owned(),
            position_ms: 200,
            duration_ms: None,
            updated_at_epoch_ms: 2,
        });

        assert_eq!(session.progresses.len(), 1);
        assert_eq!(session.progresses[0].position_ms, 200);
    }
}
