use std::{fmt, time::Duration};

pub const DANMAKU_VELOCITY_JITTER_THRESHOLD: f64 = 0.05;

const MAX_TRACKED_DANMAKU: usize = 8;
const MAX_COMPLETED_JITTERS: usize = 64;
const MIN_VELOCITY_SAMPLES: u64 = 24;
const MIN_FRAME_DT_S: f64 = 0.001;
const MAX_FRAME_DT_S: f64 = 0.250;

#[derive(Clone, Debug, Default)]
pub struct SmokeStats {
    pub rendered_frames: u64,
    pub render_failures: u64,
    pub dropped_frames: Option<u64>,
    pub ui_frame_samples: u64,
    pub total_ui_frame_ms: f64,
    pub active_danmaku_peak: usize,
    pub hwdec_current: Option<String>,
    velocity_sampler: DanmakuVelocitySampler,
}

#[derive(Clone, Copy, Debug)]
pub struct DanmakuVelocityObservation {
    pub id: u64,
    pub x: f32,
}

impl SmokeStats {
    pub fn record_rendered_frame(&mut self) {
        self.rendered_frames += 1;
    }

    pub fn record_render_failure(&mut self) {
        self.render_failures += 1;
    }

    pub fn record_ui_frame(&mut self, duration: Duration) {
        self.ui_frame_samples += 1;
        self.total_ui_frame_ms += duration.as_secs_f64() * 1000.0;
    }

    pub fn record_active_danmaku(&mut self, active_count: usize) {
        self.active_danmaku_peak = self.active_danmaku_peak.max(active_count);
    }

    pub fn record_danmaku_motion(
        &mut self,
        frame_duration: Duration,
        observations: &[DanmakuVelocityObservation],
    ) {
        self.velocity_sampler
            .record_frame(frame_duration, observations);
    }

    pub fn average_ui_frame_ms(&self) -> Option<f64> {
        (self.ui_frame_samples > 0).then_some(self.total_ui_frame_ms / self.ui_frame_samples as f64)
    }

    pub fn danmaku_velocity_jitter(&self) -> Option<f64> {
        self.velocity_sampler.jitter()
    }
}

#[derive(Clone, Debug, Default)]
struct DanmakuVelocitySampler {
    tracked: Vec<TrackedDanmakuVelocity>,
    completed_jitters: Vec<f64>,
}

impl DanmakuVelocitySampler {
    fn record_frame(
        &mut self,
        frame_duration: Duration,
        observations: &[DanmakuVelocityObservation],
    ) {
        let dt_s = frame_duration.as_secs_f64();
        if !(MIN_FRAME_DT_S..=MAX_FRAME_DT_S).contains(&dt_s) {
            return;
        }

        let mut index = 0;
        while index < self.tracked.len() {
            let event_id = self.tracked[index].event_id;
            if let Some(observation) = observations.iter().find(|sample| sample.id == event_id) {
                self.tracked[index].record(observation.x, dt_s);
                index += 1;
            } else {
                let tracked = self.tracked.swap_remove(index);
                self.finish_tracking(tracked);
            }
        }

        for observation in observations {
            if self.tracked.len() >= MAX_TRACKED_DANMAKU {
                break;
            }
            if !observation.x.is_finite()
                || self
                    .tracked
                    .iter()
                    .any(|tracked| tracked.event_id == observation.id)
            {
                continue;
            }
            self.tracked
                .push(TrackedDanmakuVelocity::new(observation.id, observation.x));
        }
    }

    fn finish_tracking(&mut self, tracked: TrackedDanmakuVelocity) {
        if self.completed_jitters.len() >= MAX_COMPLETED_JITTERS {
            return;
        }
        if let Some(jitter) = tracked.jitter() {
            self.completed_jitters.push(jitter);
        }
    }

    fn jitter(&self) -> Option<f64> {
        let mut count = 0_usize;
        let mut total = 0.0;

        for jitter in &self.completed_jitters {
            total += jitter;
            count += 1;
        }
        for tracked in &self.tracked {
            if let Some(jitter) = tracked.jitter() {
                total += jitter;
                count += 1;
            }
        }

        (count > 0).then_some(total / count as f64)
    }
}

#[derive(Clone, Debug)]
struct TrackedDanmakuVelocity {
    event_id: u64,
    last_x: f32,
    velocity_stats: VelocityStats,
}

impl TrackedDanmakuVelocity {
    fn new(event_id: u64, x: f32) -> Self {
        Self {
            event_id,
            last_x: x,
            velocity_stats: VelocityStats::default(),
        }
    }

    fn record(&mut self, x: f32, dt_s: f64) {
        if !x.is_finite() {
            return;
        }

        let velocity = (x as f64 - self.last_x as f64) / dt_s;
        self.last_x = x;
        if velocity.is_finite() {
            self.velocity_stats.record(velocity);
        }
    }

    fn jitter(&self) -> Option<f64> {
        self.velocity_stats.coefficient_of_variation()
    }
}

#[derive(Clone, Debug, Default)]
struct VelocityStats {
    count: u64,
    mean: f64,
    m2: f64,
}

impl VelocityStats {
    fn record(&mut self, value: f64) {
        self.count += 1;
        let delta = value - self.mean;
        self.mean += delta / self.count as f64;
        let delta_after = value - self.mean;
        self.m2 += delta * delta_after;
    }

    fn coefficient_of_variation(&self) -> Option<f64> {
        if self.count < MIN_VELOCITY_SAMPLES {
            return None;
        }
        let mean = self.mean.abs();
        if mean <= f64::EPSILON {
            return None;
        }
        let variance = self.m2 / (self.count - 1) as f64;
        Some(variance.max(0.0).sqrt() / mean)
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum SmokeVerdict {
    Pass,
    Fail,
}

#[derive(Clone, Debug)]
pub struct SmokeReport {
    pub verdict: SmokeVerdict,
    pub media: String,
    pub requested_seconds: f64,
    pub rendered_frames: u64,
    pub render_failures: u64,
    pub dropped_frames: Option<u64>,
    pub average_ui_frame_ms: Option<f64>,
    pub active_danmaku_peak: usize,
    pub danmaku_velocity_jitter: Option<f64>,
    pub hwdec_current: Option<String>,
    pub reasons: Vec<String>,
}

impl SmokeReport {
    pub fn from_stats(media: &str, requested_duration: Duration, stats: &SmokeStats) -> Self {
        let mut reasons = Vec::new();
        if stats.rendered_frames == 0 {
            reasons.push("no mpv frames were rendered into the app FBO".to_owned());
        }
        if stats.render_failures > 0 {
            reasons.push(format!(
                "{} render callback failures",
                stats.render_failures
            ));
        }
        if stats.active_danmaku_peak < 1_500 {
            reasons.push(format!(
                "active danmaku peak was {}, expected at least 1500",
                stats.active_danmaku_peak
            ));
        }
        let average_ui_frame_ms = stats.average_ui_frame_ms();
        match average_ui_frame_ms {
            Some(ms) if ms <= 50.0 => {}
            Some(ms) => reasons.push(format!("average UI frame time was {ms:.2} ms")),
            None => reasons.push("no UI frame samples were recorded".to_owned()),
        }
        let danmaku_velocity_jitter = stats.danmaku_velocity_jitter();
        match danmaku_velocity_jitter {
            Some(jitter) if jitter <= DANMAKU_VELOCITY_JITTER_THRESHOLD => {}
            Some(jitter) => reasons.push(format!(
                "danmaku_velocity_jitter was {jitter:.4}, expected <= {DANMAKU_VELOCITY_JITTER_THRESHOLD:.2}"
            )),
            None => reasons.push(
                "danmaku_velocity_jitter unavailable; expected scrolling comment samples"
                    .to_owned(),
            ),
        }

        let verdict = if reasons.is_empty() {
            SmokeVerdict::Pass
        } else {
            SmokeVerdict::Fail
        };

        Self {
            verdict,
            media: media.to_owned(),
            requested_seconds: requested_duration.as_secs_f64(),
            rendered_frames: stats.rendered_frames,
            render_failures: stats.render_failures,
            dropped_frames: stats.dropped_frames,
            average_ui_frame_ms,
            active_danmaku_peak: stats.active_danmaku_peak,
            danmaku_velocity_jitter,
            hwdec_current: stats.hwdec_current.clone(),
            reasons,
        }
    }

    pub fn fail(media: &str, requested_duration: Duration, reason: impl Into<String>) -> Self {
        Self {
            verdict: SmokeVerdict::Fail,
            media: media.to_owned(),
            requested_seconds: requested_duration.as_secs_f64(),
            rendered_frames: 0,
            render_failures: 0,
            dropped_frames: None,
            average_ui_frame_ms: None,
            active_danmaku_peak: 0,
            danmaku_velocity_jitter: None,
            hwdec_current: None,
            reasons: vec![reason.into()],
        }
    }

    pub fn exit_code(&self) -> i32 {
        match self.verdict {
            SmokeVerdict::Pass => 0,
            SmokeVerdict::Fail => 1,
        }
    }
}

impl fmt::Display for SmokeReport {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        let verdict = match self.verdict {
            SmokeVerdict::Pass => "PASS",
            SmokeVerdict::Fail => "FAIL",
        };
        writeln!(formatter, "Danmaku egui/mpv smoke: {verdict}")?;
        writeln!(formatter, "media: {}", self.media)?;
        writeln!(
            formatter,
            "requested_seconds: {:.2}",
            self.requested_seconds
        )?;
        writeln!(formatter, "rendered_frames: {}", self.rendered_frames)?;
        writeln!(formatter, "render_failures: {}", self.render_failures)?;
        writeln!(
            formatter,
            "dropped_frames: {}",
            self.dropped_frames
                .map(|value| value.to_string())
                .unwrap_or_else(|| "unavailable".to_owned())
        )?;
        writeln!(
            formatter,
            "average_ui_frame_ms: {}",
            self.average_ui_frame_ms
                .map(|value| format!("{value:.2}"))
                .unwrap_or_else(|| "unavailable".to_owned())
        )?;
        writeln!(
            formatter,
            "active_danmaku_peak: {}",
            self.active_danmaku_peak
        )?;
        writeln!(
            formatter,
            "danmaku_velocity_jitter: {}",
            self.danmaku_velocity_jitter
                .map(|value| format!("{value:.4}"))
                .unwrap_or_else(|| "unavailable".to_owned())
        )?;
        writeln!(
            formatter,
            "hwdec_current: {}",
            self.hwdec_current
                .as_deref()
                .filter(|value| !value.is_empty())
                .unwrap_or("unavailable")
        )?;
        if !self.reasons.is_empty() {
            writeln!(formatter, "reasons:")?;
            for reason in &self.reasons {
                writeln!(formatter, "- {reason}")?;
            }
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::{
        DANMAKU_VELOCITY_JITTER_THRESHOLD, DanmakuVelocityObservation, SmokeReport, SmokeStats,
        SmokeVerdict,
    };
    use std::time::Duration;

    fn record_smooth_danmaku_motion(stats: &mut SmokeStats) {
        for frame in 0..40 {
            let x = 900.0 - frame as f32 * 2.0;
            stats.record_danmaku_motion(
                Duration::from_millis(16),
                &[
                    DanmakuVelocityObservation { id: 1, x },
                    DanmakuVelocityObservation { id: 2, x: x + 80.0 },
                    DanmakuVelocityObservation {
                        id: 3,
                        x: x + 160.0,
                    },
                ],
            );
        }
    }

    #[test]
    fn report_passes_when_smoke_thresholds_are_met() {
        let mut stats = SmokeStats::default();
        stats.rendered_frames = 120;
        stats.active_danmaku_peak = 1_700;
        stats.record_ui_frame(Duration::from_millis(12));
        stats.record_ui_frame(Duration::from_millis(14));
        record_smooth_danmaku_motion(&mut stats);

        let report = SmokeReport::from_stats("media", Duration::from_secs(2), &stats);

        assert_eq!(report.verdict, SmokeVerdict::Pass);
        assert!(report.reasons.is_empty());
        assert_eq!(report.exit_code(), 0);
    }

    #[test]
    fn report_fails_when_required_stats_are_missing() {
        let stats = SmokeStats::default();

        let report = SmokeReport::from_stats("media", Duration::from_secs(2), &stats);

        assert_eq!(report.verdict, SmokeVerdict::Fail);
        assert!(
            report
                .reasons
                .iter()
                .any(|reason| reason.contains("no mpv"))
        );
        assert!(
            report
                .reasons
                .iter()
                .any(|reason| reason.contains("expected at least 1500"))
        );
        assert!(
            report
                .reasons
                .iter()
                .any(|reason| reason.contains("danmaku_velocity_jitter unavailable"))
        );
        assert_eq!(report.exit_code(), 1);
    }

    #[test]
    fn report_fails_when_danmaku_velocity_jitter_is_too_high() {
        let mut stats = SmokeStats::default();
        stats.rendered_frames = 120;
        stats.active_danmaku_peak = 1_700;
        stats.record_ui_frame(Duration::from_millis(12));
        stats.record_ui_frame(Duration::from_millis(14));

        let mut x = 900.0_f32;
        for frame in 0..60 {
            x -= if frame % 2 == 0 { 1.0 } else { 4.0 };
            stats.record_danmaku_motion(
                Duration::from_millis(16),
                &[DanmakuVelocityObservation { id: 1, x }],
            );
        }

        let jitter = stats
            .danmaku_velocity_jitter()
            .expect("jitter should be available");
        assert!(jitter > DANMAKU_VELOCITY_JITTER_THRESHOLD);

        let report = SmokeReport::from_stats("media", Duration::from_secs(2), &stats);

        assert_eq!(report.verdict, SmokeVerdict::Fail);
        assert!(
            report
                .reasons
                .iter()
                .any(|reason| reason.contains("danmaku_velocity_jitter"))
        );
    }
}
