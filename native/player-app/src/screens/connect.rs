use super::*;

#[derive(Clone, Debug, PartialEq)]
pub struct ConnectRequest {
    pub base_url: String,
}

#[derive(Clone, Debug, PartialEq)]
pub enum ConnectAction {
    Connect(ConnectRequest),
    StartLocal(PathBuf),
}

#[derive(Default)]
pub struct ConnectScreen {
    pub manual_url: String,
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
        qa_primary_state: Option<&str>,
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
                                        qa_primary_state,
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
    if matches!(status, LocalHostStatus::Starting) {
        ui.put(
            Rect::from_center_size(pos2(rect.right() - 28.0, rect.center().y), vec2(20.0, 20.0)),
            egui::Spinner::new()
                .size(18.0)
                .color(palette::ACCENT_BRIGHT),
        );
        ui.ctx()
            .request_repaint_after(std::time::Duration::from_millis(100));
    }

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
        paint_focus_outline(ui, rect, 6.0, &response);
        let color = if selected {
            palette::ACCENT_OUTLINE
        } else if response.hovered() || response.has_focus() {
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
    let color = if response.hovered() || response.has_focus() {
        theme::mix(palette::ACCENT_OUTLINE, Color32::WHITE, 0.35)
    } else {
        palette::ACCENT_OUTLINE
    };
    paint_focus_outline(ui, rect, 6.0, &response);
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
        let enabled = screen.manual_url.trim().starts_with("http://");
        if ui
            .add_enabled(enabled, egui::Button::new(strings.connect()))
            .clicked()
        {
            *action = Some(ConnectAction::Connect(ConnectRequest {
                base_url: screen.manual_url.trim().to_owned(),
            }));
        }
    });
}
