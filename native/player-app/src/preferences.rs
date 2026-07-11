//! Durable, user-scoped preferences for the native player.

use std::{
    fs, io,
    path::{Path, PathBuf},
};

use serde::{Deserialize, Serialize};

use crate::{danmaku::DanmakuDisplaySettings, localization::Language};

#[derive(Clone, Debug, Deserialize, PartialEq, Serialize)]
#[serde(default)]
pub struct PlayerPreferences {
    pub language: Language,
    pub volume_percent: u8,
    pub playback_rate: f64,
    pub auto_next: bool,
    pub danmaku_enabled: bool,
    pub danmaku_opacity: f32,
    pub danmaku_speed: f32,
    pub danmaku_density: f32,
    pub danmaku_lanes: usize,
    pub last_server_url: Option<String>,
    pub local_library_roots: Vec<String>,
}

impl Default for PlayerPreferences {
    fn default() -> Self {
        let danmaku = DanmakuDisplaySettings::default();
        Self {
            language: Language::English,
            volume_percent: 100,
            playback_rate: 1.0,
            auto_next: false,
            danmaku_enabled: danmaku.enabled,
            danmaku_opacity: danmaku.opacity,
            danmaku_speed: danmaku.speed,
            danmaku_density: danmaku.density,
            danmaku_lanes: danmaku.max_lanes,
            last_server_url: None,
            local_library_roots: Vec::new(),
        }
    }
}

impl PlayerPreferences {
    pub fn sanitized(mut self) -> Self {
        self.volume_percent = self.volume_percent.min(130);
        self.playback_rate = if self.playback_rate.is_finite() {
            self.playback_rate.clamp(0.5, 2.0)
        } else {
            1.0
        };
        let danmaku = self.danmaku_settings().sanitized();
        self.danmaku_enabled = danmaku.enabled;
        self.danmaku_opacity = danmaku.opacity;
        self.danmaku_speed = danmaku.speed;
        self.danmaku_density = danmaku.density;
        self.danmaku_lanes = danmaku.max_lanes;
        self.last_server_url = self
            .last_server_url
            .take()
            .map(|url| url.trim().trim_end_matches('/').to_owned())
            .filter(|url| url.starts_with("http://") && url.len() > "http://".len());
        self.local_library_roots = self
            .local_library_roots
            .into_iter()
            .map(|root| root.trim().to_owned())
            .filter(|root| !root.is_empty())
            .fold(Vec::new(), |mut roots, root| {
                if !roots.contains(&root) {
                    roots.push(root);
                }
                roots
            });
        self
    }

    pub fn danmaku_settings(&self) -> DanmakuDisplaySettings {
        DanmakuDisplaySettings {
            enabled: self.danmaku_enabled,
            opacity: self.danmaku_opacity,
            speed: self.danmaku_speed,
            density: self.danmaku_density,
            max_lanes: self.danmaku_lanes,
        }
    }

    pub fn update_danmaku(&mut self, settings: &DanmakuDisplaySettings) {
        let settings = settings.sanitized();
        self.danmaku_enabled = settings.enabled;
        self.danmaku_opacity = settings.opacity;
        self.danmaku_speed = settings.speed;
        self.danmaku_density = settings.density;
        self.danmaku_lanes = settings.max_lanes;
    }
}

#[derive(Clone, Debug)]
pub struct PreferenceStore {
    path: PathBuf,
}

impl PreferenceStore {
    pub fn for_current_user() -> Self {
        let root = std::env::var_os("LOCALAPPDATA")
            .map(PathBuf::from)
            .or_else(|| std::env::current_dir().ok())
            .unwrap_or_else(|| PathBuf::from("."));
        Self {
            path: root.join("Danmaku").join("player-preferences.json"),
        }
    }

    #[cfg(test)]
    fn at(path: PathBuf) -> Self {
        Self { path }
    }

    pub fn load(&self) -> PlayerPreferences {
        fs::read_to_string(&self.path)
            .ok()
            .and_then(|json| serde_json::from_str::<PlayerPreferences>(&json).ok())
            .unwrap_or_default()
            .sanitized()
    }

    pub fn save(&self, preferences: &PlayerPreferences) -> io::Result<()> {
        let preferences = preferences.clone().sanitized();
        let parent = self.path.parent().unwrap_or_else(|| Path::new("."));
        fs::create_dir_all(parent)?;
        let temporary = self.path.with_extension("json.tmp");
        let json = serde_json::to_vec_pretty(&preferences)
            .map_err(|error| io::Error::new(io::ErrorKind::InvalidData, error))?;
        fs::write(&temporary, json)?;
        if self.path.exists() {
            fs::remove_file(&self.path)?;
        }
        fs::rename(temporary, &self.path)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn temporary_path(name: &str) -> PathBuf {
        std::env::temp_dir().join(format!("danmaku-player-{name}-{}.json", std::process::id()))
    }

    #[test]
    fn round_trips_preferences_without_credentials() {
        let path = temporary_path("round-trip");
        let store = PreferenceStore::at(path.clone());
        let expected = PlayerPreferences {
            language: Language::TraditionalChinese,
            volume_percent: 72,
            playback_rate: 1.25,
            auto_next: true,
            last_server_url: Some("http://192.168.1.2:8686".to_owned()),
            local_library_roots: vec!["W:/Anime".to_owned()],
            ..Default::default()
        };
        store.save(&expected).expect("preferences save");
        assert_eq!(store.load(), expected);
        let serialized = fs::read_to_string(&path).expect("saved JSON");
        assert!(!serialized.contains("token"));
        let _ = fs::remove_file(path);
    }

    #[test]
    fn malformed_or_out_of_range_values_fall_back_safely() {
        let path = temporary_path("sanitize");
        fs::write(
            &path,
            r#"{"volume_percent":255,"playback_rate":99.0,"last_server_url":"https://remote"}"#,
        )
        .expect("fixture write");
        let actual = PreferenceStore::at(path.clone()).load();
        assert_eq!(actual.volume_percent, 130);
        assert_eq!(actual.playback_rate, 2.0);
        assert_eq!(actual.last_server_url, None);
        let _ = fs::remove_file(path);
    }
}
