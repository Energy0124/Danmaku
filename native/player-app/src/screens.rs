//! Library-mode screens: server connect and catalog browse.

use eframe::egui::{
    self, Align, Align2, Color32, CursorIcon, FontId, Frame, Layout, Rect, RichText, Sense,
    TextEdit, vec2,
};

use crate::{
    discovery::DiscoveredServer,
    library::{
        DEFAULT_NEXT_UP_LIMIT, LibraryCatalog, MINIMUM_REMAINING_MS, MINIMUM_RESUME_POSITION_MS,
        MediaItem, NextUpItem, NextUpReason, PlaybackProgress, ProgressItem, Series,
        continue_watching_items, grouped_series, next_up_items,
    },
    posters::{PosterCache, PosterState},
    session::LibrarySession,
    theme::{self, metrics, palette, typography},
};

const CARD_WIDTH: f32 = 132.0;
const CARD_HEIGHT: f32 = 198.0;
const CARD_GAP: f32 = 12.0;
const RAIL_LIMIT: usize = 12;

// ---------------------------------------------------------------------------
// Connect screen
// ---------------------------------------------------------------------------

#[derive(Clone, Debug, PartialEq)]
pub struct ConnectRequest {
    pub base_url: String,
    pub pairing_token: Option<String>,
}

#[derive(Default)]
pub struct ConnectScreen {
    pub manual_url: String,
    pub manual_token: String,
    pub error: Option<String>,
}

impl ConnectScreen {
    pub fn show(
        &mut self,
        ctx: &egui::Context,
        discovered: &[DiscoveredServer],
    ) -> Option<ConnectRequest> {
        let mut request = None;
        egui::CentralPanel::default()
            .frame(Frame::NONE.fill(palette::BG_PANEL))
            .show(ctx, |ui| {
                let panel_width = 460.0_f32.min(ui.available_width() - 2.0 * metrics::GUTTER);
                ui.vertical_centered(|ui| {
                    ui.add_space(48.0);
                    ui.label(
                        RichText::new("Danmaku")
                            .font(FontId::proportional(30.0))
                            .strong()
                            .color(palette::TEXT_PRIMARY),
                    );
                    ui.label(
                        RichText::new("Connect to a library server")
                            .font(typography::body())
                            .color(palette::TEXT_MUTED),
                    );
                    ui.add_space(24.0);

                    ui.scope(|ui| {
                        ui.set_max_width(panel_width);
                        ui.vertical(|ui| {
                            ui.label(
                                RichText::new("Discovered on this network")
                                    .font(typography::heading())
                                    .color(palette::TEXT_SECONDARY),
                            );
                            ui.add_space(6.0);
                            if discovered.is_empty() {
                                ui.label(
                                    RichText::new("Listening for servers…")
                                        .font(typography::caption())
                                        .color(palette::TEXT_MUTED),
                                );
                            }
                            for server in discovered {
                                if ui
                                    .add_sized(
                                        [panel_width, 34.0],
                                        egui::Button::new(
                                            RichText::new(&server.base_url)
                                                .font(typography::body()),
                                        ),
                                    )
                                    .clicked()
                                {
                                    request = Some(ConnectRequest {
                                        base_url: server.base_url.clone(),
                                        pairing_token: token_or_none(&self.manual_token),
                                    });
                                }
                            }

                            ui.add_space(18.0);
                            ui.label(
                                RichText::new("Manual connection")
                                    .font(typography::heading())
                                    .color(palette::TEXT_SECONDARY),
                            );
                            ui.add_space(6.0);
                            ui.add(
                                TextEdit::singleline(&mut self.manual_url)
                                    .hint_text("http://192.168.1.10:8686")
                                    .desired_width(panel_width),
                            );
                            ui.add_space(4.0);
                            ui.add(
                                TextEdit::singleline(&mut self.manual_token)
                                    .hint_text("Pairing token (optional)")
                                    .desired_width(panel_width),
                            );
                            ui.add_space(10.0);
                            let connect_enabled = self.manual_url.trim().starts_with("http://");
                            if ui
                                .add_enabled(
                                    connect_enabled,
                                    egui::Button::new(
                                        RichText::new("Connect").font(typography::body()),
                                    )
                                    .min_size(vec2(panel_width, 36.0)),
                                )
                                .clicked()
                            {
                                request = Some(ConnectRequest {
                                    base_url: self.manual_url.trim().to_owned(),
                                    pairing_token: token_or_none(&self.manual_token),
                                });
                            }
                            if let Some(error) = &self.error {
                                ui.add_space(8.0);
                                ui.label(
                                    RichText::new(error)
                                        .font(typography::caption())
                                        .color(palette::DANGER),
                                );
                            }
                        });
                    });
                });
            });
        request
    }
}

fn token_or_none(token: &str) -> Option<String> {
    let token = token.trim();
    (!token.is_empty()).then(|| token.to_owned())
}

// ---------------------------------------------------------------------------
// Library screen
// ---------------------------------------------------------------------------

#[derive(Clone, Debug, PartialEq)]
pub enum LibraryAction {
    Play { media_id: String },
    Refresh,
    Disconnect,
}

#[derive(Clone, Debug, PartialEq, Eq)]
enum LibraryView {
    Home,
    Series(String),
}

pub struct LibraryScreen {
    query: String,
    view: LibraryView,
    cached_series: Vec<Series>,
    cached_catalog_stamp: i64,
}

impl Default for LibraryScreen {
    fn default() -> Self {
        Self {
            query: String::new(),
            view: LibraryView::Home,
            cached_series: Vec::new(),
            cached_catalog_stamp: i64::MIN,
        }
    }
}

impl LibraryScreen {
    pub fn show(
        &mut self,
        ctx: &egui::Context,
        session: &LibrarySession,
        posters: &mut PosterCache,
    ) -> Option<LibraryAction> {
        let mut action = None;
        egui::TopBottomPanel::top("library_top")
            .exact_height(56.0)
            .frame(Frame::NONE.fill(palette::BG_DEEP))
            .show(ctx, |ui| {
                ui.horizontal_centered(|ui| {
                    ui.add_space(metrics::GUTTER);
                    if self.view != LibraryView::Home && ui.button("< Back").clicked() {
                        self.view = LibraryView::Home;
                    }
                    ui.label(
                        RichText::new("Danmaku")
                            .font(FontId::proportional(20.0))
                            .strong()
                            .color(palette::TEXT_PRIMARY),
                    );
                    ui.add_space(12.0);
                    let search_width = (ui.available_width() * 0.4).clamp(200.0, 460.0);
                    let response = ui.add_sized(
                        [search_width, 32.0],
                        TextEdit::singleline(&mut self.query).hint_text("搜尋 / Search"),
                    );
                    ctx.send_viewport_cmd(egui::ViewportCommand::IMEAllowed(response.has_focus()));
                    if response.has_focus() {
                        ctx.send_viewport_cmd(egui::ViewportCommand::IMERect(response.rect));
                    }
                    ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                        ui.add_space(metrics::GUTTER);
                        if ui.button("Disconnect").clicked() {
                            action = Some(LibraryAction::Disconnect);
                        }
                        if ui.button("Refresh").clicked() {
                            action = Some(LibraryAction::Refresh);
                        }
                        ui.label(
                            RichText::new(&session.base_url)
                                .font(typography::caption())
                                .color(palette::TEXT_MUTED),
                        );
                    });
                });
            });

        egui::CentralPanel::default()
            .frame(Frame::NONE.fill(palette::BG_PANEL))
            .show(ctx, |ui| {
                let Some(catalog) = &session.catalog else {
                    ui.centered_and_justified(|ui| {
                        let message = match &session.catalog_error {
                            Some(error) => format!("Failed to load library: {error}"),
                            None => "Loading library…".to_owned(),
                        };
                        let color = if session.catalog_error.is_some() {
                            palette::DANGER
                        } else {
                            palette::TEXT_MUTED
                        };
                        ui.label(RichText::new(message).font(typography::body()).color(color));
                    });
                    return;
                };
                self.refresh_series_cache(catalog);

                let inner_action = if !self.query.trim().is_empty() {
                    self.show_search_results(ui, catalog, posters)
                } else {
                    match self.view.clone() {
                        LibraryView::Home => self.show_home(ui, catalog, session, posters),
                        LibraryView::Series(series_id) => {
                            self.show_series(ui, &series_id, &session.progresses, posters)
                        }
                    }
                };
                if inner_action.is_some() {
                    action = inner_action;
                }
            });
        action
    }

    fn refresh_series_cache(&mut self, catalog: &LibraryCatalog) {
        let stamp = catalog.indexed_at_epoch_ms ^ (catalog.items.len() as i64) << 20;
        if stamp != self.cached_catalog_stamp {
            self.cached_series = grouped_series(catalog);
            self.cached_catalog_stamp = stamp;
        }
    }

    fn show_home(
        &mut self,
        ui: &mut egui::Ui,
        catalog: &LibraryCatalog,
        session: &LibrarySession,
        posters: &mut PosterCache,
    ) -> Option<LibraryAction> {
        let mut action = None;
        let continue_watching = continue_watching_items(
            catalog,
            &session.progresses,
            RAIL_LIMIT,
            MINIMUM_RESUME_POSITION_MS,
            MINIMUM_REMAINING_MS,
        );
        let next_up = next_up_items(
            catalog,
            &session.progresses,
            DEFAULT_NEXT_UP_LIMIT,
            MINIMUM_RESUME_POSITION_MS,
            MINIMUM_REMAINING_MS,
        );

        egui::ScrollArea::vertical()
            .id_salt("library_home")
            .auto_shrink([false, false])
            .show(ui, |ui| {
                ui.add_space(12.0);
                if !continue_watching.is_empty() {
                    section_heading(ui, "Continue watching");
                    if let Some(clicked) = continue_watching_rail(ui, &continue_watching, posters) {
                        action = Some(LibraryAction::Play { media_id: clicked });
                    }
                    ui.add_space(16.0);
                }
                if !next_up.is_empty() {
                    section_heading(ui, "Next up");
                    if let Some(clicked) = next_up_rail(ui, &next_up, posters) {
                        action = Some(LibraryAction::Play { media_id: clicked });
                    }
                    ui.add_space(16.0);
                }

                section_heading(
                    ui,
                    &format!(
                        "All series - {} titles, {} episodes",
                        self.cached_series.len(),
                        catalog.items.len()
                    ),
                );
                if let Some(series_id) = series_grid(ui, &self.cached_series, posters) {
                    self.view = LibraryView::Series(series_id);
                }
                ui.add_space(24.0);
            });
        action
    }

    fn show_search_results(
        &mut self,
        ui: &mut egui::Ui,
        catalog: &LibraryCatalog,
        posters: &mut PosterCache,
    ) -> Option<LibraryAction> {
        let query = self.query.trim().to_lowercase();
        let mut action = None;

        let matching_series: Vec<&Series> = self
            .cached_series
            .iter()
            .filter(|series| series.title.to_lowercase().contains(&query))
            .take(60)
            .collect();
        let matching_episodes: Vec<&MediaItem> = catalog
            .items
            .iter()
            .filter(|item| {
                item.episode_title.to_lowercase().contains(&query)
                    || item.relative_path.to_lowercase().contains(&query)
            })
            .take(200)
            .collect();

        egui::ScrollArea::vertical()
            .id_salt("library_search")
            .auto_shrink([false, false])
            .show(ui, |ui| {
                ui.add_space(12.0);
                section_heading(ui, &format!("Series matching \"{}\"", self.query.trim()));
                if matching_series.is_empty() {
                    muted_line(ui, "No matching series.");
                } else {
                    let owned: Vec<Series> = matching_series.into_iter().cloned().collect();
                    if let Some(series_id) = series_grid(ui, &owned, posters) {
                        self.query.clear();
                        self.view = LibraryView::Series(series_id);
                    }
                }
                ui.add_space(16.0);
                section_heading(ui, "Episodes");
                if matching_episodes.is_empty() {
                    muted_line(ui, "No matching episodes.");
                }
                for item in matching_episodes {
                    if episode_row(ui, item, None).clicked() {
                        action = Some(LibraryAction::Play {
                            media_id: item.id.clone(),
                        });
                    }
                }
                ui.add_space(24.0);
            });
        action
    }

    fn show_series(
        &mut self,
        ui: &mut egui::Ui,
        series_id: &str,
        progresses: &[PlaybackProgress],
        posters: &mut PosterCache,
    ) -> Option<LibraryAction> {
        let Some(series) = self
            .cached_series
            .iter()
            .find(|series| series.id == series_id)
            .cloned()
        else {
            self.view = LibraryView::Home;
            return None;
        };
        let mut action = None;
        let latest: std::collections::HashMap<&str, &PlaybackProgress> = {
            let mut map: std::collections::HashMap<&str, &PlaybackProgress> =
                std::collections::HashMap::new();
            for progress in progresses {
                match map.get(progress.media_id.as_str()) {
                    Some(existing)
                        if existing.updated_at_epoch_ms >= progress.updated_at_epoch_ms => {}
                    _ => {
                        map.insert(progress.media_id.as_str(), progress);
                    }
                }
            }
            map
        };

        egui::ScrollArea::vertical()
            .id_salt("library_series")
            .auto_shrink([false, false])
            .show(ui, |ui| {
                ui.add_space(12.0);
                ui.horizontal(|ui| {
                    ui.add_space(metrics::GUTTER);
                    let poster_item = series.items().next().cloned();
                    if let Some(item) = poster_item {
                        poster_thumbnail(ui, &item, posters, vec2(96.0, 144.0));
                    }
                    ui.vertical(|ui| {
                        ui.label(
                            RichText::new(&series.title)
                                .font(FontId::proportional(22.0))
                                .strong()
                                .color(palette::TEXT_PRIMARY),
                        );
                        muted_line(ui, &format!("{} episodes", series.episode_count()));
                    });
                });
                ui.add_space(12.0);
                for season in &series.seasons {
                    if series.seasons.len() > 1 {
                        section_heading(ui, &season.label);
                    }
                    for item in &season.items {
                        let progress = latest.get(item.id.as_str()).copied();
                        if episode_row(ui, item, progress).clicked() {
                            action = Some(LibraryAction::Play {
                                media_id: item.id.clone(),
                            });
                        }
                    }
                    ui.add_space(10.0);
                }
                ui.add_space(24.0);
            });
        action
    }
}

// ---------------------------------------------------------------------------
// Shared widgets
// ---------------------------------------------------------------------------

fn section_heading(ui: &mut egui::Ui, text: &str) {
    ui.horizontal(|ui| {
        ui.add_space(metrics::GUTTER);
        ui.label(
            RichText::new(text)
                .font(typography::heading())
                .strong()
                .color(palette::TEXT_SECONDARY),
        );
    });
    ui.add_space(6.0);
}

fn muted_line(ui: &mut egui::Ui, text: &str) {
    ui.horizontal(|ui| {
        ui.add_space(metrics::GUTTER);
        ui.label(
            RichText::new(text)
                .font(typography::caption())
                .color(palette::TEXT_MUTED),
        );
    });
}

fn continue_watching_rail(
    ui: &mut egui::Ui,
    entries: &[ProgressItem],
    posters: &mut PosterCache,
) -> Option<String> {
    let mut clicked = None;
    egui::ScrollArea::horizontal()
        .id_salt("rail_continue")
        .show(ui, |ui| {
            ui.horizontal(|ui| {
                ui.add_space(metrics::GUTTER);
                for entry in entries {
                    let fraction = entry
                        .progress
                        .duration_ms
                        .filter(|duration| *duration > 0)
                        .map(|duration| {
                            (entry.progress.position_ms as f32 / duration as f32).clamp(0.0, 1.0)
                        });
                    if poster_card(ui, &entry.item, posters, fraction, None).clicked() {
                        clicked = Some(entry.item.id.clone());
                    }
                }
            });
        });
    clicked
}

fn next_up_rail(
    ui: &mut egui::Ui,
    entries: &[NextUpItem],
    posters: &mut PosterCache,
) -> Option<String> {
    let mut clicked = None;
    egui::ScrollArea::horizontal()
        .id_salt("rail_next_up")
        .show(ui, |ui| {
            ui.horizontal(|ui| {
                ui.add_space(metrics::GUTTER);
                for entry in entries {
                    let badge = match entry.reason {
                        NextUpReason::Resume => "Resume",
                        NextUpReason::NextEpisode => "Next",
                        NextUpReason::Start => "Start",
                    };
                    if poster_card(ui, &entry.item, posters, None, Some(badge)).clicked() {
                        clicked = Some(entry.item.id.clone());
                    }
                }
            });
        });
    clicked
}

fn series_grid(ui: &mut egui::Ui, series: &[Series], posters: &mut PosterCache) -> Option<String> {
    let mut clicked = None;
    let available_width = ui.available_width() - 2.0 * metrics::GUTTER;
    let columns = ((available_width + CARD_GAP) / (CARD_WIDTH + CARD_GAP))
        .floor()
        .max(1.0) as usize;
    let rows = series.len().div_ceil(columns);
    let row_height = CARD_HEIGHT + CARD_GAP;

    // The grid lives inside the page ScrollArea, so paint rows directly;
    // per-row culling comes from ScrollArea clipping and cheap card paint.
    for row in 0..rows {
        let row_rect = Rect::from_min_size(ui.cursor().min, vec2(available_width, row_height));
        if ui.is_rect_visible(row_rect) {
            ui.horizontal(|ui| {
                ui.add_space(metrics::GUTTER);
                for column in 0..columns {
                    let index = row * columns + column;
                    let Some(series) = series.get(index) else {
                        break;
                    };
                    let representative = series.items().next().cloned();
                    if let Some(item) = representative {
                        let response = poster_card_with_title(
                            ui,
                            &item,
                            &series.title,
                            &format!("{} eps", series.episode_count()),
                            posters,
                        );
                        if response.clicked() {
                            clicked = Some(series.id.clone());
                        }
                    }
                }
            });
            ui.add_space(CARD_GAP);
        } else {
            ui.allocate_space(vec2(available_width, row_height));
        }
    }
    clicked
}

fn poster_card(
    ui: &mut egui::Ui,
    item: &MediaItem,
    posters: &mut PosterCache,
    progress_fraction: Option<f32>,
    badge: Option<&str>,
) -> egui::Response {
    let (rect, response) = ui.allocate_exact_size(vec2(CARD_WIDTH, CARD_HEIGHT), Sense::click());
    if !ui.is_rect_visible(rect) {
        return response;
    }
    paint_card_background(ui, rect, response.hovered());
    let image_rect = Rect::from_min_max(
        rect.min + vec2(6.0, 6.0),
        egui::pos2(rect.max.x - 6.0, rect.max.y - 44.0),
    );
    paint_poster_area(ui, image_rect, item, posters);

    if let Some(fraction) = progress_fraction {
        let bar = Rect::from_min_max(
            egui::pos2(image_rect.left(), image_rect.bottom() - 5.0),
            image_rect.right_bottom(),
        );
        ui.painter()
            .rect_filled(bar, 2.0, Color32::from_rgba_premultiplied(0, 0, 0, 140));
        let filled = Rect::from_min_size(bar.min, vec2(bar.width() * fraction, bar.height()));
        ui.painter().rect_filled(filled, 2.0, palette::ACCENT);
    }
    if let Some(badge) = badge {
        let badge_rect = Rect::from_min_size(image_rect.min + vec2(6.0, 6.0), vec2(52.0, 20.0));
        ui.painter().rect_filled(
            badge_rect,
            4.0,
            Color32::from_rgba_premultiplied(8, 10, 13, 200),
        );
        ui.painter().text(
            badge_rect.center(),
            Align2::CENTER_CENTER,
            badge,
            typography::small(),
            palette::TEXT_SECONDARY,
        );
    }

    let title = format!("{} - {}", item.series_title, item.episode_title);
    paint_card_caption(ui, rect, &title, None);
    if response.hovered() {
        ui.ctx().set_cursor_icon(CursorIcon::PointingHand);
    }
    response
}

fn poster_card_with_title(
    ui: &mut egui::Ui,
    representative: &MediaItem,
    title: &str,
    subtitle: &str,
    posters: &mut PosterCache,
) -> egui::Response {
    let (rect, response) = ui.allocate_exact_size(vec2(CARD_WIDTH, CARD_HEIGHT), Sense::click());
    if !ui.is_rect_visible(rect) {
        return response;
    }
    paint_card_background(ui, rect, response.hovered());
    let image_rect = Rect::from_min_max(
        rect.min + vec2(6.0, 6.0),
        egui::pos2(rect.max.x - 6.0, rect.max.y - 44.0),
    );
    paint_poster_area(ui, image_rect, representative, posters);
    paint_card_caption(ui, rect, title, Some(subtitle));
    if response.hovered() {
        ui.ctx().set_cursor_icon(CursorIcon::PointingHand);
    }
    response
}

fn paint_card_background(ui: &egui::Ui, rect: Rect, hovered: bool) {
    ui.painter()
        .rect_filled(rect, metrics::CARD_RADIUS, palette::SURFACE);
    ui.painter().rect_stroke(
        rect,
        metrics::CARD_RADIUS,
        theme::card_outline(if hovered { 1.0 } else { 0.0 }),
        egui::StrokeKind::Inside,
    );
}

fn paint_card_caption(ui: &egui::Ui, rect: Rect, title: &str, subtitle: Option<&str>) {
    let caption_rect = Rect::from_min_max(
        egui::pos2(rect.left() + 8.0, rect.bottom() - 40.0),
        rect.max - vec2(8.0, 6.0),
    );
    let painter = ui.painter().with_clip_rect(caption_rect);
    painter.text(
        caption_rect.left_top(),
        Align2::LEFT_TOP,
        title,
        typography::small(),
        palette::TEXT_PRIMARY,
    );
    if let Some(subtitle) = subtitle {
        painter.text(
            caption_rect.left_top() + vec2(0.0, 18.0),
            Align2::LEFT_TOP,
            subtitle,
            typography::small(),
            palette::TEXT_MUTED,
        );
    }
}

fn poster_thumbnail(
    ui: &mut egui::Ui,
    item: &MediaItem,
    posters: &mut PosterCache,
    size: egui::Vec2,
) {
    let (rect, _) = ui.allocate_exact_size(size, Sense::hover());
    if ui.is_rect_visible(rect) {
        paint_poster_area(ui, rect, item, posters);
    }
}

fn paint_poster_area(ui: &egui::Ui, rect: Rect, item: &MediaItem, posters: &mut PosterCache) {
    match posters.poster(&item.id, item.poster_path.as_deref()) {
        Some(PosterState::Ready(texture)) => {
            let size = texture.size_vec2();
            let scale = (rect.width() / size.x).max(rect.height() / size.y);
            let scaled = size * scale;
            let offset = (scaled - rect.size()) / 2.0;
            let uv_min = egui::pos2(
                (offset.x / scaled.x).clamp(0.0, 1.0),
                (offset.y / scaled.y).clamp(0.0, 1.0),
            );
            let uv = Rect::from_min_max(uv_min, egui::pos2(1.0 - uv_min.x, 1.0 - uv_min.y));
            ui.painter().image(texture.id(), rect, uv, Color32::WHITE);
        }
        _ => paint_initials_poster(ui, rect, &item.series_title),
    }
}

/// Fallback poster: gradient block with the series initials, matching the
/// desktop app's look for items without cached posters.
fn paint_initials_poster(ui: &egui::Ui, rect: Rect, series_title: &str) {
    let seed: u32 = series_title.bytes().fold(0_u32, |accumulator, byte| {
        accumulator.wrapping_mul(31).wrapping_add(byte as u32)
    });
    let top = color_from_seed(seed.wrapping_mul(73).wrapping_add(19));
    let bottom = color_from_seed(seed.wrapping_mul(137).wrapping_add(91));
    let mut mesh = egui::Mesh::default();
    let base = mesh.vertices.len() as u32;
    mesh.colored_vertex(rect.left_top(), top);
    mesh.colored_vertex(rect.right_top(), top);
    mesh.colored_vertex(rect.left_bottom(), bottom);
    mesh.colored_vertex(rect.right_bottom(), bottom);
    mesh.add_triangle(base, base + 1, base + 2);
    mesh.add_triangle(base + 2, base + 1, base + 3);
    ui.painter().add(egui::Shape::mesh(mesh));
    ui.painter().text(
        rect.center(),
        Align2::CENTER_CENTER,
        initials(series_title),
        FontId::proportional(26.0),
        Color32::from_rgba_premultiplied(255, 255, 255, 220),
    );
}

fn color_from_seed(seed: u32) -> Color32 {
    Color32::from_rgb(
        40 + (seed.wrapping_mul(37) % 120) as u8,
        44 + (seed.wrapping_mul(61) % 110) as u8,
        52 + (seed.wrapping_mul(97) % 120) as u8,
    )
}

pub(crate) fn initials(title: &str) -> String {
    title
        .split_whitespace()
        .filter(|word| !word.is_empty())
        .take(2)
        .filter_map(|word| word.chars().next())
        .collect::<String>()
        .to_uppercase()
}

fn episode_row(
    ui: &mut egui::Ui,
    item: &MediaItem,
    progress: Option<&PlaybackProgress>,
) -> egui::Response {
    let width = ui.available_width() - 2.0 * metrics::GUTTER;
    let (rect, response) =
        ui.allocate_exact_size(vec2(width + 2.0 * metrics::GUTTER, 44.0), Sense::click());
    if !ui.is_rect_visible(rect) {
        return response;
    }
    let row_rect = Rect::from_min_size(rect.min + vec2(metrics::GUTTER, 2.0), vec2(width, 40.0));
    let fill = if response.hovered() {
        palette::WIDGET_HOVER
    } else {
        palette::SURFACE
    };
    ui.painter().rect_filled(row_rect, 6.0, fill);
    ui.painter().text(
        row_rect.left_center() + vec2(12.0, 0.0),
        Align2::LEFT_CENTER,
        &item.episode_title,
        typography::body(),
        palette::TEXT_PRIMARY,
    );
    let status = match progress {
        Some(progress) => match progress.duration_ms {
            Some(duration) if duration > 0 => {
                let percent =
                    ((progress.position_ms as f64 / duration as f64) * 100.0).round() as i64;
                if duration - progress.position_ms < MINIMUM_REMAINING_MS {
                    "Watched".to_owned()
                } else {
                    format!("Resume {percent}%")
                }
            }
            _ => "Started".to_owned(),
        },
        None => String::new(),
    };
    if !status.is_empty() {
        ui.painter().text(
            row_rect.right_center() - vec2(12.0, 0.0),
            Align2::RIGHT_CENTER,
            status,
            typography::caption(),
            palette::TEXT_MUTED,
        );
    }
    if response.hovered() {
        ui.ctx().set_cursor_icon(CursorIcon::PointingHand);
    }
    response
}

#[cfg(test)]
mod tests {
    use super::initials;

    #[test]
    fn derives_initials_from_titles() {
        assert_eq!(initials("Example Show"), "ES");
        assert_eq!(initials("16bit Sensation"), "1S");
        assert_eq!(initials("約束のネバーランド"), "約");
        assert_eq!(initials(""), "");
    }
}
