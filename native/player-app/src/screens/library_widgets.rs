use super::*;

pub(super) fn sidebar_heading(ui: &mut egui::Ui, label: &str) {
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
pub(super) fn filter_dropdown(
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
pub(super) fn filter_toggle_chip(ui: &mut egui::Ui, label: &str, on: &mut bool) -> bool {
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
pub(super) fn toolbar_chip_button(ui: &mut egui::Ui, label: &str) -> egui::Response {
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

pub(super) fn nav_button(
    ui: &mut egui::Ui,
    icon: Icon,
    label: &str,
    selected: bool,
) -> egui::Response {
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

pub(super) fn folder_nav_button(
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

pub(super) fn labeled_icon_button(
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
pub(super) fn online_pill(ui: &mut egui::Ui, label: &str, server_url: &str) {
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
pub(super) fn icon_chip_button(ui: &mut egui::Ui, icon: Icon, tooltip: &str) -> egui::Response {
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

pub(super) fn featured_media_card(
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

pub(super) fn section_heading(ui: &mut egui::Ui, text: &str) {
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

pub(super) fn section_subheading(ui: &mut egui::Ui, text: &str) {
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

pub(super) fn muted_line(ui: &mut egui::Ui, text: &str) {
    ui.horizontal(|ui| {
        ui.add_space(PAGE_GUTTER);
        ui.label(
            RichText::new(text)
                .font(typography::caption())
                .color(palette::TEXT_MUTED),
        );
    });
}

pub(super) fn series_fact(ui: &mut egui::Ui, label: &str, value: &str) {
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
pub(super) fn rating_chip(ui: &mut egui::Ui, rating: f64) {
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
pub(super) fn info_chip(ui: &mut egui::Ui, text: &str, color: Color32) {
    Frame::NONE
        .fill(palette::SURFACE_FAINT)
        .corner_radius(egui::CornerRadius::same(8))
        .inner_margin(egui::Margin::symmetric(10, 6))
        .show(ui, |ui| {
            ui.label(RichText::new(text).font(typography::caption()).color(color));
        });
}

pub(super) fn continue_watching_rail(
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

pub(super) fn next_up_rail(
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
pub(super) fn series_rail(
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
