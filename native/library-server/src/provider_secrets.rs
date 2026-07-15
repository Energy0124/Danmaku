use std::fmt;
use std::fs;
use std::path::PathBuf;
use std::sync::Arc;

use serde::{Deserialize, Serialize};

use crate::settings::HeadlessServerSettings;
use crate::{LibraryServerError, Result};

const SCHEMA_VERSION: u32 = 1;

pub trait SecretProtector: Send + Sync {
    fn protect(&self, plaintext: &[u8]) -> Result<Vec<u8>>;
    fn unprotect(&self, ciphertext: &[u8]) -> Result<Vec<u8>>;
}

#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct ProviderSecrets {
    pub dandanplay_app_secret: Option<String>,
    pub my_anime_list_client_secret: Option<String>,
    pub my_anime_list_access_token: Option<String>,
    pub bangumi_access_token: Option<String>,
}

impl ProviderSecrets {
    pub fn from_settings(settings: &HeadlessServerSettings) -> Self {
        Self {
            dandanplay_app_secret: settings.dandanplay.app_secret.clone(),
            my_anime_list_client_secret: settings
                .external_anime
                .my_anime_list_client_secret
                .clone(),
            my_anime_list_access_token: settings.external_anime.my_anime_list_access_token.clone(),
            bangumi_access_token: settings.external_anime.bangumi_access_token.clone(),
        }
    }

    pub fn merge_into(&self, settings: &mut HeadlessServerSettings) {
        if let Some(secret) = &self.dandanplay_app_secret {
            settings.dandanplay.app_secret = Some(secret.clone());
        }
        settings.dandanplay.has_app_secret =
            settings.dandanplay.app_secret.is_some() || settings.dandanplay.has_app_secret;
        if let Some(secret) = &self.my_anime_list_client_secret {
            settings.external_anime.my_anime_list_client_secret = Some(secret.clone());
        }
        settings.external_anime.has_my_anime_list_client_secret = settings
            .external_anime
            .my_anime_list_client_secret
            .is_some()
            || settings.external_anime.has_my_anime_list_client_secret;
        if let Some(token) = &self.my_anime_list_access_token {
            settings.external_anime.my_anime_list_access_token = Some(token.clone());
        }
        settings.external_anime.has_my_anime_list_access_token =
            settings.external_anime.my_anime_list_access_token.is_some()
                || settings.external_anime.has_my_anime_list_access_token;
        if let Some(token) = &self.bangumi_access_token {
            settings.external_anime.bangumi_access_token = Some(token.clone());
        }
        settings.external_anime.has_bangumi_access_token =
            settings.external_anime.bangumi_access_token.is_some()
                || settings.external_anime.has_bangumi_access_token;
    }

    fn is_empty(&self) -> bool {
        self.dandanplay_app_secret.is_none()
            && self.my_anime_list_client_secret.is_none()
            && self.my_anime_list_access_token.is_none()
            && self.bangumi_access_token.is_none()
    }
}

#[derive(Clone)]
pub struct ProviderSecretStore {
    file: PathBuf,
    protector: Arc<dyn SecretProtector>,
}

impl fmt::Debug for ProviderSecretStore {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_struct("ProviderSecretStore")
            .field("file", &self.file)
            .finish_non_exhaustive()
    }
}

impl ProviderSecretStore {
    pub fn platform(file: impl Into<PathBuf>) -> Self {
        Self {
            file: file.into(),
            protector: Arc::new(PlatformSecretProtector),
        }
    }

    #[cfg(test)]
    pub(crate) fn with_protector(
        file: impl Into<PathBuf>,
        protector: Arc<dyn SecretProtector>,
    ) -> Self {
        Self {
            file: file.into(),
            protector,
        }
    }

    pub fn load(&self) -> Result<ProviderSecrets> {
        if !self.file.is_file() {
            return Ok(ProviderSecrets::default());
        }
        let text = fs::read_to_string(&self.file).map_err(|error| {
            LibraryServerError::with_context(
                error,
                format!(
                    "failed to read provider secret store {}",
                    self.file.display()
                ),
            )
        })?;
        let snapshot: ProviderSecretSnapshot = serde_json::from_str(&text).map_err(|error| {
            LibraryServerError::with_context(
                error,
                format!(
                    "failed to parse provider secret store {}",
                    self.file.display()
                ),
            )
        })?;
        if snapshot.schema_version != SCHEMA_VERSION {
            return Err(LibraryServerError::new(format!(
                "unsupported provider secret store schema {}",
                snapshot.schema_version
            )));
        }
        Ok(ProviderSecrets {
            dandanplay_app_secret: self.decrypt(snapshot.dandanplay_app_secret)?,
            my_anime_list_client_secret: self.decrypt(snapshot.my_anime_list_client_secret)?,
            my_anime_list_access_token: self.decrypt(snapshot.my_anime_list_access_token)?,
            bangumi_access_token: self.decrypt(snapshot.bangumi_access_token)?,
        })
    }

    pub fn save(&self, secrets: &ProviderSecrets) -> Result<()> {
        if secrets.is_empty() {
            if self.file.exists() {
                fs::remove_file(&self.file).map_err(|error| {
                    LibraryServerError::with_context(
                        error,
                        format!(
                            "failed to remove provider secret store {}",
                            self.file.display()
                        ),
                    )
                })?;
            }
            return Ok(());
        }
        if let Some(parent) = self.file.parent() {
            fs::create_dir_all(parent).map_err(|error| {
                LibraryServerError::with_context(
                    error,
                    format!(
                        "failed to create provider secret directory {}",
                        parent.display()
                    ),
                )
            })?;
        }
        let snapshot = ProviderSecretSnapshot {
            schema_version: SCHEMA_VERSION,
            dandanplay_app_secret: self.encrypt(secrets.dandanplay_app_secret.as_deref())?,
            my_anime_list_client_secret: self
                .encrypt(secrets.my_anime_list_client_secret.as_deref())?,
            my_anime_list_access_token: self
                .encrypt(secrets.my_anime_list_access_token.as_deref())?,
            bangumi_access_token: self.encrypt(secrets.bangumi_access_token.as_deref())?,
        };
        let body = serde_json::to_string_pretty(&snapshot)?;
        let temp = self.file.with_file_name(format!(
            "{}.tmp",
            self.file
                .file_name()
                .ok_or_else(|| LibraryServerError::new("provider secret path needs a file name"))?
                .to_string_lossy()
        ));
        fs::write(&temp, body).map_err(|error| {
            LibraryServerError::with_context(
                error,
                format!("failed to write provider secret store {}", temp.display()),
            )
        })?;
        fs::rename(&temp, &self.file).map_err(|error| {
            LibraryServerError::with_context(
                error,
                format!(
                    "failed to replace provider secret store {}",
                    self.file.display()
                ),
            )
        })
    }

    fn encrypt(&self, value: Option<&str>) -> Result<Option<String>> {
        value
            .map(|value| self.protector.protect(value.as_bytes()).map(hex_encode))
            .transpose()
    }

    fn decrypt(&self, value: Option<String>) -> Result<Option<String>> {
        value
            .map(|value| {
                let ciphertext = hex_decode(&value)?;
                let plaintext = self.protector.unprotect(&ciphertext)?;
                String::from_utf8(plaintext).map_err(|error| {
                    LibraryServerError::with_context(error, "provider secret is not valid UTF-8")
                })
            })
            .transpose()
    }
}

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ProviderSecretSnapshot {
    schema_version: u32,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    dandanplay_app_secret: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    my_anime_list_client_secret: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    my_anime_list_access_token: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    bangumi_access_token: Option<String>,
}

fn hex_encode(bytes: Vec<u8>) -> String {
    const HEX: &[u8; 16] = b"0123456789abcdef";
    let mut encoded = String::with_capacity(bytes.len() * 2);
    for byte in bytes {
        encoded.push(HEX[(byte >> 4) as usize] as char);
        encoded.push(HEX[(byte & 0x0f) as usize] as char);
    }
    encoded
}

fn hex_decode(value: &str) -> Result<Vec<u8>> {
    if !value.len().is_multiple_of(2) {
        return Err(LibraryServerError::new(
            "provider secret ciphertext is not valid hexadecimal",
        ));
    }
    value
        .as_bytes()
        .chunks_exact(2)
        .map(|pair| {
            let high = hex_nibble(pair[0])?;
            let low = hex_nibble(pair[1])?;
            Ok((high << 4) | low)
        })
        .collect()
}

fn hex_nibble(value: u8) -> Result<u8> {
    match value {
        b'0'..=b'9' => Ok(value - b'0'),
        b'a'..=b'f' => Ok(value - b'a' + 10),
        b'A'..=b'F' => Ok(value - b'A' + 10),
        _ => Err(LibraryServerError::new(
            "provider secret ciphertext is not valid hexadecimal",
        )),
    }
}

#[cfg(windows)]
#[derive(Debug)]
struct PlatformSecretProtector;

#[cfg(windows)]
impl SecretProtector for PlatformSecretProtector {
    fn protect(&self, plaintext: &[u8]) -> Result<Vec<u8>> {
        use std::ptr;

        use windows_sys::Win32::Foundation::LocalFree;
        use windows_sys::Win32::Security::Cryptography::{
            CRYPT_INTEGER_BLOB, CRYPTPROTECT_UI_FORBIDDEN, CryptProtectData,
        };

        let length = u32::try_from(plaintext.len())
            .map_err(|_| LibraryServerError::new("provider secret is too large"))?;
        let input = CRYPT_INTEGER_BLOB {
            cbData: length,
            pbData: plaintext.as_ptr().cast_mut(),
        };
        let mut output = CRYPT_INTEGER_BLOB {
            cbData: 0,
            pbData: ptr::null_mut(),
        };
        let succeeded = unsafe {
            CryptProtectData(
                &input,
                ptr::null(),
                ptr::null(),
                ptr::null(),
                ptr::null(),
                CRYPTPROTECT_UI_FORBIDDEN,
                &mut output,
            )
        };
        if succeeded == 0 {
            return Err(LibraryServerError::with_context(
                std::io::Error::last_os_error(),
                "Windows DPAPI failed to protect provider secret",
            ));
        }
        let protected = unsafe {
            let bytes = std::slice::from_raw_parts(output.pbData, output.cbData as usize).to_vec();
            let _ = LocalFree(output.pbData.cast());
            bytes
        };
        Ok(protected)
    }

    fn unprotect(&self, ciphertext: &[u8]) -> Result<Vec<u8>> {
        use std::ptr;

        use windows_sys::Win32::Foundation::LocalFree;
        use windows_sys::Win32::Security::Cryptography::{
            CRYPT_INTEGER_BLOB, CRYPTPROTECT_UI_FORBIDDEN, CryptUnprotectData,
        };

        let length = u32::try_from(ciphertext.len())
            .map_err(|_| LibraryServerError::new("provider secret is too large"))?;
        let input = CRYPT_INTEGER_BLOB {
            cbData: length,
            pbData: ciphertext.as_ptr().cast_mut(),
        };
        let mut output = CRYPT_INTEGER_BLOB {
            cbData: 0,
            pbData: ptr::null_mut(),
        };
        let succeeded = unsafe {
            CryptUnprotectData(
                &input,
                ptr::null_mut(),
                ptr::null(),
                ptr::null(),
                ptr::null(),
                CRYPTPROTECT_UI_FORBIDDEN,
                &mut output,
            )
        };
        if succeeded == 0 {
            return Err(LibraryServerError::with_context(
                std::io::Error::last_os_error(),
                "Windows DPAPI failed to unprotect provider secret",
            ));
        }
        let plaintext = unsafe {
            let bytes = std::slice::from_raw_parts(output.pbData, output.cbData as usize).to_vec();
            let _ = LocalFree(output.pbData.cast());
            bytes
        };
        Ok(plaintext)
    }
}

#[cfg(not(windows))]
#[derive(Debug)]
struct PlatformSecretProtector;

#[cfg(not(windows))]
impl SecretProtector for PlatformSecretProtector {
    fn protect(&self, _plaintext: &[u8]) -> Result<Vec<u8>> {
        Err(LibraryServerError::new(
            "secure provider credential storage is only available on Windows",
        ))
    }

    fn unprotect(&self, _ciphertext: &[u8]) -> Result<Vec<u8>> {
        Err(LibraryServerError::new(
            "secure provider credential storage is only available on Windows",
        ))
    }
}

#[cfg(test)]
mod tests {
    use std::sync::atomic::{AtomicU64, Ordering};

    use super::*;

    static TEMP_COUNTER: AtomicU64 = AtomicU64::new(0);

    #[derive(Debug)]
    struct ReversingProtector;

    impl SecretProtector for ReversingProtector {
        fn protect(&self, plaintext: &[u8]) -> Result<Vec<u8>> {
            Ok(plaintext.iter().rev().map(|byte| byte ^ 0xa5).collect())
        }

        fn unprotect(&self, ciphertext: &[u8]) -> Result<Vec<u8>> {
            Ok(ciphertext.iter().rev().map(|byte| byte ^ 0xa5).collect())
        }
    }

    #[test]
    fn round_trips_without_writing_raw_secrets() {
        let temp = temp_dir("danmaku-provider-secret-store");
        let file = temp.join("provider-secrets.json");
        let store = ProviderSecretStore::with_protector(file.clone(), Arc::new(ReversingProtector));
        let secrets = ProviderSecrets {
            dandanplay_app_secret: Some("dandanplay-secret".to_owned()),
            my_anime_list_client_secret: Some("mal-secret".to_owned()),
            my_anime_list_access_token: Some("mal-token".to_owned()),
            bangumi_access_token: Some("bangumi-token".to_owned()),
        };

        store.save(&secrets).expect("secrets should save");
        let raw = fs::read_to_string(&file).expect("secret snapshot should exist");
        for secret in [
            "dandanplay-secret",
            "mal-secret",
            "mal-token",
            "bangumi-token",
        ] {
            assert!(!raw.contains(secret));
        }
        assert_eq!(secrets, store.load().expect("secrets should load"));

        store
            .save(&ProviderSecrets::default())
            .expect("empty secrets should clear");
        assert!(!file.exists());
        fs::remove_dir_all(temp).expect("temp should delete");
    }

    #[test]
    fn rejects_invalid_ciphertext() {
        let temp = temp_dir("danmaku-provider-secret-store-invalid");
        let file = temp.join("provider-secrets.json");
        fs::write(&file, r#"{"schemaVersion":1,"dandanplayAppSecret":"xyz"}"#)
            .expect("fixture should write");
        let store = ProviderSecretStore::with_protector(file, Arc::new(ReversingProtector));

        assert!(
            store
                .load()
                .expect_err("invalid ciphertext should fail")
                .to_string()
                .contains("hexadecimal")
        );
        fs::remove_dir_all(temp).expect("temp should delete");
    }

    fn temp_dir(prefix: &str) -> PathBuf {
        let id = TEMP_COUNTER.fetch_add(1, Ordering::Relaxed);
        let path = std::env::temp_dir().join(format!("{prefix}-{}-{id}", std::process::id()));
        let _ = fs::remove_dir_all(&path);
        fs::create_dir_all(&path).expect("temp dir should create");
        path
    }
}
