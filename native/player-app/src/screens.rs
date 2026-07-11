//! Library-mode screens: server connect and catalog browse.

use std::path::PathBuf;

use eframe::egui::{
    self, Align, Align2, Color32, CursorIcon, FontId, Frame, Layout, Rect, RichText, Sense,
    TextEdit, pos2, vec2,
};

use crate::{
    discovery::DiscoveredServer,
    hosting::LocalHostStatus,
    icons::{Icon, paint_icon},
    library::{
        DEFAULT_NEXT_UP_LIMIT, LibraryCatalog, MINIMUM_REMAINING_MS, MINIMUM_RESUME_POSITION_MS,
        MediaItem, NextUpItem, NextUpReason, PlaybackProgress, ProgressItem, Series,
        continue_watching_items, grouped_series, next_up_items,
    },
    localization::{Language, Strings},
    posters::{PosterCache, PosterState},
    preferences::PlayerPreferences,
    session::LibrarySession,
    theme::{self, metrics, palette, typography},
};

const CARD_WIDTH: f32 = 148.0;
const CARD_HEIGHT: f32 = 222.0;
const CARD_GAP: f32 = 16.0;
const RAIL_LIMIT: usize = 12;

// ---------------------------------------------------------------------------
// Connect screen
// ---------------------------------------------------------------------------

#[derive(Clone, Debug, PartialEq)]
pub struct ConnectRequest {
    pub base_url: String,
    pub pairing_token: Option<String>,
}

#[derive(Clone, Debug, PartialEq)]
pub enum ConnectAction {
    Connect(ConnectRequest),
    StartLocal(PathBuf),
}

#[derive(Default)]
pub struct ConnectScreen {
    pub manual_url: String,
    pub manual_token: String,
    pub local_library_root: String,
    pub error: Option<String>,
    show_remote_options: bool,
}

impl ConnectScreen {
    pub fn show(
        &mut self,
        ctx: &egui::Context,
        discovered: &[DiscoveredServer],
        language: &mut Language,
        local_host_status: Option<&LocalHostStatus>,
    ) -> Option<ConnectAction> {
        let strings = Strings::new(*language);
        let mut action = None;

        egui::TopBottomPanel::bottom("connect_language")
            .exact_height(42.0)
            .frame(Frame::NONE.fill(palette::BG_DEEP))
            .show(ctx, |ui| {
                ui.horizontal_centered(|ui| {
                    for option in Language::ALL {
                        ui.selectable_value(language, option, option.native_name());
                    }
                });
            });
        egui::CentralPanel::default()
            .frame(Frame::NONE.fill(palette::BG_DEEP))
            .show(ctx, |ui| {
                egui::ScrollArea::vertical()
                    .auto_shrink([false, false])
                    .show(ui, |ui| {
                        let content_width = 640.0_f32.min(ui.available_width() - 32.0);
                        ui.vertical_centered(|ui| {
                            ui.add_space(24.0);
                            let (illustration, _) =
                                ui.allocate_exact_size(vec2(300.0, 138.0), Sense::hover());
                            paint_library_illustration(ui, illustration);

                            ui.add_space(12.0);
                            ui.label(
                                RichText::new(strings.welcome_title())
                                    .font(typography::display())
                                    .strong()
                                    .color(palette::TEXT_PRIMARY),
                            );
                            ui.add_space(4.0);
                            ui.label(
                                RichText::new(strings.welcome_body())
                                    .font(typography::body())
                                    .color(palette::TEXT_MUTED),
                            );
                            ui.add_space(20.0);

                            let status = local_host_status.unwrap_or(&LocalHostStatus::Unavailable);
                            let starting = matches!(status, LocalHostStatus::Starting);
                            let button_text = if starting {
                                strings.starting_local_server()
                            } else {
                                strings.choose_anime_folder()
                            };
                            let response = ui
                                .add_enabled_ui(!starting && status.is_available(), |ui| {
                                    labeled_icon_button(
                                        ui,
                                        Icon::Folder,
                                        button_text,
                                        vec2(380.0, 48.0),
                                        true,
                                    )
                                })
                                .inner;
                            if response.clicked()
                                && let Some(folder) = rfd::FileDialog::new()
                                    .set_title(strings.library_folder())
                                    .pick_folder()
                            {
                                self.local_library_root = folder.to_string_lossy().into_owned();
                                action = Some(ConnectAction::StartLocal(folder));
                            }

                            ui.add_space(18.0);
                            setup_progress(ui, strings, starting);
                            ui.add_space(16.0);

                            ui.scope(|ui| {
                                ui.set_max_width(content_width);
                                local_status_card(ui, status, strings);
                            });

                            if let Some(error) = &self.error {
                                ui.add_space(8.0);
                                ui.label(
                                    RichText::new(error)
                                        .font(typography::caption())
                                        .color(palette::DANGER),
                                );
                            }

                            ui.add_space(12.0);
                            if labeled_icon_button(
                                ui,
                                Icon::Swap,
                                strings.connect_another_server(),
                                vec2(300.0, 38.0),
                                false,
                            )
                            .clicked()
                            {
                                self.show_remote_options = !self.show_remote_options;
                            }

                            if self.show_remote_options {
                                ui.add_space(10.0);
                                ui.scope(|ui| {
                                    ui.set_max_width(460.0);
                                    remote_connection_panel(
                                        ui,
                                        self,
                                        discovered,
                                        strings,
                                        &mut action,
                                    );
                                });
                            }
                            ui.add_space(24.0);
                        });
                    });
            });
        action
    }
}

fn paint_brand_mark(ui: &mut egui::Ui, size: f32) {
    let (rect, _) = ui.allocate_exact_size(vec2(size, size), Sense::hover());
    ui.painter()
        .circle_filled(rect.center(), size * 0.5, palette::ACCENT_BRIGHT);
    let points = vec![
        rect.center() + vec2(-4.0, -7.0),
        rect.center() + vec2(8.0, 0.0),
        rect.center() + vec2(-4.0, 7.0),
    ];
    ui.painter().add(egui::Shape::convex_polygon(
        points,
        Color32::WHITE,
        egui::Stroke::NONE,
    ));
}

fn paint_library_illustration(ui: &egui::Ui, rect: Rect) {
    let painter = ui.painter();
    painter.circle_filled(
        rect.center() + vec2(0.0, 4.0),
        92.0,
        Color32::from_rgba_premultiplied(54, 112, 168, 26),
    );
    let folder = Rect::from_center_size(rect.center() + vec2(0.0, 18.0), vec2(190.0, 94.0));
    let tab = Rect::from_min_size(folder.min + vec2(18.0, -16.0), vec2(72.0, 28.0));
    painter.rect_filled(tab, 10.0, palette::ACCENT);
    painter.rect_filled(folder, 14.0, palette::SURFACE_RAISED);
    painter.rect_stroke(
        folder,
        14.0,
        egui::Stroke::new(1.5, palette::ACCENT_OUTLINE),
        egui::StrokeKind::Inside,
    );
    for (index, color) in [
        palette::ACCENT,
        Color32::from_rgb(83, 91, 132),
        Color32::from_rgb(56, 124, 132),
        Color32::from_rgb(125, 87, 135),
    ]
    .into_iter()
    .enumerate()
    {
        let x = folder.left() + 28.0 + index as f32 * 38.0;
        let card = Rect::from_min_size(egui::pos2(x, folder.top() - 24.0), vec2(28.0, 76.0));
        painter.rect_filled(card, 5.0, color);
        painter.line_segment(
            [
                card.left_bottom() + vec2(5.0, -12.0),
                card.right_bottom() + vec2(-5.0, -12.0),
            ],
            egui::Stroke::new(2.0, Color32::from_white_alpha(100)),
        );
    }
    painter.circle_filled(
        folder.right_bottom() - vec2(26.0, 24.0),
        18.0,
        palette::ACCENT_BRIGHT,
    );
    paint_icon(
        painter,
        Rect::from_center_size(folder.right_bottom() - vec2(26.0, 24.0), vec2(18.0, 18.0)),
        Icon::Play,
        Color32::WHITE,
        1.5,
    );
}

fn setup_progress(ui: &mut egui::Ui, strings: Strings, starting: bool) {
    let labels = [
        strings.setup_folder_step(),
        strings.setup_server_step(),
        strings.setup_ready_step(),
    ];
    let (rect, _) = ui.allocate_exact_size(vec2(460.0, 50.0), Sense::hover());
    let line_y = rect.top() + 14.0;
    let centers = [
        pos2(rect.left() + 28.0, line_y),
        pos2(rect.center().x, line_y),
        pos2(rect.right() - 28.0, line_y),
    ];
    ui.painter().line_segment(
        [centers[0], centers[2]],
        egui::Stroke::new(1.0, Color32::from_white_alpha(42)),
    );
    for (index, (center, label)) in centers.into_iter().zip(labels).enumerate() {
        let active = index == 0 || (starting && index == 1);
        let color = if active {
            palette::ACCENT_BRIGHT
        } else {
            palette::SURFACE_FAINT
        };
        ui.painter().circle_filled(center, 11.0, color);
        ui.painter().text(
            center,
            Align2::CENTER_CENTER,
            (index + 1).to_string(),
            typography::small(),
            if active {
                Color32::WHITE
            } else {
                palette::TEXT_MUTED
            },
        );
        ui.painter().text(
            pos2(center.x, rect.bottom()),
            Align2::CENTER_BOTTOM,
            label,
            typography::small(),
            if active {
                palette::ACCENT_OUTLINE
            } else {
                palette::TEXT_MUTED
            },
        );
    }
}
fn local_status_card(ui: &mut egui::Ui, status: &LocalHostStatus, strings: Strings) {
    let (rect, _) = ui.allocate_exact_size(vec2(460.0, 62.0), Sense::hover());
    ui.painter()
        .rect_filled(rect, 10.0, palette::SURFACE_RAISED);
    ui.painter().rect_stroke(
        rect,
        10.0,
        egui::Stroke::new(1.0, Color32::from_white_alpha(24)),
        egui::StrokeKind::Inside,
    );
    let dot = rect.left_center() + vec2(24.0, 0.0);
    let (color, detail) = match status {
        LocalHostStatus::Starting => (palette::ACCENT_OUTLINE, strings.starting_local_server()),
        LocalHostStatus::Running { .. } => (palette::SUCCESS, strings.ready_automatically()),
        LocalHostStatus::Failed(error) => (palette::DANGER, error.as_str()),
        LocalHostStatus::Unavailable => (palette::TEXT_MUTED, strings.local_server_unavailable()),
        LocalHostStatus::Stopped => (palette::TEXT_MUTED, strings.local_server_stopped()),
        LocalHostStatus::NeedsSetup => (palette::ACCENT_OUTLINE, strings.local_host_note()),
    };
    ui.painter().circle_filled(dot, 5.0, color);
    ui.painter().text(
        dot + vec2(16.0, -9.0),
        Align2::LEFT_CENTER,
        strings.local_hosting(),
        typography::body(),
        palette::TEXT_PRIMARY,
    );
    ui.painter().text(
        dot + vec2(16.0, 11.0),
        Align2::LEFT_CENTER,
        detail,
        typography::small(),
        palette::TEXT_MUTED,
    );
}

fn remote_connection_panel(
    ui: &mut egui::Ui,
    screen: &mut ConnectScreen,
    discovered: &[DiscoveredServer],
    strings: Strings,
    action: &mut Option<ConnectAction>,
) {
    ui.label(
        RichText::new(strings.other_servers())
            .font(typography::heading())
            .color(palette::TEXT_SECONDARY),
    );
    for server in discovered {
        if ui
            .add_sized(
                [460.0, 36.0],
                egui::Button::new(&server.base_url).fill(palette::SURFACE_RAISED),
            )
            .clicked()
        {
            *action = Some(ConnectAction::Connect(ConnectRequest {
                base_url: server.base_url.clone(),
                pairing_token: token_or_none(&screen.manual_token),
            }));
        }
    }
    if discovered.is_empty() {
        ui.label(
            RichText::new(strings.listening())
                .font(typography::caption())
                .color(palette::TEXT_MUTED),
        );
    }
    ui.add_space(8.0);
    ui.collapsing(strings.manual_connection(), |ui| {
        ui.add(
            TextEdit::singleline(&mut screen.manual_url)
                .hint_text("http://192.168.1.10:8686")
                .desired_width(460.0),
        );
        ui.add(
            TextEdit::singleline(&mut screen.manual_token)
                .hint_text(strings.pairing_token())
                .desired_width(460.0),
        );
        let enabled = screen.manual_url.trim().starts_with("http://");
        if ui
            .add_enabled(enabled, egui::Button::new(strings.connect()))
            .clicked()
        {
            *action = Some(ConnectAction::Connect(ConnectRequest {
                base_url: screen.manual_url.trim().to_owned(),
                pairing_token: token_or_none(&screen.manual_token),
            }));
        }
    });
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
    Settings,
}

#[derive(Clone, Debug, PartialEq, Eq)]
enum LibraryView {
    Home,
    AllSeries,
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
        strings: Strings,
    ) -> Option<LibraryAction> {
        let mut action = None;
        let search_id = egui::Id::new("library_search_field");

        egui::SidePanel::left("library_navigation")
            .exact_width(metrics::NAV_RAIL_WIDTH)
            .resizable(false)
            .frame(Frame::NONE.fill(palette::BG_NAV))
            .show(ctx, |ui| {
                ui.vertical_centered(|ui| {
                    ui.add_space(18.0);
                    paint_brand_mark(ui, 42.0);
                    ui.add_space(28.0);
                    if nav_button(
                        ui,
                        Icon::Home,
                        strings.home(),
                        self.view == LibraryView::Home && self.query.is_empty(),
                    )
                    .clicked()
                    {
                        self.view = LibraryView::Home;
                        self.query.clear();
                    }
                    ui.add_space(6.0);
                    if nav_button(
                        ui,
                        Icon::Library,
                        strings.library(),
                        self.view == LibraryView::AllSeries,
                    )
                    .clicked()
                    {
                        self.view = LibraryView::AllSeries;
                        self.query.clear();
                    }
                    ui.add_space(6.0);
                    if nav_button(ui, Icon::Search, strings.search(), !self.query.is_empty())
                        .clicked()
                    {
                        ctx.memory_mut(|memory| memory.request_focus(search_id));
                    }
                });
                ui.with_layout(Layout::bottom_up(Align::Center), |ui| {
                    ui.add_space(16.0);
                    if nav_button(ui, Icon::Power, strings.disconnect(), false).clicked() {
                        action = Some(LibraryAction::Disconnect);
                    }
                    if nav_button(ui, Icon::Settings, strings.settings(), false).clicked() {
                        action = Some(LibraryAction::Settings);
                    }
                    if nav_button(ui, Icon::Refresh, strings.refresh(), false).clicked() {
                        action = Some(LibraryAction::Refresh);
                    }
                });
            });

        egui::TopBottomPanel::top("library_header")
            .exact_height(metrics::LIBRARY_HEADER_HEIGHT)
            .frame(Frame::NONE.fill(palette::BG_PANEL))
            .show(ctx, |ui| {
                ui.horizontal_centered(|ui| {
                    ui.add_space(24.0);
                    ui.vertical(|ui| {
                        ui.add_space(15.0);
                        ui.label(
                            RichText::new(strings.your_library())
                                .font(typography::hero())
                                .strong()
                                .color(palette::TEXT_PRIMARY),
                        );
                        ui.label(
                            RichText::new(format!(
                                "{} {}  •  {} {}",
                                self.cached_series.len(),
                                strings.titles(),
                                session
                                    .catalog
                                    .as_ref()
                                    .map(|catalog| catalog.items.len())
                                    .unwrap_or_default(),
                                strings.episodes()
                            ))
                            .font(typography::caption())
                            .color(palette::TEXT_MUTED),
                        );
                    });
                    ui.add_space(28.0);
                    let search_width = (ui.available_width() * 0.42).clamp(220.0, 480.0);
                    let response = ui.add_sized(
                        [search_width, 38.0],
                        TextEdit::singleline(&mut self.query)
                            .id(search_id)
                            .hint_text(format!("      {}", strings.search()))
                            .background_color(palette::SURFACE_RAISED)
                            .text_color(palette::TEXT_PRIMARY),
                    );
                    paint_icon(
                        ui.painter(),
                        Rect::from_center_size(
                            pos2(response.rect.left() + 18.0, response.rect.center().y),
                            vec2(18.0, 18.0),
                        ),
                        Icon::Search,
                        palette::TEXT_MUTED,
                        1.4,
                    );
                    ctx.send_viewport_cmd(egui::ViewportCommand::IMEAllowed(response.has_focus()));
                    if response.has_focus() {
                        ctx.send_viewport_cmd(egui::ViewportCommand::IMERect(response.rect));
                    }
                    ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                        ui.add_space(24.0);
                        online_pill(ui, strings.library_online(), &session.base_url);
                        if matches!(self.view, LibraryView::Series(_))
                            && ui.button(strings.back()).clicked()
                        {
                            self.view = LibraryView::Home;
                        }
                    });
                });
            });

        egui::CentralPanel::default()
            .frame(Frame::NONE.fill(palette::BG_DEEP))
            .show(ctx, |ui| {
                let Some(catalog) = &session.catalog else {
                    ui.centered_and_justified(|ui| {
                        let message = match &session.catalog_error {
                            Some(error) => format!("{}: {error}", strings.failed_library()),
                            None => strings.loading_library().to_owned(),
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
                    self.show_search_results(ui, catalog, posters, strings)
                } else {
                    match self.view.clone() {
                        LibraryView::Home => self.show_home(ui, catalog, session, posters, strings),
                        LibraryView::AllSeries => self.show_all_series(ui, posters, strings),
                        LibraryView::Series(series_id) => {
                            self.show_series(ui, &series_id, &session.progresses, posters, strings)
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
        strings: Strings,
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
                ui.add_space(20.0);
                let mut featured_next_up = false;
                if let Some(featured) = continue_watching.first() {
                    if featured_media_card(
                        ui,
                        &featured.item,
                        Some(&featured.progress),
                        strings.continue_watching(),
                        posters,
                    )
                    .clicked()
                    {
                        action = Some(LibraryAction::Play {
                            media_id: featured.item.id.clone(),
                        });
                    }
                    ui.add_space(24.0);
                } else if let Some(featured) = next_up.first() {
                    featured_next_up = true;
                    if featured_media_card(
                        ui,
                        &featured.item,
                        featured.progress.as_ref(),
                        strings.next_up(),
                        posters,
                    )
                    .clicked()
                    {
                        action = Some(LibraryAction::Play {
                            media_id: featured.item.id.clone(),
                        });
                    }
                    ui.add_space(24.0);
                }

                if continue_watching.len() > 1 {
                    section_heading(ui, strings.continue_watching());
                    if let Some(clicked) =
                        continue_watching_rail(ui, &continue_watching[1..], posters)
                    {
                        action = Some(LibraryAction::Play { media_id: clicked });
                    }
                    ui.add_space(22.0);
                }
                let remaining_next_up = if featured_next_up {
                    &next_up[1..]
                } else {
                    next_up.as_slice()
                };
                if !remaining_next_up.is_empty() {
                    section_heading(ui, strings.next_up());
                    if let Some(clicked) = next_up_rail(ui, remaining_next_up, posters, strings) {
                        action = Some(LibraryAction::Play { media_id: clicked });
                    }
                    ui.add_space(22.0);
                }

                section_heading(
                    ui,
                    &format!(
                        "{}  ·  {} {}",
                        strings.recently_added(),
                        self.cached_series.len(),
                        strings.titles()
                    ),
                );
                if let Some(series_id) = series_grid(ui, &self.cached_series, posters, strings) {
                    self.view = LibraryView::Series(series_id);
                }
                ui.add_space(28.0);
            });
        action
    }
    fn show_all_series(
        &mut self,
        ui: &mut egui::Ui,
        posters: &mut PosterCache,
        strings: Strings,
    ) -> Option<LibraryAction> {
        egui::ScrollArea::vertical()
            .id_salt("library_all_series")
            .auto_shrink([false, false])
            .show(ui, |ui| {
                ui.add_space(20.0);
                section_heading(
                    ui,
                    &format!(
                        "{}  ·  {} {}",
                        strings.all_series(),
                        self.cached_series.len(),
                        strings.titles()
                    ),
                );
                if let Some(series_id) = series_grid(ui, &self.cached_series, posters, strings) {
                    self.view = LibraryView::Series(series_id);
                }
                ui.add_space(28.0);
            });
        None
    }

    fn show_search_results(
        &mut self,
        ui: &mut egui::Ui,
        catalog: &LibraryCatalog,
        posters: &mut PosterCache,
        strings: Strings,
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
                section_heading(
                    ui,
                    &format!("{} \"{}\"", strings.series_matching(), self.query.trim()),
                );
                if matching_series.is_empty() {
                    muted_line(ui, strings.no_series());
                } else {
                    let owned: Vec<Series> = matching_series.into_iter().cloned().collect();
                    if let Some(series_id) = series_grid(ui, &owned, posters, strings) {
                        self.query.clear();
                        self.view = LibraryView::Series(series_id);
                    }
                }
                ui.add_space(16.0);
                section_heading(ui, strings.episodes());
                if matching_episodes.is_empty() {
                    muted_line(ui, strings.no_episodes());
                }
                for item in matching_episodes {
                    if episode_row(ui, item, None, strings).clicked() {
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
        strings: Strings,
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
                        muted_line(
                            ui,
                            &format!("{} {}", series.episode_count(), strings.episodes()),
                        );
                    });
                });
                ui.add_space(12.0);
                for season in &series.seasons {
                    if series.seasons.len() > 1 {
                        section_heading(ui, &season.label);
                    }
                    for item in &season.items {
                        let progress = latest.get(item.id.as_str()).copied();
                        if episode_row(ui, item, progress, strings).clicked() {
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

fn nav_button(ui: &mut egui::Ui, icon: Icon, label: &str, selected: bool) -> egui::Response {
    let (rect, response) = ui.allocate_exact_size(vec2(68.0, 64.0), Sense::click());
    let fill = if selected {
        Color32::from_rgb(22, 42, 67)
    } else if response.hovered() {
        palette::SURFACE_RAISED
    } else {
        Color32::TRANSPARENT
    };
    ui.painter().rect_filled(rect, 10.0, fill);
    if selected {
        ui.painter().rect_filled(
            Rect::from_min_size(
                rect.left_top() + vec2(0.0, 8.0),
                vec2(3.0, rect.height() - 16.0),
            ),
            1.5,
            palette::ACCENT_BRIGHT,
        );
    }
    let color = if selected {
        palette::ACCENT_OUTLINE
    } else {
        palette::TEXT_MUTED
    };
    let icon_rect = Rect::from_center_size(rect.center_top() + vec2(0.0, 22.0), vec2(24.0, 24.0));
    paint_icon(ui.painter(), icon_rect, icon, color, 1.7);
    ui.painter().text(
        rect.center_bottom() - vec2(0.0, 12.0),
        Align2::CENTER_BOTTOM,
        label,
        typography::small(),
        color,
    );
    if response.hovered() {
        ui.ctx().set_cursor_icon(CursorIcon::PointingHand);
    }
    response
}

fn labeled_icon_button(
    ui: &mut egui::Ui,
    icon: Icon,
    label: &str,
    size: egui::Vec2,
    prominent: bool,
) -> egui::Response {
    let (rect, response) = ui.allocate_exact_size(size, Sense::click());
    let fill = if prominent {
        if response.hovered() {
            palette::ACCENT_OUTLINE
        } else {
            palette::ACCENT_BRIGHT
        }
    } else if response.hovered() {
        palette::SURFACE_RAISED
    } else {
        Color32::TRANSPARENT
    };
    ui.painter().rect_filled(rect, 8.0, fill);
    if !prominent {
        ui.painter().rect_stroke(
            rect,
            8.0,
            egui::Stroke::new(1.0, Color32::from_white_alpha(20)),
            egui::StrokeKind::Inside,
        );
    }
    let color = if prominent {
        Color32::WHITE
    } else {
        palette::ACCENT_OUTLINE
    };
    let icon_rect =
        Rect::from_center_size(pos2(rect.left() + 28.0, rect.center().y), vec2(22.0, 22.0));
    paint_icon(ui.painter(), icon_rect, icon, color, 1.7);
    ui.painter().text(
        pos2(rect.left() + 50.0, rect.center().y),
        Align2::LEFT_CENTER,
        label,
        typography::heading(),
        color,
    );
    if response.hovered() {
        ui.ctx().set_cursor_icon(CursorIcon::PointingHand);
    }
    response
}
fn online_pill(ui: &mut egui::Ui, label: &str, server_url: &str) {
    let (rect, response) = ui.allocate_exact_size(vec2(148.0, 34.0), Sense::hover());
    ui.painter().rect_filled(rect, 9.0, palette::SURFACE_RAISED);
    ui.painter().rect_stroke(
        rect,
        9.0,
        egui::Stroke::new(1.0, Color32::from_white_alpha(24)),
        egui::StrokeKind::Inside,
    );
    ui.painter()
        .circle_filled(rect.left_center() + vec2(17.0, 0.0), 4.5, palette::SUCCESS);
    ui.painter().text(
        rect.left_center() + vec2(30.0, 0.0),
        Align2::LEFT_CENTER,
        label,
        typography::small(),
        palette::TEXT_SECONDARY,
    );
    response.on_hover_text(server_url);
}

fn featured_media_card(
    ui: &mut egui::Ui,
    item: &MediaItem,
    progress: Option<&PlaybackProgress>,
    eyebrow: &str,
    posters: &mut PosterCache,
) -> egui::Response {
    let mut result = None;
    ui.horizontal(|ui| {
        ui.add_space(24.0);
        let width = (ui.available_width() - 24.0).max(480.0);
        let (rect, response) =
            ui.allocate_exact_size(vec2(width, metrics::HERO_HEIGHT), Sense::click());
        ui.painter()
            .rect_filled(rect, 12.0, palette::SURFACE_RAISED);

        let image_rect = Rect::from_min_max(
            egui::pos2(rect.left() + rect.width() * 0.43, rect.top()),
            rect.right_bottom(),
        );
        paint_poster_area(ui, image_rect, item, posters);

        let mut mesh = egui::Mesh::default();
        let base = mesh.vertices.len() as u32;
        mesh.colored_vertex(rect.left_top(), Color32::from_rgb(10, 14, 22));
        mesh.colored_vertex(
            egui::pos2(rect.left() + rect.width() * 0.72, rect.top()),
            Color32::from_rgba_premultiplied(10, 14, 22, 210),
        );
        mesh.colored_vertex(rect.left_bottom(), Color32::from_rgb(10, 14, 22));
        mesh.colored_vertex(
            egui::pos2(rect.left() + rect.width() * 0.72, rect.bottom()),
            Color32::from_rgba_premultiplied(10, 14, 22, 210),
        );
        mesh.add_triangle(base, base + 1, base + 2);
        mesh.add_triangle(base + 2, base + 1, base + 3);
        ui.painter().add(egui::Shape::mesh(mesh));

        ui.painter().rect_stroke(
            rect,
            12.0,
            theme::card_outline(if response.hovered() { 1.0 } else { 0.0 }),
            egui::StrokeKind::Inside,
        );
        let content = Rect::from_min_max(
            rect.min + vec2(38.0, 30.0),
            egui::pos2(rect.left() + rect.width() * 0.55, rect.bottom() - 26.0),
        );
        ui.painter().text(
            content.left_top(),
            Align2::LEFT_TOP,
            eyebrow,
            typography::caption(),
            palette::ACCENT_OUTLINE,
        );
        ui.painter().text(
            content.left_top() + vec2(0.0, 30.0),
            Align2::LEFT_TOP,
            &item.series_title,
            typography::hero(),
            palette::TEXT_PRIMARY,
        );
        ui.painter().text(
            content.left_top() + vec2(0.0, 70.0),
            Align2::LEFT_TOP,
            &item.episode_title,
            typography::body(),
            palette::TEXT_SECONDARY,
        );
        let play_center = content.left_bottom() + vec2(24.0, -34.0);
        ui.painter()
            .circle_filled(play_center, 23.0, palette::ACCENT_BRIGHT);
        paint_icon(
            ui.painter(),
            Rect::from_center_size(play_center + vec2(1.0, 0.0), vec2(18.0, 18.0)),
            Icon::Play,
            Color32::WHITE,
            1.5,
        );

        if let Some(progress) = progress
            && let Some(duration) = progress.duration_ms.filter(|duration| *duration > 0)
        {
            let fraction = (progress.position_ms as f32 / duration as f32).clamp(0.0, 1.0);
            let bar = Rect::from_min_size(
                egui::pos2(content.left() + 62.0, content.bottom() - 39.0),
                vec2((content.width() - 76.0).max(80.0), 5.0),
            );
            ui.painter()
                .rect_filled(bar, 2.5, Color32::from_white_alpha(28));
            ui.painter().rect_filled(
                Rect::from_min_size(bar.min, vec2(bar.width() * fraction, bar.height())),
                2.5,
                palette::ACCENT_BRIGHT,
            );
        }
        if response.hovered() {
            ui.ctx().set_cursor_icon(CursorIcon::PointingHand);
        }
        result = Some(response);
    });
    result.expect("featured card response")
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
    strings: Strings,
) -> Option<String> {
    let mut clicked = None;
    egui::ScrollArea::horizontal()
        .id_salt("rail_next_up")
        .show(ui, |ui| {
            ui.horizontal(|ui| {
                ui.add_space(metrics::GUTTER);
                for entry in entries {
                    let badge = match entry.reason {
                        NextUpReason::Resume => strings.resume(),
                        NextUpReason::NextEpisode => strings.next(),
                        NextUpReason::Start => strings.start(),
                    };
                    if poster_card(ui, &entry.item, posters, None, Some(badge)).clicked() {
                        clicked = Some(entry.item.id.clone());
                    }
                }
            });
        });
    clicked
}

fn series_grid(
    ui: &mut egui::Ui,
    series: &[Series],
    posters: &mut PosterCache,
    strings: Strings,
) -> Option<String> {
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
                            &format!("{} {}", series.episode_count(), strings.episodes()),
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
        egui::pos2(rect.max.x - 6.0, rect.max.y - 56.0),
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
        egui::pos2(rect.max.x - 6.0, rect.max.y - 56.0),
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
        egui::pos2(rect.left() + 8.0, rect.bottom() - 52.0),
        rect.max - vec2(8.0, 6.0),
    );
    let painter = ui.painter().with_clip_rect(caption_rect);
    let title_height = if subtitle.is_some() {
        caption_rect.height() - 18.0
    } else {
        caption_rect.height()
    };
    let title_galley = painter.layout(
        title.to_owned(),
        typography::small(),
        palette::TEXT_PRIMARY,
        caption_rect.width(),
    );
    painter
        .with_clip_rect(Rect::from_min_size(
            caption_rect.min,
            vec2(caption_rect.width(), title_height),
        ))
        .galley(caption_rect.min, title_galley, palette::TEXT_PRIMARY);
    if let Some(subtitle) = subtitle {
        painter.text(
            egui::pos2(caption_rect.left(), caption_rect.bottom()),
            Align2::LEFT_BOTTOM,
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
    let painter = ui.painter().with_clip_rect(rect);
    let mut mesh = egui::Mesh::default();
    let base = mesh.vertices.len() as u32;
    mesh.colored_vertex(rect.left_top(), top);
    mesh.colored_vertex(rect.right_top(), top);
    mesh.colored_vertex(rect.left_bottom(), bottom);
    mesh.colored_vertex(rect.right_bottom(), bottom);
    mesh.add_triangle(base, base + 1, base + 2);
    mesh.add_triangle(base + 2, base + 1, base + 3);
    painter.add(egui::Shape::mesh(mesh));

    let glow_center = rect.center() + vec2(rect.width() * 0.18, -rect.height() * 0.18);
    for (radius, alpha) in [(0.24, 14), (0.16, 18), (0.09, 24)] {
        painter.circle_filled(
            glow_center,
            rect.width().min(rect.height()) * radius,
            Color32::from_rgba_unmultiplied(180, 220, 255, alpha),
        );
    }
    for index in 0..14_u32 {
        let mixed = seed
            .wrapping_mul(1_664_525)
            .wrapping_add(index.wrapping_mul(1_013_904_223));
        let px = rect.left() + rect.width() * (0.08 + (mixed % 840) as f32 / 1000.0);
        let py =
            rect.top() + rect.height() * (0.08 + (mixed.rotate_left(11) % 560) as f32 / 1000.0);
        let radius = 0.7 + (mixed % 3) as f32 * 0.45;
        painter.circle_filled(
            pos2(px, py),
            radius,
            Color32::from_rgba_unmultiplied(255, 255, 255, 80 + (mixed % 100) as u8),
        );
    }
    let ridge = vec![
        rect.left_bottom(),
        pos2(
            rect.left() + rect.width() * 0.22,
            rect.bottom() - rect.height() * 0.18,
        ),
        pos2(
            rect.left() + rect.width() * 0.40,
            rect.bottom() - rect.height() * 0.08,
        ),
        pos2(
            rect.left() + rect.width() * 0.62,
            rect.bottom() - rect.height() * 0.24,
        ),
        pos2(
            rect.left() + rect.width() * 0.82,
            rect.bottom() - rect.height() * 0.12,
        ),
        rect.right_bottom(),
    ];
    painter.add(egui::Shape::convex_polygon(
        ridge,
        Color32::from_rgba_unmultiplied(8, 12, 20, 105),
        egui::Stroke::NONE,
    ));
    painter.text(
        rect.center(),
        Align2::CENTER_CENTER,
        initials(series_title),
        FontId::proportional((rect.height() * 0.15).clamp(24.0, 52.0)),
        Color32::from_rgba_unmultiplied(255, 255, 255, 210),
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
    strings: Strings,
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
                    strings.watched().to_owned()
                } else {
                    format!("{} {percent}%", strings.resume())
                }
            }
            _ => strings.started().to_owned(),
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

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum SettingsAction {
    Back,
    ChangeServer,
    RestartLocalServer,
    StopLocalServer,
    SetLocalRoot(PathBuf),
}

pub fn show_settings(
    ctx: &egui::Context,
    preferences: &mut PlayerPreferences,
    connected_url: Option<&str>,
    return_to_playback: bool,
    local_host_status: Option<&LocalHostStatus>,
    local_roots: &[String],
) -> Option<SettingsAction> {
    let strings = Strings::new(preferences.language);
    let mut action = None;
    egui::TopBottomPanel::top("settings_top")
        .exact_height(56.0)
        .frame(Frame::NONE.fill(palette::BG_DEEP))
        .show(ctx, |ui| {
            ui.horizontal_centered(|ui| {
                ui.add_space(metrics::GUTTER);
                if ui.button(strings.back()).clicked() {
                    action = Some(SettingsAction::Back);
                }
                ui.label(
                    RichText::new(strings.settings())
                        .font(FontId::proportional(20.0))
                        .strong()
                        .color(palette::TEXT_PRIMARY),
                );
            });
        });
    egui::CentralPanel::default()
        .frame(Frame::NONE.fill(palette::BG_PANEL))
        .show(ctx, |ui| {
            egui::ScrollArea::vertical().show(ui, |ui| {
                ui.set_max_width(680.0);
                ui.add_space(20.0);
                section_heading(ui, strings.language_label());
                ui.horizontal(|ui| {
                    ui.add_space(metrics::GUTTER);
                    for language in Language::ALL {
                        ui.selectable_value(
                            &mut preferences.language,
                            language,
                            language.native_name(),
                        );
                    }
                });

                let strings = Strings::new(preferences.language);
                ui.add_space(20.0);
                section_heading(ui, strings.preferences());
                settings_slider_u8(
                    ui,
                    strings.default_volume(),
                    &mut preferences.volume_percent,
                    0..=130,
                    "%",
                );
                settings_slider_f64(
                    ui,
                    strings.playback_rate(),
                    &mut preferences.playback_rate,
                    0.5..=2.0,
                    "x",
                );
                settings_checkbox(ui, strings.auto_next(), &mut preferences.auto_next);

                ui.add_space(20.0);
                section_heading(ui, strings.danmaku_defaults());
                settings_checkbox(ui, strings.show_danmaku(), &mut preferences.danmaku_enabled);
                settings_slider_f32(
                    ui,
                    strings.opacity(),
                    &mut preferences.danmaku_opacity,
                    0.0..=1.0,
                    "",
                );
                settings_slider_f32(
                    ui,
                    strings.speed(),
                    &mut preferences.danmaku_speed,
                    0.25..=4.0,
                    "x",
                );
                settings_slider_f32(
                    ui,
                    strings.density(),
                    &mut preferences.danmaku_density,
                    0.0..=1.0,
                    "",
                );
                let mut lanes = preferences.danmaku_lanes as u32;
                settings_slider_u32(ui, strings.lanes(), &mut lanes, 1..=32, "");
                preferences.danmaku_lanes = lanes as usize;

                ui.add_space(20.0);
                section_heading(ui, strings.server_connection());
                let server = connected_url
                    .map(str::to_owned)
                    .or_else(|| preferences.last_server_url.clone());
                muted_line(
                    ui,
                    &server
                        .as_deref()
                        .map(|url| {
                            format!(
                                "{}: {url}",
                                if connected_url.is_some() {
                                    strings.connected_to()
                                } else {
                                    strings.remembered_server()
                                }
                            )
                        })
                        .unwrap_or_else(|| strings.no_server().to_owned()),
                );
                ui.horizontal(|ui| {
                    ui.add_space(metrics::GUTTER);
                    if !return_to_playback && ui.button(strings.change_server()).clicked() {
                        action = Some(SettingsAction::ChangeServer);
                    }
                    if preferences.last_server_url.is_some()
                        && ui.button(strings.forget_server()).clicked()
                    {
                        preferences.last_server_url = None;
                    }
                });

                if let Some(status) = local_host_status {
                    ui.add_space(20.0);
                    section_heading(ui, strings.local_hosting());
                    let status_text = match status {
                        LocalHostStatus::Unavailable => {
                            strings.local_server_unavailable().to_owned()
                        }
                        LocalHostStatus::NeedsSetup => strings.local_host_note().to_owned(),
                        LocalHostStatus::Starting => strings.starting_local_server().to_owned(),
                        LocalHostStatus::Running { base_url, managed } => format!(
                            "{}: {base_url}",
                            if *managed {
                                strings.managed_by_player()
                            } else {
                                strings.attached_server()
                            }
                        ),
                        LocalHostStatus::Stopped => strings.local_server_stopped().to_owned(),
                        LocalHostStatus::Failed(error) => error.clone(),
                    };
                    muted_line(ui, &status_text);
                    if !return_to_playback && status.is_available() {
                        ui.horizontal(|ui| {
                            ui.add_space(metrics::GUTTER);
                            if ui.button(strings.change_library_folder()).clicked() {
                                let mut dialog =
                                    rfd::FileDialog::new().set_title(strings.library_folder());
                                if let Some(root) = local_roots.first() {
                                    dialog = dialog.set_directory(root);
                                }
                                if let Some(folder) = dialog.pick_folder() {
                                    action = Some(SettingsAction::SetLocalRoot(folder));
                                }
                            }
                            if status.is_managed_running()
                                && ui.button(strings.restart_server()).clicked()
                            {
                                action = Some(SettingsAction::RestartLocalServer);
                            }
                            if status.is_managed_running()
                                && ui.button(strings.stop_server()).clicked()
                            {
                                action = Some(SettingsAction::StopLocalServer);
                            }
                        });
                    }
                }

                if let Some(server) = server.as_deref() {
                    ui.add_space(20.0);
                    section_heading(ui, strings.administration());
                    muted_line(ui, strings.administration_note());
                    ui.horizontal(|ui| {
                        ui.add_space(metrics::GUTTER);
                        ui.hyperlink_to(
                            strings.open_web_admin(),
                            format!("{}/web/", server.trim_end_matches('/')),
                        );
                    });
                }
                ui.add_space(20.0);
                muted_line(ui, strings.saved());
            });
        });
    action
}

fn settings_checkbox(ui: &mut egui::Ui, label: &str, value: &mut bool) {
    ui.horizontal(|ui| {
        ui.add_space(metrics::GUTTER);
        ui.checkbox(value, label);
    });
}

fn settings_slider_u8(
    ui: &mut egui::Ui,
    label: &str,
    value: &mut u8,
    range: std::ops::RangeInclusive<u8>,
    suffix: &str,
) {
    ui.horizontal(|ui| {
        ui.add_space(metrics::GUTTER);
        ui.label(label);
        ui.add(egui::Slider::new(value, range).suffix(suffix));
    });
}

fn settings_slider_u32(
    ui: &mut egui::Ui,
    label: &str,
    value: &mut u32,
    range: std::ops::RangeInclusive<u32>,
    suffix: &str,
) {
    ui.horizontal(|ui| {
        ui.add_space(metrics::GUTTER);
        ui.label(label);
        ui.add(egui::Slider::new(value, range).suffix(suffix));
    });
}

fn settings_slider_f32(
    ui: &mut egui::Ui,
    label: &str,
    value: &mut f32,
    range: std::ops::RangeInclusive<f32>,
    suffix: &str,
) {
    ui.horizontal(|ui| {
        ui.add_space(metrics::GUTTER);
        ui.label(label);
        ui.add(egui::Slider::new(value, range).suffix(suffix));
    });
}

fn settings_slider_f64(
    ui: &mut egui::Ui,
    label: &str,
    value: &mut f64,
    range: std::ops::RangeInclusive<f64>,
    suffix: &str,
) {
    ui.horizontal(|ui| {
        ui.add_space(metrics::GUTTER);
        ui.label(label);
        ui.add(egui::Slider::new(value, range).suffix(suffix));
    });
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
