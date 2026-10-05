import XCTest
@testable import Danmaku
import DanmakuCore

final class NetworkTests: XCTestCase {
    @MainActor private func waitFor(_ condition: () -> Bool, seconds: Double = 10) async throws {
        let deadline = Date().addingTimeInterval(seconds)
        while !condition(), Date() < deadline { try await Task.sleep(for: .milliseconds(50)) }
        guard condition() else { XCTFail("Fixture operation timed out"); throw URLError(.timedOut) }
    }
    @MainActor func testOfflineBundleHandlesMissingOptionalAssetsAndDeletion() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        let media = Data([1, 2, 3, 4])
        let server = try FixtureServer { _, path, _ in
            if path.hasPrefix("/api/danmaku/") { return (200, Data(#"{"mediaId":"one","status":"READY","comments":[]}"#.utf8)) }
            if path == "/media/one" { return (200, media) }
            return (404, Data())
        }
        defer { server.stop() }
        try await waitFor { server.ready }
        let connection = try Connection(name: "Fixture", baseURL: server.baseURL)
        var item = MediaItem(id: "one", seriesTitle: "Show", episodeTitle: "Episode", relativePath: "one.mkv", sizeBytes: 4, mediaType: "video/x-matroska", streamPath: "/media/one")
        item.posterPath = "/missing-poster"
        let downloads = Downloads(root: root, background: false)
        defer { downloads.shutdown() }
        await downloads.enqueue([item], connection: connection, client: LibraryClient())
        try await waitFor { downloads.entries.first?.state == .ready }
        let entry = try XCTUnwrap(downloads.entries.first)
        XCTAssertEqual(try Data(contentsOf: entry.video(root: root)), media)
        XCTAssertEqual(entry.danmaku?.status, "READY")
        downloads.playingEntryID = entry.id
        downloads.delete(entry.id)
        XCTAssertEqual(downloads.entries.count, 1)
        downloads.playingEntryID = nil
        downloads.delete(entry.id)
        XCTAssertTrue(downloads.entries.isEmpty)
        XCTAssertFalse(FileManager.default.fileExists(atPath: root.appendingPathComponent(entry.id).path))
    }
    @MainActor func testInterruptedDownloadRelaunchRangeResumeAndChangedSource() async throws {
        for (changed, preciseTag) in [(false, false), (true, false), (true, true)] {
            let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
            defer { try? FileManager.default.removeItem(at: root) }
            let first = Data(repeating: 1, count: 4 * 1024 * 1024)
            let server = try FixtureServer { _, _, _ in (404, Data()) }
            server.media = first; server.chunkDelay = 0.02
            if preciseTag { server.etag = "\"first\"" }
            defer { server.stop() }
            try await waitFor { server.ready }
            let connection = try Connection(name: "Fixture", baseURL: server.baseURL)
            let item = MediaItem(id: "one", seriesTitle: "Show", episodeTitle: "Episode", relativePath: "one.mp4", sizeBytes: Int64(first.count), mediaType: "video/mp4", streamPath: "/media/one")
            let original = Downloads(root: root, background: false)
            await original.enqueue([item], connection: connection, client: LibraryClient())
            try await waitFor { (original.entries.first?.receivedBytes ?? 0) >= 131072 }
            let id = try XCTUnwrap(original.entries.first?.id)
            original.pause(id)
            try await waitFor { original.entries.first?.resumeData != nil }
            original.shutdown()
            let restored = Downloads(root: root, background: false)
            defer { restored.shutdown() }
            XCTAssertEqual(restored.entries.first?.state, .paused)
            let final = changed ? Data(repeating: 2, count: first.count) : first
            if changed {
                server.media = final
                if preciseTag { server.etag = "\"second\"" }
                else { server.lastModified = "Tue, 06 Oct 2026 00:00:00 GMT" }
            }
            restored.resume(id)
            try await waitFor { restored.entries.first?.state == .ready }
            let entry = try XCTUnwrap(restored.entries.first)
            XCTAssertEqual(try Data(contentsOf: entry.video(root: root)), final)
            let resumed = server.requests.filter { $0.path == "/media/one" && $0.headers["range"] != nil }
            XCTAssertFalse(resumed.isEmpty, "Native resume must request a byte range")
            XCTAssertEqual(resumed.first?.headers["if-range"], preciseTag ? "\"first\"" : "Mon, 05 Oct 2026 00:00:00 GMT")
        }
    }
    @MainActor func testStorageFailureDoesNotPublishSuccessfulDownload() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try Data([1]).write(to: root)
        defer { try? FileManager.default.removeItem(at: root) }
        let server = try FixtureServer { _, _, _ in (404, Data()) }
        defer { server.stop() }
        try await waitFor { server.ready }
        let queue = Downloads(root: root, background: false)
        defer { queue.shutdown() }
        let item = MediaItem(id: "one", seriesTitle: "Show", episodeTitle: "Episode", relativePath: "one.mp4", sizeBytes: 4, mediaType: "video/mp4", streamPath: "/media/one")
        await queue.enqueue([item], connection: try Connection(name: "Fixture", baseURL: server.baseURL), client: LibraryClient())
        XCTAssertNotNil(queue.error)
        XCTAssertTrue(queue.entries.isEmpty)
    }
    @MainActor func testProtocolAndExactPreviewRequest() async throws {
        var saved: Data?
        let server = try FixtureServer { method, path, body in
            if path == "/api/server/status" { return (200, Data(#"{"appName":"Danmaku","apiVersion":1,"mediaStreaming":true}"#.utf8)) }
            if path == "/api/library" { return (200, Data(#"{"rootName":"Fixture","indexedAtEpochMs":0,"items":[]}"#.utf8)) }
            if path == "/api/progress" { return (200, Data("[]".utf8)) }
            if path == "/api/providers/tracking/sync", method == "POST" { saved = body; return (409, Data()) }
            return (404, Data())
        }
        defer { server.stop() }
        try await waitFor { server.ready }
        let connection = try Connection(name: "Fixture", baseURL: server.baseURL)
        let client = LibraryClient()
        let (catalog, progress) = try await client.connect(connection)
        XCTAssertEqual(catalog.rootName, "Fixture"); XCTAssertTrue(progress.isEmpty)
        let preview = JSONValue.object(["watchedEpisodes": .integer(5), "animeId": .object(["provider": .string("BANGUMI"), "value": .integer(9)])])
        do { _ = try await client.sync(connection, updates: [preview]); XCTFail("Changed preview must be rejected") }
        catch ClientError.http(409) { }
        let request = try JSONDecoder().decode(JSONValue.self, from: XCTUnwrap(saved))
        XCTAssertEqual(request["expectedUpdates"].array, [preview])
    }
}
