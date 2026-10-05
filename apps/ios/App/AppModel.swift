import Foundation
import Combine
import UIKit
import DanmakuCore

@MainActor final class AppModel: ObservableObject {
    @Published var state = PersistentState()
    @Published var catalog = Catalog()
    @Published private(set) var progress: [PlaybackProgress] = []
    @Published private(set) var library = LibraryIndex()
    @Published private(set) var progressByID: [String: PlaybackProgress] = [:]
    @Published private(set) var continuing: [NextUp] = []
    @Published private(set) var nextUp: [NextUp] = []
    @Published private(set) var libraryRevision: UInt64 = 0
    @Published private(set) var progressRevision: UInt64 = 0
    @Published var connection: Connection?
    @Published var loading = false
    @Published var online = false
    @Published var error: String?
    @Published var scanCount: Int64?
    @Published var scanning = false
    @Published var accounts: JSONValue = .null
    @Published var tracking: JSONValue = .null
    @Published var trackingBusy = false
    @Published var trackingResult: String?
    let client: LibraryClient
    let downloads: Downloads
    let discovery = Discovery()
    let player = PlaybackEngine()
    private let stateURL: URL
    private let persistence: any StatePersistence
    private var storageHealthy = true
    private let storageQueue = DispatchQueue(label: "app.danmaku.ios.state", qos: .utility)
    private let storedState: Task<(PersistentState, LibrarySnapshot), Error>
    private var restored = false
    private var homeTask: Task<Void, Never>?
    private var generation = RequestGeneration()
    private var playbackGeneration = RequestGeneration()
    private var scanTask: Task<Void, Never>?
    private var uploadTask: Task<Void, Never>?
    private var playbackConnection: Connection?
    private var playbackItem: MediaItem?
    private var playbackOrder: [MediaItem] = []
    private var subscriptions = Set<AnyCancellable>()

    static func forLaunch() -> AppModel {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--qa-fixture") {
            return AppModel(root: FileManager.default.temporaryDirectory.appendingPathComponent("FixtureState"), backgroundDownloads: false)
        }
        #endif
        return AppModel()
    }

    init(root: URL? = nil, backgroundDownloads: Bool = true, client: LibraryClient = LibraryClient(), persistence: (any StatePersistence)? = nil) {
        self.client = client
        let root = root ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("Danmaku")
        stateURL = root.appendingPathComponent("state.json")
        let storage = persistence ?? FileStatePersistence(url: stateURL)
        self.persistence = storage
        storedState = Task.detached(priority: .userInitiated) {
            let state = try storage.load()
            let server = state.selectedServer ?? ""
            return (state, LibrarySnapshot(catalog: state.catalogs[server] ?? Catalog(), progress: state.progress[server] ?? []))
        }
        downloads = Downloads(root: root.appendingPathComponent("Downloads"), background: backgroundDownloads)
        player.checkpoint = { [weak self] position, duration in self?.checkpoint(position: position, duration: duration) }
        player.navigate = { [weak self] direction in self?.navigate(direction) }
        downloads.objectWillChange.sink { [weak self] _ in self?.objectWillChange.send() }.store(in: &subscriptions)
    }
    func restore() async {
        guard !restored else { return }
        do {
            let (saved, snapshot) = try await storedState.value
            guard !restored else { return }
            restored = true; state = saved
            connection = saved.connections.first { $0.id == saved.selectedServer }
            publish(snapshot)
        } catch {
            guard !restored else { return }
            restored = true; storageHealthy = false; self.error = error.localizedDescription
        }
    }
    func persist() {
        guard restored, storageHealthy else { return }
        let snapshot = state; let persistence = persistence
        let backgroundTask = UIApplication.shared.beginBackgroundTask(withName: "Save library state")
        // FIFO writes keep the newest checkpoint last without encoding the catalog on the UI thread.
        storageQueue.async { [weak self] in
            var failure: Error?
            do { try persistence.save(snapshot) } catch { failure = error }
            let error = failure
            Task { @MainActor in
                if let error { self?.error = error.localizedDescription }
                if backgroundTask != .invalid { UIApplication.shared.endBackgroundTask(backgroundTask) }
            }
        }
    }
    func flushPersistence() async {
        await withCheckedContinuation { continuation in storageQueue.async { continuation.resume() } }
    }
    private func publish(_ snapshot: LibrarySnapshot) {
        homeTask?.cancel()
        catalog = snapshot.catalog; library = snapshot.index; libraryRevision &+= 1
        progress = snapshot.progress; progressByID = snapshot.progressByID; progressRevision &+= 1
        continuing = snapshot.continuing; nextUp = snapshot.nextUp
    }
    private func snapshot(_ catalog: Catalog, progress: [PlaybackProgress]) async -> LibrarySnapshot {
        await Task.detached(priority: .userInitiated) { LibrarySnapshot(catalog: catalog, progress: progress) }.value
    }
    private func updateProgress(_ rows: [PlaybackProgress]) {
        progress = rows; progressByID = LibraryPolicy.latest(rows); progressRevision &+= 1
        let revision = progressRevision; let catalog = catalog
        homeTask?.cancel()
        homeTask = Task {
            let home = await Task.detached(priority: .utility) {
                (LibraryPolicy.continuing(catalog, progress: rows), LibraryPolicy.nextUp(catalog, progress: rows))
            }.value
            guard !Task.isCancelled, revision == progressRevision else { return }
            continuing = home.0; nextUp = home.1
        }
    }
    func connect(_ target: Connection) async {
        await restore()
        let previous = connection?.id
        let ticket = generation.advance()
        scanTask?.cancel(); scanning = false; scanCount = nil
        connection = target; online = false; accounts = .null; tracking = .null; trackingResult = nil
        if previous != target.id { publish(LibrarySnapshot()) }
        loading = true; error = nil
        defer { if generation.accepts(ticket) { loading = false } }
        do {
            if previous != target.id, let cached = state.catalogs[target.id] {
                let prepared = await snapshot(cached, progress: state.progress[target.id] ?? [])
                guard generation.accepts(ticket) else { return }
                publish(prepared)
            }
            let (catalog, remote) = try await client.connect(target)
            guard generation.accepts(ticket) else { return }
            let local = state.pending.filter { $0.server == target.id }.map(\.progress)
            let rows = Array(LibraryPolicy.latest(remote + local).values)
            let prepared = await snapshot(catalog, progress: rows)
            guard generation.accepts(ticket) else { return }
            publish(prepared); self.online = true
            let newestLocal = state.pending.filter { $0.server == target.id }.map(\.progress)
            if newestLocal != local { updateProgress(Array(LibraryPolicy.latest(remote + newestLocal).values)) }
            state.catalogs[target.id] = catalog; state.progress[target.id] = progress
            state.selectedServer = target.id
            if !state.connections.contains(where: { $0.id == target.id }) { state.connections.append(target) }
            persist(); reconcile(target, remote: remote)
        } catch {
            guard generation.accepts(ticket) else { return }
            self.error = error.localizedDescription
        }
    }
    #if DEBUG
    @discardableResult func launchFixture() -> Bool {
        let arguments = ProcessInfo.processInfo.arguments
        guard let index = arguments.firstIndex(of: "--qa-fixture"), arguments.indices.contains(index + 1),
              ["mp4", "mkv"].contains(arguments[index + 1]) else { return false }
        let format = arguments[index + 1]
        let root = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("Fixture")
        let video = root.appendingPathComponent("probe." + format)
        guard FileManager.default.fileExists(atPath: video.path) else { error = NSLocalizedString("Downloaded file is missing", comment: ""); return true }
        player.fixtureRunID = arguments.indices.contains(index + 2) ? arguments[index + 2] : ""
        player.fixtureReportURL = root.appendingPathComponent("result-" + format + ".json")
        try? FileManager.default.removeItem(at: player.fixtureReportURL!)
        player.load(url: video, title: "Danmaku fixture · " + format, resume: 0,
                    subtitles: [("SRT", root.appendingPathComponent("probe.srt")), ("ASS", root.appendingPathComponent("probe.ass"))])
        player.setDanmaku(DanmakuTrack(mediaId: "fixture", comments: [
            DanmakuEvent(id: "scroll", timestampMs: 1000, text: "Danmaku · 彈幕"),
            DanmakuEvent(id: "top", timestampMs: 2000, text: "Top · 頂部", style: DanmakuStyle(mode: "TOP")),
            DanmakuEvent(id: "bottom", timestampMs: 3000, text: "Bottom · 底部", style: DanmakuStyle(mode: "BOTTOM"))]))
        return true
    }
    #endif
    func reconnect() async { if let connection { await connect(connection) } }
    func forget(_ target: Connection) {
        state.connections.removeAll { $0.id == target.id }
        if connection?.id == target.id {
            generation.advance(); scanTask?.cancel(); connection = nil; online = false; loading = false; scanning = false; scanCount = nil
            publish(LibrarySnapshot()); state.selectedServer = nil; tracking = .null; accounts = .null
        }
        persist()
    }
    func rescan(_ path: [String]) {
        guard let target = connection, online, !scanning else { return }
        let ticket = generation.value
        scanning = true; scanCount = nil; error = nil
        scanTask = Task {
            defer { if generation.accepts(ticket) { scanning = false } }
            do {
                _ = try await client.raw(target, path: "/api/library/rescan", method: "POST",
                                         body: JSONEncoder().encode(JSONValue.object(["path": .array(path.map(JSONValue.string))])))
                while !Task.isCancelled {
                    let status: ServerStatus = try await client.request(target, path: "/api/server/status")
                    guard generation.accepts(ticket) else { return }
                    scanCount = status.scanFilesSeen
                    if status.scanning != true {
                        if let scanError = status.scanError { error = scanError }
                        let updated: Catalog = try await client.request(target, path: "/api/library")
                        guard generation.accepts(ticket) else { return }
                        let prepared = await snapshot(updated, progress: progress)
                        guard generation.accepts(ticket) else { return }
                        let latest = state.progress[target.id] ?? []
                        publish(prepared)
                        if latest != prepared.progress { updateProgress(latest) }
                        state.catalogs[target.id] = updated; persist(); return
                    }
                    try await Task.sleep(for: .seconds(1))
                }
            } catch { if generation.accepts(ticket), !Task.isCancelled { self.error = error.localizedDescription } }
        }
    }
    func isFavorite(_ item: MediaItem) -> Bool { state.favorites[connection?.id ?? ""]?.contains(item.id) == true }
    func favorite(_ item: MediaItem) {
        guard let connection else { return }
        if isFavorite(item) { state.favorites[connection.id]?.remove(item.id) }
        else { state.favorites[connection.id, default: []].insert(item.id) }
        persist()
    }
    func cachedPoster(_ item: MediaItem) -> URL? {
        guard let connection, let entry = downloads.cached(item, connection: connection),
              let asset = entry.assets.first(where: { $0.id == "poster" && $0.complete }) else { return nil }
        return entry.file(root: downloads.root, asset: asset)
    }
    func download(_ items: [MediaItem]) {
        guard let connection, online else { return }
        Task { await downloads.enqueue(items, connection: connection, client: client) }
    }
    func play(_ item: MediaItem) {
        guard let connection else { return }
        let cached = downloads.cached(item, connection: connection)
        if let cached { playCached(cached); return }
        guard online else { error = NSLocalizedString("Connect to the server or choose a download", comment: ""); return }
        prepare(item, connection: connection, cached: nil, order: catalog.items)
    }
    func playCached(_ entry: DownloadEntry) {
        let order = downloads.entries.filter { $0.connection.id == entry.connection.id && $0.state == .ready }.map(\.item)
        let catalogOrder = state.catalogs[entry.connection.id]?.items ?? order
        let ids = Set(order.map(\.id))
        prepare(entry.item, connection: entry.connection, cached: entry, order: catalogOrder.filter { ids.contains($0.id) })
    }
    private func prepare(_ item: MediaItem, connection target: Connection, cached: DownloadEntry?, order: [MediaItem]) {
        player.saveCheckpoint(); player.stop()
        let ticket = playbackGeneration.advance()
        playbackItem = item; playbackConnection = target; playbackOrder = order
        downloads.playingEntryID = cached?.id
        let rows = state.progress[target.id] ?? []
        let resume = LibraryPolicy.latest(rows)[item.id]?.resume() ?? 0
        do {
            let source = try cached?.video(root: downloads.root) ?? target.url(path: item.streamPath)
            let subtitles: [(String, URL)]
            if let cached {
                subtitles = cached.assets.filter { $0.id != "video" && $0.id != "poster" && $0.complete }
                    .compactMap { asset in
                        let file = cached.file(root: downloads.root, asset: asset)
                        guard FileManager.default.fileExists(atPath: file.path) else { return nil }
                        return ((item.subtitles ?? []).first { $0.id == asset.id }?.label ?? asset.filename, file)
                    }
            } else { subtitles = try (item.subtitles ?? []).map { ($0.label, try target.url(path: $0.streamPath)) } }
            player.load(url: source, title: item.episodeTitle, resume: resume, subtitles: subtitles)
            updateNavigation()
            if let cached { player.setDanmaku(cached.danmaku); return }
            Task {
                do {
                    let id = item.id.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed)!
                    let track: DanmakuTrack = try await client.request(target, path: "/api/danmaku/" + id)
                    guard playbackGeneration.accepts(ticket) else { return }
                    player.setDanmaku(track)
                } catch { if playbackGeneration.accepts(ticket) { player.danmakuStatus = NSLocalizedString("Danmaku request failed. Retry from playback options.", comment: "") } }
            }
        } catch { self.error = error.localizedDescription; downloads.playingEntryID = nil }
    }
    func retryDanmaku() {
        guard let item = playbackItem, let target = playbackConnection, downloads.playingEntryID == nil else { return }
        let ticket = playbackGeneration.value
        player.danmakuStatus = NSLocalizedString("Loading danmaku…", comment: "")
        Task {
            do {
                let id = item.id.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed)!
                let track: DanmakuTrack = try await client.request(target, path: "/api/danmaku/" + id + "?forceRefresh=true")
                if playbackGeneration.accepts(ticket) { player.setDanmaku(track) }
            } catch { if playbackGeneration.accepts(ticket) { player.danmakuStatus = NSLocalizedString("Danmaku request failed. Retry from playback options.", comment: "") } }
        }
    }
    func importFile(_ url: URL) {
        let access = url.startAccessingSecurityScopedResource()
        defer { if access { url.stopAccessingSecurityScopedResource() } }
        do {
            let directory = stateURL.deletingLastPathComponent().appendingPathComponent("Imports")
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            let destination = directory.appendingPathComponent(UUID().uuidString + "." + url.pathExtension)
            try FileManager.default.copyItem(at: url, to: destination)
            player.saveCheckpoint(); player.stop(); playbackGeneration.advance()
            playbackItem = nil; playbackConnection = nil; playbackOrder = []; downloads.playingEntryID = nil
            player.load(url: destination, title: url.lastPathComponent, resume: 0, subtitles: [])
            player.canPrevious = false; player.canNext = false
        } catch { self.error = error.localizedDescription }
    }
    func stop() { player.saveCheckpoint(); player.stop(); playbackGeneration.advance(); downloads.playingEntryID = nil; playbackItem = nil; playbackConnection = nil }
    private func updateNavigation() {
        let index = playbackOrder.firstIndex { $0.id == playbackItem?.id }
        player.canPrevious = index.map { $0 > 0 } ?? false
        player.canNext = index.map { $0 + 1 < playbackOrder.count } ?? false
    }
    private func navigate(_ direction: Int) {
        guard let target = playbackConnection, let index = playbackOrder.firstIndex(where: { $0.id == playbackItem?.id }),
              playbackOrder.indices.contains(index + direction) else { return }
        let item = playbackOrder[index + direction]
        let cached = downloads.playingEntryID == nil ? nil : downloads.cached(item, connection: target)
        prepare(item, connection: target, cached: cached, order: playbackOrder)
    }
    private func checkpoint(position: Int64, duration: Int64?) {
        guard position > 0, let target = playbackConnection, let item = playbackItem else { return }
        let row = PlaybackProgress(mediaId: item.id, positionMs: position, durationMs: duration,
                                   updatedAtEpochMs: Int64(Date().timeIntervalSince1970 * 1000))
        state.checkpoint(server: target.id, progress: row)
        if connection?.id == target.id { updateProgress(state.progress[target.id] ?? []) }
        persist()
        if online, connection?.id == target.id { reconcile(target, remote: []) }
    }
    private func reconcile(_ target: Connection, remote: [PlaybackProgress]) {
        guard uploadTask == nil, state.pending.contains(where: { $0.server == target.id }) else { return }
        uploadTask = Task {
            var failed = false
            defer {
                uploadTask = nil
                if !failed, online, let current = connection,
                   state.pending.contains(where: { $0.server == current.id }) { reconcile(current, remote: []) }
            }
            let pending = state.pending.filter { $0.server == target.id }
            let uploads = state.uploads(server: target.id, remote: remote)
            for row in pending where !uploads.contains(where: { $0.progress == row.progress }) { state.acknowledge(row) }
            for checkpoint in uploads {
                do { try await client.save(target, progress: checkpoint.progress); state.acknowledge(checkpoint); persist() }
                catch { failed = true; return }
            }
            persist()
        }
    }
    func loadTracking(readback: Bool = false) async {
        guard let target = connection, online, !trackingBusy else { return }
        let ticket = generation.value
        trackingBusy = true; trackingResult = nil
        defer { trackingBusy = false }
        do {
            let accounts: JSONValue = try await client.request(target, path: "/api/providers/accounts")
            let response: JSONValue = try await client.request(target, path: readback ? "/api/providers/tracking/readback" : "/api/providers/tracking", method: readback ? "POST" : "GET")
            guard generation.accepts(ticket) else { return }
            self.accounts = accounts; tracking = readback ? response["document"] : response
            if readback { trackingResult = String(format: NSLocalizedString("Readback: %lld successful, %lld errors", comment: ""), response["successCount"].integer ?? 0, Int64(response["errors"].array.count)) }
        } catch { if generation.accepts(ticket) { self.error = error.localizedDescription } }
    }
    func syncTracking(_ updates: [JSONValue], server: String) async {
        guard let target = connection, target.id == server, online, !trackingBusy else { return }
        let ticket = generation.value; trackingBusy = true
        defer { trackingBusy = false }
        do {
            let response = try await client.sync(target, updates: updates)
            guard generation.accepts(ticket) else { return }
            tracking = response["document"]
            trackingResult = String(format: NSLocalizedString("Sync: %lld successful, %lld errors", comment: ""), response["successCount"].integer ?? 0, Int64(response["errors"].array.count))
        } catch {
            guard generation.accepts(ticket) else { return }
            tracking = .null
            if case ClientError.http(409) = error { self.error = NSLocalizedString("Preview changed. Refresh and review again.", comment: "") }
            else { self.error = error.localizedDescription }
        }
    }
}
