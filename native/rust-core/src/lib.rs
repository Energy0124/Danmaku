//! Performance-sensitive primitives for the Danmaku application.
//!
//! Keep this crate small. It exists for work that benefits from a Rust
//! boundary, not as a home for general application logic.

mod timeline;

pub use timeline::{DanmakuEvent, Timeline};
