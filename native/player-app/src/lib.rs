//! Danmaku native player client.
//!
//! Phase 3 M1 playback core: an egui window compositing libmpv's OpenGL
//! render API output, with play/pause/seek/rate/volume controls, audio and
//! subtitle track selection, fade-over-video controls, and fullscreen with
//! bounds restore. Builds on the compositing approach proven by
//! `native/spike-egui-player` and the `player-windows-mpv` bridge crate.

pub mod app;
pub mod cli;
pub mod clock;
pub mod smoke;
pub mod tracks;
pub mod video;
