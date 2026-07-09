use std::fs;
use std::path::PathBuf;

use serde::Serialize;
use serde_json::{Map, Value};

use crate::{LibraryServerError, Result};

const SCHEMA_VERSION: u32 = 1;
const DEFAULT_DANDANPLAY_BASE_URL: &str = "https://api.dandanplay.net";
const DEFAULT_DANDANPLAY_CACHE_MAX_AGE_DAYS: u32 = 30;
const MIN_DANDANPLAY_CACHE_MAX_AGE_DAYS: u32 = 1;
const DEFAULT_BANGUMI_BASE_URL: &str = "https://api.bgm.tv/";
const DEFAULT_BANGUMI_USER_AGENT: &str = "Danmaku/0.1 (https://github.com/Energy0124/Danmaku)";

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SettingsStore {
    file: PathBuf,
}

impl SettingsStore {
    pub fn new(file: impl Into<PathBuf>) -> Self {
        Self { file: file.into() }
    }

    pub fn load_or_create<F>(
        &self,
        explicit_pairing_token: Option<&str>,
        mut token_generator: F,
    ) -> Result<HeadlessServerSettings>
    where
        F: FnMut() -> String,
    {
        let loaded = self.load()?;
        let pairing_token = explicit_pairing_token
            .map(str::to_owned)
            .or_else(|| {
                loaded
                    .as_ref()
                    .map(|settings| settings.pairing_token.clone())
            })
            .unwrap_or_else(&mut token_generator);
        if pairing_token.trim().is_empty() {
            return Err(LibraryServerError::new("pairingToken must not be blank"));
        }

        let settings = HeadlessServerSettings {
            pairing_token,
            library_roots: loaded
                .as_ref()
                .map(|settings| settings.library_roots.clone())
                .unwrap_or_default(),
            dandanplay: loaded
                .as_ref()
                .map(|settings| settings.dandanplay.clone())
                .unwrap_or_default(),
            external_anime: loaded
                .as_ref()
                .map(|settings| settings.external_anime.clone())
                .unwrap_or_default(),
        };
        self.write_snapshot(&settings)?;
        Ok(settings)
    }

    pub fn load(&self) -> Result<Option<HeadlessServerSettings>> {
        if !self.file.is_file() {
            return Ok(None);
        }

        // The JVM store wraps the full load in runCatching and returns null for
        // malformed or unsupported snapshots, then rewrites a fresh schema-v1 file.
        let text = match fs::read_to_string(&self.file) {
            Ok(text) => text,
            Err(_) => return Ok(None),
        };
        let value = match serde_json::from_str::<Value>(&text) {
            Ok(value) => value,
            Err(_) => return Ok(None),
        };
        Ok(settings_from_value(&value))
    }

    fn write_snapshot(&self, settings: &HeadlessServerSettings) -> Result<()> {
        if let Some(parent) = self.file.parent() {
            fs::create_dir_all(parent).map_err(|error| {
                LibraryServerError::with_context(
                    error,
                    format!("failed to create settings directory {}", parent.display()),
                )
            })?;
        }

        let temp = self
            .file
            .with_file_name(format!("{}.tmp", self.file_name_for_temp()?));
        let body = serde_json::to_string_pretty(&SettingsSnapshot::from(settings))?;
        fs::write(&temp, body).map_err(|error| {
            LibraryServerError::with_context(
                error,
                format!("failed to write settings snapshot {}", temp.display()),
            )
        })?;
        fs::rename(&temp, &self.file).map_err(|error| {
            LibraryServerError::with_context(
                error,
                format!(
                    "failed to replace settings snapshot {}",
                    self.file.display()
                ),
            )
        })
    }

    fn file_name_for_temp(&self) -> Result<String> {
        self.file
            .file_name()
            .map(|name| name.to_string_lossy().into_owned())
            .ok_or_else(|| {
                LibraryServerError::new(format!(
                    "settings path must include a file name: {}",
                    self.file.display()
                ))
            })
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct HeadlessServerSettings {
    pub pairing_token: String,
    pub library_roots: Vec<PathBuf>,
    pub dandanplay: HeadlessDandanplayProviderSettings,
    pub external_anime: HeadlessExternalAnimeProviderSettings,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum HeadlessDandanplayAuthenticationMode {
    Signed,
    Credential,
}

impl HeadlessDandanplayAuthenticationMode {
    pub fn jvm_name(self) -> &'static str {
        match self {
            Self::Signed => "SIGNED",
            Self::Credential => "CREDENTIAL",
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct HeadlessDandanplayProviderSettings {
    pub base_url: String,
    pub app_id: Option<String>,
    pub app_secret: Option<String>,
    pub has_app_secret: bool,
    pub authentication_mode: HeadlessDandanplayAuthenticationMode,
    pub cache_max_age_days: u32,
}

impl Default for HeadlessDandanplayProviderSettings {
    fn default() -> Self {
        Self {
            base_url: DEFAULT_DANDANPLAY_BASE_URL.to_owned(),
            app_id: None,
            app_secret: None,
            has_app_secret: false,
            authentication_mode: HeadlessDandanplayAuthenticationMode::Signed,
            cache_max_age_days: DEFAULT_DANDANPLAY_CACHE_MAX_AGE_DAYS,
        }
    }
}

impl HeadlessDandanplayProviderSettings {
    pub fn has_credentials(&self) -> bool {
        self.app_id.is_some() && self.app_secret.is_some()
    }

    pub fn is_fetch_enabled(&self) -> bool {
        self.has_credentials() || self.base_url != DEFAULT_DANDANPLAY_BASE_URL
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct HeadlessExternalAnimeProviderSettings {
    pub my_anime_list_client_id: Option<String>,
    pub has_my_anime_list_client_secret: bool,
    pub has_my_anime_list_access_token: bool,
    pub bangumi_base_url: String,
    pub bangumi_user_agent: String,
    pub has_bangumi_access_token: bool,
}

impl Default for HeadlessExternalAnimeProviderSettings {
    fn default() -> Self {
        Self {
            my_anime_list_client_id: None,
            has_my_anime_list_client_secret: false,
            has_my_anime_list_access_token: false,
            bangumi_base_url: DEFAULT_BANGUMI_BASE_URL.to_owned(),
            bangumi_user_agent: DEFAULT_BANGUMI_USER_AGENT.to_owned(),
            has_bangumi_access_token: false,
        }
    }
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct SettingsSnapshot {
    schema_version: u32,
    pairing_token: String,
    library_roots: Vec<String>,
    dandanplay: DandanplaySettingsSnapshot,
    external_anime: ExternalAnimeSettingsSnapshot,
}

impl From<&HeadlessServerSettings> for SettingsSnapshot {
    fn from(settings: &HeadlessServerSettings) -> Self {
        Self {
            schema_version: SCHEMA_VERSION,
            pairing_token: settings.pairing_token.clone(),
            library_roots: settings
                .library_roots
                .iter()
                .map(|path| path.to_string_lossy().into_owned())
                .collect(),
            dandanplay: DandanplaySettingsSnapshot::from(&settings.dandanplay),
            external_anime: ExternalAnimeSettingsSnapshot::from(&settings.external_anime),
        }
    }
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct DandanplaySettingsSnapshot {
    base_url: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    app_id: Option<String>,
    has_app_secret: bool,
    authentication_mode: String,
    cache_max_age_days: u32,
}

impl From<&HeadlessDandanplayProviderSettings> for DandanplaySettingsSnapshot {
    fn from(settings: &HeadlessDandanplayProviderSettings) -> Self {
        Self {
            base_url: settings.base_url.clone(),
            app_id: settings.app_id.clone(),
            has_app_secret: settings.has_app_secret,
            authentication_mode: settings.authentication_mode.jvm_name().to_owned(),
            cache_max_age_days: settings.cache_max_age_days,
        }
    }
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct ExternalAnimeSettingsSnapshot {
    #[serde(skip_serializing_if = "Option::is_none")]
    my_anime_list_client_id: Option<String>,
    has_my_anime_list_client_secret: bool,
    has_my_anime_list_access_token: bool,
    bangumi_base_url: String,
    bangumi_user_agent: String,
    has_bangumi_access_token: bool,
}

impl From<&HeadlessExternalAnimeProviderSettings> for ExternalAnimeSettingsSnapshot {
    fn from(settings: &HeadlessExternalAnimeProviderSettings) -> Self {
        Self {
            my_anime_list_client_id: settings.my_anime_list_client_id.clone(),
            has_my_anime_list_client_secret: settings.has_my_anime_list_client_secret,
            has_my_anime_list_access_token: settings.has_my_anime_list_access_token,
            bangumi_base_url: settings.bangumi_base_url.clone(),
            bangumi_user_agent: settings.bangumi_user_agent.clone(),
            has_bangumi_access_token: settings.has_bangumi_access_token,
        }
    }
}

pub fn generate_pairing_token() -> String {
    const TOKEN_BOUND: u32 = 1_000_000;
    const REJECTION_ZONE: u32 = u32::MAX - (u32::MAX % TOKEN_BOUND);

    loop {
        let mut bytes = [0_u8; 4];
        getrandom::fill(&mut bytes).expect("operating system randomness should be available");
        let candidate = u32::from_ne_bytes(bytes);
        if candidate < REJECTION_ZONE {
            return format!("{:06}", candidate % TOKEN_BOUND);
        }
    }
}

fn settings_from_value(value: &Value) -> Option<HeadlessServerSettings> {
    let root = value.as_object()?;
    let schema_version = int_or_null(root, "schemaVersion")?;
    if schema_version != SCHEMA_VERSION as i64 {
        return None;
    }
    let pairing_token = string_or_null(root, "pairingToken")?;
    let library_roots = library_roots_or_null(root.get("libraryRoots"))?;
    let dandanplay = match root.get("dandanplay").and_then(Value::as_object) {
        Some(object) => dandanplay_settings_or_null(object)?,
        None => HeadlessDandanplayProviderSettings::default(),
    };
    let external_anime = match root.get("externalAnime").and_then(Value::as_object) {
        Some(object) => external_anime_settings_or_null(object)?,
        None => HeadlessExternalAnimeProviderSettings::default(),
    };

    Some(HeadlessServerSettings {
        pairing_token,
        library_roots,
        dandanplay,
        external_anime,
    })
}

fn library_roots_or_null(value: Option<&Value>) -> Option<Vec<PathBuf>> {
    match value {
        None => Some(Vec::new()),
        Some(Value::Array(values)) => {
            let mut roots = Vec::new();
            for value in values {
                match value {
                    Value::String(path) if !path.trim().is_empty() => {
                        roots.push(PathBuf::from(path));
                    }
                    Value::String(_) | Value::Null => {}
                    _ => return None,
                }
            }
            Some(roots)
        }
        Some(_) => None,
    }
}

fn dandanplay_settings_or_null(
    object: &Map<String, Value>,
) -> Option<HeadlessDandanplayProviderSettings> {
    let base_url =
        string_or_null(object, "baseUrl").unwrap_or_else(|| DEFAULT_DANDANPLAY_BASE_URL.to_owned());
    if !is_http_base_url(&base_url) {
        return None;
    }
    let app_id = string_or_null(object, "appId");
    let app_secret = string_or_null(object, "appSecret");
    let has_app_secret =
        app_secret.is_some() || boolean_or_null(object, "hasAppSecret") == Some(true);
    let authentication_mode = string_or_null(object, "authenticationMode")
        .as_deref()
        .map(authentication_mode_or_default)
        .unwrap_or(HeadlessDandanplayAuthenticationMode::Signed);
    let cache_max_age_days = int_or_null(object, "cacheMaxAgeDays")
        .and_then(|value| u32::try_from(value).ok())
        .filter(|value| *value >= MIN_DANDANPLAY_CACHE_MAX_AGE_DAYS)
        .unwrap_or(DEFAULT_DANDANPLAY_CACHE_MAX_AGE_DAYS);

    Some(HeadlessDandanplayProviderSettings {
        base_url,
        app_id,
        app_secret,
        has_app_secret,
        authentication_mode,
        cache_max_age_days,
    })
}

fn external_anime_settings_or_null(
    object: &Map<String, Value>,
) -> Option<HeadlessExternalAnimeProviderSettings> {
    let my_anime_list_client_id = string_or_null(object, "myAnimeListClientId");
    let my_anime_list_client_secret = string_or_null(object, "myAnimeListClientSecret");
    let my_anime_list_access_token = string_or_null(object, "myAnimeListAccessToken");
    let bangumi_base_url = string_or_null(object, "bangumiBaseUrl")
        .unwrap_or_else(|| DEFAULT_BANGUMI_BASE_URL.to_owned());
    if !is_https_base_url(&bangumi_base_url) {
        return None;
    }
    let bangumi_user_agent = string_or_null(object, "bangumiUserAgent")
        .unwrap_or_else(|| DEFAULT_BANGUMI_USER_AGENT.to_owned());
    if bangumi_user_agent.is_empty() {
        return None;
    }
    let bangumi_access_token = string_or_null(object, "bangumiAccessToken");

    Some(HeadlessExternalAnimeProviderSettings {
        my_anime_list_client_id,
        has_my_anime_list_client_secret: my_anime_list_client_secret.is_some()
            || boolean_or_null(object, "hasMyAnimeListClientSecret") == Some(true),
        has_my_anime_list_access_token: my_anime_list_access_token.is_some()
            || boolean_or_null(object, "hasMyAnimeListAccessToken") == Some(true),
        bangumi_base_url,
        bangumi_user_agent,
        has_bangumi_access_token: bangumi_access_token.is_some()
            || boolean_or_null(object, "hasBangumiAccessToken") == Some(true),
    })
}

fn authentication_mode_or_default(value: &str) -> HeadlessDandanplayAuthenticationMode {
    match value.trim().to_ascii_uppercase().as_str() {
        "CREDENTIAL" => HeadlessDandanplayAuthenticationMode::Credential,
        _ => HeadlessDandanplayAuthenticationMode::Signed,
    }
}

fn string_or_null(object: &Map<String, Value>, name: &str) -> Option<String> {
    object
        .get(name)
        .and_then(Value::as_str)
        .filter(|value| !value.trim().is_empty())
        .map(ToOwned::to_owned)
}

fn int_or_null(object: &Map<String, Value>, name: &str) -> Option<i64> {
    object.get(name).and_then(Value::as_i64)
}

fn boolean_or_null(object: &Map<String, Value>, name: &str) -> Option<bool> {
    object.get(name).and_then(Value::as_bool)
}

fn is_http_base_url(value: &str) -> bool {
    has_host_after_scheme(value, "http://") || has_host_after_scheme(value, "https://")
}

fn is_https_base_url(value: &str) -> bool {
    has_host_after_scheme(value, "https://")
}

fn has_host_after_scheme(value: &str, scheme: &str) -> bool {
    let Some(rest) = value.trim().strip_prefix(scheme) else {
        return false;
    };
    !rest.is_empty() && !rest.starts_with('/')
}

#[cfg(test)]
mod tests {
    use std::fs;
    use std::sync::atomic::{AtomicU64, Ordering};

    use serde_json::Value;

    use super::*;

    static TEMP_COUNTER: AtomicU64 = AtomicU64::new(0);

    #[test]
    fn reads_provider_settings_and_rewrites_without_raw_secrets() {
        let temp = temp_dir("danmaku-settings-provider");
        let file = temp.join("server-settings.json");
        fs::write(
            &file,
            r#"{
  "schemaVersion": 1,
  "pairingToken": "123456",
  "libraryRoots": ["W:/Anime"],
  "dandanplay": {
    "baseUrl": "https://worker.example/dandanplay",
    "appId": "app-id",
    "appSecret": "raw-secret",
    "hasAppSecret": true,
    "authenticationMode": "credential",
    "cacheMaxAgeDays": 7
  },
  "externalAnime": {
    "myAnimeListClientId": "mal-client-id",
    "myAnimeListClientSecret": "raw-mal-secret",
    "hasMyAnimeListClientSecret": true,
    "myAnimeListAccessToken": "raw-mal-token",
    "hasMyAnimeListAccessToken": true,
    "bangumiBaseUrl": "https://api.bgm.tv/",
    "bangumiUserAgent": "Danmaku QA",
    "bangumiAccessToken": "raw-bangumi-token",
    "hasBangumiAccessToken": true
  }
}"#,
        )
        .expect("settings fixture should write");

        let settings = SettingsStore::new(&file)
            .load_or_create(None, || panic!("token should be loaded"))
            .expect("settings should load");

        assert_eq!("123456", settings.pairing_token);
        assert_eq!(vec![PathBuf::from("W:/Anime")], settings.library_roots);
        assert_eq!(
            "https://worker.example/dandanplay",
            settings.dandanplay.base_url
        );
        assert_eq!(Some("app-id".to_owned()), settings.dandanplay.app_id);
        assert_eq!(
            Some("raw-secret".to_owned()),
            settings.dandanplay.app_secret
        );
        assert!(settings.dandanplay.has_app_secret);
        assert_eq!(
            HeadlessDandanplayAuthenticationMode::Credential,
            settings.dandanplay.authentication_mode
        );
        assert_eq!(7, settings.dandanplay.cache_max_age_days);
        assert_eq!(
            Some("mal-client-id".to_owned()),
            settings.external_anime.my_anime_list_client_id
        );
        assert!(settings.external_anime.has_my_anime_list_client_secret);
        assert!(settings.external_anime.has_my_anime_list_access_token);
        assert_eq!(
            "https://api.bgm.tv/",
            settings.external_anime.bangumi_base_url
        );
        assert_eq!("Danmaku QA", settings.external_anime.bangumi_user_agent);
        assert!(settings.external_anime.has_bangumi_access_token);

        let rewritten = fs::read_to_string(&file).expect("settings should rewrite");
        assert!(!rewritten.contains("raw-secret"));
        assert!(!rewritten.contains("raw-mal-secret"));
        assert!(!rewritten.contains("raw-mal-token"));
        assert!(!rewritten.contains("raw-bangumi-token"));
        let json = serde_json::from_str::<Value>(&rewritten).expect("rewritten settings are json");
        assert_eq!(json["schemaVersion"], 1);
        assert_eq!(json["pairingToken"], "123456");

        fs::remove_dir_all(temp).expect("temp dir should delete");
    }

    #[test]
    fn generated_pairing_token_round_trips() {
        let temp = temp_dir("danmaku-settings-token");
        let file = temp.join("server-settings.json");
        let store = SettingsStore::new(&file);

        let first = store
            .load_or_create(None, || "654321".to_owned())
            .expect("settings should create");
        let second = store
            .load_or_create(None, || "111111".to_owned())
            .expect("settings should reload");

        assert_eq!("654321", first.pairing_token);
        assert_eq!("654321", second.pairing_token);
        assert!(file.is_file());

        fs::remove_dir_all(temp).expect("temp dir should delete");
    }

    #[test]
    fn explicit_pairing_token_overrides_persisted_token() {
        let temp = temp_dir("danmaku-settings-explicit-token");
        let file = temp.join("server-settings.json");
        let store = SettingsStore::new(&file);
        store
            .load_or_create(None, || "654321".to_owned())
            .expect("settings should create");

        let settings = store
            .load_or_create(Some("222333"), || "111111".to_owned())
            .expect("settings should reload");

        assert_eq!("222333", settings.pairing_token);
        assert_eq!(
            "222333",
            serde_json::from_str::<Value>(
                &fs::read_to_string(&file).expect("settings should read")
            )
            .expect("settings json should parse")["pairingToken"]
        );

        fs::remove_dir_all(temp).expect("temp dir should delete");
    }

    fn temp_dir(prefix: &str) -> PathBuf {
        let id = TEMP_COUNTER.fetch_add(1, Ordering::Relaxed);
        let path = std::env::temp_dir().join(format!("{prefix}-{}-{id}", std::process::id()));
        let _ = fs::remove_dir_all(&path);
        fs::create_dir_all(&path).expect("temp dir should create");
        path
    }
}
