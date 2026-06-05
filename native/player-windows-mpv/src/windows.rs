use std::{
    ffi::{CStr, CString, c_char, c_void},
    fmt, mem,
    os::windows::ffi::OsStrExt,
    path::{Path, PathBuf},
    ptr::NonNull,
    sync::Arc,
};

type MpvClientApiVersion = unsafe extern "C" fn() -> u64;
type MpvCreate = unsafe extern "C" fn() -> *mut MpvHandle;
type MpvInitialize = unsafe extern "C" fn(*mut MpvHandle) -> i32;
type MpvDestroy = unsafe extern "C" fn(*mut MpvHandle);
type MpvTerminateDestroy = unsafe extern "C" fn(*mut MpvHandle);
type MpvCommand = unsafe extern "C" fn(*mut MpvHandle, *const *const c_char) -> i32;
type MpvSetOptionString = unsafe extern "C" fn(*mut MpvHandle, *const c_char, *const c_char) -> i32;
type MpvGetPropertyString = unsafe extern "C" fn(*mut MpvHandle, *const c_char) -> *mut c_char;
type MpvFree = unsafe extern "C" fn(*mut c_void);

#[repr(C)]
struct MpvHandle {
    _private: [u8; 0],
}

pub struct MpvLibrary {
    api: Arc<MpvApi>,
}

struct MpvApi {
    module: Module,
    client_api_version: MpvClientApiVersion,
    create: MpvCreate,
    initialize: MpvInitialize,
    destroy: MpvDestroy,
    terminate_destroy: MpvTerminateDestroy,
    command: MpvCommand,
    set_option_string: MpvSetOptionString,
    get_property_string: MpvGetPropertyString,
    free: MpvFree,
}

impl MpvLibrary {
    pub fn load(path: impl AsRef<Path>) -> Result<Self, LibraryLoadError> {
        let module = Module::load(path.as_ref())?;
        let client_api_version = unsafe {
            mem::transmute::<*mut c_void, MpvClientApiVersion>(
                module.symbol("mpv_client_api_version")?,
            )
        };
        let create =
            unsafe { mem::transmute::<*mut c_void, MpvCreate>(module.symbol("mpv_create")?) };
        let initialize = unsafe {
            mem::transmute::<*mut c_void, MpvInitialize>(module.symbol("mpv_initialize")?)
        };
        let destroy =
            unsafe { mem::transmute::<*mut c_void, MpvDestroy>(module.symbol("mpv_destroy")?) };
        let terminate_destroy = unsafe {
            mem::transmute::<*mut c_void, MpvTerminateDestroy>(
                module.symbol("mpv_terminate_destroy")?,
            )
        };
        let command =
            unsafe { mem::transmute::<*mut c_void, MpvCommand>(module.symbol("mpv_command")?) };
        let set_option_string = unsafe {
            mem::transmute::<*mut c_void, MpvSetOptionString>(
                module.symbol("mpv_set_option_string")?,
            )
        };
        let get_property_string = unsafe {
            mem::transmute::<*mut c_void, MpvGetPropertyString>(
                module.symbol("mpv_get_property_string")?,
            )
        };
        let free = unsafe { mem::transmute::<*mut c_void, MpvFree>(module.symbol("mpv_free")?) };

        Ok(Self {
            api: Arc::new(MpvApi {
                module,
                client_api_version,
                create,
                initialize,
                destroy,
                terminate_destroy,
                command,
                set_option_string,
                get_property_string,
                free,
            }),
        })
    }

    pub fn client_api_version(&self) -> u64 {
        unsafe { (self.api.client_api_version)() }
    }

    pub fn loaded_path(&self) -> &Path {
        &self.api.module.path
    }

    pub fn create(&self) -> Result<Mpv, MpvError> {
        self.create_with_options(&[])
    }

    pub fn create_with_options(&self, options: &[(&str, &str)]) -> Result<Mpv, MpvError> {
        let options = options
            .iter()
            .map(|(name, value)| {
                Ok((
                    CString::new(*name)
                        .map_err(|_| MpvError::InvalidOptionName((*name).to_owned()))?,
                    CString::new(*value)
                        .map_err(|_| MpvError::InvalidOptionValue((*value).to_owned()))?,
                ))
            })
            .collect::<Result<Vec<_>, MpvError>>()?;
        let handle = unsafe { (self.api.create)() };
        let handle = NonNull::new(handle).ok_or(MpvError::CreateFailed)?;
        for (name, value) in options {
            let status = unsafe {
                (self.api.set_option_string)(handle.as_ptr(), name.as_ptr(), value.as_ptr())
            };
            if status < 0 {
                unsafe {
                    (self.api.destroy)(handle.as_ptr());
                }
                return Err(MpvError::SetOptionFailed {
                    name: name.to_string_lossy().into_owned(),
                    status,
                });
            }
        }
        let status = unsafe { (self.api.initialize)(handle.as_ptr()) };

        if status < 0 {
            unsafe {
                (self.api.destroy)(handle.as_ptr());
            }
            Err(MpvError::InitializeFailed(status))
        } else {
            Ok(Mpv {
                api: Arc::clone(&self.api),
                handle,
            })
        }
    }
}

pub struct Mpv {
    api: Arc<MpvApi>,
    handle: NonNull<MpvHandle>,
}

impl Mpv {
    /// Executes an mpv command using its null-terminated string argument API.
    pub fn command(&self, args: &[&str]) -> Result<(), MpvError> {
        let args: Vec<_> = args
            .iter()
            .map(|arg| {
                CString::new(*arg).map_err(|_| MpvError::InvalidCommandArgument((*arg).to_owned()))
            })
            .collect::<Result<_, _>>()?;
        let mut pointers: Vec<_> = args.iter().map(|arg| arg.as_ptr()).collect();
        pointers.push(std::ptr::null());

        let status = unsafe { (self.api.command)(self.handle.as_ptr(), pointers.as_ptr()) };
        if status < 0 {
            Err(MpvError::CommandFailed(status))
        } else {
            Ok(())
        }
    }

    pub fn property_string(&self, name: &str) -> Result<Option<String>, MpvError> {
        let name = CString::new(name)
            .map_err(|_| MpvError::InvalidPropertyName(name.to_owned()))?;
        let value = unsafe { (self.api.get_property_string)(self.handle.as_ptr(), name.as_ptr()) };
        let Some(value) = NonNull::new(value.cast::<c_void>()) else {
            return Ok(None);
        };
        let text = unsafe { CStr::from_ptr(value.as_ptr().cast::<c_char>()) }
            .to_string_lossy()
            .into_owned();
        unsafe {
            (self.api.free)(value.as_ptr());
        }
        Ok(Some(text))
    }
}

impl Drop for Mpv {
    fn drop(&mut self) {
        unsafe {
            (self.api.terminate_destroy)(self.handle.as_ptr());
        }
    }
}

#[derive(Debug)]
pub enum LibraryLoadError {
    InvalidSymbolName(String),
    LoadFailed(PathBuf),
    MissingSymbol { path: PathBuf, symbol: String },
}

impl fmt::Display for LibraryLoadError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::InvalidSymbolName(symbol) => {
                write!(
                    formatter,
                    "libmpv symbol name contains a null byte: {symbol}"
                )
            }
            Self::LoadFailed(path) => write!(formatter, "failed to load {}", path.display()),
            Self::MissingSymbol { path, symbol } => {
                write!(formatter, "{symbol} was not found in {}", path.display())
            }
        }
    }
}

impl std::error::Error for LibraryLoadError {}

#[derive(Debug, Eq, PartialEq)]
pub enum MpvError {
    CreateFailed,
    InitializeFailed(i32),
    InvalidOptionName(String),
    InvalidOptionValue(String),
    InvalidPropertyName(String),
    SetOptionFailed { name: String, status: i32 },
    InvalidCommandArgument(String),
    CommandFailed(i32),
}

impl fmt::Display for MpvError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::CreateFailed => write!(formatter, "mpv_create returned a null handle"),
            Self::InitializeFailed(status) => {
                write!(formatter, "mpv_initialize failed with status {status}")
            }
            Self::InvalidOptionName(name) => {
                write!(formatter, "mpv option name contains a null byte: {name}")
            }
            Self::InvalidOptionValue(value) => {
                write!(formatter, "mpv option value contains a null byte: {value}")
            }
            Self::InvalidPropertyName(name) => {
                write!(formatter, "mpv property name contains a null byte: {name}")
            }
            Self::SetOptionFailed { name, status } => {
                write!(formatter, "mpv option {name} failed with status {status}")
            }
            Self::InvalidCommandArgument(argument) => {
                write!(
                    formatter,
                    "mpv command argument contains a null byte: {argument}"
                )
            }
            Self::CommandFailed(status) => {
                write!(formatter, "mpv_command failed with status {status}")
            }
        }
    }
}

impl std::error::Error for MpvError {}

struct Module {
    handle: NonNull<c_void>,
    path: PathBuf,
}

impl Module {
    fn load(path: &Path) -> Result<Self, LibraryLoadError> {
        let wide_path: Vec<_> = path
            .as_os_str()
            .encode_wide()
            .chain(std::iter::once(0))
            .collect();
        let handle = unsafe { LoadLibraryW(wide_path.as_ptr()) };
        let handle =
            NonNull::new(handle).ok_or_else(|| LibraryLoadError::LoadFailed(path.to_path_buf()))?;

        Ok(Self {
            handle,
            path: path.to_path_buf(),
        })
    }

    fn symbol(&self, symbol: &str) -> Result<*mut c_void, LibraryLoadError> {
        let symbol_name = CString::new(symbol)
            .map_err(|_| LibraryLoadError::InvalidSymbolName(symbol.to_owned()))?;
        let address = unsafe { GetProcAddress(self.handle.as_ptr(), symbol_name.as_ptr()) };

        if address.is_null() {
            Err(LibraryLoadError::MissingSymbol {
                path: self.path.clone(),
                symbol: symbol.to_owned(),
            })
        } else {
            Ok(address)
        }
    }
}

impl Drop for Module {
    fn drop(&mut self) {
        unsafe {
            FreeLibrary(self.handle.as_ptr());
        }
    }
}

#[link(name = "kernel32")]
unsafe extern "system" {
    fn LoadLibraryW(file_name: *const u16) -> *mut c_void;
    fn GetProcAddress(module: *mut c_void, procedure_name: *const c_char) -> *mut c_void;
    fn FreeLibrary(module: *mut c_void) -> i32;
}

#[cfg(test)]
mod tests {
    use super::{LibraryLoadError, MpvLibrary};
    use std::path::Path;

    #[test]
    fn reports_a_missing_dll() {
        let path = Path::new("C:/missing-danmaku-test-libmpv-2.dll");

        let error = match MpvLibrary::load(path) {
            Ok(_) => panic!("library should not load"),
            Err(error) => error,
        };

        assert!(matches!(error, LibraryLoadError::LoadFailed(error_path) if error_path == path));
    }
}
