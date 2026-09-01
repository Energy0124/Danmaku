use super::*;

impl PlayerApp {
    pub(super) fn start_attention_repairs(&mut self, requests: Vec<AttentionRepairRequest>) {
        if self.attention_repairs.enqueue(requests) > 0 {
            self.dispatch_next_attention_repair();
        }
    }

    pub(super) fn dispatch_next_attention_repair(&mut self) {
        if self.attention_repairs.active_media_id.is_some() {
            return;
        }
        if self.attention_repairs.pending.is_empty() {
            if self.attention_repairs.is_complete() {
                if let Some(session) = self.session.as_mut() {
                    session.refresh_catalog();
                    session.refresh_attention();
                }
                self.attention_repairs.clear();
            }
            return;
        }
        if self
            .session
            .as_ref()
            .is_none_or(|session| !session.connected)
        {
            self.attention_repairs.clear();
            return;
        }
        let request = self
            .attention_repairs
            .begin_next()
            .expect("pending attention repair should dispatch");
        self.pending_preloads.insert(request.media_id.clone());
        self.session
            .as_ref()
            .expect("connected attention repair session")
            .repair_attention(request);
    }

    pub(super) fn handle_session_events(&mut self, ctx: &egui::Context) {
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
                    let is_active = self.active_media_id.as_deref() == Some(media_id.as_str());
                    // A preload (see `LibraryAction::PreloadDanmaku`) resolves the same
                    // way as a playback-triggered fetch but must not touch `self.danmaku`,
                    // since it can run for an item other than the one on screen.
                    let is_preload = self.pending_preloads.remove(&media_id);
                    if (is_active || is_preload)
                        && let Ok(load) = &load
                        && let Some(match_title) = &load.match_title
                        && self.library_grouping_is_stale(&media_id, match_title)
                        && let Some(session) = self.session.as_mut()
                    {
                        session.refresh_catalog();
                    }
                    if is_active {
                        self.danmaku = load.unwrap_or_else(DanmakuLoad::failed);
                    }
                }
                SessionEvent::AttentionRepair { media_id, result } => {
                    let failed = result.is_err();
                    if !self.attention_repairs.complete(&media_id, failed) {
                        eprintln!("ignored stale danmaku repair result for {media_id}");
                        continue;
                    }
                    self.pending_preloads.remove(&media_id);
                    if let Err(error) = result {
                        eprintln!("danmaku repair failed for {media_id}: {error}");
                    }
                    self.dispatch_next_attention_repair();
                    ctx.request_repaint();
                }
                SessionEvent::DandanplayCandidates { media_id, result } => {
                    // Discard a response for an item the picker isn't showing
                    // anymore (closed, or the user moved to another episode).
                    if self.match_picker.media_id.as_deref() == Some(media_id.as_str()) {
                        self.match_picker.loading = false;
                        match result {
                            Ok(candidates) => {
                                self.match_picker.candidates = candidates;
                                self.match_picker.error = None;
                            }
                            Err(error) => self.match_picker.error = Some(error),
                        }
                    }
                }
                SessionEvent::DandanplaySearch { media_id, result } => {
                    if self.match_picker.media_id.as_deref() == Some(media_id.as_str()) {
                        self.match_picker.searching = false;
                        match result {
                            Ok(animes) => {
                                // Auto-expand a lone anime so the episode list
                                // is one click closer.
                                self.match_picker.expanded_anime_id = (animes.len() == 1)
                                    .then(|| animes[0].anime_id)
                                    .or(self.match_picker.expanded_anime_id);
                                self.match_picker.search_results = animes;
                                self.match_picker.searched = true;
                                self.match_picker.error = None;
                            }
                            Err(error) => self.match_picker.error = Some(error),
                        }
                    }
                }
                SessionEvent::DandanplaySelected {
                    media_id,
                    selection,
                    result,
                } => {
                    if self.match_picker.media_id.as_deref() == Some(media_id.as_str())
                        && self.match_picker.selecting_episode_id == Some(selection.episode_id)
                    {
                        match result {
                            Ok(()) => {
                                // Selecting from the library doesn't necessarily
                                // touch the actively playing item, so run the
                                // catalog-staleness check directly here rather
                                // than relying on the active/preload branches in
                                // the `Danmaku` event above.
                                let anime_title = self
                                    .match_picker
                                    .selecting_anime_title
                                    .clone()
                                    .or(selection.anime_title.clone());
                                if let Some(anime_title) = &anime_title
                                    && self.library_grouping_is_stale(&media_id, anime_title)
                                    && let Some(session) = self.session.as_mut()
                                {
                                    session.refresh_catalog();
                                }
                                self.match_picker = MatchPickerState::default();
                                // Keep the server-side comment cache warm for
                                // this episode, and update the live overlay if
                                // it happens to be the item currently playing.
                                if let Some(session) = &self.session {
                                    session.fetch_danmaku(media_id, false);
                                }
                            }
                            Err(error) => {
                                self.match_picker.selecting_episode_id = None;
                                self.match_picker.selecting_anime_title = None;
                                self.match_picker.error = Some(error);
                            }
                        }
                    }
                }
                SessionEvent::BangumiDetail { anime_id, result } => {
                    self.bangumi_details.insert(
                        anime_id,
                        match result {
                            Ok(detail) => BangumiDetailState::Ready(detail),
                            Err(error) => BangumiDetailState::Failed(error),
                        },
                    );
                }
                SessionEvent::ProviderAccounts(result) => {
                    self.tracking_screen.loading = false;
                    match result {
                        Ok(accounts) => {
                            self.tracking_screen.accounts = Some(accounts);
                            self.tracking_screen.bangumi_token.clear();
                            self.tracking_screen.error = None;
                        }
                        Err(error) => self.tracking_screen.error = Some(error),
                    }
                }
                SessionEvent::MyAnimeListOAuthReady(result) => match result {
                    Ok(url) => {
                        ctx.open_url(egui::OpenUrl::new_tab(url));
                        self.tracking_screen.notice = Some(
                            Strings::new(self.preferences.language)
                                .mal_browser_notice()
                                .to_owned(),
                        );
                    }
                    Err(error) => {
                        self.tracking_screen.loading = false;
                        self.tracking_screen.error = Some(error);
                    }
                },
                SessionEvent::Tracking(result) => {
                    self.tracking_screen.loading = false;
                    match result {
                        Ok(document) => {
                            self.tracking_screen.document = Some(document);
                            self.tracking_screen.error = None;
                        }
                        Err(error) => {
                            self.tracking_screen.error = Some(error);
                            if let Some(session) = &self.session {
                                session.refresh_provider_accounts();
                            }
                        }
                    }
                }
                SessionEvent::TrackingSearch {
                    local_series_id,
                    result,
                } => {
                    if let Some(editor) = &mut self.tracking_screen.mapping_editor
                        && editor.local_series_id == local_series_id
                    {
                        editor.searching = false;
                        match result {
                            Ok(results) => {
                                editor.results = results;
                                self.tracking_screen.error = None;
                            }
                            Err(error) => self.tracking_screen.error = Some(error),
                        }
                    }
                }
                _ => {}
            }
        }
    }

    /// True when the session's cached catalog does not yet reflect a
    /// dandanplay recognition for `media_id`. See `catalog_grouping_is_stale`.
    pub(super) fn library_grouping_is_stale(&self, media_id: &str, match_title: &str) -> bool {
        catalog_grouping_is_stale(
            self.session
                .as_ref()
                .and_then(|session| session.catalog.as_ref()),
            media_id,
            match_title,
        )
    }

    pub(super) fn connect_to_server(&mut self, ctx: &egui::Context, request: ConnectRequest) {
        self.preferences.last_server_url = Some(request.base_url.clone());
        self.posters.set_base_url(Some(request.base_url.clone()));
        // A cache-seeded session keeps its catalog on screen; attaching just
        // points it at the live server and refreshes in the background.
        if self
            .session
            .as_ref()
            .is_some_and(|session| !session.connected)
        {
            self.session
                .as_mut()
                .expect("cache-seeded session")
                .attach(request.base_url);
        } else {
            discard_library_session(
                &mut self.session,
                &mut self.pending_preloads,
                &mut self.attention_repairs,
            );
            self.session = Some(LibrarySession::connect(request.base_url, ctx.clone()));
        }
        self.connect_screen.error = None;
        self.screen = AppScreen::Library;
    }

    pub(super) fn connect_to_local_server(
        &mut self,
        ctx: &egui::Context,
        connection: LocalConnection,
    ) {
        self.connect_to_server(
            ctx,
            ConnectRequest {
                base_url: connection.base_url,
            },
        );
    }

    pub(super) fn prepare_for_host_transition(&mut self) {
        self.upload_active_progress(true);
        self.run_mpv_command(&["stop"]);
        self.active_media_id = None;
        discard_library_session(
            &mut self.session,
            &mut self.pending_preloads,
            &mut self.attention_repairs,
        );
        self.bangumi_details.clear();
        self.posters.set_base_url(None);
        self.screen = AppScreen::Connect;
    }

    pub(super) fn local_roots(&self) -> Vec<PathBuf> {
        self.preferences
            .local_library_roots
            .iter()
            .map(PathBuf::from)
            .collect()
    }

    /// Restarts the managed sidecar against the currently saved roots, or stops
    /// it when no folders remain. Used when folders or credentials change.
    pub(super) fn restart_local_host_with_saved_roots(&mut self) {
        let roots = self.local_roots();
        self.prepare_for_host_transition();
        let Some(local_host) = &mut self.local_host else {
            return;
        };
        if roots.is_empty() {
            local_host.stop();
        } else if let Err(error) = local_host.restart(roots) {
            self.connect_screen.error = Some(error);
        }
    }

    pub(super) fn request_library_play(&mut self, media_id: String) {
        // Streaming needs the live server; ignore clicks while the session is
        // only showing the cached library (the sync banner explains why).
        if let Some(session) = &self.session
            && session.connected
        {
            session.lookup_resume(media_id.clone());
            self.pending_play_media_id = Some(media_id);
        }
    }

    pub(super) fn start_library_playback(
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
        self.match_picker = MatchPickerState::default();
        if let Some(session) = &self.session {
            session.fetch_danmaku(media_id, self.cli.danmaku_force_refresh);
        }
        self.eof_handled = false;
        self.last_progress_upload = now;
        self.screen = AppScreen::Playback;
    }

    pub(super) fn play_adjacent_episode(&mut self, direction: i64) {
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

    pub(super) fn back_to_library(&mut self) {
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
            session.refresh_catalog();
        }
    }

    pub(super) fn disconnect(&mut self) {
        self.upload_active_progress(true);
        self.run_mpv_command(&["stop"]);
        self.active_media_id = None;
        discard_library_session(
            &mut self.session,
            &mut self.pending_preloads,
            &mut self.attention_repairs,
        );
        self.bangumi_details.clear();
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
    pub(super) fn upload_active_progress(&mut self, force: bool) {
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

    pub(super) fn handle_end_of_file(&mut self) {
        if !self.snapshot.eof_reached || self.eof_handled {
            return;
        }
        self.eof_handled = true;
        if self.session.is_none() || self.active_media_id.is_none() {
            return;
        }
        // Record the position as watched, then advance when enabled.
        self.upload_active_progress(true);
        let tracking_connected = self
            .tracking_screen
            .accounts
            .as_ref()
            .is_some_and(|accounts| {
                accounts.my_anime_list.is_connected() || accounts.bangumi.is_connected()
            });
        if tracking_connected {
            self.tracking_completion_prompt = true;
        } else if self.auto_next {
            self.play_adjacent_episode(1);
        }
    }

    pub(super) fn open_tracking(&mut self, return_to: AppScreen) {
        self.tracking_return = return_to;
        self.tracking_screen.loading = self.session.is_some();
        self.tracking_screen.error = None;
        self.screen = AppScreen::Tracking;
        if let Some(session) = &self.session {
            session.refresh_provider_accounts();
            session.refresh_tracking();
        }
    }

    pub(super) fn show_tracking_completion_prompt(&mut self, ctx: &egui::Context) {
        if !self.tracking_completion_prompt {
            return;
        }
        let strings = Strings::new(self.preferences.language);
        egui::Window::new(strings.tracking_completion_title())
            .id(egui::Id::new("tracking_completion_prompt"))
            .anchor(Align2::CENTER_CENTER, egui::Vec2::ZERO)
            .collapsible(false)
            .resizable(false)
            .show(ctx, |ui| {
                ui.set_width(390.0);
                ui.label(strings.tracking_completion_body());
                ui.add_space(10.0);
                ui.horizontal(|ui| {
                    if ui.button(strings.review_tracking_update()).clicked() {
                        self.tracking_completion_prompt = false;
                        self.open_tracking(AppScreen::Playback);
                    }
                    if ui.button(strings.not_now()).clicked() {
                        self.tracking_completion_prompt = false;
                        if self.auto_next {
                            self.play_adjacent_episode(1);
                        }
                    }
                });
            });
    }
}
