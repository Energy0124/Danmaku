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

/// Dandanplay developer credentials used to sign match/comment requests.
///
/// Kept out of [`PlayerPreferences`] and in a dedicated store so the secret is
/// never written into the general preferences file that may be exported or
/// shown in screenshots.
#[derive(Clone, Debug, Default, Deserialize, PartialEq, Serialize)]
#[serde(default)]
pub struct DandanplayCredentials {
    pub app_id: String,
    pub app_secret: String,
}

impl DandanplayCredentials {
    pub fn sanitized(mut self) -> Self {
        self.app_id = self.app_id.trim().to_owned();
        self.app_secret = self.app_secret.trim().to_owned();
        self
    }

    /// Both halves are present, so the sidecar can build a signed resolver.
    pub fn is_complete(&self) -> bool {
        !self.app_id.trim().is_empty() && !self.app_secret.trim().is_empty()
    }

    /// Imports credentials from the same `local.properties` locations the Kotlin
    /// desktop app reads (`danmaku.dandanplay.appId`/`appSecret`), so a user who
    /// configured the old app keeps working without re-entering anything. The
    /// headless sidecar intentionally ignores these files, so the player reads
    /// them and injects the result explicitly.
    pub fn from_local_properties() -> Option<Self> {
        Self::from_local_properties_at(&local_properties_search_paths())
    }

    fn from_local_properties_at(paths: &[PathBuf]) -> Option<Self> {
        let mut app_id: Option<String> = None;
        let mut app_secret: Option<String> = None;
        for path in paths {
            let Ok(text) = fs::read_to_string(path) else {
                continue;
            };
            for line in text.lines() {
                let trimmed = line.trim();
                if trimmed.is_empty() || trimmed.starts_with('#') || trimmed.starts_with('!') {
                    continue;
                }
                let Some((key, value)) =
                    trimmed.split_once('=').or_else(|| trimmed.split_once(':'))
                else {
                    continue;
                };
                let value = value.trim();
                if value.is_empty() {
                    continue;
                }
                match key.trim() {
                    "danmaku.dandanplay.appId" => app_id = Some(value.to_owned()),
                    "danmaku.dandanplay.appSecret" => app_secret = Some(value.to_owned()),
                    _ => {}
                }
            }
        }
        let credentials = Self {
            app_id: app_id?,
            app_secret: app_secret?,
        }
        .sanitized();
        credentials.is_complete().then_some(credentials)
    }
}

/// The `local.properties` search order the Kotlin desktop app uses.
fn local_properties_search_paths() -> Vec<PathBuf> {
    let mut paths = Vec::new();
    if let Ok(cwd) = std::env::current_dir() {
        paths.push(cwd.join("local.properties"));
    }
    if let Some(local_app_data) = std::env::var_os("LOCALAPPDATA") {
        paths.push(
            PathBuf::from(local_app_data)
                .join("Danmaku")
                .join("local.properties"),
        );
    }
    if let Some(home) = std::env::var_os("USERPROFILE").or_else(|| std::env::var_os("HOME")) {
        paths.push(
            PathBuf::from(home)
                .join(".danmaku")
                .join("local.properties"),
        );
    }
    if let Some(explicit) = std::env::var_os("DANMAKU_LOCAL_PROPERTIES") {
        paths.push(PathBuf::from(explicit));
    }
    paths.dedup();
    paths
}

#[derive(Clone, Debug)]
pub struct CredentialStore {
    path: PathBuf,
}

impl CredentialStore {
    pub fn for_current_user() -> Self {
        let root = std::env::var_os("LOCALAPPDATA")
            .map(PathBuf::from)
            .or_else(|| std::env::current_dir().ok())
            .unwrap_or_else(|| PathBuf::from("."));
        Self {
            path: root.join("Danmaku").join("player-credentials.json"),
        }
    }

    #[cfg(test)]
    fn at(path: PathBuf) -> Self {
        Self { path }
    }

    pub fn load(&self) -> DandanplayCredentials {
        fs::read_to_string(&self.path)
            .ok()
            .and_then(|json| serde_json::from_str::<DandanplayCredentials>(&json).ok())
            .unwrap_or_default()
            .sanitized()
    }

    pub fn save(&self, credentials: &DandanplayCredentials) -> io::Result<()> {
        let credentials = credentials.clone().sanitized();
        let parent = self.path.parent().unwrap_or_else(|| Path::new("."));
        fs::create_dir_all(parent)?;
        let temporary = self.path.with_extension("json.tmp");
        let json = serde_json::to_vec_pretty(&credentials)
            .map_err(|error| io::Error::new(io::ErrorKind::InvalidData, error))?;
        fs::write(&temporary, json)?;
        if self.path.exists() {
            fs::remove_file(&self.path)?;
        }
        fs::rename(temporary, &self.path)
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
    fn credential_store_round_trips_and_trims() {
        let path = temporary_path("credentials");
        let store = CredentialStore::at(path.clone());
        assert!(!store.load().is_complete());
        store
            .save(&DandanplayCredentials {
                app_id: "  app-123  ".to_owned(),
                app_secret: " secret-xyz ".to_owned(),
            })
            .expect("credentials save");
        let loaded = store.load();
        assert_eq!(loaded.app_id, "app-123");
        assert_eq!(loaded.app_secret, "secret-xyz");
        assert!(loaded.is_complete());
        let _ = fs::remove_file(path);
    }

    #[test]
    fn imports_dandanplay_credentials_from_local_properties() {
        let path = temporary_path("local-properties");
        fs::write(
            &path,
            "sdk.dir=W\\:\\\\Android\\\\Sdk\n\
             # comment\n\
             danmaku.dandanplay.appId = app-77 \n\
             danmaku.dandanplay.appSecret= secret-88\n\
             danmaku.myanimelist.clientId=ignored\n",
        )
        .expect("fixture write");
        let credentials =
            DandanplayCredentials::from_local_properties_at(std::slice::from_ref(&path))
                .expect("credentials imported");
        assert_eq!(credentials.app_id, "app-77");
        assert_eq!(credentials.app_secret, "secret-88");

        // Missing either half yields nothing.
        fs::write(&path, "danmaku.dandanplay.appId=only-id\n").expect("fixture write");
        assert!(
            DandanplayCredentials::from_local_properties_at(std::slice::from_ref(&path)).is_none()
        );
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
