//! Local packaged-server supervision for the unified Windows player.

use std::{
    fs::{self, File, OpenOptions},
    net::TcpListener,
    path::{Path, PathBuf},
    process::{Child, Command, Stdio},
    sync::mpsc::{self, Receiver, Sender},
    thread,
    time::{Duration, Instant},
};

use eframe::egui;
use serde::Deserialize;

use crate::net::http_get;
use crate::preferences::DandanplayCredentials;

const DEFAULT_BASE_URL: &str = "http://127.0.0.1:8686";
const DEFAULT_PORT: u16 = 8_686;
const READY_TIMEOUT: Duration = Duration::from_secs(180);
const READY_POLL_INTERVAL: Duration = Duration::from_millis(250);
const MAX_RESTARTS: u8 = 3;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum LocalHostOwnership {
    PlayerOwned,
    BackgroundHost,
    External,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum LocalHostStatus {
    Unavailable,
    NeedsSetup,
    Starting,
    Running {
        base_url: String,
        ownership: LocalHostOwnership,
    },
    Stopped,
    Failed(String),
}

impl LocalHostStatus {
    pub fn is_available(&self) -> bool {
        !matches!(self, Self::Unavailable)
    }

    pub fn is_player_owned_running(&self) -> bool {
        matches!(
            self,
            Self::Running {
                ownership: LocalHostOwnership::PlayerOwned,
                ..
            }
        )
    }

    pub fn is_background_running(&self) -> bool {
        matches!(
            self,
            Self::Running {
                ownership: LocalHostOwnership::BackgroundHost,
                ..
            }
        )
    }

    pub fn allows_player_management(&self) -> bool {
        !matches!(
            self,
            Self::Running {
                ownership: LocalHostOwnership::BackgroundHost | LocalHostOwnership::External,
                ..
            }
        )
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LocalConnection {
    pub base_url: String,
    pub pairing_token: Option<String>,
}

const BACKGROUND_HOST_CONFIG_NAME: &str = "background-host.json";
const BACKGROUND_HOST_SCHEMA_VERSION: u32 = 1;
const BACKGROUND_HOST_TASK_NAME: &str = "\\Danmaku\\Library Server";

#[derive(Clone, Debug, Deserialize, Eq, PartialEq)]
#[serde(rename_all = "camelCase")]
struct BackgroundHostConfig {
    schema_version: u32,
    task_name: String,
    base_url: String,
    library_roots: Vec<PathBuf>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ServerStatusProbe {
    #[serde(default)]
    host_mode: String,
}

#[derive(Clone, Debug)]
struct LocalServerPackage {
    executable: PathBuf,
    web_assets: Option<PathBuf>,
}

#[derive(Clone, Debug)]
enum LocalHostTarget {
    Packaged(LocalServerPackage),
    Background(BackgroundHostConfig),
}

impl LocalServerPackage {
    fn discover() -> Option<Self> {
        let current_executable = std::env::current_exe().ok()?;
        let executable_directory = current_executable.parent()?;
        let override_path = std::env::var_os("DANMAKU_SERVER_PATH").map(PathBuf::from);
        let candidates = override_path
            .into_iter()
            .chain([executable_directory.join(server_executable_name())]);
        let executable = candidates.into_iter().find(|path| path.is_file())?;
        let packaged_web = executable_directory.join("web");
        let repository_web = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
            .join("..")
            .join("..")
            .join("apps")
            .join("web-ui")
            .join("dist");
        let web_assets = [packaged_web, repository_web]
            .into_iter()
            .find(|path| path.is_dir());
        Some(Self {
            executable,
            web_assets,
        })
    }
}

pub struct LocalServerSupervisor {
    package: Option<LocalServerPackage>,
    data_directory: PathBuf,
    state: LocalHostStatus,
    generation: u64,
    receiver: Receiver<SupervisorEvent>,
    sender: Sender<SupervisorEvent>,
    child: Option<Child>,
    roots: Vec<PathBuf>,
    dandanplay: Option<DandanplayCredentials>,
    restart_count: u8,
    repaint: egui::Context,
}

impl LocalServerSupervisor {
    pub fn new(
        saved_roots: &[String],
        dandanplay: Option<DandanplayCredentials>,
        repaint: egui::Context,
    ) -> Self {
        let (sender, receiver) = mpsc::channel();
        let package = LocalServerPackage::discover();
        let data_directory = default_data_directory();
        let background_configured = data_directory.join(BACKGROUND_HOST_CONFIG_NAME).is_file();
        let state = if package.is_some() || background_configured {
            LocalHostStatus::NeedsSetup
        } else {
            LocalHostStatus::Unavailable
        };
        let mut supervisor = Self {
            package,
            data_directory,
            state,
            generation: 0,
            receiver,
            sender,
            child: None,
            roots: normalize_roots(saved_roots),
            dandanplay: dandanplay.filter(DandanplayCredentials::is_complete),
            restart_count: 0,
            repaint,
        };
        if supervisor.package.is_some()
            || supervisor
                .data_directory
                .join(BACKGROUND_HOST_CONFIG_NAME)
                .is_file()
        {
            let roots = supervisor.roots.clone();
            supervisor.begin(roots.clone(), true, !roots.is_empty());
        }
        supervisor
    }

    pub fn status(&self) -> &LocalHostStatus {
        &self.state
    }

    pub fn start(&mut self, root: PathBuf) -> Result<(), String> {
        self.ensure_player_management_allowed()?;
        if !root.is_dir() {
            return Err(format!("library folder does not exist: {}", root.display()));
        }
        self.restart_count = 0;
        self.begin(vec![root], false, true);
        Ok(())
    }

    pub fn restart(&mut self, roots: Vec<PathBuf>) -> Result<(), String> {
        self.ensure_player_management_allowed()?;
        if roots.is_empty() {
            return Err("choose at least one library folder".to_owned());
        }
        if let Some(invalid) = roots.iter().find(|root| !root.is_dir()) {
            return Err(format!(
                "library folder does not exist: {}",
                invalid.display()
            ));
        }
        self.restart_count = 0;
        self.stop_owned();
        self.begin(roots, false, true);
        Ok(())
    }

    pub fn stop(&mut self) {
        self.generation = self.generation.wrapping_add(1);
        self.stop_owned();
        self.state = LocalHostStatus::Stopped;
    }

    /// Updates the dandanplay credentials injected into future server launches.
    /// Restarting the server (or a fresh launch) is what actually applies them.
    pub fn set_dandanplay(&mut self, dandanplay: Option<DandanplayCredentials>) {
        self.dandanplay = dandanplay.filter(DandanplayCredentials::is_complete);
    }

    fn ensure_player_management_allowed(&self) -> Result<(), String> {
        match read_background_host_config(&self.data_directory) {
            Ok(Some(_)) => Err(
                "the local server is managed by the Danmaku background-host task; use manage-rust-library-background-host.ps1"
                    .to_owned(),
            ),
            Ok(None) => Ok(()),
            Err(error) => Err(error),
        }
    }

    pub fn poll(&mut self) -> Option<LocalConnection> {
        let mut connection = None;
        while let Ok(event) = self.receiver.try_recv() {
            match event {
                SupervisorEvent::NeedsSetup { generation } if generation == self.generation => {
                    self.state = LocalHostStatus::NeedsSetup;
                }
                SupervisorEvent::Ready {
                    generation,
                    connection: ready,
                    child,
                    ownership,
                } if generation == self.generation => {
                    self.child = child;
                    self.state = LocalHostStatus::Running {
                        base_url: ready.base_url.clone(),
                        ownership,
                    };
                    connection = Some(ready);
                }
                SupervisorEvent::Failed {
                    generation,
                    message,
                } if generation == self.generation => {
                    self.state = LocalHostStatus::Failed(message);
                }
                SupervisorEvent::Ready {
                    mut child,
                    generation: _,
                    connection: _,
                    ownership: _,
                } => {
                    stop_child(child.as_mut());
                }
                _ => {}
            }
        }

        let exited = self
            .child
            .as_mut()
            .and_then(|child| child.try_wait().ok().flatten());
        if let Some(status) = exited {
            self.child = None;
            if self.restart_count < MAX_RESTARTS && !self.roots.is_empty() {
                self.restart_count += 1;
                let roots = self.roots.clone();
                self.begin(roots, false, true);
            } else {
                self.state = LocalHostStatus::Failed(format!("local server exited with {status}"));
            }
        }
        connection
    }

    fn begin(&mut self, roots: Vec<PathBuf>, allow_attach: bool, start_if_missing: bool) {
        let background = match read_background_host_config(&self.data_directory) {
            Ok(background) => background,
            Err(error) => {
                self.state = LocalHostStatus::Failed(error);
                return;
            }
        };
        let target = match (background, self.package.clone()) {
            (Some(background), _) => LocalHostTarget::Background(background),
            (None, Some(package)) => LocalHostTarget::Packaged(package),
            (None, None) => {
                self.state = LocalHostStatus::Unavailable;
                return;
            }
        };
        self.generation = self.generation.wrapping_add(1);
        let generation = self.generation;
        self.roots = roots.clone();
        self.state = LocalHostStatus::Starting;
        let sender = self.sender.clone();
        let data_directory = self.data_directory.clone();
        let dandanplay = self.dandanplay.clone();
        let repaint = self.repaint.clone();
        thread::spawn(move || {
            let event = launch_or_attach(
                generation,
                target,
                data_directory,
                roots,
                dandanplay,
                allow_attach,
                start_if_missing,
            );
            if let Err(error) = sender.send(event)
                && let SupervisorEvent::Ready { mut child, .. } = error.0
            {
                stop_child(child.as_mut());
            }
            repaint.request_repaint();
        });
    }

    fn stop_owned(&mut self) {
        stop_child(self.child.as_mut());
        self.child = None;
    }
}

impl Drop for LocalServerSupervisor {
    fn drop(&mut self) {
        self.stop_owned();
    }
}

enum SupervisorEvent {
    NeedsSetup {
        generation: u64,
    },
    Ready {
        generation: u64,
        connection: LocalConnection,
        child: Option<Child>,
        ownership: LocalHostOwnership,
    },
    Failed {
        generation: u64,
        message: String,
    },
}

fn launch_or_attach(
    generation: u64,
    target: LocalHostTarget,
    data_directory: PathBuf,
    roots: Vec<PathBuf>,
    dandanplay: Option<DandanplayCredentials>,
    allow_attach: bool,
    start_if_missing: bool,
) -> SupervisorEvent {
    let package = match target {
        LocalHostTarget::Background(background) => {
            return wait_for_background_host(generation, &background, &data_directory);
        }
        LocalHostTarget::Packaged(package) => package,
    };
    if allow_attach && server_is_ready(DEFAULT_BASE_URL) {
        return SupervisorEvent::Ready {
            generation,
            connection: LocalConnection {
                base_url: DEFAULT_BASE_URL.to_owned(),
                pairing_token: read_pairing_token(&data_directory),
            },
            child: None,
            ownership: LocalHostOwnership::External,
        };
    }
    if !start_if_missing {
        return SupervisorEvent::NeedsSetup { generation };
    }
    match launch_server(package, data_directory, roots, dandanplay) {
        Ok((connection, child)) => SupervisorEvent::Ready {
            generation,
            connection,
            child: Some(child),
            ownership: LocalHostOwnership::PlayerOwned,
        },
        Err(message) => SupervisorEvent::Failed {
            generation,
            message,
        },
    }
}

fn launch_server(
    package: LocalServerPackage,
    data_directory: PathBuf,
    roots: Vec<PathBuf>,
    dandanplay: Option<DandanplayCredentials>,
) -> Result<(LocalConnection, Child), String> {
    fs::create_dir_all(&data_directory)
        .map_err(|error| format!("failed to create server data directory: {error}"))?;
    let port = choose_available_port()?;
    let base_url = format!("http://127.0.0.1:{port}");
    let log = open_log(&data_directory)?;
    let error_log = log
        .try_clone()
        .map_err(|error| format!("failed to open server error log: {error}"))?;
    let mut command = Command::new(&package.executable);
    command
        .arg("--data-dir")
        .arg(&data_directory)
        .arg("--port")
        .arg(port.to_string())
        .stdout(Stdio::from(log))
        .stderr(Stdio::from(error_log));
    // The sidecar reads these at startup via apply_dandanplay_local_defaults and
    // builds a signed resolver; without them the resolve route returns HTTP 502.
    if let Some(credentials) = dandanplay.filter(DandanplayCredentials::is_complete) {
        command
            .env("DANMAKU_DANDANPLAY_APP_ID", credentials.app_id.trim())
            .env(
                "DANMAKU_DANDANPLAY_APP_SECRET",
                credentials.app_secret.trim(),
            );
    }
    if let Some(web_assets) = &package.web_assets {
        command.arg("--web-assets-dir").arg(web_assets);
    }
    for root in &roots {
        command.arg("--root").arg(root);
    }
    let mut child = command
        .spawn()
        .map_err(|error| format!("failed to start local server: {error}"))?;
    let deadline = Instant::now() + READY_TIMEOUT;
    while Instant::now() < deadline {
        if server_is_ready(&base_url) {
            return Ok((
                LocalConnection {
                    base_url,
                    pairing_token: read_pairing_token(&data_directory),
                },
                child,
            ));
        }
        if let Some(status) = child
            .try_wait()
            .map_err(|error| format!("failed to inspect local server: {error}"))?
        {
            return Err(format!(
                "local server exited before becoming ready: {status}"
            ));
        }
        thread::sleep(READY_POLL_INTERVAL);
    }
    stop_child(Some(&mut child));
    Err("timed out waiting for local server startup".to_owned())
}

fn choose_available_port() -> Result<u16, String> {
    if TcpListener::bind(("127.0.0.1", DEFAULT_PORT)).is_ok() {
        return Ok(DEFAULT_PORT);
    }
    let listener = TcpListener::bind(("127.0.0.1", 0))
        .map_err(|error| format!("failed to select a local server port: {error}"))?;
    listener
        .local_addr()
        .map(|address| address.port())
        .map_err(|error| format!("failed to read selected server port: {error}"))
}

fn server_is_ready(base_url: &str) -> bool {
    http_get(base_url, "/api/server/status")
        .ok()
        .and_then(|body| serde_json::from_str::<ServerStatusProbe>(&body).ok())
        .is_some_and(|status| status.host_mode == "headless-server")
}

fn read_background_host_config(
    data_directory: &Path,
) -> Result<Option<BackgroundHostConfig>, String> {
    let path = data_directory.join(BACKGROUND_HOST_CONFIG_NAME);
    if !path.is_file() {
        return Ok(None);
    }
    let body = fs::read_to_string(&path)
        .map_err(|error| format!("failed to read background-host configuration: {error}"))?;
    let config = serde_json::from_str::<BackgroundHostConfig>(&body)
        .map_err(|error| format!("background-host configuration is invalid: {error}"))?;
    if config.schema_version != BACKGROUND_HOST_SCHEMA_VERSION {
        return Err(format!(
            "unsupported background-host schema version {}",
            config.schema_version
        ));
    }
    if config.task_name != BACKGROUND_HOST_TASK_NAME {
        return Err(format!(
            "background-host task name must be {BACKGROUND_HOST_TASK_NAME}"
        ));
    }
    if config.base_url != DEFAULT_BASE_URL {
        return Err(format!(
            "background-host base URL must be {DEFAULT_BASE_URL}"
        ));
    }
    if config.library_roots.is_empty() {
        return Err("background-host configuration has no library roots".to_owned());
    }
    if config.library_roots.iter().any(|root| !root.is_absolute()) {
        return Err("background-host library roots must be absolute".to_owned());
    }
    Ok(Some(config))
}

fn wait_for_background_host(
    generation: u64,
    background: &BackgroundHostConfig,
    data_directory: &Path,
) -> SupervisorEvent {
    let deadline = Instant::now() + READY_TIMEOUT;
    while Instant::now() < deadline {
        if server_is_ready(&background.base_url) {
            return SupervisorEvent::Ready {
                generation,
                connection: LocalConnection {
                    base_url: background.base_url.clone(),
                    pairing_token: read_pairing_token(data_directory),
                },
                child: None,
                ownership: LocalHostOwnership::BackgroundHost,
            };
        }
        thread::sleep(READY_POLL_INTERVAL);
    }
    SupervisorEvent::Failed {
        generation,
        message: format!(
            "the Danmaku background host ({}) did not become ready; run manage-rust-library-background-host.ps1 with -Action Status",
            background.task_name
        ),
    }
}

fn open_log(data_directory: &Path) -> Result<File, String> {
    OpenOptions::new()
        .create(true)
        .append(true)
        .open(data_directory.join("sidecar.log"))
        .map_err(|error| format!("failed to open local server log: {error}"))
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ServerSettingsSnapshot {
    pairing_token: Option<String>,
}

fn read_pairing_token(data_directory: &Path) -> Option<String> {
    let body = fs::read_to_string(data_directory.join("server-settings.json")).ok()?;
    serde_json::from_str::<ServerSettingsSnapshot>(&body)
        .ok()?
        .pairing_token
        .filter(|token| !token.trim().is_empty())
}

fn normalize_roots(roots: &[String]) -> Vec<PathBuf> {
    roots
        .iter()
        .map(|root| PathBuf::from(root.trim()))
        .filter(|root| root.is_dir())
        .collect()
}

fn default_data_directory() -> PathBuf {
    std::env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .or_else(|| std::env::current_dir().ok())
        .unwrap_or_else(|| PathBuf::from("."))
        .join("Danmaku")
        .join("server")
}

fn server_executable_name() -> &'static str {
    if cfg!(windows) {
        "library-server.exe"
    } else {
        "library-server"
    }
}

fn stop_child(child: Option<&mut Child>) {
    if let Some(child) = child
        && child.try_wait().ok().flatten().is_none()
    {
        let _ = child.kill();
        let _ = child.wait();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn normalizes_only_existing_library_roots() {
        let root = std::env::temp_dir();
        let roots = normalize_roots(&[
            root.to_string_lossy().into_owned(),
            "Z:/definitely-missing-danmaku-root".to_owned(),
        ]);
        assert_eq!(roots, vec![root]);
    }

    #[test]
    fn reads_pairing_token_without_exposing_other_settings() {
        let directory =
            std::env::temp_dir().join(format!("danmaku-host-settings-{}", std::process::id()));
        fs::create_dir_all(&directory).expect("settings dir");
        fs::write(
            directory.join("server-settings.json"),
            r#"{"pairingToken":"123456","libraryRoots":["W:/Anime"]}"#,
        )
        .expect("settings write");
        assert_eq!(read_pairing_token(&directory), Some("123456".to_owned()));
        let _ = fs::remove_dir_all(directory);
    }

    #[test]
    fn port_selector_returns_a_bindable_port() {
        let port = choose_available_port().expect("port");
        assert!(port > 0);
    }

    #[test]
    fn reads_valid_background_host_configuration() {
        let directory = std::env::temp_dir().join(format!(
            "danmaku-background-host-config-{}",
            std::process::id()
        ));
        fs::create_dir_all(&directory).expect("config dir");
        fs::write(
            directory.join(BACKGROUND_HOST_CONFIG_NAME),
            r#"{
                "schemaVersion": 1,
                "taskName": "\\Danmaku\\Library Server",
                "baseUrl": "http://127.0.0.1:8686",
                "libraryRoots": ["W:/Anime"]
            }"#,
        )
        .expect("config write");

        let config = read_background_host_config(&directory)
            .expect("config parses")
            .expect("config exists");
        assert_eq!(config.schema_version, 1);
        assert_eq!(config.library_roots, vec![PathBuf::from("W:/Anime")]);
        let _ = fs::remove_dir_all(directory);
    }

    #[test]
    fn rejects_incomplete_background_host_configuration() {
        let directory = std::env::temp_dir().join(format!(
            "danmaku-background-host-invalid-{}",
            std::process::id()
        ));
        fs::create_dir_all(&directory).expect("config dir");
        fs::write(
            directory.join(BACKGROUND_HOST_CONFIG_NAME),
            r#"{"schemaVersion":1,"taskName":"\\Danmaku\\Library Server","baseUrl":"http://127.0.0.1:8686","libraryRoots":[]}"#,
        )
        .expect("config write");

        let error = read_background_host_config(&directory).expect_err("config should fail");
        assert!(error.contains("no library roots"));
        let _ = fs::remove_dir_all(directory);
    }

    #[test]
    fn only_player_owned_hosts_allow_process_controls() {
        let player_owned = LocalHostStatus::Running {
            base_url: DEFAULT_BASE_URL.to_owned(),
            ownership: LocalHostOwnership::PlayerOwned,
        };
        let background = LocalHostStatus::Running {
            base_url: DEFAULT_BASE_URL.to_owned(),
            ownership: LocalHostOwnership::BackgroundHost,
        };
        assert!(player_owned.is_player_owned_running());
        assert!(player_owned.allows_player_management());
        assert!(!background.is_player_owned_running());
        assert!(background.is_background_running());
        assert!(!background.allows_player_management());
    }
}
