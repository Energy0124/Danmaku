use super::*;

impl eframe::App for PlayerApp {
    fn update(&mut self, ctx: &egui::Context, frame: &mut eframe::Frame) {
        let now = Instant::now();
        if self.updater.poll(ctx) {
            ctx.send_viewport_cmd(ViewportCommand::Close);
        }
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
        self.abandon_unattached_session_if_host_gone(ctx);
        self.poll_server_scan(ctx, now);
        self.persist_session_cache_if_needed();

        match self.screen {
            AppScreen::Connect => {
                let discovered = self
                    .discovery
                    .as_ref()
                    .map(|listener| listener.servers())
                    .unwrap_or_default();
                let qa_host_status = LocalHostStatus::NeedsSetup;
                let local_host_status = if self.cli.qa_onboarding {
                    Some(&qa_host_status)
                } else {
                    self.local_host.as_ref().map(LocalServerSupervisor::status)
                };
                let action = self.connect_screen.show(
                    ctx,
                    &discovered,
                    &mut self.preferences.language,
                    local_host_status,
                    self.cli.qa_primary_state.as_deref(),
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
                self.show_library_sync_banner(ctx);
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
                        &self.pending_preloads,
                        &self.bangumi_details,
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
                    Some(LibraryAction::PreloadDanmaku { media_ids }) => {
                        if let Some(session) = &self.session
                            && session.connected
                        {
                            for media_id in media_ids {
                                if self.pending_preloads.insert(media_id.clone()) {
                                    session.fetch_danmaku(media_id, false);
                                }
                            }
                        }
                    }
                    Some(LibraryAction::RepairAttention { requests }) => {
                        self.start_attention_repairs(requests);
                    }
                    Some(LibraryAction::ChangeMatch { media_id }) => {
                        self.open_match_picker(media_id);
                    }
                    Some(LibraryAction::FetchBangumiDetail { anime_id }) => {
                        if let Some(session) = &self.session
                            && !self.bangumi_details.contains_key(&anime_id)
                        {
                            self.bangumi_details
                                .insert(anime_id, BangumiDetailState::Loading);
                            session.fetch_bangumi_detail(anime_id);
                        }
                    }
                    Some(LibraryAction::Refresh) => {
                        if let Some(session) = &mut self.session {
                            session.refresh_catalog();
                            session.refresh_attention();
                            session.refresh_progress();
                        }
                    }
                    Some(LibraryAction::RescanFolder { path }) => {
                        if let Some(session) = &mut self.session {
                            session.refresh_folder(path);
                        }
                    }
                    Some(LibraryAction::PreviewOrganization(request)) => {
                        if let Some(session) = &mut self.session {
                            session.preview_organization(request);
                        }
                    }
                    Some(LibraryAction::ExecuteOrganization { plan_id, batch }) => {
                        if let Some(session) = &mut self.session {
                            session.execute_organization(plan_id, batch);
                        }
                    }
                    Some(LibraryAction::RefreshOrganizationStatus) => {
                        if let Some(session) = &self.session {
                            session.refresh_organization_status();
                        }
                    }
                    Some(LibraryAction::CancelOrganization) => {
                        if let Some(session) = &mut self.session {
                            session.cancel_organization();
                        }
                    }
                    Some(LibraryAction::UndoOrganization { completed_batch_id }) => {
                        if let Some(session) = &mut self.session {
                            session.undo_organization(completed_batch_id);
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
                    &mut self.dandanplay_credentials,
                    self.updater.status(),
                ) {
                    Some(SettingsAction::Back) => self.screen = self.settings_return,
                    Some(SettingsAction::OpenTracking) => self.open_tracking(AppScreen::Settings),
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
                    Some(SettingsAction::AddLibraryFolder(root)) => {
                        let root_string = root.to_string_lossy().into_owned();
                        if !self
                            .preferences
                            .local_library_roots
                            .iter()
                            .any(|existing| existing == &root_string)
                        {
                            self.preferences.local_library_roots.push(root_string);
                        }
                        if self.connect_screen.local_library_root.trim().is_empty() {
                            self.connect_screen.local_library_root =
                                root.to_string_lossy().into_owned();
                        }
                        self.restart_local_host_with_saved_roots();
                    }
                    Some(SettingsAction::RemoveLibraryFolder(root)) => {
                        self.preferences
                            .local_library_roots
                            .retain(|existing| existing != &root);
                        self.restart_local_host_with_saved_roots();
                    }
                    Some(SettingsAction::SaveDandanplayCredentials) => {
                        self.dandanplay_credentials =
                            self.dandanplay_credentials.clone().sanitized();
                        if let Err(error) = self.credential_store.save(&self.dandanplay_credentials)
                        {
                            self.connect_screen.error =
                                Some(format!("failed to save credentials: {error}"));
                        }
                        if let Some(local_host) = &mut self.local_host {
                            local_host.set_dandanplay(Some(self.dandanplay_credentials.clone()));
                        }
                        self.restart_local_host_with_saved_roots();
                    }
                    Some(SettingsAction::ClearDandanplayCredentials) => {
                        self.dandanplay_credentials = DandanplayCredentials::default();
                        if let Err(error) = self.credential_store.save(&self.dandanplay_credentials)
                        {
                            self.connect_screen.error =
                                Some(format!("failed to clear credentials: {error}"));
                        }
                        if let Some(local_host) = &mut self.local_host {
                            local_host.set_dandanplay(None);
                        }
                        self.restart_local_host_with_saved_roots();
                    }
                    Some(SettingsAction::CheckForUpdates) => self.updater.check(ctx),
                    Some(SettingsAction::UpdateAndRestart) => {
                        if matches!(self.updater.status(), UpdateStatus::Available { .. }) {
                            self.updater.download(ctx);
                        } else if self.updater.install_and_restart() {
                            ctx.send_viewport_cmd(ViewportCommand::Close);
                        }
                    }
                    None => {}
                }
                if self.preferences != before {
                    self.apply_changed_preferences(&before, ctx);
                }
            }
            AppScreen::Tracking => {
                if let Some(action) =
                    show_tracking(ctx, &mut self.tracking_screen, self.preferences.language)
                {
                    let session = self.session.as_ref();
                    match action {
                        TrackingAction::Back => self.screen = self.tracking_return,
                        TrackingAction::Refresh => {
                            self.tracking_screen.loading = true;
                            if let Some(session) = session {
                                session.refresh_provider_accounts();
                                session.refresh_tracking();
                            }
                        }
                        TrackingAction::StartMyAnimeList => {
                            self.tracking_screen.loading = true;
                            self.tracking_screen.error = None;
                            if let Some(session) = session {
                                session.start_my_anime_list_oauth();
                            }
                        }
                        TrackingAction::ConnectBangumi(token) => {
                            self.tracking_screen.loading = true;
                            if let Some(session) = session {
                                session.connect_bangumi(token);
                            }
                        }
                        TrackingAction::Disconnect(provider) => {
                            self.tracking_screen.loading = true;
                            if let Some(session) = session {
                                session.disconnect_provider(provider);
                            }
                        }
                        TrackingAction::Readback => {
                            self.tracking_screen.loading = true;
                            if let Some(session) = session {
                                session.refresh_tracking_readback();
                            }
                        }
                        TrackingAction::Sync(updates) => {
                            self.tracking_screen.loading = true;
                            if let Some(session) = session {
                                session.sync_tracking(updates);
                            }
                        }
                        TrackingAction::Search {
                            local_series_id,
                            query,
                            provider,
                        } => {
                            if let Some(session) = session {
                                session.search_tracking(local_series_id, query, provider);
                            }
                        }
                        TrackingAction::SaveMapping {
                            local_series_id,
                            anime_id,
                        } => {
                            self.tracking_screen.loading = true;
                            if let Some(session) = session {
                                session.save_tracking_mapping(local_series_id, anime_id);
                            }
                        }
                        TrackingAction::DeleteMapping {
                            local_series_id,
                            anime_id,
                        } => {
                            self.tracking_screen.loading = true;
                            if let Some(session) = session {
                                session.delete_tracking_mapping(local_series_id, anime_id);
                            }
                        }
                        TrackingAction::ImportConflict {
                            local_series_id,
                            anime_id,
                            expected_external_watched_episodes,
                        } => {
                            self.tracking_screen.loading = true;
                            if let Some(session) = session {
                                session.import_tracking_conflict(
                                    local_series_id,
                                    anime_id,
                                    expected_external_watched_episodes,
                                );
                            }
                        }
                    }
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
        self.sync_fullscreen_window_level(ctx);
        self.show_match_picker_overlay(ctx);
        self.show_tracking_completion_prompt(ctx);
        if self.updater.should_prompt()
            && let Some(action) =
                show_update_prompt(ctx, self.updater.status(), self.preferences.language)
        {
            match action {
                UpdatePromptAction::Dismiss => self.updater.dismiss_prompt(),
                UpdatePromptAction::UpdateAndRestart => {
                    if matches!(self.updater.status(), UpdateStatus::Available { .. }) {
                        self.updater.download(ctx);
                    } else if self.updater.install_and_restart() {
                        ctx.send_viewport_cmd(ViewportCommand::Close);
                    }
                }
            }
        }
        self.save_preferences_if_changed();
        self.finish_qa_screenshot_if_needed(ctx);
    }
}
