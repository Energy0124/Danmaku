import Foundation

public struct Season: Identifiable, Sendable {
    public var id: String; public var label: String; public var sortKey: Int; public var items: [MediaItem]
}
public struct Series: Identifiable, Sendable {
    public var id: String; public var title: String; public var seasons: [Season]
    public var items: [MediaItem] { seasons.flatMap(\.items) }
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
    private static func number(_ text: String, patterns: [String]) -> Int? {
        for pattern in patterns {
            if let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive),
               let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)),
               let range = Range(match.range(at: 1), in: text), let value = Int(text[range]) { return value }
        }
        return nil
    }
    public static func grouped(_ items: [MediaItem]) -> [Series] {
        Dictionary(grouping: items, by: \.seriesID).map { id, items in
            let counts = Dictionary(grouping: items, by: \.title).mapValues(\.count)
            let title = counts.keys.sorted {
                if counts[$0] != counts[$1] { return counts[$0]! > counts[$1]! }
                return $0.count == $1.count ? $0 < $1 : $0.count < $1.count
            }.first ?? "Series"
            let seasons = Dictionary(grouping: items) { item in
                number(item.relativePath + " " + item.episodeTitle, patterns: [#"\bseason\s*([0-9]{1,2})\b"#, #"\bs([0-9]{1,2})\b"#]) ?? Int(Int32.max)
            }.map { key, rows in
                Season(id: id + (key == Int(Int32.max) ? "-season-unknown" : String(format: "-season-%02d", key)),
                       label: key == Int(Int32.max) ? "Season unknown" : "Season \(key)", sortKey: key,
                       items: rows.sorted { a, b in
                    let patterns = [#"\bepisode\s*([0-9]{1,4})\b"#, #"\bep\s*([0-9]{1,4})\b"#, #"\be([0-9]{1,4})\b"#]
                    let x = number(a.episodeTitle + " " + a.relativePath, patterns: patterns) ?? Int.max
                    let y = number(b.episodeTitle + " " + b.relativePath, patterns: patterns) ?? Int.max
                    if x != y { return x < y }
                    if a.episodeTitle.lowercased() != b.episodeTitle.lowercased() { return a.episodeTitle.lowercased() < b.episodeTitle.lowercased() }
                    return a.relativePath.lowercased() < b.relativePath.lowercased()
                })
            }.sorted { $0.sortKey < $1.sortKey }
            return Series(id: id, title: title, seasons: seasons)
        }.sorted { a, b in
            if a.items.count != b.items.count { return a.items.count > b.items.count }
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
        var seen = Set<String>(); var candidates = [NextUp]()
        for row in progress.sorted(by: { $0.updatedAtEpochMs > $1.updatedAtEpochMs }) {
            guard let index = catalog.items.firstIndex(where: { $0.id == row.mediaId }) else { continue }
            var candidate: NextUp?
            if row.resume() != nil { candidate = NextUp(item: catalog.items[index], reason: "RESUME", progress: row, source: row) }
            else if let duration = row.durationMs, row.positionMs >= 10_000, duration - row.positionMs < 30_000,
                    index + 1 < catalog.items.count, newest[catalog.items[index + 1].id] == nil {
                candidate = NextUp(item: catalog.items[index + 1], reason: "NEXT_EPISODE", progress: nil, source: row)
            }
            if let candidate, seen.insert(candidate.item.id).inserted { candidates.append(candidate) }
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
