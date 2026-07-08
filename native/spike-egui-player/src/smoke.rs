use std::{fmt, time::Duration};

#[derive(Clone, Debug, Default)]
pub struct SmokeStats {
    pub rendered_frames: u64,
    pub render_failures: u64,
    pub dropped_frames: Option<u64>,
    pub ui_frame_samples: u64,
    pub total_ui_frame_ms: f64,
    pub active_danmaku_peak: usize,
    pub hwdec_current: Option<String>,
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

    pub fn average_ui_frame_ms(&self) -> Option<f64> {
        (self.ui_frame_samples > 0).then_some(self.total_ui_frame_ms / self.ui_frame_samples as f64)
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
    use super::{SmokeReport, SmokeStats, SmokeVerdict};
    use std::time::Duration;

    #[test]
    fn report_passes_when_smoke_thresholds_are_met() {
        let mut stats = SmokeStats::default();
        stats.rendered_frames = 120;
        stats.active_danmaku_peak = 1_700;
        stats.record_ui_frame(Duration::from_millis(12));
        stats.record_ui_frame(Duration::from_millis(14));

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
        assert_eq!(report.exit_code(), 1);
    }
}
