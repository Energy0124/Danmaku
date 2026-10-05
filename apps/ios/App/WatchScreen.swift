import SwiftUI
import UniformTypeIdentifiers
import DanmakuCore

struct VideoSurface: UIViewRepresentable {
    let engine: PlaybackEngine
    func makeUIView(context: Context) -> UIView {
        let container = UIView(); container.backgroundColor = .black
        engine.surface.removeFromSuperview(); container.addSubview(engine.surface)
        engine.surface.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        engine.surface.frame = container.bounds
        return container
    }
    func updateUIView(_ uiView: UIView, context: Context) { if engine.surface.superview === uiView { engine.surface.frame = uiView.bounds } }
}

struct WatchScreen: View {
    @ObservedObject var model: AppModel
    @ObservedObject var engine: PlaybackEngine
    @Binding var fullscreen: Bool
    @State private var options = false
    @State private var importer = false
    @State private var scrub = 0.0
    @State private var scrubbing = false
    var body: some View {
        VStack(spacing: 0) {
            if engine.loaded {
                ZStack {
                    VideoSurface(engine: engine)
                    DanmakuOverlay(engine: engine, settings: model.state.danmaku)
                    if engine.buffering { ProgressView().tint(.white) }
                }
                .aspectRatio(fullscreen ? nil : 16 / 9, contentMode: .fit)
                .frame(maxWidth: .infinity, maxHeight: fullscreen ? .infinity : nil)
                VStack(spacing: 12) {
                    HStack {
                        Text(engine.title).font(.headline).lineLimit(1)
                        Spacer()
                        Button("Playback options", systemImage: "slider.horizontal.3") { options = true }.labelStyle(.iconOnly)
                        Button(LocalizedStringKey(fullscreen ? "Exit fullscreen" : "Fullscreen"), systemImage: fullscreen ? "arrow.down.right.and.arrow.up.left" : "arrow.up.left.and.arrow.down.right") { fullscreen.toggle() }.labelStyle(.iconOnly)
                    }
                    Slider(value: Binding(get: { scrubbing ? scrub : Double(engine.position) }, set: { scrub = $0 }), in: 0...Double(max(1, engine.duration))) { editing in
                        if editing { scrub = Double(engine.position); scrubbing = true }
                        else { engine.seek(Int64(scrub)); scrubbing = false }
                    }.disabled(engine.duration <= 0).accessibilityLabel("Playback position")
                    HStack {
                        Text(timeLabel(engine.position)).monospacedDigit()
                        Spacer()
                        Button("Previous", systemImage: "backward.end.fill") { engine.navigate?(-1) }.disabled(!engine.canPrevious).labelStyle(.iconOnly)
                        Button("Back 10 seconds", systemImage: "gobackward.10") { engine.seek(engine.position - 10_000) }.labelStyle(.iconOnly)
                        Button(LocalizedStringKey(engine.playing ? "Pause" : "Play"), systemImage: engine.playing ? "pause.fill" : "play.fill") { engine.toggle() }.labelStyle(.iconOnly)
                        Button("Forward 10 seconds", systemImage: "goforward.10") { engine.seek(engine.position + 10_000) }.labelStyle(.iconOnly)
                        Button("Next", systemImage: "forward.end.fill") { engine.navigate?(1) }.disabled(!engine.canNext).labelStyle(.iconOnly)
                        Spacer()
                        Text(timeLabel(engine.duration)).monospacedDigit()
                    }.font(.title3)
                    if !fullscreen { Text(engine.danmakuStatus).font(.caption).foregroundStyle(.secondary) }
                    if let error = engine.error { Text(error).font(.caption).foregroundStyle(.red) }
                    if !fullscreen { Button("Stop playback", role: .destructive) { model.stop() } }
                }.padding().background(.ultraThinMaterial)
                if !fullscreen { Spacer(minLength: 0) }
            } else {
                ContentUnavailableView("Choose an episode", systemImage: "play.rectangle", description: Text("Browse your library, downloads, or open a video from Files."))
                Button("Open video from Files", systemImage: "folder") { importer = true }.padding()
            }
        }.navigationTitle("Watch")
        .sheet(isPresented: $options) { PlaybackOptions(model: model, engine: engine) }
        .fileImporter(isPresented: $importer, allowedContentTypes: [.movie, .video, .data]) { result in
            switch result { case .success(let url): model.importFile(url); case .failure(let error): model.error = error.localizedDescription }
        }
    }
}

struct PlaybackOptions: View {
    @ObservedObject var model: AppModel
    @ObservedObject var engine: PlaybackEngine
    @Environment(\.dismiss) private var dismiss
    @State private var offset = ""
    @State private var step: Int64 = 1000
    @State private var invalidOffset = false
    var body: some View {
        NavigationStack {
            Form {
                Section("Playback") {
                    Picker("Playback speed", selection: Binding(get: { engine.rate }, set: { engine.setRate($0) })) {
                        ForEach([Float(0.5), 0.75, 1, 1.25, 1.5, 1.75, 2], id: \.self) { Text(String(format: "%.2g×", $0)).tag($0) }
                    }
                    Picker("Audio", selection: Binding(get: { engine.selectedAudio }, set: { engine.audio($0) })) {
                        ForEach(engine.audioTracks) { Text($0.name).tag($0.id) }
                    }
                    Picker("Subtitles", selection: Binding(get: { engine.selectedSubtitle }, set: { engine.subtitle($0) })) {
                        Text("Disabled").tag(Int32(-1))
                        ForEach(engine.subtitleTracks.filter { $0.id != -1 }) { Text($0.name).tag($0.id) }
                    }
                }
                Section("Danmaku") {
                    Toggle("Show danmaku", isOn: $model.state.danmaku.visible)
                    Toggle("Scrolling", isOn: $model.state.danmaku.showScrolling)
                    Toggle("Top", isOn: $model.state.danmaku.showTop)
                    Toggle("Bottom", isOn: $model.state.danmaku.showBottom)
                    settingSlider("Opacity", value: $model.state.danmaku.opacity, range: 0...1)
                    settingSlider("Text size", value: $model.state.danmaku.fontScale, range: 0.5...2)
                    settingSlider("Travel speed", value: $model.state.danmaku.speed, range: 0.25...3)
                    settingSlider("Density", value: $model.state.danmaku.density, range: 0.1...2)
                    settingSlider("Screen area", value: $model.state.danmaku.area, range: 0.1...1)
                    Text(engine.danmakuStatus).font(.caption)
                    Button("Retry danmaku") { model.retryDanmaku() }
                }
                Section("Timing offset") {
                    TextField("Seconds (−3600 to 3600)", text: $offset).keyboardType(.numbersAndPunctuation)
                    Button("Apply offset") {
                        guard let seconds = Double(offset), seconds.isFinite, (-3600...3600).contains(seconds) else { invalidOffset = true; return }
                        model.state.danmaku.offsetMs = Int64((seconds * 1000).rounded()); invalidOffset = false
                    }
                    if invalidOffset { Text("Enter a number from −3600 to 3600.").foregroundStyle(.red) }
                    Picker("Adjustment step", selection: $step) {
                        ForEach([Int64(100), 500, 1000, 5000, 30_000, 60_000], id: \.self) { Text(String(format: "%.1f s", Double($0) / 1000)).tag($0) }
                    }
                    HStack {
                        Button("Earlier", systemImage: "minus") { adjust(-step) }
                        Spacer()
                        Button("Reset") { model.state.danmaku.offsetMs = 0; updateOffset() }
                        Spacer()
                        Button("Later", systemImage: "plus") { adjust(step) }
                    }
                    Text("Positive values show comments later.").font(.caption)
                }
            }.navigationTitle("Playback options")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
            .onAppear { updateOffset() }
            .onChange(of: model.state.danmaku) { _, _ in model.persist() }
        }
    }
    private func settingSlider(_ title: String, value: Binding<Double>, range: ClosedRange<Double>) -> some View {
        VStack(alignment: .leading) {
            HStack { Text(LocalizedStringKey(title)); Spacer(); Text("\(Int(value.wrappedValue * 100))%").monospacedDigit().foregroundStyle(.secondary) }
            Slider(value: value, in: range).accessibilityLabel(Text(LocalizedStringKey(title)))
        }
    }
    private func updateOffset() { offset = String(format: "%.3f", Double(model.state.danmaku.offsetMs) / 1000) }
    private func adjust(_ delta: Int64) { model.state.danmaku.offsetMs = max(-3_600_000, min(3_600_000, model.state.danmaku.offsetMs + delta)); updateOffset() }
}
