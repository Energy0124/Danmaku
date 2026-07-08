use std::time::{Duration, Instant};

pub const DEFAULT_DRIFT_CLAMP: Duration = Duration::from_millis(250);

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct ClockObservation {
    pub drift_s: f64,
    pub hard_resynced: bool,
}

#[derive(Clone, Debug)]
pub struct OverlayClock {
    base_position_s: f64,
    base_instant: Instant,
    rate: f64,
    paused: bool,
    drift_clamp_s: f64,
}

impl OverlayClock {
    pub fn new(now: Instant) -> Self {
        Self {
            base_position_s: 0.0,
            base_instant: now,
            rate: 1.0,
            paused: false,
            drift_clamp_s: DEFAULT_DRIFT_CLAMP.as_secs_f64(),
        }
    }

    pub fn position_at(&self, now: Instant) -> f64 {
        if self.paused {
            return self.base_position_s;
        }

        (self.base_position_s
            + now
                .saturating_duration_since(self.base_instant)
                .as_secs_f64()
                * self.rate)
            .max(0.0)
    }

    pub fn paused(&self) -> bool {
        self.paused
    }

    pub fn observe_time_pos(
        &mut self,
        observed_position_s: f64,
        rate: f64,
        paused: bool,
        now: Instant,
    ) -> ClockObservation {
        let observed_position_s = sanitize_position(observed_position_s);
        let rate = sanitize_rate(rate);
        let predicted_position_s = self.position_at(now);
        let drift_s = observed_position_s - predicted_position_s;
        let state_changed = self.paused != paused || !approximately_equal(self.rate, rate);
        let hard_resynced = paused || state_changed || drift_s.abs() > self.drift_clamp_s;

        self.base_position_s = if hard_resynced {
            observed_position_s
        } else {
            predicted_position_s
        };
        self.base_instant = now;
        self.rate = rate;
        self.paused = paused;

        ClockObservation {
            drift_s,
            hard_resynced,
        }
    }

    pub fn seek(&mut self, position_s: f64, now: Instant) {
        self.base_position_s = sanitize_position(position_s);
        self.base_instant = now;
    }

    pub fn set_paused(&mut self, paused: bool, now: Instant) {
        let position_s = self.position_at(now);
        self.base_position_s = position_s;
        self.base_instant = now;
        self.paused = paused;
    }

    pub fn set_rate(&mut self, rate: f64, now: Instant) {
        let position_s = self.position_at(now);
        self.base_position_s = position_s;
        self.base_instant = now;
        self.rate = sanitize_rate(rate);
    }
}

fn sanitize_position(position_s: f64) -> f64 {
    if position_s.is_finite() {
        position_s.max(0.0)
    } else {
        0.0
    }
}

fn sanitize_rate(rate: f64) -> f64 {
    if rate.is_finite() && rate > 0.0 {
        rate
    } else {
        1.0
    }
}

fn approximately_equal(left: f64, right: f64) -> bool {
    (left - right).abs() <= f64::EPSILON * left.abs().max(right.abs()).max(1.0)
}

#[cfg(test)]
mod tests {
    use super::OverlayClock;
    use std::time::{Duration, Instant};

    fn assert_close(actual: f64, expected: f64) {
        assert!(
            (actual - expected).abs() < 0.000_001,
            "actual {actual}, expected {expected}"
        );
    }

    #[test]
    fn advances_while_playing() {
        let start = Instant::now();
        let clock = OverlayClock::new(start);

        assert_close(clock.position_at(start + Duration::from_millis(750)), 0.75);
    }

    #[test]
    fn pause_freezes_position_exactly() {
        let start = Instant::now();
        let mut clock = OverlayClock::new(start);
        let pause_at = start + Duration::from_millis(250);

        clock.set_paused(true, pause_at);

        assert_close(clock.position_at(pause_at + Duration::from_secs(5)), 0.25);
    }

    #[test]
    fn rate_change_preserves_position_then_advances_at_new_rate() {
        let start = Instant::now();
        let mut clock = OverlayClock::new(start);
        let changed_at = start + Duration::from_secs(1);

        clock.set_rate(1.5, changed_at);

        assert_close(clock.position_at(changed_at), 1.0);
        assert_close(clock.position_at(changed_at + Duration::from_secs(2)), 4.0);
    }

    #[test]
    fn seek_resyncs_base_position() {
        let start = Instant::now();
        let mut clock = OverlayClock::new(start);
        let seek_at = start + Duration::from_secs(10);

        clock.seek(42.5, seek_at);

        assert_close(clock.position_at(seek_at), 42.5);
        assert_close(
            clock.position_at(seek_at + Duration::from_millis(500)),
            43.0,
        );
    }

    #[test]
    fn small_observed_drift_does_not_snap_overlay_position() {
        let start = Instant::now();
        let mut clock = OverlayClock::new(start);
        let observed_at = start + Duration::from_secs(1);

        let observation = clock.observe_time_pos(0.98, 1.0, false, observed_at);

        assert!(!observation.hard_resynced);
        assert_close(clock.position_at(observed_at), 1.0);
    }

    #[test]
    fn drift_clamp_hard_resyncs_when_observed_time_is_far_away() {
        let start = Instant::now();
        let mut clock = OverlayClock::new(start);
        let observed_at = start + Duration::from_secs(1);

        let observation = clock.observe_time_pos(0.2, 1.0, false, observed_at);

        assert!(observation.hard_resynced);
        assert_close(clock.position_at(observed_at), 0.2);
    }
}
