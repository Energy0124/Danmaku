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

mod library_screen;
pub use library_screen::{BangumiDetailState, LibraryAction, LibraryScreen};
use library_screen::{
    LibraryGridDensity, LibraryMatchFilter, LibraryProgressFilter, LibrarySeriesSort,
};
mod library_widgets;
use library_widgets::*;
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
