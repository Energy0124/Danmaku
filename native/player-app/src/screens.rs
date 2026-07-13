//! Library-mode screens: server connect and catalog browse.

use std::path::PathBuf;

use eframe::egui::{
    self, Align, Align2, Color32, CursorIcon, FontId, Frame, Layout, Rect, RichText, Sense,
    TextEdit, pos2, vec2,
};

use crate::{
    branding::Branding,
    discovery::DiscoveredServer,
    hosting::LocalHostStatus,
    icons::{Icon, paint_icon},
    library::{
        DEFAULT_NEXT_UP_LIMIT, LibraryCatalog, MINIMUM_REMAINING_MS, MINIMUM_RESUME_POSITION_MS,
        MediaItem, NextUpItem, NextUpReason, PlaybackProgress, ProgressItem, Series,
        continue_watching_items, folder_grouped_series, grouped_series, matched_anime_series,
        next_up_items,
    },
    localization::{Language, Strings},
    posters::{PosterCache, PosterState},
    preferences::{DandanplayCredentials, PlayerPreferences},
    session::LibrarySession,
    theme::{self, metrics, palette, typography},
};

const CARD_WIDTH: f32 = 158.0;
const CARD_HEIGHT: f32 = 236.0;
const CARD_GAP: f32 = 16.0;
const RAIL_LIMIT: usize = 12;
/// Left inset of library page content, beyond the navigation rail.
const PAGE_GUTTER: f32 = 26.0;

/// Local wall-clock hour (0-23) for the greeting line.
#[cfg(windows)]
fn local_hour() -> u8 {
    #[repr(C)]
    #[derive(Default)]
    struct Win32SystemTime {
        year: u16,
        month: u16,
        day_of_week: u16,
        day: u16,
        hour: u16,
        minute: u16,
        second: u16,
        milliseconds: u16,
    }
    unsafe extern "system" {
        fn GetLocalTime(system_time: *mut Win32SystemTime);
    }
    let mut time = Win32SystemTime::default();
    unsafe { GetLocalTime(&mut time) };
    (time.hour % 24) as u8
}

#[cfg(not(windows))]
fn local_hour() -> u8 {
    // UTC fallback: close enough for a greeting on non-Windows dev builds.
    let seconds = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|elapsed| elapsed.as_secs())
        .unwrap_or(0);
    ((seconds / 3600) % 24) as u8
}

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
        branding: &Branding,
    ) -> Option<ConnectAction> {
        let strings = Strings::new(*language);
        let mut action = None;

        egui::TopBottomPanel::bottom("connect_language")
            .exact_height(46.0)
            .frame(Frame::NONE.fill(palette::BG_DEEP))
            .show(ctx, |ui| {
                language_bar(ui, language);
            });
        egui::CentralPanel::default()
            .frame(Frame::NONE.fill(palette::BG_DEEP))
            .show(ctx, |ui| {
                let viewport_height = ui.available_height();
                egui::ScrollArea::vertical()
                    .auto_shrink([false, false])
                    .show(ui, |ui| {
                        let content_width = 640.0_f32.min(ui.available_width() - 32.0);
                        ui.vertical_centered(|ui| {
                            // Center the setup column when the window is tall;
                            // the estimate covers the fixed-height widgets below.
                            let content_estimate = if self.show_remote_options {
                                760.0
                            } else {
                                560.0
                            };
                            ui.add_space(((viewport_height - content_estimate) / 2.0).max(16.0));
                            let (illustration, _) =
                                ui.allocate_exact_size(vec2(320.0, 190.0), Sense::hover());
                            paint_library_illustration(ui, illustration, branding);

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

                            ui.add_space(14.0);
                            if link_icon_button(ui, Icon::Swap, strings.connect_another_server())
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

fn paint_library_illustration(ui: &egui::Ui, rect: Rect, branding: &Branding) {
    ui.painter().image(
        branding.mascot.id(),
        rect,
        Rect::from_min_max(pos2(0.10, 0.22), pos2(0.90, 0.78)),
        Color32::WHITE,
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
    let width = ui.available_width().clamp(460.0, 620.0);
    let (rect, _) = ui.allocate_exact_size(vec2(width, 72.0), Sense::hover());
    ui.painter()
        .rect_filled(rect, 12.0, palette::SURFACE_RAISED);
    ui.painter().rect_stroke(
        rect,
        12.0,
        egui::Stroke::new(1.0, Color32::from_white_alpha(24)),
        egui::StrokeKind::Inside,
    );
    let (color, detail) = match status {
        LocalHostStatus::Starting => (palette::ACCENT_OUTLINE, strings.starting_local_server()),
        LocalHostStatus::Running { .. } => (palette::SUCCESS, strings.ready_automatically()),
        LocalHostStatus::Failed(error) => (palette::DANGER, error.as_str()),
        LocalHostStatus::Unavailable => (palette::TEXT_MUTED, strings.local_server_unavailable()),
        LocalHostStatus::Stopped => (palette::TEXT_MUTED, strings.local_server_stopped()),
        LocalHostStatus::NeedsSetup => (palette::ACCENT_OUTLINE, strings.local_host_note()),
    };

    // Icon tile with a status dot pinned to its corner, like a device badge.
    let tile = Rect::from_center_size(rect.left_center() + vec2(38.0, 0.0), vec2(44.0, 44.0));
    ui.painter().rect_filled(tile, 12.0, palette::SURFACE_FAINT);
    paint_icon(
        ui.painter(),
        Rect::from_center_size(tile.center(), vec2(22.0, 22.0)),
        Icon::Home,
        palette::TEXT_SECONDARY,
        1.6,
    );
    let dot = tile.right_bottom() - vec2(6.0, 6.0);
    ui.painter()
        .circle_filled(dot, 6.0, palette::SURFACE_RAISED);
    ui.painter().circle_filled(dot, 4.0, color);

    let text_left = tile.right() + 14.0;
    ui.painter().text(
        pos2(text_left, rect.center().y - 10.0),
        Align2::LEFT_CENTER,
        strings.local_hosting(),
        typography::body(),
        palette::TEXT_PRIMARY,
    );
    let detail_clip = Rect::from_min_max(
        pos2(text_left, rect.top()),
        rect.right_bottom() - vec2(12.0, 0.0),
    );
    ui.painter().with_clip_rect(detail_clip).text(
        pos2(text_left, rect.center().y + 11.0),
        Align2::LEFT_CENTER,
        detail,
        typography::small(),
        palette::TEXT_MUTED,
    );
}

/// Centered bottom language selector: globe, then languages with separators.
fn language_bar(ui: &mut egui::Ui, language: &mut Language) {
    let bar = ui.max_rect();
    let mut widths: Vec<f32> = Vec::with_capacity(Language::ALL.len());
    let mut total = 26.0; // globe icon + gap
    for option in Language::ALL {
        let galley = ui.painter().layout_no_wrap(
            option.native_name().to_owned(),
            typography::body(),
            palette::TEXT_MUTED,
        );
        widths.push(galley.size().x);
        total += galley.size().x + 30.0;
    }
    total -= 30.0;
    let mut cursor = bar.center().x - total / 2.0;
    paint_icon(
        ui.painter(),
        Rect::from_center_size(pos2(cursor + 9.0, bar.center().y), vec2(18.0, 18.0)),
        Icon::Globe,
        palette::TEXT_MUTED,
        1.3,
    );
    cursor += 26.0;
    for (index, option) in Language::ALL.into_iter().enumerate() {
        let selected = *language == option;
        let rect = Rect::from_min_max(
            pos2(cursor - 6.0, bar.center().y - 14.0),
            pos2(cursor + widths[index] + 6.0, bar.center().y + 14.0),
        );
        let response = ui.interact(
            rect,
            ui.id().with(("language_option", index)),
            Sense::click(),
        );
        let color = if selected {
            palette::ACCENT_OUTLINE
        } else if response.hovered() {
            palette::TEXT_SECONDARY
        } else {
            palette::TEXT_MUTED
        };
        ui.painter().text(
            pos2(cursor, bar.center().y),
            Align2::LEFT_CENTER,
            option.native_name(),
            typography::body(),
            color,
        );
        if response.clicked() {
            *language = option;
        }
        if response.hovered() {
            ui.ctx().set_cursor_icon(CursorIcon::PointingHand);
        }
        cursor += widths[index] + 30.0;
        if index + 1 < Language::ALL.len() {
            ui.painter().text(
                pos2(cursor - 15.0, bar.center().y),
                Align2::CENTER_CENTER,
                "|",
                typography::body(),
                Color32::from_white_alpha(40),
            );
        }
    }
}

/// Borderless accent text button with a leading icon (quiet secondary action).
fn link_icon_button(ui: &mut egui::Ui, icon: Icon, label: &str) -> egui::Response {
    let galley = ui.painter().layout_no_wrap(
        label.to_owned(),
        typography::heading(),
        palette::ACCENT_OUTLINE,
    );
    let size = vec2(galley.size().x + 34.0, 32.0);
    let (rect, response) = ui.allocate_exact_size(size, Sense::click());
    let color = if response.hovered() {
        theme::mix(palette::ACCENT_OUTLINE, Color32::WHITE, 0.35)
    } else {
        palette::ACCENT_OUTLINE
    };
    paint_icon(
        ui.painter(),
        Rect::from_center_size(pos2(rect.left() + 11.0, rect.center().y), vec2(20.0, 20.0)),
        icon,
        color,
        1.6,
    );
    ui.painter().text(
        pos2(rect.left() + 28.0, rect.center().y),
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
    Play {
        media_id: String,
    },
    /// Resolves danmaku (and records the anime match server-side) for the
    /// given episodes without navigating to playback.
    PreloadDanmaku {
        media_ids: Vec<String>,
    },
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

/// Which grouping the "All series" page is browsing. Kept as UI-only state
/// (not part of `LibraryView`'s navigation history) since switching it is a
/// filter change, not a new page.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
enum LibrarySeriesTab {
    /// Only items with a recognized dandanplay/provider anime match,
    /// grouped by that identity.
    #[default]
    MatchedAnime,
    /// Every item, grouped strictly by its on-disk folder, ignoring any
    /// recognized anime metadata.
    Folder,
}

pub struct LibraryScreen {
    query: String,
    view: LibraryView,
    all_series_tab: LibrarySeriesTab,
    /// Mixed grouping (anime identity when recognized, folder otherwise) —
    /// only used for the low-visibility Home "recently added" rail, where a
    /// little mixing is a reasonable tradeoff for always showing every item.
    cached_series: Vec<Series>,
    /// Recognized-anime-only grouping, never mixed with folder entries. Used
    /// by the "All series" page's "Matched anime" tab.
    cached_anime_series: Vec<Series>,
    /// Folder-only grouping, ignoring recognition entirely. Used by the
    /// "All series" page's "Folders" tab.
    cached_folder_series: Vec<Series>,
    /// The `LibrarySession::catalog_version` the caches above were last
    /// built from. `None` never matches a real version, so the first render
    /// always (re)builds them.
    cached_catalog_version: Option<u64>,
}

impl Default for LibraryScreen {
    fn default() -> Self {
        Self {
            query: String::new(),
            view: LibraryView::Home,
            all_series_tab: LibrarySeriesTab::default(),
            cached_series: Vec::new(),
            cached_anime_series: Vec::new(),
            cached_folder_series: Vec::new(),
            cached_catalog_version: None,
        }
    }
}

impl LibraryScreen {
    pub fn show(
        &mut self,
        ctx: &egui::Context,
        session: &LibrarySession,
        posters: &mut PosterCache,
        pending_preloads: &std::collections::HashSet<String>,
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
                    ui.add_space(24.0);
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
                        if matches!(self.view, LibraryView::Series(_)) {
                            self.view = LibraryView::Home;
                        }
                        ctx.memory_mut(|memory| memory.request_focus(search_id));
                    }
                });
                ui.with_layout(Layout::bottom_up(Align::Center), |ui| {
                    ui.add_space(16.0);
                    if nav_button(ui, Icon::Settings, strings.settings(), false).clicked() {
                        action = Some(LibraryAction::Settings);
                    }
                    if nav_button(ui, Icon::Power, strings.disconnect(), false).clicked() {
                        action = Some(LibraryAction::Disconnect);
                    }
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
                self.refresh_series_cache(catalog, session.catalog_version);

                let inner_action = if !self.query.trim().is_empty() {
                    self.show_search_results(ui, catalog, session, posters, strings)
                } else {
                    match self.view.clone() {
                        LibraryView::Home => self.show_home(ui, catalog, session, posters, strings),
                        LibraryView::AllSeries => {
                            self.show_all_series(ui, session, posters, strings)
                        }
                        LibraryView::Series(series_id) => self.show_series(
                            ui,
                            &series_id,
                            &session.progresses,
                            posters,
                            pending_preloads,
                            strings,
                        ),
                    }
                };
                if inner_action.is_some() {
                    action = inner_action;
                }
            });
        action
    }

    /// Wordmark, greeting, quiet status, and the search field. Rendered at the
    /// top of every scrolling library page (mirrors the approved mockup).
    fn page_header(
        &mut self,
        ui: &mut egui::Ui,
        session: &LibrarySession,
        strings: Strings,
    ) -> Option<LibraryAction> {
        let mut action = None;
        ui.add_space(22.0);
        ui.horizontal(|ui| {
            ui.add_space(PAGE_GUTTER);
            ui.vertical(|ui| {
                ui.spacing_mut().item_spacing.y = 4.0;
                ui.label(
                    RichText::new("Danmaku")
                        .font(typography::display())
                        .strong()
                        .color(palette::TEXT_PRIMARY),
                );
                ui.label(
                    RichText::new(strings.greeting(local_hour()))
                        .font(typography::body())
                        .color(palette::TEXT_MUTED),
                );
            });
            ui.with_layout(Layout::right_to_left(Align::Min), |ui| {
                ui.add_space(PAGE_GUTTER);
                let local = session.base_url.starts_with("http://127.")
                    || session.base_url.starts_with("http://localhost");
                let label = if local {
                    format!("{}  •  {}", strings.local_library(), strings.online())
                } else {
                    strings.library_online().to_owned()
                };
                online_pill(ui, &label, &session.base_url);
                if icon_chip_button(ui, Icon::Refresh, strings.refresh()).clicked() {
                    action = Some(LibraryAction::Refresh);
                }
            });
        });
        ui.add_space(16.0);
        ui.horizontal(|ui| {
            ui.add_space(PAGE_GUTTER);
            let search_width = (ui.available_width() * 0.40).clamp(260.0, 460.0);
            let response = ui.add_sized(
                [search_width, 38.0],
                TextEdit::singleline(&mut self.query)
                    .id(egui::Id::new("library_search_field"))
                    .hint_text(strings.search())
                    .margin(egui::Margin {
                        left: 36,
                        right: 10,
                        top: 10,
                        bottom: 10,
                    })
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
            ui.ctx()
                .send_viewport_cmd(egui::ViewportCommand::IMEAllowed(response.has_focus()));
            if response.has_focus() {
                ui.ctx()
                    .send_viewport_cmd(egui::ViewportCommand::IMERect(response.rect));
            }
        });
        ui.add_space(20.0);
        action
    }
    /// Rebuilds the grouped-series cache whenever the session has received a
    /// newer catalog. Keyed off `catalog_version` rather than catalog
    /// content: server-side enrichment (recognized anime, cached poster) can
    /// change items without touching `indexedAtEpochMs` or the item count,
    /// which a content-derived stamp would otherwise miss.
    fn refresh_series_cache(&mut self, catalog: &LibraryCatalog, catalog_version: u64) {
        if self.cached_catalog_version != Some(catalog_version) {
            self.cached_series = grouped_series(catalog);
            self.cached_anime_series = matched_anime_series(catalog);
            self.cached_folder_series = folder_grouped_series(catalog);
            self.cached_catalog_version = Some(catalog_version);
        }
    }

    /// Looks up a series by ID across every cache, since it may have been
    /// clicked from the mixed Home rail, either "All series" tab, or search.
    fn find_series(&self, series_id: &str) -> Option<&Series> {
        self.cached_series
            .iter()
            .find(|series| series.id == series_id)
            .or_else(|| {
                self.cached_anime_series
                    .iter()
                    .find(|series| series.id == series_id)
            })
            .or_else(|| {
                self.cached_folder_series
                    .iter()
                    .find(|series| series.id == series_id)
            })
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
                if let Some(header_action) = self.page_header(ui, session, strings) {
                    action = Some(header_action);
                }
                let mut featured_next_up = false;
                if let Some(featured) = continue_watching.first() {
                    if featured_media_card(
                        ui,
                        &featured.item,
                        Some(&featured.progress),
                        strings.continue_watching(),
                        strings,
                        posters,
                    )
                    .clicked()
                    {
                        action = Some(LibraryAction::Play {
                            media_id: featured.item.id.clone(),
                        });
                    }
                    ui.add_space(26.0);
                } else if let Some(featured) = next_up.first() {
                    featured_next_up = true;
                    if featured_media_card(
                        ui,
                        &featured.item,
                        featured.progress.as_ref(),
                        strings.next_up(),
                        strings,
                        posters,
                    )
                    .clicked()
                    {
                        action = Some(LibraryAction::Play {
                            media_id: featured.item.id.clone(),
                        });
                    }
                    ui.add_space(26.0);
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
                // The featured hero item must not repeat inside the rail.
                let featured_id = if featured_next_up {
                    next_up.first().map(|entry| entry.item.id.as_str())
                } else {
                    continue_watching
                        .first()
                        .map(|entry| entry.item.id.as_str())
                };
                let remaining_next_up: Vec<&NextUpItem> = next_up
                    .iter()
                    .filter(|entry| Some(entry.item.id.as_str()) != featured_id)
                    .collect();
                if !remaining_next_up.is_empty() {
                    section_heading(ui, strings.next_up());
                    if let Some(clicked) = next_up_rail(ui, &remaining_next_up, posters, strings) {
                        action = Some(LibraryAction::Play { media_id: clicked });
                    }
                    ui.add_space(22.0);
                }

                section_heading(ui, strings.recently_added());
                if let Some(series_id) = series_rail(ui, &self.cached_series, posters, strings) {
                    self.view = LibraryView::Series(series_id);
                }
                ui.add_space(28.0);
            });
        action
    }
    fn show_all_series(
        &mut self,
        ui: &mut egui::Ui,
        session: &LibrarySession,
        posters: &mut PosterCache,
        strings: Strings,
    ) -> Option<LibraryAction> {
        let mut action = None;
        egui::ScrollArea::vertical()
            .id_salt("library_all_series")
            .auto_shrink([false, false])
            .show(ui, |ui| {
                if let Some(header_action) = self.page_header(ui, session, strings) {
                    action = Some(header_action);
                }
                ui.horizontal(|ui| {
                    ui.add_space(PAGE_GUTTER);
                    if ui
                        .selectable_label(
                            self.all_series_tab == LibrarySeriesTab::MatchedAnime,
                            strings.matched_anime(),
                        )
                        .clicked()
                    {
                        self.all_series_tab = LibrarySeriesTab::MatchedAnime;
                    }
                    if ui
                        .selectable_label(
                            self.all_series_tab == LibrarySeriesTab::Folder,
                            strings.folders(),
                        )
                        .clicked()
                    {
                        self.all_series_tab = LibrarySeriesTab::Folder;
                    }
                });
                ui.add_space(10.0);
                let (series, heading_label) = match self.all_series_tab {
                    LibrarySeriesTab::MatchedAnime => {
                        (&self.cached_anime_series, strings.matched_anime())
                    }
                    LibrarySeriesTab::Folder => (&self.cached_folder_series, strings.folders()),
                };
                section_heading(
                    ui,
                    &format!(
                        "{}  ·  {} {}",
                        heading_label,
                        series.len(),
                        strings.titles()
                    ),
                );
                if series.is_empty() {
                    muted_line(ui, strings.no_series());
                } else if let Some(series_id) = series_grid(ui, series, posters, strings) {
                    self.view = LibraryView::Series(series_id);
                }
                ui.add_space(28.0);
            });
        action
    }

    fn show_search_results(
        &mut self,
        ui: &mut egui::Ui,
        catalog: &LibraryCatalog,
        session: &LibrarySession,
        posters: &mut PosterCache,
        strings: Strings,
    ) -> Option<LibraryAction> {
        let query = self.query.trim().to_lowercase();
        let mut action = None;

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
                if let Some(header_action) = self.page_header(ui, session, strings) {
                    action = Some(header_action);
                }
                let matching_series: Vec<&Series> = self
                    .cached_series
                    .iter()
                    .filter(|series| series.title.to_lowercase().contains(&query))
                    .take(60)
                    .collect();
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
        pending_preloads: &std::collections::HashSet<String>,
        strings: Strings,
    ) -> Option<LibraryAction> {
        let Some(series) = self.find_series(series_id).cloned() else {
            self.view = LibraryView::Home;
            return None;
        };
        let mut action = None;
        let unmatched_ids: Vec<String> = series
            .items()
            .filter(|item| item.anime_metadata.is_none())
            .map(|item| item.id.clone())
            .collect();
        let matching_in_progress = series
            .items()
            .any(|item| pending_preloads.contains(&item.id));
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
                ui.add_space(18.0);
                ui.horizontal(|ui| {
                    ui.add_space(PAGE_GUTTER);
                    if icon_chip_button(ui, Icon::Back, strings.back()).clicked() {
                        self.view = LibraryView::Home;
                    }
                    if !unmatched_ids.is_empty() {
                        ui.add_space(8.0);
                        let tooltip = if matching_in_progress {
                            strings.matching_episodes()
                        } else {
                            strings.match_episodes()
                        };
                        let response = icon_chip_button(ui, Icon::Danmaku, tooltip);
                        if matching_in_progress {
                            ui.add_space(8.0);
                            ui.label(
                                RichText::new(strings.matching_episodes())
                                    .font(typography::caption())
                                    .color(palette::TEXT_MUTED),
                            );
                        } else if response.clicked() {
                            action = Some(LibraryAction::PreloadDanmaku {
                                media_ids: unmatched_ids.clone(),
                            });
                        }
                    }
                });
                ui.add_space(12.0);
                ui.horizontal(|ui| {
                    ui.add_space(PAGE_GUTTER);
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
    let galley = ui.painter().layout_no_wrap(
        label.to_owned(),
        typography::small(),
        palette::TEXT_SECONDARY,
    );
    let (rect, response) =
        ui.allocate_exact_size(vec2(galley.size().x + 44.0, 34.0), Sense::hover());
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

/// Small squared icon button used in headers (refresh, back).
fn icon_chip_button(ui: &mut egui::Ui, icon: Icon, tooltip: &str) -> egui::Response {
    let (rect, response) = ui.allocate_exact_size(vec2(34.0, 34.0), Sense::click());
    let fill = if response.hovered() {
        palette::WIDGET_HOVER
    } else {
        palette::SURFACE_RAISED
    };
    ui.painter().rect_filled(rect, 9.0, fill);
    ui.painter().rect_stroke(
        rect,
        9.0,
        egui::Stroke::new(1.0, Color32::from_white_alpha(24)),
        egui::StrokeKind::Inside,
    );
    paint_icon(
        ui.painter(),
        Rect::from_center_size(rect.center(), vec2(18.0, 18.0)),
        icon,
        palette::TEXT_SECONDARY,
        1.5,
    );
    if response.hovered() {
        ui.ctx().set_cursor_icon(CursorIcon::PointingHand);
    }
    response.on_hover_text(tooltip)
}

fn featured_media_card(
    ui: &mut egui::Ui,
    item: &MediaItem,
    progress: Option<&PlaybackProgress>,
    eyebrow: &str,
    strings: Strings,
    posters: &mut PosterCache,
) -> egui::Response {
    let mut result = None;
    ui.horizontal(|ui| {
        ui.add_space(PAGE_GUTTER);
        let width = (ui.available_width() - PAGE_GUTTER).max(480.0);
        let (rect, response) =
            ui.allocate_exact_size(vec2(width, metrics::HERO_HEIGHT), Sense::click());

        // Full-bleed artwork with a left scrim that keeps text legible.
        paint_poster_rounded(ui, rect, item, posters, 14.0);
        let scrim_edge = rect.left() + rect.width() * 0.68;
        let mut mesh = egui::Mesh::default();
        let base = mesh.vertices.len() as u32;
        let scrim = Color32::from_rgba_premultiplied(7, 10, 16, 232);
        mesh.colored_vertex(rect.left_top(), scrim);
        mesh.colored_vertex(egui::pos2(scrim_edge, rect.top()), Color32::TRANSPARENT);
        mesh.colored_vertex(rect.left_bottom(), scrim);
        mesh.colored_vertex(egui::pos2(scrim_edge, rect.bottom()), Color32::TRANSPARENT);
        mesh.add_triangle(base, base + 1, base + 2);
        mesh.add_triangle(base + 2, base + 1, base + 3);
        ui.painter().add(egui::Shape::mesh(mesh));
        mask_rounded_corners(ui, rect, 14.0, palette::BG_DEEP);

        ui.painter().rect_stroke(
            rect,
            14.0,
            theme::card_outline(if response.hovered() { 1.0 } else { 0.0 }),
            egui::StrokeKind::Inside,
        );

        let content_left = rect.left() + 36.0;
        let text_clip = ui.painter().with_clip_rect(Rect::from_min_max(
            rect.min,
            egui::pos2(rect.left() + rect.width() * 0.66, rect.bottom()),
        ));
        text_clip.text(
            pos2(content_left, rect.top() + 30.0),
            Align2::LEFT_TOP,
            eyebrow,
            typography::heading(),
            palette::ACCENT_OUTLINE,
        );
        text_clip.text(
            pos2(content_left, rect.top() + 56.0),
            Align2::LEFT_TOP,
            &item.series_title,
            typography::display(),
            palette::TEXT_PRIMARY,
        );
        text_clip.text(
            pos2(content_left, rect.top() + 102.0),
            Align2::LEFT_TOP,
            &item.episode_title,
            typography::body(),
            palette::TEXT_SECONDARY,
        );

        // Progress bar plus remaining time, mirrored from the mockup hero.
        let bar_top = rect.top() + 140.0;
        if let Some(progress) = progress
            && let Some(duration) = progress.duration_ms.filter(|duration| *duration > 0)
        {
            let fraction = (progress.position_ms as f32 / duration as f32).clamp(0.0, 1.0);
            let bar = Rect::from_min_size(
                pos2(content_left, bar_top),
                vec2((rect.width() * 0.30).clamp(180.0, 380.0), 5.0),
            );
            ui.painter()
                .rect_filled(bar, 2.5, Color32::from_white_alpha(36));
            ui.painter().rect_filled(
                Rect::from_min_size(bar.min, vec2(bar.width() * fraction, bar.height())),
                2.5,
                palette::ACCENT_BRIGHT,
            );
            let remaining_minutes =
                (((duration - progress.position_ms).max(0) as f64) / 60_000.0).ceil() as i64;
            text_clip.text(
                pos2(content_left, bar.bottom() + 10.0),
                Align2::LEFT_TOP,
                strings.minutes_left(remaining_minutes),
                typography::caption(),
                palette::TEXT_MUTED,
            );
        }

        let play_center = pos2(content_left + 23.0, rect.bottom() - 52.0);
        ui.painter()
            .circle_filled(play_center, 23.0, palette::ACCENT_BRIGHT);
        paint_icon(
            ui.painter(),
            Rect::from_center_size(play_center + vec2(1.0, 0.0), vec2(18.0, 18.0)),
            Icon::Play,
            Color32::WHITE,
            1.5,
        );

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
        ui.add_space(PAGE_GUTTER);
        ui.label(
            RichText::new(text)
                .font(typography::title())
                .strong()
                .color(palette::TEXT_PRIMARY),
        );
    });
    ui.add_space(8.0);
}

fn muted_line(ui: &mut egui::Ui, text: &str) {
    ui.horizontal(|ui| {
        ui.add_space(PAGE_GUTTER);
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
                ui.add_space(PAGE_GUTTER);
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
    entries: &[&NextUpItem],
    posters: &mut PosterCache,
    strings: Strings,
) -> Option<String> {
    let mut clicked = None;
    egui::ScrollArea::horizontal()
        .id_salt("rail_next_up")
        .show(ui, |ui| {
            ui.horizontal(|ui| {
                ui.add_space(PAGE_GUTTER);
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

/// Single-row horizontal rail of series posters (the Home "Recently added").
fn series_rail(
    ui: &mut egui::Ui,
    series: &[Series],
    posters: &mut PosterCache,
    strings: Strings,
) -> Option<String> {
    let mut clicked = None;
    egui::ScrollArea::horizontal()
        .id_salt("rail_series")
        .show(ui, |ui| {
            ui.horizontal(|ui| {
                ui.add_space(PAGE_GUTTER);
                for series in series.iter().take(24) {
                    let Some(item) = series.items().next().cloned() else {
                        continue;
                    };
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
    let available_width = ui.available_width() - 2.0 * PAGE_GUTTER;
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
                ui.add_space(PAGE_GUTTER);
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
    let title = format!("{} - {}", item.series_title, item.episode_title);
    poster_card_impl(ui, item, &title, None, posters, progress_fraction, badge)
}

fn poster_card_with_title(
    ui: &mut egui::Ui,
    representative: &MediaItem,
    title: &str,
    subtitle: &str,
    posters: &mut PosterCache,
) -> egui::Response {
    poster_card_impl(
        ui,
        representative,
        title,
        Some(subtitle),
        posters,
        None,
        None,
    )
}

/// Full-bleed poster with the caption on a bottom scrim, like the mockups.
fn poster_card_impl(
    ui: &mut egui::Ui,
    item: &MediaItem,
    title: &str,
    subtitle: Option<&str>,
    posters: &mut PosterCache,
    progress_fraction: Option<f32>,
    badge: Option<&str>,
) -> egui::Response {
    let radius = metrics::CARD_RADIUS + 2.0;
    let (rect, response) = ui.allocate_exact_size(vec2(CARD_WIDTH, CARD_HEIGHT), Sense::click());
    if !ui.is_rect_visible(rect) {
        return response;
    }
    paint_poster_rounded(ui, rect, item, posters, radius);

    // Bottom scrim keeps caption text readable over any artwork.
    let scrim_top = rect.bottom() - 74.0;
    let mut mesh = egui::Mesh::default();
    let base = mesh.vertices.len() as u32;
    let dark = Color32::from_rgba_premultiplied(4, 6, 9, 208);
    mesh.colored_vertex(egui::pos2(rect.left(), scrim_top), Color32::TRANSPARENT);
    mesh.colored_vertex(egui::pos2(rect.right(), scrim_top), Color32::TRANSPARENT);
    mesh.colored_vertex(rect.left_bottom(), dark);
    mesh.colored_vertex(rect.right_bottom(), dark);
    mesh.add_triangle(base, base + 1, base + 2);
    mesh.add_triangle(base + 2, base + 1, base + 3);
    ui.painter().add(egui::Shape::mesh(mesh));
    mask_rounded_corners(ui, rect, radius, palette::BG_DEEP);

    let caption_rect = Rect::from_min_max(
        egui::pos2(rect.left() + 10.0, rect.bottom() - 46.0),
        rect.max - vec2(10.0, 8.0),
    );
    let painter = ui.painter().with_clip_rect(caption_rect);
    let title_height = if subtitle.is_some() {
        caption_rect.height() - 17.0
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

    if let Some(fraction) = progress_fraction {
        let bar = Rect::from_min_max(
            egui::pos2(rect.left() + 10.0, rect.bottom() - 58.0),
            egui::pos2(rect.right() - 10.0, rect.bottom() - 54.0),
        );
        ui.painter()
            .rect_filled(bar, 2.0, Color32::from_white_alpha(40));
        let filled = Rect::from_min_size(bar.min, vec2(bar.width() * fraction, bar.height()));
        ui.painter()
            .rect_filled(filled, 2.0, palette::ACCENT_BRIGHT);
    }
    if let Some(badge) = badge {
        let galley = ui.painter().layout_no_wrap(
            badge.to_owned(),
            typography::small(),
            palette::TEXT_PRIMARY,
        );
        let badge_rect = Rect::from_min_size(
            rect.min + vec2(8.0, 8.0),
            vec2(galley.size().x + 16.0, 20.0),
        );
        ui.painter().rect_filled(
            badge_rect,
            6.0,
            Color32::from_rgba_premultiplied(8, 10, 13, 200),
        );
        ui.painter().text(
            badge_rect.center(),
            Align2::CENTER_CENTER,
            badge,
            typography::small(),
            palette::TEXT_PRIMARY,
        );
    }

    ui.painter().rect_stroke(
        rect,
        radius,
        theme::card_outline(if response.hovered() { 1.0 } else { 0.0 }),
        egui::StrokeKind::Inside,
    );
    if response.hovered() {
        ui.ctx().set_cursor_icon(CursorIcon::PointingHand);
    }
    response
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

/// Small rounded poster thumbnail for chrome surfaces (next-episode card).
pub(crate) fn paint_poster_thumb(
    ui: &egui::Ui,
    rect: Rect,
    item: &MediaItem,
    posters: &mut PosterCache,
    radius: f32,
) {
    paint_poster_rounded(ui, rect, item, posters, radius);
}

/// Cover-cropped poster art clipped to rounded corners. Falls back to the
/// procedural initials poster with masked corners.
fn paint_poster_rounded(
    ui: &egui::Ui,
    rect: Rect,
    item: &MediaItem,
    posters: &mut PosterCache,
    radius: f32,
) {
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
            egui::Image::from_texture(egui::load::SizedTexture::from_handle(&texture))
                .uv(uv)
                .corner_radius(radius)
                .paint_at(ui, rect);
        }
        _ => {
            paint_initials_poster(ui, rect, &item.series_title);
            mask_rounded_corners(ui, rect, radius, palette::BG_DEEP);
        }
    }
}

/// Covers the square-corner bleed of non-rounded painting with an outside
/// stroke in the page background color (classic corner-mask trick). Only
/// valid when the widget sits on a solid `background` and neighbors are at
/// least `radius` away.
fn mask_rounded_corners(ui: &egui::Ui, rect: Rect, radius: f32, background: Color32) {
    ui.painter().rect_stroke(
        rect,
        radius,
        egui::Stroke::new(radius, background),
        egui::StrokeKind::Outside,
    );
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
    AddLibraryFolder(PathBuf),
    RemoveLibraryFolder(String),
    SaveDandanplayCredentials,
    ClearDandanplayCredentials,
}

pub fn show_settings(
    ctx: &egui::Context,
    preferences: &mut PlayerPreferences,
    connected_url: Option<&str>,
    return_to_playback: bool,
    local_host_status: Option<&LocalHostStatus>,
    local_roots: &[String],
    dandanplay: &mut DandanplayCredentials,
) -> Option<SettingsAction> {
    let mut action = None;
    egui::CentralPanel::default()
        .frame(Frame::NONE.fill(palette::BG_DEEP))
        .show(ctx, |ui| {
            egui::ScrollArea::vertical()
                .auto_shrink([false, false])
                .show(ui, |ui| {
                    let column_width = 720.0_f32.min(ui.available_width() - 2.0 * PAGE_GUTTER);
                    let margin = ((ui.available_width() - column_width) / 2.0).max(PAGE_GUTTER);
                    ui.horizontal(|ui| {
                        ui.add_space(margin);
                        ui.vertical(|ui| {
                            ui.set_width(column_width);
                            if settings_body(
                                ui,
                                preferences,
                                connected_url,
                                return_to_playback,
                                local_host_status,
                                local_roots,
                                dandanplay,
                                &mut action,
                            ) {
                                action = Some(SettingsAction::Back);
                            }
                        });
                    });
                });
        });
    action
}

/// Builds the settings content column. Returns `true` when the header back
/// affordance was pressed.
#[allow(clippy::too_many_arguments)]
fn settings_body(
    ui: &mut egui::Ui,
    preferences: &mut PlayerPreferences,
    connected_url: Option<&str>,
    return_to_playback: bool,
    local_host_status: Option<&LocalHostStatus>,
    local_roots: &[String],
    dandanplay: &mut DandanplayCredentials,
    action: &mut Option<SettingsAction>,
) -> bool {
    let strings = Strings::new(preferences.language);
    let mut back = false;

    ui.add_space(24.0);
    ui.horizontal(|ui| {
        if icon_chip_button(ui, Icon::Back, strings.back()).clicked() {
            back = true;
        }
        ui.add_space(10.0);
        ui.label(
            RichText::new(strings.settings())
                .font(typography::display())
                .strong()
                .color(palette::TEXT_PRIMARY),
        );
    });
    ui.add_space(18.0);

    // Language + playback preferences.
    settings_card(ui, strings.preferences(), |ui| {
        settings_row(ui, strings.language_label(), |ui| {
            language_segmented(ui, &mut preferences.language);
        });
        let strings = Strings::new(preferences.language);
        let mut volume = preferences.volume_percent as f32;
        if let Some(value) = settings_slider_row(
            ui,
            "set_volume",
            strings.default_volume(),
            &format!("{}%", preferences.volume_percent),
            volume,
            0.0..=130.0,
        ) {
            volume = value;
            preferences.volume_percent = volume.round() as u8;
        }
        let mut rate = preferences.playback_rate as f32;
        if let Some(value) = settings_slider_row(
            ui,
            "set_rate",
            strings.playback_rate(),
            &format!("{:.2}×", preferences.playback_rate),
            rate,
            0.5..=2.0,
        ) {
            rate = value;
            preferences.playback_rate = rate as f64;
        }
        settings_toggle_row(
            ui,
            "set_autonext",
            strings.auto_next(),
            &mut preferences.auto_next,
        );
    });

    let strings = Strings::new(preferences.language);
    ui.add_space(14.0);

    // Danmaku defaults.
    settings_card(ui, strings.danmaku_defaults(), |ui| {
        settings_toggle_row(
            ui,
            "set_danmaku_on",
            strings.show_danmaku(),
            &mut preferences.danmaku_enabled,
        );
        if let Some(value) = settings_slider_row(
            ui,
            "set_opacity",
            strings.opacity(),
            &format!("{:.2}", preferences.danmaku_opacity),
            preferences.danmaku_opacity,
            0.0..=1.0,
        ) {
            preferences.danmaku_opacity = value;
        }
        if let Some(value) = settings_slider_row(
            ui,
            "set_speed",
            strings.speed(),
            &format!("{:.2}×", preferences.danmaku_speed),
            preferences.danmaku_speed,
            0.25..=4.0,
        ) {
            preferences.danmaku_speed = value;
        }
        if let Some(value) = settings_slider_row(
            ui,
            "set_density",
            strings.density(),
            &format!("{:.2}", preferences.danmaku_density),
            preferences.danmaku_density,
            0.0..=1.0,
        ) {
            preferences.danmaku_density = value;
        }
        let mut lanes = preferences.danmaku_lanes as f32;
        if let Some(value) = settings_slider_row(
            ui,
            "set_lanes",
            strings.lanes(),
            &preferences.danmaku_lanes.to_string(),
            lanes,
            1.0..=32.0,
        ) {
            lanes = value;
            preferences.danmaku_lanes = lanes.round() as usize;
        }
    });

    ui.add_space(14.0);

    // Server connection.
    let server = connected_url
        .map(str::to_owned)
        .or_else(|| preferences.last_server_url.clone());
    settings_card(ui, strings.server_connection(), |ui| {
        card_status_line(
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
        ui.add_space(4.0);
        ui.horizontal(|ui| {
            if !return_to_playback
                && settings_pill_button(ui, strings.change_server(), false).clicked()
            {
                *action = Some(SettingsAction::ChangeServer);
            }
            if preferences.last_server_url.is_some()
                && settings_pill_button(ui, strings.forget_server(), false).clicked()
            {
                preferences.last_server_url = None;
            }
        });
    });

    // Local hosting.
    if let Some(status) = local_host_status {
        ui.add_space(14.0);
        settings_card(ui, strings.local_hosting(), |ui| {
            let status_text = match status {
                LocalHostStatus::Unavailable => strings.local_server_unavailable().to_owned(),
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
            let status_color = match status {
                LocalHostStatus::Running { .. } => palette::SUCCESS,
                LocalHostStatus::Failed(_) => palette::DANGER,
                LocalHostStatus::Starting => palette::ACCENT_OUTLINE,
                _ => palette::TEXT_MUTED,
            };
            card_status_dot(ui, &status_text, status_color);
            if !return_to_playback && status.is_available() {
                ui.add_space(8.0);
                // Each configured library folder with an inline remove control.
                if local_roots.is_empty() {
                    card_status_line(ui, strings.no_library_folders());
                } else {
                    for root in local_roots {
                        ui.horizontal(|ui| {
                            ui.label(
                                RichText::new(root)
                                    .font(typography::body())
                                    .color(palette::TEXT_PRIMARY),
                            );
                            ui.with_layout(
                                egui::Layout::right_to_left(egui::Align::Center),
                                |ui| {
                                    if settings_pill_button(ui, strings.remove_folder(), false)
                                        .clicked()
                                    {
                                        *action =
                                            Some(SettingsAction::RemoveLibraryFolder(root.clone()));
                                    }
                                },
                            );
                        });
                    }
                }
                ui.add_space(6.0);
                ui.horizontal(|ui| {
                    if settings_pill_button(ui, strings.add_library_folder(), false).clicked() {
                        let mut dialog = rfd::FileDialog::new().set_title(strings.library_folder());
                        if let Some(root) = local_roots.last() {
                            dialog = dialog.set_directory(root);
                        }
                        if let Some(folder) = dialog.pick_folder() {
                            *action = Some(SettingsAction::AddLibraryFolder(folder));
                        }
                    }
                    if status.is_managed_running()
                        && settings_pill_button(ui, strings.restart_server(), false).clicked()
                    {
                        *action = Some(SettingsAction::RestartLocalServer);
                    }
                    if status.is_managed_running()
                        && settings_pill_button(ui, strings.stop_server(), false).clicked()
                    {
                        *action = Some(SettingsAction::StopLocalServer);
                    }
                });
            }
        });

        // Danmaku provider (dandanplay) credentials for the local sidecar.
        ui.add_space(14.0);
        settings_card(ui, strings.danmaku_provider(), |ui| {
            let configured = dandanplay.is_complete();
            card_status_dot(
                ui,
                if configured {
                    strings.dandanplay_configured()
                } else {
                    strings.dandanplay_not_configured()
                },
                if configured {
                    palette::SUCCESS
                } else {
                    palette::TEXT_MUTED
                },
            );
            ui.add_space(6.0);
            settings_row(ui, strings.dandanplay_app_id(), |ui| {
                ui.add(
                    TextEdit::singleline(&mut dandanplay.app_id)
                        .desired_width(320.0)
                        .hint_text("app id"),
                );
            });
            settings_row(ui, strings.dandanplay_app_secret(), |ui| {
                ui.add(
                    TextEdit::singleline(&mut dandanplay.app_secret)
                        .password(true)
                        .desired_width(320.0)
                        .hint_text("app secret"),
                );
            });
            card_status_line(ui, strings.dandanplay_restart_hint());
            ui.add_space(4.0);
            ui.horizontal(|ui| {
                let can_save = !dandanplay.app_id.trim().is_empty()
                    && !dandanplay.app_secret.trim().is_empty();
                if settings_pill_button(ui, strings.save_and_apply(), can_save).clicked()
                    && can_save
                {
                    *action = Some(SettingsAction::SaveDandanplayCredentials);
                }
                if (!dandanplay.app_id.trim().is_empty()
                    || !dandanplay.app_secret.trim().is_empty())
                    && settings_pill_button(ui, strings.clear_credentials(), false).clicked()
                {
                    *action = Some(SettingsAction::ClearDandanplayCredentials);
                }
            });
        });
    }

    // Server administration.
    if let Some(server) = server.as_deref() {
        ui.add_space(14.0);
        settings_card(ui, strings.administration(), |ui| {
            card_status_line(ui, strings.administration_note());
            ui.add_space(4.0);
            ui.hyperlink_to(
                strings.open_web_admin(),
                format!("{}/web/", server.trim_end_matches('/')),
            );
        });
    }

    ui.add_space(16.0);
    ui.horizontal(|ui| {
        ui.add_space(2.0);
        ui.label(
            RichText::new(strings.saved())
                .font(typography::caption())
                .color(palette::TEXT_MUTED),
        );
    });
    ui.add_space(28.0);
    back
}

/// Rounded raised card with a title; runs `add_contents` for the body rows.
fn settings_card(ui: &mut egui::Ui, title: &str, add_contents: impl FnOnce(&mut egui::Ui)) {
    Frame::NONE
        .fill(palette::SURFACE_RAISED)
        .corner_radius(egui::CornerRadius::same(14))
        .inner_margin(egui::Margin::symmetric(20, 16))
        .stroke(egui::Stroke::new(1.0, Color32::from_white_alpha(16)))
        .show(ui, |ui| {
            ui.set_width(ui.available_width());
            ui.label(
                RichText::new(title)
                    .font(typography::title())
                    .strong()
                    .color(palette::TEXT_PRIMARY),
            );
            ui.add_space(10.0);
            add_contents(ui);
        });
}

/// A card row: label on the left, `control` right-aligned on the same baseline.
fn settings_row(ui: &mut egui::Ui, label: &str, control: impl FnOnce(&mut egui::Ui)) {
    ui.add_space(4.0);
    ui.horizontal(|ui| {
        ui.set_min_height(30.0);
        ui.label(
            RichText::new(label)
                .font(typography::body())
                .color(palette::TEXT_SECONDARY),
        );
        ui.with_layout(Layout::right_to_left(Align::Center), control);
    });
    ui.add_space(4.0);
}

/// Label + themed slider + value readout, laid out as a single card row.
fn settings_slider_row(
    ui: &mut egui::Ui,
    id_source: &str,
    label: &str,
    value_text: &str,
    value: f32,
    range: std::ops::RangeInclusive<f32>,
) -> Option<f32> {
    let mut result = None;
    let (min, max) = (*range.start(), *range.end());
    let fraction = if max > min {
        ((value - min) / (max - min)).clamp(0.0, 1.0)
    } else {
        0.0
    };
    settings_row(ui, label, |ui| {
        ui.label(
            RichText::new(value_text)
                .font(typography::body())
                .color(palette::TEXT_MUTED),
        );
        ui.add_space(14.0);
        let (rect, _) = ui.allocate_exact_size(vec2(230.0, 24.0), Sense::hover());
        if let Some(new_fraction) = themed_slider(ui, id_source, rect, fraction) {
            result = Some(min + new_fraction * (max - min));
        }
    });
    result
}

/// Thin track with an accent trailing fill and a round thumb. Returns the new
/// fraction while the user clicks or drags.
fn themed_slider(ui: &mut egui::Ui, id_source: &str, rect: Rect, fraction: f32) -> Option<f32> {
    let response = ui.interact(rect, ui.id().with(id_source), Sense::click_and_drag());
    let track = Rect::from_center_size(rect.center(), vec2(rect.width(), 5.0));
    let painter = ui.painter();
    painter.rect_filled(track, 2.5, Color32::from_white_alpha(40));
    let fraction = fraction.clamp(0.0, 1.0);
    painter.rect_filled(
        Rect::from_min_size(track.min, vec2(track.width() * fraction, track.height())),
        2.5,
        palette::ACCENT_BRIGHT,
    );
    let engaged = response.hovered() || response.dragged();
    painter.circle_filled(
        pos2(track.left() + track.width() * fraction, track.center().y),
        if engaged { 9.0 } else { 7.0 },
        Color32::WHITE,
    );
    if engaged {
        ui.ctx().set_cursor_icon(CursorIcon::PointingHand);
    }
    if (response.clicked() || response.dragged())
        && let Some(pointer) = response.interact_pointer_pos()
        && track.width() > 0.0
    {
        return Some(((pointer.x - track.left()) / track.width()).clamp(0.0, 1.0));
    }
    None
}

/// Label + animated pill toggle, laid out as a single card row.
fn settings_toggle_row(ui: &mut egui::Ui, id_source: &str, label: &str, value: &mut bool) {
    settings_row(ui, label, |ui| {
        let (rect, response) = ui.allocate_exact_size(vec2(46.0, 26.0), Sense::click());
        if response.clicked() {
            *value = !*value;
        }
        let how_on = ui
            .ctx()
            .animate_bool_with_time(ui.id().with(id_source), *value, 0.12);
        let track = theme::mix(palette::SURFACE_FAINT, palette::ACCENT_BRIGHT, how_on);
        ui.painter().rect_filled(rect, rect.height() / 2.0, track);
        let knob_x = rect.left() + 13.0 + (rect.width() - 26.0) * how_on;
        ui.painter()
            .circle_filled(pos2(knob_x, rect.center().y), 9.0, Color32::WHITE);
        if response.hovered() {
            ui.ctx().set_cursor_icon(CursorIcon::PointingHand);
        }
    });
}

/// Segmented two-language selector, right-aligned inside a settings row.
fn language_segmented(ui: &mut egui::Ui, current: &mut Language) {
    let options = Language::ALL;
    let segment_width = 128.0;
    let height = 34.0;
    let (rect, _) = ui.allocate_exact_size(
        vec2(segment_width * options.len() as f32, height),
        Sense::hover(),
    );
    ui.painter().rect_filled(rect, 9.0, palette::SURFACE_FAINT);
    for (index, option) in options.into_iter().enumerate() {
        let segment = Rect::from_min_size(
            pos2(rect.left() + index as f32 * segment_width, rect.top()),
            vec2(segment_width, height),
        );
        let response = ui.interact(segment, ui.id().with(("lang_seg", index)), Sense::click());
        let selected = *current == option;
        if selected {
            ui.painter()
                .rect_filled(segment.shrink(3.0), 7.0, palette::ACCENT);
        }
        ui.painter().text(
            segment.center(),
            Align2::CENTER_CENTER,
            option.native_name(),
            typography::body(),
            if selected {
                Color32::WHITE
            } else {
                palette::TEXT_MUTED
            },
        );
        if response.clicked() {
            *current = option;
        }
        if response.hovered() {
            ui.ctx().set_cursor_icon(CursorIcon::PointingHand);
        }
    }
}

/// Compact pill button for card actions. `primary` uses the accent fill.
fn settings_pill_button(ui: &mut egui::Ui, label: &str, primary: bool) -> egui::Response {
    let galley =
        ui.painter()
            .layout_no_wrap(label.to_owned(), typography::body(), palette::TEXT_PRIMARY);
    let (rect, response) =
        ui.allocate_exact_size(vec2(galley.size().x + 32.0, 36.0), Sense::click());
    let fill = if primary {
        if response.hovered() {
            palette::ACCENT_OUTLINE
        } else {
            palette::ACCENT_BRIGHT
        }
    } else if response.hovered() {
        palette::WIDGET_HOVER
    } else {
        palette::SURFACE_FAINT
    };
    ui.painter().rect_filled(rect, 9.0, fill);
    if !primary {
        ui.painter().rect_stroke(
            rect,
            9.0,
            egui::Stroke::new(1.0, Color32::from_white_alpha(20)),
            egui::StrokeKind::Inside,
        );
    }
    ui.painter().text(
        rect.center(),
        Align2::CENTER_CENTER,
        label,
        typography::body(),
        if primary {
            Color32::WHITE
        } else {
            palette::TEXT_SECONDARY
        },
    );
    if response.hovered() {
        ui.ctx().set_cursor_icon(CursorIcon::PointingHand);
    }
    response
}

/// Muted descriptive line inside a settings card.
fn card_status_line(ui: &mut egui::Ui, text: &str) {
    ui.label(
        RichText::new(text)
            .font(typography::caption())
            .color(palette::TEXT_MUTED),
    );
}

/// Status line prefixed with a colored dot inside a settings card.
fn card_status_dot(ui: &mut egui::Ui, text: &str, color: Color32) {
    ui.horizontal(|ui| {
        let (dot, _) = ui.allocate_exact_size(vec2(12.0, 12.0), Sense::hover());
        ui.painter().circle_filled(dot.center(), 4.5, color);
        ui.label(
            RichText::new(text)
                .font(typography::caption())
                .color(palette::TEXT_MUTED),
        );
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
