import Foundation

public struct Catalog: Codable, Sendable {
    public var rootName: String
    public var indexedAtEpochMs: Int64
    public var items: [MediaItem]
    public init(rootName: String = "Danmaku", indexedAtEpochMs: Int64 = 0, items: [MediaItem] = []) {
        self.rootName = rootName; self.indexedAtEpochMs = indexedAtEpochMs; self.items = items
    }
}

public struct AnimeID: Codable, Hashable, Sendable {
    public var provider: String
    public var value: Int64
}

public struct AnimeMetadata: Codable, Sendable {
    public var animeId: AnimeID
    public var displayTitle: String
    public var primaryTitle: String
    public var imageUrl: String?
    public var alternateNames: [String]?
}

public struct Subtitle: Codable, Identifiable, Sendable {
    public var id: String
    public var label: String
    public var relativePath: String
    public var mediaType: String
    public var streamPath: String
}

public struct MediaItem: Codable, Identifiable, Sendable {
    public var id: String
    public var seriesTitle: String
    public var episodeTitle: String
    public var relativePath: String
    public var rootLabel: String?
    public var sizeBytes: Int64
    public var mediaType: String
    public var streamPath: String
    public var indexedAtEpochMs: Int64?
    public var subtitles: [Subtitle]?
    public var posterPath: String?
    public var animeMetadata: AnimeMetadata?
    public var metadataStatus: String?
    public var title: String { animeMetadata?.displayTitle ?? seriesTitle }
    public var seriesID: String {
        if let anime = animeMetadata?.animeId { return "anime-\(anime.provider.lowercased())-\(anime.value)" }
        let words = seriesTitle.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            .components(separatedBy: CharacterSet.alphanumerics.inverted).filter { !$0.isEmpty }
        return words.isEmpty ? "series" : words.joined(separator: "-")
    }
    public init(id: String, seriesTitle: String, episodeTitle: String, relativePath: String,
                sizeBytes: Int64, mediaType: String, streamPath: String) {
        self.id = id; self.seriesTitle = seriesTitle; self.episodeTitle = episodeTitle
        self.relativePath = relativePath; self.sizeBytes = sizeBytes
        self.mediaType = mediaType; self.streamPath = streamPath
    }
}

public struct PlaybackProgress: Codable, Equatable, Sendable {
    public var mediaId: String
    public var positionMs: Int64
    public var durationMs: Int64?
    public var updatedAtEpochMs: Int64
    public init(mediaId: String, positionMs: Int64, durationMs: Int64?, updatedAtEpochMs: Int64) {
        self.mediaId = mediaId; self.positionMs = max(0, positionMs)
        self.durationMs = durationMs; self.updatedAtEpochMs = updatedAtEpochMs
    }
    public func resume(minimumPosition: Int64 = 10_000, minimumRemaining: Int64 = 30_000) -> Int64? {
        guard positionMs >= minimumPosition,
              durationMs.map({ $0 - positionMs >= minimumRemaining }) ?? true else { return nil }
        return positionMs
    }
    public var watchState: String {
        if positionMs > 0, let durationMs, durationMs - positionMs <= 30_000 { return "WATCHED" }
        return resume() == nil ? "NEW" : "IN_PROGRESS"
    }
}

public struct ServerStatus: Decodable, Sendable {
    public var appName: String
    public var apiVersion: Int
    public var mediaStreaming: Bool?
    public var scanning: Bool?
    public var scanFilesSeen: Int64?
    public var scanError: String?
}

public struct Connection: Codable, Identifiable, Equatable, Sendable {
    public var id: String { baseURL }
    public var name: String
    public var baseURL: String
    public init(name: String, baseURL: String) throws {
        let input = baseURL.trimmingCharacters(in: .whitespacesAndNewlines)
        let components = URLComponents(string: input.contains("://") ? input : "http://" + input)
        guard let parts = components, ["http", "https"].contains(parts.scheme?.lowercased() ?? ""),
              let host = parts.host, !host.isEmpty, parts.user == nil, parts.password == nil,
              parts.query == nil, parts.fragment == nil, parts.path.isEmpty || parts.path == "/",
              parts.port.map({ (1...65535).contains($0) }) ?? true,
              let url = parts.url else { throw ClientError.invalidAddress }
        self.baseURL = url.absoluteString.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        self.name = name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? host : name
    }
    public func url(path: String) throws -> URL {
        guard path.hasPrefix("/"), !path.hasPrefix("//"),
              let url = URL(string: baseURL + path), let base = URL(string: baseURL),
              url.host == base.host, url.scheme == base.scheme, url.port == base.port else {
            throw ClientError.invalidAddress
        }
        return url
    }
}

public enum ClientError: Error, LocalizedError {
    case invalidAddress, incompatibleServer, http(Int), invalidDownload, missingFile
    public var errorDescription: String? {
        switch self {
        case .invalidAddress: return NSLocalizedString("Invalid server address", comment: "")
        case .incompatibleServer: return NSLocalizedString("Unsupported server version", comment: "")
        case .http(let code): return String(format: NSLocalizedString("Server returned HTTP %d", comment: ""), code)
        case .invalidDownload: return NSLocalizedString("Incomplete download", comment: "")
        case .missingFile: return NSLocalizedString("Downloaded file is missing", comment: "")
        }
    }
}

/// Preserves the exact server preview, including optional fields, for confirmed tracking writes.
public enum JSONValue: Codable, Equatable, Sendable {
    case object([String: JSONValue]), array([JSONValue]), string(String), integer(Int64), number(Double), bool(Bool), null
    public init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if c.decodeNil() { self = .null }
        else if let v = try? c.decode(Bool.self) { self = .bool(v) }
        else if let v = try? c.decode(Int64.self) { self = .integer(v) }
        else if let v = try? c.decode(Double.self) { self = .number(v) }
        else if let v = try? c.decode(String.self) { self = .string(v) }
        else if let v = try? c.decode([JSONValue].self) { self = .array(v) }
        else { self = .object(try c.decode([String: JSONValue].self)) }
    }
    public func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        switch self {
        case .object(let v): try c.encode(v)
        case .array(let v): try c.encode(v)
        case .string(let v): try c.encode(v)
        case .integer(let v): try c.encode(v)
        case .number(let v): try c.encode(v)
        case .bool(let v): try c.encode(v)
        case .null: try c.encodeNil()
        }
    }
    public subscript(key: String) -> JSONValue { if case .object(let v) = self { return v[key] ?? .null }; return .null }
    public var array: [JSONValue] { if case .array(let v) = self { return v }; return [] }
    public var text: String { if case .string(let v) = self { return v }; return "" }
    public var integer: Int64? { if case .integer(let v) = self { return v }; return nil }
}
