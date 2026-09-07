use super::*;

impl PlayerApp {
    pub(super) fn show_video(
        &mut self,
        ctx: &egui::Context,
        now: Instant,
        overlay_position_s: f64,
    ) {
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

    pub(super) fn paint_danmaku(
        &self,
        ui: &egui::Ui,
        video_rect: Rect,
        overlay_position_s: f64,
    ) -> usize {
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

    pub(super) fn controls_alpha(&self) -> f32 {
        if self.snapshot.paused {
            return 1.0;
        }
        theme::fade_alpha(
            self.last_pointer_activity.elapsed().as_secs_f32(),
            theme::CHROME_HOLD_SECONDS,
            theme::CHROME_FADE_SECONDS,
        )
    }

    /// Title lines for the playback chrome: series and episode when a library
    /// item is active, otherwise the direct-media display title.
    pub(super) fn playback_titles(&self) -> (String, Option<String>) {
        if let (Some(session), Some(active)) = (&self.session, self.active_media_id.as_deref())
            && let Some(catalog) = &session.catalog
            && let Some(item) = catalog.items.iter().find(|item| item.id == active)
        {
            return (item.series_title.clone(), Some(item.episode_title.clone()));
        }
        (self.display_title.clone(), None)
    }

    /// The upcoming library episode, for the "Next:" preview card.
    pub(super) fn upcoming_episode(&self) -> Option<crate::library::MediaItem> {
        let session = self.session.as_ref()?;
        let catalog = session.catalog.as_ref()?;
        let active = self.active_media_id.as_deref()?;
        catalog.next_item(active).cloned()
    }

    pub(super) fn show_controls(
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

        // Title band: soft gradient instead of a hard box, per the mockup.
        let band_rect = Rect::from_min_max(
            video_rect.left_top(),
            pos2(video_rect.right(), video_rect.top() + 92.0),
        );
        let mut band = egui::Mesh::default();
        let band_base = band.vertices.len() as u32;
        let band_color = Color32::from_rgba_premultiplied(4, 6, 10, (200.0 * alpha) as u8);
        band.colored_vertex(band_rect.left_top(), band_color);
        band.colored_vertex(band_rect.right_top(), band_color);
        band.colored_vertex(band_rect.left_bottom(), Color32::TRANSPARENT);
        band.colored_vertex(band_rect.right_bottom(), Color32::TRANSPARENT);
        band.add_triangle(band_base, band_base + 1, band_base + 2);
        band.add_triangle(band_base + 2, band_base + 1, band_base + 3);
        ui.painter().add(egui::Shape::mesh(band));

        let (title_main, title_sub) = self.playback_titles();
        ui.scope_builder(
            egui::UiBuilder::new().max_rect(band_rect.shrink2(vec2(20.0, 12.0))),
            |ui| {
                ui.multiply_opacity(alpha);
                ui.horizontal(|ui| {
                    if self.session.is_some()
                        && playback_icon_button(ui, Icon::Back, strings.library(), false).clicked()
                    {
                        self.back_to_library();
                    }
                    ui.add_space(8.0);
                    ui.vertical(|ui| {
                        ui.spacing_mut().item_spacing.y = 2.0;
                        ui.label(
                            RichText::new(&title_main)
                                .font(typography::title())
                                .strong()
                                .color(theme::text_primary_faded(alpha)),
                        );
                        if let Some(subtitle) = &title_sub {
                            ui.label(
                                RichText::new(subtitle)
                                    .font(typography::caption())
                                    .color(palette::TEXT_MUTED),
                            );
                        }
                    });
                });
            },
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

        self.show_next_episode_card(ui, rect, alpha);

        ui.painter()
            .rect_filled(rect, 14.0, theme::overlay_fill(alpha));
        ui.painter().rect_stroke(
            rect,
            14.0,
            theme::overlay_outline(alpha),
            StrokeKind::Inside,
        );

        let content = rect.shrink(metrics::OVERLAY_PADDING);
        ui.scope_builder(egui::UiBuilder::new().max_rect(content), |ui| {
            ui.set_clip_rect(rect);
            ui.multiply_opacity(alpha);

            // Full-width seek bar with a round thumb, visually primary.
            let duration = self.snapshot.duration_s.max(1.0);
            let seek_rect = Rect::from_min_size(content.min, vec2(content.width(), 18.0));
            let fraction = (overlay_position_s.clamp(0.0, duration) / duration) as f32;
            if let Some(new_fraction) =
                media_slider(ui, "playback_seek", seek_rect, fraction, alpha)
            {
                self.seek_to(new_fraction as f64 * duration, now);
            }

            let row = Rect::from_min_max(
                pos2(content.left(), content.top() + 26.0),
                content.right_bottom(),
            );

            // Left cluster: time, then volume.
            ui.scope_builder(egui::UiBuilder::new().max_rect(row), |ui| {
                ui.horizontal_centered(|ui| {
                    ui.label(
                        RichText::new(format!(
                            "{} / {}",
                            format_time(overlay_position_s),
                            format_time(self.snapshot.duration_s)
                        ))
                        .font(typography::body())
                        .color(palette::TEXT_SECONDARY),
                    );
                    ui.add_space(14.0);
                    let mute_label = if self.snapshot.muted {
                        strings.unmute()
                    } else {
                        strings.mute()
                    };
                    if playback_icon_button(
                        ui,
                        if self.snapshot.muted {
                            Icon::Muted
                        } else {
                            Icon::Volume
                        },
                        mute_label,
                        false,
                    )
                    .clicked()
                    {
                        self.toggle_mute();
                    }
                    let volume_rect =
                        Rect::from_min_size(ui.cursor().min + vec2(0.0, 11.0), vec2(96.0, 18.0));
                    ui.allocate_rect(volume_rect, egui::Sense::hover());
                    let volume_fraction = (self.snapshot.volume_percent / 130.0) as f32;
                    if let Some(new_fraction) =
                        media_slider(ui, "playback_volume", volume_rect, volume_fraction, alpha)
                    {
                        self.set_volume(new_fraction as f64 * 130.0);
                    }
                });
            });

            // Center transport cluster.
            let transport_width = 5.0 * 44.0 + 24.0;
            let center_rect = Rect::from_center_size(
                pos2(row.center().x, row.center().y),
                vec2(transport_width, row.height()),
            );
            ui.scope_builder(egui::UiBuilder::new().max_rect(center_rect), |ui| {
                ui.horizontal_centered(|ui| {
                    let has_neighbors = self.session.is_some() && self.active_media_id.is_some();
                    if has_neighbors
                        && playback_icon_button(
                            ui,
                            Icon::Previous,
                            strings.previous_episode(),
                            false,
                        )
                        .clicked()
                    {
                        self.play_adjacent_episode(-1);
                    }
                    if playback_icon_button(ui, Icon::Replay10, "-10 s", false).clicked() {
                        self.seek_relative(-10.0, now);
                    }
                    let play_icon = if self.snapshot.paused {
                        Icon::Play
                    } else {
                        Icon::Pause
                    };
                    let play_label = if self.snapshot.paused {
                        strings.play()
                    } else {
                        strings.pause()
                    };
                    if playback_icon_button(ui, play_icon, play_label, true).clicked() {
                        self.toggle_pause(now);
                    }
                    if playback_icon_button(ui, Icon::Forward30, "+30 s", false).clicked() {
                        self.seek_relative(30.0, now);
                    }
                    if has_neighbors
                        && playback_icon_button(ui, Icon::Next, strings.next_episode(), false)
                            .clicked()
                    {
                        self.play_adjacent_episode(1);
                    }
                });
            });

            // Right cluster: tracks, danmaku, speed, settings, fullscreen.
            ui.scope_builder(egui::UiBuilder::new().max_rect(row), |ui| {
                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    if playback_icon_button(
                        ui,
                        Icon::Fullscreen,
                        if self.fullscreen {
                            strings.windowed()
                        } else {
                            strings.fullscreen()
                        },
                        false,
                    )
                    .clicked()
                    {
                        let ctx = ui.ctx().clone();
                        self.toggle_fullscreen(&ctx);
                    }
                    if playback_icon_button(ui, Icon::Settings, strings.settings(), false).clicked()
                    {
                        self.open_settings(AppScreen::Playback);
                    }
                    self.show_danmaku_menu(ui, active_danmaku);
                    let speed_response =
                        playback_text_button(ui, &format_speed(self.snapshot.speed));
                    egui::Popup::menu(&speed_response).show(|ui| {
                        for rate in PLAYBACK_RATES {
                            if ui.button(format!("{rate:.2}×")).clicked() {
                                self.set_playback_rate(rate, now);
                                ui.close();
                            }
                        }
                    });
                    self.show_track_menus(ui);
                });
            });
        });
    }

    /// Small "Next: …" preview card above the control bar, library mode only.
    pub(super) fn show_next_episode_card(
        &mut self,
        ui: &mut egui::Ui,
        control_rect: Rect,
        alpha: f32,
    ) {
        let Some(next) = self.upcoming_episode() else {
            return;
        };
        let strings = Strings::new(self.preferences.language);
        let label = strings.up_next(&next.episode_title);
        let galley = ui.painter().layout_no_wrap(
            label.clone(),
            typography::caption(),
            palette::TEXT_PRIMARY,
        );
        let width = (galley.size().x + 110.0).min(control_rect.width() * 0.5);
        let card = Rect::from_min_max(
            pos2(
                control_rect.right() - width,
                control_rect.top() - 10.0 - 64.0,
            ),
            pos2(control_rect.right(), control_rect.top() - 10.0),
        );
        let response = ui.interact(
            card,
            ui.id().with("next_episode_card"),
            egui::Sense::click(),
        );
        ui.painter()
            .rect_filled(card, 10.0, theme::overlay_fill(alpha));
        ui.painter().rect_stroke(
            card,
            10.0,
            if response.hovered() {
                egui::Stroke::new(1.0, palette::ACCENT_OUTLINE)
            } else {
                theme::overlay_outline(alpha)
            },
            StrokeKind::Inside,
        );
        let thumb = Rect::from_min_size(card.min + vec2(8.0, 8.0), vec2(78.0, 48.0));
        crate::screens::paint_poster_thumb(ui, thumb, &next, &mut self.posters, 6.0);
        let text_clip = ui.painter().with_clip_rect(card.shrink(8.0));
        text_clip.text(
            pos2(thumb.right() + 10.0, card.center().y),
            Align2::LEFT_CENTER,
            label,
            typography::caption(),
            theme::text_primary_faded(alpha),
        );
        if response.hovered() {
            ui.ctx().set_cursor_icon(egui::CursorIcon::PointingHand);
        }
        if response.clicked() {
            self.play_adjacent_episode(1);
        }
    }
    /// Opens the match picker for `media_id` and requests its candidates.
    /// Opens the manual match picker (see `show_match_picker_overlay`) for
    /// `media_id`, requesting its dandanplay candidates. Triggered from the
    /// library (`LibraryAction::ChangeMatch`), not from playback, so the
    /// item does not need to be actively playing.
    pub(super) fn open_match_picker(&mut self, media_id: String) {
        if let Some(session) = &self.session {
            session.fetch_dandanplay_candidates(media_id.clone());
        }
        self.match_picker = MatchPickerState {
            open: true,
            media_id: Some(media_id),
            loading: true,
            ..MatchPickerState::default()
        };
    }

    /// Floating window with every dandanplay candidate for the item the
    /// match picker is open for. Rendered on top of whichever screen is
    /// active; only `open_match_picker` (from the library) sets it open.
    /// Fires a selection request for a picked episode and marks it pending.
    pub(super) fn request_match_selection(
        &mut self,
        selection: crate::danmaku::DandanplaySelection,
    ) {
        let Some(media_id) = self.match_picker.media_id.clone() else {
            return;
        };
        let Some(session) = &self.session else {
            return;
        };
        self.match_picker.selecting_episode_id = Some(selection.episode_id);
        self.match_picker.selecting_anime_title = selection.anime_title.clone();
        session.select_dandanplay_match(media_id, selection);
    }

    pub(super) fn show_match_picker_overlay(&mut self, ctx: &egui::Context) {
        if !self.match_picker.open {
            return;
        }
        let strings = Strings::new(self.preferences.language);
        let media_context = self
            .match_picker
            .media_id
            .as_deref()
            .and_then(|media_id| self.session.as_ref()?.catalog.as_ref()?.item(media_id))
            .map(|item| format!("{}  ·  {}", item.series_title, item.episode_title));
        let mut still_open = true;
        let mut pick: Option<crate::danmaku::DandanplaySelection> = None;
        let mut search_requested = false;

        egui::Window::new(strings.change_match())
            .open(&mut still_open)
            .collapsible(false)
            .resizable(true)
            .anchor(Align2::CENTER_CENTER, vec2(0.0, 0.0))
            .default_size(vec2(680.0, 620.0))
            .min_width(560.0)
            .frame(
                Frame::NONE
                    .fill(palette::SURFACE)
                    .corner_radius(egui::CornerRadius::same(14))
                    .inner_margin(egui::Margin::symmetric(20, 18))
                    .stroke(egui::Stroke::new(1.0, Color32::from_white_alpha(24))),
            )
            .show(ctx, |ui| {
                let selecting = self.match_picker.selecting_episode_id;
                ui.label(
                    RichText::new(media_context.as_deref().unwrap_or(strings.change_match()))
                        .font(typography::title())
                        .strong()
                        .color(palette::TEXT_PRIMARY),
                );
                ui.label(
                    RichText::new(strings.match_picker_hint())
                        .font(typography::caption())
                        .color(palette::TEXT_MUTED),
                );
                ui.add_space(12.0);

                if let Some(error) = &self.match_picker.error {
                    Frame::NONE
                        .fill(Color32::from_rgba_unmultiplied(92, 28, 32, 180))
                        .corner_radius(egui::CornerRadius::same(9))
                        .inner_margin(egui::Margin::symmetric(12, 9))
                        .show(ui, |ui| {
                            ui.colored_label(palette::DANGER, error.as_str());
                        });
                    ui.add_space(10.0);
                }

                Frame::NONE
                    .fill(palette::SURFACE_RAISED)
                    .corner_radius(egui::CornerRadius::same(12))
                    .inner_margin(egui::Margin::symmetric(14, 12))
                    .stroke(egui::Stroke::new(1.0, Color32::from_white_alpha(16)))
                    .show(ui, |ui| {
                        ui.set_width(ui.available_width());
                        ui.label(
                            RichText::new(strings.file_suggestions())
                                .font(typography::heading())
                                .strong()
                                .color(palette::TEXT_PRIMARY),
                        );
                        ui.label(
                            RichText::new(strings.hash_matches())
                                .font(typography::small())
                                .color(palette::TEXT_MUTED),
                        );
                        ui.add_space(8.0);

                        if self.match_picker.loading {
                            ui.horizontal(|ui| {
                                ui.spinner();
                                ui.label(strings.loading_matches());
                            });
                        } else if self.match_picker.candidates.is_empty() {
                            ui.label(
                                RichText::new(strings.no_matches_found())
                                    .font(typography::caption())
                                    .color(palette::TEXT_MUTED),
                            );
                        } else {
                            egui::ScrollArea::vertical()
                                .id_salt("picker-file-suggestions")
                                .max_height(160.0)
                                .show(ui, |ui| {
                                    for candidate in self.match_picker.candidates.clone() {
                                        let pending = selecting == Some(candidate.episode_id);
                                        Frame::NONE
                                            .fill(palette::SURFACE_FAINT)
                                            .corner_radius(egui::CornerRadius::same(9))
                                            .inner_margin(egui::Margin::symmetric(11, 8))
                                            .show(ui, |ui| {
                                                ui.set_width(ui.available_width());
                                                ui.horizontal(|ui| {
                                                    ui.vertical(|ui| {
                                                        ui.set_width(
                                                            (ui.available_width() - 110.0)
                                                                .max(240.0),
                                                        );
                                                        ui.label(
                                                            RichText::new(
                                                                candidate
                                                                    .anime_title
                                                                    .as_deref()
                                                                    .unwrap_or(
                                                                        &candidate.display_title,
                                                                    ),
                                                            )
                                                            .font(typography::body())
                                                            .strong()
                                                            .color(palette::TEXT_PRIMARY),
                                                        );
                                                        ui.label(
                                                            RichText::new(
                                                                candidate
                                                                    .episode_title
                                                                    .as_deref()
                                                                    .unwrap_or(
                                                                        &candidate.display_title,
                                                                    ),
                                                            )
                                                            .font(typography::caption())
                                                            .color(palette::TEXT_MUTED),
                                                        );
                                                    });
                                                    let label = if pending {
                                                        format!("{} …", strings.select_match())
                                                    } else {
                                                        strings.select_match().to_owned()
                                                    };
                                                    if ui
                                                        .add_enabled(
                                                            selecting.is_none(),
                                                            egui::Button::new(label),
                                                        )
                                                        .clicked()
                                                    {
                                                        pick = Some(
                                                            crate::danmaku::DandanplaySelection {
                                                                episode_id: candidate.episode_id,
                                                                anime_id: None,
                                                                anime_title: candidate
                                                                    .anime_title
                                                                    .clone(),
                                                                episode_title: candidate
                                                                    .episode_title
                                                                    .clone(),
                                                            },
                                                        );
                                                    }
                                                });
                                            });
                                        ui.add_space(6.0);
                                    }
                                });
                        }
                    });

                ui.add_space(12.0);

                Frame::NONE
                    .fill(palette::SURFACE_RAISED)
                    .corner_radius(egui::CornerRadius::same(12))
                    .inner_margin(egui::Margin::symmetric(14, 12))
                    .stroke(egui::Stroke::new(1.0, Color32::from_white_alpha(16)))
                    .show(ui, |ui| {
                        ui.set_width(ui.available_width());
                        ui.label(
                            RichText::new(strings.database_search())
                                .font(typography::heading())
                                .strong()
                                .color(palette::TEXT_PRIMARY),
                        );
                        ui.label(
                            RichText::new(strings.search_dandanplay())
                                .font(typography::small())
                                .color(palette::TEXT_MUTED),
                        );
                        ui.add_space(8.0);
                        ui.horizontal(|ui| {
                            let field_width = (ui.available_width() - 92.0).max(260.0);
                            let field = ui.add(
                                egui::TextEdit::singleline(&mut self.match_picker.search_query)
                                    .hint_text(strings.search_dandanplay_hint())
                                    .desired_width(field_width),
                            );
                            let submitted = field.lost_focus()
                                && ui.input(|input| input.key_pressed(egui::Key::Enter));
                            if (ui.button(strings.search()).clicked() || submitted)
                                && !self.match_picker.search_query.trim().is_empty()
                            {
                                search_requested = true;
                            }
                        });

                        if self.match_picker.searching {
                            ui.horizontal(|ui| {
                                ui.spinner();
                                ui.label(strings.loading_matches());
                            });
                        } else if self.match_picker.searched
                            && self.match_picker.search_results.is_empty()
                        {
                            ui.label(
                                RichText::new(strings.no_matches_found())
                                    .font(typography::caption())
                                    .color(palette::TEXT_MUTED),
                            );
                        }

                        let results = self.match_picker.search_results.clone();
                        if !results.is_empty() {
                            ui.add_space(8.0);
                            egui::ScrollArea::vertical()
                                .id_salt("picker-database-results")
                                .max_height(280.0)
                                .show(ui, |ui| {
                                    for anime in &results {
                                        let expanded = self.match_picker.expanded_anime_id
                                            == Some(anime.anime_id);
                                        let metadata = match &anime.type_description {
                                            Some(kind) => format!(
                                                "{kind}  ·  {} {}",
                                                anime.episodes.len(),
                                                strings.episodes()
                                            ),
                                            None => format!(
                                                "{} {}",
                                                anime.episodes.len(),
                                                strings.episodes()
                                            ),
                                        };
                                        let heading = format!(
                                            "{}  {}\n     {}",
                                            if expanded { "▾" } else { "▸" },
                                            anime.anime_title,
                                            metadata
                                        );
                                        let response = ui.add_sized(
                                            [ui.available_width(), 52.0],
                                            egui::Button::new(
                                                RichText::new(heading).font(typography::body()),
                                            )
                                            .selected(expanded),
                                        );
                                        if response.clicked() {
                                            self.match_picker.expanded_anime_id =
                                                (!expanded).then_some(anime.anime_id);
                                        }
                                        if expanded {
                                            ui.indent(("picker-episodes", anime.anime_id), |ui| {
                                                for episode in &anime.episodes {
                                                    let pending =
                                                        selecting == Some(episode.episode_id);
                                                    let label = if pending {
                                                        format!("{} …", episode.episode_title)
                                                    } else {
                                                        episode.episode_title.clone()
                                                    };
                                                    if ui
                                                        .add_enabled(
                                                            selecting.is_none(),
                                                            egui::Button::new(label).min_size(
                                                                vec2(ui.available_width(), 34.0),
                                                            ),
                                                        )
                                                        .clicked()
                                                    {
                                                        pick = Some(
                                                            crate::danmaku::DandanplaySelection {
                                                                episode_id: episode.episode_id,
                                                                anime_id: Some(anime.anime_id),
                                                                anime_title: Some(
                                                                    anime.anime_title.clone(),
                                                                ),
                                                                episode_title: Some(
                                                                    episode.episode_title.clone(),
                                                                ),
                                                            },
                                                        );
                                                    }
                                                }
                                            });
                                        }
                                        ui.add_space(6.0);
                                    }
                                });
                        }
                    });
            });

        if search_requested
            && let (Some(media_id), Some(session)) =
                (self.match_picker.media_id.clone(), &self.session)
        {
            self.match_picker.searching = true;
            self.match_picker.searched = false;
            self.match_picker.error = None;
            session.search_dandanplay(media_id, self.match_picker.search_query.trim().to_owned());
        }
        if let Some(selection) = pick {
            self.request_match_selection(selection);
        }
        if !still_open {
            self.match_picker = MatchPickerState::default();
        }
    }

    pub(super) fn show_danmaku_menu(&mut self, ui: &mut egui::Ui, active: usize) {
        let strings = Strings::new(self.preferences.language);
        let status = match self.danmaku.kind {
            DanmakuLoadKind::Ass => "ASS".to_owned(),
            DanmakuLoadKind::Failed => "!".to_owned(),
            _ if !self.danmaku_settings.enabled => strings.off().to_owned(),
            _ => active.to_string(),
        };
        let response = danmaku_pill_button(
            ui,
            strings.danmaku_label(),
            self.danmaku_settings.enabled && self.danmaku.kind != DanmakuLoadKind::Failed,
        )
        .on_hover_text(format!("{}: {status}", strings.danmaku_label()));
        egui::Popup::menu(&response)
            .frame(Frame::popup(ui.style()).fill(Color32::from_rgba_unmultiplied(24, 28, 34, 200)))
            .show(|ui| {
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
                    ui.label(strings.danmaku_types());
                    ui.horizontal_wrapped(|ui| {
                        ui.checkbox(
                            &mut self.danmaku_settings.show_scrolling,
                            strings.scrolling_danmaku(),
                        );
                        ui.checkbox(&mut self.danmaku_settings.show_top, strings.top_danmaku());
                        ui.checkbox(
                            &mut self.danmaku_settings.show_bottom,
                            strings.bottom_danmaku(),
                        );
                    });
                    ui.separator();
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

    pub(super) fn show_track_menus(&mut self, ui: &mut egui::Ui) {
        let strings = Strings::new(self.preferences.language);
        let audio_description = self
            .tracks
            .selected_audio()
            .map(|track| format!("{}: {}", strings.audio(), track.label()))
            .unwrap_or_else(|| strings.audio().to_owned());
        let audio_tracks = self.tracks.audio.clone();
        let audio_response = playback_icon_button(ui, Icon::AudioTrack, &audio_description, false);
        egui::Popup::menu(&audio_response).show(|ui| {
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

        let subtitle_description = self
            .tracks
            .selected_subtitle()
            .map(|track| format!("{}: {}", strings.subtitles(), track.label()))
            .unwrap_or_else(|| strings.subtitles_off().to_owned());
        let subtitle_tracks = self.tracks.subtitles.clone();
        let subtitle_response =
            playback_icon_button(ui, Icon::Subtitles, &subtitle_description, false);
        egui::Popup::menu(&subtitle_response).show(|ui| {
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
}
