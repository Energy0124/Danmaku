//! Windows installer update integration.
//!
//! The public state is platform-neutral so the settings screen remains
//! buildable on macOS. Velopack itself is linked only into Windows builds.

pub const UPDATE_REPOSITORY: &str = "https://github.com/Energy0124/Danmaku";
pub const LATEST_INSTALLER_URL: &str = "https://github.com/Energy0124/Danmaku/releases/latest";

#[derive(Clone, Debug, PartialEq, Eq)]
pub enum UpdateStatus {
    Unavailable {
        current_version: String,
    },
    Idle {
        current_version: String,
    },
    Checking {
        current_version: String,
    },
    Available {
        current_version: String,
        version: String,
        notes: String,
    },
    Downloading {
        current_version: String,
        version: String,
        progress: u8,
    },
    Ready {
        current_version: String,
        version: String,
    },
    Failed {
        current_version: String,
        message: String,
    },
}

impl UpdateStatus {
    pub fn current_version(&self) -> &str {
        match self {
            Self::Unavailable { current_version }
            | Self::Idle { current_version }
            | Self::Checking { current_version }
            | Self::Available {
                current_version, ..
            }
            | Self::Downloading {
                current_version, ..
            }
            | Self::Ready {
                current_version, ..
            }
            | Self::Failed {
                current_version, ..
            } => current_version,
        }
    }
}

pub fn automatic_updates_enabled(
    smoke_test: bool,
    screenshot_qa: bool,
    onboarding_qa: bool,
) -> bool {
    !(smoke_test || screenshot_qa || onboarding_qa)
}

#[cfg(windows)]
mod platform {
    use std::{
        env,
        ffi::OsString,
        path::Path,
        process::Command,
        sync::mpsc::{self, Receiver},
        thread,
    };

    use eframe::egui;
    use velopack::{
        UpdateCheck, UpdateInfo, UpdateManager, VelopackApp, VelopackAsset, sources::GithubSource,
    };

    use super::{UPDATE_REPOSITORY, UpdateStatus};

    enum WorkerEvent {
        Checked(Option<UpdateInfo>),
        Downloaded(VelopackAsset),
        Failed(String),
    }

    pub struct UpdateService {
        manager: Option<UpdateManager>,
        status: UpdateStatus,
        worker_rx: Receiver<WorkerEvent>,
        worker_tx: mpsc::Sender<WorkerEvent>,
        progress_rx: Option<Receiver<i16>>,
        available: Option<UpdateInfo>,
        ready: Option<VelopackAsset>,
        prompt_available: bool,
        restart_after_download: bool,
    }

    impl UpdateService {
        pub fn new(enabled: bool, ctx: &egui::Context) -> Self {
            let current_version = env!("CARGO_PKG_VERSION").to_owned();
            let (worker_tx, worker_rx) = mpsc::channel();
            let mut service = Self {
                manager: None,
                status: UpdateStatus::Unavailable {
                    current_version: current_version.clone(),
                },
                worker_rx,
                worker_tx,
                progress_rx: None,
                available: None,
                ready: None,
                prompt_available: false,
                restart_after_download: false,
            };
            if !enabled {
                return service;
            }

            let source = GithubSource::new(UPDATE_REPOSITORY, None, false);
            let Ok(manager) = UpdateManager::new(source, None, None) else {
                // Development and legacy portable builds do not have a
                // Velopack locator manifest, so updates intentionally remain
                // unavailable rather than surfacing an error on every launch.
                return service;
            };
            if manager.get_is_portable() {
                return service;
            }
            service.status = UpdateStatus::Idle {
                current_version: manager.get_current_version_as_string(),
            };
            if let Some(asset) = manager.get_update_pending_restart() {
                service.status = UpdateStatus::Ready {
                    current_version: service.status.current_version().to_owned(),
                    version: asset.Version.clone(),
                };
                service.ready = Some(asset);
                service.prompt_available = true;
            }
            service.manager = Some(manager);
            if service.ready.is_none() {
                service.check(ctx);
            }
            service
        }

        pub fn status(&self) -> &UpdateStatus {
            &self.status
        }

        pub fn should_prompt(&self) -> bool {
            self.prompt_available
                && matches!(
                    self.status,
                    UpdateStatus::Available { .. } | UpdateStatus::Ready { .. }
                )
        }

        pub fn dismiss_prompt(&mut self) {
            self.prompt_available = false;
            self.restart_after_download = false;
        }

        pub fn check(&mut self, ctx: &egui::Context) {
            let Some(manager) = self.manager.clone() else {
                return;
            };
            if matches!(
                self.status,
                UpdateStatus::Checking { .. } | UpdateStatus::Downloading { .. }
            ) {
                return;
            }
            let current_version = self.status.current_version().to_owned();
            self.status = UpdateStatus::Checking { current_version };
            self.available = None;
            self.ready = None;
            self.restart_after_download = false;
            let tx = self.worker_tx.clone();
            let ctx = ctx.clone();
            thread::spawn(move || {
                let event = match manager.check_for_updates() {
                    Ok(UpdateCheck::UpdateAvailable(update)) => WorkerEvent::Checked(Some(*update)),
                    Ok(UpdateCheck::NoUpdateAvailable | UpdateCheck::RemoteIsEmpty) => {
                        WorkerEvent::Checked(None)
                    }
                    Err(error) => WorkerEvent::Failed(error.to_string()),
                };
                let _ = tx.send(event);
                ctx.request_repaint();
            });
        }

        pub fn download(&mut self, ctx: &egui::Context) {
            let (Some(manager), Some(update)) = (self.manager.clone(), self.available.clone())
            else {
                return;
            };
            let current_version = self.status.current_version().to_owned();
            let version = update.TargetFullRelease.Version.clone();
            self.status = UpdateStatus::Downloading {
                current_version,
                version,
                progress: 0,
            };
            self.prompt_available = false;
            self.restart_after_download = true;
            let (progress_tx, progress_rx) = mpsc::channel();
            self.progress_rx = Some(progress_rx);
            let tx = self.worker_tx.clone();
            let ctx = ctx.clone();
            thread::spawn(move || {
                let event = match manager.download_updates(&update, Some(progress_tx)) {
                    Ok(()) => WorkerEvent::Downloaded(update.TargetFullRelease),
                    Err(error) => WorkerEvent::Failed(error.to_string()),
                };
                let _ = tx.send(event);
                ctx.request_repaint();
            });
        }

        /// Starts the external updater and returns `true` when the window
        /// should close. Velopack waits for this process, allowing normal Rust
        /// drops to stop playback and the player-owned local server.
        pub fn install_and_restart(&mut self) -> bool {
            let (Some(manager), Some(asset)) = (&self.manager, &self.ready) else {
                return false;
            };
            match manager.wait_exit_then_apply_updates(asset, false, true, Vec::<OsString>::new()) {
                Ok(()) => {
                    self.prompt_available = false;
                    true
                }
                Err(error) => {
                    self.status = UpdateStatus::Failed {
                        current_version: self.status.current_version().to_owned(),
                        message: error.to_string(),
                    };
                    false
                }
            }
        }

        pub fn poll(&mut self, ctx: &egui::Context) -> bool {
            if let Some(progress_rx) = &self.progress_rx {
                while let Ok(progress) = progress_rx.try_recv() {
                    if let UpdateStatus::Downloading {
                        progress: value, ..
                    } = &mut self.status
                    {
                        *value = progress.clamp(0, 100) as u8;
                    }
                }
            }
            while let Ok(event) = self.worker_rx.try_recv() {
                let current_version = self.status.current_version().to_owned();
                match event {
                    WorkerEvent::Checked(Some(update)) => {
                        let asset = &update.TargetFullRelease;
                        self.status = UpdateStatus::Available {
                            current_version,
                            version: asset.Version.clone(),
                            notes: release_notes(asset),
                        };
                        self.available = Some(update);
                        self.prompt_available = true;
                    }
                    WorkerEvent::Checked(None) => {
                        self.status = UpdateStatus::Idle { current_version };
                    }
                    WorkerEvent::Downloaded(asset) => {
                        self.status = UpdateStatus::Ready {
                            current_version,
                            version: asset.Version.clone(),
                        };
                        self.ready = Some(asset);
                        self.progress_rx = None;
                        self.prompt_available = true;
                    }
                    WorkerEvent::Failed(message) => {
                        self.status = UpdateStatus::Failed {
                            current_version,
                            message,
                        };
                        self.progress_rx = None;
                    }
                }
                ctx.request_repaint();
            }
            if self.restart_after_download && self.ready.is_some() {
                self.restart_after_download = false;
                return self.install_and_restart();
            }
            false
        }
    }

    fn release_notes(asset: &VelopackAsset) -> String {
        let notes = asset.NotesMarkdown.trim();
        if notes.is_empty() {
            asset.NotesHtml.trim().to_owned()
        } else {
            notes.to_owned()
        }
    }

    pub fn run_startup_hooks() {
        VelopackApp::build()
            .set_auto_apply_on_startup(false)
            .on_before_uninstall_fast_callback(|_| {
                if let Err(error) = run_background_host_action("Uninstall") {
                    eprintln!("{error}");
                    std::process::exit(1);
                }
            })
            .on_restarted(|_| {
                if let Err(error) = run_background_host_action("Refresh") {
                    eprintln!("{error}");
                }
            })
            .run();
    }

    fn run_background_host_action(action: &str) -> Result<(), String> {
        let Some(directory) = env::current_exe()
            .ok()
            .and_then(|path| path.parent().map(Path::to_owned))
        else {
            return Ok(());
        };
        let manager = directory.join("manage-rust-library-background-host.ps1");
        if !manager.is_file() {
            return Ok(());
        }
        let powershell = env::var_os("SystemRoot")
            .map(|root| Path::new(&root).join("System32/WindowsPowerShell/v1.0/powershell.exe"))
            .unwrap_or_else(|| "powershell.exe".into());
        use std::os::windows::process::CommandExt;
        const CREATE_NO_WINDOW: u32 = 0x0800_0000;
        let status = Command::new(powershell)
            .args([
                "-NoLogo",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-WindowStyle",
                "Hidden",
                "-File",
            ])
            .arg(manager)
            .args(["-Action", action])
            .creation_flags(CREATE_NO_WINDOW)
            .status()
            .map_err(|error| format!("Background host {action} failed to start: {error}"))?;
        if status.success() {
            Ok(())
        } else {
            Err(format!(
                "Background host {action} failed with exit code {}.",
                status
                    .code()
                    .map_or_else(|| "unknown".to_owned(), |code| code.to_string())
            ))
        }
    }

    #[cfg(test)]
    mod tests {
        use super::*;

        fn service(status: UpdateStatus) -> UpdateService {
            let (worker_tx, worker_rx) = mpsc::channel();
            UpdateService {
                manager: None,
                status,
                worker_rx,
                worker_tx,
                progress_rx: None,
                available: None,
                ready: None,
                prompt_available: false,
                restart_after_download: false,
            }
        }

        fn asset(version: &str, notes: &str) -> VelopackAsset {
            VelopackAsset {
                PackageId: "app.danmaku.player".to_owned(),
                Version: version.to_owned(),
                Type: "Full".to_owned(),
                FileName: format!("app.danmaku.player-{version}-full.nupkg"),
                NotesMarkdown: notes.to_owned(),
                ..VelopackAsset::default()
            }
        }

        #[test]
        fn checked_update_retains_notes_and_opens_prompt() {
            let mut service = service(UpdateStatus::Checking {
                current_version: "1.0.0".to_owned(),
            });
            let update_asset = asset("1.1.0", "## Fixed\n\n- Playback.");
            let update = UpdateInfo {
                TargetFullRelease: update_asset,
                ..UpdateInfo::default()
            };
            service
                .worker_tx
                .send(WorkerEvent::Checked(Some(update)))
                .unwrap();
            assert!(!service.poll(&egui::Context::default()));
            assert!(matches!(
                service.status(),
                UpdateStatus::Available { version, notes, .. }
                    if version == "1.1.0" && notes.contains("Playback")
            ));
            assert!(service.should_prompt());
            service.dismiss_prompt();
            assert!(!service.should_prompt());
        }

        #[test]
        fn download_progress_and_ready_state_are_reduced_without_blocking_ui_poll() {
            let mut service = service(UpdateStatus::Downloading {
                current_version: "1.0.0".to_owned(),
                version: "1.1.0".to_owned(),
                progress: 0,
            });
            let (progress_tx, progress_rx) = mpsc::channel();
            progress_tx.send(64).unwrap();
            service.progress_rx = Some(progress_rx);
            assert!(!service.poll(&egui::Context::default()));
            assert!(matches!(
                service.status(),
                UpdateStatus::Downloading { progress: 64, .. }
            ));
            service
                .worker_tx
                .send(WorkerEvent::Downloaded(asset("1.1.0", "")))
                .unwrap();
            assert!(!service.poll(&egui::Context::default()));
            assert!(
                matches!(service.status(), UpdateStatus::Ready { version, .. } if version == "1.1.0")
            );
        }

        #[test]
        fn worker_failure_is_retryable_state_not_a_prompt() {
            let mut service = service(UpdateStatus::Checking {
                current_version: "1.0.0".to_owned(),
            });
            service
                .worker_tx
                .send(WorkerEvent::Failed("offline".to_owned()))
                .unwrap();
            assert!(!service.poll(&egui::Context::default()));
            assert!(
                matches!(service.status(), UpdateStatus::Failed { message, .. } if message == "offline")
            );
            assert!(!service.should_prompt());
        }
    }
}

#[cfg(windows)]
pub use platform::{UpdateService, run_startup_hooks};

#[cfg(not(windows))]
pub struct UpdateService {
    status: UpdateStatus,
}

#[cfg(not(windows))]
impl UpdateService {
    pub fn new(_enabled: bool, _ctx: &eframe::egui::Context) -> Self {
        Self {
            status: UpdateStatus::Unavailable {
                current_version: env!("CARGO_PKG_VERSION").to_owned(),
            },
        }
    }
    pub fn status(&self) -> &UpdateStatus {
        &self.status
    }
    pub fn should_prompt(&self) -> bool {
        false
    }
    pub fn dismiss_prompt(&mut self) {}
    pub fn check(&mut self, _ctx: &eframe::egui::Context) {}
    pub fn download(&mut self, _ctx: &eframe::egui::Context) {}
    pub fn install_and_restart(&mut self) -> bool {
        false
    }
    pub fn poll(&mut self, _ctx: &eframe::egui::Context) -> bool {
        false
    }
}

#[cfg(not(windows))]
pub fn run_startup_hooks() {}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn automatic_checks_skip_all_qa_modes() {
        assert!(automatic_updates_enabled(false, false, false));
        assert!(!automatic_updates_enabled(true, false, false));
        assert!(!automatic_updates_enabled(false, true, false));
        assert!(!automatic_updates_enabled(false, false, true));
    }

    #[test]
    fn every_status_retains_the_running_version() {
        let status = UpdateStatus::Downloading {
            current_version: "1.2.3".to_owned(),
            version: "1.3.0".to_owned(),
            progress: 42,
        };
        assert_eq!(status.current_version(), "1.2.3");
    }
}
