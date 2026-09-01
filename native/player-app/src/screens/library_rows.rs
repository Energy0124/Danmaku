use eframe::egui::{self, Align2, Color32, CursorIcon, Rect, Sense, pos2, vec2};

use crate::{
    icons::{Icon, paint_icon},
    library::{
        AttentionCacheStatus, AttentionIssueCode, AttentionMappingStatus, MediaItem,
        PlaybackProgress, file_name,
    },
    localization::Strings,
    theme::{metrics, palette, typography},
};

use super::library_query::{progress_is_completed, short_date_from_epoch_ms};

pub(super) struct EpisodeRowAction {
    pub(super) play_clicked: bool,
    pub(super) change_match_clicked: bool,
}

/// Renders one episode row with two independent click targets: the row body
/// (play) and a small trailing icon button (open the manual match picker —
/// see `LibraryAction::ChangeMatch`). Interact regions are carved out of the
/// row explicitly (rather than nesting widgets) so clicking the icon can
/// never also register as clicking play.
pub(super) fn episode_row(
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
pub(super) fn explorer_folder_row(
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
pub(super) fn explorer_file_row(
    ui: &mut egui::Ui,
    item: &MediaItem,
    strings: Strings,
) -> EpisodeRowAction {
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
pub(super) fn format_size(size_bytes: i64) -> String {
    const MB: f64 = 1024.0 * 1024.0;
    const GB: f64 = MB * 1024.0;
    let size = size_bytes.max(0) as f64;
    if size >= GB {
        format!("{:.2}GB", size / GB)
    } else {
        format!("{:.1}MB", size / MB)
    }
}
