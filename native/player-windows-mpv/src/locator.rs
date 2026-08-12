use std::{
    env,
    ffi::OsStr,
    fmt,
    path::{Path, PathBuf},
};

pub const LIBMPV_PATH_ENV: &str = "DANMAKU_LIBMPV_PATH";
#[cfg(target_os = "macos")]
pub const LIBMPV_DLL_NAME: &str = "libmpv.2.dylib";
#[cfg(all(unix, not(target_os = "macos")))]
pub const LIBMPV_DLL_NAME: &str = "libmpv.so.2";
#[cfg(windows)]
pub const LIBMPV_DLL_NAME: &str = "libmpv-2.dll";

#[cfg(target_os = "macos")]
const LIBMPV_LIBRARY_NAMES: &[&str] = &["libmpv.2.dylib", "libmpv.dylib"];
#[cfg(all(unix, not(target_os = "macos")))]
const LIBMPV_LIBRARY_NAMES: &[&str] = &["libmpv.so.2", "libmpv.so.1", "libmpv.so"];
#[cfg(windows)]
const LIBMPV_LIBRARY_NAMES: &[&str] = &["libmpv-2.dll"];

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LibraryLocationError {
    pub searched_paths: Vec<PathBuf>,
}

impl fmt::Display for LibraryLocationError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(
            formatter,
            "libmpv was not found; searched {}",
            self.searched_paths
                .iter()
                .map(|path| path.display().to_string())
                .collect::<Vec<_>>()
                .join(", ")
        )
    }
}

impl std::error::Error for LibraryLocationError {}

pub fn candidate_paths(executable_dir: &Path, configured_path: Option<&OsStr>) -> Vec<PathBuf> {
    let mut candidates = Vec::new();

    if let Some(configured_path) = configured_path.filter(|path| !path.is_empty()) {
        let configured_path = PathBuf::from(configured_path);
        if configured_path.is_dir() {
            candidates.extend(
                LIBMPV_LIBRARY_NAMES
                    .iter()
                    .map(|name| configured_path.join(name)),
            );
        } else {
            candidates.push(configured_path);
        }
    }

    for directory in platform_library_directories(executable_dir) {
        for library_name in LIBMPV_LIBRARY_NAMES {
            let candidate = directory.join(library_name);
            if !candidates.contains(&candidate) {
                candidates.push(candidate);
            }
        }
    }

    candidates
}

fn platform_library_directories(executable_dir: &Path) -> Vec<PathBuf> {
    let mut directories = vec![executable_dir.to_path_buf()];
    #[cfg(target_os = "macos")]
    {
        directories.push(executable_dir.join("..").join("Frameworks"));
        directories.push(PathBuf::from("/opt/homebrew/opt/mpv/lib"));
        directories.push(PathBuf::from("/usr/local/opt/mpv/lib"));
    }
    directories
}

pub fn find_library(
    executable_dir: &Path,
    configured_path: Option<&OsStr>,
) -> Result<PathBuf, LibraryLocationError> {
    let searched_paths = candidate_paths(executable_dir, configured_path);

    searched_paths
        .iter()
        .find(|path| path.is_file())
        .cloned()
        .ok_or_else(|| LibraryLocationError { searched_paths })
}

pub fn find_library_for_current_process() -> Result<PathBuf, LibraryLocationError> {
    let executable = env::current_exe().unwrap_or_else(|_| PathBuf::from("."));
    let executable_dir = executable.parent().unwrap_or_else(|| Path::new("."));

    find_library(executable_dir, env::var_os(LIBMPV_PATH_ENV).as_deref())
}

#[cfg(test)]
mod tests {
    use super::{LIBMPV_DLL_NAME, LIBMPV_LIBRARY_NAMES, candidate_paths, find_library};
    use std::{ffi::OsStr, fs, path::Path};

    #[test]
    fn uses_configured_dll_before_the_executable_directory() {
        let candidates = candidate_paths(
            Path::new("C:/app"),
            Some(OsStr::new("C:/media/libmpv-custom.dll")),
        );

        assert_eq!(
            candidates.first(),
            Some(&Path::new("C:/media/libmpv-custom.dll").to_path_buf())
        );
        for library_name in LIBMPV_LIBRARY_NAMES {
            assert!(candidates.contains(&Path::new("C:/app").join(library_name)));
        }
    }

    #[test]
    fn expands_a_configured_directory() {
        let temp_dir = std::env::temp_dir().join(format!(
            "danmaku-libmpv-locator-directory-{}",
            std::process::id()
        ));
        fs::create_dir_all(&temp_dir).expect("create test directory");

        let candidates = candidate_paths(Path::new("C:/app"), Some(temp_dir.as_os_str()));

        assert_eq!(candidates[0], temp_dir.join(LIBMPV_DLL_NAME));
        for library_name in LIBMPV_LIBRARY_NAMES {
            assert!(candidates.contains(&temp_dir.join(library_name)));
        }
        fs::remove_dir(&temp_dir).expect("remove test directory");
    }

    #[test]
    fn reports_every_searched_path_when_the_dll_is_missing() {
        let executable_dir = Path::new("C:/app");
        let configured_path = OsStr::new("C:/media/libmpv-custom.dll");

        let error =
            find_library(executable_dir, Some(configured_path)).expect_err("missing library");

        assert_eq!(
            error.searched_paths,
            candidate_paths(executable_dir, Some(configured_path))
        );
    }

    #[cfg(target_os = "macos")]
    #[test]
    fn searches_app_frameworks_and_homebrew_on_macos() {
        let candidates =
            candidate_paths(Path::new("/Applications/Danmaku.app/Contents/MacOS"), None);

        assert!(
            candidates.contains(
                &Path::new("/Applications/Danmaku.app/Contents/MacOS/../Frameworks/libmpv.2.dylib")
                    .to_path_buf()
            )
        );
        assert!(
            candidates
                .contains(&Path::new("/opt/homebrew/opt/mpv/lib/libmpv.2.dylib").to_path_buf())
        );
        assert!(
            candidates.contains(&Path::new("/usr/local/opt/mpv/lib/libmpv.dylib").to_path_buf())
        );
    }
}
