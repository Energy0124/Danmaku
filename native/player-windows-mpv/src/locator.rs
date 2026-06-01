use std::{
    env,
    ffi::OsStr,
    fmt,
    path::{Path, PathBuf},
};

pub const LIBMPV_PATH_ENV: &str = "DANMAKU_LIBMPV_PATH";
pub const LIBMPV_DLL_NAME: &str = "libmpv-2.dll";

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LibraryLocationError {
    pub searched_paths: Vec<PathBuf>,
}

impl fmt::Display for LibraryLocationError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(
            formatter,
            "{LIBMPV_DLL_NAME} was not found; searched {}",
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
    let mut candidates = Vec::with_capacity(2);

    if let Some(configured_path) = configured_path.filter(|path| !path.is_empty()) {
        let configured_path = PathBuf::from(configured_path);
        if configured_path.is_dir() {
            candidates.push(configured_path.join(LIBMPV_DLL_NAME));
        } else {
            candidates.push(configured_path);
        }
    }

    let executable_candidate = executable_dir.join(LIBMPV_DLL_NAME);
    if !candidates.contains(&executable_candidate) {
        candidates.push(executable_candidate);
    }

    candidates
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
    use super::{LIBMPV_DLL_NAME, candidate_paths, find_library};
    use std::{ffi::OsStr, fs, path::Path};

    #[test]
    fn uses_configured_dll_before_the_executable_directory() {
        let candidates = candidate_paths(
            Path::new("C:/app"),
            Some(OsStr::new("C:/media/libmpv-custom.dll")),
        );

        assert_eq!(
            candidates,
            vec![
                Path::new("C:/media/libmpv-custom.dll").to_path_buf(),
                Path::new("C:/app").join(LIBMPV_DLL_NAME),
            ]
        );
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
}
