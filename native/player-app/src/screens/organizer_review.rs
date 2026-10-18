//! Pure review-queue model derived from the server draft: series order,
//! per-series state, blockers and the preview key. No I/O, no egui.
use crate::library::{DraftFile, IdentificationCandidate, OrganizationDraft};
use std::cmp::Ordering;
use std::collections::BTreeMap;
use std::hash::{Hash, Hasher};

#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord)]
pub(super) enum GroupState {
    /// Some included video lacks a match/confirmed title or a season.
    NeedsAttention,
    Ready,
    Skipped,
    /// Every video is excluded; nothing will move.
    Excluded,
    Done,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(super) enum QueueFilter {
    ToDo,
    Ready,
    Skipped,
    Done,
    All,
}

impl QueueFilter {
    pub(super) fn accepts(self, state: GroupState) -> bool {
        match self {
            Self::ToDo => matches!(state, GroupState::NeedsAttention | GroupState::Ready),
            Self::Ready => state == GroupState::Ready,
            Self::Skipped => matches!(state, GroupState::Skipped | GroupState::Excluded),
            Self::Done => state == GroupState::Done,
            Self::All => true,
        }
    }
}

#[derive(Clone, Debug, PartialEq)]
pub(super) struct GroupSummary {
    pub id: String,
    pub title: String,
    pub state: GroupState,
    /// Draft file indices in natural path order.
    pub members: Vec<usize>,
    pub included: usize,
    pub unidentified: usize,
    pub without_season: usize,
    pub seasons: Vec<u32>,
}

impl GroupSummary {
    pub(super) fn season_label(&self) -> String {
        match (self.seasons.first(), self.seasons.last()) {
            (Some(first), Some(last)) if first == last => format!("S{first}"),
            (Some(first), Some(last)) => format!("S{first}–S{last}"),
            _ => String::new(),
        }
    }
}

/// Queue of review groups, sorted by title like a file manager.
#[derive(Clone, Debug, Default)]
pub(super) struct ReviewModel {
    pub groups: Vec<GroupSummary>,
}

impl ReviewModel {
    pub(super) fn build(draft: &OrganizationDraft) -> Self {
        let mut by_id = BTreeMap::<&str, GroupSummary>::new();
        for (index, file) in draft.files.iter().enumerate() {
            let summary = by_id
                .entry(file.group_id.as_str())
                .or_insert_with(|| GroupSummary {
                    id: file.group_id.clone(),
                    title: file.series_title.clone(),
                    state: GroupState::Ready,
                    members: Vec::new(),
                    included: 0,
                    unidentified: 0,
                    without_season: 0,
                    seasons: Vec::new(),
                });
            summary.members.push(index);
            if file.excluded {
                continue;
            }
            summary.included += 1;
            if !file.identified() {
                summary.unidentified += 1;
            }
            match file.season_number {
                Some(season) => {
                    if !summary.seasons.contains(&season) {
                        summary.seasons.push(season);
                    }
                }
                None => summary.without_season += 1,
            }
        }
        let mut groups = by_id.into_values().collect::<Vec<_>>();
        for group in &mut groups {
            group.seasons.sort_unstable();
            group.state = if draft.completed.contains_key(&group.id) {
                GroupState::Done
            } else if draft.skipped.contains(&group.id) {
                GroupState::Skipped
            } else if group.included == 0 {
                GroupState::Excluded
            } else if group.unidentified > 0 || group.without_season > 0 {
                GroupState::NeedsAttention
            } else {
                GroupState::Ready
            };
        }
        groups.sort_by(|a, b| natural_cmp(&a.title, &b.title).then_with(|| a.id.cmp(&b.id)));
        Self { groups }
    }

    pub(super) fn get(&self, id: &str) -> Option<&GroupSummary> {
        self.groups.iter().find(|g| g.id == id)
    }

    pub(super) fn count(&self, filter: QueueFilter) -> usize {
        self.groups
            .iter()
            .filter(|g| filter.accepts(g.state))
            .count()
    }

    /// First group still to do, starting after `after` and wrapping around.
    pub(super) fn next_to_do(&self, after: Option<&str>) -> Option<&GroupSummary> {
        let start = after
            .and_then(|id| self.groups.iter().position(|g| g.id == id))
            .map_or(0, |index| index + 1);
        self.groups[start..]
            .iter()
            .chain(&self.groups[..start])
            .find(|g| QueueFilter::ToDo.accepts(g.state))
    }
}

/// Provider identities the user can pick for a group: search results first,
/// then series identified for its files, one entry per series.
pub(super) fn group_match_options(
    draft: &OrganizationDraft,
    group: &GroupSummary,
) -> Vec<IdentificationCandidate> {
    let mut options = Vec::<IdentificationCandidate>::new();
    let from_files = group.members.iter().flat_map(|&i| {
        let file = &draft.files[i];
        file.candidates.iter().chain(file.candidate.iter())
    });
    for candidate in draft
        .group_candidates
        .get(&group.id)
        .into_iter()
        .flatten()
        .chain(from_files)
    {
        if !options.iter().any(|o| o.anime_id == candidate.anime_id) {
            options.push(IdentificationCandidate {
                anime_id: candidate.anime_id,
                series_title: candidate.series_title.clone(),
                episode_id: None,
                episode_title: String::new(),
            });
        }
    }
    options
}

/// The provider series shared by every linked video, if there is exactly one.
pub(super) fn group_match<'a>(
    draft: &'a OrganizationDraft,
    group: &GroupSummary,
) -> GroupMatch<'a> {
    let mut linked = group
        .members
        .iter()
        .map(|&i| &draft.files[i])
        .filter(|f| !f.excluded)
        .filter_map(|f| f.candidate.as_ref());
    let Some(first) = linked.next() else {
        return GroupMatch::None;
    };
    if linked.all(|c| c.anime_id == first.anime_id) {
        GroupMatch::One(first)
    } else {
        GroupMatch::Mixed
    }
}

pub(super) enum GroupMatch<'a> {
    None,
    One(&'a IdentificationCandidate),
    Mixed,
}

/// Changes whenever anything that decides this group's moves changes.
pub(super) fn preview_key(
    draft: &OrganizationDraft,
    group: &GroupSummary,
    catalog_version: u64,
) -> u64 {
    let mut hasher = std::collections::hash_map::DefaultHasher::new();
    draft.id.hash(&mut hasher);
    group.id.hash(&mut hasher);
    catalog_version.hash(&mut hasher);
    draft.destination.hash(&mut hasher);
    draft.skipped.contains(&group.id).hash(&mut hasher);
    for &index in &group.members {
        let file: &DraftFile = &draft.files[index];
        (
            &file.media_id,
            &file.source_signature,
            file.excluded,
            &file.series_title,
            file.season_number,
            file.identified(),
        )
            .hash(&mut hasher);
    }
    for map in [&draft.companion_owners, &draft.companion_series_owners] {
        map.hash(&mut hasher);
    }
    draft.companion_choices.hash(&mut hasher);
    hasher.finish()
}

/// File-manager ordering: numbers compare by value, case is a tie-breaker.
pub(super) fn natural_cmp(left: &str, right: &str) -> Ordering {
    fn chunks(value: &str) -> Vec<(bool, String)> {
        let mut out: Vec<(bool, String)> = Vec::new();
        for c in value.chars() {
            let digit = c.is_ascii_digit();
            match out.last_mut() {
                Some((kind, text)) if *kind == digit => text.push(c),
                _ => out.push((digit, c.to_string())),
            }
        }
        out
    }
    let (a, b) = (chunks(left), chunks(right));
    for (x, y) in a.iter().zip(&b) {
        let ordering = match (x, y) {
            ((true, x), (true, y)) => {
                let (xt, yt) = (x.trim_start_matches('0'), y.trim_start_matches('0'));
                xt.len().cmp(&yt.len()).then_with(|| xt.cmp(yt))
            }
            ((_, x), (_, y)) => x.to_lowercase().cmp(&y.to_lowercase()),
        };
        if ordering != Ordering::Equal {
            return ordering;
        }
    }
    a.len().cmp(&b.len()).then_with(|| left.cmp(right))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn file(id: &str, group: &str, title: &str) -> DraftFile {
        DraftFile {
            media_id: id.into(),
            group_id: group.into(),
            series_title: title.into(),
            season_number: Some(1),
            manual_assignment: true,
            ..Default::default()
        }
    }

    fn draft(files: Vec<DraftFile>) -> OrganizationDraft {
        OrganizationDraft {
            id: "d".into(),
            files,
            ..Default::default()
        }
    }

    #[test]
    fn queue_sorts_by_title_and_classifies_each_series() {
        let mut unmatched = file("c", "g3", "Show 10");
        unmatched.manual_assignment = false;
        let mut draft = draft(vec![
            file("a", "g2", "show 2"),
            unmatched,
            file("b", "g1", "Bocchi"),
            file("d", "g4", "Zeta"),
        ]);
        draft.completed.insert("g4".into(), "done".into());
        let model = ReviewModel::build(&draft);
        let titles = model
            .groups
            .iter()
            .map(|g| g.title.as_str())
            .collect::<Vec<_>>();
        assert_eq!(titles, ["Bocchi", "show 2", "Show 10", "Zeta"]);
        assert_eq!(model.get("g3").unwrap().state, GroupState::NeedsAttention);
        assert_eq!(model.get("g4").unwrap().state, GroupState::Done);
        assert_eq!(model.count(QueueFilter::ToDo), 3);
        let mut excluded = draft.clone();
        excluded.files[0].excluded = true;
        let model = ReviewModel::build(&excluded);
        assert_eq!(model.get("g2").unwrap().state, GroupState::Excluded);
        assert_eq!(model.count(QueueFilter::ToDo), 2);
        assert_eq!(model.next_to_do(Some("g3")).unwrap().id, "g1");
    }

    #[test]
    fn preview_key_ignores_other_groups_and_tracks_this_one() {
        let mut draft = draft(vec![file("a", "g1", "A"), file("b", "g2", "B")]);
        let model = ReviewModel::build(&draft);
        let group = model.get("g1").unwrap().clone();
        let key = preview_key(&draft, &group, 1);
        draft.files[1].season_number = Some(4);
        draft.files[1].candidates.push(Default::default());
        assert_eq!(key, preview_key(&draft, &group, 1));
        draft.files[0].excluded = true;
        assert_ne!(key, preview_key(&draft, &group, 1));
        assert_ne!(
            preview_key(&draft, &group, 1),
            preview_key(&draft, &group, 2)
        );
    }

    #[test]
    fn group_options_merge_search_results_and_file_matches_once() {
        let mut a = file("a", "g1", "A");
        a.candidates = vec![IdentificationCandidate {
            anime_id: 7,
            episode_id: Some(70),
            series_title: "Seven".into(),
            episode_title: "Ep".into(),
        }];
        let mut draft = draft(vec![a]);
        draft.group_candidates.insert(
            "g1".into(),
            vec![
                IdentificationCandidate {
                    anime_id: 7,
                    series_title: "Seven".into(),
                    ..Default::default()
                },
                IdentificationCandidate {
                    anime_id: 8,
                    series_title: "Eight".into(),
                    ..Default::default()
                },
            ],
        );
        let model = ReviewModel::build(&draft);
        let options = group_match_options(&draft, model.get("g1").unwrap());
        assert_eq!(options.len(), 2);
        assert!(options.iter().all(|o| o.episode_id.is_none()));
    }
}
