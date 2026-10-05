import Foundation
import UIKit
import AVFAudio
import Combine
import DanmakuCore
import MobileVLCKit

struct PlayerTrack: Identifiable {
    var id: Int32
    var name: String
}
protocol PlaybackControlling: AnyObject {
    func toggle()
    func seek(_ milliseconds: Int64)
    func setRate(_ rate: Float)
    func stop()
}

final class PlaybackEngine: NSObject, ObservableObject, PlaybackControlling, VLCMediaPlayerDelegate {
    @Published private(set) var title = ""
    @Published private(set) var loaded = false
    @Published private(set) var playing = false
    @Published private(set) var buffering = false
    @Published private(set) var position: Int64 = 0
    @Published private(set) var duration: Int64 = 0
    @Published private(set) var rate: Float = 1
    @Published private(set) var audioTracks: [PlayerTrack] = []
    @Published private(set) var subtitleTracks: [PlayerTrack] = []
    @Published private(set) var selectedAudio: Int32 = -1
    @Published private(set) var selectedSubtitle: Int32 = -1
    @Published var canPrevious = false
    @Published var canNext = false
    @Published var error: String?
    @Published var danmakuStatus = ""
    @Published private(set) var comments: [DanmakuEvent] = []
    let surface = UIView()
    private let player = VLCMediaPlayer(options: ["--quiet", "--no-video-title-show", "--stats"])
    private var timer: Timer?
    private var anchor = Date()
    private var lastCheckpoint = Date()
    private var pendingResume: Int64 = 0
    private var pendingSubtitles: [(String, URL)] = []
    private var interruptionObserver: NSObjectProtocol?
    var checkpoint: ((Int64, Int64?) -> Void)?
    var navigate: ((Int) -> Void)?
    #if DEBUG
    var fixtureReportURL: URL?
    var fixtureRunID = ""
    var decodedVideoFrames: Int { Int(player.media?.statistics.decodedVideo ?? 0) }
    var displayedVideoFrames: Int { Int(player.media?.statistics.displayedPictures ?? 0) }
    private var reportedFixture = false
    var diagnostic: String { "state=\(player.state.rawValue) playing=\(player.isPlaying) position=\(position) audio=\(audioTracks.count) subtitles=\(subtitleTracks.count) duration=\(duration) audioRaw=\(String(describing: player.audioTrackIndexes)) names=\(String(describing: player.audioTrackNames))" }
    #endif

    override init() {
        super.init()
        surface.backgroundColor = .black
        player.delegate = self; player.drawable = surface
        interruptionObserver = NotificationCenter.default.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] note in
            guard let type = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
                  type == AVAudioSession.InterruptionType.began.rawValue else { return }
            self?.player.pause(); self?.saveCheckpoint()
        }
        timer = Timer.scheduledTimer(withTimeInterval: 0.2, repeats: true) { [weak self] _ in self?.refresh() }
    }
    func load(url: URL, title: String, resume: Int64, subtitles: [(String, URL)]) {
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
            try AVAudioSession.sharedInstance().setActive(true)
        } catch { self.error = error.localizedDescription; return }
        player.stop(); self.title = title; loaded = true; playing = false; position = 0; duration = 0
        buffering = true; error = nil; comments = []; rate = 1; player.rate = 1
        #if DEBUG
        reportedFixture = false
        #endif
        danmakuStatus = NSLocalizedString("Loading danmaku…", comment: "")
        pendingResume = resume; pendingSubtitles = subtitles
        player.media = VLCMedia(url: url); player.play(); lastCheckpoint = Date()
    }
    func setDanmaku(_ track: DanmakuTrack?) {
        comments = (track?.comments ?? []).sorted { $0.timestampMs < $1.timestampMs }
        switch track?.status {
        case "READY": danmakuStatus = String(format: NSLocalizedString("%d comments", comment: ""), comments.count)
        case "FAILED": danmakuStatus = NSLocalizedString("Danmaku request failed. Retry from playback options.", comment: "")
        default: danmakuStatus = NSLocalizedString("Danmaku unavailable", comment: "")
        }
    }
    func toggle() { if player.isPlaying { player.pause(); saveCheckpoint() } else { player.play() }; refresh() }
    func seek(_ milliseconds: Int64) {
        let target = max(0, min(milliseconds, duration > 0 ? duration : Int64(Int32.max)))
        player.time = VLCTime(int: Int32(clamping: target)); position = target; anchor = Date()
        saveCheckpoint()
    }
    func setRate(_ rate: Float) { player.rate = rate; self.rate = rate; refresh() }
    func audio(_ id: Int32) { player.currentAudioTrackIndex = id; selectedAudio = id }
    func subtitle(_ id: Int32) { player.currentVideoSubTitleIndex = id; selectedSubtitle = id }
    func stop() {
        player.stop(); loaded = false; playing = false; buffering = false; comments = []
        audioTracks = []; subtitleTracks = []; title = ""; position = 0; duration = 0
        UIApplication.shared.isIdleTimerDisabled = false
    }
    func clock(at date: Date) -> Double {
        let time = Double(position) + (playing ? min(0.3, max(0, date.timeIntervalSince(anchor))) * 1000 * Double(rate) : 0)
        return duration > 0 ? min(Double(duration), time) : time
    }
    func saveCheckpoint() { guard loaded else { return }; checkpoint?(position, duration > 0 ? duration : nil); lastCheckpoint = Date() }
    private func refresh() {
        guard loaded else { return }
        // VLCKit state is the last notification, which can remain buffering/ESAdded
        // while libvlc is actively playing. Query libvlc for playback activity.
        playing = player.isPlaying
        buffering = !playing && (player.state == .opening || player.state == .buffering)
        let time = max(0, Int64(player.time.intValue))
        if time != position || !playing { position = time; anchor = Date() }
        duration = max(0, Int64(player.media?.length.intValue ?? 0)); rate = player.rate
        if playing {
            if pendingResume > 0 { let target = pendingResume; pendingResume = 0; seek(target) }
            if !pendingSubtitles.isEmpty {
                let subtitles = pendingSubtitles; pendingSubtitles = []
                for (_, url) in subtitles { player.addPlaybackSlave(url, type: .subtitle, enforce: false) }
            }
        }
        audioTracks = tracks(names: player.audioTrackNames, indexes: player.audioTrackIndexes)
        subtitleTracks = tracks(names: player.videoSubTitlesNames, indexes: player.videoSubTitlesIndexes)
        selectedAudio = player.currentAudioTrackIndex; selectedSubtitle = player.currentVideoSubTitleIndex
        UIApplication.shared.isIdleTimerDisabled = playing
        if playing, Date().timeIntervalSince(lastCheckpoint) >= 5 { saveCheckpoint() }
        if player.state == .error { error = NSLocalizedString("Playback failed. Check the connection and media file.", comment: "") }
        #if DEBUG
        if let report = fixtureReportURL, !reportedFixture,
           (playing && position >= 1000 && decodedVideoFrames > 0 && displayedVideoFrames > 0 && audioTracks.filter({ $0.id >= 0 }).count >= 2 && subtitleTracks.filter({ $0.id >= 0 }).count >= 2) || error != nil {
            let result = ["runId": fixtureRunID, "playing": String(playing), "positionMs": String(position), "durationMs": String(duration),
                          "decodedVideoFrames": String(decodedVideoFrames), "displayedVideoFrames": String(displayedVideoFrames),
                          "audioTracks": String(audioTracks.filter { $0.id >= 0 }.count),
                          "subtitleTracks": String(subtitleTracks.filter { $0.id >= 0 }.count), "error": error ?? ""]
            do { try AtomicFile.write(result, to: report); reportedFixture = true }
            catch { self.error = error.localizedDescription }
        }
        #endif
    }
    private func tracks(names: [Any]?, indexes: [Any]?) -> [PlayerTrack] {
        zip(names ?? [], indexes ?? []).compactMap { name, index in
            guard let number = index as? NSNumber else { return nil }
            return PlayerTrack(id: number.int32Value, name: String(describing: name))
        }
    }
    func mediaPlayerTimeChanged(_ aNotification: Notification) { DispatchQueue.main.async { [weak self] in self?.refresh() } }
    func mediaPlayerStateChanged(_ aNotification: Notification) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.refresh()
            if self.player.state == .ended {
                if self.duration > 0 { self.position = self.duration }
                self.saveCheckpoint(); self.playing = false
            } else if self.player.state == .paused { self.saveCheckpoint() }
        }
    }
    deinit { timer?.invalidate(); if let interruptionObserver { NotificationCenter.default.removeObserver(interruptionObserver) }; player.stop() }
}
