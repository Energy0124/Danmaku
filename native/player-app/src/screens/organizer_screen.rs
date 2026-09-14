//! Organizer review workspace; network operations are dispatched through LibrarySession.
use super::library_screen::LibraryAction;
use crate::library::{OrganizationDraft, OrganizationPreviewRequest};
use crate::localization::{OrganizerText as T, Strings};
use crate::session::LibrarySession;
use eframe::egui;
use std::collections::{BTreeMap, BTreeSet};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

#[derive(Default)]
pub(super) struct OrganizerScreen {
    pub open: bool,
    pub selection: Vec<String>,
    draft: Option<OrganizationDraft>,
    baseline: Option<OrganizationDraft>,
    submitted: Option<OrganizationDraft>,
    observed: Option<(String, u64)>,
    preview_requested: Option<(String, u64)>,
    changed: Option<Instant>,
    saving: bool,
    save_failed: bool,
    closing: bool,
    search: String,
    filter: String,
    selected_files: BTreeSet<String>,
    assign_title: String,
    assign_season: String,
    assign_group: String,
    assign_provider: Option<crate::library::IdentificationCandidate>,
    search_title: String,
    destination: String,
    folder_result: Arc<Mutex<Option<String>>>,
    index: ReviewIndex,
    index_key: Option<(String, u64, u64)>,
    edit_generation: u64,
    catalog_version: u64,
}

fn command(method: &str, endpoint: &str, body: impl serde::Serialize) -> LibraryAction {
    LibraryAction::OrganizerCommand {
        method: method.into(),
        endpoint: endpoint.into(),
        body: serde_json::to_string(&body).expect("serializable organizer request"),
    }
}

impl OrganizerScreen {
    pub fn needs_tick(&self) -> bool {
        self.changed.is_some() || self.saving
    }

    pub fn prepare_close(&mut self, requested: bool) -> bool {
        self.closing |= requested;
        if self.closing && self.needs_tick() {
            if self.changed.is_some() {
                self.changed = Some(Instant::now() - Duration::from_secs(1));
            }
            return false;
        }
        true
    }

    pub fn finish_close(&mut self) -> bool {
        if self.closing && !self.needs_tick() {
            self.closing = false;
            true
        } else {
            false
        }
    }

    pub fn show(
        &mut self,
        ctx: &egui::Context,
        session: &LibrarySession,
        s: Strings,
    ) -> Option<LibraryAction> {
        let status = session.organization_status.as_ref();
        if self.catalog_version != session.catalog_version {
            self.catalog_version = session.catalog_version;
            self.preview_requested = None;
        }
        if let (Some(sent), Some(saved), Some(local)) = (
            &self.submitted,
            &session.organization_saved_draft,
            &self.draft,
        ) {
            if sent.id == saved.id && saved.revision > sent.revision {
                let merged = rebase_decisions(sent, local, saved);
                self.changed = (merged != *saved).then(Instant::now);
                self.draft = Some(merged);
                self.baseline = Some(saved.clone());
                self.observed = Some((saved.id.clone(), saved.revision));
                self.preview_requested = None;
                self.submitted = None;
                self.saving = false;
            }
        }
        let remote = status.and_then(|s| s.draft.as_ref());
        let revision = remote.map(|d| (d.id.clone(), d.revision));
        let newer = match (&revision, &self.observed) {
            (Some((id, rev)), Some((old_id, old_rev))) => id != old_id || rev > old_rev,
            _ => revision != self.observed,
        };
        if newer && !(self.saving && session.organization_loading) {
            self.draft = match (self.baseline.as_ref(), self.draft.as_ref(), remote) {
                (Some(base), Some(local), Some(remote))
                    if self.changed.is_some() && base.id == remote.id =>
                {
                    Some(rebase_decisions(base, local, remote))
                }
                _ => remote.cloned(),
            };
            self.changed = if self.draft.as_ref() != remote {
                Some(Instant::now())
            } else {
                None
            };
            self.baseline = remote.cloned();
            self.observed = revision;
            self.preview_requested = None;
            self.saving = false;
        }
        let busy = status.is_some_and(|s| matches!(s.state.as_str(), "RUNNING" | "ROLLING_BACK"));
        let identifying = status.is_some_and(|s| s.identification.running);
        let retry_wait = self
            .draft
            .as_ref()
            .and_then(|d| d.retry_not_before_epoch_ms)
            .map(|until| {
                let now = std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .unwrap_or_default()
                    .as_millis() as u64;
                until.saturating_sub(now).div_ceil(1000)
            })
            .unwrap_or(0);
        if retry_wait > 0 {
            ctx.request_repaint_after(Duration::from_secs(1));
        }
        if let Some(draft) = &self.draft {
            let key = (draft.id.clone(), draft.revision, self.edit_generation);
            if self.index_key.as_ref() != Some(&key) {
                self.index = ReviewIndex::build(draft);
                self.index_key = Some(key);
            }
        }
        let mut action = None;
        let mut changed = false;
        let mut open = self.open;
        if open {
            ctx.show_viewport_immediate(
            egui::ViewportId::from_hash_of("library-organizer"),
            egui::ViewportBuilder::default().with_title(s.organizer_title())
                .with_inner_size([1100.0,720.0]).with_min_inner_size([700.0,480.0]).with_taskbar(true),
            |ctx, class| {
            if class != egui::ViewportClass::Embedded && ctx.input(|i| i.viewport().close_requested()) {
                open = false;
            }
            let content = |ui: &mut egui::Ui| {
            ui.heading(s.organizer_text(T::ReviewWorkflow));
            egui::CollapsingHeader::new(s.organizer_text(T::UsageGuide)).id_salt("organizer-help").show(ui, |ui| {
                ui.label(s.organizer_text(T::UsageSteps));
                ui.label(s.organizer_text(T::UsageSeriesOnly));
                ui.label(s.organizer_text(T::UsageCompanions));
            });
            if let Some(error) = &session.organization_error {
                ui.colored_label(egui::Color32::LIGHT_RED, error);
                if ui.button(s.organizer_text(T::Reload)).clicked() {
                    self.saving = false; self.save_failed=false;
                    // Keep local edits for an explicit retry when the server revision is unchanged.
                    if self.observed != remote.map(|d| (d.id.clone(),d.revision)) { self.changed = None; }
                    action = Some(LibraryAction::RefreshOrganizationStatus);
                }
            }
            if let Some(status) = status {
                ui.label(s.organizer_status(&status.state, status.completed_operations, status.total_operations));
                if let Some(message) = &status.message { ui.label(message); }
                if busy {
                    ui.add(egui::ProgressBar::new(status.completed_bytes as f32 / status.total_bytes.max(1) as f32).show_percentage());
                    if ui.button(s.organizer_cancel()).clicked() { action = Some(LibraryAction::CancelOrganization); }
                } else if status.state == "RECOVERY_REQUIRED" {
                    if ui.button(s.organizer_text(T::RetryRecovery)).clicked() { action = Some(command("POST", "recover", serde_json::json!({}))); }
                } else if status.can_undo && ui.button(s.undo_last_series()).clicked() {
                    if let Some(id) = &status.last_completed_batch_id { action = Some(LibraryAction::UndoOrganization { completed_batch_id:id.clone() }); }
                }
            }
            if identifying {
                ui.horizontal(|ui| {
                    ui.spinner(); ui.label(s.organizer_text(T::Identifying));
                    if let Some(status) = status { ui.label(format!("{} / {}",status.identification.completed,status.identification.total)); }
                    if ui.button(s.organizer_text(T::StopIdentifying)).clicked() { action = Some(command("POST","identify/cancel",serde_json::json!({}))); }
                });
            }
            let picked = self.folder_result.lock().expect("folder dialog result").take();
            if let Some(path) = picked {
                if let Some(draft) = &mut self.draft { draft.destination = path; changed = true; } else { self.destination = path; }
            }
            if self.draft.is_none() {
                ui.label(s.organizer_text(T::SelectHint));
                ui.label(format!("{} {}",self.selection.len(),s.organizer_text(T::SelectedVideos)));
                if self.destination.is_empty() {
                    if let Some(catalog) = &session.catalog {
                        let roots = catalog.items.iter().filter(|i| self.selection.contains(&i.id)).filter_map(|i| i.root_label.as_ref()).collect::<BTreeSet<_>>();
                        if roots.len() == 1 { self.destination = (*roots.first().unwrap()).clone(); }
                    }
                }
                destination_row(ui, &mut self.destination, &self.folder_result, s);
                if ui.add_enabled(!self.selection.is_empty() && !self.destination.trim().is_empty() && !session.organization_loading,egui::Button::new(s.organizer_text(T::StartReview))).clicked() {
                    action = Some(command("POST","draft",serde_json::json!({"mediaIds":self.selection,"destination":self.destination})));
                }
                return;
            }
            let draft = self.draft.as_mut().unwrap();
            if let Some(reason) = &draft.identification_pause {
                ui.colored_label(egui::Color32::YELLOW, s.organizer_text(T::IdentificationPaused));
                ui.label(reason);
                if retry_wait > 0 { ui.label(format!("{} {retry_wait}s", s.organizer_text(T::RetryAfter))); }
            }
            ui.add_enabled_ui(!busy && !session.organization_mutating, |ui| {
                ui.horizontal(|ui| {
                    ui.label(s.organizer_text(if self.saving || self.changed.is_some() { T::SavingDraft } else { T::SavedDraft }));
                    ui.add_enabled_ui(!session.organization_loading && !self.saving && self.changed.is_none(), |ui| {
                    if ui.button(s.organizer_text(T::Discard)).clicked() {
                        action = Some(command("DELETE","draft",serde_json::json!({})));
                    }
                    if ui.add_enabled(!identifying && retry_wait == 0 && self.changed.is_none(), egui::Button::new(s.organizer_text(T::RetryIdentification))).clicked() {
                        action = Some(command("POST","identify",serde_json::json!({"draftId":draft.id,"revision":draft.revision,"mediaIds":self.selected_files})));
                    }
                    if ui.add_enabled(self.changed.is_none() && !identifying, egui::Button::new(s.organizer_text(T::SaveIdentification))).clicked() {
                        action = Some(command("POST","identify/save",serde_json::json!({"draftId":draft.id,"revision":draft.revision})));
                    }
                    });
                });
                egui::CollapsingHeader::new(format!("{}: {}", s.organizer_text(T::Destination), draft.destination)).id_salt("organizer-destination").show(ui, |ui| {
                    changed |= destination_row(ui, &mut draft.destination, &self.folder_result, s);
                });
                ui.heading(s.organizer_text(T::StepSeries));
                let groups = &self.index.titles;
                ui.horizontal(|ui| {
                    ui.label(s.organizer_text(T::Search)); ui.text_edit_singleline(&mut self.search);
                    egui::ComboBox::from_id_salt("organizer-filter").selected_text(filter_label(&self.filter,s)).show_ui(ui,|ui| {
                        for key in ["","pending","review","blocked","skipped","completed"] { ui.selectable_value(&mut self.filter,key.into(),filter_label(key,s)); }
                    });
                });
                ui.columns(2, |columns| {
                    let visible = groups.iter().filter(|(id,title)| {
                        if !title.to_lowercase().contains(&self.search.to_lowercase()) { return false; }
                        let complete = draft.completed.contains_key(*id);
                        let skipped = draft.skipped.contains(*id);
                        let batch = session.organization_plan.as_ref().and_then(|p| p.batches.iter().find(|b| &b.batch_id == *id));
                        let review = self.index.needs_review.contains(*id);
                        match self.filter.as_str() { "completed"=>complete,"skipped"=>skipped,"review"=>review&&!complete,"blocked"=>batch.is_some_and(|b| !b.conflicts.is_empty()),"pending"=>!complete&&!skipped,_=>true }
                    }).collect::<Vec<_>>();
                    egui::ScrollArea::vertical().id_salt("organizer-queue").max_height(220.0).show_rows(&mut columns[0],24.0,visible.len(),|ui,range| {
                        for index in range {
                            let (id,title) = visible[index];
                            let count = self.index.counts.get(id).copied().unwrap_or(0);
                            let state = if draft.completed.contains_key(id) { s.organizer_text(T::Completed) } else if draft.skipped.contains(id) { s.organizer_text(T::Skipped) } else { "" };
                            if ui.selectable_label(draft.active_group.as_ref()==Some(id),format!("{title} · {count} {state}")).clicked() {
                                draft.active_group = Some(id.clone()); self.selected_files.clear(); changed = true;
                            }
                        }
                    });
                    columns[1].label(s.organizer_text(T::UsageSteps));
                    columns[1].label(s.organizer_text(T::UsageSeriesOnly));
                });
                let Some(group) = draft.active_group.clone() else { ui.label(s.organizer_text(T::ReviewComplete)); return; };
                ui.separator();
                let complete = draft.completed.contains_key(&group);
                let indices = self.index.members.get(&group).cloned().unwrap_or_default();
                ui.heading(format!("{} · {}", s.organizer_text(T::StepFiles), groups.get(&group).map(String::as_str).unwrap_or("")));
                ui.label(format!("{}: {}", s.organizer_text(T::SelectedVideos), self.selected_files.len()));
                ui.horizontal(|ui| {
                    if ui.button(s.organizer_text(T::SelectVisible)).clicked() { self.selected_files.extend(indices.iter().map(|i|draft.files[*i].media_id.clone())); }
                    if ui.button(s.organizer_text(T::Clear)).clicked() { self.selected_files.clear(); }
                    ui.text_edit_singleline(&mut self.search_title);
                    if ui.add_enabled(!session.organization_loading && self.changed.is_none() && !self.saving && !identifying && retry_wait == 0 && !self.search_title.trim().is_empty() && !self.selected_files.is_empty(),egui::Button::new(s.organizer_text(T::SearchProvider))).clicked() {
                        action = Some(command("POST","identify",serde_json::json!({"draftId":draft.id,"revision":draft.revision,"mediaIds":self.selected_files,"query":self.search_title})));
                    }
                });
                egui::ScrollArea::vertical().id_salt("organizer-files").max_height(300.0).show_rows(ui,155.0,indices.len(),|ui,range| {
                    for index in range {
                        let file = &mut draft.files[indices[index]];
                        ui.add_enabled_ui(!complete, |ui| { ui.push_id(&file.media_id,|ui| {
                            ui.horizontal(|ui| {
                                let mut selected = self.selected_files.contains(&file.media_id);
                                if ui.checkbox(&mut selected, &file.source_relative_path).changed() {
                                    if selected { self.selected_files.insert(file.media_id.clone()); } else { self.selected_files.remove(&file.media_id); }
                                }
                                changed |= ui.checkbox(&mut file.excluded,s.organizer_text(T::Exclude)).changed();
                            });
                            ui.label(format!("{}: {}",s.organizer_text(T::Source),file.source_root));
                            ui.horizontal(|ui| {
                                ui.label(s.season_number_label());
                                let mut season = file.season_number.unwrap_or(1);
                                if ui.add(egui::DragValue::new(&mut season).range(0..=9999)).changed() || (file.season_number.is_none() && ui.button(s.organizer_text(T::ConfirmSeason)).clicked()) {
                                    file.season_number = Some(season); file.season_evidence="MANUAL".into(); changed=true;
                                }
                                ui.label(match file.season_evidence.as_str(){"SUGGESTED"=>s.organizer_text(T::SeasonSuggested),"CONFLICT"=>s.organizer_text(T::SeasonConflict),"MANUAL"=>s.organizer_text(T::Manual),_=>s.organizer_text(T::Parsed)});
                                changed |= ui.checkbox(&mut file.manual_assignment,s.organizer_text(T::ManualAssignment)).changed();
                            });
                            changed |= ui.checkbox(&mut file.series_only, s.organizer_text(T::SeriesOnly)).on_hover_text(s.organizer_text(T::UsageSeriesOnly)).changed();
                            if let Some(title)=&file.provider_title { ui.label(format!("{}: {title}",s.organizer_text(T::Identified))); }
                            if !file.candidates.is_empty() {
                                egui::ComboBox::from_id_salt("candidate").selected_text(file.candidate.as_ref().map(|c|c.series_title.as_str()).unwrap_or(s.organizer_text(T::ChooseMatch))).show_ui(ui,|ui|{
                                    for candidate in &file.candidates {
                                        if ui.selectable_label(file.candidate.as_ref()==Some(candidate),format!("{} · {}",candidate.series_title, if file.series_only || candidate.episode_id.is_none() {s.organizer_text(T::NoEpisode)} else {&candidate.episode_title})).clicked(){
                                            file.series_only |= candidate.episode_id.is_none(); file.candidate=Some(candidate.clone()); file.series_title=candidate.series_title.clone(); file.group_id=format!("dandanplay-{}",candidate.anime_id); file.manual_assignment=false; changed=true;
                                        }
                                    }
                                });
                            }
                            if let Some(error)=&file.identification_error { ui.label(error); }
                            if identifying && status.and_then(|s| s.identification.media_id.as_ref()) == Some(&file.media_id) { ui.spinner(); ui.label(s.organizer_text(T::Identifying)); }
                        }); });
                    }
                });
                egui::CollapsingHeader::new(s.organizer_text(T::EditSelected)).id_salt("organizer-bulk").default_open(false).show(ui, |ui| {
                    ui.add_enabled_ui(!complete && !self.selected_files.is_empty(), |ui| {
                    ui.label(s.organizer_text(T::AssignmentHint));
                    ui.horizontal(|ui| {
                        ui.label(s.series_title_label()); ui.text_edit_singleline(&mut self.assign_title);
                    });
                    ui.horizontal(|ui| {
                        ui.label(s.season_number_label()); ui.add(egui::TextEdit::singleline(&mut self.assign_season).desired_width(60.0));
                    });
                    let valid_season = self.assign_season.trim().parse::<u32>().ok();
                    if !self.assign_season.is_empty() && valid_season.is_none() { ui.colored_label(egui::Color32::LIGHT_RED,s.organizer_text(T::InvalidSeason)); }
                    egui::ComboBox::from_id_salt("assign-group").selected_text(groups.get(&self.assign_group).map(String::as_str).unwrap_or(s.organizer_text(T::NewGroup))).show_ui(ui,|ui| {
                        ui.selectable_value(&mut self.assign_group,String::new(),s.organizer_text(T::NewGroup));
                        for (id,title) in groups { if !draft.completed.contains_key(id) { ui.selectable_value(&mut self.assign_group,id.clone(),title); } }
                    });
                    if ui.add_enabled(!self.selected_files.is_empty() && valid_season.is_some() && (!self.assign_title.trim().is_empty() || groups.contains_key(&self.assign_group)),egui::Button::new(s.organizer_text(T::AssignSelected))).clicked() {
                        let group = if self.assign_group.is_empty() { format!("manual-{}",std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_nanos()) } else { self.assign_group.clone() };
                        let title = groups.get(&self.assign_group).cloned().unwrap_or_else(||self.assign_title.trim().into());
                        for file in draft.files.iter_mut().filter(|f| self.selected_files.contains(&f.media_id) && !draft.completed.contains_key(&f.group_id)) {
                            file.group_id = group.clone(); file.series_title = title.clone(); file.season_number = valid_season; file.season_evidence = "MANUAL".into(); file.manual_assignment = true;
                        }
                        draft.active_group = Some(group); changed = true;
                    }
                    if ui.button(s.organizer_text(T::ExcludeSelected)).clicked() {
                        for file in draft.files.iter_mut().filter(|f| self.selected_files.contains(&f.media_id) && !draft.completed.contains_key(&f.group_id)) { file.excluded = true; }
                        changed = true;
                    }
                    ui.separator();
                    ui.label(s.organizer_text(T::LinkKnownSeriesHint));
                    egui::ComboBox::from_id_salt("organizer-known-series").selected_text(self.assign_provider.as_ref().map(|p|p.series_title.as_str()).unwrap_or(s.organizer_text(T::ChooseMatch))).show_ui(ui, |ui| {
                        for candidate in self.index.providers.values() { ui.selectable_value(&mut self.assign_provider, Some(candidate.clone()), &candidate.series_title); }
                    });
                    if ui.add_enabled(!self.selected_files.is_empty() && self.assign_provider.is_some(), egui::Button::new(s.organizer_text(T::LinkSeriesSelected))).clicked() {
                        let provider = self.assign_provider.clone().unwrap();
                        for file in draft.files.iter_mut().filter(|f|self.selected_files.contains(&f.media_id) && !draft.completed.contains_key(&f.group_id)) {
                            file.candidate=Some(provider.clone()); file.series_only=true; file.manual_assignment=false;
                        }
                        changed=true;
                    }
                    });
                });
                ui.heading(s.organizer_text(T::StepCompanions));
                let plan_current = session.organization_plan.as_ref().is_some_and(|p|p.draft_id==draft.id&&p.draft_revision==draft.revision) && self.changed.is_none() && !changed && !self.saving && self.preview_requested == self.observed;
                if let Some(batch) = session.organization_plan.as_ref().and_then(|p|p.batches.iter().find(|b|b.batch_id==group)) {
                    egui::CollapsingHeader::new(s.nearby_files_label()).id_salt("organizer-companions").default_open(true).show(ui,|ui|{
                        egui::ScrollArea::vertical().id_salt("organizer-companion-list").max_height(220.0).show_rows(ui,56.0,batch.nearby_files.len(),|ui,range| {
                        for index in range {
                            let nearby = &batch.nearby_files[index];
                            ui.allocate_ui(egui::vec2(ui.available_width(), 56.0), |ui| {
                            ui.style_mut().wrap_mode = Some(egui::TextWrapMode::Truncate);
                            let mut selected = draft.companion_choices.get(&nearby.relative_path).copied().unwrap_or(nearby.selected);
                            let label = if nearby.recommended {format!("{} · {}",nearby.relative_path,s.organizer_text(T::MatchingSubtitle))}else{nearby.relative_path.clone()};
                            if ui.checkbox(&mut selected,label).on_hover_text(&nearby.relative_path).changed(){draft.companion_choices.insert(nearby.relative_path.clone(),selected);changed=true;}
                            if selected {
                                let manual_video = draft.companion_owners.get(&nearby.relative_path);
                                let manual_series = draft.companion_series_owners.get(&nearby.relative_path);
                                let selected_owner = if manual_series.is_some() { None } else { manual_video.or(nearby.owner_media_id.as_ref()).cloned() };
                                let series_owner = if manual_video.is_some() { None } else { manual_series.or(nearby.owner_group_id.as_ref()).cloned() };
                                let video_label = selected_owner.as_ref().and_then(|id|draft.files.iter().find(|f|&f.media_id==id)).map(|f|f.source_relative_path.as_str()).unwrap_or(s.organizer_text(T::ChooseCompanionOwner));
                                let label = series_owner.as_ref().and_then(|id|groups.get(id)).map(|title|format!("{}: {title}",s.organizer_text(T::SeriesCompanion))).unwrap_or_else(||video_label.to_owned());
                                egui::ComboBox::from_id_salt((&nearby.relative_path,"owner")).selected_text(label).show_ui(ui,|ui|{
                                    for id in &nearby.owner_group_ids {
                                        if ui.selectable_label(series_owner.as_ref()==Some(id),format!("{}: {}",s.organizer_text(T::SeriesCompanion),groups.get(id).map(String::as_str).unwrap_or(id))).clicked() {
                                            draft.companion_series_owners.insert(nearby.relative_path.clone(), id.clone());
                                            draft.companion_owners.remove(&nearby.relative_path); changed=true;
                                        }
                                    }
                                    for id in &nearby.owner_media_ids {
                                        if let Some(file)=draft.files.iter().find(|f|&f.media_id==id) {
                                            if ui.selectable_label(selected_owner.as_ref()==Some(id),&file.source_relative_path).clicked(){draft.companion_owners.insert(nearby.relative_path.clone(),id.clone());draft.companion_series_owners.remove(&nearby.relative_path);changed=true;}
                                        }
                                    }
                                });
                            }
                            });
                        }
                        });
                    });
                    ui.heading(s.organizer_text(T::StepApprove));
                    if !plan_current { ui.label(s.organizer_text(T::PreviewPending)); }
                    for conflict in &batch.conflicts { ui.colored_label(egui::Color32::LIGHT_RED,conflict); }
                    ui.label(s.approved_moves(batch.moves.len()));
                    egui::ScrollArea::both().id_salt("organizer-manifest").max_height(180.0).show_rows(ui,24.0,batch.moves.len(),|ui,range|{
                        for index in range {
                            let movement=&batch.moves[index];
                            ui.horizontal(|ui| {
                                ui.label(format!("{}/{}",movement.source_root.as_deref().unwrap_or(""),movement.source_relative_path));
                                ui.label("→");
                                ui.label(format!("{}/{}",movement.destination_root.as_deref().unwrap_or(""),movement.destination_relative_path));
                            });
                        }
                    });
                    if ui.add_enabled(plan_current && !session.organization_loading && !changed && !complete && batch.executable && !identifying,egui::Button::new(s.organizer_text(T::ApproveNext))).clicked(){
                        action=Some(LibraryAction::ExecuteOrganization{plan_id:session.organization_plan.as_ref().unwrap().plan_id.clone(),batch:batch.clone()});
                    }
                } else { ui.label(s.organizer_text(T::UpdatingPreview)); }
                if !complete && ui.button(s.organizer_text(T::SkipNext)).clicked(){
                    draft.skipped.insert(group.clone());
                    draft.active_group=draft.files.iter().find(|f|!f.excluded&&!draft.completed.contains_key(&f.group_id)&&!draft.skipped.contains(&f.group_id)).map(|f|f.group_id.clone());changed=true;
                }
                if draft.skipped.contains(&group) && ui.button(s.organizer_text(T::ReturnPending)).clicked(){draft.skipped.remove(&group);changed=true;}
            });
            };
            if class == egui::ViewportClass::Embedded {
                egui::Window::new(s.organizer_title()).open(&mut open).vscroll(true).show(ctx, content);
            } else {
                egui::CentralPanel::default().show(ctx, |ui| { egui::ScrollArea::vertical().show(ui, content); });
            }
        });
        }
        self.open = open;
        if changed {
            self.edit_generation += 1;
            self.save_failed = false;
            if let Some(draft) = &mut self.draft {
                if draft
                    .active_group
                    .as_ref()
                    .is_some_and(|g| !draft.files.iter().any(|f| &f.group_id == g))
                {
                    draft.active_group = draft.files.first().map(|f| f.group_id.clone());
                }
            }
            self.changed = Some(Instant::now());
            self.preview_requested = None;
        }
        if self.saving && !session.organization_loading && session.organization_error.is_some() {
            self.saving = false;
            self.save_failed = true;
        }
        if action.is_none() && !busy && !session.organization_loading && !self.saving {
            if let Some(draft) = &self.draft {
                if !self.save_failed
                    && self
                        .changed
                        .is_some_and(|at| at.elapsed() >= Duration::from_millis(350))
                {
                    self.submitted = Some(draft.clone());
                    action = Some(command("PUT", "draft", draft));
                    self.saving = true;
                } else if self.changed.is_none()
                    && !identifying
                    && self.preview_requested != self.observed
                    && !draft.destination.trim().is_empty()
                {
                    action = Some(LibraryAction::PreviewOrganization(
                        OrganizationPreviewRequest {
                            group_id: draft.active_group.clone(),
                            draft_id: draft.id.clone(),
                            revision: draft.revision,
                        },
                    ));
                    self.preview_requested = self.observed.clone();
                }
            }
        }
        if self.changed.is_some() || self.saving {
            ctx.request_repaint_after(Duration::from_millis(100));
        }
        action
    }
}

#[derive(Default)]
struct ReviewIndex {
    titles: BTreeMap<String, String>,
    members: BTreeMap<String, Vec<usize>>,
    counts: BTreeMap<String, usize>,
    needs_review: BTreeSet<String>,
    providers: BTreeMap<u64, crate::library::IdentificationCandidate>,
}

impl ReviewIndex {
    fn build(draft: &OrganizationDraft) -> Self {
        let mut index = Self::default();
        for (i, file) in draft.files.iter().enumerate() {
            for candidate in file.candidates.iter().chain(file.candidate.iter()) {
                index
                    .providers
                    .entry(candidate.anime_id)
                    .or_insert_with(|| crate::library::IdentificationCandidate {
                        anime_id: candidate.anime_id,
                        series_title: candidate.series_title.clone(),
                        episode_id: None,
                        episode_title: String::new(),
                    });
            }
            index
                .titles
                .entry(file.group_id.clone())
                .or_insert(file.series_title.clone());
            index
                .members
                .entry(file.group_id.clone())
                .or_default()
                .push(i);
            if !file.excluded {
                *index.counts.entry(file.group_id.clone()).or_default() += 1;
                if file.season_number.is_none()
                    || (!file.manual_assignment
                        && file.candidate.is_none()
                        && file.provider_title.is_none())
                {
                    index.needs_review.insert(file.group_id.clone());
                }
            }
        }
        index
    }
}

/// Apply only local review decisions to fresh provider/catalog state.
fn rebase_decisions(
    base: &OrganizationDraft,
    local: &OrganizationDraft,
    remote: &OrganizationDraft,
) -> OrganizationDraft {
    let mut merged = remote.clone();
    if local.destination != base.destination {
        merged.destination = local.destination.clone();
    }
    if local.active_group != base.active_group {
        merged.active_group = local.active_group.clone();
    }
    if local.skipped != base.skipped {
        merged.skipped = local.skipped.clone();
    }
    rebase_map(
        &base.companion_choices,
        &local.companion_choices,
        &mut merged.companion_choices,
    );
    rebase_map(
        &base.companion_owners,
        &local.companion_owners,
        &mut merged.companion_owners,
    );
    rebase_map(
        &base.companion_series_owners,
        &local.companion_series_owners,
        &mut merged.companion_series_owners,
    );
    for file in &mut merged.files {
        if merged.completed.contains_key(&file.group_id) {
            continue;
        }
        if let (Some(before), Some(after)) = (
            base.files.iter().find(|f| f.media_id == file.media_id),
            local.files.iter().find(|f| f.media_id == file.media_id),
        ) {
            if before.series_only != after.series_only {
                file.series_only = after.series_only;
            }
            if before.series_title != after.series_title {
                file.series_title = after.series_title.clone();
            }
            if before.group_id != after.group_id {
                file.group_id = after.group_id.clone();
            }
            if before.season_number != after.season_number {
                file.season_number = after.season_number;
                file.season_evidence = after.season_evidence.clone();
            }
            if before.excluded != after.excluded {
                file.excluded = after.excluded;
            }
            if before.manual_assignment != after.manual_assignment {
                file.manual_assignment = after.manual_assignment;
            }
            if before.candidate != after.candidate {
                file.candidate = after.candidate.clone();
            }
        }
    }
    merged
}

fn destination_row(
    ui: &mut egui::Ui,
    value: &mut String,
    result: &Arc<Mutex<Option<String>>>,
    s: Strings,
) -> bool {
    let mut changed = false;
    ui.horizontal(|ui| {
        ui.label(s.organizer_text(T::Destination));
        changed = ui
            .add(egui::TextEdit::singleline(value).desired_width(650.0))
            .changed();
        if ui.button(s.organizer_text(T::Browse)).clicked() {
            let result = Arc::clone(result);
            let ctx = ui.ctx().clone();
            std::thread::spawn(move || {
                if let Some(path) = rfd::FileDialog::new().pick_folder() {
                    *result.lock().expect("folder dialog result") =
                        Some(path.to_string_lossy().into_owned());
                }
                ctx.request_repaint();
            });
        }
    });
    changed
}

fn filter_label(filter: &str, s: Strings) -> &'static str {
    s.organizer_text(match filter {
        "pending" => T::Pending,
        "review" => T::NeedsReview,
        "blocked" => T::Blocked,
        "skipped" => T::Skipped,
        "completed" => T::Completed,
        _ => T::All,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::library::{DraftFile, IdentificationCandidate};

    fn draft() -> OrganizationDraft {
        OrganizationDraft {
            id: "draft".into(),
            revision: 1,
            files: vec![DraftFile {
                media_id: "one".into(),
                group_id: "group".into(),
                series_title: "Original".into(),
                season_number: Some(1),
                ..Default::default()
            }],
            ..Default::default()
        }
    }

    #[test]
    fn new_identification_preserves_local_season_and_destination_edits() {
        let base = draft();
        let mut local = base.clone();
        local.files[0].season_number = Some(3);
        local.files[0].season_evidence = "MANUAL".into();
        local.destination = "new destination".into();
        let mut remote = base.clone();
        remote.revision = 2;
        remote.files[0].series_title = "Identified".into();
        remote.files[0].candidate = Some(IdentificationCandidate {
            anime_id: 42,
            series_title: "Identified".into(),
            ..Default::default()
        });
        let merged = rebase_decisions(&base, &local, &remote);
        assert_eq!(merged.revision, 2);
        assert_eq!(merged.files[0].season_number, Some(3));
        assert_eq!(merged.files[0].series_title, "Identified");
        assert!(merged.files[0].candidate.is_some());
        assert_eq!(merged.destination, "new destination");
    }

    #[test]
    fn acknowledged_save_clears_dirty_state_without_reapplying_old_revision() {
        let base = draft();
        let mut local = base.clone();
        local.files[0].excluded = true;
        let mut acknowledged = local.clone();
        acknowledged.revision += 1;
        assert_eq!(rebase_decisions(&base, &local, &acknowledged), acknowledged);
    }

    #[test]
    fn queue_index_handles_large_catalog_without_io() {
        let mut draft = draft();
        draft.files = (0..10_000)
            .map(|n| DraftFile {
                media_id: n.to_string(),
                group_id: (n / 10).to_string(),
                manual_assignment: true,
                season_number: Some(1),
                ..Default::default()
            })
            .collect();
        let index = ReviewIndex::build(&draft);
        assert_eq!(index.members.len(), 1_000);
        assert_eq!(index.members["99"], (990..1000).collect::<Vec<_>>());
        assert!(index.needs_review.is_empty());
    }

    #[test]
    fn edits_during_save_preserve_reverted_checkboxes_and_switched_companion_owner() {
        let original = draft();
        let mut submitted = original.clone();
        submitted.files[0].excluded = true;
        submitted
            .companion_owners
            .insert("fonts.ttf".into(), "one".into());
        let mut local = submitted.clone();
        local.files[0].excluded = false;
        local.files[0].series_only = true;
        local.companion_owners.remove("fonts.ttf");
        local
            .companion_series_owners
            .insert("fonts.ttf".into(), "group".into());
        let mut saved = submitted.clone();
        saved.revision += 1;
        let merged = rebase_decisions(&submitted, &local, &saved);
        assert!(!merged.files[0].excluded);
        assert!(merged.files[0].series_only);
        assert!(merged.companion_owners.is_empty());
        assert_eq!(merged.companion_series_owners["fonts.ttf"], "group");
        assert_eq!(merged.revision, saved.revision);
    }

    #[test]
    fn close_waits_for_debounced_draft_save() {
        let mut screen = OrganizerScreen {
            changed: Some(Instant::now()),
            ..Default::default()
        };
        assert!(!screen.prepare_close(true));
        assert!(!screen.finish_close());
        screen.changed = None;
        screen.saving = true;
        assert!(!screen.finish_close());
        screen.saving = false;
        assert!(screen.finish_close());
    }
}

fn rebase_map<V: Clone + PartialEq>(
    base: &BTreeMap<String, V>,
    local: &BTreeMap<String, V>,
    remote: &mut BTreeMap<String, V>,
) {
    for key in base.keys().chain(local.keys()) {
        if base.get(key) != local.get(key) {
            if let Some(value) = local.get(key) {
                remote.insert(key.clone(), value.clone());
            } else {
                remote.remove(key);
            }
        }
    }
}
