//! Pure naming rules for review groups: titles, seasons, group keys and ordering.
use std::cmp::Ordering;

use super::normalize_key;

/// Infers a series title from a release file name, returning `None` when the
/// name carries no episode marker (for example `random clip` or `Episode 05`).
pub(super) fn title_from_file_stem(stem: &str) -> Option<String> {
    let stem = stem.trim();
    let inferred = crate::scanner::infer_root_file_series_title(stem);
    if !inferred.is_empty() && inferred != stem {
        return Some(inferred);
    }
    // The scanner does not treat bare `E1` / `S01E01` tokens as episode markers.
    let lower = stem.to_lowercase();
    let mut previous: Option<char> = None;
    for (index, character) in lower.char_indices() {
        let boundary = previous.is_none_or(|c| !c.is_alphanumeric());
        previous = Some(character);
        if !boundary || !matches!(character, 's' | 'e') {
            continue;
        }
        if episode_token(&lower[index..]) {
            let title = stem[..index]
                .trim()
                .trim_end_matches(['-', '_', '.', ' '])
                .trim();
            return (!title.is_empty()).then(|| title.to_owned());
        }
    }
    None
}

/// `e12`, `s01e12` (optionally followed by a version) up to a word boundary.
fn episode_token(value: &str) -> bool {
    let mut rest = value;
    if let Some(after) = rest.strip_prefix('s') {
        let digits = after.chars().take_while(char::is_ascii_digit).count();
        if digits == 0 || digits > 2 {
            return false;
        }
        rest = &after[digits..];
    }
    let Some(after) = rest.strip_prefix('e') else {
        return false;
    };
    let digits = after.chars().take_while(char::is_ascii_digit).count();
    if digits == 0 || digits > 4 {
        return false;
    }
    let mut tail = &after[digits..];
    if let Some(version) = tail.strip_prefix('v') {
        tail = version.trim_start_matches(|c: char| c.is_ascii_digit());
    }
    tail.chars().next().is_none_or(|c| !c.is_alphanumeric())
}

/// Splits a trailing season marker from a series title:
/// `药屋少女的呢喃 第二季` → (`药屋少女的呢喃`, 2), `Show S2` → (`Show`, 2).
pub(super) fn split_season_suffix(title: &str) -> (String, Option<u32>) {
    let trimmed = title.trim().trim_end_matches(['-', '_', ':', '：', ' ']);
    if let Some(season) = cjk_season_suffix(trimmed) {
        return season;
    }
    let words = trimmed.split_whitespace().collect::<Vec<_>>();
    let base = |count: usize| -> String {
        let end = words.len() - count;
        words[..end]
            .join(" ")
            .trim_end_matches(['-', '_', ':', '：', ' '])
            .to_owned()
    };
    let lower = words
        .iter()
        .map(|word| word.to_lowercase())
        .collect::<Vec<_>>();
    let found = match lower.as_slice() {
        [.., season, number] if season == "season" => number.parse().ok().map(|n| (2, n)),
        [.., ordinal, season] if season == "season" => ordinal_number(ordinal).map(|n| (2, n)),
        [.., last] => last
            .strip_prefix('s')
            .filter(|digits| (1..=2).contains(&digits.len()))
            .and_then(|digits| digits.parse().ok())
            .map(|n| (1, n)),
        [] => None,
    };
    match found {
        Some((count, number)) if words.len() > count => {
            let base = base(count);
            if base.is_empty() {
                (title.trim().to_owned(), None)
            } else {
                (base, Some(number))
            }
        }
        _ => (title.trim().to_owned(), None),
    }
}

fn ordinal_number(value: &str) -> Option<u32> {
    let digits = value.trim_end_matches(|c: char| c.is_ascii_alphabetic());
    let suffix = &value[digits.len()..];
    matches!(suffix, "st" | "nd" | "rd" | "th")
        .then(|| digits.parse().ok())
        .flatten()
}

fn cjk_season_suffix(title: &str) -> Option<(String, Option<u32>)> {
    let without = title
        .strip_suffix('季')
        .or_else(|| title.strip_suffix('期'))?;
    let start = without.rfind('第')?;
    let number = parse_cjk_number(&without[start + '第'.len_utf8()..])?;
    let base = without[..start]
        .trim()
        .trim_end_matches(['-', '_', ':', '：', ' '])
        .to_owned();
    (!base.is_empty()).then_some((base, Some(number)))
}

fn parse_cjk_number(value: &str) -> Option<u32> {
    if let Ok(number) = value.trim().parse() {
        return Some(number);
    }
    let digit = |c: char| "零一二三四五六七八九".chars().position(|d| d == c);
    let chars = value.trim().chars().collect::<Vec<_>>();
    match chars.as_slice() {
        [single] if *single == '十' => Some(10),
        [single] => digit(*single).map(|n| n as u32),
        ['十', ones] => digit(*ones).map(|n| 10 + n as u32),
        [tens, '十'] => digit(*tens).map(|n| n as u32 * 10),
        [tens, '十', ones] => Some(digit(*tens)? as u32 * 10 + digit(*ones)? as u32),
        _ => None,
    }
    .filter(|number| *number > 0)
}

/// Stable grouping key: titles that differ only by season marker,
/// punctuation or case belong to one series folder.
pub(super) fn series_key(title: &str) -> String {
    let key = normalize_key(&split_season_suffix(title).0);
    if key.is_empty() {
        "untitled".into()
    } else {
        key
    }
}

/// Human ordering for paths and titles: `Show - 2` sorts before `Show - 10`.
pub(super) fn natural_cmp(left: &str, right: &str) -> Ordering {
    natural_cmp_folded(left, right).then_with(|| left.cmp(right))
}

fn natural_cmp_folded(left: &str, right: &str) -> Ordering {
    let mut left = left.chars().peekable();
    let mut right = right.chars().peekable();
    loop {
        match (left.peek().copied(), right.peek().copied()) {
            (None, None) => return Ordering::Equal,
            (None, Some(_)) => return Ordering::Less,
            (Some(_), None) => return Ordering::Greater,
            (Some(a), Some(b)) if a.is_ascii_digit() && b.is_ascii_digit() => {
                let take = |chars: &mut std::iter::Peekable<std::str::Chars>| {
                    let mut digits = String::new();
                    while let Some(c) = chars.peek().copied().filter(char::is_ascii_digit) {
                        digits.push(c);
                        chars.next();
                    }
                    digits
                };
                let (a, b) = (take(&mut left), take(&mut right));
                let (a_trim, b_trim) = (a.trim_start_matches('0'), b.trim_start_matches('0'));
                let ordering = a_trim
                    .len()
                    .cmp(&b_trim.len())
                    .then_with(|| a_trim.cmp(b_trim))
                    .then_with(|| a.len().cmp(&b.len()));
                if ordering != Ordering::Equal {
                    return ordering;
                }
            }
            (Some(a), Some(b)) => {
                let ordering = a.to_lowercase().cmp(b.to_lowercase());
                if ordering != Ordering::Equal {
                    return ordering;
                }
                left.next();
                right.next();
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn file_titles_need_an_episode_marker() {
        assert_eq!(
            title_from_file_stem("[SubsPlease] Sousou no Frieren - 01 (1080p)").as_deref(),
            Some("Sousou no Frieren")
        );
        assert_eq!(
            title_from_file_stem("Kusuriya no Hitorigoto S2 - E10").as_deref(),
            Some("Kusuriya no Hitorigoto S2")
        );
        assert_eq!(
            title_from_file_stem("Spy x Family - S01E03").as_deref(),
            Some("Spy x Family")
        );
        assert_eq!(title_from_file_stem("random clip"), None);
        assert_eq!(title_from_file_stem("Episode 05"), None);
        assert_eq!(title_from_file_stem("Sesame"), None);
    }

    #[test]
    fn season_suffixes_are_split_from_series_titles() {
        let split = |t: &str| split_season_suffix(t);
        assert_eq!(
            split("药屋少女的呢喃 第二季"),
            ("药屋少女的呢喃".into(), Some(2))
        );
        assert_eq!(split("Show 第十二期"), ("Show".into(), Some(12)));
        assert_eq!(
            split("Kusuriya no Hitorigoto S2"),
            ("Kusuriya no Hitorigoto".into(), Some(2))
        );
        assert_eq!(split("Show Season 3"), ("Show".into(), Some(3)));
        assert_eq!(split("Show 2nd Season"), ("Show".into(), Some(2)));
        assert_eq!(split("Show: Season 2"), ("Show".into(), Some(2)));
        assert_eq!(split("Frieren"), ("Frieren".into(), None));
        assert_eq!(split("S2"), ("S2".into(), None));
        assert_eq!(
            split("Mobile Suit Gundam SEED"),
            ("Mobile Suit Gundam SEED".into(), None)
        );
        assert_eq!(
            series_key("Bocchi the Rock!"),
            series_key("bocchi the rock")
        );
        assert_eq!(series_key("Show S2"), series_key("Show 第二季"));
    }

    #[test]
    fn natural_order_compares_numbers_by_value() {
        let mut names = vec![
            "Show - 10.mkv",
            "Show - 2.mkv",
            "show - 1.mkv",
            "Show - 02v2.mkv",
        ];
        names.sort_by(|a, b| natural_cmp(a, b));
        assert_eq!(
            names,
            [
                "show - 1.mkv",
                "Show - 2.mkv",
                "Show - 02v2.mkv",
                "Show - 10.mkv"
            ]
        );
    }
}
