use std::collections::HashSet;
use std::fs::{self, File, OpenOptions};
use std::path::{Path, PathBuf};
use std::sync::{Mutex, OnceLock};

use crate::{LibraryServerError, Result};

const LOCK_FILE_NAME: &str = ".danmaku-host.lock";
static LOCKED_PATHS: OnceLock<Mutex<HashSet<PathBuf>>> = OnceLock::new();

#[derive(Debug)]
pub struct DataDirectoryLock {
    file: File,
    path: PathBuf,
    registry_key: PathBuf,
}

impl DataDirectoryLock {
    pub fn acquire(data_directory: impl AsRef<Path>) -> Result<Self> {
        let data_directory = data_directory.as_ref();
        fs::create_dir_all(data_directory).map_err(|error| {
            LibraryServerError::with_context(
                error,
                format!(
                    "failed to create data directory {}",
                    data_directory.display()
                ),
            )
        })?;
        let path = data_directory.join(LOCK_FILE_NAME);
        let file = OpenOptions::new()
            .create(true)
            .write(true)
            .truncate(false)
            .open(&path)
            .map_err(|error| {
                LibraryServerError::with_context(
                    error,
                    format!("failed to open data directory lock {}", path.display()),
                )
            })?;
        let registry_key = path.canonicalize().unwrap_or_else(|_| path.clone());
        if !register_lock(&registry_key) {
            return Err(already_locked_error(data_directory));
        }

        match platform::try_lock(&file) {
            Ok(()) => Ok(Self {
                file,
                path,
                registry_key,
            }),
            Err(error) if platform::is_lock_conflict(&error) => {
                unregister_lock(&registry_key);
                Err(already_locked_error(data_directory))
            }
            Err(error) => {
                unregister_lock(&registry_key);
                Err(LibraryServerError::with_context(
                    error,
                    format!(
                        "failed to lock Danmaku data directory {}",
                        data_directory.display()
                    ),
                ))
            }
        }
    }

    pub fn path(&self) -> &Path {
        &self.path
    }
}

impl Drop for DataDirectoryLock {
    fn drop(&mut self) {
        let _ = platform::unlock(&self.file);
        unregister_lock(&self.registry_key);
    }
}

fn already_locked_error(data_directory: &Path) -> LibraryServerError {
    LibraryServerError::new(format!(
        "Danmaku data directory is already locked by another host: {}",
        data_directory.display()
    ))
}

fn register_lock(path: &Path) -> bool {
    let registry = LOCKED_PATHS.get_or_init(|| Mutex::new(HashSet::new()));
    let mut locked_paths = registry
        .lock()
        .expect("lock registry should not be poisoned");
    locked_paths.insert(path.to_path_buf())
}

fn unregister_lock(path: &Path) {
    if let Some(registry) = LOCKED_PATHS.get() {
        let mut locked_paths = registry
            .lock()
            .expect("lock registry should not be poisoned");
        locked_paths.remove(path);
    }
}

#[cfg(windows)]
mod platform {
    use std::fs::File;
    use std::io;
    use std::os::windows::io::AsRawHandle;

    use windows_sys::Win32::Foundation::{
        ERROR_LOCK_VIOLATION, ERROR_LOCKED, ERROR_SHARING_VIOLATION,
    };
    use windows_sys::Win32::Storage::FileSystem::{
        LOCKFILE_EXCLUSIVE_LOCK, LOCKFILE_FAIL_IMMEDIATELY, LockFileEx, UnlockFileEx,
    };
    use windows_sys::Win32::System::IO::OVERLAPPED;

    pub fn try_lock(file: &File) -> io::Result<()> {
        let mut overlapped = zeroed_overlapped();
        // SAFETY: The handle belongs to `file` and stays valid for the call. The
        // OVERLAPPED value locks from byte offset 0, matching FileChannel.tryLock.
        let ok = unsafe {
            LockFileEx(
                file.as_raw_handle(),
                LOCKFILE_EXCLUSIVE_LOCK | LOCKFILE_FAIL_IMMEDIATELY,
                0,
                u32::MAX,
                u32::MAX,
                &mut overlapped,
            )
        };
        if ok == 0 {
            Err(io::Error::last_os_error())
        } else {
            Ok(())
        }
    }

    pub fn unlock(file: &File) -> io::Result<()> {
        let mut overlapped = zeroed_overlapped();
        // SAFETY: This unlocks the same byte range acquired in `try_lock` using
        // the still-valid file handle.
        let ok =
            unsafe { UnlockFileEx(file.as_raw_handle(), 0, u32::MAX, u32::MAX, &mut overlapped) };
        if ok == 0 {
            Err(io::Error::last_os_error())
        } else {
            Ok(())
        }
    }

    pub fn is_lock_conflict(error: &io::Error) -> bool {
        matches!(
            error.raw_os_error().map(|code| code as u32),
            Some(ERROR_LOCK_VIOLATION | ERROR_SHARING_VIOLATION | ERROR_LOCKED)
        ) || error.kind() == io::ErrorKind::WouldBlock
    }

    fn zeroed_overlapped() -> OVERLAPPED {
        // SAFETY: OVERLAPPED is a plain old data FFI struct. A zeroed value means
        // synchronous offset 0 for LockFileEx/UnlockFileEx.
        unsafe { std::mem::zeroed() }
    }
}

#[cfg(unix)]
mod platform {
    use std::fs::File;
    use std::io;
    use std::os::unix::io::AsRawFd;

    pub fn try_lock(file: &File) -> io::Result<()> {
        set_lock(file, libc::F_WRLCK as i16)
    }

    pub fn unlock(file: &File) -> io::Result<()> {
        set_lock(file, libc::F_UNLCK as i16)
    }

    pub fn is_lock_conflict(error: &io::Error) -> bool {
        matches!(error.raw_os_error(), Some(libc::EACCES | libc::EAGAIN))
            || error.kind() == io::ErrorKind::WouldBlock
    }

    fn set_lock(file: &File, lock_type: i16) -> io::Result<()> {
        let mut lock = libc::flock {
            l_type: lock_type,
            l_whence: libc::SEEK_SET as i16,
            l_start: 0,
            l_len: 0,
            l_pid: 0,
        };
        // SAFETY: The fd is valid for the duration of the call and `lock` points
        // to a properly initialized POSIX record-lock structure.
        let result = unsafe { libc::fcntl(file.as_raw_fd(), libc::F_SETLK, &mut lock) };
        if result == -1 {
            Err(io::Error::last_os_error())
        } else {
            Ok(())
        }
    }
}

#[cfg(test)]
mod tests {
    use std::fs;
    use std::sync::atomic::{AtomicU64, Ordering};

    use super::*;

    static TEMP_COUNTER: AtomicU64 = AtomicU64::new(0);

    #[test]
    fn acquire_creates_jvm_lock_artifact() {
        let temp = temp_dir("danmaku-lock-artifact");
        let lock = DataDirectoryLock::acquire(&temp).expect("lock should acquire");

        assert_eq!(temp.join(LOCK_FILE_NAME), lock.path());
        assert!(temp.join(LOCK_FILE_NAME).is_file());

        drop(lock);
        fs::remove_dir_all(temp).expect("temp dir should delete");
    }

    #[test]
    fn acquire_fails_when_already_locked_and_releases_on_drop() {
        let temp = temp_dir("danmaku-lock-conflict");
        let first = DataDirectoryLock::acquire(&temp).expect("first lock should acquire");

        let error = DataDirectoryLock::acquire(&temp).expect_err("second lock should fail");
        assert!(error.to_string().contains("already locked by another host"));

        drop(first);
        let second = DataDirectoryLock::acquire(&temp).expect("lock should release on drop");
        drop(second);

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
