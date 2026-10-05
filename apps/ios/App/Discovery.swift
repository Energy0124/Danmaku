import Foundation
import Network
import Combine
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
            if case .failed = state { self.error = NSLocalizedString("Discovery unavailable. Check Local Network access in Settings or enter an address.", comment: ""); self.searching = false }
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
            if case .ready = state, let resolved = resolver.currentPath?.remoteEndpoint,
               case .hostPort(let host, let port) = resolved {
                let name: String
                if case .service(let service, _, _, _) = endpoint { name = service } else { name = "Danmaku" }
                let hostText = "\(host)"
                let address = hostText.contains(":") ? "[\(hostText)]" : hostText
                if let connection = try? Connection(name: name, baseURL: "http://\(address):\(port.rawValue)") {
                    self.resolved[endpoint] = connection; self.updateServers()
                }
                resolver.cancel()
            }
        }
        resolver.start(queue: .main)
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
