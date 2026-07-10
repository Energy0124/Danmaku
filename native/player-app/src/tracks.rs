//! Audio/subtitle track discovery and selection state.
//!
//! Reads mpv's indexed `track-list/N/*` string sub-properties instead of
//! parsing the JSON `track-list` blob, so the crate stays free of a JSON
//! dependency and the mapping is trivially unit-testable against a fake
//! property reader.

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum TrackKind {
    Audio,
    Subtitle,
}

impl TrackKind {
    pub fn selection_property(self) -> &'static str {
        match self {
            TrackKind::Audio => "aid",
            TrackKind::Subtitle => "sid",
        }
    }
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Track {
    pub kind: TrackKind,
    pub id: u64,
    pub title: Option<String>,
    pub language: Option<String>,
    pub selected: bool,
}

impl Track {
    /// Human-readable menu label: "1: 日本語 (jpn)" / "2: eng".
    pub fn label(&self) -> String {
        let mut label = self.id.to_string();
        match (&self.title, &self.language) {
            (Some(title), Some(language)) => {
                label.push_str(&format!(": {title} ({language})"));
            }
            (Some(title), None) => label.push_str(&format!(": {title}")),
            (None, Some(language)) => label.push_str(&format!(": {language}")),
            (None, None) => {}
        }
        label
    }
}

#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct TrackInventory {
    pub audio: Vec<Track>,
    pub subtitles: Vec<Track>,
}

impl TrackInventory {
    pub fn selected_audio(&self) -> Option<&Track> {
        self.audio.iter().find(|track| track.selected)
    }

    pub fn selected_subtitle(&self) -> Option<&Track> {
        self.subtitles.iter().find(|track| track.selected)
    }
}

/// Reads the track inventory through a property reader
/// (`name -> Option<value>`), which in production is
/// `VideoRenderer::property_string`.
pub fn read_track_inventory(read_property: impl Fn(&str) -> Option<String>) -> TrackInventory {
    let count = read_property("track-list/count")
        .and_then(|value| value.trim().parse::<usize>().ok())
        .unwrap_or(0);

    let mut inventory = TrackInventory::default();
    for index in 0..count {
        let kind = match read_property(&format!("track-list/{index}/type")).as_deref() {
            Some("audio") => TrackKind::Audio,
            Some("sub") => TrackKind::Subtitle,
            _ => continue,
        };
        let Some(id) = read_property(&format!("track-list/{index}/id"))
            .and_then(|value| value.trim().parse::<u64>().ok())
        else {
            continue;
        };
        let track = Track {
            kind,
            id,
            title: read_property(&format!("track-list/{index}/title"))
                .filter(|value| !value.is_empty()),
            language: read_property(&format!("track-list/{index}/lang"))
                .filter(|value| !value.is_empty()),
            selected: matches!(
                read_property(&format!("track-list/{index}/selected")).as_deref(),
                Some("yes") | Some("true")
            ),
        };
        match kind {
            TrackKind::Audio => inventory.audio.push(track),
            TrackKind::Subtitle => inventory.subtitles.push(track),
        }
    }
    inventory
}

/// mpv `set aid/sid` command arguments for selecting a track (`None` disables
/// the track, which is only meaningful for subtitles in the M1 UI).
pub fn selection_command(kind: TrackKind, id: Option<u64>) -> [String; 3] {
    [
        "set".to_owned(),
        kind.selection_property().to_owned(),
        id.map(|id| id.to_string())
            .unwrap_or_else(|| "no".to_owned()),
    ]
}

#[cfg(test)]
mod tests {
    use super::{TrackKind, read_track_inventory, selection_command};
    use std::collections::HashMap;

    fn reader(entries: &[(&str, &str)]) -> impl Fn(&str) -> Option<String> {
        let map: HashMap<String, String> = entries
            .iter()
            .map(|(key, value)| ((*key).to_owned(), (*value).to_owned()))
            .collect();
        move |name: &str| map.get(name).cloned()
    }

    #[test]
    fn reads_audio_and_subtitle_tracks_with_selection() {
        let inventory = read_track_inventory(reader(&[
            ("track-list/count", "4"),
            ("track-list/0/type", "video"),
            ("track-list/0/id", "1"),
            ("track-list/1/type", "audio"),
            ("track-list/1/id", "1"),
            ("track-list/1/title", "日本語"),
            ("track-list/1/lang", "jpn"),
            ("track-list/1/selected", "yes"),
            ("track-list/2/type", "sub"),
            ("track-list/2/id", "1"),
            ("track-list/2/lang", "zh-Hant"),
            ("track-list/2/selected", "yes"),
            ("track-list/3/type", "sub"),
            ("track-list/3/id", "2"),
            ("track-list/3/lang", "zh-Hans"),
            ("track-list/3/selected", "no"),
        ]));

        assert_eq!(inventory.audio.len(), 1);
        assert_eq!(inventory.subtitles.len(), 2);
        assert_eq!(inventory.selected_audio().map(|track| track.id), Some(1));
        assert_eq!(inventory.selected_subtitle().map(|track| track.id), Some(1));
        assert_eq!(inventory.audio[0].label(), "1: 日本語 (jpn)");
        assert_eq!(inventory.subtitles[1].label(), "2: zh-Hans");
    }

    #[test]
    fn missing_or_garbled_entries_are_skipped() {
        let inventory = read_track_inventory(reader(&[
            ("track-list/count", "3"),
            ("track-list/0/type", "audio"),
            // no id -> skipped
            ("track-list/1/type", "mystery"),
            ("track-list/1/id", "9"),
            ("track-list/2/type", "sub"),
            ("track-list/2/id", "not-a-number"),
        ]));

        assert!(inventory.audio.is_empty());
        assert!(inventory.subtitles.is_empty());
    }

    #[test]
    fn empty_property_reader_yields_empty_inventory() {
        let inventory = read_track_inventory(|_| None);

        assert!(inventory.audio.is_empty());
        assert!(inventory.subtitles.is_empty());
    }

    #[test]
    fn selection_commands_target_the_right_property() {
        assert_eq!(
            selection_command(TrackKind::Audio, Some(2)),
            ["set".to_owned(), "aid".to_owned(), "2".to_owned()],
        );
        assert_eq!(
            selection_command(TrackKind::Subtitle, None),
            ["set".to_owned(), "sid".to_owned(), "no".to_owned()],
        );
    }
}
