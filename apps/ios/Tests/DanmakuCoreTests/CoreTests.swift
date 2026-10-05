import XCTest
@testable import DanmakuCore

final class CoreTests: XCTestCase {
    private var repository: URL {
        URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
    }
    private func fixture(_ name: String) throws -> [String: Any] {
        let path = repository.appendingPathComponent("shared/domain/src/jvmTest/resources/domain-conformance-fixtures/\(name).json")
        return try JSONSerialization.jsonObject(with: Data(contentsOf: path)) as! [String: Any]
    }
    private func inputs(_ name: String) throws -> (Catalog, [PlaybackProgress], [String: Any]) {
        let data = try fixture(name)
        let input = data["input"] as! [String: Any]
        let catalog = try JSONDecoder().decode(Catalog.self, from: JSONSerialization.data(withJSONObject: input["catalog"]!))
        let progress = try JSONDecoder().decode([PlaybackProgress].self, from: JSONSerialization.data(withJSONObject: input["progress"]!))
        return (catalog, progress, data["expected"] as! [String: Any])
    }
    func testGroupingMatchesAndroidFixture() throws {
        let (catalog, _, expected) = try inputs("series-grouping")
        let groups = LibraryPolicy.grouped(catalog.items)
        let rows = expected["groupedSeries"] as! [[String: Any]]
        XCTAssertEqual(groups.map(\.id), rows.map { $0["id"] as! String })
        for (group, row) in zip(groups, rows) {
            XCTAssertEqual(group.title, row["title"] as? String)
            XCTAssertEqual(group.items.count, row["episodeCount"] as? Int)
            XCTAssertEqual(group.items.map { $0.subtitles?.count ?? 0 }.reduce(0, +), row["subtitleTrackCount"] as? Int)
            XCTAssertEqual(group.items.map(\.sizeBytes).reduce(0, +), (row["totalSizeBytes"] as? NSNumber)?.int64Value)
            XCTAssertEqual(group.items.max { $0.relativePath.lowercased() < $1.relativePath.lowercased() }?.id, row["latestIndexedMediaId"] as? String)
            for (season, expectedSeason) in zip(group.seasons, row["seasons"] as! [[String: Any]]) {
                XCTAssertEqual(season.id, expectedSeason["id"] as? String)
                XCTAssertEqual(season.sortKey, expectedSeason["sortKey"] as? Int)
                XCTAssertEqual(season.items.map(\.id), expectedSeason["itemIds"] as? [String])
            }
        }
    }
    func testWatchAndResumeMatchAndroidFixture() throws {
        let (catalog, progress, expected) = try inputs("watch-state")
        let latest = LibraryPolicy.latest(progress)
        let rows = expected["watchStatusByMediaId"] as! [[String: Any]]
        XCTAssertEqual(Set(catalog.items.map(\.id)), Set(rows.map { $0["mediaId"] as! String }))
        for row in rows { XCTAssertEqual(latest[row["mediaId"] as! String]?.watchState ?? "NEW", row["state"] as? String) }
        let boundary = PlaybackProgress(mediaId: "a", positionMs: 10_000, durationMs: 40_000, updatedAtEpochMs: 1)
        XCTAssertEqual(boundary.resume(), 10_000)
        XCTAssertEqual(boundary.watchState, "WATCHED")
    }
    func testHomeOrderingMatchesAndroidFixtures() throws {
        for name in ["next-up", "continue-watching"] {
            let (catalog, progress, expected) = try inputs(name)
            let next = name == "next-up" ? LibraryPolicy.nextUp(catalog, progress: progress) : LibraryPolicy.continuing(catalog, progress: progress)
            let rows = expected[name == "next-up" ? "nextUp" : "continueWatching"] as! [[String: Any]]
            XCTAssertEqual(next.map { $0.item.id }, rows.map { $0["mediaId"] as! String })
            if name == "next-up" {
                XCTAssertEqual(next.map(\.reason), rows.map { $0["reason"] as! String })
                XCTAssertEqual(next.map { $0.source?.mediaId }, rows.map { $0["sourceProgressMediaId"] as? String })
            }
        }
    }
    func testRustWireFixtureDecodesOmittedDefaults() throws {
        let path = repository.appendingPathComponent("native/library-server/tests/fixtures/lan-protocol/catalog.json")
        let fixture = try JSONSerialization.jsonObject(with: Data(contentsOf: path)) as! [String: Any]
        let response = fixture["response"] as! [String: Any]
        let body = response["body"] as! [String: Any]
        let catalog = try JSONDecoder().decode(Catalog.self, from: Data((body["text"] as! String).utf8))
        XCTAssertEqual(catalog.items.first?.subtitles?.first?.id, "subtitle-id")
        XCTAssertNil(catalog.items.first?.animeMetadata)
    }
    private func lanResponse(_ name: String) throws -> Data {
        let path = repository.appendingPathComponent("native/library-server/tests/fixtures/lan-protocol/\(name).json")
        let fixture = try JSONSerialization.jsonObject(with: Data(contentsOf: path)) as! [String: Any]
        let response = fixture["response"] as! [String: Any]
        let body = response["body"] as! [String: Any]
        return Data((body["text"] as! String).utf8)
    }
    func testConnectUsesRustStatusCatalogAndProgressResponses() async throws {
        let statusData = try lanResponse("server-status")
        let status = try JSONDecoder().decode(ServerStatus.self, from: statusData)
        XCTAssertEqual(status.appName, "Danmaku")
        XCTAssertEqual(status.apiVersion, 1)
        XCTAssertEqual(status.mediaStreaming, true)
        XCTAssertEqual(status.scanning, false)
        let client = LibraryClient(transport: ProtocolFixtureTransport(responses: [
            "/api/server/status": statusData,
            "/api/library": try lanResponse("catalog"),
            "/api/progress": try lanResponse("progress-list")]))
        let (catalog, progress) = try await client.connect(Connection(name: "Fixture", baseURL: "http://fixture.local"))
        XCTAssertEqual(catalog.items.first?.id, "episode-id")
        XCTAssertEqual(progress.first?.mediaId, "episode-id")
        XCTAssertEqual(progress.first?.positionMs, 12345)
    }
    func testStatusOverridesDefaultsAndRejectsUnsupportedServer() async throws {
        let data = Data(#"{"appName":"Custom","apiVersion":2,"mediaStreaming":false,"scanning":true,"scanFilesSeen":12,"scanError":"Failed"}"#.utf8)
        let status = try JSONDecoder().decode(ServerStatus.self, from: data)
        XCTAssertEqual(status.appName, "Custom")
        XCTAssertEqual(status.apiVersion, 2)
        XCTAssertEqual(status.mediaStreaming, false)
        XCTAssertEqual(status.scanning, true)
        XCTAssertEqual(status.scanFilesSeen, 12)
        XCTAssertEqual(status.scanError, "Failed")
        for body in [data, Data(#"{"mediaStreaming":false}"#.utf8)] {
            let client = LibraryClient(transport: ProtocolFixtureTransport(responses: ["/api/server/status": body]))
            do {
                _ = try await client.connect(Connection(name: "Fixture", baseURL: "http://fixture.local"))
                XCTFail("Unsupported server should be rejected")
            } catch ClientError.incompatibleServer {}
        }
        XCTAssertThrowsError(try JSONDecoder().decode(ServerStatus.self, from: Data(#"{"apiVersion":"invalid"}"#.utf8)))
    }
    func testLibraryIndexPreservesOrderingFoldersAndFilters() throws {
        let (catalog, progress, _) = try inputs("series-grouping")
        let index = LibraryIndex(catalog)
        XCTAssertEqual(index.series.map { $0.items.map(\.id) }, LibraryPolicy.grouped(catalog.items).map { $0.items.map(\.id) })
        let root = index.folder([])
        XCTAssertEqual(root.descendants.map(\.id), catalog.items.map(\.id))
        for folder in root.folders {
            XCTAssertEqual(index.folder([folder]).descendants.map(\.id), LibraryPolicy.descendants(catalog.items, path: [folder]).map(\.id))
        }
        let item = try XCTUnwrap(catalog.items.first)
        XCTAssertEqual(index.filtered(query: "", filter: "Favorites", progress: LibraryPolicy.latest(progress), favorites: [item.id]).flatMap(\.items).map(\.id), [item.id])
        XCTAssertTrue(index.filtered(query: "nonexistent-search-term", filter: "All", progress: [:], favorites: []).isEmpty)
        XCTAssertEqual(index.filtered(query: "", filter: "Watched", progress: [item.id: PlaybackProgress(mediaId: item.id, positionMs: 60000, durationMs: 60000, updatedAtEpochMs: 1)], favorites: []).flatMap(\.items).map(\.id), [item.id])
    }
    func testCheckpointIsolationAndAcknowledgementRace() throws {
        var state = PersistentState()
        let first = PlaybackProgress(mediaId: "same", positionMs: 12_000, durationMs: nil, updatedAtEpochMs: 1)
        let newer = PlaybackProgress(mediaId: "same", positionMs: 20_000, durationMs: nil, updatedAtEpochMs: 2)
        state.checkpoint(server: "pc-a", progress: first)
        let inFlight = state.pending[0]
        state.checkpoint(server: "pc-b", progress: first)
        state.checkpoint(server: "pc-a", progress: newer)
        state.acknowledge(inFlight)
        XCTAssertEqual(state.uploads(server: "pc-a", remote: []).map(\.progress), [newer])
        XCTAssertEqual(state.uploads(server: "pc-b", remote: []).count, 1)
        XCTAssertTrue(state.uploads(server: "pc-a", remote: [newer]).isEmpty)
    }
    func testGenerationRejectsStaleResponses() {
        var gate = RequestGeneration(); let old = gate.advance(); let current = gate.advance()
        XCTAssertFalse(gate.accepts(old)); XCTAssertTrue(gate.accepts(current))
    }
    func testDanmakuCannotCollideAndSeekIsDeterministic() {
        let events = [0, 0, 2000, 8000].enumerated().map { DanmakuEvent(id: "\($0.offset)", timestampMs: Int64($0.element), text: "Hello") }
        let layout = DanmakuScheduler.schedule(events: events, widths: [100, 100, 100, 100], viewport: 1000, lanes: 1, duration: 8000)
        XCTAssertEqual(layout.map { $0.event.id }, ["0", "2", "3"])
        XCTAssertEqual(DanmakuScheduler.visible(layout, time: 1000, maxDuration: 8000).count, 1)
        XCTAssertEqual(DanmakuScheduler.visible(layout, time: 9000, maxDuration: 8000).map { $0.event.id }, ["2", "3"])
        XCTAssertEqual(layout[0].x(at: 1000), 862.5)
    }
    func testAtomicStorageAndFavoritesSurviveRelaunch() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let file = directory.appendingPathComponent("state.json")
        var state = PersistentState(); state.favorites["pc"] = ["one"]
        try AtomicFile.write(state, to: file)
        XCTAssertEqual(try AtomicFile.read(PersistentState.self, at: file, default: PersistentState()).favorites["pc"], ["one"])
        try Data("broken".utf8).write(to: file)
        XCTAssertThrowsError(try AtomicFile.read(PersistentState.self, at: file, default: PersistentState()))
    }
    func testDownloadRejectsTruncationAndMissingFile() throws {
        let file = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: file) }
        try Data([1, 2, 3]).write(to: file)
        let response = HTTPURLResponse(url: URL(string: "http://localhost/media/a")!, statusCode: 200, httpVersion: nil, headerFields: nil)!
        XCTAssertThrowsError(try DownloadEntry.validateVideo(url: file, expectedBytes: 4, response: response))
        XCTAssertNoThrow(try DownloadEntry.validateVideo(url: file, expectedBytes: 3, response: response))
        let item = MediaItem(id: "a", seriesTitle: "Series", episodeTitle: "Episode", relativePath: "a.mkv", sizeBytes: 3, mediaType: "video/x-matroska", streamPath: "/media/a")
        let entry = DownloadEntry(connection: try Connection(name: "PC", baseURL: "http://localhost"), item: item)
        XCTAssertThrowsError(try entry.video(root: file.deletingLastPathComponent()))
    }
    func testConnectionValidationAndPreviewPreservesIntegerPrecision() throws {
        XCTAssertThrowsError(try Connection(name: "", baseURL: "http://user:secret@pc"))
        XCTAssertThrowsError(try Connection(name: "", baseURL: "http://pc/path"))
        let connection = try Connection(name: "PC", baseURL: "http://pc.local:8686/")
        XCTAssertEqual(try connection.url(path: "/api/library").absoluteString, "http://pc.local:8686/api/library")
        XCTAssertThrowsError(try connection.url(path: "//evil.invalid/media"))
        let raw = Data(#"{"animeId":{"provider":"BANGUMI","value":9007199254740993},"watchedEpisodes":4}"#.utf8)
        let value = try JSONDecoder().decode(JSONValue.self, from: raw)
        XCTAssertEqual(try JSONDecoder().decode(JSONValue.self, from: JSONEncoder().encode(value)), value)
    }
}

private struct ProtocolFixtureTransport: LibraryTransport {
    let responses: [String: Data]
    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let url = request.url!
        let data = try XCTUnwrap(responses[url.path], "Unexpected protocol request: \(url.path)")
        return (data, HTTPURLResponse(url: url, statusCode: 200, httpVersion: nil, headerFields: nil)!)
    }
}
