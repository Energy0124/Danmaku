import SwiftUI

final class AppDelegate: NSObject, UIApplicationDelegate {
    static weak var downloads: Downloads?
    func application(_ application: UIApplication, handleEventsForBackgroundURLSession identifier: String, completionHandler: @escaping () -> Void) {
        guard identifier == "app.danmaku.ios.downloads" else { completionHandler(); return }
        Self.downloads?.backgroundCompletion = completionHandler
    }
}

@main struct DanmakuApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @StateObject private var model = AppModel.forLaunch()
    @Environment(\.scenePhase) private var phase
    @State private var started = false
    var body: some Scene {
        WindowGroup {
            AppShell(model: model)
                .task {
                    await model.restore()
                    AppDelegate.downloads = model.downloads
                    #if DEBUG
                    if model.launchFixture() { return }
                    #endif
                    await model.reconnect()
                    started = true
                }
                .onChange(of: phase) { _, phase in
                    if phase == .active && started && !model.loading {
                        #if DEBUG
                        if ProcessInfo.processInfo.arguments.contains("--qa-fixture") { return }
                        #endif
                        Task { await model.reconnect() }
                    }
                    else { model.player.saveCheckpoint() }
                }
        }
    }
}
