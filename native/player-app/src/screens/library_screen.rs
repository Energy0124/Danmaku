use super::*;

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
pub(super) enum LibraryMatchFilter {
    #[default]
    All,
    Matched,
    Unmatched,
}

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub(super) enum LibraryProgressFilter {
    #[default]
    All,
    Unwatched,
    InProgress,
    Completed,
}

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub(super) enum LibrarySeriesSort {
    #[default]
    Title,
    Newest,
    ReleaseYear,
    EpisodeCount,
}

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub(super) enum LibraryGridDensity {
    Compact,
    #[default]
    Comfortable,
    Large,
}

impl LibraryGridDensity {
    pub(super) fn card_size(self) -> egui::Vec2 {
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
