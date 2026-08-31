use std::path::Path;
#[cfg(windows)]
use std::path::PathBuf;

use serde_json::{Map, Value};

use crate::settings::{HeadlessAniRssMode, HeadlessAniRssSettings};
use crate::{LibraryServerError, Result};

#[derive(Debug)]
pub struct ManagedAniRss {
    #[cfg(windows)]
    child: std::process::Child,
}

impl ManagedAniRss {
    pub fn start(settings: &HeadlessAniRssSettings, data_directory: &Path) -> Result<Option<Self>> {
        if settings.mode != HeadlessAniRssMode::ManagedWindows {
            return Ok(None);
        }
        #[cfg(not(windows))]
        {
            let _ = data_directory;
            Err(LibraryServerError::new(
                "managed ANI-RSS is only available on Windows",
            ))
        }
        #[cfg(windows)]
        {
            Self::start_windows(settings, data_directory).map(Some)
        }
    }

    #[cfg(windows)]
    fn start_windows(settings: &HeadlessAniRssSettings, data_directory: &Path) -> Result<Self> {
        use std::os::windows::process::CommandExt;
        use std::process::{Command, Stdio};

        const CREATE_NO_WINDOW: u32 = 0x0800_0000;
        let executable = managed_executable(data_directory).ok_or_else(|| {
            LibraryServerError::new(format!(
                "managed ANI-RSS executable was not found; place ani-rss.exe in {} or set DANMAKU_ANI_RSS_EXECUTABLE",
                data_directory.join("ani-rss").display()
            ))
        })?;
        let config_directory = data_directory.join("ani-rss").join("config");
        std::fs::create_dir_all(&config_directory).map_err(|error| {
            LibraryServerError::with_context(
                error,
                "failed to create managed ANI-RSS config directory",
            )
        })?;
        let api_key = settings
            .api_key
            .as_deref()
            .map(str::trim)
            .filter(|value| !value.is_empty())
            .ok_or_else(|| LibraryServerError::new("managed ANI-RSS requires an API key"))?;
        sync_managed_config(&config_directory, api_key)?;
        let log_file = std::fs::OpenOptions::new()
            .create(true)
            .append(true)
            .open(data_directory.join("ani-rss").join("ani-rss.log"))
            .map_err(|error| {
                LibraryServerError::with_context(error, "failed to open ANI-RSS log")
            })?;
        let error_file = log_file.try_clone().map_err(|error| {
            LibraryServerError::with_context(error, "failed to clone ANI-RSS log")
        })?;
        let child = Command::new(&executable)
            .arg(format!("--server.port={}", settings.managed_port))
            .arg("--server.address=127.0.0.1")
            .arg(format!("--config={}", config_directory.display()))
            .env("CONFIG", &config_directory)
            .current_dir(executable.parent().unwrap_or(data_directory))
            .stdin(Stdio::null())
            .stdout(Stdio::from(log_file))
            .stderr(Stdio::from(error_file))
            .creation_flags(CREATE_NO_WINDOW)
            .spawn()
            .map_err(|error| {
                LibraryServerError::with_context(
                    error,
                    format!(
                        "failed to start managed ANI-RSS from {}",
                        executable.display()
                    ),
                )
            })?;
        Ok(Self { child })
    }
}

fn sync_managed_config(config_directory: &Path, api_key: &str) -> Result<()> {
    std::fs::create_dir_all(config_directory).map_err(|error| {
        LibraryServerError::with_context(error, "failed to create managed ANI-RSS config directory")
    })?;
    let config_file = config_directory.join("config.v2.json");
    let mut config = if config_file.is_file() {
        let bytes = std::fs::read(&config_file).map_err(|error| {
            LibraryServerError::with_context(error, "failed to read managed ANI-RSS config")
        })?;
        serde_json::from_slice::<Value>(&bytes)
            .map_err(|error| {
                LibraryServerError::with_context(error, "managed ANI-RSS config is invalid")
            })?
            .as_object()
            .cloned()
            .ok_or_else(|| LibraryServerError::new("managed ANI-RSS config must be an object"))?
    } else {
        Map::new()
    };
    config.insert("apiKey".to_owned(), Value::String(api_key.to_owned()));
    let body = serde_json::to_vec_pretty(&Value::Object(config))?;
    std::fs::write(&config_file, body).map_err(|error| {
        LibraryServerError::with_context(error, "failed to write managed ANI-RSS config")
    })
}

#[cfg(windows)]
fn managed_executable(data_directory: &Path) -> Option<PathBuf> {
    std::env::var_os("DANMAKU_ANI_RSS_EXECUTABLE")
        .map(PathBuf::from)
        .filter(|path| path.is_file())
        .or_else(|| {
            let path = data_directory.join("ani-rss").join("ani-rss.exe");
            path.is_file().then_some(path)
        })
        .or_else(|| {
            let path = std::env::current_exe()
                .ok()?
                .parent()?
                .join("ani-rss")
                .join("ani-rss.exe");
            path.is_file().then_some(path)
        })
}

impl Drop for ManagedAniRss {
    fn drop(&mut self) {
        #[cfg(windows)]
        {
            let _ = self.child.kill();
            let _ = self.child.wait();
        }
    }
}

#[cfg(test)]
mod tests {
    use std::sync::atomic::{AtomicU64, Ordering};

    use super::*;

    static TEMP_COUNTER: AtomicU64 = AtomicU64::new(0);

    #[test]
    fn managed_config_preserves_provider_settings_and_sets_the_shared_api_key() {
        let directory = std::env::temp_dir().join(format!(
            "danmaku-managed-ani-rss-{}-{}",
            std::process::id(),
            TEMP_COUNTER.fetch_add(1, Ordering::Relaxed)
        ));
        std::fs::create_dir_all(&directory).expect("temp config directory");
        std::fs::write(
            directory.join("config.v2.json"),
            br#"{"downloadToolType":"qBittorrent","apiKey":"old"}"#,
        )
        .expect("existing config writes");

        sync_managed_config(&directory, "shared-secret").expect("config synchronizes");

        let config: Value = serde_json::from_slice(
            &std::fs::read(directory.join("config.v2.json")).expect("config reads"),
        )
        .expect("config parses");
        assert_eq!(Some("shared-secret"), config["apiKey"].as_str());
        assert_eq!(Some("qBittorrent"), config["downloadToolType"].as_str());
        std::fs::remove_dir_all(directory).expect("temp config removes");
    }
}
