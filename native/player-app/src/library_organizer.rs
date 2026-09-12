//! Desktop organizer wire contracts.
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
#[serde(rename_all = "camelCase")]
pub struct DraftFile {
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

#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct OrganizationDraft {
    #[serde(default)]
    pub identification_pause: Option<String>,
    #[serde(default)]
    pub retry_not_before_epoch_ms: Option<u64>,
    pub id: String,
    pub revision: u64,
    pub destination: String,
    pub files: Vec<DraftFile>,
    pub companion_choices: BTreeMap<String, bool>,
    #[serde(default)]
    pub companion_owners: BTreeMap<String, String>,
    pub active_group: Option<String>,
    pub skipped: BTreeSet<String>,
    pub completed: BTreeMap<String, String>,
}

#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateDraftRequest {
    pub media_ids: Vec<String>,
    pub destination: String,
}

#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct OrganizationPreviewRequest {
    #[serde(default)]
    pub group_id: Option<String>,
    pub draft_id: String,
    pub revision: u64,
}

#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct IdentificationStatus {
    pub running: bool,
    pub completed: usize,
    pub total: usize,
    pub media_id: Option<String>,
}

#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct IdentifyRequest {
    pub draft_id: String,
    pub revision: u64,
    #[serde(default)]
    pub media_ids: Vec<String>,
    #[serde(default)]
    pub query: Option<String>,
}
