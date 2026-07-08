use danmaku_core::{DanmakuEvent, Timeline};

pub const TRACK_DURATION_MS: u64 = 60 * 60 * 1000;
pub const SYNTHETIC_COMMENTS_PER_SECOND: u64 = 150;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum DanmakuMode {
    Scroll,
    Top,
    Bottom,
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
            font_px: 19.0,
            lane_gap_px: 4.0,
            horizontal_padding_px: 32.0,
        }
    }
}

#[derive(Clone, Debug)]
pub struct ScheduledComment<'a> {
    pub event: &'a DanmakuEvent,
    pub mode: DanmakuMode,
    pub lane: usize,
    pub x: f32,
    pub y: f32,
    pub opacity: f32,
}

#[derive(Clone, Debug)]
struct LaneState {
    available_at_ms: u64,
}

impl DanmakuLayout {
    pub fn visible_comments<'a>(
        &self,
        timeline: &'a Timeline,
        playback_ms: u64,
        video_width: f32,
        video_height: f32,
    ) -> Vec<ScheduledComment<'a>> {
        if video_width <= 1.0 || video_height <= 1.0 {
            return Vec::new();
        }

        let query_start = playback_ms.saturating_sub(self.scroll_duration_ms);
        let query_end = playback_ms.saturating_add(1);
        let candidates = timeline.events_in_window(query_start, query_end);
        let mut visible = Vec::with_capacity(candidates.len());
        let line_height = self.line_height();
        let scroll_lanes = ((video_height * 0.74) / line_height).floor().max(1.0) as usize;
        let static_lanes = ((video_height * 0.13) / line_height).floor().max(1.0) as usize;

        let mut scroll_state = vec![LaneState { available_at_ms: 0 }; scroll_lanes];
        let mut top_state = vec![LaneState { available_at_ms: 0 }; static_lanes];
        let mut bottom_state = vec![LaneState { available_at_ms: 0 }; static_lanes];

        for event in candidates {
            let mode = mode_for_event(event.id);
            let duration_ms = match mode {
                DanmakuMode::Scroll => self.scroll_duration_ms,
                DanmakuMode::Top | DanmakuMode::Bottom => self.static_duration_ms,
            };
            let age_ms = playback_ms.saturating_sub(event.timestamp_ms);
            if age_ms > duration_ms {
                continue;
            }

            let text_width = estimate_text_width(&event.text, self.font_px);
            let (lane, x, y) = match mode {
                DanmakuMode::Scroll => {
                    let lane = self.assign_scroll_lane(
                        &mut scroll_state,
                        event.timestamp_ms,
                        text_width,
                        video_width,
                    );
                    let progress = age_ms as f32 / self.scroll_duration_ms as f32;
                    let x = video_width - progress * (video_width + text_width);
                    let y = lane as f32 * line_height + self.lane_gap_px;
                    (lane, x, y)
                }
                DanmakuMode::Top => {
                    let lane = assign_static_lane(&mut top_state, event.timestamp_ms, duration_ms);
                    let x = (video_width - text_width).max(0.0) * 0.5;
                    let y = lane as f32 * line_height + self.lane_gap_px;
                    (lane, x, y)
                }
                DanmakuMode::Bottom => {
                    let lane =
                        assign_static_lane(&mut bottom_state, event.timestamp_ms, duration_ms);
                    let x = (video_width - text_width).max(0.0) * 0.5;
                    let y = video_height - ((lane + 1) as f32 * line_height) - self.lane_gap_px;
                    (lane, x, y)
                }
            };

            visible.push(ScheduledComment {
                event,
                mode,
                lane,
                x,
                y,
                opacity: opacity_for_age(age_ms, duration_ms),
            });
        }

        visible
    }

    fn line_height(&self) -> f32 {
        self.font_px + self.lane_gap_px
    }

    fn assign_scroll_lane(
        &self,
        lanes: &mut [LaneState],
        timestamp_ms: u64,
        text_width: f32,
        video_width: f32,
    ) -> usize {
        let lane = lanes
            .iter()
            .position(|lane| lane.available_at_ms <= timestamp_ms)
            .unwrap_or_else(|| {
                lanes
                    .iter()
                    .enumerate()
                    .min_by_key(|(_, lane)| lane.available_at_ms)
                    .map(|(index, _)| index)
                    .unwrap_or(0)
            });

        let pixels_per_ms = (video_width + text_width).max(1.0) / self.scroll_duration_ms as f32;
        let release_ms = ((text_width + self.horizontal_padding_px) / pixels_per_ms).ceil() as u64;
        lanes[lane].available_at_ms = timestamp_ms.saturating_add(release_ms.max(1));
        lane
    }
}

fn assign_static_lane(lanes: &mut [LaneState], timestamp_ms: u64, duration_ms: u64) -> usize {
    let lane = lanes
        .iter()
        .position(|lane| lane.available_at_ms <= timestamp_ms)
        .unwrap_or_else(|| {
            lanes
                .iter()
                .enumerate()
                .min_by_key(|(_, lane)| lane.available_at_ms)
                .map(|(index, _)| index)
                .unwrap_or(0)
        });
    lanes[lane].available_at_ms = timestamp_ms.saturating_add(duration_ms);
    lane
}

pub fn mode_for_event(id: u64) -> DanmakuMode {
    if id.is_multiple_of(41) {
        DanmakuMode::Top
    } else if id.is_multiple_of(47) {
        DanmakuMode::Bottom
    } else {
        DanmakuMode::Scroll
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

fn opacity_for_age(age_ms: u64, duration_ms: u64) -> f32 {
    let fade_ms = 280.0_f32.min(duration_ms as f32 * 0.2);
    if fade_ms <= 0.0 {
        return 1.0;
    }
    let age = age_ms as f32;
    let remaining = duration_ms.saturating_sub(age_ms) as f32;
    (age / fade_ms).min(remaining / fade_ms).clamp(0.0, 1.0)
}

pub fn synthetic_timeline() -> Timeline {
    let total_events = TRACK_DURATION_MS / 1000 * SYNTHETIC_COMMENTS_PER_SECOND;
    let mut events = Vec::with_capacity(total_events as usize);
    let mut id = 1_u64;
    for second in 0..(TRACK_DURATION_MS / 1000) {
        for slot in 0..SYNTHETIC_COMMENTS_PER_SECOND {
            let timestamp_ms = second * 1000 + slot * 1000 / SYNTHETIC_COMMENTS_PER_SECOND;
            let phrase = match id % 8 {
                0 => "畫面好順",
                1 => "彈幕壓力測試",
                2 => "繁體中文測試",
                3 => "egui overlay",
                4 => "libmpv render API",
                5 => "字幕軌同步",
                6 => "seek pause rate",
                _ => "Windows player spike",
            };
            events.push(DanmakuEvent {
                id,
                timestamp_ms,
                text: format!("{phrase} #{id:06}"),
            });
            id += 1;
        }
    }
    Timeline::new(events)
}

#[cfg(test)]
mod tests {
    use super::{DanmakuLayout, DanmakuMode, estimate_text_width, synthetic_timeline};
    use danmaku_core::{DanmakuEvent, Timeline};

    fn event(id: u64, timestamp_ms: u64, text: &str) -> DanmakuEvent {
        DanmakuEvent {
            id,
            timestamp_ms,
            text: text.to_owned(),
        }
    }

    #[test]
    fn scroll_lane_scheduler_uses_another_lane_when_previous_comment_is_too_close() {
        let timeline = Timeline::new(vec![
            event(1, 0, "first long scrolling comment"),
            event(2, 100, "second long scrolling comment"),
        ]);
        let layout = DanmakuLayout {
            scroll_duration_ms: 10_000,
            static_duration_ms: 5_000,
            font_px: 20.0,
            lane_gap_px: 4.0,
            horizontal_padding_px: 24.0,
        };

        let visible = layout.visible_comments(&timeline, 101, 800.0, 80.0);

        assert_eq!(visible.len(), 2);
        assert_eq!(visible[0].mode, DanmakuMode::Scroll);
        assert_eq!(visible[1].mode, DanmakuMode::Scroll);
        assert_ne!(visible[0].lane, visible[1].lane);
    }

    #[test]
    fn synthetic_track_reaches_required_active_density() {
        let timeline = synthetic_timeline();
        let layout = DanmakuLayout::default();

        let visible = layout.visible_comments(&timeline, 20_000, 1920.0, 1080.0);

        assert!(visible.len() >= 1_500, "active comments: {}", visible.len());
    }

    #[test]
    fn cjk_width_estimate_is_wider_than_ascii_for_same_character_count() {
        let ascii = estimate_text_width("abcdef", 20.0);
        let cjk = estimate_text_width("繁體中文測試", 20.0);

        assert!(cjk > ascii);
    }
}
