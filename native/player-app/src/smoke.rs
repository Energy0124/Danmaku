//! Unattended smoke reporting: proves the compositing path renders frames.

use std::{fmt, time::Duration};

use crate::video::RenderCounters;

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct SmokeReport {
    pub media: String,
    pub duration: Duration,
    pub rendered_frames: u64,
    pub render_failures: u64,
    pub hwdec_current: Option<String>,
    pub dropped_frames: Option<u64>,
    pub failure: Option<String>,
}

impl SmokeReport {
    pub fn from_counters(
        media: &str,
        duration: Duration,
        counters: &RenderCounters,
        hwdec_current: Option<String>,
        dropped_frames: Option<u64>,
    ) -> Self {
        // Expect at least a handful of frames per smoke second; a stalled
        // render path reliably reports zero.
        let minimum_frames = (duration.as_secs_f64() * 5.0).max(1.0) as u64;
        let failure = if counters.render_failures > 0 {
            Some(format!(
                "{} render failure(s) recorded",
                counters.render_failures
            ))
        } else if counters.rendered_frames < minimum_frames {
            Some(format!(
                "only {} frame(s) rendered in {:.1}s (expected at least {minimum_frames})",
                counters.rendered_frames,
                duration.as_secs_f64(),
            ))
        } else {
            None
        };
        Self {
            media: media.to_owned(),
            duration,
            rendered_frames: counters.rendered_frames,
            render_failures: counters.render_failures,
            hwdec_current,
            dropped_frames,
            failure,
        }
    }

    pub fn fail(media: &str, duration: Duration, message: impl Into<String>) -> Self {
        Self {
            media: media.to_owned(),
            duration,
            rendered_frames: 0,
            render_failures: 0,
            hwdec_current: None,
            dropped_frames: None,
            failure: Some(message.into()),
        }
    }

    pub fn passed(&self) -> bool {
        self.failure.is_none()
    }

    pub fn exit_code(&self) -> i32 {
        if self.passed() { 0 } else { 1 }
    }
}

impl fmt::Display for SmokeReport {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        writeln!(
            formatter,
            "danmaku-player smoke: {}",
            if self.passed() { "PASS" } else { "FAIL" }
        )?;
        writeln!(formatter, "  media: {}", self.media)?;
        writeln!(formatter, "  duration: {:.1}s", self.duration.as_secs_f64())?;
        writeln!(formatter, "  rendered frames: {}", self.rendered_frames)?;
        writeln!(formatter, "  render failures: {}", self.render_failures)?;
        writeln!(
            formatter,
            "  hwdec: {}",
            self.hwdec_current.as_deref().unwrap_or("unknown")
        )?;
        writeln!(
            formatter,
            "  dropped frames: {}",
            self.dropped_frames
                .map(|count| count.to_string())
                .unwrap_or_else(|| "unknown".to_owned())
        )?;
        if let Some(failure) = &self.failure {
            writeln!(formatter, "  failure: {failure}")?;
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::SmokeReport;
    use crate::video::RenderCounters;
    use std::time::Duration;

    #[test]
    fn passes_with_enough_frames_and_no_failures() {
        let report = SmokeReport::from_counters(
            "sample.mkv",
            Duration::from_secs(4),
            &RenderCounters {
                rendered_frames: 200,
                render_failures: 0,
            },
            Some("d3d11va".to_owned()),
            Some(0),
        );

        assert!(report.passed());
        assert_eq!(report.exit_code(), 0);
    }

    #[test]
    fn fails_when_no_frames_rendered() {
        let report = SmokeReport::from_counters(
            "sample.mkv",
            Duration::from_secs(4),
            &RenderCounters::default(),
            None,
            None,
        );

        assert!(!report.passed());
        assert_eq!(report.exit_code(), 1);
        assert!(report.to_string().contains("FAIL"));
    }

    #[test]
    fn fails_on_render_failures_even_with_frames() {
        let report = SmokeReport::from_counters(
            "sample.mkv",
            Duration::from_secs(2),
            &RenderCounters {
                rendered_frames: 500,
                render_failures: 3,
            },
            None,
            None,
        );

        assert!(!report.passed());
    }
}
