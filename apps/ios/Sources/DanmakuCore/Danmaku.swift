import Foundation

public struct DanmakuStyle: Codable, Sendable {
    public var colorArgb: UInt32?
    public var mode: String?
    public var size: String?
    public init(colorArgb: UInt32 = .max, mode: String = "SCROLLING", size: String = "NORMAL") {
        self.colorArgb = colorArgb; self.mode = mode; self.size = size
    }
}
public struct DanmakuEvent: Codable, Sendable {
    public var id: String
    public var timestampMs: Int64
    public var text: String
    public var style: DanmakuStyle?
    public init(id: String, timestampMs: Int64, text: String, style: DanmakuStyle = DanmakuStyle()) {
        self.id = id; self.timestampMs = timestampMs; self.text = text; self.style = style
    }
}
public struct DanmakuTrack: Codable, Sendable {
    public var mediaId: String
    public var status: String
    public var comments: [DanmakuEvent]?
    public var message: String?
    public init(mediaId: String, status: String = "READY", comments: [DanmakuEvent] = []) {
        self.mediaId = mediaId; self.status = status; self.comments = comments
    }
}
public struct DanmakuSettings: Codable, Equatable, Sendable {
    public var visible = true
    public var showScrolling = true
    public var showTop = true
    public var showBottom = true
    public var opacity = 1.0
    public var fontScale = 1.0
    public var speed = 1.0
    public var density = 1.0
    public var area = 1.0
    public var offsetMs: Int64 = 0
    public init() {}
    public func shows(_ mode: String) -> Bool {
        visible && (mode == "TOP" ? showTop : mode == "BOTTOM" ? showBottom : showScrolling)
    }
}
public struct DanmakuPlacement: Sendable {
    public var event: DanmakuEvent
    public var lane: Int
    public var width: Double
    public var viewport: Double
    public var duration: Double
    public var start: Double
    public init(event: DanmakuEvent, lane: Int, width: Double, viewport: Double, duration: Double, start: Double) {
        self.event = event; self.lane = lane; self.width = width; self.viewport = viewport
        self.duration = duration; self.start = start
    }
    public var end: Double { start + duration }
    public func x(at time: Double) -> Double { viewport - (time - start) / duration * (viewport + width) }
}
public enum DanmakuScheduler {
    /// Schedules complete tracks in batches; rendering uses only Swift placements.
    public static func schedule(events: [DanmakuEvent], widths: [Double], viewport: Double, lanes: Int,
                                duration: Double, gap: Double = 24, offset: Int64 = 0) -> [DanmakuPlacement] {
        guard viewport > 0, lanes > 0, duration > 0, widths.count == events.count else { return [] }
        var tails = [DanmakuPlacement?](repeating: nil, count: lanes)
        var placements = [DanmakuPlacement]()
        for index in events.indices.sorted(by: { events[$0].timestampMs < events[$1].timestampMs }) {
            let event = events[index]
            let start = max(0, Double(event.timestampMs) + Double(offset))
            guard widths[index].isFinite, widths[index] > 0 else { continue }
            var candidate = DanmakuPlacement(event: event, lane: 0, width: widths[index], viewport: viewport, duration: duration, start: start)
            for lane in tails.indices {
                candidate.lane = lane
                if let tail = tails[lane], start < tail.end {
                    guard viewport - tail.x(at: start) - tail.width >= gap,
                          candidate.x(at: tail.end) - tail.x(at: tail.end) - tail.width >= gap else { continue }
                }
                tails[lane] = candidate; placements.append(candidate); break
            }
        }
        return placements
    }
    public static func visible(_ placements: [DanmakuPlacement], time: Double, maxDuration: Double) -> ArraySlice<DanmakuPlacement> {
        func bound(_ value: Double, inclusive: Bool) -> Int {
            var low = 0; var high = placements.count
            while low < high {
                let mid = (low + high) / 2
                if placements[mid].start < value || (inclusive && placements[mid].start == value) { low = mid + 1 } else { high = mid }
            }
            return low
        }
        return placements[bound(time - maxDuration, inclusive: true)..<bound(time, inclusive: true)]
    }
}
