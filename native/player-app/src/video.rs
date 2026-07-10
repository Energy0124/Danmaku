//! libmpv render-API → egui/glow compositing host.
//!
//! Ported from the validated `spike-egui-player` renderer: mpv renders into a
//! crate-owned FBO texture which is then drawn into the egui viewport, so the
//! UI (controls, later the danmaku overlay) always composites above video.

use std::{
    env,
    ffi::{c_char, c_void},
    path::PathBuf,
    ptr,
    sync::{Arc, Mutex},
};

use eframe::{egui, glow};
use egui_glow::Painter;
use glow::HasContext;
use player_windows_mpv::{
    LIBMPV_DLL_NAME, LIBMPV_PATH_ENV, MPV_RENDER_API_TYPE_OPENGL, Mpv, MpvLibrary, MpvOpenGlFbo,
    MpvOpenGlInitParams, MpvRenderContextHandle, MpvRenderParam, find_library_for_current_process,
};

pub type SharedVideoRenderer = Arc<Mutex<VideoRenderer>>;

/// Counters the smoke harness reads to prove frames actually rendered.
#[derive(Clone, Debug, Default)]
pub struct RenderCounters {
    pub rendered_frames: u64,
    pub render_failures: u64,
}

pub struct VideoRenderer {
    mpv: Mpv,
    render_context: Option<MpvRenderContextHandle>,
    repaint_context: Box<egui::Context>,
    gl_objects: Option<GlVideoObjects>,
    counters: Arc<Mutex<RenderCounters>>,
    last_render_error: Option<String>,
}

unsafe impl Send for VideoRenderer {}

impl VideoRenderer {
    pub fn create(
        media: &str,
        start_position_s: Option<f64>,
        volume_percent: Option<u8>,
        egui_context: &egui::Context,
        counters: Arc<Mutex<RenderCounters>>,
    ) -> Result<Self, String> {
        let libmpv_path = resolve_libmpv_path()?;
        let library = MpvLibrary::load(&libmpv_path).map_err(|error| {
            format!(
                "failed to load libmpv at {}: {error}",
                libmpv_path.display()
            )
        })?;
        let render_api = library
            .render_api()
            .map_err(|error| format!("failed to load libmpv render API symbols: {error}"))?;
        let mpv = library
            .create_with_options(&[
                ("vo", "libmpv"),
                ("hwdec", "auto"),
                ("idle", "yes"),
                ("keep-open", "yes"),
                ("osc", "no"),
                ("terminal", "no"),
            ])
            .map_err(|error| format!("failed to create mpv render client: {error}"))?;

        let mut init_params = MpvOpenGlInitParams {
            get_proc_address: Some(get_proc_address),
            get_proc_address_ctx: ptr::null_mut(),
            extra_exts: ptr::null(),
        };
        let mut render_params = [
            MpvRenderParam::api_type(MPV_RENDER_API_TYPE_OPENGL.as_ptr().cast::<c_char>()),
            MpvRenderParam::opengl_init_params(&mut init_params),
            MpvRenderParam::invalid(),
        ];
        let render_context = render_api
            .create_context(&mpv, &mut render_params)
            .map_err(|error| format!("failed to create mpv OpenGL render context: {error}"))?;

        let mut repaint_context = Box::new(egui_context.clone());
        let repaint_context_ptr = (&mut *repaint_context) as *mut egui::Context as *mut c_void;
        // Safety: the boxed context is heap-pinned for the renderer's
        // lifetime, and Drop clears the callback before releasing it.
        unsafe {
            render_context.set_update_callback(Some(request_repaint), repaint_context_ptr);
        }

        if let Some(start_position_s) = start_position_s.filter(|value| *value > 0.0) {
            let value = format!("{start_position_s:.3}");
            mpv.command(&["set", "start", &value])
                .map_err(|error| format!("failed to set start position: {error}"))?;
        }
        if let Some(volume_percent) = volume_percent {
            let value = volume_percent.to_string();
            mpv.command(&["set", "volume", &value])
                .map_err(|error| format!("failed to set volume: {error}"))?;
        }
        mpv.command(&["loadfile", media, "replace"])
            .map_err(|error| format!("failed to load media {media}: {error}"))?;
        mpv.command(&["set", "pause", "no"])
            .map_err(|error| format!("failed to start playback: {error}"))?;

        Ok(Self {
            mpv,
            render_context: Some(render_context),
            repaint_context,
            gl_objects: None,
            counters,
            last_render_error: None,
        })
    }

    pub fn render(&mut self, info: egui::PaintCallbackInfo, painter: &Painter) {
        let viewport = info.viewport_in_pixels();
        let width = viewport.width_px.max(1);
        let height = viewport.height_px.max(1);
        let gl = painter.gl();

        let render_result = unsafe {
            self.ensure_gl_objects(gl, width, height)
                .and_then(|()| self.render_mpv_to_texture(gl, width, height))
                .and_then(|()| self.draw_texture_to_viewport(gl, painter, &viewport))
        };

        match render_result {
            Ok(()) => {
                self.last_render_error = None;
                if let Ok(mut counters) = self.counters.lock() {
                    counters.rendered_frames += 1;
                }
            }
            Err(error) => {
                self.last_render_error = Some(error);
                if let Ok(mut counters) = self.counters.lock() {
                    counters.render_failures += 1;
                }
            }
        }
    }

    pub fn command(&self, args: &[&str]) -> Result<(), String> {
        self.mpv.command(args).map_err(|error| error.to_string())
    }

    pub fn property_string(&self, name: &str) -> Option<String> {
        self.mpv.property_string(name).ok().flatten()
    }

    pub fn last_render_error(&self) -> Option<&str> {
        self.last_render_error.as_deref()
    }

    unsafe fn ensure_gl_objects(
        &mut self,
        gl: &glow::Context,
        width: i32,
        height: i32,
    ) -> Result<(), String> {
        if self.gl_objects.is_none() {
            self.gl_objects = Some(unsafe { GlVideoObjects::create(gl)? });
        }
        let objects = self.gl_objects.as_mut().expect("objects were just created");
        if objects.width == width && objects.height == height {
            return Ok(());
        }

        unsafe {
            gl.bind_texture(glow::TEXTURE_2D, Some(objects.texture));
            gl.tex_parameter_i32(
                glow::TEXTURE_2D,
                glow::TEXTURE_MIN_FILTER,
                glow::LINEAR as i32,
            );
            gl.tex_parameter_i32(
                glow::TEXTURE_2D,
                glow::TEXTURE_MAG_FILTER,
                glow::LINEAR as i32,
            );
            gl.tex_parameter_i32(
                glow::TEXTURE_2D,
                glow::TEXTURE_WRAP_S,
                glow::CLAMP_TO_EDGE as i32,
            );
            gl.tex_parameter_i32(
                glow::TEXTURE_2D,
                glow::TEXTURE_WRAP_T,
                glow::CLAMP_TO_EDGE as i32,
            );
            gl.tex_image_2d(
                glow::TEXTURE_2D,
                0,
                glow::RGBA8 as i32,
                width,
                height,
                0,
                glow::RGBA,
                glow::UNSIGNED_BYTE,
                glow::PixelUnpackData::Slice(None),
            );
            gl.bind_framebuffer(glow::FRAMEBUFFER, Some(objects.framebuffer));
            gl.framebuffer_texture_2d(
                glow::FRAMEBUFFER,
                glow::COLOR_ATTACHMENT0,
                glow::TEXTURE_2D,
                Some(objects.texture),
                0,
            );
            let status = gl.check_framebuffer_status(glow::FRAMEBUFFER);
            gl.bind_framebuffer(glow::FRAMEBUFFER, None);
            if status != glow::FRAMEBUFFER_COMPLETE {
                return Err(format!("video FBO incomplete: 0x{status:04x}"));
            }
        }

        objects.width = width;
        objects.height = height;
        Ok(())
    }

    unsafe fn render_mpv_to_texture(
        &mut self,
        gl: &glow::Context,
        width: i32,
        height: i32,
    ) -> Result<(), String> {
        let objects = self
            .gl_objects
            .as_ref()
            .ok_or_else(|| "video GL objects are not initialized".to_owned())?;
        let render_context = self
            .render_context
            .as_ref()
            .ok_or_else(|| "mpv render context was already freed".to_owned())?;
        let _flags = render_context.update();

        unsafe {
            gl.bind_framebuffer(glow::FRAMEBUFFER, Some(objects.framebuffer));
            gl.viewport(0, 0, width, height);
            gl.disable(glow::SCISSOR_TEST);
            gl.clear_color(0.015, 0.016, 0.018, 1.0);
            gl.clear(glow::COLOR_BUFFER_BIT);
        }

        let mut fbo = MpvOpenGlFbo {
            fbo: native_framebuffer_id(objects.framebuffer),
            w: width,
            h: height,
            internal_format: 0,
        };
        let mut flip_y = 1;
        let mut params = [
            MpvRenderParam::opengl_fbo(&mut fbo),
            MpvRenderParam::flip_y(&mut flip_y),
            MpvRenderParam::invalid(),
        ];
        render_context
            .render(&mut params)
            .map_err(|error| error.to_string())?;
        Ok(())
    }

    unsafe fn draw_texture_to_viewport(
        &self,
        gl: &glow::Context,
        painter: &Painter,
        viewport: &egui::epaint::ViewportInPixels,
    ) -> Result<(), String> {
        let objects = self
            .gl_objects
            .as_ref()
            .ok_or_else(|| "video GL objects are not initialized".to_owned())?;

        unsafe {
            gl.bind_framebuffer(glow::FRAMEBUFFER, painter.intermediate_fbo());
            gl.viewport(
                viewport.left_px,
                viewport.from_bottom_px,
                viewport.width_px,
                viewport.height_px,
            );
            gl.disable(glow::DEPTH_TEST);
            gl.disable(glow::CULL_FACE);
            gl.disable(glow::BLEND);
            gl.use_program(Some(objects.program));
            gl.active_texture(glow::TEXTURE0);
            gl.bind_texture(glow::TEXTURE_2D, Some(objects.texture));
            gl.bind_vertex_array(Some(objects.vertex_array));
            gl.draw_arrays(glow::TRIANGLE_STRIP, 0, 4);
            gl.bind_vertex_array(None);
            gl.bind_texture(glow::TEXTURE_2D, None);
            gl.use_program(None);
        }
        Ok(())
    }
}

impl Drop for VideoRenderer {
    fn drop(&mut self) {
        if let Some(render_context) = self.render_context.take() {
            // Safety: clearing the callback with a null context stops mpv
            // from touching the repaint context before it is dropped.
            unsafe {
                render_context.set_update_callback(None, ptr::null_mut());
            }
            drop(render_context);
        }
        let _ = &self.repaint_context;
    }
}

struct GlVideoObjects {
    framebuffer: glow::NativeFramebuffer,
    texture: glow::NativeTexture,
    program: glow::NativeProgram,
    vertex_array: glow::NativeVertexArray,
    _vertex_buffer: glow::NativeBuffer,
    width: i32,
    height: i32,
}

impl GlVideoObjects {
    unsafe fn create(gl: &glow::Context) -> Result<Self, String> {
        unsafe {
            let framebuffer = gl.create_framebuffer().map_err(string_error)?;
            let texture = gl.create_texture().map_err(string_error)?;
            let program = create_program(gl)?;
            let vertex_array = gl.create_vertex_array().map_err(string_error)?;
            let vertex_buffer = gl.create_buffer().map_err(string_error)?;

            let vertices: [f32; 16] = [
                -1.0, -1.0, 0.0, 0.0, 1.0, -1.0, 1.0, 0.0, -1.0, 1.0, 0.0, 1.0, 1.0, 1.0, 1.0, 1.0,
            ];
            gl.bind_vertex_array(Some(vertex_array));
            gl.bind_buffer(glow::ARRAY_BUFFER, Some(vertex_buffer));
            gl.buffer_data_u8_slice(
                glow::ARRAY_BUFFER,
                floats_as_bytes(&vertices),
                glow::STATIC_DRAW,
            );
            gl.enable_vertex_attrib_array(0);
            gl.vertex_attrib_pointer_f32(0, 2, glow::FLOAT, false, 16, 0);
            gl.enable_vertex_attrib_array(1);
            gl.vertex_attrib_pointer_f32(1, 2, glow::FLOAT, false, 16, 8);
            gl.bind_buffer(glow::ARRAY_BUFFER, None);
            gl.bind_vertex_array(None);

            gl.use_program(Some(program));
            if let Some(location) = gl.get_uniform_location(program, "u_texture") {
                gl.uniform_1_i32(Some(&location), 0);
            }
            gl.use_program(None);

            Ok(Self {
                framebuffer,
                texture,
                program,
                vertex_array,
                _vertex_buffer: vertex_buffer,
                width: 0,
                height: 0,
            })
        }
    }
}

fn create_program(gl: &glow::Context) -> Result<glow::NativeProgram, String> {
    unsafe {
        let program = gl.create_program().map_err(string_error)?;
        let vertex_shader = compile_shader(
            gl,
            glow::VERTEX_SHADER,
            r#"#version 330 core
layout(location = 0) in vec2 a_pos;
layout(location = 1) in vec2 a_uv;
out vec2 v_uv;
void main() {
    gl_Position = vec4(a_pos, 0.0, 1.0);
    v_uv = a_uv;
}"#,
        )?;
        let fragment_shader = compile_shader(
            gl,
            glow::FRAGMENT_SHADER,
            r#"#version 330 core
in vec2 v_uv;
uniform sampler2D u_texture;
out vec4 out_color;
void main() {
    out_color = texture(u_texture, v_uv);
}"#,
        )?;
        gl.attach_shader(program, vertex_shader);
        gl.attach_shader(program, fragment_shader);
        gl.link_program(program);
        gl.detach_shader(program, vertex_shader);
        gl.detach_shader(program, fragment_shader);
        gl.delete_shader(vertex_shader);
        gl.delete_shader(fragment_shader);

        if !gl.get_program_link_status(program) {
            let log = gl.get_program_info_log(program);
            gl.delete_program(program);
            return Err(format!("video shader link failed: {log}"));
        }

        Ok(program)
    }
}

fn compile_shader(
    gl: &glow::Context,
    shader_type: u32,
    source: &str,
) -> Result<glow::NativeShader, String> {
    unsafe {
        let shader = gl.create_shader(shader_type).map_err(string_error)?;
        gl.shader_source(shader, source);
        gl.compile_shader(shader);
        if !gl.get_shader_compile_status(shader) {
            let log = gl.get_shader_info_log(shader);
            gl.delete_shader(shader);
            return Err(format!("video shader compile failed: {log}"));
        }
        Ok(shader)
    }
}

fn floats_as_bytes(values: &[f32]) -> &[u8] {
    unsafe {
        std::slice::from_raw_parts(values.as_ptr().cast::<u8>(), std::mem::size_of_val(values))
    }
}

fn string_error(error: String) -> String {
    error
}

pub fn resolve_libmpv_path() -> Result<PathBuf, String> {
    let mut searched = Vec::new();
    if let Some(configured) = env::var_os(LIBMPV_PATH_ENV).filter(|value| !value.is_empty()) {
        let configured = PathBuf::from(configured);
        let candidate = if configured.is_dir() {
            configured.join(LIBMPV_DLL_NAME)
        } else {
            configured
        };
        searched.push(candidate.clone());
        if candidate.is_file() {
            return Ok(candidate);
        }
    }

    match find_library_for_current_process() {
        Ok(path) => return Ok(path),
        Err(error) => searched.extend(error.searched_paths),
    }

    let repo_runtime = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("..")
        .join("..")
        .join("runtime")
        .join("windows")
        .join("libmpv")
        .join(LIBMPV_DLL_NAME);
    searched.push(repo_runtime.clone());
    if repo_runtime.is_file() {
        return Ok(repo_runtime);
    }

    let portable = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("..")
        .join("..")
        .join("apps")
        .join("desktop-windows")
        .join("build")
        .join("release")
        .join("windows-portable")
        .join("app")
        .join(LIBMPV_DLL_NAME);
    searched.push(portable.clone());
    if portable.is_file() {
        return Ok(portable);
    }

    Err(format!(
        "libmpv was not found; searched {}",
        searched
            .iter()
            .map(|path| path.display().to_string())
            .collect::<Vec<_>>()
            .join(", ")
    ))
}

unsafe extern "C" fn request_repaint(context: *mut c_void) {
    if context.is_null() {
        return;
    }
    let context = unsafe { &*(context.cast::<egui::Context>()) };
    context.request_repaint();
}

#[cfg(windows)]
unsafe extern "C" fn get_proc_address(_context: *mut c_void, name: *const c_char) -> *mut c_void {
    if name.is_null() {
        return ptr::null_mut();
    }
    let address = unsafe { wglGetProcAddress(name) };
    if !is_invalid_gl_proc(address) {
        return address;
    }
    let module = opengl32_module();
    if module.is_null() {
        return ptr::null_mut();
    }
    unsafe { GetProcAddress(module, name) }
}

#[cfg(not(windows))]
unsafe extern "C" fn get_proc_address(_context: *mut c_void, _name: *const c_char) -> *mut c_void {
    ptr::null_mut()
}

#[cfg(windows)]
fn opengl32_module() -> *mut c_void {
    static OPENGL32: std::sync::OnceLock<usize> = std::sync::OnceLock::new();
    *OPENGL32.get_or_init(|| {
        let wide: Vec<u16> = "opengl32.dll".encode_utf16().chain([0]).collect();
        unsafe { LoadLibraryW(wide.as_ptr()) as usize }
    }) as *mut c_void
}

#[cfg(windows)]
fn is_invalid_gl_proc(address: *mut c_void) -> bool {
    matches!(address as usize, 0 | 1 | 2 | 3 | usize::MAX)
}

#[cfg(windows)]
fn native_framebuffer_id(framebuffer: glow::NativeFramebuffer) -> i32 {
    framebuffer.0.get() as i32
}

#[cfg(not(windows))]
fn native_framebuffer_id(_framebuffer: glow::NativeFramebuffer) -> i32 {
    0
}

#[cfg(windows)]
#[link(name = "opengl32")]
unsafe extern "system" {
    fn wglGetProcAddress(name: *const c_char) -> *mut c_void;
}

#[cfg(windows)]
#[link(name = "kernel32")]
unsafe extern "system" {
    fn LoadLibraryW(file_name: *const u16) -> *mut c_void;
    fn GetProcAddress(module: *mut c_void, procedure_name: *const c_char) -> *mut c_void;
}
