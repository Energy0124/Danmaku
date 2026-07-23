//! Native danmaku loading, normalized layout, and server transport.

use std::{
    fs,
    path::{Path, PathBuf},
};

use danmaku_core::{DanmakuEvent, Timeline};
use serde_json::Value;

use crate::net::{http_get, percent_encode_path_segment};

#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub enum DanmakuMode {
    #[default]
    Scrolling,
    Top,
    Bottom,
}

#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub enum DanmakuSize {
    Small,
    #[default]
    Normal,
    Large,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct DanmakuStyle {
    pub color_argb: u32,
    pub mode: DanmakuMode,
    pub size: DanmakuSize,
}

impl Default for DanmakuStyle {
    fn default() -> Self {
        Self {
            color_argb: 0xffff_ffff,
            mode: DanmakuMode::Scrolling,
            size: DanmakuSize::Normal,
        }
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct DanmakuComment {
    pub id: String,
    pub timestamp_ms: u64,
    pub text: String,
    pub style: DanmakuStyle,
}

#[derive(Clone, Debug)]
struct CommentMetadata {
    style: DanmakuStyle,
    density_key: u64,
}

#[derive(Clone, Debug, Default)]
pub struct DanmakuTrack {
    timeline: Timeline,
    metadata: Vec<CommentMetadata>,
}

impl DanmakuTrack {
    pub fn new(comments: Vec<DanmakuComment>) -> Self {
        let mut events = Vec::with_capacity(comments.len());
        let mut metadata = Vec::with_capacity(comments.len());
        for (index, comment) in comments.into_iter().enumerate() {
            metadata.push(CommentMetadata {
                style: comment.style,
                density_key: stable_density_key(&comment),
            });
            events.push(DanmakuEvent {
                id: index as u64,
                timestamp_ms: comment.timestamp_ms,
                text: comment.text,
            });
        }
        Self {
            timeline: Timeline::new(events),
            metadata,
        }
    }

    pub fn len(&self) -> usize {
        self.timeline.len()
    }

    pub fn is_empty(&self) -> bool {
        self.timeline.is_empty()
    }

    fn metadata_for(&self, event: &DanmakuEvent) -> Option<&CommentMetadata> {
        self.metadata.get(event.id as usize)
    }
}

#[derive(Clone, Debug, PartialEq)]
pub struct DanmakuDisplaySettings {
    pub enabled: bool,
    pub opacity: f32,
    pub speed: f32,
    pub density: f32,
    pub max_lanes: usize,
}

impl Default for DanmakuDisplaySettings {
    fn default() -> Self {
        Self {
            enabled: true,
            opacity: 0.92,
            speed: 1.0,
            density: 1.0,
            max_lanes: 12,
        }
    }
}

impl DanmakuDisplaySettings {
    pub fn sanitized(&self) -> Self {
        Self {
            enabled: self.enabled,
            opacity: finite_or(self.opacity, 0.92).clamp(0.0, 1.0),
            speed: finite_or(self.speed, 1.0).clamp(0.25, 4.0),
            density: finite_or(self.density, 1.0).clamp(0.0, 1.0),
            max_lanes: self.max_lanes.clamp(1, 64),
        }
    }
}

#[derive(Clone, Debug)]
pub struct DanmakuLayout {
    pub scroll_duration_ms: u64,
    pub static_duration_ms: u64,
    pub font_px: f32,
    pub lane_gap_px: f32,
    pub horizontal_padding_px: f32,
}

impl Default for DanmakuLayout {
    fn default() -> Self {
        Self {
            scroll_duration_ms: 12_000,
            static_duration_ms: 5_000,
            font_px: 20.0,
            lane_gap_px: 4.0,
            horizontal_padding_px: 32.0,
        }
    }
}

#[derive(Clone, Debug)]
pub struct ScheduledComment<'a> {
    pub event: &'a DanmakuEvent,
    pub style: DanmakuStyle,
    pub lane: usize,
    pub x: f32,
    pub y: f32,
    pub age_ms: f64,
    pub duration_ms: f64,
    pub opacity: f32,
    pub font_px: f32,
}

#[derive(Clone, Debug)]
struct LaneState {
    available_at_ms: u64,
}

impl DanmakuLayout {
    pub fn visible_comments<'a>(
        &self,
        track: &'a DanmakuTrack,
        playback_ms: f64,
        video_width: f32,
        video_height: f32,
        settings: &DanmakuDisplaySettings,
    ) -> Vec<ScheduledComment<'a>> {
        let settings = settings.sanitized();
        if !settings.enabled || video_width <= 1.0 || video_height <= 1.0 {
            return Vec::new();
        }

        let playback_ms = if playback_ms.is_finite() {
            playback_ms.max(0.0)
        } else {
            0.0
        };
        let scroll_duration_ms = (self.scroll_duration_ms as f32 / settings.speed)
            .round()
            .max(1.0) as u64;
        let lookback_ms = scroll_duration_ms.max(self.static_duration_ms);
        let query_start = floor_ms(playback_ms - lookback_ms as f64);
        let query_end = floor_ms(playback_ms).saturating_add(1);
        let candidates = track.timeline.events_in_window(query_start, query_end);
        let line_height = self.font_px * 1.25 + self.lane_gap_px;
        let available_scroll_lanes =
            ((video_height * 0.74) / line_height).floor().max(1.0) as usize;
        let scroll_lanes = available_scroll_lanes.min(settings.max_lanes);
        let available_static_lanes =
            ((video_height * 0.13) / line_height).floor().max(1.0) as usize;
        let static_lanes = available_static_lanes.min((settings.max_lanes / 3).max(1));

        let mut scroll_state = vec![LaneState { available_at_ms: 0 }; scroll_lanes];
        let mut top_state = vec![LaneState { available_at_ms: 0 }; static_lanes];
        let mut bottom_state = vec![LaneState { available_at_ms: 0 }; static_lanes];
        let mut visible = Vec::with_capacity(candidates.len());

        for event in candidates {
            let Some(metadata) = track.metadata_for(event) else {
                continue;
            };
            if !passes_density(metadata.density_key, settings.density) {
                continue;
            }
            let duration_ms = match metadata.style.mode {
                DanmakuMode::Scrolling => scroll_duration_ms,
                DanmakuMode::Top | DanmakuMode::Bottom => self.static_duration_ms,
            } as f64;
            let age_ms = playback_ms - event.timestamp_ms as f64;
            if age_ms < 0.0 || age_ms > duration_ms {
                continue;
            }

            let font_px = self.font_px * size_scale(metadata.style.size);
            let text_width = estimate_text_width(&event.text, font_px);
            let (lane, x, y) = match metadata.style.mode {
                DanmakuMode::Scrolling => {
                    let lane = self.assign_scroll_lane(
                        &mut scroll_state,
                        event.timestamp_ms,
                        text_width,
                        video_width,
                        scroll_duration_ms,
                    );
                    let progress = age_ms / scroll_duration_ms as f64;
                    let x = (video_width as f64
                        - progress * (video_width as f64 + text_width as f64))
                        as f32;
                    let y = lane as f32 * line_height + self.lane_gap_px;
                    (lane, x, y)
                }
                DanmakuMode::Top => {
                    let lane = assign_static_lane(
                        &mut top_state,
                        event.timestamp_ms,
                        self.static_duration_ms,
                    );
                    let x = (video_width - text_width).max(0.0) * 0.5;
                    let y = lane as f32 * line_height + self.lane_gap_px;
                    (lane, x, y)
                }
                DanmakuMode::Bottom => {
                    let lane = assign_static_lane(
                        &mut bottom_state,
                        event.timestamp_ms,
                        self.static_duration_ms,
                    );
                    let x = (video_width - text_width).max(0.0) * 0.5;
                    let y = video_height - ((lane + 1) as f32 * line_height) - self.lane_gap_px;
                    (lane, x, y)
                }
            };

            visible.push(ScheduledComment {
                event,
                style: metadata.style,
                lane,
                x,
                y,
                age_ms,
                duration_ms,
                opacity: opacity_for_age(age_ms, duration_ms) * settings.opacity,
                font_px,
            });
        }

        visible
    }

    fn assign_scroll_lane(
        &self,
        lanes: &mut [LaneState],
        timestamp_ms: u64,
        text_width: f32,
        video_width: f32,
        duration_ms: u64,
    ) -> usize {
        let lane = lanes
            .iter()
            .position(|lane| lane.available_at_ms <= timestamp_ms)
            .unwrap_or_else(|| earliest_lane(lanes));
        let pixels_per_ms = (video_width + text_width).max(1.0) / duration_ms as f32;
        let release_ms = ((text_width + self.horizontal_padding_px) / pixels_per_ms).ceil() as u64;
        lanes[lane].available_at_ms = timestamp_ms.saturating_add(release_ms.max(1));
        lane
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum DanmakuLoadKind {
    None,
    Local,
    Server,
    Ass,
    Failed,
}

#[derive(Clone, Debug)]
pub struct DanmakuLoad {
    pub kind: DanmakuLoadKind,
    pub track: DanmakuTrack,
    pub status: String,
    pub ass_path: Option<PathBuf>,
    /// Raw status returned by `/api/danmaku/{mediaId}` for server loads.
    /// Preserved so repair callers can distinguish READY from FAILED/UNAVAILABLE.
    pub server_status: Option<String>,
    /// Recognized anime title (no episode suffix) from a server danmaku
    /// match, when the server resolved one. Compared against the catalog's
    /// recognized `animeMetadata.displayTitle` to detect that the library
    /// grouping for this item is stale (see `PlayerApp::handle_session_events`).
    pub match_title: Option<String>,
}

impl DanmakuLoad {
    pub fn none() -> Self {
        Self {
            kind: DanmakuLoadKind::None,
            track: DanmakuTrack::default(),
            status: "No danmaku source attached".to_owned(),
            ass_path: None,
            server_status: None,
            match_title: None,
        }
    }

    pub fn failed(message: impl Into<String>) -> Self {
        Self {
            kind: DanmakuLoadKind::Failed,
            track: DanmakuTrack::default(),
            status: message.into(),
            ass_path: None,
            server_status: None,
            match_title: None,
        }
    }

    pub fn require_server_ready(self) -> Result<Self, String> {
        if self.server_status.as_deref() == Some("READY") {
            Ok(self)
        } else {
            Err(self.status)
        }
    }
}

pub fn load_local_danmaku(path: &Path) -> Result<DanmakuLoad, String> {
    let path = path
        .canonicalize()
        .map_err(|error| format!("danmaku file is unavailable: {error}"))?;
    if path
        .extension()
        .and_then(|value| value.to_str())
        .is_some_and(|value| value.eq_ignore_ascii_case("ass"))
    {
        return Ok(DanmakuLoad {
            kind: DanmakuLoadKind::Ass,
            track: DanmakuTrack::default(),
            status: format!("ASS overlay attached: {}", path.display()),
            ass_path: Some(path),
            match_title: None,
            server_status: None,
        });
    }

    let source = fs::read_to_string(&path)
        .map_err(|error| format!("failed to read danmaku file: {error}"))?;
    let trimmed = source.trim_start();
    let comments = if path
        .extension()
        .and_then(|value| value.to_str())
        .is_some_and(|value| value.eq_ignore_ascii_case("xml"))
        || trimmed.starts_with('<')
    {
        parse_bilibili_xml(&source)
    } else {
        parse_normalized_json(&source)?
    };
    if comments.is_empty() {
        return Err("danmaku file did not contain supported comments".to_owned());
    }
    let count = comments.len();
    Ok(DanmakuLoad {
        kind: DanmakuLoadKind::Local,
        track: DanmakuTrack::new(comments),
        status: format!("Local danmaku: {count} comments"),
        ass_path: None,
        server_status: None,
        match_title: None,
    })
}

pub fn fetch_server_danmaku(
    base_url: &str,
    media_id: &str,
    force_refresh: bool,
) -> Result<DanmakuLoad, String> {
    if media_id.trim().is_empty() {
        return Err("server danmaku requires a non-blank media ID".to_owned());
    }
    let endpoint = format!(
        "/api/danmaku/{}?forceRefresh={force_refresh}",
        percent_encode_path_segment(media_id.trim())
    );
    let body = http_get(base_url, &endpoint)?;
    let root: Value = serde_json::from_str(&body)
        .map_err(|error| format!("server returned invalid danmaku JSON: {error}"))?;
    let status = root
        .get("status")
        .and_then(Value::as_str)
        .unwrap_or("FAILED");
    let message = root.get("message").and_then(Value::as_str);
    // `animeTitle` (anime only) matches the catalog's recognized
    // displayTitle exactly; `matchTitle` ("Anime - Episode") is only a
    // fallback for older servers and can cause a spurious (harmless)
    // catalog refresh since it never equals the catalog title.
    let match_title = root
        .get("animeTitle")
        .or_else(|| root.get("matchTitle"))
        .and_then(Value::as_str)
        .map(str::to_owned)
        .filter(|title| !title.trim().is_empty());
    if status != "READY" {
        return Ok(DanmakuLoad {
            kind: DanmakuLoadKind::Server,
            track: DanmakuTrack::default(),
            status: message
                .map(str::to_owned)
                .unwrap_or_else(|| format!("Server danmaku status: {status}")),
            ass_path: None,
            match_title,
            server_status: Some(status.to_owned()),
        });
    }
    let comments = root
        .get("comments")
        .and_then(Value::as_array)
        .map(|items| parse_comment_values(items))
        .unwrap_or_default();
    let count = comments.len();
    let source = root
        .get("source")
        .and_then(Value::as_str)
        .unwrap_or("SERVER");
    Ok(DanmakuLoad {
        kind: DanmakuLoadKind::Server,
        track: DanmakuTrack::new(comments),
        status: format!("Server danmaku ({source}): {count} comments"),
        ass_path: None,
        server_status: Some(status.to_owned()),
        match_title,
    })
}

/// One dandanplay match candidate for a media item, as offered by a manual
/// match picker (see `fetch_dandanplay_candidates`/`select_dandanplay_match`).
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct DandanplayMatchCandidate {
    pub episode_id: u64,
    pub anime_title: Option<String>,
    pub episode_title: Option<String>,
    pub display_title: String,
}

/// Lists every dandanplay match candidate for `media_id`, always forcing a
/// fresh match: once an item has been auto-resolved, the server's comment
/// cache only remembers the single candidate it picked, not the full list
/// (see `DandanplayResolver::resolve`), so a picker must bypass it to show
/// alternatives.
pub fn fetch_dandanplay_candidates(
    base_url: &str,
    media_id: &str,
) -> Result<Vec<DandanplayMatchCandidate>, String> {
    if media_id.trim().is_empty() {
        return Err("dandanplay match listing requires a non-blank media ID".to_owned());
    }
    let endpoint = format!(
        "/api/providers/dandanplay/resolve?mediaId={}&forceRefresh=true&withRelated=false",
        percent_encode_path_segment(media_id.trim())
    );
    let body = http_get(base_url, &endpoint)?;
    let root: Value = serde_json::from_str(&body)
        .map_err(|error| format!("server returned invalid match JSON: {error}"))?;
    Ok(root
        .get("matches")
        .and_then(Value::as_array)
        .map(|matches| matches.iter().filter_map(parse_match_candidate).collect())
        .unwrap_or_default())
}

fn parse_match_candidate(value: &Value) -> Option<DandanplayMatchCandidate> {
    let object = value.as_object()?;
    let episode_id = object.get("episodeId")?.as_u64()?;
    let anime_title = object
        .get("animeTitle")
        .and_then(Value::as_str)
        .map(str::to_owned);
    let episode_title = object
        .get("episodeTitle")
        .and_then(Value::as_str)
        .map(str::to_owned);
    let display_title = object
        .get("displayTitle")
        .and_then(Value::as_str)
        .filter(|title| !title.trim().is_empty())
        .map(str::to_owned)
        .unwrap_or_else(|| episode_id.to_string());
    Some(DandanplayMatchCandidate {
        episode_id,
        anime_title,
        episode_title,
        display_title,
    })
}

/// The episode a user picked in the match picker, with the identity the
/// server should record for it. `anime_id`/titles matter when the episode
/// came from a keyword search: hash matching may never propose it, so the
/// server cannot learn them any other way.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct DandanplaySelection {
    pub episode_id: u64,
    pub anime_id: Option<u64>,
    pub anime_title: Option<String>,
    pub episode_title: Option<String>,
}

/// One anime from a dandanplay keyword search, with its episode list.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct DandanplaySearchAnime {
    pub anime_id: u64,
    pub anime_title: String,
    pub type_description: Option<String>,
    pub episodes: Vec<DandanplaySearchEpisode>,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct DandanplaySearchEpisode {
    pub episode_id: u64,
    pub episode_title: String,
}

/// Searches the dandanplay database by anime keyword for the match picker.
pub fn search_dandanplay(
    base_url: &str,
    keyword: &str,
) -> Result<Vec<DandanplaySearchAnime>, String> {
    if keyword.trim().is_empty() {
        return Err("dandanplay search requires a non-blank keyword".to_owned());
    }
    let endpoint = format!(
        "/api/providers/dandanplay/search?keyword={}",
        percent_encode_path_segment(keyword.trim())
    );
    let body = http_get(base_url, &endpoint)?;
    let root: Value = serde_json::from_str(&body)
        .map_err(|error| format!("server returned invalid search JSON: {error}"))?;
    Ok(root
        .get("animes")
        .and_then(Value::as_array)
        .map(|animes| animes.iter().filter_map(parse_search_anime).collect())
        .unwrap_or_default())
}

fn parse_search_anime(value: &Value) -> Option<DandanplaySearchAnime> {
    let object = value.as_object()?;
    let anime_id = object.get("animeId")?.as_u64()?;
    let anime_title = object
        .get("animeTitle")
        .and_then(Value::as_str)
        .filter(|title| !title.trim().is_empty())?
        .to_owned();
    let episodes = object
        .get("episodes")
        .and_then(Value::as_array)
        .into_iter()
        .flatten()
        .filter_map(|episode| {
            let episode_id = episode.get("episodeId")?.as_u64()?;
            Some(DandanplaySearchEpisode {
                episode_id,
                episode_title: episode
                    .get("episodeTitle")
                    .and_then(Value::as_str)
                    .filter(|title| !title.trim().is_empty())
                    .map(str::to_owned)
                    .unwrap_or_else(|| episode_id.to_string()),
            })
        })
        .collect();
    Some(DandanplaySearchAnime {
        anime_id,
        anime_title,
        type_description: object
            .get("typeDescription")
            .and_then(Value::as_str)
            .map(str::to_owned),
        episodes,
    })
}

/// Full dandanplay profile of one anime for the library's information
/// page: community rating, synopsis, tags, per-episode air dates, and
/// links into the public anime databases.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct BangumiDetail {
    pub anime_id: u64,
    pub anime_title: String,
    pub type_description: Option<String>,
    pub summary: Option<String>,
    pub rating: Option<f64>,
    pub is_on_air: Option<bool>,
    pub tags: Vec<String>,
    pub episodes: Vec<BangumiEpisode>,
    pub online_databases: Vec<BangumiOnlineDatabase>,
}

#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct BangumiEpisode {
    pub episode_id: u64,
    pub episode_title: String,
    pub air_date: Option<String>,
}

#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct BangumiOnlineDatabase {
    pub name: String,
    pub url: String,
}

/// Fetches the bangumi profile of `anime_id` through the library server's
/// dandanplay proxy.
pub fn fetch_bangumi_detail(base_url: &str, anime_id: u64) -> Result<BangumiDetail, String> {
    if anime_id == 0 {
        return Err("bangumi detail requires a positive anime ID".to_owned());
    }
    let body = http_get(
        base_url,
        &format!("/api/providers/dandanplay/bangumi?animeId={anime_id}"),
    )?;
    let root: Value = serde_json::from_str(&body)
        .map_err(|error| format!("server returned invalid bangumi JSON: {error}"))?;
    parse_bangumi_detail(&root).ok_or_else(|| "server returned no bangumi profile".to_owned())
}

fn parse_bangumi_detail(value: &Value) -> Option<BangumiDetail> {
    let object = value.as_object()?;
    let anime_id = object.get("animeId")?.as_u64()?;
    let anime_title = object
        .get("animeTitle")
        .and_then(Value::as_str)
        .filter(|title| !title.trim().is_empty())?
        .to_owned();
    let string_field = |key: &str| {
        object
            .get(key)
            .and_then(Value::as_str)
            .map(str::trim)
            .filter(|text| !text.is_empty())
            .map(str::to_owned)
    };
    Some(BangumiDetail {
        anime_id,
        anime_title,
        type_description: string_field("typeDescription"),
        summary: string_field("summary"),
        rating: object.get("rating").and_then(Value::as_f64),
        is_on_air: object.get("isOnAir").and_then(Value::as_bool),
        tags: object
            .get("tags")
            .and_then(Value::as_array)
            .into_iter()
            .flatten()
            .filter_map(Value::as_str)
            .map(str::to_owned)
            .collect(),
        episodes: object
            .get("episodes")
            .and_then(Value::as_array)
            .into_iter()
            .flatten()
            .filter_map(|episode| {
                Some(BangumiEpisode {
                    episode_id: episode.get("episodeId")?.as_u64()?,
                    episode_title: episode
                        .get("episodeTitle")
                        .and_then(Value::as_str)
                        .unwrap_or_default()
                        .to_owned(),
                    air_date: episode
                        .get("airDate")
                        .and_then(Value::as_str)
                        .map(str::to_owned),
                })
            })
            .collect(),
        online_databases: object
            .get("onlineDatabases")
            .and_then(Value::as_array)
            .into_iter()
            .flatten()
            .filter_map(|database| {
                Some(BangumiOnlineDatabase {
                    name: database.get("name")?.as_str()?.to_owned(),
                    url: database.get("url")?.as_str()?.to_owned(),
                })
            })
            .collect(),
    })
}

/// Pins `media_id` to a specific dandanplay episode, replacing whatever
/// match (auto or manual) it currently has. Records the anime association
/// server-side and refreshes the cached comments for the new episode; call
/// `fetch_server_danmaku` afterward to load them into the active session.
pub fn select_dandanplay_match(
    base_url: &str,
    media_id: &str,
    selection: &DandanplaySelection,
) -> Result<(), String> {
    if media_id.trim().is_empty() {
        return Err("dandanplay match selection requires a non-blank media ID".to_owned());
    }
    let mut endpoint = format!(
        "/api/providers/dandanplay/resolve?mediaId={}&episodeId={}",
        percent_encode_path_segment(media_id.trim()),
        selection.episode_id,
    );
    if let Some(anime_id) = selection.anime_id {
        endpoint.push_str(&format!("&animeId={anime_id}"));
    }
    if let Some(anime_title) = selection.anime_title.as_deref().map(str::trim)
        && !anime_title.is_empty()
    {
        endpoint.push_str(&format!(
            "&animeTitle={}",
            percent_encode_path_segment(anime_title)
        ));
    }
    if let Some(episode_title) = selection.episode_title.as_deref().map(str::trim)
        && !episode_title.is_empty()
    {
        endpoint.push_str(&format!(
            "&episodeTitle={}",
            percent_encode_path_segment(episode_title)
        ));
    }
    http_get(base_url, &endpoint)?;
    Ok(())
}

pub fn parse_normalized_json(source: &str) -> Result<Vec<DanmakuComment>, String> {
    let root: Value = serde_json::from_str(source)
        .map_err(|error| format!("invalid normalized danmaku JSON: {error}"))?;
    let items = root
        .as_array()
        .or_else(|| root.get("events").and_then(Value::as_array))
        .or_else(|| root.get("comments").and_then(Value::as_array));
    Ok(items
        .map(|items| parse_comment_values(items))
        .unwrap_or_default())
}

pub fn parse_bilibili_xml(source: &str) -> Vec<DanmakuComment> {
    let mut comments = Vec::new();
    let mut cursor = 0;
    while let Some(relative_start) = source[cursor..].find("<d") {
        let start = cursor + relative_start;
        let after_name = source.as_bytes().get(start + 2).copied();
        if !after_name.is_some_and(|value| value.is_ascii_whitespace() || value == b'>') {
            cursor = start + 2;
            continue;
        }
        let Some(relative_open_end) = source[start..].find('>') else {
            break;
        };
        let open_end = start + relative_open_end;
        let Some(relative_close) = source[open_end + 1..].find("</d>") else {
            break;
        };
        let close = open_end + 1 + relative_close;
        let attributes = &source[start + 2..open_end];
        let text = decode_xml_text(&source[open_end + 1..close]);
        if let Some(parameter) = xml_attribute(attributes, "p")
            && let Some(comment) = parse_bilibili_parameter(
                &parameter,
                text.trim(),
                &format!("xml-{}", comments.len()),
            )
        {
            comments.push(comment);
        }
        cursor = close + 4;
    }
    comments
}

fn parse_comment_values(items: &[Value]) -> Vec<DanmakuComment> {
    items
        .iter()
        .enumerate()
        .filter_map(|(index, value)| parse_comment_value(value, index))
        .collect()
}

fn parse_comment_value(value: &Value, index: usize) -> Option<DanmakuComment> {
    let object = value.as_object()?;
    let timestamp_ms = object
        .get("timestampMs")
        .and_then(number_value)
        .or_else(|| object.get("timeMs").and_then(number_value))
        .or_else(|| {
            object
                .get("time")
                .and_then(number_f64)
                .filter(|value| *value >= 0.0)
                .map(|seconds| (seconds * 1000.0).round() as u64)
        })?;
    let text = object.get("text")?.as_str()?.trim();
    if text.is_empty() {
        return None;
    }
    let style = object
        .get("style")
        .and_then(Value::as_object)
        .unwrap_or(object);
    let id = object
        .get("id")
        .and_then(value_string)
        .filter(|value| !value.trim().is_empty())
        .unwrap_or_else(|| format!("json-{index}"));
    Some(DanmakuComment {
        id,
        timestamp_ms,
        text: text.to_owned(),
        style: DanmakuStyle {
            color_argb: style
                .get("colorArgb")
                .or_else(|| style.get("color"))
                .and_then(parse_color)
                .unwrap_or(0xffff_ffff),
            mode: style
                .get("mode")
                .and_then(Value::as_str)
                .and_then(parse_mode)
                .unwrap_or_default(),
            size: style
                .get("size")
                .and_then(Value::as_str)
                .and_then(parse_size)
                .unwrap_or_default(),
        },
    })
}

fn parse_bilibili_parameter(
    parameter: &str,
    text: &str,
    fallback_id: &str,
) -> Option<DanmakuComment> {
    if text.trim().is_empty() {
        return None;
    }
    let parts: Vec<_> = parameter.split(',').map(str::trim).collect();
    let seconds = parts.first()?.parse::<f64>().ok()?;
    if !seconds.is_finite() || seconds < 0.0 {
        return None;
    }
    let rgb = parts
        .get(3)
        .and_then(|value| value.parse::<u32>().ok())
        .unwrap_or(0x00ff_ffff)
        & 0x00ff_ffff;
    Some(DanmakuComment {
        id: parts
            .get(7)
            .filter(|value| !value.is_empty())
            .copied()
            .unwrap_or(fallback_id)
            .to_owned(),
        timestamp_ms: (seconds * 1000.0).round() as u64,
        text: text.trim().to_owned(),
        style: DanmakuStyle {
            color_argb: 0xff00_0000 | rgb,
            mode: match parts.get(1).copied().unwrap_or("1") {
                "4" => DanmakuMode::Bottom,
                "5" => DanmakuMode::Top,
                _ => DanmakuMode::Scrolling,
            },
            size: parts
                .get(2)
                .and_then(|value| value.parse::<f32>().ok())
                .map(|value| {
                    if value <= 18.0 {
                        DanmakuSize::Small
                    } else if value >= 36.0 {
                        DanmakuSize::Large
                    } else {
                        DanmakuSize::Normal
                    }
                })
                .unwrap_or_default(),
        },
    })
}

fn xml_attribute(attributes: &str, name: &str) -> Option<String> {
    for quote in ['"', '\''] {
        let needle = format!("{name}={quote}");
        if let Some(start) = attributes.find(&needle) {
            let value = &attributes[start + needle.len()..];
            let end = value.find(quote)?;
            return Some(decode_xml_text(&value[..end]));
        }
    }
    None
}

fn decode_xml_text(value: &str) -> String {
    value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}

fn parse_mode(value: &str) -> Option<DanmakuMode> {
    match value.trim().to_ascii_uppercase().as_str() {
        "SCROLLING" | "SCROLL" => Some(DanmakuMode::Scrolling),
        "TOP" => Some(DanmakuMode::Top),
        "BOTTOM" => Some(DanmakuMode::Bottom),
        _ => None,
    }
}

fn parse_size(value: &str) -> Option<DanmakuSize> {
    match value.trim().to_ascii_uppercase().as_str() {
        "SMALL" => Some(DanmakuSize::Small),
        "NORMAL" | "MEDIUM" => Some(DanmakuSize::Normal),
        "LARGE" => Some(DanmakuSize::Large),
        _ => None,
    }
}

fn parse_color(value: &Value) -> Option<u32> {
    if let Some(value) = value.as_u64() {
        return u32::try_from(value).ok();
    }
    let value = value.as_str()?.trim();
    if let Some(hex) = value.strip_prefix('#') {
        let rgb = u32::from_str_radix(hex, 16).ok()?;
        return Some(if hex.len() <= 6 {
            0xff00_0000 | rgb
        } else {
            rgb
        });
    }
    if let Some(hex) = value
        .strip_prefix("0x")
        .or_else(|| value.strip_prefix("0X"))
    {
        return u32::from_str_radix(hex, 16).ok();
    }
    value.parse::<u32>().ok()
}

fn number_value(value: &Value) -> Option<u64> {
    value.as_u64().or_else(|| {
        number_f64(value)
            .filter(|value| *value >= 0.0)
            .map(|value| value.round() as u64)
    })
}

fn number_f64(value: &Value) -> Option<f64> {
    value
        .as_f64()
        .or_else(|| value.as_str().and_then(|value| value.parse::<f64>().ok()))
        .filter(|value| value.is_finite())
}

fn value_string(value: &Value) -> Option<String> {
    value
        .as_str()
        .map(str::to_owned)
        .or_else(|| value.as_i64().map(|value| value.to_string()))
        .or_else(|| value.as_u64().map(|value| value.to_string()))
}

fn size_scale(size: DanmakuSize) -> f32 {
    match size {
        DanmakuSize::Small => 0.82,
        DanmakuSize::Normal => 1.0,
        DanmakuSize::Large => 1.24,
    }
}

pub fn estimate_text_width(text: &str, font_px: f32) -> f32 {
    text.chars()
        .map(|character| {
            if character.is_ascii() {
                font_px * 0.56
            } else {
                font_px * 0.96
            }
        })
        .sum::<f32>()
        .max(font_px)
}

fn stable_density_key(comment: &DanmakuComment) -> u64 {
    let mut hash = 0xcbf2_9ce4_8422_2325_u64;
    for byte in comment
        .id
        .bytes()
        .chain(comment.timestamp_ms.to_le_bytes())
        .chain(comment.text.bytes())
    {
        hash ^= byte as u64;
        hash = hash.wrapping_mul(0x0000_0100_0000_01b3);
    }
    hash
}

fn passes_density(key: u64, density: f32) -> bool {
    density >= 1.0 || (density > 0.0 && key % 10_000 < (density * 10_000.0).round() as u64)
}

fn floor_ms(value: f64) -> u64 {
    if !value.is_finite() || value <= 0.0 {
        0
    } else {
        value.floor().min(u64::MAX as f64) as u64
    }
}

fn earliest_lane(lanes: &[LaneState]) -> usize {
    lanes
        .iter()
        .enumerate()
        .min_by_key(|(_, lane)| lane.available_at_ms)
        .map(|(index, _)| index)
        .unwrap_or(0)
}

fn assign_static_lane(lanes: &mut [LaneState], timestamp_ms: u64, duration_ms: u64) -> usize {
    let lane = lanes
        .iter()
        .position(|lane| lane.available_at_ms <= timestamp_ms)
        .unwrap_or_else(|| earliest_lane(lanes));
    lanes[lane].available_at_ms = timestamp_ms.saturating_add(duration_ms);
    lane
}

fn opacity_for_age(age_ms: f64, duration_ms: f64) -> f32 {
    let fade_ms = 280.0_f64.min(duration_ms * 0.2);
    if fade_ms <= 0.0 {
        return 1.0;
    }
    let remaining = (duration_ms - age_ms).max(0.0);
    (age_ms / fade_ms).min(remaining / fade_ms).clamp(0.0, 1.0) as f32
}

fn finite_or(value: f32, fallback: f32) -> f32 {
    if value.is_finite() { value } else { fallback }
}

#[cfg(test)]
mod tests {
    use std::{
        io::{Read, Write},
        net::TcpListener,
        thread,
    };

    use super::*;
    use crate::net::parse_http_response;

    fn comment(id: &str, timestamp_ms: u64, text: &str) -> DanmakuComment {
        DanmakuComment {
            id: id.to_owned(),
            timestamp_ms,
            text: text.to_owned(),
            style: DanmakuStyle::default(),
        }
    }

    #[test]
    fn parses_bangumi_detail_profile() {
        let detail = parse_bangumi_detail(&serde_json::json!({
            "animeId": 999,
            "animeTitle": "Sagrada Reset",
            "typeDescription": "TV动画",
            "summary": "A town where half the residents have powers.",
            "rating": 7.7,
            "isOnAir": false,
            "tags": ["悬疑", "校园"],
            "episodes": [
                {"episodeId": 9990001, "episodeTitle": "第1话", "airDate": "2017-04-05T00:00:00"},
                {"episodeId": 9990002, "episodeTitle": "第2话"}
            ],
            "onlineDatabases": [
                {"name": "Bangumi.tv", "url": "https://bangumi.tv/subject/179949"}
            ]
        }))
        .expect("detail parses");

        assert_eq!(detail.anime_id, 999);
        assert_eq!(detail.anime_title, "Sagrada Reset");
        assert_eq!(detail.type_description.as_deref(), Some("TV动画"));
        assert_eq!(detail.rating, Some(7.7));
        assert_eq!(detail.is_on_air, Some(false));
        assert_eq!(detail.tags, vec!["悬疑", "校园"]);
        assert_eq!(detail.episodes.len(), 2);
        assert_eq!(
            detail.episodes[0].air_date.as_deref(),
            Some("2017-04-05T00:00:00")
        );
        assert_eq!(detail.episodes[1].air_date, None);
        assert_eq!(detail.online_databases[0].name, "Bangumi.tv");

        // A profile without the identifying fields is rejected.
        assert!(parse_bangumi_detail(&serde_json::json!({"animeTitle": "x"})).is_none());
    }

    #[test]
    fn parses_normalized_arrays_and_style_variants() {
        let comments = parse_normalized_json(
            r##"[
              {"id":"a","timestampMs":1500,"text":"scroll","style":{"colorArgb":4294901760,"mode":"SCROLLING","size":"LARGE"}},
              {"time":2.5,"text":"top","color":"#00ff00","mode":"top","size":"small"},
              {"timestampMs":5,"text":"   "}
            ]"##,
        )
        .expect("normalized comments");

        assert_eq!(comments.len(), 2);
        assert_eq!(comments[0].timestamp_ms, 1_500);
        assert_eq!(comments[0].style.color_argb, 0xffff_0000);
        assert_eq!(comments[0].style.size, DanmakuSize::Large);
        assert_eq!(comments[1].timestamp_ms, 2_500);
        assert_eq!(comments[1].style.mode, DanmakuMode::Top);
        assert_eq!(comments[1].style.color_argb, 0xff00_ff00);
    }

    #[test]
    fn parses_bilibili_xml_and_decodes_text() {
        let comments = parse_bilibili_xml(
            r#"<i><d p="1.25,5,36,16711680,0,0,0,source-id">hello &amp; top</d></i>"#,
        );

        assert_eq!(comments.len(), 1);
        assert_eq!(comments[0].id, "source-id");
        assert_eq!(comments[0].timestamp_ms, 1_250);
        assert_eq!(comments[0].text, "hello & top");
        assert_eq!(comments[0].style.mode, DanmakuMode::Top);
        assert_eq!(comments[0].style.size, DanmakuSize::Large);
        assert_eq!(comments[0].style.color_argb, 0xffff_0000);
    }

    #[test]
    fn scheduler_avoids_recent_lane_and_moves_at_subpixel_precision() {
        let track = DanmakuTrack::new(vec![
            comment("first", 0, "first long scrolling comment"),
            comment("second", 100, "second long scrolling comment"),
        ]);
        let layout = DanmakuLayout::default();
        let settings = DanmakuDisplaySettings::default();

        let first = layout.visible_comments(&track, 1_000.25, 800.0, 200.0, &settings);
        let second = layout.visible_comments(&track, 1_000.75, 800.0, 200.0, &settings);

        assert_eq!(first.len(), 2);
        assert_ne!(first[0].lane, first[1].lane);
        let delta = first[0].x - second[0].x;
        assert!(delta > 0.0 && delta < 1.0, "subpixel delta: {delta}");
    }

    #[test]
    fn settings_control_density_speed_and_lanes() {
        let comments = (0..200)
            .map(|index| comment(&format!("id-{index}"), index * 10, "dense"))
            .collect();
        let track = DanmakuTrack::new(comments);
        let layout = DanmakuLayout::default();
        let full = layout.visible_comments(
            &track,
            2_000.0,
            800.0,
            720.0,
            &DanmakuDisplaySettings::default(),
        );
        let reduced = layout.visible_comments(
            &track,
            2_000.0,
            800.0,
            720.0,
            &DanmakuDisplaySettings {
                density: 0.25,
                speed: 2.0,
                max_lanes: 2,
                ..DanmakuDisplaySettings::default()
            },
        );

        assert!(reduced.len() < full.len());
        assert!(reduced.iter().all(|comment| comment.lane < 2));
        assert!(reduced.iter().all(|comment| comment.duration_ms == 6_000.0));
    }

    #[test]
    fn dense_track_meets_spike_active_comment_parity() {
        let comments = (0_u64..3_000)
            .map(|index| {
                comment(
                    &format!("dense-{index}"),
                    index * 1_000 / 150,
                    "dense parity",
                )
            })
            .collect();
        let track = DanmakuTrack::new(comments);
        let visible = DanmakuLayout::default().visible_comments(
            &track,
            20_000.0,
            1_920.0,
            1_080.0,
            &DanmakuDisplaySettings::default(),
        );

        assert!(visible.len() >= 1_500, "active comments: {}", visible.len());
    }

    #[test]
    fn seek_window_drops_comments_from_the_old_position() {
        let track = DanmakuTrack::new(vec![
            comment("early", 1_000, "early"),
            comment("late", 50_000, "late"),
        ]);
        let layout = DanmakuLayout::default();
        let settings = DanmakuDisplaySettings::default();

        let before_seek = layout.visible_comments(&track, 5_000.0, 800.0, 400.0, &settings);
        let after_seek = layout.visible_comments(&track, 50_100.0, 800.0, 400.0, &settings);

        assert_eq!(before_seek.len(), 1);
        assert_eq!(before_seek[0].event.text, "early");
        assert_eq!(after_seek.len(), 1);
        assert_eq!(after_seek[0].event.text, "late");
    }

    #[test]
    fn ass_files_use_the_mpv_compatibility_path() {
        let path = std::env::temp_dir().join(format!(
            "danmaku-player-ass-{}-{}.ass",
            std::process::id(),
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .expect("clock")
                .as_nanos()
        ));
        std::fs::write(&path, "[Script Info]\n[Events]\n").expect("ASS fixture");

        let load = load_local_danmaku(&path).expect("ASS attachment");
        let _ = std::fs::remove_file(&path);

        assert_eq!(load.kind, DanmakuLoadKind::Ass);
        assert!(load.track.is_empty());
        assert!(load.ass_path.is_some());
    }

    #[test]
    fn server_fetch_uses_normalized_client_route() {
        let listener = TcpListener::bind("127.0.0.1:0").expect("listener");
        let address = listener.local_addr().expect("address");
        let server = thread::spawn(move || {
            let (mut stream, _) = listener.accept().expect("accept");
            let mut request = Vec::new();
            while !request.windows(4).any(|window| window == b"\r\n\r\n") {
                let mut chunk = [0_u8; 512];
                let count = stream.read(&mut chunk).expect("request");
                if count == 0 {
                    break;
                }
                request.extend_from_slice(&chunk[..count]);
            }
            let request = String::from_utf8_lossy(&request);
            assert!(
                request.starts_with("GET /api/danmaku/episode%20id?forceRefresh=true "),
                "{request}"
            );
            let body = r#"{"mediaId":"episode id","status":"READY","source":"CACHE","matchTitle":"Example Anime","comments":[{"id":"1","timestampMs":1000,"text":"hello","style":{"colorArgb":4294967295,"mode":"SCROLLING","size":"NORMAL"}}]}"#;
            write!(
                stream,
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                body.len(),
                body
            )
            .expect("response");
        });

        let load = fetch_server_danmaku(&format!("http://{address}"), "episode id", true)
            .expect("server danmaku");
        server.join().expect("server thread");

        assert_eq!(load.kind, DanmakuLoadKind::Server);
        assert_eq!(load.track.len(), 1);
        assert!(load.status.contains("CACHE"));
        assert_eq!(load.match_title.as_deref(), Some("Example Anime"));
    }

    #[test]
    fn repair_observes_failed_status_in_a_successful_http_response() {
        let listener = TcpListener::bind("127.0.0.1:0").expect("listener");
        let address = listener.local_addr().expect("address");
        let server = thread::spawn(move || {
            let (mut stream, _) = listener.accept().expect("accept");
            let mut request = Vec::new();
            while !request.windows(4).any(|window| window == b"\r\n\r\n") {
                let mut chunk = [0_u8; 512];
                let count = stream.read(&mut chunk).expect("request");
                if count == 0 {
                    break;
                }
                request.extend_from_slice(&chunk[..count]);
            }
            let body = r#"{"mediaId":"episode-id","status":"FAILED","message":"provider unavailable","comments":[]}"#;
            write!(
                stream,
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                body.len(),
                body
            )
            .expect("response");
        });

        let load = fetch_server_danmaku(&format!("http://{address}"), "episode-id", false)
            .expect("HTTP response should parse");
        server.join().expect("server thread");

        assert_eq!(load.server_status.as_deref(), Some("FAILED"));
        assert_eq!(
            load.require_server_ready().unwrap_err(),
            "provider unavailable"
        );
    }

    #[test]
    fn candidate_listing_always_forces_a_fresh_match() {
        let listener = TcpListener::bind("127.0.0.1:0").expect("listener");
        let address = listener.local_addr().expect("address");
        let server = thread::spawn(move || {
            let (mut stream, _) = listener.accept().expect("accept");
            let mut request = Vec::new();
            while !request.windows(4).any(|window| window == b"\r\n\r\n") {
                let mut chunk = [0_u8; 512];
                let count = stream.read(&mut chunk).expect("request");
                if count == 0 {
                    break;
                }
                request.extend_from_slice(&chunk[..count]);
            }
            let request = String::from_utf8_lossy(&request);
            assert!(
                request.starts_with(
                    "GET /api/providers/dandanplay/resolve?mediaId=episode%20id&forceRefresh=true&withRelated=false "
                ),
                "{request}"
            );
            let body = r#"{"mediaId":"episode id","matches":[{"episodeId":111,"animeTitle":"Example Anime","episodeTitle":"Episode 1","displayTitle":"Example Anime - Episode 1"},{"episodeId":222,"episodeTitle":"Episode 2","displayTitle":"222"}]}"#;
            write!(
                stream,
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                body.len(),
                body
            )
            .expect("response");
        });

        let candidates = fetch_dandanplay_candidates(&format!("http://{address}"), "episode id")
            .expect("candidates");
        server.join().expect("server thread");

        assert_eq!(candidates.len(), 2);
        assert_eq!(candidates[0].episode_id, 111);
        assert_eq!(candidates[0].anime_title.as_deref(), Some("Example Anime"));
        assert_eq!(candidates[0].display_title, "Example Anime - Episode 1");
        assert_eq!(candidates[1].episode_id, 222);
        assert_eq!(candidates[1].anime_title, None);
        assert_eq!(candidates[1].display_title, "222");
    }

    #[test]
    fn selecting_a_match_pins_the_given_episode_id() {
        let listener = TcpListener::bind("127.0.0.1:0").expect("listener");
        let address = listener.local_addr().expect("address");
        let server = thread::spawn(move || {
            let (mut stream, _) = listener.accept().expect("accept");
            let mut request = Vec::new();
            while !request.windows(4).any(|window| window == b"\r\n\r\n") {
                let mut chunk = [0_u8; 512];
                let count = stream.read(&mut chunk).expect("request");
                if count == 0 {
                    break;
                }
                request.extend_from_slice(&chunk[..count]);
            }
            let request = String::from_utf8_lossy(&request);
            assert!(
                request.starts_with(
                    "GET /api/providers/dandanplay/resolve?mediaId=episode%20id&episodeId=222&animeId=99&animeTitle=Example%20Anime&episodeTitle=Episode%202 "
                ),
                "{request}"
            );
            let body = r#"{"mediaId":"episode id","matches":[]}"#;
            write!(
                stream,
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                body.len(),
                body
            )
            .expect("response");
        });

        select_dandanplay_match(
            &format!("http://{address}"),
            "episode id",
            &DandanplaySelection {
                episode_id: 222,
                anime_id: Some(99),
                anime_title: Some("Example Anime".to_owned()),
                episode_title: Some("Episode 2".to_owned()),
            },
        )
        .expect("selection succeeds");
        server.join().expect("server thread");
    }

    #[test]
    fn dandanplay_search_parses_animes_and_episodes() {
        let listener = TcpListener::bind("127.0.0.1:0").expect("listener");
        let address = listener.local_addr().expect("address");
        let server = thread::spawn(move || {
            let (mut stream, _) = listener.accept().expect("accept");
            let mut request = Vec::new();
            while !request.windows(4).any(|window| window == b"\r\n\r\n") {
                let mut chunk = [0_u8; 512];
                let count = stream.read(&mut chunk).expect("request");
                if count == 0 {
                    break;
                }
                request.extend_from_slice(&chunk[..count]);
            }
            let request = String::from_utf8_lossy(&request);
            assert!(
                request
                    .starts_with("GET /api/providers/dandanplay/search?keyword=lycoris%20recoil "),
                "{request}"
            );
            let body = r#"{"animes":[{"animeId":17097,"animeTitle":"莉可丽丝","typeDescription":"TV动画","episodes":[{"episodeId":170970001,"episodeTitle":"第1话 慢慢来"},{"episodeId":170970002,"episodeTitle":"第2话"}]}]}"#;
            write!(
                stream,
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                body.len(),
                body
            )
            .expect("response");
        });

        let animes = search_dandanplay(&format!("http://{address}"), " lycoris recoil ")
            .expect("search succeeds");
        server.join().expect("server thread");

        assert_eq!(animes.len(), 1);
        assert_eq!(animes[0].anime_id, 17097);
        assert_eq!(animes[0].anime_title, "莉可丽丝");
        assert_eq!(animes[0].type_description.as_deref(), Some("TV动画"));
        assert_eq!(animes[0].episodes.len(), 2);
        assert_eq!(animes[0].episodes[0].episode_id, 170970001);
        assert_eq!(animes[0].episodes[0].episode_title, "第1话 慢慢来");
    }

    #[test]
    fn parses_chunked_http_body() {
        let response =
            b"HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n4\r\ntest\r\n0\r\n\r\n";
        let parsed = parse_http_response(response).expect("chunked");
        assert_eq!(parsed.status, 200);
        assert_eq!(parsed.body, b"test");
    }
}
