//! Windows libmpv loading spike.
//!
//! This crate deliberately has no third-party dependencies. It proves the
//! native library location and loading boundary before render integration is
//! added.

mod locator;

pub use locator::{
    LIBMPV_DLL_NAME, LIBMPV_PATH_ENV, LibraryLocationError, candidate_paths, find_library,
    find_library_for_current_process,
};

#[cfg(windows)]
mod windows;

#[cfg(windows)]
mod ffi;

#[cfg(windows)]
pub use ffi::{
    DanmakuMpv, DanmakuMpvStatus, danmaku_mpv_command, danmaku_mpv_create,
    danmaku_mpv_create_with_options, danmaku_mpv_destroy, danmaku_mpv_status_ok,
};

#[cfg(windows)]
pub use windows::{LibraryLoadError, Mpv, MpvError, MpvLibrary};
