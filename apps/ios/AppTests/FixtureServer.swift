import Foundation
import Network

/// Loopback-only HTTP fixture; requests and replies stay on the main queue.
final class FixtureServer {
    let listener: NWListener
    var handler: (String, String, Data) -> (Int, Data)
    var connections: [NWConnection] = []
    private(set) var ready = false
    var media: Data?
    var chunkDelay: TimeInterval = 0
    var etag: String?
    var lastModified = "Mon, 05 Oct 2026 00:00:00 GMT"
    private(set) var requests: [(path: String, headers: [String: String])] = []
    var baseURL: String { "http://127.0.0.1:\(listener.port!.rawValue)" }
    init(handler: @escaping (String, String, Data) -> (Int, Data)) throws {
        self.handler = handler
        let parameters = NWParameters.tcp
        parameters.requiredLocalEndpoint = .hostPort(host: "127.0.0.1", port: .any)
        listener = try NWListener(using: parameters)
        listener.newConnectionHandler = { [weak self] connection in
            guard let self else { return }
            self.connections.append(connection); connection.start(queue: .main)
            self.receive(connection, buffered: Data())
        }
        listener.stateUpdateHandler = { [weak self] state in
            if case .ready = state { self?.ready = (self?.listener.port?.rawValue ?? 0) > 0 }
        }
        listener.start(queue: .main)
    }
    private func receive(_ connection: NWConnection, buffered: Data) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] data, _, complete, error in
            guard let self, error == nil else { connection.cancel(); return }
            var bytes = buffered; if let data { bytes.append(data) }
            guard let boundary = bytes.range(of: Data("\r\n\r\n".utf8)),
                  let headers = String(data: bytes[..<boundary.lowerBound], encoding: .utf8) else {
                if !complete { self.receive(connection, buffered: bytes) }; return
            }
            let length = headers.components(separatedBy: "\r\n").first { $0.lowercased().hasPrefix("content-length:") }
                .flatMap { Int($0.split(separator: ":").last!.trimmingCharacters(in: .whitespaces)) } ?? 0
            let body = Data(bytes[boundary.upperBound...])
            guard body.count >= length else { self.receive(connection, buffered: bytes); return }
            let parts = headers.components(separatedBy: "\r\n")[0].split(separator: " ")
            guard parts.count >= 2 else { connection.cancel(); return }
            let path = String(parts[1])
            let fields = Dictionary(headers.components(separatedBy: "\r\n").dropFirst().compactMap { line -> (String, String)? in
                guard let colon = line.firstIndex(of: ":") else { return nil }
                return (String(line[..<colon]).lowercased(), line[line.index(after: colon)...].trimmingCharacters(in: .whitespaces))
            }, uniquingKeysWith: { _, latest in latest })
            self.requests.append((path, fields))
            var (status, response) = self.handler(String(parts[0]), path, body)
            var extra = ""
            if path == "/media/one", let media = self.media {
                status = 200; response = media
                if let range = fields["range"], (fields["if-range"] == nil || fields["if-range"] == (self.etag ?? self.lastModified)),
                   let start = Int(range.replacingOccurrences(of: "bytes=", with: "").split(separator: "-")[0]), start < media.count {
                    status = 206; response = Data(media[start...])
                    extra = "Content-Range: bytes \(start)-\(media.count - 1)/\(media.count)\r\n"
                }
            }
            if let etag = self.etag { extra += "ETag: \(etag)\r\n" }
            let responseHeaders = "HTTP/1.1 \(status) Fixture\r\nContent-Type: application/octet-stream\r\nContent-Length: \(response.count)\r\nAccept-Ranges: bytes\r\nLast-Modified: \(self.lastModified)\r\n" + extra + "Connection: close\r\n\r\n"
            connection.send(content: Data(responseHeaders.utf8), completion: .contentProcessed { _ in
                self.send(connection, data: parts[0] == "HEAD" ? Data() : response, offset: 0)
            })
        }
    }
    private func send(_ connection: NWConnection, data: Data, offset: Int) {
        guard offset < data.count else { connection.cancel(); return }
        let end = min(data.count, offset + 65536)
        connection.send(content: Data(data[offset..<end]), completion: .contentProcessed { [weak self] error in
            guard let self, error == nil else { connection.cancel(); return }
            if end == data.count { connection.cancel() }
            else { DispatchQueue.main.asyncAfter(deadline: .now() + self.chunkDelay) { self.send(connection, data: data, offset: end) } }
        })
    }
    func stop() { connections.forEach { $0.cancel() }; listener.cancel() }
    deinit { stop() }
}
