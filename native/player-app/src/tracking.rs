//! Native account connection and external-list tracking UI/wire contract.

use std::io::{Read, Write};
use std::net::TcpListener;
use std::time::{Duration, Instant};

use eframe::egui::{self, Color32, Frame, RichText, TextEdit};
use serde::{Deserialize, Serialize};

use crate::localization::Language;
use crate::net::{http_authenticated_json, percent_encode_path_segment};
use crate::theme::{palette, typography};

pub const MAL_CALLBACK_ADDRESS: &str = "127.0.0.1:18765";

#[derive(Clone, Debug, Default, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ProviderAccounts {
    pub my_anime_list: ProviderAccountStatus,
    pub bangumi: ProviderAccountStatus,
    pub bangumi_token_url: String,
}

#[derive(Clone, Debug, Default, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ProviderAccountStatus {
    pub state: String,
    pub user_id: Option<String>,
    pub display_name: Option<String>,
    pub last_verified_at_epoch_ms: Option<u64>,
    pub reason_code: Option<String>,
}

impl ProviderAccountStatus {
    pub fn is_connected(&self) -> bool {
        self.state == "CONNECTED"
    }
}

#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MyAnimeListOAuthStart {
    pub flow_id: String,
    pub authorization_url: String,
    pub callback_url: String,
    pub expires_at_epoch_ms: u64,
}

#[derive(Clone, Copy, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum ExternalProvider {
    MyAnimeList,
    Bangumi,
    Dandanplay,
}

impl ExternalProvider {
    pub fn label(self) -> &'static str {
        match self {
            Self::MyAnimeList => "MyAnimeList",
            Self::Bangumi => "Bangumi",
            Self::Dandanplay => "dandanplay",
        }
    }

    fn query_value(self) -> &'static str {
        match self {
            Self::MyAnimeList => "myanimelist",
            Self::Bangumi => "bangumi",
            Self::Dandanplay => "dandanplay",
        }
    }
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ExternalAnimeId {
    pub provider: ExternalProvider,
    pub value: u64,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ExternalMapping {
    pub local_series_id: String,
    pub anime_id: ExternalAnimeId,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TrackingSeries {
    pub id: String,
    pub title: String,
    pub local_series_ids: Vec<String>,
    pub episode_count: usize,
    pub mappings: Vec<ExternalMapping>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct TrackingUpdateValue {
    pub anime_id: ExternalAnimeId,
    pub status: Option<String>,
    pub watched_episodes: Option<u32>,
    pub score: Option<u32>,
    pub tracking_enabled: bool,
    pub rating_enabled: bool,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TrackingUpdate {
    pub local_series_id: String,
    pub series_title: String,
    pub mapping: ExternalMapping,
    pub update: TrackingUpdateValue,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TrackingListEntry {
    pub watched_episodes: Option<u32>,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TrackingConflict {
    pub local_series_id: String,
    pub series_title: String,
    pub mapping: ExternalMapping,
    pub local_update: TrackingUpdateValue,
    pub external_entry: TrackingListEntry,
}

#[derive(Clone, Debug, Default, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TrackingPlanSummary {
    pub update_count: usize,
    pub skipped_count: usize,
    pub conflict_count: usize,
    pub failure_count: usize,
}

#[derive(Clone, Debug, Default, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TrackingPlan {
    pub summary: TrackingPlanSummary,
    pub updates: Vec<TrackingUpdate>,
    pub conflicts: Vec<TrackingConflict>,
}

#[derive(Clone, Debug, Default, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TrackingDocument {
    pub series: Vec<TrackingSeries>,
    pub plan: TrackingPlan,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SearchTitles {
    pub primary: String,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SearchAnime {
    pub id: ExternalAnimeId,
    pub titles: SearchTitles,
    pub episode_count: Option<u32>,
    pub start_year: Option<u32>,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SearchCandidate {
    pub anime: SearchAnime,
}

#[derive(Default)]
pub struct MappingEditor {
    pub local_series_id: String,
    pub title: String,
    pub query: String,
    pub provider: Option<ExternalProvider>,
    pub results: Vec<SearchCandidate>,
    pub searching: bool,
}

#[derive(Default)]
pub struct TrackingScreenState {
    pub accounts: Option<ProviderAccounts>,
    pub document: Option<TrackingDocument>,
    pub loading: bool,
    pub error: Option<String>,
    pub notice: Option<String>,
    pub bangumi_token: String,
    pub mapping_editor: Option<MappingEditor>,
}

#[derive(Clone, Debug)]
pub enum TrackingAction {
    Back,
    Refresh,
    StartMyAnimeList,
    ConnectBangumi(String),
    Disconnect(ExternalProvider),
    Readback,
    Sync(Vec<TrackingUpdateValue>),
    Search {
        local_series_id: String,
        query: String,
        provider: Option<ExternalProvider>,
    },
    SaveMapping {
        local_series_id: String,
        anime_id: ExternalAnimeId,
    },
    DeleteMapping {
        local_series_id: String,
        anime_id: ExternalAnimeId,
    },
    ImportConflict {
        local_series_id: String,
        anime_id: ExternalAnimeId,
        expected_external_watched_episodes: u32,
    },
}

pub fn fetch_accounts(base_url: &str, token: &str) -> Result<ProviderAccounts, String> {
    let body = http_authenticated_json(base_url, token, "GET", "/api/providers/accounts", None)?;
    serde_json::from_str(&body).map_err(|error| format!("invalid account response: {error}"))
}

pub fn start_my_anime_list_oauth(
    base_url: &str,
    token: &str,
) -> Result<MyAnimeListOAuthStart, String> {
    let body = http_authenticated_json(
        base_url,
        token,
        "POST",
        "/api/providers/accounts/myanimelist/oauth/start",
        Some("{}"),
    )?;
    serde_json::from_str(&body).map_err(|error| format!("invalid OAuth response: {error}"))
}

pub fn complete_my_anime_list_oauth(
    base_url: &str,
    token: &str,
    flow_id: &str,
    state: &str,
    code: &str,
) -> Result<ProviderAccounts, String> {
    let request = serde_json::json!({ "flowId": flow_id, "state": state, "code": code });
    let body = http_authenticated_json(
        base_url,
        token,
        "POST",
        "/api/providers/accounts/myanimelist/oauth/complete",
        Some(&request.to_string()),
    )?;
    serde_json::from_str(&body).map_err(|error| format!("invalid account response: {error}"))
}

pub fn connect_bangumi(
    base_url: &str,
    token: &str,
    access_token: &str,
) -> Result<ProviderAccounts, String> {
    let request = serde_json::json!({ "accessToken": access_token });
    let body = http_authenticated_json(
        base_url,
        token,
        "PUT",
        "/api/providers/accounts/bangumi",
        Some(&request.to_string()),
    )?;
    serde_json::from_str(&body).map_err(|error| format!("invalid account response: {error}"))
}

pub fn disconnect_account(
    base_url: &str,
    token: &str,
    provider: ExternalProvider,
) -> Result<ProviderAccounts, String> {
    let path = match provider {
        ExternalProvider::MyAnimeList => "/api/providers/accounts/myanimelist",
        ExternalProvider::Bangumi => "/api/providers/accounts/bangumi",
        ExternalProvider::Dandanplay => return Err("dandanplay is not a list account".to_owned()),
    };
    let body = http_authenticated_json(base_url, token, "DELETE", path, None)?;
    serde_json::from_str(&body).map_err(|error| format!("invalid account response: {error}"))
}

pub fn fetch_tracking(base_url: &str, token: &str) -> Result<TrackingDocument, String> {
    let body = http_authenticated_json(base_url, token, "GET", "/api/providers/tracking", None)?;
    serde_json::from_str(&body).map_err(|error| format!("invalid tracking response: {error}"))
}

pub fn refresh_readback(base_url: &str, token: &str) -> Result<TrackingDocument, String> {
    operation_document(base_url, token, "/api/providers/tracking/readback", "{}")
}

pub fn sync_updates(
    base_url: &str,
    token: &str,
    updates: &[TrackingUpdateValue],
) -> Result<TrackingDocument, String> {
    let body = serde_json::json!({ "expectedUpdates": updates }).to_string();
    operation_document(base_url, token, "/api/providers/tracking/sync", &body)
}

fn operation_document(
    base_url: &str,
    token: &str,
    path: &str,
    request: &str,
) -> Result<TrackingDocument, String> {
    let body = http_authenticated_json(base_url, token, "POST", path, Some(request))?;
    let value: serde_json::Value = serde_json::from_str(&body)
        .map_err(|error| format!("invalid tracking response: {error}"))?;
    serde_json::from_value(value["document"].clone())
        .map_err(|error| format!("invalid tracking document: {error}"))
}

pub fn save_mapping(
    base_url: &str,
    token: &str,
    local_series_id: &str,
    anime_id: &ExternalAnimeId,
) -> Result<TrackingDocument, String> {
    mapping_request(base_url, token, "PUT", local_series_id, anime_id)
}

pub fn delete_mapping(
    base_url: &str,
    token: &str,
    local_series_id: &str,
    anime_id: &ExternalAnimeId,
) -> Result<TrackingDocument, String> {
    mapping_request(base_url, token, "DELETE", local_series_id, anime_id)
}

fn mapping_request(
    base_url: &str,
    token: &str,
    method: &str,
    local_series_id: &str,
    anime_id: &ExternalAnimeId,
) -> Result<TrackingDocument, String> {
    let request = serde_json::json!({ "localSeriesId": local_series_id, "animeId": anime_id });
    let body = http_authenticated_json(
        base_url,
        token,
        method,
        "/api/providers/tracking/mapping",
        Some(&request.to_string()),
    )?;
    serde_json::from_str(&body).map_err(|error| format!("invalid tracking response: {error}"))
}

pub fn import_conflict(
    base_url: &str,
    token: &str,
    local_series_id: &str,
    anime_id: &ExternalAnimeId,
    expected_external_watched_episodes: u32,
) -> Result<TrackingDocument, String> {
    let request = serde_json::json!({
        "localSeriesId": local_series_id,
        "animeId": anime_id,
        "expectedExternalWatchedEpisodes": expected_external_watched_episodes,
    });
    operation_document(
        base_url,
        token,
        "/api/providers/tracking/conflicts/import",
        &request.to_string(),
    )
}

pub fn search(
    base_url: &str,
    query: &str,
    provider: Option<ExternalProvider>,
) -> Result<Vec<SearchCandidate>, String> {
    let mut path = format!(
        "/api/providers/search?title={}&limit=8",
        percent_encode_path_segment(query)
    );
    if let Some(provider) = provider {
        path.push_str("&provider=");
        path.push_str(provider.query_value());
    }
    let body = crate::net::http_get(base_url, &path)?;
    serde_json::from_str(&body).map_err(|error| format!("invalid search response: {error}"))
}

pub fn bind_mal_callback() -> Result<TcpListener, String> {
    let listener = TcpListener::bind(MAL_CALLBACK_ADDRESS).map_err(|error| {
        format!("MyAnimeList sign-in callback port 18765 is unavailable: {error}")
    })?;
    listener
        .set_nonblocking(true)
        .map_err(|error| format!("failed to configure OAuth callback: {error}"))?;
    Ok(listener)
}

pub fn wait_for_mal_callback(listener: TcpListener) -> Result<(String, String), String> {
    let deadline = Instant::now() + Duration::from_secs(10 * 60);
    let mut stream = loop {
        match listener.accept() {
            Ok((stream, _)) => break stream,
            Err(error) if error.kind() == std::io::ErrorKind::WouldBlock => {
                if Instant::now() >= deadline {
                    return Err("MyAnimeList sign-in expired; try connecting again".to_owned());
                }
                std::thread::sleep(Duration::from_millis(100));
            }
            Err(error) => return Err(format!("failed to receive OAuth callback: {error}")),
        }
    };
    stream
        .set_nonblocking(false)
        .map_err(|error| format!("failed to configure OAuth callback connection: {error}"))?;
    stream
        .set_read_timeout(Some(Duration::from_secs(5)))
        .map_err(|error| format!("failed to configure OAuth callback timeout: {error}"))?;
    let mut request = [0_u8; 16_384];
    let size = stream
        .read(&mut request)
        .map_err(|error| format!("failed to read OAuth callback: {error}"))?;
    let first_line = String::from_utf8_lossy(&request[..size])
        .lines()
        .next()
        .unwrap_or_default()
        .to_owned();
    let target = first_line
        .split_whitespace()
        .nth(1)
        .ok_or_else(|| "OAuth callback was malformed".to_owned())?;
    let query = target
        .split_once('?')
        .map(|(_, query)| query)
        .ok_or_else(|| "OAuth callback omitted its query".to_owned())?;
    let state =
        query_value(query, "state").ok_or_else(|| "OAuth callback omitted state".to_owned())?;
    let code =
        query_value(query, "code").ok_or_else(|| "OAuth callback omitted code".to_owned())?;
    let html = "<!doctype html><meta charset=utf-8><title>Danmaku</title><h1>Sign-in received</h1><p>You can return to Danmaku.</p>";
    let response = format!(
        "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
        html.len(),
        html,
    );
    let _ = stream.write_all(response.as_bytes());
    Ok((state, code))
}

fn query_value(query: &str, key: &str) -> Option<String> {
    query.split('&').find_map(|part| {
        let (name, value) = part.split_once('=').unwrap_or((part, ""));
        (name == key).then(|| percent_decode(value))
    })
}

fn percent_decode(value: &str) -> String {
    let bytes = value.as_bytes();
    let mut output = Vec::with_capacity(bytes.len());
    let mut index = 0;
    while index < bytes.len() {
        if bytes[index] == b'%'
            && index + 2 < bytes.len()
            && let (Some(high), Some(low)) = (hex(bytes[index + 1]), hex(bytes[index + 2]))
        {
            output.push((high << 4) | low);
            index += 3;
        } else {
            output.push(if bytes[index] == b'+' {
                b' '
            } else {
                bytes[index]
            });
            index += 1;
        }
    }
    String::from_utf8_lossy(&output).into_owned()
}

fn hex(value: u8) -> Option<u8> {
    match value {
        b'0'..=b'9' => Some(value - b'0'),
        b'a'..=b'f' => Some(value - b'a' + 10),
        b'A'..=b'F' => Some(value - b'A' + 10),
        _ => None,
    }
}

pub fn show_tracking(
    ctx: &egui::Context,
    state: &mut TrackingScreenState,
    language: Language,
) -> Option<TrackingAction> {
    let copy = Copy::new(language);
    let mut action = None;
    egui::CentralPanel::default()
        .frame(Frame::NONE.fill(palette::BG_DEEP))
        .show(ctx, |ui| {
            egui::ScrollArea::vertical().show(ui, |ui| {
                ui.set_max_width(860.0);
                ui.add_space(20.0);
                ui.horizontal(|ui| {
                    if ui.button(copy.back).clicked() {
                        action = Some(TrackingAction::Back);
                    }
                    ui.heading(copy.title);
                    if ui.button(copy.refresh).clicked() {
                        action = Some(TrackingAction::Refresh);
                    }
                });
                ui.label(RichText::new(copy.subtitle).color(palette::TEXT_MUTED));
                if state.loading {
                    ui.spinner();
                }
                if let Some(error) = &state.error {
                    ui.colored_label(palette::DANGER, error);
                }
                if let Some(notice) = &state.notice {
                    ui.colored_label(palette::SUCCESS, notice);
                }
                ui.add_space(12.0);
                card(ui, copy.accounts, |ui| {
                    let accounts = state.accounts.clone().unwrap_or_default();
                    if let Some(connecting) =
                        account_row(ui, "MyAnimeList", &accounts.my_anime_list, copy)
                    {
                        action = Some(if connecting {
                            TrackingAction::StartMyAnimeList
                        } else {
                            TrackingAction::Disconnect(ExternalProvider::MyAnimeList)
                        });
                    }
                    ui.separator();
                    ui.horizontal(|ui| {
                        ui.vertical(|ui| {
                            ui.strong("Bangumi");
                            ui.label(account_label(&accounts.bangumi, copy));
                        });
                        ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                            if accounts.bangumi.is_connected() {
                                if ui.button(copy.disconnect).clicked() {
                                    action =
                                        Some(TrackingAction::Disconnect(ExternalProvider::Bangumi));
                                }
                            } else if ui.button(copy.connect).clicked()
                                && !state.bangumi_token.trim().is_empty()
                            {
                                action = Some(TrackingAction::ConnectBangumi(
                                    state.bangumi_token.trim().to_owned(),
                                ));
                            }
                        });
                    });
                    if !accounts.bangumi.is_connected() {
                        ui.horizontal(|ui| {
                            ui.add(
                                TextEdit::singleline(&mut state.bangumi_token)
                                    .password(true)
                                    .hint_text(copy.paste_token),
                            );
                            if !accounts.bangumi_token_url.is_empty() {
                                ui.hyperlink_to(copy.create_token, &accounts.bangumi_token_url);
                            }
                        });
                    }
                });

                ui.add_space(12.0);
                card(ui, copy.preview, |ui| {
                    if let Some(document) = &state.document {
                        let summary = &document.plan.summary;
                        ui.label(format!(
                            "{}: {}  •  {}: {}  •  {}: {}",
                            copy.updates,
                            summary.update_count,
                            copy.conflicts,
                            summary.conflict_count,
                            copy.skipped,
                            summary.skipped_count,
                        ));
                        ui.horizontal(|ui| {
                            if ui.button(copy.readback).clicked() {
                                action = Some(TrackingAction::Readback);
                            }
                            if ui
                                .add_enabled(summary.update_count > 0, egui::Button::new(copy.sync))
                                .clicked()
                            {
                                action = Some(TrackingAction::Sync(
                                    document
                                        .plan
                                        .updates
                                        .iter()
                                        .map(|update| update.update.clone())
                                        .collect(),
                                ));
                            }
                        });
                        for update in &document.plan.updates {
                            ui.label(format!(
                                "{} → {} #{} ({})",
                                update.series_title,
                                update.mapping.anime_id.provider.label(),
                                update.mapping.anime_id.value,
                                update.update.watched_episodes.unwrap_or(0),
                            ));
                        }
                        for conflict in &document.plan.conflicts {
                            ui.horizontal(|ui| {
                                ui.colored_label(
                                    Color32::YELLOW,
                                    format!(
                                        "{}: {} {} / {} {}",
                                        conflict.series_title,
                                        copy.local,
                                        conflict.local_update.watched_episodes.unwrap_or(0),
                                        copy.provider,
                                        conflict.external_entry.watched_episodes.unwrap_or(0),
                                    ),
                                );
                                if ui.button(copy.import).clicked() {
                                    action = Some(TrackingAction::ImportConflict {
                                        local_series_id: conflict.local_series_id.clone(),
                                        anime_id: conflict.mapping.anime_id.clone(),
                                        expected_external_watched_episodes: conflict
                                            .external_entry
                                            .watched_episodes
                                            .unwrap_or(0),
                                    });
                                }
                            });
                        }
                    } else {
                        ui.label(copy.load_hint);
                    }
                });

                ui.add_space(12.0);
                card(ui, copy.mappings, |ui| {
                    let series = state
                        .document
                        .as_ref()
                        .map(|document| document.series.clone())
                        .unwrap_or_default();
                    for series in series {
                        ui.horizontal(|ui| {
                            ui.vertical(|ui| {
                                ui.strong(&series.title);
                                ui.label(format!("{} {}", series.episode_count, copy.episodes));
                                for mapping in &series.mappings {
                                    ui.horizontal(|ui| {
                                        ui.label(format!(
                                            "{} #{}",
                                            mapping.anime_id.provider.label(),
                                            mapping.anime_id.value
                                        ));
                                        if ui.small_button(copy.remove).clicked() {
                                            action = Some(TrackingAction::DeleteMapping {
                                                local_series_id: series.id.clone(),
                                                anime_id: mapping.anime_id.clone(),
                                            });
                                        }
                                    });
                                }
                            });
                            ui.with_layout(
                                egui::Layout::right_to_left(egui::Align::Center),
                                |ui| {
                                    if ui.button(copy.map).clicked() {
                                        state.mapping_editor = Some(MappingEditor {
                                            local_series_id: series.id.clone(),
                                            title: series.title.clone(),
                                            query: series.title.clone(),
                                            ..Default::default()
                                        });
                                    }
                                },
                            );
                        });
                        ui.separator();
                    }
                    if let Some(editor) = &mut state.mapping_editor {
                        ui.heading(format!("{}: {}", copy.map, editor.title));
                        ui.horizontal(|ui| {
                            ui.text_edit_singleline(&mut editor.query);
                            egui::ComboBox::from_id_salt("tracking_provider")
                                .selected_text(
                                    editor
                                        .provider
                                        .map_or(copy.all_providers, ExternalProvider::label),
                                )
                                .show_ui(ui, |ui| {
                                    ui.selectable_value(
                                        &mut editor.provider,
                                        None,
                                        copy.all_providers,
                                    );
                                    ui.selectable_value(
                                        &mut editor.provider,
                                        Some(ExternalProvider::MyAnimeList),
                                        "MyAnimeList",
                                    );
                                    ui.selectable_value(
                                        &mut editor.provider,
                                        Some(ExternalProvider::Bangumi),
                                        "Bangumi",
                                    );
                                });
                            if ui.button(copy.search).clicked() && !editor.query.trim().is_empty() {
                                editor.searching = true;
                                action = Some(TrackingAction::Search {
                                    local_series_id: editor.local_series_id.clone(),
                                    query: editor.query.trim().to_owned(),
                                    provider: editor.provider,
                                });
                            }
                        });
                        for result in &editor.results {
                            ui.horizontal(|ui| {
                                ui.label(format!(
                                    "{} — {} #{}{}",
                                    result.anime.titles.primary,
                                    result.anime.id.provider.label(),
                                    result.anime.id.value,
                                    result
                                        .anime
                                        .start_year
                                        .map(|year| format!(" ({year})"))
                                        .unwrap_or_default(),
                                ));
                                if ui.button(copy.use_result).clicked() {
                                    action = Some(TrackingAction::SaveMapping {
                                        local_series_id: editor.local_series_id.clone(),
                                        anime_id: result.anime.id.clone(),
                                    });
                                }
                            });
                        }
                    }
                });
                ui.add_space(30.0);
            });
        });
    action
}

fn card(ui: &mut egui::Ui, title: &str, contents: impl FnOnce(&mut egui::Ui)) {
    Frame::NONE
        .fill(palette::SURFACE_RAISED)
        .corner_radius(14)
        .inner_margin(16)
        .show(ui, |ui| {
            ui.set_width(ui.available_width());
            ui.label(RichText::new(title).font(typography::title()).strong());
            ui.add_space(8.0);
            contents(ui);
        });
}

fn account_row(
    ui: &mut egui::Ui,
    name: &str,
    account: &ProviderAccountStatus,
    copy: Copy,
) -> Option<bool> {
    let mut result = None;
    ui.horizontal(|ui| {
        ui.vertical(|ui| {
            ui.strong(name);
            ui.label(account_label(account, copy));
        });
        ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
            if account.is_connected() {
                if ui.button(copy.disconnect).clicked() {
                    result = Some(false);
                }
            } else if ui
                .add_enabled(
                    account.state != "UNAVAILABLE",
                    egui::Button::new(copy.connect),
                )
                .clicked()
            {
                result = Some(true);
            }
        });
    });
    result
}

fn account_label(account: &ProviderAccountStatus, copy: Copy) -> String {
    match account.state.as_str() {
        "CONNECTED" => format!(
            "{} — {}",
            copy.connected,
            account
                .display_name
                .as_deref()
                .or(account.user_id.as_deref())
                .unwrap_or(copy.account)
        ),
        "NEEDS_RECONNECT" => copy.reconnect.to_owned(),
        "UNAVAILABLE" => copy.unavailable.to_owned(),
        _ => copy.disconnected.to_owned(),
    }
}

#[derive(Clone, Copy)]
struct Copy {
    title: &'static str,
    subtitle: &'static str,
    back: &'static str,
    refresh: &'static str,
    accounts: &'static str,
    connect: &'static str,
    disconnect: &'static str,
    connected: &'static str,
    disconnected: &'static str,
    reconnect: &'static str,
    unavailable: &'static str,
    account: &'static str,
    paste_token: &'static str,
    create_token: &'static str,
    preview: &'static str,
    updates: &'static str,
    conflicts: &'static str,
    skipped: &'static str,
    readback: &'static str,
    sync: &'static str,
    local: &'static str,
    provider: &'static str,
    import: &'static str,
    load_hint: &'static str,
    mappings: &'static str,
    episodes: &'static str,
    remove: &'static str,
    map: &'static str,
    all_providers: &'static str,
    search: &'static str,
    use_result: &'static str,
}

impl Copy {
    fn new(language: Language) -> Self {
        match language {
            Language::English => Self {
                title: "Accounts & Tracking",
                subtitle: "Connect once, map each series, then review every list update before it is sent.",
                back: "Back",
                refresh: "Refresh",
                accounts: "Accounts",
                connect: "Connect",
                disconnect: "Disconnect",
                connected: "Connected",
                disconnected: "Not connected",
                reconnect: "Reconnect required",
                unavailable: "Unavailable in this build",
                account: "account",
                paste_token: "Paste Bangumi token",
                create_token: "Create token",
                preview: "Review & sync",
                updates: "Updates",
                conflicts: "Conflicts",
                skipped: "Skipped",
                readback: "Check provider progress",
                sync: "Confirm and sync",
                local: "local",
                provider: "provider",
                import: "Import provider progress",
                load_hint: "Refresh to load the current tracking plan.",
                mappings: "Series mappings",
                episodes: "episodes",
                remove: "Remove",
                map: "Find match",
                all_providers: "MAL + Bangumi",
                search: "Search",
                use_result: "Use",
            },
            Language::TraditionalChinese => Self {
                title: "帳號與追蹤",
                subtitle: "連接帳號、配對作品，並在送出前確認每次清單更新。",
                back: "返回",
                refresh: "重新整理",
                accounts: "帳號",
                connect: "連接",
                disconnect: "中斷連接",
                connected: "已連接",
                disconnected: "尚未連接",
                reconnect: "需要重新連接",
                unavailable: "此版本無法使用",
                account: "帳號",
                paste_token: "貼上 Bangumi 權杖",
                create_token: "建立權杖",
                preview: "檢查與同步",
                updates: "更新",
                conflicts: "衝突",
                skipped: "略過",
                readback: "檢查服務進度",
                sync: "確認並同步",
                local: "本機",
                provider: "服務",
                import: "匯入服務進度",
                load_hint: "重新整理以載入目前的追蹤計畫。",
                mappings: "作品配對",
                episodes: "集",
                remove: "移除",
                map: "尋找配對",
                all_providers: "MAL + Bangumi",
                search: "搜尋",
                use_result: "使用",
            },
        }
    }
}

#[cfg(test)]
mod tests {
    use super::{percent_decode, query_value};

    #[test]
    fn parses_encoded_oauth_callback_values() {
        assert_eq!(
            query_value("state=a%2Fb&code=c+d", "state").as_deref(),
            Some("a/b")
        );
        assert_eq!(
            query_value("state=a%2Fb&code=c+d", "code").as_deref(),
            Some("c d")
        );
        assert_eq!(percent_decode("%E6%97%A5"), "日");
    }
}
