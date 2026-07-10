//! Player window: chrome, video surface, and fade-over-video controls.

use std::{
    sync::{Arc, Mutex},
    time::{Duration, Instant},
};

use eframe::egui::{
    self, Align2, Color32, FontData, FontDefinitions, FontFamily, FontId, Frame, Rect, RichText,
    Sense, Stroke, StrokeKind, ViewportCommand, pos2, vec2,
};
use egui_glow::CallbackFn;

use crate::{
    cli::Cli,
    clock::OverlayClock,
    smoke::SmokeReport,
    tracks::{TrackInventory, TrackKind, read_track_inventory, selection_command},
    video::{RenderCounters, SharedVideoRenderer, VideoRenderer},
};

const PROPERTY_REFRESH_INTERVAL: Duration = Duration::from_millis(180);
const TRACK_REFRESH_INTERVAL: Duration = Duration::from_millis(1_000);
const CONTROLS_VISIBLE_SECONDS: f32 = 1.7;
const CONTROLS_FADE_SECONDS: f32 = 1.1;
const PLAYBACK_RATES: [f64; 6] = [0.5, 0.75, 1.0, 1.25, 1.5, 2.0];

pub struct PlayerApp {
    cli: Cli,
    display_title: String,
    renderer: SharedVideoRenderer,
    counters: Arc<Mutex<RenderCounters>>,
    report_slot: Arc<Mutex<Option<SmokeReport>>>,
    overlay_clock: OverlayClock,
    snapshot: PlaybackSnapshot,
    tracks: TrackInventory,
    last_property_refresh: Instant,
    last_track_refresh: Instant,
    last_pointer_activity: Instant,
    last_normal_inner_rect: Option<Rect>,
    last_normal_outer_rect: Option<Rect>,
    fullscreen: bool,
    smoke_started: Instant,
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
        let renderer = Arc::new(Mutex::new(
            VideoRenderer::create(
                &cli.media,
                cli.start_position_s,
                cli.volume_percent,
                &creation_context.egui_ctx,
                Arc::clone(&counters),
            )
            .map_err(|error| format!("mpv initialization failed: {error}"))?,
        ));
        let now = Instant::now();
        let display_title = cli
            .title
            .clone()
            .unwrap_or_else(|| media_display_title(&cli.media));
        let mut overlay_clock = OverlayClock::new(now);
        if let Some(start_position_s) = cli.start_position_s {
            overlay_clock.seek(start_position_s, now);
        }

        Ok(Self {
            cli,
            display_title,
            renderer,
            counters,
            report_slot,
            overlay_clock,
            snapshot: PlaybackSnapshot::default(),
            tracks: TrackInventory::default(),
            last_property_refresh: now - PROPERTY_REFRESH_INTERVAL,
            last_track_refresh: now - TRACK_REFRESH_INTERVAL,
            last_pointer_activity: now,
            last_normal_inner_rect: None,
            last_normal_outer_rect: None,
            fullscreen: false,
            smoke_started: now,
        })
    }

    pub fn configure_fonts(ctx: &egui::Context) {
        let mut fonts = FontDefinitions::default();
        let name = "noto-sans-cjk-tc".to_owned();
        // Shared with the spike until the M2.5 design-system pass gives the
        // player its own asset pipeline.
        fonts.font_data.insert(
            name.clone(),
            Arc::new(FontData::from_static(include_bytes!(
                "../../spike-egui-player/assets/NotoSansCJKtc-Regular.otf"
            ))),
        );
        fonts
            .families
            .entry(FontFamily::Proportional)
            .or_default()
            .insert(0, name.clone());
        fonts
            .families
            .entry(FontFamily::Monospace)
            .or_default()
            .push(name);
        ctx.set_fonts(fonts);
    }

    pub fn configure_style(ctx: &egui::Context) {
        let mut visuals = egui::Visuals::dark();
        visuals.panel_fill = Color32::from_rgb(12, 14, 18);
        visuals.window_fill = Color32::from_rgb(18, 20, 25);
        visuals.faint_bg_color = Color32::from_rgb(28, 32, 39);
        visuals.extreme_bg_color = Color32::from_rgb(7, 8, 11);
        visuals.selection.bg_fill = Color32::from_rgb(54, 112, 168);
        visuals.widgets.hovered.bg_fill = Color32::from_rgb(38, 43, 52);
        visuals.widgets.active.bg_fill = Color32::from_rgb(48, 65, 86);
        ctx.set_visuals(visuals);

        let mut style = (*ctx.style()).clone();
        style.spacing.item_spacing = vec2(8.0, 8.0);
        style.spacing.button_padding = vec2(12.0, 6.0);
        ctx.set_style(style);
    }

    fn handle_shortcuts(&mut self, ctx: &egui::Context, now: Instant) {
        let wants_text_input = ctx.wants_keyboard_input();
        if wants_text_input {
            return;
        }
        let (
            toggle_fullscreen,
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
        if escape && self.fullscreen {
            self.toggle_fullscreen(ctx);
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
        }
    }

    fn set_playback_rate(&mut self, rate: f64, now: Instant) {
        let value = format!("{rate:.3}");
        if self.run_mpv_command(&["set", "speed", &value]) {
            self.snapshot.speed = rate;
            self.overlay_clock.set_rate(rate, now);
        }
    }

    fn set_volume(&mut self, volume_percent: f64) {
        let volume_percent = volume_percent.clamp(0.0, 130.0);
        let value = format!("{volume_percent:.0}");
        if self.run_mpv_command(&["set", "volume", &value]) {
            self.snapshot.volume_percent = volume_percent;
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
            .lock()
            .ok()
            .map(|mut renderer| f(&mut renderer))
    }

    fn show_video(&mut self, ctx: &egui::Context, now: Instant, overlay_position_s: f64) {
        egui::CentralPanel::default()
            .frame(Frame::NONE.fill(Color32::from_rgb(5, 6, 8)))
            .show(ctx, |ui| {
                let rect = ui.available_rect_before_wrap();
                let response = ui.allocate_rect(rect, Sense::click_and_drag());
                if response.double_clicked() {
                    self.toggle_fullscreen(ctx);
                }

                let renderer = Arc::clone(&self.renderer);
                ui.painter().add(egui::PaintCallback {
                    rect,
                    callback: Arc::new(CallbackFn::new(move |info, painter| {
                        if let Ok(mut renderer) = renderer.lock() {
                            renderer.render(info, painter);
                        }
                    })),
                });

                if let Some(error) = self.snapshot.render_error.clone() {
                    ui.painter().text(
                        rect.left_top() + vec2(14.0, 14.0),
                        Align2::LEFT_TOP,
                        error,
                        FontId::proportional(13.0),
                        Color32::from_rgb(255, 126, 126),
                    );
                }

                self.show_controls(ui, rect, now, overlay_position_s);
            });
    }

    fn controls_alpha(&self) -> f32 {
        let inactive_for = self.last_pointer_activity.elapsed().as_secs_f32();
        if self.snapshot.paused {
            return 1.0;
        }
        if inactive_for <= CONTROLS_VISIBLE_SECONDS {
            1.0
        } else {
            (1.0 - (inactive_for - CONTROLS_VISIBLE_SECONDS) / CONTROLS_FADE_SECONDS)
                .clamp(0.0, 1.0)
        }
    }

    fn show_controls(
        &mut self,
        ui: &mut egui::Ui,
        video_rect: Rect,
        now: Instant,
        overlay_position_s: f64,
    ) {
        let alpha = self.controls_alpha();
        if alpha <= 0.02 {
            return;
        }

        // Title ribbon (top) fades together with the control bar.
        let title_alpha = (alpha * 220.0).round() as u8;
        ui.painter().text(
            video_rect.left_top() + vec2(18.0, 16.0),
            Align2::LEFT_TOP,
            &self.display_title,
            FontId::proportional(17.0),
            Color32::from_rgba_premultiplied(233, 238, 244, title_alpha),
        );

        let height = 96.0;
        let rect = Rect::from_min_max(
            pos2(
                video_rect.left() + 18.0,
                video_rect.bottom() - height - 18.0,
            ),
            pos2(video_rect.right() - 18.0, video_rect.bottom() - 18.0),
        );
        let fill_alpha = (alpha * 210.0).round() as u8;
        ui.painter().rect_filled(
            rect,
            10.0,
            Color32::from_rgba_premultiplied(10, 12, 16, fill_alpha),
        );
        ui.painter().rect_stroke(
            rect,
            10.0,
            Stroke::new(
                1.0_f32,
                Color32::from_rgba_premultiplied(255, 255, 255, (alpha * 36.0) as u8),
            ),
            StrokeKind::Inside,
        );

        ui.scope_builder(egui::UiBuilder::new().max_rect(rect.shrink(14.0)), |ui| {
            ui.set_clip_rect(rect);
            ui.multiply_opacity(alpha);
            ui.horizontal(|ui| {
                let play_label = if self.snapshot.paused {
                    "Play"
                } else {
                    "Pause"
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
                ui.menu_button(format!("{:.2}x", self.snapshot.speed), |ui| {
                    for rate in PLAYBACK_RATES {
                        if ui.button(format!("{rate:.2}x")).clicked() {
                            self.set_playback_rate(rate, now);
                            ui.close();
                        }
                    }
                });
                self.show_track_menus(ui);

                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    ui.label(
                        RichText::new(format!(
                            "{} / {}",
                            format_time(overlay_position_s),
                            format_time(self.snapshot.duration_s)
                        ))
                        .color(Color32::from_rgb(214, 221, 229)),
                    );
                    if ui
                        .button(if self.fullscreen {
                            "Windowed"
                        } else {
                            "Fullscreen"
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
                        "Unmute"
                    } else {
                        "Mute"
                    };
                    if ui.button(mute_label).clicked() {
                        self.toggle_mute();
                    }
                });
            });
            ui.add_space(8.0);
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
        });
    }

    fn show_track_menus(&mut self, ui: &mut egui::Ui) {
        let audio_label = self
            .tracks
            .selected_audio()
            .map(|track| format!("Audio {}", track.label()))
            .unwrap_or_else(|| "Audio".to_owned());
        let audio_tracks = self.tracks.audio.clone();
        ui.menu_button(audio_label, |ui| {
            if audio_tracks.is_empty() {
                ui.label("No audio tracks");
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
            .map(|track| format!("Subs {}", track.label()))
            .unwrap_or_else(|| "Subs off".to_owned());
        let subtitle_tracks = self.tracks.subtitles.clone();
        ui.menu_button(subtitle_label, |ui| {
            let none_selected = subtitle_tracks.iter().all(|track| !track.selected);
            if ui.selectable_label(none_selected, "Off").clicked() {
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
            &self.cli.media,
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
        self.handle_shortcuts(ctx, now);
        self.record_activity(ctx);
        self.refresh_snapshot(now);
        self.refresh_tracks(now);
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
            render_error: None,
        }
    }
}

#[derive(Clone, Debug)]
struct PlaybackReadback {
    observed_position_s: Option<f64>,
    snapshot: PlaybackSnapshot,
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
    use super::{format_time, media_display_title};

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
}
