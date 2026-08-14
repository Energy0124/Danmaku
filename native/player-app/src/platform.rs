//! OS-specific window presentation and user-directory helpers.
//!
//! On Windows 11 the player runs borderless (`with_decorations(false)`), which
//! leaves square corners. Opting the window into the DWM rounded-corner policy
//! restores the platform-native rounded corners and drop shadow. Maximized
//! windows are intentionally left square by the compositor.

/// Requests Windows 11 rounded corners for the player window. No-op if the
/// window handle is unavailable or the attribute call fails (older Windows).
#[cfg(windows)]
pub fn apply_rounded_corners(frame: &eframe::Frame) {
    use raw_window_handle::{HasWindowHandle, RawWindowHandle};
    use windows_sys::Win32::Foundation::HWND;
    use windows_sys::Win32::Graphics::Dwm::{
        DWMWA_WINDOW_CORNER_PREFERENCE, DWMWCP_ROUND, DwmSetWindowAttribute,
    };

    let Ok(handle) = frame.window_handle() else {
        return;
    };
    let RawWindowHandle::Win32(win32) = handle.as_raw() else {
        return;
    };
    let hwnd = win32.hwnd.get() as HWND;
    let preference: i32 = DWMWCP_ROUND;
    // SAFETY: `hwnd` is a live top-level window owned by this process for the
    // duration of the call, and `preference` outlives the call. The DWM
    // attribute API ignores unknown attributes on unsupported Windows builds.
    unsafe {
        DwmSetWindowAttribute(
            hwnd,
            DWMWA_WINDOW_CORNER_PREFERENCE as u32,
            &preference as *const i32 as *const core::ffi::c_void,
            core::mem::size_of::<i32>() as u32,
        );
    }
}

#[cfg(not(windows))]
pub fn apply_rounded_corners(_frame: &eframe::Frame) {}

/// The Windows shell draws its own title bar. macOS and other desktop hosts
/// keep native decorations so standard traffic-light controls and window
/// behaviors remain available.
pub const fn uses_custom_window_chrome() -> bool {
    cfg!(windows)
}

/// User-scoped durable application data.
pub fn application_support_directory() -> std::path::PathBuf {
    platform_application_support_directory(
        std::env::var_os("LOCALAPPDATA"),
        std::env::var_os("HOME"),
        std::env::var_os("XDG_DATA_HOME"),
        std::env::current_dir().ok(),
    )
}

/// User-scoped disposable cache data.
pub fn cache_directory() -> std::path::PathBuf {
    platform_cache_directory(
        std::env::var_os("LOCALAPPDATA"),
        std::env::var_os("HOME"),
        std::env::var_os("XDG_CACHE_HOME"),
        std::env::current_dir().ok(),
    )
}

fn platform_application_support_directory(
    local_app_data: Option<std::ffi::OsString>,
    home: Option<std::ffi::OsString>,
    xdg_data_home: Option<std::ffi::OsString>,
    current_directory: Option<std::path::PathBuf>,
) -> std::path::PathBuf {
    if cfg!(windows) {
        return local_app_data
            .map(std::path::PathBuf::from)
            .or(current_directory)
            .unwrap_or_else(|| std::path::PathBuf::from("."))
            .join("Danmaku");
    }
    if cfg!(target_os = "macos") {
        return home
            .map(std::path::PathBuf::from)
            .or(current_directory)
            .unwrap_or_else(|| std::path::PathBuf::from("."))
            .join("Library")
            .join("Application Support")
            .join("Danmaku");
    }
    xdg_data_home
        .map(std::path::PathBuf::from)
        .or_else(|| home.map(|home| std::path::PathBuf::from(home).join(".local/share")))
        .or(current_directory)
        .unwrap_or_else(|| std::path::PathBuf::from("."))
        .join("Danmaku")
}

fn platform_cache_directory(
    local_app_data: Option<std::ffi::OsString>,
    home: Option<std::ffi::OsString>,
    xdg_cache_home: Option<std::ffi::OsString>,
    current_directory: Option<std::path::PathBuf>,
) -> std::path::PathBuf {
    if cfg!(windows) {
        return local_app_data
            .map(std::path::PathBuf::from)
            .or(current_directory)
            .unwrap_or_else(|| std::path::PathBuf::from("."))
            .join("Danmaku");
    }
    if cfg!(target_os = "macos") {
        return home
            .map(std::path::PathBuf::from)
            .or(current_directory)
            .unwrap_or_else(|| std::path::PathBuf::from("."))
            .join("Library")
            .join("Caches")
            .join("Danmaku");
    }
    xdg_cache_home
        .map(std::path::PathBuf::from)
        .or_else(|| home.map(|home| std::path::PathBuf::from(home).join(".cache")))
        .or(current_directory)
        .unwrap_or_else(|| std::path::PathBuf::from("."))
        .join("Danmaku")
}

#[cfg(test)]
mod tests {
    use super::{platform_application_support_directory, platform_cache_directory};
    use std::{ffi::OsString, path::PathBuf};

    #[test]
    fn selects_platform_native_user_directories() {
        let support = platform_application_support_directory(
            Some(OsString::from("C:/Users/test/AppData/Local")),
            Some(OsString::from("/Users/test")),
            Some(OsString::from("/tmp/xdg-data")),
            Some(PathBuf::from("/tmp/current")),
        );
        let cache = platform_cache_directory(
            Some(OsString::from("C:/Users/test/AppData/Local")),
            Some(OsString::from("/Users/test")),
            Some(OsString::from("/tmp/xdg-cache")),
            Some(PathBuf::from("/tmp/current")),
        );

        #[cfg(windows)]
        {
            assert_eq!(
                support,
                PathBuf::from("C:/Users/test/AppData/Local/Danmaku")
            );
            assert_eq!(cache, support);
        }
        #[cfg(target_os = "macos")]
        {
            assert_eq!(
                support,
                PathBuf::from("/Users/test/Library/Application Support/Danmaku")
            );
            assert_eq!(cache, PathBuf::from("/Users/test/Library/Caches/Danmaku"));
        }
        #[cfg(all(unix, not(target_os = "macos")))]
        {
            assert_eq!(support, PathBuf::from("/tmp/xdg-data/Danmaku"));
            assert_eq!(cache, PathBuf::from("/tmp/xdg-cache/Danmaku"));
        }
    }
}
