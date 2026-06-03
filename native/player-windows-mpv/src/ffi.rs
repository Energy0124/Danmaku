use crate::{Mpv, MpvLibrary};
use std::{
    ffi::{CStr, c_char},
    path::Path,
    slice,
};

#[repr(C)]
pub struct DanmakuMpv {
    mpv: Mpv,
}

#[repr(i32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum DanmakuMpvStatus {
    Ok = 0,
    NullPointer = -1,
    InvalidUtf8 = -2,
    LoadFailed = -3,
    CreateFailed = -4,
    CommandFailed = -5,
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn danmaku_mpv_create(
    libmpv_path: *const c_char,
    out_handle: *mut *mut DanmakuMpv,
) -> DanmakuMpvStatus {
    if libmpv_path.is_null() || out_handle.is_null() {
        return DanmakuMpvStatus::NullPointer;
    }

    let libmpv_path = unsafe { CStr::from_ptr(libmpv_path) };
    let Ok(libmpv_path) = libmpv_path.to_str() else {
        return DanmakuMpvStatus::InvalidUtf8;
    };
    let library = match MpvLibrary::load(Path::new(libmpv_path)) {
        Ok(library) => library,
        Err(_) => return DanmakuMpvStatus::LoadFailed,
    };
    let mpv = match library.create() {
        Ok(mpv) => mpv,
        Err(_) => return DanmakuMpvStatus::CreateFailed,
    };

    unsafe {
        *out_handle = Box::into_raw(Box::new(DanmakuMpv { mpv }));
    }
    DanmakuMpvStatus::Ok
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn danmaku_mpv_command(
    handle: *mut DanmakuMpv,
    args: *const *const c_char,
    args_len: usize,
) -> DanmakuMpvStatus {
    if handle.is_null() || (args_len > 0 && args.is_null()) {
        return DanmakuMpvStatus::NullPointer;
    }

    let handle = unsafe { &mut *handle };
    let args = unsafe { slice::from_raw_parts(args, args_len) };
    let mut owned_args = Vec::with_capacity(args.len());
    for arg in args {
        if arg.is_null() {
            return DanmakuMpvStatus::NullPointer;
        }
        let arg = unsafe { CStr::from_ptr(*arg) };
        let Ok(arg) = arg.to_str() else {
            return DanmakuMpvStatus::InvalidUtf8;
        };
        owned_args.push(arg.to_owned());
    }
    let borrowed_args = owned_args.iter().map(String::as_str).collect::<Vec<_>>();

    match handle.mpv.command(&borrowed_args) {
        Ok(()) => DanmakuMpvStatus::Ok,
        Err(_) => DanmakuMpvStatus::CommandFailed,
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn danmaku_mpv_destroy(handle: *mut DanmakuMpv) {
    if !handle.is_null() {
        unsafe {
            drop(Box::from_raw(handle));
        }
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn danmaku_mpv_status_ok() -> DanmakuMpvStatus {
    DanmakuMpvStatus::Ok
}

#[cfg(test)]
mod tests {
    use super::{
        DanmakuMpv, DanmakuMpvStatus, danmaku_mpv_command, danmaku_mpv_create, danmaku_mpv_destroy,
    };
    use std::{ffi::CString, ptr};

    fn null_handle() -> *mut DanmakuMpv {
        ptr::null_mut()
    }

    #[test]
    fn rejects_null_create_arguments() {
        let path = CString::new("C:/missing-danmaku-test-libmpv-2.dll").unwrap();
        let mut handle = null_handle();

        assert_eq!(DanmakuMpvStatus::NullPointer, unsafe {
            danmaku_mpv_create(ptr::null(), &mut handle)
        },);
        assert_eq!(DanmakuMpvStatus::NullPointer, unsafe {
            danmaku_mpv_create(path.as_ptr(), ptr::null_mut())
        },);
    }

    #[test]
    fn reports_missing_libmpv_as_load_failure() {
        let path = CString::new("C:/missing-danmaku-test-libmpv-2.dll").unwrap();
        let mut handle: *mut DanmakuMpv = null_handle();

        assert_eq!(DanmakuMpvStatus::LoadFailed, unsafe {
            danmaku_mpv_create(path.as_ptr(), &mut handle)
        },);
        assert!(handle.is_null());
    }

    #[test]
    fn rejects_null_command_arguments() {
        let command = CString::new("loadfile").unwrap();
        let mut command = command.as_ptr();

        assert_eq!(DanmakuMpvStatus::NullPointer, unsafe {
            danmaku_mpv_command(null_handle(), &mut command, 1)
        },);
        unsafe {
            danmaku_mpv_destroy(null_handle());
        }
    }
}
