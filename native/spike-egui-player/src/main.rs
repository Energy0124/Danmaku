use std::{
    process,
    sync::{Arc, Mutex},
    time::{Duration, Instant},
};

use eframe::egui::{
    self, Align2, Color32, FontData, FontDefinitions, FontFamily, FontId, Frame, Rect, RichText,
    Sense, Stroke, StrokeKind, TextEdit, TopBottomPanel, Vec2, ViewportCommand, pos2, vec2,
};
use egui_glow::CallbackFn;
use spike_egui_player::{
    cli::{Cli, usage},
    clock::OverlayClock,
    danmaku::{DanmakuLayout, DanmakuMode, estimate_text_width, synthetic_timeline},
    mpv::{SharedVideoRenderer, VideoRenderer},
    smoke::{DanmakuVelocityObservation, SmokeReport, SmokeStats},
};

fn main() {
    let cli = match Cli::parse_env() {
        Ok(cli) => cli,
        Err(error) => {
            eprintln!("{error}");
            eprintln!("{}", usage());
            process::exit(2);
        }
    };
    if cli.help {
        println!("{}", usage());
        return;
    }

    let report_slot = Arc::new(Mutex::new(None));
    let app_cli = cli.clone();
    let app_report_slot = Arc::clone(&report_slot);
    let native_options = eframe::NativeOptions {
        renderer: eframe::Renderer::Glow,
        viewport: egui::ViewportBuilder::default()
            .with_inner_size([1600.0, 900.0])
            .with_min_inner_size([960.0, 540.0])
            .with_title("Danmaku egui/mpv compositing spike"),
        vsync: true,
        ..Default::default()
    };

    let run_result = eframe::run_native(
        "Danmaku egui/mpv compositing spike",
        native_options,
        Box::new(move |creation_context| {
            configure_fonts(&creation_context.egui_ctx);
            configure_style(&creation_context.egui_ctx);
            PlayerApp::new(
                creation_context,
                app_cli.clone(),
                Arc::clone(&app_report_slot),
            )
            .map(|app| Box::new(app) as Box<dyn eframe::App>)
        }),
    );

    if let Err(error) = run_result {
        if let Some(duration) = cli.smoke {
            let report = SmokeReport::fail(&cli.media, duration, format!("app failed: {error}"));
            println!("{report}");
            process::exit(report.exit_code());
        }
        eprintln!("app failed: {error}");
        process::exit(1);
    }

    if let Some(duration) = cli.smoke {
        let report = report_slot
            .lock()
            .ok()
            .and_then(|mut slot| slot.take())
            .unwrap_or_else(|| {
                SmokeReport::fail(&cli.media, duration, "app closed before smoke completed")
            });
        println!("{report}");
        process::exit(report.exit_code());
    }
}

struct PlayerApp {
    cli: Cli,
    renderer: SharedVideoRenderer,
    stats: Arc<Mutex<SmokeStats>>,
    report_slot: Arc<Mutex<Option<SmokeReport>>>,
    timeline: danmaku_core::Timeline,
    danmaku_layout: DanmakuLayout,
    overlay_clock: OverlayClock,
    posters: Vec<Poster>,
    search: String,
    snapshot: PlaybackSnapshot,
    last_update: Instant,
    last_property_refresh: Instant,
    last_pointer_activity: Instant,
    last_normal_inner_rect: Option<Rect>,
    last_normal_outer_rect: Option<Rect>,
    fullscreen: bool,
    smoke_started: Instant,
    last_ui_frame_ms: f64,
}

impl PlayerApp {
    fn new(
        creation_context: &eframe::CreationContext<'_>,
        cli: Cli,
        report_slot: Arc<Mutex<Option<SmokeReport>>>,
    ) -> Result<Self, Box<dyn std::error::Error + Send + Sync>> {
        if creation_context.gl.is_none() {
            return Err("eframe did not provide a glow context".into());
        }

        let stats = Arc::new(Mutex::new(SmokeStats::default()));
        let renderer = Arc::new(Mutex::new(
            VideoRenderer::create(&cli.media, &creation_context.egui_ctx, Arc::clone(&stats))
                .map_err(|error| format!("mpv initialization failed: {error}"))?,
        ));
        let now = Instant::now();

        Ok(Self {
            cli,
            renderer,
            stats,
            report_slot,
            timeline: synthetic_timeline(),
            danmaku_layout: DanmakuLayout::default(),
            overlay_clock: OverlayClock::new(now),
            posters: generate_posters(1_200),
            search: String::new(),
            snapshot: PlaybackSnapshot::default(),
            last_update: now,
            last_property_refresh: now - Duration::from_secs(1),
            last_pointer_activity: now,
            last_normal_inner_rect: None,
            last_normal_outer_rect: None,
            fullscreen: false,
            smoke_started: now,
            last_ui_frame_ms: 0.0,
        })
    }

    fn handle_shortcuts(&mut self, ctx: &egui::Context, now: Instant) {
        let toggle_fullscreen = ctx.input(|input| input.key_pressed(egui::Key::F));
        if toggle_fullscreen {
            self.toggle_fullscreen(ctx);
        }

        let toggle_pause = ctx.input(|input| input.key_pressed(egui::Key::Space));
        if toggle_pause {
            self.toggle_pause(now);
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
            input.pointer.delta() != Vec2::ZERO
                || input.pointer.any_down()
                || input.key_pressed(egui::Key::Space)
                || input.key_pressed(egui::Key::F)
        });
        if active {
            self.last_pointer_activity = Instant::now();
        }
    }

    fn refresh_snapshot(&mut self, now: Instant) {
        if now.saturating_duration_since(self.last_property_refresh) < Duration::from_millis(180) {
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
                    estimated_vf_fps: renderer.property_string("estimated-vf-fps"),
                    frame_drop_count: parse_u64(renderer.property_string("frame-drop-count")),
                    hwdec_current: renderer.property_string("hwdec-current"),
                    video_params: renderer.property_string("video-params"),
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

    fn with_renderer<T>(&self, f: impl FnOnce(&mut VideoRenderer) -> T) -> Option<T> {
        self.renderer
            .lock()
            .ok()
            .map(|mut renderer| f(&mut renderer))
    }

    fn show_top_bar(&mut self, ctx: &egui::Context) {
        TopBottomPanel::top("top_bar")
            .exact_height(54.0)
            .frame(Frame::NONE.fill(Color32::from_rgb(13, 15, 18)))
            .show(ctx, |ui| {
                ui.add_space(8.0);
                ui.horizontal_centered(|ui| {
                    ui.add_space(12.0);
                    ui.label(
                        RichText::new("Danmaku")
                            .font(FontId::proportional(22.0))
                            .strong()
                            .color(Color32::from_rgb(236, 240, 244)),
                    );
                    ui.add_space(18.0);
                    let search_width = (ui.available_width() * 0.48).clamp(240.0, 520.0);
                    let response = ui.add_sized(
                        [search_width, 34.0],
                        TextEdit::singleline(&mut self.search)
                            .hint_text("搜尋 / Search")
                            .desired_width(search_width),
                    );
                    ctx.send_viewport_cmd(ViewportCommand::IMEAllowed(response.has_focus()));
                    if response.has_focus() {
                        ctx.send_viewport_cmd(ViewportCommand::IMERect(response.rect));
                    }
                    ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                        ui.label(
                            RichText::new("egui + libmpv render API")
                                .color(Color32::from_rgb(128, 139, 151)),
                        );
                    });
                });
            });
    }

    fn show_library(&mut self, ctx: &egui::Context) {
        egui::SidePanel::left("library_panel")
            .resizable(true)
            .default_width(326.0)
            .width_range(240.0..=520.0)
            .frame(Frame::NONE.fill(Color32::from_rgb(17, 19, 24)))
            .show(ctx, |ui| {
                ui.add_space(12.0);
                ui.horizontal(|ui| {
                    ui.add_space(12.0);
                    ui.label(
                        RichText::new("Library")
                            .font(FontId::proportional(18.0))
                            .strong()
                            .color(Color32::from_rgb(225, 230, 236)),
                    );
                    ui.label(
                        RichText::new(format!("{} titles", self.posters.len()))
                            .small()
                            .color(Color32::from_rgb(121, 132, 145)),
                    );
                });
                ui.add_space(10.0);

                let card_width = 118.0;
                let card_height = 178.0;
                let gap = 10.0;
                let available_width = ui.available_width().max(card_width);
                let columns = ((available_width + gap) / (card_width + gap))
                    .floor()
                    .max(1.0) as usize;
                let row_height = card_height + 14.0;
                let rows = self.posters.len().div_ceil(columns);

                egui::ScrollArea::vertical()
                    .id_salt("library_grid")
                    .auto_shrink([false, false])
                    .show_rows(ui, row_height, rows, |ui, row_range| {
                        for row in row_range {
                            ui.horizontal(|ui| {
                                ui.add_space(8.0);
                                for column in 0..columns {
                                    let index = row * columns + column;
                                    if let Some(poster) = self.posters.get(index) {
                                        let (rect, response) = ui.allocate_exact_size(
                                            vec2(card_width, card_height),
                                            Sense::click(),
                                        );
                                        paint_poster(ui, rect, response.hovered(), poster);
                                        ui.add_space(gap);
                                    }
                                }
                            });
                        }
                    });
            });
    }

    fn show_video(
        &mut self,
        ctx: &egui::Context,
        now: Instant,
        frame_duration: Duration,
        overlay_position_s: f64,
    ) {
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

                let danmaku_stats = self.paint_danmaku(ui, rect, overlay_position_s);
                if let Ok(mut stats) = self.stats.lock() {
                    stats.record_active_danmaku(danmaku_stats.active);
                    stats.record_danmaku_motion(
                        frame_duration,
                        &danmaku_stats.velocity_observations,
                    );
                }
                self.paint_stats(ui, rect, danmaku_stats.active, overlay_position_s);
                self.show_controls(ui, rect, now, overlay_position_s);
            });
    }

    fn paint_danmaku(
        &self,
        ui: &egui::Ui,
        video_rect: Rect,
        overlay_position_s: f64,
    ) -> DanmakuPaintStats {
        let playback_ms = overlay_position_s.max(0.0) * 1000.0;
        let comments = self.danmaku_layout.visible_comments(
            &self.timeline,
            playback_ms,
            video_rect.width(),
            video_rect.height(),
        );
        let painter = ui.painter();
        let font = FontId::proportional(self.danmaku_layout.font_px);
        let mut velocity_observations = Vec::with_capacity(8);
        for comment in &comments {
            let alpha = (comment.opacity * 238.0).round().clamp(0.0, 238.0) as u8;
            let color = match comment.mode {
                DanmakuMode::Scroll => Color32::from_rgba_premultiplied(245, 248, 252, alpha),
                DanmakuMode::Top => Color32::from_rgba_premultiplied(135, 214, 255, alpha),
                DanmakuMode::Bottom => Color32::from_rgba_premultiplied(255, 216, 130, alpha),
            };
            let pos = video_rect.min + vec2(comment.x, comment.y);
            let width = estimate_text_width(&comment.event.text, self.danmaku_layout.font_px);
            if pos.x > video_rect.right() || pos.x + width < video_rect.left() {
                continue;
            }
            if velocity_observations.len() < 8
                && comment.mode == DanmakuMode::Scroll
                && comment.age_ms > 500.0
                && comment.age_ms < self.danmaku_layout.scroll_duration_ms as f64 - 500.0
                && pos.x >= video_rect.left()
                && pos.x + width <= video_rect.right()
            {
                velocity_observations.push(DanmakuVelocityObservation {
                    id: comment.event.id,
                    x: comment.x,
                });
            }
            for offset in [
                vec2(1.0, 0.0),
                vec2(-1.0, 0.0),
                vec2(0.0, 1.0),
                vec2(0.0, -1.0),
            ] {
                painter.text(
                    pos + offset,
                    Align2::LEFT_TOP,
                    &comment.event.text,
                    font.clone(),
                    Color32::from_rgba_premultiplied(0, 0, 0, alpha.saturating_sub(45)),
                );
            }
            painter.text(
                pos,
                Align2::LEFT_TOP,
                &comment.event.text,
                font.clone(),
                color,
            );
        }
        DanmakuPaintStats {
            active: comments.len(),
            velocity_observations,
        }
    }

    fn paint_stats(&self, ui: &egui::Ui, video_rect: Rect, active: usize, overlay_position_s: f64) {
        let painter = ui.painter();
        let width = 370.0_f32.min(video_rect.width() - 28.0).max(260.0);
        let rect = Rect::from_min_size(
            pos2(video_rect.right() - width - 14.0, video_rect.top() + 14.0),
            vec2(width, 166.0),
        );
        painter.rect_filled(rect, 8.0, Color32::from_rgba_premultiplied(8, 10, 13, 190));
        painter.rect_stroke(
            rect,
            8.0,
            Stroke::new(1.0, Color32::from_rgba_premultiplied(255, 255, 255, 28)),
            StrokeKind::Inside,
        );

        let lines = [
            format!(
                "fps {}  dropped {}  UI {:.2} ms",
                self.snapshot.estimated_vf_fps.as_deref().unwrap_or("?"),
                self.snapshot
                    .frame_drop_count
                    .map(|value| value.to_string())
                    .unwrap_or_else(|| "?".to_owned()),
                self.last_ui_frame_ms
            ),
            format!(
                "time {} / {}  speed {:.2}x",
                format_time(overlay_position_s),
                format_time(self.snapshot.duration_s),
                self.snapshot.speed
            ),
            format!(
                "hwdec {}  active danmaku {}",
                self.snapshot
                    .hwdec_current
                    .as_deref()
                    .filter(|value| !value.is_empty())
                    .unwrap_or("unavailable"),
                active
            ),
            format!(
                "cache {}  pause {}",
                self.snapshot
                    .cache_duration_s
                    .map(|value| format!("{value:.1}s"))
                    .unwrap_or_else(|| "?".to_owned()),
                if self.snapshot.paused { "yes" } else { "no" }
            ),
            format!(
                "video {}",
                self.snapshot
                    .video_params
                    .as_deref()
                    .unwrap_or("params unavailable")
            ),
        ];
        for (index, line) in lines.iter().enumerate() {
            painter.text(
                rect.min + vec2(12.0, 12.0 + index as f32 * 27.0),
                Align2::LEFT_TOP,
                line,
                FontId::proportional(13.0),
                Color32::from_rgb(222, 228, 235),
            );
        }

        if let Some(error) = &self.snapshot.render_error {
            painter.text(
                rect.left_bottom() + vec2(12.0, -24.0),
                Align2::LEFT_BOTTOM,
                error,
                FontId::proportional(12.0),
                Color32::from_rgb(255, 126, 126),
            );
        }
    }

    fn show_controls(
        &mut self,
        ui: &mut egui::Ui,
        video_rect: Rect,
        now: Instant,
        overlay_position_s: f64,
    ) {
        let inactive_for = self.last_pointer_activity.elapsed().as_secs_f32();
        let alpha = if inactive_for <= 1.7 {
            1.0
        } else {
            (1.0 - (inactive_for - 1.7) / 1.1).clamp(0.0, 1.0)
        };
        if alpha <= 0.02 {
            return;
        }

        let height = 92.0;
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
                1.0,
                Color32::from_rgba_premultiplied(255, 255, 255, (alpha * 36.0) as u8),
            ),
            StrokeKind::Inside,
        );

        ui.scope_builder(egui::UiBuilder::new().max_rect(rect.shrink(14.0)), |ui| {
            ui.set_clip_rect(rect);
            ui.horizontal_centered(|ui| {
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
                if ui.button("0.5x").clicked() {
                    self.set_playback_rate(0.5, now);
                }
                if ui.button("1x").clicked() {
                    self.set_playback_rate(1.0, now);
                }
                if ui.button("1.5x").clicked() {
                    self.set_playback_rate(1.5, now);
                }
                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    ui.label(
                        RichText::new(format!(
                            "{} / {}",
                            format_time(overlay_position_s),
                            format_time(self.snapshot.duration_s)
                        ))
                        .color(Color32::from_rgb(214, 221, 229)),
                    );
                });
            });
            ui.add_space(10.0);
            let duration = self.snapshot.duration_s.max(1.0) as f32;
            let mut position = overlay_position_s.clamp(0.0, duration as f64) as f32;
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
            let buffered = self
                .snapshot
                .cache_duration_s
                .map(|value| format!("buffer +{value:.1}s"))
                .unwrap_or_else(|| "buffer unavailable".to_owned());
            ui.label(
                RichText::new(buffered)
                    .small()
                    .color(Color32::from_rgb(137, 149, 164)),
            );
        });
    }

    fn finish_smoke_if_needed(&mut self, ctx: &egui::Context) {
        let Some(duration) = self.cli.smoke else {
            return;
        };
        if self.smoke_started.elapsed() < duration {
            return;
        }
        let mut stats = self
            .stats
            .lock()
            .map(|stats| stats.clone())
            .unwrap_or_default();
        stats.dropped_frames = self.snapshot.frame_drop_count;
        stats.hwdec_current = self.snapshot.hwdec_current.clone();
        let report = SmokeReport::from_stats(&self.cli.media, duration, &stats);
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
        let frame_duration = now.saturating_duration_since(self.last_update);
        self.last_update = now;
        self.last_ui_frame_ms = frame_duration.as_secs_f64() * 1000.0;
        if let Ok(mut stats) = self.stats.lock() {
            stats.record_ui_frame(frame_duration);
        }

        self.handle_shortcuts(ctx, now);
        self.record_activity(ctx);
        self.refresh_snapshot(now);
        let overlay_position_s = self.overlay_clock.position_at(now);
        self.show_top_bar(ctx);
        self.show_library(ctx);
        self.show_video(ctx, now, frame_duration, overlay_position_s);
        self.finish_smoke_if_needed(ctx);
        if self.snapshot.paused {
            ctx.request_repaint_after(Duration::from_millis(100));
        } else {
            ctx.request_repaint();
        }
    }
}

#[derive(Clone, Debug)]
struct PlaybackSnapshot {
    position_s: f64,
    duration_s: f64,
    paused: bool,
    speed: f64,
    estimated_vf_fps: Option<String>,
    frame_drop_count: Option<u64>,
    hwdec_current: Option<String>,
    video_params: Option<String>,
    cache_duration_s: Option<f64>,
    render_error: Option<String>,
}

impl Default for PlaybackSnapshot {
    fn default() -> Self {
        Self {
            position_s: 0.0,
            duration_s: 3600.0,
            paused: false,
            speed: 1.0,
            estimated_vf_fps: None,
            frame_drop_count: None,
            hwdec_current: None,
            video_params: None,
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

#[derive(Clone, Debug)]
struct DanmakuPaintStats {
    active: usize,
    velocity_observations: Vec<DanmakuVelocityObservation>,
}

#[derive(Clone, Debug)]
struct Poster {
    title: String,
    color_a: Color32,
    color_b: Color32,
}

fn configure_fonts(ctx: &egui::Context) {
    let mut fonts = FontDefinitions::default();
    let name = "noto-sans-cjk-tc".to_owned();
    fonts.font_data.insert(
        name.clone(),
        Arc::new(FontData::from_static(include_bytes!(
            "../assets/NotoSansCJKtc-Regular.otf"
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

fn configure_style(ctx: &egui::Context) {
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

fn generate_posters(count: usize) -> Vec<Poster> {
    (0..count)
        .map(|index| {
            let seed = index as u32;
            let a = color_from_seed(seed.wrapping_mul(73).wrapping_add(19));
            let b = color_from_seed(seed.wrapping_mul(137).wrapping_add(91));
            Poster {
                title: format!("Episode Collection {:04}", index + 1),
                color_a: a,
                color_b: b,
            }
        })
        .collect()
}

fn color_from_seed(seed: u32) -> Color32 {
    let r = 52 + (seed.wrapping_mul(37) % 154) as u8;
    let g = 54 + (seed.wrapping_mul(61) % 142) as u8;
    let b = 62 + (seed.wrapping_mul(97) % 148) as u8;
    Color32::from_rgb(r, g, b)
}

fn paint_poster(ui: &egui::Ui, rect: Rect, hovered: bool, poster: &Poster) {
    let painter = ui.painter();
    let rounding = 8.0;
    painter.rect_filled(rect, rounding, Color32::from_rgb(25, 28, 34));

    let image_rect = Rect::from_min_max(rect.min + vec2(7.0, 7.0), rect.max - vec2(7.0, 38.0));
    let mut mesh = egui::Mesh::default();
    let base = mesh.vertices.len() as u32;
    mesh.colored_vertex(image_rect.left_top(), poster.color_a);
    mesh.colored_vertex(image_rect.right_top(), poster.color_b);
    mesh.colored_vertex(image_rect.left_bottom(), poster.color_b);
    mesh.colored_vertex(image_rect.right_bottom(), poster.color_a);
    mesh.add_triangle(base, base + 1, base + 2);
    mesh.add_triangle(base + 2, base + 1, base + 3);
    painter.add(egui::Shape::mesh(mesh));

    painter.rect_filled(
        Rect::from_min_max(
            image_rect.left_bottom() - vec2(0.0, 36.0),
            image_rect.right_bottom(),
        ),
        0.0,
        Color32::from_rgba_premultiplied(0, 0, 0, 64),
    );
    painter.text(
        image_rect.center(),
        Align2::CENTER_CENTER,
        format!(
            "{:02}",
            poster
                .title
                .bytes()
                .fold(0_u32, |acc, value| acc + value as u32)
                % 99
        ),
        FontId::proportional(32.0),
        Color32::from_rgba_premultiplied(255, 255, 255, 210),
    );
    painter.text(
        pos2(rect.left() + 9.0, rect.bottom() - 28.0),
        Align2::LEFT_TOP,
        &poster.title,
        FontId::proportional(12.5),
        Color32::from_rgb(224, 229, 235),
    );

    let stroke_color = if hovered {
        Color32::from_rgb(119, 184, 238)
    } else {
        Color32::from_rgba_premultiplied(255, 255, 255, 24)
    };
    painter.rect_stroke(
        rect,
        rounding,
        Stroke::new(1.2, stroke_color),
        StrokeKind::Inside,
    );
}

fn parse_f64(value: Option<String>) -> Option<f64> {
    value?.parse::<f64>().ok().filter(|value| value.is_finite())
}

fn parse_u64(value: Option<String>) -> Option<u64> {
    value?.parse::<u64>().ok()
}

fn format_time(seconds: f64) -> String {
    if !seconds.is_finite() || seconds < 0.0 {
        return "--:--".to_owned();
    }
    let total = seconds.round() as u64;
    let hours = total / 3600;
    let minutes = (total % 3600) / 60;
    let seconds = total % 60;
    if hours > 0 {
        format!("{hours}:{minutes:02}:{seconds:02}")
    } else {
        format!("{minutes:02}:{seconds:02}")
    }
}
