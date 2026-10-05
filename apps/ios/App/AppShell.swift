import SwiftUI
import DanmakuCore

enum Destination: String, CaseIterable, Identifiable {
    case home = "Home", library = "Library", folders = "Folders", watch = "Watch", downloads = "Downloads", connect = "Connect"
    var id: String { rawValue }
    var symbol: String {
        switch self {
        case .home: return "house"
        case .library: return "rectangle.stack"
        case .folders: return "folder"
        case .watch: return "play.rectangle"
        case .downloads: return "arrow.down.circle"
        case .connect: return "network"
        }
    }
}

struct AppShell: View {
    @ObservedObject var model: AppModel
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State private var selected: Destination? = .home
    @State private var fullscreen = false
    var body: some View {
        Group {
            if fullscreen {
                WatchScreen(model: model, engine: model.player, fullscreen: $fullscreen)
                    .background(.black).preferredColorScheme(.dark)
            } else if sizeClass == .regular {
                NavigationSplitView {
                    List(Destination.allCases, selection: $selected) { destination in
                        Label(LocalizedStringKey(destination.rawValue), systemImage: destination.symbol).tag(destination)
                    }.navigationTitle("Danmaku")
                } detail: { NavigationStack { page(selected ?? .home) } }
                .navigationSplitViewStyle(.balanced)
            } else {
                TabView(selection: Binding(get: { selected ?? .home }, set: { selected = $0 })) {
                    ForEach(Destination.allCases) { destination in
                        NavigationStack { page(destination) }
                            .tabItem { Label(LocalizedStringKey(destination.rawValue), systemImage: destination.symbol) }.tag(destination)
                    }
                }
            }
        }
        .tint(.indigo)
        .alert("Unable to complete action", isPresented: Binding(get: { model.error != nil || model.downloads.error != nil }, set: { if !$0 { model.error = nil; model.downloads.error = nil } })) {
            Button("OK") { model.error = nil; model.downloads.error = nil }
        } message: { Text(model.error ?? model.downloads.error ?? "") }
        .onReceive(model.player.$loaded) { loaded in if loaded { selected = .watch } }
    }
    @ViewBuilder private func page(_ destination: Destination) -> some View {
        switch destination {
        case .home: HomeScreen(model: model)
        case .library: LibraryScreen(model: model)
        case .folders: FolderScreen(model: model)
        case .watch: WatchScreen(model: model, engine: model.player, fullscreen: $fullscreen)
        case .downloads: DownloadsScreen(model: model, downloads: model.downloads)
        case .connect: ConnectScreen(model: model, discovery: model.discovery)
        }
    }
}

struct Poster: View {
    var item: MediaItem
    var connection: Connection?
    var local: URL?
    var body: some View {
        Group {
            if let local, let image = UIImage(contentsOfFile: local.path) { Image(uiImage: image).resizable().scaledToFill() }
            else { AsyncImage(url: item.posterPath.flatMap { try? connection?.url(path: $0) }) { image in image.resizable().scaledToFill() } placeholder: { Image(systemName: "film").resizable().scaledToFit().padding(18).foregroundStyle(.secondary) } }
        }
        .frame(width: 56, height: 78).background(.quaternary).clipShape(RoundedRectangle(cornerRadius: 8))
        .accessibilityHidden(true)
    }
}

struct EpisodeRow: View {
    @ObservedObject var model: AppModel
    var item: MediaItem
    var body: some View {
        let progress = model.progressByID[item.id]
        HStack(spacing: 12) {
            Poster(item: item, connection: model.connection, local: model.cachedPoster(item))
            VStack(alignment: .leading, spacing: 5) {
                NavigationLink { EpisodeDetail(model: model, item: item) } label: { Text(item.episodeTitle).font(.headline) }
                Text(item.title).font(.subheadline).foregroundStyle(.secondary)
                Text(LocalizedStringKey(progress?.watchState == "WATCHED" ? "Watched" : progress?.watchState == "IN_PROGRESS" ? "In progress" : "Unwatched")).font(.caption)
                if let progress, let duration = progress.durationMs, duration > 0 {
                    ProgressView(value: min(1, Double(progress.positionMs) / Double(duration)))
                    Text(timeLabel(progress.positionMs)).font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                }
            }
            Spacer()
            Button { model.favorite(item) } label: { Image(systemName: model.isFavorite(item) ? "star.fill" : "star") }
                .buttonStyle(.borderless).accessibilityLabel("Favorite")
            Button { model.play(item) } label: { Image(systemName: "play.fill") }
                .buttonStyle(.borderless).accessibilityLabel("Play")
            Menu {
                Button("Download", systemImage: "arrow.down") { model.download([item]) }.disabled(!model.online)
            } label: { Image(systemName: "ellipsis") }.accessibilityLabel("Episode actions")
        }.padding(.vertical, 4)
    }
}

func timeLabel(_ milliseconds: Int64) -> String {
    let seconds = max(0, milliseconds / 1000)
    return seconds >= 3600 ? String(format: "%lld:%02lld:%02lld", seconds / 3600, (seconds / 60) % 60, seconds % 60) : String(format: "%lld:%02lld", seconds / 60, seconds % 60)
}
