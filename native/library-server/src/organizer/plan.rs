//! Transient, group-scoped move manifests computed from the saved draft.
use super::draft::{DraftFile, OrganizationDraft};
use super::*;

impl LibraryOrganizer {
    /// Builds the manifest for one review group. The plan stays executable
    /// while that group's decisions and the catalog are unchanged, so edits
    /// and identification in other groups never invalidate an approval.
    pub fn preview_draft(
        &self,
        library: &PublishedLibrary,
        request: draft::DraftPreviewRequest,
    ) -> Result<OrganizationPlan> {
        self.ensure_available()?;
        let draft = self
            .draft()?
            .filter(|d| d.id == request.draft_id)
            .ok_or_else(|| LibraryServerError::new("This review was replaced or discarded."))?;
        if !Path::new(&draft.destination).is_absolute() {
            return Err(LibraryServerError::new(
                "Choose an absolute destination folder inside a library root.",
            ));
        }
        let destination = normalize_absolute(Path::new(&draft.destination))?;
        let destination_root = self.containing_root(&destination)?;
        reject_reparse_ancestors(&destination_root, &destination)?;
        let mut plan = build_draft_plan(
            library,
            &draft,
            &destination_root,
            &destination,
            &request.group_id,
        )?;
        plan.catalog_revision = catalog_revision(&library.catalog);
        plan.plan_id = sha256_hex(&format!(
            "{}:{}:{}:{}",
            draft.id,
            plan.group_signature,
            plan.catalog_revision,
            serde_json::to_string(&plan.batches)?
        ));
        let stored = StoredPlan {
            catalog_revision: plan.catalog_revision.clone(),
            root: destination_root.clone(),
            draft_group: Some((
                draft.id.clone(),
                request.group_id.clone(),
                plan.group_signature.clone(),
            )),
            batches: plan
                .batches
                .iter()
                .filter(|b| b.executable)
                .map(|b| {
                    (
                        b.batch_id.clone(),
                        StoredBatch {
                            batch_id: b.batch_id.clone(),
                            series_title: b.series_title.clone(),
                            root: destination_root.clone(),
                            moves: b.moves.clone(),
                        },
                    )
                })
                .collect(),
        };
        let mut runtime = self.runtime.lock().expect("organizer lock");
        // Keep one plan per group so switching groups never expires another approval.
        runtime.plans.retain(|_, existing| {
            existing
                .draft_group
                .as_ref()
                .is_none_or(|(_, group, _)| group != &request.group_id)
        });
        runtime.plans.insert(plan.plan_id.clone(), stored);
        Ok(plan)
    }
}

/// Digest of everything that decides a group's moves. Identification details
/// that do not change destinations (for example extra candidates) are omitted.
pub(super) fn group_signature(draft: &OrganizationDraft, group_id: &str) -> String {
    let files = draft
        .files
        .iter()
        .filter(|f| f.group_id == group_id)
        .map(|f| {
            serde_json::json!([
                f.media_id,
                f.source_signature,
                f.excluded,
                f.series_title,
                f.season_number,
                f.manual_assignment || f.candidate.is_some() || f.provider_title.is_some(),
            ])
        })
        .collect::<Vec<_>>();
    sha256_hex(
        &serde_json::json!({
            "destination": draft.destination,
            "files": files,
            "choices": draft.companion_choices,
            "owners": draft.companion_owners,
            "seriesOwners": draft.companion_series_owners,
            "skipped": draft.skipped.contains(group_id),
        })
        .to_string(),
    )
}

fn build_draft_plan(
    library: &PublishedLibrary,
    draft: &OrganizationDraft,
    destination_root: &Path,
    destination: &Path,
    focus: &str,
) -> Result<OrganizationPlan> {
    let mut batches = BTreeMap::<String, OrganizationSeriesBatch>::new();
    // Missing identities block moving, not confirming files that are already in place.
    let mut identity_conflicts = BTreeMap::<String, usize>::new();
    let mut parents = BTreeMap::<PathBuf, Vec<&DraftFile>>::new();
    let mut nearby_owners = BTreeMap::<PathBuf, Vec<&DraftFile>>::new();
    for file in draft.files.iter().filter(|f| {
        !f.excluded && !draft.completed.contains_key(&f.group_id) && f.group_id == focus
    }) {
        let batch =
            batches
                .entry(file.group_id.clone())
                .or_insert_with(|| OrganizationSeriesBatch {
                    batch_id: file.group_id.clone(),
                    series_title: file.series_title.clone(),
                    season_number: file.season_number,
                    confidence: if file.provider_title.is_some() || file.candidate.is_some() {
                        OrganizationConfidence::Provider
                    } else {
                        OrganizationConfidence::Parsed
                    },
                    reason: String::new(),
                    video_count: 0,
                    executable: false,
                    already_organized: false,
                    conflicts: Vec::new(),
                    moves: Vec::new(),
                    nearby_files: Vec::new(),
                });
        batch.video_count += 1;
        if batch.series_title != file.series_title {
            batch
                .conflicts
                .push("Files in a review group must use the same series folder title.".into());
        }
        let Some(item) = library.catalog.items.iter().find(|i| i.id == file.media_id) else {
            batch.conflicts.push(format!(
                "Missing selected video: {}",
                file.source_relative_path
            ));
            continue;
        };
        let Some(source) = library.files_by_id.get(&file.media_id) else {
            batch
                .conflicts
                .push(format!("Missing media path: {}", file.source_relative_path));
            continue;
        };
        let expected =
            Path::new(&file.source_root).join(path_from_wire(&file.source_relative_path));
        if !paths_equal(source, &expected) || item.size_bytes != file.size_bytes {
            batch.conflicts.push(format!(
                "Selected video changed: {}",
                file.source_relative_path
            ));
            continue;
        }
        if super::transfer::signature(source).ok().as_ref() != Some(&file.source_signature) {
            batch.conflicts.push(format!(
                "Selected source is missing or changed: {}",
                file.source_relative_path
            ));
            continue;
        }
        if !file.manual_assignment && file.provider_title.is_none() && file.candidate.is_none() {
            *identity_conflicts.entry(file.group_id.clone()).or_default() += 1;
            batch.conflicts.push(format!(
                "Choose a match or use the folder title for {}",
                file.source_relative_path
            ));
        }
        let title = match sanitize_component(&file.series_title) {
            Ok(t) => t,
            Err(e) => {
                batch.conflicts.push(e.to_string());
                continue;
            }
        };
        let Some(season) = file.season_number else {
            batch
                .conflicts
                .push(format!("Choose a season: {}", file.source_relative_path));
            continue;
        };
        let target = destination
            .join(&title)
            .join(format!("Season {season}"))
            .join(source.file_name().unwrap_or_default());
        if !paths_equal(source, &target) {
            batch.moves.push(OrganizationMove {
                source_signature: Some(file.source_signature.clone()),
                content_hash: None,
                source_root: Some(PathBuf::from(&file.source_root)),
                destination_root: Some(destination_root.to_owned()),
                media_id: Some(file.media_id.clone()),
                subtitle_id: None,
                source_relative_path: file.source_relative_path.clone(),
                destination_relative_path: relative_wire_path(destination_root, &target)?,
                size_bytes: file.size_bytes,
                kind: OrganizationMoveKind::Video,
                original_series_title: Some(item.series_title.clone()),
                destination_series_title: Some(file.series_title.clone()),
            });
        }
        if let Some(parent) = source.parent() {
            parents.entry(parent.to_owned()).or_default().push(file);
        }
    }
    for (parent, files) in parents {
        if let Ok(entries) = fs::read_dir(&parent) {
            for entry in entries.flatten() {
                let path = entry.path();
                if path.is_file()
                    && !VIDEO_EXTENSIONS.contains(&extension_lowercase(&path).as_str())
                {
                    nearby_owners
                        .entry(path)
                        .or_default()
                        .extend(files.iter().copied());
                }
            }
        }
        if find_season_number(&parent.file_name().unwrap_or_default().to_string_lossy()).is_some() {
            if let Some(series_parent) = parent.parent() {
                if let Ok(entries) = fs::read_dir(series_parent) {
                    for entry in entries.flatten() {
                        let path = entry.path();
                        if path.is_file()
                            && !VIDEO_EXTENSIONS.contains(&extension_lowercase(&path).as_str())
                            && files.iter().all(|f| path.starts_with(&f.source_root))
                        {
                            nearby_owners
                                .entry(path)
                                .or_default()
                                .extend(files.iter().copied());
                        }
                    }
                }
            }
        }
    }
    for (path, owners) in nearby_owners {
        let mut owners = owners;
        owners.sort_by(|a, b| a.media_id.cmp(&b.media_id));
        owners.dedup_by_key(|f| &f.media_id);
        let matching = owners
            .iter()
            .copied()
            .filter(|f| subtitle_matches(&f.source_relative_path, &path))
            .collect::<Vec<_>>();
        let matching_all = library
            .files_by_id
            .iter()
            .filter(|(_, video)| {
                video.parent() == path.parent() && subtitle_matches(&video.to_string_lossy(), &path)
            })
            .map(|(id, _)| id)
            .collect::<Vec<_>>();
        // Excluded or unselected videos retain their own subtitles, even after a previous preview selected them.
        if !matching_all.is_empty() && matching.is_empty() {
            continue;
        }
        let key = path.to_string_lossy().into_owned();
        let series_group = draft.companion_series_owners.get(&key);
        if series_group.is_some() && draft.companion_owners.contains_key(&key) {
            return Err(LibraryServerError::new(
                "Choose either a series or a video owner for each companion.",
            ));
        }
        if series_group.is_some_and(|group| !owners.iter().any(|f| &f.group_id == group))
            || draft
                .companion_owners
                .get(&key)
                .is_some_and(|id| !owners.iter().any(|f| &f.media_id == id))
        {
            continue;
        }
        let recommended = matching.len() == 1 && matching_all.len() == 1;
        let explicit_series =
            series_group.and_then(|group| owners.iter().find(|f| &f.group_id == group).copied());
        let explicit_owner = draft
            .companion_owners
            .get(&key)
            .and_then(|id| owners.iter().find(|f| &f.media_id == id).copied());
        let owner = explicit_series
            .or(explicit_owner)
            .unwrap_or_else(|| if recommended { matching[0] } else { owners[0] });
        let selected = draft
            .companion_choices
            .get(&key)
            .copied()
            .unwrap_or(recommended);
        let batch = batches.get_mut(&owner.group_id).expect("owner group");
        let root = Path::new(&owner.source_root);
        let title = match sanitize_component(&owner.series_title) {
            Ok(t) => t,
            Err(_) => continue,
        };
        let target = if explicit_series.is_some()
            || is_series_asset(&path.file_name().unwrap_or_default().to_string_lossy())
        {
            destination.join(title)
        } else {
            destination
                .join(title)
                .join(format!("Season {}", owner.season_number.unwrap_or(1)))
        }
        .join(path.file_name().unwrap_or_default());
        if paths_equal(&path, &target) {
            continue;
        }
        let size = fs::metadata(&path)?.len();
        batch.nearby_files.push(OrganizationNearbyFile {
            owner_group_ids: owners
                .iter()
                .map(|f| f.group_id.clone())
                .collect::<BTreeSet<_>>()
                .into_iter()
                .collect(),
            owner_group_id: explicit_series.map(|f| f.group_id.clone()),
            owner_media_ids: owners.iter().map(|f| f.media_id.clone()).collect(),
            owner_media_id: if explicit_series.is_none()
                && (recommended || explicit_owner.is_some())
            {
                Some(owner.media_id.clone())
            } else {
                None
            },
            relative_path: key,
            size_bytes: size,
            recommended,
            selected,
            destination_relative_path: Some(target.to_string_lossy().into_owned()),
        });
        if selected && !recommended && explicit_owner.is_none() && explicit_series.is_none() {
            batch.conflicts.push(format!(
                "Assign an owner for the companion file: {}",
                path.display()
            ));
            continue;
        }
        if selected {
            let subtitle_id = library
                .subtitle_files_by_id
                .iter()
                .find(|(_, p)| paths_equal(p, &path))
                .map(|(id, _)| id.clone());
            batch.moves.push(OrganizationMove {
                source_signature: Some(super::transfer::signature(&path)?),
                content_hash: None,
                source_root: Some(root.to_owned()),
                destination_root: Some(destination_root.to_owned()),
                media_id: None,
                subtitle_id,
                source_relative_path: relative_wire_path(root, &path)?,
                destination_relative_path: relative_wire_path(destination_root, &target)?,
                size_bytes: size,
                kind: OrganizationMoveKind::Nearby,
                original_series_title: None,
                destination_series_title: None,
            });
        }
    }
    let mut other_destinations = BTreeSet::new();
    {
        for file in draft.files.iter().filter(|f| {
            !f.excluded && f.group_id != focus && !draft.completed.contains_key(&f.group_id)
        }) {
            if let (Ok(title), Some(season)) =
                (sanitize_component(&file.series_title), file.season_number)
            {
                let filename = path_from_wire(&file.source_relative_path);
                other_destinations.insert(
                    destination
                        .join(title)
                        .join(format!("Season {season}"))
                        .join(filename.file_name().unwrap_or_default())
                        .to_string_lossy()
                        .to_lowercase(),
                );
            }
        }
    }
    let mut destinations = BTreeMap::<String, Vec<String>>::new();
    for batch in batches.values_mut() {
        for operation in &batch.moves {
            let target = operation.destination(destination_root);
            if other_destinations.contains(&target.to_string_lossy().to_lowercase()) {
                batch.conflicts.push(format!(
                    "Another selected group targets this destination: {}",
                    target.display()
                ));
            }
            if target.exists() {
                batch
                    .conflicts
                    .push(format!("Destination already exists: {}", target.display()));
            }
            destinations
                .entry(target.to_string_lossy().to_lowercase())
                .or_default()
                .push(batch.batch_id.clone());
        }
        batch.already_organized = batch.moves.is_empty()
            && batch.conflicts.len()
                == identity_conflicts
                    .get(&batch.batch_id)
                    .copied()
                    .unwrap_or_default();
    }
    for (path, groups) in destinations
        .into_iter()
        .filter(|(_, groups)| groups.len() > 1)
    {
        for group in groups {
            batches
                .get_mut(&group)
                .unwrap()
                .conflicts
                .push(format!("Two files target the same destination: {path}"));
        }
    }
    for batch in batches.values_mut() {
        batch.executable = !batch.moves.is_empty()
            && batch.conflicts.is_empty()
            && !draft.skipped.contains(&batch.batch_id);
    }
    Ok(OrganizationPlan {
        draft_id: draft.id.clone(),
        group_signature: group_signature(draft, focus),
        plan_id: String::new(),
        catalog_revision: String::new(),
        root: destination_root.to_string_lossy().into_owned(),
        batches: batches.into_values().collect(),
    })
}

fn subtitle_matches(video: &str, subtitle: &Path) -> bool {
    if !SUBTITLE_EXTENSIONS.contains(&extension_lowercase(subtitle).as_str()) {
        return false;
    }
    let video = path_from_wire(video);
    let stem = video
        .file_stem()
        .unwrap_or_default()
        .to_string_lossy()
        .to_lowercase();
    let subtitle = subtitle
        .file_stem()
        .unwrap_or_default()
        .to_string_lossy()
        .to_lowercase();
    subtitle == stem
        || subtitle
            .strip_prefix(&stem)
            .is_some_and(|rest| rest.starts_with(['.', '_', '-']))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn subtitle_matching_requires_a_filename_boundary() {
        assert!(subtitle_matches(
            "Show 01.mkv",
            Path::new("Show 01.zh-TW.ass")
        ));
        assert!(!subtitle_matches("Show 01.mkv", Path::new("Show 010.ass")));
        assert!(!subtitle_matches("Show 01.mkv", Path::new("Show 01.png")));
    }
}
