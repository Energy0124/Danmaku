//! Library session: connection state plus background wire calls.
//!
//! All HTTP happens on short-lived background threads that post events into
//! an inbox; the UI thread drains the inbox once per frame. No async runtime
//! is needed for a trusted-LAN client with a handful of in-flight calls.

use std::sync::{Arc, Mutex};

use eframe::egui;

use crate::{
    danmaku::{
        BangumiDetail, DandanplayMatchCandidate, DandanplaySearchAnime, DandanplaySelection,
        DanmakuLoad, DanmakuLoadKind, fetch_bangumi_detail, fetch_dandanplay_candidates,
        fetch_server_danmaku, search_dandanplay, select_dandanplay_match,
    },
    library::{
        AttentionMappingStatus, AttentionRepairRequest, LibraryAttentionDocument, LibraryCatalog,
        PlaybackProgress, fetch_attention, fetch_catalog, fetch_progress, fetch_progress_list,
        fetch_server_status, upload_progress,
    },
};

pub enum SessionEvent {
    Catalog(Result<LibraryCatalog, String>),
    Attention(Result<LibraryAttentionDocument, String>),
    ProgressList(Result<Vec<PlaybackProgress>, String>),
    /// Background-scan state parsed from `/api/server/status`; `files_seen`
    /// is only present while a scan is running.
    ServerScan {
        scanning: bool,
        files_seen: Option<u64>,
    },
    ResumeLookup {
        media_id: String,
        progress: Option<PlaybackProgress>,
    },
    Danmaku {
        media_id: String,
        load: Result<DanmakuLoad, String>,
    },
    AttentionRepair {
        media_id: String,
        result: Result<(), String>,
    },
    DandanplayCandidates {
        media_id: String,
        result: Result<Vec<DandanplayMatchCandidate>, String>,
    },
    DandanplaySearch {
        media_id: String,
        result: Result<Vec<DandanplaySearchAnime>, String>,
    },
    DandanplaySelected {
        media_id: String,
        selection: DandanplaySelection,
        result: Result<(), String>,
    },
    BangumiDetail {
        anime_id: u64,
        result: Result<BangumiDetail, String>,
    },
}

pub struct LibrarySession {
    pub base_url: String,
    pub pairing_token: Option<String>,
    pub catalog: Option<LibraryCatalog>,
    pub attention: Option<LibraryAttentionDocument>,
    pub attention_error: Option<String>,
    pub loading_attention: bool,
    pub progresses: Vec<PlaybackProgress>,
    pub catalog_error: Option<String>,
    pub loading_catalog: bool,
    /// False while the session is only seeded from the disk cache and the
    /// server is not reachable yet (see [`LibrarySession::from_cache`]).
    /// Network calls are suppressed until [`LibrarySession::attach`] runs.
    pub connected: bool,
    /// True while the displayed catalog came from the disk cache rather than
    /// the server; cleared by the first fresh catalog fetch.
    pub catalog_from_cache: bool,
    /// True while the server reports a background library scan in progress.
    pub server_scanning: bool,
    /// Media files the in-flight server scan has discovered so far.
    pub server_scan_files_seen: Option<u64>,
    /// Bumped whenever fresh (non-cache) catalog or progress data lands, so
    /// the app knows when to persist the session cache.
    pub sync_version: u64,
    /// Bumped every time a new catalog is received. The catalog's own
    /// `indexedAtEpochMs`/item count do not change when only per-item
    /// metadata (recognized anime, poster) is enriched server-side, so UI
    /// caches must key off this instead of catalog content to notice an
    /// enrichment-only refresh (see `LibraryScreen::refresh_series_cache`).
    pub catalog_version: u64,
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
            attention: None,
            attention_error: None,
            loading_attention: false,
            progresses: Vec::new(),
            catalog_error: None,
            loading_catalog: false,
            connected: true,
            catalog_from_cache: false,
            server_scanning: false,
            server_scan_files_seen: None,
            sync_version: 0,
            catalog_version: 0,
            inbox: Arc::new(Mutex::new(Vec::new())),
            egui_context,
        };
        session.refresh_catalog();
        session.refresh_attention();
        session.refresh_progress();
        session.refresh_server_scan();
        session
    }

    /// A session seeded from the disk cache so the library renders instantly
    /// while the server (usually the managed local sidecar) is still coming
    /// up. No network calls run until [`LibrarySession::attach`].
    pub fn from_cache(
        catalog: LibraryCatalog,
        progresses: Vec<PlaybackProgress>,
        egui_context: egui::Context,
    ) -> Self {
        Self {
            base_url: String::new(),
            pairing_token: None,
            catalog: Some(catalog),
            attention: None,
            attention_error: None,
            loading_attention: false,
            progresses,
            catalog_error: None,
            loading_catalog: true,
            connected: false,
            catalog_from_cache: true,
            server_scanning: false,
            server_scan_files_seen: None,
            sync_version: 0,
            catalog_version: 1,
            inbox: Arc::new(Mutex::new(Vec::new())),
            egui_context,
        }
    }

    /// Connects a cache-seeded session to the now-reachable server and kicks
    /// off the refreshes that replace the cached data.
    pub fn attach(&mut self, base_url: String, pairing_token: Option<String>) {
        self.base_url = base_url;
        self.pairing_token = pairing_token;
        self.connected = true;
        self.refresh_catalog();
        self.refresh_attention();
        self.refresh_progress();
        self.refresh_server_scan();
    }

    pub fn refresh_catalog(&mut self) {
        if !self.connected {
            return;
        }
        self.loading_catalog = true;
        self.catalog_error = None;
        let base_url = self.base_url.clone();
        self.spawn(
            move |base| SessionEvent::Catalog(fetch_catalog(&base)),
            base_url,
        );
    }

    pub fn refresh_attention(&mut self) {
        if !self.connected {
            return;
        }
        self.loading_attention = true;
        self.attention_error = None;
        let base_url = self.base_url.clone();
        self.spawn(
            move |base| SessionEvent::Attention(fetch_attention(&base)),
            base_url,
        );
    }

    pub fn refresh_progress(&mut self) {
        if !self.connected {
            return;
        }
        let base_url = self.base_url.clone();
        self.spawn(
            move |base| SessionEvent::ProgressList(fetch_progress_list(&base)),
            base_url,
        );
    }

    /// Polls `/api/server/status` for background-scan state so the UI can
    /// show indexing progress and refresh the catalog when the scan ends.
    pub fn refresh_server_scan(&self) {
        if !self.connected {
            return;
        }
        let base_url = self.base_url.clone();
        self.spawn(
            move |base| {
                let (scanning, files_seen) = fetch_server_status(&base)
                    .ok()
                    .and_then(|body| serde_json::from_str::<serde_json::Value>(&body).ok())
                    .map(|status| {
                        (
                            status["scanning"].as_bool().unwrap_or(false),
                            status["scanFilesSeen"].as_u64(),
                        )
                    })
                    .unwrap_or((false, None));
                SessionEvent::ServerScan {
                    scanning,
                    files_seen,
                }
            },
            base_url,
        );
    }

    /// Looks up saved progress for an item ahead of playback.
    pub fn lookup_resume(&self, media_id: String) {
        if !self.connected {
            return;
        }
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
        if !self.connected {
            return;
        }
        let base_url = self.base_url.clone();
        self.spawn(
            move |base| {
                let load = fetch_server_danmaku(&base, &media_id, force_refresh);
                SessionEvent::Danmaku { media_id, load }
            },
            base_url,
        );
    }

    pub fn repair_attention(&self, request: AttentionRepairRequest) {
        if !self.connected {
            return;
        }
        let base_url = self.base_url.clone();
        self.spawn(
            move |base| {
                let media_id = request.media_id.clone();
                let result = if let (AttentionMappingStatus::Mapped, Some(episode_id)) =
                    (request.mapping_status, request.episode_id)
                {
                    select_dandanplay_match(
                        &base,
                        &request.media_id,
                        &DandanplaySelection {
                            episode_id,
                            anime_id: request.anime_id,
                            anime_title: None,
                            episode_title: None,
                        },
                    )
                } else if request.mapping_status == AttentionMappingStatus::Mapped {
                    Err("cannot safely refresh this mapping; choose Change match first".to_owned())
                } else {
                    fetch_server_danmaku(&base, &request.media_id, false).and_then(|load| {
                        if matches!(load.kind, DanmakuLoadKind::Failed) {
                            Err(load.status)
                        } else {
                            Ok(())
                        }
                    })
                };
                SessionEvent::AttentionRepair { media_id, result }
            },
            base_url,
        );
    }

    /// Lists dandanplay match candidates for a manual match picker.
    pub fn fetch_dandanplay_candidates(&self, media_id: String) {
        if !self.connected {
            return;
        }
        let base_url = self.base_url.clone();
        self.spawn(
            move |base| {
                let result = fetch_dandanplay_candidates(&base, &media_id);
                SessionEvent::DandanplayCandidates { media_id, result }
            },
            base_url,
        );
    }

    /// Searches the dandanplay database by keyword for the match picker
    /// opened on `media_id`.
    pub fn search_dandanplay(&self, media_id: String, keyword: String) {
        if !self.connected {
            return;
        }
        let base_url = self.base_url.clone();
        self.spawn(
            move |base| {
                let result = search_dandanplay(&base, &keyword);
                SessionEvent::DandanplaySearch { media_id, result }
            },
            base_url,
        );
    }

    /// Fetches one anime's dandanplay bangumi profile for the series page.
    pub fn fetch_bangumi_detail(&self, anime_id: u64) {
        if !self.connected {
            return;
        }
        let base_url = self.base_url.clone();
        self.spawn(
            move |base| {
                let result = fetch_bangumi_detail(&base, anime_id);
                SessionEvent::BangumiDetail { anime_id, result }
            },
            base_url,
        );
    }

    /// Pins a media item to a manually chosen dandanplay episode.
    pub fn select_dandanplay_match(&self, media_id: String, selection: DandanplaySelection) {
        if !self.connected {
            return;
        }
        let base_url = self.base_url.clone();
        self.spawn(
            move |base| {
                let result = select_dandanplay_match(&base, &media_id, &selection);
                SessionEvent::DandanplaySelected {
                    media_id,
                    selection,
                    result,
                }
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
        if !self.connected {
            return;
        }
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
        let mut scan_finished = false;
        for event in events {
            match event {
                SessionEvent::Catalog(result) => {
                    self.loading_catalog = false;
                    match result {
                        Ok(catalog) => {
                            self.catalog = Some(catalog);
                            self.catalog_error = None;
                            self.catalog_from_cache = false;
                            self.catalog_version = self.catalog_version.wrapping_add(1);
                            self.sync_version = self.sync_version.wrapping_add(1);
                        }
                        Err(error) => self.catalog_error = Some(error),
                    }
                }
                SessionEvent::Attention(result) => {
                    self.loading_attention = false;
                    match result {
                        Ok(attention) => {
                            self.attention = Some(attention);
                            self.attention_error = None;
                        }
                        Err(error) => self.attention_error = Some(error),
                    }
                }
                SessionEvent::ProgressList(result) => {
                    if let Ok(progresses) = result {
                        self.progresses = progresses;
                        self.sync_version = self.sync_version.wrapping_add(1);
                    }
                }
                SessionEvent::ServerScan {
                    scanning,
                    files_seen,
                } => {
                    if self.server_scanning && !scanning {
                        scan_finished = true;
                    }
                    self.server_scanning = scanning;
                    self.server_scan_files_seen = files_seen;
                }
                other => for_app.push(other),
            }
        }
        // The catalog only changes when the server finishes its background
        // scan, so pick up the final result exactly once.
        if scan_finished {
            self.refresh_catalog();
            self.refresh_progress();
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
            attention: None,
            progresses: Vec::new(),
            catalog_error: None,
            attention_error: None,
            loading_catalog: false,
            loading_attention: false,
            connected: true,
            catalog_from_cache: false,
            server_scanning: false,
            server_scan_files_seen: None,
            sync_version: 0,
            catalog_version: 0,
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

    #[test]
    fn catalog_version_bumps_on_success_and_not_on_error() {
        let mut session = test_session("http://127.0.0.1:1");
        let catalog = LibraryCatalog {
            root_name: "root".to_owned(),
            indexed_at_epoch_ms: 0,
            items: Vec::new(),
        };

        session
            .inbox
            .lock()
            .expect("inbox lock")
            .push(SessionEvent::Catalog(Ok(catalog.clone())));
        session.drain_events();
        assert_eq!(session.catalog_version, 1);

        // A later refresh returning an identical catalog still bumps the
        // version, since the caller's whole point was noticing a refresh
        // happened (unlike catalog content, which may be unchanged when only
        // per-item metadata was enriched server-side).
        session
            .inbox
            .lock()
            .expect("inbox lock")
            .push(SessionEvent::Catalog(Ok(catalog)));
        session.drain_events();
        assert_eq!(session.catalog_version, 2);

        session
            .inbox
            .lock()
            .expect("inbox lock")
            .push(SessionEvent::Catalog(Err("boom".to_owned())));
        session.drain_events();
        assert_eq!(session.catalog_version, 2);
    }

    fn cached_catalog() -> LibraryCatalog {
        serde_json::from_value(serde_json::json!({
            "rootName": "root",
            "indexedAtEpochMs": 0,
            "items": [{"id": "a", "seriesTitle": "S", "episodeTitle": "E1", "relativePath": "S/E1.mkv", "sizeBytes": 1, "mediaType": "video/x-matroska", "streamPath": "/media/a"}]
        }))
        .expect("catalog parses")
    }

    #[test]
    fn cache_seeded_sessions_stay_offline_until_attached() {
        let mut session =
            LibrarySession::from_cache(cached_catalog(), Vec::new(), egui::Context::default());
        assert!(!session.connected);
        assert!(session.catalog_from_cache);
        assert!(session.loading_catalog);
        assert_eq!(session.catalog.as_ref().map(|c| c.items.len()), Some(1));

        // Network calls are suppressed while unattached: nothing may spawn a
        // request against the empty base URL.
        session.refresh_catalog();
        session.refresh_progress();
        session.lookup_resume("a".to_owned());
        session.fetch_danmaku("a".to_owned(), false);
        std::thread::sleep(std::time::Duration::from_millis(30));
        assert!(session.inbox.lock().expect("inbox lock").is_empty());

        session.attach("http://127.0.0.1:1".to_owned(), Some("123456".to_owned()));
        assert!(session.connected);
        assert_eq!(session.base_url, "http://127.0.0.1:1");
        assert_eq!(
            session.stream_url("/media/a"),
            "http://127.0.0.1:1/media/a?token=123456"
        );
    }

    #[test]
    fn fresh_catalog_clears_the_cache_marker_and_bumps_sync_version() {
        let mut session =
            LibrarySession::from_cache(cached_catalog(), Vec::new(), egui::Context::default());
        session.connected = true;
        session
            .inbox
            .lock()
            .expect("inbox lock")
            .push(SessionEvent::Catalog(Ok(cached_catalog())));
        session.drain_events();
        assert!(!session.catalog_from_cache);
        assert_eq!(session.sync_version, 1);
    }

    #[test]
    fn scan_completion_triggers_a_catalog_refresh() {
        let mut session = test_session("http://127.0.0.1:1");
        session
            .inbox
            .lock()
            .expect("inbox lock")
            .push(SessionEvent::ServerScan {
                scanning: true,
                files_seen: Some(12),
            });
        session.drain_events();
        assert!(session.server_scanning);
        assert_eq!(session.server_scan_files_seen, Some(12));
        assert!(!session.loading_catalog);

        session
            .inbox
            .lock()
            .expect("inbox lock")
            .push(SessionEvent::ServerScan {
                scanning: false,
                files_seen: None,
            });
        session.drain_events();
        assert!(!session.server_scanning);
        // The flip to "not scanning" refreshes the catalog to pick up the
        // scan result exactly once.
        assert!(session.loading_catalog);
    }
}
