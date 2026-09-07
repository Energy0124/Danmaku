//! Library-mode screens: server connect and catalog browse.

use eframe::egui::{self, Rect};

use crate::theme::palette;

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
mod library_query;
mod library_widgets;
mod poster_components;
pub(crate) use poster_components::paint_poster_thumb;
mod library_rows;

mod settings;
pub use settings::{SettingsAction, UpdatePromptAction, show_settings, show_update_prompt};
#[cfg(test)]
mod tests {
    use super::{
        library_query::{
            filtered_library_series, series_attention_count, series_attention_repairs,
            series_progress_state, year_month_from_epoch_ms,
        },
        library_rows::format_size,
        library_screen::{LibraryMatchFilter, LibraryProgressFilter, LibrarySeriesSort},
        poster_components::initials,
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
