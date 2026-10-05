import SwiftUI
import DanmakuCore

struct HomeScreen: View {
    @ObservedObject var model: AppModel
    var body: some View {
        List {
            Section {
                Label(LocalizedStringKey(model.online ? "Connected" : "Offline"), systemImage: model.online ? "network" : "wifi.slash")
                if let connection = model.connection { Text(connection.name).foregroundStyle(.secondary) }
                else { Text("Open Connect to find your library server.") }
                if model.loading { ProgressView("Connecting…") }
            }
            let continuing = model.continuing
            if !continuing.isEmpty {
                Section("Continue watching") { ForEach(continuing, id: \.item.id) { EpisodeRow(model: model, item: $0.item) } }
            }
            Section("Next up") { ForEach(model.nextUp, id: \.item.id) { EpisodeRow(model: model, item: $0.item) } }
            Section("Recently added") {
                ForEach(model.library.recent) { EpisodeRow(model: model, item: $0) }
            }
        }.navigationTitle("Home").refreshable { await model.reconnect() }
    }
}

struct LibraryScreen: View {
    @ObservedObject var model: AppModel
    @State private var query = ""
    @State private var filter = "All"
    @State private var selectedID: String?
    @State private var groups: [Series] = []
    private struct FilterRequest: Hashable {
        var query: String; var filter: String
        var library: UInt64; var progress: UInt64; var favorites: Set<String>
    }
    private var request: FilterRequest {
        FilterRequest(query: query, filter: filter, library: model.libraryRevision,
                      progress: model.progressRevision, favorites: model.state.favorites[model.connection?.id ?? ""] ?? [])
    }
    var body: some View {
        GeometryReader { geometry in
            VStack(spacing: 0) {
                Picker("Filter", selection: $filter) { ForEach(["All", "Favorites", "Unwatched", "In progress", "Watched"], id: \.self) { Text(LocalizedStringKey($0)).tag($0) } }
                    .pickerStyle(.menu).padding(.horizontal)
                if geometry.size.width >= 700 {
                    HStack(spacing: 0) {
                        List(groups, selection: $selectedID) { group in seriesLabel(group).tag(group.id) }.frame(width: 280)
                        Divider()
                        if let group = groups.first(where: { $0.id == selectedID }) { SeriesDetail(model: model, series: group) }
                        else { ContentUnavailableView("Select a series", systemImage: "rectangle.stack") }
                    }
                } else {
                    List(groups) { group in NavigationLink { SeriesDetail(model: model, series: group).navigationTitle(group.title) } label: { seriesLabel(group) } }
                }
            }
            .overlay { if groups.isEmpty { ContentUnavailableView("No matching episodes", systemImage: "magnifyingglass", description: Text("Connect to a library or adjust the filters.")) } }
        }.navigationTitle("Library").searchable(text: $query)
        .task(id: request) {
            let request = request; let index = model.library; let progress = model.progressByID
            if !request.query.isEmpty { try? await Task.sleep(for: .milliseconds(150)) }
            guard !Task.isCancelled else { return }
            let result = await Task.detached(priority: .userInitiated) {
                index.filtered(query: request.query, filter: request.filter, progress: progress, favorites: request.favorites)
            }.value
            guard !Task.isCancelled else { return }
            groups = result
        }
        .toolbar { Button("Refresh", systemImage: "arrow.clockwise") { Task { await model.reconnect() } }.disabled(model.loading) }
    }
    private func seriesLabel(_ group: Series) -> some View {
        HStack {
            if let item = group.seasons.first?.items.first { Poster(item: item, connection: model.connection, local: model.cachedPoster(item)) }
            VStack(alignment: .leading) {
                Text(group.title).font(.headline)
                Text("\(group.episodeCount) episodes").font(.caption).foregroundStyle(.secondary)
            }
        }
    }
}

struct SeriesDetail: View {
    @ObservedObject var model: AppModel
    var series: Series
    @State private var confirmDownload = false
    var body: some View {
        List {
            Section {
                Text(series.title).font(.title2.bold())
                Button("Download series", systemImage: "arrow.down.circle") { confirmDownload = true }.disabled(!model.online)
            }
            ForEach(series.seasons) { season in
                Section(season.sortKey == Int(Int32.max) ? NSLocalizedString("Season unknown", comment: "") : String(format: NSLocalizedString("Season %d", comment: ""), season.sortKey)) {
                    ForEach(season.items) { EpisodeRow(model: model, item: $0) }
                }
            }
        }
        .confirmationDialog("Download these episodes?", isPresented: $confirmDownload, titleVisibility: .visible) {
            Button("Download") { model.download(series.items) }
        } message: { Text(ByteCountFormatter.string(fromByteCount: series.items.map(\.sizeBytes).reduce(0, +), countStyle: .file)) }
    }
}

struct FolderScreen: View {
    @ObservedObject var model: AppModel
    @State private var path: [String] = []
    @State private var confirmDownload = false
    private var listing: FolderListing { model.library.folder(path) }
    var body: some View {
        List {
            if !path.isEmpty { Button("Up one folder", systemImage: "arrow.up") { path.removeLast() } }
            if model.scanning { ProgressView("Scanning…"); if let count = model.scanCount { Text("\(count) files found") } }
            ForEach(listing.folders, id: \.self) { folder in Button { path.append(folder) } label: { Label(folder, systemImage: "folder") } }
            ForEach(listing.files) { EpisodeRow(model: model, item: $0) }
        }.navigationTitle(path.last ?? NSLocalizedString("Folders", comment: ""))
        .toolbar {
            Button("Refresh folder", systemImage: "arrow.clockwise") { model.rescan(path) }.disabled(!model.online || model.scanning)
            Button("Download folder", systemImage: "arrow.down.circle") { confirmDownload = true }.disabled(!model.online || listing.descendants.isEmpty)
        }
        .confirmationDialog("Download this folder snapshot?", isPresented: $confirmDownload, titleVisibility: .visible) {
            Button("Download") { model.download(listing.descendants) }
        } message: { Text("Includes files in subfolders. New files are not downloaded automatically.") }
        .onChange(of: model.connection?.id) { _, _ in path = [] }
    }
}

struct EpisodeDetail: View {
    @ObservedObject var model: AppModel
    var item: MediaItem
    var body: some View {
        Form {
            Section {
                HStack(spacing: 16) {
                    Poster(item: item, connection: model.connection, local: model.cachedPoster(item))
                    VStack(alignment: .leading, spacing: 8) {
                        Text(item.episodeTitle).font(.headline)
                        Text(item.title).foregroundStyle(.secondary)
                    }
                }
                Button("Play", systemImage: "play.fill") { model.play(item) }
                Button(LocalizedStringKey(model.isFavorite(item) ? "Remove favorite" : "Favorite"), systemImage: "star") { model.favorite(item) }
                Button("Download", systemImage: "arrow.down.circle") { model.download([item]) }.disabled(!model.online)
            }
            Section("File details") {
                LabeledContent("Location", value: item.relativePath)
                if let root = item.rootLabel { LabeledContent("Library root", value: root) }
                LabeledContent("File size", value: ByteCountFormatter.string(fromByteCount: item.sizeBytes, countStyle: .file))
                LabeledContent("Format", value: item.mediaType)
                if let row = model.progressByID[item.id] {
                    LabeledContent("Playback position", value: timeLabel(row.positionMs))
                    if let duration = row.durationMs { LabeledContent("Duration", value: timeLabel(duration)) }
                }
            }
            if let metadata = item.animeMetadata {
                Section("Anime information") {
                    Text(metadata.primaryTitle)
                    ForEach(metadata.alternateNames ?? [], id: \.self) { Text($0) }
                    LabeledContent(metadata.animeId.provider, value: String(metadata.animeId.value))
                }
            }
            Section("Sidecar subtitles") {
                if (item.subtitles ?? []).isEmpty { Text("No sidecar subtitles") }
                ForEach(item.subtitles ?? []) { subtitle in
                    VStack(alignment: .leading) { Text(subtitle.label); Text(subtitle.relativePath).font(.caption).foregroundStyle(.secondary) }
                }
            }
        }.navigationTitle("Episode details")
    }
}
