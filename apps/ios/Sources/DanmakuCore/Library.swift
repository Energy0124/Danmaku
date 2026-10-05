import Foundation

public struct Season: Identifiable, Sendable {
    public var id: String; public var label: String; public var sortKey: Int; public var items: [MediaItem]
}
public struct Series: Identifiable, Sendable {
    public var id: String; public var title: String; public var seasons: [Season]
    public var items: [MediaItem] { seasons.flatMap(\.items) }
    public var episodeCount: Int { seasons.reduce(0) { $0 + $1.items.count } }
}
public struct NextUp: Sendable {
    public var item: MediaItem; public var reason: String; public var progress: PlaybackProgress?; public var source: PlaybackProgress?
}
public enum LibraryPolicy {
    public static func latest(_ rows: [PlaybackProgress]) -> [String: PlaybackProgress] {
        rows.reduce(into: [:]) { result, row in
            if result[row.mediaId].map({ $0.updatedAtEpochMs < row.updatedAtEpochMs }) ?? true { result[row.mediaId] = row }
        }
    }
    private static let seasonPatterns = [#"\bseason\s*([0-9]{1,2})\b"#, #"\bs([0-9]{1,2})\b"#].map { try! NSRegularExpression(pattern: $0, options: .caseInsensitive) }
    private static let episodePatterns = [#"\bepisode\s*([0-9]{1,4})\b"#, #"\bep\s*([0-9]{1,4})\b"#, #"\be([0-9]{1,4})\b"#].map { try! NSRegularExpression(pattern: $0, options: .caseInsensitive) }
    private static func number(_ text: String, patterns: [NSRegularExpression]) -> Int? {
        for regex in patterns {
            if let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)),
               let range = Range(match.range(at: 1), in: text), let value = Int(text[range]) { return value }
        }
        return nil
    }
    private struct OrderedItem {
        let item: MediaItem
        let season: Int
        let episode: Int
        let title: String
        let path: String
        init(_ item: MediaItem) {
            self.item = item
            season = number(item.relativePath + " " + item.episodeTitle, patterns: seasonPatterns) ?? Int(Int32.max)
            episode = number(item.episodeTitle + " " + item.relativePath, patterns: episodePatterns) ?? Int.max
            title = item.episodeTitle.lowercased(); path = item.relativePath.lowercased()
        }
    }
    public static func grouped(_ items: [MediaItem]) -> [Series] {
        Dictionary(grouping: items, by: \.seriesID).map { id, items in
            let counts = Dictionary(grouping: items, by: \.title).mapValues(\.count)
            let title = counts.keys.sorted {
                if counts[$0] != counts[$1] { return counts[$0]! > counts[$1]! }
                return $0.count == $1.count ? $0 < $1 : $0.count < $1.count
            }.first ?? "Series"
            let seasons = Dictionary(grouping: items.map(OrderedItem.init), by: \.season).map { key, rows in
                Season(id: id + (key == Int(Int32.max) ? "-season-unknown" : String(format: "-season-%02d", key)),
                       label: key == Int(Int32.max) ? "Season unknown" : "Season \(key)", sortKey: key,
                       items: rows.sorted { a, b in
                           if a.episode != b.episode { return a.episode < b.episode }
                           if a.title != b.title { return a.title < b.title }
                           return a.path < b.path
                       }.map(\.item))
            }.sorted { $0.sortKey < $1.sortKey }
            return Series(id: id, title: title, seasons: seasons)
        }.sorted { a, b in
            if a.episodeCount != b.episodeCount { return a.episodeCount > b.episodeCount }
            if a.title.lowercased() != b.title.lowercased() { return a.title.lowercased() < b.title.lowercased() }
            return a.id < b.id
        }
    }
    public static func continuing(_ catalog: Catalog, progress: [PlaybackProgress], limit: Int = 8) -> [NextUp] {
        let newest = latest(progress)
        return catalog.items.compactMap { item -> NextUp? in
            guard let row = newest[item.id], row.resume() != nil else { return nil }
            return NextUp(item: item, reason: "RESUME", progress: row, source: row)
        }.sorted { $0.progress!.updatedAtEpochMs > $1.progress!.updatedAtEpochMs }.prefix(max(0, limit)).map { $0 }
    }
    public static func nextUp(_ catalog: Catalog, progress: [PlaybackProgress], limit: Int = 8) -> [NextUp] {
        guard limit > 0, let first = catalog.items.first else { return [] }
        let newest = latest(progress)
        let indexes = Dictionary(catalog.items.enumerated().map { ($0.element.id, $0.offset) }, uniquingKeysWith: { first, _ in first })
        var seen = Set<String>(); var candidates = [NextUp]()
        for row in progress.sorted(by: { $0.updatedAtEpochMs > $1.updatedAtEpochMs }) {
            guard let index = indexes[row.mediaId] else { continue }
            var candidate: NextUp?
            if row.resume() != nil { candidate = NextUp(item: catalog.items[index], reason: "RESUME", progress: row, source: row) }
            else if let duration = row.durationMs, row.positionMs >= 10_000, duration - row.positionMs < 30_000,
                    index + 1 < catalog.items.count, newest[catalog.items[index + 1].id] == nil {
                candidate = NextUp(item: catalog.items[index + 1], reason: "NEXT_EPISODE", progress: nil, source: row)
            }
            if let candidate, seen.insert(candidate.item.id).inserted {
                candidates.append(candidate)
                if candidates.count == limit { break }
            }
        }
        if candidates.isEmpty { candidates = [NextUp(item: first, reason: "START", progress: nil, source: nil)] }
        return Array(candidates.prefix(limit))
    }
    public static func folderComponents(_ item: MediaItem, multiRoot: Bool) -> [String] {
        let relative = item.relativePath.replacingOccurrences(of: "\\", with: "/").split(separator: "/").map(String.init)
        return (multiRoot ? [item.rootLabel ?? "Library"] : []) + relative
    }
    public static func descendants(_ items: [MediaItem], path: [String]) -> [MediaItem] {
        let multi = Set(items.compactMap(\.rootLabel)).count > 1
        return items.filter { Array(folderComponents($0, multiRoot: multi).dropLast().prefix(path.count)) == path }
    }
}

public struct FolderListing: Sendable {
    public var folders: [String] = []
    public var files: [MediaItem] = []
    public var descendants: [MediaItem] = []
    public init() {}
}

/// Built once per catalog on a worker, then read cheaply by library views.
public struct LibraryIndex: Sendable {
    public let series: [Series]
    public let recent: [MediaItem]
    private let folders: [[String]: FolderListing]
    private let searchText: [String: String]
    public init(_ catalog: Catalog = Catalog()) {
        series = LibraryPolicy.grouped(catalog.items)
        recent = Array(catalog.items.sorted { ($0.indexedAtEpochMs ?? 0) > ($1.indexedAtEpochMs ?? 0) }.prefix(12))
        var listings: [[String]: FolderListing] = [:]
        var names: [[String]: Set<String>] = [:]
        var text: [String: String] = [:]
        let multiRoot = Set(catalog.items.compactMap(\.rootLabel)).count > 1
        for item in catalog.items {
            text[item.id] = [item.title, item.seriesTitle, item.episodeTitle, item.relativePath].joined(separator: " ")
            let components = LibraryPolicy.folderComponents(item, multiRoot: multiRoot)
            let parent = Array(components.dropLast())
            for depth in 0...parent.count {
                let path = Array(parent.prefix(depth))
                listings[path, default: FolderListing()].descendants.append(item)
                if depth < parent.count { names[path, default: []].insert(parent[depth]) }
                else { listings[path, default: FolderListing()].files.append(item) }
            }
        }
        for (path, children) in names { listings[path]?.folders = children.sorted { $0.localizedStandardCompare($1) == .orderedAscending } }
        folders = listings; searchText = text
    }
    public func folder(_ path: [String]) -> FolderListing { folders[path] ?? FolderListing() }
    public func filtered(query: String, filter: String, progress: [String: PlaybackProgress], favorites: Set<String>) -> [Series] {
        if query.isEmpty && filter == "All" { return series }
        return series.compactMap { group in
            let seasons = group.seasons.compactMap { season -> Season? in
                let items = season.items.filter { item in
                    guard query.isEmpty || searchText[item.id]?.localizedCaseInsensitiveContains(query) == true else { return false }
                    let state = progress[item.id]?.watchState ?? "NEW"
                    return filter == "All" || (filter == "Favorites" && favorites.contains(item.id)) ||
                        (filter == "Unwatched" && state == "NEW") || (filter == "In progress" && state == "IN_PROGRESS") ||
                        (filter == "Watched" && state == "WATCHED")
                }
                return items.isEmpty ? nil : Season(id: season.id, label: season.label, sortKey: season.sortKey, items: items)
            }
            return seasons.isEmpty ? nil : Series(id: group.id, title: group.title, seasons: seasons)
        }.sorted { a, b in
            if a.episodeCount != b.episodeCount { return a.episodeCount > b.episodeCount }
            if a.title.lowercased() != b.title.lowercased() { return a.title.lowercased() < b.title.lowercased() }
            return a.id < b.id
        }
    }
}

public struct LibrarySnapshot: Sendable {
    public let catalog: Catalog
    public let progress: [PlaybackProgress]
    public let progressByID: [String: PlaybackProgress]
    public let index: LibraryIndex
    public let continuing: [NextUp]
    public let nextUp: [NextUp]
    public init(catalog: Catalog = Catalog(), progress: [PlaybackProgress] = []) {
        self.catalog = catalog; self.progress = progress
        progressByID = LibraryPolicy.latest(progress)
        index = LibraryIndex(catalog)
        continuing = LibraryPolicy.continuing(catalog, progress: progress)
        nextUp = LibraryPolicy.nextUp(catalog, progress: progress)
    }
}
