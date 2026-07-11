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

const DEFAULT_BASE_URL: &str = "http://127.0.0.1:8686";
const DEFAULT_PORT: u16 = 8_686;
const READY_TIMEOUT: Duration = Duration::from_secs(180);
const READY_POLL_INTERVAL: Duration = Duration::from_millis(250);
const MAX_RESTARTS: u8 = 3;

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum LocalHostStatus {
    Unavailable,
    NeedsSetup,
    Starting,
    Running { base_url: String, managed: bool },
    Stopped,
    Failed(String),
}

impl LocalHostStatus {
    pub fn is_available(&self) -> bool {
        !matches!(self, Self::Unavailable)
    }

    pub fn is_managed_running(&self) -> bool {
        matches!(self, Self::Running { managed: true, .. })
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LocalConnection {
    pub base_url: String,
    pub pairing_token: Option<String>,
}

#[derive(Clone, Debug)]
struct LocalServerPackage {
    executable: PathBuf,
    web_assets: Option<PathBuf>,
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
    restart_count: u8,
    repaint: egui::Context,
}

impl LocalServerSupervisor {
    pub fn new(saved_roots: &[String], repaint: egui::Context) -> Self {
        let (sender, receiver) = mpsc::channel();
        let package = LocalServerPackage::discover();
        let state = if package.is_some() {
            LocalHostStatus::NeedsSetup
        } else {
            LocalHostStatus::Unavailable
        };
        let mut supervisor = Self {
            package,
            data_directory: default_data_directory(),
            state,
            generation: 0,
            receiver,
            sender,
            child: None,
            roots: normalize_roots(saved_roots),
            restart_count: 0,
            repaint,
        };
        if supervisor.package.is_some() {
            let roots = supervisor.roots.clone();
            supervisor.begin(roots.clone(), true, !roots.is_empty());
        }
        supervisor
    }

    pub fn status(&self) -> &LocalHostStatus {
        &self.state
    }

    pub fn start(&mut self, root: PathBuf) -> Result<(), String> {
        if !root.is_dir() {
            return Err(format!("library folder does not exist: {}", root.display()));
        }
        self.restart_count = 0;
        self.begin(vec![root], false, true);
        Ok(())
    }

    pub fn restart(&mut self, roots: Vec<PathBuf>) -> Result<(), String> {
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
                } if generation == self.generation => {
                    self.child = child;
                    self.state = LocalHostStatus::Running {
                        base_url: ready.base_url.clone(),
                        managed: self.child.is_some(),
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
        let Some(package) = self.package.clone() else {
            self.state = LocalHostStatus::Unavailable;
            return;
        };
        self.generation = self.generation.wrapping_add(1);
        let generation = self.generation;
        self.roots = roots.clone();
        self.state = LocalHostStatus::Starting;
        let sender = self.sender.clone();
        let data_directory = self.data_directory.clone();
        let repaint = self.repaint.clone();
        thread::spawn(move || {
            let event = launch_or_attach(
                generation,
                package,
                data_directory,
                roots,
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
    },
    Failed {
        generation: u64,
        message: String,
    },
}

fn launch_or_attach(
    generation: u64,
    package: LocalServerPackage,
    data_directory: PathBuf,
    roots: Vec<PathBuf>,
    allow_attach: bool,
    start_if_missing: bool,
) -> SupervisorEvent {
    if allow_attach && server_is_ready(DEFAULT_BASE_URL) {
        return SupervisorEvent::Ready {
            generation,
            connection: LocalConnection {
                base_url: DEFAULT_BASE_URL.to_owned(),
                pairing_token: read_pairing_token(&data_directory),
            },
            child: None,
        };
    }
    if !start_if_missing {
        return SupervisorEvent::NeedsSetup { generation };
    }

    match launch_server(package, data_directory, roots) {
        Ok((connection, child)) => SupervisorEvent::Ready {
            generation,
            connection,
            child: Some(child),
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
    http_get(base_url, "/api/server/status").is_ok()
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
}
