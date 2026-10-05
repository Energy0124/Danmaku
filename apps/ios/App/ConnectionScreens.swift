import SwiftUI
import DanmakuCore

struct TrackingPreview: Identifiable {
    let id = UUID()
    var server: String
    var rows: [JSONValue]
    var updates: [JSONValue] { rows.map { $0["update"] } }
}

struct ConnectScreen: View {
    @ObservedObject var model: AppModel
    @ObservedObject var discovery: Discovery
    @State private var address = ""
    @State private var name = ""
    @State private var preview: TrackingPreview?
    var body: some View {
        Form {
            Section("Library server") {
                if let connection = model.connection {
                    Label(connection.name, systemImage: model.online ? "checkmark.circle.fill" : "wifi.slash")
                    Text(connection.baseURL).font(.caption).textSelection(.enabled)
                }
                TextField("Name (optional)", text: $name)
                TextField("http://PC-address:8686", text: $address).textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.URL)
                Button("Connect") {
                    do { let target = try Connection(name: name, baseURL: address); Task { await model.connect(target) } }
                    catch { model.error = error.localizedDescription }
                }.disabled(model.loading || address.isEmpty)
                if model.loading { ProgressView("Connecting…") }
                Text("Connect only to a library server on your trusted local network.").font(.caption).foregroundStyle(.secondary)
            }
            Section("Find servers") {
                Button(LocalizedStringKey(discovery.searching ? "Stop discovery" : "Discover servers"), systemImage: "network") {
                    if discovery.searching { discovery.stop() } else { discovery.start() }
                }
                ForEach(discovery.servers) { server in Button(server.name) { Task { await model.connect(server) } } }
                if let error = discovery.error { Text(error).foregroundStyle(.secondary) }
            }
            Section("Saved connections") {
                ForEach(model.state.connections) { connection in
                    HStack {
                        Button(connection.name) { Task { await model.connect(connection) } }
                        Spacer()
                        Menu {
                            Button("Edit") { address = connection.baseURL; name = connection.name }
                            Button("Forget", role: .destructive) { model.forget(connection) }
                        } label: { Image(systemName: "ellipsis") }
                    }
                }
            }
            Section("Accounts & tracking") {
                Button("Refresh tracking") { Task { await model.loadTracking() } }.disabled(!model.online || model.trackingBusy)
                Button("Read provider progress") { Task { await model.loadTracking(readback: true) } }.disabled(!model.online || model.trackingBusy)
                if model.trackingBusy { ProgressView() }
                ForEach([("myAnimeList", "MyAnimeList"), ("bangumi", "Bangumi")], id: \.0) { key, label in
                    LabeledContent(label, value: accountLabel(model.accounts[key]))
                }
                if let target = model.connection, let url = try? target.url(path: "/web/") { Link("Open server administration", destination: url) }
                Text("Connect accounts, map series, and resolve conflicts in server administration.").font(.caption).foregroundStyle(.secondary)
                let rows = model.tracking["plan"]["updates"].array
                ForEach(Array(rows.enumerated()), id: \.offset) { _, row in TrackingUpdateRow(row: row) }
                Button("Review and sync", systemImage: "checkmark.circle") {
                    if let server = model.connection?.id { preview = TrackingPreview(server: server, rows: rows) }
                }.disabled(rows.isEmpty || !model.online || model.trackingBusy)
                let conflicts = model.tracking["plan"]["conflicts"].array
                let mappingConflicts = model.tracking["plan"]["mappingConflicts"].array
                if !conflicts.isEmpty || !mappingConflicts.isEmpty {
                    Text("Provider or mapping conflicts need review in server administration.").foregroundStyle(.orange)
                    ForEach(Array(conflicts.enumerated()), id: \.offset) { _, row in
                        Text(row["seriesTitle"].text + " · " + String(format: NSLocalizedString("Provider watched: %lld", comment: ""), row["externalEntry"]["watchedEpisodes"].integer ?? 0))
                    }
                }
                let failures = model.tracking["plan"]["failures"].array
                if !failures.isEmpty { Text("Provider requests failed. Review server administration before retrying.").foregroundStyle(.orange) }
                if let result = model.trackingResult { Text(result) }
            }
            Section("About") {
                Text("Danmaku · 0.1.0")
                NavigationLink("Licenses") { LicenseScreen() }
            }
        }.navigationTitle("Connect").onDisappear { discovery.stop() }
        .sheet(item: $preview) { preview in
            NavigationStack {
                List {
                    Section { Text("Confirm the exact progress changes below. Provider progress ahead of your library is never overwritten.") }
                    ForEach(Array(preview.rows.enumerated()), id: \.offset) { _, row in TrackingUpdateRow(row: row) }
                }.navigationTitle("Review updates")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("Cancel") { self.preview = nil } }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Confirm and sync") {
                            self.preview = nil
                            Task { await model.syncTracking(preview.updates, server: preview.server) }
                        }.disabled(model.trackingBusy || model.connection?.id != preview.server || !model.online)
                    }
                }
            }
        }
    }
    private func accountLabel(_ account: JSONValue) -> String {
        let state = account["state"].text
        let label: String
        switch state {
        case "CONNECTED": label = NSLocalizedString("Connected", comment: "")
        case "NEEDS_RECONNECT": label = NSLocalizedString("Reconnect required", comment: "")
        case "DISCONNECTED": label = NSLocalizedString("Disconnected", comment: "")
        default: label = NSLocalizedString("Unavailable", comment: "")
        }
        let name = account["displayName"].text
        return name.isEmpty ? label : label + " · " + name
    }
}

struct TrackingUpdateRow: View {
    var row: JSONValue
    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(row["seriesTitle"].text).font(.headline)
            Text(row["update"]["animeId"]["provider"].text)
            Text(String(format: NSLocalizedString("Watched: %lld episodes", comment: ""), row["update"]["watchedEpisodes"].integer ?? 0))
            Text(LocalizedStringKey(statusLabel(row["update"]["status"].text))).font(.caption)
            if let score = row["update"]["score"].integer { Text(String(format: NSLocalizedString("Score: %lld", comment: ""), score)) }
        }
    }
    private func statusLabel(_ status: String) -> String {
        switch status {
        case "COMPLETED": return "Completed"
        case "WATCHING": return "Watching"
        case "ON_HOLD": return "On hold"
        case "DROPPED": return "Dropped"
        default: return "Plan to watch"
        }
    }
}

struct LicenseScreen: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Danmaku · MIT").font(.headline)
                ForEach(["LICENSE", "THIRD_PARTY_NOTICES", "VLCKit-COPYING"], id: \.self) { name in
                    if let url = Bundle.main.url(forResource: name, withExtension: "txt"), let text = try? String(contentsOf: url, encoding: .utf8) { Text(text).font(.caption).textSelection(.enabled) }
                }
                Link("VLCKit source code", destination: URL(string: "https://code.videolan.org/videolan/VLCKit/-/tree/3.7.2")!)
            }.padding()
        }.navigationTitle("Licenses")
    }
}

struct DownloadsScreen: View {
    @ObservedObject var model: AppModel
    @ObservedObject var downloads: Downloads
    @State private var clear = false
    var body: some View {
        List {
            if downloads.entries.isEmpty { ContentUnavailableView("No downloads", systemImage: "arrow.down.circle", description: Text("Download episodes from your library for offline playback.")) }
            ForEach(downloads.entries) { entry in
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Poster(item: entry.item, connection: nil, local: entry.assets.first { $0.id == "poster" && $0.complete }.map { entry.file(root: downloads.root, asset: $0) })
                        VStack(alignment: .leading) {
                            Text(entry.item.episodeTitle).font(.headline)
                            Text(entry.item.title).font(.subheadline)
                            Text(entry.connection.name).font(.caption).foregroundStyle(.secondary)
                            Text(LocalizedStringKey(entry.state.rawValue.capitalized)).font(.caption)
                        }
                    }
                    if entry.state == .downloading {
                        ProgressView(value: Double(entry.receivedBytes), total: Double(max(1, entry.expectedBytes)))
                    }
                    if let error = entry.error { Text(error).font(.caption).foregroundStyle(.red) }
                    HStack {
                        if entry.state == .ready { Button("Play", systemImage: "play.fill") { model.playCached(entry) } }
                        if entry.state == .downloading || entry.state == .queued { Button("Pause") { downloads.pause(entry.id) } }
                        if entry.state == .paused || entry.state == .failed || entry.state == .cancelled { Button("Retry") { downloads.resume(entry.id) } }
                        if entry.state == .downloading || entry.state == .queued || entry.state == .paused {
                            Button("Cancel") { downloads.cancel(entry.id) }.disabled(downloads.playingEntryID == entry.id)
                        }
                        Spacer()
                        Button("Delete", role: .destructive) { downloads.delete(entry.id) }.disabled(downloads.playingEntryID == entry.id)
                    }.buttonStyle(.borderless)
                }.padding(.vertical, 6)
            }
            Section { Text("Downloads use Wi-Fi. iOS schedules background transfers; force-quitting pauses them until the app is opened again.").font(.caption).foregroundStyle(.secondary) }
        }.navigationTitle("Downloads")
        .toolbar { Button("Clear downloads", systemImage: "trash") { clear = true }.disabled(downloads.entries.isEmpty) }
        .confirmationDialog("Delete all downloads?", isPresented: $clear, titleVisibility: .visible) {
            Button("Clear downloads", role: .destructive) { downloads.clear() }
        } message: { Text("Playback progress is preserved. The currently playing download is kept.") }
    }
}
