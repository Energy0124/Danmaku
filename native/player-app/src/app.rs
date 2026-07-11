//! Player window: chrome, video surface, and fade-over-video controls.

use std::{
    path::Path,
    sync::{Arc, Mutex},
    time::{Duration, Instant},
};

use eframe::egui::{
    self, Align2, Color32, FontId, Frame, Rect, RichText, Sense, StrokeKind, ViewportCommand, pos2,
    vec2,
};
use egui_glow::CallbackFn;

use crate::{
    cli::Cli,
    clock::OverlayClock,
    danmaku::{
        DanmakuDisplaySettings, DanmakuLayout, DanmakuLoad, DanmakuLoadKind, estimate_text_width,
        fetch_server_danmaku, load_local_danmaku,
    },
    discovery::{DEFAULT_DISCOVERY_PORT, DiscoveryListener},
    library::PlaybackProgress,
    localization::Strings,
    posters::PosterCache,
    preferences::{PlayerPreferences, PreferenceStore},
    screens::{ConnectScreen, LibraryAction, LibraryScreen, SettingsAction, show_settings},
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
        let preference_store = PreferenceStore::for_current_user();
        let mut preferences = preference_store.load();
        let saved_preferences = preferences.clone();
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

        // Title ribbon (top) fades together with the control bar.
        ui.painter().text(
            video_rect.left_top() + vec2(metrics::GUTTER, 16.0),
            Align2::LEFT_TOP,
            &self.display_title,
            typography::title(),
            theme::text_primary_faded(alpha),
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
        ui.painter()
            .rect_filled(rect, metrics::OVERLAY_RADIUS, theme::overlay_fill(alpha));
        ui.painter().rect_stroke(
            rect,
            metrics::OVERLAY_RADIUS,
            theme::overlay_outline(alpha),
            StrokeKind::Inside,
        );

        ui.scope_builder(
            egui::UiBuilder::new().max_rect(rect.shrink(metrics::OVERLAY_PADDING)),
            |ui| {
                ui.set_clip_rect(rect);
                ui.multiply_opacity(alpha);
                ui.horizontal(|ui| {
                    let play_label = if self.snapshot.paused {
                        strings.play()
                    } else {
                        strings.pause()
                    };
                    if ui.button(play_label).clicked() {
                        self.toggle_pause(now);
                    }
                    if ui.button("-10s").clicked() {
                        self.seek_relative(-10.0, now);
                    }
                    if ui.button("+30s").clicked() {
                        self.seek_relative(30.0, now);
                    }
                    if self.session.is_some() && self.active_media_id.is_some() {
                        if ui.button(strings.library()).clicked() {
                            self.back_to_library();
                        }
                        if ui
                            .button("|<")
                            .on_hover_text(strings.previous_episode())
                            .clicked()
                        {
                            self.play_adjacent_episode(-1);
                        }
                        if ui
                            .button(">|")
                            .on_hover_text(strings.next_episode())
                            .clicked()
                        {
                            self.play_adjacent_episode(1);
                        }
                        let auto_label = if self.auto_next {
                            strings.auto_next_on()
                        } else {
                            strings.auto_next_off()
                        };
                        if ui.button(auto_label).clicked() {
                            self.auto_next = !self.auto_next;
                        }
                    }
                    if ui.button(strings.settings()).clicked() {
                        self.open_settings(AppScreen::Playback);
                    }
                    ui.menu_button(format!("{:.2}x", self.snapshot.speed), |ui| {
                        for rate in PLAYBACK_RATES {
                            if ui.button(format!("{rate:.2}x")).clicked() {
                                self.set_playback_rate(rate, now);
                                ui.close();
                            }
                        }
                    });
                    self.show_track_menus(ui);
                    self.show_danmaku_menu(ui, active_danmaku);

                    ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                        ui.label(
                            RichText::new(format!(
                                "{} / {}",
                                format_time(overlay_position_s),
                                format_time(self.snapshot.duration_s)
                            ))
                            .color(palette::TEXT_SECONDARY),
                        );
                        if ui
                            .button(if self.fullscreen {
                                strings.windowed()
                            } else {
                                strings.fullscreen()
                            })
                            .clicked()
                        {
                            let ctx = ui.ctx().clone();
                            self.toggle_fullscreen(&ctx);
                        }
                        let mut volume = self.snapshot.volume_percent as f32;
                        if ui
                            .add(
                                egui::Slider::new(&mut volume, 0.0..=130.0)
                                    .show_value(false)
                                    .trailing_fill(true),
                            )
                            .changed()
                        {
                            self.set_volume(volume as f64);
                        }
                        let mute_label = if self.snapshot.muted {
                            strings.unmute()
                        } else {
                            strings.mute()
                        };
                        if ui.button(mute_label).clicked() {
                            self.toggle_mute();
                        }
                    });
                });
                ui.add_space(metrics::ROW_GAP);
                let duration = self.snapshot.duration_s.max(1.0) as f32;
                let mut position = overlay_position_s.clamp(0.0, duration as f64) as f32;
                let slider_width = ui.available_width();
                ui.style_mut().spacing.slider_width = slider_width;
                let changed = ui
                    .add(
                        egui::Slider::new(&mut position, 0.0..=duration)
                            .show_value(false)
                            .trailing_fill(true),
                    )
                    .changed();
                if changed {
                    self.seek_to(position as f64, now);
                }
            },
        );
    }

    fn show_danmaku_menu(&mut self, ui: &mut egui::Ui, active: usize) {
        let strings = Strings::new(self.preferences.language);
        let label = match self.danmaku.kind {
            DanmakuLoadKind::Ass => "Danmaku ASS".to_owned(),
            DanmakuLoadKind::Failed => "Danmaku !".to_owned(),
            _ if !self.danmaku_settings.enabled => {
                format!("Danmaku {}", strings.off())
            }
            _ => format!("Danmaku {active}"),
        };
        ui.menu_button(label, |ui| {
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
        let audio_label = self
            .tracks
            .selected_audio()
            .map(|track| format!("{} {}", strings.audio(), track.label()))
            .unwrap_or_else(|| strings.audio().to_owned());
        let audio_tracks = self.tracks.audio.clone();
        ui.menu_button(audio_label, |ui| {
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

        let subtitle_label = self
            .tracks
            .selected_subtitle()
            .map(|track| format!("{} {}", strings.subtitles(), track.label()))
            .unwrap_or_else(|| strings.subtitles_off().to_owned());
        let subtitle_tracks = self.tracks.subtitles.clone();
        ui.menu_button(subtitle_label, |ui| {
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
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        let now = Instant::now();
        self.posters.poll(ctx);
        self.handle_session_events(ctx);

        match self.screen {
            AppScreen::Connect => {
                let discovered = self
                    .discovery
                    .as_ref()
                    .map(|listener| listener.servers())
                    .unwrap_or_default();
                if let Some(request) =
                    self.connect_screen
                        .show(ctx, &discovered, &mut self.preferences.language)
                {
                    self.preferences.last_server_url = Some(request.base_url.clone());
                    self.posters.set_base_url(Some(request.base_url.clone()));
                    self.session = Some(LibrarySession::connect(
                        request.base_url,
                        request.pairing_token,
                        ctx.clone(),
                    ));
                    self.screen = AppScreen::Library;
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
                match show_settings(
                    ctx,
                    &mut self.preferences,
                    connected_url.as_deref(),
                    return_to_playback,
                ) {
                    Some(SettingsAction::Back) => self.screen = self.settings_return,
                    Some(SettingsAction::ChangeServer) => {
                        self.connect_screen.manual_url =
                            self.preferences.last_server_url.clone().unwrap_or_default();
                        self.disconnect();
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
