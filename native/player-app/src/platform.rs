//! OS-specific window presentation helpers.
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
