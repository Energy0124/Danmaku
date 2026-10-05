import Foundation

public protocol LibraryTransport: Sendable {
    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse)
}
public struct URLSessionTransport: LibraryTransport {
    public let session: URLSession
    public init(session: URLSession = .shared) { self.session = session }
    public func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let (data, response) = try await session.data(for: request)
        guard let response = response as? HTTPURLResponse else { throw ClientError.http(0) }
        return (data, response)
    }
}
public struct LibraryClient: Sendable {
    private let transport: any LibraryTransport
    public init(transport: any LibraryTransport = URLSessionTransport()) { self.transport = transport }
    public func request<T: Decodable>(_ connection: Connection, path: String, method: String = "GET", body: Data? = nil) async throws -> T {
        let data = try await raw(connection, path: path, method: method, body: body)
        return try JSONDecoder().decode(T.self, from: data)
    }
    public func raw(_ connection: Connection, path: String, method: String = "GET", body: Data? = nil) async throws -> Data {
        var request = URLRequest(url: try connection.url(path: path), cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 30)
        request.httpMethod = method; request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if body != nil { request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type") }
        let (data, response) = try await transport.data(for: request)
        guard (200...299).contains(response.statusCode) else { throw ClientError.http(response.statusCode) }
        return data
    }
    public func connect(_ connection: Connection) async throws -> (Catalog, [PlaybackProgress]) {
        let status: ServerStatus = try await request(connection, path: "/api/server/status")
        guard status.apiVersion == 1, status.mediaStreaming != false else { throw ClientError.incompatibleServer }
        async let catalog: Catalog = request(connection, path: "/api/library")
        async let progress: [PlaybackProgress] = request(connection, path: "/api/progress")
        return try await (catalog, progress)
    }
    public func save(_ connection: Connection, progress: PlaybackProgress) async throws {
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-._~"))
        let id = progress.mediaId.addingPercentEncoding(withAllowedCharacters: allowed)!
        _ = try await raw(connection, path: "/api/progress/" + id, method: "PUT", body: JSONEncoder().encode(progress))
    }
    public func sync(_ connection: Connection, updates: [JSONValue]) async throws -> JSONValue {
        try await request(connection, path: "/api/providers/tracking/sync", method: "POST",
                          body: JSONEncoder().encode(JSONValue.object(["expectedUpdates": .array(updates)])))
    }
}

/// A response may only update the session that requested it.
public struct RequestGeneration: Sendable {
    public private(set) var value: UInt64 = 0
    public init() {}
    @discardableResult public mutating func advance() -> UInt64 { value &+= 1; return value }
    public func accepts(_ generation: UInt64) -> Bool { value == generation }
}
