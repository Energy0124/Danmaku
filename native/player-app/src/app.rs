//! Player window: chrome, video surface, and fade-over-video controls.

use std::{
    path::{Path, PathBuf},
    sync::{Arc, Mutex},
    time::{Duration, Instant},
};

use eframe::egui::{
    self, Align2, Color32, FontId, Frame, Rect, RichText, Sense, StrokeKind, ViewportCommand, pos2,
    vec2,
};
use egui_glow::CallbackFn;

use crate::{
    branding::Branding,
    cli::Cli,
    clock::OverlayClock,
    danmaku::{
        DanmakuDisplaySettings, DanmakuLayout, DanmakuLoad, DanmakuLoadKind, estimate_text_width,
        fetch_server_danmaku, load_local_danmaku,
    },
    discovery::{DEFAULT_DISCOVERY_PORT, DiscoveryListener},
    hosting::{LocalConnection, LocalServerSupervisor},
    icons::{Icon, paint_icon},
    library::PlaybackProgress,
    localization::Strings,
    posters::PosterCache,
    preferences::{PlayerPreferences, PreferenceStore},
    screens::{
        ConnectAction, ConnectRequest, ConnectScreen, LibraryAction, LibraryScreen, SettingsAction,
        show_settings,
    },
    session::{LibrarySession, SessionEvent},
    smoke::SmokeReport,
    theme::{self, metrics, palette, typography},
    tracks::{TrackInventory, TrackKind, read_track_inventory, selection_command},
    video::{RenderCounters, SharedVideoRenderer, VideoRenderer},
};

const PROPERTY_REFRESH_INTERVAL: Duration = Duration::from_millis(180);
const TRACK_REFRESH_INTERVAL: Duration = Duration::from_millis(1_000);
const PROGRESS_UPLOAD_INTERVAL: Duration = Duration::from_secs(15);
const PLAYBACK_RATES: [f64; 6] = [0.5, 0.75, 1.0, 1.25, 1.5, 2.0];

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum AppScreen {
    Connect,
    Library,
    Playback,
    Settings,
}

pub struct PlayerApp {
    cli: Cli,
    display_title: String,
    renderer: Option<SharedVideoRenderer>,
    counters: Arc<Mutex<RenderCounters>>,
    report_slot: Arc<Mutex<Option<SmokeReport>>>,
    overlay_clock: OverlayClock,
    snapshot: PlaybackSnapshot,
    tracks: TrackInventory,
    danmaku: DanmakuLoad,
    danmaku_layout: DanmakuLayout,
    danmaku_settings: DanmakuDisplaySettings,
    last_property_refresh: Instant,
    last_track_refresh: Instant,
    last_pointer_activity: Instant,
    last_normal_inner_rect: Option<Rect>,
    last_normal_outer_rect: Option<Rect>,
    fullscreen: bool,
    smoke_started: Instant,
    // Library mode.
    screen: AppScreen,
    connect_screen: ConnectScreen,
    library_screen: LibraryScreen,
    session: Option<LibrarySession>,
    discovery: Option<DiscoveryListener>,
    posters: PosterCache,
    active_media_id: Option<String>,
    pending_play_media_id: Option<String>,
    auto_next: bool,
    eof_handled: bool,
    last_progress_upload: Instant,
    qa_play_first_pending: bool,
    preferences: PlayerPreferences,
    preference_store: PreferenceStore,
    saved_preferences: PlayerPreferences,
    settings_return: AppScreen,
    local_host: Option<LocalServerSupervisor>,
    window_effects_applied: bool,
    branding: Branding,
}

impl PlayerApp {
    pub fn new(
        creation_context: &eframe::CreationContext<'_>,
        cli: Cli,
        report_slot: Arc<Mutex<Option<SmokeReport>>>,
    ) -> Result<Self, Box<dyn std::error::Error + Send + Sync>> {
        if creation_context.gl.is_none() {
            return Err("eframe did not provide a glow context".into());
        }

        let counters = Arc::new(Mutex::new(RenderCounters::default()));
        let branding = Branding::load(&creation_context.egui_ctx)?;
        let preference_store = PreferenceStore::for_current_user();
        let mut preferences = preference_store.load();
        let saved_preferences = preferences.clone();
        let local_host = (cli.media.is_none() && cli.server_url.is_none()).then(|| {
            LocalServerSupervisor::new(
                &preferences.local_library_roots,
                creation_context.egui_ctx.clone(),
            )
        });
        if let Some(volume) = cli.volume_percent {
            preferences.volume_percent = volume;
        }
        if cli.auto_next {
            preferences.auto_next = true;
        }
        let mut danmaku_settings = preferences.danmaku_settings();
        if let Some(value) = cli.danmaku_opacity {
            danmaku_settings.opacity = value;
        }
        if let Some(value) = cli.danmaku_speed {
            danmaku_settings.speed = value;
        }
        if let Some(value) = cli.danmaku_density {
            danmaku_settings.density = value;
        }
        if let Some(value) = cli.danmaku_lanes {
            danmaku_settings.max_lanes = value;
        }
        danmaku_settings = danmaku_settings.sanitized();
        preferences.update_danmaku(&danmaku_settings);
        let mut danmaku = DanmakuLoad::none();
        let mut renderer = None;
        let mut display_title = "Danmaku Player".to_owned();

        // Direct playback keeps its eager startup; library mode defers the
        // renderer until the first episode plays.
        if let Some(media) = cli.media.clone() {
            danmaku = load_danmaku(&cli);
            let video_renderer = VideoRenderer::create(
                &media,
                cli.start_position_s,
                Some(preferences.volume_percent),
                &creation_context.egui_ctx,
                Arc::clone(&counters),
            )
            .map_err(|error| format!("mpv initialization failed: {error}"))?;
            if let Some(ass_path) = danmaku.ass_path.clone() {
                let source = ass_path.to_string_lossy();
                if let Err(error) = video_renderer.command(&[
                    "sub-add",
                    source.as_ref(),
                    "select",
                    "Danmaku ASS overlay",
                ]) {
                    danmaku = DanmakuLoad::failed(format!("failed to attach ASS overlay: {error}"));
                }
            }
            let speed = format!("{:.3}", preferences.playback_rate);
            let _ = video_renderer.command(&["set", "speed", &speed]);
            renderer = Some(Arc::new(Mutex::new(video_renderer)));
            display_title = cli
                .title
                .clone()
                .unwrap_or_else(|| media_display_title(&media));
        }

        let mut connect_screen = ConnectScreen::default();
        if let Some(url) = preferences.last_server_url.clone() {
            connect_screen.manual_url = url;
        }
        if let Some(root) = preferences.local_library_roots.first() {
            connect_screen.local_library_root = root.clone();
        }
        let mut posters = PosterCache::new(creation_context.egui_ctx.clone());
        let mut session = None;
        let mut discovery = None;
        let screen = if cli.media.is_some() {
            AppScreen::Playback
        } else if let Some(server_url) = cli.server_url.clone() {
            preferences.last_server_url = Some(server_url.clone());
            posters.set_base_url(Some(server_url.clone()));
            session = Some(LibrarySession::connect(
                server_url,
                cli.pairing_token.clone(),
                creation_context.egui_ctx.clone(),
            ));
            AppScreen::Library
        } else {
            match DiscoveryListener::start(DEFAULT_DISCOVERY_PORT) {
                Ok(listener) => discovery = Some(listener),
                Err(error) => eprintln!("discovery unavailable: {error}"),
            }
            AppScreen::Connect
        };

        let now = Instant::now();
        let mut overlay_clock = OverlayClock::new(now);
        if let Some(start_position_s) = cli.start_position_s {
            overlay_clock.seek(start_position_s, now);
        }
        let auto_next = preferences.auto_next;
        let qa_play_first_pending = cli.qa_play_first;

        Ok(Self {
            cli,
            display_title,
            renderer,
            counters,
            report_slot,
            overlay_clock,
            snapshot: PlaybackSnapshot::default(),
            tracks: TrackInventory::default(),
            danmaku,
            danmaku_layout: DanmakuLayout::default(),
            danmaku_settings,
            last_property_refresh: now - PROPERTY_REFRESH_INTERVAL,
            last_track_refresh: now - TRACK_REFRESH_INTERVAL,
            last_pointer_activity: now,
            last_normal_inner_rect: None,
            last_normal_outer_rect: None,
            fullscreen: false,
            smoke_started: now,
            screen,
            connect_screen,
            library_screen: LibraryScreen::default(),
            session,
            discovery,
            posters,
            active_media_id: None,
            pending_play_media_id: None,
            auto_next,
            eof_handled: false,
            last_progress_upload: now,
            qa_play_first_pending,
            preferences,
            preference_store,
            saved_preferences,
            settings_return: AppScreen::Library,
            local_host,
            window_effects_applied: false,
            branding,
        })
    }

    fn handle_shortcuts(&mut self, ctx: &egui::Context, now: Instant) {
        let wants_text_input = ctx.wants_keyboard_input();
        if wants_text_input {
            return;
        }
        let (
            toggle_fullscreen,
            toggle_danmaku,
            escape,
            toggle_pause,
            seek_back,
            seek_forward,
            volume_up,
            volume_down,
            toggle_mute,
        ) = ctx.input(|input| {
            (
                input.key_pressed(egui::Key::F),
                input.key_pressed(egui::Key::D),
                input.key_pressed(egui::Key::Escape),
                input.key_pressed(egui::Key::Space),
                input.key_pressed(egui::Key::ArrowLeft),
                input.key_pressed(egui::Key::ArrowRight),
                input.key_pressed(egui::Key::ArrowUp),
                input.key_pressed(egui::Key::ArrowDown),
                input.key_pressed(egui::Key::M),
            )
        });

        if toggle_fullscreen {
            self.toggle_fullscreen(ctx);
        }
        if toggle_danmaku && self.danmaku.kind != DanmakuLoadKind::Ass {
            self.danmaku_settings.enabled = !self.danmaku_settings.enabled;
        }
        if escape {
            if self.fullscreen {
                self.toggle_fullscreen(ctx);
            } else if self.session.is_some() {
                self.back_to_library();
            }
        }
        if toggle_pause {
            self.toggle_pause(now);
        }
        if seek_back {
            self.seek_relative(-5.0, now);
        }
        if seek_forward {
            self.seek_relative(5.0, now);
        }
        if volume_up {
            self.set_volume(self.snapshot.volume_percent + 5.0);
        }
        if volume_down {
            self.set_volume(self.snapshot.volume_percent - 5.0);
        }
        if toggle_mute {
            self.toggle_mute();
        }
    }

    fn toggle_pause(&mut self, now: Instant) {
        if self.run_mpv_command(&["cycle", "pause"]) {
            let paused = !self.snapshot.paused;
            self.snapshot.paused = paused;
            self.overlay_clock.set_paused(paused, now);
            if paused {
                self.upload_active_progress(true);
            }
        }
    }

    fn seek_relative(&mut self, delta_s: f64, now: Instant) {
        let position_s = self.overlay_clock.position_at(now) + delta_s;
        self.seek_to(position_s, now);
    }

    fn seek_to(&mut self, position_s: f64, now: Instant) {
        let duration_s = if self.snapshot.duration_s.is_finite() && self.snapshot.duration_s > 0.0 {
            self.snapshot.duration_s
        } else {
            3600.0
        };
        let position_s = position_s.clamp(0.0, duration_s);
        let value = format!("{position_s:.3}");
        if self.run_mpv_command(&["set", "time-pos", &value]) {
            self.snapshot.position_s = position_s;
            self.overlay_clock.seek(position_s, now);
            self.upload_active_progress(true);
        }
    }

    fn set_playback_rate(&mut self, rate: f64, now: Instant) {
        let value = format!("{rate:.3}");
        if self.run_mpv_command(&["set", "speed", &value]) {
            self.snapshot.speed = rate;
            self.overlay_clock.set_rate(rate, now);
            self.preferences.playback_rate = rate;
        }
    }

    fn set_volume(&mut self, volume_percent: f64) {
        let volume_percent = volume_percent.clamp(0.0, 130.0);
        let value = format!("{volume_percent:.0}");
        if self.run_mpv_command(&["set", "volume", &value]) {
            self.snapshot.volume_percent = volume_percent;
            self.preferences.volume_percent = volume_percent.round() as u8;
        }
    }

    fn toggle_mute(&mut self) {
        if self.run_mpv_command(&["cycle", "mute"]) {
            self.snapshot.muted = !self.snapshot.muted;
        }
    }

    fn select_track(&mut self, kind: TrackKind, id: Option<u64>) {
        let command = selection_command(kind, id);
        let args: Vec<&str> = command.iter().map(String::as_str).collect();
        if self.run_mpv_command(&args) {
            // Optimistically update selection; the next track refresh
            // confirms it from mpv.
            let tracks = match kind {
                TrackKind::Audio => &mut self.tracks.audio,
                TrackKind::Subtitle => &mut self.tracks.subtitles,
            };
            for track in tracks {
                track.selected = Some(track.id) == id;
            }
        }
    }

    fn run_mpv_command(&self, args: &[&str]) -> bool {
        matches!(
            self.with_renderer(|renderer| renderer.command(args)),
            Some(Ok(()))
        )
    }

    fn toggle_fullscreen(&mut self, ctx: &egui::Context) {
        if self.fullscreen {
            ctx.send_viewport_cmd(ViewportCommand::Fullscreen(false));
            if let Some(outer) = self.last_normal_outer_rect {
                ctx.send_viewport_cmd(ViewportCommand::OuterPosition(outer.min));
            }
            if let Some(inner) = self.last_normal_inner_rect {
                ctx.send_viewport_cmd(ViewportCommand::InnerSize(inner.size()));
            }
            self.fullscreen = false;
        } else {
            let (inner, outer) = ctx.input(|input| {
                let viewport = input.viewport();
                (viewport.inner_rect, viewport.outer_rect)
            });
            self.last_normal_inner_rect = inner;
            self.last_normal_outer_rect = outer;
            ctx.send_viewport_cmd(ViewportCommand::Fullscreen(true));
            self.fullscreen = true;
        }
    }

    fn record_activity(&mut self, ctx: &egui::Context) {
        let active = ctx.input(|input| {
            input.pointer.delta() != egui::Vec2::ZERO
                || input.pointer.any_down()
                || !input.keys_down.is_empty()
        });
        if active {
            self.last_pointer_activity = Instant::now();
        }
    }

    fn handle_dropped_danmaku(&mut self, ctx: &egui::Context) {
        let path = ctx.input(|input| {
            input
                .raw
                .dropped_files
                .iter()
                .filter_map(|file| file.path.clone())
                .find(|path| is_danmaku_path(path))
        });
        let Some(path) = path else {
            return;
        };
        let load = match load_local_danmaku(&path) {
            Ok(load) => load,
            Err(error) => {
                self.danmaku.status = format!(
                    "{}: {error}",
                    Strings::new(self.preferences.language).attachment_failed()
                );
                return;
            }
        };
        if self.danmaku.kind == DanmakuLoadKind::Ass {
            self.run_mpv_command(&["sub-remove"]);
        }
        if let Some(ass_path) = &load.ass_path {
            let source = ass_path.to_string_lossy();
            if !self.run_mpv_command(&["sub-add", source.as_ref(), "select", "Danmaku ASS overlay"])
            {
                self.danmaku.status = format!(
                    "{}: mpv rejected ASS",
                    Strings::new(self.preferences.language).attachment_failed()
                );
                return;
            }
        } else {
            self.danmaku_settings.enabled = true;
        }
        self.danmaku = load;
    }

    fn refresh_snapshot(&mut self, now: Instant) {
        if now.saturating_duration_since(self.last_property_refresh) < PROPERTY_REFRESH_INTERVAL {
            return;
        }
        self.last_property_refresh = now;
        let previous = self.snapshot.clone();
        let readback = self.with_renderer(|renderer| {
            let observed_position_s = parse_f64(renderer.property_string("time-pos"));
            let paused = renderer
                .property_string("pause")
                .map(|value| value == "yes" || value == "true")
                .unwrap_or(previous.paused);
            let speed = parse_f64(renderer.property_string("speed")).unwrap_or(previous.speed);
            PlaybackReadback {
                observed_position_s,
                snapshot: PlaybackSnapshot {
                    position_s: observed_position_s.unwrap_or(previous.position_s),
                    duration_s: parse_f64(renderer.property_string("duration"))
                        .unwrap_or(previous.duration_s),
                    paused,
                    speed,
                    volume_percent: parse_f64(renderer.property_string("volume"))
                        .unwrap_or(previous.volume_percent),
                    muted: renderer
                        .property_string("mute")
                        .map(|value| value == "yes" || value == "true")
                        .unwrap_or(previous.muted),
                    estimated_vf_fps: renderer.property_string("estimated-vf-fps"),
                    frame_drop_count: parse_u64(renderer.property_string("frame-drop-count")),
                    hwdec_current: renderer.property_string("hwdec-current"),
                    cache_duration_s: parse_f64(renderer.property_string("demuxer-cache-duration")),
                    eof_reached: renderer
                        .property_string("eof-reached")
                        .map(|value| value == "yes" || value == "true")
                        .unwrap_or(false),
                    render_error: renderer.last_render_error().map(str::to_owned),
                },
            }
        });
        if let Some(readback) = readback {
            self.snapshot = readback.snapshot;
            if let Some(position_s) = readback.observed_position_s {
                self.overlay_clock.observe_time_pos(
                    position_s,
                    self.snapshot.speed,
                    self.snapshot.paused,
                    now,
                );
            } else if self.overlay_clock.paused() != self.snapshot.paused {
                self.overlay_clock.set_paused(self.snapshot.paused, now);
                self.overlay_clock.set_rate(self.snapshot.speed, now);
            } else {
                self.overlay_clock.set_rate(self.snapshot.speed, now);
            }
        }
    }

    fn refresh_tracks(&mut self, now: Instant) {
        if now.saturating_duration_since(self.last_track_refresh) < TRACK_REFRESH_INTERVAL {
            return;
        }
        self.last_track_refresh = now;
        if let Some(tracks) = self
            .with_renderer(|renderer| read_track_inventory(|name| renderer.property_string(name)))
        {
            self.tracks = tracks;
        }
    }

    fn with_renderer<T>(&self, f: impl FnOnce(&mut VideoRenderer) -> T) -> Option<T> {
        self.renderer
            .as_ref()?
            .lock()
            .ok()
            .map(|mut renderer| f(&mut renderer))
    }

    // -----------------------------------------------------------------
    // Library mode
    // -----------------------------------------------------------------

    fn handle_session_events(&mut self, ctx: &egui::Context) {
        let Some(mut session) = self.session.take() else {
            return;
        };
        let events = session.drain_events();
        self.session = Some(session);
        for event in events {
            match event {
                SessionEvent::ResumeLookup { media_id, progress } => {
                    if self.pending_play_media_id.as_deref() == Some(media_id.as_str()) {
                        self.pending_play_media_id = None;
                        let resume_s = progress
                            .and_then(|progress| {
                                progress.resume_position_ms(
                                    crate::library::MINIMUM_RESUME_POSITION_MS,
                                    crate::library::MINIMUM_REMAINING_MS,
                                )
                            })
                            .map(|position_ms| position_ms as f64 / 1000.0);
                        self.start_library_playback(ctx, &media_id, resume_s);
                    }
                }
                SessionEvent::Danmaku { media_id, load } => {
                    if self.active_media_id.as_deref() == Some(media_id.as_str()) {
                        self.danmaku = load.unwrap_or_else(DanmakuLoad::failed);
                    }
                }
                _ => {}
            }
        }
    }

    fn connect_to_server(&mut self, ctx: &egui::Context, request: ConnectRequest) {
        self.preferences.last_server_url = Some(request.base_url.clone());
        self.posters.set_base_url(Some(request.base_url.clone()));
        self.session = Some(LibrarySession::connect(
            request.base_url,
            request.pairing_token,
            ctx.clone(),
        ));
        self.connect_screen.error = None;
        self.screen = AppScreen::Library;
    }

    fn connect_to_local_server(&mut self, ctx: &egui::Context, connection: LocalConnection) {
        self.connect_to_server(
            ctx,
            ConnectRequest {
                base_url: connection.base_url,
                pairing_token: connection.pairing_token,
            },
        );
    }

    fn prepare_for_host_transition(&mut self) {
        self.upload_active_progress(true);
        self.run_mpv_command(&["stop"]);
        self.active_media_id = None;
        self.session = None;
        self.posters.set_base_url(None);
        self.screen = AppScreen::Connect;
    }

    fn local_roots(&self) -> Vec<PathBuf> {
        self.preferences
            .local_library_roots
            .iter()
            .map(PathBuf::from)
            .collect()
    }

    fn request_library_play(&mut self, media_id: String) {
        if let Some(session) = &self.session {
            session.lookup_resume(media_id.clone());
            self.pending_play_media_id = Some(media_id);
        }
    }

    fn start_library_playback(
        &mut self,
        ctx: &egui::Context,
        media_id: &str,
        resume_position_s: Option<f64>,
    ) {
        let Some(session) = &self.session else {
            return;
        };
        let Some(catalog) = &session.catalog else {
            return;
        };
        let Some(item) = catalog.item(media_id) else {
            return;
        };
        let stream_url = session.stream_url(&item.stream_path);
        let title = format!("{} - {}", item.series_title, item.episode_title);
        let media_id = media_id.to_owned();

        if self.renderer.is_none() {
            match VideoRenderer::create(
                &stream_url,
                resume_position_s,
                Some(self.preferences.volume_percent),
                ctx,
                Arc::clone(&self.counters),
            ) {
                Ok(renderer) => {
                    let speed = format!("{:.3}", self.preferences.playback_rate);
                    let _ = renderer.command(&["set", "speed", &speed]);
                    self.renderer = Some(Arc::new(Mutex::new(renderer)));
                }
                Err(error) => {
                    self.connect_screen.error = Some(format!("playback failed: {error}"));
                    return;
                }
            }
        } else {
            let start_value = resume_position_s
                .map(|seconds| format!("{seconds:.3}"))
                .unwrap_or_else(|| "none".to_owned());
            self.run_mpv_command(&["set", "start", &start_value]);
            self.run_mpv_command(&["loadfile", &stream_url, "replace"]);
            self.run_mpv_command(&["set", "pause", "no"]);
        }

        let now = Instant::now();
        self.display_title = title;
        self.active_media_id = Some(media_id.clone());
        self.snapshot = PlaybackSnapshot::default();
        self.tracks = TrackInventory::default();
        self.overlay_clock = OverlayClock::new(now);
        if let Some(resume_position_s) = resume_position_s {
            self.overlay_clock.seek(resume_position_s, now);
        }
        self.danmaku = DanmakuLoad::none();
        if let Some(session) = &self.session {
            session.fetch_danmaku(media_id, self.cli.danmaku_force_refresh);
        }
        self.eof_handled = false;
        self.last_progress_upload = now;
        self.screen = AppScreen::Playback;
    }

    fn play_adjacent_episode(&mut self, direction: i64) {
        let Some(session) = &self.session else {
            return;
        };
        let Some(catalog) = &session.catalog else {
            return;
        };
        let Some(active) = self.active_media_id.as_deref() else {
            return;
        };
        let neighbor = if direction < 0 {
            catalog.previous_item(active)
        } else {
            catalog.next_item(active)
        };
        if let Some(item) = neighbor {
            let media_id = item.id.clone();
            self.upload_active_progress(true);
            self.request_library_play(media_id);
        }
    }

    fn back_to_library(&mut self) {
        if self.session.is_none() {
            return;
        }
        self.upload_active_progress(true);
        self.run_mpv_command(&["stop"]);
        self.active_media_id = None;
        self.danmaku = DanmakuLoad::none();
        self.screen = AppScreen::Library;
        if let Some(session) = &mut self.session {
            session.refresh_progress();
        }
    }

    fn disconnect(&mut self) {
        self.upload_active_progress(true);
        self.run_mpv_command(&["stop"]);
        self.active_media_id = None;
        self.session = None;
        self.posters.set_base_url(None);
        self.screen = AppScreen::Connect;
        if self.discovery.is_none() {
            match DiscoveryListener::start(DEFAULT_DISCOVERY_PORT) {
                Ok(listener) => self.discovery = Some(listener),
                Err(error) => eprintln!("discovery unavailable: {error}"),
            }
        }
    }

    /// Uploads playback progress for the active library item. Uploads are
    /// throttled unless `force` is set (pause, seek, episode switch, exit).
    fn upload_active_progress(&mut self, force: bool) {
        let Some(media_id) = self.active_media_id.clone() else {
            return;
        };
        if self.session.is_none() {
            return;
        }
        let now = Instant::now();
        if !force
            && now.saturating_duration_since(self.last_progress_upload) < PROGRESS_UPLOAD_INTERVAL
        {
            return;
        }
        let position_ms = (self.snapshot.position_s.max(0.0) * 1000.0) as i64;
        if position_ms <= 0 {
            return;
        }
        self.last_progress_upload = now;
        let duration_ms = (self.snapshot.duration_s.is_finite() && self.snapshot.duration_s > 0.0)
            .then_some((self.snapshot.duration_s * 1000.0) as i64);
        let updated_at_epoch_ms = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|elapsed| elapsed.as_millis() as i64)
            .unwrap_or(0);
        if let Some(session) = &mut self.session {
            session.upload_progress(PlaybackProgress {
                media_id,
                position_ms,
                duration_ms,
                updated_at_epoch_ms,
            });
        }
    }

    fn handle_end_of_file(&mut self) {
        if !self.snapshot.eof_reached || self.eof_handled {
            return;
        }
        self.eof_handled = true;
        if self.session.is_none() || self.active_media_id.is_none() {
            return;
        }
        // Record the position as watched, then advance when enabled.
        self.upload_active_progress(true);
        if self.auto_next {
            self.play_adjacent_episode(1);
        }
    }

    fn show_window_title_bar(&mut self, ctx: &egui::Context) {
        if self.fullscreen {
            return;
        }
        egui::TopBottomPanel::top("app_window_title_bar")
            .exact_height(38.0)
            .frame(Frame::NONE.fill(palette::BG_NAV))
            .show(ctx, |ui| {
                let full = ui.max_rect();
                let controls_width = 138.0;
                let drag_rect = Rect::from_min_max(
                    full.min,
                    pos2(full.right() - controls_width, full.bottom()),
                );
                let drag_response = ui.interact(
                    drag_rect,
                    ui.id().with("window_drag"),
                    Sense::click_and_drag(),
                );
                if drag_response.drag_started() {
                    ctx.send_viewport_cmd(ViewportCommand::StartDrag);
                }
                if drag_response.double_clicked() {
                    let maximized = ctx.input(|input| input.viewport().maximized.unwrap_or(false));
                    ctx.send_viewport_cmd(ViewportCommand::Maximized(!maximized));
                }

                let brand_center = pos2(full.left() + 20.0, full.center().y);
                ui.painter().image(
                    self.branding.icon.id(),
                    Rect::from_center_size(brand_center, vec2(28.0, 28.0)),
                    Rect::from_min_max(pos2(0.07, 0.07), pos2(0.93, 0.93)),
                    Color32::WHITE,
                );
                ui.painter().text(
                    pos2(full.left() + 40.0, full.center().y),
                    Align2::LEFT_CENTER,
                    "Danmaku",
                    typography::body(),
                    palette::TEXT_PRIMARY,
                );

                let controls_rect = Rect::from_min_max(
                    pos2(full.right() - controls_width, full.top()),
                    full.right_bottom(),
                );
                ui.scope_builder(egui::UiBuilder::new().max_rect(controls_rect), |ui| {
                    ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                        if window_control_button(ui, Icon::Close, true).clicked() {
                            ctx.send_viewport_cmd(ViewportCommand::Close);
                        }
                        let maximized =
                            ctx.input(|input| input.viewport().maximized.unwrap_or(false));
                        if window_control_button(ui, Icon::Maximize, false).clicked() {
                            ctx.send_viewport_cmd(ViewportCommand::Maximized(!maximized));
                        }
                        if window_control_button(ui, Icon::Minimize, false).clicked() {
                            ctx.send_viewport_cmd(ViewportCommand::Minimized(true));
                        }
                    });
                });
            });
    }

    fn show_video(&mut self, ctx: &egui::Context, now: Instant, overlay_position_s: f64) {
        egui::CentralPanel::default()
            .frame(Frame::NONE.fill(palette::VIDEO_BACKDROP))
            .show(ctx, |ui| {
                let rect = ui.available_rect_before_wrap();
                let response = ui.allocate_rect(rect, Sense::click_and_drag());
                if response.double_clicked() {
                    self.toggle_fullscreen(ctx);
                }

                if let Some(renderer) = &self.renderer {
                    let renderer = Arc::clone(renderer);
                    ui.painter().add(egui::PaintCallback {
                        rect,
                        callback: Arc::new(CallbackFn::new(move |info, painter| {
                            if let Ok(mut renderer) = renderer.lock() {
                                renderer.render(info, painter);
                            }
                        })),
                    });
                }

                let active_danmaku = self.paint_danmaku(ui, rect, overlay_position_s);
                if let Some(error) = self.snapshot.render_error.clone() {
                    ui.painter().text(
                        rect.left_top() + vec2(metrics::OVERLAY_PADDING, metrics::OVERLAY_PADDING),
                        Align2::LEFT_TOP,
                        error,
                        typography::caption(),
                        palette::DANGER,
                    );
                }

                self.show_controls(ui, rect, now, overlay_position_s, active_danmaku);
            });
    }

    fn paint_danmaku(&self, ui: &egui::Ui, video_rect: Rect, overlay_position_s: f64) -> usize {
        if self.danmaku.track.is_empty() {
            return 0;
        }
        let comments = self.danmaku_layout.visible_comments(
            &self.danmaku.track,
            overlay_position_s.max(0.0) * 1000.0,
            video_rect.width(),
            video_rect.height(),
            &self.danmaku_settings,
        );
        let painter = ui.painter().with_clip_rect(video_rect);
        let mut painted = 0;
        for comment in comments {
            let position = video_rect.min + vec2(comment.x, comment.y);
            let width = estimate_text_width(&comment.event.text, comment.font_px);
            if position.x > video_rect.right() || position.x + width < video_rect.left() {
                continue;
            }
            let color = danmaku_color(comment.style.color_argb, comment.opacity);
            let outline_alpha = color.a().saturating_sub(36);
            let outline = Color32::from_rgba_unmultiplied(0, 0, 0, outline_alpha);
            let font = FontId::proportional(comment.font_px);
            for offset in [
                vec2(1.0, 0.0),
                vec2(-1.0, 0.0),
                vec2(0.0, 1.0),
                vec2(0.0, -1.0),
            ] {
                painter.text(
                    position + offset,
                    Align2::LEFT_TOP,
                    &comment.event.text,
                    font.clone(),
                    outline,
                );
            }
            painter.text(position, Align2::LEFT_TOP, &comment.event.text, font, color);
            painted += 1;
        }
        painted
    }

    fn controls_alpha(&self) -> f32 {
        if self.snapshot.paused {
            return 1.0;
        }
        theme::fade_alpha(
            self.last_pointer_activity.elapsed().as_secs_f32(),
            theme::CHROME_HOLD_SECONDS,
            theme::CHROME_FADE_SECONDS,
        )
    }

    /// Title lines for the playback chrome: series and episode when a library
    /// item is active, otherwise the direct-media display title.
    fn playback_titles(&self) -> (String, Option<String>) {
        if let (Some(session), Some(active)) = (&self.session, self.active_media_id.as_deref())
            && let Some(catalog) = &session.catalog
            && let Some(item) = catalog.items.iter().find(|item| item.id == active)
        {
            return (item.series_title.clone(), Some(item.episode_title.clone()));
        }
        (self.display_title.clone(), None)
    }

    /// The upcoming library episode, for the "Next:" preview card.
    fn upcoming_episode(&self) -> Option<crate::library::MediaItem> {
        let session = self.session.as_ref()?;
        let catalog = session.catalog.as_ref()?;
        let active = self.active_media_id.as_deref()?;
        catalog.next_item(active).cloned()
    }

    fn show_controls(
        &mut self,
        ui: &mut egui::Ui,
        video_rect: Rect,
        now: Instant,
        overlay_position_s: f64,
        active_danmaku: usize,
    ) {
        let strings = Strings::new(self.preferences.language);
        let alpha = self.controls_alpha();
        if alpha <= 0.02 {
            return;
        }

        // Title band: soft gradient instead of a hard box, per the mockup.
        let band_rect = Rect::from_min_max(
            video_rect.left_top(),
            pos2(video_rect.right(), video_rect.top() + 92.0),
        );
        let mut band = egui::Mesh::default();
        let band_base = band.vertices.len() as u32;
        let band_color = Color32::from_rgba_premultiplied(4, 6, 10, (200.0 * alpha) as u8);
        band.colored_vertex(band_rect.left_top(), band_color);
        band.colored_vertex(band_rect.right_top(), band_color);
        band.colored_vertex(band_rect.left_bottom(), Color32::TRANSPARENT);
        band.colored_vertex(band_rect.right_bottom(), Color32::TRANSPARENT);
        band.add_triangle(band_base, band_base + 1, band_base + 2);
        band.add_triangle(band_base + 2, band_base + 1, band_base + 3);
        ui.painter().add(egui::Shape::mesh(band));

        let (title_main, title_sub) = self.playback_titles();
        ui.scope_builder(
            egui::UiBuilder::new().max_rect(band_rect.shrink2(vec2(20.0, 12.0))),
            |ui| {
                ui.multiply_opacity(alpha);
                ui.horizontal(|ui| {
                    if self.session.is_some()
                        && playback_icon_button(ui, Icon::Back, strings.library(), false).clicked()
                    {
                        self.back_to_library();
                    }
                    ui.add_space(8.0);
                    ui.vertical(|ui| {
                        ui.spacing_mut().item_spacing.y = 2.0;
                        ui.label(
                            RichText::new(&title_main)
                                .font(typography::title())
                                .strong()
                                .color(theme::text_primary_faded(alpha)),
                        );
                        if let Some(subtitle) = &title_sub {
                            ui.label(
                                RichText::new(subtitle)
                                    .font(typography::caption())
                                    .color(palette::TEXT_MUTED),
                            );
                        }
                    });
                });
            },
        );

        let rect = Rect::from_min_max(
            pos2(
                video_rect.left() + metrics::GUTTER,
                video_rect.bottom() - metrics::CONTROL_BAR_HEIGHT - metrics::GUTTER,
            ),
            pos2(
                video_rect.right() - metrics::GUTTER,
                video_rect.bottom() - metrics::GUTTER,
            ),
        );

        self.show_next_episode_card(ui, rect, alpha);

        ui.painter()
            .rect_filled(rect, 14.0, theme::overlay_fill(alpha));
        ui.painter().rect_stroke(
            rect,
            14.0,
            theme::overlay_outline(alpha),
            StrokeKind::Inside,
        );

        let content = rect.shrink(metrics::OVERLAY_PADDING);
        ui.scope_builder(egui::UiBuilder::new().max_rect(content), |ui| {
            ui.set_clip_rect(rect);
            ui.multiply_opacity(alpha);

            // Full-width seek bar with a round thumb, visually primary.
            let duration = self.snapshot.duration_s.max(1.0);
            let seek_rect = Rect::from_min_size(content.min, vec2(content.width(), 18.0));
            let fraction = (overlay_position_s.clamp(0.0, duration) / duration) as f32;
            if let Some(new_fraction) =
                media_slider(ui, "playback_seek", seek_rect, fraction, alpha)
            {
                self.seek_to(new_fraction as f64 * duration, now);
            }

            let row = Rect::from_min_max(
                pos2(content.left(), content.top() + 26.0),
                content.right_bottom(),
            );

            // Left cluster: time, then volume.
            ui.scope_builder(egui::UiBuilder::new().max_rect(row), |ui| {
                ui.horizontal_centered(|ui| {
                    ui.label(
                        RichText::new(format!(
                            "{} / {}",
                            format_time(overlay_position_s),
                            format_time(self.snapshot.duration_s)
                        ))
                        .font(typography::body())
                        .color(palette::TEXT_SECONDARY),
                    );
                    ui.add_space(14.0);
                    let mute_label = if self.snapshot.muted {
                        strings.unmute()
                    } else {
                        strings.mute()
                    };
                    if playback_icon_button(
                        ui,
                        if self.snapshot.muted {
                            Icon::Muted
                        } else {
                            Icon::Volume
                        },
                        mute_label,
                        false,
                    )
                    .clicked()
                    {
                        self.toggle_mute();
                    }
                    let volume_rect =
                        Rect::from_min_size(ui.cursor().min + vec2(0.0, 11.0), vec2(96.0, 18.0));
                    ui.allocate_rect(volume_rect, egui::Sense::hover());
                    let volume_fraction = (self.snapshot.volume_percent / 130.0) as f32;
                    if let Some(new_fraction) =
                        media_slider(ui, "playback_volume", volume_rect, volume_fraction, alpha)
                    {
                        self.set_volume(new_fraction as f64 * 130.0);
                    }
                });
            });

            // Center transport cluster.
            let transport_width = 5.0 * 44.0 + 24.0;
            let center_rect = Rect::from_center_size(
                pos2(row.center().x, row.center().y),
                vec2(transport_width, row.height()),
            );
            ui.scope_builder(egui::UiBuilder::new().max_rect(center_rect), |ui| {
                ui.horizontal_centered(|ui| {
                    let has_neighbors = self.session.is_some() && self.active_media_id.is_some();
                    if has_neighbors
                        && playback_icon_button(
                            ui,
                            Icon::Previous,
                            strings.previous_episode(),
                            false,
                        )
                        .clicked()
                    {
                        self.play_adjacent_episode(-1);
                    }
                    if playback_icon_button(ui, Icon::Replay10, "-10 s", false).clicked() {
                        self.seek_relative(-10.0, now);
                    }
                    let play_icon = if self.snapshot.paused {
                        Icon::Play
                    } else {
                        Icon::Pause
                    };
                    let play_label = if self.snapshot.paused {
                        strings.play()
                    } else {
                        strings.pause()
                    };
                    if playback_icon_button(ui, play_icon, play_label, true).clicked() {
                        self.toggle_pause(now);
                    }
                    if playback_icon_button(ui, Icon::Forward30, "+30 s", false).clicked() {
                        self.seek_relative(30.0, now);
                    }
                    if has_neighbors
                        && playback_icon_button(ui, Icon::Next, strings.next_episode(), false)
                            .clicked()
                    {
                        self.play_adjacent_episode(1);
                    }
                });
            });

            // Right cluster: tracks, danmaku, speed, settings, fullscreen.
            ui.scope_builder(egui::UiBuilder::new().max_rect(row), |ui| {
                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    if playback_icon_button(
                        ui,
                        Icon::Fullscreen,
                        if self.fullscreen {
                            strings.windowed()
                        } else {
                            strings.fullscreen()
                        },
                        false,
                    )
                    .clicked()
                    {
                        let ctx = ui.ctx().clone();
                        self.toggle_fullscreen(&ctx);
                    }
                    if playback_icon_button(ui, Icon::Settings, strings.settings(), false).clicked()
                    {
                        self.open_settings(AppScreen::Playback);
                    }
                    self.show_danmaku_menu(ui, active_danmaku);
                    let speed_response =
                        playback_text_button(ui, &format_speed(self.snapshot.speed));
                    egui::Popup::menu(&speed_response).show(|ui| {
                        for rate in PLAYBACK_RATES {
                            if ui.button(format!("{rate:.2}×")).clicked() {
                                self.set_playback_rate(rate, now);
                                ui.close();
                            }
                        }
                    });
                    self.show_track_menus(ui);
                });
            });
        });
    }

    /// Small "Next: …" preview card above the control bar, library mode only.
    fn show_next_episode_card(&mut self, ui: &mut egui::Ui, control_rect: Rect, alpha: f32) {
        let Some(next) = self.upcoming_episode() else {
            return;
        };
        let strings = Strings::new(self.preferences.language);
        let label = strings.up_next(&next.episode_title);
        let galley = ui.painter().layout_no_wrap(
            label.clone(),
            typography::caption(),
            palette::TEXT_PRIMARY,
        );
        let width = (galley.size().x + 110.0).min(control_rect.width() * 0.5);
        let card = Rect::from_min_max(
            pos2(
                control_rect.right() - width,
                control_rect.top() - 10.0 - 64.0,
            ),
            pos2(control_rect.right(), control_rect.top() - 10.0),
        );
        let response = ui.interact(
            card,
            ui.id().with("next_episode_card"),
            egui::Sense::click(),
        );
        ui.painter()
            .rect_filled(card, 10.0, theme::overlay_fill(alpha));
        ui.painter().rect_stroke(
            card,
            10.0,
            if response.hovered() {
                egui::Stroke::new(1.0, palette::ACCENT_OUTLINE)
            } else {
                theme::overlay_outline(alpha)
            },
            StrokeKind::Inside,
        );
        let thumb = Rect::from_min_size(card.min + vec2(8.0, 8.0), vec2(78.0, 48.0));
        crate::screens::paint_poster_thumb(ui, thumb, &next, &mut self.posters, 6.0);
        let text_clip = ui.painter().with_clip_rect(card.shrink(8.0));
        text_clip.text(
            pos2(thumb.right() + 10.0, card.center().y),
            Align2::LEFT_CENTER,
            label,
            typography::caption(),
            theme::text_primary_faded(alpha),
        );
        if response.hovered() {
            ui.ctx().set_cursor_icon(egui::CursorIcon::PointingHand);
        }
        if response.clicked() {
            self.play_adjacent_episode(1);
        }
    }
    fn show_danmaku_menu(&mut self, ui: &mut egui::Ui, active: usize) {
        let strings = Strings::new(self.preferences.language);
        let status = match self.danmaku.kind {
            DanmakuLoadKind::Ass => "ASS".to_owned(),
            DanmakuLoadKind::Failed => "!".to_owned(),
            _ if !self.danmaku_settings.enabled => strings.off().to_owned(),
            _ => active.to_string(),
        };
        let response = danmaku_pill_button(
            ui,
            strings.danmaku_label(),
            self.danmaku_settings.enabled && self.danmaku.kind != DanmakuLoadKind::Failed,
        )
        .on_hover_text(format!("{}: {status}", strings.danmaku_label()));
        egui::Popup::menu(&response).show(|ui| {
            ui.set_min_width(280.0);
            ui.label(RichText::new(&self.danmaku.status).small());
            if self.danmaku.kind == DanmakuLoadKind::Ass {
                ui.label(strings.ass_compatibility());
                ui.label(strings.select_subtitles());
                return;
            }
            ui.label(strings.drop_danmaku());
            ui.separator();
            ui.checkbox(&mut self.danmaku_settings.enabled, strings.show_danmaku());
            ui.add_enabled_ui(self.danmaku_settings.enabled, |ui| {
                ui.horizontal(|ui| {
                    ui.label(strings.opacity());
                    ui.add(
                        egui::Slider::new(&mut self.danmaku_settings.opacity, 0.0..=1.0)
                            .show_value(true),
                    );
                });
                ui.horizontal(|ui| {
                    ui.label(strings.speed());
                    ui.add(
                        egui::Slider::new(&mut self.danmaku_settings.speed, 0.25..=4.0)
                            .logarithmic(true)
                            .suffix("x"),
                    );
                });
                ui.horizontal(|ui| {
                    ui.label(strings.density());
                    ui.add(
                        egui::Slider::new(&mut self.danmaku_settings.density, 0.0..=1.0)
                            .show_value(true),
                    );
                });
                let mut lanes = self.danmaku_settings.max_lanes as u32;
                ui.horizontal(|ui| {
                    ui.label(strings.lanes());
                    if ui.add(egui::Slider::new(&mut lanes, 1..=32)).changed() {
                        self.danmaku_settings.max_lanes = lanes as usize;
                    }
                });
            });
            if ui.button(strings.reset_display()).clicked() {
                self.danmaku_settings = DanmakuDisplaySettings::default();
            }
            ui.label(strings.danmaku_shortcut());
        });
    }

    fn show_track_menus(&mut self, ui: &mut egui::Ui) {
        let strings = Strings::new(self.preferences.language);
        let audio_description = self
            .tracks
            .selected_audio()
            .map(|track| format!("{}: {}", strings.audio(), track.label()))
            .unwrap_or_else(|| strings.audio().to_owned());
        let audio_tracks = self.tracks.audio.clone();
        let audio_response = playback_icon_button(ui, Icon::AudioTrack, &audio_description, false);
        egui::Popup::menu(&audio_response).show(|ui| {
            if audio_tracks.is_empty() {
                ui.label(strings.no_audio());
            }
            for track in &audio_tracks {
                let checked = track.selected;
                if ui.selectable_label(checked, track.label()).clicked() {
                    self.select_track(TrackKind::Audio, Some(track.id));
                    ui.close();
                }
            }
        });

        let subtitle_description = self
            .tracks
            .selected_subtitle()
            .map(|track| format!("{}: {}", strings.subtitles(), track.label()))
            .unwrap_or_else(|| strings.subtitles_off().to_owned());
        let subtitle_tracks = self.tracks.subtitles.clone();
        let subtitle_response =
            playback_icon_button(ui, Icon::Subtitles, &subtitle_description, false);
        egui::Popup::menu(&subtitle_response).show(|ui| {
            let none_selected = subtitle_tracks.iter().all(|track| !track.selected);
            if ui.selectable_label(none_selected, strings.off()).clicked() {
                self.select_track(TrackKind::Subtitle, None);
                ui.close();
            }
            for track in &subtitle_tracks {
                let checked = track.selected;
                if ui.selectable_label(checked, track.label()).clicked() {
                    self.select_track(TrackKind::Subtitle, Some(track.id));
                    ui.close();
                }
            }
        });
    }
    fn open_settings(&mut self, return_to: AppScreen) {
        self.settings_return = return_to;
        self.screen = AppScreen::Settings;
    }

    fn apply_changed_preferences(&mut self, before: &PlayerPreferences, ctx: &egui::Context) {
        self.preferences = self.preferences.clone().sanitized();
        if self.preferences.language != before.language {
            ctx.request_repaint();
        }
        if self.preferences.volume_percent != before.volume_percent {
            self.set_volume(self.preferences.volume_percent as f64);
        }
        if (self.preferences.playback_rate - before.playback_rate).abs() > f64::EPSILON {
            self.set_playback_rate(self.preferences.playback_rate, Instant::now());
        }
        self.auto_next = self.preferences.auto_next;
        self.danmaku_settings = self.preferences.danmaku_settings();
    }

    fn save_preferences_if_changed(&mut self) {
        self.preferences.auto_next = self.auto_next;
        self.preferences.update_danmaku(&self.danmaku_settings);
        if self.preferences == self.saved_preferences {
            return;
        }
        if let Err(error) = self.preference_store.save(&self.preferences) {
            eprintln!("failed to save player preferences: {error}");
        } else {
            self.saved_preferences = self.preferences.clone();
        }
    }

    fn finish_smoke_if_needed(&mut self, ctx: &egui::Context) {
        let Some(duration) = self.cli.smoke else {
            return;
        };
        if self.smoke_started.elapsed() < duration {
            return;
        }
        let counters = self
            .counters
            .lock()
            .map(|counters| counters.clone())
            .unwrap_or_default();
        let report = SmokeReport::from_counters(
            self.cli.media.as_deref().unwrap_or_default(),
            duration,
            &counters,
            self.snapshot.hwdec_current.clone(),
            self.snapshot.frame_drop_count,
        );
        if let Ok(mut slot) = self.report_slot.lock()
            && slot.is_none()
        {
            *slot = Some(report);
            ctx.send_viewport_cmd(ViewportCommand::Close);
        }
    }
}

impl eframe::App for PlayerApp {
    fn update(&mut self, ctx: &egui::Context, frame: &mut eframe::Frame) {
        let now = Instant::now();
        if !self.window_effects_applied {
            crate::platform::apply_rounded_corners(frame);
            self.window_effects_applied = true;
        }
        self.show_window_title_bar(ctx);
        self.posters.poll(ctx);
        self.handle_session_events(ctx);
        let local_connection = self
            .local_host
            .as_mut()
            .and_then(LocalServerSupervisor::poll);
        if let Some(connection) = local_connection {
            self.connect_to_local_server(ctx, connection);
        }

        match self.screen {
            AppScreen::Connect => {
                let discovered = self
                    .discovery
                    .as_ref()
                    .map(|listener| listener.servers())
                    .unwrap_or_default();
                let action = self.connect_screen.show(
                    ctx,
                    &discovered,
                    &mut self.preferences.language,
                    self.local_host.as_ref().map(LocalServerSupervisor::status),
                    &self.branding,
                );
                match action {
                    Some(ConnectAction::Connect(request)) => {
                        self.connect_to_server(ctx, request);
                    }
                    Some(ConnectAction::StartLocal(root)) => {
                        self.preferences.local_library_roots =
                            vec![root.to_string_lossy().into_owned()];
                        if let Some(local_host) = &mut self.local_host {
                            if let Err(error) = local_host.start(root) {
                                self.connect_screen.error = Some(error);
                            } else {
                                self.connect_screen.error = None;
                            }
                        }
                    }
                    None => {}
                }
                // Keep polling for new announcements while idle.
                ctx.request_repaint_after(Duration::from_millis(500));
            }
            AppScreen::Library => {
                if self.qa_play_first_pending
                    && let Some(first_id) = self
                        .session
                        .as_ref()
                        .and_then(|session| session.catalog.as_ref())
                        .and_then(|catalog| catalog.items.first())
                        .map(|item| item.id.clone())
                {
                    self.qa_play_first_pending = false;
                    self.request_library_play(first_id);
                }
                let action = match &self.session {
                    Some(session) => self.library_screen.show(
                        ctx,
                        session,
                        &mut self.posters,
                        Strings::new(self.preferences.language),
                    ),
                    None => {
                        self.screen = AppScreen::Connect;
                        None
                    }
                };
                match action {
                    Some(LibraryAction::Play { media_id }) => {
                        self.request_library_play(media_id);
                    }
                    Some(LibraryAction::Refresh) => {
                        if let Some(session) = &mut self.session {
                            session.refresh_catalog();
                            session.refresh_progress();
                        }
                    }
                    Some(LibraryAction::Disconnect) => self.disconnect(),
                    Some(LibraryAction::Settings) => self.open_settings(AppScreen::Library),
                    None => {}
                }
            }
            AppScreen::Settings => {
                let before = self.preferences.clone();
                let connected_url = self
                    .session
                    .as_ref()
                    .map(|session| session.base_url.clone());
                let return_to_playback = self.settings_return == AppScreen::Playback;
                let local_roots = self.preferences.local_library_roots.clone();
                match show_settings(
                    ctx,
                    &mut self.preferences,
                    connected_url.as_deref(),
                    return_to_playback,
                    self.local_host.as_ref().map(LocalServerSupervisor::status),
                    &local_roots,
                ) {
                    Some(SettingsAction::Back) => self.screen = self.settings_return,
                    Some(SettingsAction::ChangeServer) => {
                        self.connect_screen.manual_url =
                            self.preferences.last_server_url.clone().unwrap_or_default();
                        self.disconnect();
                    }
                    Some(SettingsAction::RestartLocalServer) => {
                        let roots = self.local_roots();
                        self.prepare_for_host_transition();
                        if let Some(local_host) = &mut self.local_host
                            && let Err(error) = local_host.restart(roots)
                        {
                            self.connect_screen.error = Some(error);
                        }
                    }
                    Some(SettingsAction::StopLocalServer) => {
                        if let Some(local_host) = &mut self.local_host {
                            local_host.stop();
                        }
                        self.prepare_for_host_transition();
                    }
                    Some(SettingsAction::SetLocalRoot(root)) => {
                        self.preferences.local_library_roots =
                            vec![root.to_string_lossy().into_owned()];
                        self.connect_screen.local_library_root =
                            root.to_string_lossy().into_owned();
                        self.prepare_for_host_transition();
                        if let Some(local_host) = &mut self.local_host
                            && let Err(error) = local_host.restart(vec![root])
                        {
                            self.connect_screen.error = Some(error);
                        }
                    }
                    None => {}
                }
                if self.preferences != before {
                    self.apply_changed_preferences(&before, ctx);
                }
            }
            AppScreen::Playback => {
                self.handle_shortcuts(ctx, now);
                self.record_activity(ctx);
                self.handle_dropped_danmaku(ctx);
                self.refresh_snapshot(now);
                self.refresh_tracks(now);
                self.upload_active_progress(false);
                self.handle_end_of_file();
                let overlay_position_s = self.overlay_clock.position_at(now);
                self.show_video(ctx, now, overlay_position_s);
                self.finish_smoke_if_needed(ctx);
                if self.snapshot.paused {
                    ctx.request_repaint_after(Duration::from_millis(100));
                } else {
                    ctx.request_repaint();
                }
            }
        }
        self.save_preferences_if_changed();
    }
}

fn window_control_button(ui: &mut egui::Ui, icon: Icon, close: bool) -> egui::Response {
    let (rect, response) = ui.allocate_exact_size(vec2(46.0, 38.0), Sense::click());
    let fill = if response.hovered() {
        if close {
            Color32::from_rgb(190, 48, 48)
        } else {
            Color32::from_white_alpha(18)
        }
    } else {
        Color32::TRANSPARENT
    };
    ui.painter().rect_filled(rect, 0.0, fill);
    paint_icon(
        ui.painter(),
        Rect::from_center_size(rect.center(), vec2(15.0, 15.0)),
        icon,
        palette::TEXT_SECONDARY,
        1.4,
    );
    response
}

fn playback_icon_button(
    ui: &mut egui::Ui,
    icon: Icon,
    tooltip: &str,
    prominent: bool,
) -> egui::Response {
    let size = if prominent {
        vec2(46.0, 42.0)
    } else {
        vec2(38.0, 34.0)
    };
    let (rect, response) = ui.allocate_exact_size(size, Sense::click());
    let fill = if prominent {
        if response.hovered() {
            palette::ACCENT_OUTLINE
        } else {
            palette::ACCENT_BRIGHT
        }
    } else if response.hovered() {
        Color32::from_white_alpha(18)
    } else {
        Color32::TRANSPARENT
    };
    ui.painter()
        .rect_filled(rect, if prominent { 21.0 } else { 8.0 }, fill);
    let icon_size = if prominent { 23.0 } else { 20.0 };
    paint_icon(
        ui.painter(),
        Rect::from_center_size(rect.center(), vec2(icon_size, icon_size)),
        icon,
        palette::TEXT_PRIMARY,
        if prominent { 2.0 } else { 1.6 },
    );
    if response.hovered() {
        ui.ctx().set_cursor_icon(egui::CursorIcon::PointingHand);
    }
    response.on_hover_text(tooltip)
}

/// Compact text control matching the icon-button footprint (playback speed).
fn playback_text_button(ui: &mut egui::Ui, label: &str) -> egui::Response {
    let galley = ui.painter().layout_no_wrap(
        label.to_owned(),
        typography::caption(),
        palette::TEXT_PRIMARY,
    );
    let (rect, response) =
        ui.allocate_exact_size(vec2(galley.size().x + 18.0, 34.0), Sense::click());
    let fill = if response.hovered() {
        Color32::from_white_alpha(18)
    } else {
        Color32::TRANSPARENT
    };
    ui.painter().rect_filled(rect, 8.0, fill);
    ui.painter().text(
        rect.center(),
        Align2::CENTER_CENTER,
        label,
        typography::caption(),
        palette::TEXT_PRIMARY,
    );
    if response.hovered() {
        ui.ctx().set_cursor_icon(egui::CursorIcon::PointingHand);
    }
    response
}

/// Labeled danmaku toggle-menu control; accent colored while the overlay is on.
fn danmaku_pill_button(ui: &mut egui::Ui, label: &str, enabled: bool) -> egui::Response {
    let galley =
        ui.painter()
            .layout_no_wrap(label.to_owned(), typography::body(), palette::TEXT_PRIMARY);
    let (rect, response) =
        ui.allocate_exact_size(vec2(galley.size().x + 44.0, 34.0), Sense::click());
    let fill = if response.hovered() {
        Color32::from_white_alpha(18)
    } else {
        Color32::TRANSPARENT
    };
    ui.painter().rect_filled(rect, 8.0, fill);
    let color = if enabled {
        palette::ACCENT_OUTLINE
    } else {
        palette::TEXT_MUTED
    };
    paint_icon(
        ui.painter(),
        Rect::from_center_size(pos2(rect.left() + 19.0, rect.center().y), vec2(19.0, 19.0)),
        Icon::Danmaku,
        color,
        1.5,
    );
    ui.painter().text(
        pos2(rect.left() + 34.0, rect.center().y),
        Align2::LEFT_CENTER,
        label,
        typography::body(),
        color,
    );
    if response.hovered() {
        ui.ctx().set_cursor_icon(egui::CursorIcon::PointingHand);
    }
    response
}

fn format_speed(speed: f64) -> String {
    if (speed - speed.round()).abs() < 0.005 {
        format!("{speed:.0}×")
    } else {
        format!("{speed:.2}×")
    }
}

/// Thin media slider with a round thumb. Returns the new fraction while the
/// user clicks or drags. `alpha` follows the fading playback chrome.
fn media_slider(
    ui: &mut egui::Ui,
    id_source: &str,
    rect: Rect,
    fraction: f32,
    alpha: f32,
) -> Option<f32> {
    let response = ui.interact(rect, ui.id().with(id_source), Sense::click_and_drag());
    let track = Rect::from_center_size(rect.center(), vec2(rect.width(), 4.0));
    let painter = ui.painter();
    painter.rect_filled(track, 2.0, Color32::from_white_alpha((64.0 * alpha) as u8));
    let fraction = fraction.clamp(0.0, 1.0);
    let accent = palette::ACCENT_BRIGHT;
    painter.rect_filled(
        Rect::from_min_size(track.min, vec2(track.width() * fraction, track.height())),
        2.0,
        Color32::from_rgba_unmultiplied(accent.r(), accent.g(), accent.b(), (255.0 * alpha) as u8),
    );
    let engaged = response.hovered() || response.dragged();
    let thumb_radius = if engaged { 8.0 } else { 6.0 };
    painter.circle_filled(
        pos2(track.left() + track.width() * fraction, track.center().y),
        thumb_radius,
        Color32::from_rgba_unmultiplied(255, 255, 255, (235.0 * alpha) as u8),
    );
    if engaged {
        ui.ctx().set_cursor_icon(egui::CursorIcon::PointingHand);
    }
    if (response.clicked() || response.dragged())
        && let Some(pointer) = response.interact_pointer_pos()
        && track.width() > 0.0
    {
        return Some(((pointer.x - track.left()) / track.width()).clamp(0.0, 1.0));
    }
    None
}
#[derive(Clone, Debug)]
pub struct PlaybackSnapshot {
    pub position_s: f64,
    pub duration_s: f64,
    pub paused: bool,
    pub speed: f64,
    pub volume_percent: f64,
    pub muted: bool,
    pub estimated_vf_fps: Option<String>,
    pub frame_drop_count: Option<u64>,
    pub hwdec_current: Option<String>,
    pub cache_duration_s: Option<f64>,
    pub eof_reached: bool,
    pub render_error: Option<String>,
}

impl Default for PlaybackSnapshot {
    fn default() -> Self {
        Self {
            position_s: 0.0,
            duration_s: 3600.0,
            paused: false,
            speed: 1.0,
            volume_percent: 100.0,
            muted: false,
            estimated_vf_fps: None,
            frame_drop_count: None,
            hwdec_current: None,
            cache_duration_s: None,
            eof_reached: false,
            render_error: None,
        }
    }
}

#[derive(Clone, Debug)]
struct PlaybackReadback {
    observed_position_s: Option<f64>,
    snapshot: PlaybackSnapshot,
}

fn load_danmaku(cli: &Cli) -> DanmakuLoad {
    let result = if let Some(path) = &cli.danmaku_path {
        load_local_danmaku(path)
    } else if let (Some(base_url), Some(media_id)) = (&cli.server_url, &cli.media_id) {
        fetch_server_danmaku(base_url, media_id, cli.danmaku_force_refresh)
    } else {
        return DanmakuLoad::none();
    };
    result.unwrap_or_else(DanmakuLoad::failed)
}

fn danmaku_color(color_argb: u32, opacity: f32) -> Color32 {
    let source_alpha = ((color_argb >> 24) & 0xff) as f32;
    let alpha = (source_alpha * opacity.clamp(0.0, 1.0))
        .round()
        .clamp(0.0, 255.0) as u8;
    Color32::from_rgba_unmultiplied(
        ((color_argb >> 16) & 0xff) as u8,
        ((color_argb >> 8) & 0xff) as u8,
        (color_argb & 0xff) as u8,
        alpha,
    )
}

fn is_danmaku_path(path: &Path) -> bool {
    path.extension()
        .and_then(|value| value.to_str())
        .is_some_and(|extension| {
            matches!(
                extension.to_ascii_lowercase().as_str(),
                "xml" | "json" | "danmaku" | "ass"
            )
        })
}

pub fn media_display_title(media: &str) -> String {
    // Media strings mix Windows paths, POSIX paths, and URLs, so split on
    // both separators instead of using `Path` (whose separator handling is
    // host-platform dependent).
    let trimmed = media.trim_end_matches(['/', '\\']);
    trimmed
        .rsplit(['/', '\\'])
        .next()
        .filter(|segment| !segment.is_empty())
        .unwrap_or(trimmed)
        .to_owned()
}

pub fn format_time(seconds: f64) -> String {
    if !seconds.is_finite() || seconds < 0.0 {
        return "--:--".to_owned();
    }
    let total = seconds.round() as u64;
    let hours = total / 3600;
    let minutes = (total % 3600) / 60;
    let secs = total % 60;
    if hours > 0 {
        format!("{hours}:{minutes:02}:{secs:02}")
    } else {
        format!("{minutes}:{secs:02}")
    }
}

fn parse_f64(value: Option<String>) -> Option<f64> {
    value.and_then(|value| value.trim().parse::<f64>().ok())
}

fn parse_u64(value: Option<String>) -> Option<u64> {
    value.and_then(|value| value.trim().parse::<u64>().ok())
}

#[cfg(test)]
mod tests {
    use std::path::Path;

    use super::{danmaku_color, format_time, is_danmaku_path, media_display_title};

    #[test]
    fn formats_times_with_and_without_hours() {
        assert_eq!(format_time(0.0), "0:00");
        assert_eq!(format_time(65.4), "1:05");
        assert_eq!(format_time(3_735.0), "1:02:15");
        assert_eq!(format_time(f64::NAN), "--:--");
    }

    #[test]
    fn derives_display_titles_from_paths_and_urls() {
        assert_eq!(
            media_display_title(r"D:\Anime\Episode 01 [1080p].mkv"),
            "Episode 01 [1080p].mkv"
        );
        assert_eq!(
            media_display_title("http://127.0.0.1:8686/media/abc123"),
            "abc123"
        );
        assert_eq!(media_display_title("plain-name.mkv"), "plain-name.mkv");
    }

    #[test]
    fn converts_argb_and_multiplies_overlay_opacity() {
        let color = danmaku_color(0x80ff_8040, 0.5);
        assert_eq!(color.to_srgba_unmultiplied(), [255, 128, 64, 64]);
    }

    #[test]
    fn recognizes_supported_manual_danmaku_files() {
        assert!(is_danmaku_path(Path::new("comments.XML")));
        assert!(is_danmaku_path(Path::new("comments.danmaku")));
        assert!(is_danmaku_path(Path::new("cached.ass")));
        assert!(!is_danmaku_path(Path::new("episode.mkv")));
    }
}
