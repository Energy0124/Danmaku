import XCTest
@testable import Danmaku
import DanmakuCore

final class PlaybackTests: XCTestCase {
    enum PlaybackTestError: Error { case timeout }
    @MainActor private func eventually(_ condition: () -> Bool, details: () -> String = { "" }, seconds: Double = 15) async throws {
        let deadline = Date().addingTimeInterval(seconds)
        while !condition(), Date() < deadline { try await Task.sleep(for: .milliseconds(100)) }
        guard condition() else { XCTFail("Playback condition did not become true: " + details()); throw PlaybackTestError.timeout }
    }
    @MainActor func testMP4MKVAndStreamingWithAudioSRTASSSeekAndClock() async throws {
        let bundle = Bundle(for: Self.self)
        for ext in ["mp4", "mkv", "streaming"] {
            let file = try XCTUnwrap(bundle.url(forResource: "probe", withExtension: ext == "streaming" ? "mp4" : ext))
            let server = try FixtureServer { _, _, _ in (404, Data()) }
            defer { server.stop() }
            server.media = try Data(contentsOf: file)
            try await eventually { server.ready }
            let url = ext == "streaming" ? URL(string: server.baseURL + "/media/one")! : file
            let srt = try XCTUnwrap(bundle.url(forResource: "probe", withExtension: "srt"))
            let ass = try XCTUnwrap(bundle.url(forResource: "probe", withExtension: "ass"))
            let engine = PlaybackEngine()
            let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.first as? UIWindowScene)
            let window = UIWindow(windowScene: scene)
            let controller = UIViewController()
            window.rootViewController = controller
            controller.view.addSubview(engine.surface)
            engine.surface.frame = CGRect(x: 0, y: 0, width: 320, height: 180)
            window.makeKeyAndVisible()
            defer { engine.stop(); window.isHidden = true }
            engine.load(url: url, title: "Synthetic \(ext)", resume: 0, subtitles: [("SRT", srt), ("ASS", ass)])
            try await eventually({ engine.playing && engine.position >= 1000 && engine.decodedVideoFrames > 0 && engine.displayedVideoFrames > 0 && engine.audioTracks.filter { $0.id >= 0 }.count >= 2 }, details: { engine.diagnostic })
            try await eventually({ engine.subtitleTracks.filter { $0.id >= 0 }.count >= 2 }, details: { engine.diagnostic })
            let audio = try XCTUnwrap(engine.audioTracks.last)
            engine.audio(audio.id)
            XCTAssertEqual(engine.selectedAudio, audio.id)
            let subtitle = try XCTUnwrap(engine.subtitleTracks.last)
            engine.subtitle(subtitle.id)
            XCTAssertEqual(engine.selectedSubtitle, subtitle.id)
            engine.setRate(1.5)
            XCTAssertEqual(engine.rate, 1.5, accuracy: 0.01)
            engine.toggle()
            try await eventually { !engine.playing }
            let clock = engine.clock(at: Date())
            try await Task.sleep(for: .milliseconds(300))
            XCTAssertEqual(engine.clock(at: Date()), clock, accuracy: 300)
            engine.seek(5000)
            XCTAssertEqual(engine.position, 5000)
            engine.setDanmaku(DanmakuTrack(mediaId: "fixture", comments: [DanmakuEvent(id: "hello", timestampMs: 5000, text: "Hello")]))
            XCTAssertEqual(engine.comments.count, 1)
            engine.toggle()
            try await eventually { engine.playing && engine.position >= 5000 }
            XCTAssertNil(engine.error)
        }
    }
}
