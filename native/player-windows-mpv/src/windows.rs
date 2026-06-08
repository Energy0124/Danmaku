use std::{
    ffi::{CStr, CString, c_char, c_void},
    fmt, mem,
    path::{Path, PathBuf},
    ptr::NonNull,
    sync::Arc,
};

#[cfg(unix)]
use std::os::unix::ffi::OsStrExt;
#[cfg(windows)]
use std::os::windows::ffi::OsStrExt;

type MpvClientApiVersion = unsafe extern "C" fn() -> u64;
type MpvCreate = unsafe extern "C" fn() -> *mut MpvHandle;
type MpvInitialize = unsafe extern "C" fn(*mut MpvHandle) -> i32;
type MpvDestroy = unsafe extern "C" fn(*mut MpvHandle);
type MpvTerminateDestroy = unsafe extern "C" fn(*mut MpvHandle);
type MpvCommand = unsafe extern "C" fn(*mut MpvHandle, *const *const c_char) -> i32;
type MpvCommandNode = unsafe extern "C" fn(*mut MpvHandle, *mut MpvNode, *mut MpvNode) -> i32;
type MpvSetOptionString = unsafe extern "C" fn(*mut MpvHandle, *const c_char, *const c_char) -> i32;
type MpvGetPropertyString = unsafe extern "C" fn(*mut MpvHandle, *const c_char) -> *mut c_char;
type MpvFree = unsafe extern "C" fn(*mut c_void);

const MPV_FORMAT_STRING: i32 = 1;
const MPV_FORMAT_FLAG: i32 = 3;
const MPV_FORMAT_INT64: i32 = 4;
const MPV_FORMAT_NODE_MAP: i32 = 8;

#[repr(C)]
struct MpvHandle {
    _private: [u8; 0],
}

#[repr(C)]
union MpvNodeValue {
    string: *mut c_char,
    flag: i32,
    int64: i64,
    list: *mut MpvNodeList,
}

#[repr(C)]
struct MpvNode {
    value: MpvNodeValue,
    format: i32,
}

#[repr(C)]
struct MpvNodeList {
    num: i32,
    values: *mut MpvNode,
    keys: *mut *mut c_char,
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
    command_node: MpvCommandNode,
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
        let command_node = unsafe {
            mem::transmute::<*mut c_void, MpvCommandNode>(module.symbol("mpv_command_node")?)
        };
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
                command_node,
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

    pub fn osd_overlay(
        &self,
        id: i64,
        format: &str,
        data: &str,
        res_x: i64,
        res_y: i64,
        z: i64,
        hidden: bool,
    ) -> Result<(), MpvError> {
        let strings = vec![
            CString::new("_name").map_err(|_| MpvError::InvalidCommandArgument("_name".into()))?,
            CString::new("id").map_err(|_| MpvError::InvalidCommandArgument("id".into()))?,
            CString::new("format").map_err(|_| MpvError::InvalidCommandArgument("format".into()))?,
            CString::new("data").map_err(|_| MpvError::InvalidCommandArgument("data".into()))?,
            CString::new("res_x").map_err(|_| MpvError::InvalidCommandArgument("res_x".into()))?,
            CString::new("res_y").map_err(|_| MpvError::InvalidCommandArgument("res_y".into()))?,
            CString::new("z").map_err(|_| MpvError::InvalidCommandArgument("z".into()))?,
            CString::new("hidden").map_err(|_| MpvError::InvalidCommandArgument("hidden".into()))?,
            CString::new("osd-overlay")
                .map_err(|_| MpvError::InvalidCommandArgument("osd-overlay".into()))?,
            CString::new(format).map_err(|_| MpvError::InvalidCommandArgument(format.into()))?,
            CString::new(data).map_err(|_| MpvError::InvalidCommandArgument(data.into()))?,
        ];
        let mut keys = vec![
            strings[0].as_ptr() as *mut c_char,
            strings[1].as_ptr() as *mut c_char,
            strings[2].as_ptr() as *mut c_char,
            strings[3].as_ptr() as *mut c_char,
            strings[4].as_ptr() as *mut c_char,
            strings[5].as_ptr() as *mut c_char,
            strings[6].as_ptr() as *mut c_char,
            strings[7].as_ptr() as *mut c_char,
        ];
        let mut values = vec![
            MpvNode {
                value: MpvNodeValue {
                    string: strings[8].as_ptr() as *mut c_char,
                },
                format: MPV_FORMAT_STRING,
            },
            MpvNode {
                value: MpvNodeValue { int64: id },
                format: MPV_FORMAT_INT64,
            },
            MpvNode {
                value: MpvNodeValue {
                    string: strings[9].as_ptr() as *mut c_char,
                },
                format: MPV_FORMAT_STRING,
            },
            MpvNode {
                value: MpvNodeValue {
                    string: strings[10].as_ptr() as *mut c_char,
                },
                format: MPV_FORMAT_STRING,
            },
            MpvNode {
                value: MpvNodeValue { int64: res_x },
                format: MPV_FORMAT_INT64,
            },
            MpvNode {
                value: MpvNodeValue { int64: res_y },
                format: MPV_FORMAT_INT64,
            },
            MpvNode {
                value: MpvNodeValue { int64: z },
                format: MPV_FORMAT_INT64,
            },
            MpvNode {
                value: MpvNodeValue {
                    flag: i32::from(hidden),
                },
                format: MPV_FORMAT_FLAG,
            },
        ];
        let mut list = MpvNodeList {
            num: values.len() as i32,
            values: values.as_mut_ptr(),
            keys: keys.as_mut_ptr(),
        };
        let mut root = MpvNode {
            value: MpvNodeValue { list: &mut list },
            format: MPV_FORMAT_NODE_MAP,
        };

        let status = unsafe {
            (self.api.command_node)(self.handle.as_ptr(), &mut root, std::ptr::null_mut())
        };
        if status < 0 {
            Err(MpvError::CommandFailed(status))
        } else {
            Ok(())
        }
    }

    pub fn property_string(&self, name: &str) -> Result<Option<String>, MpvError> {
        let name =
            CString::new(name).map_err(|_| MpvError::InvalidPropertyName(name.to_owned()))?;
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
    #[cfg(windows)]
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

    #[cfg(unix)]
    fn load(path: &Path) -> Result<Self, LibraryLoadError> {
        let path_name = CString::new(path.as_os_str().as_bytes())
            .map_err(|_| LibraryLoadError::LoadFailed(path.to_path_buf()))?;
        let handle = unsafe { dlopen(path_name.as_ptr(), RTLD_NOW) };
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
        #[cfg(windows)]
        let address = unsafe { GetProcAddress(self.handle.as_ptr(), symbol_name.as_ptr()) };
        #[cfg(unix)]
        let address = unsafe { dlsym(self.handle.as_ptr(), symbol_name.as_ptr()) };

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
        #[cfg(windows)]
        unsafe {
            FreeLibrary(self.handle.as_ptr());
        }
        #[cfg(unix)]
        unsafe {
            dlclose(self.handle.as_ptr());
        }
    }
}

#[cfg(windows)]
#[link(name = "kernel32")]
unsafe extern "system" {
    fn LoadLibraryW(file_name: *const u16) -> *mut c_void;
    fn GetProcAddress(module: *mut c_void, procedure_name: *const c_char) -> *mut c_void;
    fn FreeLibrary(module: *mut c_void) -> i32;
}

#[cfg(unix)]
const RTLD_NOW: i32 = 2;

#[cfg(all(unix, not(target_os = "macos")))]
#[link(name = "dl")]
unsafe extern "C" {}

#[cfg(unix)]
unsafe extern "C" {
    fn dlopen(file_name: *const c_char, flags: i32) -> *mut c_void;
    fn dlsym(handle: *mut c_void, symbol: *const c_char) -> *mut c_void;
    fn dlclose(handle: *mut c_void) -> i32;
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
