import Foundation
import Combine
import DanmakuCore

protocol DownloadManaging: AnyObject {
    var entries: [DownloadEntry] { get }
    func enqueue(_ items: [MediaItem], connection: Connection, client: LibraryClient) async
    func pause(_ id: String)
    func resume(_ id: String)
    func cancel(_ id: String)
    func delete(_ id: String)
    func clear()
}

/// All queue state and delegate callbacks run on the main queue. URLSession owns background transfer lifetimes.
final class Downloads: NSObject, ObservableObject, DownloadManaging, URLSessionDownloadDelegate {
    @Published private(set) var entries: [DownloadEntry] = []
    @Published var error: String?
    let root: URL
    private let manifest: URL
    private var session: URLSession!
    private var restoring = true
    private var storageHealthy = true
    private var activeTask: URLSessionDownloadTask?
    private var lastPersist = Date.distantPast
    var backgroundCompletion: (() -> Void)?
    var playingEntryID: String?

    init(root: URL, background: Bool = true) {
        self.root = root; manifest = root.appendingPathComponent("index.json")
        super.init()
        do {
            entries = try AtomicFile.read([DownloadEntry].self, at: manifest, default: [])
            try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
            var excluded = root; var values = URLResourceValues(); values.isExcludedFromBackup = true
            try excluded.setResourceValues(values)
        } catch { self.error = error.localizedDescription; storageHealthy = false }
        let config = background ? URLSessionConfiguration.background(withIdentifier: "app.danmaku.ios.downloads") : .ephemeral
        config.sessionSendsLaunchEvents = true; config.isDiscretionary = false
        config.httpMaximumConnectionsPerHost = 1; config.allowsCellularAccess = false
        session = URLSession(configuration: config, delegate: self, delegateQueue: .main)
        session.getAllTasks { tasks in
            DispatchQueue.main.async {
                for task in tasks {
                    guard self.location(task) != nil, self.activeTask == nil,
                          let download = task as? URLSessionDownloadTask else { task.cancel(); continue }
                    let index = self.location(task)!.0
                    if self.entries[index].state == .downloading {
                        self.activeTask = download; download.resume()
                    } else { task.cancel() }
                }
                for index in self.entries.indices where self.entries[index].state == .downloading {
                    if self.activeTask?.taskDescription?.hasPrefix(self.entries[index].id + ":") != true { self.entries[index].state = .queued }
                }
                for index in self.entries.indices where self.entries[index].state == .ready {
                    if (try? self.entries[index].video(root: self.root)) == nil {
                        self.entries[index].state = .failed; self.entries[index].error = NSLocalizedString("Downloaded file is missing", comment: "")
                        for asset in self.entries[index].assets.indices { self.entries[index].assets[asset].complete = false }
                    }
                }
                self.restoring = false; self.persist(); self.pump()
            }
        }
    }
    @discardableResult private func persist() -> Bool {
        guard storageHealthy else { return false }
        do { try AtomicFile.write(entries, to: manifest); lastPersist = Date(); return true }
        catch { self.error = error.localizedDescription; return false }
    }
    func enqueue(_ items: [MediaItem], connection: Connection, client: LibraryClient) async {
        for item in items {
            if entries.contains(where: { $0.connection.id == connection.id && $0.item.id == item.id && $0.state != .cancelled }) { continue }
            var entry = DownloadEntry(connection: connection, item: item)
            // Resolve comments before scheduling video so a completed cache includes its offline overlay.
            let path = "/api/danmaku/" + item.id.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed)!
            entry.danmaku = try? await client.request(connection, path: path)
            entries.append(entry)
            guard persist() else { entries.removeLast(); return }
            pump()
        }
    }
    private func pump() {
        guard !restoring, storageHealthy, activeTask == nil,
              let index = entries.firstIndex(where: { $0.state == .queued }) else { return }
        guard let assetIndex = entries[index].nextAsset else {
            entries[index].state = .ready; entries[index].resumeData = nil
            persist(); pump(); return
        }
        do {
            let entry = entries[index]; let asset = entry.assets[assetIndex]
            try FileManager.default.createDirectory(at: root.appendingPathComponent(entry.id), withIntermediateDirectories: true)
            var task: URLSessionDownloadTask
            if let resume = entry.resumeData { task = session.downloadTask(withResumeData: resume) }
            else { task = session.downloadTask(with: try entry.connection.url(path: asset.path)) }
            task.taskDescription = entry.id + ":\(assetIndex)"
            entries[index].state = .downloading; entries[index].error = nil
            guard persist() else { entries[index].state = .failed; task.cancel(); return }
            activeTask = task; task.resume()
        } catch { entries[index].state = .failed; entries[index].error = error.localizedDescription; persist(); pump() }
    }
    private func location(_ task: URLSessionTask) -> (Int, Int)? {
        guard let parts = task.taskDescription?.split(separator: ":"), parts.count == 2,
              let asset = Int(parts[1]), let index = entries.firstIndex(where: { $0.id == String(parts[0]) }),
              entries[index].assets.indices.contains(asset) else { return nil }
        return (index, asset)
    }
    func pause(_ id: String) {
        guard let index = entries.firstIndex(where: { $0.id == id }) else { return }
        entries[index].state = .paused; persist()
        guard let task = activeTask, task.taskDescription?.hasPrefix(id + ":") == true else { return }
        task.cancel { [weak self] resume in
            DispatchQueue.main.async {
                guard let self, let index = self.entries.firstIndex(where: { $0.id == id }), self.entries[index].state == .paused else { return }
                self.entries[index].resumeData = resume; self.persist()
            }
        }
    }
    func resume(_ id: String) {
        guard let index = entries.firstIndex(where: { $0.id == id }) else { return }
        entries[index].state = .queued; entries[index].error = nil; persist(); pump()
    }
    func cancel(_ id: String) {
        guard let index = entries.firstIndex(where: { $0.id == id }), id != playingEntryID else { return }
        entries[index].state = .cancelled; entries[index].resumeData = nil; persist()
        if activeTask?.taskDescription?.hasPrefix(id + ":") == true { activeTask?.cancel() }
        do { let directory = root.appendingPathComponent(id); if FileManager.default.fileExists(atPath: directory.path) { try FileManager.default.removeItem(at: directory) } }
        catch { self.error = error.localizedDescription }
        for asset in entries[index].assets.indices { entries[index].assets[asset].complete = false }
        persist(); pump()
    }
    func delete(_ id: String) {
        guard id != playingEntryID else { error = NSLocalizedString("Stop playback before deleting this download", comment: ""); return }
        cancel(id); entries.removeAll { $0.id == id }; persist()
    }
    func clear() { for entry in entries where entry.id != playingEntryID { delete(entry.id) } }
    /// Ends the session after callers have paused active work (used by isolated fixtures).
    func shutdown() { session.invalidateAndCancel() }
    func cached(_ item: MediaItem, connection: Connection) -> DownloadEntry? {
        entries.first { $0.item.id == item.id && $0.connection.id == connection.id && $0.state == .ready && (try? $0.video(root: root)) != nil }
    }
    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didWriteData bytesWritten: Int64, totalBytesWritten: Int64, totalBytesExpectedToWrite: Int64) {
        guard let (index, _) = location(downloadTask) else { return }
        entries[index].receivedBytes = totalBytesWritten; entries[index].expectedBytes = totalBytesExpectedToWrite
        if Date().timeIntervalSince(lastPersist) >= 2 { persist() }
    }
    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didFinishDownloadingTo location: URL) {
        guard let (index, assetIndex) = self.location(downloadTask), entries[index].state == .downloading else { return }
        do {
            guard let response = downloadTask.response as? HTTPURLResponse, (200...299).contains(response.statusCode) else { throw ClientError.http((downloadTask.response as? HTTPURLResponse)?.statusCode ?? 0) }
            let asset = entries[index].assets[assetIndex]
            if asset.required { try DownloadEntry.validateVideo(url: location, expectedBytes: entries[index].item.sizeBytes, response: response) }
            let destination = entries[index].file(root: root, asset: asset)
            if FileManager.default.fileExists(atPath: destination.path) { try FileManager.default.removeItem(at: destination) }
            try FileManager.default.moveItem(at: location, to: destination)
            entries[index].assets[assetIndex].complete = true
            entries[index].resumeData = nil; entries[index].receivedBytes = 0; entries[index].expectedBytes = 0
            persist()
        } catch {
            if entries[index].assets[assetIndex].required { entries[index].state = .failed }
            else { entries[index].assets[assetIndex].complete = true }
            entries[index].error = error.localizedDescription; persist()
        }
    }
    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        let isActive = activeTask?.taskIdentifier == task.taskIdentifier
        if isActive { activeTask = nil }
        guard let (index, assetIndex) = location(task) else { pump(); return }
        if let error = error as NSError?, entries[index].state == .downloading {
            entries[index].resumeData = error.userInfo[NSURLSessionDownloadTaskResumeData] as? Data
            if !entries[index].assets[assetIndex].required, error.code != NSURLErrorCancelled {
                // Optional assets may be unavailable; video remains usable.
                entries[index].assets[assetIndex].complete = true; entries[index].state = .queued
            } else { entries[index].state = .failed; entries[index].error = error.localizedDescription }
        } else if entries[index].state == .downloading {
            entries[index].state = entries[index].nextAsset == nil ? .ready : .queued
        }
        persist(); pump()
    }
    func urlSessionDidFinishEvents(forBackgroundURLSession session: URLSession) {
        let completion = backgroundCompletion; backgroundCompletion = nil
        DispatchQueue.main.async { completion?() }
    }
}
