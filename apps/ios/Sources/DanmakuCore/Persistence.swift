import Foundation

public struct Checkpoint: Codable, Sendable {
    public var server: String
    public var progress: PlaybackProgress
}
public struct PersistentState: Codable, Sendable {
    public var connections: [Connection] = []
    public var selectedServer: String?
    public var catalogs: [String: Catalog] = [:]
    public var progress: [String: [PlaybackProgress]] = [:]
    public var favorites: [String: Set<String>] = [:]
    public var pending: [Checkpoint] = []
    public var danmaku = DanmakuSettings()
    public init() {}
    public mutating func checkpoint(server: String, progress: PlaybackProgress) {
        if pending.contains(where: { $0.server == server && $0.progress.mediaId == progress.mediaId && $0.progress.updatedAtEpochMs > progress.updatedAtEpochMs }) { return }
        pending.removeAll { $0.server == server && $0.progress.mediaId == progress.mediaId && $0.progress.updatedAtEpochMs <= progress.updatedAtEpochMs }
        pending.append(Checkpoint(server: server, progress: progress))
        self.progress[server] = Array(LibraryPolicy.latest((self.progress[server] ?? []) + [progress]).values)
    }
    public mutating func acknowledge(_ checkpoint: Checkpoint) {
        pending.removeAll { $0.server == checkpoint.server && $0.progress == checkpoint.progress }
    }
    public func uploads(server: String, remote: [PlaybackProgress]) -> [Checkpoint] {
        let latest = LibraryPolicy.latest(remote)
        return pending.filter { checkpoint in
            checkpoint.server == server && (latest[checkpoint.progress.mediaId].map {
                $0.updatedAtEpochMs < checkpoint.progress.updatedAtEpochMs
            } ?? true)
        }
    }
}

public protocol StatePersistence: Sendable {
    func load() throws -> PersistentState
    func save(_ state: PersistentState) throws
}
public struct FileStatePersistence: StatePersistence {
    private let url: URL
    public init(url: URL) { self.url = url }
    public func load() throws -> PersistentState { try AtomicFile.read(PersistentState.self, at: url, default: PersistentState()) }
    public func save(_ state: PersistentState) throws { try AtomicFile.write(state, to: url) }
}

public enum AtomicFile {
    public static func read<T: Decodable>(_ type: T.Type, at url: URL, default defaultValue: T) throws -> T {
        guard FileManager.default.fileExists(atPath: url.path) else { return defaultValue }
        return try JSONDecoder().decode(type, from: Data(contentsOf: url))
    }
    public static func write<T: Encodable>(_ value: T, to url: URL) throws {
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try JSONEncoder().encode(value).write(to: url, options: .atomic)
    }
}

public enum DownloadState: String, Codable { case queued, downloading, paused, failed, ready, cancelled }
public struct DownloadAsset: Codable, Identifiable {
    public var id: String
    public var path: String
    public var filename: String
    public var required: Bool
    public var complete: Bool = false
    public init(id: String, path: String, filename: String, required: Bool) {
        self.id = id; self.path = path; self.filename = filename; self.required = required
    }
}
public struct DownloadEntry: Codable, Identifiable {
    public var id: String
    public var connection: Connection
    public var item: MediaItem
    public var state: DownloadState = .queued
    public var assets: [DownloadAsset]
    public var receivedBytes: Int64 = 0
    public var expectedBytes: Int64 = 0
    public var resumeData: Data?
    public var error: String?
    public var danmaku: DanmakuTrack?
    public init(connection: Connection, item: MediaItem) {
        self.id = UUID().uuidString; self.connection = connection; self.item = item
        let ext = (item.relativePath as NSString).pathExtension
        assets = [DownloadAsset(id: "video", path: item.streamPath, filename: "video." + (ext.isEmpty ? "bin" : ext), required: true)]
        assets += (item.subtitles ?? []).enumerated().map { index, subtitle in
            DownloadAsset(id: subtitle.id, path: subtitle.streamPath,
                          filename: "subtitle-\(index)." + (subtitle.relativePath as NSString).pathExtension, required: false)
        }
        if let poster = item.posterPath { assets.append(DownloadAsset(id: "poster", path: poster, filename: "poster.jpg", required: false)) }
    }
    public var nextAsset: Int? { assets.firstIndex { !$0.complete } }
    public func file(root: URL, asset: DownloadAsset) -> URL { root.appendingPathComponent(id).appendingPathComponent(asset.filename) }
    public func video(root: URL) throws -> URL {
        guard state == .ready, let asset = assets.first, asset.complete,
              let attributes = try? FileManager.default.attributesOfItem(atPath: file(root: root, asset: asset).path),
              let bytes = attributes[.size] as? NSNumber, bytes.int64Value > 0,
              item.sizeBytes == 0 || bytes.int64Value == item.sizeBytes else { throw ClientError.missingFile }
        return file(root: root, asset: asset)
    }
    public static func validateVideo(url: URL, expectedBytes: Int64, response: HTTPURLResponse) throws {
        guard (200...299).contains(response.statusCode),
              let bytes = (try FileManager.default.attributesOfItem(atPath: url.path)[.size]) as? NSNumber,
              bytes.int64Value > 0, expectedBytes == 0 || bytes.int64Value == expectedBytes else { throw ClientError.invalidDownload }
    }
}
