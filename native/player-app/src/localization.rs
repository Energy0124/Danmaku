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
    pub fn pairing_token(self) -> &'static str {
        self.text("Pairing token (optional)", "配對權杖（選填）")
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
    pub fn settings(self) -> &'static str {
        self.text("Settings", "設定")
    }
    pub fn loading_library(self) -> &'static str {
        self.text("Loading library…", "正在載入媒體庫…")
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
            "Library scanning, providers, and account sync are managed in the web UI.",
            "媒體庫掃描、資料來源與帳號同步請在網頁管理介面中設定。",
        )
    }
    pub fn open_web_admin(self) -> &'static str {
        self.text("Open web administration", "開啟網頁管理介面")
    }
    pub fn saved(self) -> &'static str {
        self.text("Preferences save automatically.", "偏好設定會自動儲存。")
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
    pub fn attached_server(self) -> &'static str {
        self.text("Attached to an existing server", "已連線至現有伺服器")
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
