use eframe::egui::{self, Align2, Color32, CursorIcon, FontId, Rect, Sense, pos2, vec2};

use crate::{
    library::{LibraryAttentionDocument, MediaItem, Series},
    localization::Strings,
    posters::{PosterCache, PosterState},
    theme::{self, metrics, palette, typography},
};

use super::{
    CARD_GAP, CARD_HEIGHT, CARD_WIDTH, PAGE_GUTTER, library_query::series_attention_badge,
    library_screen::LibraryGridDensity,
};

pub(super) fn series_grid(
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

pub(super) fn poster_card(
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

pub(super) fn poster_card_with_title(
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

pub(super) fn poster_card_with_title_sized(
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
pub(super) fn poster_thumbnail(
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
pub(super) fn paint_poster_rounded(
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
pub(super) fn mask_rounded_corners(ui: &egui::Ui, rect: Rect, radius: f32, background: Color32) {
    ui.painter().rect_stroke(
        rect,
        radius,
        egui::Stroke::new(radius, background),
        egui::StrokeKind::Outside,
    );
}

pub(super) fn paint_poster_area(
    ui: &egui::Ui,
    rect: Rect,
    item: &MediaItem,
    posters: &mut PosterCache,
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
