pub mod cli;
pub mod clock;
pub mod danmaku;
pub mod mpv;
pub mod smoke;

pub const DEFAULT_MEDIA: &str = "av://lavfi:testsrc2=duration=3600:size=1920x1080:rate=60";
