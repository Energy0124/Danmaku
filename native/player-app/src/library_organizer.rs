//! Desktop organizer wire contracts and the client-side sync state.
//!
//! The server owns the review draft. The client sends small intent operations
//! ([`DraftOp`]) and always renders the latest server draft, so there is no
//! local copy to rebase and no debounce window in which edits can be lost.
use serde::{Deserialize, Serialize};
use std::collections::{BTreeMap, BTreeSet};

#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct IdentificationCandidate {
    pub anime_id: u64,
    pub episode_id: Option<u64>,
    pub series_title: String,
    pub episode_title: String,
}

#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct DraftFile {
    pub series_only: bool,
    pub source_signature: String,
    pub media_id: String,
    pub source_root: String,
    pub source_relative_path: String,
    pub size_bytes: u64,
    pub group_id: String,
    pub series_title: String,
    pub season_number: Option<u32>,
    pub season_evidence: String,
    pub excluded: bool,
    pub manual_assignment: bool,
    pub provider_title: Option<String>,
    pub candidates: Vec<IdentificationCandidate>,
    pub candidate: Option<IdentificationCandidate>,
    pub identification_error: Option<String>,
    pub fingerprint: Option<String>,
}

impl DraftFile {
    /// Linked to a provider identity or accepted under its folder title.
    pub fn identified(&self) -> bool {
        self.manual_assignment || self.candidate.is_some() || self.provider_title.is_some()
    }

    pub fn file_name(&self) -> &str {
        self.source_relative_path
            .rsplit(['/', '\\'])
            .next()
            .unwrap_or(&self.source_relative_path)
    }
}

#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct OrganizationDraft {
    pub identification_pause: Option<String>,
    pub retry_not_before_epoch_ms: Option<u64>,
    pub id: String,
    pub revision: u64,
    pub destination: String,
    pub files: Vec<DraftFile>,
    pub companion_choices: BTreeMap<String, bool>,
    pub companion_owners: BTreeMap<String, String>,
    pub companion_series_owners: BTreeMap<String, String>,
    pub skipped: BTreeSet<String>,
    pub completed: BTreeMap<String, String>,
    pub group_candidates: BTreeMap<String, Vec<IdentificationCandidate>>,
}

#[derive(Debug, Clone, PartialEq, Serialize)]
#[serde(tag = "op", rename_all = "camelCase", rename_all_fields = "camelCase")]
pub enum DraftOp {
    SetDestination {
        destination: String,
    },
    RenameGroup {
        group_id: String,
        title: String,
    },
    ConfirmGroup {
        group_id: String,
    },
    MoveFiles {
        media_ids: Vec<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        group_id: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        title: Option<String>,
    },
    SetSeason {
        media_ids: Vec<String>,
        season: u32,
    },
    SetExcluded {
        media_ids: Vec<String>,
        excluded: bool,
    },
    SetMatch {
        media_ids: Vec<String>,
        candidate: Option<IdentificationCandidate>,
    },
    SetSeriesOnly {
        media_ids: Vec<String>,
        series_only: bool,
    },
    SetCompanion {
        path: String,
        selected: bool,
    },
    SetCompanionOwner {
        path: String,
        owner: CompanionOwner,
    },
    SetSkipped {
        group_id: String,
        skipped: bool,
    },
    MarkDone {
        group_id: String,
    },
}

#[derive(Debug, Clone, PartialEq, Serialize)]
#[serde(
    tag = "kind",
    rename_all = "camelCase",
    rename_all_fields = "camelCase"
)]
pub enum CompanionOwner {
    Automatic,
    Video { media_id: String },
    Series { group_id: String },
}

#[derive(Clone, Debug, Default, Deserialize, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", default)]
pub struct OrganizationPlan {
    pub draft_id: String,
    pub group_signature: String,
    pub plan_id: String,
    pub catalog_revision: String,
    pub root: String,
    pub batches: Vec<OrganizationSeriesBatch>,
}

#[derive(Clone, Debug, Default, Deserialize, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", default)]
pub struct OrganizationSeriesBatch {
    pub batch_id: String,
    pub series_title: String,
    pub season_number: Option<u32>,
    pub confidence: String,
    pub reason: String,
    pub video_count: usize,
    pub executable: bool,
    pub already_organized: bool,
    pub conflicts: Vec<String>,
    pub moves: Vec<OrganizationMove>,
    pub nearby_files: Vec<OrganizationNearbyFile>,
}

#[derive(Clone, Debug, Default, Deserialize, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", default)]
pub struct OrganizationMove {
    pub source_signature: Option<String>,
    pub content_hash: Option<String>,
    pub source_root: Option<String>,
    pub destination_root: Option<String>,
    pub media_id: Option<String>,
    pub subtitle_id: Option<String>,
    pub source_relative_path: String,
    pub destination_relative_path: String,
    pub size_bytes: u64,
    pub kind: String,
    pub original_series_title: Option<String>,
    pub destination_series_title: Option<String>,
}

#[derive(Clone, Debug, Default, Deserialize, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", default)]
pub struct OrganizationNearbyFile {
    pub owner_group_ids: Vec<String>,
    pub owner_group_id: Option<String>,
    pub owner_media_ids: Vec<String>,
    pub owner_media_id: Option<String>,
    pub relative_path: String,
    pub size_bytes: u64,
    pub recommended: bool,
    pub selected: bool,
    pub destination_relative_path: Option<String>,
}

#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct IdentificationStatus {
    pub running: bool,
    pub completed: usize,
    pub total: usize,
    pub media_id: Option<String>,
    pub available: bool,
}

#[derive(Clone, Debug, Default, Deserialize, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", default)]
pub struct OrganizationStatus {
    pub draft: Option<OrganizationDraft>,
    pub identification: IdentificationStatus,
    pub completed_bytes: u64,
    pub total_bytes: u64,
    pub state: String,
    pub batch_id: Option<String>,
    pub series_title: Option<String>,
    pub completed_operations: usize,
    pub total_operations: usize,
    pub message: Option<String>,
    pub last_completed_batch_id: Option<String>,
    pub can_undo: bool,
}

impl OrganizationStatus {
    pub fn transferring(&self) -> bool {
        matches!(self.state.as_str(), "RUNNING" | "ROLLING_BACK")
    }
}

/// One organizer call; `LibrarySession::organizer` performs it off the UI thread.
#[derive(Debug, Clone, PartialEq)]
pub enum OrganizerRequest {
    Status,
    Create {
        media_ids: Vec<String>,
        destination: String,
        replace: bool,
    },
    Discard,
    Ops {
        draft_id: String,
        ops: Vec<DraftOp>,
    },
    Identify {
        draft_id: String,
        media_ids: Vec<String>,
    },
    StopIdentify,
    Search {
        draft_id: String,
        group_id: String,
        query: String,
    },
    Preview {
        draft_id: String,
        group_id: String,
        key: u64,
    },
    Execute {
        plan_id: String,
        batch: OrganizationSeriesBatch,
    },
    Cancel,
    Undo {
        completed_batch_id: String,
    },
    Recover,
}

impl OrganizerRequest {
    /// Method, endpoint and JSON body for the request.
    pub fn http(&self) -> (&'static str, &'static str, Option<String>) {
        use serde_json::json;
        let body = |value: serde_json::Value| Some(value.to_string());
        match self {
            Self::Status => ("GET", "/api/library/organize/status", None),
            Self::Create {
                media_ids,
                destination,
                replace,
            } => (
                "POST",
                "/api/library/organize/draft",
                body(
                    json!({"mediaIds": media_ids, "destination": destination, "replace": replace}),
                ),
            ),
            Self::Discard => ("DELETE", "/api/library/organize/draft", None),
            Self::Ops { draft_id, ops } => (
                "POST",
                "/api/library/organize/draft/ops",
                body(json!({"draftId": draft_id, "ops": ops})),
            ),
            Self::Identify {
                draft_id,
                media_ids,
            } => (
                "POST",
                "/api/library/organize/identify",
                body(json!({"draftId": draft_id, "mediaIds": media_ids})),
            ),
            Self::StopIdentify => (
                "POST",
                "/api/library/organize/identify/cancel",
                body(json!({})),
            ),
            Self::Search {
                draft_id,
                group_id,
                query,
            } => (
                "POST",
                "/api/library/organize/search",
                body(json!({"draftId": draft_id, "groupId": group_id, "query": query})),
            ),
            Self::Preview {
                draft_id, group_id, ..
            } => (
                "POST",
                "/api/library/organize/preview",
                body(json!({"draftId": draft_id, "groupId": group_id})),
            ),
            Self::Execute { plan_id, batch } => (
                "POST",
                "/api/library/organize/execute",
                body(json!({
                    "planId": plan_id,
                    "batchId": batch.batch_id,
                    "expectedMoves": batch.moves,
                })),
            ),
            Self::Cancel => ("POST", "/api/library/organize/cancel", body(json!({}))),
            Self::Undo { completed_batch_id } => (
                "POST",
                "/api/library/organize/undo",
                body(json!({"completedBatchId": completed_batch_id})),
            ),
            Self::Recover => ("POST", "/api/library/organize/recover", body(json!({}))),
        }
    }

    /// Requests whose success response is the updated draft.
    pub fn returns_draft(&self) -> bool {
        matches!(
            self,
            Self::Create { .. } | Self::Ops { .. } | Self::Search { .. }
        )
    }
}

/// Response of one organizer call, delivered back to the session.
#[derive(Debug)]
pub enum OrganizerResponse {
    Status {
        generation: u64,
        result: Result<OrganizationStatus, String>,
    },
    Draft {
        request: OrganizerRequest,
        result: Result<Option<OrganizationDraft>, String>,
    },
    Preview {
        key: u64,
        group_id: String,
        result: Result<OrganizationPlan, String>,
    },
    Done {
        request: OrganizerRequest,
        result: Result<(), String>,
    },
}

/// Performs a request synchronously (call from a background thread).
pub fn perform(base_url: &str, request: OrganizerRequest, generation: u64) -> OrganizerResponse {
    let (method, path, body) = request.http();
    let result = crate::net::organizer_json(base_url, method, path, body.as_deref());
    let parse = |body: String| -> Result<serde_json::Value, String> {
        serde_json::from_str(&body).map_err(|error| format!("invalid organizer response: {error}"))
    };
    match request {
        OrganizerRequest::Status => OrganizerResponse::Status {
            generation,
            result: result.and_then(|body| {
                serde_json::from_value(parse(body)?).map_err(|error| error.to_string())
            }),
        },
        OrganizerRequest::Preview { key, group_id, .. } => OrganizerResponse::Preview {
            key,
            group_id,
            result: result.and_then(|body| {
                serde_json::from_value(parse(body)?).map_err(|error| error.to_string())
            }),
        },
        request if request.returns_draft() => OrganizerResponse::Draft {
            result: result.and_then(|body| {
                serde_json::from_value(parse(body)?).map_err(|error| error.to_string())
            }),
            request,
        },
        OrganizerRequest::Discard => OrganizerResponse::Draft {
            result: result.map(|_| None),
            request: OrganizerRequest::Discard,
        },
        request => OrganizerResponse::Done {
            result: result.map(|_| ()),
            request,
        },
    }
}

/// Client view of the organizer: the latest server draft, status and preview.
#[derive(Debug, Default)]
pub struct OrganizerState {
    pub status: Option<OrganizationStatus>,
    pub draft: Option<OrganizationDraft>,
    /// Latest preview and the client key it was requested for.
    pub plan: Option<(u64, OrganizationPlan)>,
    /// Key of the most recent preview request; older responses are ignored.
    pub preview_requested: Option<u64>,
    pub preview_loading: bool,
    pub error: Option<String>,
    /// Commands (create, discard, identify, search, execute…) in flight.
    pub commands_in_flight: usize,
    pending_ops: Vec<DraftOp>,
    ops_in_flight: bool,
    /// Status responses at or below this generation predate a create/discard.
    status_barrier: u64,
    /// Outcome of the last move or undo seen in this session: (success, message).
    pub notice: Option<(bool, String)>,
}

impl OrganizerState {
    pub fn busy(&self) -> bool {
        self.commands_in_flight > 0
    }

    pub fn saving(&self) -> bool {
        self.ops_in_flight || !self.pending_ops.is_empty()
    }

    pub fn transferring(&self) -> bool {
        self.status
            .as_ref()
            .is_some_and(OrganizationStatus::transferring)
    }

    pub fn identifying(&self) -> bool {
        self.status
            .as_ref()
            .is_some_and(|status| status.identification.running)
    }

    /// Queues edits; returns a batch to send when none is in flight.
    pub fn queue_ops(&mut self, ops: Vec<DraftOp>) -> Option<OrganizerRequest> {
        self.pending_ops.extend(ops);
        self.next_ops()
    }

    fn next_ops(&mut self) -> Option<OrganizerRequest> {
        if self.ops_in_flight || self.pending_ops.is_empty() {
            return None;
        }
        let draft_id = self.draft.as_ref()?.id.clone();
        self.ops_in_flight = true;
        Some(OrganizerRequest::Ops {
            draft_id,
            ops: std::mem::take(&mut self.pending_ops),
        })
    }

    /// Accepts a server draft unless it is older than the one shown.
    fn accept_draft(&mut self, draft: Option<OrganizationDraft>) {
        let newer = match (&self.draft, &draft) {
            (Some(current), Some(next)) => {
                current.id != next.id || next.revision >= current.revision
            }
            _ => true,
        };
        if newer {
            self.draft = draft;
        }
    }

    /// Records that a request was sent.
    pub fn started(&mut self, request: &OrganizerRequest) {
        match request {
            OrganizerRequest::Status | OrganizerRequest::Ops { .. } => {}
            OrganizerRequest::Preview { key, .. } => {
                self.preview_requested = Some(*key);
                self.preview_loading = true;
            }
            _ => {
                self.commands_in_flight += 1;
                self.error = None;
                self.notice = None;
            }
        }
    }

    /// Applies a response. Returns follow-up requests to send.
    pub fn finish(
        &mut self,
        response: OrganizerResponse,
        status_generation: u64,
    ) -> Vec<OrganizerRequest> {
        let mut follow_up = Vec::new();
        match response {
            OrganizerResponse::Status { generation, result } => match result {
                Ok(mut status) => {
                    let draft = status.draft.take();
                    let was_active = self.transferring();
                    if was_active
                        && !status.transferring()
                        && let Some(message) = &status.message
                    {
                        self.notice = Some((status.state == "COMPLETED", message.clone()));
                    }
                    if generation > self.status_barrier {
                        if self.saving() {
                            // An in-flight edit returns a newer draft itself.
                            if draft.as_ref().is_some_and(|d| {
                                self.draft.as_ref().is_none_or(|current| current.id != d.id)
                            }) {
                                self.accept_draft(draft);
                            }
                        } else {
                            self.accept_draft(draft);
                        }
                    }
                    self.status = Some(status);
                }
                Err(error) => self.error = Some(error),
            },
            OrganizerResponse::Draft { request, result } => {
                let is_ops = matches!(request, OrganizerRequest::Ops { .. });
                if is_ops {
                    self.ops_in_flight = false;
                } else {
                    self.commands_in_flight = self.commands_in_flight.saturating_sub(1);
                }
                match result {
                    Ok(draft) => {
                        if matches!(
                            request,
                            OrganizerRequest::Create { .. } | OrganizerRequest::Discard
                        ) {
                            self.status_barrier = status_generation;
                            self.draft = draft;
                            self.plan = None;
                            self.pending_ops.clear();
                            follow_up.push(OrganizerRequest::Status);
                        } else {
                            self.accept_draft(draft);
                        }
                        self.error = None;
                    }
                    Err(error) => {
                        self.error = Some(error);
                        if is_ops {
                            // Rejected edits are dropped; show the server's state.
                            self.pending_ops.clear();
                            follow_up.push(OrganizerRequest::Status);
                        }
                    }
                }
                follow_up.extend(self.next_ops());
            }
            OrganizerResponse::Preview {
                key,
                group_id: _,
                result,
            } => {
                if self.preview_requested != Some(key) {
                    return follow_up;
                }
                self.preview_loading = false;
                match result {
                    Ok(plan) => self.plan = Some((key, plan)),
                    Err(error) => {
                        self.plan = None;
                        self.error = Some(error);
                    }
                }
            }
            OrganizerResponse::Done { request, result } => {
                self.commands_in_flight = self.commands_in_flight.saturating_sub(1);
                if let Err(error) = result {
                    self.error = Some(error);
                }
                if matches!(
                    request,
                    OrganizerRequest::Execute { .. } | OrganizerRequest::Undo { .. }
                ) {
                    self.plan = None;
                }
                follow_up.push(OrganizerRequest::Status);
            }
        }
        follow_up
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn draft(id: &str, revision: u64) -> OrganizationDraft {
        OrganizationDraft {
            id: id.into(),
            revision,
            ..Default::default()
        }
    }

    fn status(draft: Option<OrganizationDraft>) -> OrganizerResponse {
        OrganizerResponse::Status {
            generation: 5,
            result: Ok(OrganizationStatus {
                draft,
                ..Default::default()
            }),
        }
    }

    #[test]
    fn ops_serialize_as_tagged_camel_case_and_batch_while_one_is_in_flight() {
        let op = DraftOp::MoveFiles {
            media_ids: vec!["a".into()],
            group_id: None,
            title: Some("Show".into()),
        };
        assert_eq!(
            serde_json::to_value(&op).unwrap(),
            serde_json::json!({"op": "moveFiles", "mediaIds": ["a"], "title": "Show"})
        );
        let owner = DraftOp::SetCompanionOwner {
            path: "x".into(),
            owner: CompanionOwner::Series {
                group_id: "g".into(),
            },
        };
        assert_eq!(
            serde_json::to_value(&owner).unwrap()["owner"],
            serde_json::json!({"kind": "series", "groupId": "g"})
        );
        let mut state = OrganizerState {
            draft: Some(draft("d", 1)),
            ..Default::default()
        };
        assert!(state.queue_ops(vec![op.clone()]).is_some());
        assert!(state.queue_ops(vec![op.clone()]).is_none());
        assert!(state.queue_ops(vec![op]).is_none());
        let follow_up = state.finish(
            OrganizerResponse::Draft {
                request: OrganizerRequest::Ops {
                    draft_id: "d".into(),
                    ops: Vec::new(),
                },
                result: Ok(Some(draft("d", 2))),
            },
            0,
        );
        // The two queued edits go out together.
        assert!(matches!(&follow_up[..], [OrganizerRequest::Ops { ops, .. }] if ops.len() == 2));
        assert_eq!(state.draft.as_ref().unwrap().revision, 2);
    }

    #[test]
    fn stale_status_never_replaces_a_newer_or_recreated_draft() {
        let mut state = OrganizerState {
            draft: Some(draft("d", 4)),
            ..Default::default()
        };
        state.finish(status(Some(draft("d", 3))), 0);
        assert_eq!(state.draft.as_ref().unwrap().revision, 4);
        state.finish(status(Some(draft("d", 6))), 0);
        assert_eq!(state.draft.as_ref().unwrap().revision, 6);
        // A status requested before a discard completed cannot resurrect it.
        state.commands_in_flight = 1;
        state.finish(
            OrganizerResponse::Draft {
                request: OrganizerRequest::Discard,
                result: Ok(None),
            },
            5,
        );
        state.finish(status(Some(draft("d", 6))), 0);
        assert!(state.draft.is_none());
        state.finish(
            OrganizerResponse::Status {
                generation: 6,
                result: Ok(OrganizationStatus::default()),
            },
            6,
        );
        assert!(state.draft.is_none());
    }

    #[test]
    fn rejected_edits_surface_the_error_and_resync() {
        let mut state = OrganizerState {
            draft: Some(draft("d", 1)),
            ..Default::default()
        };
        let request = state
            .queue_ops(vec![DraftOp::ConfirmGroup {
                group_id: "g".into(),
            }])
            .unwrap();
        state.queue_ops(vec![DraftOp::ConfirmGroup {
            group_id: "h".into(),
        }]);
        let follow_up = state.finish(
            OrganizerResponse::Draft {
                request,
                result: Err("This series is already finished.".into()),
            },
            0,
        );
        assert_eq!(follow_up, vec![OrganizerRequest::Status]);
        assert!(!state.saving());
        assert_eq!(
            state.error.as_deref(),
            Some("This series is already finished.")
        );
    }
}
