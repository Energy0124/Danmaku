/// A normalized danmaku event.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct DanmakuEvent {
    pub id: u64,
    pub timestamp_ms: u64,
    pub text: String,
}

/// A time-ordered danmaku event index.
///
/// The timeline owns normalized events and answers coarse-grained time-window
/// queries. Rendering, filtering, and lane allocation remain in the caller.
#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub struct Timeline {
    events: Vec<DanmakuEvent>,
}

impl Timeline {
    /// Builds a stable time-ordered timeline.
    ///
    /// Events with identical timestamps retain their original order.
    pub fn new(mut events: Vec<DanmakuEvent>) -> Self {
        events.sort_by_key(|event| event.timestamp_ms);
        Self { events }
    }

    pub fn len(&self) -> usize {
        self.events.len()
    }

    pub fn is_empty(&self) -> bool {
        self.events.is_empty()
    }

    pub fn events(&self) -> &[DanmakuEvent] {
        &self.events
    }

    /// Returns events in the half-open interval `[start_ms, end_ms)`.
    ///
    /// Half-open windows can be queried back-to-back without returning an
    /// event twice. An empty or reversed window returns no events.
    pub fn events_in_window(&self, start_ms: u64, end_ms: u64) -> &[DanmakuEvent] {
        if start_ms >= end_ms {
            return &[];
        }

        let start = self
            .events
            .partition_point(|event| event.timestamp_ms < start_ms);
        let end = self
            .events
            .partition_point(|event| event.timestamp_ms < end_ms);

        &self.events[start..end]
    }
}

#[cfg(test)]
mod tests {
    use super::{DanmakuEvent, Timeline};

    fn event(id: u64, timestamp_ms: u64) -> DanmakuEvent {
        DanmakuEvent {
            id,
            timestamp_ms,
            text: format!("event-{id}"),
        }
    }

    #[test]
    fn sorts_events_by_timestamp_and_preserves_equal_timestamp_order() {
        let timeline = Timeline::new(vec![event(1, 300), event(2, 100), event(3, 100)]);

        let ids: Vec<_> = timeline.events().iter().map(|event| event.id).collect();

        assert_eq!(ids, vec![2, 3, 1]);
    }

    #[test]
    fn queries_a_half_open_window() {
        let timeline = Timeline::new(vec![
            event(1, 99),
            event(2, 100),
            event(3, 150),
            event(4, 199),
            event(5, 200),
        ]);

        let ids: Vec<_> = timeline
            .events_in_window(100, 200)
            .iter()
            .map(|event| event.id)
            .collect();

        assert_eq!(ids, vec![2, 3, 4]);
    }

    #[test]
    fn supports_back_to_back_windows_without_duplicates() {
        let timeline = Timeline::new(vec![event(1, 99), event(2, 100), event(3, 101)]);

        let first: Vec<_> = timeline
            .events_in_window(99, 100)
            .iter()
            .map(|event| event.id)
            .collect();
        let second: Vec<_> = timeline
            .events_in_window(100, 102)
            .iter()
            .map(|event| event.id)
            .collect();

        assert_eq!(first, vec![1]);
        assert_eq!(second, vec![2, 3]);
    }

    #[test]
    fn returns_no_events_for_empty_or_reversed_windows() {
        let timeline = Timeline::new(vec![event(1, 100)]);

        assert!(timeline.events_in_window(100, 100).is_empty());
        assert!(timeline.events_in_window(200, 100).is_empty());
    }
}
