use super::*;

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum SettingsAction {
    Back,
    OpenTracking,
    ChangeServer,
    RestartLocalServer,
    StopLocalServer,
    SetLocalRoot(PathBuf),
    AddLibraryFolder(PathBuf),
    RemoveLibraryFolder(String),
    SaveDandanplayCredentials,
    ClearDandanplayCredentials,
    CheckForUpdates,
    UpdateAndRestart,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum UpdatePromptAction {
    Dismiss,
    UpdateAndRestart,
}

pub fn show_settings(
    ctx: &egui::Context,
    preferences: &mut PlayerPreferences,
    connected_url: Option<&str>,
    return_to_playback: bool,
    local_host_status: Option<&LocalHostStatus>,
    local_roots: &[String],
    dandanplay: &mut DandanplayCredentials,
    update_status: &UpdateStatus,
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
                                update_status,
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
    update_status: &UpdateStatus,
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
                LocalHostStatus::Running {
                    base_url,
                    ownership,
                } => format!(
                    "{}: {base_url}",
                    match ownership {
                        LocalHostOwnership::PlayerOwned => strings.managed_by_player(),
                        LocalHostOwnership::BackgroundHost => strings.managed_by_background_host(),
                        LocalHostOwnership::External => strings.attached_server(),
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
            if status.is_background_running() {
                ui.add_space(8.0);
                card_status_line(ui, strings.background_host_note());
            }
            if !return_to_playback && status.is_available() && status.allows_player_management() {
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
                    if status.is_player_owned_running()
                        && settings_pill_button(ui, strings.restart_server(), false).clicked()
                    {
                        *action = Some(SettingsAction::RestartLocalServer);
                    }
                    if status.is_player_owned_running()
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
            let can_manage = status.allows_player_management();
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
            ui.add_enabled_ui(can_manage, |ui| {
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
            if !can_manage {
                card_status_line(ui, strings.server_managed_settings_note());
            }
        });
    }

    // Server administration.
    if let Some(server) = server.as_deref() {
        ui.add_space(14.0);
        settings_card(ui, strings.accounts_tracking(), |ui| {
            card_status_line(ui, strings.accounts_tracking_note());
            ui.add_space(4.0);
            if settings_pill_button(ui, strings.open_accounts_tracking(), false).clicked() {
                *action = Some(SettingsAction::OpenTracking);
            }
        });
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

    ui.add_space(14.0);
    settings_card(ui, strings.application_updates(), |ui| {
        card_status_line(
            ui,
            &format!(
                "{}: {}",
                strings.current_version(),
                update_status.current_version()
            ),
        );
        ui.add_space(6.0);
        match update_status {
            UpdateStatus::Unavailable { .. } => {
                card_status_line(ui, strings.updates_unavailable_portable());
                #[cfg(windows)]
                ui.hyperlink_to(strings.download_latest_installer(), LATEST_INSTALLER_URL);
            }
            UpdateStatus::Idle { .. } => {
                card_status_line(ui, strings.up_to_date());
                if settings_pill_button(ui, strings.check_for_updates(), false).clicked() {
                    *action = Some(SettingsAction::CheckForUpdates);
                }
            }
            UpdateStatus::Checking { .. } => {
                ui.horizontal(|ui| {
                    ui.add(egui::Spinner::new().size(14.0));
                    card_status_line(ui, strings.checking_for_updates());
                });
            }
            UpdateStatus::Available { version, .. } => {
                card_status_line(ui, &strings.update_available(version));
                if settings_pill_button(ui, strings.update_and_restart(), true).clicked() {
                    *action = Some(SettingsAction::UpdateAndRestart);
                }
            }
            UpdateStatus::Downloading {
                version, progress, ..
            } => {
                card_status_line(ui, &strings.downloading_update(version, *progress));
                ui.add(egui::ProgressBar::new(*progress as f32 / 100.0).show_percentage());
            }
            UpdateStatus::Ready { version, .. } => {
                card_status_line(ui, &strings.update_ready(version));
                if settings_pill_button(ui, strings.update_and_restart(), true).clicked() {
                    *action = Some(SettingsAction::UpdateAndRestart);
                }
            }
            UpdateStatus::Failed { message, .. } => {
                ui.label(
                    RichText::new(strings.update_failed(message))
                        .font(typography::caption())
                        .color(palette::DANGER),
                );
                if settings_pill_button(ui, strings.retry(), false).clicked() {
                    *action = Some(SettingsAction::CheckForUpdates);
                }
            }
        }
    });

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

pub fn show_update_prompt(
    ctx: &egui::Context,
    status: &UpdateStatus,
    language: Language,
) -> Option<UpdatePromptAction> {
    let strings = Strings::new(language);
    let (version, notes, ready) = match status {
        UpdateStatus::Available { version, notes, .. } => (version.as_str(), notes.as_str(), false),
        UpdateStatus::Ready { version, .. } => (version.as_str(), "", true),
        _ => return None,
    };
    let mut action = None;
    egui::Window::new(strings.update_available_title())
        .id(egui::Id::new("application_update_prompt"))
        .collapsible(false)
        .resizable(false)
        .anchor(Align2::CENTER_CENTER, [0.0, 0.0])
        .fixed_size([500.0, 330.0])
        .show(ctx, |ui| {
            ui.label(
                RichText::new(strings.update_available(version))
                    .font(typography::title())
                    .strong(),
            );
            ui.add_space(10.0);
            if ready {
                card_status_line(ui, strings.update_downloaded());
            } else if notes.trim().is_empty() {
                card_status_line(ui, strings.no_release_notes());
            } else {
                ui.label(RichText::new(strings.release_notes()).strong());
                egui::ScrollArea::vertical()
                    .max_height(190.0)
                    .show(ui, |ui| {
                        ui.label(notes);
                    });
            }
            ui.add_space(14.0);
            ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                if settings_pill_button(ui, strings.update_and_restart(), true).clicked() {
                    action = Some(UpdatePromptAction::UpdateAndRestart);
                }
                if settings_pill_button(ui, strings.not_now(), false).clicked() {
                    action = Some(UpdatePromptAction::Dismiss);
                }
            });
        });
    action
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
    let engaged = response.hovered() || response.has_focus() || response.dragged();
    painter.circle_filled(
        pos2(track.left() + track.width() * fraction, track.center().y),
        if engaged { 9.0 } else { 7.0 },
        Color32::WHITE,
    );
    paint_focus_outline(ui, rect, 8.0, &response);
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
        paint_focus_outline(ui, rect, rect.height() / 2.0, &response);
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
        paint_focus_outline(ui, segment, 7.0, &response);
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
        if response.hovered() || response.has_focus() {
            palette::ACCENT_OUTLINE
        } else {
            palette::ACCENT_BRIGHT
        }
    } else if response.hovered() || response.has_focus() {
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
    paint_focus_outline(ui, rect, 9.0, &response);
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
