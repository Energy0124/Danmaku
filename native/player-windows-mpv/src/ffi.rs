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
    SetOptionFailed = -6,
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn danmaku_mpv_create(
    libmpv_path: *const c_char,
    out_handle: *mut *mut DanmakuMpv,
) -> DanmakuMpvStatus {
    unsafe {
        danmaku_mpv_create_with_options(
            libmpv_path,
            std::ptr::null(),
            std::ptr::null(),
            0,
            out_handle,
        )
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn danmaku_mpv_create_with_options(
    libmpv_path: *const c_char,
    option_names: *const *const c_char,
    option_values: *const *const c_char,
    options_len: usize,
    out_handle: *mut *mut DanmakuMpv,
) -> DanmakuMpvStatus {
    if libmpv_path.is_null()
        || out_handle.is_null()
        || (options_len > 0 && (option_names.is_null() || option_values.is_null()))
    {
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
    let option_names = match unsafe { read_string_array(option_names, options_len) } {
        Ok(option_names) => option_names,
        Err(status) => return status,
    };
    let option_values = match unsafe { read_string_array(option_values, options_len) } {
        Ok(option_values) => option_values,
        Err(status) => return status,
    };
    let options = option_names
        .iter()
        .zip(option_values.iter())
        .map(|(name, value)| (name.as_str(), value.as_str()))
        .collect::<Vec<_>>();
    let mpv = match library.create_with_options(&options) {
        Ok(mpv) => mpv,
        Err(crate::MpvError::SetOptionFailed { .. }) => return DanmakuMpvStatus::SetOptionFailed,
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
    let owned_args = match unsafe { read_string_array(args, args_len) } {
        Ok(args) => args,
        Err(status) => return status,
    };
    let borrowed_args = owned_args.iter().map(String::as_str).collect::<Vec<_>>();

    match handle.mpv.command(&borrowed_args) {
        Ok(()) => DanmakuMpvStatus::Ok,
        Err(_) => DanmakuMpvStatus::CommandFailed,
    }
}

unsafe fn read_string_array(
    values: *const *const c_char,
    values_len: usize,
) -> Result<Vec<String>, DanmakuMpvStatus> {
    if values_len == 0 {
        return Ok(Vec::new());
    }
    let values = unsafe { slice::from_raw_parts(values, values_len) };
    let mut owned_values = Vec::with_capacity(values.len());
    for value in values {
        if value.is_null() {
            return Err(DanmakuMpvStatus::NullPointer);
        }
        let value = unsafe { CStr::from_ptr(*value) };
        let value = value
            .to_str()
            .map_err(|_| DanmakuMpvStatus::InvalidUtf8)?
            .to_owned();
        owned_values.push(value);
    }
    Ok(owned_values)
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
        DanmakuMpv, DanmakuMpvStatus, danmaku_mpv_command, danmaku_mpv_create,
        danmaku_mpv_create_with_options, danmaku_mpv_destroy,
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
    fn rejects_null_option_arrays() {
        let path = CString::new("C:/missing-danmaku-test-libmpv-2.dll").unwrap();
        let option = CString::new("wid").unwrap();
        let mut option = option.as_ptr();
        let mut handle = null_handle();

        assert_eq!(DanmakuMpvStatus::NullPointer, unsafe {
            danmaku_mpv_create_with_options(path.as_ptr(), ptr::null(), &mut option, 1, &mut handle)
        });
        assert_eq!(DanmakuMpvStatus::NullPointer, unsafe {
            danmaku_mpv_create_with_options(path.as_ptr(), &mut option, ptr::null(), 1, &mut handle)
        });
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
