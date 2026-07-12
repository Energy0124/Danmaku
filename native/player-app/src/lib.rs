//! Danmaku native player client.
//!
//! Phase 3 M1/M2: an egui window compositing libmpv's OpenGL
//! render API output, with play/pause/seek/rate/volume controls, audio and
//! subtitle track selection, fade-over-video controls, and fullscreen with
//! bounds restore. Native danmaku supports the Rust server client route,
//! local XML/JSON, ASS compatibility, drag-and-drop attachment, display
//! controls, and seek-correct layout. Builds on the approach proven by
//! `native/spike-egui-player` and the `player-windows-mpv` bridge crate.

pub mod app;
pub mod cli;
pub mod clock;
pub mod danmaku;
pub mod discovery;
pub mod hosting;
pub mod icons;
pub mod library;
pub mod localization;
mod net;
pub mod platform;
pub mod posters;
pub mod preferences;
pub mod screens;
pub mod session;
pub mod smoke;
pub mod theme;
pub mod tracks;
pub mod video;
