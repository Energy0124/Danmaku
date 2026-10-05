import SwiftUI
import DanmakuCore

struct DanmakuOverlay: View {
    @ObservedObject var engine: PlaybackEngine
    var settings: DanmakuSettings
    @State private var placements: [DanmakuPlacement] = []
    @State private var fixed: [DanmakuPlacement] = []
    @State private var duration = 8000.0
    var body: some View {
        GeometryReader { geometry in
            TimelineView(.animation(paused: !engine.playing)) { timeline in
                Canvas { context, size in
                    guard settings.visible else { return }
                    let time = engine.clock(at: timeline.date)
                    context.opacity = settings.opacity
                    let visible = DanmakuScheduler.visible(placements, time: time, maxDuration: duration)
                    for placement in visible { draw(placement, time: time, size: size, context: &context, scrolling: true) }
                    for placement in DanmakuScheduler.visible(fixed, time: time, maxDuration: 4000) {
                        draw(placement, time: time, size: size, context: &context, scrolling: false)
                    }
                }
            }
            .onChange(of: geometry.size, initial: true) { _, size in rebuild(size) }
            .onChange(of: settings) { _, _ in rebuild(geometry.size) }
            .onChange(of: engine.comments.count) { _, _ in rebuild(geometry.size) }
            .onChange(of: engine.danmakuStatus) { _, _ in rebuild(geometry.size) }
        }.allowsHitTesting(false).accessibilityHidden(true)
    }
    private func fontSize(_ event: DanmakuEvent) -> Double {
        22 * settings.fontScale * (event.style?.size == "LARGE" ? 1.3 : event.style?.size == "SMALL" ? 0.75 : 1)
    }
    private func rebuild(_ size: CGSize) {
        duration = 8000 / max(0.25, settings.speed)
        let laneHeight = 22 * settings.fontScale * 1.55
        let lanes = max(1, min(Int(size.height * settings.area / laneHeight), Int((8 * settings.density * settings.area).rounded())))
        let events = engine.comments.filter { ($0.style?.mode ?? "SCROLLING") == "SCROLLING" && settings.showScrolling }
        let widths = events.map { Double(($0.text as NSString).size(withAttributes: [.font: UIFont.systemFont(ofSize: fontSize($0), weight: .semibold)]).width) }
        placements = DanmakuScheduler.schedule(events: events, widths: widths, viewport: size.width, lanes: lanes,
                                               duration: duration, gap: 22 * settings.fontScale, offset: settings.offsetMs)
        // Fixed comments reserve distinct lanes for their lifetime instead of painting over each other.
        fixed = []
        for mode in ["TOP", "BOTTOM"] where settings.shows(mode) {
            var release = [Double](repeating: 0, count: lanes)
            for event in engine.comments where event.style?.mode == mode {
                let start = max(0, Double(event.timestampMs + settings.offsetMs))
                guard let lane = release.firstIndex(where: { $0 <= start }) else { continue }
                release[lane] = start + 4000
                fixed.append(DanmakuPlacement(event: event, lane: lane, width: 0, viewport: size.width, duration: 4000, start: start))
            }
        }
        fixed.sort { $0.start < $1.start }
    }
    private func draw(_ placement: DanmakuPlacement, time: Double, size: CGSize, context: inout GraphicsContext, scrolling: Bool) {
        let event = placement.event
        guard settings.shows(event.style?.mode ?? "SCROLLING") else { return }
        let color = event.style?.colorArgb ?? .max
        let text = Text(event.text).font(.system(size: fontSize(event), weight: .semibold))
            .foregroundStyle(Color(red: Double((color >> 16) & 255) / 255, green: Double((color >> 8) & 255) / 255, blue: Double(color & 255) / 255))
        let laneHeight = 22 * settings.fontScale * 1.55
        let y = event.style?.mode == "BOTTOM" ? size.height * settings.area - Double(placement.lane + 1) * laneHeight : Double(placement.lane) * laneHeight + 8
        let x = scrolling ? placement.x(at: time) : size.width / 2
        var textContext = context
        textContext.addFilter(.shadow(color: .black, radius: 1, x: 1, y: 1))
        textContext.draw(text, at: CGPoint(x: x, y: y), anchor: scrolling ? .topLeading : .top)
    }
}
