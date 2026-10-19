use super::draft::{
    CompanionOwner, CreateDraftRequest, DraftOp, DraftOpsRequest, DraftPreviewRequest,
    IdentificationCandidate, OrganizationDraft,
};
use super::identify::IdentifyRequest;
use super::tests::{Fixture, cleanup, fixture};
use super::*;
use crate::dandanplay::DandanplayResolver;
use std::sync::Arc;

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
                replace: false,
            },
        )
        .unwrap();
    (fixture, organizer, draft, destination)
}

fn ops(
    organizer: &LibraryOrganizer,
    draft: &OrganizationDraft,
    ops: Vec<DraftOp>,
) -> OrganizationDraft {
    organizer
        .apply_ops(DraftOpsRequest {
            draft_id: draft.id.clone(),
            ops,
        })
        .unwrap()
}

fn group(draft: &OrganizationDraft, media_id: &str) -> String {
    draft
        .files
        .iter()
        .find(|f| f.media_id == media_id)
        .unwrap()
        .group_id
        .clone()
}

fn ids(values: &[&str]) -> Vec<String> {
    values.iter().map(|v| (*v).to_owned()).collect()
}

/// Accept folder titles for every open group.
fn manual(organizer: &LibraryOrganizer, draft: OrganizationDraft) -> OrganizationDraft {
    let groups = draft
        .files
        .iter()
        .filter(|f| !draft.completed.contains_key(&f.group_id))
        .map(|f| f.group_id.clone())
        .collect::<BTreeSet<_>>();
    let confirm = groups
        .into_iter()
        .map(|group_id| DraftOp::ConfirmGroup { group_id })
        .collect();
    ops(organizer, &draft, confirm)
}

/// Simulates provider results arriving for the stored draft.
fn provide(organizer: &LibraryOrganizer, media_id: &str, candidates: Vec<IdentificationCandidate>) {
    let mut runtime = organizer.runtime.lock().unwrap();
    let draft = runtime.draft.as_mut().unwrap();
    draft
        .files
        .iter_mut()
        .find(|f| f.media_id == media_id)
        .unwrap()
        .candidates = candidates;
}

fn preview_group(
    organizer: &LibraryOrganizer,
    library: &PublishedLibrary,
    draft: &OrganizationDraft,
    group_id: &str,
) -> OrganizationPlan {
    organizer
        .preview_draft(
            library,
            DraftPreviewRequest {
                draft_id: draft.id.clone(),
                group_id: group_id.into(),
            },
        )
        .unwrap()
}

/// Preview the first open group.
fn preview(
    organizer: &LibraryOrganizer,
    library: &PublishedLibrary,
    draft: &OrganizationDraft,
) -> OrganizationPlan {
    let group = draft
        .files
        .iter()
        .find(|f| !draft.completed.contains_key(&f.group_id))
        .unwrap_or(&draft.files[0])
        .group_id
        .clone();
    preview_group(organizer, library, draft, &group)
}

fn unreachable_resolver(fixture: &Fixture) -> Arc<DandanplayResolver> {
    use crate::dandanplay::{
        DandanplayCommentCacheStore, DandanplayConnection, DandanplayDanmakuClient,
    };
    Arc::new(DandanplayResolver::new(
        DandanplayDanmakuClient::new(DandanplayConnection::new(
            "http://127.0.0.1:9",
            Some("app".into()),
            Some("secret".into()),
            crate::settings::HeadlessDandanplayAuthenticationMode::Signed,
        )),
        DandanplayCommentCacheStore::new(fixture.data.join("cache.json")),
        1,
        crate::catalog::current_epoch_ms,
    ))
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
    let (fixture, organizer, draft, destination) = setup(false);
    let draft = ops(
        &organizer,
        &draft,
        vec![
            DraftOp::MoveFiles {
                media_ids: ids(&["one"]),
                group_id: None,
                title: Some("Changed".into()),
            },
            DraftOp::SetSeason {
                media_ids: ids(&["one"]),
                season: 0,
            },
            DraftOp::SetExcluded {
                media_ids: ids(&["two"]),
                excluded: true,
            },
        ],
    );
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
    let (fixture, organizer, draft, destination) = setup(false);
    let draft = ops(
        &organizer,
        &draft,
        vec![DraftOp::MoveFiles {
            media_ids: ids(&["two"]),
            group_id: None,
            title: Some("Second".into()),
        }],
    );
    let draft = manual(&organizer, draft);
    let plan = preview_group(
        &organizer,
        &fixture.published,
        &draft,
        &group(&draft, "one"),
    );
    // Edits in another group keep this approval valid.
    let other = ops(
        &organizer,
        &draft,
        vec![DraftOp::SetSeason {
            media_ids: ids(&["two"]),
            season: 4,
        }],
    );
    assert!(
        organizer
            .prepare_execute(
                &fixture.published.catalog,
                OrganizationExecuteRequest {
                    plan_id: plan.plan_id.clone(),
                    batch_id: plan.batches[0].batch_id.clone(),
                    expected_moves: plan.batches[0].moves.clone()
                }
            )
            .is_ok()
    );
    organizer.runtime.lock().unwrap().status = OrganizationStatus::default();
    let plan = preview_group(
        &organizer,
        &fixture.published,
        &other,
        &group(&other, "one"),
    );
    let updated = ops(
        &organizer,
        &other,
        vec![DraftOp::SetDestination {
            destination: destination.join("edited").display().to_string(),
        }],
    );
    assert_eq!(updated.files, other.files);
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
    assert!(
        organizer
            .apply_ops(DraftOpsRequest {
                draft_id: "replaced".into(),
                ops: Vec::new(),
            })
            .is_err()
    );
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
    let (fixture, organizer, draft, _) = setup(false);
    let draft = ops(
        &organizer,
        &draft,
        vec![
            DraftOp::MoveFiles {
                media_ids: ids(&["two"]),
                group_id: None,
                title: Some("Second edited".into()),
            },
            DraftOp::SetSeason {
                media_ids: ids(&["two"]),
                season: 7,
            },
        ],
    );
    let draft = manual(&organizer, draft);
    let plan = preview_group(
        &organizer,
        &fixture.published,
        &draft,
        &group(&draft, "one"),
    );
    organizer
        .execute(prepare(&organizer, &fixture.published, &plan))
        .unwrap();
    let after = organizer.draft().unwrap().unwrap();
    assert_eq!(after.files, draft.files);
    assert!(after.completed.contains_key(&group(&draft, "one")));
    assert!(!after.completed.contains_key(&group(&draft, "two")));
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
    let draft = manual(&organizer, draft);
    let plan = preview(&organizer, &fixture.published, &draft);
    let sidecar = plan.batches[0]
        .nearby_files
        .iter()
        .find(|f| f.relative_path == path.to_string_lossy())
        .unwrap();
    assert!(sidecar.recommended && sidecar.selected);
    let draft = ops(
        &organizer,
        &draft,
        vec![DraftOp::SetCompanion {
            path: sidecar.relative_path.clone(),
            selected: false,
        }],
    );
    assert!(
        preview(&organizer, &fixture.published, &draft).batches[0]
            .moves
            .iter()
            .all(|m| !m.source_relative_path.ends_with(".ass"))
    );
    let draft = ops(
        &organizer,
        &draft,
        vec![
            DraftOp::SetCompanion {
                path: path.to_string_lossy().into_owned(),
                selected: true,
            },
            DraftOp::SetExcluded {
                media_ids: ids(&["one"]),
                excluded: true,
            },
        ],
    );
    assert!(
        preview(&organizer, &fixture.published, &draft).batches[0]
            .moves
            .iter()
            .all(|m| !m.source_relative_path.ends_with(".ass"))
    );
    cleanup(fixture.temp);
}

#[test]
fn identification_save_does_not_move_files_or_use_folder_title_as_provider_identity() {
    let (fixture, organizer, draft, _) = setup(false);
    let candidate = IdentificationCandidate {
        anime_id: 42,
        episode_id: Some(4201),
        series_title: "Provider title".into(),
        episode_title: "Episode 1".into(),
    };
    provide(&organizer, "one", vec![candidate.clone()]);
    let folder_title = draft.files[0].series_title.clone();
    // Choosing one video's episode never renames or splits its series.
    let draft = ops(
        &organizer,
        &draft,
        vec![DraftOp::SetMatch {
            media_ids: ids(&["one"]),
            candidate: Some(candidate.clone()),
        }],
    );
    assert_eq!(draft.files[0].series_title, folder_title);
    let members = draft
        .files
        .iter()
        .filter(|f| f.group_id == group(&draft, "one"))
        .map(|f| f.media_id.clone())
        .collect::<Vec<_>>();
    // A match for the whole series files it under the provider title.
    let draft = ops(
        &organizer,
        &draft,
        vec![DraftOp::SetMatch {
            media_ids: members,
            candidate: Some(candidate),
        }],
    );
    assert_eq!(draft.files[0].series_title, "Provider title");
    let draft = ops(
        &organizer,
        &draft,
        vec![DraftOp::RenameGroup {
            group_id: group(&draft, "one"),
            title: "My folder name".into(),
        }],
    );
    assert_eq!(draft.files[0].series_title, "My folder name");
    let metadata =
        crate::catalog_metadata::CatalogMetadataStore::new(fixture.data.join("metadata.json"));
    organizer.save_identification(None, &metadata).unwrap();
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
    let (fixture, organizer, draft, _) = setup(false);
    let draft = ops(
        &organizer,
        &draft,
        vec![DraftOp::MoveFiles {
            media_ids: ids(&["two"]),
            group_id: None,
            title: Some("Second".into()),
        }],
    );
    let draft = manual(&organizer, draft);
    let plan = preview_group(
        &organizer,
        &fixture.published,
        &draft,
        &group(&draft, "one"),
    );
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
async fn edits_during_identification_are_kept_and_results_merge_per_file() {
    let (fixture, organizer, draft, _) = setup(false);
    let server = crate::dandanplay::tests::TestServer::start();
    let resolver = Arc::new(crate::dandanplay::tests::test_resolver(
        &server,
        &fixture.data.join("cache.json"),
    ));
    let organizer = Arc::new(organizer);
    let run = organizer
        .begin_identification(&IdentifyRequest {
            draft_id: draft.id.clone(),
            media_ids: Vec::new(),
        })
        .unwrap();
    assert!(organizer.identification_status().running);
    // A user edit while identification runs neither cancels nor loses it.
    ops(
        &organizer,
        &draft,
        vec![DraftOp::SetSeason {
            media_ids: ids(&["two"]),
            season: 3,
        }],
    );
    organizer.clone().identify(run, resolver).await;
    let draft = organizer.draft().unwrap().unwrap();
    assert!(!organizer.identification_status().running);
    assert!(draft.files.iter().all(|f| f.candidate.is_some()));
    assert!(
        draft
            .files
            .iter()
            .all(|f| f.series_title == "Example Anime")
    );
    assert_eq!(draft.files[1].season_number, Some(3));
    assert_eq!(draft.files[1].season_evidence, "MANUAL");
    assert_eq!(group(&draft, "one"), group(&draft, "two"));
    cleanup(fixture.temp);
}

#[tokio::test]
async fn manual_titles_survive_identification_and_failures_keep_review_usable() {
    let (fixture, organizer, draft, _) = setup(false);
    let draft = ops(
        &organizer,
        &draft,
        vec![DraftOp::RenameGroup {
            group_id: group(&draft, "one"),
            title: "My Folder".into(),
        }],
    );
    let organizer = Arc::new(organizer);
    let run = organizer
        .begin_identification(&IdentifyRequest {
            draft_id: draft.id.clone(),
            media_ids: Vec::new(),
        })
        .unwrap();
    organizer
        .clone()
        .identify(run, unreachable_resolver(&fixture))
        .await;
    let draft = organizer.draft().unwrap().unwrap();
    assert!(draft.files.iter().all(|f| f.identification_error.is_some()));
    assert!(draft.files.iter().all(|f| f.series_title == "My Folder"));
    assert!(preview(&organizer, &fixture.published, &draft).batches[0].executable);
    cleanup(fixture.temp);
}

#[test]
fn duplicate_destinations_block_approval() {
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
    let draft = organizer
        .create_draft(
            &fixture.published,
            CreateDraftRequest {
                media_ids: vec!["one".into(), "two".into()],
                destination: fixture.root.to_string_lossy().into_owned(),
                replace: false,
            },
        )
        .unwrap();
    // Same file name and series title in two folders: one destination.
    assert_eq!(group(&draft, "one"), group(&draft, "two"));
    let draft = manual(&organizer, draft);
    let plan = preview(&organizer, &fixture.published, &draft);
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
                replace: false,
            },
        )
        .unwrap();
    let draft = manual(&organizer, draft);
    let initial = preview(&organizer, &fixture.published, &draft);
    assert!(initial.batches[0].already_organized || !initial.batches[0].nearby_files.is_empty());
    assert!(!initial.batches[0].nearby_files.is_empty(), "{initial:?}");
    let key = initial.batches[0].nearby_files[0].relative_path.clone();
    let draft = ops(
        &organizer,
        &draft,
        vec![
            DraftOp::SetCompanion {
                path: key.clone(),
                selected: true,
            },
            DraftOp::SetCompanionOwner {
                path: key,
                owner: CompanionOwner::Video {
                    media_id: "one".into(),
                },
            },
        ],
    );
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

#[tokio::test]
async fn repeated_provider_failure_pauses_and_survives_restart_without_touching_remaining_files() {
    let (fixture, organizer, mut draft, destination) = setup(false);
    // Extend the immutable test snapshot with IDs sharing the same fixture path.
    for index in 0..5 {
        let mut file = draft.files[0].clone();
        file.media_id = format!("extra-{index}");
        draft.files.push(file);
    }
    organizer.runtime.lock().unwrap().draft = Some(draft.clone());
    let organizer = Arc::new(organizer);
    let request = IdentifyRequest {
        draft_id: draft.id.clone(),
        media_ids: Vec::new(),
    };
    let run = organizer.begin_identification(&request).unwrap();
    organizer
        .clone()
        .identify(run, unreachable_resolver(&fixture))
        .await;
    let saved = organizer.draft().unwrap().unwrap();
    assert_eq!(
        saved
            .files
            .iter()
            .filter(|f| f.identification_error.is_some())
            .count(),
        3
    );
    assert!(saved.identification_pause.is_some());
    assert!(!organizer.identification_status().running);
    assert_eq!(organizer.identification_status().completed, 3);
    drop(organizer);
    let restored = LibraryOrganizer::new(
        vec![fixture.root.clone(), destination],
        CatalogStore::new(fixture.data.join("catalog.json")),
    );
    let saved = restored.draft().unwrap().unwrap();
    assert!(restored.begin_identification(&request).is_err());
    let edited = ops(
        &restored,
        &saved,
        vec![DraftOp::RenameGroup {
            group_id: saved.files[0].group_id.clone(),
            title: "Still editable while paused".into(),
        }],
    );
    assert!(edited.identification_pause.is_some());
    assert!(edited.retry_not_before_epoch_ms.is_some());
    cleanup(fixture.temp);
}

#[test]
fn series_companions_move_once_to_series_folder_across_seasons_and_undo() {
    let (fixture, organizer, draft, destination) = setup(true);
    let group = draft.files[0].group_id.clone();
    let title = draft.files[0].series_title.clone();
    let mut edits = vec![DraftOp::SetSeason {
        media_ids: ids(&["two"]),
        season: 0,
    }];
    for name in ["fonts.ttf", "subtitles.zip"] {
        let path = fixture.root.join(name);
        fs::write(&path, name.as_bytes()).unwrap();
        let key = path.to_string_lossy().into_owned();
        edits.push(DraftOp::SetCompanion {
            path: key.clone(),
            selected: true,
        });
        edits.push(DraftOp::SetCompanionOwner {
            path: key,
            owner: CompanionOwner::Series {
                group_id: group.clone(),
            },
        });
    }
    let draft = ops(&organizer, &draft, edits);
    let draft = manual(&organizer, draft);
    // Choices survive restart without becoming approvals.
    drop(organizer);
    let organizer = LibraryOrganizer::new(
        vec![fixture.root.clone(), destination.clone()],
        CatalogStore::new(fixture.data.join("catalog.json")),
    );
    organizer.force_copy.store(true, Ordering::Relaxed);
    assert_eq!(organizer.draft().unwrap().unwrap(), draft);
    let plan = preview(&organizer, &fixture.published, &draft);
    let batch = &plan.batches[0];
    assert_eq!(batch.moves.len(), 4);
    for name in ["fonts.ttf", "subtitles.zip"] {
        let movement = batch
            .moves
            .iter()
            .find(|m| m.source_relative_path == name)
            .unwrap();
        assert_eq!(
            movement.destination_relative_path,
            format!("{title}/{name}")
        );
        assert!(movement.media_id.is_none());
        let nearby = batch
            .nearby_files
            .iter()
            .find(|n| n.relative_path.ends_with(name))
            .unwrap();
        assert_eq!(nearby.owner_group_id.as_deref(), Some(group.as_str()));
        assert!(nearby.owner_media_id.is_none());
    }
    let updated = organizer
        .execute(prepare(&organizer, &fixture.published, &plan))
        .unwrap();
    assert_eq!(
        updated.files_by_id.len(),
        fixture.published.files_by_id.len()
    );
    let undo = organizer
        .prepare_undo(&organizer.status().last_completed_batch_id.unwrap())
        .unwrap();
    organizer.execute(undo).unwrap();
    for name in ["fonts.ttf", "subtitles.zip"] {
        assert_eq!(fs::read(fixture.root.join(name)).unwrap(), name.as_bytes());
        assert!(!destination.join(&title).join(name).exists());
    }
    cleanup(fixture.temp);
}

#[test]
fn series_companions_require_one_explicit_owner_and_respect_deselection() {
    let (fixture, organizer, draft, _) = setup(false);
    let path = fixture.root.join("subtitles.zip");
    fs::write(&path, b"archive").unwrap();
    let key = path.to_string_lossy().into_owned();
    let draft = ops(
        &organizer,
        &draft,
        vec![DraftOp::SetCompanion {
            path: key.clone(),
            selected: true,
        }],
    );
    let draft = manual(&organizer, draft);
    assert!(!preview(&organizer, &fixture.published, &draft).batches[0].executable);
    let owned = ops(
        &organizer,
        &draft,
        vec![
            DraftOp::SetCompanionOwner {
                path: key.clone(),
                owner: CompanionOwner::Video {
                    media_id: "one".into(),
                },
            },
            DraftOp::SetCompanionOwner {
                path: key.clone(),
                owner: CompanionOwner::Series {
                    group_id: draft.files[0].group_id.clone(),
                },
            },
        ],
    );
    // Choosing a series owner replaces the video owner: one owner, one move.
    assert!(owned.companion_owners.is_empty());
    let plan = preview(&organizer, &fixture.published, &owned);
    assert!(plan.batches[0].executable);
    assert_eq!(
        plan.batches[0]
            .moves
            .iter()
            .filter(|m| m.source_relative_path.ends_with(".zip"))
            .count(),
        1
    );
    let draft = ops(
        &organizer,
        &owned,
        vec![DraftOp::SetCompanion {
            path: key,
            selected: false,
        }],
    );
    let plan = preview(&organizer, &fixture.published, &draft);
    assert!(plan.batches[0].executable);
    assert!(
        plan.batches[0]
            .moves
            .iter()
            .all(|m| !m.source_relative_path.ends_with(".zip"))
    );
    cleanup(fixture.temp);
}

#[test]
fn series_only_can_reuse_known_provider_without_borrowing_an_episode() {
    let (fixture, organizer, draft, _) = setup(false);
    let episode = IdentificationCandidate {
        anime_id: 42,
        series_title: "Provider title".into(),
        episode_id: Some(420001),
        episode_title: "Episode 1".into(),
    };
    provide(&organizer, "one", vec![episode.clone()]);
    let mut series = episode.clone();
    series.episode_id = None;
    series.episode_title.clear();
    let draft = ops(
        &organizer,
        &draft,
        vec![
            DraftOp::SetMatch {
                media_ids: ids(&["one"]),
                candidate: Some(episode),
            },
            DraftOp::MoveFiles {
                media_ids: ids(&["two"]),
                group_id: None,
                title: Some("My specials folder".into()),
            },
            DraftOp::SetMatch {
                media_ids: ids(&["two"]),
                candidate: Some(series.clone()),
            },
        ],
    );
    assert!(draft.files[1].series_only);
    assert_eq!(draft.files[1].series_title, "My specials folder");
    let path = fixture.data.join("metadata.json");
    let metadata = crate::catalog_metadata::CatalogMetadataStore::new(&path);
    organizer.save_identification(None, &metadata).unwrap();
    let reloaded = crate::catalog_metadata::CatalogMetadataStore::new(&path);
    let special = reloaded.get(&draft.files[1].media_id).unwrap();
    assert!(special.series_only);
    assert_eq!(special.anime_title, "Provider title");
    assert_eq!(special.dandanplay_episode_id, None);
    assert_eq!(special.episode_title, None);
    assert_eq!(
        reloaded
            .get(&draft.files[0].media_id)
            .unwrap()
            .dandanplay_episode_id,
        Some(420001)
    );
    series.anime_id = 999;
    assert!(
        organizer
            .apply_ops(DraftOpsRequest {
                draft_id: draft.id.clone(),
                ops: vec![DraftOp::SetMatch {
                    media_ids: ids(&["two"]),
                    candidate: Some(series),
                }],
            })
            .is_err()
    );
    assert!(fixture.published.files_by_id.values().all(|p| p.exists()));
    cleanup(fixture.temp);
}

#[test]
fn series_assets_are_discovered_above_season_folders_without_a_video_owner() {
    let mut fixture = fixture();
    let series = fixture.root.join("Example Show");
    for (index, item) in fixture.published.catalog.items.iter_mut().enumerate() {
        let original = fixture.published.files_by_id[&item.id].clone();
        let target = series
            .join(format!("Season {}", index + 1))
            .join(original.file_name().unwrap());
        fs::create_dir_all(target.parent().unwrap()).unwrap();
        fs::rename(&original, &target).unwrap();
        item.relative_path = relative_wire_path(&fixture.root, &target).unwrap();
        fixture
            .published
            .files_by_id
            .insert(item.id.clone(), target);
    }
    let asset = series.join("subtitle-pack.zip");
    fs::write(&asset, b"shared subtitles").unwrap();
    let destination = fixture.temp.join("destination");
    fs::create_dir(&destination).unwrap();
    let store = CatalogStore::new(fixture.data.join("catalog.json"));
    store.save(fixture.published.clone()).unwrap();
    let organizer = LibraryOrganizer::new(vec![fixture.root.clone(), destination], store);
    let draft = organizer
        .create_draft(
            &fixture.published,
            CreateDraftRequest {
                media_ids: vec!["one".into(), "two".into()],
                destination: fixture.temp.join("destination").display().to_string(),
                replace: false,
            },
        )
        .unwrap();
    let key = asset.to_string_lossy().into_owned();
    let draft = ops(
        &organizer,
        &draft,
        vec![
            DraftOp::SetCompanion {
                path: key.clone(),
                selected: true,
            },
            DraftOp::SetCompanionOwner {
                path: key,
                owner: CompanionOwner::Series {
                    group_id: draft.files[0].group_id.clone(),
                },
            },
            // The shared archive is not dependent on this video.
            DraftOp::SetExcluded {
                media_ids: ids(&["one"]),
                excluded: true,
            },
        ],
    );
    let draft = manual(&organizer, draft);
    let plan = preview(&organizer, &fixture.published, &draft);
    assert!(plan.batches[0].executable);
    assert_eq!(
        plan.batches[0]
            .moves
            .iter()
            .filter(|m| m.source_relative_path.ends_with(".zip"))
            .count(),
        1
    );
    assert_eq!(plan.batches[0].nearby_files[0].owner_media_id, None);
    assert!(
        plan.batches[0]
            .moves
            .iter()
            .all(|m| m.media_id.as_deref() != Some("one"))
    );
    cleanup(fixture.temp);
}

/// Adds a video in `folder` under the fixture root with catalog title `folder_title`.
fn add_video(fixture: &mut Fixture, id: &str, relative: &str, folder_title: &str) {
    let path = fixture.root.join(relative);
    fs::create_dir_all(path.parent().unwrap()).unwrap();
    fs::write(&path, id.as_bytes()).unwrap();
    let mut item = fixture.published.catalog.items[0].clone();
    item.id = id.into();
    item.series_title = folder_title.into();
    item.episode_title = path.file_stem().unwrap().to_string_lossy().into_owned();
    item.relative_path = relative.into();
    item.size_bytes = id.len() as u64;
    fixture.published.catalog.items.push(item);
    fixture.published.files_by_id.insert(id.into(), path);
}

#[test]
fn download_dumps_group_by_file_title_in_natural_order_and_series_folders_keep_their_name() {
    let mut fixture = fixture();
    for episode in [10, 2, 1] {
        add_video(
            &mut fixture,
            &format!("frieren-{episode}"),
            &format!("Downloads/[Sub] Sousou no Frieren - {episode:02} (1080p).mkv"),
            "Downloads",
        );
    }
    add_video(
        &mut fixture,
        "kusuriya",
        "Downloads/Kusuriya no Hitorigoto S2 - E1.mkv",
        "Downloads",
    );
    add_video(
        &mut fixture,
        "clip",
        "Downloads/random clip.mp4",
        "Downloads",
    );
    add_video(
        &mut fixture,
        "bocchi",
        "Bocchi the Rock/Bocchi the Rock! - 01.mp4",
        "Bocchi the Rock",
    );
    let store = CatalogStore::new(fixture.data.join("catalog.json"));
    store.save(fixture.published.clone()).unwrap();
    let organizer = LibraryOrganizer::new(vec![fixture.root.clone()], store);
    let draft = organizer
        .create_draft(
            &fixture.published,
            CreateDraftRequest {
                media_ids: fixture.published.files_by_id.keys().cloned().collect(),
                destination: fixture.root.display().to_string(),
                replace: false,
            },
        )
        .unwrap();
    let title = |id: &str| {
        draft
            .files
            .iter()
            .find(|f| f.media_id == id)
            .unwrap()
            .series_title
            .clone()
    };
    assert_eq!(title("frieren-1"), "Sousou no Frieren");
    assert_eq!(title("kusuriya"), "Kusuriya no Hitorigoto");
    assert_eq!(
        draft
            .files
            .iter()
            .find(|f| f.media_id == "kusuriya")
            .unwrap()
            .season_number,
        Some(2)
    );
    assert_eq!(title("clip"), "Downloads");
    assert_eq!(title("bocchi"), "Bocchi the Rock");
    let frieren = draft
        .files
        .iter()
        .filter(|f| f.media_id.starts_with("frieren"))
        .map(|f| f.media_id.as_str())
        .collect::<Vec<_>>();
    assert_eq!(frieren, ["frieren-1", "frieren-2", "frieren-10"]);
    assert_eq!(group(&draft, "frieren-1"), group(&draft, "frieren-10"));
    cleanup(fixture.temp);
}

#[test]
fn a_new_selection_replaces_an_unfinished_review_only_when_asked() {
    let (fixture, organizer, draft, destination) = setup(false);
    let request = |replace| CreateDraftRequest {
        media_ids: vec!["two".into()],
        destination: destination.display().to_string(),
        replace,
    };
    assert!(
        organizer
            .create_draft(&fixture.published, request(false))
            .is_err()
    );
    let replaced = organizer
        .create_draft(&fixture.published, request(true))
        .unwrap();
    assert_ne!(replaced.id, draft.id);
    assert_eq!(replaced.files.len(), 1);
    assert!(
        organizer
            .apply_ops(DraftOpsRequest {
                draft_id: draft.id,
                ops: Vec::new(),
            })
            .is_err()
    );
    cleanup(fixture.temp);
}

#[test]
fn already_organized_groups_can_be_marked_done_and_stay_done_after_restart() {
    let mut fixture = fixture();
    add_video(
        &mut fixture,
        "placed",
        "Example Show/Season 1/[Group] Example Show - 03.mkv",
        "Example Show",
    );
    let store = CatalogStore::new(fixture.data.join("catalog.json"));
    store.save(fixture.published.clone()).unwrap();
    let organizer = LibraryOrganizer::new(vec![fixture.root.clone()], store);
    let draft = organizer
        .create_draft(
            &fixture.published,
            CreateDraftRequest {
                media_ids: vec!["placed".into()],
                destination: fixture.root.display().to_string(),
                replace: false,
            },
        )
        .unwrap();
    // Files already in place can be finished without a match or confirmation.
    let plan = preview(&organizer, &fixture.published, &draft);
    assert!(plan.batches[0].already_organized);
    assert!(!plan.batches[0].executable);
    let group = group(&draft, "placed");
    ops(
        &organizer,
        &draft,
        vec![DraftOp::MarkDone {
            group_id: group.clone(),
        }],
    );
    drop(organizer);
    let restored = LibraryOrganizer::new(
        vec![fixture.root.clone()],
        CatalogStore::new(fixture.data.join("catalog.json")),
    );
    assert!(
        restored
            .draft()
            .unwrap()
            .unwrap()
            .completed
            .contains_key(&group)
    );
    cleanup(fixture.temp);
}

#[test]
fn extras_already_in_the_series_folder_stay_listed_without_a_move() {
    let mut fixture = fixture();
    for episode in [10, 2] {
        add_video(
            &mut fixture,
            &format!("bocchi-{episode}"),
            &format!("Bocchi the Rock/Bocchi the Rock - {episode:02}.mkv"),
            "Bocchi the Rock",
        );
    }
    let fonts = fixture.root.join("Bocchi the Rock").join("fonts.zip");
    fs::write(&fonts, b"fonts").unwrap();
    let store = CatalogStore::new(fixture.data.join("catalog.json"));
    store.save(fixture.published.clone()).unwrap();
    let organizer = LibraryOrganizer::new(vec![fixture.root.clone()], store);
    let draft = organizer
        .create_draft(
            &fixture.published,
            CreateDraftRequest {
                media_ids: ids(&["bocchi-10", "bocchi-2"]),
                destination: fixture.root.display().to_string(),
                replace: false,
            },
        )
        .unwrap();
    let key = fonts.to_string_lossy().into_owned();
    let before = preview(&organizer, &fixture.published, &draft);
    let listed = &before.batches[0].nearby_files[0];
    // Owner choices follow episode order, not hashed IDs.
    assert_eq!(listed.owner_media_ids, ids(&["bocchi-2", "bocchi-10"]));
    assert!(listed.destination_relative_path.is_some());
    let draft = ops(
        &organizer,
        &draft,
        vec![
            DraftOp::SetCompanion {
                path: key.clone(),
                selected: true,
            },
            DraftOp::SetCompanionOwner {
                path: key.clone(),
                owner: CompanionOwner::Series {
                    group_id: group(&draft, "bocchi-2"),
                },
            },
        ],
    );
    let draft = manual(&organizer, draft);
    let plan = preview(&organizer, &fixture.published, &draft);
    let batch = &plan.batches[0];
    let fonts_entry = batch
        .nearby_files
        .iter()
        .find(|n| n.relative_path == key)
        .expect("ticked extra stays listed");
    assert_eq!(fonts_entry.destination_relative_path, None);
    assert!(
        batch
            .moves
            .iter()
            .all(|m| !m.source_relative_path.ends_with("fonts.zip"))
    );
    assert!(batch.conflicts.is_empty(), "{:?}", batch.conflicts);
    cleanup(fixture.temp);
}
