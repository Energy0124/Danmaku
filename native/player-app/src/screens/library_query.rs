use std::collections::BTreeMap;

use crate::{
    library::{
        AttentionCacheStatus, AttentionIssueCode, AttentionMappingStatus, AttentionRepairRequest,
        LibraryAttentionDocument, MINIMUM_REMAINING_MS, MINIMUM_RESUME_POSITION_MS,
        PlaybackProgress, Series, item_in_folder_shortcut,
    },
    localization::{Language, Strings},
};

use super::library_screen::{LibraryMatchFilter, LibraryProgressFilter, LibrarySeriesSort};

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
pub(super) fn progress_is_completed(progress: &PlaybackProgress) -> bool {
    progress.duration_ms.is_some_and(|duration| {
        duration > 0 && duration - progress.position_ms < MINIMUM_REMAINING_MS
    })
}

pub(super) fn series_progress_state(
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
pub(super) fn filtered_library_series(
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

pub(super) fn series_attention_count(
    series: &Series,
    attention: Option<&LibraryAttentionDocument>,
) -> usize {
    let Some(attention) = attention else { return 0 };
    series
        .items()
        .filter_map(|item| attention.item(&item.id))
        .filter(|item| item.needs_attention())
        .count()
}

pub(super) fn series_attention_badge(
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

pub(super) fn series_attention_repairs(
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

pub(super) fn series_release_year(series: &Series) -> Option<i32> {
    series
        .items()
        .filter_map(|item| item.anime_metadata.as_ref()?.start_year)
        .next()
}

pub(super) fn recent_series_groups(
    series: &[Series],
    strings: Strings,
) -> Vec<(String, Vec<Series>)> {
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
pub(super) fn series_latest_played_at(
    series: &Series,
    progresses: &[PlaybackProgress],
) -> Option<i64> {
    progresses
        .iter()
        .filter(|progress| series.items().any(|item| item.id == progress.media_id))
        .map(|progress| progress.updated_at_epoch_ms)
        .max()
}

/// Groups an already recency-sorted list by the month each series was
/// last played, preserving the incoming order inside every group.
pub(super) fn recently_played_groups(
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

pub(super) fn season_series_groups(
    series: &[Series],
    strings: Strings,
) -> Vec<(String, Vec<Series>)> {
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

pub(super) fn year_month_from_epoch_ms(epoch_ms: i64) -> Option<(i32, u32)> {
    civil_date_from_epoch_ms(epoch_ms).map(|(year, month, _)| (year, month))
}

/// `2026/07/13`-style date for compact row captions.
pub(super) fn short_date_from_epoch_ms(epoch_ms: i64) -> Option<String> {
    civil_date_from_epoch_ms(epoch_ms)
        .map(|(year, month, day)| format!("{year}/{month:02}/{day:02}"))
}
