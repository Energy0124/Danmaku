import Foundation
import Network
import Combine
import Darwin
import DanmakuCore

final class Discovery: ObservableObject {
    @Published private(set) var servers: [Connection] = []
    @Published private(set) var searching = false
    @Published var error: String?
    private var browser: NWBrowser?
    private var resolved: [NWEndpoint: Connection] = [:]
    private var resolvers: [NWEndpoint: NWConnection] = [:]

    func start() {
        stop(); servers = []; error = nil; searching = true
        let browser = NWBrowser(for: .bonjour(type: "_danmaku._tcp", domain: nil), using: .tcp)
        self.browser = browser
        browser.stateUpdateHandler = { [weak self, weak browser] state in
            guard let self, self.browser === browser else { return }
            switch state {
            case .waiting:
                self.error = NSLocalizedString("Discovery unavailable. Check Local Network access in Settings or enter an address.", comment: "")
            case .failed:
                self.error = NSLocalizedString("Discovery unavailable. Check Local Network access in Settings or enter an address.", comment: ""); self.searching = false
            case .ready: self.error = nil
            default: break
            }
        }
        browser.browseResultsChangedHandler = { [weak self, weak browser] results, _ in
            guard let self, self.browser === browser else { return }
            let endpoints = Set(results.map(\.endpoint))
            for (endpoint, resolver) in self.resolvers where !endpoints.contains(endpoint) { resolver.cancel(); self.resolvers.removeValue(forKey: endpoint); self.resolved.removeValue(forKey: endpoint) }
            self.updateServers()
            for result in results where self.resolvers[result.endpoint] == nil { self.resolve(result.endpoint) }
        }
        browser.start(queue: .main)
    }
    private func resolve(_ endpoint: NWEndpoint) {
        let resolver = NWConnection(to: endpoint, using: .tcp)
        resolvers[endpoint] = resolver
        resolver.stateUpdateHandler = { [weak self, weak resolver] state in
            guard let self, let resolver, self.resolvers[endpoint] === resolver else { return }
            if case .ready = state, let endpointAddress = resolver.currentPath?.remoteEndpoint {
                let name: String
                if case .service(let service, _, _, _) = endpoint { name = service } else { name = "Danmaku" }
                do {
                    self.resolved[endpoint] = try Self.connection(name: name, endpoint: endpointAddress)
                    self.error = nil; self.updateServers()
                } catch {
                    self.error = NSLocalizedString("Discovery unavailable. Check Local Network access in Settings or enter an address.", comment: "")
                }
                resolver.cancel()
            } else if case .waiting = state {
                self.error = NSLocalizedString("Discovery unavailable. Check Local Network access in Settings or enter an address.", comment: "")
            } else if case .failed = state {
                self.resolvers.removeValue(forKey: endpoint)
                self.error = NSLocalizedString("Discovery unavailable. Check Local Network access in Settings or enter an address.", comment: "")
            }
        }
        resolver.start(queue: .main)
    }
    /// Network's debug descriptions include interface scopes, even for IPv4.
    /// Format the address bytes for HTTP instead of interpolating that description.
    static func connection(name: String, endpoint: NWEndpoint) throws -> Connection {
        guard case .hostPort(let host, let port) = endpoint else { throw ClientError.invalidAddress }
        let address: String
        switch host {
        case .ipv4(let ip):
            address = ip.rawValue.map { String($0) }.joined(separator: ".")
        case .ipv6(let ip):
            var buffer = [CChar](repeating: 0, count: Int(INET6_ADDRSTRLEN))
            let converted = ip.rawValue.withUnsafeBytes { bytes in
                inet_ntop(AF_INET6, bytes.baseAddress, &buffer, socklen_t(buffer.count))
            }
            guard converted != nil else { throw ClientError.invalidAddress }
            let scope = ip.isLinkLocal ? ip.interface.map { "%25" + $0.name } ?? "" : ""
            address = "[" + String(cString: buffer) + scope + "]"
        case .name(let hostname, _): address = hostname
        @unknown default: throw ClientError.invalidAddress
        }
        return try Connection(name: name, baseURL: "http://\(address):\(port.rawValue)")
    }
    private func updateServers() {
        servers = Dictionary(resolved.values.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
            .values.sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
    }
    func stop() {
        browser?.cancel(); browser = nil
        resolvers.values.forEach { $0.cancel() }; resolvers.removeAll(); resolved.removeAll(); searching = false
    }
    deinit { browser?.cancel(); resolvers.values.forEach { $0.cancel() } }
}
