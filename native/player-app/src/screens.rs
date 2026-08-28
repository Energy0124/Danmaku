//! Library-mode screens: server connect and catalog browse.

use std::{
    collections::{BTreeMap, BTreeSet},
    path::PathBuf,
};

use eframe::egui::{
    self, Align, Align2, Color32, CursorIcon, FontId, Frame, Layout, Rect, RichText, Sense,
    TextEdit, pos2, vec2,
};

use crate::{
    branding::Branding,
    danmaku::BangumiDetail,
    discovery::DiscoveredServer,
    hosting::{LocalHostOwnership, LocalHostStatus},
    icons::{Icon, paint_icon},
    library::{
        AttentionCacheStatus, AttentionIssueCode, AttentionMappingStatus, AttentionRepairRequest,
        DEFAULT_NEXT_UP_LIMIT, FolderListing, LibraryAttentionDocument, LibraryCatalog,
        MINIMUM_REMAINING_MS, MINIMUM_RESUME_POSITION_MS, MediaItem, NextUpItem, NextUpReason,
        OrganizationPreviewRequest, OrganizationSeriesBatch, OrganizationSeriesOverride,
        PlaybackProgress, ProgressItem, Series, continue_watching_items, file_name,
        folder_grouped_series, grouped_series, item_in_folder_shortcut, library_folder_shortcuts,
        library_root_labels, matched_anime_series, next_up_items, scoped_folder_listing,
    },
    localization::{Language, Strings},
    posters::{PosterCache, PosterState},
    preferences::{DandanplayCredentials, PlayerPreferences},
    session::LibrarySession,
    theme::{self, metrics, palette, typography},
    updater::UpdateStatus,
};

#[cfg(windows)]
use crate::updater::LATEST_INSTALLER_URL;

const CARD_WIDTH: f32 = 158.0;
const CARD_HEIGHT: f32 = 236.0;
const CARD_GAP: f32 = 16.0;
const RAIL_LIMIT: usize = 12;
/// Left inset of library page content, beyond the navigation rail.
const PAGE_GUTTER: f32 = 26.0;
fn paint_focus_outline(ui: &egui::Ui, rect: Rect, radius: f32, response: &egui::Response) {
    if response.has_focus() {
        ui.painter().rect_stroke(
            rect.shrink(1.0),
            radius,
            egui::Stroke::new(2.0, palette::TEXT_PRIMARY),
            egui::StrokeKind::Inside,
        );
    }
}

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

mod connect;
pub use connect::{ConnectAction, ConnectRequest, ConnectScreen};
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
    RepairAttention {
        requests: Vec<AttentionRepairRequest>,
    },
    /// Opens the manual danmaku match picker for one episode.
    ChangeMatch {
        media_id: String,
    },
    /// Requests the dandanplay bangumi profile shown on a series page.
    FetchBangumiDetail {
        anime_id: u64,
    },
    Refresh,
    RescanFolder {
        path: Vec<String>,
    },
    PreviewOrganization(OrganizationPreviewRequest),
    ExecuteOrganization {
        plan_id: String,
        batch: OrganizationSeriesBatch,
    },
    RefreshOrganizationStatus,
    CancelOrganization,
    UndoOrganization {
        completed_batch_id: String,
    },
    Disconnect,
    Settings,
}

/// Lifecycle of one bangumi profile fetch, keyed by dandanplay anime ID
/// (owned by the app, rendered by the series page).
#[derive(Clone, Debug, PartialEq)]
pub enum BangumiDetailState {
    Loading,
    Ready(BangumiDetail),
    Failed(String),
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
    /// Every series ordered by the newest file indexed into the library.
    #[default]
    Recent,
    /// Series the user has actually watched, grouped by the month they
    /// were last played (the official client's 最近播放).
    RecentlyPlayed,
    /// Recognized anime grouped by release year.
    Season,
    /// Only items with a recognized dandanplay/provider anime match,
    /// grouped by that identity.
    MatchedAnime,
    /// Every item, browsed through its on-disk folder hierarchy.
    Folder,
}

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
enum LibraryMatchFilter {
    #[default]
    All,
    Matched,
    Unmatched,
}

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
enum LibraryProgressFilter {
    #[default]
    All,
    Unwatched,
    InProgress,
    Completed,
}

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
enum LibrarySeriesSort {
    #[default]
    Title,
    Newest,
    ReleaseYear,
    EpisodeCount,
}

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
enum LibraryGridDensity {
    Compact,
    #[default]
    Comfortable,
    Large,
}

impl LibraryGridDensity {
    fn card_size(self) -> egui::Vec2 {
        match self {
            Self::Compact => vec2(126.0, 190.0),
            Self::Comfortable => vec2(CARD_WIDTH, CARD_HEIGHT),
            Self::Large => vec2(190.0, 284.0),
        }
    }
}

pub struct LibraryScreen {
    query: String,
    view: LibraryView,
    all_series_tab: LibrarySeriesTab,
    match_filter: LibraryMatchFilter,
    attention_only: bool,
    progress_filter: LibraryProgressFilter,
    series_sort: LibrarySeriesSort,
    grid_density: LibraryGridDensity,
    selected_folder: Option<String>,
    selected_year: Option<i32>,
    /// Whether Recent/Season/Recently-played render their month/year
    /// section groups (the official client's 分組顯示 toggle).
    group_display: bool,
    /// Mixed grouping (anime identity when recognized, folder otherwise) —
    /// only used for the low-visibility Home "recently added" rail, where a
    /// little mixing is a reasonable tradeoff for always showing every item.
    cached_series: Vec<Series>,
    /// Recognized-anime-only grouping, never mixed with folder entries. Used
    /// by the "All series" page's "Matched anime" tab.
    cached_anime_series: Vec<Series>,
    /// Folder-only grouping, kept for `find_series` so episode pages reached
    /// from older folder cards keep working.
    cached_folder_series: Vec<Series>,
    /// The `LibrarySession::catalog_version` the caches above were last
    /// built from. `None` never matches a real version, so the first render
    /// always (re)builds them.
    cached_catalog_version: Option<u64>,
    /// Current location inside the "Folders" explorer tab (path components
    /// of the items' `relative_path`s).
    folder_path: Vec<String>,
    cached_folder_listing: FolderListing,
    /// (catalog version, path) the listing above was computed for.
    cached_folder_listing_key: Option<(u64, Vec<String>)>,
    organizer_open: bool,
    organizer_root: String,
    organizer_base: String,
    organizer_batch_id: Option<String>,
    organizer_series_title: String,
    organizer_season: String,
    organizer_nearby: BTreeSet<String>,
}

impl Default for LibraryScreen {
    fn default() -> Self {
        Self {
            query: String::new(),
            view: LibraryView::Home,
            all_series_tab: LibrarySeriesTab::default(),
            match_filter: LibraryMatchFilter::default(),
            attention_only: false,
            progress_filter: LibraryProgressFilter::default(),
            series_sort: LibrarySeriesSort::default(),
            grid_density: LibraryGridDensity::default(),
            selected_folder: None,
            selected_year: None,
            group_display: true,
            cached_series: Vec::new(),
            cached_anime_series: Vec::new(),
            cached_folder_series: Vec::new(),
            cached_catalog_version: None,
            folder_path: Vec::new(),
            cached_folder_listing: FolderListing::default(),
            cached_folder_listing_key: None,
            organizer_open: false,
            organizer_root: String::new(),
            organizer_base: "Anime".to_owned(),
            organizer_batch_id: None,
            organizer_series_title: String::new(),
            organizer_season: String::new(),
            organizer_nearby: BTreeSet::new(),
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
        bangumi: &std::collections::HashMap<u64, BangumiDetailState>,
        strings: Strings,
    ) -> Option<LibraryAction> {
        let mut action = None;
        let folder_shortcuts = session
            .catalog
            .as_ref()
            .map(library_folder_shortcuts)
            .unwrap_or_default();

        egui::SidePanel::left("library_navigation")
            .exact_width(metrics::NAV_RAIL_WIDTH)
            .resizable(false)
            .frame(Frame::NONE.fill(palette::BG_NAV))
            .show(ctx, |ui| {
                ui.add_space(22.0);
                ui.horizontal(|ui| {
                    ui.add_space(18.0);
                    ui.label(
                        RichText::new("Danmaku")
                            .font(typography::hero())
                            .strong()
                            .color(palette::TEXT_PRIMARY),
                    );
                });
                ui.add_space(20.0);
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

                sidebar_heading(ui, strings.library_views());
                for (tab, icon, label) in [
                    (
                        LibrarySeriesTab::Recent,
                        Icon::Refresh,
                        strings.recent_view(),
                    ),
                    (
                        LibrarySeriesTab::RecentlyPlayed,
                        Icon::Play,
                        strings.recently_played(),
                    ),
                    (
                        LibrarySeriesTab::Season,
                        Icon::Library,
                        strings.season_view(),
                    ),
                    (
                        LibrarySeriesTab::MatchedAnime,
                        Icon::Danmaku,
                        strings.matched_anime(),
                    ),
                    (LibrarySeriesTab::Folder, Icon::Folder, strings.folders()),
                ] {
                    let selected =
                        self.view == LibraryView::AllSeries && self.all_series_tab == tab;
                    if nav_button(ui, icon, label, selected).clicked() {
                        self.view = LibraryView::AllSeries;
                        self.all_series_tab = tab;
                        if tab != LibrarySeriesTab::Folder {
                            self.folder_path.clear();
                        }
                    }
                }

                if !folder_shortcuts.is_empty() {
                    sidebar_heading(ui, strings.library_folders());
                    egui::ScrollArea::vertical()
                        .id_salt("library-root-navigation")
                        .max_height((ui.available_height() - 150.0).max(100.0))
                        .show(ui, |ui| {
                            for (folder, item_count) in &folder_shortcuts {
                                let selected = self.view == LibraryView::AllSeries
                                    && self.all_series_tab == LibrarySeriesTab::Folder
                                    && self.folder_path.first() == Some(folder);
                                if folder_nav_button(ui, folder, *item_count, selected).clicked() {
                                    self.view = LibraryView::AllSeries;
                                    self.all_series_tab = LibrarySeriesTab::Folder;
                                    self.folder_path = vec![folder.clone()];
                                    self.selected_folder = Some(folder.clone());
                                    self.query.clear();
                                }
                            }
                        });
                }

                ui.with_layout(Layout::bottom_up(Align::Center), |ui| {
                    ui.add_space(12.0);
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

                let inner_action = if !self.query.trim().is_empty()
                    && self.view != LibraryView::AllSeries
                {
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
                            &catalog.root_name,
                            &session.progresses,
                            posters,
                            pending_preloads,
                            bangumi,
                            session.attention.as_ref(),
                            strings,
                        ),
                    }
                };
                if inner_action.is_some() {
                    action = inner_action;
                }
            });
        if self.organizer_open
            && let Some(organizer_action) = self.show_organizer(ctx, session, strings)
        {
            action = Some(organizer_action);
        }
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
                let label = if !session.connected {
                    strings.connecting_to_server().to_owned()
                } else if local {
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
                let folders = session
                    .catalog
                    .as_ref()
                    .map(library_folder_shortcuts)
                    .unwrap_or_default();
                self.show_series_filter_toolbar(ui, &folders, session.attention.is_some(), strings);
                ui.add_space(16.0);

                if self.all_series_tab == LibrarySeriesTab::Folder {
                    if let Some(explorer_action) = self.show_folder_explorer(ui, session, strings) {
                        action = Some(explorer_action);
                    }
                    ui.add_space(28.0);
                    return;
                }

                let source = match self.all_series_tab {
                    LibrarySeriesTab::Recent | LibrarySeriesTab::RecentlyPlayed => {
                        &self.cached_series
                    }
                    LibrarySeriesTab::Season | LibrarySeriesTab::MatchedAnime => {
                        &self.cached_anime_series
                    }
                    LibrarySeriesTab::Folder => unreachable!(),
                };
                let mut filtered = filtered_library_series(
                    source,
                    &self.query,
                    self.match_filter,
                    self.progress_filter,
                    self.selected_folder.as_deref(),
                    self.selected_year,
                    self.series_sort,
                    &session.progresses,
                );
                if self.attention_only {
                    filtered.retain(|series| {
                        series_attention_count(series, session.attention.as_ref()) > 0
                    });
                }
                if self.all_series_tab == LibrarySeriesTab::RecentlyPlayed {
                    // Only series actually played, most recently played
                    // first — recency is the whole point of this view.
                    filtered.retain(|series| {
                        series_latest_played_at(series, &session.progresses).is_some()
                    });
                    filtered.sort_by_key(|series| {
                        std::cmp::Reverse(series_latest_played_at(series, &session.progresses))
                    });
                }
                let view_label = match self.all_series_tab {
                    LibrarySeriesTab::Recent => strings.recent_view(),
                    LibrarySeriesTab::RecentlyPlayed => strings.recently_played(),
                    LibrarySeriesTab::Season => strings.season_view(),
                    LibrarySeriesTab::MatchedAnime => strings.matched_anime(),
                    LibrarySeriesTab::Folder => strings.folders(),
                };
                section_heading(
                    ui,
                    &format!("{view_label}  ·  {} {}", filtered.len(), strings.titles()),
                );
                if filtered.is_empty() {
                    muted_line(ui, strings.no_filtered_series());
                    ui.add_space(28.0);
                    return;
                }

                let groups = match self.all_series_tab {
                    _ if !self.group_display => None,
                    LibrarySeriesTab::Recent => Some(recent_series_groups(&filtered, strings)),
                    LibrarySeriesTab::RecentlyPlayed => Some(recently_played_groups(
                        &filtered,
                        &session.progresses,
                        strings,
                    )),
                    LibrarySeriesTab::Season => Some(season_series_groups(&filtered, strings)),
                    LibrarySeriesTab::MatchedAnime => None,
                    LibrarySeriesTab::Folder => unreachable!(),
                };
                match groups {
                    Some(groups) => {
                        for (heading, series) in groups {
                            section_subheading(ui, &format!("{heading}  ·  {}", series.len()));
                            if let Some(series_id) = series_grid(
                                ui,
                                &series,
                                posters,
                                strings,
                                self.grid_density,
                                session.attention.as_ref(),
                            ) {
                                self.view = LibraryView::Series(series_id);
                            }
                            ui.add_space(14.0);
                        }
                    }
                    None => {
                        if let Some(series_id) = series_grid(
                            ui,
                            &filtered,
                            posters,
                            strings,
                            self.grid_density,
                            session.attention.as_ref(),
                        ) {
                            self.view = LibraryView::Series(series_id);
                        }
                    }
                }
                ui.add_space(28.0);
            });
        action
    }

    /// One wrapped row of themed filter chips. View selection lives in
    /// the sidebar only; this bar is purely about narrowing the current
    /// view.
    fn show_series_filter_toolbar(
        &mut self,
        ui: &mut egui::Ui,
        folders: &[(String, usize)],
        attention_available: bool,
        strings: Strings,
    ) {
        let folder_before = self.selected_folder.clone();
        ui.horizontal(|ui| {
            ui.add_space(PAGE_GUTTER);
            Frame::NONE
                .fill(palette::SURFACE_RAISED)
                .corner_radius(egui::CornerRadius::same(12))
                .inner_margin(egui::Margin::symmetric(12, 10))
                .stroke(egui::Stroke::new(1.0, Color32::from_white_alpha(16)))
                .show(ui, |ui| {
                    ui.set_width((ui.available_width() - PAGE_GUTTER).max(560.0));
                    ui.horizontal_wrapped(|ui| {
                        ui.spacing_mut().item_spacing = vec2(8.0, 8.0);
                        let browsing_folders = self.all_series_tab == LibrarySeriesTab::Folder;

                        if !browsing_folders {
                            const MATCH_CHOICES: [LibraryMatchFilter; 3] = [
                                LibraryMatchFilter::All,
                                LibraryMatchFilter::Matched,
                                LibraryMatchFilter::Unmatched,
                            ];
                            let match_label = |filter: LibraryMatchFilter| match filter {
                                LibraryMatchFilter::All => strings.all_matches(),
                                LibraryMatchFilter::Matched => strings.matched_only(),
                                LibraryMatchFilter::Unmatched => strings.unmatched_only(),
                            };
                            let options: Vec<(String, bool)> = MATCH_CHOICES
                                .iter()
                                .map(|choice| {
                                    (
                                        match_label(*choice).to_owned(),
                                        *choice == self.match_filter,
                                    )
                                })
                                .collect();
                            if let Some(picked) = filter_dropdown(
                                ui,
                                "library-match-filter",
                                "",
                                match_label(self.match_filter),
                                &options,
                                self.match_filter != LibraryMatchFilter::All,
                            ) {
                                self.match_filter = MATCH_CHOICES[picked];
                            }
                            if attention_available {
                                filter_toggle_chip(
                                    ui,
                                    strings.needs_attention(),
                                    &mut self.attention_only,
                                );
                            }

                            const PROGRESS_CHOICES: [LibraryProgressFilter; 4] = [
                                LibraryProgressFilter::All,
                                LibraryProgressFilter::Unwatched,
                                LibraryProgressFilter::InProgress,
                                LibraryProgressFilter::Completed,
                            ];
                            let progress_label = |filter: LibraryProgressFilter| match filter {
                                LibraryProgressFilter::All => strings.all_progress(),
                                LibraryProgressFilter::Unwatched => strings.unwatched(),
                                LibraryProgressFilter::InProgress => strings.in_progress(),
                                LibraryProgressFilter::Completed => strings.completed(),
                            };
                            let options: Vec<(String, bool)> = PROGRESS_CHOICES
                                .iter()
                                .map(|choice| {
                                    (
                                        progress_label(*choice).to_owned(),
                                        *choice == self.progress_filter,
                                    )
                                })
                                .collect();
                            if let Some(picked) = filter_dropdown(
                                ui,
                                "library-progress-filter",
                                "",
                                progress_label(self.progress_filter),
                                &options,
                                self.progress_filter != LibraryProgressFilter::All,
                            ) {
                                self.progress_filter = PROGRESS_CHOICES[picked];
                            }

                            let mut years: Vec<i32> = self
                                .cached_anime_series
                                .iter()
                                .filter_map(series_release_year)
                                .collect();
                            years.sort_unstable_by(|left, right| right.cmp(left));
                            years.dedup();
                            if !years.is_empty() || self.selected_year.is_some() {
                                let mut options = vec![(
                                    strings.all_years().to_owned(),
                                    self.selected_year.is_none(),
                                )];
                                options.extend(years.iter().map(|year| {
                                    (year.to_string(), self.selected_year == Some(*year))
                                }));
                                let value = self
                                    .selected_year
                                    .map(|year| year.to_string())
                                    .unwrap_or_else(|| strings.all_years().to_owned());
                                if let Some(picked) = filter_dropdown(
                                    ui,
                                    "library-year-filter",
                                    "",
                                    &value,
                                    &options,
                                    self.selected_year.is_some(),
                                ) {
                                    self.selected_year = (picked > 0).then(|| years[picked - 1]);
                                }
                            }
                        }

                        let mut options = vec![(
                            strings.all_folders().to_owned(),
                            self.selected_folder.is_none(),
                        )];
                        options.extend(folders.iter().map(|(folder, item_count)| {
                            (
                                format!("{folder}  \u{b7}  {item_count}"),
                                self.selected_folder.as_deref() == Some(folder.as_str()),
                            )
                        }));
                        let value = self
                            .selected_folder
                            .as_deref()
                            .unwrap_or(strings.all_folders())
                            .to_owned();
                        if let Some(picked) = filter_dropdown(
                            ui,
                            "library-folder-filter",
                            "",
                            &value,
                            &options,
                            self.selected_folder.is_some(),
                        ) {
                            self.selected_folder =
                                (picked > 0).then(|| folders[picked - 1].0.clone());
                        }

                        if !browsing_folders {
                            const SORT_CHOICES: [LibrarySeriesSort; 4] = [
                                LibrarySeriesSort::Title,
                                LibrarySeriesSort::Newest,
                                LibrarySeriesSort::ReleaseYear,
                                LibrarySeriesSort::EpisodeCount,
                            ];
                            let sort_label = |sort: LibrarySeriesSort| match sort {
                                LibrarySeriesSort::Title => strings.sort_title(),
                                LibrarySeriesSort::Newest => strings.sort_newest(),
                                LibrarySeriesSort::ReleaseYear => strings.sort_release_year(),
                                LibrarySeriesSort::EpisodeCount => strings.sort_episode_count(),
                            };
                            let options: Vec<(String, bool)> = SORT_CHOICES
                                .iter()
                                .map(|choice| {
                                    (sort_label(*choice).to_owned(), *choice == self.series_sort)
                                })
                                .collect();
                            if let Some(picked) = filter_dropdown(
                                ui,
                                "library-sort",
                                strings.sort_by(),
                                sort_label(self.series_sort),
                                &options,
                                self.series_sort != LibrarySeriesSort::Title,
                            ) {
                                self.series_sort = SORT_CHOICES[picked];
                            }

                            const DENSITY_CHOICES: [LibraryGridDensity; 3] = [
                                LibraryGridDensity::Compact,
                                LibraryGridDensity::Comfortable,
                                LibraryGridDensity::Large,
                            ];
                            let density_label = |density: LibraryGridDensity| match density {
                                LibraryGridDensity::Compact => strings.compact(),
                                LibraryGridDensity::Comfortable => strings.comfortable(),
                                LibraryGridDensity::Large => strings.large(),
                            };
                            let options: Vec<(String, bool)> = DENSITY_CHOICES
                                .iter()
                                .map(|choice| {
                                    (
                                        density_label(*choice).to_owned(),
                                        *choice == self.grid_density,
                                    )
                                })
                                .collect();
                            if let Some(picked) = filter_dropdown(
                                ui,
                                "library-grid-density",
                                strings.grid_size(),
                                density_label(self.grid_density),
                                &options,
                                self.grid_density != LibraryGridDensity::Comfortable,
                            ) {
                                self.grid_density = DENSITY_CHOICES[picked];
                            }
                        }

                        if matches!(
                            self.all_series_tab,
                            LibrarySeriesTab::Recent
                                | LibrarySeriesTab::RecentlyPlayed
                                | LibrarySeriesTab::Season
                        ) {
                            filter_toggle_chip(
                                ui,
                                strings.group_display(),
                                &mut self.group_display,
                            );
                        }

                        let filters_active = self.match_filter != LibraryMatchFilter::All
                            || self.attention_only
                            || self.progress_filter != LibraryProgressFilter::All
                            || self.selected_folder.is_some()
                            || self.selected_year.is_some()
                            || self.series_sort != LibrarySeriesSort::Title
                            || self.grid_density != LibraryGridDensity::Comfortable
                            || !self.group_display
                            || !self.query.trim().is_empty();
                        if filters_active
                            && toolbar_chip_button(ui, strings.clear_filters()).clicked()
                        {
                            self.attention_only = false;
                            self.match_filter = LibraryMatchFilter::All;
                            self.progress_filter = LibraryProgressFilter::All;
                            self.selected_folder = None;
                            self.selected_year = None;
                            self.series_sort = LibrarySeriesSort::Title;
                            self.grid_density = LibraryGridDensity::Comfortable;
                            self.group_display = true;
                            self.query.clear();
                            self.folder_path.clear();
                        }
                    });
                });
        });
        if self.all_series_tab == LibrarySeriesTab::Folder && folder_before != self.selected_folder
        {
            self.folder_path = self.selected_folder.clone().into_iter().collect();
            self.cached_folder_listing_key = None;
        }
    }

    /// File-explorer style browse of the library's on-disk layout, like the
    /// official dandanplay client's media library: folder rows navigate,
    /// file rows show the file name/size plus the matched anime and episode
    /// titles, with the per-row match button to fix a wrong match.
    fn show_folder_explorer(
        &mut self,
        ui: &mut egui::Ui,
        session: &LibrarySession,
        strings: Strings,
    ) -> Option<LibraryAction> {
        let Some(catalog) = &session.catalog else {
            return None;
        };
        let key = (session.catalog_version, self.folder_path.clone());
        if self.cached_folder_listing_key.as_ref() != Some(&key) {
            self.cached_folder_listing = scoped_folder_listing(catalog, &self.folder_path);
            self.cached_folder_listing_key = Some(key);
        }

        let query = self.query.trim().to_lowercase();
        let visible_folders: Vec<_> = self
            .cached_folder_listing
            .folders
            .iter()
            .filter(|folder| query.is_empty() || folder.name.to_lowercase().contains(&query))
            .collect();
        let visible_files: Vec<_> = self
            .cached_folder_listing
            .files
            .iter()
            .filter(|item| {
                query.is_empty()
                    || item.episode_title.to_lowercase().contains(&query)
                    || item.relative_path.to_lowercase().contains(&query)
                    || item.series_title.to_lowercase().contains(&query)
            })
            .collect();

        // With several attributed roots the first path component is already
        // an absolute root path (e.g. `M:\Anime`), so the merged catalog
        // name would only add noise in front of it.
        let multi_root = library_root_labels(catalog).len() >= 2;
        let heading = match (multi_root, self.folder_path.is_empty()) {
            (_, true) => catalog.root_name.clone(),
            (true, false) => self.folder_path.join("\\"),
            (false, false) => {
                format!("{}\\{}", catalog.root_name, self.folder_path.join("\\"))
            }
        };
        let total = visible_folders.len() + visible_files.len();
        section_heading(
            ui,
            &format!("{heading}  ·  {total} {}", strings.items_label()),
        );

        let mut action = None;
        ui.horizontal(|ui| {
            ui.add_space(PAGE_GUTTER);
            if ui
                .add_enabled(
                    !session.server_scanning,
                    egui::Button::new(strings.refresh_folder()),
                )
                .clicked()
            {
                action = Some(LibraryAction::RescanFolder {
                    path: self.folder_path.clone(),
                });
            }
            if session.server_scanning {
                ui.add(egui::Spinner::new().size(14.0));
                let status = session.server_scan_files_seen.map_or_else(
                    || strings.scanning_folder().to_owned(),
                    |files| {
                        format!(
                            "{} · {}",
                            strings.scanning_folder(),
                            strings.indexing_files_found(files)
                        )
                    },
                );
                ui.label(RichText::new(status).color(palette::TEXT_SECONDARY));
            }
            let local = session.base_url.starts_with("http://127.")
                || session.base_url.starts_with("http://localhost")
                || session.base_url.starts_with("http://[::1]");
            let organize = ui.add_enabled(
                local && !session.server_scanning,
                egui::Button::new(strings.organize_library()),
            );
            if organize.clicked() {
                let roots = library_root_labels(catalog);
                self.organizer_root = roots
                    .iter()
                    .find(|(root, _)| self.folder_path.first() == Some(root))
                    .or_else(|| roots.first())
                    .map(|(root, _)| root.clone())
                    .unwrap_or_default();
                self.organizer_open = true;
                action = Some(LibraryAction::RefreshOrganizationStatus);
            }
            organize.on_hover_text(strings.organizer_safety());
        });
        if let Some(error) = &session.server_scan_error {
            ui.horizontal(|ui| {
                ui.add_space(PAGE_GUTTER);
                ui.label(
                    RichText::new(format!("{} {error}", strings.folder_scan_failed()))
                        .color(palette::DANGER),
                );
            });
        }
        ui.add_space(8.0);
        let mut navigate: Option<Option<String>> = None;
        if !self.folder_path.is_empty() && explorer_folder_row(ui, None, 0, strings).clicked() {
            navigate = Some(None);
        }
        for folder in visible_folders {
            if explorer_folder_row(ui, Some(&folder.name), folder.item_count, strings).clicked() {
                navigate = Some(Some(folder.name.clone()));
            }
        }
        for item in visible_files {
            let row = explorer_file_row(ui, item, strings);
            if row.play_clicked {
                action = Some(LibraryAction::Play {
                    media_id: item.id.clone(),
                });
            } else if row.change_match_clicked {
                action = Some(LibraryAction::ChangeMatch {
                    media_id: item.id.clone(),
                });
            }
        }
        if total == 0 {
            muted_line(ui, strings.no_episodes());
        }
        match navigate {
            Some(None) => {
                self.folder_path.pop();
            }
            Some(Some(folder)) => self.folder_path.push(folder),
            None => {}
        }
        action
    }

    fn show_organizer(
        &mut self,
        ctx: &egui::Context,
        session: &LibrarySession,
        strings: Strings,
    ) -> Option<LibraryAction> {
        let mut action = None;
        let mut open = self.organizer_open;
        egui::Window::new(strings.organizer_title())
            .open(&mut open)
            .resizable(true)
            .collapsible(false)
            .default_width(760.0)
            .max_height(ctx.available_rect().height() * 0.9)
            .show(ctx, |ui| {
                ui.label(RichText::new(strings.organizer_safety()).color(palette::TEXT_SECONDARY));
                ui.add_space(8.0);
                let organizer_busy = session.organization_status.as_ref().is_some_and(|status| {
                    matches!(status.state.as_str(), "RUNNING" | "ROLLING_BACK")
                });
                let roots = session
                    .catalog
                    .as_ref()
                    .map(library_root_labels)
                    .unwrap_or_default();
                egui::ComboBox::from_label(strings.organizer_root())
                    .selected_text(if self.organizer_root.is_empty() {
                        strings.organizer_root()
                    } else {
                        &self.organizer_root
                    })
                    .show_ui(ui, |ui| {
                        for (root, _) in roots {
                            ui.selectable_value(&mut self.organizer_root, root.clone(), root);
                        }
                    });
                ui.horizontal(|ui| {
                    ui.label(strings.organizer_base());
                    ui.add(TextEdit::singleline(&mut self.organizer_base).desired_width(300.0));
                    if ui
                        .add_enabled(
                            !self.organizer_root.is_empty()
                                && !session.organization_loading
                                && !organizer_busy,
                            egui::Button::new(strings.generate_preview()),
                        )
                        .clicked()
                    {
                        self.organizer_batch_id = None;
                        action = Some(LibraryAction::PreviewOrganization(
                            OrganizationPreviewRequest {
                                root: self.organizer_root.clone(),
                                base_relative_path: self.organizer_base.clone(),
                                overrides: Vec::new(),
                            },
                        ));
                    }
                });
                if session.organization_loading {
                    ui.add(egui::Spinner::new());
                }
                if let Some(error) = &session.organization_error {
                    ui.label(RichText::new(error).color(palette::DANGER));
                }
                if let Some(status) = &session.organization_status {
                    ui.separator();
                    ui.label(strings.organizer_status(
                        &status.state,
                        status.completed_operations,
                        status.total_operations,
                    ));
                    if matches!(status.state.as_str(), "FAILED" | "RECOVERY_REQUIRED")
                        && let Some(message) = &status.message
                    {
                        ui.label(RichText::new(message).color(palette::DANGER));
                    }
                    if matches!(status.state.as_str(), "RUNNING" | "ROLLING_BACK") {
                        ui.add(egui::ProgressBar::new(
                            status.completed_operations as f32
                                / status.total_operations.max(1) as f32,
                        ));
                        if ui.button(strings.organizer_cancel()).clicked() {
                            action = Some(LibraryAction::CancelOrganization);
                        }
                    } else if status.can_undo
                        && let Some(completed_batch_id) = &status.last_completed_batch_id
                        && ui.button(strings.undo_last_series()).clicked()
                    {
                        action = Some(LibraryAction::UndoOrganization {
                            completed_batch_id: completed_batch_id.clone(),
                        });
                    }
                }
                let Some(plan) = session.organization_plan.as_ref() else {
                    return;
                };
                ui.separator();
                egui::ScrollArea::vertical()
                    .id_salt("organizer-series-list")
                    .max_height(180.0)
                    .show(ui, |ui| {
                        for batch in &plan.batches {
                            let label = strings.organizer_batch_summary(
                                &batch.series_title,
                                batch.video_count,
                                batch.already_organized,
                            );
                            if ui
                                .selectable_label(
                                    self.organizer_batch_id.as_deref()
                                        == Some(batch.batch_id.as_str()),
                                    label,
                                )
                                .clicked()
                            {
                                self.organizer_batch_id = Some(batch.batch_id.clone());
                                self.organizer_series_title = batch.series_title.clone();
                                self.organizer_season = batch
                                    .season_number
                                    .map(|season| season.to_string())
                                    .unwrap_or_default();
                                self.organizer_nearby = batch
                                    .nearby_files
                                    .iter()
                                    .filter(|file| file.selected)
                                    .map(|file| file.relative_path.clone())
                                    .collect();
                            }
                        }
                    });
                let Some(batch) = self
                    .organizer_batch_id
                    .as_deref()
                    .and_then(|id| plan.batches.iter().find(|batch| batch.batch_id == id))
                else {
                    return;
                };
                ui.separator();
                ui.label(strings.organizer_reason(&batch.confidence));
                ui.horizontal(|ui| {
                    ui.label(strings.series_title_label());
                    ui.add(
                        TextEdit::singleline(&mut self.organizer_series_title).desired_width(360.0),
                    );
                    ui.label(strings.season_number_label());
                    ui.add(TextEdit::singleline(&mut self.organizer_season).desired_width(60.0));
                });
                if !batch.nearby_files.is_empty() {
                    ui.label(strings.nearby_files_label());
                    egui::ScrollArea::vertical()
                        .id_salt("organizer-nearby")
                        .max_height(110.0)
                        .show(ui, |ui| {
                            for file in &batch.nearby_files {
                                let mut selected =
                                    self.organizer_nearby.contains(&file.relative_path);
                                if ui.checkbox(&mut selected, &file.relative_path).changed() {
                                    if selected {
                                        self.organizer_nearby.insert(file.relative_path.clone());
                                    } else {
                                        self.organizer_nearby.remove(&file.relative_path);
                                    }
                                }
                            }
                        });
                }
                if ui.button(strings.update_preview()).clicked()
                    && let Ok(season_number) = self.organizer_season.trim().parse::<u32>()
                {
                    action = Some(LibraryAction::PreviewOrganization(
                        OrganizationPreviewRequest {
                            root: self.organizer_root.clone(),
                            base_relative_path: self.organizer_base.clone(),
                            overrides: vec![OrganizationSeriesOverride {
                                batch_id: batch.batch_id.clone(),
                                series_title: self.organizer_series_title.clone(),
                                season_number,
                                included_nearby_paths: self
                                    .organizer_nearby
                                    .iter()
                                    .cloned()
                                    .collect(),
                            }],
                        },
                    ));
                }
                if !batch.conflicts.is_empty() {
                    ui.label(RichText::new(strings.organizer_conflicts()).color(palette::DANGER));
                    for conflict in &batch.conflicts {
                        ui.label(
                            RichText::new(
                                conflict
                                    .strip_prefix("Destination already exists: ")
                                    .unwrap_or(conflict),
                            )
                            .color(palette::DANGER),
                        );
                    }
                }
                ui.label(strings.approved_moves(batch.moves.len()));
                egui::ScrollArea::vertical()
                    .id_salt("organizer-exact-moves")
                    .max_height(180.0)
                    .show(ui, |ui| {
                        for operation in &batch.moves {
                            ui.label(format!(
                                "{}  →  {}",
                                operation.source_relative_path, operation.destination_relative_path
                            ));
                        }
                    });
                let preview_nearby = batch
                    .nearby_files
                    .iter()
                    .filter(|file| file.selected)
                    .map(|file| file.relative_path.clone())
                    .collect::<BTreeSet<_>>();
                let review_matches_preview = self.organizer_series_title.trim()
                    == batch.series_title
                    && self.organizer_season.trim().parse::<u32>().ok() == batch.season_number
                    && self.organizer_nearby == preview_nearby;
                if ui
                    .add_enabled(
                        batch.executable
                            && review_matches_preview
                            && !session.organization_loading
                            && !organizer_busy,
                        egui::Button::new(strings.approve_series()),
                    )
                    .clicked()
                {
                    action = Some(LibraryAction::ExecuteOrganization {
                        plan_id: plan.plan_id.clone(),
                        batch: batch.clone(),
                    });
                }
            });
        self.organizer_open = open;
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
                    if let Some(series_id) = series_grid(
                        ui,
                        &owned,
                        posters,
                        strings,
                        LibraryGridDensity::Comfortable,
                        session.attention.as_ref(),
                    ) {
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
                    let row = episode_row(
                        ui,
                        item,
                        None,
                        session
                            .attention
                            .as_ref()
                            .and_then(|status| status.item(&item.id)),
                        strings,
                    );
                    if row.play_clicked {
                        action = Some(LibraryAction::Play {
                            media_id: item.id.clone(),
                        });
                    } else if row.change_match_clicked {
                        action = Some(LibraryAction::ChangeMatch {
                            media_id: item.id.clone(),
                        });
                    }
                }
                ui.add_space(24.0);
            });
        action
    }

    #[allow(clippy::too_many_arguments)]
    fn show_series(
        &mut self,
        ui: &mut egui::Ui,
        series_id: &str,
        library_root_name: &str,
        progresses: &[PlaybackProgress],
        posters: &mut PosterCache,
        pending_preloads: &std::collections::HashSet<String>,
        bangumi: &std::collections::HashMap<u64, BangumiDetailState>,
        attention: Option<&LibraryAttentionDocument>,
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
        let missing_repairs = series_attention_repairs(&series, attention, true);
        let refresh_repairs = series_attention_repairs(&series, attention, false);
        let attention_count = series_attention_count(&series, attention);
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

        let items: Vec<&MediaItem> = series.items().collect();
        let total_size = items.iter().map(|item| item.size_bytes.max(0)).sum::<i64>();
        let subtitle_count = items.iter().map(|item| item.subtitles.len()).sum::<usize>();
        let release_year = items
            .iter()
            .find_map(|item| item.anime_metadata.as_ref()?.start_year);
        let watched_count = items
            .iter()
            .filter(|item| {
                latest
                    .get(item.id.as_str())
                    .is_some_and(|progress| progress_is_completed(progress))
            })
            .count();
        let metadata = items
            .iter()
            .find_map(|item| item.anime_metadata.as_ref())
            .cloned();
        let dandanplay_anime_id = metadata.as_ref().and_then(|metadata| {
            (metadata.anime_id.provider == "DANDANPLAY")
                .then(|| u64::try_from(metadata.anime_id.value).ok())
                .flatten()
        });
        let detail_state = dandanplay_anime_id.and_then(|anime_id| bangumi.get(&anime_id));
        if let Some(anime_id) = dandanplay_anime_id
            && detail_state.is_none()
        {
            // Kick off the profile fetch the first time this page renders;
            // the app marks it Loading so this fires only once.
            action = Some(LibraryAction::FetchBangumiDetail { anime_id });
        }
        let library_location = items
            .iter()
            .find_map(|item| item.root_label.as_deref())
            .unwrap_or(library_root_name)
            .to_owned();

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
                    if !missing_repairs.is_empty()
                        || (attention.is_none() && !unmatched_ids.is_empty())
                    {
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
                            action = Some(if missing_repairs.is_empty() {
                                LibraryAction::PreloadDanmaku {
                                    media_ids: unmatched_ids.clone(),
                                }
                            } else {
                                LibraryAction::RepairAttention {
                                    requests: missing_repairs.clone(),
                                }
                            });
                        }
                    }
                    if !refresh_repairs.is_empty() && !matching_in_progress {
                        ui.add_space(8.0);
                        if icon_chip_button(ui, Icon::Refresh, strings.refresh_danmaku()).clicked()
                        {
                            action = Some(LibraryAction::RepairAttention {
                                requests: refresh_repairs.clone(),
                            });
                        }
                    }
                });
                if let Some(attention) = attention {
                    ui.add_space(8.0);
                    ui.horizontal(|ui| {
                        ui.add_space(PAGE_GUTTER);
                        let text = if !attention.provider.available {
                            strings.danmaku_provider_unavailable().to_owned()
                        } else if attention_count > 0 {
                            strings.series_needs_attention(attention_count)
                        } else {
                            strings.danmaku_ready().to_owned()
                        };
                        let color = if attention_count > 0 {
                            palette::ACCENT_OUTLINE
                        } else {
                            palette::TEXT_MUTED
                        };
                        ui.label(RichText::new(text).font(typography::caption()).color(color));
                    });
                }
                ui.add_space(12.0);
                ui.horizontal(|ui| {
                    ui.add_space(PAGE_GUTTER);
                    Frame::NONE
                        .fill(palette::SURFACE_RAISED)
                        .corner_radius(egui::CornerRadius::same(16))
                        .inner_margin(egui::Margin::symmetric(20, 20))
                        .stroke(egui::Stroke::new(1.0, Color32::from_white_alpha(18)))
                        .show(ui, |ui| {
                            ui.set_width((ui.available_width() - PAGE_GUTTER).max(420.0));
                            ui.horizontal(|ui| {
                                if let Some(item) = items.first() {
                                    poster_thumbnail(ui, item, posters, vec2(132.0, 198.0));
                                }
                                ui.add_space(16.0);
                                ui.vertical(|ui| {
                                    ui.label(
                                        RichText::new(&series.title)
                                            .font(typography::hero())
                                            .strong()
                                            .color(palette::TEXT_PRIMARY),
                                    );
                                    if let Some(metadata) = &metadata {
                                        // Alternate titles, like the official
                                        // detail page's secondary title line.
                                        let mut titles: Vec<&str> = Vec::new();
                                        for candidate in [
                                            Some(metadata.display_title.as_str()),
                                            metadata.japanese_title.as_deref(),
                                            metadata.english_title.as_deref(),
                                            metadata.chinese_title.as_deref(),
                                        ]
                                        .into_iter()
                                        .flatten()
                                        {
                                            if candidate != series.title
                                                && !titles.contains(&candidate)
                                            {
                                                titles.push(candidate);
                                            }
                                        }
                                        if !titles.is_empty() {
                                            ui.label(
                                                RichText::new(titles.join("  ·  "))
                                                    .font(typography::body())
                                                    .color(palette::TEXT_MUTED),
                                            );
                                        }
                                    }
                                    if let Some(BangumiDetailState::Ready(detail)) = detail_state {
                                        ui.add_space(6.0);
                                        ui.horizontal_wrapped(|ui| {
                                            if let Some(rating) = detail.rating {
                                                rating_chip(ui, rating);
                                            }
                                            if let Some(kind) = detail.type_description.as_deref() {
                                                info_chip(ui, kind, palette::TEXT_SECONDARY);
                                            }
                                            if let Some(is_on_air) = detail.is_on_air {
                                                info_chip(
                                                    ui,
                                                    if is_on_air {
                                                        strings.on_air()
                                                    } else {
                                                        strings.finished_airing()
                                                    },
                                                    if is_on_air {
                                                        palette::ACCENT_OUTLINE
                                                    } else {
                                                        palette::TEXT_MUTED
                                                    },
                                                );
                                            }
                                        });
                                    }
                                    ui.add_space(10.0);
                                    ui.label(
                                        RichText::new(strings.library_overview())
                                            .font(typography::heading())
                                            .strong()
                                            .color(palette::TEXT_SECONDARY),
                                    );
                                    ui.add_space(6.0);
                                    ui.horizontal_wrapped(|ui| {
                                        series_fact(
                                            ui,
                                            strings.episodes(),
                                            &series.episode_count().to_string(),
                                        );
                                        series_fact(
                                            ui,
                                            strings.watched(),
                                            &format!("{watched_count}/{}", series.episode_count()),
                                        );
                                        if let Some(year) = release_year {
                                            series_fact(
                                                ui,
                                                strings.season_view(),
                                                &year.to_string(),
                                            );
                                        }
                                        series_fact(
                                            ui,
                                            strings.total_size(),
                                            &format_size(total_size),
                                        );
                                        series_fact(
                                            ui,
                                            strings.subtitles(),
                                            &subtitle_count.to_string(),
                                        );
                                    });
                                    ui.add_space(12.0);
                                    ui.label(
                                        RichText::new(format!(
                                            "{}  ·  {}",
                                            strings.folders(),
                                            library_location
                                        ))
                                        .font(typography::caption())
                                        .color(palette::TEXT_MUTED),
                                    );
                                });
                            });
                        });
                });
                self.show_series_detail_sections(ui, detail_state, metadata.as_ref(), strings);
                ui.add_space(20.0);
                for season in &series.seasons {
                    if series.seasons.len() > 1 {
                        section_heading(ui, &season.label);
                    }
                    for item in &season.items {
                        let progress = latest.get(item.id.as_str()).copied();
                        let row = episode_row(
                            ui,
                            item,
                            progress,
                            attention.and_then(|status| status.item(&item.id)),
                            strings,
                        );
                        if row.play_clicked {
                            action = Some(LibraryAction::Play {
                                media_id: item.id.clone(),
                            });
                        } else if row.change_match_clicked {
                            action = Some(LibraryAction::ChangeMatch {
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

    /// Synopsis, tags, and online-database links under the series overview
    /// card, mirroring the official client's 簡介/標籤/線上資料庫 sections.
    fn show_series_detail_sections(
        &mut self,
        ui: &mut egui::Ui,
        detail_state: Option<&BangumiDetailState>,
        metadata: Option<&crate::library::AnimeMetadata>,
        strings: Strings,
    ) {
        match detail_state {
            Some(BangumiDetailState::Loading) => {
                ui.add_space(10.0);
                ui.horizontal(|ui| {
                    ui.add_space(PAGE_GUTTER);
                    ui.spinner();
                    ui.label(
                        RichText::new(strings.loading_details())
                            .font(typography::caption())
                            .color(palette::TEXT_MUTED),
                    );
                });
            }
            Some(BangumiDetailState::Failed(_)) => {
                // The library page stays useful without the online profile;
                // a quiet note beats an error banner here.
                ui.add_space(10.0);
                muted_line(ui, strings.details_unavailable());
            }
            Some(BangumiDetailState::Ready(detail)) => {
                if let Some(summary) = detail.summary.as_deref() {
                    ui.add_space(14.0);
                    ui.horizontal(|ui| {
                        ui.add_space(PAGE_GUTTER);
                        Frame::NONE
                            .fill(palette::SURFACE)
                            .corner_radius(egui::CornerRadius::same(12))
                            .inner_margin(egui::Margin::symmetric(16, 14))
                            .show(ui, |ui| {
                                ui.set_width((ui.available_width() - PAGE_GUTTER).max(420.0));
                                ui.label(
                                    RichText::new(strings.synopsis())
                                        .font(typography::heading())
                                        .strong()
                                        .color(palette::TEXT_SECONDARY),
                                );
                                ui.add_space(6.0);
                                ui.label(
                                    RichText::new(summary)
                                        .font(typography::body())
                                        .color(palette::TEXT_PRIMARY),
                                );
                            });
                    });
                }
                if !detail.tags.is_empty() {
                    ui.add_space(12.0);
                    ui.horizontal(|ui| {
                        ui.add_space(PAGE_GUTTER);
                        ui.vertical(|ui| {
                            ui.label(
                                RichText::new(strings.tags_label())
                                    .font(typography::heading())
                                    .strong()
                                    .color(palette::TEXT_SECONDARY),
                            );
                            ui.add_space(6.0);
                            ui.horizontal_wrapped(|ui| {
                                ui.set_width(ui.available_width() - PAGE_GUTTER);
                                for tag in &detail.tags {
                                    info_chip(ui, tag, palette::TEXT_SECONDARY);
                                }
                            });
                        });
                    });
                }
                self.show_database_links(ui, Some(detail), metadata, strings);
            }
            None => {
                self.show_database_links(ui, None, metadata, strings);
            }
        }
    }

    /// Buttons that open the anime in the public databases: the bangumi
    /// profile's own list when loaded, otherwise the identities the server
    /// already recognized.
    fn show_database_links(
        &mut self,
        ui: &mut egui::Ui,
        detail: Option<&BangumiDetail>,
        metadata: Option<&crate::library::AnimeMetadata>,
        strings: Strings,
    ) {
        let mut links: Vec<(String, String)> = Vec::new();
        if let Some(detail) = detail {
            for database in &detail.online_databases {
                links.push((database.name.clone(), database.url.clone()));
            }
        }
        if let Some(metadata) = metadata {
            let mut push_link = |name: &str, url: Option<String>| {
                if let Some(url) = url
                    && !links.iter().any(|(_, existing)| existing == &url)
                {
                    links.push((name.to_owned(), url));
                }
            };
            for link in &metadata.external_links {
                push_link(link.anime_id.provider_name(), link.web_url());
            }
            push_link(
                metadata.anime_id.provider_name(),
                metadata.anime_id.web_url(),
            );
        }
        if links.is_empty() {
            return;
        }
        ui.add_space(12.0);
        ui.horizontal(|ui| {
            ui.add_space(PAGE_GUTTER);
            ui.vertical(|ui| {
                ui.label(
                    RichText::new(strings.online_databases())
                        .font(typography::heading())
                        .strong()
                        .color(palette::TEXT_SECONDARY),
                );
                ui.add_space(6.0);
                ui.horizontal_wrapped(|ui| {
                    ui.set_width(ui.available_width() - PAGE_GUTTER);
                    for (name, url) in links {
                        if ui
                            .add(egui::Button::new(
                                RichText::new(name).font(typography::caption()),
                            ))
                            .clicked()
                        {
                            ui.ctx().open_url(egui::OpenUrl::new_tab(url));
                        }
                    }
                });
            });
        });
    }
}

fn sidebar_heading(ui: &mut egui::Ui, label: &str) {
    ui.add_space(16.0);
    ui.horizontal(|ui| {
        ui.add_space(20.0);
        ui.label(
            RichText::new(label.to_uppercase())
                .font(typography::small())
                .strong()
                .color(palette::TEXT_MUTED),
        );
    });
    ui.add_space(6.0);
}

/// Themed dropdown chip for the filter toolbar: a rounded pill showing an
/// optional muted category label plus the active value, opening a
/// card-styled option menu. Returns the index of a newly picked option.
/// Replaces the stock egui `ComboBox`, whose plain rectangle reads as a
/// debug control inside the library's card system.
fn filter_dropdown(
    ui: &mut egui::Ui,
    id_salt: &str,
    label: &str,
    value: &str,
    options: &[(String, bool)],
    active: bool,
) -> Option<usize> {
    const HEIGHT: f32 = 32.0;
    const PAD: f32 = 12.0;
    const GAP: f32 = 7.0;
    const CARET: f32 = 16.0;
    let label_galley = (!label.is_empty()).then(|| {
        ui.painter()
            .layout_no_wrap(label.to_owned(), typography::small(), palette::TEXT_MUTED)
    });
    let value_color = if active {
        palette::ACCENT_OUTLINE
    } else {
        palette::TEXT_PRIMARY
    };
    let value_galley =
        ui.painter()
            .layout_no_wrap(value.to_owned(), typography::caption(), value_color);
    let label_width = label_galley
        .as_ref()
        .map(|galley| galley.size().x + GAP)
        .unwrap_or(0.0);
    let width = (PAD + label_width + value_galley.size().x + GAP + CARET + PAD).min(320.0);
    let (rect, response) = ui.allocate_exact_size(vec2(width, HEIGHT), Sense::click());
    if ui.is_rect_visible(rect) {
        let fill = if response.hovered() {
            palette::WIDGET_HOVER
        } else {
            palette::SURFACE_FAINT
        };
        ui.painter().rect_filled(rect, 9.0, fill);
        if active {
            ui.painter().rect_stroke(
                rect,
                9.0,
                egui::Stroke::new(1.0, palette::ACCENT),
                egui::StrokeKind::Inside,
            );
        }
        let mut cursor = rect.left() + PAD;
        if let Some(galley) = label_galley {
            let position = pos2(cursor, rect.center().y - galley.size().y / 2.0);
            cursor += galley.size().x + GAP;
            ui.painter().galley(position, galley, palette::TEXT_MUTED);
        }
        // Long values (folder paths) clip in front of the caret.
        let value_clip = ui.painter().with_clip_rect(Rect::from_min_max(
            rect.min,
            pos2(rect.right() - PAD - CARET, rect.max.y),
        ));
        value_clip.galley(
            pos2(cursor, rect.center().y - value_galley.size().y / 2.0),
            value_galley,
            value_color,
        );
        let caret_center = pos2(
            rect.right() - PAD - CARET / 2.0 + 3.0,
            rect.center().y + 1.0,
        );
        let caret_stroke = egui::Stroke::new(1.6, palette::TEXT_MUTED);
        ui.painter().line_segment(
            [
                caret_center + vec2(-4.0, -2.0),
                caret_center + vec2(0.0, 2.5),
            ],
            caret_stroke,
        );
        ui.painter().line_segment(
            [
                caret_center + vec2(4.0, -2.0),
                caret_center + vec2(0.0, 2.5),
            ],
            caret_stroke,
        );
    }
    if response.hovered() {
        ui.ctx().set_cursor_icon(CursorIcon::PointingHand);
    }

    let mut picked = None;
    egui::Popup::menu(&response)
        .id(egui::Id::new(("library-filter-dropdown", id_salt)))
        .gap(6.0)
        .width(rect.width().max(200.0))
        // Explicit card frame: the option list must stay on the dark
        // raised surface regardless of what egui derives for menus.
        .frame(
            Frame::NONE
                .fill(palette::SURFACE_RAISED)
                .corner_radius(egui::CornerRadius::same(12))
                .inner_margin(egui::Margin::symmetric(8, 8))
                .stroke(egui::Stroke::new(1.0, Color32::from_white_alpha(24)))
                .shadow(egui::Shadow {
                    offset: [0, 6],
                    blur: 18,
                    spread: 0,
                    color: Color32::from_black_alpha(140),
                }),
        )
        .show(|ui| {
            ui.spacing_mut().item_spacing.y = 2.0;
            for (index, (text, selected)) in options.iter().enumerate() {
                let (row, row_response) = ui.allocate_exact_size(
                    vec2(ui.available_width().max(176.0), 30.0),
                    Sense::click(),
                );
                let row_fill = if row_response.hovered() {
                    palette::WIDGET_HOVER
                } else if *selected {
                    palette::SURFACE_FAINT
                } else {
                    Color32::TRANSPARENT
                };
                ui.painter().rect_filled(row, 7.0, row_fill);
                if *selected {
                    paint_icon(
                        ui.painter(),
                        Rect::from_center_size(
                            pos2(row.left() + 15.0, row.center().y),
                            vec2(12.0, 12.0),
                        ),
                        Icon::Check,
                        palette::ACCENT_OUTLINE,
                        1.8,
                    );
                }
                ui.painter().text(
                    pos2(row.left() + 30.0, row.center().y),
                    Align2::LEFT_CENTER,
                    text,
                    typography::caption(),
                    if *selected {
                        palette::TEXT_PRIMARY
                    } else {
                        palette::TEXT_SECONDARY
                    },
                );
                if row_response.hovered() {
                    ui.ctx().set_cursor_icon(CursorIcon::PointingHand);
                }
                if row_response.clicked() {
                    picked = Some(index);
                }
            }
        });
    picked
}

/// On/off pill for the filter toolbar (the grouped-display toggle).
fn filter_toggle_chip(ui: &mut egui::Ui, label: &str, on: &mut bool) -> bool {
    const HEIGHT: f32 = 32.0;
    let galley = ui.painter().layout_no_wrap(
        label.to_owned(),
        typography::caption(),
        palette::TEXT_PRIMARY,
    );
    let width = galley.size().x + 46.0;
    let (rect, response) = ui.allocate_exact_size(vec2(width, HEIGHT), Sense::click());
    if response.clicked() {
        *on = !*on;
    }
    if ui.is_rect_visible(rect) {
        let fill = if *on {
            palette::WIDGET_ACTIVE
        } else if response.hovered() {
            palette::WIDGET_HOVER
        } else {
            palette::SURFACE_FAINT
        };
        ui.painter().rect_filled(rect, 9.0, fill);
        if *on {
            ui.painter().rect_stroke(
                rect,
                9.0,
                egui::Stroke::new(1.0, palette::ACCENT),
                egui::StrokeKind::Inside,
            );
        }
        paint_icon(
            ui.painter(),
            Rect::from_center_size(pos2(rect.left() + 16.0, rect.center().y), vec2(12.0, 12.0)),
            Icon::Check,
            if *on {
                palette::ACCENT_OUTLINE
            } else {
                palette::TEXT_MUTED
            },
            1.8,
        );
        ui.painter().galley(
            pos2(rect.left() + 30.0, rect.center().y - galley.size().y / 2.0),
            galley,
            if *on {
                palette::TEXT_PRIMARY
            } else {
                palette::TEXT_MUTED
            },
        );
    }
    if response.hovered() {
        ui.ctx().set_cursor_icon(CursorIcon::PointingHand);
    }
    response.clicked()
}

/// Plain action chip matching the dropdown chips (e.g. "Clear filters").
fn toolbar_chip_button(ui: &mut egui::Ui, label: &str) -> egui::Response {
    const HEIGHT: f32 = 32.0;
    let galley = ui.painter().layout_no_wrap(
        label.to_owned(),
        typography::caption(),
        palette::TEXT_SECONDARY,
    );
    let width = galley.size().x + 28.0;
    let (rect, response) = ui.allocate_exact_size(vec2(width, HEIGHT), Sense::click());
    if ui.is_rect_visible(rect) {
        let fill = if response.hovered() {
            palette::WIDGET_HOVER
        } else {
            Color32::TRANSPARENT
        };
        ui.painter().rect_filled(rect, 9.0, fill);
        ui.painter().rect_stroke(
            rect,
            9.0,
            egui::Stroke::new(1.0, Color32::from_white_alpha(28)),
            egui::StrokeKind::Inside,
        );
        ui.painter().galley(
            pos2(rect.left() + 14.0, rect.center().y - galley.size().y / 2.0),
            galley,
            palette::TEXT_SECONDARY,
        );
    }
    if response.hovered() {
        ui.ctx().set_cursor_icon(CursorIcon::PointingHand);
    }
    response
}

fn nav_button(ui: &mut egui::Ui, icon: Icon, label: &str, selected: bool) -> egui::Response {
    let width = (ui.available_width() - 20.0).max(120.0);
    let (full_rect, response) =
        ui.allocate_exact_size(vec2(ui.available_width().max(width), 42.0), Sense::click());
    let rect = Rect::from_min_size(
        pos2(full_rect.left() + 10.0, full_rect.top()),
        vec2(width, 42.0),
    );
    let fill = if selected {
        Color32::from_rgb(22, 42, 67)
    } else if response.hovered() || response.has_focus() {
        palette::SURFACE_RAISED
    } else {
        Color32::TRANSPARENT
    };
    ui.painter().rect_filled(rect, 9.0, fill);
    if selected {
        ui.painter().rect_filled(
            Rect::from_min_size(rect.left_top() + vec2(0.0, 7.0), vec2(3.0, 28.0)),
            1.5,
            palette::ACCENT_BRIGHT,
        );
    }
    paint_focus_outline(ui, rect, 10.0, &response);
    let color = if selected {
        palette::ACCENT_OUTLINE
    } else {
        palette::TEXT_MUTED
    };
    paint_icon(
        ui.painter(),
        Rect::from_center_size(pos2(rect.left() + 24.0, rect.center().y), vec2(20.0, 20.0)),
        icon,
        color,
        1.6,
    );
    ui.painter().text(
        pos2(rect.left() + 46.0, rect.center().y),
        Align2::LEFT_CENTER,
        label,
        typography::body(),
        if selected {
            palette::TEXT_PRIMARY
        } else {
            palette::TEXT_SECONDARY
        },
    );
    if response.hovered() {
        ui.ctx().set_cursor_icon(CursorIcon::PointingHand);
    }
    response
}

fn folder_nav_button(
    ui: &mut egui::Ui,
    folder: &str,
    item_count: usize,
    selected: bool,
) -> egui::Response {
    let width = (ui.available_width() - 20.0).max(120.0);
    let (full_rect, response) =
        ui.allocate_exact_size(vec2(ui.available_width().max(width), 36.0), Sense::click());
    let rect = Rect::from_min_size(
        pos2(full_rect.left() + 10.0, full_rect.top()),
        vec2(width, 36.0),
    );
    let fill = if selected {
        palette::WIDGET_ACTIVE
    } else if response.hovered() {
        palette::SURFACE_RAISED
    } else {
        Color32::TRANSPARENT
    };
    ui.painter().rect_filled(rect, 8.0, fill);
    paint_icon(
        ui.painter(),
        Rect::from_center_size(pos2(rect.left() + 23.0, rect.center().y), vec2(18.0, 18.0)),
        Icon::Folder,
        if selected {
            palette::ACCENT_OUTLINE
        } else {
            palette::TEXT_MUTED
        },
        1.4,
    );
    let count_text = item_count.to_string();
    let count_width = ui
        .painter()
        .layout_no_wrap(count_text.clone(), typography::small(), palette::TEXT_MUTED)
        .size()
        .x;
    ui.painter().text(
        pos2(rect.right() - 10.0, rect.center().y),
        Align2::RIGHT_CENTER,
        count_text,
        typography::small(),
        palette::TEXT_MUTED,
    );
    let label_clip = Rect::from_min_max(
        pos2(rect.left() + 44.0, rect.top()),
        pos2(rect.right() - count_width - 18.0, rect.bottom()),
    );
    ui.painter().with_clip_rect(label_clip).text(
        pos2(rect.left() + 44.0, rect.center().y),
        Align2::LEFT_CENTER,
        folder,
        typography::caption(),
        palette::TEXT_SECONDARY,
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
    qa_primary_state: Option<&str>,
) -> egui::Response {
    let (rect, response) = ui.allocate_exact_size(size, Sense::click());
    let force_hover = qa_primary_state == Some("hover");
    let force_focus = qa_primary_state == Some("focus");
    if force_focus {
        response.request_focus();
    }
    let focused = force_focus || response.has_focus();
    let highlighted = force_hover || focused || response.hovered();
    let fill = if prominent {
        if highlighted {
            palette::ACCENT_OUTLINE
        } else {
            palette::ACCENT_BRIGHT
        }
    } else if highlighted {
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
    if focused {
        ui.painter().rect_stroke(
            rect.shrink(1.0),
            8.0,
            egui::Stroke::new(2.0, palette::TEXT_PRIMARY),
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
    let fill = if response.hovered() || response.has_focus() {
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
    paint_focus_outline(ui, rect, 9.0, &response);
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

fn section_subheading(ui: &mut egui::Ui, text: &str) {
    ui.horizontal(|ui| {
        ui.add_space(PAGE_GUTTER);
        Frame::NONE
            .fill(palette::SURFACE)
            .corner_radius(egui::CornerRadius::same(8))
            .inner_margin(egui::Margin::symmetric(11, 7))
            .show(ui, |ui| {
                ui.label(
                    RichText::new(text)
                        .font(typography::heading())
                        .strong()
                        .color(palette::TEXT_SECONDARY),
                );
            });
    });
    ui.add_space(10.0);
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

fn series_fact(ui: &mut egui::Ui, label: &str, value: &str) {
    Frame::NONE
        .fill(palette::SURFACE_FAINT)
        .corner_radius(egui::CornerRadius::same(8))
        .inner_margin(egui::Margin::symmetric(10, 7))
        .show(ui, |ui| {
            ui.horizontal(|ui| {
                ui.label(
                    RichText::new(label)
                        .font(typography::small())
                        .color(palette::TEXT_MUTED),
                );
                ui.label(
                    RichText::new(value)
                        .font(typography::caption())
                        .strong()
                        .color(palette::TEXT_PRIMARY),
                );
            });
        });
}

/// Star + score chip like the official detail page's 綜合評分.
fn rating_chip(ui: &mut egui::Ui, rating: f64) {
    Frame::NONE
        .fill(palette::SURFACE_FAINT)
        .corner_radius(egui::CornerRadius::same(8))
        .inner_margin(egui::Margin::symmetric(10, 6))
        .show(ui, |ui| {
            ui.horizontal(|ui| {
                ui.label(
                    RichText::new("★")
                        .font(typography::caption())
                        .color(Color32::from_rgb(255, 196, 87)),
                );
                ui.label(
                    RichText::new(format!("{rating:.1}"))
                        .font(typography::caption())
                        .strong()
                        .color(palette::TEXT_PRIMARY),
                );
            });
        });
}

/// Small rounded text chip (anime type, airing state, tag).
fn info_chip(ui: &mut egui::Ui, text: &str, color: Color32) {
    Frame::NONE
        .fill(palette::SURFACE_FAINT)
        .corner_radius(egui::CornerRadius::same(8))
        .inner_margin(egui::Margin::symmetric(10, 6))
        .show(ui, |ui| {
            ui.label(RichText::new(text).font(typography::caption()).color(color));
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

fn series_matches_query(series: &Series, query: &str) -> bool {
    let query = query.trim().to_lowercase();
    query.is_empty()
        || series.title.to_lowercase().contains(&query)
        || series.items().any(|item| {
            item.episode_title.to_lowercase().contains(&query)
                || item.relative_path.to_lowercase().contains(&query)
                || item
                    .anime_metadata
                    .as_ref()
                    .is_some_and(|metadata| metadata.display_title.to_lowercase().contains(&query))
        })
}

fn series_is_matched(series: &Series) -> bool {
    series.items().any(|item| item.anime_metadata.is_some())
}

/// Whether one episode's saved progress means "watched to the end",
/// matching the resume policy's remaining-time threshold.
fn progress_is_completed(progress: &PlaybackProgress) -> bool {
    progress.duration_ms.is_some_and(|duration| {
        duration > 0 && duration - progress.position_ms < MINIMUM_REMAINING_MS
    })
}

fn series_progress_state(
    series: &Series,
    progresses: &[PlaybackProgress],
) -> LibraryProgressFilter {
    let latest = progresses
        .iter()
        .filter(|progress| series.items().any(|item| item.id == progress.media_id))
        .fold(
            std::collections::HashMap::<&str, &PlaybackProgress>::new(),
            |mut by_media_id, progress| {
                match by_media_id.get(progress.media_id.as_str()) {
                    Some(existing)
                        if existing.updated_at_epoch_ms >= progress.updated_at_epoch_ms => {}
                    _ => {
                        by_media_id.insert(progress.media_id.as_str(), progress);
                    }
                }
                by_media_id
            },
        );
    let mut any_started = false;
    let mut all_completed = series.episode_count() > 0;
    for item in series.items() {
        let progress = latest.get(item.id.as_str()).copied();
        let completed = progress.is_some_and(progress_is_completed);
        // A fully watched episode also means the series was started, so a
        // series with some episodes done and the rest untouched lands in
        // "in progress", not "unwatched".
        any_started |= completed
            || progress.is_some_and(|progress| progress.position_ms >= MINIMUM_RESUME_POSITION_MS);
        all_completed &= completed;
    }
    if all_completed {
        LibraryProgressFilter::Completed
    } else if any_started {
        LibraryProgressFilter::InProgress
    } else {
        LibraryProgressFilter::Unwatched
    }
}

#[allow(clippy::too_many_arguments)]
fn filtered_library_series(
    series: &[Series],
    query: &str,
    match_filter: LibraryMatchFilter,
    progress_filter: LibraryProgressFilter,
    selected_folder: Option<&str>,
    selected_year: Option<i32>,
    sort: LibrarySeriesSort,
    progresses: &[PlaybackProgress],
) -> Vec<Series> {
    let mut filtered: Vec<Series> = series
        .iter()
        .filter(|series| series_matches_query(series, query))
        .filter(|series| match match_filter {
            LibraryMatchFilter::All => true,
            LibraryMatchFilter::Matched => series_is_matched(series),
            LibraryMatchFilter::Unmatched => !series_is_matched(series),
        })
        .filter(|series| {
            selected_folder.is_none_or(|folder| {
                series
                    .items()
                    .any(|item| item_in_folder_shortcut(item, folder))
            })
        })
        .filter(|series| selected_year.is_none_or(|year| series_release_year(series) == Some(year)))
        .filter(|series| {
            progress_filter == LibraryProgressFilter::All
                || series_progress_state(series, progresses) == progress_filter
        })
        .cloned()
        .collect();

    filtered.sort_by(|left, right| match sort {
        LibrarySeriesSort::Title => left.title.to_lowercase().cmp(&right.title.to_lowercase()),
        LibrarySeriesSort::Newest => series_latest_indexed_at(right)
            .cmp(&series_latest_indexed_at(left))
            .then_with(|| left.title.to_lowercase().cmp(&right.title.to_lowercase())),
        LibrarySeriesSort::ReleaseYear => series_release_year(right)
            .cmp(&series_release_year(left))
            .then_with(|| left.title.to_lowercase().cmp(&right.title.to_lowercase())),
        LibrarySeriesSort::EpisodeCount => right
            .episode_count()
            .cmp(&left.episode_count())
            .then_with(|| left.title.to_lowercase().cmp(&right.title.to_lowercase())),
    });
    filtered
}

fn series_attention_count(series: &Series, attention: Option<&LibraryAttentionDocument>) -> usize {
    let Some(attention) = attention else { return 0 };
    series
        .items()
        .filter_map(|item| attention.item(&item.id))
        .filter(|item| item.needs_attention())
        .count()
}

fn series_attention_badge(
    series: &Series,
    attention: Option<&LibraryAttentionDocument>,
    strings: Strings,
) -> Option<String> {
    let attention = attention?;
    let affected = series_attention_count(series, Some(attention));
    if affected == 0 {
        return None;
    }
    let blocking = series
        .items()
        .filter_map(|item| attention.item(&item.id))
        .any(|item| {
            item.issue_codes.iter().any(|issue| {
                matches!(
                    issue,
                    AttentionIssueCode::UnmappedAnime
                        | AttentionIssueCode::ConflictingAnimeIds
                        | AttentionIssueCode::RefreshFailed
                )
            })
        });
    Some(if blocking {
        strings.issues_count(affected)
    } else {
        strings.cache_needed_count(affected)
    })
}

fn series_attention_repairs(
    series: &Series,
    attention: Option<&LibraryAttentionDocument>,
    unmapped_only: bool,
) -> Vec<AttentionRepairRequest> {
    let Some(attention) = attention.filter(|attention| attention.provider.available) else {
        return Vec::new();
    };
    series
        .items()
        .filter_map(|item| attention.item(&item.id))
        .filter(|item| item.needs_attention())
        .filter(|item| {
            if unmapped_only {
                item.mapping_status == AttentionMappingStatus::Unmapped
            } else {
                item.mapping_status == AttentionMappingStatus::Mapped
                    && item.episode_id.is_some()
                    && (item.cache_status != AttentionCacheStatus::Fresh
                        || item
                            .issue_codes
                            .contains(&AttentionIssueCode::RefreshFailed))
            }
        })
        .map(|item| AttentionRepairRequest {
            media_id: item.media_id.clone(),
            mapping_status: item.mapping_status,
            anime_id: item.anime_id,
            episode_id: item.episode_id,
        })
        .collect()
}

fn series_latest_indexed_at(series: &Series) -> i64 {
    series
        .items()
        .map(|item| item.indexed_at_epoch_ms)
        .max()
        .unwrap_or_default()
}

fn series_release_year(series: &Series) -> Option<i32> {
    series
        .items()
        .filter_map(|item| item.anime_metadata.as_ref()?.start_year)
        .next()
}

fn recent_series_groups(series: &[Series], strings: Strings) -> Vec<(String, Vec<Series>)> {
    let mut groups = BTreeMap::<Option<(i32, u32)>, Vec<Series>>::new();
    for entry in series {
        let key = year_month_from_epoch_ms(series_latest_indexed_at(entry));
        groups.entry(key).or_default().push(entry.clone());
    }
    groups
        .into_iter()
        .rev()
        .map(|(key, entries)| {
            let label = key
                .map(|(year, month)| recent_month_label(year, month, strings))
                .unwrap_or_else(|| strings.unknown_date().to_owned());
            (label, entries)
        })
        .collect()
}

/// When any episode of the series was last played, from the newest
/// progress row across its items.
fn series_latest_played_at(series: &Series, progresses: &[PlaybackProgress]) -> Option<i64> {
    progresses
        .iter()
        .filter(|progress| series.items().any(|item| item.id == progress.media_id))
        .map(|progress| progress.updated_at_epoch_ms)
        .max()
}

/// Groups an already recency-sorted list by the month each series was
/// last played, preserving the incoming order inside every group.
fn recently_played_groups(
    series: &[Series],
    progresses: &[PlaybackProgress],
    strings: Strings,
) -> Vec<(String, Vec<Series>)> {
    type MonthKey = Option<(i32, u32)>;
    let mut groups: Vec<(MonthKey, Vec<Series>)> = Vec::new();
    for entry in series {
        let key = series_latest_played_at(entry, progresses).and_then(year_month_from_epoch_ms);
        match groups.last_mut() {
            Some((last_key, entries)) if *last_key == key => entries.push(entry.clone()),
            _ => groups.push((key, vec![entry.clone()])),
        }
    }
    groups
        .into_iter()
        .map(|(key, entries)| {
            let label = key
                .map(|(year, month)| recent_month_label(year, month, strings))
                .unwrap_or_else(|| strings.unknown_date().to_owned());
            (label, entries)
        })
        .collect()
}

fn season_series_groups(series: &[Series], strings: Strings) -> Vec<(String, Vec<Series>)> {
    let mut groups = BTreeMap::<Option<i32>, Vec<Series>>::new();
    for entry in series {
        groups
            .entry(series_release_year(entry))
            .or_default()
            .push(entry.clone());
    }
    groups
        .into_iter()
        .rev()
        .map(|(year, entries)| {
            let label = year
                .map(|year| year.to_string())
                .unwrap_or_else(|| strings.unknown_season().to_owned());
            (label, entries)
        })
        .collect()
}

fn recent_month_label(year: i32, month: u32, strings: Strings) -> String {
    match strings.language() {
        Language::English => {
            const MONTHS: [&str; 12] = [
                "January",
                "February",
                "March",
                "April",
                "May",
                "June",
                "July",
                "August",
                "September",
                "October",
                "November",
                "December",
            ];
            format!(
                "{} {year}",
                MONTHS[(month.saturating_sub(1) as usize).min(11)]
            )
        }
        Language::TraditionalChinese => format!("{year}年{month}月"),
    }
}

/// Converts a Unix epoch millisecond timestamp to a Gregorian
/// year/month/day without pulling a date-time dependency into the
/// lightweight player.
fn civil_date_from_epoch_ms(epoch_ms: i64) -> Option<(i32, u32, u32)> {
    if epoch_ms <= 0 {
        return None;
    }
    let days = epoch_ms.div_euclid(86_400_000);
    let z = days + 719_468;
    let era = if z >= 0 { z } else { z - 146_096 }.div_euclid(146_097);
    let day_of_era = z - era * 146_097;
    let year_of_era =
        (day_of_era - day_of_era / 1_460 + day_of_era / 36_524 - day_of_era / 146_096) / 365;
    let year = year_of_era + era * 400;
    let day_of_year = day_of_era - (365 * year_of_era + year_of_era / 4 - year_of_era / 100);
    let month_prime = (5 * day_of_year + 2) / 153;
    let day = day_of_year - (153 * month_prime + 2) / 5 + 1;
    let month = month_prime + if month_prime < 10 { 3 } else { -9 };
    let year = year + i64::from(month <= 2);
    Some((
        i32::try_from(year).ok()?,
        u32::try_from(month).ok()?,
        u32::try_from(day).ok()?,
    ))
}

fn year_month_from_epoch_ms(epoch_ms: i64) -> Option<(i32, u32)> {
    civil_date_from_epoch_ms(epoch_ms).map(|(year, month, _)| (year, month))
}

/// `2026/07/13`-style date for compact row captions.
fn short_date_from_epoch_ms(epoch_ms: i64) -> Option<String> {
    civil_date_from_epoch_ms(epoch_ms)
        .map(|(year, month, day)| format!("{year}/{month:02}/{day:02}"))
}

fn series_grid(
    ui: &mut egui::Ui,
    series: &[Series],
    posters: &mut PosterCache,
    strings: Strings,
    density: LibraryGridDensity,
    attention: Option<&LibraryAttentionDocument>,
) -> Option<String> {
    let mut clicked = None;
    let card_size = density.card_size();
    let available_width = ui.available_width() - 2.0 * PAGE_GUTTER;
    let columns = ((available_width + CARD_GAP) / (card_size.x + CARD_GAP))
        .floor()
        .max(1.0) as usize;
    let rows = series.len().div_ceil(columns);
    let row_height = card_size.y + CARD_GAP;

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
                        let badge = series_attention_badge(series, attention, strings);
                        let response = poster_card_with_title_sized(
                            ui,
                            &item,
                            &series.title,
                            &format!("{} {}", series.episode_count(), strings.episodes()),
                            posters,
                            card_size,
                            badge.as_deref(),
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
    poster_card_impl(
        ui,
        item,
        &title,
        None,
        posters,
        vec2(CARD_WIDTH, CARD_HEIGHT),
        progress_fraction,
        badge,
    )
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
        vec2(CARD_WIDTH, CARD_HEIGHT),
        None,
        None,
    )
}

fn poster_card_with_title_sized(
    ui: &mut egui::Ui,
    representative: &MediaItem,
    title: &str,
    subtitle: &str,
    posters: &mut PosterCache,
    size: egui::Vec2,
    badge: Option<&str>,
) -> egui::Response {
    poster_card_impl(
        ui,
        representative,
        title,
        Some(subtitle),
        posters,
        size,
        None,
        badge,
    )
}

/// Full-bleed poster with the caption on a bottom scrim, like the mockups.
#[allow(clippy::too_many_arguments)]
fn poster_card_impl(
    ui: &mut egui::Ui,
    item: &MediaItem,
    title: &str,
    subtitle: Option<&str>,
    posters: &mut PosterCache,
    size: egui::Vec2,
    progress_fraction: Option<f32>,
    badge: Option<&str>,
) -> egui::Response {
    let radius = metrics::CARD_RADIUS + 2.0;
    let (rect, response) = ui.allocate_exact_size(size, Sense::click());
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

struct EpisodeRowAction {
    play_clicked: bool,
    change_match_clicked: bool,
}

/// Renders one episode row with two independent click targets: the row body
/// (play) and a small trailing icon button (open the manual match picker —
/// see `LibraryAction::ChangeMatch`). Interact regions are carved out of the
/// row explicitly (rather than nesting widgets) so clicking the icon can
/// never also register as clicking play.
fn episode_row(
    ui: &mut egui::Ui,
    item: &MediaItem,
    progress: Option<&PlaybackProgress>,
    attention: Option<&crate::library::LibraryAttentionItem>,
    strings: Strings,
) -> EpisodeRowAction {
    let width = ui.available_width() - 2.0 * metrics::GUTTER;
    let (rect, _) =
        ui.allocate_exact_size(vec2(width + 2.0 * metrics::GUTTER, 44.0), Sense::hover());
    if !ui.is_rect_visible(rect) {
        return EpisodeRowAction {
            play_clicked: false,
            change_match_clicked: false,
        };
    }
    let row_rect = Rect::from_min_size(rect.min + vec2(metrics::GUTTER, 2.0), vec2(width, 40.0));
    const MATCH_BUTTON_SIZE: f32 = 28.0;
    let match_rect = Rect::from_center_size(
        row_rect.right_center() - vec2(MATCH_BUTTON_SIZE / 2.0 + 6.0, 0.0),
        vec2(MATCH_BUTTON_SIZE, MATCH_BUTTON_SIZE),
    );
    let play_rect = Rect::from_min_max(row_rect.min, pos2(match_rect.left() - 4.0, row_rect.max.y));

    let match_id = ui.id().with(("episode-row-match", item.id.as_str()));
    let play_id = ui.id().with(("episode-row-play", item.id.as_str()));
    let match_response = ui
        .interact(match_rect, match_id, Sense::click())
        .on_hover_text(strings.change_match());
    let play_response = ui.interact(play_rect, play_id, Sense::click());

    let fill = if play_response.hovered() {
        palette::WIDGET_HOVER
    } else {
        palette::SURFACE
    };
    ui.painter().rect_filled(row_rect, 6.0, fill);
    let completed = progress.is_some_and(progress_is_completed);
    let mut title_left = row_rect.left() + 12.0;
    if completed {
        // Green check before the title, like the official episode list.
        paint_icon(
            ui.painter(),
            Rect::from_center_size(
                pos2(row_rect.left() + 18.0, row_rect.center().y),
                vec2(14.0, 14.0),
            ),
            Icon::Check,
            palette::SUCCESS,
            2.0,
        );
        title_left = row_rect.left() + 32.0;
    }
    ui.painter().text(
        pos2(title_left, row_rect.center().y),
        Align2::LEFT_CENTER,
        &item.episode_title,
        typography::body(),
        palette::TEXT_PRIMARY,
    );
    let mut status_parts: Vec<String> = Vec::new();
    if let Some(attention) = attention {
        if attention.mapping_status == AttentionMappingStatus::Unmapped {
            status_parts.push(strings.needs_match().to_owned());
        } else {
            match attention.cache_status {
                AttentionCacheStatus::Fresh => {}
                AttentionCacheStatus::Missing => {
                    status_parts.push(strings.danmaku_uncached().to_owned())
                }
                AttentionCacheStatus::Stale => {
                    status_parts.push(strings.danmaku_stale().to_owned())
                }
            }
        }
        if attention
            .issue_codes
            .contains(&AttentionIssueCode::RefreshFailed)
        {
            status_parts.push(strings.refresh_failed().to_owned());
        }
    }
    if item.size_bytes > 0 {
        status_parts.push(format_size(item.size_bytes));
    }
    if let Some(progress) = progress {
        match progress.duration_ms {
            Some(duration) if duration > 0 => {
                if completed {
                    status_parts.push(strings.watched().to_owned());
                } else {
                    let percent =
                        ((progress.position_ms as f64 / duration as f64) * 100.0).round() as i64;
                    status_parts.push(format!("{} {percent}%", strings.resume()));
                }
            }
            _ => status_parts.push(strings.started().to_owned()),
        }
        if let Some(date) = short_date_from_epoch_ms(progress.updated_at_epoch_ms) {
            status_parts.push(date);
        }
    }
    if !status_parts.is_empty() {
        ui.painter().text(
            pos2(match_rect.left() - 10.0, row_rect.center().y),
            Align2::RIGHT_CENTER,
            status_parts.join("  ·  "),
            typography::caption(),
            palette::TEXT_MUTED,
        );
    }
    let match_fill = if match_response.hovered() {
        palette::WIDGET_HOVER
    } else {
        Color32::TRANSPARENT
    };
    ui.painter().rect_filled(match_rect, 6.0, match_fill);
    paint_icon(
        ui.painter(),
        match_rect.shrink(6.0),
        Icon::Danmaku,
        if item.anime_metadata.is_some() {
            palette::TEXT_SECONDARY
        } else {
            palette::ACCENT_OUTLINE
        },
        1.4,
    );
    if play_response.hovered() || match_response.hovered() {
        ui.ctx().set_cursor_icon(CursorIcon::PointingHand);
    }
    EpisodeRowAction {
        play_clicked: play_response.clicked(),
        change_match_clicked: match_response.clicked(),
    }
}

/// One folder row in the explorer view. `name` of `None` renders the
/// "up one level" row.
fn explorer_folder_row(
    ui: &mut egui::Ui,
    name: Option<&str>,
    item_count: usize,
    strings: Strings,
) -> egui::Response {
    let width = ui.available_width() - 2.0 * metrics::GUTTER;
    let (rect, response) =
        ui.allocate_exact_size(vec2(width + 2.0 * metrics::GUTTER, 40.0), Sense::click());
    if !ui.is_rect_visible(rect) {
        return response;
    }
    let row_rect = Rect::from_min_size(rect.min + vec2(metrics::GUTTER, 2.0), vec2(width, 36.0));
    let fill = if response.hovered() {
        palette::WIDGET_HOVER
    } else {
        palette::SURFACE
    };
    ui.painter().rect_filled(row_rect, 6.0, fill);
    let icon_rect = Rect::from_center_size(
        pos2(row_rect.left() + 22.0, row_rect.center().y),
        vec2(18.0, 18.0),
    );
    paint_icon(
        ui.painter(),
        icon_rect,
        if name.is_some() {
            Icon::Folder
        } else {
            Icon::Back
        },
        palette::TEXT_SECONDARY,
        1.5,
    );
    ui.painter().text(
        pos2(row_rect.left() + 40.0, row_rect.center().y),
        Align2::LEFT_CENTER,
        name.unwrap_or(strings.parent_folder()),
        typography::body(),
        palette::TEXT_PRIMARY,
    );
    if name.is_some() {
        ui.painter().text(
            row_rect.right_center() - vec2(12.0, 0.0),
            Align2::RIGHT_CENTER,
            format!("{item_count} {}", strings.items_label()),
            typography::caption(),
            palette::TEXT_MUTED,
        );
    }
    if response.hovered() {
        ui.ctx().set_cursor_icon(CursorIcon::PointingHand);
    }
    response
}

/// One media file row in the explorer view, columned like the official
/// client's library list: file name + size, matched anime title, matched
/// episode title, and the change-match button.
fn explorer_file_row(ui: &mut egui::Ui, item: &MediaItem, strings: Strings) -> EpisodeRowAction {
    let width = ui.available_width() - 2.0 * metrics::GUTTER;
    let (rect, _) =
        ui.allocate_exact_size(vec2(width + 2.0 * metrics::GUTTER, 48.0), Sense::hover());
    if !ui.is_rect_visible(rect) {
        return EpisodeRowAction {
            play_clicked: false,
            change_match_clicked: false,
        };
    }
    let row_rect = Rect::from_min_size(rect.min + vec2(metrics::GUTTER, 2.0), vec2(width, 44.0));
    const MATCH_BUTTON_SIZE: f32 = 28.0;
    let match_rect = Rect::from_center_size(
        row_rect.right_center() - vec2(MATCH_BUTTON_SIZE / 2.0 + 6.0, 0.0),
        vec2(MATCH_BUTTON_SIZE, MATCH_BUTTON_SIZE),
    );
    let play_rect = Rect::from_min_max(row_rect.min, pos2(match_rect.left() - 4.0, row_rect.max.y));

    let match_id = ui.id().with(("explorer-row-match", item.id.as_str()));
    let play_id = ui.id().with(("explorer-row-play", item.id.as_str()));
    let match_response = ui
        .interact(match_rect, match_id, Sense::click())
        .on_hover_text(strings.change_match());
    let play_response = ui.interact(play_rect, play_id, Sense::click());

    let fill = if play_response.hovered() {
        palette::WIDGET_HOVER
    } else {
        palette::SURFACE
    };
    ui.painter().rect_filled(row_rect, 6.0, fill);

    // Columns: name+size up to 52%, anime title to 74%, episode title to the
    // match button. Each column clips its own text.
    let name_right = row_rect.left() + row_rect.width() * 0.52;
    let anime_right = row_rect.left() + row_rect.width() * 0.74;
    let name_clip = ui.painter().with_clip_rect(Rect::from_min_max(
        row_rect.min,
        pos2(name_right - 8.0, row_rect.max.y),
    ));
    name_clip.text(
        pos2(row_rect.left() + 12.0, row_rect.top() + 13.0),
        Align2::LEFT_CENTER,
        file_name(&item.relative_path),
        typography::body(),
        palette::TEXT_PRIMARY,
    );
    name_clip.text(
        pos2(row_rect.left() + 12.0, row_rect.bottom() - 11.0),
        Align2::LEFT_CENTER,
        format_size(item.size_bytes),
        typography::caption(),
        palette::TEXT_MUTED,
    );
    if let Some(metadata) = &item.anime_metadata {
        let anime_clip = ui.painter().with_clip_rect(Rect::from_min_max(
            pos2(name_right, row_rect.min.y),
            pos2(anime_right - 8.0, row_rect.max.y),
        ));
        anime_clip.text(
            pos2(name_right, row_rect.center().y),
            Align2::LEFT_CENTER,
            &metadata.display_title,
            typography::caption(),
            palette::TEXT_SECONDARY,
        );
        let episode_clip = ui.painter().with_clip_rect(Rect::from_min_max(
            pos2(anime_right, row_rect.min.y),
            pos2(match_rect.left() - 8.0, row_rect.max.y),
        ));
        episode_clip.text(
            pos2(anime_right, row_rect.center().y),
            Align2::LEFT_CENTER,
            &item.episode_title,
            typography::caption(),
            palette::TEXT_MUTED,
        );
    }

    let match_fill = if match_response.hovered() {
        palette::WIDGET_HOVER
    } else {
        Color32::TRANSPARENT
    };
    ui.painter().rect_filled(match_rect, 6.0, match_fill);
    paint_icon(
        ui.painter(),
        match_rect.shrink(6.0),
        Icon::Danmaku,
        if item.anime_metadata.is_some() {
            palette::TEXT_SECONDARY
        } else {
            palette::ACCENT_OUTLINE
        },
        1.4,
    );
    if play_response.hovered() || match_response.hovered() {
        ui.ctx().set_cursor_icon(CursorIcon::PointingHand);
    }
    EpisodeRowAction {
        play_clicked: play_response.clicked(),
        change_match_clicked: match_response.clicked(),
    }
}

/// Formats a byte count the way the official client's library list does
/// ("113.2MB", "1.40GB").
fn format_size(size_bytes: i64) -> String {
    const MB: f64 = 1024.0 * 1024.0;
    const GB: f64 = MB * 1024.0;
    let size = size_bytes.max(0) as f64;
    if size >= GB {
        format!("{:.2}GB", size / GB)
    } else {
        format!("{:.1}MB", size / MB)
    }
}

mod settings;
pub use settings::{SettingsAction, UpdatePromptAction, show_settings, show_update_prompt};
#[cfg(test)]
mod tests {
    use super::{
        LibraryMatchFilter, LibraryProgressFilter, LibrarySeriesSort, filtered_library_series,
        format_size, initials, series_attention_count, series_attention_repairs,
        series_progress_state, year_month_from_epoch_ms,
    };
    use crate::library::{
        AnimeMetadata, AttentionCacheStatus, AttentionIssueCode, AttentionMappingStatus,
        AttentionProviderStatus, AttentionSummary, LibraryAttentionDocument, LibraryAttentionItem,
        MediaItem, PlaybackProgress, Season, Series,
    };

    #[test]
    fn derives_initials_from_titles() {
        assert_eq!(initials("Example Show"), "ES");
        assert_eq!(initials("16bit Sensation"), "1S");
        assert_eq!(initials("約束のネバーランド"), "約");
        assert_eq!(initials(""), "");
    }

    #[test]
    fn converts_index_timestamps_to_recent_month_groups() {
        assert_eq!(year_month_from_epoch_ms(1), Some((1970, 1)));
        assert_eq!(year_month_from_epoch_ms(1_704_067_200_000), Some((2024, 1)));
        assert_eq!(year_month_from_epoch_ms(0), None);
    }

    #[test]
    fn formats_sizes_like_the_official_library_list() {
        assert_eq!(format_size(118_720_922), "113.2MB");
        assert_eq!(format_size(1_503_238_554), "1.40GB");
        assert_eq!(format_size(0), "0.0MB");
        assert_eq!(format_size(-5), "0.0MB");
    }
    fn item(id: &str, path: &str, indexed_at: i64, year: Option<i32>) -> MediaItem {
        MediaItem {
            id: id.to_owned(),
            series_title: path
                .split(['/', '\\'])
                .next()
                .unwrap_or_default()
                .to_owned(),
            episode_title: format!("Episode {id}"),
            relative_path: path.to_owned(),
            indexed_at_epoch_ms: indexed_at,
            anime_metadata: year.map(|start_year| AnimeMetadata {
                display_title: format!("Anime {id}"),
                start_year: Some(start_year),
                ..AnimeMetadata::default()
            }),
            ..MediaItem::default()
        }
    }

    fn series(title: &str, items: Vec<MediaItem>) -> Series {
        Series {
            id: title.to_lowercase(),
            title: title.to_owned(),
            seasons: vec![Season {
                id: format!("{title}-season"),
                label: "Season 1".to_owned(),
                sort_key: 1,
                items,
            }],
        }
    }

    #[test]
    fn filters_library_series_by_query_match_folder_and_sort() {
        let alpha = series(
            "Alpha",
            vec![item("a", "Library A\\Alpha\\01.mkv", 20, Some(2024))],
        );
        let beta = series(
            "Beta",
            vec![
                item("b1", "Library B\\Beta\\01.mkv", 30, None),
                item("b2", "Library B\\Beta\\02.mkv", 31, None),
            ],
        );
        let source = vec![alpha, beta];

        let matched = filtered_library_series(
            &source,
            "alpha",
            LibraryMatchFilter::Matched,
            LibraryProgressFilter::All,
            Some("Library A"),
            None,
            LibrarySeriesSort::Title,
            &[],
        );
        assert_eq!(
            matched
                .iter()
                .map(|entry| entry.title.as_str())
                .collect::<Vec<_>>(),
            vec!["Alpha"]
        );

        let newest = filtered_library_series(
            &source,
            "",
            LibraryMatchFilter::All,
            LibraryProgressFilter::All,
            None,
            None,
            LibrarySeriesSort::Newest,
            &[],
        );
        assert_eq!(
            newest
                .iter()
                .map(|entry| entry.title.as_str())
                .collect::<Vec<_>>(),
            vec!["Beta", "Alpha"]
        );

        let by_year = filtered_library_series(
            &source,
            "",
            LibraryMatchFilter::All,
            LibraryProgressFilter::All,
            None,
            Some(2024),
            LibrarySeriesSort::Title,
            &[],
        );
        assert_eq!(
            by_year
                .iter()
                .map(|entry| entry.title.as_str())
                .collect::<Vec<_>>(),
            vec!["Alpha"]
        );
    }

    #[test]
    fn folder_filter_matches_root_labels_when_present() {
        let mut first = item("a", "Alpha\\01.mkv", 20, None);
        first.root_label = Some("M:\\Anime".to_owned());
        let mut second = item("b", "Beta\\01.mkv", 30, None);
        second.root_label = Some("D:\\AniRss".to_owned());
        let source = vec![series("Alpha", vec![first]), series("Beta", vec![second])];

        let scoped = filtered_library_series(
            &source,
            "",
            LibraryMatchFilter::All,
            LibraryProgressFilter::All,
            Some("m:\\anime"),
            None,
            LibrarySeriesSort::Title,
            &[],
        );
        assert_eq!(
            scoped
                .iter()
                .map(|entry| entry.title.as_str())
                .collect::<Vec<_>>(),
            vec!["Alpha"]
        );
    }

    #[test]
    fn classifies_unwatched_in_progress_and_completed_series() {
        let entry = series(
            "Alpha",
            vec![item("a", "Library A\\Alpha\\01.mkv", 20, Some(2024))],
        );
        assert_eq!(
            series_progress_state(&entry, &[]),
            LibraryProgressFilter::Unwatched
        );

        let in_progress = PlaybackProgress {
            media_id: "a".to_owned(),
            position_ms: 20_000,
            duration_ms: Some(100_000),
            updated_at_epoch_ms: 1,
        };
        assert_eq!(
            series_progress_state(&entry, &[in_progress]),
            LibraryProgressFilter::InProgress
        );

        let completed = PlaybackProgress {
            media_id: "a".to_owned(),
            position_ms: 90_000,
            duration_ms: Some(100_000),
            updated_at_epoch_ms: 2,
        };
        assert_eq!(
            series_progress_state(&entry, &[completed]),
            LibraryProgressFilter::Completed
        );
    }

    #[test]
    fn series_with_some_episodes_completed_counts_as_in_progress() {
        // Episode 1 fully watched, episode 2 untouched: the series was
        // started, so it must not land in "unwatched".
        let entry = series(
            "Alpha",
            vec![
                item("a1", "Library A\\Alpha\\01.mkv", 20, None),
                item("a2", "Library A\\Alpha\\02.mkv", 21, None),
            ],
        );
        let first_completed = PlaybackProgress {
            media_id: "a1".to_owned(),
            position_ms: 99_000,
            duration_ms: Some(100_000),
            updated_at_epoch_ms: 5,
        };
        assert_eq!(
            series_progress_state(&entry, &[first_completed]),
            LibraryProgressFilter::InProgress
        );
    }

    #[test]
    fn attention_repairs_preserve_mapped_episode_identity() {
        let entry = series(
            "Alpha",
            vec![
                item("mapped-safe", "Alpha\\01.mkv", 20, None),
                item("mapped-legacy", "Alpha\\02.mkv", 21, None),
                item("unmapped", "Alpha\\03.mkv", 22, None),
            ],
        );
        let attention = LibraryAttentionDocument::new(
            1,
            AttentionProviderStatus {
                available: true,
                reason_code: None,
            },
            AttentionSummary::default(),
            vec![
                LibraryAttentionItem {
                    media_id: "mapped-safe".to_owned(),
                    mapping_status: AttentionMappingStatus::Mapped,
                    cache_status: AttentionCacheStatus::Stale,
                    anime_id: Some(42),
                    episode_id: Some(420001),
                    issue_codes: vec![AttentionIssueCode::StaleDanmakuCache],
                    last_failure: None,
                },
                LibraryAttentionItem {
                    media_id: "mapped-legacy".to_owned(),
                    mapping_status: AttentionMappingStatus::Mapped,
                    cache_status: AttentionCacheStatus::Missing,
                    anime_id: Some(42),
                    episode_id: None,
                    issue_codes: vec![AttentionIssueCode::MissingDanmakuCache],
                    last_failure: None,
                },
                LibraryAttentionItem {
                    media_id: "unmapped".to_owned(),
                    mapping_status: AttentionMappingStatus::Unmapped,
                    cache_status: AttentionCacheStatus::Missing,
                    anime_id: None,
                    episode_id: None,
                    issue_codes: vec![
                        AttentionIssueCode::UnmappedAnime,
                        AttentionIssueCode::MissingDanmakuCache,
                    ],
                    last_failure: None,
                },
            ],
        );

        assert_eq!(series_attention_count(&entry, Some(&attention)), 3);
        let refresh = series_attention_repairs(&entry, Some(&attention), false);
        assert_eq!(refresh.len(), 1);
        assert_eq!(refresh[0].media_id, "mapped-safe");
        assert_eq!(refresh[0].episode_id, Some(420001));
        let matches = series_attention_repairs(&entry, Some(&attention), true);
        assert_eq!(matches.len(), 1);
        assert_eq!(matches[0].media_id, "unmapped");
    }
}
