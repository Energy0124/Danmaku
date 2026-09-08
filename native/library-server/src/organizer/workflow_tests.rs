use super::draft::{CreateDraftRequest, DraftPreviewRequest, OrganizationDraft};
use super::tests::{Fixture, cleanup, fixture};
use super::*;

fn setup(copy: bool) -> (Fixture, LibraryOrganizer, OrganizationDraft, PathBuf) {
    let fixture = fixture();
    let destination = fixture.temp.join("destination");
    fs::create_dir_all(&destination).unwrap();
    let store = CatalogStore::new(fixture.data.join("catalog.json"));
    store.save(fixture.published.clone()).unwrap();
    let organizer = LibraryOrganizer::new(vec![fixture.root.clone(), destination.clone()], store);
    organizer.force_copy.store(copy, Ordering::Relaxed);
    let draft = organizer
        .create_draft(
            &fixture.published,
            CreateDraftRequest {
                media_ids: vec!["one".into(), "one".into(), "two".into()],
                destination: destination.to_string_lossy().into_owned(),
            },
        )
        .unwrap();
    (fixture, organizer, draft, destination)
}

fn manual(organizer: &LibraryOrganizer, mut draft: OrganizationDraft) -> OrganizationDraft {
    for file in &mut draft.files {
        file.manual_assignment = true;
    }
    organizer.update_draft(draft).unwrap()
}

fn preview(
    organizer: &LibraryOrganizer,
    library: &PublishedLibrary,
    draft: &OrganizationDraft,
) -> OrganizationPlan {
    organizer
        .preview_draft(
            library,
            DraftPreviewRequest {
                group_id: None,
                draft_id: draft.id.clone(),
                revision: draft.revision,
            },
        )
        .unwrap()
}

fn prepare(
    organizer: &LibraryOrganizer,
    library: &PublishedLibrary,
    plan: &OrganizationPlan,
) -> PreparedOrganization {
    let batch = &plan.batches[0];
    assert!(batch.executable, "{:?}", batch.conflicts);
    organizer
        .prepare_execute(
            &library.catalog,
            OrganizationExecuteRequest {
                plan_id: plan.plan_id.clone(),
                batch_id: batch.batch_id.clone(),
                expected_moves: batch.moves.clone(),
            },
        )
        .unwrap()
}

#[test]
fn draft_snapshot_deduplicates_and_requires_identification_or_manual_confirmation() {
    let (fixture, organizer, draft, _) = setup(false);
    assert_eq!(draft.files.len(), 2);
    assert!(
        draft
            .files
            .iter()
            .all(|f| f.season_number == Some(1) && f.season_evidence == "SUGGESTED")
    );
    assert!(!preview(&organizer, &fixture.published, &draft).batches[0].executable);
    let draft = manual(&organizer, draft);
    assert!(preview(&organizer, &fixture.published, &draft).batches[0].executable);
    cleanup(fixture.temp);
}

#[test]
fn saved_draft_restores_edits_but_not_an_executable_manifest() {
    let (fixture, organizer, mut draft, destination) = setup(false);
    draft.files[0].series_title = "Changed".into();
    draft.files[0].group_id = "split".into();
    draft.files[0].season_number = Some(0);
    draft.files[1].excluded = true;
    draft.skipped.insert("other".into());
    let draft = manual(&organizer, draft);
    let plan = preview(&organizer, &fixture.published, &draft);
    drop(organizer);
    let organizer = LibraryOrganizer::new(
        vec![fixture.root.clone(), destination],
        CatalogStore::new(fixture.data.join("catalog.json")),
    );
    assert_eq!(organizer.draft().unwrap(), Some(draft));
    assert!(
        organizer
            .prepare_execute(
                &fixture.published.catalog,
                OrganizationExecuteRequest {
                    plan_id: plan.plan_id,
                    batch_id: plan.batches[0].batch_id.clone(),
                    expected_moves: plan.batches[0].moves.clone()
                }
            )
            .is_err()
    );
    cleanup(fixture.temp);
}

#[test]
fn destination_or_assignment_edits_reject_old_approval_and_preserve_other_groups() {
    let (fixture, organizer, mut draft, destination) = setup(false);
    draft.files[1].group_id = "second".into();
    draft.files[1].series_title = "Second".into();
    let draft = manual(&organizer, draft);
    let plan = preview(&organizer, &fixture.published, &draft);
    let mut edited = draft.clone();
    edited.destination = destination.join("edited").display().to_string();
    let updated = organizer.update_draft(edited).unwrap();
    assert_eq!(updated.files, draft.files);
    assert!(
        organizer
            .prepare_execute(
                &fixture.published.catalog,
                OrganizationExecuteRequest {
                    plan_id: plan.plan_id,
                    batch_id: plan.batches[0].batch_id.clone(),
                    expected_moves: plan.batches[0].moves.clone()
                }
            )
            .is_err()
    );
    assert!(organizer.update_draft(draft).is_err());
    cleanup(fixture.temp);
}

#[test]
fn cross_root_and_forced_cross_volume_moves_preserve_ids_and_undo() {
    for copy in [false, true] {
        let (fixture, organizer, draft, destination) = setup(copy);
        let draft = manual(&organizer, draft);
        let plan = preview(&organizer, &fixture.published, &draft);
        let updated = organizer
            .execute(prepare(&organizer, &fixture.published, &plan))
            .unwrap();
        assert_eq!(
            updated
                .catalog
                .items
                .iter()
                .map(|i| i.id.clone())
                .collect::<BTreeSet<_>>(),
            BTreeSet::from(["one".into(), "two".into()])
        );
        assert!(
            updated
                .catalog
                .items
                .iter()
                .all(|i| i.root_label.as_deref() == Some(destination.to_str().unwrap()))
        );
        for (id, path) in &updated.files_by_id {
            assert_eq!(fs::read(path).unwrap(), fs_content(id));
        }
        let status = organizer.status();
        assert_eq!(status.completed_bytes, 6);
        assert_eq!(status.draft.unwrap().completed.len(), 1);
        let restored = organizer
            .execute(
                organizer
                    .prepare_undo(&status.last_completed_batch_id.unwrap())
                    .unwrap(),
            )
            .unwrap();
        assert_eq!(restored.files_by_id, fixture.published.files_by_id);
        assert!(organizer.draft().unwrap().unwrap().completed.is_empty());
        assert!(
            preview(&organizer, &restored, &organizer.draft().unwrap().unwrap()).batches[0]
                .executable
        );
        cleanup(fixture.temp);
    }
}

#[test]
fn insufficient_space_does_not_create_staging_or_remove_sources() {
    let (fixture, organizer, draft, destination) = setup(true);
    *organizer.available_space.lock().unwrap() = Some(0);
    let draft = manual(&organizer, draft);
    let plan = preview(&organizer, &fixture.published, &draft);
    assert!(
        organizer
            .execute(prepare(&organizer, &fixture.published, &plan))
            .unwrap_err()
            .to_string()
            .contains("free space")
    );
    assert!(fixture.published.files_by_id.values().all(|p| p.exists()));
    assert_eq!(fs::read_dir(destination).unwrap().count(), 0);
    cleanup(fixture.temp);
}

#[test]
fn disconnected_destination_requires_recovery_then_retry_restores_sources() {
    let (fixture, organizer, draft, destination) = setup(true);
    let draft = manual(&organizer, draft);
    let plan = preview(&organizer, &fixture.published, &draft);
    let prepared = prepare(&organizer, &fixture.published, &plan);
    *organizer.transfer_failpoint.lock().unwrap() = Some("removed".into());
    assert!(organizer.execute_transfer(&prepared.batch, false).is_err());
    let detached = fixture.temp.join("detached");
    fs::rename(&destination, &detached).unwrap();
    drop(organizer);
    let recovered = LibraryOrganizer::new(
        vec![fixture.root.clone(), destination.clone()],
        CatalogStore::new(fixture.data.join("catalog.json")),
    );
    assert_eq!(
        recovered.status().state,
        OrganizationState::RecoveryRequired
    );
    fs::rename(detached, destination).unwrap();
    recovered.retry_recovery().unwrap();
    assert!(fixture.published.files_by_id.values().all(|p| p.exists()));
    assert!(!recovered.recovery_required());
    cleanup(fixture.temp);
}

#[cfg(windows)]
#[test]
fn locked_source_fails_without_losing_originals() {
    use std::os::windows::fs::OpenOptionsExt;
    let (fixture, organizer, draft, _) = setup(true);
    let draft = manual(&organizer, draft);
    let plan = preview(&organizer, &fixture.published, &draft);
    let prepared = prepare(&organizer, &fixture.published, &plan);
    let held = fs::OpenOptions::new()
        .read(true)
        .share_mode(0)
        .open(fixture.published.files_by_id.get("one").unwrap())
        .unwrap();
    assert!(organizer.execute(prepared).is_err());
    drop(held);
    assert!(fixture.published.files_by_id.values().all(|p| p.exists()));
    cleanup(fixture.temp);
}

#[test]
fn existing_destination_race_never_overwrites_a_foreign_file() {
    let (fixture, organizer, draft, _) = setup(true);
    let draft = manual(&organizer, draft);
    let plan = preview(&organizer, &fixture.published, &draft);
    let prepared = prepare(&organizer, &fixture.published, &plan);
    *organizer.transfer_failpoint.lock().unwrap() = Some("verified".into());
    assert!(organizer.execute_transfer(&prepared.batch, false).is_err());
    let transaction = organizer
        .runtime
        .lock()
        .unwrap()
        .journal
        .active
        .clone()
        .unwrap();
    let target = prepared.batch.moves[0].destination(&prepared.batch.root);
    fs::write(&target, b"foreign").unwrap();
    organizer.rollback_transfer(transaction).unwrap();
    assert_eq!(fs::read(target).unwrap(), b"foreign");
    assert!(fixture.published.files_by_id.values().all(|p| p.exists()));
    cleanup(fixture.temp);
}

fn fs_content(id: &str) -> Vec<u8> {
    if id == "one" {
        b"one".to_vec()
    } else {
        b"two".to_vec()
    }
}

#[test]
fn completion_advances_to_next_group_without_losing_edits() {
    let (fixture, organizer, mut draft, _) = setup(false);
    draft.files[1].group_id = "second".into();
    draft.files[1].series_title = "Second edited".into();
    draft.files[1].season_number = Some(7);
    let draft = manual(&organizer, draft);
    let plan = preview(&organizer, &fixture.published, &draft);
    organizer
        .execute(prepare(&organizer, &fixture.published, &plan))
        .unwrap();
    let after = organizer.draft().unwrap().unwrap();
    assert_eq!(after.files, draft.files);
    assert!(
        after
            .active_group
            .as_ref()
            .is_some_and(|g| !after.completed.contains_key(g))
    );
    cleanup(fixture.temp);
}

#[test]
fn restart_rolls_back_every_precommit_transfer_phase() {
    for copy in [false, true] {
        let phases = if copy {
            vec![
                "journal",
                "staged",
                "verified",
                "publish-intent",
                "published",
                "removed",
            ]
        } else {
            vec!["journal", "publish-intent", "published", "removed"]
        };
        for phase in phases {
            let (fixture, organizer, draft, destination) = setup(copy);
            let draft = manual(&organizer, draft);
            let plan = preview(&organizer, &fixture.published, &draft);
            let prepared = prepare(&organizer, &fixture.published, &plan);
            *organizer.transfer_failpoint.lock().unwrap() = Some(phase.into());
            assert!(
                organizer.execute_transfer(&prepared.batch, false).is_err(),
                "{phase}"
            );
            drop(organizer);
            let recovered = LibraryOrganizer::new(
                vec![fixture.root.clone(), destination.clone()],
                CatalogStore::new(fixture.data.join("catalog.json")),
            );
            assert_ne!(
                recovered.status().state,
                OrganizationState::RecoveryRequired,
                "{phase}: {:?}",
                recovered.status().message
            );
            for (id, path) in &fixture.published.files_by_id {
                assert_eq!(fs::read(path).unwrap(), fs_content(id), "{phase}");
            }
            assert!(recovered.runtime.lock().unwrap().journal.active.is_none());
            cleanup(fixture.temp);
        }
    }
}

#[test]
fn restart_finishes_catalog_commit_and_preserves_undo_and_queue() {
    let (fixture, organizer, draft, destination) = setup(true);
    let draft = manual(&organizer, draft);
    let plan = preview(&organizer, &fixture.published, &draft);
    *organizer.transfer_failpoint.lock().unwrap() = Some("catalog-committed".into());
    assert!(
        organizer
            .execute(prepare(&organizer, &fixture.published, &plan))
            .is_err()
    );
    drop(organizer);
    let recovered = LibraryOrganizer::new(
        vec![fixture.root.clone(), destination],
        CatalogStore::new(fixture.data.join("catalog.json")),
    );
    assert!(recovered.status().can_undo);
    assert_eq!(recovered.draft().unwrap().unwrap().completed.len(), 1);
    assert!(fixture.published.files_by_id.values().all(|p| !p.exists()));
    cleanup(fixture.temp);
}

#[test]
fn changed_published_copy_is_preserved_and_requires_recovery() {
    let (fixture, organizer, draft, destination) = setup(true);
    let draft = manual(&organizer, draft);
    let plan = preview(&organizer, &fixture.published, &draft);
    let prepared = prepare(&organizer, &fixture.published, &plan);
    *organizer.transfer_failpoint.lock().unwrap() = Some("published".into());
    assert!(organizer.execute_transfer(&prepared.batch, false).is_err());
    let target = prepared.batch.moves[0].destination(&prepared.batch.root);
    fs::write(&target, b"external change").unwrap();
    drop(organizer);
    let recovered = LibraryOrganizer::new(
        vec![fixture.root.clone(), destination],
        CatalogStore::new(fixture.data.join("catalog.json")),
    );
    assert_eq!(
        recovered.status().state,
        OrganizationState::RecoveryRequired
    );
    assert_eq!(fs::read(target).unwrap(), b"external change");
    assert!(fixture.published.files_by_id.values().all(|p| p.exists()));
    cleanup(fixture.temp);
}

#[test]
fn changed_source_and_existing_destinations_block_before_moving() {
    let (fixture, organizer, draft, _) = setup(false);
    let draft = manual(&organizer, draft);
    let plan = preview(&organizer, &fixture.published, &draft);
    let target = plan.batches[0].moves[0].destination(Path::new(&plan.root));
    fs::create_dir_all(target.parent().unwrap()).unwrap();
    fs::write(&target, b"occupied").unwrap();
    let blocked = preview(&organizer, &fixture.published, &draft);
    assert!(!blocked.batches[0].executable);
    fs::remove_file(target).unwrap();
    let plan = preview(&organizer, &fixture.published, &draft);
    let prepared = prepare(&organizer, &fixture.published, &plan);
    fs::write(
        fixture.published.files_by_id.get("one").unwrap(),
        b"changed",
    )
    .unwrap();
    assert!(organizer.execute(prepared).is_err());
    assert!(fixture.published.files_by_id.values().all(|p| p.exists()));
    cleanup(fixture.temp);
}

#[test]
fn subtitles_are_selected_exclusions_win_and_manual_deselection_survives() {
    let (fixture, organizer, draft, _) = setup(false);
    let path = fixture.root.join("[Group] Example Show - 01.zh-TW.ass");
    fs::write(&path, b"subtitle").unwrap();
    let mut draft = manual(&organizer, draft);
    let plan = preview(&organizer, &fixture.published, &draft);
    let sidecar = plan.batches[0]
        .nearby_files
        .iter()
        .find(|f| f.relative_path == path.to_string_lossy())
        .unwrap();
    assert!(sidecar.recommended && sidecar.selected);
    draft
        .companion_choices
        .insert(sidecar.relative_path.clone(), false);
    let mut draft = organizer.update_draft(draft).unwrap();
    assert!(
        preview(&organizer, &fixture.published, &draft).batches[0]
            .moves
            .iter()
            .all(|m| !m.source_relative_path.ends_with(".ass"))
    );
    draft
        .companion_choices
        .insert(path.to_string_lossy().into_owned(), true);
    draft
        .files
        .iter_mut()
        .find(|f| f.media_id == "one")
        .unwrap()
        .excluded = true;
    let draft = organizer.update_draft(draft).unwrap();
    assert!(
        preview(&organizer, &fixture.published, &draft).batches[0]
            .moves
            .iter()
            .all(|m| !m.source_relative_path.ends_with(".ass"))
    );
    cleanup(fixture.temp);
}

#[test]
fn explicit_identification_save_does_not_move_files_or_use_folder_title_as_provider_identity() {
    let (fixture, organizer, mut draft, _) = setup(false);
    let candidate = super::draft::IdentificationCandidate {
        anime_id: 42,
        episode_id: Some(4201),
        series_title: "Provider title".into(),
        episode_title: "Episode 1".into(),
    };
    organizer
        .runtime
        .lock()
        .unwrap()
        .draft
        .as_mut()
        .unwrap()
        .files[0]
        .candidates = vec![candidate.clone()];
    draft.files[0].candidate = Some(candidate.clone());
    draft.files[0].candidates = vec![candidate];
    draft.files[0].series_title = "My folder name".into();
    let draft = organizer.update_draft(draft).unwrap();
    let metadata =
        crate::catalog_metadata::CatalogMetadataStore::new(fixture.data.join("metadata.json"));
    organizer
        .save_identification(
            DraftPreviewRequest {
                draft_id: draft.id,
                revision: draft.revision,
                group_id: None,
            },
            &metadata,
        )
        .unwrap();
    let enriched = metadata.enrich_catalog(&fixture.published.catalog);
    assert_eq!(
        enriched.items[0]
            .anime_metadata
            .as_ref()
            .unwrap()
            .display_title,
        "Provider title"
    );
    assert!(fixture.published.files_by_id.values().all(|p| p.exists()));
    cleanup(fixture.temp);
}

#[test]
fn focused_preview_only_contains_the_requested_review_group() {
    let (fixture, organizer, mut draft, _) = setup(false);
    draft.files[0].group_id = "first".into();
    draft.files[1].group_id = "second".into();
    let draft = manual(&organizer, draft);
    let plan = organizer
        .preview_draft(
            &fixture.published,
            DraftPreviewRequest {
                draft_id: draft.id.clone(),
                revision: draft.revision,
                group_id: Some("first".into()),
            },
        )
        .unwrap();
    assert_eq!(plan.batches.len(), 1);
    assert_eq!(plan.batches[0].video_count, 1);
    cleanup(fixture.temp);
}

#[test]
fn legacy_journal_version_is_upgraded_without_losing_history() {
    let fixture = fixture();
    let store = CatalogStore::new(fixture.data.join("catalog.json"));
    store.save(fixture.published.clone()).unwrap();
    let organizer = LibraryOrganizer::new(vec![fixture.root.clone()], store);
    organizer
        .execute(PreparedOrganization {
            batch: super::tests::fixture_batch(&fixture),
            undo: false,
        })
        .unwrap();
    let journal_path = fixture.data.join("library-organization.json");
    let mut journal: serde_json::Value =
        serde_json::from_slice(&fs::read(&journal_path).unwrap()).unwrap();
    journal["schemaVersion"] = 1.into();
    for operation in journal["completed"][0]["batch"]["moves"]
        .as_array_mut()
        .unwrap()
    {
        for key in [
            "sourceRoot",
            "destinationRoot",
            "sourceSignature",
            "contentHash",
        ] {
            operation.as_object_mut().unwrap().remove(key);
        }
    }
    fs::write(&journal_path, serde_json::to_vec(&journal).unwrap()).unwrap();
    drop(organizer);
    let restored = LibraryOrganizer::new(
        vec![fixture.root.clone()],
        CatalogStore::new(fixture.data.join("catalog.json")),
    );
    assert!(restored.status().can_undo);
    assert_eq!(restored.runtime.lock().unwrap().journal.schema_version, 2);
    let undo = restored
        .prepare_undo(&restored.status().last_completed_batch_id.unwrap())
        .unwrap();
    let library = restored.execute(undo).unwrap();
    assert_eq!(library.files_by_id, fixture.published.files_by_id);
    assert_eq!(
        fs::read(library.files_by_id.get("one").unwrap()).unwrap(),
        b"one"
    );
    cleanup(fixture.temp);
}

#[test]
fn altered_manifest_is_rejected_and_cancelled_copy_preserves_sources() {
    let (fixture, organizer, draft, _) = setup(true);
    let draft = manual(&organizer, draft);
    let plan = preview(&organizer, &fixture.published, &draft);
    let mut altered = plan.batches[0].moves.clone();
    altered[0].destination_relative_path = "unapproved.mkv".into();
    assert!(
        organizer
            .prepare_execute(
                &fixture.published.catalog,
                OrganizationExecuteRequest {
                    plan_id: plan.plan_id.clone(),
                    batch_id: plan.batches[0].batch_id.clone(),
                    expected_moves: altered,
                }
            )
            .is_err()
    );
    let prepared = prepare(&organizer, &fixture.published, &plan);
    organizer.cancel_requested.store(true, Ordering::Release);
    assert!(organizer.execute(prepared).is_err());
    assert!(fixture.published.files_by_id.values().all(|p| p.exists()));
    assert!(organizer.runtime.lock().unwrap().journal.active.is_none());
    cleanup(fixture.temp);
}

#[test]
fn restart_after_undo_catalog_commit_revalidates_restored_draft() {
    let (fixture, organizer, draft, destination) = setup(true);
    let draft = manual(&organizer, draft);
    let plan = preview(&organizer, &fixture.published, &draft);
    organizer
        .execute(prepare(&organizer, &fixture.published, &plan))
        .unwrap();
    let undo = organizer
        .prepare_undo(&organizer.status().last_completed_batch_id.unwrap())
        .unwrap();
    *organizer.transfer_failpoint.lock().unwrap() = Some("catalog-committed".into());
    assert!(organizer.execute(undo).is_err());
    drop(organizer);
    let store = CatalogStore::new(fixture.data.join("catalog.json"));
    let restored = LibraryOrganizer::new(vec![fixture.root.clone(), destination], store.clone());
    let draft = restored.draft().unwrap().unwrap();
    assert!(draft.completed.is_empty());
    let catalog = store.load().unwrap().unwrap().published_library;
    assert!(preview(&restored, &catalog, &draft).batches[0].executable);
    cleanup(fixture.temp);
}

#[tokio::test]
async fn provider_failure_preserves_manual_review_and_stale_identification_is_discarded() {
    let (fixture, organizer, draft, _) = setup(false);
    let organizer = std::sync::Arc::new(organizer);
    let request = super::draft::IdentifyRequest {
        draft_id: draft.id.clone(),
        revision: draft.revision,
        media_ids: Vec::new(),
        query: None,
    };
    let snapshot = organizer.begin_identification(&request).unwrap();
    organizer.clone().identify(snapshot, request, None).await;
    let draft = organizer.draft().unwrap().unwrap();
    assert!(draft.files.iter().all(|f| f.identification_error.is_some()));
    let request = super::draft::IdentifyRequest {
        draft_id: draft.id.clone(),
        revision: draft.revision,
        media_ids: Vec::new(),
        query: None,
    };
    let snapshot = organizer.begin_identification(&request).unwrap();
    let edited = manual(&organizer, draft);
    organizer.clone().identify(snapshot, request, None).await;
    assert_eq!(organizer.draft().unwrap(), Some(edited));
    cleanup(fixture.temp);
}

#[test]
fn duplicate_destinations_across_groups_block_focused_approval() {
    let mut fixture = fixture();
    let second = fixture
        .root
        .join("other")
        .join("[Group] Example Show - 01.mkv");
    fs::create_dir_all(second.parent().unwrap()).unwrap();
    fs::rename(fixture.published.files_by_id.get("two").unwrap(), &second).unwrap();
    fixture
        .published
        .files_by_id
        .insert("two".into(), second.clone());
    fixture.published.catalog.items[1].relative_path =
        relative_wire_path(&fixture.root, &second).unwrap();
    let store = CatalogStore::new(fixture.data.join("catalog.json"));
    store.save(fixture.published.clone()).unwrap();
    let organizer = LibraryOrganizer::new(vec![fixture.root.clone()], store);
    let mut draft = organizer
        .create_draft(
            &fixture.published,
            CreateDraftRequest {
                media_ids: vec!["one".into(), "two".into()],
                destination: fixture.root.to_string_lossy().into_owned(),
            },
        )
        .unwrap();
    draft.files[0].group_id = "first".into();
    draft.files[1].group_id = "second".into();
    let draft = manual(&organizer, draft);
    let plan = organizer
        .preview_draft(
            &fixture.published,
            DraftPreviewRequest {
                draft_id: draft.id,
                revision: draft.revision,
                group_id: Some("first".into()),
            },
        )
        .unwrap();
    assert!(!plan.batches[0].executable);
    assert!(!plan.batches[0].conflicts.is_empty());
    assert!(fixture.published.files_by_id.values().all(|p| p.exists()));
    cleanup(fixture.temp);
}

#[test]
fn already_organized_video_can_move_an_explicit_companion() {
    let mut fixture = fixture();
    let target = fixture
        .root
        .join("Example Show/Season 1/[Group] Example Show - 01.mkv");
    fs::create_dir_all(target.parent().unwrap()).unwrap();
    fs::rename(fixture.published.files_by_id.get("one").unwrap(), &target).unwrap();
    fixture
        .published
        .files_by_id
        .insert("one".into(), target.clone());
    fixture.published.catalog.items[0].relative_path =
        relative_wire_path(&fixture.root, &target).unwrap();
    let artwork = target.parent().unwrap().join("poster.jpg");
    fs::write(&artwork, b"fixture artwork").unwrap();
    let store = CatalogStore::new(fixture.data.join("catalog.json"));
    store.save(fixture.published.clone()).unwrap();
    let organizer = LibraryOrganizer::new(vec![fixture.root.clone()], store);
    let draft = organizer
        .create_draft(
            &fixture.published,
            CreateDraftRequest {
                media_ids: vec!["one".into()],
                destination: fixture.root.to_string_lossy().into_owned(),
            },
        )
        .unwrap();
    let draft = manual(&organizer, draft);
    let initial = preview(&organizer, &fixture.published, &draft);
    assert!(!initial.batches[0].nearby_files.is_empty(), "{initial:?}");
    let key = initial.batches[0].nearby_files[0].relative_path.clone();
    let mut draft = draft;
    draft.companion_choices.insert(key.clone(), true);
    draft.companion_owners.insert(key, "one".into());
    let draft = organizer.update_draft(draft).unwrap();
    let plan = preview(&organizer, &fixture.published, &draft);
    assert_eq!(plan.batches[0].moves.len(), 1);
    assert!(plan.batches[0].moves[0].media_id.is_none());
    let updated = organizer
        .execute(prepare(&organizer, &fixture.published, &plan))
        .unwrap();
    assert_eq!(updated.files_by_id.get("one"), Some(&target));
    assert!(fixture.root.join("Example Show/poster.jpg").exists());
    cleanup(fixture.temp);
}
