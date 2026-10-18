//! Library organizer window: series queue on the left, the selected series in
//! the middle, and a fixed action bar that always shows what blocks approval.
//!
//! The window renders the latest server draft from `LibrarySession` and turns
//! every edit into a `DraftOp`; it keeps only UI state (selection, filters,
//! in-progress text) locally.
use super::library_screen::LibraryAction;
use super::organizer_review::{
    GroupMatch, GroupState, GroupSummary, QueueFilter, ReviewModel, group_match,
    group_match_options, preview_key,
};
use crate::library::{
    CompanionOwner, DraftOp, IdentificationCandidate, OrganizationDraft, OrganizationSeriesBatch,
    OrganizerRequest,
};
use crate::localization::{OrganizerText as T, Strings};
use crate::session::LibrarySession;
use crate::theme::{palette, typography};
use eframe::egui::{self, Color32, RichText};
use egui_extras::{Column, TableBuilder};
use std::collections::BTreeSet;
use std::sync::{Arc, Mutex};
use std::time::Duration;

const ROW_HEIGHT: f32 = 30.0;
const QUEUE_ROW_HEIGHT: f32 = 46.0;
const ATTENTION: Color32 = Color32::from_rgb(240, 180, 90);

#[derive(Clone, Copy, Default, PartialEq, Eq)]
enum DetailTab {
    #[default]
    Videos,
    Extras,
    Moves,
}

pub(super) struct OrganizerScreen {
    pub open: bool,
    /// Videos chosen in Folders for the next review.
    pending_selection: Option<Vec<String>>,
    destination_input: String,
    destination_draft: Option<String>,
    folder_pick: Arc<Mutex<Option<String>>>,
    model: ReviewModel,
    model_key: Option<(String, u64)>,
    active: Option<String>,
    /// A video of the active series, followed when series merge or rename.
    anchor: Option<String>,
    /// After approve/skip/mark done, move on once the series leaves the to-do list.
    advance_from: Option<String>,
    filter: QueueFilter,
    queue_search: String,
    title_input: String,
    title_focused: bool,
    destination_focused: bool,
    selected: BTreeSet<String>,
    range_anchor: Option<usize>,
    tab: DetailTab,
    match_panel: bool,
    match_query: String,
    move_title: String,
    bulk_season: u32,
    season_drag: Option<(String, u32)>,
    help: bool,
    /// Every series is done or skipped; show the summary instead of a series.
    finished: bool,
    closing: bool,
}

impl Default for OrganizerScreen {
    fn default() -> Self {
        Self {
            open: false,
            pending_selection: None,
            destination_input: String::new(),
            destination_draft: None,
            folder_pick: Arc::default(),
            model: ReviewModel::default(),
            model_key: None,
            active: None,
            anchor: None,
            advance_from: None,
            filter: QueueFilter::ToDo,
            queue_search: String::new(),
            title_input: String::new(),
            title_focused: false,
            destination_focused: false,
            selected: BTreeSet::new(),
            range_anchor: None,
            tab: DetailTab::Videos,
            match_panel: false,
            match_query: String::new(),
            move_title: String::new(),
            bulk_season: 1,
            season_drag: None,
            help: false,
            finished: false,
            closing: false,
        }
    }
}

/// Collects requests produced while drawing one frame.
struct Out(Vec<LibraryAction>);

impl Out {
    fn ops(&mut self, ops: Vec<DraftOp>) {
        if !ops.is_empty() {
            self.0.push(LibraryAction::OrganizerOps(ops));
        }
    }
    fn request(&mut self, request: OrganizerRequest) {
        self.0.push(LibraryAction::Organizer(request));
    }
}

impl OrganizerScreen {
    /// Opens the window; `selection` starts (or offers to replace) a review.
    pub fn open_with(&mut self, selection: Option<Vec<String>>) -> LibraryAction {
        self.open = true;
        if selection.is_some() {
            self.pending_selection = selection;
            self.destination_input.clear();
        }
        LibraryAction::Organizer(OrganizerRequest::Status)
    }

    pub fn needs_tick(&self, session: &LibrarySession) -> bool {
        self.closing && session.organizer.saving()
    }

    /// Delays closing the app until queued edits reach the server.
    pub fn prepare_close(&mut self, requested: bool, saving: bool) -> bool {
        self.closing |= requested;
        !(self.closing && saving)
    }

    pub fn finish_close(&mut self, saving: bool) -> bool {
        if self.closing && !saving {
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
    ) -> Vec<LibraryAction> {
        let mut out = Out(Vec::new());
        if let Some(path) = self
            .folder_pick
            .lock()
            .ok()
            .and_then(|mut pick| pick.take())
        {
            self.destination_input = path.clone();
            if session.organizer.draft.is_some() {
                out.ops(vec![DraftOp::SetDestination { destination: path }]);
            }
        }
        self.sync(session);
        self.request_preview(session, &mut out);
        if self.open {
            let mut open = true;
            ctx.show_viewport_immediate(
                egui::ViewportId::from_hash_of("library-organizer"),
                egui::ViewportBuilder::default()
                    .with_title(s.organizer_text(T::WindowTitle))
                    .with_inner_size([1180.0, 760.0])
                    .with_min_inner_size([860.0, 540.0])
                    .with_taskbar(true),
                |ctx, class| {
                    if class == egui::ViewportClass::Embedded {
                        egui::Window::new(s.organizer_text(T::WindowTitle))
                            .default_size([1100.0, 700.0])
                            .resizable(true)
                            .collapsible(false)
                            .open(&mut open)
                            .show(ctx, |ui| self.content(ui, session, s, &mut out));
                    } else {
                        if ctx.input(|i| i.viewport().close_requested()) {
                            open = false;
                        }
                        egui::CentralPanel::default()
                            .frame(egui::Frame::NONE.fill(palette::BG_PANEL))
                            .show(ctx, |ui| self.content(ui, session, s, &mut out));
                    }
                },
            );
            self.open = open;
        }
        let paused = session
            .organizer
            .draft
            .as_ref()
            .is_some_and(|d| d.retry_not_before_epoch_ms.is_some());
        if session.organizer.saving() || session.organizer.preview_loading || paused {
            ctx.request_repaint_after(Duration::from_millis(if paused { 1000 } else { 100 }));
        }
        out.0
    }

    /// Rebuilds the queue for a new draft revision and keeps a valid selection.
    fn sync(&mut self, session: &LibrarySession) {
        let Some(draft) = &session.organizer.draft else {
            self.model = ReviewModel::default();
            self.model_key = None;
            self.active = None;
            self.finished = false;
            self.selected.clear();
            return;
        };
        let key = (draft.id.clone(), draft.revision);
        if self.model_key.as_ref() != Some(&key) {
            self.model = ReviewModel::build(draft);
            self.model_key = Some(key);
            self.selected
                .retain(|id| draft.files.iter().any(|f| &f.media_id == id));
        }
        if self.destination_draft.as_deref() != Some(&draft.id)
            || (!self.destination_focused && self.destination_input != draft.destination)
        {
            self.destination_input = draft.destination.clone();
            self.destination_draft = Some(draft.id.clone());
        }
        match self.active.as_ref().map(|id| self.model.get(id).is_some()) {
            // The series was merged or renamed: follow one of its videos.
            Some(false) => {
                let followed = self
                    .anchor
                    .as_ref()
                    .and_then(|media| draft.files.iter().find(|f| &f.media_id == media))
                    .map(|f| f.group_id.clone())
                    .or_else(|| self.model.next_to_do(None).map(|g| g.id.clone()));
                self.set_active(followed);
            }
            None if !self.finished => {
                let next = self.model.next_to_do(None).map(|g| g.id.clone());
                self.finished = next.is_none();
                self.set_active(next);
            }
            _ => {}
        }
        if let Some(from) = self.advance_from.clone() {
            match self.model.get(&from).map(|g| g.state) {
                Some(GroupState::Done | GroupState::Skipped | GroupState::Excluded) => {
                    let next = self.model.next_to_do(Some(&from)).map(|g| g.id.clone());
                    self.finished = next.is_none();
                    self.set_active(next);
                    self.advance_from = None;
                }
                Some(_) => {}
                None => self.advance_from = None,
            }
        }
        if let Some(group) = self.active.as_ref().and_then(|id| self.model.get(id)) {
            self.anchor = group
                .members
                .first()
                .map(|&i| draft.files[i].media_id.clone());
            if !self.title_focused {
                self.title_input = group.title.clone();
            }
        }
    }

    fn set_active(&mut self, group: Option<String>) {
        if group.is_some() {
            self.finished = false;
        }
        if self.active != group {
            self.selected.clear();
            self.range_anchor = None;
            self.match_panel = false;
            self.title_focused = false;
            self.tab = DetailTab::Videos;
            self.match_query.clear();
            self.active = group;
        }
    }

    fn request_preview(&self, session: &LibrarySession, out: &mut Out) {
        let state = &session.organizer;
        let (Some(draft), Some(group)) = (
            state.draft.as_ref(),
            self.active.as_ref().and_then(|id| self.model.get(id)),
        ) else {
            return;
        };
        if group.state == GroupState::Done
            || group.included == 0
            || state.saving()
            || state.busy()
            || state.transferring()
        {
            return;
        }
        let key = preview_key(draft, group, session.catalog_version);
        if state.preview_requested != Some(key) {
            out.request(OrganizerRequest::Preview {
                draft_id: draft.id.clone(),
                group_id: group.id.clone(),
                key,
            });
        }
    }

    fn content(&mut self, ui: &mut egui::Ui, session: &LibrarySession, s: Strings, out: &mut Out) {
        egui::TopBottomPanel::top("organizer-header")
            .frame(egui::Frame::NONE.inner_margin(egui::Margin::symmetric(16, 12)))
            .show_inside(ui, |ui| self.header(ui, session, s, out));
        let Some(draft) = session.organizer.draft.as_ref() else {
            egui::CentralPanel::default()
                .frame(egui::Frame::NONE.inner_margin(egui::Margin::same(24)))
                .show_inside(ui, |ui| self.start_page(ui, session, s, out));
            return;
        };
        egui::SidePanel::left("organizer-queue")
            .resizable(true)
            .default_width(300.0)
            .width_range(220.0..=480.0)
            .frame(
                egui::Frame::NONE
                    .fill(palette::BG_NAV)
                    .inner_margin(egui::Margin::symmetric(10, 12)),
            )
            .show_inside(ui, |ui| self.queue(ui, draft, s, out));
        let group = self
            .active
            .as_ref()
            .and_then(|id| self.model.get(id).cloned());
        let Some(group) = group else {
            egui::CentralPanel::default()
                .frame(egui::Frame::NONE.inner_margin(egui::Margin::same(24)))
                .show_inside(ui, |ui| self.finished_page(ui, session, s, out));
            return;
        };
        let plan = current_batch(session, draft, &group);
        egui::TopBottomPanel::bottom("organizer-actions")
            .frame(
                egui::Frame::NONE
                    .fill(palette::SURFACE)
                    .inner_margin(egui::Margin::symmetric(16, 10)),
            )
            .show_inside(ui, |ui| {
                self.action_bar(ui, session, draft, &group, plan, s, out)
            });
        egui::CentralPanel::default()
            .frame(egui::Frame::NONE.inner_margin(egui::Margin::symmetric(16, 12)))
            .show_inside(ui, |ui| {
                self.detail(ui, session, draft, &group, plan, s, out)
            });
    }

    fn header(&mut self, ui: &mut egui::Ui, session: &LibrarySession, s: Strings, out: &mut Out) {
        let state = &session.organizer;
        let status = state.status.as_ref();
        let draft = state.draft.as_ref();
        ui.horizontal(|ui| {
            ui.label(
                RichText::new(s.organizer_text(T::WindowTitle))
                    .font(typography::title())
                    .color(palette::TEXT_PRIMARY),
            );
            ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                if draft.is_some() {
                    ui.menu_button(s.organizer_text(T::ReviewMenu), |ui| {
                        if ui.button(s.organizer_text(T::FinishReview)).clicked() {
                            out.request(OrganizerRequest::Discard);
                            ui.close();
                        }
                        ui.label(
                            RichText::new(s.organizer_text(T::FinishReviewHint))
                                .font(typography::small())
                                .color(palette::TEXT_MUTED),
                        );
                    });
                }
                ui.toggle_value(&mut self.help, "?")
                    .on_hover_text(s.organizer_text(T::HowItWorks));
                if let Some(status) = status
                    && status.can_undo
                    && !status.transferring()
                    && let Some(id) = &status.last_completed_batch_id
                    && ui
                        .add_enabled(
                            !state.busy(),
                            egui::Button::new(s.organizer_text(T::UndoLast)),
                        )
                        .on_hover_text(s.organizer_text(T::UndoLastHint))
                        .clicked()
                {
                    out.request(OrganizerRequest::Undo {
                        completed_batch_id: id.clone(),
                    });
                }
                if let Some(draft) = draft {
                    self.identification_badge(ui, session, draft, s, out);
                }
            });
        });
        if self.help {
            egui::Frame::NONE
                .fill(palette::SURFACE)
                .corner_radius(6)
                .inner_margin(egui::Margin::same(10))
                .show(ui, |ui| {
                    for key in [T::HelpFlow, T::HelpMatching, T::HelpExtras, T::HelpSafety] {
                        ui.label(
                            RichText::new(s.organizer_text(key)).color(palette::TEXT_SECONDARY),
                        );
                    }
                });
        }
        if draft.is_some() {
            ui.add_space(4.0);
            self.destination_row(ui, session, s, out);
        }
        if let Some(status) = status {
            if status.transferring() {
                ui.horizontal(|ui| {
                    ui.spinner();
                    ui.label(s.organizer_moving(
                        status.series_title.as_deref().unwrap_or_default(),
                        status.completed_operations,
                        status.total_operations,
                    ));
                    ui.add(
                        egui::ProgressBar::new(
                            status.completed_bytes as f32 / status.total_bytes.max(1) as f32,
                        )
                        .desired_width(240.0)
                        .show_percentage(),
                    );
                    if ui.button(s.organizer_text(T::CancelMove)).clicked() {
                        out.request(OrganizerRequest::Cancel);
                    }
                });
            } else if status.state == "RECOVERY_REQUIRED" {
                ui.horizontal_wrapped(|ui| {
                    ui.colored_label(palette::DANGER, s.organizer_text(T::RecoveryRequired));
                    if let Some(message) = &status.message {
                        ui.label(message);
                    }
                    if ui
                        .add_enabled(
                            !state.busy(),
                            egui::Button::new(s.organizer_text(T::RetryRecovery)),
                        )
                        .clicked()
                    {
                        out.request(OrganizerRequest::Recover);
                    }
                });
            } else if let Some((success, message)) = &state.notice {
                let color = if *success {
                    palette::SUCCESS
                } else {
                    palette::DANGER
                };
                ui.colored_label(color, message);
            }
        }
        if let Some(error) = &state.error {
            ui.horizontal_wrapped(|ui| {
                ui.colored_label(palette::DANGER, format!("⚠ {error}"));
            });
        }
    }

    fn identification_badge(
        &mut self,
        ui: &mut egui::Ui,
        session: &LibrarySession,
        draft: &OrganizationDraft,
        s: Strings,
        out: &mut Out,
    ) {
        let state = &session.organizer;
        let status = state.status.as_ref();
        let identification = status.map(|s| &s.identification);
        if state.identifying() {
            if ui.button(s.organizer_text(T::StopMatching)).clicked() {
                out.request(OrganizerRequest::StopIdentify);
            }
            if let Some(progress) = identification {
                ui.label(s.organizer_matching(progress.completed, progress.total));
            }
            ui.spinner();
            return;
        }
        if identification.is_some_and(|i| !i.available) {
            ui.label(
                RichText::new(s.organizer_text(T::MatchingOff))
                    .font(typography::small())
                    .color(palette::TEXT_MUTED),
            )
            .on_hover_text(s.organizer_text(T::MatchingOffHint));
            return;
        }
        let wait = draft
            .retry_not_before_epoch_ms
            .map(|until| until.saturating_sub(now_ms()).div_ceil(1000))
            .unwrap_or(0);
        if draft.identification_pause.is_some() || draft.retry_not_before_epoch_ms.is_some() {
            if ui
                .add_enabled(
                    wait == 0 && !state.busy(),
                    egui::Button::new(s.organizer_text(T::ResumeMatching)),
                )
                .clicked()
            {
                out.request(OrganizerRequest::Identify {
                    draft_id: draft.id.clone(),
                    media_ids: Vec::new(),
                });
            }
            let label = if wait > 0 {
                s.organizer_paused_wait(wait)
            } else {
                s.organizer_text(T::MatchingPaused).to_owned()
            };
            ui.colored_label(ATTENTION, label)
                .on_hover_text(draft.identification_pause.as_deref().unwrap_or_default());
        }
    }

    fn destination_row(
        &mut self,
        ui: &mut egui::Ui,
        session: &LibrarySession,
        s: Strings,
        out: &mut Out,
    ) {
        let has_draft = session.organizer.draft.is_some();
        ui.horizontal(|ui| {
            ui.label(RichText::new(s.organizer_text(T::Destination)).color(palette::TEXT_MUTED));
            let width = (ui.available_width() - 110.0).max(200.0);
            let response = ui.add(
                egui::TextEdit::singleline(&mut self.destination_input)
                    .desired_width(width)
                    .hint_text(s.organizer_text(T::DestinationHint)),
            );
            self.destination_focused = response.has_focus();
            let committed = response.lost_focus();
            if has_draft
                && committed
                && let Some(draft) = &session.organizer.draft
                && self.destination_input.trim() != draft.destination
                && !self.destination_input.trim().is_empty()
            {
                out.ops(vec![DraftOp::SetDestination {
                    destination: self.destination_input.trim().to_owned(),
                }]);
            }
            if ui.button(s.organizer_text(T::Browse)).clicked() {
                let pick = Arc::clone(&self.folder_pick);
                let ctx = ui.ctx().clone();
                let start = self.destination_input.clone();
                std::thread::spawn(move || {
                    let mut dialog = rfd::FileDialog::new();
                    if !start.is_empty() {
                        dialog = dialog.set_directory(&start);
                    }
                    if let Some(path) = dialog.pick_folder()
                        && let Ok(mut pick) = pick.lock()
                    {
                        *pick = Some(path.to_string_lossy().into_owned());
                    }
                    ctx.request_repaint();
                });
            }
        });
    }

    fn start_page(
        &mut self,
        ui: &mut egui::Ui,
        session: &LibrarySession,
        s: Strings,
        out: &mut Out,
    ) {
        let state = &session.organizer;
        ui.vertical(|ui| {
            ui.set_max_width(720.0);
            ui.label(
                RichText::new(s.organizer_text(T::StartHeading))
                    .font(typography::hero())
                    .color(palette::TEXT_PRIMARY),
            );
            ui.label(RichText::new(s.organizer_text(T::StartBody)).color(palette::TEXT_SECONDARY));
            ui.add_space(12.0);
            let Some(selection) = self.pending_selection.clone().filter(|s| !s.is_empty()) else {
                ui.label(RichText::new(s.organizer_text(T::StartHint)).color(palette::TEXT_MUTED));
                return;
            };
            if self.destination_input.is_empty()
                && let Some(catalog) = &session.catalog
            {
                let roots = catalog
                    .items
                    .iter()
                    .filter(|i| selection.contains(&i.id))
                    .filter_map(|i| i.root_label.clone())
                    .collect::<BTreeSet<_>>();
                if roots.len() == 1 {
                    self.destination_input = roots.into_iter().next().unwrap_or_default();
                }
            }
            ui.label(
                RichText::new(s.organizer_selected_videos(selection.len()))
                    .font(typography::heading())
                    .color(palette::TEXT_PRIMARY),
            );
            ui.add_space(6.0);
            self.destination_row(ui, session, s, out);
            ui.label(
                RichText::new(s.organizer_text(T::DestinationExplain))
                    .font(typography::small())
                    .color(palette::TEXT_MUTED),
            );
            ui.add_space(10.0);
            let ready = !self.destination_input.trim().is_empty() && !state.busy();
            if ui
                .add_enabled(ready, primary_button(s.organizer_text(T::StartReview)))
                .clicked()
            {
                out.request(OrganizerRequest::Create {
                    media_ids: selection,
                    destination: self.destination_input.trim().to_owned(),
                    replace: false,
                });
                self.pending_selection = None;
            }
        });
    }

    fn finished_page(
        &mut self,
        ui: &mut egui::Ui,
        session: &LibrarySession,
        s: Strings,
        out: &mut Out,
    ) {
        ui.label(
            RichText::new(s.organizer_text(T::AllDone))
                .font(typography::hero())
                .color(palette::TEXT_PRIMARY),
        );
        ui.label(s.organizer_done_summary(
            self.model.count(QueueFilter::Done),
            self.model.count(QueueFilter::Skipped),
        ));
        ui.add_space(10.0);
        ui.horizontal(|ui| {
            if ui
                .add_enabled(
                    !session.organizer.busy(),
                    primary_button(s.organizer_text(T::FinishReview)),
                )
                .clicked()
            {
                out.request(OrganizerRequest::Discard);
            }
            if self.model.count(QueueFilter::Skipped) > 0
                && ui.button(s.organizer_text(T::ReviewSkipped)).clicked()
            {
                self.filter = QueueFilter::Skipped;
                self.finished = false;
                let first = self
                    .model
                    .groups
                    .iter()
                    .find(|g| g.state == GroupState::Skipped)
                    .map(|g| g.id.clone());
                self.set_active(first);
            }
        });
    }

    fn queue(&mut self, ui: &mut egui::Ui, draft: &OrganizationDraft, s: Strings, out: &mut Out) {
        if let Some(selection) = self.pending_selection.clone().filter(|s| !s.is_empty()) {
            self.replace_prompt(ui, draft, selection, s, out);
        }
        ui.add(
            egui::TextEdit::singleline(&mut self.queue_search)
                .hint_text(s.organizer_text(T::FilterSeries))
                .desired_width(f32::INFINITY),
        );
        ui.horizontal_wrapped(|ui| {
            for (filter, key) in [
                (QueueFilter::ToDo, T::FilterToDo),
                (QueueFilter::Ready, T::FilterReady),
                (QueueFilter::Skipped, T::FilterSkipped),
                (QueueFilter::Done, T::FilterDone),
                (QueueFilter::All, T::FilterAll),
            ] {
                let label = format!("{} {}", s.organizer_text(key), self.model.count(filter));
                ui.selectable_value(&mut self.filter, filter, label);
            }
        });
        ui.add_space(4.0);
        let search = self.queue_search.to_lowercase();
        let visible = self
            .model
            .groups
            .iter()
            .filter(|g| self.filter.accepts(g.state) || self.active.as_ref() == Some(&g.id))
            .filter(|g| search.is_empty() || g.title.to_lowercase().contains(&search))
            .cloned()
            .collect::<Vec<_>>();
        let footer = 26.0;
        if visible.is_empty() {
            ui.label(RichText::new(s.organizer_text(T::QueueEmpty)).color(palette::TEXT_MUTED));
        }
        egui::ScrollArea::vertical()
            .id_salt("organizer-queue-list")
            .auto_shrink([false, false])
            .max_height((ui.available_height() - footer).max(0.0))
            .show_rows(ui, QUEUE_ROW_HEIGHT, visible.len(), |ui, range| {
                for group in &visible[range] {
                    if self.queue_row(ui, group, s).clicked() {
                        self.set_active(Some(group.id.clone()));
                    }
                }
            });
        ui.with_layout(egui::Layout::bottom_up(egui::Align::Min), |ui| {
            ui.label(
                RichText::new(s.organizer_progress(
                    self.model.count(QueueFilter::Done),
                    self.model.groups.len(),
                ))
                .font(typography::small())
                .color(palette::TEXT_MUTED),
            );
        });
    }

    /// Offered when Folders asks to organize a new selection while a review exists.
    fn replace_prompt(
        &mut self,
        ui: &mut egui::Ui,
        draft: &OrganizationDraft,
        selection: Vec<String>,
        s: Strings,
        out: &mut Out,
    ) {
        egui::Frame::NONE
            .fill(palette::SURFACE_RAISED)
            .corner_radius(6)
            .inner_margin(egui::Margin::same(10))
            .show(ui, |ui| {
                ui.label(
                    RichText::new(s.organizer_unfinished(
                        self.model.count(QueueFilter::Done),
                        self.model.groups.len(),
                    ))
                    .color(palette::TEXT_PRIMARY),
                );
                ui.horizontal_wrapped(|ui| {
                    if ui.button(s.organizer_text(T::KeepReview)).clicked() {
                        self.pending_selection = None;
                    }
                    if ui
                        .button(s.organizer_replace_review(selection.len()))
                        .on_hover_text(s.organizer_text(T::ReplaceReviewHint))
                        .clicked()
                    {
                        out.request(OrganizerRequest::Create {
                            media_ids: selection.clone(),
                            destination: draft.destination.clone(),
                            replace: true,
                        });
                        self.pending_selection = None;
                        self.active = None;
                    }
                });
            });
        ui.add_space(6.0);
    }

    fn queue_row(&self, ui: &mut egui::Ui, group: &GroupSummary, s: Strings) -> egui::Response {
        let (rect, response) = ui.allocate_exact_size(
            egui::vec2(ui.available_width(), QUEUE_ROW_HEIGHT - 4.0),
            egui::Sense::click(),
        );
        let active = self.active.as_ref() == Some(&group.id);
        let fill = if active {
            palette::WIDGET_ACTIVE
        } else if response.hovered() {
            palette::WIDGET_HOVER
        } else {
            Color32::TRANSPARENT
        };
        let painter = ui.painter_at(rect);
        painter.rect_filled(rect, 6.0, fill);
        painter.circle_filled(
            egui::pos2(rect.left() + 12.0, rect.center().y),
            4.5,
            state_color(group.state),
        );
        let text_left = rect.left() + 26.0;
        let width = rect.width() - 34.0;
        let title = ui.fonts(|f| {
            f.layout(
                group.title.clone(),
                typography::body(),
                palette::TEXT_PRIMARY,
                f32::INFINITY,
            )
        });
        let title_rect = egui::Rect::from_min_size(
            egui::pos2(text_left, rect.top() + 5.0),
            egui::vec2(width, title.size().y),
        );
        painter
            .with_clip_rect(title_rect)
            .galley(title_rect.min, title, palette::TEXT_PRIMARY);
        let status = match group.state {
            GroupState::NeedsAttention if group.unidentified > 0 => {
                s.organizer_needs_match(group.unidentified)
            }
            GroupState::NeedsAttention => s.organizer_text(T::NeedsSeason).to_owned(),
            GroupState::Ready => group.season_label(),
            GroupState::Skipped => s.organizer_text(T::StateSkipped).to_owned(),
            GroupState::Excluded => s.organizer_text(T::StateExcluded).to_owned(),
            GroupState::Done => s.organizer_text(T::StateDone).to_owned(),
        };
        let count = s.organizer_video_count(group.included);
        let detail = if status.is_empty() {
            count
        } else {
            format!("{count} · {status}")
        };
        painter.text(
            egui::pos2(text_left, rect.bottom() - 6.0),
            egui::Align2::LEFT_BOTTOM,
            detail,
            typography::small(),
            palette::TEXT_MUTED,
        );
        response.on_hover_text(&group.title)
    }

    #[allow(clippy::too_many_arguments)]
    fn action_bar(
        &mut self,
        ui: &mut egui::Ui,
        session: &LibrarySession,
        draft: &OrganizationDraft,
        group: &GroupSummary,
        batch: Option<&OrganizationSeriesBatch>,
        s: Strings,
        out: &mut Out,
    ) {
        let state = &session.organizer;
        let locked = state.saving() || state.busy() || state.transferring();
        ui.horizontal(|ui| {
            ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                match group.state {
                    GroupState::Done => {
                        ui.label(
                            RichText::new(s.organizer_text(T::StateDone)).color(palette::SUCCESS),
                        );
                        return;
                    }
                    GroupState::Excluded => {
                        ui.label(
                            RichText::new(s.organizer_text(T::AllExcluded))
                                .color(palette::TEXT_MUTED),
                        );
                        return;
                    }
                    GroupState::Skipped => {
                        if ui
                            .add_enabled(!locked, egui::Button::new(s.organizer_text(T::Unskip)))
                            .clicked()
                        {
                            out.ops(vec![DraftOp::SetSkipped {
                                group_id: group.id.clone(),
                                skipped: false,
                            }]);
                        }
                        return;
                    }
                    _ => {}
                }
                let in_place = batch.is_some_and(|b| b.already_organized);
                if in_place {
                    if ui
                        .add_enabled(!locked, primary_button(s.organizer_text(T::MarkDone)))
                        .on_hover_text(s.organizer_text(T::MarkDoneHint))
                        .clicked()
                    {
                        out.ops(vec![DraftOp::MarkDone {
                            group_id: group.id.clone(),
                        }]);
                        self.advance_from = Some(group.id.clone());
                    }
                } else {
                    let ready = batch.is_some_and(|b| b.executable);
                    let response = ui.add_enabled(
                        ready && !locked,
                        primary_button(s.organizer_text(T::ApproveNext)),
                    );
                    if response.clicked()
                        && let (Some(batch), Some((_, plan))) = (batch, &state.plan)
                    {
                        out.request(OrganizerRequest::Execute {
                            plan_id: plan.plan_id.clone(),
                            batch: batch.clone(),
                        });
                        self.advance_from = Some(group.id.clone());
                    }
                }
                if ui
                    .add_enabled(!locked, egui::Button::new(s.organizer_text(T::Skip)))
                    .on_hover_text(s.organizer_text(T::SkipHint))
                    .clicked()
                {
                    out.ops(vec![DraftOp::SetSkipped {
                        group_id: group.id.clone(),
                        skipped: true,
                    }]);
                    self.advance_from = Some(group.id.clone());
                }
                ui.with_layout(egui::Layout::left_to_right(egui::Align::Center), |ui| {
                    self.readiness(ui, session, draft, group, batch, s, out);
                });
            });
        });
    }

    /// Explains what approval will do, or exactly what blocks it.
    #[allow(clippy::too_many_arguments)]
    fn readiness(
        &self,
        ui: &mut egui::Ui,
        session: &LibrarySession,
        draft: &OrganizationDraft,
        group: &GroupSummary,
        batch: Option<&OrganizationSeriesBatch>,
        s: Strings,
        out: &mut Out,
    ) {
        let state = &session.organizer;
        let mut blockers = Vec::new();
        if group.included == 0 {
            blockers.push(s.organizer_text(T::AllExcluded).to_owned());
        }
        if group.unidentified > 0 {
            blockers.push(s.organizer_needs_match_long(group.unidentified));
        }
        if group.without_season > 0 {
            blockers.push(s.organizer_needs_season(group.without_season));
        }
        if blockers.is_empty()
            && let Some(batch) = batch
        {
            blockers.extend(batch.conflicts.iter().cloned());
        }
        if state.transferring() {
            ui.spinner();
            ui.label(s.organizer_text(T::MoveRunning));
        } else if batch.is_some_and(|b| b.already_organized) {
            ui.label(RichText::new(s.organizer_text(T::InPlace)).color(palette::SUCCESS));
        } else if !blockers.is_empty() {
            let first = format!("⚠ {}", blockers[0]);
            let label = if blockers.len() > 1 {
                format!("{first}  {}", s.organizer_more(blockers.len() - 1))
            } else {
                first
            };
            ui.add(egui::Label::new(RichText::new(label).color(ATTENTION)).truncate())
                .on_hover_text(blockers.join("\n"));
        } else if let Some(batch) = batch {
            let bytes = batch.moves.iter().map(|m| m.size_bytes).sum::<u64>();
            ui.add(
                egui::Label::new(
                    RichText::new(s.organizer_move_summary(batch.moves.len(), bytes))
                        .color(palette::TEXT_PRIMARY),
                )
                .truncate(),
            );
        } else if state.saving() || state.preview_loading {
            ui.spinner();
            ui.label(s.organizer_text(T::Checking));
        } else if state.error.is_some() {
            if ui.button(s.organizer_text(T::CheckAgain)).clicked() {
                out.request(OrganizerRequest::Preview {
                    draft_id: draft.id.clone(),
                    group_id: group.id.clone(),
                    key: preview_key(draft, group, session.catalog_version),
                });
            }
        } else {
            ui.spinner();
            ui.label(s.organizer_text(T::Checking));
        }
    }

    #[allow(clippy::too_many_arguments)]
    fn detail(
        &mut self,
        ui: &mut egui::Ui,
        session: &LibrarySession,
        draft: &OrganizationDraft,
        group: &GroupSummary,
        batch: Option<&OrganizationSeriesBatch>,
        s: Strings,
        out: &mut Out,
    ) {
        let editable = group.state != GroupState::Done && !session.organizer.transferring();
        ui.add_enabled_ui(editable, |ui| {
            self.group_header(ui, session, draft, group, s, out);
        });
        ui.add_space(6.0);
        ui.horizontal(|ui| {
            let extras = batch.map_or(0, |b| b.nearby_files.len());
            let moves = batch.map_or(0, |b| b.moves.len());
            for (tab, label) in [
                (
                    DetailTab::Videos,
                    s.organizer_tab_videos(group.members.len()),
                ),
                (DetailTab::Extras, s.organizer_tab_extras(extras)),
                (DetailTab::Moves, s.organizer_tab_moves(moves)),
            ] {
                ui.selectable_value(&mut self.tab, tab, label);
            }
        });
        ui.separator();
        ui.add_enabled_ui(editable, |ui| match self.tab {
            DetailTab::Videos => self.videos(ui, session, draft, group, batch, s, out),
            DetailTab::Extras => self.extras(ui, draft, batch, s, out),
            DetailTab::Moves => moves_table(ui, batch, s),
        });
    }

    fn group_header(
        &mut self,
        ui: &mut egui::Ui,
        session: &LibrarySession,
        draft: &OrganizationDraft,
        group: &GroupSummary,
        s: Strings,
        out: &mut Out,
    ) {
        ui.horizontal(|ui| {
            ui.label(RichText::new(s.organizer_text(T::SeriesFolder)).color(palette::TEXT_MUTED));
            let response = ui.add(
                egui::TextEdit::singleline(&mut self.title_input)
                    .font(typography::title())
                    .desired_width((ui.available_width() - 20.0).min(560.0)),
            );
            self.title_focused = response.has_focus();
            if response.lost_focus() {
                let title = self.title_input.trim().to_owned();
                if !title.is_empty() && title != group.title {
                    out.ops(vec![DraftOp::RenameGroup {
                        group_id: group.id.clone(),
                        title,
                    }]);
                } else {
                    self.title_input = group.title.clone();
                }
            }
        });
        ui.label(
            RichText::new(s.organizer_folder_preview(
                &draft.destination,
                &group.title,
                &group.seasons,
            ))
            .font(typography::small())
            .color(palette::TEXT_MUTED),
        );
        ui.horizontal_wrapped(|ui| {
            ui.label(RichText::new(s.organizer_text(T::Match)).color(palette::TEXT_MUTED));
            match group_match(draft, group) {
                GroupMatch::One(candidate) => {
                    ui.label(
                        RichText::new(format!(
                            "{} · #{}",
                            candidate.series_title, candidate.anime_id
                        ))
                        .color(palette::TEXT_PRIMARY),
                    );
                }
                GroupMatch::Mixed => {
                    ui.label(
                        RichText::new(s.organizer_text(T::MixedMatches))
                            .color(palette::TEXT_SECONDARY),
                    );
                }
                GroupMatch::None if group.unidentified > 0 => {
                    ui.label(RichText::new(s.organizer_text(T::NoMatch)).color(ATTENTION));
                }
                GroupMatch::None => {
                    ui.label(
                        RichText::new(s.organizer_text(T::FolderNameOnly))
                            .color(palette::TEXT_SECONDARY),
                    );
                }
            }
            if group.unidentified > 0
                && ui
                    .button(s.organizer_text(T::UseFolderName))
                    .on_hover_text(s.organizer_text(T::UseFolderNameHint))
                    .clicked()
            {
                out.ops(vec![DraftOp::ConfirmGroup {
                    group_id: group.id.clone(),
                }]);
            }
            if ui
                .selectable_label(self.match_panel, s.organizer_text(T::ChangeMatch))
                .clicked()
            {
                self.match_panel = !self.match_panel;
                if self.match_panel && self.match_query.is_empty() {
                    self.match_query = group.title.clone();
                }
            }
        });
        if self.match_panel {
            self.match_panel_ui(ui, session, draft, group, s, out);
        }
    }

    fn match_panel_ui(
        &mut self,
        ui: &mut egui::Ui,
        session: &LibrarySession,
        draft: &OrganizationDraft,
        group: &GroupSummary,
        s: Strings,
        out: &mut Out,
    ) {
        let targets = self.edit_targets(draft, group);
        egui::Frame::NONE
            .fill(palette::SURFACE)
            .corner_radius(6)
            .inner_margin(egui::Margin::same(10))
            .show(ui, |ui| {
                let available = session
                    .organizer
                    .status
                    .as_ref()
                    .is_none_or(|s| s.identification.available);
                ui.horizontal(|ui| {
                    let response = ui.add(
                        egui::TextEdit::singleline(&mut self.match_query)
                            .hint_text(s.organizer_text(T::SearchHint))
                            .desired_width(320.0),
                    );
                    let submit =
                        response.lost_focus() && ui.input(|i| i.key_pressed(egui::Key::Enter));
                    if (ui
                        .add_enabled(
                            available && !session.organizer.busy(),
                            egui::Button::new(s.organizer_text(T::Search)),
                        )
                        .clicked()
                        || submit)
                        && !self.match_query.trim().is_empty()
                    {
                        out.request(OrganizerRequest::Search {
                            draft_id: draft.id.clone(),
                            group_id: group.id.clone(),
                            query: self.match_query.trim().to_owned(),
                        });
                    }
                    if session.organizer.busy() {
                        ui.spinner();
                    }
                    if !available {
                        ui.label(
                            RichText::new(s.organizer_text(T::MatchingOffHint))
                                .font(typography::small())
                                .color(palette::TEXT_MUTED),
                        );
                    }
                });
                ui.label(
                    RichText::new(s.organizer_apply_to(targets.len(), !self.selected.is_empty()))
                        .font(typography::small())
                        .color(palette::TEXT_MUTED),
                );
                let options = group_match_options(draft, group);
                egui::ScrollArea::vertical()
                    .id_salt("organizer-match-options")
                    .max_height(150.0)
                    .show(ui, |ui| {
                        for option in &options {
                            if ui
                                .selectable_label(
                                    false,
                                    format!("{} · #{}", option.series_title, option.anime_id),
                                )
                                .clicked()
                            {
                                out.ops(vec![DraftOp::SetMatch {
                                    media_ids: targets.clone(),
                                    candidate: Some(option.clone()),
                                }]);
                                self.match_panel = false;
                            }
                        }
                        if options.is_empty() {
                            ui.label(
                                RichText::new(s.organizer_text(T::NoResults))
                                    .color(palette::TEXT_MUTED),
                            );
                        }
                    });
                ui.horizontal(|ui| {
                    let linked = targets.iter().any(|id| {
                        draft
                            .files
                            .iter()
                            .any(|f| &f.media_id == id && f.candidate.is_some())
                    });
                    if linked && ui.button(s.organizer_text(T::ClearMatch)).clicked() {
                        out.ops(vec![DraftOp::SetMatch {
                            media_ids: targets.clone(),
                            candidate: None,
                        }]);
                    }
                    if available
                        && ui
                            .add_enabled(
                                !session.organizer.identifying() && !session.organizer.busy(),
                                egui::Button::new(s.organizer_text(T::MatchAgain)),
                            )
                            .on_hover_text(s.organizer_text(T::MatchAgainHint))
                            .clicked()
                    {
                        out.request(OrganizerRequest::Identify {
                            draft_id: draft.id.clone(),
                            media_ids: targets.clone(),
                        });
                    }
                });
            });
    }

    /// Selected videos of the group, or all of them when none are selected.
    fn edit_targets(&self, draft: &OrganizationDraft, group: &GroupSummary) -> Vec<String> {
        let all = group
            .members
            .iter()
            .map(|&i| draft.files[i].media_id.clone());
        if self.selected.is_empty() {
            all.collect()
        } else {
            all.filter(|id| self.selected.contains(id)).collect()
        }
    }

    #[allow(clippy::too_many_arguments)]
    fn videos(
        &mut self,
        ui: &mut egui::Ui,
        session: &LibrarySession,
        draft: &OrganizationDraft,
        group: &GroupSummary,
        batch: Option<&OrganizationSeriesBatch>,
        s: Strings,
        out: &mut Out,
    ) {
        self.selection_toolbar(ui, draft, group, s, out);
        let identifying = session
            .organizer
            .status
            .as_ref()
            .filter(|s| s.identification.running)
            .and_then(|s| s.identification.media_id.clone());
        let mut toggles = Vec::<(usize, bool)>::new();
        let shift = ui.input(|i| i.modifiers.shift);
        TableBuilder::new(ui)
            .id_salt("organizer-videos")
            .striped(true)
            .resizable(true)
            .auto_shrink([false, false])
            .cell_layout(egui::Layout::left_to_right(egui::Align::Center))
            .column(Column::exact(28.0))
            .column(Column::remainder().at_least(220.0).clip(true))
            .column(Column::initial(96.0).at_least(80.0))
            .column(Column::initial(240.0).at_least(140.0).clip(true))
            .column(Column::initial(160.0).at_least(100.0).clip(true))
            .header(24.0, |mut header| {
                let all_selected = !group.members.is_empty()
                    && group
                        .members
                        .iter()
                        .all(|&i| self.selected.contains(&draft.files[i].media_id));
                header.col(|ui| {
                    let mut value = all_selected;
                    if ui.checkbox(&mut value, "").changed() {
                        if value {
                            self.selected.extend(
                                group
                                    .members
                                    .iter()
                                    .map(|&i| draft.files[i].media_id.clone()),
                            );
                        } else {
                            self.selected.clear();
                        }
                    }
                });
                for key in [
                    T::ColumnFile,
                    T::ColumnSeason,
                    T::ColumnEpisode,
                    T::ColumnDestination,
                ] {
                    header.col(|ui| {
                        ui.label(RichText::new(s.organizer_text(key)).color(palette::TEXT_MUTED));
                    });
                }
            })
            .body(|body| {
                body.rows(ROW_HEIGHT, group.members.len(), |mut row| {
                    let position = row.index();
                    let file = &draft.files[group.members[position]];
                    let selected = self.selected.contains(&file.media_id);
                    row.set_selected(selected);
                    let dim = |text: RichText| {
                        if file.excluded {
                            text.color(palette::TEXT_MUTED).strikethrough()
                        } else {
                            text
                        }
                    };
                    row.col(|ui| {
                        let mut value = selected;
                        if ui.checkbox(&mut value, "").changed() {
                            toggles.push((position, value));
                        }
                    });
                    row.col(|ui| {
                        let label = ui
                            .add(
                                egui::Label::new(dim(RichText::new(file.file_name())))
                                    .truncate()
                                    .sense(egui::Sense::click()),
                            )
                            .on_hover_text(format!(
                                "{}\n{}",
                                file.source_root, file.source_relative_path
                            ));
                        if label.clicked() {
                            toggles.push((position, !selected));
                        }
                    });
                    row.col(|ui| {
                        if file.excluded {
                            ui.label(
                                RichText::new(s.organizer_text(T::Excluded))
                                    .color(palette::TEXT_MUTED),
                            );
                            return;
                        }
                        self.season_cell(ui, file, s, out);
                    });
                    row.col(|ui| episode_cell(ui, file, identifying.as_deref(), s, out));
                    row.col(|ui| {
                        let destination = batch.and_then(|b| {
                            b.moves
                                .iter()
                                .find(|m| m.media_id.as_ref() == Some(&file.media_id))
                        });
                        let text = match (file.excluded, destination) {
                            (true, _) => String::new(),
                            (false, Some(m)) => {
                                folder_of(&m.destination_relative_path, &group.title)
                            }
                            (false, None) if batch.is_some() => {
                                s.organizer_text(T::InPlaceShort).to_owned()
                            }
                            (false, None) => String::new(),
                        };
                        ui.add(
                            egui::Label::new(RichText::new(text).color(palette::TEXT_SECONDARY))
                                .truncate(),
                        );
                    });
                });
            });
        for (position, value) in toggles {
            let range = match (shift, self.range_anchor) {
                (true, Some(anchor)) => anchor.min(position)..=anchor.max(position),
                _ => position..=position,
            };
            for index in range {
                let id = draft.files[group.members[index]].media_id.clone();
                if value {
                    self.selected.insert(id);
                } else {
                    self.selected.remove(&id);
                }
            }
            self.range_anchor = Some(position);
        }
    }

    fn season_cell(
        &mut self,
        ui: &mut egui::Ui,
        file: &crate::library::DraftFile,
        s: Strings,
        out: &mut Out,
    ) {
        let mut value = match &self.season_drag {
            Some((id, value)) if id == &file.media_id => *value,
            _ => file.season_number.unwrap_or(1),
        };
        let response = ui.add(
            egui::DragValue::new(&mut value)
                .range(0..=9999)
                .speed(0.05)
                .prefix("S"),
        );
        if response.dragged() || response.has_focus() {
            self.season_drag = Some((file.media_id.clone(), value));
        }
        let commit = response.drag_stopped()
            || response.lost_focus()
            || (response.changed() && !response.dragged() && !response.has_focus());
        if commit {
            self.season_drag = None;
            if Some(value) != file.season_number {
                out.ops(vec![DraftOp::SetSeason {
                    media_ids: vec![file.media_id.clone()],
                    season: value,
                }]);
            }
        }
        match file.season_evidence.as_str() {
            "CONFLICT" => {
                ui.colored_label(ATTENTION, "?")
                    .on_hover_text(s.organizer_text(T::SeasonConflict));
            }
            "SUGGESTED" => {
                ui.label(RichText::new("·").color(palette::TEXT_MUTED))
                    .on_hover_text(s.organizer_text(T::SeasonSuggested));
            }
            _ => {}
        }
    }

    fn selection_toolbar(
        &mut self,
        ui: &mut egui::Ui,
        draft: &OrganizationDraft,
        group: &GroupSummary,
        s: Strings,
        out: &mut Out,
    ) {
        let targets = self
            .selected
            .iter()
            .filter(|id| {
                group
                    .members
                    .iter()
                    .any(|&i| &draft.files[i].media_id == *id)
            })
            .cloned()
            .collect::<Vec<_>>();
        let any = !targets.is_empty();
        ui.horizontal_wrapped(|ui| {
            // Fixed width keeps the buttons still when the selection changes.
            ui.allocate_ui_with_layout(
                egui::vec2(200.0, 24.0),
                egui::Layout::left_to_right(egui::Align::Center),
                |ui| {
                    ui.set_min_width(200.0);
                    ui.add(
                        egui::Label::new(
                            RichText::new(if any {
                                s.organizer_selected_count(targets.len())
                            } else {
                                s.organizer_text(T::SelectHint).to_owned()
                            })
                            .color(if any {
                                palette::TEXT_PRIMARY
                            } else {
                                palette::TEXT_MUTED
                            }),
                        )
                        .truncate(),
                    );
                },
            );
            ui.add_enabled_ui(any, |ui| {
                ui.separator();
                ui.add(
                    egui::DragValue::new(&mut self.bulk_season)
                        .range(0..=9999)
                        .prefix("S"),
                );
                if ui.button(s.organizer_text(T::SetSeason)).clicked() {
                    out.ops(vec![DraftOp::SetSeason {
                        media_ids: targets.clone(),
                        season: self.bulk_season,
                    }]);
                }
                let keep_open = egui::containers::menu::MenuConfig::new()
                    .close_behavior(egui::PopupCloseBehavior::CloseOnClickOutside);
                egui::containers::menu::MenuButton::new(s.organizer_text(T::MoveTo))
                    .config(keep_open)
                    .ui(ui, |ui| {
                        ui.label(
                            RichText::new(s.organizer_text(T::MoveToHint))
                                .color(palette::TEXT_MUTED),
                        );
                        ui.horizontal(|ui| {
                            ui.add(
                                egui::TextEdit::singleline(&mut self.move_title)
                                    .hint_text(s.organizer_text(T::NewSeriesHint))
                                    .desired_width(220.0),
                            );
                            if ui
                                .add_enabled(
                                    !self.move_title.trim().is_empty(),
                                    egui::Button::new(s.organizer_text(T::Create)),
                                )
                                .clicked()
                            {
                                out.ops(vec![DraftOp::MoveFiles {
                                    media_ids: targets.clone(),
                                    group_id: None,
                                    title: Some(self.move_title.trim().to_owned()),
                                }]);
                                self.move_title.clear();
                                ui.close();
                            }
                        });
                        ui.separator();
                        egui::ScrollArea::vertical()
                            .max_height(260.0)
                            .show(ui, |ui| {
                                for other in self
                                    .model
                                    .groups
                                    .iter()
                                    .filter(|g| g.id != group.id && g.state != GroupState::Done)
                                {
                                    if ui.button(&other.title).clicked() {
                                        out.ops(vec![DraftOp::MoveFiles {
                                            media_ids: targets.clone(),
                                            group_id: Some(other.id.clone()),
                                            title: None,
                                        }]);
                                        ui.close();
                                    }
                                }
                            });
                    });
                if ui.button(s.organizer_text(T::Exclude)).clicked() {
                    out.ops(vec![DraftOp::SetExcluded {
                        media_ids: targets.clone(),
                        excluded: true,
                    }]);
                }
                if ui.button(s.organizer_text(T::Include)).clicked() {
                    out.ops(vec![DraftOp::SetExcluded {
                        media_ids: targets.clone(),
                        excluded: false,
                    }]);
                }
                if ui.button(s.organizer_text(T::ClearSelection)).clicked() {
                    self.selected.clear();
                }
            });
        });
        ui.add_space(4.0);
    }

    fn extras(
        &mut self,
        ui: &mut egui::Ui,
        draft: &OrganizationDraft,
        batch: Option<&OrganizationSeriesBatch>,
        s: Strings,
        out: &mut Out,
    ) {
        ui.label(RichText::new(s.organizer_text(T::ExtrasHint)).color(palette::TEXT_MUTED));
        let Some(batch) = batch else {
            ui.spinner();
            return;
        };
        if batch.nearby_files.is_empty() {
            ui.label(RichText::new(s.organizer_text(T::NoExtras)).color(palette::TEXT_MUTED));
            return;
        }
        TableBuilder::new(ui)
            .id_salt("organizer-extras")
            .striped(true)
            .resizable(true)
            .auto_shrink([false, false])
            .cell_layout(egui::Layout::left_to_right(egui::Align::Center))
            .column(Column::exact(28.0))
            .column(Column::remainder().at_least(220.0).clip(true))
            .column(Column::initial(260.0).at_least(160.0).clip(true))
            .header(24.0, |mut header| {
                header.col(|_| {});
                for key in [T::ColumnFile, T::ColumnBelongsTo] {
                    header.col(|ui| {
                        ui.label(RichText::new(s.organizer_text(key)).color(palette::TEXT_MUTED));
                    });
                }
            })
            .body(|body| {
                body.rows(ROW_HEIGHT, batch.nearby_files.len(), |mut row| {
                    let nearby = &batch.nearby_files[row.index()];
                    let selected = draft
                        .companion_choices
                        .get(&nearby.relative_path)
                        .copied()
                        .unwrap_or(nearby.selected);
                    row.col(|ui| {
                        let mut value = selected;
                        if ui.checkbox(&mut value, "").changed() {
                            let mut ops = vec![DraftOp::SetCompanion {
                                path: nearby.relative_path.clone(),
                                selected: value,
                            }];
                            // A shared file with one possible series goes to its folder.
                            if value
                                && !nearby.recommended
                                && nearby.owner_media_id.is_none()
                                && nearby.owner_group_id.is_none()
                                && let [group_id] = nearby.owner_group_ids.as_slice()
                            {
                                ops.push(DraftOp::SetCompanionOwner {
                                    path: nearby.relative_path.clone(),
                                    owner: CompanionOwner::Series {
                                        group_id: group_id.clone(),
                                    },
                                });
                            }
                            out.ops(ops);
                        }
                    });
                    row.col(|ui| {
                        let name = nearby
                            .relative_path
                            .rsplit(['/', '\\'])
                            .next()
                            .unwrap_or(&nearby.relative_path);
                        let mut text = RichText::new(name);
                        if !selected {
                            text = text.color(palette::TEXT_MUTED);
                        }
                        ui.add(egui::Label::new(text).truncate())
                            .on_hover_text(&nearby.relative_path);
                        if nearby.recommended {
                            ui.label(
                                RichText::new(s.organizer_text(T::MatchingSubtitle))
                                    .font(typography::small())
                                    .color(palette::SUCCESS),
                            );
                        }
                    });
                    row.col(|ui| {
                        let current = if let Some(id) = &nearby.owner_group_id {
                            format!(
                                "{}: {}",
                                s.organizer_text(T::SeriesFolderOwner),
                                self.model.get(id).map_or(id.as_str(), |g| g.title.as_str())
                            )
                        } else if let Some(id) = &nearby.owner_media_id {
                            draft
                                .files
                                .iter()
                                .find(|f| &f.media_id == id)
                                .map_or_else(String::new, |f| f.file_name().to_owned())
                        } else {
                            s.organizer_text(T::ChooseOwner).to_owned()
                        };
                        egui::ComboBox::from_id_salt(("owner", &nearby.relative_path))
                            .selected_text(current)
                            .width(ui.available_width() - 8.0)
                            .show_ui(ui, |ui| {
                                for id in &nearby.owner_group_ids {
                                    let title = self
                                        .model
                                        .get(id)
                                        .map_or(id.as_str(), |g| g.title.as_str());
                                    if ui
                                        .selectable_label(
                                            nearby.owner_group_id.as_ref() == Some(id),
                                            format!(
                                                "{}: {title}",
                                                s.organizer_text(T::SeriesFolderOwner)
                                            ),
                                        )
                                        .clicked()
                                    {
                                        out.ops(vec![DraftOp::SetCompanionOwner {
                                            path: nearby.relative_path.clone(),
                                            owner: CompanionOwner::Series {
                                                group_id: id.clone(),
                                            },
                                        }]);
                                    }
                                }
                                for id in &nearby.owner_media_ids {
                                    let Some(file) = draft.files.iter().find(|f| &f.media_id == id)
                                    else {
                                        continue;
                                    };
                                    if ui
                                        .selectable_label(
                                            nearby.owner_media_id.as_ref() == Some(id),
                                            file.file_name(),
                                        )
                                        .clicked()
                                    {
                                        out.ops(vec![DraftOp::SetCompanionOwner {
                                            path: nearby.relative_path.clone(),
                                            owner: CompanionOwner::Video {
                                                media_id: id.clone(),
                                            },
                                        }]);
                                    }
                                }
                            });
                    });
                });
            });
    }
}

fn episode_cell(
    ui: &mut egui::Ui,
    file: &crate::library::DraftFile,
    identifying: Option<&str>,
    s: Strings,
    out: &mut Out,
) {
    if identifying == Some(file.media_id.as_str()) {
        ui.spinner();
        ui.label(RichText::new(s.organizer_text(T::Matching)).color(palette::TEXT_MUTED));
        return;
    }
    if let Some(error) = &file.identification_error
        && file.candidate.is_none()
    {
        ui.colored_label(ATTENTION, s.organizer_text(T::MatchFailed))
            .on_hover_text(error);
        return;
    }
    let episodes = file
        .candidates
        .iter()
        .filter(|c| c.episode_id.is_some())
        .collect::<Vec<_>>();
    if episodes.len() > 1 || (file.candidate.is_none() && file.candidates.len() > 1) {
        let current = file.candidate.as_ref().map_or_else(
            || s.organizer_choose_of(file.candidates.len()),
            candidate_label,
        );
        egui::ComboBox::from_id_salt(("episode", &file.media_id))
            .selected_text(current)
            .width(ui.available_width() - 8.0)
            .show_ui(ui, |ui| {
                for candidate in &file.candidates {
                    if ui
                        .selectable_label(
                            file.candidate.as_ref() == Some(candidate),
                            candidate_label(candidate),
                        )
                        .clicked()
                    {
                        out.ops(vec![DraftOp::SetMatch {
                            media_ids: vec![file.media_id.clone()],
                            candidate: Some(candidate.clone()),
                        }]);
                    }
                }
            });
        return;
    }
    let text = match &file.candidate {
        Some(candidate) if file.series_only || candidate.episode_id.is_none() => {
            s.organizer_text(T::SeriesOnly).to_owned()
        }
        Some(candidate) => candidate.episode_title.clone(),
        None if file.provider_title.is_some() => s.organizer_text(T::FromLibrary).to_owned(),
        None if file.manual_assignment => s.organizer_text(T::FolderNameOnly).to_owned(),
        None => "—".to_owned(),
    };
    ui.add(egui::Label::new(RichText::new(text).color(palette::TEXT_SECONDARY)).truncate());
}

fn candidate_label(candidate: &IdentificationCandidate) -> String {
    if candidate.episode_title.is_empty() {
        candidate.series_title.clone()
    } else {
        format!("{} · {}", candidate.series_title, candidate.episode_title)
    }
}

fn moves_table(ui: &mut egui::Ui, batch: Option<&OrganizationSeriesBatch>, s: Strings) {
    let Some(batch) = batch else {
        ui.spinner();
        return;
    };
    if batch.moves.is_empty() {
        ui.label(RichText::new(s.organizer_text(T::InPlace)).color(palette::TEXT_MUTED));
        return;
    }
    TableBuilder::new(ui)
        .id_salt("organizer-moves")
        .striped(true)
        .resizable(true)
        .auto_shrink([false, false])
        .cell_layout(egui::Layout::left_to_right(egui::Align::Center))
        .column(Column::initial(420.0).at_least(200.0).clip(true))
        .column(Column::remainder().at_least(200.0).clip(true))
        .header(24.0, |mut header| {
            for key in [T::ColumnFrom, T::ColumnTo] {
                header.col(|ui| {
                    ui.label(RichText::new(s.organizer_text(key)).color(palette::TEXT_MUTED));
                });
            }
        })
        .body(|body| {
            body.rows(ROW_HEIGHT, batch.moves.len(), |mut row| {
                let movement = &batch.moves[row.index()];
                let from = join(
                    movement.source_root.as_deref(),
                    &movement.source_relative_path,
                );
                let to = join(
                    movement.destination_root.as_deref(),
                    &movement.destination_relative_path,
                );
                row.col(|ui| {
                    ui.add(egui::Label::new(&movement.source_relative_path).truncate())
                        .on_hover_text(from);
                });
                row.col(|ui| {
                    ui.add(egui::Label::new(&movement.destination_relative_path).truncate())
                        .on_hover_text(to);
                });
            });
        });
}

/// The batch for this group when its preview matches the current decisions.
fn current_batch<'a>(
    session: &'a LibrarySession,
    draft: &OrganizationDraft,
    group: &GroupSummary,
) -> Option<&'a OrganizationSeriesBatch> {
    let (key, plan) = session.organizer.plan.as_ref()?;
    (*key == preview_key(draft, group, session.catalog_version))
        .then(|| plan.batches.iter().find(|b| b.batch_id == group.id))
        .flatten()
}

fn join(root: Option<&str>, relative: &str) -> String {
    match root {
        Some(root) => format!("{}/{relative}", root.trim_end_matches(['/', '\\'])),
        None => relative.to_owned(),
    }
}

/// `Anime/Show/Season 1/file.mkv` → `Season 1/`: the part below the series
/// folder, which the header already shows.
fn folder_of(destination: &str, title: &str) -> String {
    let parts = destination.split(['/', '\\']).collect::<Vec<_>>();
    let folders = &parts[..parts.len().saturating_sub(1)];
    let below = folders
        .iter()
        .rposition(|p| p.eq_ignore_ascii_case(title))
        .map_or(folders.len().saturating_sub(1), |index| index + 1);
    let tail = folders[below.min(folders.len())..].join("/");
    if tail.is_empty() {
        "./".to_owned()
    } else {
        format!("{tail}/")
    }
}

fn state_color(state: GroupState) -> Color32 {
    match state {
        GroupState::NeedsAttention => ATTENTION,
        GroupState::Ready => palette::ACCENT_BRIGHT,
        GroupState::Skipped | GroupState::Excluded => palette::TEXT_MUTED,
        GroupState::Done => palette::SUCCESS,
    }
}

fn primary_button(text: &str) -> egui::Button<'_> {
    egui::Button::new(RichText::new(text).color(palette::TEXT_PRIMARY).strong())
        .fill(palette::ACCENT)
        .min_size(egui::vec2(140.0, 30.0))
}

fn now_ms() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn destination_column_shows_the_part_below_the_series_folder() {
        assert_eq!(folder_of("Anime/Show/Season 1/ep.mkv", "Show"), "Season 1/");
        assert_eq!(folder_of("Show/ep.mkv", "Show"), "./");
        assert_eq!(folder_of("Other/Season 2/ep.mkv", "Show"), "Season 2/");
    }

    #[test]
    fn close_waits_for_queued_edits() {
        let mut screen = OrganizerScreen::default();
        assert!(!screen.prepare_close(true, true));
        assert!(!screen.finish_close(true));
        assert!(screen.finish_close(false));
    }
}
