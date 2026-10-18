//! Typed UI localization for the native player.

use serde::{Deserialize, Serialize};

#[derive(Clone, Copy, Debug, Default, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "kebab-case")]
pub enum Language {
    #[default]
    English,
    #[serde(rename = "zh-TW")]
    TraditionalChinese,
}

impl Language {
    pub const ALL: [Self; 2] = [Self::English, Self::TraditionalChinese];

    pub fn code(self) -> &'static str {
        match self {
            Self::English => "en",
            Self::TraditionalChinese => "zh-TW",
        }
    }

    pub fn native_name(self) -> &'static str {
        match self {
            Self::English => "English",
            Self::TraditionalChinese => "繁體中文",
        }
    }

    pub fn parse(value: &str) -> Option<Self> {
        match value {
            "en" | "en-US" => Some(Self::English),
            "zh-TW" | "zh-Hant" => Some(Self::TraditionalChinese),
            _ => None,
        }
    }
}

#[derive(Clone, Copy)]
pub struct Strings {
    language: Language,
}

impl Strings {
    pub fn new(language: Language) -> Self {
        Self { language }
    }
    pub fn language(self) -> Language {
        self.language
    }

    pub fn text(self, english: &'static str, chinese: &'static str) -> &'static str {
        match self.language {
            Language::English => english,
            Language::TraditionalChinese => chinese,
        }
    }

    pub fn connect_subtitle(self) -> &'static str {
        self.text("Connect to a library server", "連線至媒體庫伺服器")
    }
    pub fn welcome_title(self) -> &'static str {
        self.text("Your anime, ready to play", "你的動畫，隨時開播")
    }
    pub fn welcome_body(self) -> &'static str {
        self.text(
            "Choose a folder once. Danmaku starts your local library automatically.",
            "只需選擇一次資料夾，Danmaku 就會自動啟動本機媒體庫。",
        )
    }
    pub fn choose_anime_folder(self) -> &'static str {
        self.text("Choose anime folder", "選擇動畫資料夾")
    }
    pub fn setup_folder_step(self) -> &'static str {
        self.text("Library folder", "媒體庫資料夾")
    }
    pub fn setup_server_step(self) -> &'static str {
        self.text("Start local server", "啟動本機伺服器")
    }
    pub fn setup_ready_step(self) -> &'static str {
        self.text("Ready", "就緒")
    }
    pub fn ready_automatically(self) -> &'static str {
        self.text("Ready automatically", "自動準備就緒")
    }
    pub fn connect_another_server(self) -> &'static str {
        self.text("Connect to another server", "連線至其他伺服器")
    }
    pub fn home(self) -> &'static str {
        self.text("Home", "首頁")
    }
    pub fn your_library(self) -> &'static str {
        self.text("Your library", "你的媒體庫")
    }
    pub fn library_online(self) -> &'static str {
        self.text("Library online", "媒體庫已上線")
    }
    pub fn recently_added(self) -> &'static str {
        self.text("Recently added", "最近加入")
    }
    pub fn discovered(self) -> &'static str {
        self.text("Discovered on this network", "此網路上找到的伺服器")
    }
    pub fn listening(self) -> &'static str {
        self.text("Listening for servers…", "正在尋找伺服器…")
    }
    pub fn manual_connection(self) -> &'static str {
        self.text("Manual connection", "手動連線")
    }
    pub fn connect(self) -> &'static str {
        self.text("Connect", "連線")
    }
    pub fn back(self) -> &'static str {
        self.text("< Back", "< 返回")
    }
    pub fn search(self) -> &'static str {
        self.text("Search", "搜尋")
    }
    pub fn disconnect(self) -> &'static str {
        self.text("Disconnect", "中斷連線")
    }
    pub fn refresh(self) -> &'static str {
        self.text("Refresh", "重新整理")
    }
    pub fn refresh_folder(self) -> &'static str {
        self.text("Refresh current folder", "重新掃描目前資料夾")
    }
    pub fn close(self) -> &'static str {
        self.text("Close", "關閉")
    }
    pub fn scanning_folder(self) -> &'static str {
        self.text("Scanning folder…", "正在掃描資料夾…")
    }
    pub fn folder_scan_failed(self) -> &'static str {
        self.text(
            "Couldn’t refresh this folder. The previous listing was kept.",
            "無法重新掃描此資料夾，已保留先前的清單。",
        )
    }
    pub fn settings(self) -> &'static str {
        self.text("Settings", "設定")
    }
    pub fn loading_library(self) -> &'static str {
        self.text("Loading library…", "正在載入媒體庫…")
    }
    pub fn connecting_to_server(self) -> &'static str {
        self.text("Connecting…", "連線中…")
    }
    pub fn refreshing_library(self) -> &'static str {
        self.text("Refreshing library…", "正在重新整理媒體庫…")
    }
    pub fn indexing_library(self) -> &'static str {
        self.text("Indexing library…", "正在建立媒體庫索引…")
    }
    pub fn indexing_files_found(self, files: u64) -> String {
        match self.language {
            Language::English => format!("{files} files found"),
            Language::TraditionalChinese => format!("已找到 {files} 個檔案"),
        }
    }
    pub fn failed_library(self) -> &'static str {
        self.text("Failed to load library", "無法載入媒體庫")
    }
    pub fn continue_watching(self) -> &'static str {
        self.text("Continue watching", "繼續觀看")
    }
    pub fn next_up(self) -> &'static str {
        self.text("Next up", "接下來播放")
    }
    pub fn all_series(self) -> &'static str {
        self.text("All series", "所有系列")
    }
    pub fn titles(self) -> &'static str {
        self.text("titles", "部作品")
    }
    pub fn episodes(self) -> &'static str {
        self.text("episodes", "集")
    }
    pub fn series_matching(self) -> &'static str {
        self.text("Series matching", "符合的系列")
    }
    pub fn no_series(self) -> &'static str {
        self.text("No matching series.", "找不到符合的系列。")
    }
    pub fn no_episodes(self) -> &'static str {
        self.text("No matching episodes.", "找不到符合的集數。")
    }
    pub fn resume(self) -> &'static str {
        self.text("Resume", "繼續")
    }
    pub fn next(self) -> &'static str {
        self.text("Next", "下一集")
    }
    pub fn start(self) -> &'static str {
        self.text("Start", "開始")
    }
    pub fn watched(self) -> &'static str {
        self.text("Watched", "已觀看")
    }
    pub fn started(self) -> &'static str {
        self.text("Started", "已開始")
    }
    pub fn play(self) -> &'static str {
        self.text("Play", "播放")
    }
    pub fn pause(self) -> &'static str {
        self.text("Pause", "暫停")
    }
    pub fn library(self) -> &'static str {
        self.text("Library", "媒體庫")
    }
    pub fn previous_episode(self) -> &'static str {
        self.text("Previous episode", "上一集")
    }
    pub fn next_episode(self) -> &'static str {
        self.text("Next episode", "下一集")
    }
    pub fn auto_next_on(self) -> &'static str {
        self.text("Auto-next on", "自動下一集：開")
    }
    pub fn auto_next_off(self) -> &'static str {
        self.text("Auto-next off", "自動下一集：關")
    }
    pub fn windowed(self) -> &'static str {
        self.text("Windowed", "視窗模式")
    }
    pub fn fullscreen(self) -> &'static str {
        self.text("Fullscreen", "全螢幕")
    }
    pub fn mute(self) -> &'static str {
        self.text("Mute", "靜音")
    }
    pub fn unmute(self) -> &'static str {
        self.text("Unmute", "取消靜音")
    }
    pub fn show_danmaku(self) -> &'static str {
        self.text("Show danmaku", "顯示彈幕")
    }
    pub fn danmaku_types(self) -> &'static str {
        self.text("Danmaku types", "彈幕類型")
    }
    pub fn scrolling_danmaku(self) -> &'static str {
        self.text("Scrolling", "滾動")
    }
    pub fn top_danmaku(self) -> &'static str {
        self.text("Top", "頂部")
    }
    pub fn bottom_danmaku(self) -> &'static str {
        self.text("Bottom", "底部")
    }
    pub fn opacity(self) -> &'static str {
        self.text("Opacity", "透明度")
    }
    pub fn speed(self) -> &'static str {
        self.text("Speed", "速度")
    }
    pub fn density(self) -> &'static str {
        self.text("Density", "密度")
    }
    pub fn lanes(self) -> &'static str {
        self.text("Lanes", "軌道數")
    }
    pub fn reset_display(self) -> &'static str {
        self.text("Reset display settings", "重設顯示設定")
    }
    pub fn audio(self) -> &'static str {
        self.text("Audio", "音訊")
    }
    pub fn no_audio(self) -> &'static str {
        self.text("No audio tracks", "沒有音訊軌")
    }
    pub fn subtitles(self) -> &'static str {
        self.text("Subs", "字幕")
    }
    pub fn subtitles_off(self) -> &'static str {
        self.text("Subs off", "字幕關閉")
    }
    pub fn off(self) -> &'static str {
        self.text("Off", "關閉")
    }
    pub fn preferences(self) -> &'static str {
        self.text("Playback preferences", "播放偏好設定")
    }
    pub fn language_label(self) -> &'static str {
        self.text("Language", "語言")
    }
    pub fn default_volume(self) -> &'static str {
        self.text("Default volume", "預設音量")
    }
    pub fn playback_rate(self) -> &'static str {
        self.text("Playback rate", "播放速度")
    }
    pub fn auto_next(self) -> &'static str {
        self.text("Automatically play next episode", "自動播放下一集")
    }
    pub fn danmaku_defaults(self) -> &'static str {
        self.text("Danmaku defaults", "彈幕預設值")
    }
    pub fn server_connection(self) -> &'static str {
        self.text("Server connection", "伺服器連線")
    }
    pub fn connected_to(self) -> &'static str {
        self.text("Connected to", "已連線至")
    }
    pub fn remembered_server(self) -> &'static str {
        self.text("Remembered server", "已記住的伺服器")
    }
    pub fn no_server(self) -> &'static str {
        self.text("No server is connected.", "目前未連線至伺服器。")
    }
    pub fn change_server(self) -> &'static str {
        self.text("Change server", "更換伺服器")
    }
    pub fn forget_server(self) -> &'static str {
        self.text("Forget remembered server", "忘記已記住的伺服器")
    }
    pub fn administration(self) -> &'static str {
        self.text("Server administration", "伺服器管理")
    }
    pub fn administration_note(self) -> &'static str {
        self.text(
            "Advanced server diagnostics and library administration remain available in the web UI.",
            "進階伺服器診斷與媒體庫管理仍可在網頁管理介面中使用。",
        )
    }
    pub fn open_web_admin(self) -> &'static str {
        self.text("Open web administration", "開啟網頁管理介面")
    }
    pub fn auto_downloads(self) -> &'static str {
        self.text("Automatic anime downloads", "自動下載動畫")
    }
    pub fn auto_downloads_note(self) -> &'static str {
        self.text(
            "Search and manage ANI-RSS subscriptions from the responsive web administration UI.",
            "在響應式網頁管理介面中搜尋與管理 ANI-RSS 訂閱。",
        )
    }
    pub fn open_auto_downloads(self) -> &'static str {
        self.text("Manage automatic downloads", "管理自動下載")
    }
    pub fn accounts_tracking(self) -> &'static str {
        self.text("Accounts & tracking", "帳號與追蹤")
    }
    pub fn accounts_tracking_note(self) -> &'static str {
        self.text(
            "Connect MyAnimeList or Bangumi, match your series, and review list updates.",
            "連接 MyAnimeList 或 Bangumi、配對作品並檢查清單更新。",
        )
    }
    pub fn open_accounts_tracking(self) -> &'static str {
        self.text("Manage accounts & tracking", "管理帳號與追蹤")
    }
    pub fn tracking_completion_title(self) -> &'static str {
        self.text("Update your anime lists?", "要更新動畫清單嗎？")
    }
    pub fn tracking_completion_body(self) -> &'static str {
        self.text(
            "This episode is complete. Review the proposed MyAnimeList and Bangumi changes before sending them.",
            "此集已播放完畢。送出前請先檢查 MyAnimeList 與 Bangumi 的建議變更。",
        )
    }
    pub fn review_tracking_update(self) -> &'static str {
        self.text("Review update", "檢查更新")
    }
    pub fn not_now(self) -> &'static str {
        self.text("Not now", "暫時不要")
    }
    pub fn mal_browser_notice(self) -> &'static str {
        self.text(
            "Complete MyAnimeList sign-in in your browser, then return here.",
            "請在瀏覽器完成 MyAnimeList 登入，然後返回此處。",
        )
    }
    pub fn saved(self) -> &'static str {
        self.text("Preferences save automatically.", "偏好設定會自動儲存。")
    }
    pub fn application_updates(self) -> &'static str {
        self.text("Application updates", "應用程式更新")
    }
    pub fn current_version(self) -> &'static str {
        self.text("Current version", "目前版本")
    }
    pub fn updates_unavailable_portable(self) -> &'static str {
        self.text(
            "Automatic updates are available in the installed Windows app.",
            "自動更新僅適用於已安裝的 Windows 應用程式。",
        )
    }
    pub fn download_latest_installer(self) -> &'static str {
        self.text("Download the latest installer", "下載最新安裝程式")
    }
    pub fn up_to_date(self) -> &'static str {
        self.text("Danmaku is up to date.", "Danmaku 已是最新版本。")
    }
    pub fn check_for_updates(self) -> &'static str {
        self.text("Check for updates", "檢查更新")
    }
    pub fn checking_for_updates(self) -> &'static str {
        self.text("Checking for updates…", "正在檢查更新…")
    }
    pub fn update_available_title(self) -> &'static str {
        self.text("Danmaku update", "Danmaku 更新")
    }
    pub fn update_available(self, version: &str) -> String {
        match self.language {
            Language::English => format!("Version {version} is available."),
            Language::TraditionalChinese => format!("版本 {version} 已可下載。"),
        }
    }
    pub fn downloading_update(self, version: &str, progress: u8) -> String {
        match self.language {
            Language::English => format!("Downloading version {version}… {progress}%"),
            Language::TraditionalChinese => format!("正在下載版本 {version}… {progress}%"),
        }
    }
    pub fn update_ready(self, version: &str) -> String {
        match self.language {
            Language::English => format!("Version {version} is ready to install."),
            Language::TraditionalChinese => format!("版本 {version} 已可安裝。"),
        }
    }
    pub fn update_and_restart(self) -> &'static str {
        self.text("Update and restart", "更新並重新啟動")
    }
    pub fn update_downloaded(self) -> &'static str {
        self.text(
            "The update is downloaded and ready. Danmaku will close, install it, and restart.",
            "更新已下載完成。Danmaku 將關閉、安裝更新並重新啟動。",
        )
    }
    pub fn release_notes(self) -> &'static str {
        self.text("Release notes", "版本說明")
    }
    pub fn no_release_notes(self) -> &'static str {
        self.text(
            "No release notes were provided for this version.",
            "此版本未提供版本說明。",
        )
    }
    pub fn update_failed(self, message: &str) -> String {
        match self.language {
            Language::English => format!("Update failed: {message}"),
            Language::TraditionalChinese => format!("更新失敗：{message}"),
        }
    }
    pub fn retry(self) -> &'static str {
        self.text("Retry", "重試")
    }
    pub fn this_pc(self) -> &'static str {
        self.text("Library on this PC", "此電腦上的媒體庫")
    }
    pub fn local_host_note(self) -> &'static str {
        self.text(
            "Choose your anime folder. Danmaku will start and connect the bundled server automatically.",
            "選擇動畫資料夾，Danmaku 會自動啟動並連線至內建伺服器。",
        )
    }
    pub fn library_folder(self) -> &'static str {
        self.text("Library folder", "媒體庫資料夾")
    }
    pub fn choose_folder(self) -> &'static str {
        self.text("Choose folder…", "選擇資料夾…")
    }
    pub fn start_local_library(self) -> &'static str {
        self.text("Start local library", "啟動本機媒體庫")
    }
    pub fn starting_local_server(self) -> &'static str {
        self.text(
            "Starting the local library server…",
            "正在啟動本機媒體庫伺服器…",
        )
    }
    pub fn local_server_running(self) -> &'static str {
        self.text(
            "Local library server is running.",
            "本機媒體庫伺服器正在執行。",
        )
    }
    pub fn local_server_stopped(self) -> &'static str {
        self.text(
            "Local library server is stopped.",
            "本機媒體庫伺服器已停止。",
        )
    }
    pub fn other_servers(self) -> &'static str {
        self.text("Other servers", "其他伺服器")
    }
    pub fn local_hosting(self) -> &'static str {
        self.text("Local server", "本機伺服器")
    }
    pub fn managed_by_player(self) -> &'static str {
        self.text("Managed by this player", "由此播放器管理")
    }
    pub fn managed_by_background_host(self) -> &'static str {
        self.text("Managed in the background", "由背景主機管理")
    }
    pub fn background_host_note(self) -> &'static str {
        self.text(
            "Use manage-rust-library-background-host.ps1 to change or stop the background server.",
            "請使用 manage-rust-library-background-host.ps1 變更或停止背景伺服器。",
        )
    }
    pub fn attached_server(self) -> &'static str {
        self.text("Attached to an existing server", "已連線至現有伺服器")
    }
    pub fn server_managed_settings_note(self) -> &'static str {
        self.text(
            "Manage provider settings in the server web admin.",
            "請在伺服器網頁管理介面中管理提供者設定。",
        )
    }
    pub fn restart_server(self) -> &'static str {
        self.text("Restart server", "重新啟動伺服器")
    }
    pub fn stop_server(self) -> &'static str {
        self.text("Stop server", "停止伺服器")
    }
    pub fn change_library_folder(self) -> &'static str {
        self.text("Change library folder…", "變更媒體庫資料夾…")
    }
    pub fn local_server_unavailable(self) -> &'static str {
        self.text(
            "The bundled local server is not available in this build.",
            "此版本未包含本機伺服器。",
        )
    }
    pub fn ass_compatibility(self) -> &'static str {
        self.text(
            "ASS compatibility uses mpv's subtitle renderer.",
            "ASS 相容模式使用 mpv 字幕渲染器。",
        )
    }
    pub fn select_subtitles(self) -> &'static str {
        self.text(
            "Select or disable it from the Subs menu.",
            "請在字幕選單中選擇或停用。",
        )
    }
    pub fn drop_danmaku(self) -> &'static str {
        self.text(
            "Drop XML, JSON, or ASS onto the player to attach.",
            "將 XML、JSON 或 ASS 拖放至播放器即可載入。",
        )
    }
    pub fn attachment_failed(self) -> &'static str {
        self.text("Danmaku attachment failed", "彈幕載入失敗")
    }
    pub fn danmaku_shortcut(self) -> &'static str {
        self.text(
            "Shortcut: D toggles the native overlay.",
            "快捷鍵：D 切換原生彈幕。",
        )
    }
    pub fn greeting(self, local_hour: u8) -> &'static str {
        match local_hour {
            5..=11 => self.text("Good morning", "早安"),
            12..=17 => self.text("Good afternoon", "午安"),
            _ => self.text("Good evening", "晚安"),
        }
    }
    pub fn local_library(self) -> &'static str {
        self.text("Local library", "本機媒體庫")
    }
    pub fn online(self) -> &'static str {
        self.text("Online", "已上線")
    }
    pub fn minutes_left(self, minutes: i64) -> String {
        match self.language {
            Language::English => format!("{minutes} min left"),
            Language::TraditionalChinese => format!("剩餘 {minutes} 分鐘"),
        }
    }
    pub fn up_next(self, title: &str) -> String {
        match self.language {
            Language::English => format!("Next: {title}"),
            Language::TraditionalChinese => format!("接下來：{title}"),
        }
    }
    pub fn danmaku_label(self) -> &'static str {
        self.text("Danmaku", "彈幕")
    }
    pub fn no_library_folders(self) -> &'static str {
        self.text("No library folders yet.", "尚未加入媒體庫資料夾。")
    }
    pub fn add_library_folder(self) -> &'static str {
        self.text("Add folder…", "新增資料夾…")
    }
    pub fn remove_folder(self) -> &'static str {
        self.text("Remove", "移除")
    }
    pub fn danmaku_provider(self) -> &'static str {
        self.text("Danmaku provider", "彈幕來源")
    }
    pub fn dandanplay_configured(self) -> &'static str {
        self.text("dandanplay credentials saved.", "已儲存 dandanplay 憑證。")
    }
    pub fn dandanplay_not_configured(self) -> &'static str {
        self.text(
            "Add dandanplay credentials to load danmaku automatically.",
            "新增 dandanplay 憑證即可自動載入彈幕。",
        )
    }
    pub fn dandanplay_app_id(self) -> &'static str {
        self.text("App ID", "App ID")
    }
    pub fn dandanplay_app_secret(self) -> &'static str {
        self.text("App secret", "App Secret")
    }
    pub fn dandanplay_restart_hint(self) -> &'static str {
        self.text(
            "Saving restarts the local server to apply new credentials.",
            "儲存後會重新啟動本機伺服器以套用新憑證。",
        )
    }
    pub fn save_and_apply(self) -> &'static str {
        self.text("Save & apply", "儲存並套用")
    }
    pub fn clear_credentials(self) -> &'static str {
        self.text("Clear", "清除")
    }
    pub fn match_episodes(self) -> &'static str {
        self.text("Match episodes without playing", "在不播放的情況下比對集數")
    }
    pub fn matching_episodes(self) -> &'static str {
        self.text("Matching episodes…", "正在比對集數…")
    }
    pub fn needs_attention(self) -> &'static str {
        self.text("Needs attention", "需要處理")
    }
    pub fn refresh_danmaku(self) -> &'static str {
        self.text("Refresh danmaku", "重新整理彈幕")
    }
    pub fn danmaku_provider_unavailable(self) -> &'static str {
        self.text(
            "Danmaku provider unavailable — check Settings",
            "彈幕服務無法使用 — 請檢查設定",
        )
    }
    pub fn danmaku_ready(self) -> &'static str {
        self.text("Danmaku data is ready", "彈幕資料已就緒")
    }
    pub fn series_needs_attention(self, count: usize) -> String {
        match self.language {
            Language::English => format!("{count} episodes need attention"),
            Language::TraditionalChinese => format!("{count} 集需要處理"),
        }
    }
    pub fn issues_count(self, count: usize) -> String {
        match self.language {
            Language::English => format!("{count} issues"),
            Language::TraditionalChinese => format!("{count} 個問題"),
        }
    }
    pub fn cache_needed_count(self, count: usize) -> String {
        match self.language {
            Language::English => format!("{count} uncached"),
            Language::TraditionalChinese => format!("{count} 集未快取"),
        }
    }
    pub fn needs_match(self) -> &'static str {
        self.text("Needs match", "需要比對")
    }
    pub fn danmaku_uncached(self) -> &'static str {
        self.text("Danmaku not cached", "彈幕尚未快取")
    }
    pub fn danmaku_stale(self) -> &'static str {
        self.text("Danmaku stale", "彈幕快取已過期")
    }
    pub fn refresh_failed(self) -> &'static str {
        self.text("Last refresh failed", "上次重新整理失敗")
    }
    pub fn repairing_danmaku(self, done: usize, total: usize, failed: usize) -> String {
        match self.language {
            Language::English => format!("Repairing danmaku {done}/{total} · {failed} failed"),
            Language::TraditionalChinese => {
                format!("正在修復彈幕 {done}/{total} · {failed} 個失敗")
            }
        }
    }
    pub fn change_match(self) -> &'static str {
        self.text("Change danmaku match", "變更彈幕比對結果")
    }
    pub fn no_matches_found(self) -> &'static str {
        self.text(
            "No dandanplay matches found.",
            "找不到 dandanplay 比對結果。",
        )
    }
    pub fn loading_matches(self) -> &'static str {
        self.text("Looking up matches…", "正在查詢比對結果…")
    }
    pub fn recent_view(self) -> &'static str {
        self.text("Recent", "最近")
    }
    pub fn season_view(self) -> &'static str {
        self.text("Season", "季度")
    }
    pub fn unknown_date(self) -> &'static str {
        self.text("Earlier", "較早")
    }
    pub fn unknown_season(self) -> &'static str {
        self.text("Unknown season", "季度不明")
    }
    pub fn library_overview(self) -> &'static str {
        self.text("Library overview", "媒體庫概覽")
    }
    pub fn total_size(self) -> &'static str {
        self.text("Total size", "總大小")
    }
    pub fn file_suggestions(self) -> &'static str {
        self.text("Suggested for this file", "此檔案的建議比對")
    }
    pub fn database_search(self) -> &'static str {
        self.text("Database search", "資料庫搜尋")
    }
    pub fn select_match(self) -> &'static str {
        self.text("Use match", "套用比對")
    }
    pub fn match_picker_hint(self) -> &'static str {
        self.text(
            "Choose a suggested episode or search the complete dandanplay database.",
            "選擇建議集數，或搜尋完整的 dandanplay 資料庫。",
        )
    }
    pub fn library_views(self) -> &'static str {
        self.text("Library views", "媒體庫檢視")
    }
    pub fn library_folders(self) -> &'static str {
        self.text("Library folders", "媒體庫資料夾")
    }
    pub fn sort_by(self) -> &'static str {
        self.text("Sort", "排序")
    }
    pub fn sort_title(self) -> &'static str {
        self.text("Title", "名稱")
    }
    pub fn sort_newest(self) -> &'static str {
        self.text("Newest added", "最近加入")
    }
    pub fn sort_release_year(self) -> &'static str {
        self.text("Release year", "推出年份")
    }
    pub fn sort_episode_count(self) -> &'static str {
        self.text("Episode count", "集數")
    }
    pub fn all_matches(self) -> &'static str {
        self.text("All matches", "所有比對狀態")
    }
    pub fn matched_only(self) -> &'static str {
        self.text("Matched", "已比對")
    }
    pub fn unmatched_only(self) -> &'static str {
        self.text("Unmatched", "未比對")
    }
    pub fn all_progress(self) -> &'static str {
        self.text("All progress", "所有觀看狀態")
    }
    pub fn unwatched(self) -> &'static str {
        self.text("Unwatched", "未觀看")
    }
    pub fn in_progress(self) -> &'static str {
        self.text("In progress", "觀看中")
    }
    pub fn completed(self) -> &'static str {
        self.text("Completed", "已看完")
    }
    pub fn all_folders(self) -> &'static str {
        self.text("All folders", "所有資料夾")
    }
    pub fn grid_size(self) -> &'static str {
        self.text("Card size", "卡片大小")
    }
    pub fn compact(self) -> &'static str {
        self.text("Compact", "小")
    }
    pub fn comfortable(self) -> &'static str {
        self.text("Comfortable", "中")
    }
    pub fn large(self) -> &'static str {
        self.text("Large", "大")
    }
    pub fn clear_filters(self) -> &'static str {
        self.text("Clear filters", "清除篩選")
    }
    pub fn no_filtered_series(self) -> &'static str {
        self.text(
            "No series match the current filters.",
            "沒有系列符合目前的篩選條件。",
        )
    }
    pub fn matched_anime(self) -> &'static str {
        self.text("Matched anime", "已比對動畫")
    }
    pub fn folders(self) -> &'static str {
        self.text("Folders", "資料夾")
    }
    pub fn hash_matches(self) -> &'static str {
        self.text("Matches for this file", "此檔案的比對結果")
    }
    pub fn search_dandanplay(self) -> &'static str {
        self.text("Search dandanplay", "搜尋 dandanplay")
    }
    pub fn search_dandanplay_hint(self) -> &'static str {
        self.text("Anime title keyword…", "動畫標題關鍵字…")
    }
    pub fn parent_folder(self) -> &'static str {
        self.text("Up one level", "返回上一級")
    }
    pub fn items_label(self) -> &'static str {
        self.text("items", "個項目")
    }
    pub fn recently_played(self) -> &'static str {
        self.text("Recently played", "最近播放")
    }
    pub fn all_years(self) -> &'static str {
        self.text("All years", "所有年份")
    }
    pub fn group_display(self) -> &'static str {
        self.text("Grouped", "分組顯示")
    }
    pub fn synopsis(self) -> &'static str {
        self.text("Synopsis", "簡介")
    }
    pub fn tags_label(self) -> &'static str {
        self.text("Tags", "標籤")
    }
    pub fn online_databases(self) -> &'static str {
        self.text("Online databases", "線上資料庫")
    }
    pub fn on_air(self) -> &'static str {
        self.text("Airing", "連載中")
    }
    pub fn finished_airing(self) -> &'static str {
        self.text("Finished airing", "已完結")
    }
    pub fn loading_details(self) -> &'static str {
        self.text("Loading anime details…", "正在載入動畫資料…")
    }
    pub fn details_unavailable(self) -> &'static str {
        self.text(
            "Online anime details are unavailable right now.",
            "目前無法取得線上動畫資料。",
        )
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_supported_language_codes() {
        assert_eq!(Language::parse("en"), Some(Language::English));
        assert_eq!(Language::parse("zh-TW"), Some(Language::TraditionalChinese));
        assert_eq!(Language::parse("zh-CN"), None);
    }

    #[test]
    fn consumer_surfaces_are_localized() {
        let zh = Strings::new(Language::TraditionalChinese);
        let en = Strings::new(Language::English);
        assert_eq!(en.greeting(8), "Good morning");
        assert_eq!(en.greeting(14), "Good afternoon");
        assert_eq!(en.greeting(22), "Good evening");
        assert_eq!(zh.greeting(22), "晚安");
        assert_eq!(en.minutes_left(42), "42 min left");
        assert_eq!(zh.minutes_left(42), "剩餘 42 分鐘");
        assert_eq!(en.up_next("Episode 19"), "Next: Episode 19");
        assert_eq!(zh.up_next("第 19 集"), "接下來：第 19 集");
        assert_ne!(zh.local_library(), en.local_library());
        assert_ne!(zh.online(), en.online());
        assert_eq!(en.recent_view(), "Recent");
        assert_eq!(zh.season_view(), "季度");
        assert_eq!(zh.database_search(), "資料庫搜尋");
        assert_eq!(en.recently_played(), "Recently played");
        assert_eq!(zh.recently_played(), "最近播放");
        assert_eq!(zh.synopsis(), "簡介");
        assert_eq!(zh.online_databases(), "線上資料庫");
    }

    #[test]
    fn first_screen_is_localized() {
        let zh = Strings::new(Language::TraditionalChinese);
        assert_eq!(zh.connect_subtitle(), "連線至媒體庫伺服器");
        assert_eq!(zh.welcome_title(), "你的動畫，隨時開播");
        assert_eq!(zh.choose_anime_folder(), "選擇動畫資料夾");
        assert_eq!(zh.home(), "首頁");
        assert_eq!(zh.connect(), "連線");
        assert_ne!(
            zh.connect_subtitle(),
            Strings::new(Language::English).connect_subtitle()
        );
    }
}

#[derive(Clone, Copy)]
pub enum OrganizerText {
    WindowTitle,
    Select,
    SelectModeHint,
    SelectVisible,
    ClearSelection,
    OpenFolderHint,
    OrganizeFolder,
    OpenOrganizer,
    ResumeReview,
    ReviewMenu,
    FinishReview,
    FinishReviewHint,
    HowItWorks,
    HelpFlow,
    HelpMatching,
    HelpExtras,
    HelpSafety,
    UndoLast,
    UndoLastHint,
    StopMatching,
    MatchingOff,
    MatchingOffHint,
    MatchingPaused,
    ResumeMatching,
    Destination,
    DestinationHint,
    DestinationExplain,
    Browse,
    CancelMove,
    RecoveryRequired,
    RetryRecovery,
    StartHeading,
    StartBody,
    StartHint,
    StartReview,
    AllDone,
    ReviewSkipped,
    FilterSeries,
    FilterToDo,
    FilterReady,
    FilterSkipped,
    FilterDone,
    FilterAll,
    QueueEmpty,
    KeepReview,
    ReplaceReviewHint,
    NeedsSeason,
    StateSkipped,
    StateDone,
    StateExcluded,
    Unskip,
    MarkDone,
    MarkDoneHint,
    ApproveNext,
    Skip,
    SkipHint,
    AllExcluded,
    MoveRunning,
    InPlace,
    InPlaceShort,
    Checking,
    CheckAgain,
    SeriesFolder,
    Match,
    MixedMatches,
    NoMatch,
    FolderNameOnly,
    UseFolderName,
    UseFolderNameHint,
    ChangeMatch,
    SearchHint,
    Search,
    NoResults,
    ClearMatch,
    MatchAgain,
    MatchAgainHint,
    ColumnFile,
    ColumnSeason,
    ColumnEpisode,
    ColumnDestination,
    ColumnBelongsTo,
    ColumnFrom,
    ColumnTo,
    Excluded,
    SeasonConflict,
    SeasonSuggested,
    SelectHint,
    SetSeason,
    MoveTo,
    MoveToHint,
    NewSeriesHint,
    Create,
    Exclude,
    Include,
    ExtrasHint,
    NoExtras,
    MatchingSubtitle,
    SeriesFolderOwner,
    ChooseOwner,
    Matching,
    MatchFailed,
    SeriesOnly,
    FromLibrary,
}

impl Strings {
    pub fn organizer_text(self, key: OrganizerText) -> &'static str {
        match key {
            OrganizerText::WindowTitle => self.text("Organize library", "整理媒體庫"),
            OrganizerText::Select => self.text("Select", "選取"),
            OrganizerText::SelectModeHint => self.text("Tick folders and videos to organize. Click a folder name to open it.", "勾選要整理的資料夾與影片。按資料夾名稱可開啟它。"),
            OrganizerText::SelectVisible => self.text("Select all here", "選取此處全部"),
            OrganizerText::ClearSelection => self.text("Clear selection", "清除選取"),
            OrganizerText::OpenFolderHint => self.text("Open folder", "開啟資料夾"),
            OrganizerText::OrganizeFolder => self.text("Organize this folder", "整理此資料夾"),
            OrganizerText::OpenOrganizer => self.text("Organize…", "整理…"),
            OrganizerText::ResumeReview => self.text("Resume organizing", "繼續整理"),
            OrganizerText::ReviewMenu => self.text("Review", "檢閱"),
            OrganizerText::FinishReview => self.text("Finish review", "結束檢閱"),
            OrganizerText::FinishReviewHint => self.text("Closes this review. Moved files stay where they are and can still be undone.", "關閉此檢閱。已移動的檔案會留在原處，仍可復原。"),
            OrganizerText::HowItWorks => self.text("How organizing works", "整理方式說明"),
            OrganizerText::HelpFlow => self.text("Pick a series on the left, check its folder name, seasons and match, then Approve & next. Each approval moves one series; nothing else changes.", "在左側選擇系列，確認資料夾名稱、季數與辨識結果，再按「核准並下一個」。每次核准只移動一個系列，其他項目不受影響。"),
            OrganizerText::HelpMatching => self.text("Videos are matched with dandanplay in the background while you work. A series with no match can use its folder name instead.", "整理時會在背景以彈彈play辨識影片。沒有辨識結果的系列可直接使用資料夾名稱。"),
            OrganizerText::HelpExtras => self.text("Matching subtitles move with their video. Fonts, subtitle packs and artwork can go in the series folder from the Extras tab.", "相符的字幕會隨影片一起移動。字型、字幕包與圖片可在「附加檔案」分頁放入系列資料夾。"),
            OrganizerText::HelpSafety => self.text("Nothing is overwritten or deleted. Use Undo to move the last series back.", "不會覆寫或刪除任何檔案。可使用「復原」將上一個系列移回原處。"),
            OrganizerText::UndoLast => self.text("Undo last move", "復原上次移動"),
            OrganizerText::UndoLastHint => self.text("Moves the most recently organized series back to where it was.", "將最近整理的系列移回原來位置。"),
            OrganizerText::StopMatching => self.text("Stop", "停止"),
            OrganizerText::MatchingOff => self.text("Automatic matching is off", "自動辨識已關閉"),
            OrganizerText::MatchingOffHint => self.text("Add dandanplay credentials in Settings to match videos automatically.", "請在設定中加入彈彈play憑證，以自動辨識影片。"),
            OrganizerText::MatchingPaused => self.text("Matching paused", "辨識已暫停"),
            OrganizerText::ResumeMatching => self.text("Resume matching", "繼續辨識"),
            OrganizerText::Destination => self.text("Destination", "目的地"),
            OrganizerText::DestinationHint => self.text("Folder inside a library root, e.g. D:\\Anime", "媒體庫根目錄內的資料夾，例如 D:\\Anime"),
            OrganizerText::DestinationExplain => self.text("Each series goes to <destination>/<series>/Season N/ and keeps its file names.", "每個系列會放到 <目的地>/<系列>/Season N/，並保留原檔名。"),
            OrganizerText::Browse => self.text("Browse…", "瀏覽…"),
            OrganizerText::CancelMove => self.text("Cancel", "取消"),
            OrganizerText::RecoveryRequired => self.text("An interrupted move needs recovery.", "有中斷的移動需要復原。"),
            OrganizerText::RetryRecovery => self.text("Retry recovery", "重試復原"),
            OrganizerText::StartHeading => self.text("Organize videos into series folders", "將影片整理到系列資料夾"),
            OrganizerText::StartBody => self.text("Review one series at a time. Nothing is overwritten or deleted, and each move can be undone.", "逐一檢閱每個系列。不會覆寫或刪除任何檔案，每次移動都可以復原。"),
            OrganizerText::StartHint => self.text("In Folders, turn on Select, tick the folders or videos to organize, then choose Organize. You can also use Organize this folder inside any folder.", "請在「資料夾」中開啟「選取」，勾選要整理的資料夾或影片後按「整理」。也可以在任一資料夾內使用「整理此資料夾」。"),
            OrganizerText::StartReview => self.text("Start review", "開始檢閱"),
            OrganizerText::AllDone => self.text("Every series is done", "所有系列都已完成"),
            OrganizerText::ReviewSkipped => self.text("Review skipped series", "檢閱已略過的系列"),
            OrganizerText::FilterSeries => self.text("Filter series", "篩選系列"),
            OrganizerText::FilterToDo => self.text("To do", "待處理"),
            OrganizerText::FilterReady => self.text("Ready", "可核准"),
            OrganizerText::FilterSkipped => self.text("Skipped", "已略過"),
            OrganizerText::FilterDone => self.text("Done", "已完成"),
            OrganizerText::FilterAll => self.text("All", "全部"),
            OrganizerText::QueueEmpty => self.text("No series in this list.", "此清單中沒有系列。"),
            OrganizerText::KeepReview => self.text("Keep current review", "保留目前的檢閱"),
            OrganizerText::ReplaceReviewHint => self.text("Starts a new review with your selection. Decisions for unfinished series are discarded; finished moves stay and can still be undone.", "以目前的選取開始新的檢閱。未完成系列的設定會被捨棄；已完成的移動會保留，仍可復原。"),
            OrganizerText::NeedsSeason => self.text("choose a season", "請選擇季數"),
            OrganizerText::StateSkipped => self.text("skipped", "已略過"),
            OrganizerText::StateDone => self.text("Done", "已完成"),
            OrganizerText::StateExcluded => self.text("all excluded", "全部排除"),
            OrganizerText::Unskip => self.text("Move back to To do", "移回待處理"),
            OrganizerText::MarkDone => self.text("Mark done", "標記為完成"),
            OrganizerText::MarkDoneHint => self.text("These videos are already in place. Saves their matches without moving anything.", "這些影片已在正確位置。只儲存辨識結果，不移動任何檔案。"),
            OrganizerText::ApproveNext => self.text("Approve & next", "核准並下一個"),
            OrganizerText::Skip => self.text("Skip", "略過"),
            OrganizerText::SkipHint => self.text("Leave this series as it is for now.", "暫時不處理此系列。"),
            OrganizerText::AllExcluded => self.text("Every video in this series is excluded; nothing will move.", "此系列的所有影片都已排除，不會移動任何檔案。"),
            OrganizerText::MoveRunning => self.text("Moving files…", "正在移動檔案…"),
            OrganizerText::InPlace => self.text("Already in place — nothing to move.", "已在正確位置，無需移動。"),
            OrganizerText::InPlaceShort => self.text("In place", "已在原位"),
            OrganizerText::Checking => self.text("Checking destinations…", "正在檢查目的地…"),
            OrganizerText::CheckAgain => self.text("Check again", "重新檢查"),
            OrganizerText::SeriesFolder => self.text("Series folder", "系列資料夾"),
            OrganizerText::Match => self.text("Match:", "辨識結果："),
            OrganizerText::MixedMatches => self.text("Several series — see each video", "多個系列，請查看各影片"),
            OrganizerText::NoMatch => self.text("No match yet", "尚未辨識"),
            OrganizerText::FolderNameOnly => self.text("Folder name", "資料夾名稱"),
            OrganizerText::UseFolderName => self.text("Use folder name", "使用資料夾名稱"),
            OrganizerText::UseFolderNameHint => self.text("Organize these videos under the folder name without a database match.", "不使用資料庫辨識，直接以資料夾名稱整理這些影片。"),
            OrganizerText::ChangeMatch => self.text("Change match…", "變更辨識…"),
            OrganizerText::SearchHint => self.text("Series title", "系列名稱"),
            OrganizerText::Search => self.text("Search", "搜尋"),
            OrganizerText::NoResults => self.text("No results yet. Search for the series title.", "尚無結果。請搜尋系列名稱。"),
            OrganizerText::ClearMatch => self.text("Clear match", "清除辨識"),
            OrganizerText::MatchAgain => self.text("Match again", "重新辨識"),
            OrganizerText::MatchAgainHint => self.text("Fingerprint these videos with dandanplay again.", "以彈彈play重新比對這些影片。"),
            OrganizerText::ColumnFile => self.text("File", "檔案"),
            OrganizerText::ColumnSeason => self.text("Season", "季"),
            OrganizerText::ColumnEpisode => self.text("Episode match", "集數辨識"),
            OrganizerText::ColumnDestination => self.text("Goes to", "目的地"),
            OrganizerText::ColumnBelongsTo => self.text("Goes with", "歸屬"),
            OrganizerText::ColumnFrom => self.text("From", "來源"),
            OrganizerText::ColumnTo => self.text("To", "目的地"),
            OrganizerText::Excluded => self.text("Excluded", "已排除"),
            OrganizerText::SeasonConflict => self.text("The path and title disagree about the season. Choose one.", "路徑與標題的季數不一致，請選擇一個。"),
            OrganizerText::SeasonSuggested => self.text("No season found; Season 1 is suggested.", "找不到季數，建議為第 1 季。"),
            OrganizerText::SelectHint => self.text("Tick videos to edit together", "勾選影片以一併編輯"),
            OrganizerText::SetSeason => self.text("Set season", "設定季數"),
            OrganizerText::MoveTo => self.text("Move to series…", "移到系列…"),
            OrganizerText::MoveToHint => self.text("Move the selected videos to another series folder.", "將選取的影片移到其他系列資料夾。"),
            OrganizerText::NewSeriesHint => self.text("New series name", "新系列名稱"),
            OrganizerText::Create => self.text("Create", "建立"),
            OrganizerText::Exclude => self.text("Exclude", "排除"),
            OrganizerText::Include => self.text("Include", "納入"),
            OrganizerText::ExtrasHint => self.text("Matching subtitles are ticked automatically. Fonts, subtitle packs and artwork can go in the series folder.", "相符字幕會自動勾選。字型、字幕包與圖片可放入系列資料夾。"),
            OrganizerText::NoExtras => self.text("No subtitles or other files next to these videos.", "這些影片旁沒有字幕或其他檔案。"),
            OrganizerText::MatchingSubtitle => self.text("matching subtitle", "相符字幕"),
            OrganizerText::SeriesFolderOwner => self.text("Series folder", "系列資料夾"),
            OrganizerText::ChooseOwner => self.text("Choose where it goes", "選擇放置位置"),
            OrganizerText::Matching => self.text("Matching…", "辨識中…"),
            OrganizerText::MatchFailed => self.text("Match failed", "辨識失敗"),
            OrganizerText::SeriesOnly => self.text("Series only", "僅系列"),
            OrganizerText::FromLibrary => self.text("From library", "來自媒體庫"),
        }
    }

    pub fn organizer_selected_videos(self, count: usize) -> String {
        self.en_zh(
            format!(
                "{count} {} selected",
                if count == 1 { "video" } else { "videos" }
            ),
            format!("已選取 {count} 部影片"),
        )
    }
    pub fn organizer_organize_selected(self, count: usize) -> String {
        self.en_zh(
            format!("Organize {count} selected"),
            format!("整理選取的 {count} 部"),
        )
    }
    pub fn organizer_selected_count(self, count: usize) -> String {
        self.en_zh(format!("{count} selected"), format!("已選取 {count} 部"))
    }
    pub fn organizer_video_count(self, count: usize) -> String {
        self.en_zh(
            format!("{count} {}", if count == 1 { "video" } else { "videos" }),
            format!("{count} 部影片"),
        )
    }
    pub fn organizer_needs_match(self, count: usize) -> String {
        self.en_zh(format!("{count} need a match"), format!("{count} 部待辨識"))
    }
    pub fn organizer_needs_match_long(self, count: usize) -> String {
        self.en_zh(
            format!(
                "{count} videos have no match. Choose one with Change match, or Use folder name."
            ),
            format!("{count} 部影片尚未辨識。請以「變更辨識」選擇，或「使用資料夾名稱」。"),
        )
    }
    pub fn organizer_needs_season(self, count: usize) -> String {
        self.en_zh(
            format!("Choose a season for {count} videos."),
            format!("請為 {count} 部影片選擇季數。"),
        )
    }
    pub fn organizer_more(self, count: usize) -> String {
        self.en_zh(format!("(+{count} more)"), format!("（另有 {count} 項）"))
    }
    pub fn organizer_move_summary(self, moves: usize, bytes: u64) -> String {
        let size = format_bytes(bytes);
        self.en_zh(
            format!("Ready: {moves} files ({size}) will move."),
            format!("可核准：將移動 {moves} 個檔案（{size}）。"),
        )
    }
    pub fn organizer_tab_videos(self, count: usize) -> String {
        self.en_zh(format!("Videos ({count})"), format!("影片（{count}）"))
    }
    pub fn organizer_tab_extras(self, count: usize) -> String {
        self.en_zh(format!("Extras ({count})"), format!("附加檔案（{count}）"))
    }
    pub fn organizer_tab_moves(self, count: usize) -> String {
        self.en_zh(format!("Moves ({count})"), format!("移動（{count}）"))
    }
    pub fn organizer_folder_preview(
        self,
        destination: &str,
        title: &str,
        seasons: &[u32],
    ) -> String {
        let season = match seasons {
            [one] => format!("Season {one}"),
            _ => "Season N".to_owned(),
        };
        let path = format!(
            "{}/{title}/{season}/",
            destination.trim_end_matches(['/', '\\'])
        );
        self.en_zh(format!("Files go to {path}"), format!("檔案將放到 {path}"))
    }
    pub fn organizer_apply_to(self, count: usize, selected: bool) -> String {
        if selected {
            self.en_zh(
                format!("A match you pick applies to the {count} selected videos."),
                format!("選擇的辨識結果會套用到選取的 {count} 部影片。"),
            )
        } else {
            self.en_zh(
                format!("A match you pick applies to every video in this series ({count})."),
                format!("選擇的辨識結果會套用到此系列的全部 {count} 部影片。"),
            )
        }
    }
    pub fn organizer_choose_of(self, count: usize) -> String {
        self.en_zh(format!("Choose ({count})…"), format!("選擇（{count}）…"))
    }
    pub fn organizer_matching(self, done: usize, total: usize) -> String {
        self.en_zh(
            format!("Matching {done} of {total}"),
            format!("辨識中 {done}/{total}"),
        )
    }
    pub fn organizer_paused_wait(self, seconds: u64) -> String {
        self.en_zh(
            format!("Matching paused · retry in {seconds}s"),
            format!("辨識已暫停 · {seconds} 秒後可重試"),
        )
    }
    pub fn organizer_moving(self, title: &str, done: usize, total: usize) -> String {
        self.en_zh(
            format!("Moving {title} · {done} of {total} files"),
            format!("正在移動 {title} · {done}/{total} 個檔案"),
        )
    }
    pub fn organizer_progress(self, done: usize, total: usize) -> String {
        self.en_zh(
            format!("{done} of {total} series done"),
            format!("已完成 {done}/{total} 個系列"),
        )
    }
    pub fn organizer_done_summary(self, done: usize, skipped: usize) -> String {
        self.en_zh(
            format!(
                "{done} organized, {skipped} skipped. Finish the review, or revisit skipped series."
            ),
            format!("已整理 {done} 個，略過 {skipped} 個。可結束檢閱，或回頭處理已略過的系列。"),
        )
    }
    pub fn organizer_unfinished(self, done: usize, total: usize) -> String {
        self.en_zh(
            format!("A review is already open ({done} of {total} series done)."),
            format!("已有進行中的檢閱（已完成 {done}/{total} 個系列）。"),
        )
    }
    pub fn organizer_replace_review(self, count: usize) -> String {
        self.en_zh(
            format!("Start over with {count} selected"),
            format!("以選取的 {count} 部重新開始"),
        )
    }

    fn en_zh(self, english: String, chinese: String) -> String {
        match self.language {
            Language::English => english,
            Language::TraditionalChinese => chinese,
        }
    }
}

fn format_bytes(bytes: u64) -> String {
    const UNITS: [&str; 4] = ["B", "KB", "MB", "GB"];
    let mut value = bytes as f64;
    let mut unit = 0;
    while value >= 1024.0 && unit < UNITS.len() - 1 {
        value /= 1024.0;
        unit += 1;
    }
    if unit == 0 {
        format!("{bytes} B")
    } else {
        format!("{value:.1} {}", UNITS[unit])
    }
}
