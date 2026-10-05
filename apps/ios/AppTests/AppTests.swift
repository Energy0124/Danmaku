import XCTest
import UIKit
import SwiftUI
import Network
@testable import Danmaku
import DanmakuCore

final class AppTests: XCTestCase {
    func testBonjourResolvedAddressesProduceHTTPURLs() throws {
        let scopedIPv4 = try XCTUnwrap(IPv4Address("192.0.2.10%lo0"))
        XCTAssertNotNil(scopedIPv4.interface)
        let ipv4 = try Discovery.connection(name: "Fixture", endpoint: .hostPort(host: .ipv4(scopedIPv4), port: 8686))
        XCTAssertEqual(ipv4.baseURL, "http://192.0.2.10:8686")
        XCTAssertEqual(try ipv4.url(path: "/api/server/status").absoluteString, "http://192.0.2.10:8686/api/server/status")
        let scopedIPv6 = try XCTUnwrap(IPv6Address("fe80::1234%lo0"))
        XCTAssertNotNil(scopedIPv6.interface)
        let ipv6 = try Discovery.connection(name: "Fixture", endpoint: .hostPort(host: .ipv6(scopedIPv6), port: 8686))
        XCTAssertEqual(ipv6.baseURL, "http://[fe80::1234%25lo0]:8686")
        XCTAssertEqual(try ipv6.url(path: "/api/server/status").absoluteString, ipv6.baseURL + "/api/server/status")
        let globalIPv6 = try XCTUnwrap(IPv6Address("2001:db8::1234%lo0"))
        XCTAssertEqual(try Discovery.connection(name: "Fixture", endpoint: .hostPort(host: .ipv6(globalIPv6), port: 12345)).baseURL, "http://[2001:db8::1234]:12345")
        XCTAssertEqual(try Discovery.connection(name: "Fixture", endpoint: .hostPort(host: .name("fixture.local", nil), port: 12345)).baseURL, "http://fixture.local:12345")
        XCTAssertThrowsError(try Discovery.connection(name: "Fixture", endpoint: .service(name: "Fixture", type: "_danmaku._tcp", domain: "local.", interface: nil)))
    }
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
    @MainActor func testAppModelKeepsCheckpointWhenCacheIsDeleted() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        var state = PersistentState()
        state.checkpoint(server: "http://localhost:8686", progress: PlaybackProgress(mediaId: "one", positionMs: 20000, durationMs: 60000, updatedAtEpochMs: 1))
        try AtomicFile.write(state, to: root.appendingPathComponent("state.json"))
        let model = AppModel(root: root, backgroundDownloads: false)
        await model.restore()
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
        await model.flushPersistence()
    }
}

private struct LargeLibraryTransport: LibraryTransport {
    let catalog: Data
    let progress: Data
    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let url = request.url!
        let data: Data
        switch url.path {
        case "/api/server/status": data = Data(#"{"hostMode":"headless-server"}"#.utf8)
        case "/api/library": data = catalog
        default: data = progress
        }
        return (data, HTTPURLResponse(url: url, statusCode: 200, httpVersion: nil, headerFields: nil)!)
    }
}

private final class RecordingStateStore: StatePersistence, @unchecked Sendable {
    private let lock = NSLock()
    private var writes: [PersistentState] = []
    func load() throws -> PersistentState {
        XCTAssertFalse(Thread.isMainThread, "State decoding must stay off the UI thread")
        return PersistentState()
    }
    func save(_ state: PersistentState) throws {
        XCTAssertFalse(Thread.isMainThread, "Catalog encoding and disk writes must stay off the UI thread")
        Thread.sleep(forTimeInterval: 0.02)
        lock.lock(); defer { lock.unlock() }; writes.append(state)
    }
    var saved: [PersistentState] { lock.lock(); defer { lock.unlock() }; return writes }
}

extension AppTests {
    @MainActor func testLargeLibraryConnectAndBrowsingKeepMainRunLoopResponsive() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        let transport = try await Task.detached {
            let items = (0..<10000).map { index in
                MediaItem(id: "episode-\(index)", seriesTitle: "Show \(index / 25)", episodeTitle: "Episode \(index % 25)",
                          relativePath: "Show \(index / 25)/Season 1/Episode \(index % 25).mkv", sizeBytes: 1,
                          mediaType: "video/x-matroska", streamPath: "/media/episode-\(index)")
            }
            let rows = items.enumerated().map { offset, item in
                PlaybackProgress(mediaId: item.id, positionMs: 20000, durationMs: 100000, updatedAtEpochMs: Int64(offset))
            }
            return LargeLibraryTransport(catalog: try JSONEncoder().encode(Catalog(items: items)), progress: try JSONEncoder().encode(rows))
        }.value
        let store = RecordingStateStore()
        let model = AppModel(root: root, backgroundDownloads: false, client: LibraryClient(transport: transport), persistence: store)
        defer { model.downloads.shutdown() }
        var lastTick = Date(); var maxDelay = 0.0; var ticks = 0
        let timer = Timer.scheduledTimer(withTimeInterval: 0.01, repeats: true) { _ in
            let now = Date(); maxDelay = max(maxDelay, now.timeIntervalSince(lastTick)); lastTick = now; ticks += 1
        }
        defer { timer.invalidate() }
        await model.connect(try Connection(name: "Synthetic", baseURL: "http://fixture.local"))
        XCTAssertTrue(model.online)
        XCTAssertEqual(model.library.series.count, 400)
        XCTAssertEqual(model.progressByID.count, 10000)
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 1024, height: 768))
        window.rootViewController = UIHostingController(rootView: LibraryScreen(model: model))
        window.makeKeyAndVisible()
        try await Task.sleep(for: .milliseconds(200))
        window.rootViewController = UIHostingController(rootView: FolderScreen(model: model))
        try await Task.sleep(for: .milliseconds(200))
        window.isHidden = true
        XCTAssertGreaterThan(ticks, 10)
        XCTAssertLessThan(maxDelay, 0.5, "Connection/browsing blocked the UI for \(maxDelay) seconds")
        let item = model.catalog.items[0]
        model.favorite(item); model.favorite(item); model.favorite(item)
        await model.flushPersistence()
        XCTAssertEqual(store.saved.last?.favorites[model.connection!.id], [item.id], "Queued snapshots must retain newest writes")
        print("10,000-episode UI heartbeat: max gap \(maxDelay)s across \(ticks) ticks")
    }
}

private struct UnavailableTransport: LibraryTransport {
    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) { throw ClientError.http(503) }
}

extension AppTests {
    @MainActor func testCachedLibraryIndexRestoresAndSurvivesOfflineServerSwitch() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        let first = try Connection(name: "First", baseURL: "http://first.local")
        let second = try Connection(name: "Second", baseURL: "http://second.local")
        let item = MediaItem(id: "cached", seriesTitle: "Cached Show", episodeTitle: "Episode 1", relativePath: "Show/one.mkv", sizeBytes: 1, mediaType: "video/x-matroska", streamPath: "/media/cached")
        var state = PersistentState()
        state.connections = [first, second]; state.selectedServer = first.id
        state.catalogs[second.id] = Catalog(items: [item])
        state.progress[second.id] = [PlaybackProgress(mediaId: item.id, positionMs: 20000, durationMs: 100000, updatedAtEpochMs: 1)]
        try AtomicFile.write(state, to: root.appendingPathComponent("state.json"))
        let model = AppModel(root: root, backgroundDownloads: false, client: LibraryClient(transport: UnavailableTransport()))
        defer { model.downloads.shutdown() }
        await model.restore()
        XCTAssertEqual(model.connection, first)
        await model.connect(second)
        XCTAssertFalse(model.online)
        XCTAssertEqual(model.library.series.first?.items.first?.id, item.id)
        XCTAssertEqual(model.library.folder(["Show"]).files.first?.id, item.id)
        XCTAssertEqual(model.progressByID[item.id]?.positionMs, 20000)
        XCTAssertEqual(model.continuing.first?.item.id, item.id)
    }
}
