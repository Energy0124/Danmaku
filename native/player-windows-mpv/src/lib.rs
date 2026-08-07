//! Cross-platform libmpv loading bridge.
//!
//! This crate deliberately has no third-party dependencies. It proves the
//! native library location and loading boundary before render integration is
//! added.

mod locator;

pub use locator::{
    LIBMPV_DLL_NAME, LIBMPV_PATH_ENV, LibraryLocationError, candidate_paths, find_library,
    find_library_for_current_process,
};

mod windows;

pub use windows::{
    LibraryLoadError, MPV_RENDER_API_TYPE_OPENGL, MPV_RENDER_PARAM_API_TYPE,
    MPV_RENDER_PARAM_FLIP_Y, MPV_RENDER_PARAM_INVALID, MPV_RENDER_PARAM_OPENGL_FBO,
    MPV_RENDER_PARAM_OPENGL_INIT_PARAMS, MPV_RENDER_UPDATE_FRAME, Mpv, MpvError, MpvLibrary,
    MpvOpenGlFbo, MpvOpenGlGetProcAddress, MpvOpenGlInitParams, MpvRenderApi, MpvRenderContext,
    MpvRenderContextHandle, MpvRenderParam,
};
