import XCTest
import UIKit
@testable import Danmaku
import DanmakuCore

final class AppTests: XCTestCase {
    func testPlaybackEngineStartsStopped() {
        let player = PlaybackEngine()
        XCTAssertFalse(player.loaded)
        XCTAssertFalse(player.playing)
        XCTAssertEqual(player.clock(at: Date()), 0)
        player.stop()
    }
    func testDownloadQueueRestoresDurablePausedEntry() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        let item = MediaItem(id: "one", seriesTitle: "Show", episodeTitle: "Episode", relativePath: "one.mkv", sizeBytes: 3, mediaType: "video/x-matroska", streamPath: "/media/one")
        var entry = DownloadEntry(connection: try Connection(name: "PC", baseURL: "http://localhost:8686"), item: item)
        entry.state = .paused
        try AtomicFile.write([entry], to: root.appendingPathComponent("index.json"))
        let queue = Downloads(root: root, background: false)
        XCTAssertEqual(queue.entries.first?.state, .paused)
        XCTAssertEqual(queue.entries.first?.connection.id, "http://localhost:8686")
    }
    @MainActor func testAppModelKeepsCheckpointWhenCacheIsDeleted() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        var state = PersistentState()
        state.checkpoint(server: "http://localhost:8686", progress: PlaybackProgress(mediaId: "one", positionMs: 20000, durationMs: 60000, updatedAtEpochMs: 1))
        try AtomicFile.write(state, to: root.appendingPathComponent("state.json"))
        let model = AppModel(root: root, backgroundDownloads: false)
        let before = state.pending
        model.downloads.clear()
        XCTAssertEqual(model.state.pending.map(\.progress), before.map(\.progress))
    }
}

private actor DelayedTransport: LibraryTransport {
    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let url = request.url!
        if url.host == "slow.local" { try await Task.sleep(for: .milliseconds(150)) }
        let body: String
        switch url.path {
        case "/api/server/status": body = #"{"hostMode":"headless-server"}"#
        case "/api/library": body = "{\"rootName\":\"" + url.host! + "\",\"indexedAtEpochMs\":0,\"items\":[]}"
        default: body = "[]"
        }
        return (Data(body.utf8), HTTPURLResponse(url: url, statusCode: 200, httpVersion: nil, headerFields: nil)!)
    }
}

extension AppTests {
    @MainActor func testSlowConnectionCannotOverwriteNewSelectedServer() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        let model = AppModel(root: root, backgroundDownloads: false, client: LibraryClient(transport: DelayedTransport()))
        defer { model.downloads.shutdown() }
        let slow = try Connection(name: "Slow", baseURL: "http://slow.local")
        let fast = try Connection(name: "Fast", baseURL: "http://fast.local")
        let pending = Task { await model.connect(slow) }
        try await Task.sleep(for: .milliseconds(20))
        await model.connect(fast)
        await pending.value
        XCTAssertEqual(model.connection, fast)
        XCTAssertEqual(model.catalog.rootName, "fast.local")
        XCTAssertEqual(model.state.selectedServer, fast.id)
        XCTAssertFalse(model.loading)
        XCTAssertTrue(model.online)
        XCTAssertNil(model.state.catalogs[slow.id])
    }
}
